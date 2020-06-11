package gr.csd.uoc.hy463.themis.linkAnalysis.graph;

import java.util.*;

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
     * Returns a score of the node based on the In Nodes and number of Out Nodes of each In Node.
     * The formula for this score is: sum(InNode / #OutNodes(InNode))
     */
    public double calcInScore() {
        double inScore = 0;
        for (int i = 0; i < inNodes.length; i++) {
            inScore += inNodes[i].getPrevScore() / inNodes[i].getOutNodes();
        }
        return inScore;
    }

    /**
     * Sets the current score to the specified score
     * @param newScore
     */
    public void setScore(double newScore) {
        score = newScore;
    }

    /**
     * Sets the previous score equal to the current score
     */
    public void updatePrevScore() {
        prevScore = score;
    }

    /**
     * Adds a node to the array of In Nodes at the specified index
     * @param node
     */
    public void addInNode(int index, PagerankNode node) {
        inNodes[index] = node;
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
}
