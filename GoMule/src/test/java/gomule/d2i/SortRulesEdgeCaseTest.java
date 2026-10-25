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

import static gomule.d2x.StashSortRule.ItemType;
import static gomule.d2x.StashSortRule.Kind;
import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class SortRulesEdgeCaseTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void oldAndOddSavedKindsParseSafely() {
        assertEquals(Arrays.asList(Kind.RING, Kind.AMULET), StashSortRule.parse("KIND;RING_AMULET, RING, AMULET").getKinds());
        assertEquals(Arrays.asList(Kind.AMULET, Kind.RING), StashSortRule.parse("KIND;AMULET,RING_AMULET").getKinds());
        assertEquals(Collections.singletonList(Kind.POTION), StashSortRule.parse("KIND;GEM, POTION, RUNE").getKinds());
        for (String bad : Arrays.asList("KIND;GEM, RUNE, OTHER_MISC", "TYPE;", "TYPE;sword", "TYPE;SWORD, ", "TYPE;WEAPON", "KIND;SWORD",
                "KIND;ONE_HANDED", "TYPE;RING")) {
            assertThrows(IllegalArgumentException.class, () -> StashSortRule.parse(bad), bad);
        }
        // every kind and type survives saving
        StashSortRule kinds = StashSortRule.kindIs(Arrays.asList(Kind.values()));
        assertEquals(kinds, StashSortRule.parse(kinds.serialize()));
        StashSortRule types = StashSortRule.typeIs(Arrays.asList(ItemType.values()));
        assertEquals(types, StashSortRule.parse(types.serialize()));
        assertThrows(IllegalArgumentException.class, () -> StashSortRule.typeIs(Collections.emptyList()));
    }

    @Test
    public void handsAndTypesCombineAsExpectedOnTheSamples() throws Exception {
        List<D2Item> items = sampleItems();
        StashSortRule anyWeapon = StashSortRule.kindIs(Arrays.asList(Kind.WEAPON, Kind.TWO_HANDED_WEAPON));
        StashSortRule bothHands = StashSortRule.kindIs(Arrays.asList(Kind.ONE_HANDED_WEAPON, Kind.TWO_HANDED_WEAPON));
        List<StashSortRule> twoHandedSwords = Arrays.asList(StashSortRule.kindIs(Collections.singletonList(Kind.TWO_HANDED_WEAPON)),
                StashSortRule.typeIs(Collections.singletonList(ItemType.SWORD)));
        List<StashSortRule> armorThatIsASword = Arrays.asList(StashSortRule.kindIs(Collections.singletonList(Kind.ARMOR)),
                StashSortRule.typeIs(Collections.singletonList(ItemType.SWORD)));
        for (D2Item item : items) {
            assertEquals(item.isTypeWeapon(), anyWeapon.matches(item), item.getItemName());
            assertEquals(item.isTypeWeapon(), bothHands.matches(item), item.getItemName());
            assertEquals(item.isWeaponType(gomule.item.D2WeaponTypes.WEAP_SWOR) && item.isTwoHanded(), StashSortRule.matchesAll(twoHandedSwords, item));
            assertFalse(StashSortRule.matchesAll(armorThatIsASword, item), "contradicting sections match nothing");
            assertFalse(item.isPotion() && (item.isTypeWeapon() || item.isTypeArmor()));
            assertFalse(!item.isTypeWeapon() && item.isTwoHanded());
        }
    }

    @Test
    public void anItemMatchingAKindStashAndATypeStashStaysInTheSharedStash() throws Exception {
        D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i").getAbsolutePath());
        Map<String, List<StashSortRule>> rules = new LinkedHashMap<>();
        rules.put("two-handed.d2x", Collections.singletonList(StashSortRule.kindIs(Collections.singletonList(Kind.TWO_HANDED_WEAPON))));
        rules.put("swords.d2x", Collections.singletonList(StashSortRule.typeIs(Collections.singletonList(ItemType.SWORD))));
        SharedStashSorter.Plan plan = SharedStashSorter.plan(sharedStash, rules);
        for (D2Item item : sharedStash.getItemList()) {
            boolean sword = item.isWeaponType(gomule.item.D2WeaponTypes.WEAP_SWOR);
            boolean twoHanded = item.isTwoHanded();
            if (sword && twoHanded) assertEquals(new ArrayList<>(rules.keySet()), plan.getAmbiguous().get(item), item.getItemName());
            else if (sword) assertEquals("swords.d2x", plan.getMoves().get(item), item.getItemName());
            else if (twoHanded) assertEquals("two-handed.d2x", plan.getMoves().get(item), item.getItemName());
            else assertTrue(plan.getUnmatched().contains(item), item.getItemName());
        }
        assertFalse(plan.getMoves().isEmpty());
    }

    @Test
    public void potionsAndRingsSortWriteReadBackAndRevert() throws Exception {
        File sharedStashFile = tempDir.resolve("ModernSharedStashSoftCoreV2.d2i").toFile();
        Files.copy(loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i").toPath(), sharedStashFile.toPath());
        D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
        List<Set<String>> before = layout(sharedStash);
        int goldBefore = sharedStash.getTotalGold();
        D2Stash potions = new D2StashReader().readStash(ROW, tempDir.resolve("potions.d2x").toString());
        D2Stash rings = new D2StashReader().readStash(ROW, tempDir.resolve("rings.d2x").toString());
        D2Stash amulets = new D2StashReader().readStash(ROW, tempDir.resolve("amulets.d2x").toString());
        Map<String, D2Stash> stashes = new LinkedHashMap<>();
        Map<String, List<StashSortRule>> rules = new LinkedHashMap<>();
        for (Object[] entry : new Object[][]{{potions, Kind.POTION}, {rings, Kind.RING}, {amulets, Kind.AMULET}}) {
            D2Stash stash = (D2Stash) entry[0];
            stashes.put(stash.getFilename(), stash);
            rules.put(stash.getFilename(), Collections.singletonList(StashSortRule.kindIs(Collections.singletonList((Kind) entry[1]))));
        }
        Map<String, List<String>> expected = new HashMap<>();
        for (String stash : rules.keySet()) {
            expected.put(stash, sharedStash.getItemList().stream().filter(item -> StashSortRule.matchesAll(rules.get(stash), item))
                    .map(D2Item::getItemName).sorted().collect(Collectors.toList()));
        }
        assertFalse(expected.get(potions.getFilename()).isEmpty(), "the sample holds a potion");
        assertFalse(expected.get(rings.getFilename()).isEmpty(), "the sample holds a ring");

        List<SharedStashSorter.MovedItem> moved = new ArrayList<>();
        SharedStashSorter.apply(sharedStash, SharedStashSorter.plan(sharedStash, rules), stashes::get, moved);
        new D2SharedStashWriter(ROW, sharedStashFile, Files.readAllBytes(sharedStashFile.toPath())).write(sharedStash);
        for (D2Stash stash : stashes.values()) new D2StashWriter(ROW, stash.getFilename()).write(stash);
        for (String stash : rules.keySet()) {
            assertEquals(expected.get(stash), new D2StashReader().readStash(ROW, stash).getItemList().stream().map(D2Item::getItemName).sorted()
                    .collect(Collectors.toList()), stash);
        }

        assertTrue(SharedStashSorter.revert(sharedStash, moved).isEmpty());
        new D2SharedStashWriter(ROW, sharedStashFile, Files.readAllBytes(sharedStashFile.toPath())).write(sharedStash);
        // items go back on their own page and spot (the order within a page may differ, which the game ignores)
        D2SharedStash readBack = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
        assertEquals(before, layout(readBack));
        assertEquals(goldBefore, readBack.getTotalGold());
    }

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

    private static List<D2Item> sampleItems() throws Exception {
        List<D2Item> items = new ArrayList<>();
        items.addAll(new D2SharedStashReader().readStash(ROW, loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i").getAbsolutePath()).getItemList());
        items.addAll(new D2SharedStashReader().readStash(ROW, loadTestResources("sharedStashFiles", "modernGold.d2i").getAbsolutePath()).getItemList());
        items.addAll(new D2StashReader().readStash(ROW, loadTestResources("userSamples", "Set - Aldur.d2x").getAbsolutePath()).getItemList());
        for (String character : new String[]{"tester_row_d2r.d2s", "complexChar_afterRowConversion.d2s"}) {
            for (Object item : new gomule.d2s.D2Character(ROW, loadTestResources("charFiles", "ROW", character).getAbsolutePath()).getItemList()) {
                items.add((D2Item) item);
            }
        }
        return items;
    }
}
