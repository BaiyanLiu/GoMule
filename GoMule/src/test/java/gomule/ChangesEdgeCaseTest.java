package gomule;

import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStash.D2SharedStashPane;
import gomule.d2i.D2SharedStashReader;
import gomule.d2i.D2SharedStashWriter;
import gomule.d2i.SharedStashSorter;
import gomule.d2x.D2Stash;
import gomule.d2x.D2StashReader;
import gomule.d2x.D2StashWriter;
import gomule.d2x.StashColumn;
import gomule.d2x.StashSortRule;
import gomule.d2x.StashSortRule.Kind;
import gomule.d2x.StashSortRule.Quality;
import gomule.gui.D2ItemList;
import gomule.item.D2Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge cases for the shared stash gold, drag to ATMA stash, sort into ATMA stashes and stash column changes,
 * checked against the user's sample saves by writing them and reading them back.
 */
public class ChangesEdgeCaseTest {

    private static final int MAX_GOLD_PER_PANE = 2_500_000;

    @TempDir
    Path tempDir;

    private File sharedStashFile;
    private File aldurFile;

    @BeforeEach
    public void setup() throws Exception {
        D2TxtFile.constructTxtFiles("./d2111");
        sharedStashFile = copySample("ModernSharedStashSoftCoreV2.d2i");
        aldurFile = copySample("Set - Aldur.d2x");
    }

    // ---- gold --------------------------------------------------------------------------------------------

    @Test
    public void goldAtCapZeroAndOnEveryPageSurvivesWriteAndReadBack() throws Exception {
        byte[] original = Files.readAllBytes(sharedStashFile.toPath());
        D2SharedStash stash = readShared();
        assertEquals(2_444_290, stash.getTotalGold());
        int[] gold = {MAX_GOLD_PER_PANE, 0, 1, MAX_GOLD_PER_PANE, 123_456};
        for (int i = 0; i < stash.getPanes().size(); i++) {
            stash.replacePane(i, D2SharedStashPane.fromItems(stash.getPane(i).getItems(), gold[i]));
        }
        writeShared(stash, original);
        D2SharedStash readBack = readShared();
        for (int i = 0; i < gold.length; i++) assertEquals(gold[i], readBack.getPane(i).getGold(), "pane " + i);
        assertEquals(2 * MAX_GOLD_PER_PANE + 1 + 123_456, readBack.getTotalGold());
        assertSameItems(stash, readBack);
    }

    @Test
    public void totalGoldOfFullPagesDoesNotOverflow() {
        List<D2SharedStashPane> panes = new ArrayList<>();
        for (int i = 0; i < 5; i++) panes.add(D2SharedStashPane.fromItems(Collections.emptyList(), MAX_GOLD_PER_PANE));
        D2SharedStash stash = new D2SharedStash(ROW, "x.d2i", panes, new byte[0]);
        assertEquals(5 * MAX_GOLD_PER_PANE, stash.getTotalGold());
    }

