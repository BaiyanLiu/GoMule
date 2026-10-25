package gomule.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Files of one part of the project (the ATMA stashes) that the user has left out of the All view.
 * Stored in the project file as {@code <prefix>.N.notInAll=true} next to each {@code <prefix>.N} entry.
 */
public class AllViewExclusions {
    private final String iPrefix;
    private final Set<String> iExcluded = new HashSet<>();

    public AllViewExclusions(String pPrefix) {
        iPrefix = pPrefix;
    }

    public boolean isExcluded(String pFileName) {
        return iExcluded.contains(pFileName);
    }

    /**
     * Leaves a file out of the All view (true) or puts it back (false). Returns true when this changed anything.
     */
    public boolean setExcluded(String pFileName, boolean pExcluded) {
        return pExcluded ? iExcluded.add(pFileName) : iExcluded.remove(pFileName);
    }

    public void removeFile(String pFileName) {
        iExcluded.remove(pFileName);
    }

    /**
     * Files of pFiles that are shown in the All view, in pFiles order.
     */
    public List<String> keep(List<?> pFiles) {
        List<String> lKept = new ArrayList<>();
        for (Object lFile : pFiles) {
            if (!iExcluded.contains(lFile)) lKept.add((String) lFile);
        }
        return lKept;
    }

    public void load(Properties pProperties) {
        iExcluded.clear();
        for (int i = 0; ; i++) {
            String lFile = pProperties.getProperty(iPrefix + "." + i);
            if (lFile == null) break;
            if ("true".equalsIgnoreCase(pProperties.getProperty(iPrefix + "." + i + ".notInAll"))) {
                iExcluded.add(lFile);
            }
        }
    }

    /**
     * Writes the flag for each file in pFiles that is left out, using the same N as its {@code <prefix>.N} entry.
     */
    public void save(Properties pProperties, List<?> pFiles) {
        for (int i = 0; i < pFiles.size(); i++) {
            if (iExcluded.contains(pFiles.get(i))) pProperties.put(iPrefix + "." + i + ".notInAll", "true");
        }
    }
}
