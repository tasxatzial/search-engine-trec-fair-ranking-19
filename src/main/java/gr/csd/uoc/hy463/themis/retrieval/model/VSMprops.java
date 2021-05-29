package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the essential props for the Vector space retrieval model. Each object has 3 arrays, each array
 * has the values for a specific property of the relevant documents of a term. The properties are:
 * 1) Max TF in a document
 * 2) Citations pagerank score
 * 3) Document weight
 */
public class VSMprops {
    private final int[] _maxTfs;
    private final double[] _citationsPagerank;
    private final double[] _VSMweights;

    public VSMprops(int[] maxTfs, double[] citationsPagerank, double[] VSMweights) {
        _maxTfs = maxTfs;
        _citationsPagerank = citationsPagerank;
        _VSMweights = VSMweights;
    }

    public int[] get_MaxTfs() {
        return _maxTfs;
    }

    public double[] get_CitationsPagerank() {
        return _citationsPagerank;
    }

    public double[] get_VSMweights() {
        return _VSMweights;
    }
}
