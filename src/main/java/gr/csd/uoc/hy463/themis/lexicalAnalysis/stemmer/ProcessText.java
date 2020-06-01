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
     * Splits a query into tokens and applies stopwords, stemming (if they are enabled)
     * @param query
     * @return
     */
    public static List<String> editQuery(String query, boolean useStopwords, boolean useStemmer) {
        String tokens = "\u0020“”/\"-.\uff0c[]()?+#，";
        StringTokenizer tokenizer = new StringTokenizer(query, tokens);
        List<String> terms = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            token = applyStopwordsStemming(token, useStopwords, useStemmer);
            if (token != null) {
                terms.add(token);
            }
        }
        return terms;
    }
}
