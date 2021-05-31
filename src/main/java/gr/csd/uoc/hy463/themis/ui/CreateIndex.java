package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.config.Exceptions.ConfigLoadException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.linkAnalysis.Exceptions.PagerankException;

import java.io.IOException;

/**
 * This class is used for creating and deleting an index
 */
public class CreateIndex {
    private final Indexer _indexer;

    /**
     * Constructor.
     *
     * Initializes the Indexer. Reads configuration options from themis.config file.
     *
     * @throws IOException
     * @throws ConfigLoadException
     */
    public CreateIndex()
            throws IOException, ConfigLoadException {
        _indexer = new Indexer();
    }

    /**
     * Starts the indexing. The final index files will be saved in the 'index' folder. The temporary files will be
     * saved in the 'index_tmp' folder, however they will have been deleted at the end of the indexing process.
     *
     * @throws IOException
     * @throws PagerankException
     */
    public void createIndex()
            throws IOException, PagerankException {
        _indexer.index();
    }

    /**
     * Deletes the 'index' and 'index_tmp' folders
     *
     * @throws IOException
     */
    public void deleteIndex()
            throws IOException {
        _indexer.deleteIndex();
    }

    /**
     * Returns true iff the 'index' folder is empty, false otherwise
     *
     * @return
     */
    public boolean isIndexDirEmpty() {
        return _indexer.isIndexDirEmpty();
    }
}
