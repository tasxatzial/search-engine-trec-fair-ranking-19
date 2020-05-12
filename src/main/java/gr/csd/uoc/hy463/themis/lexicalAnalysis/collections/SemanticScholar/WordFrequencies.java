package gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar;

import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.Stemmer;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Creates a map of term frequencies for a textual entry.
 * Each value of the map is the list of field frequencies of the corresponding term:
 * <<field1, tf1> <field1, tf2> ...>
 */
public class WordFrequencies {
    private boolean useStemmer;
    private boolean useStopwords;
    private Pattern numberCheck;
    private Pattern leadTrailDash;
    private Pattern anyDash;

    public WordFrequencies(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config is null");
        }
        useStemmer = config.getUseStemmer();
        useStopwords = config.getUseStopwords();
        numberCheck = Pattern.compile("[0-9]+");
        leadTrailDash = Pattern.compile("^[-―−—–‑‐]+|[-―−—–‑‐]+$");
        anyDash = Pattern.compile("[―−—–‑‐]+");
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
        addToMapAsIs(Integer.toString(entry.getYear()), DocInfo.PROPERTY.YEAR, entryWordsMap);
        addToWordsMap(entry.getVenue(), DocInfo.PROPERTY.VENUE, entryWordsMap);
        addToWordsMap(entry.getJournalName(), DocInfo.PROPERTY.JOURNAL_NAME, entryWordsMap);
        for (String source : entry.getSources()) {
            addToWordsMap(source, DocInfo.PROPERTY.SOURCES, entryWordsMap);
        }
        return entryWordsMap;
    }

    /* Takes a string, performs stemming, and adds it to the map of term frequencies.
    This method should be used for strings that are NOT dash delimited. */
    private void addToWordsMap(String field, DocInfo.PROPERTY prop,
                               Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWords) {
        String delimiter = getDelimiter(prop);
        StringTokenizer tokenizer = new StringTokenizer(field, delimiter);
        String currentToken;
        while (tokenizer.hasMoreTokens()) {
            currentToken = tokenizer.nextToken();
            addToMap_afterStem(currentToken, prop, entryWords);
        }
    }

    /* Takes a string, performs split based on dashes, and adds the tokens to the map of term frequencies.
    This method should only be used for strings that are dash delimited. */
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
                if (!numberCheck.matcher(dashToken).matches()) {
                    addToMap_afterStem(dashToken, prop, entryWords);
                    while (dashTokenizer.hasMoreTokens()) {
                        dashToken = dashTokenizer.nextToken();
                        addToMap_afterStem(dashToken, prop, entryWords);
                    }
                }
                else {
                    currentToken = leadTrailDash.matcher(currentToken).replaceAll("");
                    currentToken = anyDash.matcher(currentToken).replaceAll("-");
                    addToMap_afterStem(currentToken, prop, entryWords);
                }
            }
            else if (dashTokenizer.countTokens() == 1){
                addToMap_afterStem(dashTokenizer.nextToken(), prop, entryWords);
            }
        }
    }

    /* Takes a token from an already split string, performs stemming, and adds it to the map of term frequencies */
    private void addToMap_afterStem(String currentToken, DocInfo.PROPERTY prop,
                                    Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWords) {
        String stemmedToken;
        if (useStopwords && StopWords.isStopWord(currentToken)) {
            return;
        }
        if (useStemmer && currentToken.length() > 2) {
            stemmedToken = Stemmer.Stem(currentToken);
            currentToken = (stemmedToken.length() > 1) ? stemmedToken : currentToken.toLowerCase();
        }
        else {
            currentToken = currentToken.toLowerCase();
        }
        addToMapAsIs(currentToken, prop, entryWords);
    }

    /* Takes a token from an already split string and adds it to the map of term frequencies */
    private void addToMapAsIs(String currentToken, DocInfo.PROPERTY prop,
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
