package gomule.gui.sharedStash;

import org.junit.jupiter.api.Test;

import static gomule.gui.sharedStash.SharedStashGoldTransferPanel.MAX_GOLD_PER_PANE;
import static gomule.gui.sharedStash.SharedStashGoldTransferPanel.clampTransferAmount;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SharedStashGoldTransferPanelTest {

    @Test
    public void transfersRequestedAmountWhenThereIsRoom() {
        assertEquals(10_000, clampTransferAmount(10_000, 100_000, 51_470, MAX_GOLD_PER_PANE));
    }

    @Test
    public void limitedBySourceGold() {
        assertEquals(51_470, clampTransferAmount(100_000, 51_470, 0, Integer.MAX_VALUE));
    }

    @Test
    public void limitedByRoomLeftInPane() {
        assertEquals(100_000, clampTransferAmount(500_000, 1_000_000, 2_400_000, MAX_GOLD_PER_PANE));
    }

    @Test
    public void nothingMovesIntoFullPane() {
        assertEquals(0, clampTransferAmount(10_000, 1_000_000, MAX_GOLD_PER_PANE, MAX_GOLD_PER_PANE));
    }

    @Test
    public void negativeAmountMovesNothing() {
        assertEquals(0, clampTransferAmount(-10_000, 1_000_000, 0, MAX_GOLD_PER_PANE));
    }
}
