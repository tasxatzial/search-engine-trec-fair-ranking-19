package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of the Okapi retrieval model. BM25+ is used as the scoring function.
 */
public class OkapiBM25 extends ARetrievalModel {
    private double k1 = 2.0;
    private double b = 0.75;

    public OkapiBM25(Indexer index) {
        super(index);
    }

    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props) throws IOException {
        return getRankedResults(query, props, Integer.MAX_VALUE);
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props, int endDoc) throws IOException {
        List<Pair<Object, Double>> results = new ArrayList<>();
        int totalArticles = _indexer.getTotalArticles();
        double avgdl = _indexer.getAvgdl();

        //merge weights of the same terms
        query = mergeTerms(query);

        //get the relevant documents from the documents file
        fetchEssentialDocInfo(query, props, endDoc);

        //compute the idf for each term of the query
        double[] idfs = new double[query.size()];
        int[] dfs = _indexer.getDf(query);
        for (int i = 0; i < query.size(); i++) {
            idfs[i] = Math.log((1.0 + totalArticles) / dfs[i]);
        }

        //frequencies of each term in each document
        Map<DocInfo, double[]> documentsFreqs = new HashMap<>();
        for (int i = 0; i < _termsDocInfo.size(); i++) {
            int[] freqs = _indexer.getFreq(query.get(i).getTerm());
            double weight = query.get(i).getWeight();
            for (int j = 0; j < _termsDocInfo.get(i).size(); j++) {
                DocInfo docInfo = _termsDocInfo.get(i).get(j);
                double[] docFreqs = documentsFreqs.get(docInfo);
                if (docFreqs == null) {
                    docFreqs = new double[query.size()];
                    documentsFreqs.put(docInfo, docFreqs);
                }
                docFreqs[i] = freqs[j] * weight;
            }
        }

        //calculate the scores
        double maxScore = 0;
        for (Map.Entry<DocInfo, double[]> docFreqs : documentsFreqs.entrySet()) {
            double documentScore = 0;
            double[] freqs = docFreqs.getValue();
            DocInfo docInfo = docFreqs.getKey();
            int docLength = (int) docInfo.getProperty(DocInfo.PROPERTY.LENGTH);
            for (int i = 0; i < query.size(); i++) {
                documentScore += idfs[i] * (freqs[i] * (k1 + 1) / (freqs[i] + k1 * (1 - b + (b * docLength) / avgdl)) + 1);
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
        updateDocInfo(results, props, endDoc);

        return results;
    }
}
