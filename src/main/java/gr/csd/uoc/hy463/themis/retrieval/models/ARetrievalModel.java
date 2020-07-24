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
        BM25, VSM, EXISTENTIAL
    }

    protected List<List<DocInfo>> _termsDocInfo;
    protected List<String> _terms;
    protected Indexer _indexer;

    public ARetrievalModel(Indexer indexer) {
        _indexer = indexer;
        _terms = new ArrayList<>();
        _termsDocInfo = new ArrayList<>();
    }

    public static Set<DocInfo.PROPERTY> getVSMProps() {
        Set<DocInfo.PROPERTY> props = new HashSet<>();
        props.add(DocInfo.PROPERTY.WEIGHT);
        props.add(DocInfo.PROPERTY.MAX_TF);
        props.add(DocInfo.PROPERTY.PAGERANK);
        return props;
    }

    public static Set<DocInfo.PROPERTY> getOkapiProps() {
        Set<DocInfo.PROPERTY> props = new HashSet<>();
        props.add(DocInfo.PROPERTY.LENGTH);
        props.add(DocInfo.PROPERTY.PAGERANK);
        return props;
    }

    /**
     * Method that evaluates the query and returns a ranked list of pairs of the
     * whole relevant documents.
     *
     * The double is the score of the document as returned by the corresponding
     * retrieval model.
     *
     * The list must be in descending order according to the score
     *
     * @param query set of query terms
     * @return
     */
    public abstract List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props) throws IOException;

    /**
     * Method that evaluates the query and returns a list of pairs with
     * the ranked results. In that list the properties specified in props are retrieved only for the
     * documents with indexes from startDoc to endDoc.
     *
     * startDoc and endDoc range is from 0 (top ranked doc) to Integer.MAX_VALUE.
     * startDoc should be set to 0 and endDoc should be set to Integer.MAX_VALUE if we want to retrieve all
     * documents related to this query. Using different values for any of them is encouraged only when we want
     * to see a small number of results.
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
    public abstract List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException;

    /**
     * Reads the documents file and creates a list of list of docInfo objects (one list for each term of the query).
     * The list will have been updated when the function returns.
     * @param query
     * @param props
     * @param startDoc
     * @param endDoc
     * @throws IOException
     */
    protected void fetchEssentialDocInfo(List<QueryTerm> query, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException {

        /* collect all terms */
        List<String> terms = new ArrayList<>(query.size());
        for (QueryTerm queryTerm : query) {
            terms.add(queryTerm.getTerm());
        }

        /* initialize structures */
        List<List<DocInfo>> termsDocInfo = new ArrayList<>(terms.size());
        for (int i = 0; i < terms.size(); i++) {
            termsDocInfo.add(new ArrayList<>());
        }

        /* check whether this query has same terms as the previous query */
        for (int i = 0; i < terms.size(); i++) {
            for (int j = 0; j < _terms.size(); j++) {
                if (terms.get(i).equals(_terms.get(j))) {
                    termsDocInfo.set(i, _termsDocInfo.get(j));
                    break;
                }
            }
        }

        /* remove the docInfo from all the terms of the previous query that do not appear in this query */
        for (int i = 0; i < _termsDocInfo.size(); i++) {
            boolean found = false;
            for (int j = 0; j < termsDocInfo.size(); j++) {
                if (_termsDocInfo.get(i) == termsDocInfo.get(j)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                _termsDocInfo.set(i, null);
            }
        }

        /* if we want paginated results, both the okapi and VSM models should fetch only their
        essential properties for each result so that they can do the ranking. The rest of the properties
        will be fetched after the ranking is determined */
        Set<DocInfo.PROPERTY> newProps = new HashSet<>(props);
        if (startDoc == 0 && endDoc == Integer.MAX_VALUE) {
            if (this instanceof VSM) {
                newProps.addAll(getVSMProps());
            }
            else if (this instanceof OkapiBM25) {
                newProps.addAll(getOkapiProps());
            }
        }
        else {
            if (this instanceof VSM) {
                newProps = getVSMProps();
            }
            else if (this instanceof OkapiBM25) {
                newProps = getOkapiProps();
            }
        }

        /* finally fetch the properties */
        _indexer.getDocInfo(terms, termsDocInfo, newProps);

        _termsDocInfo = termsDocInfo;
        _terms = terms;
    }

    /**
     * Updates the ranked results that have index in [startDoc, endDoc] by fetching the specified props
     * from the documents file
     * @param results
     * @param startDoc
     * @param endDoc
     * @throws IOException
     */
    protected void updateDocInfo(List<Pair<Object, Double>> results, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException {

        /* the properties that each docInfo should have that are not the essential properties of this model */
        Set<DocInfo.PROPERTY> extraProps = new HashSet<>(props);
        if (this instanceof VSM) {
            extraProps.removeAll(getVSMProps());
        }
        else if (this instanceof OkapiBM25) {
            extraProps.removeAll(getOkapiProps());
        }
        if (extraProps.isEmpty()) {
            return;
        }

        /* update all docInfo items of the results accordingly */
        List<DocInfo> updatedDocInfos = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            DocInfo docInfo = (DocInfo) results.get(i).getL();
            if (i >= startDoc && i <= endDoc) {
                updatedDocInfos.add(docInfo);
            }
        }

        /* finally update the collected docInfo */
        _indexer.updateDocInfo(updatedDocInfos, extraProps);
    }

    /**
     * Sorts the specified results based on the pagerank scores of the citations and the score of this
     * model. The scores should be normalized to [0, 1]
     * @param results
     */
    protected void sort(List<Pair<Object, Double>> results) {
        double citationsPagerankWeight = _indexer.getConfig().getPagerankPublicationsWeight();
        double modelWeight = _indexer.getConfig().getRetrievalModelWeight();
        double citationsMaxPagerank = 0;
        double[] citationsPageranks = new double[results.size()];
        for (int i = 0; i < results.size(); i++) {
            DocInfo docInfo = (DocInfo) results.get(i).getL();
            citationsPageranks[i] = (double) docInfo.getProperty(DocInfo.PROPERTY.PAGERANK);
            if (citationsPageranks[i] > citationsMaxPagerank) {
                citationsMaxPagerank = citationsPageranks[i];
            }
        }
        for (int i = 0; i < results.size(); i++) {
            Pair<Object, Double> result = results.get(i);
            double modelScore = result.getR();
            result.setR(modelScore * modelWeight + (citationsPageranks[i] / citationsMaxPagerank) * citationsPagerankWeight);
        }

        results.sort((o1, o2) -> Double.compare(o2.getR(), o1.getR()));
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
