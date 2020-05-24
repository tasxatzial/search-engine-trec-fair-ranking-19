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
package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This is an abstract class that each retrieval model should extend
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public abstract class ARetrievalModel {
    public enum MODEL {
        BM25, VSM, EXISTENTIAL
    }
    protected List<Pair<Object, Double>> _results;
    protected Set<DocInfo.PROPERTY> _docInfoProps;
    protected List<QueryTerm> _query;
    protected int _startDoc;
    protected int _endDoc;
    protected Indexer _indexer;
    protected Set<DocInfo.PROPERTY> _essentialProps;

    public ARetrievalModel(Indexer indexer) {
        _indexer = indexer;
        _docInfoProps = new HashSet<>();
        _query = new ArrayList<>();
        _startDoc = 0;
        _endDoc = Integer.MAX_VALUE;
    }

    /* This method is implemented by all models and returns a ranked list of pair of the relevant documents.
    * The method is called by the public getRankedResults() function */
    protected abstract List<Pair<Object, Double>> getRankedResults_internal(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps) throws IOException;

    /**
     * Method that evaluates the query and returns a ranked list of pairs of the
     * whole relevant documents.
     *
     * The double is the score of the document as returned by the corresponding
     * retrieval model.
     *
     * The list must be in descending order according to the score
     *
     * @param query set of query terms
     * @param docInfoProps set of properties we want to be retrieved from the documents
     * @return
     */
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps) throws IOException {
        return getRankedResults(query, docInfoProps, 0, Integer.MAX_VALUE);
    }

    /**
     * Method that evaluates the query and returns a list of pairs with
     * the ranked results. In that list the properties specified in docInfoProps are retrieved only for the
     * documents with indexes from startDoc to endDoc.
     *
     * startDoc and endDoc range is from 0 (top ranked doc) to Integer.MAX_VALUE.
     * endDoc should be set to Integer.MAX_VALUE if we want to retrieve the properties of all the documents
     * but we don't know the actual number of the retrieved documents
     *
     * There are various policies to be faster when doing this if we do not want
     * to compute the scores of all queries.
     *
     * For example by sorting the terms of the query based on some indicator of
     * goodness and process the terms in this order (e.g., cutoff based on
     * document frequency, cutoff based on maximum estimated weight, and cutoff
     * based on the weight of a disk page in the posting list
     *
     * The double is the score of the document as returned by the corresponding
     * retrieval model.
     *
     * The list must be in descending order according to the score
     *
     * @param query list of query terms
     * @param docInfoProps set of properties we want to be retrieved from the documents
     * @return
     */
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps, int startDoc, int endDoc) throws IOException {
        boolean isSameQuery = hasSameQuery(query);
        boolean isSameProps = hasSameProps(docInfoProps);

        /* return the previous results if everything is unchanged */
        if (isSameQuery && isSameProps && startDoc == _startDoc) {
            if (endDoc >= _results.size() - 1) {
                _endDoc = endDoc;
                return _results;
            }
        }

        if (isSameQuery) { /* same query, different props */
            List<DocInfo> docInfoList = new ArrayList<>();

            /* since we are not going to display all relevant documents, we need to to clear the properties of
            the corresponding docInfo items that will not be displayed */
            Set<DocInfo.PROPERTY> removeProps = new HashSet<>(docInfoProps);
            for (DocInfo.PROPERTY prop : _essentialProps) {
                removeProps.remove(prop);
            }
            for (int i = 0; i < startDoc; i++) {
                ((DocInfo) _results.get(i).getL()).clearProperties(removeProps);
            }
            if (endDoc != Integer.MAX_VALUE) {
                for (int i = endDoc + 1; i < _results.size(); i++) {
                    ((DocInfo) _results.get(i).getL()).clearProperties(removeProps);
                }
            }
            for (int i = startDoc; i <= Math.min(endDoc, _results.size() - 1); i++) {
                DocInfo docInfo = (DocInfo) _results.get(i).getL();
                docInfoList.add(docInfo);
            }

            /* update the docInfo items with the new props */
            _indexer.updateDocInfo(docInfoList, docInfoProps);
        }
        else { /* different query */
            _results = null;

            /* initially fetch only the essential props from each document. Those props will be used
            by the retrieval model */
            List<Pair<Object, Double>> results = getRankedResults_internal(query, _essentialProps);

            /* collect all docInfo objects that correspond to the documents that will be displayed */
            List<DocInfo> docInfoList = new ArrayList<>();
            int i = 0;
            for (Pair<Object, Double> result : results) {
                if (i >= startDoc && i <= endDoc) {
                    docInfoList.add((DocInfo) result.getL());
                }
                i++;
            }

            /* fetch the properties only for the above docInfo list */
            _indexer.updateDocInfo(docInfoList, docInfoProps);

            _query = query;
            _results = results;
        }
        _docInfoProps = docInfoProps;
        _startDoc = startDoc;
        _endDoc = endDoc;

        return _results;
    }

    /**
     * Returns true only if the previous query is the same as the new query, false otherwise
     * @param query
     * @return
     */
    private boolean hasSameQuery(List<QueryTerm> query) {
        if (query.size() == _query.size()) {
            for (int i = 0; i < query.size(); i++) {
                if (!query.get(i).getTerm().equals(_query.get(i).getTerm())) {
                    return false;
                }
            }
        }
        else {
            return false;
        }
        return true;
    }

    /**
     * Returns true if the previous props is the same as the new props, false otherwise
     * @param docInfoProps
     * @return
     */
    private boolean hasSameProps(Set<DocInfo.PROPERTY> docInfoProps) {
        if (docInfoProps.size() == _docInfoProps.size()) {
            Set<DocInfo.PROPERTY> props1 = new HashSet<>(docInfoProps);
            Set<DocInfo.PROPERTY> props2 = new HashSet<>(_docInfoProps);
            props1.removeAll(_docInfoProps);
            props2.removeAll(docInfoProps);
            return props1.isEmpty() && props2.isEmpty();
        }
        else {
            return false;
        }
    }
}
