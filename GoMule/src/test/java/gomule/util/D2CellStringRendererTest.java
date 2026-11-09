package gomule.util;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

public class D2CellStringRendererTest {

    // GoMule.itemTable.selectionBackground in GoMuleModernLaf.properties
    private static final Color MODERN_SELECTION = new Color(0x6b, 0x56, 0x28);

    @Test
    public void contrastIsTheWcagRatioAndDoesNotDependOnOrder() {
        assertEquals(21.0, D2CellStringRenderer.contrast(Color.black, Color.white), 0.01);
        assertEquals(1.0, D2CellStringRenderer.contrast(MODERN_SELECTION, MODERN_SELECTION), 0.0001);
        assertEquals(D2CellStringRenderer.contrast(Color.gray, MODERN_SELECTION), D2CellStringRenderer.contrast(MODERN_SELECTION, Color.gray), 0.0001);
    }

    @Test
    public void aColorThatAlreadyReadsWellIsKept() {
        assertEquals(Color.white, D2CellStringRenderer.readableOn(Color.white, MODERN_SELECTION));
        Color unique = new Color(0xe6, 0xd2, 0x96);
        assertTrue(D2CellStringRenderer.contrast(unique, MODERN_SELECTION) >= 4.5);
        assertEquals(unique, D2CellStringRenderer.readableOn(unique, MODERN_SELECTION));
    }

    @Test
    public void dimItemColorsAreLightenedJustEnoughToRead() {
        for (Color dim : new Color[]{new Color(108, 145, 255), Color.gray, Color.green.darker(), Color.black}) {
            Color shown = D2CellStringRenderer.readableOn(dim, MODERN_SELECTION);
            assertTrue(D2CellStringRenderer.contrast(shown, MODERN_SELECTION) >= 4.5, dim + " became " + shown);
            // lightened toward white, never darkened
            assertTrue(shown.getRed() >= dim.getRed() && shown.getGreen() >= dim.getGreen() && shown.getBlue() >= dim.getBlue(), dim + " became " + shown);
            assertNotEquals(Color.white, shown, "lightened all the way to white: " + dim);
        }
    }

    @Test
    public void onABackgroundNothingReadsOnTheColorStopsAtWhite() {
        assertEquals(Color.white, D2CellStringRenderer.readableOn(Color.gray, new Color(200, 200, 200)));
    }
}
