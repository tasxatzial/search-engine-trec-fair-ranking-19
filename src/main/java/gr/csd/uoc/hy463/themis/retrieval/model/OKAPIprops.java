package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the essential props for the Okapi retrieval model. These are:
 * 1) Array of Token counts
 */
public class OKAPIprops {
    private final int[] _tokenCount;

    public OKAPIprops(int[] tokenCount) {
        _tokenCount = tokenCount;
    }

    public int[] get_tokenCount() {
        return _tokenCount;
    }
}
