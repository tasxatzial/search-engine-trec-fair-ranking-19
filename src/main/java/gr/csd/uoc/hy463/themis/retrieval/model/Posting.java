package gr.csd.uoc.hy463.themis.retrieval.model;

/**
 * Class that holds the postings of a term, organized in two arrays. One for the TF and one for the offsets
 * to the documents_meta file.
 */
public class Posting {
    private int[] tfs;
    private long[] docMetaOffsets;

    public Posting(int[] tfs, long[] docMetaOffsets) {
        this.tfs = tfs;
        this.docMetaOffsets = docMetaOffsets;
    }

    public int[] getTfs() {
        return tfs;
    }

    public long[] getDocMetaOffsets() {
        return docMetaOffsets;
    }
}
