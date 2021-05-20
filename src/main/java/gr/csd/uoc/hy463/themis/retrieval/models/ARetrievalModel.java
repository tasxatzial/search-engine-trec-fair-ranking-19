package gr.csd.uoc.hy463.themis.retrieval.models;

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
    protected int totalArticles;
    protected int totalResults = 0;

    protected ARetrievalModel(Indexer indexer) {
        _indexer = indexer;
        totalArticles = _indexer.getTotalArticles();
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
     * The list must be in descending order according to the score
     *
     * @param query list of query terms
     * @return
     */
    public abstract List<Pair<DocInfo, Double>> getRankedResults(List<QueryTerm> query, int endResult) throws IOException;

    /**
     * Sorts the specified results based on the citations pagerank scores and the retrieval model score.
     * @param results
     */
    protected List<Pair<DocInfo, Double>> sort(List<DocInfo> results, double[] citationsPagerank, double[] modelScore, int endResult) {
        double pagerankWeight = _indexer.getConfig().getPagerankPublicationsWeight();
        double modelWeight = _indexer.getConfig().getRetrievalModelWeight();
        List<Pair<DocInfo, Double>> finalResults = new ArrayList<>();

        //normalize pagerank scores
        double maxScore = 0;
        for (int i = 0; i < citationsPagerank.length; i++) {
            if (citationsPagerank[i] > maxScore) {
                maxScore = citationsPagerank[i];
            }
        }
        for (int i = 0; i < citationsPagerank.length; i++) {
            citationsPagerank[i] /= maxScore;
        }

        //calculate the combined score
        for (int i = 0; i < results.size(); i++) {
            double baseScore = modelScore[results.get(i).getId()];
            double pagerankScore = citationsPagerank[results.get(i).getId()];
            double combinedScore = baseScore * modelWeight + pagerankScore * pagerankWeight;
            finalResults.add(new Pair<>(results.get(i), combinedScore));
        }

        //sort results based on the combined score
        finalResults.sort((o1, o2) -> Double.compare(o2.getR(), o1.getR()));

        //return at most endResults results
        List<Pair<DocInfo, Double>> topResults = new ArrayList<>();
        for (int i = 0; i < Math.min(endResult, finalResults.size()); i++) {
            topResults.add(finalResults.get(i));
        }
        return topResults;
    }

    /**
     * Returns the total number of results of the last query
     * @return
     */
    public int getTotalResults() {
        return totalResults;
    }

    /**
     * Merges the weights of the equal terms in the specified query list and returns a new list
     * @param query
     * @return
     */
    protected List<QueryTerm> mergeTerms(List<QueryTerm> query) {
        List<QueryTerm> mergedQuery = new ArrayList<>();
        for (int i = 0; i < query.size(); i++) {
            QueryTerm currentTerm = query.get(i);
            boolean found = false;
            for (int j = 0; j < i; j++) {
                if (currentTerm.getTerm().equals(query.get(j).getTerm())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                QueryTerm mergedTerm = new QueryTerm(currentTerm.getTerm(), currentTerm.getWeight());
                for (int j = i + 1; j < query.size(); j++) {
                    if (currentTerm.getTerm().equals(query.get(j).getTerm())) {
                        mergedTerm.setWeight(mergedTerm.getWeight() + query.get(j).getWeight());
                    }
                }
                mergedQuery.add(mergedTerm);
            }
        }
        return mergedQuery;
    }
}
