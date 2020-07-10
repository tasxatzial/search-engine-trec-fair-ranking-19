package gr.csd.uoc.hy463.themis.queryExpansion;

import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.QueryExpansionException;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;

import java.util.List;

/**
 * Abstract class for all query expansion models
 */
public abstract class QueryExpansion {
    public enum DICTIONARY {
        NONE, GLOVE, EXTJWNL
    }

    /**
     * Expands a given query using a query expansion dictionary. The first term in each List<QueryTerm>
     * should be the non-expanded term.
     * @param query
     * @return
     */
    public abstract List<List<QueryTerm>> expandQuery(List<String> query) throws QueryExpansionException;
}
