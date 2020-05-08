/*
 * themis - A fair search engine for scientific articles
 *
 * Currently over the Semantic Scholar Open Research Corpus
 * http://s2-public-api-prod.us-west-2.elasticbeanstalk.com/corpus/
 *
 * Collaborative work with the undergraduate/graduate students of
 * Information Retrieval Systems (hy463) course
 * Spring Semester 2020
 *
 * -- Writing code during COVID-19 pandemic times :-( --
 *
 * Aiming to participate in TREC 2020 Fair Ranking Track
 * https://fair-trec.github.io/
 *
 * Computer Science Department http://www.csd.uoc.gr
 * University of Crete
 * Greece
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.indexer.model;

import java.util.HashMap;
import java.util.Map;

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
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class DocInfo {

    public enum PROPERTY {
        TITLE,
        AUTHORS_NAMES,
        AUTHORS_IDS,
        AVG_AUTHOR_RANK,
        YEAR,
        JOURNAL_NAME,
        ABSTRACT,
        ENTITIES,
        FIELDS_OF_STUDY,
        VENUE,
        SOURCES,
        PAGERANK, // pagerank score for 2nd phase (Value should be double)
        WEIGHT, // weight (norm) of document VSM (Value should be double)
        LENGTH   // for OkapiBM25 (Value should be integer)
    }

    private String id = "";         // the 40 byte id
    private long offset = 0;        // offset in documents file
    private final Map<PROPERTY, Object> props = new HashMap<>(4);

    /**
     *
     * @param id the id of a document
     * @param offset the offset in the document file the contains all
     * information for this document
     */
    public DocInfo(String id, long offset) {
        this.id = id;
        this.offset = offset;
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

    public long getOffset() {
        return offset;
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
