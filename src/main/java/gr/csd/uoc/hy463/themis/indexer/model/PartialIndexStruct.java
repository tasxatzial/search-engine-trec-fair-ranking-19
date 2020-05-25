package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.ArrayList;
import java.util.List;

/**
 *  This class is used during the creation of the partial indexes. It holds all the required information about the
 *  value of a term: (df, list of posting entries)
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

    public int get_df() {
        return _df;
    }

    public List<PostingStruct> get_postings() {
        return _postings;
    }
}
