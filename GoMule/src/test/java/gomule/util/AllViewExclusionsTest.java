package gomule.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class AllViewExclusionsTest {

    private static final String RUNES = "C:\\Saved Games\\Diablo II Resurrected\\Runes.d2x";
    private static final String SETS = "C:\\Saved Games\\Diablo II Resurrected\\Sets.d2x";
    private static final String JUNK = "C:\\Saved Games\\Diablo II Resurrected\\Junk.d2x";

    @Test
    public void everyStashIsInAllUntilLeftOut() {
        AllViewExclusions lExclusions = new AllViewExclusions("stash");
        List<String> lStashes = Arrays.asList(JUNK, RUNES, SETS);
        assertEquals(lStashes, lExclusions.keep(lStashes));

        assertTrue(lExclusions.setExcluded(JUNK, true));
        assertFalse(lExclusions.setExcluded(JUNK, true), "already left out");
        assertTrue(lExclusions.isExcluded(JUNK));
        assertEquals(Arrays.asList(RUNES, SETS), lExclusions.keep(lStashes));

        assertTrue(lExclusions.setExcluded(JUNK, false));
        assertFalse(lExclusions.setExcluded(JUNK, false), "already shown");
        assertEquals(lStashes, lExclusions.keep(lStashes));
    }

    @Test
    public void survivesSavingAndLoadingTheProjectFile() throws Exception {
        List<String> lStashes = Arrays.asList(JUNK, RUNES, SETS);
        AllViewExclusions lExclusions = new AllViewExclusions("stash");
        lExclusions.setExcluded(JUNK, true);
        lExclusions.setExcluded(SETS, true);

        Properties lSaved = new Properties();
        for (int i = 0; i < lStashes.size(); i++) lSaved.put("stash." + i, lStashes.get(i));
        lExclusions.save(lSaved, lStashes);
        assertEquals("true", lSaved.getProperty("stash.0.notInAll"));
        assertNull(lSaved.getProperty("stash.1.notInAll"));
        assertEquals("true", lSaved.getProperty("stash.2.notInAll"));

        // through the file format too, as D2Project writes it
        ByteArrayOutputStream lOut = new ByteArrayOutputStream();
        lSaved.store(lOut, null);
        Properties lLoaded = new Properties();
        lLoaded.load(new ByteArrayInputStream(lOut.toByteArray()));

        AllViewExclusions lRead = new AllViewExclusions("stash");
        lRead.load(lLoaded);
        assertTrue(lRead.isExcluded(JUNK));
        assertFalse(lRead.isExcluded(RUNES));
        assertTrue(lRead.isExcluded(SETS));
        assertEquals(Collections.singletonList(RUNES), lRead.keep(lStashes));
    }

    @Test
    public void anOldProjectFileHasNothingLeftOut() {
        Properties lOld = new Properties();
        lOld.put("stash.0", RUNES);
        lOld.put("stash.0.folder", "Mules");
        AllViewExclusions lRead = new AllViewExclusions("stash");
        lRead.load(lOld);
        assertFalse(lRead.isExcluded(RUNES));
    }

    @Test
    public void aRemovedStashIsForgottenAndNotWrittenBack() {
        AllViewExclusions lExclusions = new AllViewExclusions("stash");
        lExclusions.setExcluded(JUNK, true);
        lExclusions.removeFile(JUNK);
        assertFalse(lExclusions.isExcluded(JUNK));

        lExclusions.setExcluded(RUNES, true);
        Properties lSaved = new Properties();
        // RUNES is no longer in the project's stash list, so nothing is written for it
        lExclusions.save(lSaved, Collections.singletonList(SETS));
        assertTrue(lSaved.isEmpty());
    }

    @Test
    public void onlyTheExactTrueValueCountsWhenLoading() {
        Properties lProps = new Properties();
        lProps.put("stash.0", RUNES);
        lProps.put("stash.0.notInAll", "TRUE");
        lProps.put("stash.1", SETS);
        lProps.put("stash.1.notInAll", "no");
        AllViewExclusions lRead = new AllViewExclusions("stash");
        lRead.load(lProps);
        assertTrue(lRead.isExcluded(RUNES));
        assertFalse(lRead.isExcluded(SETS));
    }
}
