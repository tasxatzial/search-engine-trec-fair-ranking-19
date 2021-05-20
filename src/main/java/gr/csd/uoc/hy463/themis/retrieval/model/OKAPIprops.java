package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the essential props for the Okapi retrieval model. Each object has 2 arrays, each array
 * has the values for a specific property of the relevant documents of a term. The properties are:
 * 1) Citations pagerank score
 * 2) Token count
 */
public class OKAPIprops {
    private int[] tokenCount;
    private double[] citationsPagerank;

    public OKAPIprops(int[] tokenCount, double[] citationsPagerank) {
        this.tokenCount = tokenCount;
        this.citationsPagerank = citationsPagerank;
    }

    public double[] getCitationsPagerank() {
        return citationsPagerank;
    }

    public int[] getTokenCount() {
        return tokenCount;
    }
}
