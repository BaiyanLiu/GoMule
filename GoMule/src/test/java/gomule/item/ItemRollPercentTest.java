package gomule.item;

import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.d2s.D2Character;
import gomule.d2x.D2Stash;
import gomule.d2x.D2StashReader;
import gomule.d2x.D2StashWriter;
import gomule.d2x.StashColumn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class ItemRollPercentTest {

    private static List<D2Item> sampleItems;

    @TempDir
    Path tempDir;

    @BeforeAll
    public static void setup() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        sampleItems = new ArrayList<>();
        for (File file : Objects.requireNonNull(loadTestResources("userSamples").listFiles())) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".d2s")) {
                sampleItems.addAll(new D2Character(ROW, file.getAbsolutePath()).getItemList());
            } else if (name.endsWith(".d2x")) {
                sampleItems.addAll(new D2StashReader().readStash(ROW, file.getAbsolutePath()).getItemList());
            } else if (name.endsWith(".d2i")) {
                D2SharedStash stash = new D2SharedStashReader().readStash(ROW, file.getAbsolutePath());
                for (int i = 0; i < stash.getPanes().size(); i++) sampleItems.addAll(stash.getPane(i).getItems());
            }
        }
    }

    private static D2Item sample(String name) {
        return sampleItems.stream().filter(item -> name.equals(item.getItemName())).findFirst()
                .orElseThrow(() -> new AssertionError("no " + name + " in the samples"));
    }

    @Test
    public void averagesEachVaryingLineOnceIncludingMultiStatLines() {
        // all-stats 10-20 rolled 17, res-all 10-20 rolled 20, addxp 5-10 rolled 8; +1 skills is fixed
        assertEquals((0.7 + 1.0 + 0.6) / 3, ItemRollPercent.of(sample("Annihilus")), 1e-9);
        assertEquals("77%", ItemRollPercent.display(sample("Annihilus")));
    }

    @Test
    public void minimumRollsShowZero() {
        // dmg% 40-50 rolled 40; everything else fixed; weapons have no defense
        assertEquals("0%", ItemRollPercent.display(sample("Goreshovel")));
    }

    @Test
    public void armorAddsItsBaseDefenseRoll() {
        // ac% 150-200: 156, red-mag 15-20: 18, red-dmg 15-20: 18, Wire Fleece defense 375-481(+1): 482
        assertEquals((6 / 50.0 + 0.6 + 0.6 + 1.0) / 4, ItemRollPercent.of(sample("The Gladiator's Bane")), 1e-9);
    }

    @Test
    public void etherealArmorComparesAgainstTheEtherealDefenseRange() {
        D2Item toothrow = sampleItems.stream().filter(item -> "Toothrow".equals(item.getItemName()) && item.isEthereal())
                .findFirst().orElseThrow(AssertionError::new);
        // thorns 20-40: 22, ac 40-60: 45, ac% 160-220: 196, Sharktooth 242-259 x 1.5 = 363-388: 388
        assertEquals((0.1 + 0.25 + 0.6 + 1.0) / 4, ItemRollPercent.of(toothrow), 1e-9);
    }

    @Test
    public void runewordsUseRunesTxtIncludingSkillAndAuraLines() {
        // dmg% 200-260: 210, att% 180-250: 215, Critical Strike 1-6: 4, Meditation aura 12-17: 13
        assertEquals((10 / 60.0 + 35 / 70.0 + 3 / 5.0 + 1 / 5.0) / 4, ItemRollPercent.of(sample("Insight")), 1e-9);
    }

    @Test
    public void blankWhenNothingRolls() {
        assertNull(ItemRollPercent.of(sample("Shadowfang")));
        assertEquals("", ItemRollPercent.display(sample("Shadowfang")));
        D2Item rare = sampleItems.stream().filter(D2Item::isRare).findFirst().orElseThrow(AssertionError::new);
        assertEquals("", ItemRollPercent.display(rare));
        for (D2Item item : sampleItems) {
            if (!item.isUnique() && !item.isSet() && !item.isRuneWord()) assertEquals("", ItemRollPercent.display(item), item.getItemName());
        }
    }

    @Test
    public void everySampleRollIsBetweenZeroAndAHundredPercent() {
        int rolled = 0;
        for (D2Item item : sampleItems) {
            Double roll = ItemRollPercent.of(item);
            if (roll == null) continue;
            rolled++;
            assertTrue(roll >= 0 && roll <= 1, item.getItemName() + ": " + roll);
            assertTrue(item.isUnique() || item.isSet() || item.isRuneWord(), item.getItemName());
        }
        assertTrue(rolled > 50, "only " + rolled + " items with a roll");
    }

    @Test
    public void skillTabParamsAreConvertedToTheSavedNumbering() {
        // Druid elemental skills are tab 17 in the txt files and saved as class 5 * 8 + tab 2
        assertEquals(Arrays.asList("188:42"), ItemRollPercent.statKeys(new ItemRollPercent.TemplateLine("skilltab", "17", 1, 3)));
        assertTrue(ItemRollPercent.statKeys(new ItemRollPercent.TemplateLine("hit-skill", "54", 5, 7)).isEmpty());
        assertTrue(ItemRollPercent.statKeys(new ItemRollPercent.TemplateLine("dmg-norm", "", 12, 45)).isEmpty());
    }

    @Test
    public void rollSurvivesAtmaStashWriteAndReadBack() throws Exception {
        String atmaStashFileName = tempDir.resolve("rolls.d2x").toString();
        D2Stash atmaStash = new D2StashReader().readStash(ROW, atmaStashFileName);
        List<D2Item> items = Arrays.asList(sample("Annihilus"), sample("Insight"), sample("The Gladiator's Bane"));
        items.forEach(atmaStash::addItem);
        new D2StashWriter(ROW, atmaStashFileName).write(atmaStash);
        List<D2Item> readBack = new D2StashReader().readStash(ROW, atmaStashFileName).getItemList();
        assertEquals(items.size(), readBack.size());
        for (int i = 0; i < items.size(); i++) {
            assertEquals(ItemRollPercent.display(items.get(i)), ItemRollPercent.display(readBack.get(i)));
        }
    }

    @Test
    public void rollColumnSortsByNumberWithBlanksLast() {
        StashColumn roll = new StashColumn(StashColumn.Builtin.ROLL);
        assertTrue(roll.isNumeric());
        assertEquals(roll, StashColumn.parse(roll.serialize()));
        List<String> values = new ArrayList<>(Arrays.asList("", "100%", "9%", "73%", "0%"));
        values.sort(roll::compareValues);
        assertEquals(Arrays.asList("0%", "9%", "73%", "100%", ""), values);
        assertEquals("77%", roll.getValue(sample("Annihilus")));
    }

    @Test
    public void pointsFromMaxSumsRolledMinusMaxOverVaryingLines() {
        // all-stats 17 of 20 (-3, once, though it sets four stats), res-all 20 of 20 (0), addxp 8 of 10 (-2)
        assertEquals(Integer.valueOf(-5), ItemRollPercent.pointsFromMax(sample("Annihilus")));
        assertEquals("-5", ItemRollPercent.pointsFromMaxDisplay(sample("Annihilus")));
        // dmg% 210 of 260, att% 215 of 250, Critical Strike 4 of 6, Meditation 13 of 17
        assertEquals(Integer.valueOf(-50 - 35 - 2 - 4), ItemRollPercent.pointsFromMax(sample("Insight")));
    }

    @Test
    public void pointsFromMaxLeavesOutDefense() {
        // red-mag 18 of 20, red-dmg 18 of 20; ac% 156 of 200 and the Wire Fleece's base defense are not counted
        assertEquals(Integer.valueOf(-2 - 2), ItemRollPercent.pointsFromMax(sample("The Gladiator's Bane")));
        // Roll % still counts them
        assertNotNull(ItemRollPercent.of(sample("The Gladiator's Bane")));
    }

    @Test
    public void perLevelPropertiesCountInNeitherRollNorRollPercent() {
        int perLevel = 0;
        for (int row = 0; row < D2TxtFile.PROPS.getRowSize(); row++) {
            D2TxtFileItemProperties property = D2TxtFile.PROPS.getRow(row);
            boolean isPerLevel = false;
            for (int x = 1; x <= 7; x++) {
                if (property.get("stat" + x).toLowerCase(Locale.ROOT).contains("perlevel")) isPerLevel = true;
            }
            if (!isPerLevel) continue;
            perLevel++;
            // even with a range in the template, a "based on character level" line is never judged
            assertEquals(Arrays.asList(), ItemRollPercent.statKeys(
                    new ItemRollPercent.TemplateLine(property.get("code"), "", 1, 10)), property.get("code"));
        }
        assertTrue(perLevel > 30, "only " + perLevel + " per-level properties");
    }

    @Test
    public void pointsFromMaxBlankWhenNothingRollsAndNeverPositive() {
        assertNull(ItemRollPercent.pointsFromMax(sample("Shadowfang")));
        assertEquals("", ItemRollPercent.pointsFromMaxDisplay(sample("Shadowfang")));
        int counted = 0;
        for (D2Item item : sampleItems) {
            Integer points = ItemRollPercent.pointsFromMax(item);
            if (points == null) continue;
            counted++;
            assertTrue(points <= 0, item.getItemName() + ": " + points);
            assertNotNull(ItemRollPercent.of(item), item.getItemName());
        }
        assertTrue(counted > 50, "only " + counted + " items with points");
    }

    @Test
    public void rollPointsColumnSortsNegativeNumbersWithBlanksLast() {
        StashColumn points = new StashColumn(StashColumn.Builtin.ROLL_POINTS);
        assertTrue(points.isNumeric());
        assertEquals(points, StashColumn.parse(points.serialize()));
        List<String> values = new ArrayList<>(Arrays.asList("", "0", "-9", "-120", "-5"));
        values.sort(points::compareValues);
        assertEquals(Arrays.asList("-120", "-9", "-5", "0", ""), values);
        assertEquals("-5", points.getValue(sample("Annihilus")));
        assertEquals("Roll", points.getLabel());
        assertEquals("Roll (points short of perfect rolls, e.g. -9)", StashColumn.Builtin.ROLL_POINTS.toString());
    }

    @Test
    public void pointsSurviveAtmaStashWriteAndReadBack() throws Exception {
        String atmaStashFileName = tempDir.resolve("points.d2x").toString();
        D2Stash atmaStash = new D2StashReader().readStash(ROW, atmaStashFileName);
        List<D2Item> items = Arrays.asList(sample("Annihilus"), sample("Insight"), sample("The Gladiator's Bane"));
        items.forEach(atmaStash::addItem);
        new D2StashWriter(ROW, atmaStashFileName).write(atmaStash);
        List<D2Item> readBack = new D2StashReader().readStash(ROW, atmaStashFileName).getItemList();
        for (int i = 0; i < items.size(); i++) {
            assertEquals(ItemRollPercent.pointsFromMax(items.get(i)), ItemRollPercent.pointsFromMax(readBack.get(i)));
        }
    }

    @Test
    public void tieBreakPutsWorstRollFirstAndUnrolledLast() {
        D2Item annihilus = sample("Annihilus"); // -5
        D2Item insight = sample("Insight"); // -91
        D2Item shadowfang = sample("Shadowfang"); // nothing rolls
        List<D2Item> items = new ArrayList<>(Arrays.asList(shadowfang, insight, annihilus));
        items.sort(ItemRollPercent::compareWorstRollFirst);
        assertEquals(Arrays.asList(insight, annihilus, shadowfang), items);
        assertEquals(0, ItemRollPercent.compareWorstRollFirst(shadowfang, shadowfang));
        assertEquals(0, ItemRollPercent.compareWorstRollFirst(annihilus, annihilus));
    }
}
