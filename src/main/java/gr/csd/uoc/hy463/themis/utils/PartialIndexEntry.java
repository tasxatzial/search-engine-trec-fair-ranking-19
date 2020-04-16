package gr.csd.uoc.hy463.themis.utils;

import java.util.ArrayList;
import java.util.List;

/* Class that holds basic information about an entry that belongs to a partial
index: The DF and a list of posting entries.
 */
public class PartialIndexEntry {
    private int _df;
    private List<PostingEntry> _postings;

    public PartialIndexEntry(int df) {
        _df = df;
        _postings = new ArrayList<>();
    }
    public void set_df(int df) {
        _df = df;
    }

    public Integer get_df() {
        return _df;
    }

    public List<PostingEntry> get_postings() {
        return _postings;
    }
}
