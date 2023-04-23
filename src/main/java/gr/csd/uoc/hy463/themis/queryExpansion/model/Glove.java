package gr.csd.uoc.hy463.themis.queryExpansion.model;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.config.Exceptions.ConfigLoadException;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.ExpansionDictionaryInitException;
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
    private final WordVectors _model;
    private final int _nearest;
    private final boolean _useStopwords;

    /**
     * Constructor.
     * Reads configuration options from themis.config file.
     *
     * @param useStopwords
     * @throws ExpansionDictionaryInitException
     * @throws ConfigLoadException
     */
    public Glove(boolean useStopwords)
            throws ExpansionDictionaryInitException, ConfigLoadException {
        Themis.print("-> Initializing Glove...");
        Config __CONFIG__;
        try {
            __CONFIG__ = new Config();
        }
        catch (IOException e) {
            throw new ConfigLoadException();
        }
        File gloveModel = new File(__CONFIG__.getGloveModelPath());
        if (!gloveModel.exists()) {
            throw new ExpansionDictionaryInitException();
        }
        _model = WordVectorSerializer.readWord2VecModel(gloveModel);

        /* default is to get the nearest 1 terms for each query term */
        _nearest = 1;

        _useStopwords = useStopwords;
        Themis.print("Done\n");
    }

    /**
     * Expands the specified list of terms. Each term is expanded by its 1 nearest term.
     * New terms get a weight of 0.5.
     *
     * @param query
     * @return
     */
    @Override
    public List<List<QueryTerm>> expandQuery(List<String> query)
            throws IOException {
        double weight = 0.5;
        List<List<QueryTerm>> expandedQuery = new ArrayList<>();
        for (String term : query) {
            List<QueryTerm> expandedTerm = new ArrayList<>();
            expandedTerm.add(new QueryTerm(term, 1.0)); //original term
            if (_useStopwords && StopWords.Singleton().isStopWord(term.toLowerCase())) {
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
