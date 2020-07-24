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

        //merge weights of the same terms
        query = mergeTerms(query);

        //get the relevant documents from the documents file
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

        //update the properties of these results that are in [startDoc, endDoc]
        updateDocInfo(results, props, startDoc, endDoc);

        return results;
    }
}