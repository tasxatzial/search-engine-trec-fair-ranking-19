package gr.csd.uoc.hy463.themis.queryExpansion.model;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.StopWords;
import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.QueryExpansionException;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.dictionary.Dictionary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Expands a query using the WordNet dictionary
 */
public class EXTJWNL extends QueryExpansion {
    private static EXTJWNL _instance = null;
    private MaxentTagger _maxentTagger;
    private final Dictionary _dictionary;

    private EXTJWNL()
            throws JWNLException {
        Themis.print("-> Initializing WordNet...");
        _dictionary = Dictionary.getDefaultResourceInstance();
        _maxentTagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger");
        Themis.print("Done\n");
    }

    /**
     * Returns the instance of the EXTJWNL dictionary.
     *
     * @return
     * @throws JWNLException
     */
    public static EXTJWNL Singleton()
            throws JWNLException {
        return _instance == null
                ? (_instance = new EXTJWNL())
                : _instance;
    }

    /**
     * Expands the specified list of terms. Each term is expanded by the full list of its related terms.
     * New terms get a weight of 0.5.
     *
     * @param query
     * @throws QueryExpansionException
     * @return
     */
    @Override
    public List<List<QueryTerm>> expandQuery(List<String> query, boolean useStopwords)
            throws QueryExpansionException {
        double weight = 0.5;
        List<List<QueryTerm>> expandedQuery = new ArrayList<>();

        //re-construct the query
        StringBuilder sb = new StringBuilder();
        for (String value : query) {
            sb.append(value).append(" ");
        }
        String queryString = sb.toString();

        String taggedQuery = _maxentTagger.tagString(queryString);
        String[] eachTag = taggedQuery.split("\\s+");

        try {
            for (int i = 0; i < eachTag.length; i++) {
                List<QueryTerm> expandedTerm = new ArrayList<>();
                String term = eachTag[i].split("_")[0];
                String tag = eachTag[i].split("_")[1];
                expandedTerm.add(new QueryTerm(term, 1.0));
                if (useStopwords && StopWords.Singleton().isStopWord(eachTag[i].toLowerCase())) {
                    expandedQuery.add(expandedTerm);
                    continue;
                }
                POS pos = getPos(tag);

                // Ignore anything that is not a noun, verb, adjective, adverb
                if (pos != null) {
                    IndexWord iWord;
                    iWord = _dictionary.getIndexWord(pos, term);
                    if (iWord != null) {
                        for (Synset synset : iWord.getSenses()) {
                            List<Word> words = synset.getWords();
                            for (Word word : words) {
                                expandedTerm.add(new QueryTerm(word.getLemma(), weight));
                            }
                        }
                    }
                }
                expandedQuery.add(expandedTerm);
            }
        } catch (Exception ex) {
            throw new QueryExpansionException("EXTJWNL");
        }
        return expandedQuery;
    }

    /**
     * Get the wordnet Part-of-Speech (POS) representation from the stanford one
     *
     * @param taggedAs
     * @return
     */
    private static POS getPos(String taggedAs) {
        switch(taggedAs) {
            case "NN" :
            case "NNS" :
            case "NNP" :
            case "NNPS" :
                return POS.NOUN;
            case "VB" :
            case "VBD" :
            case "VBG" :
            case "VBN" :
            case "VBP" :
            case "VBZ" :
                return POS.VERB;
            case "JJ" :
            case "JJR" :
            case "JJS" :
                return POS.ADJECTIVE;
            case "RB" :
            case "RBR" :
            case "RBS" :
                return POS.ADVERB;
            default:
                return null;
        }
    }
}
