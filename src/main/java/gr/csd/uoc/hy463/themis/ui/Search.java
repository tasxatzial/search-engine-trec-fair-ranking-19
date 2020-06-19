/*
 * themis - A fair search engine for scientific articles
 *
 * Currently over the Semantic Scholar Open Research Corpus
 * http://s2-public-api-prod.us-west-2.elasticbeanstalk.com/corpus/
 *
 * Collaborative work with the undergraduate/graduate students of
 * Information Retrieval Systems (hy463) course
 * Spring Semester 2020
 *
 * -- Writing code during COVID-19 pandemic times :-( --
 *
 * Aiming to participate in TREC 2020 Fair Ranking Track
 * https://fair-trec.github.io/
 *
 * Computer Science Department http://www.csd.uoc.gr
 * University of Crete
 * Greece
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.queryExpansion.model.EXTJWNL;
import gr.csd.uoc.hy463.themis.queryExpansion.model.Glove;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansionException;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.retrieval.models.Existential;
import gr.csd.uoc.hy463.themis.retrieval.models.OkapiBM25;
import gr.csd.uoc.hy463.themis.retrieval.models.VSM;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * Some kind of simple ui to search the indexes. Some kind of GUI will be a
 * bonus!
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class Search {
    private Indexer _indexer;
    private ARetrievalModel _model;
    private QueryExpansion _queryExpansion;
    private Set<DocInfo.PROPERTY> _props;

    public Search() throws IOException, QueryExpansionException {
        _indexer = new Indexer();
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

        if (!_indexer.load()) {
            throw new IOException("Unable to load index");
        }
        _props = new HashSet<>();
        if (_indexer.getConfig().getUseQueryExpansion()) {
            switch (_indexer.getConfig().getQueryExpansionModel()) {
                case "Glove":
                    _queryExpansion = new Glove();
                    break;
                case "extJWNL":
                    _queryExpansion = new EXTJWNL();
                    break;
            }
        }
        else {
            _queryExpansion = null;
        }
    }

    /**
     * Unloads an index from memory
     * @throws IOException
     */
    public void unloadIndex() throws IOException {
        _indexer.unload();
    }

    /**
     * Sets the retrieval model to the specified model
     * @param model
     */
    public void setRetrievalModel(ARetrievalModel.MODEL model) {
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
    public void setExpansionDictionary(QueryExpansion.DICTIONARY dictionary) throws IOException, QueryExpansionException {
        if (dictionary == QueryExpansion.DICTIONARY.GLOVE && !(_queryExpansion instanceof Glove)) {
            _queryExpansion = new Glove();
        }
        else if (dictionary == QueryExpansion.DICTIONARY.EXTJWNL && !(_queryExpansion instanceof EXTJWNL)) {
            _queryExpansion = new EXTJWNL();
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
        return QueryExpansion.DICTIONARY.NONE;
    }

    /**
     * Sets the retrieved document properties to the specified props
     * @param props
     */
    public void setDocumentProperties(Set<DocInfo.PROPERTY> props) {
        _props = props;
    }

    /**
     * Searches for a query and returns a ranked list of results.
     * @param query
     * @return
     * @throws IOException
     */
    public List<Pair<Object, Double>> search(String query) throws IOException, QueryExpansionException {
        return search(query, 0, Integer.MAX_VALUE);
    }

    /**
     * Searches for a query and returns a ranked list of results. The results in the range
     * [startResult, endResult] contain the document properties set by setDocumentProperties().
     * startResult, endResult should be set to different values other than 0, Integer.MAX_VALUE only when we
     * want a small number of results.
     * @param query
     * @param startResult From 0 to Integer.MAX_VALUE
     * @param endResult From 0 to Integer.MAX_VALUE
     * @return
     * @throws IOException
     */
    public List<Pair<Object, Double>> search(String query, int startResult, int endResult) throws QueryExpansionException, IOException {
        boolean useStopwords = _indexer.useStopwords();
        boolean useStemmer = _indexer.useStemmer();
        int maxNewTermsForEachTerm = 1; //for each term, expand the query by one extra term
        List<QueryTerm> finalQuery = new ArrayList<>();

        //split query into terms and apply stopwords, stemming
        List<String> splitQuery = ProcessText.split(query);
        Set<String> splitQuerySet = new HashSet<>(splitQuery);
        if (useStopwords) {
            splitQuerySet.removeIf(StopWords::isStopWord);
        }
        if (useStemmer) {
            Set<String> splitQueryStemmedSet = new HashSet<>();
            for (String s : splitQuerySet) {
                splitQueryStemmedSet.add(ProcessText.applyStemming(s));
            }
            splitQuerySet = splitQueryStemmedSet;
        }

        //add the above result to the final query terms
        for (String s : splitQuerySet) {
            finalQuery.add(new QueryTerm(s, 1.0));
        }

        //expand query add the results to the final query terms
        List<List<QueryTerm>> expandedQuery;
        if (_queryExpansion != null) {
            expandedQuery = _queryExpansion.expandQuery(splitQuery);
            for (List<QueryTerm> queryTerms : expandedQuery) {
                int count = 0;
                for (QueryTerm queryTerm : queryTerms) {
                    if (count == maxNewTermsForEachTerm) {
                        break;
                    }
                    String term = queryTerm.getTerm();
                    if (useStopwords && StopWords.isStopWord(term)) {
                        continue;
                    }
                    if (useStemmer) {
                        term = ProcessText.applyStemming(term);
                    }
                    if (!splitQuerySet.contains(term)) {
                        finalQuery.add(new QueryTerm(term, queryTerm.getWeight()));
                        count++;
                    }
                }
            }
        }

        return _model.getRankedResults(finalQuery, _props, startResult, endResult);
    }

    /**
     * Prints a list of results in decreasing ranking order.
     * @param searchResults
     */
    public void printResults(List<Pair<Object, Double>> searchResults) {
        printResults(searchResults, 0, Integer.MAX_VALUE);
    }

    /**
     * Prints a list of results in decreasing ranking order from ranked position startResult to endResult.
     * Only the results from ranked position startResult to endResult are displayed.
     * @param searchResults
     * @param startResult From 0 to Integer.MAX_VALUE
     * @param endResult From 0 to Integer.MAX_VALUE
     */
    public void printResults(List<Pair<Object, Double>> searchResults, int startResult, int endResult) {
        if (searchResults.isEmpty()) {
            return;
        }

        /* startResult and endResult might be out of the range of the actual results.
        therefore we need to find the proper indexes of the first and last displayed result */
        int firstDisplayedResult = Math.max(startResult, 0);
        int lastDisplayedResult = Math.min(endResult, searchResults.size() - 1);
        if (firstDisplayedResult > lastDisplayedResult) {
            Themis.print("No results found in the specified range\n");
            return;
        }

        Themis.print("Displaying results " + firstDisplayedResult + " to " + lastDisplayedResult + "\n\n");

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
