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
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFull;
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

    public void search(String terms, ARetrievalModel.RESULT_TYPE type, int topk) throws IOException {
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
        List<Pair<Object, Double>> results = model.getRankedResults(query, type, topk);

        if (type == ARetrievalModel.RESULT_TYPE.PLAIN) {
            for (Pair<Object, Double> pair : results) {
                Themis.view.print(pair.getL() + "\n");
            }
        }
        else if (type == ARetrievalModel.RESULT_TYPE.ESSENTIAL) {
            for (Pair<Object, Double> pair : results) {
                DocInfoEssential docInfo = (DocInfoEssential) pair.getL();
                Themis.view.print("doc id: " + docInfo.getId() + "\n");
                Themis.view.print("weight: " + docInfo.getProperty(DocInfoEssential.PROPERTY.WEIGHT) + "\n");
                Themis.view.print("length: " + docInfo.getProperty(DocInfoEssential.PROPERTY.LENGTH) + "\n");
                Themis.view.print("pagerank: " + docInfo.getProperty(DocInfoEssential.PROPERTY.PAGERANK) + "\n\n");
            }
        }
        else if (type == ARetrievalModel.RESULT_TYPE.FULL) {
            for (Pair<Object, Double> pair : results) {
                DocInfoFull docInfo = (DocInfoFull) pair.getL();
                Themis.view.print("doc id: " + docInfo.getId() + "\n");
                Themis.view.print("title: " + docInfo.getProperty(DocInfoEssential.PROPERTY.TITLE) + "\n");
                Themis.view.print("authors: " + docInfo.getProperty(DocInfoEssential.PROPERTY.AUTHORS_NAMES) + "\n");
                Themis.view.print("authors ids: " + docInfo.getProperty(DocInfoEssential.PROPERTY.AUTHORS_IDS) + "\n");
                Themis.view.print("year: " + docInfo.getProperty(DocInfoEssential.PROPERTY.YEAR) + "\n");
                Themis.view.print("journal name: " + docInfo.getProperty(DocInfoEssential.PROPERTY.JOURNAL_NAME) + "\n");
                Themis.view.print("weight: " + docInfo.getProperty(DocInfoEssential.PROPERTY.WEIGHT) + "\n");
                Themis.view.print("length: " + docInfo.getProperty(DocInfoEssential.PROPERTY.LENGTH) + "\n");
                Themis.view.print("pagerank: " + docInfo.getProperty(DocInfoEssential.PROPERTY.PAGERANK) + "\n\n");
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
