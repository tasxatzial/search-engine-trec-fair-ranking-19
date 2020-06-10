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
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocumentBuffers;
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocumentMetaBuffers;
import gr.csd.uoc.hy463.themis.indexer.indexes.Index;
import gr.csd.uoc.hy463.themis.indexer.model.*;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntryTermFrequencies;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.Stemmer;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.StopWords;
import gr.csd.uoc.hy463.themis.linkAnalysis.Pagerank;
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
    private String __DOCUMENTS_META_FILENAME__ = null;
    private String __META_FILENAME__ = null;

    // Vocabulary should be stored in memory for querying! This is crucial
    // since we want to keep things fast! This is done through load().
    // For this project use a HashMap instead of a trie
    private HashMap<String, VocabularyStruct> __VOCABULARY__ = null;

    private RandomAccessFile __POSTINGS__ = null;

    // has any information that appears in documents (except the docId)
    private RandomAccessFile __DOCUMENTS__ = null;

    // A list of buffers, each one is used for a different segment of the documents meta file
    DocumentMetaBuffers __DOCUMENTS_META_BUFFERS__ = null;

    // stores all information about a document from the documents meta file
    byte[] __DOCUMENT_META_ARRAY__;
    ByteBuffer __DOCUMENT_META_BUFFER__;

    // stores all information about a document from the documents file
    byte[] __DOCUMENT_ARRAY__;
    ByteBuffer __DOCUMENT_BUFFER__;

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
        __DOCUMENTS_META_FILENAME__ = __CONFIG__.getDocumentsMetaFileName();
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
            __LOGGER__.error(__DOCUMENTS_FILENAME__ + " documents binary file does not exist in " + __INDEX_PATH__);
            return false;
        }
        file = new File(__INDEX_PATH__ + __DOCUMENTS_META_FILENAME__);
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__DOCUMENTS_META_FILENAME__ + " documents meta binary file does not exist in " + __INDEX_PATH__);
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

        long startTime = System.nanoTime();
        Themis.print(">>> Start indexing\n");

        if (__CONFIG__.getUseStemmer()) {
            Stemmer.Initialize();
        }
        if (__CONFIG__.getUseStopwords()) {
            StopWords.Initialize();
        }

        // the buffer offsets to the documents file
        /*List<Long> documentsBufferOffsets = new ArrayList<>();
        documentsBufferOffsets.add(0L);

        int documentsMaxBufferSize = Integer.MAX_VALUE;
        long totalDocumentsSize = 0;*/
        int totalDocuments = 0;
        long totalDocumentLength = 0;
        long documentOffset = 0;
        long documentMetaOffset = 0;
        int maxDocumentSize = 0;

        // all files in dataset PATH
        File folder = new File(path);
        File[] files = folder.listFiles();
        if (files == null) {
            return true;
        }

        // sort the files so that we parse them in a specific order
        List<File> corpus = new ArrayList<>(files.length);
        corpus.addAll(Arrays.asList(files));
        Collections.sort(corpus);

        /* initialize the class that calculates the map of frequencies of a term in a document entry */
        S2TextualEntryTermFrequencies wordFrequencies = new S2TextualEntryTermFrequencies(__CONFIG__);

        /* create index folders */
        Files.createDirectories(Paths.get(__INDEX_PATH__));
        Files.createDirectories(Paths.get(__INDEX_TMP_PATH__));

        /* The documents file for writing document information */
        BufferedOutputStream documentsOut = new BufferedOutputStream(new FileOutputStream
                (new RandomAccessFile(__INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__, "rw").getFD()));

        /* The documents meta file for writing document meta information */
        BufferedOutputStream documentsMetaOut = new BufferedOutputStream(new FileOutputStream
                (new RandomAccessFile(__INDEX_PATH__ + "/" + __DOCUMENTS_META_FILENAME__, "rw").getFD()));

        /* Temp file that stores the <term, TF> of every term that appears in
        each document (one line per document). Will be used for fast calculation of VSM weights */
        BufferedWriter termFreqWriter = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(__INDEX_TMP_PATH__ + "/doc_tf"), "UTF-8"));

        // Use a linked list to keep the partial indexes ids
        List<Integer> partialIndexes = new LinkedList<>();

        // initialize a partial index
        Index index = new Index(__CONFIG__);
        int id = 1;
        index.setID(id);
        partialIndexes.add(id);

        // for each file in path
        for (File file : corpus) {
            if (file.isFile()) {
                Themis.print("Parsing file: " + file + "\n");
                BufferedReader currentDataFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String json;

                // for each scientific article in file
                while ((json = currentDataFile.readLine()) != null) {

                    // Extract all textual info
                    S2TextualEntry entry = S2JsonEntryReader.readTextualEntry(json);

                    // create the map of entry field frequencies for each term
                    Map<String, List<Pair<DocInfo.PROPERTY, Integer>>> entryWords = wordFrequencies.createWordsMap(entry);

                    // update total document length (total number of terms)
                    totalDocumentLength += entryWords.size();

                    // update the partial index as needed
                    index.add(entryWords, documentMetaOffset, termFreqWriter);

                    // update the documents file as needed
                    long prevDocumentOffset = documentOffset;
                    documentOffset = dumpDocuments(documentsOut, entry, documentOffset);

                    int documentSize = (int) (documentOffset - prevDocumentOffset);

                    // update the document meta file as needed
                    documentMetaOffset = dumpDocumentsMeta(documentsMetaOut, entry, entryWords.size(), documentSize, documentMetaOffset, prevDocumentOffset);

                    // calculate the maximum size of an entry in the document file
                    if (documentSize > maxDocumentSize) {
                        maxDocumentSize = documentSize;
                    }

                    // check if a dump of the current partial index is needed
                    totalDocuments++;
                    if (totalDocuments % __CONFIG__.getPartialIndexSize() == 0) {
                        index.dump();   // dump partial index to appropriate subdirectory
                        id++;
                        index = new Index(__CONFIG__);
                        index.setID(id);
                        partialIndexes.add(id);
                    }
                }
                currentDataFile.close();
            }
        }

        // docOffset at this point should be equal to the size of the documents file
        // documentsBufferOffsets.add(documentOffset);

        /* dump the last partial index if needed */
        if (totalDocuments != 0 && totalDocuments % __CONFIG__.getPartialIndexSize() == 0) {
            partialIndexes.remove(partialIndexes.size() - 1);
            id--;
        }
        else {
            index.dump();
        }

        documentsOut.close();
        documentsMetaOut.close();
        termFreqWriter.close();

        /* calculate avgdl for Okapi BM25 */
        double avgdl = (0.0 + totalDocumentLength) / totalDocuments;

        /* save any info related to this index */
        __META_INDEX_INFO__ = new HashMap<>();
        __META_INDEX_INFO__.put("use_stemmer", String.valueOf(__CONFIG__.getUseStemmer()));
        __META_INDEX_INFO__.put("use_stopwords", String.valueOf(__CONFIG__.getUseStopwords()));
        __META_INDEX_INFO__.put("articles", String.valueOf(totalDocuments));
        __META_INDEX_INFO__.put("avgdl", String.valueOf(avgdl));
        __META_INDEX_INFO__.put("max_doc_size", String.valueOf(maxDocumentSize));
        BufferedWriter meta = new BufferedWriter(new FileWriter(__INDEX_PATH__ + "/" + __META_FILENAME__));
        for (Map.Entry<String, String> pair : __META_INDEX_INFO__.entrySet()) {
            meta.write(pair.getKey() + "=" + pair.getValue() + "\n");
        }
        meta.close();

        Themis.print("Partial indexes created in: " + Math.round((System.nanoTime() - startTime) / 1e7) / 100.0 + " sec\n");

        /* merge the partial vocabularies and delete them */
        mergeVocabularies(partialIndexes);
        try {
            for (Integer partialIndex : partialIndexes) {
                String partialIndexPath = __INDEX_TMP_PATH__ + "/" + partialIndex;
                deleteDir(new File(partialIndexPath + "/" + __VOCABULARY_FILENAME__));
            }
        } catch (IOException e) {
            Themis.print("Error deleting partial vocabularies\n");
        }

        /* calculate VSM weights, update the documents file, and delete doc_tf file */
        updateVSMweights();
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

        /* compute the citations pagerank scores, update the documents file */
        Pagerank pagerank = new Pagerank();
        pagerank.citationsPagerank();

        /* finally delete the tmp index */
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
            vocabulary entries that are in the array of equal terms to the final vocabulary file */
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
    private void mergePostings(List<Integer> partialIndexes) throws IOException {

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

    /* Merges the partial postings when there are more than 1 partial indexes. Writes the merged posting file */
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

    /* DOCUMENTS META FILE => documents_meta.idx (Random Access File)
     * Writes the appropriate document entry for a textual entry to the documents file and
     * returns an offset that is the sum of the previous offset plus the document entry size
     * (in bytes).
     *
     * For each entry it stores in the following order:
     * DOCUMENT_ID (40 ASCII chars => 40 bytes) |
     * The weight (norm) of Document (double => 8 bytes) |
     * The max tf in the Document (int => 4 bytes) |
     * Length of Document (int => 4 bytes) |
     * PageRank Score (double => 8 bytes) |
     * Average author rank (double => 8 bytes) |
     * Size of the document (int => 4 bytes) |
     * Offset to the documents file (long => 8 bytes)
     *
     * For now 0 is added as PageRank, weight, max tf, average author rank */
    private long dumpDocumentsMeta(BufferedOutputStream out, S2TextualEntry textualEntry, int entryLength,
                                   int entrySize, long documentMetaOffset, long documentOffset) throws IOException {
        int totalSize = DocumentMetaEntry.WEIGHT_SIZE + DocumentMetaEntry.MAX_TF_SIZE + DocumentMetaEntry.LENGTH_SIZE +
                DocumentMetaEntry.PAGERANK_SIZE + DocumentMetaEntry.AVG_AUTHOR_RANK_SIZE + DocumentMetaEntry.ID_SIZE +
                DocumentMetaEntry.DOCUMENT_SIZE_SIZE + DocumentMetaEntry.DOCUMENT_OFFSET_SIZE;

        byte[] id = textualEntry.getId().getBytes("ASCII");
        byte[] weight = ByteBuffer.allocate(DocumentMetaEntry.WEIGHT_SIZE).putDouble(0).array();
        byte[] maxTf = ByteBuffer.allocate(DocumentMetaEntry.MAX_TF_SIZE).putInt(0).array();
        byte[] length = ByteBuffer.allocate(DocumentMetaEntry.LENGTH_SIZE).putInt(entryLength).array();
        byte[] pageRank = ByteBuffer.allocate(DocumentMetaEntry.PAGERANK_SIZE).putDouble(0).array();
        byte[] avgAuthorRank = ByteBuffer.allocate(DocumentMetaEntry.AVG_AUTHOR_RANK_SIZE).putDouble(0).array();
        byte[] size = ByteBuffer.allocate(DocumentMetaEntry.DOCUMENT_SIZE_SIZE).putInt(entrySize).array();
        byte[] offset = ByteBuffer.allocate(DocumentMetaEntry.DOCUMENT_OFFSET_SIZE).putLong(documentOffset).array();

        out.write(id);
        out.write(weight);
        out.write(maxTf);
        out.write(length);
        out.write(pageRank);
        out.write(avgAuthorRank);
        out.write(size);
        out.write(offset);

        return totalSize + documentMetaOffset;
    }

    /* DOCUMENTS FILE => documents.idx (Random Access File)
     * Writes the appropriate document entry for a textual entry to the documents file and
     * returns an offset that is the sum of the previous offset plus the document entry size
     * (in bytes).
     *
     * For each entry it stores in the following order:
     * Year (short => 2 bytes) |
     * [Title] size (int => 4 bytes) |
     * [Author_1,Author_2, ...,Author_k] size (int => 4 bytes) |
     * [AuthorID_1, AuthorID_2, ...,Author_ID_k] size (int => 4 bytes) |
     * [Journal name] size (short => 2 bytes / UTF-8) |
     * Title (variable bytes / UTF-8) |
     * Author_1,Author_2, ...,Author_k (variable bytes / UTF-8) |
     * AuthorID_1, AuthorID_2, ...,Author_ID_k (variable bytes / ASCII) |
     * Journal name (variable bytes / UTF-8)
     *
     * Authors are separated by a comma
     * Author ids are also separated with a comma */
    private long dumpDocuments(BufferedOutputStream out, S2TextualEntry textualEntry, long offset)
            throws IOException {
        int totalSize = 0;

        /* title */
        byte[] title = textualEntry.getTitle().getBytes("UTF-8");
        byte[] titleSize = ByteBuffer.allocate(DocumentEntry.TITLE_SIZE_SIZE).putInt(title.length).array();
        totalSize += title.length + DocumentEntry.TITLE_SIZE_SIZE;

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
        byte[] authorNamesSize = ByteBuffer.allocate(DocumentEntry.AUTHOR_NAMES_SIZE_SIZE).putInt(authorNames.length).array();
        totalSize += authorNames.length + DocumentEntry.AUTHOR_NAMES_SIZE_SIZE;

        byte[] authorIds = sb_authorIds.toString().getBytes("ASCII");
        byte[] authorIdsSize = ByteBuffer.allocate(DocumentEntry.AUTHOR_IDS_SIZE_SIZE).putInt(authorIds.length).array();
        totalSize += authorIds.length + DocumentEntry.AUTHOR_IDS_SIZE_SIZE;

        /* year */
        byte[] year = ByteBuffer.allocate(DocumentEntry.YEAR_SIZE).putShort((short) textualEntry.getYear()).array();
        totalSize += DocumentEntry.YEAR_SIZE;

        /* journal name */
        byte[] journalName = textualEntry.getJournalName().getBytes("UTF-8");
        byte[] journalNameSize = ByteBuffer.allocate(DocumentEntry.JOURNAL_NAME_SIZE_SIZE).putShort((short) journalName.length).array();
        totalSize += journalName.length + DocumentEntry.JOURNAL_NAME_SIZE_SIZE;

        /* write first the fixed size fields */
        out.write(year);
        out.write(titleSize);
        out.write(authorNamesSize);
        out.write(authorIdsSize);
        out.write(journalNameSize);

        /* write the variable size fields */
        out.write(title);
        out.write(authorNames);
        out.write(authorIds);
        out.write(journalName);

        return totalSize + offset;
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

    /* Reads the frequencies file doc_tf and calculates the
    VSM weights and the max tf for each document entry. It then updates the documents_meta file */
    public void updateVSMweights() throws IOException {
        long startTime = System.nanoTime();
        Themis.print(">>> Calculating VSM weights\n");

        /* load the vocabulary terms */
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

        /* open the required files: documents_meta, doc_tf */
        __DOCUMENTS_META_BUFFERS__ = new DocumentMetaBuffers(DocumentMetaBuffers.MODE.WRITE);
        BufferedReader tfReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(__INDEX_TMP_PATH__ + "/doc_tf"), "UTF-8"));

        int[] tfs;
        double weight;
        int maxTf = 0;
        int totalArticles = Integer.parseInt(__META_INDEX_INFO__.get("articles"));
        long documentMetaOffset = 0;
        int lineNum = 0;

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

            //update the documents_meta file
            documentMetaOffset = lineNum * DocumentMetaEntry.totalSize + DocumentMetaEntry.WEIGHT_OFFSET;
            ByteBuffer buffer = __DOCUMENTS_META_BUFFERS__.getBufferLong(documentMetaOffset);
            buffer.putDouble(weight);
            documentMetaOffset = lineNum * DocumentMetaEntry.totalSize + DocumentMetaEntry.MAX_TF_OFFSET;
            buffer = __DOCUMENTS_META_BUFFERS__.getBufferLong(documentMetaOffset);
            buffer.putInt(maxTf);
            lineNum++;
        }

        /* close files */
        tfReader.close();
        __DOCUMENTS_META_BUFFERS__.close();
        __DOCUMENTS_META_BUFFERS__ = null;

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

        //load vocabulary file
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

        //load index meta file
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

        //check for stopword, stemming
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

        Themis.print(">>> Opening documents, postings files...");
        //open postings
        __POSTINGS__ = new RandomAccessFile(__INDEX_PATH__ + "/" + __POSTINGS_FILENAME__, "r");

        //open documents, documents_meta files and initialize the appropriate structures
        __DOCUMENTS__ = new RandomAccessFile(__INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__, "r");
        __DOCUMENT_ARRAY__ = new byte[Integer.parseInt(__META_INDEX_INFO__.get("max_doc_size"))];
        __DOCUMENT_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_ARRAY__);
        __DOCUMENT_META_ARRAY__ = new byte[DocumentMetaEntry.totalSize];
        __DOCUMENT_META_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_META_ARRAY__);
        __DOCUMENTS_META_BUFFERS__ = new DocumentMetaBuffers(DocumentBuffers.MODE.READ);

        Themis.print("DONE\n\n");

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
        if (__DOCUMENTS_META_BUFFERS__ != null) {
            __DOCUMENTS_META_BUFFERS__.close();
            __DOCUMENTS_META_BUFFERS__ = null;
        }
        __VOCABULARY__ = null;
        __META_INDEX_INFO__ = null;
        __DOCUMENT_ARRAY__ = null;
        __DOCUMENT_BUFFER__ = null;
        __DOCUMENT_META_ARRAY__ = null;
        __DOCUMENT_META_BUFFER__ = null;
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
        boolean gotoDocuments = props.contains(DocInfo.PROPERTY.TITLE) || props.contains(DocInfo.PROPERTY.AUTHORS_NAMES) ||
                props.contains(DocInfo.PROPERTY.JOURNAL_NAME) || props.contains(DocInfo.PROPERTY.AUTHORS_IDS) ||
                props.contains(DocInfo.PROPERTY.YEAR);

        for (int i = 0; i < terms.size(); i++) {
            List<DocInfo> termDocInfo = termsDocInfo.get(i);

            //if we have already a result of docInfos for this term, just update the properties of each docInfo object
            if (!termDocInfo.isEmpty()) {
                updateDocInfo(termDocInfo, props);
                continue;
            }

            // go to the next term if this term does not appear in the vocabulary
            VocabularyStruct termValue = __VOCABULARY__.get(terms.get(i));
            if (termValue == null) {
                continue;
            }

            // go to the postings file and grab all postings for this term at once
            long postingPointer = termValue.get_offset();
            __POSTINGS__.seek(postingPointer);
            byte[] postings = new byte[termValue.get_df() * PostingStruct.SIZE];
            __POSTINGS__.readFully(postings);
            ByteBuffer postingBuffer = ByteBuffer.wrap(postings);

            // for each posting, grab any needed information from the documents_meta file and documents file
            for (int j = 0; j < termValue.get_df(); j++) {
                long documentMetaOffset = postingBuffer.getLong(j * PostingStruct.SIZE + PostingStruct.TF_SIZE);
                ByteBuffer buffer = __DOCUMENTS_META_BUFFERS__.getBufferLong(documentMetaOffset);
                buffer.get(__DOCUMENT_META_ARRAY__, 0, DocumentMetaEntry.totalSize);

                //grab information from the documents file
                if (gotoDocuments) {
                    long documentOffset = __DOCUMENT_META_BUFFER__.getLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET);
                    int documentSize = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                    __DOCUMENTS__.seek(documentOffset);
                    __DOCUMENTS__.readFully(__DOCUMENT_ARRAY__, 0, documentSize);
                }
                String docId = new String(__DOCUMENT_META_ARRAY__, 0, DocumentMetaEntry.ID_SIZE, "ASCII");
                DocInfo docInfo = new DocInfo(docId, documentMetaOffset);
                if (!props.isEmpty()) {
                    fetchInfo(docInfo, props, gotoDocuments);
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

        for (DocInfo docInfo : docInfos) {
            Set<DocInfo.PROPERTY> extraProps = new HashSet<>(props);
            extraProps.removeAll(docInfo.getProps());

            // grab only the properties that the docInfo object does not have
            if (!extraProps.isEmpty()) {
                long documentMetaOffset = docInfo.getMetaOffset();
                ByteBuffer buffer = __DOCUMENTS_META_BUFFERS__.getBufferLong(documentMetaOffset);
                buffer.get(__DOCUMENT_META_ARRAY__);
                boolean gotoDocuments = props.contains(DocInfo.PROPERTY.TITLE) || props.contains(DocInfo.PROPERTY.AUTHORS_NAMES) ||
                        props.contains(DocInfo.PROPERTY.JOURNAL_NAME) || props.contains(DocInfo.PROPERTY.AUTHORS_IDS) ||
                        props.contains(DocInfo.PROPERTY.YEAR);
                if (gotoDocuments) {
                    long documentOffset = __DOCUMENT_META_BUFFER__.getLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET);
                    int documentSize = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                    __DOCUMENTS__.seek(documentOffset);
                    __DOCUMENTS__.readFully(__DOCUMENT_ARRAY__, 0, documentSize);
                }
                fetchInfo(docInfo, extraProps, gotoDocuments);
            }
        }
    }

    /* Reads the fields byte array and adds to the docInfo object the properties specified by props */
    private void fetchInfo(DocInfo docInfo, Set<DocInfo.PROPERTY> props, boolean gotoDocuments) throws UnsupportedEncodingException {
        if (props.contains(DocInfo.PROPERTY.PAGERANK)) {
            double pagerank = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.PAGERANK_OFFSET);
            docInfo.setProperty(DocInfo.PROPERTY.PAGERANK, pagerank);
        }
        if (props.contains(DocInfo.PROPERTY.WEIGHT)) {
            double weight = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.WEIGHT_OFFSET);
            docInfo.setProperty(DocInfo.PROPERTY.WEIGHT, weight);
        }
        if (props.contains(DocInfo.PROPERTY.MAX_TF)) {
            int maxTf = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.MAX_TF_OFFSET);
            docInfo.setProperty(DocInfo.PROPERTY.MAX_TF, maxTf);
        }
        if (props.contains(DocInfo.PROPERTY.LENGTH)) {
            int length = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.LENGTH_OFFSET);
            docInfo.setProperty(DocInfo.PROPERTY.LENGTH, length);
        }
        if (props.contains(DocInfo.PROPERTY.AVG_AUTHOR_RANK)) {
            double authorRank = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.AVG_AUTHOR_RANK_OFFSET);
            docInfo.setProperty(DocInfo.PROPERTY.AVG_AUTHOR_RANK, authorRank);
        }
        if (gotoDocuments) {
            if (props.contains(DocInfo.PROPERTY.YEAR)) {
                short year = __DOCUMENT_BUFFER__.getShort(DocumentEntry.YEAR_OFFSET);
                docInfo.setProperty(DocInfo.PROPERTY.YEAR, year);
            }
            int titleSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.TITLE_SIZE_OFFSET);
            int authorNamesSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.AUTHOR_NAMES_SIZE_OFFSET);
            int authorIdsSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.AUTHOR_IDS_SIZE_OFFSET);
            short journalNameSize = __DOCUMENT_BUFFER__.getShort(DocumentEntry.JOURNAL_NAME_SIZE_OFFSET);
            if (props.contains(DocInfo.PROPERTY.TITLE)) {
                String title = new String(__DOCUMENT_ARRAY__, DocumentEntry.TITLE_OFFSET, titleSize, "UTF-8");
                docInfo.setProperty(DocInfo.PROPERTY.TITLE, title);
            }
            if (props.contains(DocInfo.PROPERTY.AUTHORS_NAMES)) {
                String authorNames = new String(__DOCUMENT_ARRAY__, DocumentEntry.TITLE_OFFSET  + titleSize, authorNamesSize, "UTF-8");
                docInfo.setProperty(DocInfo.PROPERTY.AUTHORS_NAMES, authorNames);
            }
            if (props.contains(DocInfo.PROPERTY.AUTHORS_IDS)) {
                String authorIds = new String(__DOCUMENT_ARRAY__, DocumentEntry.TITLE_OFFSET + titleSize + authorNamesSize, authorIdsSize, "UTF-8");
                docInfo.setProperty(DocInfo.PROPERTY.AUTHORS_IDS, authorIds);
            }
            if (props.contains(DocInfo.PROPERTY.JOURNAL_NAME)) {
                String journalName = new String(__DOCUMENT_ARRAY__, DocumentEntry.TITLE_OFFSET + titleSize + authorNamesSize + authorIdsSize, journalNameSize, "UTF-8");
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
        return __VOCABULARY__ != null && __POSTINGS__ != null && __DOCUMENTS__ != null
                 && __DOCUMENTS_META_BUFFERS__ != null && __META_INDEX_INFO__ != null;
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
        __POSTINGS__.readFully(postings);
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
