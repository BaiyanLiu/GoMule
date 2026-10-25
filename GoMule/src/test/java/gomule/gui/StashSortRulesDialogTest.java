package gomule.gui;

import gomule.d2x.StashSortRule;
import gomule.util.D2Project;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class StashSortRulesDialogTest {

    @Test
    public void savedRulesIncludingOldOnesComeBackUnchangedThroughTheDialog() throws Exception {
        List<StashSortRule> saved = Arrays.asList(StashSortRule.parse("KIND;RING_AMULET, POTION"), StashSortRule.parse("TYPE;SWORD, GLOVES"),
                StashSortRule.parse("QUALITY;UNIQUE"), StashSortRule.parse("NAME;Shako, Ber"));
        List<StashSortRule> result = runDialog(saved, dialog -> {
        });
        // the dialog lists ticked values in its own order
        assertEquals(Arrays.asList(StashSortRule.parse("KIND;POTION, RING, AMULET"), StashSortRule.parse("TYPE;GLOVES, SWORD"),
                StashSortRule.parse("QUALITY;UNIQUE"), StashSortRule.parse("NAME;Shako, Ber")), result);
    }

    @Test
    public void weaponAndArmorTicksMakeOneTypeRuleAndUntickingEverythingClearsTheRules() throws Exception {
        List<StashSortRule> result = runDialog(Collections.emptyList(), dialog -> {
            tick(dialog, "Axe");
            tick(dialog, "Boots");
            tick(dialog, "2-handed weapon");
        });
        assertEquals(Arrays.asList(StashSortRule.kindIs(Collections.singletonList(StashSortRule.Kind.TWO_HANDED_WEAPON)),
                StashSortRule.typeIs(Arrays.asList(StashSortRule.ItemType.BOOTS, StashSortRule.ItemType.AXE))), result);

        List<StashSortRule> cleared = runDialog(result, dialog -> {
            tick(dialog, "Axe");
            tick(dialog, "Boots");
            tick(dialog, "2-handed weapon");
        });
        assertEquals(Collections.emptyList(), cleared);
    }

    private static List<StashSortRule> runDialog(List<StashSortRule> saved, Consumer<JDialog> edit) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        D2Project project = Mockito.mock(D2Project.class);
        Mockito.when(project.getStashSortRules(Mockito.anyString())).thenReturn(saved);
        JFrame owner = new JFrame();
        SwingUtilities.invokeLater(() -> StashSortRulesDialog.showFor(owner.getContentPane(), project, "stash.d2x"));
        JDialog[] dialog = new JDialog[1];
        for (int wait = 0; wait < 50 && dialog[0] == null; wait++) {
            Thread.sleep(100);
            SwingUtilities.invokeAndWait(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof StashSortRulesDialog && window.isShowing()) dialog[0] = (JDialog) window;
                }
            });
        }
        assertNotNull(dialog[0], "dialog didn't open");
        SwingUtilities.invokeAndWait(() -> {
            edit.accept(dialog[0]);
            ((JButton) find(dialog[0], JButton.class, "OK")).doClick();
        });
        owner.dispose();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StashSortRule>> rules = ArgumentCaptor.forClass(List.class);
        Mockito.verify(project).setStashSortRules(Mockito.eq("stash.d2x"), rules.capture());
        Mockito.verify(project).saveProject();
        return rules.getValue();
    }

    private static void tick(JDialog dialog, String label) {
        ((JCheckBox) find(dialog, JCheckBox.class, label)).doClick();
    }

    private static Component find(Container container, Class<?> type, String text) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component) && ((AbstractButton) component).getText().equals(text)) return component;
            if (component instanceof Container) {
                Component found = find((Container) component, type, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
