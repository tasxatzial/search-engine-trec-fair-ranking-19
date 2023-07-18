package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.*;

/**
 * This class holds any information we might want to communicate with the retrieval model.
 * Each instance corresponds to a document.
 */
public class DocInfo {
    /* names of all possible document properties except the string doc ID */
    public enum PROPERTY {
        TITLE,
        ABSTRACT,
        AUTHORS_NAMES,
        AUTHORS_IDS,
        YEAR,
        JOURNAL_NAME,
        AVG_AUTHOR_RANK,
        ENTITIES,
        FIELDS_OF_STUDY,
        VENUE,
        SOURCES,
        CITATIONS_PAGERANK,
        VSM_WEIGHT,
        TOKEN_COUNT,
        MAX_TF,
        DOCUMENT_SIZE
    }

    /* The unique (int) ID of each instance. To save space, we'll be storing an int instead of the string doc ID
    * found in the JSON entries */
    private final int _docID;

    /* The document properties associated with this DocInfo along with their corresponding values */
    private final Map<PROPERTY, Object> _props = new HashMap<>(0);

    /**
     * Constructor.
     *
     * @param docID
     */
    public DocInfo(int docID) {
        _docID = docID;
    }

    public void setProperty(DocInfo.PROPERTY prop, Object value) {
        _props.put(prop, value);
    }

    public Object getProperty(DocInfo.PROPERTY prop) {
        return _props.get(prop);
    }

    public void clearProperty(DocInfo.PROPERTY prop) {
        _props.remove(prop);
    }

    public boolean hasProperty(DocInfo.PROPERTY prop) {
        return _props.containsKey(prop);
    }

    public int getDocID() {
        return _docID;
    }

    /**
     * Returns the offset to DOCUMENTS_META_FILENAME
     *
     * @param docID
     * @return
     */
    public static long getMetaOffset(int docID) {
        return (long) docID * DocumentMetaEntry.SIZE;
    }

    /**
     * Returns the offset to DOCUMENTS_ID_FILENAME, useful for retrieving the string doc ID
     *
     * @param docID
     * @return
     */
    public static long getDocIDOffset(int docID) {
        return (long) docID * DocumentStringID.SIZE;
    }

    /**
     * Returns a copy of the document properties associated with this DocInfo
     *
     * @return
     */
    public Set<PROPERTY> getProps() {
        return new HashSet<>(_props.keySet());
    }
}
