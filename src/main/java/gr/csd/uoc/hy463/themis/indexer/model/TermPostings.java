package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.ArrayList;
import java.util.List;

/**
 *  Stores the postings of a term (a list of entries in POSTINGS_FILENAME).
 */
public class TermPostings {
    private final List<Posting> _postings;

    public TermPostings() {
        _postings = new ArrayList<>();
    }

    /**
     * Returns the DF (document frequency) of the term.
     *
     * @return
     */
    public int getDF() {
        return _postings.size();
    }

    public void addPosting(Posting posting) {
        _postings.add(posting);
    }

    /**
     * Returns the postings. Iterating on them will always retain the order in which they were added.
     *
     * @return
     */
    public List<Posting> getPostings() {
        return _postings;
    }
}
