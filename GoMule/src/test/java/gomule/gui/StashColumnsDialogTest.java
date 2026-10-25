package gomule.gui;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StashColumnsDialogTest {

    private static final Dimension SIZE = new Dimension(500, 280);
    private static final Rectangle SCREEN = new Rectangle(0, 0, 1280, 760);

    @Test
    public void opensWithItsCornerAtTheClick() {
        assertEquals(new Point(300, 200), StashColumnsDialog.fitOnScreen(new Point(300, 200), SIZE, SCREEN));
    }

    @Test
    public void isMovedBackOnScreenNearTheEdges() {
        assertEquals(new Point(780, 480), StashColumnsDialog.fitOnScreen(new Point(1200, 700), SIZE, SCREEN));
        assertEquals(new Point(0, 0), StashColumnsDialog.fitOnScreen(new Point(-50, -10), SIZE, SCREEN));
    }

    @Test
    public void staysOnASecondScreenLeftOfTheMainOne() {
        Rectangle left = new Rectangle(-1920, 0, 1920, 1040);
        assertEquals(new Point(-500, 760), StashColumnsDialog.fitOnScreen(new Point(-10, 1000), SIZE, left));
    }

    @Test
    public void topLeftWinsOnAScreenSmallerThanTheDialog() {
        assertEquals(new Point(0, 0), StashColumnsDialog.fitOnScreen(new Point(100, 100), SIZE, new Rectangle(0, 0, 400, 200)));
    }
}
