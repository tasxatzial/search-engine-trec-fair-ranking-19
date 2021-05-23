package gr.csd.uoc.hy463.themis.indexer.Exceptions;

/**
 * Thrown when something is requested from the index but the index has not been loaded
 */
public class IndexNotLoadedException extends Exception {
    public IndexNotLoadedException() {
        super("Index not loaded");
    }
}
