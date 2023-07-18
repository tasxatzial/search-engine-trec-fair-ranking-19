package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the postings of a term, organized in two arrays of the same size:
 * - Array of ID of the documents that have the term
 * - Array of TF (frequency of the term in the corresponding documents)
 */
public class TermPostings {
    private final int[] _TFs;
    private final int[] _intID;

    public TermPostings(int[] TFs, int[] docID) {
        _TFs = TFs;
        _intID = docID;
    }

    public int[] getTFs() {
        return _TFs;
    }

    public int[] getIntID() {
        return _intID;
    }
}
