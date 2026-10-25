package gomule.d2x;

import gomule.item.D2Item;
import gomule.item.D2ItemRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One column of the item table in an ATMA (.d2x) stash view: either a built-in item attribute or the number
 * of a property line, found by text the same way the stash's Filter... button finds it.
 */
public class StashColumn {

    public enum Builtin {
        NAME("Name", 175, false, D2Item::getItemName),
        REQ_LVL("lvl", 14, true, item -> number(item.getReqLvl())),
        REQ_STR("str", 14, true, item -> number(item.getReqStr())),
        REQ_DEX("dex", 19, true, item -> number(item.getReqDex())),
        ILVL("ilvl", 26, true, item -> number(item.getIlvl())),
        RARITY("Rarity", 60, false, StashColumn::rarity),
        TIER("Tier", 60, false, item -> item.isTypeArmor() || item.isTypeWeapon() ? item.getItemQuality() : ""),
        BASE("Base", 110, false, D2Item::getBaseItemName),
        SOCKETS("Sock", 26, true, item -> item.isSocketed() ? Long.toString(item.getSocketNrTotal()) : ""),
        DEFENSE("Def", 34, true, item -> item.isTypeArmor() ? Integer.toString(item.getiDef()) : ""),
        ETHEREAL("Eth", 28, false, item -> item.isEthereal() ? "eth" : ""),
        SET("Set", 110, false, item -> item.isSet() && item.getSetName() != null ? item.getSetName() : ""),
        /** Which stash or character the item is in; only offered in the All Stashes view, which fills it in. */
        SOURCE("From", 70, false, item -> "");

        private final String header;
        private final int width;
        private final boolean numeric;
        private final Function<D2Item, String> value;

        Builtin(String header, int width, boolean numeric, Function<D2Item, String> value) {
            this.header = header;
            this.width = width;
            this.numeric = numeric;
            this.value = value;
        }

        @Override
        public String toString() {
            return this == SOURCE ? "From (stash or character)" : header;
        }
    }

