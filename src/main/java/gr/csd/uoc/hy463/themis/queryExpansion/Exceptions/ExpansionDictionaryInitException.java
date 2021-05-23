package gr.csd.uoc.hy463.themis.queryExpansion.Exceptions;

/**
 * Thrown when the query expansion dictionaries failed to initialize
 */
public class ExpansionDictionaryInitException extends Exception {
    public ExpansionDictionaryInitException() {
        super("Unable to initialize query expansion dictionary");
    }
}
