package gr.csd.uoc.hy463.themis.queryExpansion.model;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansionException;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.dictionary.Dictionary;

import java.util.ArrayList;
import java.util.List;

/**
 * Expands a query using the extJWNL dictionary
 */
public class EXTJWNL extends QueryExpansion {
    private MaxentTagger maxentTagger;
    private Dictionary dictionary;
    private boolean _useStopwords;

    public EXTJWNL(boolean useStopwords) throws QueryExpansionException {
        Themis.print(">>> Initializing extJWNL...");
        try {
            dictionary = Dictionary.getDefaultResourceInstance();
        } catch (JWNLException e) {
            throw new QueryExpansionException();
        }
        if (dictionary != null) {
            maxentTagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger");
        }
        _useStopwords = useStopwords;
        Themis.print("DONE\n");
    }

    /**
     * Expands the specified list of terms. Each term is expanded by the full list of its related terms (weight = 0.5)
     * @param query
     * @return
     */
    @Override
    public List<List<QueryTerm>> expandQuery(List<String> query) throws QueryExpansionException {
        double weight = 0.5;
        List<List<QueryTerm>> expandedQuery = new ArrayList<>();

        //re-construct the query
        StringBuilder sb = new StringBuilder();
        for (String value : query) {
            sb.append(value).append(" ");
        }
        String queryString = sb.toString();

        String taggedQuery = maxentTagger.tagString(queryString);
        String[] eachTag = taggedQuery.split("\\s+");

        for (int i = 0; i < eachTag.length; i++) {
            List<QueryTerm> expandedTerm = new ArrayList<>();
            String term = eachTag[i].split("_")[0];
            String tag = eachTag[i].split("_")[1];
            expandedTerm.add(new QueryTerm(term, 1.0));
            if (_useStopwords && StopWords.isStopWord(eachTag[i].toLowerCase())) {
                expandedQuery.add(expandedTerm);
                continue;
            }
            POS pos = getPos(tag);

            // Ignore anything that is not a noun, verb, adjective, adverb
            if (pos != null) {
                IndexWord iWord;
                try {
                    iWord = dictionary.getIndexWord(pos, term);
                } catch (JWNLException e) {
                    throw new QueryExpansionException();
                }
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
        return expandedQuery;
    }

    /**
     * Get the wordnet Part-of-Speech (POS) representation from the stanford one
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
