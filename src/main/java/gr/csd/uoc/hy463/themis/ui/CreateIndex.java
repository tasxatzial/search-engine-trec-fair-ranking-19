package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.indexer.Indexer;

import java.io.IOException;

/**
 * The main class responsible for creating and deleting an index.
 */
public class CreateIndex {
    private final Indexer _indexer;

    /**
     * Constructor.
     *
     * Initializes the Indexer. Reads configuration options from themis.config file.
     *
     * @throws IOException
     */
    public CreateIndex()
            throws IOException {
        _indexer = new Indexer();
    }

    /**
     * Creates the index. Final index files will be saved in the INDEX_DIR directory.
     * Temporary files will be saved in the INDEX_TMP_DIR directory.
     *
     * @throws IOException
     */
    public void createIndex()
            throws IOException {
        _indexer.index();
    }

    /**
     * Deletes the INDEX_DIR and INDEX_TMP_DIR folders.
     *
     * @throws IOException
     */
    public void deleteIndex()
            throws IOException {
        _indexer.deleteIndex();
    }

    /**
     * Returns true if the INDEX_DIR folder is empty, false otherwise.
     *
     * @return
     */
    public boolean isIndexDirEmpty() {
        return _indexer.areIndexDirEmpty();
    }
}
