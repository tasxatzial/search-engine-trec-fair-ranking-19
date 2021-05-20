package gr.csd.uoc.hy463.themis.retrieval.models;

import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.Posting;
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
    boolean[] valid;

    public Existential(Indexer index) {
        super(index);
        valid = new boolean[totalArticles];
    }

    @Override
    public List<Pair<DocInfo, Double>> getRankedResults(List<QueryTerm> query, int endResult) throws IOException {
        List<Pair<DocInfo, Double>> results = new ArrayList<>();
        totalResults = 0;

        for (int i = 0; i < totalArticles; i++) {
            valid[i] = false;
        }

        //merge weights of the same terms
        query = mergeTerms(query);

        int[] dfs = _indexer.getDf(query);
        for (int i = 0; i < query.size(); i++) {
            Posting postings = _indexer.getPostings(query.get(i).getTerm());
            long[] docMetaOffsets = postings.getDocMetaOffsets();
            for (int j = 0; j < dfs[i]; j++) {
                valid[DocInfo.getIntId(docMetaOffsets[j])] = true;
            }
        }

        for (int i = 0; i < totalArticles; i++) {
            if (valid[i]) {
                DocInfo docInfo = new DocInfo(i);
                results.add(new Pair<>(docInfo, 1.0));
            }
        }

        totalResults = results.size();
        return results;
    }
}