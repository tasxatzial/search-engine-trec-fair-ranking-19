package gr.csd.uoc.hy463.themis.linkAnalysis.graph;

/**
 * Class that holds two arrays that have the int indices of In nodes and Out nodes. Used for holding
 * the In and Out citations of a document.
 */
public class CitationsNode {

    /* indexes of the In Nodes */
    private int[] _inNodes;

    /* indexes of the Out Nodes */
    private int[] _outNodes;

    /**
     * Initializes the In Nodes to an array of length num
     *
     * @param num
     */
    public void initializeInNodes(int num) {
        _inNodes = new int[num];
    }

    /**
     * Initializes the Out Nodes to an array of length num
     *
     * @param num
     */
    public void initializeOutNodes(int num) {
        _outNodes = new int[num];
    }

    /**
     * Returns the array of the indexes of the In Nodes
     *
     * @return
     */
    public int[] get_inNodes() {
        return _inNodes;
    }

    /**
     * Returns the array of the indexes of the Out Nodes
     *
     * @return
     */
    public int[] get_outNodes() {
        return _outNodes;
    }
}
