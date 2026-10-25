package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.d2i.SharedStashSorter;
import gomule.d2x.StashSortRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.util.*;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class SharedStashSortActionTest {

    @BeforeEach
    public void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void previewNamesEveryStayingItemWithItsReason() throws Exception {
        D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, loadTestResources("sharedStashFiles", "modernGold.d2i").getAbsolutePath());
        Map<String, List<StashSortRule>> rules = new LinkedHashMap<>();
        rules.put("C:/mules/Sets.d2x", Collections.singletonList(StashSortRule.qualityIs(Collections.singletonList(StashSortRule.Quality.SET))));
        rules.put("C:/mules/Jewelry.d2x", Collections.singletonList(StashSortRule.kindIs(Arrays.asList(StashSortRule.Kind.RING, StashSortRule.Kind.AMULET))));
        SharedStashSorter.Plan plan = SharedStashSorter.plan(sharedStash, rules);
        assertFalse(plan.getUnmatched().isEmpty());
        assertFalse(plan.getAmbiguous().isEmpty());

        String preview = SharedStashSortAction.describe(plan);

        String noMatch = section(preview, "Staying, matches no stash (" + plan.getUnmatched().size() + "):");
        Map<String, Integer> unmatchedCounts = new LinkedHashMap<>();
        plan.getUnmatched().forEach(item -> unmatchedCounts.merge(item.getItemName(), 1, Integer::sum));
        unmatchedCounts.forEach((name, count) -> assertTrue(noMatch.contains("    " + name + (count > 1 ? " (x" + count + ")" : "") + "\n"), name));
        assertEquals(unmatchedCounts.size(), noMatch.split("\n").length - 1);
        String several = section(preview, "Staying, matches more than one stash (" + plan.getAmbiguous().size() + "):");
        assertTrue(several.contains("    Cathan's Seal: Sets.d2x, Jewelry.d2x\n"), several);
        plan.getMoveCountsByStash().forEach((stash, count) -> assertTrue(preview.contains("    " + count + " to " + stash.substring(9) + "\n")));
    }

    private static String section(String preview, String heading) {
        int start = preview.indexOf(heading);
        assertTrue(start >= 0, "missing " + heading + " in\n" + preview);
        int end = preview.indexOf("\n\n", start);
        return preview.substring(start, end < 0 ? preview.length() : end + 1);
    }
}
