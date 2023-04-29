package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.Exceptions.IncompleteFileException;
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
     * Starts the indexing. The final index files will be saved in the INDEX_PATH directory. The temporary files
     * will be saved in the INDEX_TMP_PATH directory.
     *
     * @throws IOException
     * @throws IncompleteFileException
     */
    public void createIndex()
            throws IOException, IncompleteFileException {
        _indexer.index();
    }

    /**
     * Deletes the INDEX_PATH and INDEX_TMP_PATH folders.
     *
     * @throws IOException
     */
    public void deleteIndex()
            throws IOException {
        _indexer.deleteIndex();
    }

    /**
     * Returns true if the INDEX_PATH folder is empty, false otherwise.
     *
     * @return
     */
    public boolean isIndexDirEmpty() {
        return _indexer.areIndexDirEmpty();
    }
}
