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
package gr.csd.uoc.hy463.themis.indexer;

import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.indexes.Index;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFull;
import gr.csd.uoc.hy463.themis.utils.Pair;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Our basic indexer class. This class is responsible for two tasks:
 *
 * a) Create the appropriate indexes given a specific directory with files (in
 * our case the Semantic Scholar collection)
 *
 * b) Given a path load the indexes (if they exist) and provide information
 * about the indexed data, that can be used for implementing any kind of
 * retrieval models
 *
 * When the indexes have been created we should have three files, as documented
 * in Index.java
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class Indexer {

    private static final Logger __LOGGER__ = LogManager.getLogger(Indexer.class);
    private Config __CONFIG__;  // configuration options
    // The file path of indexes
    private String __INDEX_PATH__ = null;
    // Filenames of indexes
    private String __VOCABULARY_FILENAME__ = null;
    private String __POSTINGS_FILENAME__ = null;
    private String __DOCUMENTS_FILENAME__ = null;
    private String __META_FILENAME__ = null;

    // Vocabulary should be stored in memory for querying! This is crucial
    // since we want to keep things fast! This is done through load().
    // For this project use a HashMap instead of a trie
    private HashMap<String, Pair<Integer, Long>> __VOCABULARY__ = null;
    private RandomAccessFile __POSTINGS__ = null;
    private RandomAccessFile __DOCUMENTS__ = null;

    // This map holds any information related with the indexed collection
    // and should be serialized when the index process has finished. Such
    // information could be the avgDL for the Okapi-BM25 implementation,
    // a timestamp of when the indexing process finished, the path of the indexed
    // collection, and whatever else you might want. But make sure that before
    //  querying the serialized file is loaded
    private Map<String, String> __META_INDEX_INFO__ = null;

    /**
     * Default constructor. Creates also a config instance
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public Indexer() throws IOException, ClassNotFoundException {
        __CONFIG__ = new Config();  // reads info from themis.config file
        init();
    }

    /**
     * Constructor that gets a current Config instance
     *
     * @param config
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public Indexer(Config config) throws IOException, ClassNotFoundException {
        this.__CONFIG__ = config;  // reads info from themis.config file
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
     * Checks that the index path + all *.idx files exist
     *
     * Method that checks if we have all appropriate files
     *
     * @return
     */
    public boolean hasIndex() {
        // Check if path exists
        File file = new File(__INDEX_PATH__);
        if (!file.exists() || !file.isDirectory()) {
            __LOGGER__.error(__INDEX_PATH__ + "directory does not exist!");
            return false;
        }
        // Check if index files exist
        file = new File(__INDEX_PATH__ + __VOCABULARY_FILENAME__);
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__VOCABULARY_FILENAME__ + "vocabulary file does not exist in " + __INDEX_PATH__);
            return false;
        }
        file = new File(__INDEX_PATH__ + __POSTINGS_FILENAME__);
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__POSTINGS_FILENAME__ + " posting binary file does not exist in " + __INDEX_PATH__);
            return false;
        }
        file = new File(__INDEX_PATH__ + __DOCUMENTS_FILENAME__);
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__DOCUMENTS_FILENAME__ + "documents binary file does not exist in " + __INDEX_PATH__);
            return false;
        }
        return true;
    }

    /**
     * Method responsible for indexing a directory of files
     *
     * If the number of files is larger than the PARTIAL_INDEX_MAX_DOCS_SIZE set
     * to the themis.config file then we have to dump all data read up to now to
     * a partial index and continue with a new index. After creating all partial
     * indexes then we have to merge them to create the final index that will be
     * stored in the file path.
     *
     * Can also be modified to use the MAX_MEMORY usage parameter given in
     * themis.conf for brave hearts!
     *
     * @param path
     * @return
     * @throws IOException
     */
    public boolean index(String path) throws IOException {
        Index index = new Index(__CONFIG__);
        int partialIndexes = 0;

        // Holds  all files in path
        List<String> files = new ArrayList<>();

        // for each file in path
        for (String file : files) {
            // for each scientific article in file
            for (int article = 0;; article++) {
                // Extract all textual info
                // if indexed articles for this index less than
                // config.getPartialIndexSize store all information to
                // approapriate structures in memory to Index class else dump
                // to files in appropriate directory id and increase partialIndexes

                if (article == __CONFIG__.getPartialIndexSize()) {
                    // Increase partial indexes and dump files to appropriate directory
                    partialIndexes++;
                    index.setID(partialIndexes);
                    index.dump();   // dump partial index to appropriate subdirectory
                }
            }
        }

        // Now we have finished creating the partial indexes
        // So we have to merge them (call merge())
        return false;
    }

    /**
     * Method that merges two partial indexes and creates a new index with ID
     * nextID, which is either a new partial index or the final index if we have
     * finished merging (i.e., if nextID = 0)
     *
     * @param id
     * @param id
     * @param nextID
     * @return
     */
    private void merge(int partialID1, int partialID2, boolean nextID) {
        // Read vocabulary files line by line in corresponding dirs
        // and check which is the shortest lexicographically.
        // Read the corresponding entries in the postings and documents file
        // and append accordingly the new ones
        // If both partial indexes contain the same word, them we have to update
        // the df and append the postings and documents of both
        // Continue with the next lexicographically shortest word
        // Dump the new index and delete the old partial indexes

        // If nextID = 0 (i.e., we have finished merging partial indexes, store
        // all idx files to INDEX_PATH
    }

    /**
     * Method that indexes the collection that is given in the themis.config
     * file
     *
     * Used for the task of indexing!
     *
     * @return
     * @throws IOException
     */
    public boolean index() throws IOException {
        String collectionPath = __CONFIG__.getDatasetPath();
        if (collectionPath != null) {
            return index(collectionPath);
        } else {
            __LOGGER__.error("DATASET_PATH not set in themis.config!");
            return false;
        }
    }

    /**
     * Method responsible for loading vocabulary file to memory and also opening
     * RAF files to postings and documents, ready to seek
     *
     * Used for the task of querying!
     *
     * @return
     * @throws IOException
     */
    public boolean load() throws IOException {
        if (!hasIndex()) {
            __LOGGER__.error("Index is not constructed correctly!");
            return false;
        }

        // Else load vocabulary file in memory in a HashMap and open
        // indexes postings and documents RAF files
        return false;
    }

    /**
     * Basic method for querying functionality. Given the list of terms in the
     * query, returns a List of Lists of DocInfoEssential objects, where each
     * list of DocInfoEssential objects holds where each list of
     * DocInfoEssential objects holds the DocInfoEssential representation of the
     * docs that the corresponding term of the query appears in. A
     * DocInfoEssential, should hold all needed information for implementing a
     * retrieval model, like VSM, Okapi-BM25, etc. This is more memory efficient
     * than holding getDocInfoFullTerms objects
     *
     * @param terms
     * @return
     */
    public List<List<DocInfoEssential>> getDocInfoEssentialForTerms(List<String> terms) {
        // If indexes are not loaded
        if (!loaded()) {
            return null;
        } else {
            // to implement
            return null;
        }
    }

    /**
     * Basic method for querying functionality. Given the list of terms in the
     * query, returns a List of Lists of DocInfoFull objects, where each list of
     * DocInfoFull objects holds the DocInfoFull representation of the docs that
     * the corresponding term of the query appears in (i.e., the whole
     * information). Not memory efficient though...
     *
     * Useful when we want to return the title, authors, etc.
     *
     * @param terms
     * @return
     */
    public List<List<DocInfoFull>> getDocInfoFullTerms(List<String> terms) {
        // If indexes are not loaded
        if (!loaded()) {
            return null;
        } else {
            // to implement
            return null;
        }
    }

    /**
     * This is a method that given a list of docs in the essential
     * representation, returns a list with the full description of docs stored
     * in the Documents File. This method is needed when we want to return the
     * full information of a list of documents. Could be useful if we support
     * pagination to the results (i.e. provide the full results of ten
     * documents)
     *
     * @param docs
     * @return
     */
    public List<DocInfoFull> getDocDescription(List<DocInfoEssential> docs) {
        // If indexes are not loaded
        if (!loaded()) {
            return null;
        } else {
            // to implement
            return null;
        }
    }

    /**
     * Method that checks if indexes have been loaded/opened
     *
     * @return
     */
    public boolean loaded() {
        return __VOCABULARY__ != null && __POSTINGS__ != null
                && __DOCUMENTS__ != null;
    }

    /**
     * Get the path of index as set in themis.config file
     *
     * @return
     */
    public String getIndexDirectory() {
        if (__CONFIG__ != null) {
            return __INDEX_PATH__;
        } else {
            __LOGGER__.error("Index has not been initialized correctly");
            return "";
        }
    }
}
