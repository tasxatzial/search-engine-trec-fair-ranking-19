package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.indexer.Indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class runs the indexers
 */
public class CreateIndex {
    private Indexer _indexer;

    public CreateIndex() throws IOException {
        _indexer = new Indexer();
    }

    public void createIndex() throws IOException {
        _indexer.index();
    }

    public void deleteIndex() throws IOException {
        _indexer.deleteIndex();
    }

    public boolean isIndexDirEmpty() {
        return _indexer.isIndexDirEmpty();
    }
}
