package gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * This class provides utilities for string processing:
 * 1) Stem a term.
 * 2) Split a query into tokens and convert to lowercase. Uses a predefined set of delimiters.
 * 3) Split a string using the space character as delimiter.
 */
public class ProcessText {
    private static final String splitDelimiters = "\u0020“”/\"-.\uff0c[](),?+#，*";

    /**
     * Applies stemming. Returns the original term only if the stemmed term has less than 3 characters
     * or the original term has less than 4 characters.
     *
     * @param term
     * @return
     */
    public static String applyStemming(String term) {
        if (term.length() > 3) {
            String stemTerm = Stemmer.Stem(term);
            if (stemTerm.length() >= 3) {
                return stemTerm;
            }
        }
        return term;
    }

    /**
     * Splits a string into lowercase tokens using ProcessText.splitDelimiters
     *
     * @param query
     * @return
     */
    public static List<String> split(String query) {
        StringTokenizer tokenizer = new StringTokenizer(query, splitDelimiters);
        List<String> terms = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            terms.add(token.toLowerCase());
        }
        return terms;
    }
}
