package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.Stemmer;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.StopWords;
import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.QueryExpansionException;
import gr.csd.uoc.hy463.themis.queryExpansion.model.WordNet;
import gr.csd.uoc.hy463.themis.queryExpansion.model.GloVe;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;
import gr.csd.uoc.hy463.themis.retrieval.models.Retrieval;
import gr.csd.uoc.hy463.themis.retrieval.models.Existential;
import gr.csd.uoc.hy463.themis.retrieval.models.OkapiBM25P;
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
    private Retrieval _model;
    private QueryExpansion _queryExpansion;
    private final boolean _useStemmer;
    private final boolean _useStopwords;
    private static final String splitDelimiters = "\u0020“”/\"-.\uff0c[](),?+#，*'";

    /* the set of document props that will be retrieved */
    private Set<DocInfo.PROPERTY> _props;

    /**
     * Initializes a search given an indexer with a loaded index.
     *
     * @throws IOException
     * @throws IndexNotLoadedException
     * @throws JWNLException
     */
    public Search(Indexer indexer)
            throws IOException, IndexNotLoadedException, JWNLException {
        if (!indexer.isLoaded()) {
            throw new IndexNotLoadedException();
        }
        _indexer = indexer;
        Themis.print("-> Initializing search...\n");
        String retrievalModel = _indexer.getConfig().getRetrievalModel();

        switch (retrievalModel) {
            case "OkapiBM25+":
                _model = new OkapiBM25P(_indexer);
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
                _queryExpansion = WordNet.Singleton();
            }
        }
        else {
            Themis.print("Default query expansion model: None\n");
        }
        Themis.print("Default Pagerank weight (documents): " + _indexer.getConfig().getDocumentPagerankWeight() + "\n");
        _useStemmer = _indexer.useStemmer();
        _useStopwords = _indexer.useStopwords();
        Themis.print("Ready\n\n");
    }

    /**
     * Sets the retrieval model to the specified model.
     *
     * @param model
     * @throws IndexNotLoadedException
     * @throws IOException
     */
    public void setRetrievalModel(Retrieval.MODEL model)
            throws IndexNotLoadedException, IOException {
        if (!_indexer.isLoaded()) {
            throw new IndexNotLoadedException();
        }
        if (model == Retrieval.MODEL.VSM && !(_model instanceof VSM)) {
            _model = new VSM(_indexer);
        }
        else if (model == Retrieval.MODEL.OKAPI && !(_model instanceof OkapiBM25P)) {
            _model = new OkapiBM25P(_indexer);
        }
        else if (model == Retrieval.MODEL.EXISTENTIAL && !(_model instanceof Existential)) {
            _model = new Existential(_indexer);
        }
    }

    /**
     * Returns the current retrieval model of this Search.
     *
     * @return
     */
    public Retrieval.MODEL getRetrievalmodel() {
        if (_model instanceof VSM) {
            return Retrieval.MODEL.VSM;
        }
        if (_model instanceof OkapiBM25P) {
            return Retrieval.MODEL.OKAPI;
        }
        return Retrieval.MODEL.EXISTENTIAL;
    }

    /**
     * Sets the query expansion model to the specified model.
     *
     * @param model
     * @throws FileNotFoundException
     * @throws IndexNotLoadedException
     * @throws JWNLException
     */
    public void setExpansionModel(QueryExpansion.MODEL model)
            throws FileNotFoundException, IndexNotLoadedException, JWNLException {
        if (!_indexer.isLoaded()) {
            throw new IndexNotLoadedException();
        }
        if (model == QueryExpansion.MODEL.GLOVE) {
            _queryExpansion = GloVe.Singleton(_indexer.getConfig().getGloVeModelPath());
        }
        else if (model == QueryExpansion.MODEL.WORDNET) {
            _queryExpansion = WordNet.Singleton();
        }
        else if (model == QueryExpansion.MODEL.NONE) {
            _queryExpansion = null;
        }
    }

    /**
     * Returns the current query expansion model of this Search.
     *
     * @return
     */
    public QueryExpansion.MODEL getExpansionModel() {
        if (_queryExpansion instanceof GloVe) {
            return QueryExpansion.MODEL.GLOVE;
        }
        else if (_queryExpansion instanceof WordNet) {
            return QueryExpansion.MODEL.WORDNET;
        }
        return QueryExpansion.MODEL.NONE;
    }

    /**
     * Sets the retrieved document properties to the specified props.
     *
     * @param props
     * @throws IndexNotLoadedException
     */
    public void setDocumentProperties(Set<DocInfo.PROPERTY> props)
            throws IndexNotLoadedException {
        if (!_indexer.isLoaded()) {
            throw new IndexNotLoadedException();
        }
        _props = props;
    }

    /**
     * Splits a query into lowercase tokens.
     *
     * @param query
     * @return
     */
    private static List<String> split(String query) {
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
        if (!_indexer.isLoaded()) {
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
     * Returns the total number of results of the last query.
     *
     * @return
     */
    public int getTotalResults() {
        return _model.getTotalResults();
    }

    /**
     * Sets the weight of the pagerank scores of the documents.
     *
     * @param weight
     * @throws IndexNotLoadedException
     */
    public void setDocumentPagerankWeight(double weight)
            throws IndexNotLoadedException {
        if (!_indexer.isLoaded()) {
            throw new IndexNotLoadedException();
        }
        _model.setDocumentPagerankWeight(weight);
    }

    /**
     * Gets the weight of the pagerank scores of the documents.
     *
     * @return
     */
    public double getDocumentPagerankWeight() {
        return _model.getDocumentPagerankWeight();
    }

    /**
     * Prints a list of ranked results.
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
        values based on the actual results */
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
