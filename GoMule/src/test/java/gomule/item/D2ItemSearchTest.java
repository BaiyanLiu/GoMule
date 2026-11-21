package gomule.item;

import gomule.d2i.D2SharedStashReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class D2ItemSearchTest {

    private static final String SHAKO = "harlequin crest\nshako\ndefense: 141\nrequired level: 62\n+2 to all skills\n"
            + "+141 to life (+1.5 per character level)\n50% better chance of getting magic items\nethereal\n";

    @BeforeEach
    public void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    private static boolean search(String query) {
        return D2ItemSearch.parse(query).matchesText(SHAKO);
    }

    @Test
    public void allWordsMustAppear() {
        assertTrue(search("magic skills"));
        assertTrue(search("  MAGIC   Shako "));
        assertFalse(search("magic resist"));
        assertTrue(search(""));
        assertTrue(search("   "));
        assertTrue(D2ItemSearch.parse(null).matchesText(SHAKO));
    }

    @Test
    public void dashExcludes() {
        assertFalse(search("shako -eth"));
        assertTrue(search("shako -socket"));
        assertFalse(search("-shako"));
        assertTrue(search("-"), "a lone dash (half-typed -word) is ignored");
        assertTrue(search("shako -"));
    }

    @Test
    public void dashBeforeDigitIsText() {
        assertTrue(D2ItemSearch.parse("-20%").matchesText("requirements -20%\n"));
        assertFalse(D2ItemSearch.parse("-20%").matchesText("requirements 20%\n"));
    }

    @Test
    public void barMeansOr() {
        assertTrue(search("jah|shako"));
        assertTrue(search("jah | shako"));
        assertFalse(search("jah|ber"));
        assertFalse(search("-jah|eth"));
        assertTrue(search("-jah|ber"));
        assertTrue(search("|shako|"));
    }

    @Test
    public void quotesKeepAPhraseAndSymbolsAreLiteral() {
        assertTrue(search("\"all skills\""));
        assertFalse(search("\"skills all\""));
        assertTrue(search("skills all"));
        assertFalse(search("-\"magic items\""));
        assertTrue(search("(+1.5"));
        assertTrue(search("+2"));
        assertFalse(search(".*"));
    }

    @Test
    public void quotesEdgeCases() {
        assertTrue(search("\"all skills"), "an unclosed quote runs to the end");
        assertTrue(search("\"\" shako"), "empty quotes are ignored");
        assertTrue(D2ItemSearch.parse("\"-eth\"").matchesText("cannot be -eth\n"), "a quoted dash is text");
        assertFalse(D2ItemSearch.parse("\"-eth\"").matchesText(SHAKO));
        assertTrue(search("\"all skills\"|jah"));
    }

    @Test
    public void customFilterTakesSymbolsLiterally() throws Exception {
        List<D2Item> items = sampleItems();
        D2Item withPlus = items.stream().filter(item -> item.getItemDump().matches("(?s).*\\+\\d.*")).findFirst().get();
        String line = java.util.Arrays.stream(withPlus.getItemDump().split("\\R")).filter(l -> l.matches(".*\\+\\d.*")).findFirst().get();
        int plus = line.indexOf('+', line.replaceAll("\\+\\d.*", "").length());
        String plusText = line.substring(plus, plus + 2);
        assertTrue(withPlus.conforms(plusText, 0, true), plusText + " in " + line);
        assertTrue(withPlus.conforms(plusText, -1337, true));
        for (D2Item item : items) {
            assertDoesNotThrow(() -> item.conforms("(", 1, true));
            assertDoesNotThrow(() -> item.conforms("[x", 1, false));
            assertFalse(item.conforms(".*", 0, true), item.getItemName());
        }
    }

    @Test
    public void customFilterNumbersMatchTheOldBehaviourForPlainText() throws Exception {
        String[] props = {"defense", "required level", "resist", "enhanced", "item level", "life", "magic", "sockets", "to"};
        int checked = 0;
        for (D2Item item : sampleItems()) {
            for (String prop : props) {
                for (int value : new int[]{-1337, 0, 5, 20, 60, 150, 400}) {
                    for (boolean min : new boolean[]{true, false}) {
                        boolean expected = oldConforms(item, prop, value, min);
                        boolean actual = item.conforms(prop, value, min);
                        // the old line regex skipped a matching line right after another one, so it can only miss
                        if (expected) assertTrue(actual, prop + " " + value + " " + min + " on " + item.getItemName());
                        if (!actual) assertFalse(expected);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 1000);
    }

    @Test
    public void itemDumpIsCachedAndInvalidated() throws Exception {
        for (D2Item item : sampleItems()) {
            String dump = item.getItemDump();
            assertEquals(D2ItemRenderer.itemDump(item, true), dump);
            assertSame(dump, item.getItemDump());
            assertEquals(dump.toLowerCase(), item.getItemDumpLowerCase());

            item.setCharLvl(item.getiCharLvl());
            assertSame(dump, item.getItemDump(), "same level keeps the cache");
            int level = item.getiCharLvl();
            item.setCharLvl(level == 99 ? 1 : 99);
            assertEquals(D2ItemRenderer.itemDump(item, true), item.getItemDump());
            item.setCharLvl(level);
            item.invalidateItemDump();
            assertNotSame(dump, item.getItemDump());
            assertEquals(dump, item.getItemDump());
        }
    }

    private static List<D2Item> sampleItems() throws Exception {
        List<D2Item> items = new ArrayList<>();
        items.addAll(new D2SharedStashReader().readStash(ROW, loadTestResources("sharedStashFiles", "modernGold.d2i").getAbsolutePath()).getItemList());
        items.addAll(new D2SharedStashReader().readStash(ROW, loadTestResources("userSamples", "ModernSharedStashSoftCoreV2.d2i").getAbsolutePath()).getItemList());
        assertFalse(items.isEmpty());
        return items;
    }

    /**
     * D2Item.conforms before the filter text was taken literally.
     */
    private static boolean oldConforms(D2Item item, String prop, int pVal, boolean min) {
        String dumpStr = D2ItemRenderer.itemDump(item, true);
        if (!dumpStr.toLowerCase().contains(prop.toLowerCase())) return false;
        if (pVal == -1337) return true;
        Matcher lines = Pattern.compile("(\\n.*" + prop.toLowerCase() + ".*\\n)", Pattern.UNIX_LINES).matcher("\n" + dumpStr.toLowerCase() + "\n");
        while (lines.find()) {
            Matcher mat = Pattern.compile("[^\\(?](\\d+)").matcher(lines.group());
            while (mat.find()) {
                long number = Long.parseLong(mat.group(1));
                if (min ? number >= pVal : number <= pVal) return true;
            }
        }
        return false;
    }
}
