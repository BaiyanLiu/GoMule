package gomule.item;

import randall.d2files.D2TxtFile;
import randall.d2files.D2TxtFileItemProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How well a unique, set or runeword item rolled: for each property whose range in UniqueItems.txt, SetItems.txt
 * or Runes.txt is wider than one value (and an armor's base defense against Armor.txt), how far the rolled value is
 * from the minimum (0) to the maximum (1), averaged with equal weight.
 * <p>
 * {@link #pointsFromMax} instead adds up how many points each of those properties is below its maximum, for example
 * an Annihilus with 16 of 10-20 all stats and 5 of 5-10% experience is -9. It leaves out the base defense.
 */
public final class ItemRollPercent {

    /**
     * properties.txt functions that roll one value between min and max. Others (charges, chance to cast, per level,
     * min-max damage pairs, random skills...) use min and max for something else and are left out.
     */
    private static final Set<Integer> ROLLED_FUNCS = new HashSet<>(Arrays.asList(1, 2, 3, 5, 6, 7, 8, 10, 13, 21, 22));

    /** Stats GoMule may read under another number: min/max damage, as one-hand, two-hand or throw damage. */
    private static final int[][] STAT_ALIASES = {{21, 23, 159}, {22, 24, 160}};

    /** Txt lookups, which scan whole files, kept per template row, property line and armor code. */
    private static final Map<String, List<TemplateLine>> LINES = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> STAT_KEYS = new ConcurrentHashMap<>();
    private static final Map<String, int[]> ARMOR_DEFENSE = new ConcurrentHashMap<>();

    private ItemRollPercent() {
    }

    /**
     * The average roll from 0 to 1, or null when the item has no property that can roll more than one value.
     */
    public static Double of(D2Item item) {
        return item.getRolls().average;
    }

    /**
     * The average roll as a whole percentage, for example "73%", or "" when the item has nothing that rolls.
     */
    public static String display(D2Item item) {
        return item.getRolls().averageText;
    }

    /**
     * The sum over the item's varying properties of rolled value minus maximum: 0 when every one rolled its maximum,
     * negative otherwise, or null when the item has no property that can roll more than one value.
     */
    public static Integer pointsFromMax(D2Item item) {
        return item.getRolls().pointsFromMax;
    }

    /** {@link #pointsFromMax} as text, for example "-9" or "0", or "" when the item has nothing that rolls. */
    public static String pointsFromMaxDisplay(D2Item item) {
        return item.getRolls().pointsFromMaxText;
    }

    /**
     * Orders items by {@link #pointsFromMax}, furthest from perfect first (-10 before -1 before 0), with items that have
     * nothing rolled last. The stash table uses it to break ties on whatever column it is sorted by.
     */
    public static int compareWorstRollFirst(D2Item item1, D2Item item2) {
        Integer lPoints1 = pointsFromMax(item1);
        Integer lPoints2 = pointsFromMax(item2);
        if (lPoints1 == null || lPoints2 == null) {
            return lPoints1 == null ? (lPoints2 == null ? 0 : 1) : -1;
        }
        return Integer.compare(lPoints1, lPoints2);
    }

    /**
     * Works out an item's rolls from the txt files. {@link D2Item#getRolls()} calls it once per item and keeps the
     * answer, so sorting the stash table doesn't repeat it for every comparison.
     */
    static Rolls compute(D2Item item) {
        double lSum = 0;
        int lCount = 0;
        Integer lPoints = null;
        D2TxtFileItemProperties lTemplate = item.getRollTemplate();
        if (lTemplate != null) {
            for (int[] lRoll : propertyRolls(lTemplate, item.getRolledProps())) {
                lSum += fraction(lRoll[0], lRoll[1], lRoll[2]);
                lCount++;
                lPoints = (lPoints == null ? 0 : lPoints) + Math.min(0, lRoll[0] - lRoll[2]);
            }
        }
        Double lDefenseRoll = defenseRoll(item);
        if (lDefenseRoll != null) {
            lSum += lDefenseRoll;
            lCount++;
        }
        return new Rolls(lCount == 0 ? null : lSum / lCount, lPoints);
    }

    /** {rolled value, min, max} for each of the item's properties that can roll more than one value. */
    private static List<int[]> propertyRolls(D2TxtFileItemProperties template, List<D2Prop> rolledProps) {
        // A stat can get values from more than one line (res-all and res-fire both add to fire resist) and the file
        // only stores the sum, so ranges add up per stat, and each varying line is judged by its first stat.
        Map<String, int[]> lRanges = new LinkedHashMap<>();
        Set<String> lVarying = new LinkedHashSet<>();
        List<TemplateLine> lLines = LINES.computeIfAbsent(template.getFileName() + ":" + template.getRowNum(),
                k -> lines(template));
        for (TemplateLine lLine : lLines) {
            List<String> lKeys = STAT_KEYS.computeIfAbsent(lLine.code + "\t" + lLine.param, k -> statKeys(lLine));
            if (lKeys.isEmpty()) continue;
            for (String lKey : lKeys) {
                int[] lRange = lRanges.computeIfAbsent(lKey, k -> new int[2]);
                lRange[0] += lLine.min;
                lRange[1] += lLine.max;
            }
            if (lLine.max > lLine.min) lVarying.add(lKeys.get(0));
        }
        List<int[]> lRolls = new ArrayList<>();
        for (String lKey : lVarying) {
            int[] lRange = lRanges.get(lKey);
            Integer lValue = rolledValue(rolledProps, lKey);
            if (lValue == null || lRange[1] <= lRange[0]) continue;
            lRolls.add(new int[]{lValue, lRange[0], lRange[1]});
        }
        return lRolls;
    }

    private static Double defenseRoll(D2Item item) {
        if (!item.isTypeArmor()) return null;
        if (!(item.isUnique() || item.isSet() || item.isRuneWord())) return null;
        // superior bases (a runeword in one) get the maximum defense plus a bonus, so there is nothing rolled
        if (item.getQuality() == 3) return null;
        int[] lRange = ARMOR_DEFENSE.computeIfAbsent(item.getItemCode(), ItemRollPercent::armorDefense);
        int lMin = lRange[0];
        // the game rolls up to one more than maxac (uniques in the samples carry Great Helm 36, Wire Fleece 482)
        int lMax = lRange[1] + 1;
        if (lRange[1] <= lMin) return null;
        if (item.isEthereal()) {
            lMin = lMin * 3 / 2;
            lMax = lMax * 3 / 2;
        }
        return fraction(item.getBaseDefense(), lMin, lMax);
    }

    /** Armor.txt's minac and maxac for an item code; {0, 0} when it has none. */
    private static int[] armorDefense(String code) {
        D2TxtFileItemProperties lArmor = D2TxtFile.ARMOR.searchColumns("code", code);
        if (lArmor == null) return new int[2];
        Integer lMin = parse(lArmor.get("minac"));
        Integer lMax = parse(lArmor.get("maxac"));
        return lMin == null || lMax == null ? new int[2] : new int[]{lMin, lMax};
    }

    private static double fraction(int value, int min, int max) {
        double lFraction = (value - min) / (double) (max - min);
        return Math.max(0, Math.min(1, lFraction));
    }

    /**
     * The stats one line of the template sets, as "stat id:param" keys (param -1 for stats without one), or none
     * when it isn't a property that rolls a single value.
     */
    static List<String> statKeys(TemplateLine line) {
        D2TxtFileItemProperties lProperty = D2TxtFile.PROPS.searchColumns("code", line.code);
        if (lProperty == null) return Collections.emptyList();
        List<String> lKeys = new ArrayList<>();
        for (int x = 1; x <= 7; x++) {
            String lFunc = lProperty.get("func" + x);
            if (lFunc.isEmpty()) break;
            Integer lFuncNr = parse(lFunc);
            if (lFuncNr == null || !ROLLED_FUNCS.contains(lFuncNr)) return Collections.emptyList();
            String lStat = lProperty.get("stat" + x);
            if (lFuncNr == 5) lStat = "mindamage";
            else if (lFuncNr == 6) lStat = "maxdamage";
            else if (lFuncNr == 7) lStat = "item_maxdamage_percent";
            if (lStat.isEmpty()) continue;
            D2TxtFileItemProperties lStatRow = D2TxtFile.ITEM_STAT_COST.searchColumns("Stat", lStat);
            if (lStatRow == null) return Collections.emptyList();
            int lParam = -1;
            if (!lStatRow.get("Save Param Bits").isEmpty()) {
                Integer lParsed = lFuncNr == 21 ? parse(lProperty.get("val" + x)) : param(line.param);
                if (lParsed == null) return Collections.emptyList();
                // skill tabs count 0-20 in the txt files (three per class), but are saved as class * 8 + tab
                if (lFuncNr == 10) lParsed = lParsed / 3 * 8 + lParsed % 3;
                lParam = lParsed;
            }
            lKeys.add(lStatRow.get("*ID") + ":" + lParam);
        }
        return Collections.unmodifiableList(lKeys);
    }

    /** A numeric param, or a skill name looked up in Skills.txt. */
    private static Integer param(String param) {
        Integer lNumber = parse(param);
        if (lNumber != null) return lNumber;
        D2TxtFileItemProperties lSkill = D2TxtFile.SKILLS.searchColumns("skill", param);
        return lSkill == null ? null : parse(lSkill.get("*Id"));
    }

    /** The item's value for a "stat:param" key, summed over its properties, or null if it doesn't have it. */
    static Integer rolledValue(List<D2Prop> props, String key) {
        String[] lParts = key.split(":");
        int lStat = Integer.parseInt(lParts[0]);
        int lParam = Integer.parseInt(lParts[1]);
        for (int lAlias : aliases(lStat)) {
            Integer lSum = null;
            for (D2Prop lProp : props) {
                if (lProp.getPNum() != lAlias) continue;
                int[] lVals = lProp.getPVals();
                if (lParam >= 0) {
                    if (lVals.length < 2 || lVals[0] != lParam) continue;
                    lSum = (lSum == null ? 0 : lSum) + lVals[1];
                } else {
                    lSum = (lSum == null ? 0 : lSum) + lVals[0];
                }
            }
            if (lSum != null) return lSum;
        }
        return null;
    }

    private static int[] aliases(int stat) {
        for (int[] lAliases : STAT_ALIASES) {
            for (int lAlias : lAliases) {
                if (lAlias == stat) return lAliases;
            }
        }
        return new int[]{stat};
    }

    static List<TemplateLine> lines(D2TxtFileItemProperties template) {
        List<TemplateLine> lLines = new ArrayList<>();
        String lFile = template.getFileName();
        if (lFile.equalsIgnoreCase("Runes")) {
            for (int i = 1; i <= 7; i++) {
                addLine(lLines, template.get("T1Code" + i), template.get("T1Param" + i),
                        template.get("T1Min" + i), template.get("T1Max" + i));
            }
        } else {
            int lCount = lFile.equalsIgnoreCase("SetItems") ? 9 : 12;
            for (int i = 1; i <= lCount; i++) {
                addLine(lLines, template.get("prop" + i), template.get("par" + i),
                        template.get("min" + i), template.get("max" + i));
            }
        }
        return Collections.unmodifiableList(lLines);
    }

    private static void addLine(List<TemplateLine> lines, String code, String param, String min, String max) {
        if (code == null || code.isEmpty()) return;
        Integer lMin = parse(min);
        Integer lMax = parse(max);
        if (lMin == null && lMax == null) return;
        if (lMin == null) lMin = lMax;
        if (lMax == null) lMax = lMin;
        lines.add(new TemplateLine(code, param == null ? "" : param, Math.min(lMin, lMax), Math.max(lMin, lMax)));
    }

    private static Integer parse(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException pEx) {
            return null;
        }
    }

    /** One item's results, kept on the item by {@link D2Item#getRolls()}. */
    static final class Rolls {
        final Double average;
        final String averageText;
        final Integer pointsFromMax;
        final String pointsFromMaxText;

        Rolls(Double average, Integer pointsFromMax) {
            this.average = average;
            this.averageText = average == null ? "" : Math.round(average * 100) + "%";
            this.pointsFromMax = pointsFromMax;
            this.pointsFromMaxText = pointsFromMax == null ? "" : Integer.toString(pointsFromMax);
        }
    }

    static final class TemplateLine {
        final String code;
        final String param;
        final int min;
        final int max;

        TemplateLine(String code, String param, int min, int max) {
            this.code = code;
            this.param = param;
            this.min = min;
            this.max = max;
        }
    }
}
