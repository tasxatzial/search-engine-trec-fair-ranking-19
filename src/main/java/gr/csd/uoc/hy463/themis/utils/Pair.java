/*
 * themis - A fair search engine for scientific articles
 *
 * Currently over the Semantic Scholar Open Research Corpus
 * http://s2-public-api-prod.us-west-2.elasticbeanstalk.com/corpus/
 *
 * Aiming to participate in TREC 2020 Fair Ranking Track
 * https://fair-trec.github.io/
 *
 * Writing code during COVID-19 pandemic times :-(
 *
 * Collaborative work with the undergraduate/graduate students of
 * Information Retrieval Systems (hy463) course
 * Spring Semester 2020
 * of Computer Science Department
 * University of Crete
 * http://www.csd.uoc.gr
 * Greece
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.utils;

import java.io.Serializable;

/**
 * FORTH-ICS
 *
 * @param <L>
 * @param <R>
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class Pair<L, R> implements Serializable {

    private L _l;
    private R _r;

    public Pair(L l, R r) {
        this._l = l;
        this._r = r;
    }

    public L getL() {
        return _l;
    }

    public void setL(L l) {
        this._l = l;
    }

    public R getR() {
        return _r;
    }

    public void setR(R r) {
        this._r = r;
    }

    @Override
    public String toString() {
        return _l.toString() + " " + _r.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Pair<?, ?> other = (Pair<?, ?>) obj;

        if (this._l.equals(other._l) && this._r.equals(other._r)) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this._l.hashCode() + this._r.hashCode();
    }
}
