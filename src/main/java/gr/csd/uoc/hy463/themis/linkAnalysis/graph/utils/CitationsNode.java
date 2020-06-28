package gr.csd.uoc.hy463.themis.linkAnalysis.graph.utils;

/**
 * Generic class that holds two arrays that have indexes of In nodes and Out nodes
 */
public class CitationsNode {

    /* indexes of the In Nodes */
    private int[] inNodes;

    /* indexes of the Out Nodes */
    private int[] outNodes;

    /**
     * Initializes the In Nodes to an array that has capacity num
     * @param num
     */
    public void initializeInNodes(int num) {
        inNodes = new int[num];
    }

    /**
     * Initializes the Out Nodes to an array that has capacity num
     * @param num
     */
    public void initializeOutNodes(int num) {
        outNodes = new int[num];
    }

    /**
     * Returns the array of the indexes of the In Nodes
     * @return
     */
    public int[] getInNodes() {
        return inNodes;
    }

    /**
     * Returns the array of the indexes of the Out Nodes
     * @return
     */
    public int[] getOutNodes() {
        return outNodes;
    }
}
