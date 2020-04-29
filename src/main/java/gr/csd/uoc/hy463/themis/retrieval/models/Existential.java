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
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFull;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, RESULT_TYPE type) throws IOException {
        return getRankedResults(query, type, -1);
    }

    @Override
    public List<Pair<Object, Double>> getRankedResults(List<QueryTerm> query, RESULT_TYPE type, int topk) throws IOException {
        List<String> terms = new ArrayList<>();
        List<Pair<Object, Double>> result = new ArrayList<>();

        for (QueryTerm q : query) {
            terms.add(q.getTerm());
        }

        if (type == RESULT_TYPE.PLAIN) {
            List<List<String>> docInfoPlain = null;
            docInfoPlain = indexer.getDocId(terms);
            for (List<String> termDocInfoPlain : docInfoPlain) {
                for (String docId : termDocInfoPlain) {
                    if (result.size() == topk) {
                        return result;
                    }
                    result.add(new Pair<>(docId, 1.0));
                }
            }
        }
        if (type == RESULT_TYPE.ESSENTIAL) {
            List<List<DocInfoEssential>> docInfoEssential_list = null;
            docInfoEssential_list = indexer.getDocInfoEssentialForTerms(terms);
            for (List<DocInfoEssential> termDocInfoEssential : docInfoEssential_list) {
                for (DocInfoEssential docInfo : termDocInfoEssential) {
                    if (result.size() == topk) {
                        return result;
                    }
                    result.add(new Pair<>(docInfo, 1.0));
                }
            }
        }
        else if (type == RESULT_TYPE.FULL) {
            List<List<DocInfoFull>> docInfoFull_list = null;
            docInfoFull_list = indexer.getDocInfoFullTerms(terms);
            for (List<DocInfoFull> termDocInfoFull : docInfoFull_list) {
                for (DocInfoFull docInfo : termDocInfoFull) {
                    if (result.size() == topk) {
                        return result;
                    }
                    result.add(new Pair<>(docInfo, 1.0));
                }
            }
        }
        return result;
    }
}
