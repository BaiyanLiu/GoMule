package gomule.d2x;

import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.item.D2Item;
import gomule.item.D2ItemRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class StashColumnTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void columnsSurviveSerializeAndParse() {
        for (StashColumn.Builtin builtin : StashColumn.Builtin.values()) {
            StashColumn column = new StashColumn(builtin);
            assertEquals(column, StashColumn.parse(column.serialize()));
        }
        StashColumn fcr = new StashColumn("FCR", "Faster Cast Rate");
        assertEquals(fcr, StashColumn.parse(fcr.serialize()));
        assertEquals("FCR", fcr.getLabel());
        assertEquals("Magic Find", new StashColumn("  ", "Magic Find").getLabel());
        assertThrows(IllegalArgumentException.class, () -> StashColumn.parse("NOT_A_COLUMN"));
        assertThrows(IllegalArgumentException.class, () -> new StashColumn("x", " "));
    }

    @Test
    public void layoutsPerStashSurviveProjectPropertiesFile() throws Exception {
        Map<String, List<StashColumn>> layouts = new LinkedHashMap<>();
        layouts.put("C:\\mules\\runes.d2x", Arrays.asList(new StashColumn(StashColumn.Builtin.NAME), new StashColumn(StashColumn.Builtin.ILVL)));
        layouts.put("C:\\mules\\caster gear.d2x", Arrays.asList(new StashColumn(StashColumn.Builtin.NAME),
                new StashColumn("FCR", "Faster Cast Rate"), new StashColumn("", "Better Chance of Getting Magic Items")));
        layouts.put("all", Arrays.asList(new StashColumn(StashColumn.Builtin.SOURCE), new StashColumn(StashColumn.Builtin.NAME)));
        layouts.put("empty.d2x", new ArrayList<>());

        Properties saved = new Properties();
        saved.put("sortrules.0.stash", "C:\\mules\\runes.d2x");
        StashColumn.save(layouts, saved);
        File file = tempDir.resolve("project.properties").toFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            saved.store(out, "test");
        }
        Properties loaded = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            loaded.load(in);
        }

        layouts.remove("empty.d2x");
        assertEquals(layouts, StashColumn.load(loaded));
        assertEquals("C:\\mules\\runes.d2x", loaded.getProperty("sortrules.0.stash"));
    }

    @Test
    public void unknownSavedColumnsAreSkipped() {
        Properties properties = new Properties();
        properties.put("stashcolumns.0.stash", "a.d2x");
        properties.put("stashcolumns.0.column.0", "NAME");
        properties.put("stashcolumns.0.column.1", "GONE");
        properties.put("stashcolumns.0.column.2", "ILVL");
        assertEquals(Arrays.asList(new StashColumn(StashColumn.Builtin.NAME), new StashColumn(StashColumn.Builtin.ILVL)),
                StashColumn.load(properties).get("a.d2x"));
    }

    @Test
    public void propertyValueIsTheNumberOnTheMatchingLine() {
        String dump = "Harlequin Crest\nShako\nDefense: 141\nRequired Level: 62\n+2 to All Skills\n"
                + "50% Better Chance of Getting Magic Items\nCannot Be Frozen\n";
        assertEquals("141", StashColumn.propertyValue(dump, "defense"));
        assertEquals("50", StashColumn.propertyValue(dump, "Magic Items"));
        assertEquals("2", StashColumn.propertyValue(dump, "all skills"));
        assertEquals("yes", StashColumn.propertyValue(dump, "cannot be frozen"));
        assertEquals("", StashColumn.propertyValue(dump, "Faster Cast Rate"));
    }

    @Test
    public void emptyCellsSortLastAndNumbersSortByValue() {
        StashColumn fcr = new StashColumn("FCR", "Faster Cast Rate");
        List<String> values = new ArrayList<>(Arrays.asList("", "20", "100", "9", ""));
        values.sort(fcr::compareValues);
        assertEquals(Arrays.asList("9", "20", "100", "", ""), values);
    }

    @Test
    public void columnValuesOfSharedStashItemsMatchAfterAtmaStashWriteAndReadBack() throws Exception {
        D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, loadTestResources("sharedStashFiles", "modernGold.d2i").getAbsolutePath());
        List<D2Item> items = sharedStash.getItemList();
        assertFalse(items.isEmpty());

        String atmaStashFileName = tempDir.resolve("columns.d2x").toString();
        D2Stash atmaStash = new D2StashReader().readStash(ROW, atmaStashFileName);
        items.forEach(atmaStash::addItem);
        new D2StashWriter(ROW, atmaStashFileName).write(atmaStash);
        List<D2Item> readBack = new D2StashReader().readStash(ROW, atmaStashFileName).getItemList();

        assertEquals(items.size(), readBack.size());
        List<StashColumn> columns = allColumns();
        for (int i = 0; i < items.size(); i++) {
            assertArrayEquals(items.get(i).get_bytes(), readBack.get(i).get_bytes());
            for (StashColumn column : columns) {
                assertEquals(column.getValue(items.get(i)), column.getValue(readBack.get(i)),
                        column + " of " + D2ItemRenderer.itemDump(items.get(i), true));
            }
        }
    }

    static List<StashColumn> allColumns() {
        List<StashColumn> columns = new ArrayList<>();
        for (StashColumn.Builtin builtin : StashColumn.Builtin.values()) columns.add(new StashColumn(builtin));
        columns.add(new StashColumn("Def", "Defense"));
        columns.add(new StashColumn("ED", "Enhanced"));
        columns.add(new StashColumn("Res", "Resist"));
        return columns;
    }
}
