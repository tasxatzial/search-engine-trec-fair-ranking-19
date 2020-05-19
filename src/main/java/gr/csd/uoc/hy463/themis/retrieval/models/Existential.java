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
import org.opencv.core.Mat;

import javax.print.Doc;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of the Existential retrieval model. Returns the documents that
 * contain any of the terms of the query. For this model, there is no ranking of
 * documents, since all documents that have at least one term of the query, are
 * relevant and have a score 1.0
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class Existential extends ARetrievalModel {
    private List<Pair<Object, Double>> _results;
    private Set<DocInfo.PROPERTY> _docInfoProps = new HashSet<>();
    private List<QueryTerm> _query = new ArrayList<>();
    private int _startDoc = 0;
    private int _endDoc = Integer.MAX_VALUE;

    public Existential(Indexer index) {
        super(index);
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps) throws IOException {
        boolean isSameQuery = hasSameQuery(query);
        boolean isSameProps = hasSameProps(docInfoProps);

        //return the previous results if everything is unchanged
        if (isSameQuery && isSameProps && _startDoc == 0) {
            if (_endDoc >= _results.size() - 1) {
                _endDoc = Integer.MAX_VALUE;
                return _results;
            }
        }

        //else if the query is the same, we only need to get the new document fields.
        if (isSameQuery) {
            List<DocInfo> docInfoList = new ArrayList<>();
            _results.forEach(result -> docInfoList.add((DocInfo) result.getL()));
            indexer.updateDocInfo(docInfoList, docInfoProps);
        }
        else { //if the query is not the same, just perform a new search
            _results = null;
            List<Pair<Object, Double>> results = new ArrayList<>();
            List<String> terms = new ArrayList<>(query.size());
            query.forEach(queryTerm -> terms.add(queryTerm.getTerm()));
            List<String> editedTerms = indexer.preprocessTerms(terms); //apply stemming, stopwords
            List<List<DocInfo>> termsDocInfo = indexer.getDocInfo(editedTerms, docInfoProps);
            for (List<DocInfo> termDocInfo : termsDocInfo) {
                for (DocInfo docInfo : termDocInfo) {
                    results.add(new Pair<>(docInfo, 1.0));
                }
            }
            _query = query;
            _results = results;
        }
        _docInfoProps = docInfoProps;
        _startDoc = 0;
        _endDoc = Integer.MAX_VALUE;

        return _results;
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps, int startDoc, int endDoc) throws IOException {
        boolean isSameQuery = hasSameQuery(query);
        boolean isSameProps = hasSameProps(docInfoProps);

        //return the previous results if everything is unchanged
        if (isSameQuery && isSameProps && startDoc == _startDoc) {
            if (endDoc >= _results.size() - 1) {
                _endDoc = endDoc;
                return _results;
            }
        }

        if (isSameQuery) {
            List<DocInfo> docInfoList = new ArrayList<>();
            for (int i = 0; i < startDoc; i++) {
                ((DocInfo) _results.get(i).getL()).clearProperties();
            }
            if (endDoc != Integer.MAX_VALUE) {
                for (int i = endDoc + 1; i < _results.size(); i++) {
                    ((DocInfo) _results.get(i).getL()).clearProperties();
                }
            }
            for (int i = startDoc; i <= Math.min(endDoc, _results.size() - 1); i++) {
                DocInfo docInfo = (DocInfo) _results.get(i).getL();
                docInfoList.add(docInfo);
            }
            indexer.updateDocInfo(docInfoList, docInfoProps);
        }
        else {
            _results = null;
            List<Pair<Object, Double>> results = new ArrayList<>();
            List<String> terms = new ArrayList<>(query.size());
            query.forEach(queryTerm -> terms.add(queryTerm.getTerm()));

            //apply stemming, stopwords
            List<String> editedTerms = indexer.preprocessTerms(terms);

            //get the all results but fetch only the docId
            List<List<DocInfo>> termsDocInfo = indexer.getDocInfo(editedTerms, new HashSet<>(0));

            //this is the list of docInfo from the above list that fall between startDoc, endDoc.
            List<DocInfo> docInfoList = new ArrayList<>();

            int i = 0;
            for (List<DocInfo> termDocInfo : termsDocInfo) {
                for (DocInfo docInfo : termDocInfo) {

                    //add every docInfo to the results list
                    results.add(new Pair<>(docInfo, 1.0));

                    //collect those docInfos that fall between startDoc and endDoc
                    if (i >= startDoc && i <= endDoc) {
                        docInfoList.add(docInfo);
                    }
                    i++;
                }
            }

            //now fetch the properties only for the above docInfo list
            indexer.updateDocInfo(docInfoList, docInfoProps);

            _query = query;
            _results = results;
        }
        _docInfoProps = docInfoProps;
        _startDoc = startDoc;
        _endDoc = endDoc;

        return _results;
    }

    /* Returns true only if the previous query is the same as the specified query, false otherwise */
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

    /* Returns true if the previous props is the same as the specified props, false otherwise */
    private boolean hasSameProps(Set<DocInfo.PROPERTY> docInfoProps) {
        if (docInfoProps.size() == _docInfoProps.size()) {
            Set<DocInfo.PROPERTY> props1 = new HashSet<>(docInfoProps);
            Set<DocInfo.PROPERTY> props2 = new HashSet<>(_docInfoProps);
            props1.removeAll(props2);
            props2.removeAll(props1);
            return props1.isEmpty() && props2.isEmpty();
        }
        else {
            return false;
        }
    }
}
