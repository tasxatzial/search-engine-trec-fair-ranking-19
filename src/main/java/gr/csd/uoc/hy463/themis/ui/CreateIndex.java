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

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 *
 * This class runs the indexers
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 *
 */
public class CreateIndex {
    private static final Logger __LOGGER__ = LogManager.getLogger(CreateIndex.class);
    private Indexer _indexer;
    private enum TASK {
        CREATE_INDEX
    }
    private TASK _task = null;

    public CreateIndex() throws IOException {
        _indexer = new Indexer();
    }

    public void createIndex(boolean deletePreviousIndex) {
        if (isRunning()) {
            return;
        }
        Thread runnableIndexer = new Thread(() -> {
            _task = TASK.CREATE_INDEX;
            try {
                if (deletePreviousIndex) {
                    _indexer.deleteIndex();
                }
                _indexer.index();
            } catch (IOException e) {
                Themis.print("Error: Failed to create index\n");
                __LOGGER__.error(e.getMessage());
            }
            finally {
                _task = null;
            }
        });
        runnableIndexer.start();
    }

    public boolean isIndexDirEmpty() {
        return _indexer.isIndexDirEmpty();
    }

    public boolean isRunning() {
        return _task != null;
    }

    public String get_task() {
        return _task.toString();
    }
}
