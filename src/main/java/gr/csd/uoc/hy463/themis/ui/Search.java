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
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.retrieval.models.Existential;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Some kind of simple ui to search the indexes. Some kind of GUI will be a
 * bonus!
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class Search {
    private static final Logger __LOGGER__ = LogManager.getLogger(CreateIndex.class);
    private Indexer _indexer;

    public Search() throws IOException {
        _indexer = new Indexer();
    }

    public void loadIndex() {
        if (isRunning()) {
            return;
        }
        _indexer.setTask(Indexer.TASK.LOAD_INDEX);
        Thread runnableIndexer = new Thread(_indexer);
        runnableIndexer.start();
    }

    public boolean isIndexLoaded() {
        return _indexer.loaded();
    }

    public boolean unloadIndex() {
        try {
            _indexer.unload();
        } catch (IOException e) {
            __LOGGER__.error(e.getMessage());
            return false;
        }
        return true;
    }

    public String getTask() {
        Indexer.TASK task = _indexer.getTask();
        if (task != null) {
            return task.toString();
        }
        return null;
    }

    public void search(String terms, ARetrievalModel.RESULT_TYPE type) throws IOException {

    }

    public void stop() {
        _indexer.stop();
    }

    public boolean isRunning() {
        return _indexer.isRunning();
    }
}
