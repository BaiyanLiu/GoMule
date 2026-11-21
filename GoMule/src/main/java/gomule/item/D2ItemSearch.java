package gomule.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The stash "Contains" search: every word must appear somewhere in the item's text, a word starting with {@code -}
 * must not appear (unless a digit follows, as in {@code -20%}), and {@code a|b} matches either. Quotes keep a phrase with spaces together ({@code "cast rate"}).
 * Matching ignores case and treats every other character literally.
 */
public final class D2ItemSearch {

    private static final class Term {
        final List<String> alternatives;
        final boolean exclude;

        Term(List<String> alternatives, boolean exclude) {
            this.alternatives = alternatives;
            this.exclude = exclude;
        }

        boolean foundIn(String lowerText) {
            for (String lAlternative : alternatives) {
                if (lowerText.contains(lAlternative)) return true;
            }
            return false;
        }
    }

    private final List<Term> terms;

    private D2ItemSearch(List<Term> terms) {
        this.terms = terms;
    }

    private static final Pattern SPACED_BAR = Pattern.compile("\\s*\\|\\s*");

    public static D2ItemSearch parse(String text) {
        List<Term> lTerms = new ArrayList<>();
        if (text == null) return new D2ItemSearch(lTerms);
        // "a | b" means the same as "a|b"
        for (String lToken : tokenize(SPACED_BAR.matcher(text).replaceAll("|"))) {
            if (lToken.equals("-")) continue; // half-typed "-word"; don't hide everything meanwhile
            // "-20%" is text to find (as in "Requirements -20%"), "-eth" excludes
            boolean lExclude = lToken.length() > 1 && lToken.charAt(0) == '-' && !Character.isDigit(lToken.charAt(1));
            String lBody = lExclude ? lToken.substring(1) : lToken;
            List<String> lAlternatives = new ArrayList<>();
            for (String lAlternative : lBody.split("\\|")) {
                String lClean = lAlternative.replace("\"", "").toLowerCase(Locale.ROOT);
                if (!lClean.isEmpty()) lAlternatives.add(lClean);
            }
            if (!lAlternatives.isEmpty()) lTerms.add(new Term(Collections.unmodifiableList(lAlternatives), lExclude));
        }
        return new D2ItemSearch(lTerms);
    }

    /**
     * Splits on whitespace outside double quotes; the quotes stay in the tokens and are dropped by {@link #parse}.
     */
    private static List<String> tokenize(String text) {
        List<String> lTokens = new ArrayList<>();
        StringBuilder lCurrent = new StringBuilder();
        boolean lQuoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') lQuoted = !lQuoted;
            if (Character.isWhitespace(c) && !lQuoted) {
                if (lCurrent.length() > 0) lTokens.add(lCurrent.toString());
                lCurrent.setLength(0);
            } else {
                lCurrent.append(c);
            }
        }
        if (lCurrent.length() > 0) lTokens.add(lCurrent.toString());
        return lTokens;
    }

    public boolean matches(D2Item item) {
        // with nothing to search for, don't build the item's text at all
        return terms.isEmpty() || matchesText(item.getItemDumpLowerCase());
    }

    /**
     * Whether the already lower-cased item text satisfies every term.
     */
    public boolean matchesText(String lowerText) {
        for (Term lTerm : terms) {
            if (lTerm.foundIn(lowerText) == lTerm.exclude) return false;
        }
        return true;
    }
}
