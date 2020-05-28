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
    protected List<Map<Long, DocInfo>> _docMap;
    protected List<String> _terms;
    protected Indexer _indexer;
    protected Set<DocInfo.PROPERTY> _essentialProps;

    public ARetrievalModel(Indexer indexer) {
        _indexer = indexer;
        _terms = new ArrayList<>();
        _termsDocInfo = new ArrayList<>();
        _docMap = new ArrayList<>();
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
     * the ranked results. In that list the properties specified in props are retrieved only for the
     * documents with indexes from startDoc to endDoc.
     *
     * startDoc and endDoc range is from 0 (top ranked doc) to Integer.MAX_VALUE.
     * endDoc should be set to Integer.MAX_VALUE if we want to retrieve all the documents related to this query
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
     * @param props set of properties we want to be retrieved from the documents
     * @return
     */
    public abstract List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException;

    /**
     * Creates a list of list of docInfo objects using the documents file (one list for each term of the query).
     * The protected members _termsDocInfo and _docMap are updated when the function returns
     * @param query
     * @throws IOException
     */
    protected void fetchEssentialDocInfo(List<QueryTerm> query) throws IOException {

        /* collect all terms */
        List<String> terms = new ArrayList<>(query.size());
        for (QueryTerm term : query) {
            terms.add(term.getTerm());
        }

        /* initialize */
        List<List<DocInfo>> termsDocInfo = new ArrayList<>(terms.size());
        List<Map<Long, DocInfo>> docMap = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) {
            termsDocInfo.add(new ArrayList<>());
            docMap.add(new HashMap<>());
        }

        /* check whether this query has same terms as the previous query */
        for (int i = 0; i < terms.size(); i++) {
            for (int j = 0; j < _terms.size(); j++) {
                if (terms.get(i).equals(_terms.get(j))) {
                    termsDocInfo.set(i, _termsDocInfo.get(j));
                    docMap.set(i, _docMap.get(j));
                    break;
                }
            }
        }

        /* to decrease memory usage, for all the terms of the previous query that do not belong to this query,
        clear the corresponding structures */
        for (int i = 0; i < _termsDocInfo.size(); i++) {
            boolean found = false;
            for (int j = 0; j < termsDocInfo.size(); j++) {
                if (_termsDocInfo.get(i) == termsDocInfo.get(j)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                _termsDocInfo.get(i).clear();
                _docMap.get(i).clear();
            }
        }

        /* finally, fetch the essential properties so that the model can do the ranking */
        _indexer.updateDocInfo(terms, termsDocInfo, docMap, _essentialProps);
        _termsDocInfo = termsDocInfo;
        _docMap = docMap;
        _terms = terms;
    }

    /**
     * Updates the ranked results that have index in [startDoc, endDoc] by fetching the specified props
     * from the documents file
     * @param results
     * @param props
     * @param startDoc
     * @param endDoc
     * @throws IOException
     */
    protected void updateDocInfo(List<Pair<Object, Double>> results, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException {
        Set<DocInfo.PROPERTY> nonEssentialProps = new HashSet<>(props);
        Set<DocInfo.PROPERTY> finalProps = new HashSet<>(props);

        //these are the all the properties that should be in the final docInfo objects of the results
        finalProps.addAll(_essentialProps);

        //these are all the properties that will be removed from the all the docInfo objects of the results
        //that are in [startDoc, endDoc]
        nonEssentialProps.removeAll(_essentialProps);

        List<DocInfo> updatedDocInfos = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            if (i >= startDoc && i <= endDoc) {

                //all docInfo in [startDoc, endDoc] will have their properties updated
                updatedDocInfos.add((DocInfo) results.get(i).getL());
            }
            else {

                //for the rest of them just remove the propeties that we no longer want
                ((DocInfo) results.get(i).getL()).clearProperties(nonEssentialProps);
            }
        }
        _indexer.updateDocInfo(updatedDocInfos, finalProps);
    }
}
