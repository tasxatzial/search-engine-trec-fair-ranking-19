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
package gr.csd.uoc.hy463.themis.indexer.indexes;

import gr.csd.uoc.hy463.themis.config.Config;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class holds all information related to a specific (partial or not) index
 * in memory. It also knows how to store this information in files
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class Index {

    // Partial indexes have an id > 0 and corresponding idx files are stored in
    // INDEX_PATH/id while for a full index, idx files are stored in INDEX_PATH
    // e.g., the first partial index files are saved to INDEX_PATH/1/
    private int id = 0; // the id of the index that is used for partial indexes

    private static final Logger __LOGGER__ = LogManager.getLogger(Index.class);
    private Config __CONFIG__;  // configuration options

    // The path of index
    private String __INDEX_PATH__ = null;
    // Filenames of indexes
    private String __VOCABULARY_FILENAME__ = null;
    private String __POSTINGS_FILENAME__ = null;
    private String __DOCUMENTS_FILENAME__ = null;

    // We also need to store any information about the vocabulary,
    // posting and document file in memory
    // For example a TreeMap holds entries sorted which helps with storing the
    // vocabulary file
    private TreeMap<String, Integer> __VOCABULARY__ = null;

    // We have to hold also other appropriate data structures for postings / documents
    public Index(Config config) {
        __CONFIG__ = config;
        init();
    }

    /**
     * Initialize things
     */
    private void init() {
        __VOCABULARY_FILENAME__ = __CONFIG__.getVocabularyFileName();
        __POSTINGS_FILENAME__ = __CONFIG__.getPostingsFileName();
        __DOCUMENTS_FILENAME__ = __CONFIG__.getDocumentsFileName();
        __INDEX_PATH__ = __CONFIG__.getIndexPath();
    }

    /**
     * This method is responsible for dumping all information held by this index
     * to the filesystem in the directory INDEX_PATH/id. If id = 0 then it dumps
     * every idx files to the INDEX_PATH
     *
     * Specifically, it creates:
     *
     * =========================================================================
     * 1) VOCABULARY FILE => vocabulary.idx (Normal Sequential file)
     *
     * This is a normal sequential file where we write in lexicographic order
     * the following entries separated with space: | TERM (a term of the
     * vocabulary) | DF document frequency of this term | POINTER_TO_POSTING
     * (the offset in the posting.idx) |
     *
     * =========================================================================
     * 2) POSTING FILE => posting.idx (Random Access File)
     *
     * For each entry it stores: |DOCUMENT_ID (40 ASCII chars - 40 bytes)| |TF
     * (int 4 bytes) | POINTER_TO_DOCUMENT_FILE (long 4 bytes)
     *
     * =========================================================================
     * 3) DOCUMENTS FILE => documents.idx (Random Access File)
     *
     * For each entry it stores: | Title (variable bytes) | Author1,Author2,
     * ...,Author_k (variable size) | Year (2 bytes short)| Journal Name
     * (variable bytes) | The weight of Document (double - 8 bytes)| Length of
     * Document (int - 4 bytes) | PageRank Score (double - 8 bytes => this will
     * be used in the second phase of the project)
     *
     *
     * ==> IMPORTANT NOTES
     *
     * For strings that have a variable size, just add as an int (4 bytes)
     * prefix storing the size in bytes of the string
     *
     * Authors are separated by a comma
     *
     * Author ids are also separated with a comma
     *
     * The weight of the document will be computed after indexing the whole
     * collection by scanning the whole postings list
     *
     * For now add 0.0 for PageRank score (a team will be responsible for
     * computing it in the second phase of the project
     *
     *
     * @return
     */
    public boolean dump() {
        if (id == 0) {
            // dump to INDEX_PATH
        } else {
            // dump to INDEX_PATH/id
        }
        return false;
    }

    public void setID(int id) {
        this.id = id;
    }

    /**
     * Returns if index is partial
     *
     * @return
     */
    public boolean isPartial() {
        return id != 0;
    }

}
