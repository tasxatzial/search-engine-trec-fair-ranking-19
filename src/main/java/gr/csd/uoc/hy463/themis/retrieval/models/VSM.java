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
 * Implementation of the OkapiBM25 retrieval model
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class VSM extends ARetrievalModel {

    public VSM(Indexer index) {
        super(index);
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps) throws IOException {
        return getRankedResults(query, docInfoProps,-1);
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> docInfoProps, int topk) throws IOException {
        List<String> terms = new ArrayList<>(query.size());
        List<Pair<Object, Double>> result = new ArrayList<>();
        List<List<DocInfo>> termsDocInfo;
        int totalArticles = indexer.getTotalArticles();

        //collect the terms
        query.forEach(queryTerm -> terms.add(queryTerm.getTerm()));

        //apply stemming, stopwords
        List<String> editedTerms = new ArrayList<>();
        for (int i = 0; i < query.size(); i++) {
            editedTerms = indexer.preprocessTerms(terms);
        }

        //get the docInfo objects associated with each term
        termsDocInfo = indexer.getDocInfo(editedTerms, docInfoProps);

        //frequencies of the terms in the query
        Map<String, Integer> queryFreqs = new HashMap<>(editedTerms.size());
        for (String term : editedTerms) {
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
        int[] dfs = indexer.getDf(editedTerms);

        //weights of terms in the query
        double[] queryWeights = new double[editedTerms.size()];
        for (int i = 0; i < editedTerms.size(); i++) {
            double tf = (0.0 + queryFreqs.get(editedTerms.get(i))) / queryMaxFreq;
            double idf = Math.log((0.0 + totalArticles) / dfs[i]) / Math.log(2);
            queryWeights[i] = tf * idf;
        }

        //norm of query
        double queryNorm = 0;
        for (int i = 0; i < editedTerms.size(); i++) {
            queryNorm += queryWeights[i] * queryWeights[i];
        }
        queryNorm = Math.sqrt(queryNorm);

        return result;
    }
}
