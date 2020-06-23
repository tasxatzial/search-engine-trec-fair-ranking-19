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

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFrequency;
import gr.csd.uoc.hy463.themis.utils.Pair;
import gr.csd.uoc.hy463.themis.indexer.model.PartialIndexStruct;
import gr.csd.uoc.hy463.themis.indexer.model.PostingStruct;
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
    private String __INDEX_TMP_PATH__ = null;
    // Filenames of indexes
    private String __VOCABULARY_FILENAME__ = null;
    private String __POSTINGS_FILENAME__ = null;

    // We also need to store any information about the vocabulary and
    // posting file in memory.
    private Map<String, PartialIndexStruct> __INDEX__ = null;

    // sorted list of the keys of the vocabulary
    private List<String> __INDEX_KEYS_SORTED__ = null;

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
        __INDEX_TMP_PATH__ = __CONFIG__.getIndexTmpPath();
        __INDEX__ = new HashMap<>();
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
        __INDEX_KEYS_SORTED__ = new ArrayList<>(__INDEX__.keySet());
        Collections.sort(__INDEX_KEYS_SORTED__);
        String vocabularyName = __INDEX_TMP_PATH__ + "/" + id + "/" + __VOCABULARY_FILENAME__;
        String postingsName = __INDEX_TMP_PATH__ + "/" + id + "/" + __POSTINGS_FILENAME__;
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
     * Adds the map of term frequencies of a textual entry to the partial index. Returns
     * the total number of frequencies.
     *
     * @param entryWords The map of term frequencies
     * @param docOffset The offset to the document files
     * @return
     * @throws IOException
     */
    public int add(Map<String, List<DocInfoFrequency>> entryWords, BufferedWriter termFreqWriter, long docOffset) throws IOException {
        StringBuilder sb = new StringBuilder();
        int totalTf = 0;
        for (Map.Entry<String, List<DocInfoFrequency>> entry : entryWords.entrySet()) {
            int tf = 0;
            String key = entry.getKey();
            PartialIndexStruct indexStruct = __INDEX__.get(key);
            for (DocInfoFrequency docInfoFrequency : entry.getValue()) {
                tf += docInfoFrequency.get_frequency();
                totalTf += tf;
            }
            if (indexStruct != null) {
                indexStruct.incr_df();
                indexStruct.get_postings().add(new PostingStruct(tf, docOffset));
            }
            else {
                indexStruct = new PartialIndexStruct();
                indexStruct.get_postings().add(new PostingStruct(tf, docOffset));
                __INDEX__.put(key, indexStruct);
            }
            sb.append(key).append(' ').append(tf).append(' ');
        }
        sb.append('\n');
        termFreqWriter.write(sb.toString());
        return totalTf;
    }

    /* Dumps the appropriate info from a partial index memory struct to the
    appropriate partial vocabulary file */
    private void dumpVocabulary(String filename) throws IOException {
        BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), "UTF-8"));
        long offset = 0;
        for (String term : __INDEX_KEYS_SORTED__) {
            int df = __INDEX__.get(term).get_df();
            vocabularyWriter.write(term + ' ' + df + ' ' + offset + '\n');
            offset += df * PostingStruct.SIZE;
        }
        vocabularyWriter.close();
    }

    /* Dumps the appropriate info from a partial index memory struct to the
    appropriate partial postings file */
    private void dumpPostings(String filename) throws IOException {
        BufferedOutputStream postingsWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(filename, "rw").getFD()));
        for (String term : __INDEX_KEYS_SORTED__) {
            List<PostingStruct> postings = __INDEX__.get(term).get_postings();
            byte[] postingsArray = new byte[postings.size() * PostingStruct.SIZE];
            ByteBuffer postingsBuf = ByteBuffer.wrap(postingsArray);
            int offset = 0;
            for (PostingStruct posting : postings) {
                postingsBuf.putInt(offset, posting.get_tf());
                postingsBuf.putLong(offset + PostingStruct.POINTER_OFFSET, posting.get_docPointer());
                offset += PostingStruct.SIZE;
            }
            postingsWriter.write(postingsArray);
        }
        postingsWriter.close();
    }
}
