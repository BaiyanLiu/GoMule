package gomule.d2i;

import gomule.d2x.D2Stash;
import gomule.d2x.D2StashReader;
import gomule.d2x.D2StashWriter;
import gomule.item.D2Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;

public class D2SharedStashToStashTransferTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void movingItemFromSharedStashToAtmaStashSurvivesWriteAndReadBack() throws Exception {
        File sharedStashFile = tempDir.resolve("ModernSharedStashSoftCoreV2.d2i").toFile();
        Files.copy(loadTestResources("sharedStashFiles", "modernGold.d2i").toPath(), sharedStashFile.toPath());
        String atmaStashFileName = tempDir.resolve("mule.d2x").toString();

        D2SharedStash sharedStash = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
        int paneIndex = firstPaneWithItems(sharedStash);
        D2SharedStash.D2SharedStashPane pane = sharedStash.getPane(paneIndex);
        int itemsInPaneBefore = pane.getItems().size();
        int totalGoldBefore = sharedStash.getTotalGold();
        D2Item item = pane.getItems().get(0);
        byte[] itemBytes = item.get_bytes().clone();

        D2Stash atmaStash = new D2StashReader().readStash(ROW, atmaStashFileName);
        assertEquals(0, atmaStash.getNrItems());

        sharedStash.replacePane(paneIndex, pane.removeItem(item));
        sharedStash.setModified(true);
        atmaStash.addItem(item);
        new D2SharedStashWriter(ROW, sharedStashFile, Files.readAllBytes(sharedStashFile.toPath())).write(sharedStash);
        new D2StashWriter(ROW, atmaStashFileName).write(atmaStash);

        D2SharedStash sharedStashReadBack = new D2SharedStashReader().readStash(ROW, sharedStashFile.getAbsolutePath());
        assertEquals(itemsInPaneBefore - 1, sharedStashReadBack.getPane(paneIndex).getItems().size());
        assertEquals(totalGoldBefore, sharedStashReadBack.getTotalGold());
        for (int i = 0; i < sharedStash.getPanes().size(); i++) {
            assertEquals(sharedStash.getPane(i).getItems().size(), sharedStashReadBack.getPane(i).getItems().size());
        }

        D2Stash atmaStashReadBack = new D2StashReader().readStash(ROW, atmaStashFileName);
        List<D2Item> movedItems = atmaStashReadBack.getItemList();
        assertEquals(1, movedItems.size());
        assertArrayEquals(itemBytes, movedItems.get(0).get_bytes());
        assertEquals(item.getItemName(), movedItems.get(0).getItemName());
    }

    private static int firstPaneWithItems(D2SharedStash sharedStash) {
        for (int i = 0; i < sharedStash.getPanes().size(); i++) {
            if (!sharedStash.getPane(i).getItems().isEmpty()) return i;
        }
        throw new IllegalStateException("No items in shared stash fixture");
    }
}
