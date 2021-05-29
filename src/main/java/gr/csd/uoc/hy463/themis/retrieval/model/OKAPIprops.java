package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the essential props for the Okapi retrieval model. Each object has 2 arrays, each array
 * has the values for a specific property of the relevant documents of a term. The properties are:
 * 1) Citations pagerank score
 * 2) Token count
 */
public class OKAPIprops {
    private final int[] _tokenCount;
    private final double[] _citationsPagerank;

    public OKAPIprops(int[] tokenCount, double[] citationsPagerank) {
        _tokenCount = tokenCount;
        _citationsPagerank = citationsPagerank;
    }

    public double[] get_citationsPagerank() {
        return _citationsPagerank;
    }

    public int[] get_tokenCount() {
        return _tokenCount;
    }
}
