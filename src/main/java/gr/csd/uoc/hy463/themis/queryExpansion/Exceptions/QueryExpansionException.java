package gr.csd.uoc.hy463.themis.queryExpansion.Exceptions;

/**
 * Generic exception thrown by query expansion libraries
 */
public class QueryExpansionException extends Exception {
    public QueryExpansionException() {
        super("query expansion failed");
    }
}
