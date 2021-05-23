package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.VSMprops;
import gr.csd.uoc.hy463.themis.retrieval.model.Posting;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of the VSM retrieval model
 */
public class VSM extends ARetrievalModel {
    double[][] calculatedWeights;
    double[] documentWeights;
    double[] citationsPagerank;
    double[] modelScore;

    public VSM(Indexer index) throws IndexNotLoadedException {
        super(index);
        calculatedWeights = new double[totalArticles][];
        documentWeights = new double[totalArticles];
        citationsPagerank = new double[totalArticles];
        modelScore = new double[totalArticles];
    }

    @Override
    public List<Pair<DocInfo, Double>> getRankedResults(List<QueryTerm> query, int endResult) throws IOException, IndexNotLoadedException {
        List<DocInfo> results = new ArrayList<>();
        totalResults = 0;
        for (int i = 0; i < totalArticles; i++) {
            calculatedWeights[i] = null;
            documentWeights[i] = 0;
            citationsPagerank[i] = 0;
            modelScore[i] = 0;
        }

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

        for (int i = 0; i < query.size(); i++) {
            Posting postings = _indexer.getPostings(query.get(i).getTerm());
            int[] intIDs = postings.getIntID();
            VSMprops props = _indexer.getVSMprops(intIDs);
            double[] i_VSMWeight = props.getVSMweights();
            double[] i_citationsPagerank = props.getCitationsPagerank();
            int[] maxTfs = props.getMaxTfs();
            int[] tfs = postings.getTfs();
            double weight = query.get(i).getWeight();
            double idf = Math.log(totalArticles / (1.0 + dfs[i]));

            //calculate weights
            for (int j = 0; j < dfs[i]; j++) {
                int id = intIDs[j];
                documentWeights[id] = i_VSMWeight[j];
                citationsPagerank[id] = i_citationsPagerank[j];
                double[] weights = calculatedWeights[id];
                if (weights == null) {
                    weights = new double[query.size()];
                    calculatedWeights[id] = weights;
                }
                double tf = (tfs[j] * weight) / maxTfs[j];
                weights[i] += tf * idf;
            }
        }

        //calculate the scores
        double maxScore = 0;
        for (int i = 0; i < calculatedWeights.length; i++) {
            if (calculatedWeights[i] == null) {
                continue;
            }
            double[] weights = calculatedWeights[i];
            double score = 0;
            for (int j = 0; j < queryWeights.length; j++) {
                score += queryWeights[j] * weights[j];
            }
            modelScore[i] = score / (documentWeights[i] * queryNorm);
            if (modelScore[i] > maxScore) {
                maxScore = modelScore[i];
            }
            DocInfo docInfo = new DocInfo(i);
            results.add(docInfo);
        }

        //normalize to [0, 1]
        for (int i = 0; i < modelScore.length; i++) {
            modelScore[i] /= maxScore;
        }

        totalResults = results.size();

        //sort based on pagerank score and this model score
        return sort(results, citationsPagerank, modelScore, endResult);
    }
}
