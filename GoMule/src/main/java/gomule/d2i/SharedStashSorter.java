package gomule.d2i;

import gomule.d2x.StashSortRule;
import gomule.gui.D2ItemList;
import gomule.item.D2Item;

import java.util.*;
import java.util.function.Function;

/**
 * Works out which ATMA (.d2x) stash each shared stash item belongs in, from the stashes' sorting rules,
 * and moves the items there. An item belongs in a stash when it matches all of that stash's rules; one that
 * matches no stash, or more than one, stays in the shared stash.
 */
public class SharedStashSorter {

    private SharedStashSorter() {
    }

    public static Plan plan(D2SharedStash sharedStash, Map<String, List<StashSortRule>> rulesByStash) {
        Plan plan = new Plan();
        for (D2Item item : sharedStash.getItemList()) {
            List<String> matchingStashes = new ArrayList<>();
            rulesByStash.forEach((stashFileName, rules) -> {
                if (StashSortRule.matchesAll(rules, item)) matchingStashes.add(stashFileName);
            });
            if (matchingStashes.isEmpty()) {
                plan.unmatched.add(item);
            } else if (matchingStashes.size() > 1) {
                plan.ambiguous.put(item, matchingStashes);
            } else {
                plan.moves.put(item, matchingStashes.get(0));
            }
        }
        return plan;
    }

    /**
     * Moves the planned items, returning how many went to each stash. Items whose stash can't be
     * loaded (lookup returns null) stay in the shared stash.
     */
    public static Map<String, Integer> apply(D2SharedStash sharedStash, Plan plan, Function<String, D2ItemList> stashLookup) {
        return apply(sharedStash, plan, stashLookup, new ArrayList<>());
    }

    /**
     * As {@link #apply(D2SharedStash, Plan, Function)}, also adding each moved item to movedItems so {@link #revert} can put it back.
     */
    public static Map<String, Integer> apply(D2SharedStash sharedStash, Plan plan, Function<String, D2ItemList> stashLookup, List<MovedItem> movedItems) {
        Map<String, Optional<D2ItemList>> stashes = new HashMap<>();
        Map<String, Integer> moved = new LinkedHashMap<>();
        for (int paneIndex = 0; paneIndex < sharedStash.getPanes().size(); paneIndex++) {
            D2SharedStash.D2SharedStashPane pane = sharedStash.getPane(paneIndex);
            for (D2Item item : new ArrayList<>(pane.getItems())) {
                String stashFileName = plan.moves.get(item);
                if (stashFileName == null) continue;
                // looked up once per stash, so a stash that can't be opened is reported once, not once per item
                D2ItemList stash = stashes.computeIfAbsent(stashFileName, name -> Optional.ofNullable(stashLookup.apply(name))).orElse(null);
                if (stash == null) continue;
                movedItems.add(new MovedItem(item, paneIndex, item.get_col(), item.get_row(), stashFileName, stash));
                pane = pane.removeItem(item);
                stash.addItem(item);
                moved.merge(stashFileName, 1, Integer::sum);
            }
            sharedStash.replacePane(paneIndex, pane);
        }
        if (!moved.isEmpty()) sharedStash.setModified(true);
        return moved;
    }

    /**
     * Undoes a sort: takes each moved item back out of its ATMA stash and puts it in the shared stash, on its old page
     * and spot when that is still free, otherwise in the first free spot on its old page, then on any page.
     * Returns the items that stayed in their ATMA stash, because they are no longer there or the shared stash is full.
     */
    public static List<MovedItem> revert(D2SharedStash sharedStash, List<MovedItem> movedItems) {
        List<MovedItem> notReverted = new ArrayList<>();
        boolean changed = false;
        for (MovedItem moved : movedItems) {
            if (!moved.stash.getItemList().contains(moved.item) || !putBack(sharedStash, moved)) {
                notReverted.add(moved);
                continue;
            }
            moved.stash.removeItem(moved.item);
            changed = true;
        }
        if (changed) sharedStash.setModified(true);
        return notReverted;
    }

    private static boolean putBack(D2SharedStash sharedStash, MovedItem moved) {
        D2SharedStash.D2SharedStashPane pane = sharedStash.getPane(moved.paneIndex);
        if (pane.canDropItem(moved.col, moved.row, moved.item)) {
            sharedStash.replacePane(moved.paneIndex, pane.addItem(moved.col, moved.row, moved.item));
            return true;
        }
        if (dropAnywhere(sharedStash, moved.paneIndex, moved.item)) return true;
        for (int paneIndex = 0; paneIndex < sharedStash.getPanes().size(); paneIndex++) {
            if (dropAnywhere(sharedStash, paneIndex, moved.item)) return true;
        }
        return false;
    }

    private static boolean dropAnywhere(D2SharedStash sharedStash, int paneIndex, D2Item item) {
        D2SharedStash.D2SharedStashPane pane = sharedStash.getPane(paneIndex);
        for (int row = 0; row < 10; row++) {
            for (int col = 0; col < 10; col++) {
                if (pane.canDropItem(col, row, item)) {
                    sharedStash.replacePane(paneIndex, pane.addItem(col, row, item));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Where a sorted item came from and where it went.
     */
    public static class MovedItem {
        private final D2Item item;
        private final int paneIndex;
        private final int col;
        private final int row;
        private final String stashFileName;
        private final D2ItemList stash;

        MovedItem(D2Item item, int paneIndex, int col, int row, String stashFileName, D2ItemList stash) {
            this.item = item;
            this.paneIndex = paneIndex;
            this.col = col;
            this.row = row;
            this.stashFileName = stashFileName;
            this.stash = stash;
        }

        public D2Item getItem() {
            return item;
        }

        public String getStashFileName() {
            return stashFileName;
        }

        public D2ItemList getStash() {
            return stash;
        }
    }

    public static class Plan {
        private final Map<D2Item, String> moves = new LinkedHashMap<>();
        private final Map<D2Item, List<String>> ambiguous = new LinkedHashMap<>();
        private final List<D2Item> unmatched = new ArrayList<>();

        public Map<D2Item, String> getMoves() {
            return moves;
        }

        public Map<D2Item, List<String>> getAmbiguous() {
            return ambiguous;
        }

        public List<D2Item> getUnmatched() {
            return unmatched;
        }

        public Map<String, Integer> getMoveCountsByStash() {
            Map<String, Integer> counts = new TreeMap<>();
            moves.values().forEach(stash -> counts.merge(stash, 1, Integer::sum));
            return counts;
        }
    }
}
