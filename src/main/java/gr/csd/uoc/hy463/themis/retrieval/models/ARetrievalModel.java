package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;

import java.io.IOException;
import java.util.*;

/**
 * This is an abstract class that each retrieval model should extend
 */
public abstract class ARetrievalModel {
    public enum MODEL {
        OKAPI, VSM, EXISTENTIAL
    }

    protected int _totalDocuments;
    protected int _totalResults = 0;
    Indexer _indexer;

    /**
     * Constructor.
     *
     * @param indexer
     * @throws IndexNotLoadedException
     */
    protected ARetrievalModel(Indexer indexer)
            throws IndexNotLoadedException {
        _indexer = indexer;
        _totalDocuments = indexer.getTotalDocuments();
    }

    /**
     * Method that evaluates the query and returns a list of Results sorted by their scores (descending).
     * The list will contain a maximum endResult number of results.
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
     * @param query
     * @param endResult The returned list will have at most endResult results
     * @return
     * @throws IOException
     * @throws IndexNotLoadedException
     */
    public abstract List<Result> getRankedResults(List<QueryTerm> query, int endResult)
            throws IOException, IndexNotLoadedException;

    /**
     * Sorts the specified results. Uses the pagerank scores of the documents and the scores from the
     * retrieval model.
     * The list will contain a maximum of endResult number of results.
     *
     * @param results
     * @param endResult
     * @return
     */
    protected List<Result> sort(List<Result> results, int endResult)
            throws IndexNotLoadedException {
        double documentPagerankWeight = _indexer.getDocumentPagerankWeight();
        boolean hasPagerank = Double.compare(documentPagerankWeight, 0.0) != 0;
        if (hasPagerank) {
            double[] documentsPagerank = _indexer.getDocumentsPagerank();
            double maxPagerankScore = 0;
            double modelWeight = 1 - documentPagerankWeight;

            //normalize pagerank scores
            for (int i = 0; i < results.size(); i++) {
                DocInfo docInfo = results.get(i).getDocInfo();
                int id = docInfo.get_docID();
                if (documentsPagerank[id] > maxPagerankScore) {
                    maxPagerankScore = documentsPagerank[id];
                }
            }
            if (Double.compare(maxPagerankScore, 0.0) == 0) {
                maxPagerankScore = 1;
            }

            //calculate the combined score
            for (int i = 0; i < results.size(); i++) {
                DocInfo docInfo = results.get(i).getDocInfo();
                double modelScore = results.get(i).getScore();
                double pagerankScore = documentsPagerank[docInfo.get_docID()] / maxPagerankScore;
                double combinedScore = modelScore * modelWeight + pagerankScore * documentPagerankWeight;
                results.get(i).setScore(combinedScore);
            }
        }

        //sort results (descending)
        Collections.sort(results);

        //return at most endResults results
        int finalSize = Math.min(endResult, results.size());
        if (finalSize == results.size()) {
            return results;
        }
        else {
            List<Result> topResults = new ArrayList<>();
            for (int i = 0; i < finalSize; i++) {
                topResults.add(results.get(i));
            }
            return topResults;
        }
    }

    /**
     * Returns the total number of results in the last query
     *
     * @return
     */
    public int getTotalResults() {
        return _totalResults;
    }

    /**
     * Merges (adds) the weights of the equal terms and returns a new list
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
