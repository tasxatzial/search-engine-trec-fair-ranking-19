package gr.csd.uoc.hy463.themis.utils;

/**
 * Class is used during merging the partial dictionaries. It holds all the required
 * information for an entry in a partial vocabulary file
 * (term, df, postings offset, partial index id)
 */
public class VocabularyEntry implements Comparable<VocabularyEntry> {
    private String _term;
    private int _df;
    private long _offset;
    private int _indexId;

    public VocabularyEntry(String term, int df, long offset, int indexId) {
        _term = term;
        _df = df;
        _offset = offset;
        _indexId = indexId;
    }

    public String get_term() {
        return _term;
    }

    public int get_df() {
        return _df;
    }

    public long get_offset() {
        return _offset;
    }

    public int get_indexId() {
        return _indexId;
    }

    @Override
    public int compareTo(VocabularyEntry o) {
        return _term.compareTo(o.get_term());
    }
}
