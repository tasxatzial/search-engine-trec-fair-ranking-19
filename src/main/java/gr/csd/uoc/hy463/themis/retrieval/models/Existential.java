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
package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of the Existential retrieval model. Returns the documents that
 * contain any of the terms of the query. For this model, there is no ranking of
 * documents, since all documents that have at least one term of the query, are
 * relevant and have a score 1.0
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class Existential extends ARetrievalModel {
    public Existential(Indexer index) {
        super(index);
    }

    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props) throws IOException {
        return getRankedResults(query, props, 0, Integer.MAX_VALUE);
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, Set<DocInfo.PROPERTY> props, int startDoc, int endDoc) throws IOException {
        List<Pair<Object, Double>> results = new ArrayList<>();

        query = removeDuplicateTerms(query);
        fetchEssentialDocInfo(query, props, startDoc, endDoc);

        //remove the duplicates
        Set<DocInfo> resultsSet = new HashSet<>();
        for (List<DocInfo> termDocInfo : _termsDocInfo) {
            resultsSet.addAll(termDocInfo);
        }

        //create the results list
        for (DocInfo docInfo : resultsSet) {
            results.add(new Pair<>(docInfo, 1.0));
        }

        //update the props of the results that are in [startDoc, endDoc]
        updateDocInfo(results, props, startDoc, endDoc);

        return results;
    }
}