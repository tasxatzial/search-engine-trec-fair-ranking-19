package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;
import gr.csd.uoc.hy463.themis.retrieval.model.VSMprops;
import gr.csd.uoc.hy463.themis.retrieval.model.Postings;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of the VSM retrieval model
 */
public class VSM extends ARetrievalModel {
    double[][] _calculatedWeights;
    double[] _documentWeights;
    double[] _modelScore;
    int[] _maxTfs;

    public VSM(Indexer index)
            throws IndexNotLoadedException {
        super(index);
        _calculatedWeights = new double[_totalDocuments][];
        _documentWeights = new double[_totalDocuments];
        _modelScore = new double[_totalDocuments];
        VSMprops props = _indexer.getVSMprops();
        _documentWeights = props.get_VSMweights();
        _maxTfs = props.get_MaxTfs();
    }

    @Override
    public List<Result> getRankedResults(List<QueryTerm> query, int endResult)
            throws IOException, IndexNotLoadedException {
        List<Result> results = new ArrayList<>();
        _totalResults = 0;
        for (int i = 0; i < _totalDocuments; i++) {
            _calculatedWeights[i] = null;
            _modelScore[i] = 0;
        }

        //frequencies of the terms
        Map<String, Double> queryFrequencies = new HashMap<>(query.size());
        for (QueryTerm queryTerm : query) {
            queryFrequencies.merge(queryTerm.get_term(), queryTerm.get_weight(), Double::sum);
        }

        //max frequency of the terms
        double queryMaxFrequency = 0;
        for (double frequency : queryFrequencies.values()) {
            if (frequency > queryMaxFrequency) {
                queryMaxFrequency = frequency;
            }
        }

        //merge weights for the same terms
        query = mergeTerms(query);

        int[] dfs = _indexer.getDf(query);

        //tf * idf of terms
        double[] queryWeights = new double[query.size()];
        for (int i = 0; i < query.size(); i++) {
            double tf = query.get(i).get_weight() / queryMaxFrequency;
            double idf = Math.log(_totalDocuments / (1.0 + dfs[i]));
            queryWeights[i] = tf * idf;
        }

        //norm of query
        double queryNorm = 0;
        for (int i = 0; i < query.size(); i++) {
            queryNorm += queryWeights[i] * queryWeights[i];
        }
        queryNorm = Math.sqrt(queryNorm);

        //calculate vsm weights
        for (int i = 0; i < query.size(); i++) {
            Postings postings = _indexer.getPostings(query.get(i).get_term());
            int[] intIDs = postings.get_intID();
            int[] tfs = postings.get_tfs();
            double weight = query.get(i).get_weight();
            double idf = Math.log(_totalDocuments / (1.0 + dfs[i]));
            for (int j = 0; j < dfs[i]; j++) {
                int id = intIDs[j];
                double[] weights = _calculatedWeights[id];
                if (weights == null) {
                    weights = new double[query.size()];
                    _calculatedWeights[id] = weights;
                }
                double tf = (tfs[j] * weight) / _maxTfs[id];
                weights[i] += tf * idf;
            }
        }

        //calculate scores
        double maxScore = 0;
        for (int i = 0; i < _calculatedWeights.length; i++) {
            if (_calculatedWeights[i] == null) {
                continue;
            }
            double[] weights = _calculatedWeights[i];
            double score = 0;
            for (int j = 0; j < queryWeights.length; j++) {
                score += queryWeights[j] * weights[j];
            }
            _modelScore[i] = score / (_documentWeights[i] * queryNorm);
            if (_modelScore[i] > maxScore) {
                maxScore = _modelScore[i];
            }
        }

        if (Double.compare(maxScore, 0.0) == 0) {
            maxScore = 1;
        }
        
        //normalize to [0, 1]
        for (int i = 0; i < _calculatedWeights.length; i++) {
            if (_calculatedWeights[i] == null) {
                continue;
            }
            _modelScore[i] /= maxScore;
            DocInfo docInfo = new DocInfo(i);
            results.add(new Result(docInfo, _modelScore[i]));
        }

        _totalResults = results.size();
        return sort(results, endResult);
    }
}
