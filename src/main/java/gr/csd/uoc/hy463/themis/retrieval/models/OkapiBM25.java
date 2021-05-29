package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.OKAPIprops;
import gr.csd.uoc.hy463.themis.retrieval.model.Postings;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of the Okapi retrieval model. BM25+ is used as the scoring function.
 */
public class OkapiBM25 extends ARetrievalModel {
    private final double _k1 = 2.0;
    private final double _b = 0.75;
    private final double _avgdl;
    double[] _citationsPagerank;
    int[] _tokenCount;
    double[] _modelScore;
    double[][] _calculatedFreqs;

    public OkapiBM25(Indexer index)
            throws IndexNotLoadedException {
        super(index);
        _calculatedFreqs = new double[_totalDocuments][];
        _citationsPagerank = new double[_totalDocuments];
        _tokenCount = new int[_totalDocuments];
        _modelScore = new double[_totalDocuments];
        _avgdl = _indexer.getAvgdl();
    }

    @Override
    public List<Result> getRankedResults(List<QueryTerm> query, int endResult)
            throws IOException, IndexNotLoadedException {
        List<Result> results = new ArrayList<>();
        _totalResults = 0;
        for (int i = 0; i < _totalDocuments; i++) {
            _calculatedFreqs[i] = null;
            _modelScore[i] = 0;
        }

        //merge weights for the same terms
        query = mergeTerms(query);

        int[] dfs = _indexer.getDf(query);

        //calculate frequencies
        for(int i = 0; i < query.size(); i++) {
            Postings postings = _indexer.getPostings(query.get(i).get_term());
            int[] intIDs = postings.get_intID();
            OKAPIprops props = _indexer.getOKAPIprops(intIDs);
            double[] i_citationsPagerank = props.get_citationsPagerank();
            int[] i_tokenCount = props.get_tokenCount();
            int[] tfs = postings.get_tfs();
            double weight = query.get(i).get_weight();
            for (int j = 0; j < dfs[i]; j++) {
                int id = intIDs[j];
                _citationsPagerank[id] = i_citationsPagerank[j];
                _tokenCount[id] = i_tokenCount[j];
                double[] freqs = _calculatedFreqs[id];
                if (freqs == null) {
                    freqs = new double[query.size()];
                    _calculatedFreqs[id] = freqs;
                }
                freqs[i] = tfs[j] * weight;
            }
        }

        double[] idfs = new double[query.size()];
        for (int i = 0; i < idfs.length; i++) {
            idfs[i] = Math.log(_totalDocuments / (1.0 + dfs[i]));
        }

        //calculate scores
        double maxScore = 0;
        for (int i = 0; i < _calculatedFreqs.length; i++) {
            if (_calculatedFreqs[i] == null) {
                continue;
            }
            double score = 0;
            double[] freqs = _calculatedFreqs[i];
            double B = _k1 * (1 - _b + (_b * _tokenCount[i]) / _avgdl);
            for (int j = 0; j < query.size(); j++) {
                score += idfs[j] * (freqs[j] * (_k1 + 1) / (freqs[j] + B) + 1);
            }
            _modelScore[i] = score;
            if (score > maxScore) {
                maxScore = score;
            }
        }

        if (Double.compare(maxScore, 0.0) == 0) {
            maxScore = 1;
        }

        //normalize to [0, 1]
        for (int i = 0; i < _calculatedFreqs.length; i++) {
            if (_calculatedFreqs[i] == null) {
                continue;
            }
            _modelScore[i] /= maxScore;
            DocInfo docInfo = new DocInfo(i);
            results.add(new Result(docInfo, _modelScore[i]));
        }

        _totalResults = results.size();
        return sort(results, _citationsPagerank, endResult);
    }
}
