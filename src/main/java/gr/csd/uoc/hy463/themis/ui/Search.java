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
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.retrieval.models.Existential;
import gr.csd.uoc.hy463.themis.retrieval.models.OkapiBM25;
import gr.csd.uoc.hy463.themis.retrieval.models.VSM;
import gr.csd.uoc.hy463.themis.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public void search(String terms, List<Object> fields, int topk) throws IOException {
        String retrievalModel = _indexer.getRetrievalModel();
        ARetrievalModel model;
        switch (retrievalModel) {
            case "Existential":
                model = new Existential(_indexer);
                break;
            case "BM25":
                model = new OkapiBM25(_indexer);
                break;
            case "VSM":
                model = new VSM(_indexer);
                break;
            default:
                return;
        }

        String[] split = terms.split(" ");
        List<QueryTerm> query = new ArrayList<>();
        for (String term : split) {
            query.add(new QueryTerm(term, 1.0));
        }
        List<DocInfo.PROPERTY> props = new ArrayList<>();
        List<Pair<Object, Double>> results = model.getRankedResults(query, props, topk);

        for (Pair<Object, Double> pair : results) {
            DocInfo docInfo = (DocInfo) pair.getL();
            Themis.view.print("DOC_ID: " + docInfo.getId() + "\n");
            for (DocInfo.PROPERTY prop : props) {
                Themis.view.print(prop.toString() + ": " + docInfo.getProperty(prop) + "\n");
            }
            if (!props.isEmpty()) {
                Themis.view.print("\n");
            }
        }
    }

    public void stop() {
        _indexer.stop();
    }

    public boolean isRunning() {
        return _indexer.isRunning();
    }
}
