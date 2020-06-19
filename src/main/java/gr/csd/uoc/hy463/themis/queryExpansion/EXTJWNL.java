package gr.csd.uoc.hy463.themis.queryExpansion;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.dictionary.Dictionary;

import java.util.ArrayList;
import java.util.List;

public class EXTJWNL extends QueryExpansion {
    private MaxentTagger maxentTagger;
    private Dictionary dictionary;

    public EXTJWNL() throws QueryExpansionException {
        Themis.print(">>> Initializing extJWNL...");
        try {
            dictionary = Dictionary.getDefaultResourceInstance();
        } catch (JWNLException e) {
            throw new QueryExpansionException();
        }
        if (dictionary != null) {
            maxentTagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger");
        }
        Themis.print("DONE\n");
    }

    /**
     * Expands the specified list of terms. For each term it appends the nearest 2 terms with weight 0.5.
     * The returned list contains only the new terms.
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

        for (String s : eachTag) {
            List<QueryTerm> expandedTerm = new ArrayList<>();
            String term = s.split("_")[0];
            String tag = s.split("_")[1];
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
     * Get the wordnet  Part-of-Speech (POS) representation from the stanford one
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
