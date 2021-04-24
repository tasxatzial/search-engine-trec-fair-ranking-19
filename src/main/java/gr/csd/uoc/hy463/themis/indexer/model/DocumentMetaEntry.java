package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Class that holds information about a document meta entry in the documents_meta file.
 */
public class DocumentMetaEntry {
    public static int INTID_SIZE = 4;
    public static int VSM_WEIGHT_SIZE = 8;
    public static int MAX_TF_SIZE = 4;
    public static int TOKEN_COUNT_SIZE = 4;
    public static int CITATIONS_PAGERANK_SIZE = 8;
    public static int AVG_AUTHOR_RANK_SIZE = 8;
    public static int DOCUMENT_SIZE_SIZE = 4;
    public static int DOCUMENT_OFFSET_SIZE = 8;

    public static int totalSize = INTID_SIZE + VSM_WEIGHT_SIZE + MAX_TF_SIZE + TOKEN_COUNT_SIZE +
            CITATIONS_PAGERANK_SIZE + AVG_AUTHOR_RANK_SIZE + DOCUMENT_SIZE_SIZE + DOCUMENT_OFFSET_SIZE;

    public static int INTID_OFFSET = 0;
    public static int VSM_WEIGHT_OFFSET = 4;
    public static int MAX_TF_OFFSET = 12;
    public static int TOKEN_COUNT_OFFSET = 16;
    public static int CITATIONS_PAGERANK_OFFSET = 20;
    public static int AVG_AUTHOR_RANK_OFFSET = 28;
    public static int DOCUMENT_SIZE_OFFSET = 36;
    public static int DOCUMENT_OFFSET_OFFSET = 40;
}
