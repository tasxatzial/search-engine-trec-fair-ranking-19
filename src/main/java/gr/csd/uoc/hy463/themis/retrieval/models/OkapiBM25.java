package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.OKAPIprops;
import gr.csd.uoc.hy463.themis.retrieval.model.Posting;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of the Okapi retrieval model. BM25+ is used as the scoring function.
 */
public class OkapiBM25 extends ARetrievalModel {
    private double k1 = 2.0;
    private double b = 0.75;
    private double avgdl;
    double[] citationsPagerank;
    int[] tokenCount;
    double[] modelScore;
    double[][] calculatedFreqs;

    public OkapiBM25(Indexer index) throws IndexNotLoadedException {
        super(index);
        calculatedFreqs = new double[totalArticles][];
        citationsPagerank = new double[totalArticles];
        tokenCount = new int[totalArticles];
        modelScore = new double[totalArticles];
        avgdl = _indexer.getAvgdl();
    }

    @Override
    public List<Pair<DocInfo, Double>> getRankedResults(List<QueryTerm> query, int endResult) throws IOException, IndexNotLoadedException {
        List<Pair<DocInfo, Double>> results = new ArrayList<>();
        totalResults = 0;
        for (int i = 0; i < totalArticles; i++) {
            calculatedFreqs[i] = null;
            citationsPagerank[i] = 0;
            tokenCount[i] = 0;
            modelScore[i] = 0;
        }

        //merge weights of the same terms
        query = mergeTerms(query);

        //df of the terms of the query
        int[] dfs = _indexer.getDf(query);

        for(int i = 0; i < query.size(); i++) {
            Posting postings = _indexer.getPostings(query.get(i).getTerm());
            int[] intIDs = postings.getIntID();
            OKAPIprops props = _indexer.getOKAPIprops(intIDs);
            double[] i_citationsPagerank = props.getCitationsPagerank();
            int[] i_tokenCount = props.getTokenCount();
            int[] tfs = postings.getTfs();
            double weight = query.get(i).getWeight();

            //calculate the frequencies
            for (int j = 0; j < dfs[i]; j++) {
                int id = intIDs[j];
                citationsPagerank[id] = i_citationsPagerank[j];
                tokenCount[id] = i_tokenCount[j];
                double[] freqs = calculatedFreqs[id];
                if (freqs == null) {
                    freqs = new double[query.size()];
                    calculatedFreqs[id] = freqs;
                }
                freqs[i] = tfs[j] * weight;
            }
        }

        double[] idfs = new double[query.size()];
        for (int i = 0; i < idfs.length; i++) {
            idfs[i] = Math.log(totalArticles / (1.0 + dfs[i]));
        }

        //calculate the scores
        double maxScore = 0;
        for (int i = 0; i < calculatedFreqs.length; i++) {
            if (calculatedFreqs[i] == null) {
                continue;
            }
            double score = 0;
            double[] freqs = calculatedFreqs[i];
            for (int j = 0; j < query.size(); j++) {
                score += idfs[j] * (freqs[j] * (k1 + 1) / (freqs[j] + k1 * (1 - b + (b * tokenCount[i]) / avgdl)) + 1);
            }
            modelScore[i] = score;
            if (score > maxScore) {
                maxScore = score;
            }
        }

        if (Double.compare(maxScore, 0.0) == 0) {
            maxScore = 1;
        }

        //normalize to [0, 1]
        for (int i = 0; i < calculatedFreqs.length; i++) {
            if (calculatedFreqs[i] == null) {
                continue;
            }
            modelScore[i] /= maxScore;
            DocInfo docInfo = new DocInfo(i);
            results.add(new Pair<>(docInfo, modelScore[i]));
        }

        totalResults = results.size();

        //sort based on pagerank score and this model score
        return sort(results, citationsPagerank, endResult);
    }
}
