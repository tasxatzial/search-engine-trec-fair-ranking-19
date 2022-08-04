package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.Comparator;

/**
 * Used during the merging of the partial vocabularies. Each instance stores:
 * - Term
 * - DF (document frequency of the term in the partial vocabulary)
 * - Partial index ID. This is the ID of the partial index that contains the term.
 */
public class PartialVocabularyEntry implements Comparable<PartialVocabularyEntry> {
    private final String _term;
    private final int _DF;
    private final int _indexID;

    /* comparator to sort by index ID */
    public static Comparator<PartialVocabularyEntry> IDComparator = Comparator.comparingInt(PartialVocabularyEntry::getIndexID);

    public PartialVocabularyEntry(String term, int DF, int indexId) {
        _term = term;
        _DF = DF;
        _indexID = indexId;
    }

    public String getTerm() {
        return _term;
    }

    public int getDF() {
        return _DF;
    }

    public int getIndexID() {
        return _indexID;
    }

    @Override
    public int compareTo(PartialVocabularyEntry o) {
        return _term.compareTo(o.getTerm());
    }
}
