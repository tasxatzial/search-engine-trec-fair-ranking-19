package gr.csd.uoc.hy463.themis.queryExpansion.model;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
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

    public Glove() throws IOException {
        Themis.print(">>> Initializing Glove...");
        Config __CONFIG__ = new Config();  // reads info from themis.config file
        File gloveModel = new File(__CONFIG__.getGloveModelFileName());
        _model = WordVectorSerializer.readWord2VecModel(gloveModel);

        //default is to get the nearest 2 terms for each term
        _nearest = 2;

        Themis.print("DONE\n");
    }

    /**
     * Expands the specified list of terms. For each term it appends the nearest 2 terms with weight 0.5.
     * The returned list contains only the new terms.
     * @param query
     * @return
     */
    @Override
    public List<List<QueryTerm>> expandQuery(List<String> query) {
        double weight = 0.5;
        List<List<QueryTerm>> expandedQuery = new ArrayList<>();

        for (String term : query) {
            List<QueryTerm> expandedTerm = new ArrayList<>();
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
