package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Holds the name of a DocInfo property and the frequency of a term in the corresponding data. See also class
 * DocInfo.
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
