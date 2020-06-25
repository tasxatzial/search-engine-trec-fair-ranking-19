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
    }

    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props) throws IOException {
        return getRankedResults(query, props, 0, Integer.MAX_VALUE);
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException {
        List<Pair<Object, Double>> results = new ArrayList<>();
        int totalArticles = _indexer.getTotalArticles();

        //frequencies of the terms in the query
        Map<String, Double> queryFrequencies = new HashMap<>(query.size());
        for (QueryTerm queryTerm : query) {
            queryFrequencies.merge(queryTerm.getTerm(), queryTerm.getWeight(), Double::sum);
        }

        //max frequency of the query terms
        double queryMaxFrequency = 0;
        for (double frequency : queryFrequencies.values()) {
            if (frequency > queryMaxFrequency) {
                queryMaxFrequency = frequency;
            }
        }

        //merge weights of the same terms
        query = mergeTerms(query);

        //get the relevant documents from the documents file
        fetchEssentialDocInfo(query, props, startDoc, endDoc);

        //df of the terms of the query
        int[] dfs = _indexer.getDf(query);

        //weights of terms in the query
        double[] queryWeights = new double[query.size()];
        for (int i = 0; i < query.size(); i++) {
            double tf = query.get(i).getWeight() / queryMaxFrequency;
            double idf = Math.log(totalArticles / (1.0 + dfs[i]));
            queryWeights[i] = tf * idf;
        }

        //norm of query
        double queryNorm = 0;
        for (int i = 0; i < query.size(); i++) {
            queryNorm += queryWeights[i] * queryWeights[i];
        }
        queryNorm = Math.sqrt(queryNorm);

        //weights of terms for each document
        Map<DocInfo, double[]> documentsWeights = new HashMap<>();
        for (int i = 0; i < _termsDocInfo.size(); i++) {
            int[] freqs = _indexer.getFreq(query.get(i).getTerm());
            double weight = query.get(i).getWeight();
            double idf = Math.log(totalArticles / (1.0 + dfs[i]));
            for (int j = 0; j < _termsDocInfo.get(i).size(); j++) {
                DocInfo docInfo = _termsDocInfo.get(i).get(j);
                double[] weights = documentsWeights.get(docInfo);
                double tf = (freqs[j] * weight) / (int) docInfo.getProperty(DocInfo.PROPERTY.MAX_TF);
                if (weights == null) {
                    weights = new double[query.size()];
                    documentsWeights.put(docInfo, weights);
                }
                weights[i] += tf * idf;
            }
        }

        //calculate the scores
        double maxScore = 0;
        for (Map.Entry<DocInfo, double[]> docWeights : documentsWeights.entrySet()) {
            double documentScore = 0;
            double[] weights = docWeights.getValue();
            DocInfo docInfo = docWeights.getKey();
            double documentNorm = (double) docInfo.getProperty(DocInfo.PROPERTY.WEIGHT);
            for (int i = 0; i < queryWeights.length; i++) {
                documentScore += queryWeights[i] * weights[i];
            }
            documentScore /= (documentNorm * queryNorm);
            if (documentScore > maxScore) {
                maxScore = documentScore;
            }
            results.add(new Pair<>(docInfo, documentScore));
        }

        //normalize to [0, 1]
        for (Pair<Object, Double> result : results) {
            result.setR(result.getR() / maxScore);
        }

        //sort based on pagerank score and this model score
        sort(results);

        //update the properties of these results that are in [startDoc, endDoc]
        updateDocInfo(results, props, startDoc, endDoc);

        return results;
    }
}
