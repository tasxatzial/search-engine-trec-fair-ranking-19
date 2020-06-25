package gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * String processing functions
 */
public class ProcessText {

    /**
     * Applies stemming if the specified term has more than 3 characters. Returns the non-stemmed term
     * if the stemmed term has less than 3 characters.
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
     * Splits a query into tokens and converts them to lowercase
     * @param query
     * @return
     */
    public static List<String> split(String query) {
        String tokens = "\u0020“”/\"-.\uff0c[](),?+#，*";
        StringTokenizer tokenizer = new StringTokenizer(query, tokens);
        List<String> terms = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            terms.add(token.toLowerCase());
        }
        return terms;
    }
}
