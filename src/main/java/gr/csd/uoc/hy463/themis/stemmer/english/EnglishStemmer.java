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

 /*
    !! Code from mitos web search engine !!
 */
package gr.csd.uoc.hy463.themis.stemmer.english;

/**
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class EnglishStemmer {

    private static EnglishStemmer m_englishStemmer = null;
    private PorterStemmer m_PorterStemmer;

    private EnglishStemmer() {

        m_PorterStemmer = new PorterStemmer();
    }

    public static EnglishStemmer Singleton() {

        return (m_englishStemmer == null) ? (m_englishStemmer = new EnglishStemmer())
                : m_englishStemmer;
    }

    public String Stem(String _word) {

        String word = _word;
        m_PorterStemmer.add(word.toCharArray(), word.length());
        m_PorterStemmer.stem();
        return m_PorterStemmer.toString();
    }

} // class EnglishStemmer
