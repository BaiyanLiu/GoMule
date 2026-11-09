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

package gomule.gui;

import gomule.util.D2Project;
import gomule.util.ProjectFolders;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class D2ViewProject extends JPanel {
    /**
     *
     */
    private static final long serialVersionUID = -7147981187044642216L;
    private D2FileManager iFileManager;
    private D2Project iProject;

    private JTree iTree;
    private DefaultTreeModel iTreeModel;
    private DefaultMutableTreeNode iChars;
    private DefaultMutableTreeNode iStashes;
    private DefaultMutableTreeNode iSharedStashes;
    private CharTreeNode iAll;
    private boolean iShowingDropTarget = false;
    private TreePath iSelectionBeforeDrag;
    /** Keys (see nodeKey) of nodes to expand on the next rebuild of the tree, besides those open now. */
    private final Set<String> iExpandNext = new HashSet<>();

    public D2ViewProject(D2FileManager pFileManager) {
        iFileManager = pFileManager;
        setLayout(new BorderLayout());
        iTreeModel = refreshTree();
        iTree = new JTree(iTreeModel) {
            /**
             *
             */
            private static final long serialVersionUID = 3529087650697421360L;

            public void scrollRectToVisible(Rectangle aRect) {
                // disable scrolling
            }
        };
        iTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        iTree.setCellRenderer(new DefaultTreeCellRenderer() {
            /**
             *
             */
            private static final long serialVersionUID = 4441305975881423913L;

            public Component getTreeCellRendererComponent(JTree pTree, Object pValue,
                                                          boolean pSel,
                                                          boolean pExpanded,
                                                          boolean pLeaf, int pRow,
                                                          boolean pHasFocus) {
                Component lRenderer = super.getTreeCellRendererComponent(pTree, pValue, pSel,
                        pExpanded, pLeaf, pRow, pHasFocus);

                setToolTipText(null);
                setFont(pTree.getFont());
                if (pValue instanceof CharTreeNode) {
                    CharTreeNode lNode = (CharTreeNode) pValue;
                    lRenderer.setForeground(lNode.getForeGround());
                    if (lNode.isLeftOutOfAll()) {
                        // Italic marks a stash left out of the All view.
                        setFont(pTree.getFont().deriveFont(Font.ITALIC));
                        setToolTipText("Not shown in the All view");
                    }
                }

                return lRenderer;
            }
        });

        ToolTipManager.sharedInstance().registerComponent(iTree);

        JScrollPane lScroll = new JScrollPane(iTree);
        add(lScroll, BorderLayout.CENTER);

        iTree.addMouseListener(new MouseListener() {
            private long iLastRelease = -1;
            private long iLastClickCount = 0;
            private TreePath iLastTreePath = null;

            public void mousePressed(MouseEvent e) {
            }

            public void mouseClicked(MouseEvent e) {
            }

            public void mouseReleased(MouseEvent e) {
                iFileManager.workCursor();
                try {
                    if (e.getX() >= 0 && e.getX() <= iTree.getWidth()
                            && e.getY() >= 0 && e.getY() <= iTree.getHeight()) {
                        TreePath lPath = iTree.getPathForLocation(e.getX(), e.getY());
                        if (lPath == null) {
                            return;
                        }

                        long lNow = System.currentTimeMillis();
                        if (lNow < iLastRelease + 500) {
                            iLastClickCount++;
                            if (iLastTreePath != lPath) {
                                return;
                            }
                        } else {
                            iLastClickCount = 1;
                            iLastRelease = lNow;
                            iLastTreePath = lPath;
                        }

                        if (e.getButton() == MouseEvent.BUTTON1 && iLastClickCount == 2) {
                            Object lPathObjects[] = lPath.getPath();
                            Object lLast = lPathObjects[lPathObjects.length - 1];
                            if (lLast instanceof CharTreeNode) {
                                ((CharTreeNode) lLast).view();
                            }
                        }
                        if (e.getButton() == MouseEvent.BUTTON3 && iLastClickCount == 1) {
                            Object lPathObjects[] = lPath.getPath();
                            Object lLast = lPathObjects[lPathObjects.length - 1];
                            if (lLast instanceof CharTreeNode) {
                                ((CharTreeNode) lLast).startMenu(e.getX(), e.getY());
                            } else if (lLast instanceof FolderTreeNode) {
                                startFolderMenu((FolderTreeNode) lLast, e.getX(), e.getY());
                            }
                        }
                    }
                } finally {
                    iFileManager.defaultCursor();
                }
            }

            public void mouseEntered(MouseEvent e) {
            }

            public void mouseExited(MouseEvent e) {
            }
        });
        iTree.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) && iTree.getSelectionCount() > 0) {

                    int lSelected[] = iTree.getSelectionRows();
                    for (int i = 0; i < lSelected.length; i++) {
                        TreePath lPath = iTree.getPathForRow(lSelected[i]);
                        Object lPathObjects[] = lPath.getPath();
                        Object lLast = lPathObjects[lPathObjects.length - 1];
                        if (lLast instanceof CharTreeNode) {
                            ((CharTreeNode) lLast).remove();
                        } else if (lLast instanceof FolderTreeNode && !((FolderTreeNode) lLast).isSection()) {
                            deleteFolder((FolderTreeNode) lLast);
                        }
                    }
                } else if (e.getModifiers() == KeyEvent.ALT_MASK) {
                    if (e.getKeyCode() == KeyEvent.VK_V || e.getKeyCode() == KeyEvent.VK_O || e.getKeyCode() == KeyEvent.VK_M) {
                        int lSelected[] = iTree.getSelectionRows();
                        for (int i = 0; i < lSelected.length; i++) {
                            TreePath lPath = iTree.getPathForRow(lSelected[i]);
                            Object lPathObjects[] = lPath.getPath();
                            Object lLast = lPathObjects[lPathObjects.length - 1];
                            if (lLast instanceof CharTreeNode) {
                                ((CharTreeNode) lLast).view();
                            }
                        }
                    } else if (e.getKeyCode() == KeyEvent.VK_C) {
                        int lSelected[] = iTree.getSelectionRows();
                        for (int i = 0; i < lSelected.length; i++) {
                            TreePath lPath = iTree.getPathForRow(lSelected[i]);
                            Object lPathObjects[] = lPath.getPath();
                            Object lLast = lPathObjects[lPathObjects.length - 1];
                            if (lLast instanceof CharTreeNode) {
                                ((CharTreeNode) lLast).close();
                            }
                        }
                    } else if (e.getKeyCode() == KeyEvent.VK_F) {
                        int lSelected[] = iTree.getSelectionRows();
                        for (int i = 0; i < lSelected.length; i++) {
                            TreePath lPath = iTree.getPathForRow(lSelected[i]);
                            Object lPathObjects[] = lPath.getPath();
                            Object lLast = lPathObjects[lPathObjects.length - 1];
                            if (lLast instanceof CharTreeNode) {
                                ((CharTreeNode) lLast).fullDump();
                            }
                        }
                    }
                }
            }
        });
        installFolderDragAndDrop();
    }

    private void installFolderDragAndDrop() {
        iTree.setDragEnabled(true);
        iTree.setDropMode(DropMode.ON);
        iTree.setTransferHandler(new FolderTransferHandler());
    }

    private static String getCharStr(String pFileName) {
        if (pFileName.equalsIgnoreCase("all")) {
            return pFileName;
        }
        File lFile = new File(pFileName);
        return lFile.getName() + " (" + pFileName + ")";
    }

    public void notifyFileOpened(String pFileName) {
        CharTreeNode lNode = searchNode(pFileName);
        if (lNode != null) {
            lNode.fileOpened();
            iTree.repaint();
        }
    }

    public void notifyFileClosed(String pFileName) {
        CharTreeNode lNode = searchNode(pFileName);
        if (lNode != null) {
            lNode.fileClosed();
            iTree.repaint();
        }
    }

    public void notifyItemListRead(String pItemListFileName) {
        CharTreeNode lNode = searchNode(pItemListFileName);
        if (lNode != null) {
            lNode.itemListRead();
            iTree.repaint();
        }
    }

    public void notifyItemListClosed(String pItemListFileName) {
        CharTreeNode lNode = searchNode(pItemListFileName);
        if (lNode != null) {
            lNode.itemListClosed();
            iTree.repaint();
        }
    }

    private CharTreeNode searchNode(String pFileName) {
        if (pFileName.toLowerCase().endsWith(".d2s")) {
            return searchSingleNode(iChars, pFileName);
        } else if (pFileName.toLowerCase().endsWith(".d2x")) {
            return searchSingleNode(iStashes, pFileName);
        } else if (pFileName.toLowerCase().endsWith(".d2i")) {
            return searchSingleNode(iSharedStashes, pFileName);
        } else if (pFileName.equalsIgnoreCase("all")) {
            return iAll;
        }
        return null;
    }

    private CharTreeNode searchSingleNode(DefaultMutableTreeNode pTreeNode, String pFileName) {
        Enumeration<?> lNodes = pTreeNode.depthFirstEnumeration();
        while (lNodes.hasMoreElements()) {
            Object lNode = lNodes.nextElement();
            if (lNode instanceof CharTreeNode && ((CharTreeNode) lNode).getFilename().equalsIgnoreCase(pFileName)) {
                return (CharTreeNode) lNode;
            }
        }
        System.err.println("D2ViewProject.searchNode() Not Found " + pFileName);
        return null;
    }

    /**
     * Returns the file name of the ATMA (.d2x) stash shown under the given point,
     * or null when the point is not over a stash in the tree.
     */
    public String getStashFileAt(Component pSource, Point pPoint) {
        if (!iTree.isShowing() || !pSource.isShowing()) {
            return null;
        }
        Point lPoint = SwingUtilities.convertPoint(pSource, pPoint, iTree);
        if (!iTree.getVisibleRect().contains(lPoint)) {
            return null;
        }
        TreePath lPath = iTree.getPathForLocation(lPoint.x, lPoint.y);
        if (lPath == null || !(lPath.getLastPathComponent() instanceof CharTreeNode)) {
            return null;
        }
        String lFileName = ((CharTreeNode) lPath.getLastPathComponent()).getFilename();
        return lFileName.toLowerCase().endsWith(".d2x") ? lFileName : null;
    }

    /**
     * Highlights the stash an item is being dragged onto (null for none).
     */
    public void showDropTarget(String pStashFileName) {
        if (!iShowingDropTarget) {
            iSelectionBeforeDrag = iTree.getSelectionPath();
            iShowingDropTarget = true;
        }
        CharTreeNode lNode = pStashFileName == null ? null : searchSingleNode(iStashes, pStashFileName);
        if (lNode == null) {
            iTree.clearSelection();
        } else {
            iTree.setSelectionPath(new TreePath(lNode.getPath()));
        }
    }

    public void clearDropTarget() {
        if (!iShowingDropTarget) {
            return;
        }
        iShowingDropTarget = false;
        if (iSelectionBeforeDrag != null) {
            iTree.setSelectionPath(iSelectionBeforeDrag);
        } else {
            iTree.clearSelection();
        }
        iSelectionBeforeDrag = null;
    }

    public void setProject(D2Project pProject) {
        iProject = pProject;
        refreshTreeModel(true, true, true);
    }

    public void refreshTreeModel(boolean pExpandChar, boolean pExpandStash, boolean pExpandSharedStash) {
        Set<String> lExpand = new HashSet<>(iExpandNext);
        iExpandNext.clear();
        if (iTreeModel != null) {
            Enumeration<TreePath> lOpen = iTree.getExpandedDescendants(new TreePath(iTreeModel.getRoot()));
            while (lOpen != null && lOpen.hasMoreElements()) {
                String lKey = nodeKey(lOpen.nextElement().getLastPathComponent());
                if (lKey != null) lExpand.add(lKey);
            }
        }
        iTreeModel = refreshTree();
        iTree.setModel(iTreeModel);
        if (pExpandChar) lExpand.add(nodeKey(iChars));
        if (pExpandStash) lExpand.add(nodeKey(iStashes));
        if (pExpandSharedStash) lExpand.add(nodeKey(iSharedStashes));
        // Rows below a row only appear once it is open, so going down the rows opens parents before children.
        for (int i = 0; i < iTree.getRowCount(); i++) {
            String lKey = nodeKey(iTree.getPathForRow(i).getLastPathComponent());
            if (lKey != null && lExpand.contains(lKey)) {
                iTree.expandRow(i);
            }
        }
    }

    /**
     * Names a part or folder node in a way that survives rebuilding the tree; null for other nodes.
     */
    private static String nodeKey(Object pNode) {
        if (pNode instanceof FolderTreeNode) {
            FolderTreeNode lNode = (FolderTreeNode) pNode;
            return folderKey(lNode.getFolders(), lNode.getFolderPath());
        }
        return null;
    }

    private static String folderKey(ProjectFolders pFolders, String pPath) {
        return pFolders.getPrefix() + ":" + pPath;
    }

    private DefaultTreeModel refreshTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("D2");

        iChars = addSection(root, "characters", iProject == null ? null : iProject.getCharFolders(),
                iProject == null ? null : iProject.getCharList());
        iStashes = addSection(root, "stashes", iProject == null ? null : iProject.getStashFolders(),
                iProject == null ? null : iProject.getStashList());
        iSharedStashes = addSection(root, "sharedstashes", iProject == null ? null : iProject.getSharedStashFolders(),
                iProject == null ? null : iProject.getSharedStashList());

        iAll = new CharTreeNode("All");
        root.add(iAll);

        // Ask each node whether it may hold children, so an empty folder still shows as a folder.
        return new DefaultTreeModel(root, true);
    }

    /**
     * Adds a part of the tree (Characters, Stashes, Shared Stashes) with its folders, each folder's
     * subfolders first, then its files.
     */
    private FolderTreeNode addSection(DefaultMutableTreeNode pRoot, String pLabel, ProjectFolders pFolders, List<?> pFiles) {
        FolderTreeNode lSection = new FolderTreeNode(pLabel, pFolders == null ? new ProjectFolders("") : pFolders, ProjectFolders.TOP);
        pRoot.add(lSection);
        if (pFolders == null) {
            return lSection;
        }
        Map<String, FolderTreeNode> lFolderNodes = new HashMap<>();
        lFolderNodes.put(ProjectFolders.TOP, lSection);
        for (String lPath : pFolders.getFolders()) {
            FolderTreeNode lNode = new FolderTreeNode(ProjectFolders.getName(lPath), pFolders, lPath);
            lFolderNodes.getOrDefault(ProjectFolders.getParent(lPath), lSection).add(lNode);
            lFolderNodes.put(lPath, lNode);
        }
        for (Object lFile : pFiles) {
            String lFileName = (String) lFile;
            lFolderNodes.getOrDefault(pFolders.getFolderOf(lFileName), lSection).add(new CharTreeNode(lFileName));
        }
        return lSection;
    }

    private void folderChanged() {
        iProject.saveProject();
        refreshTreeModel(false, false, false);
    }

    private void startFolderMenu(FolderTreeNode pNode, int pX, int pY) {
        if (iProject == null) {
            return;
        }
        JPopupMenu lMenu = new JPopupMenu();
        JMenuItem lNew = new JMenuItem(pNode.isSection() ? "New folder..." : "New subfolder...");
        lNew.addActionListener(e -> newFolder(pNode));
        lMenu.add(lNew);
        if (!pNode.isSection()) {
            JMenuItem lRename = new JMenuItem("Rename folder...");
            lRename.addActionListener(e -> renameFolder(pNode));
            lMenu.add(lRename);
            JMenuItem lDelete = new JMenuItem("Delete folder");
            lDelete.setToolTipText("What is in the folder moves up a level; nothing is removed from the project");
            lDelete.addActionListener(e -> deleteFolder(pNode));
            lMenu.add(lDelete);
        }
        if (pNode.getFolders() == iProject.getStashFolders()) {
            List<String> lStashes = getStashesIn(pNode);
            if (!lStashes.isEmpty()) {
                lMenu.addSeparator();
                JMenuItem lShow = new JMenuItem("Show these stashes in All");
                lShow.setEnabled(lStashes.stream().anyMatch(lStash -> !iProject.isInAllView(lStash)));
                lShow.addActionListener(e -> setInAllView(lStashes, true));
                lMenu.add(lShow);
                JMenuItem lHide = new JMenuItem("Leave these stashes out of All");
                lHide.setEnabled(lStashes.stream().anyMatch(iProject::isInAllView));
                lHide.addActionListener(e -> setInAllView(lStashes, false));
                lMenu.add(lHide);
            }
        }
        lMenu.show(iTree, pX, pY);
    }

    /**
     * The ATMA stashes in a Stashes folder and all its subfolders (every stash for the Stashes node itself).
     */
    private List<String> getStashesIn(FolderTreeNode pNode) {
        List<String> lStashes = new ArrayList<>();
        for (Object lStash : iProject.getStashList()) {
            if (pNode.isSection()
                    || ProjectFolders.isInside(iProject.getStashFolders().getFolderOf((String) lStash), pNode.getFolderPath())) {
                lStashes.add((String) lStash);
            }
        }
        return lStashes;
    }

    /**
     * Shows ATMA stashes in the All view or leaves them out, saves the project and updates an open All view.
     */
    private void setInAllView(List<String> pStashes, boolean pInAllView) {
        boolean lChanged = false;
        for (String lStash : pStashes) {
            if (iProject.setInAllView(lStash, pInAllView)) {
                lChanged = true;
                iFileManager.allViewChanged(lStash);
            }
        }
        if (lChanged) {
            iProject.saveProject();
            iTree.repaint();
        }
    }

    private void newFolder(FolderTreeNode pParent) {
        String lName = askFolderName("New folder", "");
        if (lName == null) {
            return;
        }
        String lPath = pParent.getFolders().addFolder(pParent.getFolderPath(), lName);
        if (lPath == null) {
            folderNameTaken(lName);
            return;
        }
        iExpandNext.add(nodeKey(pParent));
        folderChanged();
    }

    private void renameFolder(FolderTreeNode pNode) {
        String lName = askFolderName("Rename folder", ProjectFolders.getName(pNode.getFolderPath()));
        if (lName == null) {
            return;
        }
        boolean lWasOpen = iTree.isExpanded(new TreePath(pNode.getPath()));
        String lPath = pNode.getFolders().renameFolder(pNode.getFolderPath(), lName);
        if (lPath == null) {
            folderNameTaken(lName);
            return;
        }
        if (lWasOpen) {
            iExpandNext.add(folderKey(pNode.getFolders(), lPath));
        }
        folderChanged();
    }

    private void deleteFolder(FolderTreeNode pNode) {
        if (iProject == null) {
            return;
        }
        if (pNode.getChildCount() > 0) {
            int lAnswer = JOptionPane.showConfirmDialog(iTree,
                    "Delete the folder \"" + ProjectFolders.getName(pNode.getFolderPath()) + "\"?\n"
                            + "What is in it moves up a level. Nothing is removed from the project.",
                    "Delete folder", JOptionPane.OK_CANCEL_OPTION);
            if (lAnswer != JOptionPane.OK_OPTION) {
                return;
            }
        }
        pNode.getFolders().deleteFolder(pNode.getFolderPath());
        folderChanged();
    }

    private String askFolderName(String pTitle, String pCurrent) {
        while (true) {
            Object lAnswer = JOptionPane.showInputDialog(iTree, "Folder name:", pTitle,
                    JOptionPane.PLAIN_MESSAGE, null, null, pCurrent);
            if (lAnswer == null) {
                return null;
            }
            String lName = ProjectFolders.cleanName(lAnswer.toString());
            if (lName != null) {
                return lName;
            }
            JOptionPane.showMessageDialog(iTree, "A folder name can't be empty or contain \"" + ProjectFolders.SEPARATOR + "\".",
                    pTitle, JOptionPane.WARNING_MESSAGE);
            pCurrent = lAnswer.toString();
        }
    }

    private void folderNameTaken(String pName) {
        JOptionPane.showMessageDialog(iTree, "There is already a folder named \"" + pName + "\" there.",
                "Folder", JOptionPane.WARNING_MESSAGE);
    }

    private FolderTreeNode getSection(DefaultMutableTreeNode pNode) {
        for (TreeNode lNode = pNode; lNode != null; lNode = lNode.getParent()) {
            if (lNode instanceof FolderTreeNode && ((FolderTreeNode) lNode).isSection()) {
                return (FolderTreeNode) lNode;
            }
        }
        return null;
    }

    private static FolderTreeNode findFolderNode(FolderTreeNode pSection, String pPath) {
        Enumeration<?> lNodes = pSection.breadthFirstEnumeration();
        while (lNodes.hasMoreElements()) {
            Object lNode = lNodes.nextElement();
            if (lNode instanceof FolderTreeNode && ((FolderTreeNode) lNode).getFolderPath().equals(pPath)) {
                return (FolderTreeNode) lNode;
            }
        }
        return null;
    }

    /**
     * Moves a file or folder into a folder (or a part's top level). Returns false when it can't go there.
     */
    private boolean moveInto(DefaultMutableTreeNode pMoved, FolderTreeNode pTarget) {
        if (iProject == null || pTarget == null || getSection(pMoved) != getSection(pTarget)) {
            return false;
        }
        ProjectFolders lFolders = pTarget.getFolders();
        boolean lMoved;
        if (pMoved instanceof CharTreeNode) {
            lMoved = lFolders.moveFile(((CharTreeNode) pMoved).getFilename(), pTarget.getFolderPath());
        } else if (pMoved instanceof FolderTreeNode && !((FolderTreeNode) pMoved).isSection()) {
            lMoved = lFolders.moveFolder(((FolderTreeNode) pMoved).getFolderPath(), pTarget.getFolderPath()) != null;
        } else {
            lMoved = false;
        }
        if (lMoved) {
            iExpandNext.add(nodeKey(pTarget));
            folderChanged();
        }
        return lMoved;
    }

    /**
     * A part of the tree (Characters, Stashes, Shared Stashes) or one of the user's folders in it.
     */
    private static class FolderTreeNode extends DefaultMutableTreeNode {
        private static final long serialVersionUID = 1L;
        private final ProjectFolders iFolders;
        private final String iPath;

        FolderTreeNode(String pLabel, ProjectFolders pFolders, String pPath) {
            super(pLabel, true);
            iFolders = pFolders;
            iPath = pPath;
        }

        ProjectFolders getFolders() {
            return iFolders;
        }

        String getFolderPath() {
            return iPath;
        }

        boolean isSection() {
            return iPath.isEmpty();
        }
    }

    /**
     * Drag a character, stash or folder onto a folder (or a file in it, or the part's own node) to move it
     * there. Only within the same part of the tree.
     */
    private class FolderTransferHandler extends TransferHandler {
        private static final long serialVersionUID = 1L;
        private DefaultMutableTreeNode iDragged;

        @Override
        public int getSourceActions(JComponent pComponent) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent pComponent) {
            TreePath lPath = iTree.getSelectionPath();
            iDragged = null;
            if (lPath == null) {
                return null;
            }
            Object lNode = lPath.getLastPathComponent();
            if ((lNode instanceof CharTreeNode && lNode != iAll)
                    || (lNode instanceof FolderTreeNode && !((FolderTreeNode) lNode).isSection())) {
                iDragged = (DefaultMutableTreeNode) lNode;
                return new StringSelection(lNode.toString());
            }
            return null;
        }

        @Override
        protected void exportDone(JComponent pSource, Transferable pData, int pAction) {
            iDragged = null;
        }

        private FolderTreeNode getTarget(TransferSupport pSupport) {
            if (!pSupport.isDrop() || iDragged == null) {
                return null;
            }
            TreePath lPath = ((JTree.DropLocation) pSupport.getDropLocation()).getPath();
            if (lPath == null) {
                return null;
            }
            Object lNode = lPath.getLastPathComponent();
            if (lNode instanceof CharTreeNode) {
                lNode = ((CharTreeNode) lNode).getParent();
            }
            if (!(lNode instanceof FolderTreeNode) || getSection(iDragged) != getSection((FolderTreeNode) lNode)) {
                return null;
            }
            FolderTreeNode lTarget = (FolderTreeNode) lNode;
            if (iDragged instanceof FolderTreeNode
                    && ProjectFolders.isInside(lTarget.getFolderPath(), ((FolderTreeNode) iDragged).getFolderPath())) {
                return null;
            }
            return lTarget;
        }

        @Override
        public boolean canImport(TransferSupport pSupport) {
            if (!pSupport.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return false;
            }
            return getTarget(pSupport) != null;
        }

        @Override
        public boolean importData(TransferSupport pSupport) {
            FolderTreeNode lTarget = getTarget(pSupport);
            DefaultMutableTreeNode lDragged = iDragged;
            if (lTarget == null || lDragged == null) {
                return false;
            }
            // Rebuild the tree after the drag has finished, not in the middle of it.
            SwingUtilities.invokeLater(() -> moveInto(lDragged, lTarget));
            return true;
        }
    }

    private class CharTreeNode extends DefaultMutableTreeNode {
        /**
         *
         */
        private static final long serialVersionUID = -7061935461861570778L;
        private String iFileName;
        private final boolean iAtmaStash;
        private Color iForeGround = UIManager.getColor("Tree.textForeground");
        private boolean iItemListRead = false;
        private boolean iFileOpened = false;

        public CharTreeNode(String pFileName) {
            super(getCharStr(pFileName), false);
            iFileName = pFileName;
            iAtmaStash = iFileName.toLowerCase().endsWith(".d2x");

            if (!iFileName.equalsIgnoreCase("all")) {
                File lTest = new File(iFileName);
                if (!lTest.exists() || !lTest.isFile() || !lTest.canRead() || !lTest.canWrite()) {
                    iForeGround = new Color(187, 67, 67);
                }
            }
        }

        private String getFilename() {
            return iFileName;
        }

        public void itemListRead() {
            iItemListRead = true;
            Color lOpenColor = UIManager.getColor("GoMule.projectTree.openForeground");
            iForeGround = lOpenColor != null ? lOpenColor : new Color(0, 150, 0);
        }

        public void itemListClosed() {
            iItemListRead = false;
            iForeGround = UIManager.getColor("Tree.textForeground");
        }

        public void fileOpened() {
            iFileOpened = true;
        }

        public void fileClosed() {
            iFileOpened = false;
        }

        public Color getForeGround() {
            return iForeGround;
        }

        /**
         * True for an ATMA stash the user has left out of the All view.
         */
        boolean isLeftOutOfAll() {
            return iAtmaStash && iProject != null && !iProject.isInAllView(iFileName);
        }

        public void view() {
            if (iFileName.equalsIgnoreCase("all")) {
                // open All
//				System.err.println("All view not done yet");
                iFileManager.openStash("all", true);
            }
            if (iFileName.toLowerCase().endsWith(".d2s")) {
                iFileManager.openChar(iFileName, true);
            } else if (iFileName.toLowerCase().endsWith(".d2x")) {
                iFileManager.openStash(iFileName, true);
            } else if (iFileName.toLowerCase().endsWith(".d2i")) {
                iFileManager.openSharedStash(iFileName, true);
            }
        }

        public void close() {
            iFileManager.closeFileName(getFilename());
        }

        public void fullDump() {
            iFileManager.singleTxtDump(getFilename());
        }

        public void remove() {
            iProject.deleteCharStash(getFilename());
        }

        public void startMenu(int pX, int pY) {
            JPopupMenu lMenu = new JPopupMenu();

//			JMenuItem lName = new JMenuItem( getFilename() );
            lMenu.add(new JLabel(getFilename()));

//			lMenu.setLocation(pX, pY);

//			JMenu lList = new JMenu();

            if (iFileOpened) {
                JMenuItem lOpen = new JMenuItem("Move to Top");
                lOpen.setAccelerator(KeyStroke.getKeyStroke(new Character('M'), KeyEvent.ALT_MASK));
                lMenu.add(lOpen);
                lOpen.addActionListener(new ActionNodeView(this));

                JMenuItem lFullDump = new JMenuItem("Full Dump");
                lFullDump.setAccelerator(KeyStroke.getKeyStroke(new Character('F'), KeyEvent.ALT_MASK));
//				lMenu.add(lFullDump);
                lFullDump.addActionListener(new ActionNodeFullDump(this));

                JMenuItem lClose = new JMenuItem("Close");
                lClose.setAccelerator(KeyStroke.getKeyStroke(new Character('C'), KeyEvent.ALT_MASK));
                lMenu.add(lClose);
                lClose.addActionListener(new ActionNodeClose(this));
            } else {
                if (iItemListRead) {
                    JMenuItem lOpen = new JMenuItem("View");
                    lOpen.setAccelerator(KeyStroke.getKeyStroke(new Character('V'), KeyEvent.ALT_MASK));
                    lMenu.add(lOpen);
                    lOpen.addActionListener(new ActionNodeView(this));
                } else {
                    JMenuItem lOpen = new JMenuItem("Open");
                    lOpen.setAccelerator(KeyStroke.getKeyStroke(new Character('O'), KeyEvent.ALT_MASK));
                    lMenu.add(lOpen);
                    lOpen.addActionListener(new ActionNodeView(this));
                }
            }

            if (iAtmaStash) {
                JMenuItem lSortRules = new JMenuItem("Sorting rules...");
                lMenu.add(lSortRules);
                lSortRules.addActionListener(e -> StashSortRulesDialog.showFor(iTree, iProject, iFileName));

                JCheckBoxMenuItem lInAll = new JCheckBoxMenuItem("Show in All", iProject.isInAllView(iFileName));
                lInAll.setToolTipText("Untick to leave this stash's items out of the All view");
                lMenu.add(lInAll);
                lInAll.addActionListener(e -> setInAllView(Collections.singletonList(iFileName), lInAll.isSelected()));
            }

            if (iAtmaStash || iFileName.toLowerCase().endsWith(".d2i")) {
                JMenuItem lRestore = new JMenuItem("Restore from backup...");
                lMenu.add(lRestore);
                lRestore.addActionListener(e -> RestoreBackupDialog.showFor(iTree, iFileManager, iFileName));
            }

            FolderTreeNode lSection = getSection(this);
            if (lSection != null && !lSection.getFolders().getFolders().isEmpty()) {
                JMenu lMoveTo = new JMenu("Move to folder");
                String lCurrent = lSection.getFolders().getFolderOf(iFileName);
                JMenuItem lTop = new JMenuItem("(top level)");
                lTop.setEnabled(!lCurrent.isEmpty());
                lTop.addActionListener(e -> moveInto(this, lSection));
                lMoveTo.add(lTop);
                for (String lPath : lSection.getFolders().getFolders()) {
                    JMenuItem lFolder = new JMenuItem(lPath.replace(ProjectFolders.SEPARATOR, " / "));
                    lFolder.setEnabled(!lPath.equals(lCurrent));
                    lFolder.addActionListener(e -> moveInto(this, findFolderNode(lSection, lPath)));
                    lMoveTo.add(lFolder);
                }
                lMenu.add(lMoveTo);
            }

            JMenuItem lRemove = new JMenuItem("Remove from project");
            lRemove.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
            lMenu.add(lRemove);
            lRemove.addActionListener(new ActionNodeRemove(this));

            lMenu.show(iTree, pX, pY);
//			lMenu.add()
        }

    } // end class CharTreeNode

    private abstract class NodeAction implements ActionListener {
        protected CharTreeNode iNode;

        public NodeAction(CharTreeNode pNode) {
            iNode = pNode;
        }
    }

    private class ActionNodeView extends NodeAction {
        public ActionNodeView(CharTreeNode pNode) {
            super(pNode);
        }

        public void actionPerformed(ActionEvent pEvent) {
            iNode.view();
        }
    }

    private class ActionNodeRemove extends NodeAction {
        public ActionNodeRemove(CharTreeNode pNode) {
            super(pNode);
        }

        public void actionPerformed(ActionEvent pEvent) {
            iNode.remove();
        }
    }

    private class ActionNodeClose extends NodeAction {
        public ActionNodeClose(CharTreeNode pNode) {
            super(pNode);
        }

        public void actionPerformed(ActionEvent pEvent) {
            iNode.close();
        }
    }

    private class ActionNodeFullDump extends NodeAction {
        public ActionNodeFullDump(CharTreeNode pNode) {
            super(pNode);
        }

        public void actionPerformed(ActionEvent pEvent) {
            iNode.fullDump();
        }
    }

}
