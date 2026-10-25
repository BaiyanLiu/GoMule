package gomule.gui;

import gomule.d2x.StashColumn;
import gomule.util.D2Project;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Edits which columns the item table of one ATMA (.d2x) stash view shows, and in what order.
 */
public class StashColumnsDialog extends JDialog {

    private final D2Project project;
    private final String stashFileName;
    private final DefaultListModel<StashColumn> columnsModel = new DefaultListModel<>();
    private final JList<StashColumn> columnsList = new JList<>(columnsModel);
    private final JComboBox<StashColumn.Builtin> builtinBox;
    private final JTextField propertyField = new JTextField(16);
    private final JTextField labelField = new JTextField(6);
    private boolean changed;

    /**
     * Shows the dialog with its top left corner at screenPoint (e.g. where the user right-clicked), moved as needed to
     * fit on that screen, and returns true when the columns were changed and saved.
     */
    public static boolean showFor(Component parent, D2Project project, String stashFileName, Point screenPoint) {
        StashColumnsDialog dialog = new StashColumnsDialog(SwingUtilities.getWindowAncestor(parent), project, stashFileName);
        dialog.setLocation(fitOnScreen(screenPoint, dialog.getSize(), screenBounds(screenPoint, parent)));
        dialog.setVisible(true);
        return dialog.changed;
    }

    static Point fitOnScreen(Point screenPoint, Dimension size, Rectangle screen) {
        int x = Math.max(screen.x, Math.min(screenPoint.x, screen.x + screen.width - size.width));
        int y = Math.max(screen.y, Math.min(screenPoint.y, screen.y + screen.height - size.height));
        return new Point(x, y);
    }

    // the usable area (without task bar) of the screen holding the point, for multi-monitor setups
    private static Rectangle screenBounds(Point screenPoint, Component parent) {
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration config = device.getDefaultConfiguration();
            if (config.getBounds().contains(screenPoint)) {
                Rectangle bounds = config.getBounds();
                Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
                return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
                        bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
            }
        }
        return parent.getGraphicsConfiguration().getBounds();
    }

    private StashColumnsDialog(Window owner, D2Project project, String stashFileName) {
        super(owner, "Columns - " + ("all".equalsIgnoreCase(stashFileName) ? "All Stashes" : new File(stashFileName).getName()),
                ModalityType.APPLICATION_MODAL);
        this.project = project;
        this.stashFileName = stashFileName;
        project.getStashColumns(stashFileName).forEach(columnsModel::addElement);

        List<StashColumn.Builtin> builtins = new ArrayList<>();
        for (StashColumn.Builtin builtin : StashColumn.Builtin.values()) {
            if (builtin != StashColumn.Builtin.SOURCE || "all".equalsIgnoreCase(stashFileName)) builtins.add(builtin);
        }
        builtinBox = new JComboBox<>(builtins.toArray(new StashColumn.Builtin[0]));

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(new JLabel("<html>Columns shown in this stash's item table, left to right.<br>"
                + "Click a column header in the table to sort by it.</html>"), BorderLayout.NORTH);

        columnsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        columnsList.setVisibleRowCount(8);
        content.add(new JScrollPane(columnsList), BorderLayout.CENTER);

        JButton upButton = new JButton("Move up");
        upButton.addActionListener(e -> moveSelected(-1));
        JButton downButton = new JButton("Move down");
        downButton.addActionListener(e -> moveSelected(1));
        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> removeSelected());
        JPanel listButtons = new JPanel(new GridLayout(3, 1, 0, 4));
        listButtons.add(upButton);
        listButtons.add(downButton);
        listButtons.add(removeButton);
        JPanel east = new JPanel(new BorderLayout());
        east.add(listButtons, BorderLayout.NORTH);
        content.add(east, BorderLayout.EAST);

        JButton addBuiltinButton = new JButton("Add");
        addBuiltinButton.addActionListener(e -> addColumn(new StashColumn((StashColumn.Builtin) builtinBox.getSelectedItem())));
        JPanel builtinEditor = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        builtinEditor.add(new JLabel("Item column:"));
        builtinEditor.add(builtinBox);
        builtinEditor.add(addBuiltinButton);

        propertyField.setToolTipText("Text of a property line, e.g. Faster Cast Rate, Magic Find, Enhanced Damage. The column shows its number.");
        labelField.setToolTipText("Column header. Leave blank to use the property text.");
        JButton addPropertyButton = new JButton("Add");
        addPropertyButton.addActionListener(e -> addPropertyColumn());
        JPanel propertyEditor = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        propertyEditor.add(new JLabel("Property containing:"));
        propertyEditor.add(propertyField);
        propertyEditor.add(new JLabel("Header:"));
        propertyEditor.add(labelField);
        propertyEditor.add(addPropertyButton);

        JButton resetButton = new JButton("Reset to default");
        resetButton.addActionListener(e -> {
            columnsModel.clear();
            StashColumn.DEFAULT_COLUMNS.forEach(columnsModel::addElement);
        });
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> saveAndClose());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        JPanel dialogButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        dialogButtons.add(resetButton);
        dialogButtons.add(okButton);
        dialogButtons.add(cancelButton);

        JPanel south = new JPanel(new GridLayout(3, 1, 0, 6));
        south.add(builtinEditor);
        south.add(propertyEditor);
        south.add(dialogButtons);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(okButton);
        pack();
    }

    private void addColumn(StashColumn column) {
        if (!columnsModel.contains(column)) columnsModel.addElement(column);
        columnsList.setSelectedValue(column, true);
    }

    private void addPropertyColumn() {
        if (propertyField.getText().trim().isEmpty()) return;
        addColumn(new StashColumn(labelField.getText(), propertyField.getText()));
        propertyField.setText("");
        labelField.setText("");
    }

    private void moveSelected(int offset) {
        int index = columnsList.getSelectedIndex();
        int target = index + offset;
        if (index < 0 || target < 0 || target >= columnsModel.size()) return;
        StashColumn column = columnsModel.remove(index);
        columnsModel.add(target, column);
        columnsList.setSelectedIndex(target);
    }

    private void removeSelected() {
        int index = columnsList.getSelectedIndex();
        if (index >= 0) columnsModel.remove(index);
    }

    private void saveAndClose() {
        if (columnsModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Pick at least one column.", "Columns", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<StashColumn> columns = new ArrayList<>();
        for (int i = 0; i < columnsModel.size(); i++) columns.add(columnsModel.get(i));
        project.setStashColumns(stashFileName, columns);
        project.saveProject();
        changed = true;
        dispose();
    }
}
