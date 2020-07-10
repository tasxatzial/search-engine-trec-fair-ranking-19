package gr.csd.uoc.hy463.themis.queryExpansion.model;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.QueryExpansionException;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Expands a query using the glove dictionary
 */
public class Glove extends QueryExpansion {
    private WordVectors _model;
    private int _nearest;
    private boolean _useStopwords;

    public Glove(boolean useStopwords) throws IOException, QueryExpansionException {
        Themis.print(">>> Initializing Glove...");
        Config __CONFIG__ = new Config();
        File gloveModel = new File(__CONFIG__.getGloveModelFileName());
        if (!gloveModel.exists()) {
            throw new QueryExpansionException();
        }
        _model = WordVectorSerializer.readWord2VecModel(gloveModel);

        //default is to get the nearest 1 terms for each term
        _nearest = 1;

        _useStopwords = useStopwords;
        Themis.print("DONE\n");
    }

    /**
     * Expands the specified list of terms. Each term is expanded by its 1 nearest term (weight = 0.5)
     * @param query
     * @return
     */
    @Override
    public List<List<QueryTerm>> expandQuery(List<String> query) {
        double weight = 0.5;
        List<List<QueryTerm>> expandedQuery = new ArrayList<>();

        for (String term : query) {
            List<QueryTerm> expandedTerm = new ArrayList<>();
            expandedTerm.add(new QueryTerm(term, 1.0));
            if (_useStopwords && StopWords.isStopWord(term.toLowerCase())) {
                expandedQuery.add(expandedTerm);
                continue;
            }
            Collection<String> nearestTerms = _model.wordsNearest(term, _nearest);
            Object[] nearestArray = nearestTerms.toArray();
            for (Object o : nearestArray) {
                expandedTerm.add(new QueryTerm(o.toString(), weight));
            }
            expandedQuery.add(expandedTerm);
        }
        return expandedQuery;
    }
}
