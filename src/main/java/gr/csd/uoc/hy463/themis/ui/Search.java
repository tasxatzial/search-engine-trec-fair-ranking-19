package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.queryExpansion.model.EXTJWNL;
import gr.csd.uoc.hy463.themis.queryExpansion.model.Glove;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.QueryExpansionException;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.retrieval.models.Existential;
import gr.csd.uoc.hy463.themis.retrieval.models.OkapiBM25;
import gr.csd.uoc.hy463.themis.retrieval.models.VSM;
import gr.csd.uoc.hy463.themis.ui.Exceptions.SearchNoIndexException;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * A simple ui to search the indexes
 */
public class Search {
    private Indexer _indexer;
    private ARetrievalModel _model;
    private QueryExpansion _queryExpansion;
    private Set<DocInfo.PROPERTY> _props;

    public Search() throws IOException, QueryExpansionException, SearchNoIndexException {
        _indexer = new Indexer();
        if (!_indexer.load()) {
            throw new SearchNoIndexException();
        }
        switch (_indexer.getConfig().getRetrievalModel()) {
            case "BM25":
                _model = new OkapiBM25(_indexer);
                break;
            case "VSM":
                _model = new VSM(_indexer);
                break;
            default:
                _model = new Existential(_indexer);
                break;
        }
        _props = new HashSet<>();
        if (_indexer.getConfig().getUseQueryExpansion()) {
            switch (_indexer.getConfig().getQueryExpansionModel()) {
                case "Glove":
                    _queryExpansion = new Glove(_indexer.useStopwords());
                    break;
                case "extJWNL":
                    _queryExpansion = new EXTJWNL(_indexer.useStopwords());
                    break;
            }
        }
        else {
            _queryExpansion = null;
        }
    }

    /**
     * Returns true if index is loaded
     * @return
     */
    public boolean isIndexLoaded() {
        return _indexer.loaded();
    }

    /**
     * Unloads an index from memory. Any further method calls of this Search, except printing results, will
     * throw a SearchNoIndexException.
     * @throws IOException
     */
    public void unloadIndex() throws IOException {
        _indexer.unload();
    }

    /**
     * Sets the retrieval model to the specified model
     * @param model
     */
    public void setRetrievalModel(ARetrievalModel.MODEL model) throws SearchNoIndexException {
        if (!isIndexLoaded()) {
            throw new SearchNoIndexException();
        }
        if (model == ARetrievalModel.MODEL.VSM && !(_model instanceof VSM)) {
            _model = new VSM(_indexer);
        }
        else if (model == ARetrievalModel.MODEL.BM25 && !(_model instanceof OkapiBM25)) {
            _model = new OkapiBM25(_indexer);
        }
        else if (model == ARetrievalModel.MODEL.EXISTENTIAL && !(_model instanceof Existential)) {
            _model = new Existential(_indexer);
        }
    }

    /**
     * Returns the current retrieval model
     * @return
     */
    public ARetrievalModel.MODEL getRetrievalmodel() {
        if (_model instanceof VSM) {
            return ARetrievalModel.MODEL.VSM;
        }
        if (_model instanceof OkapiBM25) {
            return ARetrievalModel.MODEL.BM25;
        }
        return ARetrievalModel.MODEL.EXISTENTIAL;
    }

    /**
     * Sets the query expansion dictionary to the specified dictionary
     * @param dictionary
     * @throws IOException
     */
    public void setExpansionDictionary(QueryExpansion.DICTIONARY dictionary) throws IOException, QueryExpansionException, SearchNoIndexException {
        if (!isIndexLoaded()) {
            throw new SearchNoIndexException();
        }
        if (dictionary == QueryExpansion.DICTIONARY.GLOVE && !(_queryExpansion instanceof Glove)) {
            _queryExpansion = new Glove(_indexer.useStopwords());
        }
        else if (dictionary == QueryExpansion.DICTIONARY.EXTJWNL && !(_queryExpansion instanceof EXTJWNL)) {
            _queryExpansion = new EXTJWNL(_indexer.useStopwords());
        }
        else if (dictionary == QueryExpansion.DICTIONARY.NONE) {
            _queryExpansion = null;
        }
    }

    /**
     * Returns the current query expansion dictionary
     * @return
     */
    public QueryExpansion.DICTIONARY getExpansionDictionary() {
        if (_queryExpansion instanceof Glove) {
            return QueryExpansion.DICTIONARY.GLOVE;
        }
        else if (_queryExpansion instanceof EXTJWNL) {
            return QueryExpansion.DICTIONARY.EXTJWNL;
        }
        return QueryExpansion.DICTIONARY.NONE;
    }

    /**
     * Returns the timestamp of the loaded index
     * @return
     */
    public String getIndexTimestamp() throws SearchNoIndexException {
        if (!isIndexLoaded()) {
            throw new SearchNoIndexException();
        }
        return _indexer.getIndexTimestamp();
    }

    /**
     * Sets the retrieved document properties to the specified props
     * @param props
     */
    public void setDocumentProperties(Set<DocInfo.PROPERTY> props) throws SearchNoIndexException {
        if (!isIndexLoaded()) {
            throw new SearchNoIndexException();
        }
        _props = props;
    }

