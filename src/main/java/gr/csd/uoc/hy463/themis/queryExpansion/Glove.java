package gr.csd.uoc.hy463.themis.queryExpansion;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
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
        Config __CONFIG__ = new Config();  // reads info from themis.config file
        File gloveModel = new File(__CONFIG__.getGloveModelFileName());
        Themis.print(">>> Loading glove model data...");
        _model = WordVectorSerializer.readWord2VecModel(gloveModel);
        Themis.print("DONE\n");

        //default is to get the nearest 2 terms for each term
        _nearest = 2;
    }

    /**
     * Expands the specified list of terms. For each term it appends the nearest 2 terms with weight 0.5.
     * The returned list contains only the new terms.
     * @param query
     * @return
     */
    public List<QueryTerm> expandQuery(List<String> query) {
        List<QueryTerm> expandedQuery = new ArrayList<>();

        for (String term : query) {
            Collection<String> nearestTerms = _model.wordsNearest(term, _nearest);
            Object[] nearestArray = nearestTerms.toArray();
            for (Object o : nearestArray) {
                expandedQuery.add(new QueryTerm(o.toString(), 0.5));
            }
        }
        return expandedQuery;
    }
}
