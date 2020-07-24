package gr.csd.uoc.hy463.themis.retrieval;

/**
 * This class represents a query term. A query term can have some kind of
 * weight! Might be useful for experimenting with different weights for terms
 * (e.g., synonyms/antonyms)
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class QueryTerm {

    private double weight = 1.0;
    private String term = null;

    public QueryTerm(String term) {
        this.term = term;
    }

    public QueryTerm(String term, double weight) {
        this.term = term;
        this.weight = weight;

    }

    public String getTerm() {
        return term;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }
}
