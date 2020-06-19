package gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * Class that can be used for applying the following operations on a term:
 * Stemming & StopWord removal
 *
 * We can also use to split a query into terms before performing a search
 */
public class ProcessText {

    /**
     * Applies stopword/stemming on a term.
     * @param term
     * @param useStopwords
     * @param useStemmer
     * @return Stemmed term if useStemmer is true. Null if useStopwords is true and term is
     * a stopword.
     */
    public static String applyStopwordsStemming(String term, boolean useStopwords, boolean useStemmer) {
        String stemTerm = null;
        if (useStopwords) {
            term = term.toLowerCase();
            if (StopWords.isStopWord(term)) {
                return null;
            }
        }
        if (useStemmer && term.length() > 3) {
            stemTerm = Stemmer.Stem(term);
            if (stemTerm.length() > 2 && (!useStopwords || !StopWords.isStopWord(stemTerm))) {
                term = stemTerm;
            }
        }
        if (!useStopwords && stemTerm != term) {
            term = term.toLowerCase();
        }
        return term;
    }

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
     * Splits a query into tokens
     * @param query
     * @return
     */
    public static List<String> split(String query) {
        String tokens = "\u0020“”/\"-.\uff0c[]()?+#，*";
        StringTokenizer tokenizer = new StringTokenizer(query, tokens);
        List<String> terms = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            terms.add(token);
        }
        return terms;
    }
}
