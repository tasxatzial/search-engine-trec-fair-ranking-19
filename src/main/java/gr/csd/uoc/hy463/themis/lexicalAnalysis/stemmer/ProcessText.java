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
     * Splits a string into lowercase tokens using a predefined set of delimiters
     *
     * @param query
     * @return
     */
    public static List<String> split(String query) {
        String delims = "\u0020“”/\"-.\uff0c[](),?+#，*";
        StringTokenizer tokenizer = new StringTokenizer(query, delims);
        List<String> terms = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            terms.add(token.toLowerCase());
        }
        return terms;
    }

    /**
     * Splits a string into a List of terms using the space character as delimiter.
     * If there are consecutive spaces, an empty string is added as a term.
     *
     * @param str
     */
    public static List<String> splitSpace(String str) {
        StringBuilder sb = new StringBuilder();
        List<String> words = new ArrayList<>();
        char[] strArray = str.toCharArray();
        for (char c : strArray) {
            if (c == ' ') {
                words.add(sb.toString());
                sb.delete(0, sb.length());
            } else {
                sb.append(c);
            }
        }
        if (strArray[strArray.length - 1] != ' ') {
            words.add(sb.toString());
        }
        return words;
    }
}
