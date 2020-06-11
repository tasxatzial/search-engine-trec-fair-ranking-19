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
package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.indexer.Indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * This class runs the indexers
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 *
 */
public class CreateIndex {
    private Indexer _indexer;

    public CreateIndex() throws IOException {
        _indexer = new Indexer();
    }

    public void createIndex() throws IOException {
        _indexer.index();
    }

    public void deleteIndex() throws IOException {
        _indexer.deleteIndex();
    }

    public boolean isIndexDirEmpty() {
        return _indexer.isIndexDirEmpty();
    }
}
