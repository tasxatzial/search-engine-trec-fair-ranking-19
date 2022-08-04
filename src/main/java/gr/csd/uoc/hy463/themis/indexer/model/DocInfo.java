package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.*;

/**
 * This class holds any information we might want to communicate with the retrieval model.
 * Each instance corresponds to a document.
 */
public class DocInfo {
    /* names of all possible document properties */
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

    /* The unique ID of each instance */
    private final int _docID;

    /* The properties for this instance along with their corresponding values */
    private final Map<PROPERTY, Object> _props = new HashMap<>(0);

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

    public int get_docID() {
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
     * Returns the offset to DOCUMENTS_ID_FILENAME
     *
     * @param docID
     * @return
     */
    public static long getDocIDOffset(int docID) {
        return (long) docID * DocumentStringID.SIZE;
    }

    /**
     * Returns a copy of the keys of the _props map.
     *
     * @return
     */
    public Set<PROPERTY> get_props() {
        return new HashSet<>(_props.keySet());
    }
}
