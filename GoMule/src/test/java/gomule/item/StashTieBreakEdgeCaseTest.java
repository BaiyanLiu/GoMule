package gomule.item;

import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.d2i.D2SharedStashWriter;
import gomule.d2s.D2Character;
import gomule.d2x.D2StashReader;
import gomule.d2x.D2StashWriter;
import gomule.d2x.StashColumn;
import gomule.d2x.StashColumn.Builtin;
import gomule.util.D2Project;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Edge cases for how the ATMA stash table breaks ties on the clicked column: Name, then Roll (worst first, blanks
 * last), then defense (lowest first, no defense last), skipping whichever of those is the clicked column.
 */
public class StashTieBreakEdgeCaseTest {

    private static List<D2Item> sampleItems;

    @TempDir
    Path tempDir;

    @BeforeAll
    public static void setup() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        sampleItems = readItems(SAMPLES);
    }

    private static final File[] SAMPLES = {
            loadTestResources("userSamples", "Javazon.d2s"),
            loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i"),
            loadTestResources("userSamples", "Set - Aldur.d2x"),
            loadTestResources("sharedStashFiles", "modernGold.d2i")};

    private static List<D2Item> readItems(File... files) throws Exception {
        List<D2Item> items = new ArrayList<>();
        for (File file : files) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".d2s")) {
                items.addAll(new D2Character(ROW, file.getAbsolutePath()).getItemList());
            } else if (name.endsWith(".d2x")) {
                items.addAll(new D2StashReader().readStash(ROW, file.getAbsolutePath()).getItemList());
            } else {
                D2SharedStash stash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath());
                for (int i = 0; i < stash.getPanes().size(); i++) items.addAll(stash.getPane(i).getItems());
            }
        }
        return items;
    }

    /** Every built-in column, plus null for a property column, which has no built-in. */
    private static List<Builtin> sortColumns() {
        List<Builtin> columns = new ArrayList<>(Arrays.asList(Builtin.values()));
        columns.add(null);
        return columns;
    }

    private static D2Item item(String name, Integer rollPoints, Integer defense) {
        D2Item item = mock(D2Item.class);
        when(item.getName()).thenReturn(name);
        when(item.getRolls()).thenReturn(new ItemRollPercent.Rolls(rollPoints == null ? null : 0.5, rollPoints));
        when(item.isTypeArmor()).thenReturn(defense != null);
        when(item.getiDef()).thenReturn(defense == null ? 0 : defense);
        return item;
    }

    private static List<D2Item> sorted(Builtin sortedBy, D2Item... items) {
        List<D2Item> list = new ArrayList<>(Arrays.asList(items));
        Collections.reverse(list);
        list.sort((a, b) -> StashColumn.compareTieBreakers(sortedBy, a, b));
        return list;
    }

    @Test
    public void nameThenWorstRollThenLowestDefense() {
        D2Item shakoBadRoll = item("Harlequin Crest", -40, 141);
        D2Item shakoGoodRoll = item("Harlequin Crest", -5, 98);
        D2Item shakoGoodRollHighDef = item("Harlequin Crest", -5, 130);
        D2Item arachnid = item("Arachnid Mesh", 0, 200);
        assertEquals(Arrays.asList(arachnid, shakoBadRoll, shakoGoodRoll, shakoGoodRollHighDef),
                sorted(Builtin.REQ_LVL, shakoGoodRollHighDef, arachnid, shakoGoodRoll, shakoBadRoll));
    }

    @Test
    public void blankRollsAndNoDefenseGoLast() {
        D2Item perfect = item("Ring", 0, null);
        D2Item noRoll = item("Ring", null, null);
        D2Item belowPerfect = item("Ring", -1, null);
        assertEquals(Arrays.asList(belowPerfect, perfect, noRoll), sorted(Builtin.ILVL, noRoll, perfect, belowPerfect));

        D2Item zeroDefense = item("Shield", null, 0);
        D2Item noDefense = item("Shield", null, null);
        D2Item someDefense = item("Shield", null, 50);
        assertEquals(Arrays.asList(zeroDefense, someDefense, noDefense), sorted(Builtin.ILVL, noDefense, someDefense, zeroDefense));
        // a blank roll goes last even when its defense is lower
        assertTrue(StashColumn.compareTieBreakers(Builtin.ILVL, item("Shield", null, 1), item("Shield", -3, 500)) > 0);
    }

    @Test
    public void theClickedColumnIsSkipped() {
        // sorted by Name: names that differ don't matter here, the table already compared them
        assertTrue(StashColumn.compareTieBreakers(Builtin.NAME, item("B", -9, 10), item("A", -1, 10)) < 0);
        // sorted by Roll: equal names go straight to defense, even if the rolls differ
        assertTrue(StashColumn.compareTieBreakers(Builtin.ROLL_POINTS, item("A", 0, 10), item("A", -9, 20)) < 0);
        // sorted by Def: equal names and rolls are a tie, whatever the defense
        assertEquals(0, StashColumn.compareTieBreakers(Builtin.DEFENSE, item("A", -2, 10), item("A", -2, 900)));
        // sorted by Roll %: Roll is a different column, so it still breaks the tie
        assertTrue(StashColumn.compareTieBreakers(Builtin.ROLL, item("A", -9, 20), item("A", 0, 10)) < 0);
        // a property column has no built-in and gets every tie-breaker
        assertTrue(StashColumn.compareTieBreakers(null, item("A", -1, 20), item("A", -1, 10)) > 0);
    }

    @Test
    public void identicalItemsTie() {
        for (Builtin sortedBy : sortColumns()) {
            assertEquals(0, StashColumn.compareTieBreakers(sortedBy, item("A", -3, 10), item("A", -3, 10)));
            assertEquals(0, StashColumn.compareTieBreakers(sortedBy, item("A", null, null), item("A", null, null)));
        }
    }

    @Test
    public void tieBreakersAreAConsistentOrderOverEverySampleItem() {
        assertTrue(sampleItems.size() > 100, "only " + sampleItems.size() + " sample items");
        for (Builtin sortedBy : sortColumns()) {
            for (D2Item a : sampleItems) {
                assertEquals(0, StashColumn.compareTieBreakers(sortedBy, a, a), a.getItemName());
                for (D2Item b : sampleItems) {
                    int ab = Integer.signum(StashColumn.compareTieBreakers(sortedBy, a, b));
                    assertEquals(-ab, Integer.signum(StashColumn.compareTieBreakers(sortedBy, b, a)),
                            sortedBy + ": " + a.getItemName() + " / " + b.getItemName());
                }
            }
        }
    }

    @Test
    public void sampleItemsFollowNameThenRollThenDefense() {
        int sameName = 0;
        for (D2Item a : sampleItems) {
            for (D2Item b : sampleItems) {
                int result = Integer.signum(StashColumn.compareTieBreakers(Builtin.ILVL, a, b));
                int byName = Integer.signum(a.getName().compareTo(b.getName()));
                if (byName != 0) {
                    assertEquals(byName, result);
                    continue;
                }
                sameName++;
                int byRoll = Integer.signum(ItemRollPercent.compareWorstRollFirst(a, b));
                if (byRoll != 0) {
                    assertEquals(byRoll, result);
                    continue;
                }
                String defA = new StashColumn(Builtin.DEFENSE).getValue(a);
                String defB = new StashColumn(Builtin.DEFENSE).getValue(b);
                // the same order as clicking the Def column: lower first, blanks last
                assertEquals(Integer.signum(new StashColumn(Builtin.DEFENSE).compareValues(defA, defB)), result,
                        a.getItemName() + " / " + b.getItemName());
            }
        }
        assertTrue(sameName > sampleItems.size(), "the samples need items sharing a name");
    }

    @Test
    public void tableSortGivesTheSameOrderFromAnyStartingOrder() {
        // the stash table sorts by the clicked column, then these tie-breakers; a broken order would make the sort
        // throw or come out differently depending on the order the items were read in
        for (Builtin sortedBy : sortColumns()) {
            StashColumn column = sortedBy == null ? new StashColumn("", "defense") : new StashColumn(sortedBy);
            Comparator<D2Item> table = (a, b) -> {
                int result = column.getBuiltin() == Builtin.NAME ? a.getName().compareTo(b.getName())
                        : column.compareValues(column.getValue(a), column.getValue(b));
                return result != 0 ? result : StashColumn.compareTieBreakers(sortedBy, a, b);
            };
            List<D2Item> expected = new ArrayList<>(sampleItems);
            expected.sort(table);
            Random random = new Random(sortedBy == null ? 0 : sortedBy.ordinal());
            for (int run = 0; run < 10; run++) {
                List<D2Item> shuffled = new ArrayList<>(sampleItems);
                Collections.shuffle(shuffled, random);
                shuffled.sort(table);
                for (int i = 0; i < expected.size(); i++) {
                    assertEquals(0, table.compare(expected.get(i), shuffled.get(i)), sortedBy + " at row " + i);
                }
            }
            for (int i = 1; i < expected.size(); i++) {
                assertTrue(table.compare(expected.get(i - 1), expected.get(i)) <= 0, sortedBy + " at row " + i);
            }
        }
    }

    @Test
    public void samplesWrittenBackSortTheSame() throws Exception {
        // write each sample save back, as GoMule does on save, and check the written files sort exactly as before
        D2Project project = mock(D2Project.class);
        when(project.getBackup()).thenReturn(D2Project.BACKUP_NONE);
        List<File> copies = new ArrayList<>();
        for (File sample : SAMPLES) {
            File copy = tempDir.resolve(sample.getName()).toFile();
            Files.copy(sample.toPath(), copy.toPath());
            byte[] original = Files.readAllBytes(copy.toPath());
            String name = copy.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".d2s")) {
                new D2Character(ROW, copy.getAbsolutePath()).save(project);
            } else if (name.endsWith(".d2x")) {
                new D2StashWriter(ROW, copy.getAbsolutePath()).write(new D2StashReader().readStash(ROW, copy.getAbsolutePath()));
            } else {
                new D2SharedStashWriter(ROW, copy, original).write(new D2SharedStashReader().readStash(ROW, copy.getAbsolutePath()));
            }
            assertArrayEquals(original, Files.readAllBytes(copy.toPath()), sample.getName());
            copies.add(copy);
        }
        List<D2Item> readBack = readItems(copies.toArray(new File[0]));
        assertEquals(sampleItems.size(), readBack.size());
        for (int i = 0; i < sampleItems.size(); i++) {
            D2Item before = sampleItems.get(i);
            D2Item after = readBack.get(i);
            assertEquals(before.getName(), after.getName());
            assertEquals(ItemRollPercent.pointsFromMax(before), ItemRollPercent.pointsFromMax(after), before.getItemName());
            assertEquals(ItemRollPercent.of(before), ItemRollPercent.of(after), before.getItemName());
            assertEquals(before.isTypeArmor(), after.isTypeArmor(), before.getItemName());
            assertEquals(before.getiDef(), after.getiDef(), before.getItemName());
        }
        for (Builtin sortedBy : sortColumns()) {
            for (int i = 0; i < sampleItems.size(); i++) {
                for (int j = 0; j < sampleItems.size(); j++) {
                    assertEquals(StashColumn.compareTieBreakers(sortedBy, sampleItems.get(i), sampleItems.get(j)),
                            StashColumn.compareTieBreakers(sortedBy, readBack.get(i), readBack.get(j)));
                }
            }
        }
    }
}
