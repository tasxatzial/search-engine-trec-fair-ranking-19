/*
 * bias goggles - Measuring the bias of web domains through the eyes of users
 *
 * FOUNDATION OF RESEARCH AND TECHNOLOGY - HELLAS (FORTH-ICS)
 *
 * INFORMATION SYSTEMS LABORATORY (ISL)
 *
 * http://www.ics.forth.gr/isl
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2019-2020
 *
 */

 /*
    !! Code from mitos web search engine !!
 */
package gr.csd.uoc.hy463.fairness.trec.stemmer;

/**
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
import gr.csd.uoc.hy463.fairness.trec.stemmer.english.EnglishStemmer;
import javax.swing.*;

/*
 *
 *
 *
 */
public class Stemmer {

    private Stemmer() {
    }

    public static String Stem(String string) {
        // To lower case
        string = string.trim().toLowerCase();
        // If emptry return.
        if (string.length() == 0) {
            return string;
        }
        // Java uses Unicode
        // Modern Greek in unicode like ISO 8859-7 are between 0370 - 03FF
        // Basic Latin 0021 - 007E
        // !TODO Currently we do not stem any string that does not start with a ascii character
        char c = string.charAt(0); // Get the first character of the word
        if (c >= (char) 880 && c <= (char) 1023) // If character is between
        // 0x0370 - 0x03FF
        {
            return string;
            //GreekStemmer.Singleton().Stem(string); // return the greek
        } // stemmer
        else if (c >= (char) 33 && c <= (char) 126) // // If character is
        // between 0x0021 - 0x007E
        {
            return EnglishStemmer.Singleton().Stem(string); // return the
        } // english stemmer
        else {
            return string; // In all other cases return string as is
        }
    }

    public static void Initialize() {
        EnglishStemmer.Singleton();
    }

    public static void main(String[] a) {
        String w;
        Stemmer.Initialize();

        do {
            w = JOptionPane.showInputDialog("Please input a word");
            JOptionPane.showMessageDialog(null, Stemmer.Stem(w), w,
                    JOptionPane.INFORMATION_MESSAGE);
        } while (!w.equals(""));
    }

} // class Stemmer

