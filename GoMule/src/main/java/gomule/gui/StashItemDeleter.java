package gomule.gui;

import gomule.item.D2Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Deletes items from a stash (or any item list) in one go, and words the confirmation question for them.
 * Kept free of Swing so it can be tested without a screen.
 */
public final class StashItemDeleter {

    /** How many item names the confirmation lists before it says "and N more". */
    static final int MAX_NAMES_LISTED = 15;

    private StashItemDeleter() {
    }

    /** The question asked before deleting these items. */
    public static String confirmationMessage(List<D2Item> pItems) {
        if (pItems.size() == 1) {
            return "Delete " + pItems.get(0).getName() + "?";
        }
        StringBuilder lMessage = new StringBuilder("Delete these " + pItems.size() + " items?\n");
        int lListed = Math.min(pItems.size(), MAX_NAMES_LISTED);
        for (int i = 0; i < lListed; i++) {
            lMessage.append("\n  • ").append(pItems.get(i).getName());
        }
        if (pItems.size() > lListed) {
            lMessage.append("\n  …and ").append(pItems.size() - lListed).append(" more");
        }
        return lMessage.toString();
    }

    /**
     * Removes the items from the list, sending one change event at the end instead of one per item.
     *
     * @return the items that were in the list and are now removed
     */
    public static List<D2Item> delete(D2ItemList pList, List<D2Item> pItems) {
        List<D2Item> lRemoved = new ArrayList<>();
        try {
            pList.ignoreItemListEvents();
            for (D2Item lItem : pItems) {
                if (lItem != null && pList.containsItem(lItem)) {
                    pList.removeItem(lItem);
                    lRemoved.add(lItem);
                }
            }
        } finally {
            pList.listenItemListEvents();
        }
        return lRemoved;
    }
}
