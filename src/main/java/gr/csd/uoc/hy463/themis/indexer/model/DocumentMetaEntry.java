package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Class that holds information about a document meta entry in the documents_meta file.
 */
public class DocumentMetaEntry {
    public static int ID_SIZE = 40;
    public static int WEIGHT_SIZE = 8;
    public static int MAX_TF_SIZE = 4;
    public static int LENGTH_SIZE = 4;
    public static int PAGERANK_SIZE = 8;
    public static int AVG_AUTHOR_RANK_SIZE = 8;
    public static int DOCUMENT_SIZE_SIZE = 4;
    public static int DOCUMENT_OFFSET_SIZE = 8;

    public static int totalSize = ID_SIZE + WEIGHT_SIZE + MAX_TF_SIZE + LENGTH_SIZE+
            PAGERANK_SIZE + AVG_AUTHOR_RANK_SIZE + DOCUMENT_SIZE_SIZE + DOCUMENT_OFFSET_SIZE;

    public static int ID_OFFSET = 0;
    public static int WEIGHT_OFFSET = 40;
    public static int MAX_TF_OFFSET = 48;
    public static int LENGTH_OFFSET = 52;
    public static int PAGERANK_OFFSET = 56;
    public static int AVG_AUTHOR_RANK_OFFSET = 64;
    public static int DOCUMENT_SIZE_OFFSET = 72;
    public static int DOCUMENT_OFFSET_OFFSET = 76;
}
