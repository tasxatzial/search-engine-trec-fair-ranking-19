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
    protected List<Pair<Object, Double>> _results;
    protected Set<DocInfo.PROPERTY> _docInfoProps = new HashSet<>();
    protected List<QueryTerm> _query = new ArrayList<>();
    protected int _startDoc = 0;
    protected int _endDoc = Integer.MAX_VALUE;
    protected Indexer indexer;

    public ARetrievalModel(Indexer indexer) {
        this.indexer = indexer;
    }

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
    public abstract List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps) throws IOException;

    /**
     * Method that evaluates the query and returns a list of pairs with
     * the ranked results. In that list the properties specified in docInfoProps are retrieved only for the
     * documents with indexes from startDoc to endDoc.
     *
     * startDoc and endDoc range is from 0 (top ranked doc) to Integer.MAX_VALUE.
     * endDoc should be set to Integer.MAX_VALUE if we want to retrieve the properties of all the documents.
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
    public abstract List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps, int startDoc, int endDoc) throws IOException;

    /**
     * Returns true only if the previous query is the same as the new query, false otherwise
     * @param queryPrev
     * @param queryNew
     * @return
     */
    public boolean hasSameQuery(List<QueryTerm> queryPrev, List<QueryTerm> queryNew) {
        if (queryPrev.size() == queryNew.size()) {
            for (int i = 0; i < queryPrev.size(); i++) {
                if (!queryPrev.get(i).getTerm().equals(queryNew.get(i).getTerm())) {
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
     * @param docInfoPropsPrev
     * @param docInfoPropsNew
     * @return
     */
    public boolean hasSameProps(Set<DocInfo.PROPERTY> docInfoPropsPrev, Set<DocInfo.PROPERTY> docInfoPropsNew) {
        if (docInfoPropsPrev.size() == docInfoPropsNew.size()) {
            Set<DocInfo.PROPERTY> props1 = new HashSet<>(docInfoPropsPrev);
            Set<DocInfo.PROPERTY> props2 = new HashSet<>(docInfoPropsNew);
            props1.removeAll(props2);
            props2.removeAll(props1);
            return props1.isEmpty() && props2.isEmpty();
        }
        else {
            return false;
        }
    }

    // We should also add some kind of paging and caching... but maybe in the future
}
