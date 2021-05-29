package gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar;

import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFrequency;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * Creates the map of term frequencies from a S2TextualEntry
 */
public class S2TextualEntryTermFrequencies {
    private final boolean _useStemmer;
    private final boolean _useStopwords;

    public S2TextualEntryTermFrequencies(boolean useStemmer, boolean useStopwords) {
        _useStemmer = useStemmer;
        _useStopwords = useStopwords;
    }

    /**
     * Creates the map of term frequencies from a S2TextualEntry
     *
     * @param entry
     * @return
     */
    public Map<String, Integer> createWordsMap(S2TextualEntry entry) {
        Map<String, Integer> termTF = new HashMap<>();

        addToWordsMap(entry.getTitle(), DocInfo.PROPERTY.TITLE , termTF);
        addToWordsMap(entry.getPaperAbstract(), DocInfo.PROPERTY.ABSTRACT, termTF);
        for (String entity : entry.getEntities()) {
            addToWordsMap(entity, DocInfo.PROPERTY.ENTITIES, termTF);
        }
        for (String fieldsOfStudy : entry.getFieldsOfStudy()) {
            addToWordsMap(fieldsOfStudy, DocInfo.PROPERTY.FIELDS_OF_STUDY, termTF);
        }
        for (Pair<String, List<String>> author : entry.getAuthors()) {
            addToWordsMap(author.getL(), DocInfo.PROPERTY.AUTHORS_NAMES, termTF);
        }
        addToWordsMap(Integer.toString(entry.getYear()), DocInfo.PROPERTY.YEAR, termTF);
        addToWordsMap(entry.getVenue(), DocInfo.PROPERTY.VENUE, termTF);
        addToWordsMap(entry.getJournalName(), DocInfo.PROPERTY.JOURNAL_NAME, termTF);
        for (String source : entry.getSources()) {
            addToWordsMap(source, DocInfo.PROPERTY.SOURCES, termTF);
        }
        return termTF;
    }

    /* Splits a string into tokens, applies stemming/stopwords, and adds the result to the map of term frequencies */
    private void addToWordsMap(String field, DocInfo.PROPERTY prop, Map<String, Integer> termTF) {
        String delimiter = getDelimiter(prop);
        StringTokenizer tokenizer = new StringTokenizer(field, delimiter);
        String currentToken;
        while (tokenizer.hasMoreTokens()) {
            currentToken = tokenizer.nextToken().toLowerCase();
            if (_useStopwords && StopWords.isStopWord(currentToken)) {
                continue;
            }
            if (_useStemmer) {
                currentToken = ProcessText.applyStemming(currentToken);
            }
            Integer tf = termTF.get(currentToken);
            if (tf != null) {
                termTF.put(currentToken, tf + 1);
            } else {
                termTF.put(currentToken, 1);
            }
        }
    }

    /* Returns the split pattern for the specified DocInfo property */
    private static String getDelimiter(DocInfo.PROPERTY prop) {
        switch (prop) {
            case TITLE: case ABSTRACT:
                //
                return "\u0020.:,[]-―−—–‑‐‒─⎯⁻\"?“”◆!{}()+<>›'‹_″‟„¶|\\¿;»«§¡¸±：×^＂*@・˚•·∼°⁺◦，⋅ªº␣�≥⋯≡／∕" +
                        "∣ⓡ£€ⅰ⁃╉＜＞（）【】《》〈〉〔〕≪≫「」╅│｜－？＋✔✓←↑→↓↔⇑⇒⇔⇤①②③④⑤⑥⑦⑧⑨⑩…‡†‗™©®~$#%&/=∗。、‚׳‘" +
                        "’‛´`′⁄▾▵┙␥〈〉⋆⊥⊤♦♀♂●∞∙♣◆☆▪□■▶►▲▼▸‖～［］\n\t\r\f\u00AD\uF020\u008D\u00A0\uF020\u2003\u202F" +
                        "\u2009\u2002\u2005\u200A\u2006\u200B\u2008\u2004\u2000\u008E\u3000\uF0E0\uF07D\uF07A\uF076" +
                        "\uF073\uF072\uF070\uF06E\uF06D\uF06C\uF064\uF062\uF061\uF05B\uF034\uF025\uF02D\uE0D5\uE004" +
                        "\u0084\u0094\u0093\u0097\u0085\u0091\u0092\uE011\uFFFB\uF8E7\uF0D8\uF0A7\uF074\uF0E1\uF0F1" +
                        "\uF0E2\uF0B5\uF0B3\uF0B4\uF079\uF07E\uF0A0\uF0A8\uF0AB\uF0AD\uF0B5\uF078\uF077\uF071\uF06F" +
                        "\uF06B\uF067\uF0A2\uF065\uF063\uF053\uF047\uF044\uF03C\uF02A\uF000\uF0A3\uF0E8\uF0EB\uF0F7" +
                        "\uF0D6\uF0B7\uF0B9\uF0BA\uF0BD\uF0BE\u200E\uE003\uE009\u2028\u0082\u2007\uFEFF\u0096\u0099⃝◇" +
                        "◮║➔➢➤✉☁✣✭✩✴✳✿➝✦✸❛❖✞♯◌◊⌜♮❚♠❯☞┚⌈➀➁➂➃➄△≈∥\uF075‵※『と⁎₀₁₂₃₄₅₆₇₈₉⁰¹²³⁴⁵⁶⁷⁸⁹\u2FFF⩾⦁⋄✰⪡⪢" +
                        "\uF080\uF081\uF082\uF083\uF084\uF085\uF086\uF087\uF088\uF089";
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
