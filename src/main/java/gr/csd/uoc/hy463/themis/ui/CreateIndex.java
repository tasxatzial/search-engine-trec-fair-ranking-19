package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.config.Exceptions.ConfigLoadException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.linkAnalysis.Exceptions.PagerankException;

import java.io.IOException;

/**
 * This class runs the indexers
 */
public class CreateIndex {
    private Indexer _indexer;

    public CreateIndex() throws IOException, ConfigLoadException {
        _indexer = new Indexer();
    }

    public void createIndex() throws IOException, PagerankException {
        _indexer.index();
    }

    public void deleteIndex() throws IOException {
        _indexer.deleteIndex();
    }

    public boolean isIndexDirEmpty() {
        return _indexer.isIndexDirEmpty();
    }
}
