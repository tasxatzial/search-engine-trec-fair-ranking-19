package gr.csd.uoc.hy463.themis.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Split a string into a List of terms using the space character as delimiter.
 * If there are consecutive spaces, an empty string is added as a term.
 */
public class SpaceSplit {
    public static List<String> splitString(String str) {
        StringBuilder sb = new StringBuilder();
        List<String> words = new ArrayList<>();
        char[] strArray = str.toCharArray();
        for (char c : strArray) {
            if (c == ' ') {
                words.add(sb.toString());
                sb.delete(0, sb.length());
            } else {
                sb.append(c);
            }
        }
        if (strArray[strArray.length - 1] != ' ') {
            words.add(sb.toString());
        }
        return words;
    }
}
