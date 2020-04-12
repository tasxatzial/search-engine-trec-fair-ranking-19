package gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar;

import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.Stemmer;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.util.*;

/**
 * Creates a map of term frequencies for a textual entry.
 * Each value of the map is the list of field frequencies of the corresponding term:
 * <<field1, tf1> <field1, tf2> ...>
 */
public class WordFrequencies {
    private boolean useStemmer;
    private boolean useStopwords;

    public WordFrequencies(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config is null");
        }
        useStemmer = config.getUseStemmer();
        useStopwords = config.getUseStopwords();
    }

    /**
     * Returns the map of term frequencies.
     * @param entry
     * @return
     */
    public Map<String, List<Pair<DocInfoEssential.PROPERTY, Integer>>> createWordsMap(S2TextualEntry entry) {
        Map<String, List<Pair<DocInfoEssential.PROPERTY, Integer>>> entryWordsMap = new HashMap<>();

        addToWordsMap(entry.getTitle(), DocInfoEssential.PROPERTY.TITLE , entryWordsMap);

        return entryWordsMap;
    }

    /* Takes a field (e.g title) and updates the entryWords map of term
    frequencies based on the terms that appear in this field.
     */
    private void addToWordsMap(String field, DocInfoEssential.PROPERTY prop,
                               Map<String, List<Pair<DocInfoEssential.PROPERTY, Integer>>> entryWords) {
        String delimiter = getDelimiter(prop);
        StringTokenizer tokenizer = new StringTokenizer(field, delimiter);
        List<Pair<DocInfoEssential.PROPERTY, Integer>> tokenValues;
        String currentToken;
        Pair<DocInfoEssential.PROPERTY, Integer> lastPair;
        while(tokenizer.hasMoreTokens()) {
            currentToken = tokenizer.nextToken().toLowerCase();
            if (useStopwords && gr.csd.uoc.hy463.themis.stemmer.StopWords.isStopWord(currentToken)) {
                continue;
            }
            if (useStemmer) {
                currentToken = Stemmer.Stem(currentToken);
            }

            tokenValues = entryWords.get(currentToken);
            if (tokenValues != null) {
                lastPair = tokenValues.get(tokenValues.size() - 1);
                if (lastPair.getL() == prop) {
                    lastPair.setR(lastPair.getR() + 1);
                }
                else {
                    tokenValues.add(new Pair<>(prop, 1));
                }
            }
            else {
                tokenValues = new ArrayList<>();
                tokenValues.add(new Pair<>(prop, 1));
                entryWords.put(currentToken, tokenValues);
            }
        }
    }

    /* Returns the split pattern that will be used for splitting a prop (e.g title) */
    private String getDelimiter(DocInfoEssential.PROPERTY prop) {
        switch (prop) {
            case TITLE:
                return " ›‹′_`‘@″•‡†‟„&#.,()'\"[]$|/\\?-“”*{}<>:;’%+»«§¡!\n\t\r\f";
            default:
                return " \n\t\r\f";
        }
    }
}
