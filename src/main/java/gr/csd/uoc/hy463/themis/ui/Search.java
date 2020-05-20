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
import java.util.*;

/**
 * Some kind of simple ui to search the indexes. Some kind of GUI will be a
 * bonus!
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class Search {
    private static final Logger __LOGGER__ = LogManager.getLogger(Search.class);
    private Indexer _indexer;
    private ARetrievalModel _model;
    private enum TASK {
        LOAD_INDEX, SEARCH
    }
    private TASK _task = null;

    public Search() throws IOException {
        _indexer = new Indexer();
        String retrievalModel = _indexer.getRetrievalModel();
        switch (retrievalModel) {
            case "Existential":
                _model = new Existential(_indexer);
                break;
            case "BM25":
                _model = new OkapiBM25(_indexer);
                break;
            case "VSM":
                _model = new VSM(_indexer);
                break;
        }
    }

    public void loadIndex() {
        if (isRunning()) {
            return;
        }
        Thread runnableIndexer = new Thread(() -> {
            _task = TASK.LOAD_INDEX;
            try {
                _indexer.load();
            } catch (IOException e) {
                Themis.print("Failed to load index\n");
                __LOGGER__.error(e.getMessage());
            }
            finally {
                _task = null;
            }
        });
        runnableIndexer.start();
    }

    public boolean isIndexLoaded() {
        return _indexer.loaded();
    }

    public void unloadIndex() throws IOException {
        _indexer.unload();
    }

    /**
     * Searches for a query. The results should contain at least the document information specified by
     * the docInfoProps.
     * @param query A string that contains some terms
     * @param docInfoProps The document information that we want to be retrieved
     * @throws IOException
     */
    public void search(String query, Set<DocInfo.PROPERTY> docInfoProps, int startResult, int endResult) {
        Themis.clearResults();

        /* the simplest split based on spaces, suffices for now */
        String[] searchTerms = query.split(" ");

        /* create the list of query terms */
        List<QueryTerm> queryTerms = new ArrayList<>();
        for (String term : searchTerms) {
            if (!term.equals("")) {
                queryTerms.add(new QueryTerm(term, 1.0));
            }
        }

        Thread runnableSearch = new Thread(() -> {
            List<Pair<Object, Double>> searchResults;
            long startTime = System.nanoTime();
            _task = TASK.SEARCH;
            try {
                //call the model and retrieve the results
                searchResults = _model.getRankedResults(queryTerms, docInfoProps, startResult, endResult);
            } catch (IOException e) {
                __LOGGER__.error(e.getMessage());
                Themis.print("Search failed\n");
                return;
            }
            finally {
                _task = null;
            }
            Themis.print("Search time: " + Math.round((System.nanoTime() - startTime) / 1e4) / 100.0 + " ms\n");
            Themis.print("Found " + searchResults.size() + " results\n");
            Themis.print("Displaying results " + (startResult + 1) + " to " + (endResult + 1) + "\n\n");

            /* print the results */
            for (int i = startResult; i <= endResult; i++) {
                DocInfo docInfo = (DocInfo) searchResults.get(i).getL();
                List<DocInfo.PROPERTY> sortedProps = new ArrayList<>(docInfo.getProps());
                Collections.sort(sortedProps);
                Themis.print("DOC_ID: " + docInfo.getId() + "\n");
                for (DocInfo.PROPERTY docInfoProp : sortedProps) {
                    Themis.print(docInfoProp + ": " + docInfo.getProperty(docInfoProp) + "\n");
                }
                if (!docInfoProps.isEmpty()) {
                    Themis.print("\n");
                }
            }
        });
        runnableSearch.start();
    }

    public boolean isRunning() {
        return _task != null;
    }

    public String get_task() {
        return _task.toString();
    }
}
