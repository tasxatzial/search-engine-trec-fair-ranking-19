package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.TermPostings;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of the Existential retrieval model. Returns the documents that
 * contain any of the terms of the query. For this model, there is no ranking of
 * documents, since all documents that have at least one term of the query, are
 * relevant and have a score 1.0
 */
public class Existential extends ARetrievalModel {
    boolean[] _valid;

    public Existential(Indexer index)
            throws IOException {
        super(index);
        _valid = new boolean[_totalDocuments];
    }

    @Override
    public List<Result> getRankedResults(List<QueryTerm> query, int endResult)
            throws IOException, IndexNotLoadedException {
        List<Result> results = new ArrayList<>();
        _totalResults = 0;

        for (int i = 0; i < _totalDocuments; i++) {
            _valid[i] = false;
        }

        //keep only one term if it appears multiple times
        query = mergeTerms(query);

        int[] DFs = _indexer.getDFs(query);
        for (int i = 0; i < query.size(); i++) {
            TermPostings termPostings = _indexer.getPostings(query.get(i).get_term());
            int[] intIDs = termPostings.getIntID();
            for (int j = 0; j < DFs[i]; j++) {
                _valid[intIDs[j]] = true;
            }
        }

        for (int i = 0; i < _totalDocuments; i++) {
            if (_valid[i]) {
                DocInfo docInfo = new DocInfo(i);
                results.add(new Result(docInfo, 1.0));
            }
        }

        _totalResults = results.size();
        return results;
    }
}