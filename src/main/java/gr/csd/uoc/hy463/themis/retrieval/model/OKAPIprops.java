package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the essential props for the Okapi retrieval model. These are:
 * - Array of the token count for each document
 *
 * The size of each array should be equal to the number of documents in the collection.
 */
public class OKAPIprops {
    private final int[] _tokenCount;

    public OKAPIprops(int[] tokenCount) {
        _tokenCount = tokenCount;
    }

    public int[] getTokenCount() {
        return _tokenCount;
    }
}
