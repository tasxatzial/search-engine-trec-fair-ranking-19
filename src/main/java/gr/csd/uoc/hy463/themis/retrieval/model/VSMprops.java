package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the essential props for the Vector space retrieval model. These are:
 * - Array of Max TF (max term frequency in a document)
 * - Array of Document weights
 *
 * The size of each array should be equal to the number of documents in the collection.
 */
public class VSMprops {
    private final int[] _maxTFs;
    private final double[] _VSMweights;

    public VSMprops(int[] maxTFs, double[] VSMweights) {
        _maxTFs = maxTFs;
        _VSMweights = VSMweights;
    }

    public int[] getMaxTFs() {
        return _maxTFs;
    }

    public double[] getVSMweights() {
        return _VSMweights;
    }
}
