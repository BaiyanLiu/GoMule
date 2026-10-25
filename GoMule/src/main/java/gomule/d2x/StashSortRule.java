package gomule.d2x;

import gomule.item.D2BodyLocations;
import gomule.item.D2Item;
import gomule.item.D2WeaponTypes;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One condition an item must meet to belong in an ATMA (.d2x) stash: its kind, weapon or armor type, quality or name.
 * The rule's values are OR'd (e.g. unique or set), and a stash takes an item only when all of its rules match.
 */
public class StashSortRule {

    public enum Field {
        KIND("Kind is"),
        TYPE("Type is"),
        QUALITY("Quality is"),
        NAME("Name contains");

        private final String display;

        Field(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public enum Kind {
        WEAPON("Weapon", D2Item::isTypeWeapon),
        ONE_HANDED_WEAPON("1-handed weapon", item -> item.isTypeWeapon() && !item.isTwoHanded()),
        TWO_HANDED_WEAPON("2-handed weapon", D2Item::isTwoHanded),
        ARMOR("Armor", D2Item::isTypeArmor),
        CHARM("Charm", D2Item::isCharm),
        JEWEL("Jewel", D2Item::isJewel),
        POTION("Potion", D2Item::isPotion),
        RING("Ring", item -> item.isBodyLocation(D2BodyLocations.BODY_LRIN)),
        AMULET("Amulet", item -> item.isBodyLocation(D2BodyLocations.BODY_NECK));

        private final String display;
        private final Predicate<D2Item> matcher;

        Kind(String display, Predicate<D2Item> matcher) {
            this.display = display;
            this.matcher = matcher;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    /**
     * Weapon and armor types, as in the stash view's Weapon and Armor filters. Armor types go by where the item is worn,
     * so class items count too, e.g. barbarian helms and druid pelts are helms, necromancer heads and grimoires shields.
     */
    public enum ItemType {
        HELM("Helm / circlet", D2BodyLocations.BODY_HEAD),
        BODY_ARMOR("Body armor", D2BodyLocations.BODY_TORS),
        SHIELD("Shield", D2BodyLocations.BODY_RARM),
        GLOVES("Gloves", D2BodyLocations.BODY_GLOV),
        BOOTS("Boots", D2BodyLocations.BODY_FEET),
        BELT("Belt", D2BodyLocations.BODY_BELT),
        SWORD("Sword", D2WeaponTypes.WEAP_SWOR),
        AXE("Axe", D2WeaponTypes.WEAP_AXE),
        MACE("Mace", D2WeaponTypes.WEAP_MACE),
        CLUB("Club", D2WeaponTypes.WEAP_CLUB),
        HAMMER("Hammer", D2WeaponTypes.WEAP_HAMM),
        SCEPTER("Scepter", D2WeaponTypes.WEAP_SCEP),
        DAGGER("Dagger", D2WeaponTypes.WEAP_KNIF),
        CLAW("Claw", D2WeaponTypes.WEAP_H2H),
        WAND("Wand", D2WeaponTypes.WEAP_WAND),
        STAFF("Staff", D2WeaponTypes.WEAP_STAF),
        ORB("Orb", D2WeaponTypes.WEAP_ORB),
        SPEAR("Spear", D2WeaponTypes.WEAP_SPEA),
        POLEARM("Polearm", D2WeaponTypes.WEAP_POLE),
        BOW("Bow", D2WeaponTypes.WEAP_BOW),
        CROSSBOW("Crossbow", D2WeaponTypes.WEAP_XBOW),
        JAVELIN("Javelin", D2WeaponTypes.WEAP_JAVE),
        THROWING_AXE("Throwing axe", D2WeaponTypes.WEAP_TAXE),
        THROWING_KNIFE("Throwing knife", D2WeaponTypes.WEAP_TKNI),
        AMAZON_BOW("Amazon bow", D2WeaponTypes.WEAP_ABOW),
        AMAZON_SPEAR("Amazon spear", D2WeaponTypes.WEAP_ASPE),
        AMAZON_JAVELIN("Amazon javelin", D2WeaponTypes.WEAP_AJAV);

        private final String display;
        private final boolean armor;
        private final Predicate<D2Item> matcher;

        ItemType(String display, D2BodyLocations location) {
            this.display = display;
            this.armor = true;
            // weapons have body locations too, so only armor counts
            this.matcher = item -> item.isTypeArmor() && item.isBodyLocation(location);
        }

        ItemType(String display, D2WeaponTypes weaponType) {
            this.display = display;
            this.armor = false;
            this.matcher = item -> item.isWeaponType(weaponType);
        }

        public boolean isArmor() {
            return armor;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public enum Quality {
        UNIQUE("Unique", D2Item::isUnique),
        SET("Set", D2Item::isSet),
        RUNEWORD("Runeword", D2Item::isRuneWord),
        RARE("Rare", D2Item::isRare),
        MAGIC("Magic", D2Item::isMagical),
        CRAFTED("Crafted", D2Item::isCrafted),
        NORMAL("Normal", D2Item::isNormal);

        private final String display;
        private final Predicate<D2Item> matcher;

        Quality(String display, Predicate<D2Item> matcher) {
            this.display = display;
            this.matcher = matcher;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private static final String SEPARATOR = ";";
    private static final String VALUE_SEPARATOR = ",";

    private final Field field;
    private final List<String> values;
    // one test per value, worked out once: sorting checks every shared stash item against every stash's rules
    private final List<Predicate<D2Item>> matchers;

    private StashSortRule(Field field, List<String> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("A stash sorting rule needs at least one value");
        this.field = field;
        this.values = Collections.unmodifiableList(values);
        this.matchers = values.stream().map(value -> matcher(field, value)).collect(Collectors.toList());
    }

    private static Predicate<D2Item> matcher(Field field, String value) {
        switch (field) {
            case KIND:
                return Kind.valueOf(value).matcher;
            case TYPE:
                return ItemType.valueOf(value).matcher;
            case QUALITY:
                return Quality.valueOf(value).matcher;
            default:
                String lowerCaseName = value.toLowerCase(Locale.ROOT);
                return item -> nameContains(item.getItemName(), lowerCaseName) || nameContains(item.getBaseItemName(), lowerCaseName);
        }
    }

    public static StashSortRule kindIs(Collection<Kind> kinds) {
        return new StashSortRule(Field.KIND, kinds.stream().distinct().map(Enum::name).collect(Collectors.toList()));
    }

    public static StashSortRule typeIs(Collection<ItemType> types) {
        return new StashSortRule(Field.TYPE, types.stream().distinct().map(Enum::name).collect(Collectors.toList()));
    }

    public static StashSortRule qualityIs(Collection<Quality> qualities) {
        return new StashSortRule(Field.QUALITY, qualities.stream().distinct().map(Enum::name).collect(Collectors.toList()));
    }

    /**
     * @param names comma-separated text, e.g. "Shako, Ber, Jah"
     */
    public static StashSortRule nameContains(String names) {
        return new StashSortRule(Field.NAME, splitNames(names));
    }

    /**
     * The names in comma-separated text, trimmed, without blanks or repeats.
     */
    public static List<String> splitNames(String names) {
        return Arrays.stream(names.split(VALUE_SEPARATOR))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * True when the item matches all the rules. No rules match nothing, so a stash without rules takes no items.
     */
    public static boolean matchesAll(List<StashSortRule> rules, D2Item item) {
        return !rules.isEmpty() && rules.stream().allMatch(rule -> rule.matches(item));
    }

    public boolean matches(D2Item item) {
        return matchers.stream().anyMatch(matcher -> matcher.test(item));
    }

    private static boolean nameContains(String itemName, String lowerCaseText) {
        return itemName != null && itemName.toLowerCase(Locale.ROOT).contains(lowerCaseText);
    }

    public Field getField() {
        return field;
    }

    public List<Kind> getKinds() {
        return field == Field.KIND ? values.stream().map(Kind::valueOf).collect(Collectors.toList()) : Collections.emptyList();
    }

    public List<ItemType> getTypes() {
        return field == Field.TYPE ? values.stream().map(ItemType::valueOf).collect(Collectors.toList()) : Collections.emptyList();
    }

    public List<Quality> getQualities() {
        return field == Field.QUALITY ? values.stream().map(Quality::valueOf).collect(Collectors.toList()) : Collections.emptyList();
    }

    public List<String> getNames() {
        return field == Field.NAME ? values : Collections.emptyList();
    }

    /**
     * Stored form used in project.properties: FIELD;value1, value2. The values go last so names may hold the separator.
     */
    public String serialize() {
        return field.name() + SEPARATOR + String.join(VALUE_SEPARATOR + " ", values);
    }

    public static StashSortRule parse(String stored) {
        String[] parts = stored.split(SEPARATOR, 2);
        if (parts.length != 2) throw new IllegalArgumentException("Not a stash sorting rule: " + stored);
        Field field = Field.valueOf(parts[0]);
        if (field == Field.NAME) return nameContains(parts[1]);
        List<String> values = Arrays.stream(parts[1].split(VALUE_SEPARATOR)).map(String::trim).collect(Collectors.toList());
        switch (field) {
            case KIND:
                return kindIs(values.stream().flatMap(StashSortRule::parseKind).collect(Collectors.toList()));
            case TYPE:
                return typeIs(values.stream().map(ItemType::valueOf).collect(Collectors.toList()));
            default:
                return qualityIs(values.stream().map(Quality::valueOf).collect(Collectors.toList()));
        }
    }

    // older saved rules: RING_AMULET still takes both; the dropped RUNE, GEM and OTHER_MISC are left out, so the rule
    // takes fewer items, and a rule left with no kinds can't be read, so its stash takes nothing
    private static Stream<Kind> parseKind(String value) {
        if ("RING_AMULET".equals(value)) return Stream.of(Kind.RING, Kind.AMULET);
        if ("RUNE".equals(value) || "GEM".equals(value) || "OTHER_MISC".equals(value)) return Stream.empty();
        return Stream.of(Kind.valueOf(value));
    }

    /**
     * Reads the rules saved by {@link #save}, keyed by stash file name. A stash with a rule that can't be read
     * gets no rules, so it takes nothing rather than more than its owner meant (the rules are AND'd).
     */
    public static Map<String, List<StashSortRule>> load(Properties properties) {
        Map<String, List<StashSortRule>> rulesByStash = new LinkedHashMap<>();
        for (int i = 0; properties.getProperty("sortrules." + i + ".stash") != null; i++) {
            List<StashSortRule> rules = new ArrayList<>();
            for (int j = 0; properties.getProperty("sortrules." + i + ".rule." + j) != null; j++) {
                try {
                    rules.add(parse(properties.getProperty("sortrules." + i + ".rule." + j)));
                } catch (IllegalArgumentException pEx) {
                    System.err.println("Ignoring the sorting rules of " + properties.getProperty("sortrules." + i + ".stash") + ": " + pEx.getMessage());
                    rules.clear();
                    break;
                }
            }
            rulesByStash.put(properties.getProperty("sortrules." + i + ".stash"), rules);
        }
        return rulesByStash;
    }

    public static void save(Map<String, List<StashSortRule>> rulesByStash, Properties properties) {
        int index = 0;
        for (Map.Entry<String, List<StashSortRule>> entry : rulesByStash.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            properties.put("sortrules." + index + ".stash", entry.getKey());
            for (int j = 0; j < entry.getValue().size(); j++) {
                properties.put("sortrules." + index + ".rule." + j, entry.getValue().get(j).serialize());
            }
            index++;
        }
    }

    @Override
    public String toString() {
        List<String> shown = field == Field.KIND ? getKinds().stream().map(Kind::toString).collect(Collectors.toList())
                : field == Field.TYPE ? getTypes().stream().map(ItemType::toString).collect(Collectors.toList())
                : field == Field.QUALITY ? getQualities().stream().map(Quality::toString).collect(Collectors.toList())
                : values.stream().map(name -> "\"" + name + "\"").collect(Collectors.toList());
        return field + " " + String.join(" or ", shown);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StashSortRule)) return false;
        StashSortRule that = (StashSortRule) o;
        return field == that.field && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, values);
    }
}
