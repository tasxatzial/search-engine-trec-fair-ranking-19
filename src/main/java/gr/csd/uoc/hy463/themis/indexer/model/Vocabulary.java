package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Holds two things about a term:
 * 1) DF = document frequency of this term = in how many documents this term is found
 * 2) Posting offset = offset to the 'postings' file
 */
public class Vocabulary {
    private final int _df;
    private final long _postingsOffset;

    public Vocabulary(int df, long postingsOffset) {
        _df = df;
        _postingsOffset = postingsOffset;
    }

    public int get_df() {
        return _df;
    }

    public long get_postingsOffset() {
        return _postingsOffset;
    }
}
