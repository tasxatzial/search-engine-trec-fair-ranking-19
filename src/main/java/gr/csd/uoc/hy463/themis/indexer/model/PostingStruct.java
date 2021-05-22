package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * This class is used during the creation of the partial indexes and also during search.
 * It holds basic information about a posting entry: (tf, intID of relevant document)
 * It also holds the size of those fields (bytes) and the total size of a posting entry (bytes).
 */
public class PostingStruct {
    private int _tf;
    private int _intID;
    public static int TF_SIZE = 4;
    public static int TF_OFFSET = 0;
    public static int INTID_SIZE = 4;
    public static int INTID_OFFSET = 4;
    public static int SIZE = TF_SIZE + INTID_SIZE;

    public PostingStruct(int tf, int intID) {
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
