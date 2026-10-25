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
import static gomule.d2x.StashSortRule.Quality;
import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class StashSortTypeRuleTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void everyWeaponAndArmorInTheSamplesHasExactlyOneType() throws Exception {
        List<D2Item> items = sampleItems();
        int weaponsAndArmor = 0;
        for (D2Item item : items) {
            List<ItemType> types = Arrays.stream(ItemType.values()).filter(type -> StashSortRule.typeIs(Collections.singletonList(type)).matches(item))
                    .collect(Collectors.toList());
            if (item.isTypeWeapon() || item.isTypeArmor()) {
                weaponsAndArmor++;
                assertEquals(1, types.size(), item.getItemName() + " (" + item.getBaseItemName() + "): " + types);
                assertEquals(item.isTypeArmor(), types.get(0).isArmor(), item.getItemName());
            } else {
                assertTrue(types.isEmpty(), item.getItemName() + ": " + types);
            }
        }
        assertTrue(weaponsAndArmor > 50, "samples should hold plenty of weapons and armor: " + weaponsAndArmor);
    }

    @Test
    public void typesMatchTheRightItems() throws Exception {
        Map<String, D2Item> byName = new HashMap<>();
        for (D2Item item : sampleItems()) byName.putIfAbsent(item.getItemName(), item);
        assertType(byName, "Toothrow", ItemType.BODY_ARMOR);
        assertType(byName, "Lightsabre", ItemType.SWORD);
        assertType(byName, "Bloodletter", ItemType.SWORD);
        assertType(byName, "Angelic Halo", null);
    }

    private static void assertType(Map<String, D2Item> byName, String name, ItemType expected) {
        D2Item item = byName.get(name);
        assertNotNull(item, name + " not in the samples");
        for (ItemType type : ItemType.values()) {
            assertEquals(type == expected, StashSortRule.typeIs(Collections.singletonList(type)).matches(item), name + " as " + type);
        }
    }

    @Test
    public void typeRulesSurviveSavingAndOldRulesStillLoad() {
        StashSortRule swordsOrGloves = StashSortRule.typeIs(Arrays.asList(ItemType.SWORD, ItemType.GLOVES, ItemType.SWORD));
        assertEquals(Arrays.asList(ItemType.SWORD, ItemType.GLOVES), swordsOrGloves.getTypes());
        assertEquals("TYPE;SWORD, GLOVES", swordsOrGloves.serialize());
        assertEquals(swordsOrGloves, StashSortRule.parse(swordsOrGloves.serialize()));
        assertEquals("Type is Sword or Gloves", swordsOrGloves.toString());

        Properties saved = new Properties();
        saved.setProperty("sortrules.0.stash", "old.d2x");
        saved.setProperty("sortrules.0.rule.0", "KIND;WEAPON, ARMOR");
        saved.setProperty("sortrules.0.rule.1", "QUALITY;UNIQUE");
        saved.setProperty("sortrules.1.stash", "typo.d2x");
        saved.setProperty("sortrules.1.rule.0", "TYPE;SWORD, SPORK");
        Map<String, List<StashSortRule>> loaded = StashSortRule.load(saved);
        assertEquals(2, loaded.get("old.d2x").size());
        assertTrue(loaded.get("typo.d2x").isEmpty(), "an unknown type keeps the stash from taking anything");

        Map<String, List<StashSortRule>> rules = new LinkedHashMap<>();
        rules.put("new.d2x", Arrays.asList(swordsOrGloves, StashSortRule.qualityIs(Collections.singletonList(Quality.UNIQUE))));
        Properties properties = new Properties();
        StashSortRule.save(rules, properties);
        assertEquals(rules, StashSortRule.load(properties));
    }

    @Test
    public void sortingSwordsAndGlovesIntoOneStashSurvivesWriteAndReadBack() throws Exception {
        File sharedStashFile = tempDir.resolve("ModernSharedStashSoftCoreV2.d2i").toFile();
        Files.copy(loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i").toPath(), sharedStashFile.toPath());
        D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
        int itemsBefore = sharedStash.getNrItems();
        int goldBefore = sharedStash.getTotalGold();
        StashSortRule swordsOrGloves = StashSortRule.typeIs(Arrays.asList(ItemType.SWORD, ItemType.GLOVES));
        List<String> expected = sharedStash.getItemList().stream().filter(swordsOrGloves::matches).map(D2Item::getItemName).sorted()
                .collect(Collectors.toList());
        assertTrue(expected.size() >= 2, expected.toString());

        D2Stash stash = new D2StashReader().readStash(ROW, tempDir.resolve("swords and gloves.d2x").toString());
        Map<String, List<StashSortRule>> rules = Collections.singletonMap(stash.getFilename(), Collections.singletonList(swordsOrGloves));
        SharedStashSorter.apply(sharedStash, SharedStashSorter.plan(sharedStash, rules), name -> stash);
        new D2SharedStashWriter(ROW, sharedStashFile, Files.readAllBytes(sharedStashFile.toPath())).write(sharedStash);
        new D2StashWriter(ROW, stash.getFilename()).write(stash);

        D2Stash stashBack = new D2StashReader().readStash(ROW, stash.getFilename());
        assertEquals(expected, stashBack.getItemList().stream().map(D2Item::getItemName).sorted().collect(Collectors.toList()));
        D2SharedStash sharedBack = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
        assertEquals(itemsBefore - expected.size(), sharedBack.getNrItems());
        assertTrue(sharedBack.getItemList().stream().noneMatch(swordsOrGloves::matches));
        assertEquals(goldBefore, sharedBack.getTotalGold());
    }

    @Test
    public void ringsAndAmuletsAreSeparateKindsAndOldRingAmuletRulesTakeBoth() throws Exception {
        StashSortRule rings = StashSortRule.kindIs(Collections.singletonList(StashSortRule.Kind.RING));
        StashSortRule amulets = StashSortRule.kindIs(Collections.singletonList(StashSortRule.Kind.AMULET));
        int ringCount = 0;
        int amuletCount = 0;
        for (D2Item item : sampleItems()) {
            boolean ring = item.isBodyLocation(gomule.item.D2BodyLocations.BODY_LRIN);
            boolean amulet = item.isBodyLocation(gomule.item.D2BodyLocations.BODY_NECK);
            assertEquals(ring, rings.matches(item), item.getItemName());
            assertEquals(amulet, amulets.matches(item), item.getItemName());
            if (ring) ringCount++;
            if (amulet) amuletCount++;
        }
        assertTrue(ringCount > 0 && amuletCount > 0, "samples hold " + ringCount + " rings and " + amuletCount + " amulets");

        StashSortRule old = StashSortRule.parse("KIND;RING_AMULET, CHARM");
        assertEquals(Arrays.asList(StashSortRule.Kind.RING, StashSortRule.Kind.AMULET, StashSortRule.Kind.CHARM), old.getKinds());
        assertEquals("KIND;RING, AMULET, CHARM", old.serialize());
        assertEquals("Kind is Ring or Amulet or Charm", old.toString());
    }

    @Test
    public void weaponsSplitIntoOneAndTwoHanded() throws Exception {
        StashSortRule oneHanded = StashSortRule.kindIs(Collections.singletonList(StashSortRule.Kind.ONE_HANDED_WEAPON));
        StashSortRule twoHanded = StashSortRule.kindIs(Collections.singletonList(StashSortRule.Kind.TWO_HANDED_WEAPON));
        int one = 0;
        int two = 0;
        for (D2Item item : sampleItems()) {
            if (item.isTypeWeapon()) {
                assertNotEquals(oneHanded.matches(item), twoHanded.matches(item), item.getItemName() + " is exactly one of the two");
                if (twoHanded.matches(item)) two++;
                else one++;
                if (StashSortRule.typeIs(Arrays.asList(ItemType.BOW, ItemType.CROSSBOW, ItemType.STAFF, ItemType.POLEARM, ItemType.AMAZON_BOW)).matches(item)) {
                    assertTrue(twoHanded.matches(item), item.getItemName());
                }
                if (StashSortRule.typeIs(Arrays.asList(ItemType.DAGGER, ItemType.WAND, ItemType.SCEPTER, ItemType.CLAW, ItemType.JAVELIN,
                        ItemType.THROWING_AXE, ItemType.THROWING_KNIFE, ItemType.ORB)).matches(item)) {
                    assertTrue(oneHanded.matches(item), item.getItemName());
                }
            } else {
                assertFalse(oneHanded.matches(item) || twoHanded.matches(item), item.getItemName());
            }
        }
        assertTrue(one > 5 && two > 5, one + " one-handed and " + two + " two-handed weapons in the samples");
        Map<String, D2Item> byName = new HashMap<>();
        for (D2Item item : sampleItems()) byName.putIfAbsent(item.getItemName(), item);
        // Lightsabre is a phase blade, a one-handed sword
        assertTrue(oneHanded.matches(byName.get("Lightsabre")));
    }

    @Test
    public void savedRuneAndOtherMiscKindsAreDroppedWithoutWideningTheStash() {
        assertEquals(Collections.singletonList(StashSortRule.Kind.JEWEL), StashSortRule.parse("KIND;RUNE, GEM, JEWEL, OTHER_MISC").getKinds());
        assertThrows(IllegalArgumentException.class, () -> StashSortRule.parse("KIND;GEM"));
        assertThrows(IllegalArgumentException.class, () -> StashSortRule.parse("KIND;RUNE"));
        Properties saved = new Properties();
        saved.setProperty("sortrules.0.stash", "runes.d2x");
        saved.setProperty("sortrules.0.rule.0", "KIND;RUNE");
        saved.setProperty("sortrules.0.rule.1", "QUALITY;NORMAL");
        assertTrue(StashSortRule.load(saved).get("runes.d2x").isEmpty(), "a stash whose only kind was dropped takes nothing");
    }

    @Test
    public void potionsAreAKind() throws Exception {
        StashSortRule potions = StashSortRule.kindIs(Collections.singletonList(StashSortRule.Kind.POTION));
        int count = 0;
        for (File character : new File[]{loadTestResources("charFiles/ROW", "tester_row_d2r.d2s"), loadTestResources("charFiles/ROW", "complexChar_afterRowConversion.d2s"),
                loadTestResources("userSamples", "Javazon.d2s")}) {
            for (Object listed : new gomule.d2s.D2Character(ROW, character.getAbsolutePath()).getItemList()) {
                D2Item item = (D2Item) listed;
                boolean potion = item.isTypeMisc() && item.getItemName().contains("Potion") && !item.getItemName().contains("Exploding")
                        && !item.getItemName().contains("Gas");
                assertEquals(potion, potions.matches(item), item.getItemName());
                if (potion) count++;
            }
        }
        for (D2Item item : sampleItems()) {
            if (potions.matches(item)) count++;
            assertEquals(item.getItemName().endsWith(" Potion"), potions.matches(item), item.getItemName());
        }
        assertTrue(count > 0, "the character samples should hold potions");
    }

    private static List<D2Item> sampleItems() throws Exception {
        List<D2Item> items = new ArrayList<>();
        items.addAll(new D2SharedStashReader().readStash(ROW, loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i").getAbsolutePath()).getItemList());
        items.addAll(new D2SharedStashReader().readStash(ROW, loadTestResources("sharedStashFiles", "modernGold.d2i").getAbsolutePath()).getItemList());
        items.addAll(new D2StashReader().readStash(ROW, loadTestResources("userSamples", "Set - Aldur.d2x").getAbsolutePath()).getItemList());
        return items;
    }
}
