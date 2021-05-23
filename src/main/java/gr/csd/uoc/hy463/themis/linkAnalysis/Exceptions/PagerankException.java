package gr.csd.uoc.hy463.themis.linkAnalysis.Exceptions;

/**
 * Thrown when pagerank computation failed
 */
public class PagerankException extends Exception {
    public PagerankException() {
        super("Pagerank failed");
    }
}
