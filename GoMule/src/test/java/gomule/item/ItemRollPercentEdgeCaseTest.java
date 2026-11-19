package gomule.item;

import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.d2i.D2SharedStashWriter;
import gomule.d2s.D2Character;
import gomule.d2x.D2StashReader;
import gomule.d2x.StashColumn;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Edge cases for the Roll % and Roll columns and the worst-roll-first tie-break, checked on every item in the
 * sample saves.
 */
public class ItemRollPercentEdgeCaseTest {

    private static List<D2Item> sampleItems;

    @TempDir
    Path tempDir;

    @BeforeAll
    public static void setup() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        sampleItems = readItems(Objects.requireNonNull(loadTestResources("userSamples").listFiles()));
    }

    private static List<D2Item> readItems(File... files) throws Exception {
        List<D2Item> items = new ArrayList<>();
        File[] sorted = files.clone();
        Arrays.sort(sorted);
        for (File file : sorted) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".d2s")) {
                items.addAll(new D2Character(ROW, file.getAbsolutePath()).getItemList());
            } else if (name.endsWith(".d2x")) {
                items.addAll(new D2StashReader().readStash(ROW, file.getAbsolutePath()).getItemList());
            } else if (name.endsWith(".d2i")) {
                D2SharedStash stash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath());
                for (int i = 0; i < stash.getPanes().size(); i++) items.addAll(stash.getPane(i).getItems());
            }
        }
        return items;
    }

    @Test
    public void worstRollFirstIsAConsistentOrderOverEverySampleItem() {
        // a comparator that breaks its contract makes the stash table's sort throw or come out differently each time
        for (D2Item a : sampleItems) {
            assertEquals(0, ItemRollPercent.compareWorstRollFirst(a, a), a.getItemName());
            for (D2Item b : sampleItems) {
                int ab = Integer.signum(ItemRollPercent.compareWorstRollFirst(a, b));
                assertEquals(-ab, Integer.signum(ItemRollPercent.compareWorstRollFirst(b, a)), a.getItemName() + " / " + b.getItemName());
                if (ab != 0) continue;
                for (D2Item c : sampleItems) {
                    assertEquals(Integer.signum(ItemRollPercent.compareWorstRollFirst(a, c)),
                            Integer.signum(ItemRollPercent.compareWorstRollFirst(b, c)), "ties must sort alike");
                }
            }
        }
    }

    @Test
    public void worstRollFirstGivesTheSameOrderWhateverOrderTheItemsStartIn() {
        List<D2Item> expected = new ArrayList<>(sampleItems);
        expected.sort(ItemRollPercent::compareWorstRollFirst);
        Random random = new Random(42);
        for (int i = 0; i < 20; i++) {
            List<D2Item> shuffled = new ArrayList<>(sampleItems);
            Collections.shuffle(shuffled, random);
            shuffled.sort(ItemRollPercent::compareWorstRollFirst);
            List<Integer> expectedPoints = new ArrayList<>();
            List<Integer> actualPoints = new ArrayList<>();
            for (int j = 0; j < expected.size(); j++) {
                expectedPoints.add(ItemRollPercent.pointsFromMax(expected.get(j)));
                actualPoints.add(ItemRollPercent.pointsFromMax(shuffled.get(j)));
            }
            assertEquals(expectedPoints, actualPoints);
        }
        // worst first, perfect rolls (0) after every negative, and items with nothing rolled at the very end
        boolean seenBlank = false;
        Integer previous = null;
        for (D2Item item : expected) {
            Integer points = ItemRollPercent.pointsFromMax(item);
            if (points == null) {
                seenBlank = true;
                continue;
            }
            assertFalse(seenBlank, item.getItemName() + " after an item with nothing rolled");
            if (previous != null) assertTrue(previous <= points, previous + " before " + points);
            previous = points;
        }
        assertTrue(seenBlank, "the samples have items with nothing rolled");
    }

    @Test
    public void rollAndRollPercentAgreeOnWhichItemsHaveARoll() {
        int defenseOnly = 0;
        for (D2Item item : sampleItems) {
            Integer points = ItemRollPercent.pointsFromMax(item);
            Double roll = ItemRollPercent.of(item);
            assertEquals(points == null ? "" : points.toString(), ItemRollPercent.pointsFromMaxDisplay(item));
            assertEquals(roll == null ? "" : Math.round(roll * 100) + "%", ItemRollPercent.display(item));
            if (points != null) {
                assertNotNull(roll, item.getItemName());
            } else if (roll != null) {
                // only the base defense varies, which Roll leaves out
                assertTrue(item.isTypeArmor(), item.getItemName() + " has a Roll % but no Roll and isn't armor");
                defenseOnly++;
            }
        }
        assertTrue(defenseOnly > 0, "the samples have armor whose only roll is its defense");
    }

    @Test
    public void aPerfectRollIsZeroNotBlank() {
        D2Item perfect = sampleItems.stream().filter(item -> Integer.valueOf(0).equals(ItemRollPercent.pointsFromMax(item)))
                .findFirst().orElseThrow(() -> new AssertionError("no item with a perfect roll in the samples"));
        assertEquals("0", ItemRollPercent.pointsFromMaxDisplay(perfect));
        StashColumn points = new StashColumn(StashColumn.Builtin.ROLL_POINTS);
        assertEquals("0", points.getValue(perfect));
        assertTrue(points.compareValues("0", "") < 0, "0 sorts before a blank");
        assertTrue(points.compareValues("-1", "0") < 0, "-1 sorts before 0");
    }

    @Test
    public void onlyUniqueSetAndRunewordItemsHaveARollTemplate() {
        for (D2Item item : sampleItems) {
            if (item.getRollTemplate() == null) {
                assertTrue(item.getRolledProps().isEmpty(), item.getItemName());
                assertNull(ItemRollPercent.pointsFromMax(item), item.getItemName());
            } else {
                assertTrue(item.isUnique() || item.isSet() || item.isRuneWord(), item.getItemName());
            }
        }
    }

    @Test
    public void rolledPropsCannotBeChangedFromOutside() {
        D2Item item = sampleItems.stream().filter(i -> !i.getRolledProps().isEmpty()).findFirst().orElseThrow(AssertionError::new);
        assertThrows(UnsupportedOperationException.class, () -> item.getRolledProps().clear());
    }

    @Test
    public void askingTwiceGivesTheSameAnswer() {
        for (D2Item item : sampleItems) {
            assertEquals(ItemRollPercent.of(item), ItemRollPercent.of(item), item.getItemName());
            assertEquals(ItemRollPercent.pointsFromMax(item), ItemRollPercent.pointsFromMax(item), item.getItemName());
        }
    }

    @Test
    public void rollsReadBackTheSameAfterTheSampleSavesAreWrittenBack() throws Exception {
        File character = copyOfSample("Javazon.d2s");
        D2Character javazon = new D2Character(ROW, character.getAbsolutePath());
        D2Project project = mock(D2Project.class);
        when(project.getBackup()).thenReturn(D2Project.BACKUP_NONE);
        javazon.save(project);

        File sharedStash = copyOfSample("ModernSharedStashSoftCoreV2.d2i");
        byte[] original = Files.readAllBytes(sharedStash.toPath());
        D2SharedStash stash = new D2SharedStashReader().readStash(ROW, sharedStash.getAbsolutePath());
        new D2SharedStashWriter(ROW, sharedStash, original).write(stash);

        File atmaStash = copyOfSample("Set - Aldur.d2x");

        List<D2Item> before = readItems(loadTestResources("userSamples").listFiles());
        List<D2Item> after = readItems(character, sharedStash, atmaStash);
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            D2Item b = before.get(i);
            D2Item a = after.get(i);
            assertEquals(b.getItemName(), a.getItemName());
            assertEquals(ItemRollPercent.of(b), ItemRollPercent.of(a), b.getItemName());
            assertEquals(ItemRollPercent.pointsFromMax(b), ItemRollPercent.pointsFromMax(a), b.getItemName());
            assertEquals(0, ItemRollPercent.compareWorstRollFirst(b, a), b.getItemName());
        }
    }

    private File copyOfSample(String name) throws Exception {
        File copy = tempDir.resolve(name).toFile();
        Files.copy(loadTestResources("userSamples", name).toPath(), copy.toPath());
        return copy;
    }

    @Test
    public void templatesReadTheSameUnderATurkishLocale() {
        // Turkish lowercases the I in "SetItems" and "UniqueItems" to a dotless i
        Locale previous = Locale.getDefault();
        try {
            for (D2Item item : sampleItems) {
                if (item.getRollTemplate() == null) continue;
                Locale.setDefault(Locale.ROOT);
                List<String> expected = describe(ItemRollPercent.lines(item.getRollTemplate()));
                Locale.setDefault(new Locale("tr", "TR"));
                assertEquals(expected, describe(ItemRollPercent.lines(item.getRollTemplate())), item.getItemName());
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static List<String> describe(List<ItemRollPercent.TemplateLine> lines) {
        List<String> described = new ArrayList<>();
        for (ItemRollPercent.TemplateLine line : lines) described.add(line.code + " " + line.param + " " + line.min + "-" + line.max);
        return described;
    }

    @Test
    public void rolledValueAddsUpRepeatsAndMatchesParamsExactly() {
        D2Prop fireResist1 = new D2Prop(39, new int[]{10}, 0);
        D2Prop fireResist2 = new D2Prop(39, new int[]{15}, 0);
        D2Prop sorceressFireTab = new D2Prop(188, new int[]{24, 2}, 0);
        D2Prop sorceressColdTab = new D2Prop(188, new int[]{26, 3}, 0);
        List<D2Prop> props = Arrays.asList(fireResist1, fireResist2, sorceressFireTab, sorceressColdTab);
        assertEquals(Integer.valueOf(25), ItemRollPercent.rolledValue(props, "39:-1"));
        assertEquals(Integer.valueOf(2), ItemRollPercent.rolledValue(props, "188:24"));
        assertEquals(Integer.valueOf(3), ItemRollPercent.rolledValue(props, "188:26"));
        assertNull(ItemRollPercent.rolledValue(props, "188:25"));
        assertNull(ItemRollPercent.rolledValue(props, "40:-1"));
        assertNull(ItemRollPercent.rolledValue(Collections.emptyList(), "39:-1"));
    }

    @Test
    public void damageIsFoundUnderItsOneHandTwoHandOrThrowStat() {
        // one-hand min damage 21, two-hand 23, throw 159: a +min damage line may be stored as any of them
        List<D2Prop> twoHand = Collections.singletonList(new D2Prop(23, new int[]{7}, 0));
        assertEquals(Integer.valueOf(7), ItemRollPercent.rolledValue(twoHand, "21:-1"));
        assertEquals(Integer.valueOf(7), ItemRollPercent.rolledValue(twoHand, "159:-1"));
        // the first alias that is present wins, they are not added together
        List<D2Prop> both = Arrays.asList(new D2Prop(21, new int[]{4}, 0), new D2Prop(23, new int[]{7}, 0));
        assertEquals(Integer.valueOf(4), ItemRollPercent.rolledValue(both, "23:-1"));
        // max damage is a separate group
        assertNull(ItemRollPercent.rolledValue(twoHand, "22:-1"));
    }

    @Test
    public void statKeysForUnknownMultiStatAndNamedSkillLines() {
        assertTrue(ItemRollPercent.statKeys(new ItemRollPercent.TemplateLine("no-such-property", "", 1, 5)).isEmpty());
        // all-stats sets four stats from one line
        assertEquals(4, ItemRollPercent.statKeys(new ItemRollPercent.TemplateLine("all-stats", "", 10, 20)).size());
        // a skill param can be a number or a skill name
        assertEquals(ItemRollPercent.statKeys(new ItemRollPercent.TemplateLine("oskill", "Critical Strike", 1, 6)),
                ItemRollPercent.statKeys(new ItemRollPercent.TemplateLine("oskill", "9", 1, 6)));
    }
}