    /**
     * Searches for a query and returns a ranked list of results.
     * @param query
     * @return
     * @throws IOException
     */
    public List<Pair<DocInfo, Double>> search(String query) throws IOException, QueryExpansionException, SearchNoIndexException {
        return search(query, Integer.MAX_VALUE);
    }

    /**
     * Searches for a query and returns a ranked list of results. The results in the range
     * [0, endResult] contain the document properties set by setDocumentProperties().
     * @param query
     * @param endResult From 0 to Integer.MAX_VALUE
     * @return
     * @throws IOException
     */
    public List<Pair<DocInfo, Double>> search(String query, int endResult) throws QueryExpansionException, IOException, SearchNoIndexException {
        if (!isIndexLoaded()) {
            throw new SearchNoIndexException();
        }
        boolean useStopwords = _indexer.useStopwords();
        boolean useStemmer = _indexer.useStemmer();
        int maxNewTermsForEachTerm = 1; //for each term of the initial query, expand the query by one extra term
        List<QueryTerm> finalQuery = new ArrayList<>();

        //split query into terms and convert to lowercase
        List<String> splitQuery = ProcessText.split(query);

        //apply stopwords/stemming and add terms to the final query
        List<String> splitQueryProcessed = new ArrayList<>();
        for (String term : splitQuery) {
            if (useStopwords && StopWords.isStopWord(term)) {
                splitQueryProcessed.add(term);
                continue;
            }
            if (useStemmer) {
                String stemmedTerm = ProcessText.applyStemming(term);
                splitQueryProcessed.add(stemmedTerm);
                finalQuery.add(new QueryTerm(stemmedTerm, 1.0));
            } else {
                splitQueryProcessed.add(term);
                finalQuery.add(new QueryTerm(term, 1.0));
            }
        }

        //expand the query and add terms to the final query
        if (_queryExpansion != null) {
            List<List<QueryTerm>> expandedQuery = _queryExpansion.expandQuery(splitQuery);
            for (int i = 0; i < expandedQuery.size(); i++) {
                String firstTerm = expandedQuery.get(i).get(0).getTerm().toLowerCase();
                if (firstTerm.split(" ").length > 1) {
                    continue;
                }
                if (useStopwords && StopWords.isStopWord(firstTerm)) {
                    continue;
                }
                if (useStemmer) {
                    firstTerm = ProcessText.applyStemming(firstTerm);
                }
                List<QueryTerm> expandedTermList = expandedQuery.get(i);
                int count = 0;
                for (int j = 1; j < expandedTermList.size(); j++) {
                    if (count == maxNewTermsForEachTerm) {
                        break;
                    }
                    if (expandedTermList.get(j).getTerm().split(" ").length > 1) {
                        continue;
                    }
                    String term = expandedTermList.get(j).getTerm().toLowerCase();
                    if (useStopwords && StopWords.isStopWord(term)) {
                        continue;
                    }
                    if (useStemmer) {
                        term = ProcessText.applyStemming(term);
                    }
                    if (!firstTerm.equals(term)) {
                        finalQuery.add(new QueryTerm(term, expandedTermList.get(j).getWeight()));
                        count++;
                    }
                }
            }
        }

        return _model.getRankedResults(finalQuery, _props, endResult);
    }

    /**
     * Prints a list of results in decreasing ranking order.
     * @param searchResults
     */
    public void printResults(List<Pair<DocInfo, Double>> searchResults) {
        printResults(searchResults, 0, Integer.MAX_VALUE);
    }

    /**
     * Prints a list of results in decreasing ranking order from ranked position startResult to endResult.
     * Only the results from ranked position startResult to endResult are displayed.
     * @param searchResults
     * @param startResult From 0 to Integer.MAX_VALUE
     * @param endResult From 0 to Integer.MAX_VALUE
     */
    public void printResults(List<Pair<DocInfo, Double>> searchResults, int startResult, int endResult) {
        if (searchResults.isEmpty()) {
            return;
        }

        /* startResult and endResult might be out of the range of the actual results.
        therefore we need to find the proper indexes of the first and last displayed result */
        int firstDisplayedResult = Math.max(startResult, 0);
        int lastDisplayedResult = Math.min(endResult, searchResults.size() - 1);
        if (firstDisplayedResult > lastDisplayedResult) {
            Themis.print("\nNo results found in the specified range\n");
            return;
        }

        Themis.print("\nDisplaying results " + firstDisplayedResult + " to " + lastDisplayedResult + "\n\n");

        /* print the results */
        for (int i = firstDisplayedResult; i <= lastDisplayedResult; i++) {
            DocInfo docInfo = (DocInfo) searchResults.get(i).getL();
            List<DocInfo.PROPERTY> sortedProps = new ArrayList<>(_props);
            Collections.sort(sortedProps);
            Themis.print(i + " ---------------------------------------\n");
            Themis.print("DOC_ID: " + docInfo.getId() + "\n");
            for (DocInfo.PROPERTY docInfoProp : sortedProps) {
                if (docInfo.hasProperty(docInfoProp)) {
                    Themis.print(docInfoProp + ": " + docInfo.getProperty(docInfoProp) + "\n");
                }
            }
            Themis.print("\n");
        }
    }
}
