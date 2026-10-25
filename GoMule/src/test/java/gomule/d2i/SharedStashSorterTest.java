package gomule.d2i;

import gomule.d2x.D2Stash;
import gomule.d2x.D2StashReader;
import gomule.d2x.D2StashWriter;
import gomule.d2x.StashSortRule;
import gomule.item.D2Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static gomule.d2x.StashSortRule.Kind;
import static gomule.d2x.StashSortRule.Quality;
import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class SharedStashSorterTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void valuesWithinARuleAreOrdAndRulesAreAndd() throws Exception {
        D2SharedStash sharedStash = readFixture();
        D2Item toothrow = findItem(sharedStash, "Toothrow");
        D2Item cathansSeal = findItem(sharedStash, "Cathan's Seal");

        assertTrue(StashSortRule.kindIs(Arrays.asList(Kind.WEAPON, Kind.ARMOR)).matches(toothrow));
        assertFalse(StashSortRule.kindIs(Arrays.asList(Kind.WEAPON, Kind.CHARM)).matches(toothrow));
        assertTrue(StashSortRule.qualityIs(Arrays.asList(Quality.SET, Quality.UNIQUE)).matches(toothrow));
        assertTrue(StashSortRule.nameContains("Shako, toothROW , Ber").matches(toothrow));
        assertTrue(StashSortRule.nameContains("sharktooth").matches(toothrow));
        assertFalse(StashSortRule.nameContains("Shako, Ber").matches(toothrow));
        assertTrue(StashSortRule.kindIs(Arrays.asList(Kind.RING, Kind.AMULET)).matches(cathansSeal));
        assertFalse(StashSortRule.kindIs(Collections.singletonList(Kind.AMULET)).matches(cathansSeal));

        List<StashSortRule> uniqueArmorNamedToothrow = Arrays.asList(StashSortRule.kindIs(Collections.singletonList(Kind.ARMOR)),
                StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE)), StashSortRule.nameContains("Shako, Toothrow"));
        assertTrue(StashSortRule.matchesAll(uniqueArmorNamedToothrow, toothrow));
        assertFalse(StashSortRule.matchesAll(uniqueArmorNamedToothrow, cathansSeal));
        assertFalse(StashSortRule.matchesAll(Arrays.asList(StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE)),
                StashSortRule.nameContains("Shako")), toothrow));
        assertFalse(StashSortRule.matchesAll(Collections.emptyList(), toothrow));
    }

    @Test
    public void ruleSurvivesSerializing() {
        StashSortRule names = StashSortRule.nameContains("Ber, Jah;Lo, ,Ohm, Ber");
        assertEquals(Arrays.asList("Ber", "Jah;Lo", "Ohm"), names.getNames());
        assertEquals(names, StashSortRule.parse(names.serialize()));
        StashSortRule kinds = StashSortRule.kindIs(Arrays.asList(Kind.CHARM, Kind.POTION));
        assertEquals(kinds, StashSortRule.parse(kinds.serialize()));
        StashSortRule qualities = StashSortRule.qualityIs(Arrays.asList(Quality.UNIQUE, Quality.SET));
        assertEquals(qualities, StashSortRule.parse(qualities.serialize()));
        assertThrows(IllegalArgumentException.class, () -> StashSortRule.parse("KIND"));
        assertThrows(IllegalArgumentException.class, () -> StashSortRule.nameContains(" , "));
    }

    @Test
    public void sortingMovesSingleMatchesAndLeavesTheRestAfterWriteAndReadBack() throws Exception {
        File sharedStashFile = tempDir.resolve("ModernSharedStashSoftCoreV2.d2i").toFile();
        Files.copy(loadTestResources("sharedStashFiles", "modernGold.d2i").toPath(), sharedStashFile.toPath());
        String uniquesFile = tempDir.resolve("uniques.d2x").toString();
        String setsFile = tempDir.resolve("sets.d2x").toString();
        String jewelryFile = tempDir.resolve("jewelry.d2x").toString();

        D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
        List<Integer> paneGoldBefore = sharedStash.getPanes().stream().map(D2SharedStash.D2SharedStashPane::getGold).collect(Collectors.toList());
        int itemsBefore = sharedStash.getNrItems();

        Map<String, List<StashSortRule>> rules = new LinkedHashMap<>();
        rules.put(uniquesFile, Collections.singletonList(StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE))));
        rules.put(setsFile, Arrays.asList(StashSortRule.qualityIs(Collections.singletonList(Quality.SET)),
                StashSortRule.kindIs(Arrays.asList(Kind.ARMOR, Kind.RING, Kind.AMULET))));
        rules.put(jewelryFile, Collections.singletonList(StashSortRule.kindIs(Arrays.asList(Kind.RING, Kind.AMULET))));
        SharedStashSorter.Plan plan = SharedStashSorter.plan(sharedStash, rules);

        D2Item setRing = findItem(sharedStash, "Cathan's Seal");
        assertEquals(Arrays.asList(setsFile, jewelryFile), plan.getAmbiguous().get(setRing));
        assertFalse(plan.getMoves().containsKey(setRing));
        assertFalse(plan.getUnmatched().isEmpty());
        assertTrue(plan.getMoves().size() > 0);
        assertEquals(itemsBefore, plan.getMoves().size() + plan.getAmbiguous().size() + plan.getUnmatched().size());

        Map<String, List<byte[]>> expectedBytesByStash = new HashMap<>();
        plan.getMoves().forEach((item, stash) -> expectedBytesByStash.computeIfAbsent(stash, s -> new ArrayList<>()).add(item.get_bytes().clone()));
        List<String> expectedStayingNames = new ArrayList<>();
        plan.getAmbiguous().keySet().forEach(item -> expectedStayingNames.add(item.getItemName()));
        plan.getUnmatched().forEach(item -> expectedStayingNames.add(item.getItemName()));

        Map<String, D2Stash> stashes = new HashMap<>();
        for (String stashFile : rules.keySet()) stashes.put(stashFile, new D2StashReader().readStash(ROW, stashFile));
        Map<String, Integer> moved = SharedStashSorter.apply(sharedStash, plan, stashes::get);
        assertEquals(plan.getMoveCountsByStash(), new TreeMap<>(moved));
        assertTrue(sharedStash.isModified());

        new D2SharedStashWriter(ROW, sharedStashFile, Files.readAllBytes(sharedStashFile.toPath())).write(sharedStash);
        for (D2Stash stash : stashes.values()) new D2StashWriter(ROW, stash.getFilename()).write(stash);

        D2SharedStash sharedStashReadBack = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
        assertEquals(sortedNames(expectedStayingNames), sortedNames(names(sharedStashReadBack.getItemList())));
        for (int i = 0; i < paneGoldBefore.size(); i++) {
            assertEquals(paneGoldBefore.get(i), sharedStashReadBack.getPane(i).getGold());
            assertEquals(sharedStash.getPane(i).getItems().size(), sharedStashReadBack.getPane(i).getItems().size());
        }

        int movedTotal = 0;
        for (String stashFile : rules.keySet()) {
            D2Stash stashReadBack = new D2StashReader().readStash(ROW, stashFile);
            List<byte[]> expected = expectedBytesByStash.getOrDefault(stashFile, Collections.emptyList());
            assertEquals(expected.size(), stashReadBack.getNrItems(), stashFile);
            for (int i = 0; i < expected.size(); i++) {
                assertArrayEquals(expected.get(i), stashReadBack.getItemList().get(i).get_bytes(), stashFile + " item " + i);
                assertTrue(StashSortRule.matchesAll(rules.get(stashFile), stashReadBack.getItemList().get(i)));
            }
            movedTotal += stashReadBack.getNrItems();
        }
        assertEquals(itemsBefore, movedTotal + sharedStashReadBack.getNrItems());
    }

    @Test
    public void itemsStayWhenTheirStashCannotBeOpened() throws Exception {
        D2SharedStash sharedStash = readFixture();
        int itemsBefore = sharedStash.getNrItems();
        Map<String, List<StashSortRule>> rules = Collections.singletonMap("missing.d2x",
                Collections.singletonList(StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE))));
        SharedStashSorter.Plan plan = SharedStashSorter.plan(sharedStash, rules);
        assertTrue(plan.getMoves().size() > 0);

        assertTrue(SharedStashSorter.apply(sharedStash, plan, stash -> null).isEmpty());
        assertEquals(itemsBefore, sharedStash.getNrItems());
        assertFalse(sharedStash.isModified());
    }

    private D2SharedStash readFixture() throws Exception {
        File sharedStashFile = tempDir.resolve("fixture.d2i").toFile();
        Files.copy(loadTestResources("sharedStashFiles", "modernGold.d2i").toPath(), sharedStashFile.toPath());
        return new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
    }

    private static D2Item findItem(D2SharedStash sharedStash, String name) {
        return sharedStash.getItemList().stream()
                .filter(item -> item.getItemName().equals(name))
                .findFirst().orElseThrow(() -> new IllegalStateException("No " + name + " in fixture"));
    }

    private static List<String> names(List<D2Item> items) {
        return items.stream().map(D2Item::getItemName).collect(Collectors.toList());
    }

    private static List<String> sortedNames(List<String> names) {
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        return sorted;
    }
}
