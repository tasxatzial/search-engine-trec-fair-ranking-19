/*
 * themis - A fair search engine for scientific articles
 *
 * Computer Science Department
 *
 * University of Crete
 *
 * http://www.csd.uoc.gr
 *
 * Project for hy463 Information Retrieval Systems course
 * Spring Semester 2020
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.stemmer;

/**
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.StringTokenizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StopWords {

    private static final Logger LOGGER = LogManager.getLogger(StopWords.class);
    private static HashSet<String> words;
    private static StopWords m_stopWords = null;
    private static String STOPLIST_PATH = "stopwords.txt";
    private BufferedReader br;
    private StringBuffer strBuf;

    /**
     * Words constructor, initializes stoplist
     */
    private StopWords() {
        words = new HashSet<String>();

        try {
            br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/stopwords.txt")));
            strBuf = new StringBuffer();

            while (true) {
                int ch = br.read();
                if (ch == -1) {
                    break;
                }
                strBuf.append((char) ch);
            }

            StringTokenizer strTok = new StringTokenizer(strBuf.toString());
            while (strTok.hasMoreTokens()) {
                words.add(strTok.nextToken().toLowerCase());
            }

        } catch (FileNotFoundException e) {
            LOGGER.error("Could not load stopwords file!");
        } catch (IOException e) {
            LOGGER.error("IOException found!");
        }

    }

    /**
     * Initialize stopwords. Add the stopwords from file
     */
    public static void Initialize() {
        m_stopWords = (m_stopWords == null) ? new StopWords() : m_stopWords;
    }

    /**
     * Checks if a word is a stop word
     *
     * @param word The term to be checked
     *
     * @return true if the term is a stop word, false otherwise
     */
    public static boolean isStopWord(String word) {
        Initialize();

        return words.contains(word);
    }

    /**
     * Checks if a word is an operator word
     *
     * @param word The term to be checked
     *
     * @return true if the term is an operator word ("and","or","not"), false
     * otherwise
     */
    /*---------------------------------------------------*/
    public static final boolean isOpWord(String word) {
        Initialize();
        return word.compareToIgnoreCase("and") == 0
                || word.compareToIgnoreCase("or") == 0
                || word.compareToIgnoreCase("not") == 0;
    }
}
