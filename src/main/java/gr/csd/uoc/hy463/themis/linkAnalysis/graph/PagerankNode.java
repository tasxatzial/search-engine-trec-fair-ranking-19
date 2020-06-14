package gr.csd.uoc.hy463.themis.linkAnalysis.graph;

/**
 * Node used for computing the pagerank scores
 */
public class PagerankNode {
    private double prevScore = 0;
    private double score = 0;
    private int outNodes = 0;
    private PagerankNode[] inNodes;

    /**
     * Initializes the In Nodes to an array that has capacity num
     * @param num
     */
    public void initializeInNodes(int num) {
        inNodes = new PagerankNode[num];
    }

    /**
     * Returns the previous score
     * @return
     */
    public double getPrevScore() {
        return prevScore;
    }

    /**
     * Returns the current score
     * @return
     */
    public double getScore() {
        return score;
    }

    /**
     * Sets the previous score to the specified score
     * @param score
     */
    public void setPrevScore(double score) {
        prevScore = score;
    }

    /**
     * Sets the current score to the specified score
     * @param newScore
     */
    public void setScore(double newScore) {
        score = newScore;
    }

    /**
     * Returns the number of Out Nodes
     */
    public int getOutNodes() {
        return outNodes;
    }

    /**
     * Sets the number of Out Nodes to the specified num
     * @param num
     */
    public void setOutNodes(int num) {
        outNodes = num;
    }

    /**
     * Returns the array of In Nodes
     * @return
     */
    public PagerankNode[] getInNodes() {
        return inNodes;
    }
}
