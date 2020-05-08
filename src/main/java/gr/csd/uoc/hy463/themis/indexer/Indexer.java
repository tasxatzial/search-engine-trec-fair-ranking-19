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
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfoFull;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.WordFrequencies;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.Stemmer;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.utils.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.print.Doc;

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
    public Indexer() throws IOException {
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
            __LOGGER__.error(__INDEX_PATH__ + " directory does not exist!");
            return false;
        }
        // Check if index files exist
        file = new File(__INDEX_PATH__ + __VOCABULARY_FILENAME__);
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__VOCABULARY_FILENAME__ + " vocabulary file does not exist in " + __INDEX_PATH__);
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
        file = new File(__INDEX_PATH__ + __META_FILENAME__);
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__META_FILENAME__ + " meta file does not exist in " + __INDEX_PATH__);
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
        unload();

        if (__CONFIG__.getUseStemmer()) {
            Stemmer.Initialize();
        }
        if (__CONFIG__.getUseStopwords()) {
            StopWords.Initialize();
        }

        long startTime = System.nanoTime();
        Themis.view.print(">>> Start indexing\n");

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
        Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWords;

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
                (new FileOutputStream(__INDEX_PATH__ + "/doc_tf"), "UTF-8"));
        
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
                Themis.view.print("Processing file: " + file + "\n");
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

        /* calculate avgdl for Okapi BM25 */
        double avgdl = (0.0 + totalArticleLength) / totalArticles;

        /* save any info related to this index */
        __META_INDEX_INFO__.put("use_stemmer", String.valueOf(__CONFIG__.getUseStemmer()));
        __META_INDEX_INFO__.put("use_stopwords", String.valueOf(__CONFIG__.getUseStopwords()));
        __META_INDEX_INFO__.put("articles", String.valueOf(totalArticles));
        __META_INDEX_INFO__.put("avgdl", String.valueOf(avgdl));
        __META_INDEX_INFO__.put("index_path", __INDEX_PATH__);
        saveMetaInfo();

        Themis.view.print("Partial index created in: " + Math.round((System.nanoTime() - startTime) / 1e7) / 100.0 + " sec\n");

        /* merge the vocabularies and delete them */
        mergeVocabularies(partialIndexes);

        /* update VSM weights and delete doc_size and tf files */
        updateVSMweights(totalArticles);

        /* merge the postings and delete them */
        mergePostings(partialIndexes);

        /* delete the partial indexes folders */
        for (Integer partialIndex : partialIndexes) {
            deleteDir(new File(__INDEX_PATH__ + "/" + partialIndex));
        }

        Themis.view.print(">>> End of indexing\n");
        return false;
    }

    /* Merges the partial vocabularies and creates a new single vocabulary. The partial vocabularies are then
    * deleted */
    private void mergeVocabularies(List<Integer> partialIndexes) {
        // Use the indexes with the ids stored in the array.

        /* If there is only one partial index, no merging is needed for the vocabularies. Just move them
        in INDEX_PATH. If there are > 1 partial indexes, merge only the vocabularies and delete them */
        long startTime =  System.nanoTime();
        try {
            Themis.view.print(">>> Start Merging of partial vocabularies\n");
            if (partialIndexes.size() == 1) {
                String partialIndexPath = __INDEX_PATH__ + "/" + partialIndexes.get(0);
                Files.move(Paths.get(partialIndexPath + "/" + __VOCABULARY_FILENAME__),
                        Paths.get(__INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__),
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                combinePartialVocabularies(partialIndexes);
                for (int i = 0; i < partialIndexes.size(); i++) {
                    String partialIndexPath = __INDEX_PATH__ + "/" + partialIndexes.get(i);
                    deleteDir(new File(partialIndexPath + "/" + __VOCABULARY_FILENAME__));
                }
            }
            Themis.view.print("Partial vocabularies merged in: " + Math.round((System.nanoTime() - startTime) / 1e7) / 100.0 + " sec\n");
        }  catch (IOException e) {
            __LOGGER__.error(e.getMessage());
            Themis.view.showError("Error merging vocabularies\n");
        }
    }

    /* Merges the partial vocabularies when there are more than 1 partial indexes.
    Writes the merged vocabulary file */
    private void combinePartialVocabularies(List<Integer> partialIndexes) throws IOException {

        /* the partial vocabularies */
        BufferedReader[] vocabularyReader = new BufferedReader[partialIndexes.size()];
        for (int i = 0; i < partialIndexes.size(); i++) {
            String vocabularyPath = __INDEX_PATH__ + "/" + partialIndexes.get(i) + "/" + __VOCABULARY_FILENAME__;
            vocabularyReader[i] = new BufferedReader(new InputStreamReader(new FileInputStream(vocabularyPath), "UTF-8"));
        }

        /* the final vocabulary file */
        String vocabularyName = __INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__;
        BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(vocabularyName), "UTF-8"));

        BufferedWriter termTfWriter = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(__INDEX_PATH__ + "/term_tf"), "UTF-8"));

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

        /* put all first vocabulary entries (they have the min lex terms)
        from each partial vocabulary into a queue */
        PriorityQueue<VocabularyEntry> vocabularyQueue = new PriorityQueue<>();
        for (int i = 0; i < partialIndexes.size(); i++) {
            minTermVocabularyEntry = getNextVocabularyEntry(vocabularyReader[i], i);
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

            /* if the min term is not equal to the previous term, we must write all
            vocabulary entries that are in the array of equal terms to the final
            vocabulary file */
            if (!minTermVocabularyEntry.get_term().equals(prevMinTerm) && !equalTerms.isEmpty()) {
                offset = dumpEqualTerms(equalTerms, vocabularyWriter, termTfWriter, offset);
            }

            /* save the current term for the next iteration */
            prevMinTerm = minTermVocabularyEntry.get_term();

            /* the current vocabulary entry is put into the array of equal terms */
            equalTerms.add(new VocabularyEntry(prevMinTerm, minTermVocabularyEntry.get_df(),
                    minTermVocabularyEntry.get_offset(), minTermVocabularyEntry.get_indexId()));

            /* finally add the next vocabulary entry to the queue of min lex terms */
            int indexId = minTermVocabularyEntry.get_indexId();
            nextVocabularyEntry = getNextVocabularyEntry(vocabularyReader[indexId], indexId);
            if (nextVocabularyEntry != null) {
                vocabularyQueue.add(nextVocabularyEntry);
            }
        }

        /* we are done reading the vocabularies. Write the remaining vocabulary entries that are
        still in the array of equal terms to the final vocabulary file */
        if (!equalTerms.isEmpty()) {
            dumpEqualTerms(equalTerms, vocabularyWriter, termTfWriter, offset);
        }

        /* close any open files */
        for (int i = 0; i < partialIndexes.size(); i++) {
            vocabularyReader[i].close();
        }
        vocabularyWriter.close();
        termTfWriter.close();
    }

    /* Returns the next vocabulary entry (term, df, offset) that belongs to partial vocabulary with indexId */
    private VocabularyEntry getNextVocabularyEntry(BufferedReader vocabularyReader, int indexId) throws IOException {
        String line;
        String[] fields;

        line = vocabularyReader.readLine();
        if (line != null) {
            fields = line.split(" ");
            return new VocabularyEntry(fields[0], Integer.parseInt(fields[1]), Long.parseLong(fields[2]), indexId);
        }
        return null;
    }

    /* Used during merging of the partial vocabularies. Writes all entries in the array of equal terms to the final
    vocabulary files. Returns an offset to the postings file that will be used during the next iteration. Also writes
    all (partial index id, tf) for each term to a file that will be used during the merging of postings */
    private long dumpEqualTerms(List<VocabularyEntry> equalTerms, BufferedWriter vocabularyWriter,
                                BufferedWriter termTfWriter, long offset) throws IOException {
        int df = 0;

        //calculate final DF
        for (VocabularyEntry equalTerm : equalTerms) {
            df += equalTerm.get_df();
            termTfWriter.write(equalTerm.get_indexId() + " " + equalTerm.get_df() + " ");
        }
        termTfWriter.write("\n");

        //finally write a new entry in the final vocabulary file
        vocabularyWriter.write(equalTerms.get(0).get_term() + " " + df + " " + offset + "\n");

        //and calculate the offset of the next term
        offset += df * PostingEntry.SIZE;

        equalTerms.clear();
        return offset;
    }

    /* Method that merges the partial postings and creates a new single posting file. The partial postings
    * are then deleted */
    private void mergePostings(List<Integer> partialIndexes) {
        // Use the indexes with the ids stored in the array.

        /* If there is only one partial index, no merging is needed for the postings. Just move them
        in INDEX_PATH. If there are > 1 partial indexes, merge only the postings and delete them */
        long startTime =  System.nanoTime();
        try {
            Themis.view.print(">>> Start Merging of partial postings\n");
            if (partialIndexes.size() == 1) {
                String partialIndexPath = __INDEX_PATH__ + "/" + partialIndexes.get(0);
                Files.move(Paths.get(partialIndexPath + "/" + __POSTINGS_FILENAME__),
                        Paths.get(__INDEX_PATH__ + "/" + __POSTINGS_FILENAME__),
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                combinePartialPostings(partialIndexes);
                for (Integer partialIndex : partialIndexes) {
                    String partialIndexPath = __INDEX_PATH__ + "/" + partialIndex;
                    deleteDir(new File(partialIndexPath + "/" + __POSTINGS_FILENAME__));
                }
            }
            Themis.view.print("Partial postings merged in: " + Math.round((System.nanoTime() - startTime) / 1e7) / 100.0 + " sec\n");
        }  catch (IOException e) {
            __LOGGER__.error(e.getMessage());
            Themis.view.showError("Error merging postings\n");
        }
    }

    /* Merges the partial postings when there are more than 1 partial indexes.
    Writes the merged posting file and deletes the term_tf file */
    private void combinePartialPostings(List<Integer> partialIndexes) throws IOException {

        /* the partial postings */
        BufferedInputStream[] postingsStream = new BufferedInputStream[partialIndexes.size()];
        for (int i = 0; i < partialIndexes.size(); i++) {
            String postingPath = __INDEX_PATH__ + "/" + partialIndexes.get(i) + "/" + __POSTINGS_FILENAME__;
            postingsStream[i] = new BufferedInputStream(new FileInputStream
                    (new RandomAccessFile(postingPath, "rw").getFD()));
        }

        /* the final posting file */
        String postingName = __INDEX_PATH__ + "/" + __POSTINGS_FILENAME__;
        BufferedOutputStream postingOut = new BufferedOutputStream(new FileOutputStream
                (new RandomAccessFile(postingName, "rw").getFD()));

        /* the file with the (partial index id, tf) for each term */
        BufferedReader termTfReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_PATH__ + "/term_tf"), "ASCII"));

        /* read each line of the file that has the (partial index id, tf). Each line corresponds to
        the same line in the final vocabulary file, thus both lines refer to the same term */
        String line;
        String[] split;
        while ((line = termTfReader.readLine()) != null){
            split = line.split(" ");
            for (int i = 0; i < split.length; i+=2) {
                byte[] postings = new byte[Integer.parseInt(split[i + 1])  * PostingEntry.SIZE];
                postingsStream[Integer.parseInt(split[i])].read(postings); //read into postings byte array
                postingOut.write(postings);  //and write to final postings file
            }
        }

        /* close any open files */
        for (BufferedInputStream bufferedInputStream : postingsStream) {
            bufferedInputStream.close();
        }
        postingOut.close();
        termTfReader.close();

        /* finally delete temporary file */
        deleteDir(new File(__INDEX_PATH__ + "/term_tf"));
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
     * prefix storing the size in bytes of the string. Exceptions are the author names
     * and author ids for which the prefix is 4 bytes. Also the
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
        int docEntryLength = 0;

        /* id */
        byte[] id = textualEntry.getId().getBytes("ASCII");
        docEntryLength += DocumentEntry.ID_SIZE;

        /* title */
        byte[] title = textualEntry.getTitle().getBytes("UTF-8");
        byte[] titleLength = ByteBuffer.allocate(DocumentEntry.TITLE_SIZE_SIZE).putInt(title.length).array();
        docEntryLength += title.length + DocumentEntry.TITLE_SIZE_SIZE;

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
        byte[] authorNamesLength = ByteBuffer.allocate(DocumentEntry.AUTHOR_NAMES_SIZE_SIZE).putInt(authorNames.length).array();
        docEntryLength += authorNames.length + DocumentEntry.AUTHOR_NAMES_SIZE_SIZE;

        byte[] authorIds = sb_authorIds.toString().getBytes("ASCII");
        byte[] authorIdsLength = ByteBuffer.allocate(DocumentEntry.AUTHOR_IDS_SIZE_SIZE).putInt(authorIds.length).array();
        docEntryLength += authorIds.length + DocumentEntry.AUTHOR_IDS_SIZE_SIZE;

        /* year */
        byte[] year = ByteBuffer.allocate(DocumentEntry.YEAR_SIZE).putShort((short) textualEntry.getYear()).array();
        docEntryLength += DocumentEntry.YEAR_SIZE;

        /* journal name */
        byte[] journalName = textualEntry.getJournalName().getBytes("UTF-8");
        byte[] journalNameLength = ByteBuffer.allocate(DocumentEntry.JOURNAL_NAME_SIZE_SIZE).putShort((short) journalName.length).array();
        docEntryLength += journalName.length + DocumentEntry.JOURNAL_NAME_SIZE_SIZE;

        /* weight, length, pagerank, avgAuthorRank */
        byte[] weight = ByteBuffer.allocate(DocumentEntry.WEIGHT_SIZE).putDouble(0).array();
        byte[] length = ByteBuffer.allocate(DocumentEntry.LENGTH_SIZE).putInt(docLength).array();
        byte[] pageRank = ByteBuffer.allocate(DocumentEntry.PAGERANK_SIZE).putDouble(0).array();
        byte[] avgAuthorRank = ByteBuffer.allocate(DocumentEntry.AVG_AUTHOR_RANK_SIZE).putDouble(0).array();
        docEntryLength += DocumentEntry.WEIGHT_SIZE + DocumentEntry.LENGTH_SIZE +
                DocumentEntry.PAGERANK_SIZE + DocumentEntry.AVG_AUTHOR_RANK_SIZE;

        /* write first the fixed size fields */
        out.write(id);
        out.write(pageRank);
        out.write(weight);
        out.write(length);
        out.write(avgAuthorRank);
        out.write(year);
        out.write(titleLength);
        out.write(authorNamesLength);
        out.write(authorIdsLength);
        out.write(journalNameLength);

        /* write the variable size fields */
        out.write(title);
        out.write(authorNames);
        out.write(authorIds);
        out.write(journalName);

        return docEntryLength + offset;
    }

    /* Deletes everything in indexPath including indexpath */
    private boolean deleteDir(File indexPath) throws IOException {
        File[] contents = indexPath.listFiles();
        if (contents != null) {
            for (File file : contents) {
                if (!deleteDir(file)) {
                    return false;
                }
            }
        }
        return Files.deleteIfExists(indexPath.toPath());
    }

    /* Saves to disk the meta_index_info map */
    private void saveMetaInfo() throws IOException {
        BufferedWriter meta = new BufferedWriter(new FileWriter(__INDEX_PATH__ + "/" + __META_FILENAME__));
        for (Map.Entry<String, String> pair : __META_INDEX_INFO__.entrySet()) {
            meta.write(pair.getKey() + "=" + pair.getValue() + "\n");
        }
        meta.close();
    }

    /* Loads from disk the info from the meta_filename file */
    private void loadMetaInfo() throws IOException {
        BufferedReader meta = new BufferedReader(new FileReader(__INDEX_PATH__ + "/" + __META_FILENAME__));
        __META_INDEX_INFO__ = new HashMap<>();
        String line;
        String[] split;
        while((line = meta.readLine()) != null) {
            split = line.split("=");
            __META_INDEX_INFO__.put(split[0], split[1]);
        }
        meta.close();
    }

    /* Loads the final vocabulary file */
    private void loadVocabulary() throws IOException {
        Themis.view.print("Loading vocabulary...");
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
        Themis.view.print("DONE\n");
    }

    /* Calculates and updates the VSM weights in the documents file. Reads the
    * frequencies file freq and the documents size file doc_length */
    private void updateVSMweights(int totalArticles) throws IOException {
        long startTime = System.nanoTime();
        Themis.view.print(">>> Calculating VSM weights\n");

        /* load the vocabulary file except the offsets */
        Themis.view.print("Loading vocabulary terms...");
        BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__), "UTF-8"));
        Map<String, Integer> vocabulary = new HashMap<>();
        String line;
        String[] split;
        while ((line = vocabularyReader.readLine()) != null) {
            split = line.split(" ");
            vocabulary.put(split[0], Integer.parseInt(split[1]));
        }
        vocabularyReader.close();
        Themis.view.print("DONE\n");

        /* open the required files: documents, tf, doc_size */
        __DOCUMENTS__ = new RandomAccessFile(__INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__, "rw");
        BufferedReader tfReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_PATH__ + "/doc_tf"), "UTF-8"));
        BufferedReader docSizeReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_PATH__ + "/doc_size"), "UTF-8"));

        int[] tfs; //the TFs for each document
        double weight; //the weight of each document
        long docOffset = 0; //offset in documents file
        int docSize; //size of each entry in the document file
        int maxTf; //maximum tf in each document

        /* read an entry from the frequencies file and calculate the weight */
        while ((line = tfReader.readLine()) != null) {
            split = line.split(" ");
            tfs = new int[split.length / 2];
            maxTf = 0;
            for (int i = 0; i < split.length; i+=2) {
                tfs[i / 2] = Integer.parseInt(split[i+1]);
                if (tfs[i / 2] > maxTf) {
                    maxTf = tfs[i / 2];
                }
            }
            weight = 0;
            for (int i = 0; i < split.length; i+= 2) {
                double x = (0.0 + tfs[i / 2]) / maxTf * Math.log((0.0 + totalArticles) /
                        vocabulary.get(split[i])) / Math.log(2);
                weight += x * x;
            }
            weight = Math.sqrt(weight);
            docSize = Integer.parseInt(docSizeReader.readLine());
            __DOCUMENTS__.seek(docOffset + DocumentEntry.WEIGHT_OFFSET);
            docOffset += docSize;
            __DOCUMENTS__.write(ByteBuffer.allocate(8).putDouble(weight).array());
        }
        tfReader.close();

        /* close files */
        docSizeReader.close();
        __DOCUMENTS__.close();
        __DOCUMENTS__ = null;

        /* delete files */
        deleteDir(new File(__INDEX_PATH__ + "/doc_size"));
        deleteDir(new File(__INDEX_PATH__ + "/doc_tf"));

        Themis.view.print("VSM weights calculated in: " + Math.round((System.nanoTime() - startTime) / 1e7) / 100.0 + " sec\n");
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
        unload();
        if (!hasIndex()) {
            __LOGGER__.error("Index is not constructed correctly!");
            Themis.view.print("Index is not constructed correctly!\n");
            return false;
        }

        // Else load vocabulary file in memory in a HashMap and open
        // indexes postings and documents RAF files. Also load the meta index file.
        loadVocabulary();
        loadMetaInfo();
        __POSTINGS__ = new RandomAccessFile(__INDEX_PATH__ + "/" + __POSTINGS_FILENAME__, "r");
        __DOCUMENTS__ = new RandomAccessFile(__INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__, "r");

        if (Boolean.parseBoolean(__META_INDEX_INFO__.get("use_stopwords"))) {
            StopWords.Initialize();
        }
        if (Boolean.parseBoolean(__META_INDEX_INFO__.get("use_stemmer"))) {
            Stemmer.Initialize();
        }
        return true;
    }

    /* Unloads an index from memory */
    public void unload() throws IOException {
        if (__POSTINGS__ != null) {
            __POSTINGS__.close();
            __POSTINGS__ = null;
        }
        if (__DOCUMENTS__ != null) {
            __DOCUMENTS__.close();
            __DOCUMENTS__ = null;
        }
        __VOCABULARY__ = null;
        __META_INDEX_INFO__ = null;
    }

    /**
     * Deletes the index folder.
     * @throws IOException
     */
    public void deleteIndex() throws IOException {
        deleteDir(new File(__INDEX_PATH__ + "/"));
    }

    /**
     * Returns true if index directory exists and is empty, false otherwise.
     * @return
     */
    public boolean isIndexDirEmpty() {
        File file = new File(__INDEX_PATH__);
        File[] fileList = file.listFiles();
        return fileList == null || fileList.length == 0;
    }

    /* Returns a new list of terms based on the options for stemming and stopwords from the
     * meta_index_info map */
    private Set<String> preprocessTerms(Set<String> terms) {
        boolean useStopwords = Boolean.parseBoolean(__META_INDEX_INFO__.get("use_stopwords"));
        boolean useStemmer = Boolean.parseBoolean(__META_INDEX_INFO__.get("use_stemmer"));
        Set<String> editedTerms = new HashSet<>();
        for (String term : terms) {
            if (useStopwords && StopWords.isStopWord(term)) {
                continue;
            }
            if (useStemmer) {
                editedTerms.add(Stemmer.Stem(term));
            }
            else {
                editedTerms.add(term.toLowerCase());
            }
        }
        return editedTerms;
    }

    public List<List<DocInfo>> getDocInfo(Set<String> terms, Set<DocInfo.PROPERTY> props) throws IOException {
        if (!loaded()) {
            return null;
        }
        Set<String> editedTerms = preprocessTerms(terms);
        List<List<DocInfo>> docIds = new ArrayList<>();
        List<DocInfo> termDocInfo;
        DocInfo docInfo;
        Pair<Integer, Long> termValue;
        long documentPointer;
        long postingPointer;
        int df;
        byte[] docId = new byte[DocumentEntry.ID_SIZE];
        boolean hasPagerank = props.contains(DocInfo.PROPERTY.PAGERANK);
        boolean hasWeight = props.contains(DocInfo.PROPERTY.WEIGHT);
        boolean hasLength = props.contains(DocInfo.PROPERTY.LENGTH);
        boolean hasAvgAuthorRank = props.contains(DocInfo.PROPERTY.AVG_AUTHOR_RANK);
        boolean hasYear = props.contains(DocInfo.PROPERTY.YEAR);
        boolean hasTitle = props.contains(DocInfo.PROPERTY.TITLE);
        boolean hasAuthorNames = props.contains(DocInfo.PROPERTY.AUTHORS_NAMES);
        boolean hasAuthorIds = props.contains(DocInfo.PROPERTY.AUTHORS_IDS);
        boolean hasJournalName = props.contains(DocInfo.PROPERTY.JOURNAL_NAME);

        for (String term : editedTerms) {
            termValue = __VOCABULARY__.get(term);
            if (termValue == null) {
                continue;
            }
            termDocInfo = new ArrayList<>();
            postingPointer = termValue.getR();
            df = termValue.getL();
            for (int i = 0; i < df; i++) {
                __POSTINGS__.seek(postingPointer + i * PostingEntry.SIZE + PostingEntry.TF_SIZE);
                documentPointer = __POSTINGS__.readLong();
                __DOCUMENTS__.seek(documentPointer);
                __DOCUMENTS__.readFully(docId);
                docInfo = new DocInfo(new String(docId, "ASCII"), documentPointer);
                termDocInfo.add(docInfo);
                if (hasPagerank) {
                    docInfo.setProperty(DocInfo.PROPERTY.PAGERANK, __DOCUMENTS__.readDouble());
                }
                if (hasWeight) {
                    if (!hasPagerank) {
                        __DOCUMENTS__.seek(documentPointer + DocumentEntry.WEIGHT_OFFSET);
                    }
                    docInfo.setProperty(DocInfo.PROPERTY.WEIGHT, __DOCUMENTS__.readDouble());
                }
                if (hasLength) {
                    if (!hasWeight) {
                        __DOCUMENTS__.seek(documentPointer + DocumentEntry.LENGTH_OFFSET);
                    }
                    docInfo.setProperty(DocInfo.PROPERTY.LENGTH, __DOCUMENTS__.readInt());
                }
                if (hasAvgAuthorRank) {
                    if (!hasLength) {
                        __DOCUMENTS__.seek(documentPointer + DocumentEntry.AVG_AUTHOR_RANK_OFFSET);
                    }
                    docInfo.setProperty(DocInfo.PROPERTY.AVG_AUTHOR_RANK, __DOCUMENTS__.readDouble());
                }
                if (hasYear) {
                    if (!hasAvgAuthorRank) {
                        __DOCUMENTS__.seek(documentPointer + DocumentEntry.YEAR_OFFSET);
                    }
                    docInfo.setProperty(DocInfo.PROPERTY.YEAR, __DOCUMENTS__.readShort());
                }
                int titleLength = 0;
                int authorNamesLength = 0;
                int authorIdsLength = 0;
                int journalNameLength = 0;

                if (!hasTitle && !hasAuthorNames && !hasAuthorIds && !hasJournalName) {
                    continue;
                }
                if (!hasYear) {
                    __DOCUMENTS__.seek(documentPointer + DocumentEntry.TITLE_SIZE_OFFSET);
                }

                if (hasJournalName) {
                    titleLength = __DOCUMENTS__.readInt();
                    authorNamesLength = __DOCUMENTS__.readInt();
                    authorIdsLength = __DOCUMENTS__.readInt();
                    journalNameLength = __DOCUMENTS__.readShort();
                }
                else if (hasAuthorIds) {
                    titleLength = __DOCUMENTS__.readInt();
                    authorNamesLength = __DOCUMENTS__.readInt();
                    authorIdsLength = __DOCUMENTS__.readInt();
                }
                else if (hasAuthorNames) {
                    titleLength = __DOCUMENTS__.readInt();
                    authorNamesLength = __DOCUMENTS__.readInt();
                }
                else {
                    titleLength = __DOCUMENTS__.readInt();
                }

                if (hasTitle) {
                    if (!hasJournalName) {
                        __DOCUMENTS__.seek(documentPointer + DocumentEntry.TITLE_OFFSET);
                    }
                    byte[] title = new byte[titleLength];
                    __DOCUMENTS__.readFully(title);
                    docInfo.setProperty(DocInfoFull.PROPERTY.TITLE, new String(title, "UTF-8"));
                }

                if (hasAuthorNames) {
                    if (!hasTitle) {
                        __DOCUMENTS__.seek(documentPointer + DocumentEntry.TITLE_OFFSET + titleLength);
                    }
                    byte[] authorNames = new byte[authorNamesLength];
                    __DOCUMENTS__.readFully(authorNames);
                    docInfo.setProperty(DocInfoFull.PROPERTY.AUTHORS_NAMES, new String(authorNames, "UTF-8"));
                }

                if (hasAuthorIds) {
                    if (!hasAuthorNames) {
                        __DOCUMENTS__.seek(documentPointer + DocumentEntry.TITLE_OFFSET + titleLength + authorNamesLength);
                    }
                    byte[] authorIds = new byte[authorIdsLength];
                    __DOCUMENTS__.readFully(authorIds);
                    docInfo.setProperty(DocInfoFull.PROPERTY.AUTHORS_IDS, new String(authorIds, "ASCII"));
                }

                if (hasJournalName) {
                    if (!hasAuthorIds) {
                        __DOCUMENTS__.seek(documentPointer + DocumentEntry.TITLE_OFFSET + titleLength + authorNamesLength + authorIdsLength);
                    }
                    byte[] journalName = new byte[journalNameLength];
                    __DOCUMENTS__.readFully(journalName);
                    docInfo.setProperty(DocInfoFull.PROPERTY.JOURNAL_NAME, new String(journalName, "UTF-8"));
                }
            }
            docIds.add(termDocInfo);
        }
        return docIds;
    }

    public void updateDocInfo(List<Pair<Object, Double>> ranked_list, Set<DocInfo.PROPERTY> props) {

    }


    /**
     * Basic method for querying functionality. Given the list of terms in the
     * query, returns a List of Lists of String objects, where each
     * list of String objects holds the docIds of the
     * docs that the corresponding term of the query appears in.
     * @param terms
     * @return
     */
    public List<List<DocInfo>> getDocId(Set<String> terms) throws IOException {
        if (!loaded()) {
            return null;
        }
        Set<String> editedTerms = preprocessTerms(terms);
        List<List<DocInfo>> docIds = new ArrayList<>();
        List<DocInfo> termDocInfo;
        DocInfo docInfo;
        Pair<Integer, Long> termValue;
        long documentPointer;
        long postingPointer;
        int df;
        byte[] docId = new byte[DocumentEntry.ID_SIZE];
        for (String term : editedTerms) {
            termValue = __VOCABULARY__.get(term);
            if (termValue == null) {
                continue;
            }
            termDocInfo = new ArrayList<>();
            postingPointer = termValue.getR();
            df = termValue.getL();
            for (int i = 0; i < df; i++) {
                __POSTINGS__.seek(postingPointer + i * PostingEntry.SIZE + PostingEntry.TF_SIZE);
                documentPointer = __POSTINGS__.readLong();
                __DOCUMENTS__.seek(documentPointer);
                __DOCUMENTS__.readFully(docId);
                docInfo = new DocInfo(new String(docId, "ASCII"), documentPointer);
                termDocInfo.add(docInfo);
            }
            docIds.add(termDocInfo);
        }
        return docIds;
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
    public List<List<DocInfo>> getDocInfoEssentialForTerms(Set<String> terms) throws IOException {
        if (!loaded()) { // If indexes are not loaded
            return null;
        }
        Set<String> editedTerms = preprocessTerms(terms);
        List<List<DocInfo>> docInfoEssential_list = new ArrayList<>();
        List<DocInfo> termDocInfo;
        DocInfo docInfo;
        Pair<Integer, Long> termValue;
        long documentPointer;
        long postingPointer;
        int df;
        byte[] docId = new byte[DocumentEntry.ID_SIZE];
        for (String term : editedTerms) {
            termValue = __VOCABULARY__.get(term);
            if (termValue == null) {
                continue;
            }
            termDocInfo = new ArrayList<>();
            postingPointer = termValue.getR();
            df = termValue.getL();
            __POSTINGS__.seek(postingPointer);
            for (int i = 0; i < df; i++) {
                __POSTINGS__.seek(postingPointer + i * PostingEntry.SIZE + PostingEntry.TF_SIZE);
                documentPointer = __POSTINGS__.readLong();
                __DOCUMENTS__.seek(documentPointer);
                __DOCUMENTS__.readFully(docId);
                docInfo = new DocInfo(new String(docId, "ASCII"), documentPointer);
                docInfo.setProperty(DocInfo.PROPERTY.PAGERANK, __DOCUMENTS__.readDouble());
                docInfo.setProperty(DocInfo.PROPERTY.WEIGHT, __DOCUMENTS__.readDouble());
                docInfo.setProperty(DocInfo.PROPERTY.LENGTH, __DOCUMENTS__.readInt());
                termDocInfo.add(docInfo);
            }
            docInfoEssential_list.add(termDocInfo);
        }
        return docInfoEssential_list;
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
    public List<List<DocInfoFull>> getDocInfoFullTerms(Set<String> terms) throws IOException {
        if (!loaded()) { // If indexes are not loaded
            return null;
        }
        Set<String> editedTerms = preprocessTerms(terms);
        List<List<DocInfoFull>> docInfoFull_list = new ArrayList<>();
        List<DocInfoFull> termDocInfoFull;
        DocInfoFull docInfoFull;
        Pair<Integer, Long> termValue;
        long documentPointer;
        long postingPointer;
        int df;
        byte[] docId = new byte[DocumentEntry.ID_SIZE];
        for (String term : editedTerms) {
            termValue = __VOCABULARY__.get(term);
            if (termValue == null) {
                continue;
            }
            termDocInfoFull = new ArrayList<>();
            postingPointer = termValue.getR();
            df = termValue.getL();
            __POSTINGS__.seek(postingPointer);
            for (int i = 0; i < df; i++) {
                __POSTINGS__.seek(postingPointer + i * PostingEntry.SIZE + PostingEntry.TF_SIZE);
                documentPointer = __POSTINGS__.readLong();
                __DOCUMENTS__.seek(documentPointer);
                __DOCUMENTS__.readFully(docId);
                docInfoFull = new DocInfoFull(new String(docId, "ASCII"), documentPointer);

                double pageRank = __DOCUMENTS__.readDouble();
                docInfoFull.setProperty(DocInfo.PROPERTY.PAGERANK, pageRank);

                double weight = __DOCUMENTS__.readDouble();
                docInfoFull.setProperty(DocInfo.PROPERTY.WEIGHT, weight);

                int length = __DOCUMENTS__.readInt();
                docInfoFull.setProperty(DocInfo.PROPERTY.LENGTH, length);

                double avgAuthorRank = __DOCUMENTS__.readDouble();
                docInfoFull.setProperty(DocInfo.PROPERTY.AVG_AUTHOR_RANK, avgAuthorRank);

                short year = __DOCUMENTS__.readShort();
                docInfoFull.setProperty(DocInfoFull.PROPERTY.YEAR, year);

                int titleLength = __DOCUMENTS__.readInt();
                int authorIdsLength = __DOCUMENTS__.readInt();
                int authorNamesLength = __DOCUMENTS__.readInt();
                short journalNameLength = __DOCUMENTS__.readShort();

                byte[] title = new byte[titleLength];
                __DOCUMENTS__.readFully(title);
                docInfoFull.setProperty(DocInfoFull.PROPERTY.TITLE, new String(title, "UTF-8"));

                byte[] authorIds = new byte[authorIdsLength];
                __DOCUMENTS__.readFully(authorIds);
                docInfoFull.setProperty(DocInfoFull.PROPERTY.AUTHORS_IDS, new String(authorIds, "ASCII"));

                byte[] authorNames = new byte[authorNamesLength];
                __DOCUMENTS__.readFully(authorNames);
                docInfoFull.setProperty(DocInfoFull.PROPERTY.AUTHORS_NAMES, new String(authorNames, "UTF-8"));

                byte[] journalName = new byte[journalNameLength];
                __DOCUMENTS__.readFully(journalName);
                docInfoFull.setProperty(DocInfoFull.PROPERTY.JOURNAL_NAME, new String(journalName, "UTF-8"));

                termDocInfoFull.add(docInfoFull);
            }
            docInfoFull_list.add(termDocInfoFull);
        }
        return docInfoFull_list;
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
    public List<DocInfoFull> getDocDescription(List<DocInfo> docs) {
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
                && __DOCUMENTS__ != null && __META_INDEX_INFO__ != null;
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

    /**
     * Returns the retrieval model found in the config file
     * @return
     */
    public String getRetrievalModel() {
        return __CONFIG__.getRetrievalModel();
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
