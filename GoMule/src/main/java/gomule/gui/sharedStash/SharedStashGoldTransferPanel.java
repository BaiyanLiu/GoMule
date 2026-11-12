package gomule.gui.sharedStash;

import gomule.d2i.D2SharedStash;
import gomule.gui.D2FileManager;
import randall.util.RandallPanel;

import javax.swing.*;
import java.awt.*;

class SharedStashGoldTransferPanel extends JPanel {
    static final int MAX_GOLD_PER_PANE = 2_500_000;
    private final SharedStashPanel sharedStashPanel;

    public SharedStashGoldTransferPanel(SharedStashPanel sharedStashPanel) {
        super(new GridLayout(0, 1));
        this.sharedStashPanel = sharedStashPanel;
        setSize(300, 100);
        setPreferredSize(new Dimension(300, 100));
        JTextField transferGoldAmount = new JTextField("10000");
        JButton transferGoldOut = new JButton("From Stash");
        transferGoldOut.addActionListener(pEvent -> transferGoldOut(getGoldAmount(transferGoldAmount)));
        JButton transferGoldIn = new JButton("To Stash");
        transferGoldIn.addActionListener(pEvent -> transferGoldIn(getGoldAmount(transferGoldAmount)));

        RandallPanel transferPanel = new RandallPanel(true);
        transferPanel.setBorder("Transfer");
        transferPanel.addToPanel(transferGoldIn, 0, 0, 1, RandallPanel.NONE);
        transferPanel.addToPanel(transferGoldAmount, 1, 0, 1, RandallPanel.HORIZONTAL);
        transferPanel.addToPanel(transferGoldOut, 2, 0, 1, RandallPanel.NONE);
        add(transferPanel);
    }

    /**
     * "From Stash": takes gold out of the shared stash into the GoMule bank. Each stash page holds at most
     * {@link #MAX_GOLD_PER_PANE}, but the stash shows (and the user can take) the total of all pages, so this
     * empties the selected page first and then the other pages until the amount is reached.
     */
    private void transferGoldOut(int goldAmount) {
        int[] paneGold = getPaneGold();
        int bank = getBankGoldValue();
        int amount = Math.max(0, Math.min(goldAmount, roomInBank(bank)));
        int[] newPaneGold = withdraw(paneGold, sharedStashPanel.getSelectedStashPaneIndex(), amount);
        int moved = total(paneGold) - total(newPaneGold);
        if (moved <= 0) return;
        updatePaneGold(paneGold, newPaneGold);
        updateBankGoldValue(bank + moved);
    }

    /**
     * "To Stash": puts gold from the GoMule bank into the shared stash. Fills the selected page up to
     * {@link #MAX_GOLD_PER_PANE} first, then the other item pages, so no page goes over the game's limit.
     */
    private void transferGoldIn(int goldAmount) {
        int[] paneGold = getPaneGold();
        int bank = getBankGoldValue();
        int amount = Math.max(0, Math.min(goldAmount, bank));
        int[] newPaneGold = deposit(paneGold, sharedStashPanel.getSelectedStashPaneIndex(), getItemPaneCount(), amount, MAX_GOLD_PER_PANE);
        int moved = total(newPaneGold) - total(paneGold);
        if (moved <= 0) return;
        updatePaneGold(paneGold, newPaneGold);
        updateBankGoldValue(bank - moved);
    }

    private void updateBankGoldValue(int gold) {
        D2FileManager.getInstance().getProject().setBankValue(gold);
    }

    private int getBankGoldValue() {
        return D2FileManager.getInstance().getProject().getBankValue();
    }

    private int[] getPaneGold() {
        return sharedStashPanel.getSharedStash().getPanes().stream().mapToInt(D2SharedStash.D2SharedStashPane::getGold).toArray();
    }

    private int getItemPaneCount() {
        int configured = D2FileManager.getInstance().getVariant().getSharedStashConfig().getItemStashPaneCount();
        int panes = sharedStashPanel.getSharedStash().getPanes().size();
        return configured > 0 ? Math.min(configured, panes) : panes;
    }

    private void updatePaneGold(int[] oldPaneGold, int[] newPaneGold) {
        D2SharedStash sharedStash = sharedStashPanel.getSharedStash();
        for (int i = 0; i < newPaneGold.length; i++) {
            if (newPaneGold[i] == oldPaneGold[i]) continue;
            sharedStash.replacePane(i, D2SharedStash.D2SharedStashPane.fromItems(sharedStash.getPane(i).getItems(), newPaneGold[i]));
        }
        sharedStash.setModified(true);
    }

    static int roomInBank(int bank) {
        return Integer.MAX_VALUE - Math.max(0, bank);
    }

    /** Takes up to {@code amount} gold, from page {@code first} first and then from the other pages in order. */
    static int[] withdraw(int[] paneGold, int first, int amount) {
        int[] result = paneGold.clone();
        int left = Math.max(0, amount);
        for (int index : pageOrder(result.length, first, result.length)) {
            if (left == 0) break;
            int take = Math.min(left, Math.max(0, result[index]));
            result[index] -= take;
            left -= take;
        }
        return result;
    }

    /**
     * Adds up to {@code amount} gold, filling page {@code first} first and then the other pages below
     * {@code pageLimit}, never taking a page above {@code maxPerPane}.
     */
    static int[] deposit(int[] paneGold, int first, int pageLimit, int amount, int maxPerPane) {
        int[] result = paneGold.clone();
        int left = Math.max(0, amount);
        for (int index : pageOrder(result.length, first, pageLimit)) {
            if (left == 0) break;
            int put = Math.min(left, Math.max(0, maxPerPane - result[index]));
            result[index] += put;
            left -= put;
        }
        return result;
    }

    private static int[] pageOrder(int pageCount, int first, int pageLimit) {
        int limit = Math.min(pageCount, pageLimit);
        int[] order = new int[limit];
        int n = 0;
        if (first >= 0 && first < limit) order[n++] = first;
        for (int i = 0; i < limit; i++) {
            if (i != first) order[n++] = i;
        }
        return java.util.Arrays.copyOf(order, n);
    }

    private static int total(int[] paneGold) {
        long sum = 0;
        for (int gold : paneGold) sum += gold;
        return (int) sum;
    }

    public int getGoldAmount(JTextField transferGoldAmount) {
        try {
            return Integer.parseInt(transferGoldAmount.getText());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
