package gr.csd.uoc.hy463.themis.retrieval.model;

import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;

/**
 * Represents a query result. Has a DocInfo instance and a score.
 */
public class Result implements Comparable<Result> {
    private final DocInfo docInfo;
    private double score;

    public Result(DocInfo docInfo, double score) {
        this.docInfo = docInfo;
        this.score = score;
    }

    public double getScore() {
        return score;
    }

    public DocInfo getDocInfo() {
        return docInfo;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Override
    public int compareTo(Result r) {
        if (r.score > score) {
            return 1;
        }
        if (r.score < score) {
            return -1;
        }
        return 0;
    }
}
