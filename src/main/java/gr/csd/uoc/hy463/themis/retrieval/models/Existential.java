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
 * Implementation of the Existential retrieval model. Returns the documents that
 * contain any of the terms of the query. For this model, there is no ranking of
 * documents, since all documents that have at least one term of the query, are
 * relevant and have a score 1.0
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class Existential extends ARetrievalModel {
    private List<QueryTerm> _query = new ArrayList<>();
    private List<Pair<Object, Double>> _results;

    public Existential(Indexer index) {
        super(index);
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps) throws IOException {
        boolean hasSameQuery = true;

        //check if we have the same query second time in a row
        if (query.size() == _query.size()) {
            for (int i = 0; i < query.size(); i++) {
                if (!query.get(i).getTerm().equals(_query.get(i).getTerm())) {
                    hasSameQuery = false;
                    break;
                }
            }
        }
        else {
            hasSameQuery = false;
        }

        //if the query is the same, we only need to get the new document fields.
        if (hasSameQuery) {
            List<DocInfo> docInfoList = new ArrayList<>();
            _results.forEach(result -> docInfoList.add((DocInfo) result.getL()));
            indexer.updateDocInfo(docInfoList, docInfoProps);
        }
        else { //if the query is not the same, just perform a new search
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

        return _results;
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps, int startDoc, int endDoc) throws IOException {
        List<Pair<Object, Double>> result = new ArrayList<>();
        List<String> terms = new ArrayList<>(query.size());
        query.forEach(queryTerm -> terms.add(queryTerm.getTerm()));

        //apply stemming, stopwords
        List<String> editedTerms = indexer.preprocessTerms(terms);

        List<List<DocInfo>> termsDocInfo = indexer.getDocInfo(editedTerms, docInfoProps);
        for (List<DocInfo> termDocInfo : termsDocInfo) {
            for (DocInfo docInfo : termDocInfo) {
                result.add(new Pair<>(docInfo, 1.0));
                if (result.size() == startDoc) {
                    return result;
                }
            }
        }

        return result;
    }
}
