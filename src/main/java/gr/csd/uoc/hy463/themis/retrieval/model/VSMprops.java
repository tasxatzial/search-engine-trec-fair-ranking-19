package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the essential props for the Vector space retrieval model. These are:
 * 1) Array of Max TFs (max frequency of a term in a document)
 * 2) Array of Document weights
 *
 * The size of each array should be equal to the number of documents in the collection.
 */
public class VSMprops {
    private final int[] _maxTfs;
    private final double[] _VSMweights;

    public VSMprops(int[] maxTfs, double[] VSMweights) {
        _maxTfs = maxTfs;
        _VSMweights = VSMweights;
    }

    public int[] getMaxTfs() {
        return _maxTfs;
    }

    public double[] getVSMweights() {
        return _VSMweights;
    }
}
