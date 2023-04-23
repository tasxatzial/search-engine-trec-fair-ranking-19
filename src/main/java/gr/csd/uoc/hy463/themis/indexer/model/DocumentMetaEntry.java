package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Represents info about an entry in DOCUMENTS_META_FILENAME.
 *
 * For example, the (int) ID of a document is 4 bytes and can be found in position 0.
 */
public class DocumentMetaEntry {
    /* The ID of the document (int => 4 bytes) */
    public static int DOCID_SIZE = 4;
    public static int DOCID_OFFSET = 0;

    /* The weight (norm) of the document (double => 8 bytes) */
    public static int VSM_WEIGHT_SIZE = 8;
    public static int VSM_WEIGHT_OFFSET = 4;

    /* The max TF in the document (int => 4 bytes) */
    public static int MAX_TF_SIZE = 4;
    public static int MAX_TF_OFFSET = 12;

    /* Number of tokens in the 'documents' file (int => 4 bytes) */
    public static int TOKEN_COUNT_SIZE = 4;
    public static int TOKEN_COUNT_OFFSET = 16;

    /* Citations PageRank Score (double => 8 bytes) */
    public static int DOCUMENT_PAGERANK_SIZE = 8;
    public static int DOCUMENT_PAGERANK_OFFSET = 20;

    /* Average author rank (double => 8 bytes) */
    public static int AVG_AUTHOR_RANK_SIZE = 8;
    public static int AVG_AUTHOR_RANK_OFFSET = 28;

    /* Size of an entry in the 'documents' file (int => 4 bytes) */
    public static int DOCUMENT_SIZE_SIZE = 4;
    public static int DOCUMENT_SIZE_OFFSET = 36;

    /* Offset to the 'documents' file (long => 8 bytes) */
    public static int DOCUMENT_OFFSET_SIZE = 8;
    public static int DOCUMENT_OFFSET_OFFSET = 40;

    /* total size of an entry */
    public static int SIZE = DOCID_SIZE + VSM_WEIGHT_SIZE + MAX_TF_SIZE + TOKEN_COUNT_SIZE +
            DOCUMENT_PAGERANK_SIZE + AVG_AUTHOR_RANK_SIZE + DOCUMENT_SIZE_SIZE + DOCUMENT_OFFSET_SIZE;
}
