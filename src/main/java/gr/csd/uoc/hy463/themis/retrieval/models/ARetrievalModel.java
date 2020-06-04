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
import java.util.*;

/**
 * This is an abstract class that each retrieval model should extend
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public abstract class ARetrievalModel {
    public enum MODEL {
        BM25, VSM, EXISTENTIAL
    }

    protected List<List<DocInfo>> _termsDocInfo;
    protected List<String> _terms;
    protected Indexer _indexer;

    public ARetrievalModel(Indexer indexer) {
        _indexer = indexer;
        _terms = new ArrayList<>();
        _termsDocInfo = new ArrayList<>();
    }

    public static Set<DocInfo.PROPERTY> getVSMProps() {
        Set<DocInfo.PROPERTY> props = new HashSet<>();
        props.add(DocInfo.PROPERTY.WEIGHT);
        props.add(DocInfo.PROPERTY.MAX_TF);
        return props;
    }

    public static Set<DocInfo.PROPERTY> getOkapiProps() {
        Set<DocInfo.PROPERTY> props = new HashSet<>();
        props.add(DocInfo.PROPERTY.LENGTH);
        return props;
    }

    public static Set<DocInfo.PROPERTY> getMonModelProps() {
        Set<DocInfo.PROPERTY> props = new HashSet<>();
        props.add(DocInfo.PROPERTY.TITLE);
        props.add(DocInfo.PROPERTY.AUTHORS_NAMES);
        props.add(DocInfo.PROPERTY.JOURNAL_NAME);
        props.add(DocInfo.PROPERTY.AUTHORS_IDS);
        props.add(DocInfo.PROPERTY.YEAR);
        props.add(DocInfo.PROPERTY.PAGERANK);
        props.add(DocInfo.PROPERTY.AVG_AUTHOR_RANK);
        return props;
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
     * @return
     */
    public abstract List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props) throws IOException;

    /**
     * Method that evaluates the query and returns a list of pairs with
     * the ranked results. In that list the properties specified in props are retrieved only for the
     * documents with indexes from startDoc to endDoc.
     *
     * startDoc and endDoc range is from 0 (top ranked doc) to Integer.MAX_VALUE.
     * startDoc should be set to 0 and endDoc should be set to Integer.MAX_VALUE if we want to retrieve all
     * documents related to this query. Using different values for any of them is encouraged only when we want
     * to see a small number of results.
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
     * @return
     */
    public abstract List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException;

    /**
     * Reads the documents file and creates a list of list of docInfo objects (one list for each term of the query).
     * The list will have been updated when the function returns.
     * @param query
     * @param props
     * @param startDoc
     * @param endDoc
     * @throws IOException
     */
    protected void fetchEssentialDocInfo(List<QueryTerm> query, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException {

        /* collect all terms */
        List<String> terms = new ArrayList<>(query.size());
        for (QueryTerm queryTerm : query) {
            terms.add(queryTerm.getTerm());
        }

        /* initialize structures */
        List<List<DocInfo>> termsDocInfo = new ArrayList<>(terms.size());
        for (int i = 0; i < terms.size(); i++) {
            termsDocInfo.add(new ArrayList<>());
        }

        /* check whether this query has same terms as the previous query */
        for (int i = 0; i < terms.size(); i++) {
            for (int j = 0; j < _terms.size(); j++) {
                if (terms.get(i).equals(_terms.get(j))) {
                    termsDocInfo.set(i, _termsDocInfo.get(j));
                    break;
                }
            }
        }

        /* for all the terms of the previous query that do not belong to this query, clear the corresponding structures */
        for (int i = 0; i < _termsDocInfo.size(); i++) {
            boolean found = false;
            for (List<DocInfo> docInfos : termsDocInfo) {
                if (_termsDocInfo.get(i) == docInfos) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                _termsDocInfo.get(i).clear();
            }
        }

        /* if we want paginated results, both the okapi and VSM models should fetch only their
        essential properties for each result so that they can do the ranking. At this point none of those
        models should retrieve any other property: There is no point using more memory to store
        any properties that the user does not want to see */
        Set<DocInfo.PROPERTY> newProps = new HashSet<>(props);
        if (startDoc == 0 && endDoc == Integer.MAX_VALUE) {
            if (this instanceof VSM) {
                newProps.addAll(getVSMProps());
            }
            else if (this instanceof OkapiBM25) {
                newProps.addAll(getOkapiProps());
            }
        }
        else {
            if (this instanceof VSM) {
                newProps = getVSMProps();
            }
            else if (this instanceof OkapiBM25) {
                newProps = getOkapiProps();
            }
        }

        /* finally fetch the properties */
        _indexer.getDocInfo(terms, termsDocInfo, newProps);

        _termsDocInfo = termsDocInfo;
        _terms = terms;
    }

    /**
     * Updates the ranked results that have index in [startDoc, endDoc] by fetching the specified props
     * from the documents file
     * @param results
     * @param startDoc
     * @param endDoc
     * @throws IOException
     */
    protected void updateDocInfo(List<Pair<Object, Double>> results, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException {

        /* add to the specified props the essential properties of each model */
        Set<DocInfo.PROPERTY> newProps = new HashSet<>(props);
        if (this instanceof VSM) {
            newProps.addAll(getVSMProps());
        }
        else if (this instanceof OkapiBM25) {
            newProps.addAll(getOkapiProps());
        }

        /* find all the properties that should be removed but keep the essential properties of each model */
        Set<DocInfo.PROPERTY> removeProps = new HashSet<>();
        removeProps.addAll(getVSMProps());
        removeProps.addAll(getMonModelProps());
        removeProps.addAll(getOkapiProps());
        if (this instanceof VSM) {
            removeProps.removeAll(getVSMProps());
        }
        else if (this instanceof OkapiBM25) {
            removeProps.removeAll(getOkapiProps());
        }

        /* update all docInfo items of the results accordingly */
        List<DocInfo> updatedDocInfos = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            DocInfo docInfo = (DocInfo) results.get(i).getL();

            /* clear the properties of the results not in [startDoc, endDoc] */
            if (i < startDoc || i > endDoc) {
                if (!removeProps.isEmpty()) {
                    docInfo.clearProperties(removeProps);
                }
            }
            else {

                /* clear the properties of the results in [startDoc, endDoc] */
                for (DocInfo.PROPERTY prop : docInfo.getProps()) {
                    if (!newProps.contains(prop)) {
                        docInfo.clearProperty(prop);
                    }
                }

                /* and grab any new properties */
                updatedDocInfos.add(docInfo);
            }
        }
        _indexer.updateDocInfo(updatedDocInfos, newProps);
    }

    /**
     * Returns a new query with the duplicate terms removed
     * @param query
     * @return
     */
    protected List<QueryTerm> removeDuplicateTerms(List<QueryTerm> query) {
        List<QueryTerm> newQuery = new ArrayList<>();
        for (int i = 0; i < query.size(); i++) {
            boolean found = false;
            for (int j = 0; j < i; j++) {
                if (query.get(j).getTerm().equals(query.get(i).getTerm())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                newQuery.add(query.get(i));
            }
        }
        return newQuery;
    }
}
