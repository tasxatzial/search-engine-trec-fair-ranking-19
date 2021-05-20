package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the essential props for the Vector space retrieval model. Each object has 3 arrays, each array
 * has the values for a specific property of the relevant documents of a term. The properties are:
 * 1) Max TF in a document
 * 2) Citations pagerank score
 * 3) Document weight
 */
public class VSMprops {
    private int[] maxTfs;
    private double[] citationsPagerank;
    private double[] VSMweights;

    public VSMprops(int[] maxTfs, double[] citationsPagerank, double[] VSMweights) {
        this.maxTfs = maxTfs;
        this.citationsPagerank = citationsPagerank;
        this.VSMweights = VSMweights;
    }

    public int[] getMaxTfs() {
        return maxTfs;
    }

    public double[] getCitationsPagerank() {
        return citationsPagerank;
    }

    public double[] getVSMweights() {
        return VSMweights;
    }
}
