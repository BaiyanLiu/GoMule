/*
 * Created on 5-mrt-2007
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package gomule.util;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * @author Marco & Silospen
 * <p>
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class D2CellStringRenderer extends DefaultTableCellRenderer {

    /**
     *
     */
    private static final long serialVersionUID = 1351337843493777035L;

    public D2CellStringRenderer() {


    }

    public Component getTableCellRendererComponent(JTable pTable, Object pValue, boolean pIsSelected, boolean pHasFocus, int pRow, int pColumn) {

        Object lValue;
        Color lForeground = null;
        String lTooltip = null;

//              	System.out.println("yo");
//        	super.setBackground(Color.black);

        if (pValue instanceof D2CellValue) {
            lValue = ((D2CellValue) pValue).getValue();
            lForeground = ((D2CellValue) pValue).getForeground();
            lTooltip = ((D2CellValue) pValue).getTooltip();
        } else {
            lValue = pValue;
        }

        Component lRenderer = super.getTableCellRendererComponent(pTable, lValue, pIsSelected, pHasFocus, pRow, pColumn);

        // the Modern appearance sets these; other appearances keep the original gray rows with black selection
        Color lBackground = UIManager.getColor("GoMule.itemTable.background");
        Color lAlternate = UIManager.getColor("GoMule.itemTable.alternateBackground");
        Color lSelection = UIManager.getColor("GoMule.itemTable.selectionBackground");
        lRenderer.setBackground(lBackground == null ? Color.DARK_GRAY : (pRow % 2 == 1 && lAlternate != null ? lAlternate : lBackground));
        if (pIsSelected) {
            lRenderer.setBackground(lSelection == null ? Color.black : lSelection);
        }
        //  lRenderer.setBackground(Color.black);

        if (lForeground != null) {
            // on the Modern selected row, lighten dim item colors (gray, blue, green) until they read clearly
            lRenderer.setForeground(pIsSelected && lSelection != null ? readableOn(lForeground, lSelection) : lForeground);
        } else {
            lRenderer.setForeground(Color.black);
        }

        if (lRenderer instanceof D2CellStringRenderer) {
            ((D2CellStringRenderer) lRenderer).setToolTipText(lTooltip);
        }


        return lRenderer;
    }

    static Color readableOn(Color pForeground, Color pBackground) {
        Color lColor = pForeground;
        for (int i = 1; i <= 10 && contrast(lColor, pBackground) < 4.5; i++) {
            float lMix = i / 10f;
            lColor = new Color(
                    Math.round(pForeground.getRed() + (255 - pForeground.getRed()) * lMix),
                    Math.round(pForeground.getGreen() + (255 - pForeground.getGreen()) * lMix),
                    Math.round(pForeground.getBlue() + (255 - pForeground.getBlue()) * lMix));
        }
        return lColor;
    }

    static double contrast(Color pA, Color pB) {
        double lA = luminance(pA);
        double lB = luminance(pB);
        return (Math.max(lA, lB) + 0.05) / (Math.min(lA, lB) + 0.05);
    }

    private static double luminance(Color pColor) {
        return 0.2126 * channel(pColor.getRed()) + 0.7152 * channel(pColor.getGreen()) + 0.0722 * channel(pColor.getBlue());
    }

    private static double channel(int pValue) {
        double lValue = pValue / 255.0;
        return lValue <= 0.03928 ? lValue / 12.92 : Math.pow((lValue + 0.055) / 1.055, 2.4);
    }

}