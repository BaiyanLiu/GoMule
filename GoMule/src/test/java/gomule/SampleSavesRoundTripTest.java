package gomule;

import gomule.d2i.D2SharedStash;
import gomule.d2i.D2SharedStashReader;
import gomule.d2i.D2SharedStashWriter;
import gomule.d2s.D2Character;
import gomule.d2x.D2Stash;
import gomule.d2x.D2StashReader;
import gomule.d2x.D2StashWriter;
import gomule.item.D2Item;
import gomule.util.D2Project;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import randall.d2files.D2TxtFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static gomule.model.VersionController.Variant.ROW;
import static gomule.util.TestHelpers.loadTestResources;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Loads every sample save (characters, shared stashes and ATMA stashes in userSamples, plus the shared stash
 * fixtures), writes it back unchanged and checks the file is byte-identical and reads back the same.
 * Drop a new .d2s, .d2i or .d2x into src/test/resources/userSamples and it is checked too.
 */
public class SampleSavesRoundTripTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    public static void setup() {
        D2TxtFile.constructTxtFiles("./d2111");
    }

    static Stream<File> sampleSaves() {
        List<File> files = new ArrayList<>(Arrays.asList(Objects.requireNonNull(loadTestResources("userSamples").listFiles())));
        files.add(loadTestResources("sharedStashFiles", "modernGold.d2i"));
        List<File> saves = files.stream()
                .filter(file -> file.getName().matches("(?i).*\\.(d2s|d2i|d2x)"))
                .sorted()
                .collect(Collectors.toList());
        assertFalse(saves.isEmpty(), "no sample saves found");
        return saves.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sampleSaves")
    public void writesBackByteForByteAndReadsBackTheSame(File sample) throws Exception {
        File copy = tempDir.resolve(sample.getName()).toFile();
        Files.copy(sample.toPath(), copy.toPath());
        byte[] original = Files.readAllBytes(copy.toPath());
        String name = copy.getName().toLowerCase(Locale.ROOT);

        if (name.endsWith(".d2s")) {
            D2Character character = new D2Character(ROW, copy.getAbsolutePath());
            character.save(projectWithoutBackups());
            assertArrayEquals(original, Files.readAllBytes(copy.toPath()));
            D2Character readBack = new D2Character(ROW, copy.getAbsolutePath());
            assertEquals(character.getCharName(), readBack.getCharName());
            assertEquals(character.getGold(), readBack.getGold());
            assertEquals(character.getGoldBank(), readBack.getGoldBank());
            assertEquals(itemBytes(character.getItemList()), itemBytes(readBack.getItemList()));
        } else if (name.endsWith(".d2i")) {
            D2SharedStash stash = new D2SharedStashReader().readStash(ROW, copy.getAbsolutePath());
            new D2SharedStashWriter(ROW, copy, original).write(stash);
            assertArrayEquals(original, Files.readAllBytes(copy.toPath()));
            D2SharedStash readBack = new D2SharedStashReader().readStash(ROW, copy.getAbsolutePath());
            assertEquals(stash.getPanes().size(), readBack.getPanes().size());
            for (int i = 0; i < stash.getPanes().size(); i++) {
                assertEquals(stash.getPane(i).getGold(), readBack.getPane(i).getGold(), "gold on page " + (i + 1));
                assertEquals(itemBytes(stash.getPane(i).getItems()), itemBytes(readBack.getPane(i).getItems()), "items on page " + (i + 1));
            }
        } else {
            D2Stash stash = new D2StashReader().readStash(ROW, copy.getAbsolutePath());
            new D2StashWriter(ROW, copy.getAbsolutePath()).write(stash);
            assertArrayEquals(original, Files.readAllBytes(copy.toPath()));
            assertEquals(itemBytes(stash.getItemList()), itemBytes(new D2StashReader().readStash(ROW, copy.getAbsolutePath()).getItemList()));
        }
    }

    private static D2Project projectWithoutBackups() {
        D2Project project = mock(D2Project.class);
        when(project.getBackup()).thenReturn(D2Project.BACKUP_NONE);
        return project;
    }

    private static List<String> itemBytes(List<D2Item> items) {
        return items.stream().map(item -> Arrays.toString(item.get_bytes())).collect(Collectors.toList());
    }
}
