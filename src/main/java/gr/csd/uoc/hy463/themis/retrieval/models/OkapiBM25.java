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
public class OkapiBM25 extends ARetrievalModel {
    private double k1 = 2.0;
    private double b = 0.75;

    public OkapiBM25(Indexer index) {
        super(index);
    }

    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props) throws IOException {
        return getRankedResults(query, props, 0, Integer.MAX_VALUE);
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException {
        List<String> terms = new ArrayList<>(query.size());
        List<Pair<Object, Double>> results = new ArrayList<>();
        int totalArticles = _indexer.getTotalArticles();
        double avgdl = _indexer.getAvgdl();

        //collect the terms
        query = removeDuplicateTerms(query);
        query.forEach(queryTerm -> terms.add(queryTerm.getTerm()));

        //get the relevant documents from the documents file
        fetchEssentialDocInfo(query, props, startDoc, endDoc);

        //compute the idf for each term of the query
        double[] idfs = new double[terms.size()];
        int[] dfs = _indexer.getDf(terms);
        for (int i = 0; i < terms.size(); i++) {
            idfs[i] = Math.log((totalArticles - dfs[i] + 0.5) / (dfs[i] + 0.5));
        }

        //frequencies of each term in each document
        Map<DocInfo, int[]> documentsFreqs = new HashMap<>();
        for (int i = 0; i < _termsDocInfo.size(); i++) {
            int[] termFreqs = _indexer.getFreq(terms.get(i));
            for (int j = 0; j < _termsDocInfo.get(i).size(); j++) {
                DocInfo docInfo = _termsDocInfo.get(i).get(j);
                int[] docFreqs = documentsFreqs.get(docInfo);
                if (docFreqs == null) {
                    docFreqs = new int[terms.size()];
                    documentsFreqs.put(docInfo, docFreqs);
                }
                docFreqs[i] = termFreqs[j];
            }
        }

        //calculate the scores
        double maxScore = 0;
        for (Map.Entry<DocInfo, int[]> docFreqs : documentsFreqs.entrySet()) {
            double documentScore = 0;
            int[] freqs = docFreqs.getValue();
            DocInfo docInfo = docFreqs.getKey();
            int docLength = (int) docInfo.getProperty(DocInfo.PROPERTY.LENGTH);
            for (int i = 0; i < terms.size(); i++) {
                documentScore += idfs[i] * freqs[i] * (k1 + 1) / (freqs[i] + k1 * (1 - b + (b * docLength) / avgdl));
            }
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
