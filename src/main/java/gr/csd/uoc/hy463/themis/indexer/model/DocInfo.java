package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.*;

/**
 * This class holds any information we might want to communicate with the
 * retrieval model we are implementing about a specific document
 *
 * Since probably we are going to store in memory a lot of these objects, we
 * have to be as memory efficient as we can. This implementation with a map is
 * worst than just keeping all properties as primitives and private members but
 * seems to be simpler to interact with
 *
 * ID and offset in document file are set only in the constructor
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

    private String id = "";         // the 40 byte id
    private long metaOffset = 0;        // offset in documents file
    private final Map<PROPERTY, Object> props = new HashMap<>(0);

    /**
     *
     * @param id the id of a document
     * @param metaOffset the offset in the document file the contains all
     * information for this document
     */
    public DocInfo(String id, long metaOffset) {
        this.id = id;
        this.metaOffset = metaOffset;
    }

    /**
     * Set property for this docID. Properties come from the PROPERY enum and
     * value is an object
     *
     * @param prop
     * @param value
     */
    public void setProperty(DocInfo.PROPERTY prop, Object value) {
        props.put(prop, value);
    }

    /**
     * Return the value of the property. Have to cast to appropriate value the
     * result in your code!
     *
     * @param prop
     * @return
     */
    public Object getProperty(DocInfo.PROPERTY prop) {
        return props.get(prop);
    }

    public String getId() {
        return id;
    }

    public long getMetaOffset() {
        return metaOffset;
    }

    public Set<PROPERTY> getProps() {
        return new HashSet<>(props.keySet());
    }

    public void clearProperty(DocInfo.PROPERTY prop) {
        props.remove(prop);
    }

    public void clearProperties(Set<DocInfo.PROPERTY> removeProps) {
        for (DocInfo.PROPERTY prop : removeProps) {
            props.remove(prop);
        }
    }

    public boolean hasProperty(DocInfo.PROPERTY prop) {
        return props.containsKey(prop);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DocInfo other = (DocInfo) o;
        return this.id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
