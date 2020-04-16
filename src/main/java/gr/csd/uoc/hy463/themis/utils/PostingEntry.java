package gr.csd.uoc.hy463.themis.utils;

/**
 * Class that holds basic information about a posting entry:
 * the TF and the Pointer to the documents file. We can also use it to
 * get the size of the posting entry in bytes.
 */
public class PostingEntry {
    private int _tf;
    private long _docPointer;
    public static int POSTING_SIZE = 12;

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
