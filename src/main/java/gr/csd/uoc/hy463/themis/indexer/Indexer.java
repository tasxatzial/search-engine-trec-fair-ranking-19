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

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.indexes.Index;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoEssential;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFull;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.WordFrequencies;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.Stemmer;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import gr.csd.uoc.hy463.themis.utils.PartialIndex;
import gr.csd.uoc.hy463.themis.utils.PostingEntry;
import gr.csd.uoc.hy463.themis.utils.VocabularyEntry;
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
public class Indexer implements Runnable {
    public enum TASK {
        CREATE_INDEX, LOAD_INDEX
    };

    private TASK _task;
    private final AtomicBoolean running = new AtomicBoolean(false);

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
    // collection, the options for stemming and stop-words used in the indexing process,
    // and whatever else you might want. But make sure that before querying
    // the serialized file is loaded
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
        __META_FILENAME__ = __CONFIG__.getMetaFileName();
        if (__CONFIG__.getUseStemmer()) {
            Stemmer.Initialize();
        }
        if (__CONFIG__.getUseStopwords()) {
            gr.csd.uoc.hy463.themis.stemmer.StopWords.Initialize();
        }
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
        File folder = new File(path);
        File[] files = folder.listFiles();         // Holds  all files in path
        if (files == null) {
            return true;
        }

        String json;
        S2TextualEntry entry;
        int totalArticles = 0;
        int totalArticleLength = 0;
        long docOffset = 0;
        long prevDocOffset;
        __META_INDEX_INFO__ = new HashMap<>();
        WordFrequencies wordFrequencies = new WordFrequencies(__CONFIG__);

        /* Field frequencies of each term in a json entry */
        Map<String, List<Pair<DocInfoEssential.PROPERTY, Integer>>> entryWords;

        /* the dataset file that is being parsed */
        BufferedReader currentDataFile;

        /* the dataset folder */
        String documentsName =  __INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__;
        Files.createDirectories(Paths.get(documentsName).getParent());

        /* The documents file for writing the article info */
        BufferedOutputStream documentsOut = new BufferedOutputStream(new FileOutputStream
                (new RandomAccessFile(documentsName, "rw").getFD()));

