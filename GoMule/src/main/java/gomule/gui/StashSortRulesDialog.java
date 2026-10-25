package gomule.gui;

import gomule.d2x.StashSortRule;
import gomule.util.D2Project;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Edits which items belong in one ATMA (.d2x) stash when the shared stash is sorted. Each section is optional:
 * ticked boxes and listed names are OR'd within a section, and the filled-in sections are AND'd. The weapon and armor
 * type sections make one rule, so ticking Sword and Gloves takes swords or gloves.
 */
public class StashSortRulesDialog extends JDialog {

    private final D2Project project;
    private final String stashFileName;
    private final Map<StashSortRule.Kind, JCheckBox> kindBoxes = new EnumMap<>(StashSortRule.Kind.class);
    private final Map<StashSortRule.ItemType, JCheckBox> typeBoxes = new EnumMap<>(StashSortRule.ItemType.class);
    private final Map<StashSortRule.Quality, JCheckBox> qualityBoxes = new EnumMap<>(StashSortRule.Quality.class);
    private final JTextField namesField = new JTextField(30);

    public static void showFor(Component parent, D2Project project, String stashFileName) {
        StashSortRulesDialog dialog = new StashSortRulesDialog(SwingUtilities.getWindowAncestor(parent), project, stashFileName);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private StashSortRulesDialog(Window owner, D2Project project, String stashFileName) {
        super(owner, "Sorting rules - " + new File(stashFileName).getName(), ModalityType.APPLICATION_MODAL);
        this.project = project;
        this.stashFileName = stashFileName;

        JPanel sections = new JPanel();
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        sections.add(new JLabel("<html>Shared stash items go here when they match every section you fill in.<br>"
                + "Leave a section empty to ignore it. Items matching more than one stash stay in the shared stash.</html>"));
        sections.add(checkBoxSection("Kind is any of", StashSortRule.Kind.values(), kindBoxes));
        sections.add(checkBoxSection("Weapon type is any of", itemTypes(false), typeBoxes));
        sections.add(checkBoxSection("Armor type is any of", itemTypes(true), typeBoxes));
        sections.add(checkBoxSection("Quality is any of", StashSortRule.Quality.values(), qualityBoxes));
        JPanel namesSection = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        namesSection.setBorder(BorderFactory.createTitledBorder("Name contains any of (comma-separated)"));
        namesField.setToolTipText("e.g. Shako, Ber, Jah");
        namesSection.add(namesField);
        namesSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        sections.add(namesSection);
        showRules(project.getStashSortRules(stashFileName));

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> saveAndClose());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        JPanel dialogButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        dialogButtons.add(okButton);
        dialogButtons.add(cancelButton);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(sections, BorderLayout.CENTER);
        content.add(dialogButtons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(okButton);
        pack();
    }

    private static <E extends Enum<E>> JPanel checkBoxSection(String title, E[] values, Map<E, JCheckBox> boxes) {
        JPanel section = new JPanel(new GridLayout(0, 4, 4, 0));
        section.setBorder(BorderFactory.createTitledBorder(title));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (E value : values) {
            JCheckBox box = new JCheckBox(value.toString());
            boxes.put(value, box);
            section.add(box);
        }
        return section;
    }

    private static StashSortRule.ItemType[] itemTypes(boolean armor) {
        return Arrays.stream(StashSortRule.ItemType.values()).filter(type -> type.isArmor() == armor).toArray(StashSortRule.ItemType[]::new);
    }

    private void showRules(List<StashSortRule> rules) {
        for (StashSortRule rule : rules) {
            rule.getKinds().forEach(kind -> kindBoxes.get(kind).setSelected(true));
            rule.getTypes().forEach(type -> typeBoxes.get(type).setSelected(true));
            rule.getQualities().forEach(quality -> qualityBoxes.get(quality).setSelected(true));
            if (!rule.getNames().isEmpty()) namesField.setText(String.join(", ", rule.getNames()));
        }
    }

    private static <E> List<E> selected(Map<E, JCheckBox> boxes) {
        List<E> selected = new ArrayList<>();
        boxes.forEach((value, box) -> {
            if (box.isSelected()) selected.add(value);
        });
        return selected;
    }

    private List<StashSortRule> rulesFromSections() {
        List<StashSortRule> rules = new ArrayList<>();
        List<StashSortRule.Kind> kinds = selected(kindBoxes);
        if (!kinds.isEmpty()) rules.add(StashSortRule.kindIs(kinds));
        List<StashSortRule.ItemType> types = selected(typeBoxes);
        if (!types.isEmpty()) rules.add(StashSortRule.typeIs(types));
        List<StashSortRule.Quality> qualities = selected(qualityBoxes);
        if (!qualities.isEmpty()) rules.add(StashSortRule.qualityIs(qualities));
        if (!StashSortRule.splitNames(namesField.getText()).isEmpty()) rules.add(StashSortRule.nameContains(namesField.getText()));
        return rules;
    }

    private void saveAndClose() {
        project.setStashSortRules(stashFileName, rulesFromSections());
        project.saveProject();
        dispose();
    }
}
