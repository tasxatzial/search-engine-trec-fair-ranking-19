package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the essential props for the Vector space retrieval model. These are:
 * 1) Array of Max TFs
 * 2) Array of Document weights
 */
public class VSMprops {
    private final int[] _maxTfs;
    private final double[] _VSMweights;

    public VSMprops(int[] maxTfs, double[] VSMweights) {
        _maxTfs = maxTfs;
        _VSMweights = VSMweights;
    }

    public int[] get_MaxTfs() {
        return _maxTfs;
    }

    public double[] get_VSMweights() {
        return _VSMweights;
    }
}
