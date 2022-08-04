package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the postings of a term, organized in two arrays of the same size:
 * - Array of ID of the documents that have the term
 * - Array of TF (frequency of the term in the corresponding document)
 */
public class Postings {
    private final int[] _tfs;
    private final int[] _intID;

    public Postings(int[] tfs, int[] docID) {
        _tfs = tfs;
        _intID = docID;
    }

    public int[] get_tfs() {
        return _tfs;
    }

    public int[] get_intID() {
        return _intID;
    }
}
