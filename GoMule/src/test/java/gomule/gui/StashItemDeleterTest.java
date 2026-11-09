package gomule.gui;

import gomule.d2x.D2Stash;
import gomule.d2x.D2StashReader;
import gomule.item.D2Item;
import gomule.util.D2Backup;
import gomule.util.D2Project;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deleting items from an ATMA stash, checked against the sample stash "Set - Aldur.d2x": delete, save, read the
 * file back, and check exactly the deleted items are gone and the rest are unchanged.
 */
public class StashItemDeleterTest {

    @TempDir
    Path tempDir;

    private File stashFile;
    private byte[] originalBytes;

    @BeforeAll
    public static void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @BeforeEach
    public void copySample() throws Exception {
        stashFile = tempDir.resolve("Set - Aldur.d2x").toFile();
        Files.copy(loadTestResources("userSamples", "Set - Aldur.d2x").toPath(), stashFile.toPath());
        originalBytes = Files.readAllBytes(stashFile.toPath());
    }

    private D2Stash read() {
        return new D2StashReader().readStash(ROW, stashFile.getAbsolutePath());
    }

    private static List<String> bytesOf(List<D2Item> items) {
        return items.stream().map(item -> Arrays.toString(item.get_bytes())).collect(Collectors.toList());
    }

    private static D2Project project() {
        D2Project project = mock(D2Project.class);
        when(project.getBackup()).thenReturn(D2Project.BACKUP_NONE);
        return project;
    }

    @Test
    public void deletingOneItemSavesAndReadsBackWithoutIt() {
        D2Stash stash = read();
        int before = stash.getNrItems();
        D2Item victim = stash.getItemList().get(3);
        List<String> expected = bytesOf(stash.getItemList());
        expected.remove(3);

        List<D2Item> removed = StashItemDeleter.delete(stash, Collections.singletonList(victim));

        assertEquals(Collections.singletonList(victim), removed);
        assertTrue(stash.isModified());
        stash.save(project());
        D2Stash readBack = read();
        assertEquals(before - 1, readBack.getNrItems());
        assertEquals(expected, bytesOf(readBack.getItemList()));
    }

    @Test
    public void deletingSeveralItemsAtOnce() {
        D2Stash stash = read();
        List<D2Item> all = new ArrayList<>(stash.getItemList());
        List<D2Item> victims = Arrays.asList(all.get(0), all.get(5), all.get(all.size() - 1));
        List<D2Item> kept = new ArrayList<>(all);
        kept.removeAll(victims);

        assertEquals(victims, StashItemDeleter.delete(stash, victims));
        stash.save(project());

        assertEquals(bytesOf(kept), bytesOf(read().getItemList()));
    }

    @Test
    public void deletingEverythingLeavesAnEmptyStashThatStillLoads() {
        D2Stash stash = read();
        StashItemDeleter.delete(stash, new ArrayList<>(stash.getItemList()));
        stash.save(project());

        D2Stash readBack = read();
        assertEquals(0, readBack.getNrItems());
        // and it can take items again and still round-trip
        D2Stash original = new D2StashReader().readStash(ROW, loadTestResources("userSamples", "Set - Aldur.d2x").getAbsolutePath());
        original.getItemList().forEach(readBack::addItem);
        readBack.save(project());
        assertEquals(bytesOf(original.getItemList()), bytesOf(read().getItemList()));
    }

    @Test
    public void onlyOneChangeEventForSeveralItemsAndNoneDuringTheDelete() {
        D2Stash stash = read();
        int[] events = {0};
        stash.addD2ItemListListener(new D2ItemListListener() {
            public void itemListChanged() {
                events[0]++;
            }
        });
        List<D2Item> victims = new ArrayList<>(stash.getItemList().subList(0, 4));
        StashItemDeleter.delete(stash, victims);
        assertTrue(events[0] <= 1, "fired " + events[0] + " change events");
    }

    @Test
    public void itemsNotInTheStashAreSkipped() {
        D2Stash stash = read();
        D2Item other = read().getItemList().get(0); // same bytes, different object
        D2Item mine = stash.getItemList().get(1);
        int before = stash.getNrItems();

        List<D2Item> removed = StashItemDeleter.delete(stash, Arrays.asList(other, null, mine));

        assertEquals(Collections.singletonList(mine), removed);
        assertEquals(before - 1, stash.getNrItems());
    }

    @Test
    public void savingAfterDeleteKeepsABackupThatRestoresTheItems() throws Exception {
        D2Stash stash = read();
        List<String> originalItems = bytesOf(stash.getItemList());
        StashItemDeleter.delete(stash, new ArrayList<>(stash.getItemList().subList(0, 2)));
        stash.save(project());
        assertEquals(originalItems.size() - 2, read().getNrItems());

        List<Path> baks = D2Backup.getBakFiles(stashFile.getAbsolutePath());
        assertEquals(1, baks.size());
        byte[] bak = Files.readAllBytes(baks.get(0));
        assertArrayEquals(originalBytes, bak);

        D2Backup.restoreBak(stashFile.getAbsolutePath(), bak);
        assertArrayEquals(originalBytes, Files.readAllBytes(stashFile.toPath()));
        assertEquals(originalItems, bytesOf(read().getItemList()));
    }

    @Test
    public void confirmationNamesOneItem() {
        D2Item item = read().getItemList().get(0);
        assertEquals("Delete " + item.getName() + "?", StashItemDeleter.confirmationMessage(Collections.singletonList(item)));
    }

    @Test
    public void confirmationListsSeveralItemsAndCutsOffLongLists() {
        List<D2Item> items = read().getItemList();
        String few = StashItemDeleter.confirmationMessage(items.subList(0, 3));
        assertTrue(few.startsWith("Delete these 3 items?"));
        for (D2Item item : items.subList(0, 3)) assertTrue(few.contains(item.getName()));
        assertFalse(few.contains("more"));

        String many = StashItemDeleter.confirmationMessage(items);
        assertTrue(many.startsWith("Delete these " + items.size() + " items?"));
        assertTrue(many.contains("and " + (items.size() - StashItemDeleter.MAX_NAMES_LISTED) + " more"));
        assertEquals(StashItemDeleter.MAX_NAMES_LISTED, many.split("•", -1).length - 1);
    }
}
