package gomule.d2x;

import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.item.D2Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Rules work out how to test each value once, when made; these check that a rule read back from project.properties
 * tests items exactly like the rule it was saved from, for every value.
 */
public class StashSortRuleMatchingTest {

    private static List<D2Item> items;

    @BeforeAll
    public static void setup() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i").getAbsolutePath());
        items = sharedStash.getItemList();
        assertFalse(items.isEmpty());
    }

    @Test
    public void everyValueMatchesTheSameItemsAfterASaveAndLoad() {
        List<StashSortRule> rules = new ArrayList<>();
        Arrays.stream(StashSortRule.Kind.values()).forEach(kind -> rules.add(StashSortRule.kindIs(Collections.singletonList(kind))));
        Arrays.stream(StashSortRule.ItemType.values()).forEach(type -> rules.add(StashSortRule.typeIs(Collections.singletonList(type))));
        Arrays.stream(StashSortRule.Quality.values()).forEach(quality -> rules.add(StashSortRule.qualityIs(Collections.singletonList(quality))));
        rules.add(StashSortRule.nameContains("TOOTHROW, arcanna"));
        int matches = 0;
        for (StashSortRule rule : rules) {
            StashSortRule readBack = StashSortRule.parse(rule.serialize());
            assertEquals(rule, readBack);
            for (D2Item item : items) {
                assertEquals(rule.matches(item), readBack.matches(item), rule + " on " + item.getItemName());
                if (rule.matches(item)) matches++;
            }
        }
        assertTrue(matches > items.size(), "the sample should match many of the rules");
    }

    @Test
    public void aRuleMatchesWhenAnyOfItsValuesDoes() {
        StashSortRule unique = StashSortRule.qualityIs(Collections.singletonList(StashSortRule.Quality.UNIQUE));
        StashSortRule set = StashSortRule.qualityIs(Collections.singletonList(StashSortRule.Quality.SET));
        StashSortRule uniqueOrSet = StashSortRule.qualityIs(Arrays.asList(StashSortRule.Quality.UNIQUE, StashSortRule.Quality.SET));
        for (D2Item item : items) {
            assertEquals(unique.matches(item) || set.matches(item), uniqueOrSet.matches(item), item.getItemName());
        }
    }

    @Test
    public void namesKeepTheirCaseWhenSavedButMatchIgnoringIt() {
        StashSortRule rule = StashSortRule.nameContains("ToothRow");
        assertEquals("NAME;ToothRow", rule.serialize());
        assertEquals(Collections.singletonList("ToothRow"), rule.getNames());
        assertTrue(items.stream().anyMatch(rule::matches));
    }
}
