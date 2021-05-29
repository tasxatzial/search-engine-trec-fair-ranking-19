package gr.csd.uoc.hy463.themis.retrieval;

/**
 * This class represents a query term. A query term can have some kind of
 * weight! Might be useful for experimenting with different weights for terms
 * (e.g., synonyms/antonyms)
 */
public class QueryTerm {
    private double _weight;
    private final String _term;

    public QueryTerm(String term, double weight) {
        _term = term;
        _weight = weight;
    }

    public String get_term() {
        return _term;
    }

    public double get_weight() {
        return _weight;
    }

    public void set_weight(double weight) {
        _weight = weight;
    }
}
