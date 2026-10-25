package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.gui.D2FileManager;
import gomule.gui.D2ItemList;
import gomule.gui.D2ViewClipboard;
import gomule.gui.D2ViewProject;
import gomule.gui.ItemRightClickMenu;
import gomule.item.D2Item;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static gomule.gui.sharedStash.SharedStashPanel.*;

class SharedStashPanelMouseClickHandler extends MouseAdapter {

    private final SharedStashPanel sharedStashPanel;
    private D2Item draggedItem;

    public SharedStashPanelMouseClickHandler(SharedStashPanel sharedStashPanel) {
        this.sharedStashPanel = sharedStashPanel;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        draggedItem = e.getButton() == MouseEvent.BUTTON1 ? getItemAt(e.getX(), e.getY()) : null;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (draggedItem == null) return;
        String stashFileName = getViewProject().getStashFileAt(sharedStashPanel, e.getPoint());
        getViewProject().showDropTarget(stashFileName);
        if (stashFileName != null) {
            sharedStashPanel.setCursorDropItem();
        } else {
            sharedStashPanel.setCursorPickupItem();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        D2Item item = draggedItem;
        draggedItem = null;
        if (item != null) {
            getViewProject().clearDropTarget();
            String stashFileName = getViewProject().getStashFileAt(sharedStashPanel, e.getPoint());
            if (e.getButton() == MouseEvent.BUTTON1 && stashFileName != null) {
                moveItemToStash(item, stashFileName);
                return;
            }
        }
        if (e.getButton() == MouseEvent.BUTTON1) handleLeftClick(e);
        if (e.getButton() == MouseEvent.BUTTON3) handleRightClick(e);
    }

    private void moveItemToStash(D2Item item, String stashFileName) {
        D2SharedStash sharedStash = sharedStashPanel.getSharedStash();
        if (sharedStash == null) return;
        D2SharedStash.D2SharedStashPane stashPane = sharedStashPanel.getSelectedStashPane();
        if (!stashPane.getItems().contains(item)) return;
        D2ItemList stash = D2FileManager.getInstance().getItemListForDrop(stashFileName);
        if (stash == null) return;
        removeItem(stashPane, item);
        stash.addItem(item);
        sharedStashPanel.setCursorNormal();
    }

    private D2Item getItemAt(int x, int y) {
        if (sharedStashPanel.getSharedStash() == null) return null;
        int col = SharedStashPanel.getColForXCoord(x);
        int row = SharedStashPanel.getRowForYCoord(y);
        if (col < 0 || row < 0 || col > 9 || row > 9) return null;
        return sharedStashPanel.getSelectedStashPane().getItemCovering(col, row);
    }

    private static D2ViewProject getViewProject() {
        return D2FileManager.getInstance().getViewProject();
    }

    private void handleRightClick(MouseEvent e) {
        D2SharedStash sharedStash = sharedStashPanel.getSharedStash();
        if (sharedStash == null) return;
        int col = SharedStashPanel.getColForXCoord(e.getX());
        int row = SharedStashPanel.getRowForYCoord(e.getY());
        if (col < 0 || row < 0 || col > 9 || row > 9) return;
        D2SharedStash.D2SharedStashPane stashPane = sharedStashPanel.getSelectedStashPane();
        D2Item item = stashPane.getItemCovering(col, row);
        if (item != null) {
            new ItemRightClickMenu(item, this::deleteMenuItemAction).show(sharedStashPanel, e.getX(), e.getY() + 35);
        }
    }

    private void deleteMenuItemAction(D2Item d2Item) {
        removeItem(sharedStashPanel.getSelectedStashPane(), d2Item);
    }

    private void handleLeftClick(MouseEvent e) {
        D2SharedStash sharedStash = sharedStashPanel.getSharedStash();
        if (sharedStash == null) return;
        handleStashNavigationClicks(e.getX(), e.getY());
        if (isInGoldArea(e.getX(), e.getY())) showGoldDialog();

        int col = SharedStashPanel.getColForXCoord(e.getX());
        int row = SharedStashPanel.getRowForYCoord(e.getY());
        if (col < 0 || row < 0 || col > 9 || row > 9) return;
        D2SharedStash.D2SharedStashPane stashPane = sharedStashPanel.getSelectedStashPane();
        D2Item item = stashPane.getItemCovering(col, row);
        if (item != null) {
            moveItemToClipboard(stashPane, item);
        } else if (D2ViewClipboard.getItem() != null) {
            tryMoveItemFromClipboard(stashPane, col, row);
        }
    }

    private void showGoldDialog() {
        JOptionPane.showConfirmDialog(sharedStashPanel, new SharedStashGoldTransferPanel(sharedStashPanel), "Transfer Gold",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE);
        sharedStashPanel.build();
    }

    private void tryMoveItemFromClipboard(D2SharedStash.D2SharedStashPane stashPane, int col, int row) {
        D2SharedStash sharedStash = sharedStashPanel.getSharedStash();
        D2Item item = D2ViewClipboard.getItem();
        if (stashPane.canDropItem(col, row, item)) {
            D2SharedStash.D2SharedStashPane d2SharedStashPane = stashPane.addItem(col, row, D2ViewClipboard.removeItem());
            sharedStash.replacePane(sharedStashPanel.getSelectedStashPaneIndex(), d2SharedStashPane);
            sharedStash.setModified(true);
            sharedStashPanel.setCursorPickupItem();
        }
    }

    private void moveItemToClipboard(D2SharedStash.D2SharedStashPane stashPane, D2Item item) {
        removeItem(stashPane, item);
        D2ViewClipboard.addItem(item);
    }

    private void removeItem(D2SharedStash.D2SharedStashPane stashPane, D2Item item) {
        D2SharedStash sharedStash = sharedStashPanel.getSharedStash();
        D2SharedStash.D2SharedStashPane d2SharedStashPane = stashPane.removeItem(item);
        sharedStash.replacePane(sharedStashPanel.getSelectedStashPaneIndex(), d2SharedStashPane);
        sharedStash.setModified(true);
        sharedStashPanel.setCursorDropItem();
    }

    private void handleStashNavigationClicks(int x, int y) {
        if (isInRightStashSelectArea(x, y)) sharedStashPanel.moveToNextStashPane();
        if (isInLeftStashSelectArea(x, y)) sharedStashPanel.moveToPriorStashPane();
    }
}
