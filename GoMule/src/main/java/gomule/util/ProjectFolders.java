package gomule.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

/**
 * The user's own folders for one part of the project tree (characters, ATMA stashes or shared stashes),
 * and which folder each file sits in. Folders can hold folders; a path is the folder names joined by "/",
 * and "" is the top level (straight under the part's node).
 * <p>
 * Saved in the project file as {@code <prefix>folder.N=<path>} for every folder (so empty ones are kept)
 * and {@code <prefix>.N.folder=<path>} next to each file's own {@code <prefix>.N} entry.
 */
public class ProjectFolders {
    public static final String SEPARATOR = "/";
    public static final String TOP = "";

    private final String iPrefix;
    private final TreeSet<String> iFolders = new TreeSet<>(ProjectFolders::comparePaths);
    private final Map<String, String> iFileFolders = new HashMap<>();

    public ProjectFolders(String pPrefix) {
        iPrefix = pPrefix;
    }

    public String getPrefix() {
        return iPrefix;
    }

    /**
     * Sorts folder by folder, ignoring case, so a folder always comes right before what is inside it.
     */
    private static int comparePaths(String pA, String pB) {
        String[] lA = pA.split(SEPARATOR, -1);
        String[] lB = pB.split(SEPARATOR, -1);
        for (int i = 0; i < Math.min(lA.length, lB.length); i++) {
            int lCompare = lA[i].compareToIgnoreCase(lB[i]);
            if (lCompare != 0) return lCompare;
        }
        return Integer.compare(lA.length, lB.length);
    }

    /**
     * A folder name as typed, trimmed, or null when it can't be used (empty or holding a "/").
     */
    public static String cleanName(String pName) {
        if (pName == null) return null;
        String lName = pName.trim();
        if (lName.isEmpty() || lName.contains(SEPARATOR)) return null;
        return lName;
    }

    public static String getName(String pPath) {
        int lIndex = pPath.lastIndexOf(SEPARATOR);
        return lIndex < 0 ? pPath : pPath.substring(lIndex + 1);
    }

    public static String getParent(String pPath) {
        int lIndex = pPath.lastIndexOf(SEPARATOR);
        return lIndex < 0 ? TOP : pPath.substring(0, lIndex);
    }

    private static String join(String pParent, String pName) {
        return pParent.isEmpty() ? pName : pParent + SEPARATOR + pName;
    }

    /**
     * True when pPath is pFolder itself or anywhere inside it.
     */
    public static boolean isInside(String pPath, String pFolder) {
        return pPath.equals(pFolder) || pPath.startsWith(pFolder + SEPARATOR);
    }

    /**
     * Every folder, each one right before its own subfolders.
     */
    public List<String> getFolders() {
        return new ArrayList<>(iFolders);
    }

    public boolean hasFolder(String pPath) {
        return pPath.isEmpty() || iFolders.contains(pPath);
    }

    /**
     * Makes a folder inside pParent ("" for the top level). Returns its path, or null when the name can't
     * be used, the parent doesn't exist, or the parent already has a folder of that name.
     */
    public String addFolder(String pParent, String pName) {
        String lName = cleanName(pName);
        if (lName == null || !hasFolder(pParent)) return null;
        String lPath = join(canonical(pParent), lName);
        if (iFolders.contains(lPath)) return null;
        iFolders.add(lPath);
        return lPath;
    }

    /**
     * Renames a folder, keeping what is inside it. Returns the new path, or null when the name can't be used
     * or a folder next to it already has that name.
     */
    public String renameFolder(String pPath, String pNewName) {
        String lName = cleanName(pNewName);
        if (lName == null || !iFolders.contains(pPath)) return null;
        String lPath = canonical(pPath);
        return moveFolderTo(lPath, join(getParent(lPath), lName), true);
    }

    /**
     * Moves a folder, with everything in it, into pNewParent ("" for the top level). Returns the new path,
     * or null when pNewParent is the folder itself or inside it, or already holds a folder of that name.
     */
    public String moveFolder(String pPath, String pNewParent) {
        if (!iFolders.contains(pPath) || !hasFolder(pNewParent)) return null;
        String lPath = canonical(pPath);
        String lNewParent = canonical(pNewParent);
        if (isInside(lNewParent, lPath)) return null;
        if (getParent(lPath).equals(lNewParent)) return lPath;
        return moveFolderTo(lPath, join(lNewParent, getName(lPath)), false);
    }

    private String moveFolderTo(String pOld, String pNew, boolean pRename) {
        if (pOld.equals(pNew)) return pNew;
        // A rename may only change the case of its own name; anything else must not clash.
        boolean lCaseOnly = pRename && pOld.equalsIgnoreCase(pNew);
        if (iFolders.contains(pNew) && !lCaseOnly) return null;

        List<String> lMoved = new ArrayList<>();
        for (String lFolder : iFolders) {
            if (isInside(lFolder, pOld)) lMoved.add(lFolder);
        }
        iFolders.removeAll(lMoved);
        for (String lFolder : lMoved) {
            iFolders.add(pNew + lFolder.substring(pOld.length()));
        }
        for (Map.Entry<String, String> lEntry : iFileFolders.entrySet()) {
            if (isInside(lEntry.getValue(), pOld)) {
                lEntry.setValue(pNew + lEntry.getValue().substring(pOld.length()));
            }
        }
        return pNew;
    }

