package gr.csd.uoc.hy463.themis.linkAnalysis;

/**
 * Class that represents a graph node
 */
public class PagerankNode {
    private double _score = 0;
    private int _outNodes = 0;
    private PagerankNode[] _inNodes;

    /**
     * Initializes the In Nodes to an array that has capacity num
     *
     * @param num
     */
    public void initializeInNodes(int num) {
        _inNodes = new PagerankNode[num];
    }

    /**
     * Returns the current score
     *
     * @return
     */
    public double getScore() {
        return _score;
    }

    /**
     * Sets the current score to the specified score
     *
     * @param score
     */
    public void setScore(double score) {
        _score = score;
    }

    /**
     * Returns the number of Out Nodes
     */
    public int getOutNodes() {
        return _outNodes;
    }

    /**
     * Sets the number of Out Nodes to the specified num
     *
     * @param num
     */
    public void setOutNodes(int num) {
        _outNodes = num;
    }

    /**
     * Returns the array of In Nodes
     *
     * @return
     */
    public PagerankNode[] getInNodes() {
        return _inNodes;
    }
}