    @Test
    public void goldTransferClampEdgeCases() throws Exception {
        java.lang.reflect.Method clamp = Class.forName("gomule.gui.sharedStash.SharedStashGoldTransferPanel")
                .getDeclaredMethod("clampTransferAmount", int.class, int.class, int.class, int.class);
        clamp.setAccessible(true);
        Function<int[], Integer> c = a -> {
            try {
                return (Integer) clamp.invoke(null, a[0], a[1], a[2], a[3]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        // into a page: amount, bank, page gold, cap
        assertEquals(55_710, c.apply(new int[]{1_000_000, 5_000_000, 2_444_290, MAX_GOLD_PER_PANE}));
        assertEquals(0, c.apply(new int[]{10_000, 5_000_000, MAX_GOLD_PER_PANE, MAX_GOLD_PER_PANE}));
        assertEquals(0, c.apply(new int[]{10_000, 5_000_000, MAX_GOLD_PER_PANE + 1, MAX_GOLD_PER_PANE}));
        assertEquals(0, c.apply(new int[]{10_000, 0, 0, MAX_GOLD_PER_PANE}));
        assertEquals(0, c.apply(new int[]{0, 10_000, 0, MAX_GOLD_PER_PANE}));
        assertEquals(0, c.apply(new int[]{-5, 10_000, 0, MAX_GOLD_PER_PANE}));
        assertEquals(0, c.apply(new int[]{Integer.MIN_VALUE, 10_000, 0, MAX_GOLD_PER_PANE}));
        assertEquals(MAX_GOLD_PER_PANE, c.apply(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, 0, MAX_GOLD_PER_PANE}));
        // out of a page into the bank, which has no cap
        assertEquals(2_444_290, c.apply(new int[]{Integer.MAX_VALUE, 2_444_290, 0, Integer.MAX_VALUE}));
        assertEquals(1, c.apply(new int[]{10, 2_444_290, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}));
        assertEquals(0, c.apply(new int[]{10, 2_444_290, Integer.MAX_VALUE, Integer.MAX_VALUE}));
        assertEquals(0, c.apply(new int[]{10, -100, 0, Integer.MAX_VALUE}));
    }

    // ---- pages: empty, full, items not fitting -----------------------------------------------------------

    @Test
    public void emptyingAPageAndFillingTheEmptyPageSurviveWriteAndReadBack() throws Exception {
        byte[] original = Files.readAllBytes(sharedStashFile.toPath());
        D2SharedStash stash = readShared();
        assertTrue(stash.getPane(3).getItems().isEmpty());
        List<D2Item> page0 = new ArrayList<>(stash.getPane(0).getItems());
        stash.replacePane(0, D2SharedStashPane.fromItems(Collections.emptyList(), stash.getPane(0).getGold()));

        // lay page 0's items out on the empty page 3 at the same cells
        D2SharedStashPane target = stash.getPane(3);
        List<D2Item> placed = new ArrayList<>();
        for (D2Item item : page0) {
            assertTrue(target.canDropItem(item.get_col(), item.get_row(), item));
            target = target.addItem(item.get_col(), item.get_row(), item);
            placed.add(item);
        }
        for (int col = 0; col < 10; col++) {
            for (int row = 0; row < 10; row++) assertNotNull(target.getItemCovering(col, row), "page 0 of the sample is exactly full");
        }
        stash.replacePane(3, target);
        writeShared(stash, original);

        D2SharedStash readBack = readShared();
        assertTrue(readBack.getPane(0).getItems().isEmpty());
        assertEquals(2_444_290, readBack.getPane(0).getGold());
        assertEquals(itemBytes(placed), itemBytes(readBack.getPane(3).getItems()));
        assertEquals(stash.getNrItems(), readBack.getNrItems());
    }

    @Test
    public void fullPageAcceptsNothingAndItemsThatHangOffThePageAreRefused() throws Exception {
        D2SharedStash stash = readShared();
        D2Item ring = stash.getPane(0).getItems().stream().filter(it -> it.getItemName().equals("Cathan's Seal")).findFirst().get();
        D2Item lance = stash.getPane(0).getItems().stream().filter(it -> it.getItemName().equals("Spire of Honor")).findFirst().get();
        D2SharedStashPane empty = D2SharedStashPane.fromItems(Collections.emptyList(), 0);

        assertFalse(empty.canDropItem(9, 0, lance), "2 wide at the last column");
        assertFalse(empty.canDropItem(0, 7, lance), "4 tall at row 7");
        assertTrue(empty.canDropItem(8, 6, lance));
        assertFalse(empty.canDropItem(-1, 0, ring));
        assertFalse(empty.canDropItem(10, 0, ring));
        assertFalse(empty.canDropItem(0, 10, ring));

        // page 0 of the sample is exactly full (21 items, 100 cells)
        D2SharedStashPane full = stash.getPane(0);
        for (int col = 0; col < 10; col++) {
            for (int row = 0; row < 10; row++) {
                assertNotNull(full.getItemCovering(col, row));
                assertFalse(full.canDropItem(col, row, ring));
            }
        }
        // page 3 is empty: fill it with copies of the 1x1 ring until nothing more fits
        int added = 0;
        D2SharedStashPane filled = stash.getPane(3);
        while (true) {
            D2SharedStashPane next = addAnywhere(filled, new D2Item("copy", new gomule.util.D2BitReader(ring.get_bytes().clone()), 75));
            if (next == filled) break;
            filled = next;
            added++;
        }
        assertEquals(100, added);
        byte[] original = Files.readAllBytes(sharedStashFile.toPath());
        stash.replacePane(3, filled);
        writeShared(stash, original);
        D2SharedStash readBack = readShared();
        assertEquals(100, readBack.getPane(3).getItems().size());
        assertTrue(readBack.getPane(3).getItems().stream().allMatch(it -> it.getItemName().equals("Cathan's Seal")));
        assertSameItems(stash, readBack);
    }

    // ---- drag to ATMA stash -----------------------------------------------------------------------------

    @Test
    public void draggingEveryItemIntoTheAldurStashSurvivesWriteAndReadBack() throws Exception {
        byte[] original = Files.readAllBytes(sharedStashFile.toPath());
        D2SharedStash shared = readShared();
        D2Stash aldur = readAldur();
        List<byte[]> expected = new ArrayList<>(itemBytesList(aldur.getItemList()));
        for (int i = 0; i < shared.getPanes().size(); i++) {
            D2SharedStashPane pane = shared.getPane(i);
            for (D2Item item : new ArrayList<>(pane.getItems())) {
                pane = pane.removeItem(item);
                aldur.addItem(item);
                expected.add(item.get_bytes().clone());
            }
            shared.replacePane(i, pane);
        }
        writeShared(shared, original);
        new D2StashWriter(ROW, aldurFile.getAbsolutePath()).write(aldur);

        D2SharedStash sharedBack = readShared();
        assertEquals(0, sharedBack.getNrItems());
        assertEquals(2_444_290, sharedBack.getTotalGold());
        D2Stash aldurBack = readAldur();
        assertEquals(37 + 69, aldurBack.getNrItems());
        assertEquals(asStrings(expected), itemBytes(aldurBack.getItemList()));
    }

    @Test
    public void draggingIntoAStashFileThatDoesNotExistYetCreatesIt() throws Exception {
        byte[] original = Files.readAllBytes(sharedStashFile.toPath());
        D2SharedStash shared = readShared();
        String newStash = tempDir.resolve("new mule.d2x").toString();
        D2Stash stash = new D2StashReader().readStash(ROW, newStash);
        assertTrue(stash.isModified());
        D2Item amulet = shared.getPane(4).getItems().get(4);
        assertEquals("Amulet of Thawing", amulet.getItemName());
        shared.replacePane(4, shared.getPane(4).removeItem(amulet));
        stash.addItem(amulet);
        writeShared(shared, original);
        new D2StashWriter(ROW, newStash).write(stash);
        assertEquals("Amulet of Thawing", new D2StashReader().readStash(ROW, newStash).getItemList().get(0).getItemName());
        assertEquals(7, readShared().getPane(4).getItems().size());
    }

    // ---- sort into ATMA stashes -------------------------------------------------------------------------

    @Test
    public void sortingIntoTheAldurStashWithAnotherSetStashLeavesSharedSetItemsWhereTheyAre() throws Exception {
        byte[] original = Files.readAllBytes(sharedStashFile.toPath());
        D2SharedStash shared = readShared();
        String setsFile = tempDir.resolve("sets.d2x").toString();
        String uniquesFile = tempDir.resolve("uniques.d2x").toString();
        Map<String, List<StashSortRule>> rules = new LinkedHashMap<>();
        rules.put(aldurFile.getAbsolutePath(), Arrays.asList(StashSortRule.qualityIs(Collections.singletonList(Quality.SET)), StashSortRule.nameContains("Aldur")));
        rules.put(setsFile, Collections.singletonList(StashSortRule.qualityIs(Collections.singletonList(Quality.SET))));
        rules.put(uniquesFile, Arrays.asList(StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE)),
                StashSortRule.kindIs(Arrays.asList(Kind.WEAPON, Kind.ARMOR))));

        SharedStashSorter.Plan plan = SharedStashSorter.plan(shared, rules);
        D2Item deception = plan.getAmbiguous().keySet().stream().filter(it -> it.getItemName().equals("Aldur's Deception")).findFirst().orElse(null);
        assertNotNull(deception, "Aldur's Deception matches both the Aldur stash and the sets stash");
        assertEquals(Arrays.asList(aldurFile.getAbsolutePath(), setsFile), plan.getAmbiguous().get(deception));
        long sharedUniques = shared.getItemList().stream().filter(D2Item::isUnique).count();
        long sharedSets = shared.getItemList().stream().filter(D2Item::isSet).count();
        assertEquals(sharedUniques, (long) plan.getMoveCountsByStash().get(uniquesFile));
        assertEquals(sharedSets - 1, (long) plan.getMoveCountsByStash().get(setsFile));
        assertNull(plan.getMoveCountsByStash().get(aldurFile.getAbsolutePath()));

        Map<String, D2ItemList> opened = new HashMap<>();
        Map<String, Integer> moved = SharedStashSorter.apply(shared, plan, name -> opened.computeIfAbsent(name, n -> new D2StashReader().readStash(ROW, n)));
        assertEquals(plan.getMoves().size(), moved.values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(shared.isModified());
        writeShared(shared, original);
        opened.values().forEach(list -> new D2StashWriter(ROW, list.getFilename()).write((D2Stash) list));

        D2SharedStash sharedBack = readShared();
        assertEquals(shared.getNrItems(), sharedBack.getNrItems());
        assertTrue(sharedBack.getItemList().stream().anyMatch(it -> it.getItemName().equals("Aldur's Deception")));
        assertEquals(0, sharedBack.getItemList().stream().filter(D2Item::isUnique).count());
        // both identical Toothrows and both Arcanna's Deathwands moved, not just one of each
        D2Stash uniquesBack = new D2StashReader().readStash(ROW, uniquesFile);
        assertEquals(2, uniquesBack.getItemList().stream().filter(it -> it.getItemName().equals("Toothrow")).count());
        D2Stash setsBack = new D2StashReader().readStash(ROW, setsFile);
        assertEquals(2, setsBack.getItemList().stream().filter(it -> it.getItemName().equals("Arcanna's Deathwand")).count());
        assertEquals(37, readAldur().getNrItems(), "Aldur stash file untouched");
    }

    @Test
    public void stashThatCannotBeOpenedKeepsItsItemsInTheSharedStash() throws Exception {
        D2SharedStash shared = readShared();
        String uniquesFile = tempDir.resolve("uniques.d2x").toString();
        Map<String, List<StashSortRule>> rules = Collections.singletonMap(uniquesFile,
                Collections.singletonList(StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE))));
        SharedStashSorter.Plan plan = SharedStashSorter.plan(shared, rules);
        int before = shared.getNrItems();
        List<String> lookups = new ArrayList<>();
        Map<String, Integer> moved = SharedStashSorter.apply(shared, plan, name -> {
            lookups.add(name);
            return null;
        });
        assertTrue(plan.getMoves().size() > 1);
        assertEquals(Collections.singletonList(uniquesFile), lookups, "a stash that can't be opened is tried (and reported) once");
        assertTrue(moved.isEmpty());
        assertEquals(before, shared.getNrItems());
        assertFalse(shared.isModified());
    }

    @Test
    public void emptySharedStashAndStashesWithoutRulesMoveNothing() throws Exception {
        List<D2SharedStashPane> panes = new ArrayList<>();
        for (int i = 0; i < 5; i++) panes.add(D2SharedStashPane.fromItems(Collections.emptyList(), 0));
        D2SharedStash empty = new D2SharedStash(ROW, "x.d2i", panes, new byte[0]);
        Map<String, List<StashSortRule>> rules = Collections.singletonMap("a.d2x",
                Collections.singletonList(StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE))));
        assertTrue(SharedStashSorter.plan(empty, rules).getMoves().isEmpty());

        D2SharedStash shared = readShared();
        SharedStashSorter.Plan plan = SharedStashSorter.plan(shared, Collections.singletonMap("a.d2x", Collections.emptyList()));
        assertTrue(plan.getMoves().isEmpty());
        assertEquals(shared.getNrItems(), plan.getUnmatched().size());
        assertTrue(SharedStashSorter.plan(shared, Collections.emptyMap()).getMoves().isEmpty());
    }

    @Test
    public void malformedStoredRulesAreRejected() {
        for (String bad : Arrays.asList("", "KIND", "KIND;", "KIND;WEAPON, ", "KIND;SWORD", "QUALITY;", "NAME;", "NAME; , ",
                "BOGUS;x", "kind;WEAPON", "WEAPON;UNIQUE;Shako")) {
            assertThrows(IllegalArgumentException.class, () -> StashSortRule.parse(bad), bad);
        }
        assertEquals(StashSortRule.kindIs(Collections.singletonList(Kind.CHARM)), StashSortRule.parse("KIND; CHARM ,CHARM"));
        assertEquals(Collections.singletonList("Ber;Jah"), StashSortRule.parse("NAME;Ber;Jah").getNames());
    }

    @Test
    public void projectWithAMalformedRuleKeepsTheStashFromTakingMoreThanIntended() throws Exception {
        Properties properties = new Properties();
        properties.put("sortrules.0.stash", aldurFile.getAbsolutePath());
        properties.put("sortrules.0.rule.0", "QUALITY;SET");
        properties.put("sortrules.0.rule.1", "KIND;NOT_A_KIND");
        properties.put("sortrules.1.stash", "other.d2x");
        properties.put("sortrules.1.rule.0", "QUALITY;UNIQUE");
        properties.put("sortrules.2.stash", "old-format.d2x");
        properties.put("sortrules.2.rule.0", "WEAPON;UNIQUE;Shako");
        Map<String, List<StashSortRule>> loaded = StashSortRule.load(throughFile(properties));
        assertEquals(Collections.emptyList(), loaded.get(aldurFile.getAbsolutePath()),
                "keeping only QUALITY;SET would make the stash take every set item");
        assertEquals(Collections.emptyList(), loaded.get("old-format.d2x"));
        assertEquals(Collections.singletonList(StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE))), loaded.get("other.d2x"));
        // and a sort with these rules moves only uniques, to the stash whose rules all loaded
        Map<String, List<StashSortRule>> withRules = new LinkedHashMap<>();
        loaded.forEach((stash, rules) -> {
            if (!rules.isEmpty()) withRules.put(stash, rules);
        });
        SharedStashSorter.Plan plan = SharedStashSorter.plan(readShared(), withRules);
        assertTrue(plan.getMoves().keySet().stream().allMatch(D2Item::isUnique));
        assertEquals(Collections.singleton("other.d2x"), new HashSet<>(plan.getMoves().values()));
    }

    @Test
    public void rulesAndColumnsSurviveProjectPropertiesFile() throws Exception {
        Map<String, List<StashSortRule>> rules = new LinkedHashMap<>();
        rules.put(aldurFile.getAbsolutePath(), Arrays.asList(StashSortRule.kindIs(Arrays.asList(Kind.ARMOR, Kind.WEAPON)),
                StashSortRule.qualityIs(Collections.singletonList(Quality.SET)), StashSortRule.nameContains("Aldur's, Bul=Kathos, a:b #c")));
        rules.put("C:\\Diablo II\\Stashes\\no rules.d2x", Collections.emptyList());
        Map<String, List<StashColumn>> columns = new LinkedHashMap<>();
        columns.put(aldurFile.getAbsolutePath(), Arrays.asList(new StashColumn(StashColumn.Builtin.NAME), new StashColumn("FCR", "Faster Cast Rate"),
                new StashColumn("", "=:#!\\ odd"), new StashColumn(StashColumn.Builtin.SET)));
        columns.put("all", Arrays.asList(new StashColumn(StashColumn.Builtin.SOURCE), new StashColumn(StashColumn.Builtin.NAME)));
        Properties properties = new Properties();
        StashSortRule.save(rules, properties);
        StashColumn.save(columns, properties);
        Properties readBack = throughFile(properties);
        Map<String, List<StashSortRule>> rulesBack = StashSortRule.load(readBack);
        assertEquals(rules.get(aldurFile.getAbsolutePath()), rulesBack.get(aldurFile.getAbsolutePath()));
        assertFalse(rulesBack.containsKey("C:\\Diablo II\\Stashes\\no rules.d2x"), "stashes without rules aren't written");
        assertEquals(columns, StashColumn.load(readBack));
    }

    @Test
    public void malformedColumnSettingsFallBackToDefaults() {
        Properties properties = new Properties();
        properties.put("stashcolumns.0.stash", "a.d2x");
        properties.put("stashcolumns.0.column.0", "NOT_A_COLUMN");
        properties.put("stashcolumns.0.column.1", "prop:");
        properties.put("stashcolumns.0.column.2", "prop:\t");
        properties.put("stashcolumns.1.stash", "b.d2x");
        properties.put("stashcolumns.1.column.0", " ilvl ");
        properties.put("stashcolumns.1.column.1", "ILVL");
        properties.put("stashcolumns.1.column.3", "NAME");
        properties.put("stashcolumns.3.stash", "unreachable.d2x");
        properties.put("stashcolumns.3.column.0", "NAME");
        Map<String, List<StashColumn>> layouts = StashColumn.load(properties);
        assertFalse(layouts.containsKey("a.d2x"), "a stash whose columns are all bad keeps the default columns");
        assertEquals(Collections.singletonList(new StashColumn(StashColumn.Builtin.ILVL)), layouts.get("b.d2x"));
        assertFalse(layouts.containsKey("unreachable.d2x"));
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private static D2SharedStashPane addAnywhere(D2SharedStashPane pane, D2Item item) {
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 10; col++) {
                if (pane.canDropItem(col, row, item)) return pane.addItem(col, row, item);
            }
        }
        return pane;
    }

    private Properties throughFile(Properties properties) throws Exception {
        File file = tempDir.resolve("project.properties").toFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            properties.store(out, null);
        }
        Properties readBack = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            readBack.load(in);
        }
        return readBack;
    }

    private File copySample(String name) throws Exception {
        File copy = tempDir.resolve(name).toFile();
        Files.copy(loadTestResources("userSamples", name).toPath(), copy.toPath());
        return copy;
    }

    private D2SharedStash readShared() throws Exception {
        return new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
    }

    private D2Stash readAldur() {
        return new D2StashReader().readStash(ROW, aldurFile.getAbsolutePath());
    }

    private void writeShared(D2SharedStash stash, byte[] original) {
        new D2SharedStashWriter(ROW, sharedStashFile, original).write(stash);
    }

    private static void assertSameContent(D2SharedStash expected, D2SharedStash actual) {
        assertSameItems(expected, actual);
        for (int i = 0; i < expected.getPanes().size(); i++) assertEquals(expected.getPane(i).getGold(), actual.getPane(i).getGold());
    }

    private static void assertSameItems(D2SharedStash expected, D2SharedStash actual) {
        assertEquals(expected.getPanes().size(), actual.getPanes().size());
        for (int i = 0; i < expected.getPanes().size(); i++) {
            assertEquals(itemBytes(expected.getPane(i).getItems()), itemBytes(actual.getPane(i).getItems()), "pane " + i);
        }
    }

    private static List<byte[]> itemBytesList(List<D2Item> items) {
        return items.stream().map(it -> it.get_bytes().clone()).collect(Collectors.toList());
    }

    private static List<String> itemBytes(List<D2Item> items) {
        return asStrings(itemBytesList(items));
    }

    private static List<String> asStrings(List<byte[]> bytes) {
        return bytes.stream().map(Arrays::toString).collect(Collectors.toList());
    }
}
