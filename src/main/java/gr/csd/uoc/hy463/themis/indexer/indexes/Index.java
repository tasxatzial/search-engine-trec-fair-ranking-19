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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential;
import gr.csd.uoc.hy463.themis.utils.Pair;
import gr.csd.uoc.hy463.themis.utils.PartialIndexStruct;
import gr.csd.uoc.hy463.themis.utils.PostingEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class holds all information related to a specific (partial or not) index
 * in memory. It also knows how to store this information to files
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

    // We also need to store any information about the vocabulary and
    // posting file in memory
    // For example a TreeMap holds entries sorted which helps with storing the
    // vocabulary file
    private TreeMap<String, PartialIndexStruct> __INDEX__ = null;

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
        __INDEX_PATH__ = __CONFIG__.getIndexPath();
        __INDEX__ = new TreeMap<>();
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
     * the following entries separated by space: | TERM (a term of the
     * vocabulary) | DF document frequency of this term | POINTER_TO_POSTING
     * (the offset in the posting.idx, this is a long number) |
     *
     * =========================================================================
     * 2) POSTING FILE => posting.idx (Random Access File)
     *
     * For each entry it stores: | TF (int => 4 bytes) |
     * POINTER_TO_DOCUMENT_FILE (long => 4 bytes) |
     *
     * @return
     */
    public boolean dump() throws IOException {
        String vocabularyName;
        String postingsName;
        vocabularyName = __INDEX_PATH__ + "/" + id + "/" + __VOCABULARY_FILENAME__;
        postingsName = __INDEX_PATH__ + "/" + id + "/" + __POSTINGS_FILENAME__;
        Files.createDirectories(Paths.get(vocabularyName).getParent());
        Files.createDirectories(Paths.get(postingsName).getParent());
        dumpVocabulary(vocabularyName);
        dumpPostings(postingsName);

        return true;
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

    /**
     * Adds the map of term frequencies of a textual entry to the partial index.
     *
     * @param entryWords The map of term frequencies
     * @param docOffset The offset to the document files
     * @throws IOException
     */
    public void add(Map<String, List<Pair<DocInfoEssential.PROPERTY, Integer>>> entryWords, long docOffset,
                    BufferedWriter tfWriter) throws IOException {
        for (Map.Entry<String, List<Pair<DocInfoEssential.PROPERTY, Integer>>> entry : entryWords.entrySet()) {
            int tf = 0;
            String key = entry.getKey();
            PartialIndexStruct indexStruct = __INDEX__.get(key);
            for (Pair<DocInfoEssential.PROPERTY, Integer> pair : entry.getValue()) {
                tf += pair.getR();
            }
            if (indexStruct != null) {
                indexStruct.set_df(indexStruct.get_df() + 1);
                indexStruct.get_postings().add(new PostingEntry(tf, docOffset));
            }
            else {
                indexStruct = new PartialIndexStruct(1);
                indexStruct.get_postings().add(new PostingEntry(tf, docOffset));
                __INDEX__.put(key, indexStruct);
            }
            tfWriter.write(key + " " + tf + " ");
        }
        tfWriter.write("\n");
    }

    /* Dumps the appropriate info from a partial index memory struct to the
    appropriate partial vocabulary file */
    private void dumpVocabulary(String filename) throws IOException {
        BufferedWriter file = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(filename), "UTF-8"));
        long offset = 0;
        for (Map.Entry<String, PartialIndexStruct> pair : __INDEX__.entrySet()) {
            file.write(pair.getKey() + " " + pair.getValue().get_df() + " " + offset + "\n");
            offset += pair.getValue().get_postings().size() * PostingEntry.POSTING_SIZE;
        }
        file.close();
    }

    /* Dumps the appropriate info from a partial index memory struct to the
    appropriate partial postings file */
    private void dumpPostings(String filename) throws IOException {
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream
                (new RandomAccessFile(filename, "rw").getFD()));
        byte[] tf;
        byte[] offset;
        for (PartialIndexStruct index : __INDEX__.values()) {
            for (PostingEntry postings : index.get_postings()) {
                tf = ByteBuffer.allocate(4).putInt(postings.get_tf()).array();
                offset = ByteBuffer.allocate(8).putLong(postings.get_docPointer()).array();
                out.write(tf);
                out.write(offset);
            }
        }
        out.close();
    }
}
