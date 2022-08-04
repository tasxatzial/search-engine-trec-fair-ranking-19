package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Stores the required data for an entry in POSTINGS_FILENAME:
 * - TF (frequency of a term in the relevant document)
 * - The ID of the relevant document
 */
public class Posting {
    private final int _TF;
    private final int _docID;

    /* int => 4 bytes */
    public static int TF_SIZE = 4;
    public static int TF_OFFSET = 0;

    /* int => 4 bytes */
    public static int DOCID_SIZE = 4;
    public static int DOCID_OFFSET = 4;

    /* total size of all records in an entry */
    public static int SIZE = TF_SIZE + DOCID_SIZE;

    public Posting(int TF, int docID) {
        _TF = TF;
        _docID = docID;
    }

    public int getTF() {
        return _TF;
    }

    public int getDocID() {
        return _docID;
    }
}
