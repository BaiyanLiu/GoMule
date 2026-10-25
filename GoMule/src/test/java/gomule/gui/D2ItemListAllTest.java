package gomule.gui;

import gomule.util.D2Project;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

public class D2ItemListAllTest {

    private static final String CHAR = "Javazon.d2s";
    private static final String RUNES = "Runes.d2x";
    private static final String JUNK = "Junk.d2x";

    private final D2FileManager iFileManager = mock(D2FileManager.class);
    private final D2Project iProject = mock(D2Project.class);
    private final List<String> iInAll = new ArrayList<>(Arrays.asList(CHAR, RUNES));

    private D2ItemList listFor(String pFileName) throws Exception {
        D2ItemList lList = mock(D2ItemList.class);
        when(lList.getFilename()).thenReturn(pFileName);
        when(iFileManager.addItemList(eq(pFileName), isNull())).thenReturn(lList);
        when(iFileManager.getItemList(pFileName)).thenReturn(lList);
        return lList;
    }

    @Test
    public void loadsOnlyWhatTheProjectShowsInAll() throws Exception {
        when(iProject.getAllViewFiles()).thenReturn(iInAll);
        D2ItemList lChar = listFor(CHAR);
        D2ItemList lRunes = listFor(RUNES);
        listFor(JUNK);

        D2ItemListAll lAll = new D2ItemListAll(iFileManager, iProject);

        assertEquals(Arrays.asList(lChar, lRunes), lAll.getAllContainers());
        verify(iFileManager, never()).addItemList(eq(JUNK), any());
    }

    @Test
    public void addsAStashPutBackAndDropsOneLeftOut() throws Exception {
        when(iProject.getAllViewFiles()).thenReturn(iInAll);
        listFor(CHAR);
        D2ItemList lRunes = listFor(RUNES);
        D2ItemList lJunk = listFor(JUNK);
        D2ItemListAll lAll = new D2ItemListAll(iFileManager, iProject);
        D2ItemListListener lListener = mock(D2ItemListListener.class);
        lAll.addD2ItemListListener(lListener);

        // still left out: connecting it (as a reload of the file does) does nothing
        lAll.connect(JUNK);
        assertFalse(lAll.getAllContainers().contains(lJunk));

        iInAll.add(JUNK);
        lAll.connect(JUNK);
        assertTrue(lAll.getAllContainers().contains(lJunk));
        verify(lJunk).addD2ItemListListener(lListener);
        // a second connect does not show its items twice
        lAll.connect(JUNK);
        assertEquals(1, lAll.getAllContainers().stream().filter(lList -> lList == lJunk).count());

        iInAll.remove(RUNES);
        lAll.disconnect(RUNES);
        assertFalse(lAll.getAllContainers().contains(lRunes));
        verify(lRunes).removeD2ItemListListener(lListener);
        verify(iFileManager).removeItemList(RUNES, null);
    }

    @Test
    public void disconnectingAStashThatIsNotInAllLeavesItAlone() throws Exception {
        when(iProject.getAllViewFiles()).thenReturn(iInAll);
        listFor(CHAR);
        listFor(RUNES);
        listFor(JUNK); // open in its own window, not in All
        D2ItemListAll lAll = new D2ItemListAll(iFileManager, iProject);

        lAll.disconnect(JUNK);

        verify(iFileManager, never()).removeItemList(eq(JUNK), any());
        verify(iFileManager, never()).removeItemList(anyString(), isNull());
    }
}
