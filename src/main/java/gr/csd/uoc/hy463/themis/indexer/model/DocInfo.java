package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.*;

/**
 * This class holds any information we might want to communicate with the
 * retrieval model we are implementing about a specific document
 *
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

    private final int id;
    private Map<PROPERTY, Object> props = new HashMap<>(0);

    /**
     * @param id the int id of a document
     */
    public DocInfo(int id) {
        this.id = id;
    }

    public void setProperty(DocInfo.PROPERTY prop, Object value) {
        props.put(prop, value);
    }

    public Object getProperty(DocInfo.PROPERTY prop) {
        return props.get(prop);
    }

    public void clearProperty(DocInfo.PROPERTY prop) {
        props.remove(prop);
    }

    public boolean hasProperty(DocInfo.PROPERTY prop) {
        return props.containsKey(prop);
    }

    public int getId() {
        return id;
    }

    public static long getMetaOffset(int id) {
        return (long) id * DocumentMetaEntry.totalSize;
    }

    public static long getDocIdOffset(int id) {
        return (long) id * DocumentIDEntry.totalSize;
    }

    public static int getIntId(long docMetaOffset) {
        return (int) (docMetaOffset / DocumentMetaEntry.totalSize);
    }

    public Set<PROPERTY> getProps() {
        return new HashSet<>(props.keySet());
    }
}
