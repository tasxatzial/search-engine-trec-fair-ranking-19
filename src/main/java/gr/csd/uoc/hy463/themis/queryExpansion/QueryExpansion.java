package gr.csd.uoc.hy463.themis.queryExpansion;

import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.QueryExpansionException;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;

import java.util.List;

/**
 * Abstract class for all query expansion models.
 */
public abstract class QueryExpansion {
    public enum MODEL {
        NONE, GLOVE, WORDNET
    }

    /**
     * Expands a given query using an expansion model. The first term in each List<QueryTerm>
     * should be the original term.
     *
     * @param query
     * @param useStopwords
     * @throws QueryExpansionException
     * @return
     */
    public abstract List<List<QueryTerm>> expandQuery(List<String> query, boolean useStopwords)
            throws QueryExpansionException;
}
