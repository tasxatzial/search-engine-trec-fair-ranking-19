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

import javax.print.Doc;
import java.io.IOException;
import java.util.*;

/**
 * Implementation of the OkapiBM25 retrieval model
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class OkapiBM25 extends ARetrievalModel {
    private double k1 = 2.0;
    private double b = 0.75;

    public OkapiBM25(Indexer index) {
        super(index);
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps) throws IOException {
        boolean isSameQuery = hasSameQuery(_query, query);
        boolean isSameProps = hasSameProps(_docInfoProps, docInfoProps);

        //return the previous results if everything is unchanged
        if (isSameQuery && isSameProps && _startDoc == 0) {
            if (_endDoc >= _results.size() - 1) {
                _endDoc = Integer.MAX_VALUE;
                return _results;
            }
        }

        if (isSameQuery) {
            List<DocInfo> docInfoList = new ArrayList<>();
            _results.forEach(result -> docInfoList.add((DocInfo) result.getL()));
            indexer.updateDocInfo(docInfoList, docInfoProps);
        } else {
            _results = null;
            List<Pair<Object, Double>> results = getRankedResults_private(query, docInfoProps);
            _query = query;
            _results = results;
        }
        _docInfoProps = docInfoProps;
        _startDoc = 0;
        _endDoc = Integer.MAX_VALUE;

        return _results;
    }

    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps, int startDoc, int endDoc) throws IOException {
        boolean isSameQuery = hasSameQuery(_query, query);
        boolean isSameProps = hasSameProps(_docInfoProps, docInfoProps);

        //return the previous results if everything is unchanged
        if (isSameQuery && isSameProps && startDoc == _startDoc) {
            if (endDoc >= _results.size() - 1) {
                _endDoc = endDoc;
                return _results;
            }
        }

        if (isSameQuery) {
            List<DocInfo> docInfoList = new ArrayList<>();
            Set<DocInfo.PROPERTY> props = new HashSet<>(docInfoProps);
            if (docInfoProps.contains(DocInfo.PROPERTY.LENGTH)) {
                props.remove(DocInfo.PROPERTY.LENGTH);
            }
            for (int i = 0; i < startDoc; i++) {
                ((DocInfo) _results.get(i).getL()).clearProperties(props);
            }
            if (endDoc != Integer.MAX_VALUE) {
                for (int i = endDoc + 1; i < _results.size(); i++) {
                    ((DocInfo) _results.get(i).getL()).clearProperties(props);
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
            Set<DocInfo.PROPERTY> props = new HashSet<>();
            props.add(DocInfo.PROPERTY.LENGTH);
            List<Pair<Object, Double>> results = getRankedResults_private(query, props);

            //this is the list of docInfo from the above list that fall between startDoc, endDoc.
            List<DocInfo> docInfoList = new ArrayList<>();

            int i = 0;
            for (Pair<Object, Double> result : results) {
                if (i >= startDoc && i <= endDoc) {
                    docInfoList.add((DocInfo) result.getL());
                }
                i++;
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

    private List<Pair<Object, Double>> getRankedResults_private(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps) throws IOException {
        List<String> terms = new ArrayList<>(query.size());
        List<Pair<Object, Double>> results = new ArrayList<>();
        List<List<DocInfo>> termsDocInfo;
        int totalArticles = indexer.getTotalArticles();
        double avgdl = indexer.getAvgdl();

        //collect the terms
        query.forEach(queryTerm -> terms.add(queryTerm.getTerm()));

        //apply stemming, stopwords
        List<String> editedTerms = indexer.preprocessTerms(terms);

        //get the docInfo objects associated with each term
        termsDocInfo = indexer.getDocInfo(editedTerms, docInfoProps);

        //compute the idf for each term of the query
        double[] idfs = new double[editedTerms.size()];
        int[] dfs = indexer.getDf(editedTerms);
        for (int i = 0; i < editedTerms.size(); i++) {
            idfs[i] = Math.log((totalArticles - dfs[i] + 0.5) / (dfs[i] + 0.5));
        }

        //frequencies of each term in each document
        Map<DocInfo, int[]> documentsFreqs = new HashMap<>();
        for (int i = 0; i < termsDocInfo.size(); i++) {
            int[] termFreqs = indexer.getFreq(editedTerms.get(i));
            for (int j = 0; j < termsDocInfo.get(i).size(); j++) {
                DocInfo docInfo = termsDocInfo.get(i).get(j);
                int[] docFreqs = documentsFreqs.get(docInfo);
                if (docFreqs == null) {
                    docFreqs = new int[editedTerms.size()];
                    documentsFreqs.put(docInfo, docFreqs);
                }
                docFreqs[i] = termFreqs[j];
            }
        }
        
        //calculate the scores
        for (Map.Entry<DocInfo, int[]> docFreqs : documentsFreqs.entrySet()) {
            double documentScore = 0;
            int[] freqs = docFreqs.getValue();
            DocInfo docInfo = docFreqs.getKey();
            int docLength = (int) docInfo.getProperty(DocInfo.PROPERTY.LENGTH);
            for (int i = 0; i < editedTerms.size(); i++) {
                documentScore += idfs[i] * freqs[i] * (k1 + 1) / (freqs[i] + k1 * (1 - b + (b * docLength) / avgdl));
            }
            results.add(new Pair<>(docInfo, documentScore));
        }
        results.sort((o1, o2) -> o2.getR().compareTo(o1.getR()));

        return results;
    }
}
