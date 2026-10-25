package gomule.gui;

import gomule.util.D2Backup;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Restore from backup..." on a stash in the project tree: picks one of the file's timestamped .bak backups and
 * puts it back in place of the file.
 */
public class RestoreBackupDialog {

    private RestoreBackupDialog() {
    }

    public static void showFor(Component parent, D2FileManager fileManager, String fileName) {
        String name = new File(fileName).getName();
        List<Path> baks = new ArrayList<>(D2Backup.getBakFiles(fileName));
        if (baks.isEmpty()) {
            JOptionPane.showMessageDialog(parent, name + " has no backups yet. One is made each time GoMule saves it.",
                    "Restore from backup", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Collections.reverse(baks);
        List<String> choices = new ArrayList<>();
        for (Path bak : baks) choices.add(D2Backup.getBakTime(bak) + "  (" + sizeOf(bak) + ")");
        JList<String> list = new JList<>(choices.toArray(new String[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        int answer = JOptionPane.showConfirmDialog(parent, new Object[]{"Restore " + name + " to the version saved before:", new JScrollPane(list),
                        "GoMule saves all open changes first. The current " + name + " is kept as a backup,\n"
                                + "so this can be undone. Items moved to or from other files since then are not changed there."},
                "Restore from backup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION || list.getSelectedIndex() < 0) return;
        Path chosen = baks.get(list.getSelectedIndex());
        try {
            // read before saving: the save makes a new backup and may delete the oldest one
            byte[] content = Files.readAllBytes(chosen);
            D2Backup.checkReadable(fileManager.getVariant(), fileName, content);
            // saved here rather than only in closeFileName, which shows a failed save but carries on: a file that
            // couldn't be saved must not have its items replaced by an older version
            fileManager.saveAllItemLists();
            fileManager.closeFileName(fileName);
            fileManager.removeItemList(fileName, null);
            D2Backup.restoreBak(fileName, content);
            JOptionPane.showMessageDialog(parent, name + " was restored to the version from " + D2Backup.getBakTime(chosen) + ".",
                    "Restore from backup", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception pEx) {
            D2FileManager.displayErrorDialog(pEx);
        }
    }

    private static String sizeOf(Path bak) {
        try {
            return String.format("%,d bytes", Files.size(bak));
        } catch (Exception pEx) {
            return "size unknown";
        }
    }
}