    /**
     * Deletes a folder. The files and folders in it move up into its parent; a moved folder whose name is
     * already taken there is merged into the folder of that name.
     */
    public void deleteFolder(String pFolder) {
        if (!iFolders.contains(pFolder)) return;
        String pPath = canonical(pFolder);
        String lParent = getParent(pPath);
        List<String> lInside = new ArrayList<>();
        for (String lFolder : iFolders) {
            if (isInside(lFolder, pPath)) lInside.add(lFolder);
        }
        iFolders.removeAll(lInside);
        for (String lFolder : lInside) {
            if (!lFolder.equals(pPath)) iFolders.add(join(lParent, lFolder.substring(pPath.length() + 1)));
        }
        for (Map.Entry<String, String> lEntry : iFileFolders.entrySet()) {
            String lFolder = lEntry.getValue();
            if (lFolder.equals(pPath)) {
                lEntry.setValue(lParent);
            } else if (isInside(lFolder, pPath)) {
                lEntry.setValue(join(lParent, lFolder.substring(pPath.length() + 1)));
            }
        }
        // Map moved files onto the merged folder's stored spelling, when one differed only in case.
        for (Map.Entry<String, String> lEntry : iFileFolders.entrySet()) {
            lEntry.setValue(canonical(lEntry.getValue()));
        }
        iFileFolders.values().removeIf(String::isEmpty);
    }

    private String canonical(String pPath) {
        if (pPath.isEmpty()) return pPath;
        String lStored = iFolders.ceiling(pPath);
        return lStored != null && comparePaths(lStored, pPath) == 0 ? lStored : pPath;
    }

    /**
     * The folder a file is in, or "" when it is at the top level.
     */
    public String getFolderOf(String pFileName) {
        return iFileFolders.getOrDefault(pFileName, TOP);
    }

    /**
     * Puts a file in a folder ("" for the top level). Returns false when there is no such folder.
     */
    public boolean moveFile(String pFileName, String pFolder) {
        if (!hasFolder(pFolder)) return false;
        if (pFolder.isEmpty()) {
            iFileFolders.remove(pFileName);
        } else {
            iFileFolders.put(pFileName, canonical(pFolder));
        }
        return true;
    }

    public void removeFile(String pFileName) {
        iFileFolders.remove(pFileName);
    }

    /**
     * Reads this part's folders and file placements from a project file. Folders named by a file but missing
     * from the folder list (a hand-edited file, say) are made, with their parents.
     */
    public void load(Properties pProperties) {
        iFolders.clear();
        iFileFolders.clear();
        for (int i = 0; ; i++) {
            String lFolder = pProperties.getProperty(iPrefix + "folder." + i);
            if (lFolder == null) break;
            addPath(lFolder);
        }
        for (int i = 0; ; i++) {
            String lFile = pProperties.getProperty(iPrefix + "." + i);
            if (lFile == null) break;
            String lFolder = addPath(pProperties.getProperty(iPrefix + "." + i + ".folder", TOP));
            if (!lFolder.isEmpty()) iFileFolders.put(lFile, lFolder);
        }
    }

    /**
     * Adds a stored path and its parents, skipping empty or blank parts; returns the path as it was added.
     */
    private String addPath(String pPath) {
        String lPath = TOP;
        for (String lPart : pPath.split(SEPARATOR)) {
            String lName = cleanName(lPart);
            if (lName == null) continue;
            lPath = join(lPath, lName);
            lPath = canonical(lPath);
            iFolders.add(lPath);
        }
        return lPath;
    }

    /**
     * Writes this part's folders and, for each file in pFiles (in the order its {@code <prefix>.N} entries
     * are written), the folder it is in.
     */
    public void save(Properties pProperties, List<?> pFiles) {
        int i = 0;
        for (String lFolder : iFolders) {
            pProperties.put(iPrefix + "folder." + i++, lFolder);
        }
        for (int j = 0; j < pFiles.size(); j++) {
            String lFolder = getFolderOf((String) pFiles.get(j));
            if (!lFolder.isEmpty()) pProperties.put(iPrefix + "." + j + ".folder", lFolder);
        }
    }

    /**
     * Files of pFiles that are directly in pFolder, in pFiles order.
     */
    public List<String> getFilesIn(String pFolder, List<?> pFiles) {
        List<String> lFiles = new ArrayList<>();
        for (Object lFile : pFiles) {
            if (getFolderOf((String) lFile).equals(pFolder)) lFiles.add((String) lFile);
        }
        return Collections.unmodifiableList(lFiles);
    }
}
