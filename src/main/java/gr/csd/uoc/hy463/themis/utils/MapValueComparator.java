package gr.csd.uoc.hy463.themis.utils;

import java.util.Comparator;
import java.util.Map;

/**
 * A comparator for sorting a map by its values.
 */
public class MapValueComparator implements Comparator<Integer> {
    private final Map<Integer, Integer> _map;

    public MapValueComparator(Map<Integer, Integer> map) {
        _map = map;
    }

    @Override
    public int compare(Integer o1, Integer o2) {
        int res = _map.get(o2).compareTo(_map.get(o1));
        if (o1.equals(o2)) {
            return res; //equality
        } else {
            return res != 0 ? res : 1; //add all entries
        }
    }
}
