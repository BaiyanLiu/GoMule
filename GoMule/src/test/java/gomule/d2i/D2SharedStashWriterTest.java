package gomule.d2i;

import com.google.common.io.BaseEncoding;
import gomule.model.VersionController;
import gomule.model.VersionController.VersionException;
import gomule.util.D2BitReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.nio.file.Files;

import static gomule.d2i.D2SharedStash.D2SharedStashPane;
import static gomule.item.D2ItemTest.HEALTH_POT;
import static gomule.item.D2ItemTest.SMALL_CHARM;
import static gomule.model.VersionController.Variant.EXPANSION;
import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadItem;
import static gomule.util.TestHelpers.loadTestResources;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

public class D2SharedStashWriterTest {

    public static final String EMPTY_STASH =
            "55AA55AA0000000069000000000000004400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D0000";
    private static final String STASH_WITH_POTION =
            "55AA55AA0000000069000000000000004E00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D01001004A0080588144FB400";
    private static final String STASH_WITH_POTION_AND_CHARM =
            "55AA55AA0000000069000000000000006400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D02001004A0080588144FB40010008000059054D84FD88E840E0B50B00C0068F1EC3F";
    private static final String STASH_WITH_3_POTIONS =
            "55AA55AA0000000069000000000000006200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D03001004A0080580144FB4001004A00805C0144FB4001004A0080500154FB400";
    private static final String STASH_WITH_EXTRA_JM_IN_ITEM =
            "55AA55AA0200000069000000E9280000C301000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004A4D0D0010008000051CF4FCD890C739BFC5B522FA972E23B230CB312C674485E24271825C8A672BBA29393F15CC88243C3CFE031000800005A454D86D601A024A4D5210C76398400898CD7F1000800005A014DD00C2DA1FA265921600564197D17F100080000518F4FCD8C07F16B557A560189090221B94BBFF00100080000558F4FCD81038B54371B5B1DA1727F5922E2A04004412E89D2897A5FE03100880040500142B68B30265083D21070A4806F31F4049809280D280D29547975B9D409EBA7A585E3AF3FE031000A0003500E07C6F031000A0003504E07CFB001000A0003508E07C3E011000800005DC14DD000249EAC13197026856410B705D46FF011008800005D815DE173C2124B3B702CB703840FF011000800005D0151E0A2A8288A45681746462F80F100080000588D5AF316431F2F854053221FC071000C0000500763E0DC2C2CC63553103B27171C1097229C8AD20D782DC7F100080000500D5CB1AE29E4C11B9029913FE031000800005E05458267899D3FD59A900E0300028F90F";

    @BeforeEach
    public void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    @Test
    public void writeReplacingSingleItem() throws Exception {
        byte[] simpleStash = BaseEncoding.base16().decode(EMPTY_STASH + EMPTY_STASH + EMPTY_STASH);
        D2SharedStash stash = new D2SharedStash(EXPANSION,
                "",
                asList(
                        D2SharedStashPane.fromItems(emptyList(), 0),
                        D2SharedStashPane.fromItems(singletonList(loadItem(HEALTH_POT)), 0),
                        D2SharedStashPane.fromItems(emptyList(), 0)),
                simpleStash);
        byte[] expected = BaseEncoding.base16().decode(EMPTY_STASH + STASH_WITH_POTION + EMPTY_STASH);
        runTest(simpleStash, stash, expected);
    }

    @Test
    public void writeItemsOfDifferentSizes() throws Exception {
        byte[] simpleStash = BaseEncoding.base16().decode(EMPTY_STASH + EMPTY_STASH + EMPTY_STASH);
        D2SharedStash stash = new D2SharedStash(EXPANSION,
                "",
                asList(
                        D2SharedStashPane.fromItems(emptyList(), 0),
                        D2SharedStashPane.fromItems(asList(loadItem(HEALTH_POT), loadItem(SMALL_CHARM, 4, 2)), 0),
                        D2SharedStashPane.fromItems(emptyList(), 0)),
                simpleStash);
        byte[] expected = BaseEncoding.base16().decode(EMPTY_STASH + STASH_WITH_POTION_AND_CHARM + EMPTY_STASH);
        runTest(simpleStash, stash, expected);
    }

    @Test
    public void writeReplacingMultipleItems() throws Exception {
        byte[] simpleStash = BaseEncoding.base16().decode(EMPTY_STASH + EMPTY_STASH + EMPTY_STASH);
        D2SharedStash stash = new D2SharedStash(EXPANSION,
                "",
                asList(
                        D2SharedStashPane.fromItems(emptyList(), 0),
                        D2SharedStashPane.fromItems(
                                asList(
                                        loadItem(HEALTH_POT, 0, 2),
                                        loadItem(HEALTH_POT, 0, 3),
                                        loadItem(HEALTH_POT, 0, 4)),
                                0),
                        D2SharedStashPane.fromItems(emptyList(), 0)),
                simpleStash);
        byte[] expected = BaseEncoding.base16().decode(EMPTY_STASH + STASH_WITH_3_POTIONS + EMPTY_STASH);
        runTest(simpleStash, stash, expected);
    }

