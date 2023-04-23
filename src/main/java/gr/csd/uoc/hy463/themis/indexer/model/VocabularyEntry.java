package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Represents the data about a term in VOCABULARY_FILENAME. Holds the DF (document frequency) of the term
 * and the offset to POSTINGS_FILENAME.
 */
public class VocabularyEntry {
    private final int _DF;
    private final long _postingsOffset;

    public VocabularyEntry(int DF, long postingsOffset) {
        _DF = DF;
        _postingsOffset = postingsOffset;
    }

    public int getDF() {
        return _DF;
    }

    public long getPostingsOffset() {
        return _postingsOffset;
    }
}
