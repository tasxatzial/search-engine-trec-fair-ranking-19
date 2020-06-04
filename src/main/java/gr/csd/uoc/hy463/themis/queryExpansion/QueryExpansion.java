package gr.csd.uoc.hy463.themis.queryExpansion;

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
    public abstract String expandQuery(String query);
}
