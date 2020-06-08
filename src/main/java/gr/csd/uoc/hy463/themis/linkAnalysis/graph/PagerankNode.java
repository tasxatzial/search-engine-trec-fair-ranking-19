package gr.csd.uoc.hy463.themis.linkAnalysis.graph;

import java.util.*;

/**
 * Node used for computing the pagerank scores
 */
public class PagerankNode {
    private static double dumpingFactor = 0.85;
    private double prevScore = 0;
    private double score = 0;
    private int outNodes = 0;
    private List<PagerankNode> inNodes;

    /**
     * Initializes the In Nodes to an empty array list that has initial capacity num
     * @param num
     */
    public void initializeInNodes(int num) {
        inNodes = new ArrayList<>(num);
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
     * Computes the current score
     */
    public void calculateScore() {
        score = 0;
        for (PagerankNode inNode : inNodes) {
            score += inNode.getPrevScore() / inNode.getOutNodes();
        }
        score *= dumpingFactor;
        score += (1 - dumpingFactor);
    }

    /**
     * Sets the previous score equal to the current score
     */
    public void updatePrevScore() {
        prevScore = score;
    }

    /**
     * Adds a node to the list of In Nodes
     * @param node
     */
    public void addInNode(PagerankNode node) {
        inNodes.add(node);
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
