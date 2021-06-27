package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Holds the required data for an entry in the 'postings' file:
 * - TF = frequency of the term in the relevant document
 * - The int ID of the relevant document
 */
public class Posting {
    private final int _tf;
    private final int _intID;

    /* sizes of individual records in an entry e.g. TF has size 4
     * Records appear in the file in the same order as here */
    public static int TF_SIZE = 4;
    public static int INTID_SIZE = 4;

    /* offset of individual records in an entry e.g. TF has offset 0
    Records appear in the file in the same order as here */
    public static int TF_OFFSET = 0;
    public static int INTID_OFFSET = 4;

    /* total size of all records in an entry */
    public static int totalSize = TF_SIZE + INTID_SIZE;

    public Posting(int tf, int intID) {
        _tf = tf;
        _intID = intID;
    }

    public int get_tf() {
        return _tf;
    }

    public int get_intID() {
        return _intID;
    }
}
