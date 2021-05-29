package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the postings of a term, organized in two arrays. One for the TFs and one for the intIDs of
 * the relevant documents.
 */
public class Postings {
    private final int[] _tfs;
    private final int[] _intID;

    public Postings(int[] tfs, int[] intID) {
        _tfs = tfs;
        _intID = intID;
    }

    public int[] get_tfs() {
        return _tfs;
    }

    public int[] get_intID() {
        return _intID;
    }
}
