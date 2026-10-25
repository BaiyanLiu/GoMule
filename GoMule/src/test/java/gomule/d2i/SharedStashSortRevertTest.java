package gomule.d2i;

import gomule.d2x.D2Stash;
import gomule.d2x.D2StashReader;
import gomule.d2x.D2StashWriter;
import gomule.d2x.StashSortRule;
import gomule.item.D2Item;
import gomule.util.D2Backup;
import gomule.util.D2Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static gomule.d2x.StashSortRule.Kind;
import static gomule.d2x.StashSortRule.Quality;
import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class SharedStashSortRevertTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void revertPutsEveryItemBackOnItsPageAndSpotAfterWriteAndReadBack() throws Exception {
        for (File sample : new File[]{loadTestResources("sharedStashFiles", "modernGold.d2i"), loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i")}) {
            File sharedStashFile = tempDir.resolve(sample.getName()).toFile();
            Files.copy(sample.toPath(), sharedStashFile.toPath());
            D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
            List<Set<String>> before = layout(sharedStash);
            int goldBefore = sharedStash.getTotalGold();

            Map<String, D2Stash> stashes = new LinkedHashMap<>();
            Map<String, List<StashSortRule>> rules = new LinkedHashMap<>();
            String uniques = tempDir.resolve(sample.getName() + "-uniques.d2x").toString();
            String sets = tempDir.resolve(sample.getName() + "-sets.d2x").toString();
            rules.put(uniques, Collections.singletonList(StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE))));
            rules.put(sets, Collections.singletonList(StashSortRule.qualityIs(Collections.singletonList(Quality.SET))));
            for (String stash : rules.keySet()) stashes.put(stash, new D2StashReader().readStash(ROW, stash));

            List<SharedStashSorter.MovedItem> moved = new ArrayList<>();
            SharedStashSorter.apply(sharedStash, SharedStashSorter.plan(sharedStash, rules), stashes::get, moved);
            assertTrue(moved.size() > 10, sample.getName());
            assertNotEquals(before, layout(sharedStash));

            assertTrue(SharedStashSorter.revert(sharedStash, moved).isEmpty());
            stashes.values().forEach(stash -> assertEquals(0, stash.getNrItems()));
            assertEquals(before, layout(sharedStash));

            new D2SharedStashWriter(ROW, sharedStashFile, Files.readAllBytes(sharedStashFile.toPath())).write(sharedStash);
            for (D2Stash stash : stashes.values()) new D2StashWriter(ROW, stash.getFilename()).write(stash);
            D2SharedStash readBack = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
            assertEquals(before, layout(readBack), sample.getName());
            assertEquals(goldBefore, readBack.getTotalGold());
            for (String stash : rules.keySet()) assertEquals(0, new D2StashReader().readStash(ROW, stash).getNrItems());
        }
    }

    @Test
    public void revertFindsAnotherSpotWhenTheOldOneIsTakenAndSkipsItemsGoneFromTheStash() throws Exception {
        File sharedStashFile = tempDir.resolve("shared.d2i").toFile();
        Files.copy(loadTestResources("sharedStashFiles", "modernGold.d2i").toPath(), sharedStashFile.toPath());
        D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
        int itemsBefore = sharedStash.getNrItems();
        D2Stash jewelry = new D2StashReader().readStash(ROW, tempDir.resolve("jewelry.d2x").toString());
        D2Stash armor = new D2StashReader().readStash(ROW, tempDir.resolve("armor.d2x").toString());
        Map<String, List<StashSortRule>> rules = new LinkedHashMap<>();
        rules.put(jewelry.getFilename(), Collections.singletonList(StashSortRule.kindIs(Arrays.asList(Kind.RING, Kind.AMULET))));
        rules.put(armor.getFilename(), Collections.singletonList(StashSortRule.kindIs(Collections.singletonList(Kind.ARMOR))));
        Map<String, D2Stash> stashes = new HashMap<>();
        stashes.put(jewelry.getFilename(), jewelry);
        stashes.put(armor.getFilename(), armor);
        List<SharedStashSorter.MovedItem> moved = new ArrayList<>();
        SharedStashSorter.apply(sharedStash, SharedStashSorter.plan(sharedStash, rules), stashes::get, moved);

        List<SharedStashSorter.MovedItem> rings = new ArrayList<>();
        moved.stream().filter(it -> it.getStash() == jewelry).forEach(rings::add);
        assertTrue(rings.size() >= 2, "need two rings or amulets in the fixture");
        SharedStashSorter.MovedItem first = rings.get(0);
        SharedStashSorter.MovedItem second = rings.get(1);
        SharedStashSorter.MovedItem armorPiece = moved.stream().filter(it -> it.getStash() == armor).findFirst().orElseThrow(IllegalStateException::new);
        short firstCol = first.getItem().get_col();
        short firstRow = first.getItem().get_row();
        // after the sort: the second ring is dragged back onto the first ring's old spot, and the armor piece leaves its stash
        jewelry.removeItem(second.getItem());
        int pane = originalPane(first);
        sharedStash.replacePane(pane, sharedStash.getPane(pane).addItem(firstCol, firstRow, second.getItem()));
        armor.removeItem(armorPiece.getItem());

        List<SharedStashSorter.MovedItem> notReverted = SharedStashSorter.revert(sharedStash, moved);
        assertEquals(new HashSet<>(Arrays.asList(second, armorPiece)), new HashSet<>(notReverted));
        assertEquals(0, jewelry.getNrItems());
        assertEquals(0, armor.getNrItems());
        assertTrue(sharedStash.containsItem(first.getItem()));
        assertFalse(first.getItem().get_col() == firstCol && first.getItem().get_row() == firstRow, "spot was taken, so it goes elsewhere");
        assertEquals(itemsBefore - 1, sharedStash.getNrItems());
    }

    private static int originalPane(SharedStashSorter.MovedItem item) throws Exception {
        java.lang.reflect.Field field = SharedStashSorter.MovedItem.class.getDeclaredField("paneIndex");
        field.setAccessible(true);
        return field.getInt(item);
    }

    @Test
    public void savingWritesABakOfTheFileAsItWasOnDisk() throws Exception {
        D2Project project = Mockito.mock(D2Project.class);
        Mockito.when(project.getBackup()).thenReturn(D2Project.BACKUP_NONE);
        File sharedStashFile = tempDir.resolve("ModernSharedStashSoftCoreV2.d2i").toFile();
        Files.copy(loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i").toPath(), sharedStashFile.toPath());
        byte[] sharedStashBefore = Files.readAllBytes(sharedStashFile.toPath());
        File atmaFile = tempDir.resolve("Set - Aldur.d2x").toFile();
        Files.copy(loadTestResources("userSamples", "Set - Aldur.d2x").toPath(), atmaFile.toPath());
        byte[] atmaBefore = Files.readAllBytes(atmaFile.toPath());

        D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
        D2Stash atma = new D2StashReader().readStash(ROW, atmaFile.getAbsolutePath());
        D2SharedStash.D2SharedStashPane pane = sharedStash.getPane(0);
        D2Item item = pane.getItems().get(0);
        sharedStash.replacePane(0, pane.removeItem(item));
        atma.addItem(item);
        atma.save(project);
        sharedStash.save(project);

        assertArrayEquals(sharedStashBefore, Files.readAllBytes(onlyBak(sharedStashFile)));
        assertArrayEquals(atmaBefore, Files.readAllBytes(onlyBak(atmaFile)));
        assertFalse(Arrays.equals(sharedStashBefore, Files.readAllBytes(sharedStashFile.toPath())));
        assertEquals(atma.getNrItems(), new D2StashReader().readStash(ROW, atmaFile.getAbsolutePath()).getNrItems());

        // more saves keep the newest three, each holding the version from just before its save
        List<byte[]> versionsBeforeSave = new ArrayList<>();
        versionsBeforeSave.add(sharedStashBefore);
        for (int save = 0; save < 4; save++) {
            D2SharedStash.D2SharedStashPane nextPane = sharedStash.getPane(0);
            sharedStash.replacePane(0, nextPane.removeItem(nextPane.getItems().get(0)));
            versionsBeforeSave.add(Files.readAllBytes(sharedStashFile.toPath()));
            sharedStash.save(project);
            D2SharedStash readBack = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
            assertEquals(sharedStash.getNrItems(), readBack.getNrItems());
            assertEquals(sharedStash.getTotalGold(), readBack.getTotalGold());
        }
        List<Path> baks = D2Backup.getBakFiles(sharedStashFile.getAbsolutePath());
        assertEquals(D2Backup.BAK_FILES_KEPT, baks.size());
        List<byte[]> newestThree = versionsBeforeSave.subList(versionsBeforeSave.size() - 3, versionsBeforeSave.size());
        for (int i = 0; i < 3; i++) assertArrayEquals(newestThree.get(i), Files.readAllBytes(baks.get(i)), "backup " + i);
        assertEquals(1, D2Backup.getBakFiles(atmaFile.getAbsolutePath()).size(), "another file's backups are left alone");

        // a stash file that doesn't exist yet has nothing to back up
        D2Stash newStash = new D2StashReader().readStash(ROW, tempDir.resolve("new.d2x").toString());
        newStash.save(project);
        assertTrue(D2Backup.getBakFiles(tempDir.resolve("new.d2x").toString()).isEmpty());
    }

    @Test
    public void savingRemovesTheOldSingleBak() throws Exception {
        D2Project project = Mockito.mock(D2Project.class);
        Mockito.when(project.getBackup()).thenReturn(D2Project.BACKUP_NONE);
        File atmaFile = tempDir.resolve("Set - Aldur.d2x").toFile();
        Files.copy(loadTestResources("userSamples", "Set - Aldur.d2x").toPath(), atmaFile.toPath());
        File legacyBak = new File(atmaFile + ".bak");
        Files.write(legacyBak.toPath(), new byte[]{1, 2, 3});

        D2Stash atma = new D2StashReader().readStash(ROW, atmaFile.getAbsolutePath());
        atma.setModified(true);
        atma.save(project);

        assertFalse(legacyBak.exists());
        assertEquals(1, D2Backup.getBakFiles(atmaFile.getAbsolutePath()).size());
        assertEquals(atma.getNrItems(), new D2StashReader().readStash(ROW, atmaFile.getAbsolutePath()).getNrItems());
    }

    @Test
    public void restoringABackupPutsThatVersionBackAndKeepsTheCurrentOneAsABackup() throws Exception {
        D2Project project = Mockito.mock(D2Project.class);
        Mockito.when(project.getBackup()).thenReturn(D2Project.BACKUP_NONE);
        for (String sample : new String[]{"ModernSharedStashSoftCoreV2.d2i", "Set - Aldur.d2x"}) {
            File file = tempDir.resolve(sample).toFile();
            Files.copy(loadTestResources("userSamples", sample).toPath(), file.toPath());
            byte[] original = Files.readAllBytes(file.toPath());
            int originalItems = itemCount(file);

            // three saves, each with one item fewer, so every backup is a different version
            for (int save = 0; save < 3; save++) {
                if (sample.endsWith(".d2i")) {
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
            byte[] current = Files.readAllBytes(file.toPath());
            assertEquals(originalItems - 3, itemCount(file));
            List<Path> baks = D2Backup.getBakFiles(file.getAbsolutePath());
            assertEquals(3, baks.size());
            assertArrayEquals(original, Files.readAllBytes(baks.get(0)), "oldest backup is the untouched sample");

            D2Backup.restoreBak(file.getAbsolutePath(), Files.readAllBytes(baks.get(0)));

            assertArrayEquals(original, Files.readAllBytes(file.toPath()), sample);
            assertEquals(originalItems, itemCount(file), sample + " reads back with all its items");
            List<Path> after = D2Backup.getBakFiles(file.getAbsolutePath());
            assertEquals(3, after.size());
            assertArrayEquals(current, Files.readAllBytes(after.get(after.size() - 1)), "the version before the restore is the newest backup");
        }
    }

    private static int itemCount(File file) throws Exception {
        return file.getName().endsWith(".d2i")
                ? new D2SharedStashReader().readStash(ROW, file.getAbsolutePath()).getNrItems()
                : new D2StashReader().readStash(ROW, file.getAbsolutePath()).getNrItems();
    }

    private static Path onlyBak(File file) {
        List<Path> baks = D2Backup.getBakFiles(file.getAbsolutePath());
        assertEquals(1, baks.size(), baks.toString());
        return baks.get(0);
    }

    /**
     * Per page, every item as name, spot and bytes.
     */
    private static List<Set<String>> layout(D2SharedStash sharedStash) {
        List<Set<String>> layout = new ArrayList<>();
        for (D2SharedStash.D2SharedStashPane pane : sharedStash.getPanes()) {
            Set<String> items = new TreeSet<>();
            for (D2Item item : pane.getItems()) {
                items.add(item.getItemName() + "@" + item.get_col() + "," + item.get_row() + ":" + Arrays.toString(item.get_bytes()));
            }
            layout.add(items);
        }
        return layout;
    }
}
