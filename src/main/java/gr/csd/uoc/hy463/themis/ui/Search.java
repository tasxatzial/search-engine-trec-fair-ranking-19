package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.Exceptions.IncompleteFileException;
import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.Stemmer;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.StopWords;
import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.QueryExpansionException;
import gr.csd.uoc.hy463.themis.queryExpansion.model.EXTJWNL;
import gr.csd.uoc.hy463.themis.queryExpansion.model.GloVe;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.retrieval.models.Existential;
import gr.csd.uoc.hy463.themis.retrieval.models.OkapiBM25;
import gr.csd.uoc.hy463.themis.retrieval.models.VSM;
import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;

import net.sf.extjwnl.JWNLException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * The main class responsible for querying the collection and printing the results.
 */
public class Search {
    private final Indexer _indexer;
    private ARetrievalModel _model;
    private QueryExpansion _queryExpansion;
    private final boolean _useStemmer;
    private final boolean _useStopwords;
    private static final String splitDelimiters = "\u0020“”/\"-.\uff0c[](),?+#，*'";

    /* the set of document props that will be retrieved */
    private Set<DocInfo.PROPERTY> _props;

    /**
     * Initializes the Indexer, the expansion dictionary, and loads the index from INDEX_DIR.
     * Reads configuration options from themis.config file.
     *
     * @throws IOException
     * @throws IndexNotLoadedException
     * @throws JWNLException
     * @throws IncompleteFileException
     */
    public Search()
            throws IOException, IndexNotLoadedException, JWNLException, IncompleteFileException {
        _indexer = new Indexer();
        _indexer.load();
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
            Themis.print("Default query expansion model: " + expansionModel + "\n");
            if (expansionModel.equals("GloVe")) {
                _queryExpansion = GloVe.Singleton(_indexer.getConfig().getGloVeModelPath());
            } else if (expansionModel.equals("WordNet")) {
                _queryExpansion = EXTJWNL.Singleton();
            } else {
                _queryExpansion = null;
            }
        }
        else {
            _queryExpansion = null;
            Themis.print("Default query expansion model: None\n");
        }
        Themis.print("Default Pagerank weight (documents): " + _indexer.getDocumentPagerankWeight() + "\n");
        _useStemmer = _indexer.useStemmer();
        _useStopwords = _indexer.useStopwords();
        Themis.print("Ready\n\n");
    }

    /**
     * Returns the Config instance associated with this Search.
     *
     * @return
     */
    public Config getConfig() {
        return _indexer.getConfig();
    }

    /**
     * Returns true if index is loaded, false otherwise
     *
     * @return
     */
    public boolean isIndexLoaded() {
        return _indexer.isLoaded();
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
     * @throws FileNotFoundException
     * @throws IndexNotLoadedException
     * @throws JWNLException
     */
    public void setExpansionDictionary(QueryExpansion.DICTIONARY dictionary)
            throws FileNotFoundException, IndexNotLoadedException, JWNLException {
        if (!isIndexLoaded()) {
            throw new IndexNotLoadedException();
        }
        if (dictionary == QueryExpansion.DICTIONARY.GLOVE) {
            _queryExpansion = GloVe.Singleton(_indexer.getConfig().getGloVeModelPath());
        }
        else if (dictionary == QueryExpansion.DICTIONARY.EXTJWNL) {
            _queryExpansion = EXTJWNL.Singleton();
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
        if (_queryExpansion instanceof GloVe) {
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
     * @throws IndexNotLoadedException
     */
    public String getIndexTimestamp()
            throws IndexNotLoadedException {
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
        return _indexer.getDocID(docID);
    }

    /**
     * Splits a query into lowercase tokens.
     *
     * @param query
     * @return
     */
    public static List<String> split(String query) {
        StringTokenizer tokenizer = new StringTokenizer(query, Search.splitDelimiters);
        List<String> terms = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            terms.add(token);
        }
        return terms;
    }

    /**
     * Queries the index and returns a ranked list of results. Equivalent to search(query, Integer.MAX_VALUE).
     *
     * @param query
     * @return
     * @throws IndexNotLoadedException
     * @throws QueryExpansionException
     * @throws IOException
     */
    public List<Result> search(String query)
            throws IndexNotLoadedException, QueryExpansionException, IOException {
        return search(query, Integer.MAX_VALUE);
    }

    /**
     * Queries the index and returns a ranked list of results. A maximum of endResult results are returned.
     *
     * @param query
     * @param endResult From 0 to Integer.MAX_VALUE
     * @return
     * @throws IndexNotLoadedException
     * @throws QueryExpansionException
     * @throws IOException
     */
    public List<Result> search(String query, int endResult)
            throws IndexNotLoadedException, QueryExpansionException, IOException {
        if (!isIndexLoaded()) {
            throw new IndexNotLoadedException();
        }

        /* split query into tokens and convert to lowercase */
        List<String> splitQuery = Search.split(query);

        List<QueryTerm> newQuery = new ArrayList<>();
        if (_queryExpansion == null) {
            for (String term : splitQuery) {
                if (_useStopwords && StopWords.isStopWord(term)) {
                    continue;
                }
                String newTerm = term;
                if (_useStemmer) {
                    newTerm = Stemmer.stem(term);
                }
                newQuery.add(new QueryTerm(newTerm.toLowerCase(), 1.0));
            }
        }
        else {
            /* the expanded query is a list of lists */
            List<List<QueryTerm>> expandedQuery = _queryExpansion.expandQuery(splitQuery, _useStopwords);

            /* each list contains the Query terms associated to a term of the query */
            for (List<QueryTerm> expandedTermList : expandedQuery) {
                int termCount = 0;
                for (QueryTerm currQueryTerm : expandedTermList) {
                    String term = currQueryTerm.get_term();
                    if ((_useStopwords && StopWords.isStopWord(term)) ||
                            (term.split(" ").length > 1)) {
                        continue;
                    }

                    if (_useStemmer) {
                        term = Stemmer.stem(term);
                    }

                    /* Make sure the new term is not the same as the original term */
                    if (termCount == 0 || !newQuery.get(newQuery.size() - 1).get_term().equals(term)) {
                        newQuery.add(new QueryTerm(term.toLowerCase(), currQueryTerm.get_weight()));
                        termCount++;
                    }
                    if (termCount == 2) {
                        break;
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
     * Sets the weight for the pagerank scores of the documents
     *
     * @param weight
     */
    public void setDocumentPagerankWeight(double weight)
            throws IndexNotLoadedException {
        if (!isIndexLoaded()) {
            throw new IndexNotLoadedException();
        }
        _model.setDocumentPagerankWeight(weight);
    }

    /**
     * Gets the weight for the pagerank scores of the documents
     *
     * @return
     */
    public double getDocumentPagerankWeight() {
        return _model.getDocumentPagerankWeight();
    }

    /**
     * Prints a list of ranked results
     *
     * @param searchResults
     * @throws IndexNotLoadedException
     * @throws UnsupportedEncodingException
     */
    public void printResults(List<Result> searchResults)
            throws IndexNotLoadedException, UnsupportedEncodingException {
        printResults(searchResults, 0, Integer.MAX_VALUE);
    }
    
    /**
     * Prints results from index startResult to endResult (inclusive).
     * Equivalent to printResults(searchResults, 0, Integer.MAX_VALUE)
     *
     * @param searchResults
     * @param startResult From 0 to Integer.MAX_VALUE
     * @param endResult From 0 to Integer.MAX_VALUE
     * @throws IndexNotLoadedException
     * @throws UnsupportedEncodingException
     */
    public void printResults(List<Result> searchResults, int startResult, int endResult)
            throws IndexNotLoadedException, UnsupportedEncodingException {
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
            Themis.print("DOC_ID: " + _indexer.getDocID(docInfo.getDocID()) + "\n");
            for (DocInfo.PROPERTY docInfoProp : sortedProps) {
                if (docInfo.hasProperty(docInfoProp)) {
                    Themis.print(docInfoProp + ": " + docInfo.getProperty(docInfoProp) + "\n");
                }
            }
            Themis.print("\n");
        }
    }
}
