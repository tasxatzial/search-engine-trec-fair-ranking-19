package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.ArrayList;
import java.util.List;

/**
 *  Holds the postings of a term, essentially a list of Posting objects.
 */
public class TermPostings {
    private final List<Posting> _postings;

    public TermPostings() {
        _postings = new ArrayList<>();
    }

    /**
     * Returns the DF of a term (in how many documents this term is found). Should be identical to
     * the size of the Posting list since each Posting corresponds to a different relevant document.
     * @return
     */
    public int get_df() {
        return _postings.size();
    }

    public List<Posting> get_postings() {
        return _postings;
    }
}
