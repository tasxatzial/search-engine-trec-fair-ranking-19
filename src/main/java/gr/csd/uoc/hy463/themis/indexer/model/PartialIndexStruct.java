package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.ArrayList;
import java.util.List;

/* Class that holds basic information about an entry that belongs to a partial
index: The DF and a list of posting entries.
 */
public class PartialIndexStruct {
    private int _df;
    private List<PostingStruct> _postings;

    public PartialIndexStruct(int df) {
        _df = df;
        _postings = new ArrayList<>();
    }
    public void set_df(int df) {
        _df = df;
    }

    public Integer get_df() {
        return _df;
    }

    public List<PostingStruct> get_postings() {
        return _postings;
    }
}
