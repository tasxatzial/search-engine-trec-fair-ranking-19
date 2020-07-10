package gr.csd.uoc.hy463.themis.ui.Exceptions;

/**
 * Thrown when the search class is used but the index has been unloaded
 */
public class SearchNoIndexException extends Exception {
    public SearchNoIndexException() {
        super("no index loaded");
    }
}