        /* Temp file that has the size of each document entry in bytes. Needed for fast
        access to the each document entry when an update of its info is required,
        e.g. calculating VSM weights */
        BufferedWriter docLengthWriter = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(__INDEX_PATH__ + "/doc_size"), "UTF-8"));

        /* Temp file that stores the <term, TF> of every term that appears in
        each article (one line per article). Will be used for fast calculation
        of the VSM weights */
        BufferedWriter termFreqWriter = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(__INDEX_PATH__ + "/tf"), "UTF-8"));

        Index index = new Index(__CONFIG__);
        int id = 1;
        // set id of index
        index.setID(id);

        // We use a arraylist as a queue for our partial indexes
        List<Integer> partialIndexes = new LinkedList<>();

        // Add id to queue
        partialIndexes.add(id);

        // for each file in path
        for (File file : files) {
            if (file.isFile()) {
                currentDataFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

                // for each scientific article in file
                while ((json = currentDataFile.readLine()) != null) {
                    // Extract all textual info
                    // if indexed articles for this index less than
                    // config.getPartialIndexSize store all information to
                    // approapriate structures in memory to Index class else dump
                    // to files in appropriate directory id and increase partialIndexes
                    entry = S2JsonEntryReader.readTextualEntry(json);
                    entryWords = wordFrequencies.createWordsMap(entry);
                    totalArticleLength += entryWords.size();
                    index.add(entryWords, docOffset, termFreqWriter);
                    prevDocOffset = docOffset;
                    docOffset = dumpDocuments(documentsOut, entry, entryWords.size(), docOffset);
                    docLengthWriter.write(((int) docOffset - prevDocOffset) + "\n");

                    //print the map of field frequencies for this article
                    //System.out.println(entryWords);

                    totalArticles++;
                    if (totalArticles % __CONFIG__.getPartialIndexSize() == 0) {
                        index.dump();   // dump partial index to appropriate subdirectory
                        // Create a new index
                        // Increase partial indexes and dump files to appropriate directory
                        id++;
                        index = new Index(__CONFIG__);
                        index.setID(id);
                        // Add id to queue
                        partialIndexes.add(id);
                    }
                }
                currentDataFile.close();
            }
        }

        /* dump remaining structures */
        if (totalArticles != 0 && totalArticles % __CONFIG__.getPartialIndexSize() == 0) {
            partialIndexes.remove(partialIndexes.size() - 1);
            id--;
        }
        else {
            index.dump();
        }

        documentsOut.close();
        termFreqWriter.close();
        docLengthWriter.close();

        double avgdl = (0.0 + totalArticleLength) / totalArticles;

        // Now we have finished creating the partial indexes
        // So we have to merge them (call merge)
        merge(partialIndexes);

        /* update VSM weights */
        updateVSMweights(totalArticles);

        /* save any info related to this index */
        __META_INDEX_INFO__.put("use_stemmer", String.valueOf(__CONFIG__.getUseStemmer()));
        __META_INDEX_INFO__.put("use_stopwords", String.valueOf(__CONFIG__.getUseStopwords()));
        __META_INDEX_INFO__.put("articles", String.valueOf(totalArticles));
        __META_INDEX_INFO__.put("avgdl", String.valueOf(avgdl));
        __META_INDEX_INFO__.put("index_path", __INDEX_PATH__);
        saveMetaInfo();

        return false;
    }

    /**
     * Method that merges the partial indexes and creates a new single index.
     *
     * @param partialIndexes
     * @return
     */
    private void merge(List<Integer> partialIndexes) {
        // Use the indexes with the ids stored in the array.

        // Read vocabulary files line by line in corresponding dirs
        // and check which is the shortest lexicographically.
        // Read the corresponding entries in the postings and documents file
        // and append accordingly the new ones
        // If both partial indexes contain the same word, them we have to update
        // the df and append the postings and documents of both
        // Continue with the next lexicographically shortest word
        // Dump the final index and delete the old partial indexes
        // Store all idx files to INDEX_PATH
        try {
            if (partialIndexes.size() == 1) {
                String partialIndexPath = __INDEX_PATH__ + "/" + partialIndexes.get(0);
                Files.move(Paths.get(partialIndexPath + "/" + __VOCABULARY_FILENAME__),
                        Paths.get(__INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__),
                        StandardCopyOption.REPLACE_EXISTING);
                Files.move(Paths.get(partialIndexPath + "/" + __POSTINGS_FILENAME__),
                        Paths.get(__INDEX_PATH__ + "/" + __POSTINGS_FILENAME__),
                        StandardCopyOption.REPLACE_EXISTING);
                Files.delete(Paths.get(partialIndexPath));
            } else {
                combinePartialIndexes(partialIndexes);
                for (Integer partialIndex : partialIndexes) {
                    deleteIndex(new File(__INDEX_PATH__ + "/" + partialIndex));
                }
            }
        }  catch (IOException e) {
            __LOGGER__.error(e.getMessage());
            Themis.view.showError("Error merging\n");
        }
    }

    /* Combines the partial indexes when their size is > 1. Writes the merged vocabulary
    and postings files */
    private void combinePartialIndexes(List<Integer> partialIndexes) throws IOException {

        /* initialize the partial indexes */
        PartialIndex[] partialIndex = new PartialIndex[partialIndexes.size()];
        for (int i = 0; i < partialIndexes.size(); i++) {
            String vocabularyPath = __INDEX_PATH__ + "/" + partialIndexes.get(i) + "/" + __VOCABULARY_FILENAME__;
            String postingsPath = __INDEX_PATH__ + "/" + partialIndexes.get(i) + "/" + __POSTINGS_FILENAME__;
            partialIndex[i] = new PartialIndex(vocabularyPath, postingsPath, i);
        }

        /* the final vocabulary file */
        String vocabularyName = __INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__;
        BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(vocabularyName), "UTF-8"));

        /* the final postings file */
        String postingsName = __INDEX_PATH__ + "/" + __POSTINGS_FILENAME__;
        BufferedOutputStream postingsOut = new BufferedOutputStream(new FileOutputStream
                (new RandomAccessFile(postingsName, "rw").getFD()));

        /* the previous lex min word */
        String prevMinTerm = "";

        /* keep all consecutive vocabulary entries that have equal terms in an array */
        List<VocabularyEntry> equalTerms = new ArrayList<>();

        /* pointer to the postings file */
        long offset = 0;

        /* the current vocabulary entry that has the min lex word */
        VocabularyEntry minTermVocabularyEntry;

        /* the next vocabulary entry in the same vocabulary file as the one that
        has the min lex word */
        VocabularyEntry nextVocabularyEntry;

        /* the current partial index that has the lex min word */
        PartialIndex minTermIndex;

        /* put all first vocabulary entries (they have the min lex terms)
        from each partial index into a queue */
        PriorityQueue<VocabularyEntry> vocabularyQueue = new PriorityQueue<>();
        for (int i = 0; i < partialIndexes.size(); i++) {
            minTermVocabularyEntry = partialIndex[i].readNextVocabularyEntry();
            if (minTermVocabularyEntry != null) {
                vocabularyQueue.add(new VocabularyEntry(
                        minTermVocabularyEntry.get_term(),
                        minTermVocabularyEntry.get_df(),
                        minTermVocabularyEntry.get_offset(),
                        i));
            }
        }

        /* get the next min term */
        while((minTermVocabularyEntry = vocabularyQueue.poll()) != null) {
            minTermIndex = partialIndex[minTermVocabularyEntry.get_indexId()];

            /* if the min term is not equal to the previous term, we must write all
            vocabulary entries that are in the array of equal terms to the final
            vocabulary file (this also includes writing their postings to the final
            postings file) */
            if (!minTermVocabularyEntry.get_term().equals(prevMinTerm) && !equalTerms.isEmpty()) {
                offset = dumpEqualTerms(equalTerms, vocabularyWriter, postingsOut, partialIndex, offset);
            }

            /* save the current term for the next iteration */
            prevMinTerm = minTermVocabularyEntry.get_term();

            /* the current vocabulary entry is put into the array of equal terms */
            equalTerms.add(new VocabularyEntry(prevMinTerm, minTermVocabularyEntry.get_df(),
                    minTermVocabularyEntry.get_offset(), minTermVocabularyEntry.get_indexId()));

            /* finally add the next vocabulary entry to the queue of min lex terms */
            nextVocabularyEntry = minTermIndex.readNextVocabularyEntry();
            if (nextVocabularyEntry != null) {
                vocabularyQueue.add(nextVocabularyEntry);
            }
        }

        /* we are done reading the vocabularies. Write the remaining vocabulary entries that are
        still in the array of equal terms to the final vocabulary file (this also includes writing
        their postings to the final postings file) */
        if (!equalTerms.isEmpty()) {
            dumpEqualTerms(equalTerms, vocabularyWriter, postingsOut, partialIndex, offset);
        }

        /* close any open files */
        for (int i = 0; i < partialIndexes.size(); i++) {
            partialIndex[i].closeFiles();
        }
        vocabularyWriter.close();
        postingsOut.close();
    }

    /* Used during merging. Writes all entries in the array of equal terms to the final
    vocabulary files. This also includes writing their postings to the final postings file.
    Returns an offset to the postings file that will be used during the next iteration */
    private long dumpEqualTerms(List<VocabularyEntry> equalTerms, BufferedWriter vocabularyWriter,
                                BufferedOutputStream postingsOut, PartialIndex[] partialIndex,
                                long offset) throws IOException {
        int df = 0;
        byte[] postings;

        for (VocabularyEntry equalTerm : equalTerms) {

            //calculate final DF by simply adding all DF from each equal term
            df += equalTerm.get_df();

            //allocate memory for the postings of this term
            postings = new byte[equalTerm.get_df() * PostingEntry.POSTING_SIZE];

            //read the postings of this term from the partial posting file
            partialIndex[equalTerm.get_indexId()].get_postingFile().read(postings);

            //finally write the postings of this term to the final postings file
            postingsOut.write(postings);
        }

        //finally write a new entry in the final vocabulary file
        vocabularyWriter.write(equalTerms.get(0).get_term() + " " + df + " " + offset + "\n");

        //and calculate the offset of the next term
        offset += df * PostingEntry.POSTING_SIZE;

        equalTerms.clear();
        return offset;
    }

     /* DOCUMENTS FILE => documents.idx (Random Access File)
     * Writes the appropriate document entry for a textual entry to the documents file and
     * returns an offset that is the sum of the previous offset plus the document entry size
     * (in bytes).
     *
     * For each entry it stores: | DOCUMENT_ID (40 ASCII chars => 40 bytes) |
     * Title (variable bytes / UTF-8) | Author_1,Author_2, ...,Author_k
     * (variable bytes / UTF-8) | AuthorID_1, AuthorID_2, ...,Author_ID_k
     * (variable size /ASCII) | Year (short => 2 bytes)| Journal Name (variable
     * bytes / UTF-8) | The weight (norm) of Document (double => 8 bytes)|
     * Length of Document (int => 4 bytes) | PageRank Score (double => 8 bytes)
     *
     * ==> IMPORTANT NOTES
     *
     * For strings that have a variable size, a short (2 bytes) was added as
     * prefix storing the size in bytes of the string. Also the
     * correct representation was used, ASCII (1 byte) or UTF-8 (2 bytes). For
     * example the doc id is a hexadecimal hash so there is no need for UTF
     * encoding
     *
     * Authors are separated by a comma
     *
     * Author ids are also separated with a comma
     *
     * For now 0.0 was added as PageRank score and weight */
    private long dumpDocuments(BufferedOutputStream out, S2TextualEntry textualEntry, int docLength, long offset)
            throws IOException {
        int sizeOfShort = 2;
        int sizeOfInt = 4;
        int sizeOfDouble = 8;
        int docEntryLength = 0;

        /* id */
        byte[] id = textualEntry.getId().getBytes("ASCII");
        docEntryLength += id.length;

        /* title */
        byte[] title = textualEntry.getTitle().getBytes("UTF-8");
        byte[] titleLength = ByteBuffer.allocate(sizeOfShort).putShort((short) title.length).array();
        docEntryLength += title.length + sizeOfShort;

        /* authors, authors ids */
        List<Pair<String, List<String>>> authors;
        StringBuilder sb_authorNames;
        StringBuilder sb_authorIds;

        authors = textualEntry.getAuthors();
        sb_authorNames = new StringBuilder();
        sb_authorIds = new StringBuilder();
        for (int i = 0; i < authors.size(); i++) {
            sb_authorNames.append(authors.get(i).getL());
            sb_authorIds.append(authors.get(i).getR());
            if (i != authors.size() - 1) {
                sb_authorNames.append(",");
                sb_authorIds.append(",");
            }
        }

        byte[] authorNames = sb_authorNames.toString().getBytes("UTF-8");
        byte[] authorNamesLength = ByteBuffer.allocate(sizeOfShort).putShort((short) authorNames.length).array();
        docEntryLength += authorNames.length + sizeOfShort;

        byte[] authorIds = sb_authorIds.toString().getBytes("ASCII");
        byte[] authorIdsLength = ByteBuffer.allocate(sizeOfShort).putShort((short) authorIds.length).array();
        docEntryLength += authorIds.length + sizeOfShort;

        /* year */
        byte[] year = ByteBuffer.allocate(sizeOfShort).putShort((short) textualEntry.getYear()).array();
        docEntryLength += year.length;

        /* journal name */
        byte[] journalName = textualEntry.getJournalName().getBytes("UTF-8");
        byte[] journalNameLength = ByteBuffer.allocate(sizeOfShort).putShort((short) journalName.length).array();
        docEntryLength += journalName.length + sizeOfShort;

        /* weight, length, pagerank */
        byte[] weight = ByteBuffer.allocate(sizeOfDouble).putDouble(0).array();
        byte[] length = ByteBuffer.allocate(sizeOfInt).putInt(docLength).array();
        byte[] pageRank = ByteBuffer.allocate(sizeOfDouble).putDouble(0).array();
        docEntryLength += weight.length + length.length + pageRank.length;

        out.write(id);
        out.write(titleLength);
        out.write(title);
        out.write(authorNamesLength);
        out.write(authorNames);
        out.write(authorIdsLength);
        out.write(authorIds);
        out.write(year);
        out.write(journalNameLength);
        out.write(journalName);
        out.write(weight);
        out.write(length);
        out.write(pageRank);

        return docEntryLength + offset;
    }

    /* Deletes everything in indexPath including indexpath */
    private boolean deleteIndex(File indexPath) throws IOException {
        File[] contents = indexPath.listFiles();
        if (contents != null) {
            for (File file : contents) {
                if (!deleteIndex(file)) {
                    return false;
                }
            }
        }
        return Files.deleteIfExists(indexPath.toPath());
    }

    private void saveMetaInfo() throws IOException {
        BufferedWriter meta = new BufferedWriter(new FileWriter(__INDEX_PATH__ + "/" + __META_FILENAME__));
        for (Map.Entry<String, String> pair : __META_INDEX_INFO__.entrySet()) {
            meta.write(pair.getKey() + "=" + pair.getValue() + "\n");
        }
        meta.close();
    }

    /* Loads the final vocabulary file */
    private void loadVocabulary() throws IOException {
        __VOCABULARY__ = new HashMap<>();
        String line;
        String[] fields;

        BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__), "UTF-8"));

        while ((line = vocabularyReader.readLine()) != null) {
            fields = line.split(" ");
            __VOCABULARY__.put(fields[0], new Pair<>(Integer.parseInt(fields[1]), Long.parseLong(fields[2])));
        }

        vocabularyReader.close();
    }

    /* Calculates and updates the VSM weights in the documents file. Reads the
    * frequencies file freq and the documents size file doc_length */
    private void updateVSMweights(int totalArticles) throws IOException {

        /* load the vocabulary file */
        loadVocabulary();

        /* open the required files: documents, tf, doc_size */
        __DOCUMENTS__ = new RandomAccessFile(__INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__, "rw");
        BufferedReader freqReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_PATH__ + "/tf"), "UTF-8"));
        BufferedReader articleSizeReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_PATH__ + "/doc_size"), "UTF-8"));

        /* write the weights into an array before writing to disk */
        double[] weights = new double[totalArticles];

        /* the TFs for each article */
        int[] tfs;

        int article = 0;
        int maxFreq;
        String line;
        String[] split;

        /* read an entry from the frequencies file and calculate the weight */
        while ((line = freqReader.readLine()) != null) {
            split = line.split(" ");
            tfs = new int[split.length / 2];
            maxFreq = 0;
            for (int i = 0; i < split.length; i+=2) {
                tfs[i / 2] = Integer.parseInt(split[i+1]);
                if (tfs[i / 2] > maxFreq) {
                    maxFreq = tfs[i / 2];
                }
            }
            for (int i = 0; i < split.length; i+= 2) {
                double x = (0.0 + tfs[i / 2]) / maxFreq * Math.log((0.0 + totalArticles) /
                        __VOCABULARY__.get(split[i]).getL()) / Math.log(2);
                weights[article] += x * x;
            }
            weights[article] = Math.sqrt(weights[article]);
            article++;
        }
        freqReader.close();

        /* once the weights are calculated, update the documents file */
        long offset = 0;
        int docSize;
        byte[] weightB;
        for (double weight : weights) {
            docSize = Integer.parseInt(articleSizeReader.readLine());
            __DOCUMENTS__.seek(offset + docSize - 20);
            offset += docSize;
            weightB = ByteBuffer.allocate(8).putDouble(weight).array();
            __DOCUMENTS__.write(weightB);
        }

        /* close files */
        articleSizeReader.close();
        __DOCUMENTS__.close();
        __DOCUMENTS__ = null;

        /* delete files */
        deleteIndex(new File(__INDEX_PATH__ + "/doc_size"));
        deleteIndex(new File(__INDEX_PATH__ + "/tf"));
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
            Themis.view.print("DATASET_PATH not set in themis.config!\n");
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
            Themis.view.print("Index is not constructed correctly!\n");
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

    public void setTask(TASK task) {
        _task = task;
    }

    public TASK getTask() {
        return _task;
    }

    public boolean isRunning() {
        return running.get() && _task != null;
    }

    @Override
    public void run() {
        running.set(true);
        try {
            if (_task == TASK.CREATE_INDEX) {
                index();
            }
            else if (_task == TASK.LOAD_INDEX) {
                load();
            }
        } catch (IOException e) {
            __LOGGER__.error(e.getMessage());
            Themis.view.showError("Error");
        } finally {
            running.set(false);
            _task = null;
        }
    }

    public void stop()  {
        running.set(false);
    }
}
