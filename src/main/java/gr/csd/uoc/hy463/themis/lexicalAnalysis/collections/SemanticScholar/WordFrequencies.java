package gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar;

import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessTerm;
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

        addToWordsMap_afterDashSplit(entry.getTitle(), DocInfo.PROPERTY.TITLE , entryWordsMap);
        addToWordsMap_afterDashSplit(entry.getPaperAbstract(), DocInfo.PROPERTY.ABSTRACT, entryWordsMap);
        for (String entity : entry.getEntities()) {
            addToWordsMap_afterDashSplit(entity, DocInfo.PROPERTY.ENTITIES, entryWordsMap);
        }
        for (String fieldsOfStudy : entry.getFieldsOfStudy()) {
            addToWordsMap(fieldsOfStudy, DocInfo.PROPERTY.FIELDS_OF_STUDY, entryWordsMap);
        }
        for (Pair<String, List<String>> author : entry.getAuthors()) {
            addToWordsMap(author.getL(), DocInfo.PROPERTY.AUTHORS_NAMES, entryWordsMap);
        }
        addToWordsMap_asIs(Integer.toString(entry.getYear()), DocInfo.PROPERTY.YEAR, entryWordsMap);
        addToWordsMap(entry.getVenue(), DocInfo.PROPERTY.VENUE, entryWordsMap);
        addToWordsMap(entry.getJournalName(), DocInfo.PROPERTY.JOURNAL_NAME, entryWordsMap);
        for (String source : entry.getSources()) {
            addToWordsMap(source, DocInfo.PROPERTY.SOURCES, entryWordsMap);
        }
        return entryWordsMap;
    }

    /* Takes a string, applies stemming/stopwords, and adds it to the map of term frequencies.
    This method should be used for strings that are NOT dash delimited. */
    private void addToWordsMap(String field, DocInfo.PROPERTY prop,
                               Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWords) {
        String delimiter = getDelimiter(prop);
        StringTokenizer tokenizer = new StringTokenizer(field, delimiter);
        String currentToken;
        while (tokenizer.hasMoreTokens()) {
            currentToken = tokenizer.nextToken();
            currentToken = ProcessTerm.process(currentToken, useStopwords, useStemmer);
            if (currentToken != null) {
                addToWordsMap_asIs(currentToken, prop, entryWords);
            }
        }
    }

    /* Takes a string, performs split based on dashes, applies stemmimg/stopwords, and adds the tokens to the map
    of term frequencies. This method should only be used for strings that are dash delimited. */
    private void addToWordsMap_afterDashSplit(String field, DocInfo.PROPERTY prop,
                                              Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWords) {
        String delimiter = getDelimiter(prop);
        StringTokenizer tokenizer = new StringTokenizer(field, delimiter);
        StringTokenizer dashTokenizer;
        String currentToken;
        String dashToken;

        while (tokenizer.hasMoreTokens()) {
            currentToken = tokenizer.nextToken();
            dashTokenizer = new StringTokenizer(currentToken, "-―−—–‑‐");
            if (dashTokenizer.countTokens() > 1) {
                dashToken = dashTokenizer.nextToken();
                if (!ProcessTerm.isNumber(dashToken)) {
                    dashToken = ProcessTerm.process(dashToken, useStopwords, useStemmer);
                    if (dashToken != null) {
                        addToWordsMap_asIs(dashToken, prop, entryWords);
                    }
                    while (dashTokenizer.hasMoreTokens()) {
                        dashToken = dashTokenizer.nextToken();
                        dashToken = ProcessTerm.process(dashToken, useStopwords, useStemmer);
                        if (dashToken != null) {
                            addToWordsMap_asIs(dashToken, prop, entryWords);
                        }
                    }
                }
                else {
                    currentToken = ProcessTerm.removeDashes(currentToken);
                    currentToken = ProcessTerm.process(currentToken, useStopwords, useStemmer);
                    if (currentToken != null) {
                        addToWordsMap_asIs(currentToken, prop, entryWords);
                    }
                }
            }
            else if (dashTokenizer.countTokens() == 1) {
                dashToken = dashTokenizer.nextToken();
                dashToken = ProcessTerm.process(dashToken, useStopwords, useStemmer);
                if (dashToken != null) {
                    addToWordsMap_asIs(dashToken, prop, entryWords);
                }
            }
        }
    }

    /* Takes a string and adds it to the map of term frequencies. Does not apply dash removal, stemming, stopwords */
    private void addToWordsMap_asIs(String currentToken, DocInfo.PROPERTY prop,
                                    Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWords) {
        List<Pair<DocInfo.PROPERTY, Integer>> tokenValues = entryWords.get(currentToken);
        Pair<DocInfo.PROPERTY, Integer> lastPair;
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

    /* Returns the split pattern that will be used for splitting a String */
    private static String getDelimiter(DocInfo.PROPERTY prop) {
        switch (prop) {
            case TITLE: case ABSTRACT:
                return "\u0020.:,[]()'/\"’+?<>*“”\u00A0=×!∗´‘{}∼~^`′›‹_\u0091\u0092@″•·・‡†‟„&#¶，،$|\\¿;%»«§¡˚©™®¸‚…：＂±／〔〕【】《》（）＜＞\n\t\r\f\u2003\u202F\u2009\u2002\u2005\u200A\u2006\u200B\u2008";
            case AUTHORS_NAMES:
                return "\u0020-'（）．･;,，‘’“”«».*()&#·\n\r";
            case VENUE:
                return "\u0020-™®’?\\/'&!|*+\";[]=():,.\n\r";
            case JOURNAL_NAME:
                return "\u0020-'&#[](),.:/\"\n\r";
            case ENTITIES:
                return "\u0020’*^:,;&_!#\"<>[]'./()\n\r";
            default:
                return "\u0020\n\r";
        }
    }
}
