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
 * Implementation of the VSM retrieval model
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class VSM extends ARetrievalModel {
    public VSM(Indexer index) {
        super(index);

        /* These are the props that are required so that the model knows how to rank */
        _essentialProps = new HashSet<>(2);
        _essentialProps.add(DocInfo.PROPERTY.WEIGHT);
        _essentialProps.add(DocInfo.PROPERTY.MAX_TF);
    }

    /* Returns a ranked list of pairs of the relevant documents */
    protected List<Pair<Object, Double>> getRankedResults_internal(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps) throws IOException {
        List<Pair<Object, Double>> results = new ArrayList<>();
        List<String> terms = new ArrayList<>(query.size());
        List<List<DocInfo>> termsDocInfo;
        int totalArticles = _indexer.getTotalArticles();

        //collect the terms
        query.forEach(queryTerm -> terms.add(queryTerm.getTerm()));

        //get the docInfo objects associated with each term
        termsDocInfo = _indexer.getDocInfo(terms, docInfoProps);

        //frequencies of the terms in the query
        Map<String, Integer> queryFreqs = new HashMap<>(terms.size());
        for (String term : terms) {
            queryFreqs.merge(term, 1, Integer::sum);
        }

        //max frequency of the query terms
        int queryMaxFreq = 0;
        for (Integer freq : queryFreqs.values()) {
            if (freq > queryMaxFreq) {
                queryMaxFreq = freq;
            }
        }

        //df of the terms
        int[] dfs = _indexer.getDf(terms);

        //weights of terms in the query
        double[] queryWeights = new double[terms.size()];
        for (int i = 0; i < terms.size(); i++) {
            double tf = (0.0 + queryFreqs.get(terms.get(i))) / queryMaxFreq;
            double idf = Math.log((0.0 + totalArticles) / (1 + dfs[i])) / Math.log(2);
            queryWeights[i] = tf * idf;
        }

        //norm of query
        double queryNorm = 0;
        for (int i = 0; i < terms.size(); i++) {
            queryNorm += queryWeights[i] * queryWeights[i];
        }
        queryNorm = Math.sqrt(queryNorm);

        //weights of terms for each document
        Map<DocInfo, double[]> documentsWeights = new HashMap<>();
        for (int i = 0; i < termsDocInfo.size(); i++) {
            int[] freqs = _indexer.getFreq(terms.get(i));
            double idf = Math.log((0.0 + totalArticles) / (1 + dfs[i])) / Math.log(2);
            for (int j = 0; j < termsDocInfo.get(i).size(); j++) {
                DocInfo docInfo = termsDocInfo.get(i).get(j);
                double[] weights = documentsWeights.get(docInfo);
                double tf = (0.0 + freqs[j]) / (int) docInfo.getProperty(DocInfo.PROPERTY.MAX_TF);
                if (weights == null) {
                    weights = new double[terms.size()];
                    documentsWeights.put(docInfo, weights);
                }
                weights[i] += tf * idf;
            }
        }

        //calculate the scores
        for (Map.Entry<DocInfo, double[]> docWeights : documentsWeights.entrySet()) {
            double documentScore = 0;
            double[] weights = docWeights.getValue();
            DocInfo docInfo = docWeights.getKey();
            double documentNorm = (double) docInfo.getProperty(DocInfo.PROPERTY.WEIGHT);
            for (int i = 0; i < queryWeights.length; i++) {
                documentScore += queryWeights[i] * weights[i];
            }
            documentScore /= (documentNorm * queryNorm);
            results.add(new Pair<>(docInfo, documentScore));
        }
        results.sort((o1, o2) -> o2.getR().compareTo(o1.getR()));

        return results;
    }
}
