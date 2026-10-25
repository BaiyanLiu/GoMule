package gomule.d2i;

import gomule.d2x.D2Stash;
import gomule.d2x.D2StashReader;
import gomule.d2x.StashSortRule;
import gomule.util.D2Backup;
import gomule.util.D2Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static gomule.d2x.StashSortRule.Quality;
import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class BackupAndRevertEdgeCaseTest {

    @TempDir
    Path tempDir;

    private D2Project project;

    @BeforeEach
    public void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
        project = Mockito.mock(D2Project.class);
        Mockito.when(project.getBackup()).thenReturn(D2Project.BACKUP_NONE);
    }

    @Test
    public void revertingTwiceOrAfterAnItemWasDraggedToAnotherStashLeavesEveryItemInOnePlace() throws Exception {
        D2SharedStash sharedStash = copyAndReadShared("userSamples", "ModernSharedStashSoftCoreV2.d2i");
        int itemsBefore = sharedStash.getNrItems();
        D2Stash uniques = new D2StashReader().readStash(ROW, tempDir.resolve("uniques.d2x").toString());
        D2Stash other = new D2StashReader().readStash(ROW, tempDir.resolve("other.d2x").toString());
        Map<String, List<StashSortRule>> rules = Collections.singletonMap(uniques.getFilename(),
                Collections.singletonList(StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE))));
        List<SharedStashSorter.MovedItem> moved = new ArrayList<>();
        SharedStashSorter.apply(sharedStash, SharedStashSorter.plan(sharedStash, rules), name -> uniques, moved);
        assertTrue(moved.size() >= 2);

        // after the sort, one unique is dragged on into another ATMA stash
        SharedStashSorter.MovedItem dragged = moved.get(0);
        uniques.removeItem(dragged.getItem());
        other.addItem(dragged.getItem());

        assertEquals(Collections.singletonList(dragged), SharedStashSorter.revert(sharedStash, moved));
        assertEquals(itemsBefore - 1, sharedStash.getNrItems());
        assertEquals(0, uniques.getNrItems());
        assertEquals(1, other.getNrItems(), "the dragged item stays where the user put it");
        assertFalse(sharedStash.containsItem(dragged.getItem()));

        // a second revert finds nothing left to put back and doesn't add copies
        sharedStash.setModified(false);
        assertEquals(moved.size(), SharedStashSorter.revert(sharedStash, moved).size());
        assertEquals(itemsBefore - 1, sharedStash.getNrItems());
        assertFalse(sharedStash.isModified(), "nothing changed, so nothing to save");
    }

    @Test
    public void sortingWithNoStashToGoToChangesNothing() throws Exception {
        D2SharedStash sharedStash = copyAndReadShared("userSamples", "ModernSharedStashSoftCoreV2.d2i");
        int itemsBefore = sharedStash.getNrItems();
        Map<String, List<StashSortRule>> rules = Collections.singletonMap(tempDir.resolve("x.d2x").toString(),
                Collections.singletonList(StashSortRule.nameContains("no item is called this")));
        SharedStashSorter.Plan plan = SharedStashSorter.plan(sharedStash, rules);
        assertEquals(itemsBefore, plan.getUnmatched().size());
        List<SharedStashSorter.MovedItem> moved = new ArrayList<>();
        assertTrue(SharedStashSorter.apply(sharedStash, plan, name -> null, moved).isEmpty());
        assertTrue(moved.isEmpty());
        assertTrue(SharedStashSorter.revert(sharedStash, moved).isEmpty());
        assertFalse(sharedStash.isModified());
        assertEquals(itemsBefore, sharedStash.getNrItems());
    }

    @Test
    public void backupsSortByTimeThenCounterAndOnlyThisFilesBackupsArePrunedOrListed() throws Exception {
        // brackets and dots in the name must be matched literally
        String file = tempDir.resolve("Set - Aldur (1).d2x").toString();
        Files.copy(loadTestResources("userSamples", "Set - Aldur.d2x").toPath(), path(file));
        for (String suffix : new String[]{"20200101-000000-123-10", "20200101-000000-124", "20200101-000000-123-2", "20200101-000000-123", "20200101-000000-123-1"}) {
            Files.write(path(file + "." + suffix + ".bak"), suffix.getBytes(StandardCharsets.UTF_8));
        }
        List<String> unrelated = Arrays.asList(file + ".notes.bak", tempDir.resolve("Other.d2x.20200101-000000-000.bak").toString(),
                tempDir.resolve("Set - Aldur (1)Xd2x.20200101-000000-000.bak").toString(), file + ".20200101-000000-12.bak");
        for (String name : unrelated) Files.write(path(name), new byte[]{1});
        Files.write(path(file + ".bak"), new byte[]{1});

        assertEquals(Arrays.asList("20200101-000000-123", "20200101-000000-123-1", "20200101-000000-123-2", "20200101-000000-123-10", "20200101-000000-124"),
                contents(D2Backup.getBakFiles(file)));
        assertEquals("2020-01-01 00:00:00", D2Backup.getBakTime(D2Backup.getBakFiles(file).get(0)));
        assertEquals("x.notes.bak", D2Backup.getBakTime(path("x.notes.bak")));

        D2Backup.writeBak(file);

        List<Path> kept = D2Backup.getBakFiles(file);
        assertEquals(3, kept.size());
        assertEquals(Arrays.asList("20200101-000000-123-10", "20200101-000000-124"), contents(kept.subList(0, 2)));
        assertArrayEquals(Files.readAllBytes(path(file)), Files.readAllBytes(kept.get(2)), "the new backup is the newest");
        assertFalse(Files.exists(path(file + ".bak")), "the old single .bak is cleaned up");
        for (String name : unrelated) assertTrue(Files.exists(path(name)), name);
    }

    @Test
    public void manyBackupsWithinTheSameMillisecondKeepTheNewestThreeInOrder() throws Exception {
        String file = tempDir.resolve("fast.d2x").toString();
        for (int save = 0; save < 30; save++) {
            Files.write(path(file), ("v" + save).getBytes(StandardCharsets.UTF_8));
            D2Backup.writeBak(file);
        }
        assertEquals(Arrays.asList("v27", "v28", "v29"), contents(D2Backup.getBakFiles(file)));
    }

    @Test
    public void aBackupThatCannotBeWrittenStopsTheSaveAndLeavesTheFileAsItWas() throws Exception {
        // the name fits, but with the timestamp added the backup's name is longer than any file system allows
        Path file = tempDir.resolve(String.join("", Collections.nCopies(240, "x")) + ".d2x");
        Files.copy(loadTestResources("userSamples", "Set - Aldur.d2x").toPath(), file);
        byte[] before = Files.readAllBytes(file);
        D2Stash stash = new D2StashReader().readStash(ROW, file.toString());
        stash.removeItem(stash.getItemList().get(0));

        assertThrows(UncheckedIOException.class, () -> stash.save(project));

        assertArrayEquals(before, Files.readAllBytes(file));
        assertTrue(stash.isModified(), "still unsaved, so the change isn't lost");
        assertTrue(D2Backup.getBakFiles(file.toString()).isEmpty());

        // a file in a folder that doesn't exist has nothing to back up
        D2Backup.writeBak(tempDir.resolve("missing").resolve("stash.d2x").toString());
        assertFalse(Files.exists(tempDir.resolve("missing")));
    }

    @Test
    public void restoringTheOldestBackupWorksEvenWhenSavingUnsavedChangesFirstDeletesIt() throws Exception {
        for (String sample : new String[]{"ModernSharedStashSoftCoreV2.d2i", "Set - Aldur.d2x"}) {
            File file = tempDir.resolve(sample).toFile();
            Files.copy(loadTestResources("userSamples", sample).toPath(), file.toPath());
            byte[] original = Files.readAllBytes(file.toPath());
            for (int save = 0; save < 3; save++) removeOneItemAndSave(file);
            Path oldest = D2Backup.getBakFiles(file.getAbsolutePath()).get(0);
            assertArrayEquals(original, Files.readAllBytes(oldest));

            // as the restore dialog does: read the chosen backup, save the open unsaved change, then restore
            byte[] chosen = Files.readAllBytes(oldest);
            D2Backup.checkReadable(ROW, file.getAbsolutePath(), chosen);
            removeOneItemAndSave(file);
            byte[] savedBeforeRestore = Files.readAllBytes(file.toPath());
            assertFalse(Files.exists(oldest), sample + ": the save pushed the chosen backup out");
            D2Backup.restoreBak(file.getAbsolutePath(), chosen);

            assertArrayEquals(original, Files.readAllBytes(file.toPath()), sample);
            List<Path> baks = D2Backup.getBakFiles(file.getAbsolutePath());
            assertEquals(3, baks.size());
            assertArrayEquals(savedBeforeRestore, Files.readAllBytes(baks.get(2)), sample + ": the saved change can be restored in turn");
            if (sample.endsWith(".d2i")) {
                assertEquals(new D2SharedStashReader().readStash(ROW, loadTestResources("userSamples", sample).getAbsolutePath()).getNrItems(),
                        new D2SharedStashReader().readStash(ROW, file.getAbsolutePath()).getNrItems());
            } else {
                assertEquals(new D2StashReader().readStash(ROW, loadTestResources("userSamples", sample).getAbsolutePath()).getNrItems(),
                        new D2StashReader().readStash(ROW, file.getAbsolutePath()).getNrItems());
            }
        }
    }

    @Test
    public void damagedBackupsAreRefusedBeforeAnythingIsRestored() throws Exception {
        byte[] shared = Files.readAllBytes(loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i").toPath());
        byte[] atma = Files.readAllBytes(loadTestResources("userSamples", "Set - Aldur.d2x").toPath());
        String sharedName = tempDir.resolve("shared.d2i").toString();
        String atmaName = tempDir.resolve("atma.d2x").toString();
        D2Backup.checkReadable(ROW, sharedName, shared);
        D2Backup.checkReadable(ROW, atmaName, atma);

        byte[] flipped = atma.clone();
        flipped[flipped.length / 2] ^= 0x5A;
        byte[] garbage = "not a stash".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> damaged = new LinkedHashMap<>();
        damaged.put("atma garbage", garbage);
        damaged.put("atma cut off", Arrays.copyOf(atma, atma.length / 2));
        damaged.put("atma byte changed", flipped);
        damaged.put("shared stash in an ATMA stash", shared);
        for (Map.Entry<String, byte[]> backup : damaged.entrySet()) {
            Exception ex = assertThrows(Exception.class, () -> D2Backup.checkReadable(ROW, atmaName, backup.getValue()), backup.getKey());
            assertTrue(ex.getMessage().contains("can't be read, so nothing was restored"), ex.getMessage());
        }
        for (byte[] backup : Arrays.asList(garbage, new byte[0], Arrays.copyOf(shared, shared.length / 3), atma)) {
            assertThrows(Exception.class, () -> D2Backup.checkReadable(ROW, sharedName, backup));
        }
    }

    private void removeOneItemAndSave(File file) throws Exception {
        if (file.getName().endsWith(".d2i")) {
            D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath());
            D2SharedStash.D2SharedStashPane pane = sharedStash.getPane(0);
            sharedStash.replacePane(0, pane.removeItem(pane.getItems().get(0)));
            sharedStash.save(project);
        } else {
            D2Stash stash = new D2StashReader().readStash(ROW, file.getAbsolutePath());
            stash.removeItem(stash.getItemList().get(0));
            stash.save(project);
        }
    }

    private D2SharedStash copyAndReadShared(String folder, String name) throws Exception {
        File file = tempDir.resolve(name).toFile();
        Files.copy(loadTestResources(folder, name).toPath(), file.toPath());
        return new D2SharedStashReader().readStash(ROW, file.getAbsolutePath());
    }

    private static Path path(String name) {
        return java.nio.file.Paths.get(name);
    }

    private static List<String> contents(List<Path> files) {
        return files.stream().map(file -> {
            try {
                return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            } catch (Exception pEx) {
                throw new RuntimeException(pEx);
            }
        }).collect(Collectors.toList());
    }
}
