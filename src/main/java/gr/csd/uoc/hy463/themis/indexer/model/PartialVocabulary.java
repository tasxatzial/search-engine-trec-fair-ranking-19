package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.Comparator;

/**
 * This class is used during the merging of the partial vocabularies. It holds the following data:
 * - Term
 * - DF = document frequency of this term = in how many documents this vocabulary term is found
 * - Partial index ID = A vocabulary term is found in this partial index
 */
public class PartialVocabulary implements Comparable<PartialVocabulary> {
    private final String _term;
    private final int _df;
    private final int _indexID;

    /* use this comparator to sort objects by their ID */
    public static Comparator<PartialVocabulary> idComparator = Comparator.comparingInt(PartialVocabulary::get_indexID);

    public PartialVocabulary(String term, int df, int indexId) {
        _term = term;
        _df = df;
        _indexID = indexId;
    }

    public String get_term() {
        return _term;
    }

    public int get_df() {
        return _df;
    }

    public int get_indexID() {
        return _indexID;
    }

    @Override
    public int compareTo(PartialVocabulary o) {
        return _term.compareTo(o.get_term());
    }
}
