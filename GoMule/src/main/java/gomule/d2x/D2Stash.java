/*******************************************************************************
 *
 * Copyright 2007 Randall
 *
 * This file is part of gomule.
 *
 * gomule is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * gomule is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * gomlue; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 *
 ******************************************************************************/
package gomule.d2x;

import gomule.gui.D2ItemListAdapter;
import gomule.item.D2Item;
import gomule.model.VersionController.Variant;
import gomule.util.D2Backup;
import gomule.util.D2BitReader;
import gomule.util.D2Project;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;

public class D2Stash extends D2ItemListAdapter {
    private final D2StashWriter stashWriter;
    private final ArrayList<D2Item> iItems;
    private final String filename;
    private final boolean iHC;
    private final boolean iSC;
    public static final int FIXED_STASH_CHAR_LEVEL = 75; // default char lvl for properties

    public D2Stash(Variant variant, String filename, ArrayList<D2Item> d2Items, boolean iSC, boolean iHC, boolean isNewFile) {
        super(filename);
        this.stashWriter = new D2StashWriter(variant, filename);
        this.filename = filename;
        this.iHC = iHC;
        this.iSC = iSC;
        this.iItems = d2Items;
        setModified(isNewFile);
    }

    public String getFilename() {
        return iFileName;
    }

    public boolean isHC() {
        return iHC;
    }

    public boolean isSC() {
        return iSC;
    }

    public ArrayList<D2Item> getItemList() {
        return iItems;
    }

    public void addItem(D2Item pItem) {
        if (pItem != null) {
            iItems.add(pItem);
            pItem.setCharLvl(FIXED_STASH_CHAR_LEVEL);
            setModified(true);
        }
    }

    public boolean containsItem(D2Item pItem) {
        return iItems.contains(pItem);
    }

    public void removeItem(D2Item pItem) {
        iItems.remove(pItem);
        setModified(true);
    }

    public ArrayList removeAllItems() {
        ArrayList lReturn = new ArrayList();
        lReturn.addAll(iItems);
        iItems.clear();
        setModified(true);
        return lReturn;
    }

    public int getNrItems() {
        return iItems.size();
    }

    public void saveInternal(D2Project pProject) {
        // backup file
        D2Backup.writeBak(iFileName);
        D2Backup.backup(pProject, iFileName, new D2BitReader(iFileName));
        stashWriter.write(this);
        setModified(false);
    }

    public void fullDump(PrintWriter pWriter) {
        pWriter.println(iFileName);
        pWriter.println();
        if (iItems != null) {
            for (int i = 0; i < iItems.size(); i++) {
                D2Item lItem = iItems.get(i);
                lItem.toWriter(pWriter);
            }
        }
        pWriter.println("Finished: " + iFileName);
        pWriter.println();
    }

    public String getFileNameEnd() {
        return new File(filename).getName();
    }
}
