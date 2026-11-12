package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.d2i.D2SharedStashWriter;
import gomule.item.D2Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static gomule.gui.sharedStash.SharedStashGoldTransferPanel.*;
import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class SharedStashGoldTransferPanelTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    public static void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void withdrawTakesMoreThanOnePageHolds() {
        int[] pages = {MAX_GOLD_PER_PANE, MAX_GOLD_PER_PANE, MAX_GOLD_PER_PANE, 0, 0, 0, 0};
        assertArrayEquals(new int[]{0, 0, 2_000_000, 0, 0, 0, 0}, withdraw(pages, 0, 5_500_000));
    }

    @Test
    public void withdrawEmptiesTheSelectedPageFirst() {
        int[] pages = {1_000_000, 2_000_000, 500_000};
        assertArrayEquals(new int[]{500_000, 0, 500_000}, withdraw(pages, 1, 2_500_000));
    }

    @Test
    public void withdrawStopsWhenTheStashIsEmpty() {
        int[] pages = {100, 200, 300};
        assertArrayEquals(new int[]{0, 0, 0}, withdraw(pages, 2, Integer.MAX_VALUE));
    }

    @Test
    public void withdrawOfNothingOrANegativeAmountChangesNothing() {
        int[] pages = {100, 200};
        assertArrayEquals(pages, withdraw(pages, 0, 0));
        assertArrayEquals(pages, withdraw(pages, 0, -5));
    }

    @Test
    public void depositFillsTheSelectedPageThenTheOthersUpToThePageLimit() {
        int[] pages = {0, 2_400_000, 0, 0, 0, 0, 0};
        assertArrayEquals(new int[]{2_500_000, 2_500_000, 2_500_000, 2_500_000, 2_500_000, 0, 0},
                deposit(pages, 1, 5, 20_000_000, MAX_GOLD_PER_PANE));
    }

    @Test
    public void depositNeverPutsGoldOnTheNonItemPages() {
        int[] pages = {0, 0, 0, 0, 0, 0, 0};
        int[] result = deposit(pages, 6, 5, 1_000, MAX_GOLD_PER_PANE);
        assertArrayEquals(new int[]{1_000, 0, 0, 0, 0, 0, 0}, result);
    }

    @Test
    public void depositIntoAFullStashMovesNothing() {
        int[] pages = {MAX_GOLD_PER_PANE, MAX_GOLD_PER_PANE, MAX_GOLD_PER_PANE};
        assertArrayEquals(pages, deposit(pages, 0, 3, 10_000, MAX_GOLD_PER_PANE));
    }

    @Test
    public void bankNeverOverflows() {
        assertEquals(0, roomInBank(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE - 7_500_000, roomInBank(7_500_000));
    }

    @Test
    public void goldTakenFromSeveralPagesOfTheSampleStashWritesBackAndReadsBackTheSame() throws Exception {
        for (String name : new String[]{"userSamples/ModernSharedStashSoftCoreV2.d2i", "sharedStashFiles/modernGold.d2i"}) {
            String[] parts = name.split("/");
            File copy = tempDir.resolve(parts[1]).toFile();
            Files.copy(loadTestResources(parts[0], parts[1]).toPath(), copy.toPath());
            byte[] original = Files.readAllBytes(copy.toPath());

            D2SharedStash stash = new D2SharedStashReader().readStash(ROW, copy.getAbsolutePath());
            int paneCount = stash.getPanes().size();
            List<String> itemsBefore = itemBytes(stash);

            // fill every item page to the limit (more than one page holds), then write and read back
            int[] full = deposit(gold(stash), 0, 5, Integer.MAX_VALUE, MAX_GOLD_PER_PANE);
            setGold(stash, full);
            new D2SharedStashWriter(ROW, copy, original).write(stash);
            D2SharedStash readBack = new D2SharedStashReader().readStash(ROW, copy.getAbsolutePath());
            assertArrayEquals(full, gold(readBack), name);
            assertEquals(5 * MAX_GOLD_PER_PANE + Arrays.stream(full, 5, paneCount).sum(), readBack.getTotalGold(), name);
            assertEquals(itemsBefore, itemBytes(readBack), name);

            // take 7,000,000 out in one go, more than the 2,500,000 one page holds
            int[] after = withdraw(gold(readBack), 0, 7_000_000);
            assertEquals(7_000_000, Arrays.stream(gold(readBack)).sum() - Arrays.stream(after).sum(), name);
            setGold(readBack, after);
            byte[] written = Files.readAllBytes(copy.toPath());
            new D2SharedStashWriter(ROW, copy, written).write(readBack);
            D2SharedStash again = new D2SharedStashReader().readStash(ROW, copy.getAbsolutePath());
            assertArrayEquals(after, gold(again), name);
            assertEquals(itemsBefore, itemBytes(again), name);
        }
    }

    private static int[] gold(D2SharedStash stash) {
        return stash.getPanes().stream().mapToInt(D2SharedStash.D2SharedStashPane::getGold).toArray();
    }

    private static void setGold(D2SharedStash stash, int[] gold) {
        for (int i = 0; i < gold.length; i++) {
            stash.replacePane(i, D2SharedStash.D2SharedStashPane.fromItems(stash.getPane(i).getItems(), gold[i]));
        }
    }

    private static List<String> itemBytes(D2SharedStash stash) {
        return stash.getItemList().stream().map(D2Item::get_bytes).map(Arrays::toString).collect(Collectors.toList());
    }
}
