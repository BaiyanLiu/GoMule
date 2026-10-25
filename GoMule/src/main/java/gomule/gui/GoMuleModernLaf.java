package gomule.gui;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * The "Modern GoMule" appearance: FlatLaf's dark theme with a charcoal palette, a gold accent, rounded controls and
 * borderless item tables. Its colors are in GoMuleModernLaf.properties next to this class, which FlatLaf loads itself.
 */
public class GoMuleModernLaf extends FlatDarkLaf {

    public static final String NAME = "GoMule Modern";

    @Override
    public String getName() {
        return NAME;
    }
}
