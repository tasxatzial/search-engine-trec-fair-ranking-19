package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * This class is used during the indexing process. It holds all required information about the
 * frequency of a term in a document field: (document field name, frequency of term in this field)
 */
public class DocInfoFrequency {
    DocInfo.PROPERTY _prop;
    int _frequency;

    public DocInfoFrequency(DocInfo.PROPERTY prop) {
        _prop = prop;
        _frequency = 1;
    }

    public DocInfo.PROPERTY get_prop() {
        return _prop;
    }

    public int get_frequency() {
        return _frequency;
    }

    public void incr_frequency() {
        _frequency += 1;
    }
}
