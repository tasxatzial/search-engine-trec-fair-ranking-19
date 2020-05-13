package gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer;

import java.util.regex.Pattern;

/**
 * Class that can be used for applying the following operations on a term:
 * Stemming & StopWord check | Dash replacement/removal.
 */
public class ProcessText {

    //dashes
    private static Pattern anyDash = Pattern.compile("[―−—–‑‐]+");

    /**
     * Applies stopword/stemming on a term.
     * @param term
     * @param useStopwords
     * @param useStemmer
     * @return Stemmed term if useStemmer is true. Null if useStopwords is true and term is
     * a stopword.
     */
    public static String indexingProcess(String term, boolean useStopwords, boolean useStemmer) {
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
     * Removes leading/trailing dashes from a term and replaces all other dashes
     * with the minus sign (-).
     * @param term
     * @return
     */
    public static String editDashes(String term) {
        term = anyDash.matcher(term).replaceAll("-");
        return term;
    }
}
