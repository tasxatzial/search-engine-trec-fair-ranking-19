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
        ARetrievalModel.MODEL retrievalModel = _indexer.getDefaultRetrievalModel();
        switch (retrievalModel) {
            case BM25:
                _model = new OkapiBM25(_indexer);
                break;
            case VSM:
                _model = new VSM(_indexer);
                break;
            default:
                _model = new Existential(_indexer);
        }
        _indexer.load();
    }

    public void unloadIndex() throws IOException {
        _indexer.unload();
    }

    public void setModelVSM() {
        if (!(_model instanceof VSM)) {
            _model = new VSM(_indexer);
        }
    }

    public void setModelBM25() {
        if (!(_model instanceof OkapiBM25)) {
            _model = new OkapiBM25(_indexer);
        }
    }

    public void setModelExistential() {
        if (!(_model instanceof Existential)) {
            _model = new Existential(_indexer);
        }
    }

    public ARetrievalModel.MODEL getRetrievalModel() {
        if (_model instanceof OkapiBM25) {
            return ARetrievalModel.MODEL.BM25;
        }
        else if (_model instanceof VSM) {
            return ARetrievalModel.MODEL.VSM;
        }
        else if (_model instanceof Existential) {
            return ARetrievalModel.MODEL.EXISTENTIAL;
        }
        return null;
    }

    public String getIndexDirectory() {
        return _indexer.getIndexDirectory();
    }

    /**
     * Searches for a query and returns a ranked list of results. The results contain the essential properties
     * specified by the current retrieval model.
     * @param query
     * @return
     * @throws IOException
     */
    public List<Pair<Object, Double>> search(String query) throws IOException {
        return search(query, new HashSet<>(), 0, Integer.MAX_VALUE);
    }

    /**
     * Searches for a query and returns a ranked list of results. The results contain the document
     * properties specified by props + the essential properties specified by the current retrieval model.
     * @param query
     * @return
     * @throws IOException
     */
    public List<Pair<Object, Double>> search(String query, Set<DocInfo.PROPERTY> props) throws IOException {
        return search(query, props, 0, Integer.MAX_VALUE);
    }

    /**
     * Searches for a query and returns a ranked list of results. The results in the range
     * [startResult, endResult] contain the document properties specified by props + the essential properties
     * specified by the current retrieval model. All other results contain only the essential properties.
     * @param query
     * @param startResult From 0 to Integer.MAX_VALUE
     * @param endResult From 0 to Integer.MAX_VALUE
     * @return
     * @throws IOException
     */
    public List<Pair<Object, Double>> search(String query, Set<DocInfo.PROPERTY> props, int startResult, int endResult) throws IOException {
        boolean useStopwords = _indexer.useStopwords();
        boolean useStemmer = _indexer.useStemmer();

        /* create the list of query terms */
        List<String> terms = ProcessText.editQuery(query, useStopwords, useStemmer);
        List<QueryTerm> queryTerms = new ArrayList<>();
        terms.forEach(t -> queryTerms.add(new QueryTerm(t, 1.0)));

        /* perform a search */
        List<Pair<Object, Double>> results = _model.getRankedResults(queryTerms, props, startResult, endResult);

        return results;
    }

    /**
     * Prints a list of results in decreasing ranking order. The results show all properties that have been
     * retrieved for each document.
     * @param searchResults
     */
    public void printResults(List<Pair<Object, Double>> searchResults) {
        Set<DocInfo.PROPERTY> allprops = ARetrievalModel.getEssentialProps();
        allprops.addAll(ARetrievalModel.getNonEssentialProps());
        printResults(searchResults, allprops, 0, Integer.MAX_VALUE);
    }

    /**
     * Prints a list of results in decreasing ranking order. Only the results from ranked position startResult
     * to endResult are displayed. All properties that have been retrieved for each document are displayed.
     * @param searchResults
     * @param startResult From 0 to Integer.MAX_VALUE
     * @param endResult From 0 to Integer.MAX_VALUE
     */
    public void printResults(List<Pair<Object, Double>> searchResults, int startResult, int endResult) {
        Set<DocInfo.PROPERTY> allprops = ARetrievalModel.getEssentialProps();
        allprops.addAll(ARetrievalModel.getNonEssentialProps());
        printResults(searchResults, allprops, startResult, endResult);
    }

    /**
     * Prints a list of results in decreasing ranking order. The results display only the properties
     * specified by props (if they exist)
     * @param searchResults
     */
    public void printResults(List<Pair<Object, Double>> searchResults, Set<DocInfo.PROPERTY> props) {
        printResults(searchResults, props, 0, Integer.MAX_VALUE);
    }

    /**
     * Prints a list of results in decreasing ranking order from ranked position startResult to endResult.
     * Only the results from ranked position startResult to endResult are displayed.
     * Only the the properties specified by props are displayed (if they exist)
     * @param searchResults
     * @param startResult From 0 to Integer.MAX_VALUE
     * @param endResult From 0 to Integer.MAX_VALUE
     */
    public void printResults(List<Pair<Object, Double>> searchResults, Set<DocInfo.PROPERTY> props, int startResult, int endResult) {

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
        else {
            return;
        }

        /* print the results */
        for (int i = firstDisplayedResult; i <= lastDisplayedResult; i++) {
            DocInfo docInfo = (DocInfo) searchResults.get(i).getL();
            Set<DocInfo.PROPERTY> docInfoProps = docInfo.getProps();
            List<DocInfo.PROPERTY> sortedProps = new ArrayList<>(props);
            Collections.sort(sortedProps);
            Themis.print((i + 1) + " ---------------------------------------\n");
            Themis.print("DOC_ID: " + docInfo.getId() + "\n");
            for (DocInfo.PROPERTY docInfoProp : sortedProps) {
                if (docInfoProps.contains(docInfoProp)) {
                    Themis.print(docInfoProp + ": " + docInfo.getProperty(docInfoProp) + "\n");
                }
            }
            Themis.print("\n");
        }
    }
}
