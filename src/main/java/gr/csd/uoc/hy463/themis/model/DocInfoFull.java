/*
 * themis - A fair search engine for scientific articles
 *
 * Currently over the Semantic Scholar Open Research Corpus
 * http://s2-public-api-prod.us-west-2.elasticbeanstalk.com/corpus/
 *
 * Collaborative work with the undergraduate/graduate students of
 * Information Retrieval Systems (hy463) course
 * Spring Semester 2020
 *
 * -- Writing code during COVID-19 pandemic times :-( --
 *
 * Aiming to participate in TREC 2020 Fair Ranking Track
 * https://fair-trec.github.io/
 *
 * Computer Science Department http://www.csd.uoc.gr
 * University of Crete
 * Greece
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.model;

/**
 * This class could be used when we want to get all information of a specific
 * document, etc. title, authors, etc.
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class DocInfoFull extends DocInfoEssential {

    public DocInfoFull(String id, long offset) {
        super(id, offset);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DocInfoEssential other = (DocInfoEssential) o;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