    public static final List<StashColumn> DEFAULT_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            new StashColumn(Builtin.NAME), new StashColumn(Builtin.REQ_LVL),
            new StashColumn(Builtin.REQ_STR), new StashColumn(Builtin.REQ_DEX)));

    private static final String PROPERTY_PREFIX = "prop:";
    private static final Pattern NUMBER = Pattern.compile("[^\\(?](\\d+)");

    private final Builtin builtin;
    private final String label;
    private final String propertyText;

    public StashColumn(Builtin builtin) {
        this.builtin = Objects.requireNonNull(builtin);
        this.label = builtin.header;
        this.propertyText = null;
    }

    /**
     * A column showing the number on the item's first property line containing {@code propertyText}.
     */
    public StashColumn(String label, String propertyText) {
        this.propertyText = clean(propertyText);
        if (this.propertyText.isEmpty()) throw new IllegalArgumentException("Property text is empty");
        String lLabel = clean(label);
        this.label = lLabel.isEmpty() ? this.propertyText : lLabel;
        this.builtin = null;
    }

    /**
     * Reads the column layouts saved by {@link #save}, keyed by stash file name.
     */
    public static Map<String, List<StashColumn>> load(Properties properties) {
        Map<String, List<StashColumn>> layouts = new LinkedHashMap<>();
        for (int i = 0; properties.getProperty("stashcolumns." + i + ".stash") != null; i++) {
            List<StashColumn> columns = new ArrayList<>();
            for (int j = 0; properties.getProperty("stashcolumns." + i + ".column." + j) != null; j++) {
                try {
                    columns.add(parse(properties.getProperty("stashcolumns." + i + ".column." + j)));
                } catch (IllegalArgumentException pEx) {
                    System.err.println("Skipping stash column: " + pEx.getMessage());
                }
            }
            if (!columns.isEmpty()) layouts.put(properties.getProperty("stashcolumns." + i + ".stash"), columns);
        }
        return layouts;
    }

    public static void save(Map<String, List<StashColumn>> layouts, Properties properties) {
        int index = 0;
        for (Map.Entry<String, List<StashColumn>> entry : layouts.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            properties.put("stashcolumns." + index + ".stash", entry.getKey());
            for (int j = 0; j < entry.getValue().size(); j++) {
                properties.put("stashcolumns." + index + ".column." + j, entry.getValue().get(j).serialize());
            }
            index++;
        }
    }

    public static StashColumn parse(String text) {
        if (text.startsWith(PROPERTY_PREFIX)) {
            String[] parts = text.substring(PROPERTY_PREFIX.length()).split("\t", 2);
            return parts.length == 2 ? new StashColumn(parts[0], parts[1]) : new StashColumn("", parts[0]);
        }
        try {
            return new StashColumn(Builtin.valueOf(text.trim()));
        } catch (IllegalArgumentException pEx) {
            throw new IllegalArgumentException("Unknown stash column: " + text);
        }
    }

    public String serialize() {
        return builtin != null ? builtin.name() : PROPERTY_PREFIX + label + "\t" + propertyText;
    }

    public boolean isProperty() {
        return builtin == null;
    }

    public Builtin getBuiltin() {
        return builtin;
    }

    public String getLabel() {
        return label;
    }

    public int getPreferredWidth() {
        return builtin != null ? builtin.width : Math.max(34, label.length() * 8);
    }

    public boolean isNumeric() {
        return builtin == null || builtin.numeric;
    }

    /**
     * The cell text for a built-in column, or for a property column given the item's text dump
     * ({@code D2ItemRenderer.itemDump(item, true)}). Empty when the item doesn't have it.
     */
    public String getValue(D2Item item, String itemDump) {
        if (builtin != null) {
            String lValue = builtin.value.apply(item);
            return lValue == null ? "" : lValue;
        }
        return propertyValue(itemDump, propertyText);
    }

    public String getValue(D2Item item) {
        return getValue(item, isProperty() ? D2ItemRenderer.itemDump(item, true) : null);
    }

    /**
     * The first number on the first line of {@code itemDump} that contains {@code text}, ignoring case, using the
     * same number matching as {@link D2Item#conforms}. A matching line without a number gives "yes".
     */
    static String propertyValue(String itemDump, String text) {
        if (itemDump == null) return "";
        String lText = text.toLowerCase(Locale.ROOT);
        for (String lLine : itemDump.split("\n")) {
            if (lLine.toLowerCase(Locale.ROOT).contains(lText)) {
                Matcher lMatcher = NUMBER.matcher("\n" + lLine);
                return lMatcher.find() ? lMatcher.group(1) : "yes";
            }
        }
        return "";
    }

    /**
     * Orders cell values: numbers by value, other text alphabetically, and empty cells last.
     */
    public int compareValues(String value1, String value2) {
        if (value1.isEmpty() || value2.isEmpty()) return Boolean.compare(value1.isEmpty(), value2.isEmpty());
        if (isNumeric()) {
            try {
                return Long.compare(Long.parseLong(value1), Long.parseLong(value2));
            } catch (NumberFormatException pEx) {
                // "yes" from a property line without a number; fall through to text order
            }
        }
        return value1.compareToIgnoreCase(value2);
    }

    private static String number(int value) {
        return value == -1 ? "" : Integer.toString(value);
    }

    private static String rarity(D2Item item) {
        if (item.isUnique()) return "Unique";
        if (item.isSet()) return "Set";
        if (item.isRuneWord()) return "Runeword";
        if (item.isCrafted()) return "Crafted";
        if (item.isRare()) return "Rare";
        if (item.isMagical()) return "Magic";
        return "Normal";
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ').trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StashColumn)) return false;
        StashColumn that = (StashColumn) o;
        return builtin == that.builtin && Objects.equals(label, that.label) && Objects.equals(propertyText, that.propertyText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(builtin, label, propertyText);
    }

    @Override
    public String toString() {
        return builtin != null ? builtin.toString() : label + "  (property: \"" + propertyText + "\")";
    }
}
