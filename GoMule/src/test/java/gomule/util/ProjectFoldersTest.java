package gomule.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectFoldersTest {

    private static final String JAVAZON = "C:\\Saved Games\\Diablo II Resurrected\\Javazon.d2s";
    private static final String SORC = "C:\\Saved Games\\Diablo II Resurrected\\Sorc.d2s";

    @Test
    public void makesNestedFoldersAndSortsThemFolderByFolder() {
        ProjectFolders lFolders = new ProjectFolders("char");
        assertEquals("Mules", lFolders.addFolder("", "  Mules "));
        assertEquals("Mules/Rings", lFolders.addFolder("Mules", "Rings"));
        assertEquals("Mules A", lFolders.addFolder("", "Mules A"));
        assertEquals("ladder", lFolders.addFolder("", "ladder"));
        assertEquals(Arrays.asList("ladder", "Mules", "Mules/Rings", "Mules A"), lFolders.getFolders());
    }

    @Test
    public void refusesBadOrTakenNames() {
        ProjectFolders lFolders = new ProjectFolders("char");
        lFolders.addFolder("", "Mules");
        assertNull(lFolders.addFolder("", "mules"));
        assertNull(lFolders.addFolder("", "  "));
        assertNull(lFolders.addFolder("", "a/b"));
        assertNull(lFolders.addFolder("Missing", "x"));
        assertEquals(Collections.singletonList("Mules"), lFolders.getFolders());
    }

    @Test
    public void renameCarriesSubfoldersAndFiles() {
        ProjectFolders lFolders = new ProjectFolders("char");
        lFolders.addFolder("", "Mules");
        lFolders.addFolder("Mules", "Rings");
        lFolders.moveFile(JAVAZON, "Mules/Rings");
        assertEquals("Storage", lFolders.renameFolder("Mules", "Storage"));
        assertEquals(Arrays.asList("Storage", "Storage/Rings"), lFolders.getFolders());
        assertEquals("Storage/Rings", lFolders.getFolderOf(JAVAZON));
        // case-only rename of itself is fine, clashing with a neighbour is not
        assertEquals("storage", lFolders.renameFolder("Storage", "storage"));
        lFolders.addFolder("", "Other");
        assertNull(lFolders.renameFolder("Other", "STORAGE"));
    }

    @Test
    public void deleteMovesContentsUpAndMergesClashingFolders() {
        ProjectFolders lFolders = new ProjectFolders("stash");
        lFolders.addFolder("", "Old");
        lFolders.addFolder("Old", "Runes");
        lFolders.addFolder("", "runes");
        lFolders.moveFile(JAVAZON, "Old");
        lFolders.moveFile(SORC, "Old/Runes");
        lFolders.deleteFolder("Old");
        assertEquals(Collections.singletonList("runes"), lFolders.getFolders());
        assertEquals("", lFolders.getFolderOf(JAVAZON));
        assertEquals("runes", lFolders.getFolderOf(SORC));
    }

    @Test
    public void folderCannotMoveIntoItself() {
        ProjectFolders lFolders = new ProjectFolders("stash");
        lFolders.addFolder("", "A");
        lFolders.addFolder("A", "B");
        lFolders.addFolder("", "C");
        assertNull(lFolders.moveFolder("A", "A/B"));
        assertNull(lFolders.moveFolder("A", "A"));
        assertEquals("C/A", lFolders.moveFolder("A", "C"));
        assertEquals(Arrays.asList("C", "C/A", "C/A/B"), lFolders.getFolders());
        assertEquals("A", lFolders.moveFolder("C/A", ""));
    }

    @Test
    public void moveFileOnlyToExistingFolders() {
        ProjectFolders lFolders = new ProjectFolders("char");
        assertFalse(lFolders.moveFile(JAVAZON, "Nope"));
        lFolders.addFolder("", "Mules");
        assertTrue(lFolders.moveFile(JAVAZON, "mules"));
        assertEquals("Mules", lFolders.getFolderOf(JAVAZON));
        assertTrue(lFolders.moveFile(JAVAZON, ""));
        assertEquals("", lFolders.getFolderOf(JAVAZON));
    }

    @Test
    public void savesAndLoadsThroughTheProjectFile() throws Exception {
        List<String> lChars = Arrays.asList(JAVAZON, SORC);
        ProjectFolders lFolders = new ProjectFolders("char");
        lFolders.addFolder("", "Mules");
        lFolders.addFolder("Mules", "Empty one");
        lFolders.moveFile(SORC, "Mules");

        Properties lSaved = new Properties();
        for (int i = 0; i < lChars.size(); i++) lSaved.put("char." + i, lChars.get(i));
        lFolders.save(lSaved, lChars);
        ByteArrayOutputStream lOut = new ByteArrayOutputStream();
        lSaved.store(lOut, null);
        Properties lLoaded = new Properties();
        lLoaded.load(new ByteArrayInputStream(lOut.toByteArray()));

        ProjectFolders lRead = new ProjectFolders("char");
        lRead.load(lLoaded);
        assertEquals(Arrays.asList("Mules", "Mules/Empty one"), lRead.getFolders());
        assertEquals("", lRead.getFolderOf(JAVAZON));
        assertEquals("Mules", lRead.getFolderOf(SORC));
        assertEquals(Collections.singletonList(SORC), lRead.getFilesIn("Mules", lChars));
        // The other parts of the tree don't pick these up.
        ProjectFolders lStashes = new ProjectFolders("stash");
        lStashes.load(lLoaded);
        assertTrue(lStashes.getFolders().isEmpty());
    }

    @Test
    public void oldProjectFilesLoadWithNoFolders() {
        Properties lOld = new Properties();
        lOld.put("char.0", JAVAZON);
        ProjectFolders lFolders = new ProjectFolders("char");
        lFolders.load(lOld);
        assertTrue(lFolders.getFolders().isEmpty());
        assertEquals("", lFolders.getFolderOf(JAVAZON));
    }

    @Test
    public void fileInAMissingFolderMakesIt() {
        Properties lEdited = new Properties();
        lEdited.put("char.0", JAVAZON);
        lEdited.put("char.0.folder", "A//B ");
        ProjectFolders lFolders = new ProjectFolders("char");
        lFolders.load(lEdited);
        assertEquals(Arrays.asList("A", "A/B"), lFolders.getFolders());
        assertEquals("A/B", lFolders.getFolderOf(JAVAZON));
    }
}
