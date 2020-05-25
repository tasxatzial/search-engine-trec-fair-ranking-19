package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * This class is used when the final vocabulary is loaded into memory. It represents a vocabulary value:
 * (df, posting offset)
 */
public class VocabularyStruct {
    private int _df;
    private long _offset;

    public VocabularyStruct(int df, long offset) {
        _df = df;
        _offset = offset;
    }

    public int get_df() {
        return _df;
    }

    public long get_offset() {
        return _offset;
    }
}
