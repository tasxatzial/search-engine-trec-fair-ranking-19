package gr.csd.uoc.hy463.themis.utils;

import java.io.Serializable;

/**
 * Class that represents a Pair, i.e. a tuple of size two
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
