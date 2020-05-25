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
    protected List<QueryTerm> _query;
    protected Indexer _indexer;
    protected Set<DocInfo.PROPERTY> _essentialProps;

    public ARetrievalModel(Indexer indexer) {
        _indexer = indexer;
        _query = new ArrayList<>();
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

        /* new props are all props specified by the user except the essential props */
        Set<DocInfo.PROPERTY> newProps = new HashSet<>(docInfoProps);
        newProps.removeAll(_essentialProps);

        /* for same queries, keep only the essential props in the results that fall outside [startDoc, endDoc] */
        if (hasSameQuery(query)) {
            for (int i = 0; i < startDoc; i++) {
                ((DocInfo) _results.get(i).getL()).clearProperties(newProps);
            }
            if (endDoc != Integer.MAX_VALUE) {
                for (int i = endDoc + 1; i < _results.size(); i++) {
                    ((DocInfo) _results.get(i).getL()).clearProperties(newProps);
                }
            }
        }

        /* for different queries, retrieve the results using only the essential props */
        else {
            _results = getRankedResults_internal(query, _essentialProps);
            _query = query;
        }

        /* finally update the results that fall between [startDoc, endDoc] with the new props */
        List<DocInfo> docInfoList = new ArrayList<>();
        for (int i = startDoc; i <= Math.min(_results.size() - 1, endDoc); i++) {
            docInfoList.add(((DocInfo) _results.get(i).getL()));
        }
        _indexer.updateDocInfo(docInfoList, newProps);

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
}
