/*
 * Created on 23-mei-2007
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package gomule.util;

import gomule.d2i.D2SharedStashReader;
import gomule.d2x.D2StashReader;
import gomule.gui.D2FileManager;
import gomule.model.VersionController.Variant;
import randall.util.RandallUtil;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Marco
 * <p>
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class D2Backup {
    public static final int BAK_FILES_KEPT = 3;
    private static final DateTimeFormatter BAK_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    // the end of a backup's name: ".20261024-182800-123.bak", or "...-123-1.bak" when two saves fall in the same millisecond
    private static final String BAK_SUFFIX = "\\.(\\d{8}-\\d{6}-\\d{3})(?:-(\\d+))?\\.bak";
    private static final Pattern BAK_SUFFIX_PATTERN = Pattern.compile(BAK_SUFFIX + "$");
    private static final Pattern BAK_TIME_PATTERN = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})-(\\d{2})(\\d{2})(\\d{2})-\\d{3}(?:-\\d+)?\\.bak$");

    /**
     * Copies the file as it is on disk now to pFileName + ".<timestamp>.bak" and deletes all but the newest
     * {@link #BAK_FILES_KEPT} of those, so the last saved versions survive a bad save. Also removes the single
     * pFileName + ".bak" older versions wrote. Does nothing for a file not written yet. Fails, and so stops the save,
     * when the copy can't be made.
     */
    public static void writeBak(String pFileName) {
        Path lFile = Paths.get(pFileName);
        if (!Files.isRegularFile(lFile)) {
            return;
        }
        try {
            String lStamp = LocalDateTime.now().format(BAK_TIMESTAMP);
            Path lBak = Paths.get(pFileName + "." + lStamp + ".bak");
            for (int i = 1; Files.exists(lBak); i++) {
                lBak = Paths.get(pFileName + "." + lStamp + "-" + i + ".bak");
            }
            Files.copy(lFile, lBak, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException pEx) {
            throw new UncheckedIOException("Not saving " + pFileName + ": could not write its .bak backup", pEx);
        }
        List<Path> lBaks = getBakFiles(pFileName);
        List<Path> lToDelete = new ArrayList<>(lBaks.subList(0, Math.max(0, lBaks.size() - BAK_FILES_KEPT)));
        if (Files.exists(Paths.get(pFileName + ".bak"))) lToDelete.add(Paths.get(pFileName + ".bak"));
        for (int i = 0; i < lToDelete.size(); i++) {
            try {
                Files.delete(lToDelete.get(i));
            } catch (IOException pEx) {
                System.err.println("Could not delete old backup " + lToDelete.get(i) + ": " + pEx.getMessage());
            }
        }
    }

    /**
     * Puts a backup's content (read with Files.readAllBytes) back into pFileName. The current file is backed up
     * first, as on a save, so the restore itself can be undone from the backups.
     */
    public static void restoreBak(String pFileName, byte[] pBakContent) throws IOException {
        writeBak(pFileName);
        Files.write(Paths.get(pFileName), pBakContent);
    }

    /**
     * Throws when a backup's content is not a stash GoMule can read, e.g. a damaged or cut-off .bak, so it isn't
     * restored over a working file.
     */
    public static void checkReadable(Variant pVariant, String pFileName, byte[] pBakContent) throws Exception {
        try {
            if (pFileName.toLowerCase().endsWith(".d2x")) {
                new D2StashReader().readStash(pVariant, pFileName, new D2BitReader(pBakContent));
            } else if (pFileName.toLowerCase().endsWith(".d2i")) {
                new D2SharedStashReader().readStash(pVariant, pFileName, new D2BitReader(pBakContent));
            }
        } catch (Exception pEx) {
            throw new Exception("This backup of " + new File(pFileName).getName() + " can't be read, so nothing was restored: " + pEx.getMessage(), pEx);
        }
    }

    /**
     * When a backup was made, from its name, e.g. 2026-10-24 18:28:00.
     */
    public static String getBakTime(Path pBak) {
        Matcher lMatcher = BAK_TIME_PATTERN.matcher(pBak.getFileName().toString());
        if (!lMatcher.find()) return pBak.getFileName().toString();
        return lMatcher.group(1) + "-" + lMatcher.group(2) + "-" + lMatcher.group(3) + " " + lMatcher.group(4) + ":" + lMatcher.group(5) + ":" + lMatcher.group(6);
    }

    /**
     * The timestamped .bak files of a file, oldest first.
     */
    public static List<Path> getBakFiles(String pFileName) {
        Path lFile = Paths.get(pFileName).toAbsolutePath();
        Pattern lPattern = Pattern.compile(Pattern.quote(lFile.getFileName().toString()) + BAK_SUFFIX);
        try (Stream<Path> lFiles = Files.list(lFile.getParent())) {
            return lFiles.filter(lPath -> lPattern.matcher(lPath.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(D2Backup::bakSortKey))
                    .collect(Collectors.toList());
        } catch (IOException pEx) {
            throw new UncheckedIOException(pEx);
        }
    }

    // "x.d2i.20261024-182800-123.bak" and "x.d2i.20261024-182800-123-1.bak" sort by time, then by the counter
    private static String bakSortKey(Path pBak) {
        String lBakName = pBak.getFileName().toString();
        Matcher lMatcher = BAK_SUFFIX_PATTERN.matcher(lBakName);
        if (!lMatcher.find()) return lBakName;
        return lMatcher.group(1) + String.format("%06d", lMatcher.group(2) == null ? 0 : Integer.parseInt(lMatcher.group(2)));
    }

    public static void backup(D2Project pProject, String pFileName, D2BitReader pContent) {
        try {
            int lBackup = pProject.getBackup();
            if (lBackup == D2Project.BACKUP_NONE) {
                return;
            }

            File lFile = new File(pFileName);

            String lFileName = lFile.getName();
            String lPathName = lFile.getParent();

            GregorianCalendar lCalendar = new GregorianCalendar();
            String lExtra1;

            if (lBackup == D2Project.BACKUP_DAY) {
                lExtra1 = "D"
                        + RandallUtil.fill(lCalendar.get(Calendar.YEAR), 4)
                        + "." + RandallUtil.fill(lCalendar.get(Calendar.MONTH) + 1, 2)
                        + "." + RandallUtil.fill(lCalendar.get(Calendar.DAY_OF_MONTH), 2);
            } else if (lBackup == D2Project.BACKUP_MONTH) {
                lExtra1 = "M"
                        + RandallUtil.fill(lCalendar.get(Calendar.YEAR), 4)
                        + RandallUtil.fill(lCalendar.get(Calendar.MONTH) + 1, 2);
            } else {
                GregorianCalendar lWeek = new GregorianCalendar();

                while (lWeek.get(Calendar.DAY_OF_WEEK) != lWeek.getFirstDayOfWeek()) {
                    lWeek.add(Calendar.DAY_OF_MONTH, -1);
                }
                lExtra1 = "W"
                        + RandallUtil.fill(lWeek.get(Calendar.YEAR), 4)
                        + "." + RandallUtil.fill(lWeek.get(Calendar.MONTH) + 1, 2)
                        + "." + RandallUtil.fill(lWeek.get(Calendar.DAY_OF_MONTH), 2);
            }

            String lExtra2 =
                    RandallUtil.fill(lCalendar.get(Calendar.YEAR), 4)
                            + "." + RandallUtil.fill(lCalendar.get(Calendar.MONTH) + 1, 2)
                            + "." + RandallUtil.fill(lCalendar.get(Calendar.DAY_OF_MONTH), 2)
                            + "-"
                            + RandallUtil.fill(lCalendar.get(Calendar.HOUR_OF_DAY), 2)
                            + "." + RandallUtil.fill(lCalendar.get(Calendar.MINUTE), 2)
                            + "." + RandallUtil.fill(lCalendar.get(Calendar.SECOND), 2);

            String lBackupDir = lPathName + File.separator + "GoMule.backup";
            String lBackupSubDir = lBackupDir + File.separator + lExtra1;

            String lNewFileName = lFileName.substring(0, lFileName.length() - 4) + "." + lExtra2 + lFileName.substring(lFileName.length() - 4) + ".org";

            String lBackupName = lBackupSubDir + File.separator + lNewFileName;

            RandallUtil.checkDir(lBackupSubDir);

            pContent.save(lBackupName);
        } catch (Exception pEx) {
            pEx.printStackTrace();
            D2FileManager.displayErrorDialog(pEx);
        }

//        if ( pContent.isNewFile() )
//        {
//            return;
//        }
//        int backup_max = -1;
//        for (int i = 0; i <= 9; i++)
//        {
//            if (new java.io.File(pFileName + "." + i).exists())
//                backup_max = i;
//            else
//                break;
//        }
//
//        // shift the backups down
//        // (backup 9 is overwritten, 0 comes free)
//        for (int i = backup_max; i >= 0; i--)
//        {
//            if (i < 9)
//            {
//                D2BitReader src = new D2BitReader(pFileName + "." + i);
//                src.save(pFileName + "." + (i + 1));
//            }
//        }
//        
//        // save the file to backup 0
//        pContent.save(pFileName + ".0");

    }
}
