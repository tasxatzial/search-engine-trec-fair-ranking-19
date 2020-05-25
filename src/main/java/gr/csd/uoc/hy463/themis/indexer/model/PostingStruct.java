package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * This class is used during the creation of the partial indexes and also during search.
 * It holds basic information about a posting entry: (tf, document pointer)
 * It also holds the size of those fields (bytes) and the total size of a posting entry (bytes).
 */
public class PostingStruct {
    private int _tf;
    private long _docPointer;
    public static int TF_SIZE = 4;
    public static int POINTER_SIZE = 8;
    public static int SIZE = TF_SIZE + POINTER_SIZE;

    public PostingStruct(int tf, long docPointer) {
        _tf = tf;
        _docPointer = docPointer;
    }

    public int get_tf() {
        return _tf;
    }

    public long get_docPointer() {
        return _docPointer;
    }
}
