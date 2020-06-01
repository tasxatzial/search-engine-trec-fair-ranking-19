package gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar;

import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.util.*;

/**
 * Creates a map of term frequencies for a textual entry.
 * Each value of the map is the list of field frequencies of the corresponding term:
 * <<field1, tf1> <field1, tf2> ...>
 */
public class S2TextualEntryTermFrequencies {
    private boolean useStemmer;
    private boolean useStopwords;

    public S2TextualEntryTermFrequencies(Config config) {
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
        addToWordsMap_asIs(Integer.toString(entry.getYear()), DocInfo.PROPERTY.YEAR, entryWordsMap);
        addToWordsMap(entry.getVenue(), DocInfo.PROPERTY.VENUE, entryWordsMap);
        addToWordsMap(entry.getJournalName(), DocInfo.PROPERTY.JOURNAL_NAME, entryWordsMap);
        for (String source : entry.getSources()) {
            addToWordsMap(source, DocInfo.PROPERTY.SOURCES, entryWordsMap);
        }
        return entryWordsMap;
    }

    /* Takes a string, applies stemming/stopwords, and adds the result to the map of term frequencies */
    private void addToWordsMap(String field, DocInfo.PROPERTY prop,
                               Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWords) {
        String delimiter = getDelimiter(prop);
        StringTokenizer tokenizer = new StringTokenizer(field, delimiter);
        String currentToken;
        while (tokenizer.hasMoreTokens()) {
            currentToken = tokenizer.nextToken();

            //apply stopwords, stemming, convert to lowercase
            currentToken = ProcessText.applyStopwordsStemming(currentToken, useStopwords, useStemmer);
            if (currentToken != null) {
                addToWordsMap_asIs(currentToken, prop, entryWords);
            }
        }
    }

    /* Takes a string and adds it to the map of term frequencies. Does not apply stemming or stopwords */
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
                return "\u0020.:,[]-―−—–‑‐‒─⎯⁻\"?“”◆!{}()+<>›'‹_″‟„¶|\\¿;»«§¡¸±：×^＂*@・˚•·∼°⁺◦，⋅ªº␣�≥⋯≡／∕∣ⓡ£€ⅰ⁃╉＜＞（）【】《》〈〉〔〕≪≫「」╅│｜－？＋✔✓←↑→↓↔⇑⇒⇔⇤①②③④⑤⑥⑦⑧⑨⑩…‡†‗™©®~$#%&/=∗。、‚׳‘’‛´`′⁄▾▵┙␥〈〉⋆⊥⊤♦♀♂●∞∙♣◆☆▪□■▶►▲▼▸‖～［］\n\t\r\f\u00AD\uF020\u008D\u00A0\uF020\u2003\u202F\u2009\u2002\u2005\u200A\u2006\u200B\u2008\u2004\u2000\u008E\u3000\uF0E0\uF07D\uF07A\uF076\uF073\uF072\uF070\uF06E\uF06D\uF06C\uF064\uF062\uF061\uF05B\uF034\uF025\uF02D\uE0D5\uE004\u0084\u0094\u0093\u0097\u0085\u0091\u0092\uE011\uFFFB\uF8E7\uF0D8\uF0A7\uF074\uF0E1\uF0F1\uF0E2\uF0B5\uF0B3\uF0B4\uF079\uF07E\uF0A0\uF0A8\uF0AB\uF0AD\uF0B5\uF078\uF077\uF071\uF06F\uF06B\uF067\uF0A2\uF065\uF063\uF053\uF047\uF044\uF03C\uF02A\uF000\uF0A3\uF0E8\uF0EB\uF0F7\uF0D6\uF0B7\uF0B9\uF0BA\uF0BD\uF0BE\u200E\uE003\uE009\u2028\u0082\u2007\uFEFF\u0096\u0099⃝◇◮║➔➢➤✉☁✣✭✩✴✳✿➝✦✸❛❖✞♯◌◊⌜♮❚♠❯☞┚⌈➀➁➂➃➄△≈∥\uF075‵※『と⁎₀₁₂₃₄₅₆₇₈₉⁰¹²³⁴⁵⁶⁷⁸⁹\u2FFF⩾⦁⋄✰⪡⪢\uF080\uF081\uF082\uF083\uF084\uF085\uF086\uF087\uF088\uF089";
            //
            case AUTHORS_NAMES:
                return "\u0020（），･·;,-―−—–‑‐&@“”„、ᆞ⋅‧•†‡‹↑'׳′´`’‘ʿ．.©\"‟«»*∗()#\n\r\u00AD\u200E\u2009";
            case VENUE:
                return "\u0020-–™®?*=#&\\/!|\";[]():'´+,’.@\n\r\u00AD";
            case JOURNAL_NAME:
                return "\u0020-[]()&“”,?.:+/*'’ʿ´;@!\"\n\r\u00AD";
            case ENTITIES:
                return "\u0020-–:,;_!^\"<>[]()./’'*&@\n\r";
            default:
                return "\u0020\n\r";
        }
    }
}
