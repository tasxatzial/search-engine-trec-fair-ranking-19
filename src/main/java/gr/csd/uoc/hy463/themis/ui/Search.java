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
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.retrieval.models.Existential;
import gr.csd.uoc.hy463.themis.retrieval.models.OkapiBM25;
import gr.csd.uoc.hy463.themis.retrieval.models.VSM;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * Some kind of simple ui to search the indexes. Some kind of GUI will be a
 * bonus!
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class Search {
    private Indexer _indexer;
    private ARetrievalModel _model;

    public Search() throws IOException {
        _indexer = new Indexer();
        ARetrievalModel.MODEL retrievalModel = _indexer.getRetrievalModel();
        switch (retrievalModel) {
            case EXISTENTIAL:
                _model = new Existential(_indexer);
                break;
            case BM25:
                _model = new OkapiBM25(_indexer);
                break;
            case VSM:
                _model = new VSM(_indexer);
                break;
        }
        _indexer.load();
    }

    public void unloadIndex() throws IOException {
        _indexer.unload();
    }

    public void setModelVSM() {
        _model = new VSM(_indexer);
    }

    public void setModelBM25() {
        _model = new OkapiBM25(_indexer);
    }

    public void setModelExistential() {
        _model = new Existential(_indexer);
    }

    /**
     * Searches for a query and returns a ranked list of results. The results contain at least the document
     * properties specified by docInfoProps.
     * @param query
     * @param docInfoProps The document properties that we want to retrieve
     * @return
     * @throws IOException
     */
    public List<Pair<Object, Double>> search(String query, Set<DocInfo.PROPERTY> docInfoProps) throws IOException {
        return search(query, docInfoProps, 0, Integer.MAX_VALUE);
    }

    /**
     * Searches for a query using the specified model and returns a ranked list of results. The results in the range
     * [startResult, endResult] contain at least the document properties specified by docInfoProps.
     * @param query
     * @param docInfoProps The document properties that we want to retrieve
     * @param startResult From 0 to Integer.MAX_VALUE
     * @param endResult From 0 to Integer.MAX_VALUE
     * @return
     * @throws IOException
     */
    public List<Pair<Object, Double>> search(String query, Set<DocInfo.PROPERTY> docInfoProps,
                                             int startResult, int endResult) throws IOException {
        if (_model == null) {
            return null;
        }
        Themis.print("Searching for '" + query + "'...\n");
        long startTime = System.nanoTime();

        /* create the list of query terms */
        List<String> terms = ProcessText.editQuery(query);
        List<QueryTerm> queryTerms = new ArrayList<>();
        terms.forEach(t -> queryTerms.add(new QueryTerm(t, 1.0)));

        List<Pair<Object, Double>> results = _model.getRankedResults(queryTerms, docInfoProps, startResult, endResult);
        Themis.print("DONE\nSearch time: " + Math.round((System.nanoTime() - startTime) / 1e4) / 100.0 + " ms\n");
        return results;
    }

    public void printResults(List<Pair<Object, Double>> searchResults, int startResult, int endResult) {
        if (searchResults == null) {
            return;
        }
        /* startResult and endResult might be out of the range of the actual results for this document.
        therefore we need to find the proper indexes of the first and last displayed result */
        int firstDisplayedResult;
        int lastDisplayedResult;
        if (startResult > searchResults.size() - 1) {
            firstDisplayedResult = 0;
            lastDisplayedResult = 0;
        }
        else if (endResult > searchResults.size() - 1) {
            firstDisplayedResult = startResult;
            lastDisplayedResult = searchResults.size() - 1;
        }
        else {
            firstDisplayedResult = startResult;
            lastDisplayedResult = endResult;
        }

        if (!searchResults.isEmpty()) {
            Themis.print("Displaying results " + (firstDisplayedResult + 1) + " to " + (lastDisplayedResult + 1) + "\n\n");
        }

        /* print the results */
        for (int i = firstDisplayedResult; i <= lastDisplayedResult; i++) {
            DocInfo docInfo = (DocInfo) searchResults.get(i).getL();
            List<DocInfo.PROPERTY> sortedProps = new ArrayList<>(docInfo.getProps());
            Collections.sort(sortedProps);
            Themis.print("DOC_ID: " + docInfo.getId() + "\n");
            for (DocInfo.PROPERTY docInfoProp : sortedProps) {
                Themis.print(docInfoProp + ": " + docInfo.getProperty(docInfoProp) + "\n");
            }
            if (!sortedProps.isEmpty()) {
                Themis.print("\n");
            }
        }
    }
}
