package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.d2i.SharedStashSorter;
import gomule.d2x.StashSortRule;
import gomule.gui.D2FileManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The "Sort into ATMA stashes" button: previews where each shared stash item would go, then moves them.
 */
class SharedStashSortAction {

    private SharedStashSortAction() {
    }

    /**
     * Returns what was moved, for {@link #revert}; empty when nothing moved.
     */
    static List<SharedStashSorter.MovedItem> run(SharedStashPanel panel, D2FileManager fileManager) {
        D2SharedStash sharedStash = panel.getSharedStash();
        if (sharedStash == null || fileManager.getProject() == null) return Collections.emptyList();
        Map<String, List<StashSortRule>> rulesByStash = fileManager.getProject().getAllStashSortRules();
        if (rulesByStash.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "No ATMA stash has sorting rules yet.\n"
                    + "Right-click a .d2x stash in the project tree and choose \"Sorting rules...\".", "Sort into ATMA stashes", JOptionPane.INFORMATION_MESSAGE);
            return Collections.emptyList();
        }
        SharedStashSorter.Plan plan = SharedStashSorter.plan(sharedStash, rulesByStash);
        String summary = describe(plan);
        if (plan.getMoves().isEmpty()) {
            JOptionPane.showMessageDialog(panel, new Object[]{"Nothing to move.", previewPane(summary)}, "Sort into ATMA stashes", JOptionPane.INFORMATION_MESSAGE);
            return Collections.emptyList();
        }
        int answer = JOptionPane.showConfirmDialog(panel, new Object[]{previewPane(summary), "Move these items now?"}, "Sort into ATMA stashes",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return Collections.emptyList();

        List<SharedStashSorter.MovedItem> movedItems = new ArrayList<>();
        Map<String, Integer> moved = SharedStashSorter.apply(sharedStash, plan, fileManager::getItemListForDrop, movedItems);
        panel.build();
        int movedCount = moved.values().stream().mapToInt(Integer::intValue).sum();
        if (movedCount < plan.getMoves().size()) {
            JOptionPane.showMessageDialog(panel, (plan.getMoves().size() - movedCount)
                    + " items stayed in the shared stash because their ATMA stash could not be opened.", "Sort into ATMA stashes", JOptionPane.WARNING_MESSAGE);
        }
        return movedItems;
    }

    /**
     * The "Revert last sort" button: puts the last sort's items back into the shared stash.
     * Returns false when the user cancels, so the sort can still be reverted later.
     */
    static boolean revert(SharedStashPanel panel, D2FileManager fileManager, List<SharedStashSorter.MovedItem> lastSort) {
        D2SharedStash sharedStash = panel.getSharedStash();
        if (sharedStash == null || lastSort.isEmpty()) return true;
        int answer = JOptionPane.showConfirmDialog(panel, "Put the " + lastSort.size() + " items the last sort moved back into the shared stash?",
                "Revert last sort", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return false;
        // only stashes still loaded as they were: one closed (and so saved) since the sort keeps its items
        Map<Boolean, List<SharedStashSorter.MovedItem>> stillLoaded = lastSort.stream()
                .collect(Collectors.partitioningBy(moved -> fileManager.getItemList(moved.getStashFileName()) == moved.getStash()));
        List<SharedStashSorter.MovedItem> notReverted = new ArrayList<>(SharedStashSorter.revert(sharedStash, stillLoaded.get(true)));
        notReverted.addAll(stillLoaded.get(false));
        panel.build();
        if (!notReverted.isEmpty()) {
            JOptionPane.showMessageDialog(panel, new Object[]{notReverted.size() + " items stayed in their ATMA stash (moved or saved since the sort, or no room left):",
                    previewPane(notReverted.stream().map(moved -> moved.getItem().getItemName() + " in " + stashName(moved.getStashFileName()))
                            .collect(Collectors.joining("\n")))}, "Revert last sort", JOptionPane.WARNING_MESSAGE);
        }
        return true;
    }

    /**
     * The preview text: how many items go to each stash, then every item that stays and why.
     */
    static String describe(SharedStashSorter.Plan plan) {
        StringBuilder text = new StringBuilder();
        if (!plan.getMoves().isEmpty()) {
            text.append("Moving:\n");
            plan.getMoveCountsByStash().forEach((stash, count) -> text.append("    ").append(count).append(" to ").append(stashName(stash)).append('\n'));
        }
        if (!plan.getUnmatched().isEmpty()) {
            text.append("\nStaying, matches no stash (").append(plan.getUnmatched().size()).append("):\n");
            List<String> lines = new ArrayList<>();
            plan.getUnmatched().forEach(item -> lines.add(item.getItemName()));
            appendCounted(text, lines);
        }
        if (!plan.getAmbiguous().isEmpty()) {
            text.append("\nStaying, matches more than one stash (").append(plan.getAmbiguous().size()).append("):\n");
            List<String> lines = new ArrayList<>();
            plan.getAmbiguous().forEach((item, stashes) -> lines.add(item.getItemName() + ": "
                    + stashes.stream().map(SharedStashSortAction::stashName).collect(Collectors.joining(", "))));
            appendCounted(text, lines);
        }
        return text.toString();
    }

    /**
     * One line per distinct entry, in first-seen order, with "(x3)" when it repeats.
     */
    private static void appendCounted(StringBuilder text, List<String> lines) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        lines.forEach(line -> counts.merge(line, 1, Integer::sum));
        counts.forEach((line, count) -> text.append("    ").append(line).append(count > 1 ? " (x" + count + ")" : "").append('\n'));
    }

    private static String stashName(String stashFileName) {
        return new File(stashFileName).getName();
    }

    /**
     * The preview in a scrolling box, since every staying item is listed.
     */
    private static JComponent previewPane(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(area);
        Dimension size = area.getPreferredSize();
        scrollPane.setPreferredSize(new Dimension(Math.min(Math.max(size.width + 30, 320), 600), Math.min(size.height + 10, 360)));
        return scrollPane;
    }
}
