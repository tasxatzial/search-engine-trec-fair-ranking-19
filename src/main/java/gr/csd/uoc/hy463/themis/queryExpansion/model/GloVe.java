package gr.csd.uoc.hy463.themis.queryExpansion.model;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.StopWords;
import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.QueryExpansionException;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Expands a query using a GloVe pre-trained word vectors file
 */
public class GloVe extends QueryExpansion {
    private static GloVe _instance = null;
    private final WordVectors _model;

    private GloVe(String filePath)
            throws FileNotFoundException {
        Themis.print("-> Initializing GloVe...");
        File gloveModel = new File(filePath);
        if (!gloveModel.exists()) {
            throw new FileNotFoundException("GloVe file not found");
        }
        _model = WordVectorSerializer.readWord2VecModel(gloveModel);

        Themis.print("Done\n");
    }

    public static GloVe Singleton(String filePath)
            throws FileNotFoundException {
        return _instance == null
                ? (_instance = new GloVe(filePath))
                : _instance;
    }

    /**
     * Expands the specified list of terms. Each term is expanded by its 1 nearest term.
     * New terms get a weight of 0.5.
     *
     * @param query
     * @throws QueryExpansionException
     * @return
     */
    @Override
    public List<List<QueryTerm>> expandQuery(List<String> query, boolean useStopwords)
            throws QueryExpansionException {
        double newTermWeight = 0.5;
        double origTermWeight = 1.0;
        int extraTerms = 3;
        List<List<QueryTerm>> expandedQuery = new ArrayList<>();
        try {
            for (String term : query) {
                if (useStopwords && StopWords.isStopWord(term)) {
                    continue;
                }
                List<QueryTerm> expandedTerm = new ArrayList<>();
                expandedTerm.add(new QueryTerm(term, origTermWeight));
                Collection<String> nearestTerms = _model.wordsNearest(term, extraTerms);
                Object[] nearestArray = nearestTerms.toArray();
                for (Object o : nearestArray) {
                    String s = o.toString();
                    if (useStopwords && StopWords.isStopWord(s)) {
                        continue;
                    }
                    expandedTerm.add(new QueryTerm(s, newTermWeight));
                }
                expandedQuery.add(expandedTerm);
            }
        } catch (Exception ex) {
            throw new QueryExpansionException("GloVe");
        }
        return expandedQuery;
    }
}
