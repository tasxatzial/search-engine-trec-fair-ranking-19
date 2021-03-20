package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.Comparator;

/**
 * This class is used during the merging of the partial vocabularies. It holds all the required information
 * about a vocabulary entry: (term, df, partial index id)
 */
public class PartialVocabularyStruct implements Comparable<PartialVocabularyStruct> {
    private String _term;
    private int _df;
    private int _indexId;
    public static Comparator<PartialVocabularyStruct> idComparator = Comparator.comparingInt(PartialVocabularyStruct::get_indexId);

    public PartialVocabularyStruct(String term, int df, int indexId) {
        _term = term;
        _df = df;
        _indexId = indexId;
    }

    public String get_term() {
        return _term;
    }

    public int get_df() {
        return _df;
    }

    public int get_indexId() {
        return _indexId;
    }

    @Override
    public int compareTo(PartialVocabularyStruct o) {
        return _term.compareTo(o.get_term());
    }
}
