package gr.csd.uoc.hy463.themis.utils;

/**
 * Class that holds basic information about a posting entry:
 * the TF and the Pointer to the documents file. It also holds the size of
 * those fields (bytes) and the total size of a posting entry (bytes).
 */
public class PostingEntry {
    private int _tf;
    private long _docPointer;
    public static int TF_SIZE = 4;
    public static int POINTER_SIZE = 8;
    public static int SIZE = TF_SIZE + POINTER_SIZE;

    public PostingEntry(int tf, long docPointer) {
        _tf = tf;
        _docPointer = docPointer;
    }

    public Integer get_tf() {
        return _tf;
    }

    public Long get_docPointer() {
        return _docPointer;
    }
}
