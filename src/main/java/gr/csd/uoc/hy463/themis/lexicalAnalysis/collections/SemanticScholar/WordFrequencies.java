package gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar;

import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.Stemmer;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
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
    public Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> createWordsMap(S2TextualEntry entry) {
        Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWordsMap = new HashMap<>();

        addToWordsMap(entry.getTitle(), DocInfo.PROPERTY.TITLE , entryWordsMap);
        addToWordsMap(entry.getPaperAbstract(), DocInfo.PROPERTY.ABSTRACT, entryWordsMap);
        for (String entity : entry.getEntities()) {
            addToWordsMap(entity, DocInfo.PROPERTY.ENTITIES, entryWordsMap);
        }
        for (String fieldsOfStudy : entry.getFieldsOfStudy()) {
            addToWordsMap(fieldsOfStudy, DocInfo.PROPERTY.FIELDS_OF_STUDY, entryWordsMap);
        }
        for (Pair<String, List<String>> author : entry.getAuthors()) {
            addToWordsMap(author.getL(), DocInfo.PROPERTY.AUTHORS_NAMES, entryWordsMap);
        }
        addToWordsMap(Integer.toString(entry.getYear()), DocInfo.PROPERTY.YEAR, entryWordsMap);
        addToWordsMap(entry.getVenue(), DocInfo.PROPERTY.VENUE, entryWordsMap);
        addToWordsMap(entry.getJournalName(), DocInfo.PROPERTY.JOURNAL_NAME, entryWordsMap);
        for (String source : entry.getSources()) {
            addToWordsMap(source, DocInfo.PROPERTY.SOURCES, entryWordsMap);
        }
        return entryWordsMap;
    }

    /* Takes a field (e.g title) and updates the entryWords map of term
    frequencies based on the terms that appear in this field.
     */
    private void addToWordsMap(String field, DocInfo.PROPERTY prop,
                               Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWords) {
        String delimiter = getDelimiter(prop);
        StringTokenizer tokenizer = new StringTokenizer(field, delimiter);
        List<Pair<DocInfo.PROPERTY, Integer>> tokenValues;
        String currentToken;
        Pair<DocInfo.PROPERTY, Integer> lastPair;
        while(tokenizer.hasMoreTokens()) {
            currentToken = tokenizer.nextToken().toLowerCase();
            if (useStopwords && StopWords.isStopWord(currentToken)) {
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

    /* Returns the split pattern that will be used for splitting a String */
    private String getDelimiter(DocInfo.PROPERTY prop) {
        switch (prop) {
            case TITLE: case ABSTRACT:
                return "\u0020.:,[]()'/\"’+?<>*“”\u00A0=×!∗´‘{}∼~^`′›‹_\u0091\u0092@″•·・‡†‟„&#¶，،$|\\¿;%»«§¡˚©™®¸‚…：＂±／〔〕【】《》（）＜＞\n\t\r\f\u2003\u202F\u2009\u2002\u2005\u200A\u2006\u200B\u2008";
            case AUTHORS_NAMES:
                return "\u0020'（）．･;,，‘’“”«».*()&#·\n\r";
            case VENUE:
                return "\u0020™®’?\\/'&!|*+\";[]=():,.\n\r";
            case JOURNAL_NAME:
                return "\u0020'&#[](),.:/\"\n\r";
            case ENTITIES:
                return "\u0020’*^:,;&_!#\"<>[]'./()\n\r";
            default:
                return "\u0020\n\r";
        }
    }
}
