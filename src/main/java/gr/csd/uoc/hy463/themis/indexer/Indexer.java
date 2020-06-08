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
import gr.csd.uoc.hy463.themis.indexer.model.*;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntryTermFrequencies;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.Stemmer;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.utils.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

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

    // configuration options
    private Config __CONFIG__;

    // The file path of indexes
    private String __INDEX_PATH__ = null;
    private String __INDEX_TMP_PATH__ = null;

    // Filenames of indexes
    private String __VOCABULARY_FILENAME__ = null;
    private String __POSTINGS_FILENAME__ = null;
    private String __DOCUMENTS_FILENAME__ = null;
    private String __META_FILENAME__ = null;

    // Vocabulary should be stored in memory for querying! This is crucial
    // since we want to keep things fast! This is done through load().
    // For this project use a HashMap instead of a trie
    private HashMap<String, VocabularyStruct> __VOCABULARY__ = null;
    private RandomAccessFile __POSTINGS__ = null;
    private RandomAccessFile __DOCUMENTS__ = null;

    // A list of buffers, each one is used for a different segment of the documents file
    DocumentBuffers __DOCUMENTS_BUFFERS__ = null;

    // A byte array that will hold a document entry
    byte[] __DOC_BYTE_ARRAY__;
    ByteBuffer __DOC_BYTE_BUFFER__;

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
    public Indexer(Config config) {
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
        __INDEX_TMP_PATH__ = __CONFIG__.getIndexTmpPath();
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
        if (!isIndexDirEmpty()) {
            __LOGGER__.error("Previous index found. Aborting...");
            Themis.print("Previous index found. Aborting...");
            return false;
        }

        if (__CONFIG__.getUseStemmer()) {
            Stemmer.Initialize();
        }
        if (__CONFIG__.getUseStopwords()) {
            StopWords.Initialize();
        }

        int documentsSplitSize = Integer.MAX_VALUE; // maximum buffer size
        List<Long> documentsBufferOffsets = new ArrayList<>();
        documentsBufferOffsets.add(0L);

        long totalDocumentsSize = 0;
        int articleSize = 0;

        long startTime = System.nanoTime();
        Themis.print(">>> Start indexing\n");

        File folder = new File(path);
        File[] files = folder.listFiles();         // Holds  all files in path
        if (files == null) {
            return true;
        }

        String json;
        S2TextualEntry entry;
        int totalArticles = 0;
        long totalArticleLength = 0;
        long docOffset = 0;
        long prevDocOffset;
        int maxDocSize = 0;
        __META_INDEX_INFO__ = new HashMap<>();
        S2TextualEntryTermFrequencies wordFrequencies = new S2TextualEntryTermFrequencies(__CONFIG__);

        /* Field frequencies of each term in a json entry */
        Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWords;

        /* the dataset file that is being parsed */
        BufferedReader currentDataFile;

        /* create index folders */
        Files.createDirectories(Paths.get(__INDEX_PATH__));
        Files.createDirectories(Paths.get(__INDEX_TMP_PATH__));

        /* The documents file for writing the article info */
        String documentsName =  __INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__;
        BufferedOutputStream documentsOut = new BufferedOutputStream(new FileOutputStream
                (new RandomAccessFile(documentsName, "rw").getFD()));

        /* Temp file that has the size of each document entry in bytes. Needed for fast
        access to the each document entry when an update of its info is required,
        e.g. calculating VSM weights */
        BufferedWriter docLengthWriter = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(__INDEX_TMP_PATH__ + "/doc_size"), "UTF-8"));

        /* Temp file that stores the <term, TF> of every term that appears in
        each article (one line per article). Will be used for fast calculation
        of the VSM weights */
        BufferedWriter termFreqWriter = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(__INDEX_TMP_PATH__ + "/doc_tf"), "UTF-8"));

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
                Themis.print("Processing file: " + file + "\n");
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
                    articleSize = (int) (docOffset - prevDocOffset);
                    if (articleSize > documentsSplitSize - totalDocumentsSize) {
                        documentsBufferOffsets.add(prevDocOffset);
                        totalDocumentsSize = articleSize;
                    }
                    else {
                        totalDocumentsSize += articleSize;
                    }

                    if (articleSize > maxDocSize) {
                        maxDocSize = articleSize;
                    }
                    docLengthWriter.write(articleSize + "\n");

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

        documentsBufferOffsets.add(docOffset);

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

        StringBuilder docBufferString = new StringBuilder();
        for (int i = 0; i < documentsBufferOffsets.size() - 1; i++) {
            docBufferString.append(documentsBufferOffsets.get(i)).append(",");
        }
        if (!documentsBufferOffsets.isEmpty()) {
            docBufferString.append(documentsBufferOffsets.get(documentsBufferOffsets.size() - 1));
        }

        /* save any info related to this index */
        __META_INDEX_INFO__.put("use_stemmer", String.valueOf(__CONFIG__.getUseStemmer()));
        __META_INDEX_INFO__.put("use_stopwords", String.valueOf(__CONFIG__.getUseStopwords()));
        __META_INDEX_INFO__.put("articles", String.valueOf(totalArticles));
        __META_INDEX_INFO__.put("avgdl", String.valueOf(avgdl));
        __META_INDEX_INFO__.put("index_path", __INDEX_PATH__);
        __META_INDEX_INFO__.put("max_doc_size", String.valueOf(maxDocSize));
        __META_INDEX_INFO__.put("doc_split_offsets", docBufferString.toString());

        BufferedWriter meta = new BufferedWriter(new FileWriter(__INDEX_PATH__ + "/" + __META_FILENAME__));
        for (Map.Entry<String, String> pair : __META_INDEX_INFO__.entrySet()) {
            meta.write(pair.getKey() + "=" + pair.getValue() + "\n");
        }
        meta.close();

        Themis.print("Partial index created in: " + Math.round((System.nanoTime() - startTime) / 1e7) / 100.0 + " sec\n");

        /* merge the vocabularies and delete them */
        mergeVocabularies(partialIndexes);
        try {
            for (Integer partialIndex : partialIndexes) {
                String partialIndexPath = __INDEX_TMP_PATH__ + "/" + partialIndex;
                deleteDir(new File(partialIndexPath + "/" + __VOCABULARY_FILENAME__));
            }
        } catch (IOException e) {
            Themis.print("Error deleting partial vocabularies\n");
        }

        /* update VSM weights and delete doc_size and tf files */
        updateVSMweights();
        deleteDir(new File(__INDEX_TMP_PATH__ + "/doc_size"));
        deleteDir(new File(__INDEX_TMP_PATH__ + "/doc_tf"));

        /* merge the postings and delete them, also delete the term_df file */
        mergePostings(partialIndexes);
        try {
            for (Integer partialIndex : partialIndexes) {
                String partialIndexPath = __INDEX_TMP_PATH__ + "/" + partialIndex;
                deleteDir(new File(partialIndexPath + "/" + __POSTINGS_FILENAME__));
            }
        } catch (IOException e) {
            Themis.print("Error deleting partial postings\n");
        }
        deleteDir(new File(__INDEX_TMP_PATH__ + "/term_df"));

        /* delete the tmp index */
        try {
            deleteDir(new File(__INDEX_TMP_PATH__ + "/"));
        } catch (IOException e) {
            Themis.print("Error deleting tmp index\n");
        }

        Themis.print(">>> End of indexing\n");
        return false;
    }

    /* Merges the partial vocabularies and creates a new single vocabulary */
    private void mergeVocabularies(List<Integer> partialIndexes) throws IOException {

        /* If there is only one partial index, move the vocabulary file in INDEX_PATH else merge the partial
        vocabularies */
        long startTime =  System.nanoTime();
        Themis.print(">>> Start Merging of partial vocabularies\n");
        if (partialIndexes.size() == 1) {
            String partialIndexPath = __INDEX_TMP_PATH__ + "/" + partialIndexes.get(0);
            Files.move(Paths.get(partialIndexPath + "/" + __VOCABULARY_FILENAME__),
                    Paths.get(__INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__),
                    StandardCopyOption.REPLACE_EXISTING);
        } else {
            combinePartialVocabularies(partialIndexes);
        }
        Themis.print("Partial vocabularies merged in: " + Math.round((System.nanoTime() - startTime) / 1e7) / 100.0 + " sec\n");
    }

    /* Merges the partial vocabularies when there are more than 1 partial indexes.
    Writes the merged vocabulary file */
    private void combinePartialVocabularies(List<Integer> partialIndexes) throws IOException {

        /* the partial vocabularies */
        BufferedReader[] vocabularyReader = new BufferedReader[partialIndexes.size()];
        for (int i = 0; i < partialIndexes.size(); i++) {
            String vocabularyPath = __INDEX_TMP_PATH__ + "/" + partialIndexes.get(i) + "/" + __VOCABULARY_FILENAME__;
            vocabularyReader[i] = new BufferedReader(new InputStreamReader(new FileInputStream(vocabularyPath), "UTF-8"));
        }

        /* the final vocabulary file */
        String vocabularyName = __INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__;
        BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(vocabularyName), "UTF-8"));

        BufferedWriter termDfWriter = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(__INDEX_TMP_PATH__ + "/term_df"), "UTF-8"));

        /* the previous lex min word */
        String prevMinTerm = "";

        /* keep all consecutive vocabulary entries that have equal terms in an array */
        List<PartialVocabularyStruct> equalTerms = new ArrayList<>();

        /* pointer to the postings file */
        long offset = 0;

        /* the current vocabulary entry that has the min lex word */
        PartialVocabularyStruct minTermVocabularyStruct;

        /* the next vocabulary entry in the same vocabulary file as the one that
        has the min lex word */
        PartialVocabularyStruct nextVocabularyStruct;

        /* put all first vocabulary entries (they have the min lex terms)
        from each partial vocabulary into a queue */
        PriorityQueue<PartialVocabularyStruct> vocabularyQueue = new PriorityQueue<>();
        for (int i = 0; i < partialIndexes.size(); i++) {
            minTermVocabularyStruct = getNextVocabularyEntry(vocabularyReader[i], i);
            if (minTermVocabularyStruct != null) {
                vocabularyQueue.add(new PartialVocabularyStruct(
                        minTermVocabularyStruct.get_term(),
                        minTermVocabularyStruct.get_df(),
                        minTermVocabularyStruct.get_offset(),
                        i));
            }
        }

        /* get the next min term */
        while((minTermVocabularyStruct = vocabularyQueue.poll()) != null) {

            /* if the min term is not equal to the previous term, we must write all
            vocabulary entries that are in the array of equal terms to the final
            vocabulary file */
            if (!minTermVocabularyStruct.get_term().equals(prevMinTerm) && !equalTerms.isEmpty()) {
                offset = dumpEqualTerms(equalTerms, vocabularyWriter, termDfWriter, offset);
            }

            /* save the current term for the next iteration */
            prevMinTerm = minTermVocabularyStruct.get_term();

            /* the current vocabulary entry is put into the array of equal terms */
            equalTerms.add(new PartialVocabularyStruct(prevMinTerm, minTermVocabularyStruct.get_df(),
                    minTermVocabularyStruct.get_offset(), minTermVocabularyStruct.get_indexId()));

            /* finally add the next vocabulary entry to the queue of min lex terms */
            int indexId = minTermVocabularyStruct.get_indexId();
            nextVocabularyStruct = getNextVocabularyEntry(vocabularyReader[indexId], indexId);
            if (nextVocabularyStruct != null) {
                vocabularyQueue.add(nextVocabularyStruct);
            }
        }

        /* we are done reading the vocabularies. Write the remaining vocabulary entries that are
        still in the array of equal terms to the final vocabulary file */
        if (!equalTerms.isEmpty()) {
            dumpEqualTerms(equalTerms, vocabularyWriter, termDfWriter, offset);
        }

        /* close any open files */
        for (int i = 0; i < partialIndexes.size(); i++) {
            vocabularyReader[i].close();
        }
        vocabularyWriter.close();
        termDfWriter.close();
    }

    /* Returns the next vocabulary entry (term, df, offset) that belongs to partial vocabulary with indexId */
    private PartialVocabularyStruct getNextVocabularyEntry(BufferedReader vocabularyReader, int indexId) throws IOException {
        String line;
        String[] fields;

        line = vocabularyReader.readLine();
        if (line != null) {
            fields = line.split(" ");
            return new PartialVocabularyStruct(fields[0], Integer.parseInt(fields[1]), Long.parseLong(fields[2]), indexId);
        }
        return null;
    }

    /* Used during merging of the partial vocabularies. Writes all entries in the array of equal terms to the final
    vocabulary files. Returns an offset to the postings file that will be used during the next iteration. Also writes
    all (partial index id, df) for each term to a file that will be used during the merging of postings */
    private long dumpEqualTerms(List<PartialVocabularyStruct> equalTerms, BufferedWriter vocabularyWriter,
                                BufferedWriter termDfWriter, long offset) throws IOException {
        int df = 0;

        //sort based on the partial index id. This ensures that postings will be written in the final
        //posting file always in the same order.
        equalTerms.sort(PartialVocabularyStruct.idComparator);

        //calculate final DF
        for (PartialVocabularyStruct equalTerm : equalTerms) {
            df += equalTerm.get_df();
            termDfWriter.write(equalTerm.get_indexId() + " " + equalTerm.get_df() + " ");
        }
        termDfWriter.write("\n");

        //finally write a new entry in the final vocabulary file
        vocabularyWriter.write(equalTerms.get(0).get_term() + " " + df + " " + offset + "\n");

        //and calculate the offset of the next term
        offset += df * PostingStruct.SIZE;

        equalTerms.clear();
        return offset;
    }

    /* Method that merges the partial postings and creates a new single posting file */
    public void mergePostings(List<Integer> partialIndexes) throws IOException {

        /* If there is only one partial index, move the posting file in INDEX_PATH else merge the partial postings */
        long startTime =  System.nanoTime();
        Themis.print(">>> Start Merging of partial postings\n");
        if (partialIndexes.size() == 1) {
            String partialIndexPath = __INDEX_TMP_PATH__ + "/" + partialIndexes.get(0);
            Files.move(Paths.get(partialIndexPath + "/" + __POSTINGS_FILENAME__),
                    Paths.get(__INDEX_PATH__ + "/" + __POSTINGS_FILENAME__),
                    StandardCopyOption.REPLACE_EXISTING);
        } else {
            combinePartialPostings(partialIndexes);
        }
        Themis.print("Partial postings merged in: " + Math.round((System.nanoTime() - startTime) / 1e7) / 100.0 + " sec\n");
    }

    /* Merges the partial postings when there are more than 1 partial indexes.
    Writes the merged posting file */
    private void combinePartialPostings(List<Integer> partialIndexes) throws IOException {

        /* the partial postings */
        BufferedInputStream[] postingsStream = new BufferedInputStream[partialIndexes.size()];
        for (int i = 0; i < partialIndexes.size(); i++) {
            String postingPath = __INDEX_TMP_PATH__ + "/" + partialIndexes.get(i) + "/" + __POSTINGS_FILENAME__;
            postingsStream[i] = new BufferedInputStream(new FileInputStream
                    (new RandomAccessFile(postingPath, "rw").getFD()));
        }

        /* the final posting file */
        String postingName = __INDEX_PATH__ + "/" + __POSTINGS_FILENAME__;
        BufferedOutputStream postingOut = new BufferedOutputStream(new FileOutputStream
                (new RandomAccessFile(postingName, "rw").getFD()));

        /* the file with the (partial index id, df) for each term */
        BufferedReader termDfReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_TMP_PATH__ + "/term_df"), "ASCII"));

        /* read each line of the file that has the (partial index id, df). Each line corresponds to
        the same line in the final vocabulary file, thus both lines refer to the same term */
        String line;
        String[] split;
        while ((line = termDfReader.readLine()) != null){
            split = line.split(" ");
            for (int i = 0; i < split.length; i+=2) {
                byte[] postings = new byte[Integer.parseInt(split[i + 1])  * PostingStruct.SIZE];
                postingsStream[Integer.parseInt(split[i])].read(postings); //read into postings byte array
                postingOut.write(postings);  //and write to final postings file
            }
        }

        /* close any open files */
        for (BufferedInputStream bufferedInputStream : postingsStream) {
            bufferedInputStream.close();
        }
        postingOut.close();
        termDfReader.close();
    }

    /* DOCUMENTS FILE => documents.idx (Random Access File)
     * Writes the appropriate document entry for a textual entry to the documents file and
     * returns an offset that is the sum of the previous offset plus the document entry size
     * (in bytes).
     *
     * For each entry it stores in the following order:
     * Document size (int => 4 bytes) |
     * DOCUMENT_ID (40 ASCII chars => 40 bytes) |
     * PageRank Score (double => 8 bytes) |
     * The weight (norm) of Document (double => 8 bytes) |
     * The max tf in the Document (int => 4 bytes) |
     * Length of Document (int => 4 bytes) |
     * Average author rank (double => 8 bytes) |
     * Year (short => 2 bytes) |
     * [Title size] (int => 4 bytes) |
     * [Author_1,Author_2, ...,Author_k] size (int => 4 bytes) |
     * [AuthorID_1, AuthorID_2, ...,Author_ID_k] size (int => 4 bytes) |
     * [Journal name] size (short => 2 bytes) |
     * Title (variable bytes / UTF-8) |
     * Author_1,Author_2, ...,Author_k (variable bytes / UTF-8) |
     * AuthorID_1, AuthorID_2, ...,Author_ID_k (variable bytes / ASCII) |
     * Journal name (variable bytes / UTF-8)
     *
     * ==> IMPORTANT NOTES
     *
     * Authors are separated by a comma
     *
     * Author ids are also separated with a comma
     *
     * For now 0.0 was added as PageRank, weight, max tf, average author rank */
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

        /* weight, maxTf, length, pagerank, avgAuthorRank */
        byte[] weight = ByteBuffer.allocate(DocumentEntry.WEIGHT_SIZE).putDouble(0).array();
        byte[] maxTf = ByteBuffer.allocate(DocumentEntry.MAX_TF_SIZE).putInt(0).array();
        byte[] length = ByteBuffer.allocate(DocumentEntry.LENGTH_SIZE).putInt(docLength).array();
        byte[] pageRank = ByteBuffer.allocate(DocumentEntry.PAGERANK_SIZE).putDouble(0).array();
        byte[] avgAuthorRank = ByteBuffer.allocate(DocumentEntry.AVG_AUTHOR_RANK_SIZE).putDouble(0).array();
        docEntryLength += DocumentEntry.WEIGHT_SIZE + DocumentEntry.MAX_TF_SIZE + DocumentEntry.LENGTH_SIZE +
                DocumentEntry.PAGERANK_SIZE + DocumentEntry.AVG_AUTHOR_RANK_SIZE;

        /* size of this entry */
        docEntryLength += DocumentEntry.SIZE_SIZE;
        byte[] docSize = ByteBuffer.allocate(DocumentEntry.SIZE_SIZE).putInt(docEntryLength).array();

        /* write first the fixed size fields */
        out.write(docSize);
        out.write(id);
        out.write(pageRank);
        out.write(weight);
        out.write(maxTf);
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

    /* Reads the frequencies file doc_tf and the documents size file doc_length and calculates the
    VSM weights and the max tf for each document entry. It then updates the documents file */
    public void updateVSMweights() throws IOException {
        long startTime = System.nanoTime();
        Themis.print(">>> Calculating VSM weights\n");

        /* load the vocabulary file except the offsets */
        Themis.print("Loading vocabulary terms...");
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
        Themis.print("DONE\n");

        /* open the required files: documents, doc_tf, doc_size */
        BufferedOutputStream documentsWriter = new BufferedOutputStream(new FileOutputStream
                (new RandomAccessFile(__INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__, "rw").getFD()));
        BufferedInputStream documentsReader = new BufferedInputStream(new FileInputStream
                (new RandomAccessFile(__INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__, "r").getFD()));

        BufferedReader tfReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_TMP_PATH__ + "/doc_tf"), "UTF-8"));
        BufferedReader docSizeReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_TMP_PATH__ + "/doc_size"), "UTF-8"));

        int[] tfs; //the TFs for each document
        double weight; //the weight of each document
        int maxTf = 0; //maximum tf in each document
        int totalArticles = Integer.parseInt(__META_INDEX_INFO__.get("articles"));

        //offsets required for reading/writing the weight and max frequency to the documents file
        int offset1 = DocumentEntry.ID_SIZE + DocumentEntry.SIZE_OFFSET + DocumentEntry.PAGERANK_SIZE;
        int offset2 = offset1 + DocumentEntry.WEIGHT_SIZE;
        int offset3 = offset2 + DocumentEntry.MAX_TF_SIZE;

        /* read an entry from the frequencies file and calculate the weight */
        while ((line = tfReader.readLine()) != null) {
            split = line.split(" ");
            if (split.length == 1) {
                weight = 0;
                maxTf = 0;
            }
            else {
                tfs = new int[split.length / 2];
                maxTf = 0;
                for (int i = 0; i < split.length; i += 2) {
                    tfs[i / 2] = Integer.parseInt(split[i + 1]);
                    if (tfs[i / 2] > maxTf) {
                        maxTf = tfs[i / 2];
                    }
                }
                weight = 0;
                for (int i = 0; i < split.length; i += 2) {
                    double x = (0.0 + tfs[i / 2]) / maxTf * Math.log((0.0 + totalArticles) /
                            vocabulary.get(split[i])) / Math.log(2);
                    weight += x * x;
                }
                weight = Math.sqrt(weight);
            }

            //update the documents file
            int docSize = Integer.parseInt(docSizeReader.readLine());
            byte[] documentEntry = new byte[docSize];
            documentsReader.read(documentEntry);
            byte[] wnew = ByteBuffer.allocate(8).putDouble(weight).array();
            byte[] tfnew = ByteBuffer.allocate(4).putInt(maxTf).array();
            System.arraycopy(wnew, 0, documentEntry, DocumentEntry.WEIGHT_OFFSET, offset2 - offset1);
            System.arraycopy(tfnew, 0, documentEntry, DocumentEntry.MAX_TF_OFFSET, offset3 - offset2);
            documentsWriter.write(documentEntry);
        }

        /* close files */
        tfReader.close();
        docSizeReader.close();
        documentsReader.close();
        documentsWriter.close();

        Themis.print("VSM weights calculated in: " + Math.round((System.nanoTime() - startTime) / 1e7) / 100.0 + " sec\n");
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
            Themis.print("DATASET_PATH not set in themis.config!\n");
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
            Themis.print("Index is not constructed correctly!\n");
            return false;
        }

        Themis.print("Index directory: " + __INDEX_PATH__+ "\n");
        Themis.print(">>> Loading vocabulary...");
        __VOCABULARY__ = new HashMap<>();
        String line;
        String[] fields;

        BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__), "UTF-8"));
        while ((line = vocabularyReader.readLine()) != null) {
            fields = line.split(" ");
            __VOCABULARY__.put(fields[0], new VocabularyStruct(Integer.parseInt(fields[1]), Long.parseLong(fields[2])));
        }
        vocabularyReader.close();
        Themis.print("DONE\n");

        //load meta index file
        Themis.print(">>> Loading meta index file...");
        BufferedReader meta = new BufferedReader(new FileReader(__INDEX_PATH__ + "/" + __META_FILENAME__));
        __META_INDEX_INFO__ = new HashMap<>();
        String[] split;
        while((line = meta.readLine()) != null) {
            split = line.split("=");
            __META_INDEX_INFO__.put(split[0], split[1]);
        }
        meta.close();
        Themis.print("DONE\n");

        //open postings
        Themis.print(">>> Opening documents, postings files...");
        __POSTINGS__ = new RandomAccessFile(__INDEX_PATH__ + "/" + __POSTINGS_FILENAME__, "r");

        //open documents
        String[] documentsBuffersOffsets_S = __META_INDEX_INFO__.get("doc_split_offsets").split(",");
        List<Long> documentsBuffersOffsets = new ArrayList<>();
        for (String documentsBuffersOffsets_ : documentsBuffersOffsets_S) {
            documentsBuffersOffsets.add(Long.parseLong(documentsBuffersOffsets_));
        }
        __DOCUMENTS_BUFFERS__ = new DocumentBuffers(documentsBuffersOffsets, __INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__);
        __DOC_BYTE_ARRAY__ = new byte[Integer.parseInt(__META_INDEX_INFO__.get("max_doc_size"))];
        __DOC_BYTE_BUFFER__ = ByteBuffer.wrap(__DOC_BYTE_ARRAY__);
        Themis.print("DONE\n");

        //check for stopword, stemming options
        if (Boolean.parseBoolean(__META_INDEX_INFO__.get("use_stopwords"))) {
            Themis.print("Stopwords is enabled\n");
            StopWords.Initialize();
        }
        else {
            Themis.print("Stopwords is disabled\n");
        }
        if (Boolean.parseBoolean(__META_INDEX_INFO__.get("use_stemmer"))) {
            Themis.print("Stemming is enabled\n");
            Stemmer.Initialize();
        }
        else {
            Themis.print("Stemming is disabled\n");
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
        if (__DOCUMENTS_BUFFERS__ != null) {
            __DOCUMENTS_BUFFERS__.close();
        }
        __VOCABULARY__ = null;
        __META_INDEX_INFO__ = null;
        __DOC_BYTE_ARRAY__ = null;
        __DOC_BYTE_BUFFER__ = null;
    }

    /**
     * Deletes the index and index_tmp directories.
     * @throws IOException
     */
    public void deleteIndex() throws IOException {
        Themis.print("Deleting previous index...");
        deleteDir(new File(__INDEX_PATH__ + "/"));
        deleteDir(new File(__INDEX_TMP_PATH__ + "/"));
        Themis.print("DONE\n");
    }

    /**
     * Returns true if index and index_tmp directories exist and are empty, false otherwise.
     * @return
     */
    public boolean isIndexDirEmpty() {
        File file = new File(__INDEX_PATH__);
        File[] fileList = file.listFiles();
        if (fileList != null && fileList.length != 0) {
            return false;
        }

        file = new File(__INDEX_TMP_PATH__);
        fileList = file.listFiles();
        return fileList == null || fileList.length == 0;
    }

    /**
     * Basic method for querying functionality. Given the list of terms in the query, returns a List of
     * Lists of DocInfo objects, where each DocInfo object objects holds the DocInfo representation of the
     * docs that the corresponding term of the query appears in.
     * @param terms The terms of the query (after pre-processing)
     * @param termsDocInfo An initial list of lists of DocInfo objects
     * @return
     * @throws IOException
     */
    public void getDocInfo(List<String> terms, List<List<DocInfo>> termsDocInfo, Set<DocInfo.PROPERTY> props) throws IOException {
        if (!loaded()) {
            return;
        }

        int fixedSize = DocumentEntry.ID_SIZE + DocumentEntry.PAGERANK_SIZE + DocumentEntry.WEIGHT_SIZE +
                DocumentEntry.LENGTH_SIZE + DocumentEntry.MAX_TF_SIZE + DocumentEntry.YEAR_SIZE +
                DocumentEntry.AVG_AUTHOR_RANK_SIZE;
        boolean hasVarFields = props.contains(DocInfo.PROPERTY.TITLE) || props.contains(DocInfo.PROPERTY.AUTHORS_NAMES) ||
                props.contains(DocInfo.PROPERTY.JOURNAL_NAME) || props.contains(DocInfo.PROPERTY.AUTHORS_IDS);
        for (int i = 0; i < terms.size(); i++) {
            List<DocInfo> termDocInfo = termsDocInfo.get(i);

            /* if we have already a result of docInfos for this term, just update the properties of each
            docInfo object */
            if (!termDocInfo.isEmpty()) {
                updateDocInfo(termDocInfo, props);
                continue;
            }

            /* go to the next term if this term does not appear in the vocabulary */
            VocabularyStruct termValue = __VOCABULARY__.get(terms.get(i));
            if (termValue == null) {
                continue;
            }

            /* go to the postings file and grab all postings for this term at once */
            long postingPointer = termValue.get_offset();
            __POSTINGS__.seek(postingPointer);
            byte[] postings = new byte[termValue.get_df() * PostingStruct.SIZE];
            __POSTINGS__.readFully(postings);
            ByteBuffer postingBuffer = ByteBuffer.wrap(postings);
            for (int j = 0; j < termValue.get_df(); j++) {
                long documentPointer = postingBuffer.getLong(j * PostingStruct.SIZE + PostingStruct.TF_SIZE);
                ByteBuffer buffer = __DOCUMENTS_BUFFERS__.getBuffer(documentPointer);
                int docSize = buffer.getInt();
                if (hasVarFields) {
                    buffer.get(__DOC_BYTE_ARRAY__, 0, docSize - DocumentEntry.SIZE_SIZE);
                }
                else {
                    buffer.get(__DOC_BYTE_ARRAY__, 0, fixedSize);
                }
                String docId = new String(__DOC_BYTE_ARRAY__, 0, DocumentEntry.ID_SIZE, "ASCII");
                DocInfo docInfo = new DocInfo(docId, documentPointer);
                if (!props.isEmpty()) {
                    fetchInfo(docInfo, props, DocumentEntry.ID_OFFSET, hasVarFields);
                }
                termDocInfo.add(docInfo);
            }
        }
    }

    /**
     * Reads the documents file and adds the docInfo properties specified by props to each of the docInfo objects
     * @param docInfos
     * @param props
     * @throws IOException
     */
    public void updateDocInfo(List<DocInfo> docInfos, Set<DocInfo.PROPERTY> props) throws IOException {
        if (props.isEmpty()) {
            return;
        }
        int fixedSize = DocumentEntry.ID_SIZE + DocumentEntry.PAGERANK_SIZE + DocumentEntry.WEIGHT_SIZE +
                DocumentEntry.LENGTH_SIZE + DocumentEntry.MAX_TF_SIZE + DocumentEntry.YEAR_SIZE +
                DocumentEntry.AVG_AUTHOR_RANK_SIZE;
        boolean hasVarFields = props.contains(DocInfo.PROPERTY.TITLE) || props.contains(DocInfo.PROPERTY.AUTHORS_NAMES) ||
                props.contains(DocInfo.PROPERTY.JOURNAL_NAME) || props.contains(DocInfo.PROPERTY.AUTHORS_IDS);
        for (DocInfo docInfo : docInfos) {
            Set<DocInfo.PROPERTY> extraProps = new HashSet<>(props);
            extraProps.removeAll(docInfo.getProps());
            if (!extraProps.isEmpty()) {
                long documentPointer = docInfo.getOffset();
                ByteBuffer buffer = __DOCUMENTS_BUFFERS__.getBuffer(documentPointer);
                int docSize = buffer.getInt();
                if (hasVarFields) {
                    buffer.get(__DOC_BYTE_ARRAY__, 0, docSize - DocumentEntry.SIZE_SIZE);
                }
                else {
                    buffer.get(__DOC_BYTE_ARRAY__, 0, fixedSize);
                }
                fetchInfo(docInfo, extraProps, DocumentEntry.ID_OFFSET, hasVarFields);
            }
        }
    }

    /* Reads the fields byte array and adds to the docInfo object the properties specified by props */
    private void fetchInfo(DocInfo docInfo, Set<DocInfo.PROPERTY> props, long offset, boolean hasVarFields) throws UnsupportedEncodingException {
        if (props.contains(DocInfo.PROPERTY.PAGERANK)) {
            double pagerank = __DOC_BYTE_BUFFER__.getDouble((int) (DocumentEntry.PAGERANK_OFFSET - offset));
            docInfo.setProperty(DocInfo.PROPERTY.PAGERANK, pagerank);
        }
        if (props.contains(DocInfo.PROPERTY.WEIGHT)) {
            double weight = __DOC_BYTE_BUFFER__.getDouble((int) (DocumentEntry.WEIGHT_OFFSET - offset));
            docInfo.setProperty(DocInfo.PROPERTY.WEIGHT, weight);
        }
        if (props.contains(DocInfo.PROPERTY.MAX_TF)) {
            int maxTf = __DOC_BYTE_BUFFER__.getInt((int) (DocumentEntry.MAX_TF_OFFSET - offset));
            docInfo.setProperty(DocInfo.PROPERTY.MAX_TF, maxTf);
        }
        if (props.contains(DocInfo.PROPERTY.LENGTH)) {
            int length = __DOC_BYTE_BUFFER__.getInt((int) (DocumentEntry.LENGTH_OFFSET - offset));
            docInfo.setProperty(DocInfo.PROPERTY.LENGTH, length);
        }
        if (props.contains(DocInfo.PROPERTY.AVG_AUTHOR_RANK)) {
            double authorRank = __DOC_BYTE_BUFFER__.getDouble((int) (DocumentEntry.AVG_AUTHOR_RANK_OFFSET - offset));
            docInfo.setProperty(DocInfo.PROPERTY.AVG_AUTHOR_RANK, authorRank);
        }
        if (props.contains(DocInfo.PROPERTY.YEAR)) {
            short year = __DOC_BYTE_BUFFER__.getShort((int) (DocumentEntry.YEAR_OFFSET - offset));
            docInfo.setProperty(DocInfo.PROPERTY.YEAR, year);
        }
        if (hasVarFields) {
            int titleSize = __DOC_BYTE_BUFFER__.getInt((int) (DocumentEntry.TITLE_SIZE_OFFSET - offset));
            int authorNamesSize = __DOC_BYTE_BUFFER__.getInt((int) (DocumentEntry.AUTHOR_NAMES_SIZE_OFFSET - offset));
            int authorIdsSize = __DOC_BYTE_BUFFER__.getInt((int) (DocumentEntry.AUTHOR_IDS_SIZE_OFFSET - offset));
            short journalNameSize = __DOC_BYTE_BUFFER__.getShort((int) (DocumentEntry.JOURNAL_NAME_SIZE_OFFSET - offset));
            if (props.contains(DocInfo.PROPERTY.TITLE)) {
                String title = new String(__DOC_BYTE_BUFFER__.array(), (int) (DocumentEntry.TITLE_OFFSET - offset), titleSize, "UTF-8");
                docInfo.setProperty(DocInfo.PROPERTY.TITLE, title);
            }
            if (props.contains(DocInfo.PROPERTY.AUTHORS_NAMES)) {
                String authorNames = new String(__DOC_BYTE_ARRAY__, (int) (DocumentEntry.TITLE_OFFSET - offset + titleSize), authorNamesSize, "UTF-8");
                docInfo.setProperty(DocInfo.PROPERTY.AUTHORS_NAMES, authorNames);
            }
            if (props.contains(DocInfo.PROPERTY.AUTHORS_IDS)) {
                String authorIds = new String(__DOC_BYTE_ARRAY__, (int) (DocumentEntry.TITLE_OFFSET - offset + titleSize + authorNamesSize), authorIdsSize, "UTF-8");
                docInfo.setProperty(DocInfo.PROPERTY.AUTHORS_IDS, authorIds);
            }
            if (props.contains(DocInfo.PROPERTY.JOURNAL_NAME)) {
                String journalName = new String(__DOC_BYTE_ARRAY__, (int) (DocumentEntry.TITLE_OFFSET - offset + titleSize + authorNamesSize + authorIdsSize), journalNameSize, "UTF-8");
                docInfo.setProperty(DocInfo.PROPERTY.JOURNAL_NAME, journalName);
            }
        }
    }

    /**
     * Method that checks if indexes have been loaded/opened
     *
     * @return
     */
    public boolean loaded() {
        return __VOCABULARY__ != null && __POSTINGS__ != null
                && __DOCUMENTS_BUFFERS__ != null && __META_INDEX_INFO__ != null;
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
     * Returns the total number of articles of this index
     * @return
     */
    public int getTotalArticles() {
        if (__META_INDEX_INFO__ != null) {
            return Integer.parseInt(__META_INDEX_INFO__.get("articles"));
        } else {
            __LOGGER__.error("Meta index info file is not loaded!");
            return 0;
        }
    }

    /**
     * Returns the avgdl used by the okapi bm25 retrieval model
     * @return
     */
    public double getAvgdl() {
        if (__META_INDEX_INFO__ != null) {
            return Double.parseDouble(__META_INDEX_INFO__.get("avgdl"));
        } else {
            __LOGGER__.error("Meta index info file is not loaded!");
            return 0;
        }
    }

    /**
     * Returns an array of the document frequencies (df) for each term in the specified list.
     * @param terms
     * @return
     */
    public int[] getDf(List<String> terms) {
        int[] dfs = new int[terms.size()];
        VocabularyStruct vocabularyValue;
        for (int i = 0; i < terms.size(); i++) {
            vocabularyValue = __VOCABULARY__.get(terms.get(i));
            if (vocabularyValue != null) {
                dfs[i] = vocabularyValue.get_df();
            }
        }
        return dfs;
    }

    /**
     * Returns an array of the frequencies of the specified term for every article that this term appears in.
     * @param term
     * @return
     * @throws IOException
     */
    public int[] getFreq(String term) throws IOException {
        VocabularyStruct vocabularyValue = __VOCABULARY__.get(term);
        if (vocabularyValue == null) {
            return new int[0];
        }
        int df = vocabularyValue.get_df();
        int[] freq = new int[df];
        __POSTINGS__.seek(vocabularyValue.get_offset());
        byte[] postings = new byte[df * PostingStruct.SIZE];
        __POSTINGS__.read(postings);
        ByteBuffer bb = ByteBuffer.wrap(postings);
        for (int i = 0; i < df; i++) {
            freq[i] = bb.getInt(i * PostingStruct.SIZE);
        }
        return freq;
    }

    /**
     * Returns the current retrieval model
     * @return
     */
    public ARetrievalModel.MODEL getDefaultRetrievalModel() {
        String modelName = __CONFIG__.getRetrievalModel();
        if (modelName.equals("BM25")) {
            return ARetrievalModel.MODEL.BM25;
        }
        else if (modelName.equals("VSM")) {
            return ARetrievalModel.MODEL.VSM;
        }
        else if (modelName.equals("Existential")) {
            return ARetrievalModel.MODEL.EXISTENTIAL;
        }
        return null;
    }

    /**
     * Returns true if stopwords is enabled for this index. Returns null if meta index info file is not loaded.
     * @return
     */
    public Boolean useStopwords() {
        if (__META_INDEX_INFO__ != null) {
            return Boolean.parseBoolean(__META_INDEX_INFO__.get("use_stopwords"));
        } else {
            __LOGGER__.error("Meta index info file is not loaded!");
            return null;
        }
    }

    /**
     * Returns true if stemming is enabled for this index. Returns null if meta index info file is not loaded.
     * @return
     */
    public Boolean useStemmer() {
        if (__META_INDEX_INFO__ != null) {
            return Boolean.parseBoolean(__META_INDEX_INFO__.get("use_stemmer"));
        } else {
            __LOGGER__.error("Meta index info file is not loaded!");
            return null;
        }
    }
}
