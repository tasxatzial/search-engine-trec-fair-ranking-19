package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Class that holds information about an entry in the 'documents_id' file
 */
public class DocumentIDEntry {

    /* sizes of individual records in an entry e.g. DOCID has size 40 */
    public static int DOCID_SIZE = 40;

    /* offset of individual records in an entry e.g. DOCID has offset 0 */
    public static int DOCID_OFFSET = 0;

    /* total size of all records in an entry */
    public static int totalSize = 40;
}
