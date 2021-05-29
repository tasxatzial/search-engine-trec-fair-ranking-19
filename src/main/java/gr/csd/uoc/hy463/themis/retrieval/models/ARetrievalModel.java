package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * This is an abstract class that each retrieval model should extend
 */
public abstract class ARetrievalModel {
    public enum MODEL {
        OKAPI, VSM, EXISTENTIAL
    }

    protected Indexer _indexer;
    protected int _totalDocuments;
    protected int _totalResults = 0;

    /**
     * Constructor.
     *
     * @param indexer
     * @throws IndexNotLoadedException
     */
    protected ARetrievalModel(Indexer indexer)
            throws IndexNotLoadedException {
        _indexer = indexer;
        _totalDocuments = _indexer.getTotalDocuments();
    }

    /**
     * Method that evaluates the query and returns a list of pairs with
     * the ranked results. In that list the properties specified in props are retrieved only for the
     * documents with indexes from 0 to endDoc.
     *
     * There are various policies to be faster when doing this if we do not want
     * to compute the scores of all queries.
     *
     * For example by sorting the terms of the query based on some indicator of
     * goodness and process the terms in this order (e.g., cutoff based on
     * document frequency, cutoff based on maximum estimated weight, and cutoff
     * based on the weight of a disk page in the posting list
     *
     * The double is the score of the document as returned by the corresponding
     * retrieval model.
     *
     * The list must be in descending order according to the score.
     *
     * @param query
     * @param endResult The returned list will have at most endResult results
     * @return
     * @throws IOException
     * @throws IndexNotLoadedException
     */
    public abstract List<Pair<DocInfo, Double>> getRankedResults(List<QueryTerm> query, int endResult)
            throws IOException, IndexNotLoadedException;

    /**
     * Sorts the specified results based on the citations pagerank scores and the retrieval model score
     *
     * @param results
     * @param citationsPagerank
     * @param endResult
     * @return
     */
    protected List<Pair<DocInfo, Double>> sort(List<Pair<DocInfo, Double>> results, double[] citationsPagerank, int endResult) {
        double pagerankWeight = _indexer.getConfig().getPagerankPublicationsWeight();
        double modelWeight = _indexer.getConfig().getRetrievalModelWeight();

        //normalize pagerank scores
        double maxPagerankScore = 0;
        for (int i = 0; i < citationsPagerank.length; i++) {
            if (citationsPagerank[i] > maxPagerankScore) {
                maxPagerankScore = citationsPagerank[i];
            }
        }

        if (Double.compare(maxPagerankScore, 0.0) == 0) {
            maxPagerankScore = 1;
        }

        //calculate the combined score
        for (int i = 0; i < results.size(); i++) {
            Pair<DocInfo, Double> pair = results.get(i);
            double modelScore = pair.getR();
            double pagerankScore = citationsPagerank[pair.getL().get_id()] / maxPagerankScore;
            double combinedScore = modelScore * modelWeight + pagerankScore * pagerankWeight;
            pair.setR(combinedScore);
        }

        //sort results based on the combined score
        results.sort((o1, o2) -> Double.compare(o2.getR(), o1.getR()));

        //return at most endResults results
        int finalSize = Math.min(endResult, results.size());
        if (finalSize == results.size()) {
            return results;
        }
        else {
            List<Pair<DocInfo, Double>> topResults = new ArrayList<>(finalSize);
            for (int i = 0; i < finalSize; i++) {
                topResults.add(results.get(i));
            }
            return topResults;
        }
    }

    /**
     * Returns the total number of results of the last query
     *
     * @return
     */
    public int getTotalResults() {
        return _totalResults;
    }

    /**
     * Merges (adds) the weights of the equal terms in the specified query list and returns a new list
     *
     * @param query
     * @return
     */
    protected List<QueryTerm> mergeTerms(List<QueryTerm> query) {
        List<QueryTerm> mergedQuery = new ArrayList<>();
        for (int i = 0; i < query.size(); i++) {
            QueryTerm currentTerm = query.get(i);
            boolean found = false;
            for (int j = 0; j < i; j++) {
                if (currentTerm.get_term().equals(query.get(j).get_term())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                QueryTerm mergedTerm = new QueryTerm(currentTerm.get_term(), currentTerm.get_weight());
                for (int j = i + 1; j < query.size(); j++) {
                    if (currentTerm.get_term().equals(query.get(j).get_term())) {
                        mergedTerm.set_weight(mergedTerm.get_weight() + query.get(j).get_weight());
                    }
                }
                mergedQuery.add(mergedTerm);
            }
        }
        return mergedQuery;
    }
}
