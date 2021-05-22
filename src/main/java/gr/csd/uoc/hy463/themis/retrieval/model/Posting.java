package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the postings of a term, organized in two arrays. One for the TF and one for the intID of
 * the relevant document.
 */
public class Posting {
    private int[] tfs;
    private int[] intID;

    public Posting(int[] tfs, int[] intID) {
        this.tfs = tfs;
        this.intID = intID;
    }

    public int[] getTfs() {
        return tfs;
    }

    public int[] getIntID() {
        return intID;
    }
}
