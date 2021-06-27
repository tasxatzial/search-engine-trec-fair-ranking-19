package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.*;

/**
 * This class holds any information we might want to communicate with the retrieval model.
 * The DocInfo.PROPERTY corresponds to the JSON field names of the documents.
 */
public class DocInfo {
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

    /* Keep an integer as the ID of each Object */
    private final int _id;

    /* the JSON properties this object currently has */
    private final Map<PROPERTY, Object> _props = new HashMap<>(0);

    public DocInfo(int id) {
        _id = id;
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

    public int get_id() {
        return _id;
    }

    /**
     * Returns the offset to the documents_meta file from the specified int ID
     *
     * @param id
     * @return
     */
    public static long getMetaOffset(int id) {
        return (long) id * DocumentMetaEntry.totalSize;
    }

    /**
     * Returns the offset to the documents_id file from the specified int ID
     *
     * @param id
     * @return
     */
    public static long getDocIdOffset(int id) {
        return (long) id * DocumentIDEntry.totalSize;
    }

    /**
     * Returns a copy of the JSON properties that this DocInfo currently has
     *
     * @return
     */
    public Set<PROPERTY> get_props() {
        return new HashSet<>(_props.keySet());
    }
}