    @Test
    public void writeReplacingAllItems() throws Exception {
        byte[] simpleStash = BaseEncoding.base16().decode(EMPTY_STASH + EMPTY_STASH + EMPTY_STASH);
        D2SharedStash stash = new D2SharedStash(EXPANSION,
                "",
                asList(
                        D2SharedStashPane.fromItems(singletonList(loadItem(HEALTH_POT)), 0),
                        D2SharedStashPane.fromItems(singletonList(loadItem(HEALTH_POT)), 0),
                        D2SharedStashPane.fromItems(singletonList(loadItem(HEALTH_POT)), 0)),
                simpleStash);
        byte[] expected = BaseEncoding.base16().decode(STASH_WITH_POTION + STASH_WITH_POTION + STASH_WITH_POTION);
        runTest(simpleStash, stash, expected);
    }

    @Test
    public void roundTripWithExtraJms() throws Exception {
        byte[] stashBytes = BaseEncoding.base16().decode(EMPTY_STASH + EMPTY_STASH + STASH_WITH_EXTRA_JM_IN_ITEM);
        D2SharedStash stash =
                new D2SharedStashReader().readStash(EXPANSION, "JMExampleStash.d2i", new D2BitReader(stashBytes.clone()));
        runTest(stashBytes, stash, stashBytes.clone());
    }

    @Test
    public void writeWrongVariant() throws Exception {
        File tempFile = File.createTempFile("d2SharedStashWriterTest", null);
        byte[] originalContent = BaseEncoding.base16().decode(EMPTY_STASH);
        D2SharedStash sharedStash = new D2SharedStash(EXPANSION, "filename", singletonList(D2SharedStashPane.fromItems(emptyList(), 0)), originalContent);
        D2SharedStashWriter writer = new D2SharedStashWriter(EXPANSION, tempFile, originalContent);
        assertEquals("Please change the workspace variant before loading this file.\n" +
                "Current Workspace: Expansion\n" +
                "File Needs: Unknown", assertThrows(VersionException.class, () -> writer.write(sharedStash)).getMessage());
    }

    @Test
    public void simpleRowStash_roundTrip_preservesTailPanes() throws Exception {
        D2BitReader bitReader = new D2BitReader(loadTestResources("sharedStashFiles", "modern.d2i").getAbsolutePath());
        D2SharedStash stash =
                new D2SharedStashReader().readStash(ROW, "somethingSoftCore.d2i", bitReader);
        runTest(bitReader.getFileContent().clone(), stash, bitReader.getFileContent().clone(), ROW);
    }

    @Test
    public void rowStashWithGoldOnMultiplePanes_roundTrip() throws Exception {
        D2BitReader bitReader = new D2BitReader(loadTestResources("sharedStashFiles", "modernGold.d2i").getAbsolutePath());
        D2SharedStash stash =
                new D2SharedStashReader().readStash(ROW, "ModernSharedStashSoftCoreV2.d2i", bitReader);
        runTest(bitReader.getFileContent().clone(), stash, bitReader.getFileContent().clone(), ROW);
    }

    @Test
    public void writeGoldOnSecondPane() throws Exception {
        byte[] simpleStash = BaseEncoding.base16().decode(EMPTY_STASH + EMPTY_STASH + EMPTY_STASH);
        D2SharedStash stash = new D2SharedStash(EXPANSION,
                "",
                asList(
                        D2SharedStashPane.fromItems(emptyList(), 2_500_000),
                        D2SharedStashPane.fromItems(emptyList(), 51_470),
                        D2SharedStashPane.fromItems(emptyList(), 0)),
                simpleStash);
        byte[] expected = BaseEncoding.base16().decode(
                EMPTY_STASH.replaceFirst("69000000000000004400", "69000000A02526004400")
                        + EMPTY_STASH.replaceFirst("69000000000000004400", "690000000EC900004400")
                        + EMPTY_STASH);
        runTest(simpleStash, stash, expected);
    }

    private void runTest(byte[] originalStashBytes, D2SharedStash stash, byte[] expected) throws Exception {
        runTest(originalStashBytes, stash, expected, EXPANSION);
    }

    private void runTest(byte[] originalStashBytes, D2SharedStash stash, byte[] expected, VersionController.Variant variant) throws Exception {
        File tempFile = File.createTempFile("d2SharedStashWriterTest", null);
        D2SharedStashWriter writer = new D2SharedStashWriter(variant, tempFile, originalStashBytes);
        writer.write(stash);
        byte[] actual = Files.readAllBytes(tempFile.toPath());
        assertArrayEquals(expected, actual);
        D2SharedStash readBackStash = new D2SharedStashReader().readStash(variant, "foo", new D2BitReader(actual));
        assertEquals(stash.getPanes().size(), readBackStash.getPanes().size());
        for (int i = 0; i < stash.getPanes().size(); i++) {
            D2SharedStashPane pane = stash.getPane(i);
            D2SharedStashPane readBackPane = readBackStash.getPane(i);
            assertEquals(pane.getGold(), readBackPane.getGold());
            assertEquals(pane.getItems().size(), readBackPane.getItems().size());
            for (int j = 0; j < pane.getItems().size(); j++) {
                assertArrayEquals(
                        pane.getItems().get(j).get_bytes(),
                        readBackPane.getItems().get(j).get_bytes());
            }
        }
    }
}
