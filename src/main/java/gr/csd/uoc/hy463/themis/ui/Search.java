package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Exceptions.ConfigLoadException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.queryExpansion.model.EXTJWNL;
import gr.csd.uoc.hy463.themis.queryExpansion.model.Glove;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.ExpansionDictionaryInitException;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.retrieval.models.Existential;
import gr.csd.uoc.hy463.themis.retrieval.models.OkapiBM25;
import gr.csd.uoc.hy463.themis.retrieval.models.VSM;
import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import net.sf.extjwnl.JWNLException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Perform a search.
 */
public class Search {
    private final Indexer _indexer;
    private ARetrievalModel _model;
    private QueryExpansion _queryExpansion;

    /* the set of document props that will be retrieved */
    private Set<DocInfo.PROPERTY> _props;

    /**
     * Initializes the Indexer, the expansion dictionary, and loads the index from INDEX_PATH.
     * Reads configuration options from themis.config file.
     *
     * @throws ExpansionDictionaryInitException
     * @throws IndexNotLoadedException
     * @throws ConfigLoadException
     */
    public Search()
            throws ExpansionDictionaryInitException, IndexNotLoadedException, ConfigLoadException {
        _indexer = new Indexer();
        if (!_indexer.load()) {
            throw new IndexNotLoadedException();
        }
        Themis.print("-> Initializing search...\n");
        String retrievalModel = _indexer.getConfig().getRetrievalModel();
        switch (retrievalModel) {
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
        Themis.print("Default retrieval model: " + retrievalModel + "\n");
        _props = new HashSet<>();
        if (_indexer.getConfig().getUseQueryExpansion()) {
            String expansionModel = _indexer.getConfig().getQueryExpansionModel();
            switch (expansionModel) {
                case "Glove":
                    _queryExpansion = new Glove(_indexer.useStopwords());
                    break;
                case "extJWNL":
                    _queryExpansion = new EXTJWNL(_indexer.useStopwords());
                    break;
                default:
                    throw new ExpansionDictionaryInitException();
            }
            Themis.print("Default query expansion model: " + expansionModel + "\n");
        }
        else {
            _queryExpansion = null;
            Themis.print("Default query expansion model: None\n");
        }
        Themis.print("Default Pagerank weight (documents): " + _indexer.getDocumentPagerankWeight() + "\n");
        Themis.print("Ready\n\n");
    }

    /**
     * Returns true if index is loaded, false otherwise
     *
     * @return
     */
    public boolean isIndexLoaded() {
        return _indexer.isloaded();
    }

    /**
     * Unloads the index from memory.
     *
     * Note: Currently there is no method in this class that re-loads the index.
     *
     * @throws IOException
     */
    public void unloadIndex()
            throws IOException {
        _indexer.unload();
    }

    /**
     * Sets the retrieval model to the specified model
     *
     * @param model
     * @throws IndexNotLoadedException
     */
    public void setRetrievalModel(ARetrievalModel.MODEL model)
            throws IndexNotLoadedException {
        if (!isIndexLoaded()) {
            throw new IndexNotLoadedException();
        }
        if (model == ARetrievalModel.MODEL.VSM && !(_model instanceof VSM)) {
            _model = new VSM(_indexer);
        }
        else if (model == ARetrievalModel.MODEL.OKAPI && !(_model instanceof OkapiBM25)) {
            _model = new OkapiBM25(_indexer);
        }
        else if (model == ARetrievalModel.MODEL.EXISTENTIAL && !(_model instanceof Existential)) {
            _model = new Existential(_indexer);
        }
    }

    /**
     * Returns the current retrieval model of this Search
     *
     * @return
     */
    public ARetrievalModel.MODEL getRetrievalmodel() {
        if (_model instanceof VSM) {
            return ARetrievalModel.MODEL.VSM;
        }
        if (_model instanceof OkapiBM25) {
            return ARetrievalModel.MODEL.OKAPI;
        }
        return ARetrievalModel.MODEL.EXISTENTIAL;
    }

    /**
     * Sets the query expansion dictionary to the specified dictionary
     *
     * @param dictionary
     * @throws IOException
     * @throws ExpansionDictionaryInitException
     * @throws IndexNotLoadedException
     * @throws ConfigLoadException
     */
    public void setExpansionDictionary(QueryExpansion.DICTIONARY dictionary)
            throws IOException, ExpansionDictionaryInitException, IndexNotLoadedException, ConfigLoadException {
        if (!isIndexLoaded()) {
            throw new IndexNotLoadedException();
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
     * Returns the current query expansion dictionary of this Search
     *
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
     *
     * @return
     * @throws IndexNotLoadedException If the index is not loaded
     */
    public String getIndexTimestamp()
            throws IndexNotLoadedException {
        if (!isIndexLoaded()) {
            throw new IndexNotLoadedException();
        }
        return _indexer.getIndexTimestamp();
    }

    /**
     * Sets the retrieved document properties to the specified props
     *
     * @param props
     * @throws IndexNotLoadedException
     */
    public void setDocumentProperties(Set<DocInfo.PROPERTY> props)
            throws IndexNotLoadedException {
        if (!isIndexLoaded()) {
            throw new IndexNotLoadedException();
        }
        _props = props;
    }

    /**
     * Returns the string ID from an int ID
     *
     * @param docID
     * @return
     * @throws IndexNotLoadedException
     * @throws UnsupportedEncodingException
     */
    public String getDocID(int docID)
            throws IndexNotLoadedException, UnsupportedEncodingException {
        if (!isIndexLoaded()) {
            throw new IndexNotLoadedException();
        }
        return _indexer.getDocID(docID);
    }

    /**
     * Queries the index and returns a ranked list of results. Equivalent to search(query, Integer.MAX_VALUE)
     *
     * @param query
     * @return
     * @throws ExpansionDictionaryInitException
     * @throws IndexNotLoadedException
     * @throws JWNLException
     * @throws IOException
     */
    public List<Result> search(String query)
            throws ExpansionDictionaryInitException, IndexNotLoadedException, JWNLException, IOException {
        return search(query, Integer.MAX_VALUE);
    }

    /**
     * Queries the index and returns a ranked list of results. A maximum of endResult results are returned.
     *
     * @param query
     * @param endResult From 0 to Integer.MAX_VALUE
     * @return
     * @throws ExpansionDictionaryInitException
     * @throws IndexNotLoadedException
     * @throws JWNLException
     * @throws IOException
     */
    public List<Result> search(String query, int endResult)
            throws ExpansionDictionaryInitException, IndexNotLoadedException, JWNLException, IOException {
        if (!isIndexLoaded()) {
            throw new IndexNotLoadedException();
        }
        boolean useStopwords = _indexer.useStopwords();
        boolean useStemmer = _indexer.useStemmer();

        /* for each term of the initial query, keep 1 extra term from the expanded query */
        int extraTerms = 1;

        List<QueryTerm> newQuery = new ArrayList<>();

        /* split query into tokens and convert to lowercase */
        List<String> splitQuery = ProcessText.split(query);

        /* apply stopwords/stemming and collect the new terms in a new query */
        for (String term : splitQuery) {
            if (useStopwords && StopWords.isStopWord(term)) {
                continue;
            }
            if (useStemmer) {
                String stemmedTerm = ProcessText.applyStemming(term);
                newQuery.add(new QueryTerm(stemmedTerm, 1.0));
            } else {
                newQuery.add(new QueryTerm(term, 1.0));
            }
        }

        /* finally, use the expansion dictionary */
        if (_queryExpansion != null) {

            /* the expanded query is a list of lists */
            List<List<QueryTerm>> expandedQuery = _queryExpansion.expandQuery(splitQuery);

            /* process the list, each item is a list and corresponds to term of the query */
            for (int i = 0; i < expandedQuery.size(); i++) {

                /* get the expanded list of terms for the i-th term */
                List<QueryTerm> expandedTermList = expandedQuery.get(i);

                /* first item should be the original term from the query */
                String originalTerm = expandedTermList.get(0).get_term().toLowerCase();

                /* proceed to the next list if the original term is stopword or is a multiple token string */
                if (originalTerm.split(" ").length > 1) {
                    continue;
                }
                if (useStopwords && StopWords.isStopWord(originalTerm)) {
                    continue;
                }

                /* apply stemming to the original term */
                if (useStemmer) {
                    originalTerm = ProcessText.applyStemming(originalTerm);
                }

                /* keep at most extraTerms from the expanded list */
                int count = 0;
                for (int j = 1; j < expandedTermList.size(); j++) {
                    if (count == extraTerms) {
                        break;
                    }
                    if (expandedTermList.get(j).get_term().split(" ").length > 1) {
                        continue;
                    }
                    String newTerm = expandedTermList.get(j).get_term().toLowerCase();
                    if (useStopwords && StopWords.isStopWord(newTerm)) {
                        continue;
                    }
                    if (useStemmer) {
                        newTerm = ProcessText.applyStemming(newTerm);
                    }

                    /* Make sure none of the new terms are the same as the original term */
                    if (!originalTerm.equals(newTerm)) {
                        newQuery.add(new QueryTerm(newTerm, expandedTermList.get(j).get_weight()));
                        count++;
                    }
                }
            }
        }

        List<Result> result = _model.getRankedResults(newQuery, endResult);
        _indexer.updateDocInfo(result, _props);
        return result;
    }

    /**
     * Returns the total number of results of the last query
     *
     * @return
     */
    public int getTotalResults() {
        return _model.getTotalResults();
    }

    /**
     * Prints a list of ranked results
     *
     * @param searchResults
     * @throws UnsupportedEncodingException
     * @throws IndexNotLoadedException
     */
    public void printResults(List<Result> searchResults)
            throws UnsupportedEncodingException, IndexNotLoadedException {
        printResults(searchResults, 0, Integer.MAX_VALUE);
    }

    /**
     * Sets the weight for the pagerank scores of the documents
     *
     * @param weight
     */
    public void setDocumentPagerankWeight(double weight) {
        _indexer.setDocumentPagerankWeight(weight);
    }

    /**
     * Gets the weight for the pagerank scores of the documents
     *
     * @return
     */
    public double getDocumentPagerankWeight() {
        return _indexer.getDocumentPagerankWeight();
    }

    /**
     * Prints results from index startResult to endResult (inclusive).
     * Equivalent to printResults(searchResults, 0, Integer.MAX_VALUE)
     *
     * @param searchResults
     * @param startResult From 0 to Integer.MAX_VALUE
     * @param endResult From 0 to Integer.MAX_VALUE
     * @throws UnsupportedEncodingException
     * @throws IndexNotLoadedException
     */
    public void printResults(List<Result> searchResults, int startResult, int endResult)
            throws UnsupportedEncodingException, IndexNotLoadedException {
        if (searchResults.isEmpty()) {
            return;
        }

        /* startResult and endResult can be anything therefore we need to compute the correct
        * values based on the actual results */
        int firstDisplayedResult = Math.max(startResult, 0);
        int lastDisplayedResult = Math.min(endResult, searchResults.size() - 1);
        if (firstDisplayedResult > lastDisplayedResult) {
            Themis.print("\nNo results found in the specified range\n");
            return;
        }

        Themis.print("\nDisplaying results " + firstDisplayedResult + " to " + lastDisplayedResult + "\n\n");

        /* print the results */
        for (int i = firstDisplayedResult; i <= lastDisplayedResult; i++) {
            DocInfo docInfo = searchResults.get(i).getDocInfo();
            List<DocInfo.PROPERTY> sortedProps = new ArrayList<>(_props);
            Collections.sort(sortedProps);
            Themis.print(i + " ---------------------------------------\n");
            Themis.print("DOC_ID: " + _indexer.getDocID(docInfo.get_docID()) + "\n");
            for (DocInfo.PROPERTY docInfoProp : sortedProps) {
                if (docInfo.hasProperty(docInfoProp)) {
                    Themis.print(docInfoProp + ": " + docInfo.getProperty(docInfoProp) + "\n");
                }
            }
            Themis.print("\n");
        }
    }
}
