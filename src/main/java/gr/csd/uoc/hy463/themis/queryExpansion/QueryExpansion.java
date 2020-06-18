package gr.csd.uoc.hy463.themis.queryExpansion;

import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;

import java.util.List;

/**
 * Abstract class for all query expansion models
 */
public abstract class QueryExpansion {
    public enum DICTIONARY {
        NONE, GLOVE
    }

    /**
     * Expands a given query using a query expansion dictionary
     * @param query
     * @return
     */
    public abstract List<QueryTerm> expandQuery(List<String> query);
}
