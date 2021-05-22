package gr.csd.uoc.hy463.themis.indexer;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocBuffers;
import gr.csd.uoc.hy463.themis.indexer.MemMap.MemBuffers;
import gr.csd.uoc.hy463.themis.indexer.indexes.Index;
import gr.csd.uoc.hy463.themis.indexer.model.*;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntryTermFrequencies;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
import gr.csd.uoc.hy463.themis.linkAnalysis.Pagerank;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.OKAPIprops;
import gr.csd.uoc.hy463.themis.retrieval.model.VSMprops;
import gr.csd.uoc.hy463.themis.retrieval.model.Posting;
import gr.csd.uoc.hy463.themis.utils.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The basic indexer class. This class is responsible for two tasks:
 *
 * a) Create the appropriate indexes given a specific directory with files (in
 * our case the Semantic Scholar collection)
 *
 * b) Given a path load the indexes (if they exist) and provide information
 * about the indexed data, that can be used for implementing any kind of
 * retrieval models
 */
public class Indexer {
    private static final Logger __LOGGER__ = LogManager.getLogger(Indexer.class);

    // configuration options
    private Config __CONFIG__;

    // The file path of indexes
    private String __INDEX_PATH__ = null;

    // Filenames of the files of the final index
    private String __VOCABULARY_FILENAME__ = null;
    private String __POSTINGS_FILENAME__ = null;
    private String __DOCUMENTS_FILENAME__ = null;
    private String __DOCUMENTS_META_FILENAME__ = null;
    private String __META_FILENAME__ = null;
    private String __DOCUMENTS_ID_FILENAME__ = null;

    //The vocabulary struct (always loaded in memory)
    private HashMap<String, VocabularyStruct> __VOCABULARY__ = null;

    // The postings file
    private RandomAccessFile __POSTINGS__ = null;

    // has any information that appears in the dataset (except the docId)
    private RandomAccessFile __DOCUMENTS__ = null;

    // Object for using the documents_meta file as a memory mapped file
    DocBuffers __DOCMETA_BUFFERS__ = null;

    // Object for using the documents_id file as a memory mapped file
    DocBuffers __DOCID_BUFFERS__ = null;

    // stores all information about an entry (see DocumentMetaEntry class) from the documents_meta file
    byte[] __DOCUMENT_META_ARRAY__;
    ByteBuffer __DOCUMENT_META_BUFFER__;

    // stores the doc ID of an entry (see DocumentIDEntry class) from the documents_id file
    byte[] __DOCUMENT_ID_ARRAY__;
    ByteBuffer __DOCUMENT_ID_BUFFER__;

    // stores all information about an entry (see DocumentEntry class) from the documents file
    byte[] __DOCUMENT_ARRAY__;
    ByteBuffer __DOCUMENT_BUFFER__;

    // This map holds any information related with the indexed collection
    // Such information could be the avgDL for the Okapi-BM25 implementation,
    // a timestamp of when the indexing process finished, the path of the indexed
    // collection, the options for stemming and stop-words used in the indexing process,
    // and whatever else we might want
    private Map<String, String> _INDEX_META__ = null;

    /**
     * Default constructor. Creates also a config instance
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public Indexer() throws IOException {
        this.__CONFIG__ = new Config();  // reads info from themis.config file
        __INDEX_PATH__ = getIndexPath();
        __VOCABULARY_FILENAME__ = __CONFIG__.getVocabularyFileName();
        __POSTINGS_FILENAME__ = __CONFIG__.getPostingsFileName();
        __DOCUMENTS_FILENAME__ = __CONFIG__.getDocumentsFileName();
        __DOCUMENTS_META_FILENAME__ = __CONFIG__.getDocumentsMetaFileName();
        __DOCUMENTS_ID_FILENAME__ = __CONFIG__.getDocumentsIDFileName();
        __META_FILENAME__ = __CONFIG__.getMetaFileName();
    }

    /**
     * Checks if the index directory specified in the INDEX_PATH prop in the themis.config file
     * has all the required files.
     *
     * @return
     */
    public boolean hasIndex() {
        File file;

        // Check if index files exist
        file = new File(getVocabularyPath());
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__VOCABULARY_FILENAME__ + " vocabulary file does not exist in " + __INDEX_PATH__);
            Themis.print(__VOCABULARY_FILENAME__ + " vocabulary file does not exist in " + __INDEX_PATH__ + "\n");
            return false;
        }
        file = new File(getPostingsPath());
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__POSTINGS_FILENAME__ + " postings binary file does not exist in " + __INDEX_PATH__);
            Themis.print(__POSTINGS_FILENAME__ + " postings binary file does not exist in " + __INDEX_PATH__ + "\n");
            return false;
        }
        file = new File(getDocumentsFilePath());
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__DOCUMENTS_FILENAME__ + " documents binary file does not exist in " + __INDEX_PATH__);
            Themis.print(__DOCUMENTS_FILENAME__ + " documents binary file does not exist in " + __INDEX_PATH__ + "\n");
            return false;
        }
        file = new File(getDocumentsIDFilePath());
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__DOCUMENTS_ID_FILENAME__ + " documents_ID binary file does not exist in " + __INDEX_PATH__);
            Themis.print(__DOCUMENTS_ID_FILENAME__ + " documents_ID binary file does not exist in " + __INDEX_PATH__ + "\n");
            return false;
        }
        file = new File(getDocumentsMetaFilePath());
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__DOCUMENTS_META_FILENAME__ + " documents_meta binary file does not exist in " + __INDEX_PATH__);
            Themis.print(__DOCUMENTS_META_FILENAME__ + " documents_meta binary file does not exist in " + __INDEX_PATH__ + "\n");
            return false;
        }
        file = new File(getMetaPath());
        if (!file.exists() || file.isDirectory()) {
            __LOGGER__.error(__META_FILENAME__ + " meta file does not exist in " + __INDEX_PATH__);
            Themis.print(__META_FILENAME__ + " meta file does not exist in " + __INDEX_PATH__ + "\n");
            return false;
        }
        return true;
    }

    /**
     * Method that indexes the collection specified in the DATASET_PATH prop in the themis.config file
     *
     * @return
     * @throws IOException
     */
    public void index() throws IOException {
        index(getDataSetPath());
    }

    /**
     * Method responsible for indexing a directory of files
     *
     * If the number of files is larger than the PARTIAL_INDEX_MAX_DOCS_SIZE specified
     * in themis.config file then we have to dump all data read up to now to
     * a partial index and continue with a new index. After creating all partial
     * indexes then we have to merge them to create the final index.
     *
     * @param path
     * @return
     * @throws IOException
     */
    public void index(String path) throws IOException {
        if (!isIndexDirEmpty()) {
            Themis.print("Previous index found. Please delete it first.\n");
            __LOGGER__.error("Previous index found. Please delete it first.");
            return;
        }

        __DOCUMENT_META_ARRAY__ = new byte[DocumentMetaEntry.totalSize];
        __DOCUMENT_META_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_META_ARRAY__);

        String indexTmpPath = getIndexTmpPath();
        int intID = 0;
        long totalDocumentLength = 0;
        long documentOffset = 0;

        // all files in dataset PATH
        File folder = new File(path);
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            Themis.print("No dataset files found in " + path + "\n");
            return;
        }

        /* save any info related to this index */
        _INDEX_META__ = new HashMap<>();
        Themis.print(">>> Indexing options:\n");
        _INDEX_META__.put("use_stemmer", String.valueOf(__CONFIG__.getUseStemmer()));
        Themis.print("Stemmer: " + __CONFIG__.getUseStemmer() + "\n");
        _INDEX_META__.put("use_stopwords", String.valueOf(__CONFIG__.getUseStopwords()));
        Themis.print("Stopwords: " + __CONFIG__.getUseStopwords() + "\n");
        _INDEX_META__.put("pagerank_damping", String.valueOf(__CONFIG__.getPagerankDampingFactor()));
        Themis.print("Pagerank damping factor: " + __CONFIG__.getPagerankDampingFactor() + "\n");
        _INDEX_META__.put("pagerank_threshold", String.valueOf(__CONFIG__.getPagerankThreshold()));
        Themis.print("Pagerank threshold: " + __CONFIG__.getPagerankThreshold() + "\n");

        long startTime = System.nanoTime();
        Themis.print(">>> Start indexing\n");

        // sort the files so that we parse them in a specific order
        List<File> corpus = new ArrayList<>(files.length);
        corpus.addAll(Arrays.asList(files));
        Collections.sort(corpus);

        /* initialize the class that calculates the map of frequencies of a term in a document entry */
        S2TextualEntryTermFrequencies wordFrequencies = new S2TextualEntryTermFrequencies(__CONFIG__.getUseStemmer(), __CONFIG__.getUseStopwords());

        /* create index folders */
        Files.createDirectories(Paths.get(__INDEX_PATH__));
        Files.createDirectories(Paths.get(indexTmpPath));

        /* the index metadata file */
        BufferedWriter metaWriter = new BufferedWriter(new FileWriter(getMetaPath()));

        /* The documents file for writing document information */
        RandomAccessFile documents = new RandomAccessFile(getDocumentsFilePath(), "rw");
        BufferedOutputStream documents_out = new BufferedOutputStream(new FileOutputStream(documents.getFD()));

        /* The documents meta file for writing document meta information */
        RandomAccessFile documentsMeta = new RandomAccessFile(getDocumentsMetaFilePath(), "rw");
        BufferedOutputStream documentsMeta_out = new BufferedOutputStream(new FileOutputStream(documentsMeta.getFD()));

        /* The documents id file for writing the document string ID */
        RandomAccessFile documentsID = new RandomAccessFile(getDocumentsIDFilePath(), "rw");
        BufferedOutputStream documentsID_out = new BufferedOutputStream(new FileOutputStream(documentsID.getFD()));

        /* Temp file that stores the <term, TF> of every term that appears in
        each document (one line per document). Will be used during the calculation of VSM weights */
        BufferedWriter docTfWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getDocTfPath()), "UTF-8"));

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
                    Map<String, List<DocInfoFrequency>> entryWords = wordFrequencies.createWordsMap(entry);

                    // update the partial index and the doc_tf file
                    int documentLength = index.add(entryWords, docTfWriter, intID);

                    // update document length (number of tokens)
                    totalDocumentLength += documentLength;

                    // update the documents file
                    long prevDocumentOffset = documentOffset;
                    documentOffset = dumpDocuments(documents_out, entry, documentOffset);

                    int documentSize = (int) (documentOffset - prevDocumentOffset);

                    // update the documents_meta file
                    dumpDocumentsMeta(documentsMeta_out, intID, documentLength, documentSize, prevDocumentOffset);

                    //update the documents_id file
                    dumpDocumentsID(documentsID_out, entry);

                    // check if a dump of the current partial index is needed
                    intID++;
                    if (intID % __CONFIG__.getPartialIndexSize() == 0) {
                        index.dump();
                        id++;
                        index = new Index(__CONFIG__);
                        index.setID(id);
                        partialIndexes.add(id);
                    }
                }
                currentDataFile.close();
            }
        }

        /* dump the last partial index if needed */
        if (intID != 0 && intID % __CONFIG__.getPartialIndexSize() == 0) {
            partialIndexes.remove(partialIndexes.size() - 1);
            id--;
        }
        else {
            index.dump();
        }

        documents_out.close();
        documentsMeta_out.close();
        documentsID_out.close();
        docTfWriter.close();

        /* calculate avgdl for Okapi BM25 */
        double avgdl = (0.0 + totalDocumentLength) / intID;

        _INDEX_META__.put("articles", String.valueOf(intID));
        _INDEX_META__.put("avgdl", String.valueOf(avgdl));
        _INDEX_META__.put("timestamp", Instant.now().toString());

        for (Map.Entry<String, String> pair : _INDEX_META__.entrySet()) {
            metaWriter.write(pair.getKey() + "=" + pair.getValue() + "\n");
        }
        metaWriter.close();

        Themis.print("Documents files & partial vocabularies/postings created in " + new Time(System.nanoTime() - startTime) + "\n");

        /* merge the partial vocabularies and delete them */
        mergeVocabularies(partialIndexes);
        try {
            for (Integer partialIndex : partialIndexes) {
                deleteDir(new File(getPartialVocabularyPath(partialIndex)));
            }
        } catch (IOException e) {
            Themis.print("Error deleting partial vocabularies\n");
        }

        /* calculate VSM weights, update the documents file, and delete doc_tf file */
        updateVSMweights();
        deleteDir(new File(getDocTfPath()));

        /* merge the postings and delete them, also delete the term_df file */
        mergePostings(partialIndexes);
        try {
            for (Integer partialIndex : partialIndexes) {
                deleteDir(new File(getPartialPostingPath(partialIndex)));
            }
        } catch (IOException e) {
            Themis.print("Error deleting partial postings\n");
        }
        deleteDir(new File(getTermDfPath()));

        /* compute the citations pagerank scores, update the documents file */
        Pagerank pagerank = new Pagerank(this);
        pagerank.citationsPagerank();

        /* finally delete the tmp index */
        try {
            deleteDir(new File(getIndexTmpPath()));
        } catch (IOException e) {
            Themis.print("Error deleting tmp index\n");
        }

        Themis.print(">>> End of indexing\n");
    }

    /* Merges the partial vocabularies and creates the final vocabulary.idx */
    private void mergeVocabularies(List<Integer> partialIndexes) throws IOException {

        /* If there is only one partial index, we need to append to each line a postings offset */
        long startTime =  System.nanoTime();
        Themis.print(">>> Start Merging of partial vocabularies\n");
        if (partialIndexes.size() == 1) {
            String partialVocabularyPath = getPartialVocabularyPath(partialIndexes.get(0));
            BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getVocabularyPath()), "UTF-8"));
            BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(partialVocabularyPath), "UTF-8"));
            String line;
            String[] split;
            long offset = 0;
            while ((line = vocabularyReader.readLine()) != null) {
                split = line.split(" ");
                int df = Integer.parseInt(split[1]);
                vocabularyWriter.write(split[0] + ' ' + split[1] + ' ' + offset + '\n');
                offset += (long) df * PostingStruct.SIZE;
            }
            vocabularyReader.close();
            vocabularyWriter.close();
        } else {
            combinePartialVocabularies(partialIndexes);
        }
        Themis.print("Partial vocabularies merged in " + new Time(System.nanoTime() - startTime) + "\n");
    }

    /* Merges the partial vocabularies when there are more than 1 partial indexes.
    Writes the merged vocabulary file */
    private void combinePartialVocabularies(List<Integer> partialIndexes) throws IOException {

        /* the partial vocabularies */
        BufferedReader[] vocabularyReader = new BufferedReader[partialIndexes.size()];
        for (int i = 0; i < partialIndexes.size(); i++) {
            String partialVocabularyPath = getPartialVocabularyPath(partialIndexes.get(i));
            vocabularyReader[i] = new BufferedReader(new InputStreamReader(new FileInputStream(partialVocabularyPath), "UTF-8"));
        }

        /* the final vocabulary file */
        BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getVocabularyPath()), "UTF-8"));

        BufferedWriter termDfWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getTermDfPath()), "ASCII"));

        /* the previous lex min word */
        String prevMinTerm = "";

        /* keep all consecutive vocabulary entries that have equal terms in an array */
        List<PartialVocabularyStruct> equalTerms = new ArrayList<>();

        /* pointer to the postings file */
        long postingsOffset = 0;

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
                        i));
            }
        }

        /* get the next min term */
        while((minTermVocabularyStruct = vocabularyQueue.poll()) != null) {

            /* if the min term is not equal to the previous term, we must write all
            vocabulary entries that are in the array of equal terms to the final vocabulary file */
            if (!minTermVocabularyStruct.get_term().equals(prevMinTerm) && !equalTerms.isEmpty()) {
                postingsOffset = dumpEqualTerms(equalTerms, vocabularyWriter, termDfWriter, postingsOffset);
            }

            /* save the current term for the next iteration */
            prevMinTerm = minTermVocabularyStruct.get_term();

            /* the current vocabulary entry is put into the array of equal terms */
            equalTerms.add(new PartialVocabularyStruct(prevMinTerm, minTermVocabularyStruct.get_df(),
                    minTermVocabularyStruct.get_indexId()));

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
            dumpEqualTerms(equalTerms, vocabularyWriter, termDfWriter, postingsOffset);
        }

        /* close any open files */
        for (int i = 0; i < partialIndexes.size(); i++) {
            vocabularyReader[i].close();
        }
        vocabularyWriter.close();
        termDfWriter.close();
    }

    /* Returns the next vocabulary entry (term, df) that belongs to partial vocabulary with indexId */
    private PartialVocabularyStruct getNextVocabularyEntry(BufferedReader vocabularyReader, int indexId) throws IOException {
        String line = vocabularyReader.readLine();
        if (line != null) {
            String[] fields = line.split(" ");
            return new PartialVocabularyStruct(fields[0], Integer.parseInt(fields[1]), indexId);
        }
        return null;
    }

    /* Used during merging of the partial vocabularies. Writes all entries in the array of equal terms to the final
    vocabulary files. Returns an offset to the postings file that will be used during the next iteration. Also writes
    all (partial index id, df) for each term to term_df file that will be used during the merging of postings */
    private long dumpEqualTerms(List<PartialVocabularyStruct> equalTerms, BufferedWriter vocabularyWriter, BufferedWriter termDfWriter, long postingsOffset)
            throws IOException {
        int df = 0;

        //sort based on the partial index id. This ensures that postings will be written in the final
        //posting file always in the same order.
        equalTerms.sort(PartialVocabularyStruct.idComparator);

        //calculate final DF and write the (partial index id, df) for this term
        StringBuilder sb = new StringBuilder();
        for (PartialVocabularyStruct equalTerm : equalTerms) {
            df += equalTerm.get_df();
            sb.append(equalTerm.get_indexId()).append(' ').append(equalTerm.get_df()).append(' ');
        }
        sb.append('\n');
        termDfWriter.write(sb.toString());

        //write a new entry in the final vocabulary file
        vocabularyWriter.write(equalTerms.get(0).get_term() + ' ' + df + ' ' + postingsOffset + '\n');

        //calculate the posting offset of the next term
        postingsOffset += (long) df * PostingStruct.SIZE;

        equalTerms.clear();
        return postingsOffset;
    }

    /* Method that merges the partial postings and creates the final postings.idx */
    private void mergePostings(List<Integer> partialIndexes) throws IOException {

        /* If there is only one partial index, move the posting file in INDEX_PATH else merge the partial postings */
        long startTime =  System.nanoTime();
        Themis.print(">>> Start Merging of partial postings\n");
        if (partialIndexes.size() == 1) {
            String partialPostingPath = getPartialPostingPath(partialIndexes.get(0));
            Files.move(Paths.get(partialPostingPath), Paths.get(getPostingsPath()), StandardCopyOption.REPLACE_EXISTING);
        } else {
            combinePartialPostings(partialIndexes);
        }
        Themis.print("Partial postings merged in " + new Time(System.nanoTime() - startTime) + "\n");
    }

    /* Merges the partial postings when there are more than 1 partial indexes. Writes the merged posting file */
    private void combinePartialPostings(List<Integer> partialIndexes) throws IOException {

        /* the partial postings */
        BufferedInputStream[] postingsStream = new BufferedInputStream[partialIndexes.size()];
        for (int i = 0; i < partialIndexes.size(); i++) {
            String partialPostingPath = getPartialPostingPath(partialIndexes.get(i));
            postingsStream[i] = new BufferedInputStream(new FileInputStream(new RandomAccessFile(partialPostingPath, "rw").getFD()));
        }

        /* the final posting file */
        BufferedOutputStream postingsWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(getPostingsPath(), "rw").getFD()));

        /* the file with the (partial index id, df) for each term */
        BufferedReader termDfReader = new BufferedReader(new InputStreamReader(new FileInputStream(getTermDfPath()), "ASCII"));

        /* read each line of the file that has the (partial index id, df). Each line corresponds to
        the same line in the final vocabulary file, thus both lines refer to the same term */
        String line;
        while ((line = termDfReader.readLine()) != null) {
            List<String> split = ProcessText.splitString(line);
            for (int i = 0; i < split.size(); i+=2) {
                byte[] postings = new byte[Integer.parseInt(split.get(i + 1)) * PostingStruct.SIZE];
                postingsStream[Integer.parseInt(split.get(i))].read(postings); //read into postings byte array
                postingsWriter.write(postings);  //and write to final postings file
            }
        }

        /* close any open files */
        for (BufferedInputStream bufferedInputStream : postingsStream) {
            bufferedInputStream.close();
        }
        postingsWriter.close();
        termDfReader.close();
    }

    /* DOCUMENTS META FILE => documents_meta.idx (Random Access File)
     * Writes all meta info for a textual entry to the documents_meta file.
     *
     * For each document it stores in the following order:
     * The int ID of the document (int => 4 bytes)
     * The weight (norm) of the Document (double => 8 bytes)
     * The max tf in the Document (int => 4 bytes)
     * Length of Document (int => 4 bytes). This is the number of terms in the document.idx
     * PageRank Score (double => 8 bytes)
     * Average author rank (double => 8 bytes)
     * Size of the document (int => 4 bytes). This is the size of the entry in the documents.idx
     * Offset to the documents file (long => 8 bytes)
     *
     * PageRank, weight, max tf, average author rank are all initialized to 0 */
    private void dumpDocumentsMeta(BufferedOutputStream out, int intID, int entryLength, int entrySize, long documentOffset)
            throws IOException {
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.INTID_OFFSET, intID);
        __DOCUMENT_META_BUFFER__.putDouble(DocumentMetaEntry.VSM_WEIGHT_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.MAX_TF_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.TOKEN_COUNT_OFFSET, entryLength);
        __DOCUMENT_META_BUFFER__.putDouble(DocumentMetaEntry.CITATIONS_PAGERANK_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putDouble(DocumentMetaEntry.AVG_AUTHOR_RANK_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET, entrySize);
        __DOCUMENT_META_BUFFER__.putLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET, documentOffset);
        __DOCUMENT_META_BUFFER__.position(0);
        out.write(__DOCUMENT_META_ARRAY__);
    }

    /* DOCUMENTS ID FILE => documents_id.idx (Random Access File)
     * Writes the document ID of a textual entry to the documents_id file
     *
     * For each document it stores:
     * DOCUMENT_ID (40 ASCII chars => 40 bytes) */
    private void dumpDocumentsID(BufferedOutputStream out, S2TextualEntry textualEntry) throws IOException {
        out.write(textualEntry.getId().getBytes("ASCII"));
    }

    /* DOCUMENTS FILE => documents.idx (Random Access File)
     * Writes the appropriate document entry for a textual entry to the documents file and
     * returns an offset that is the sum of the previous offset + the size of the written document entry.
     *
     * For each entry it stores in the following order:
     * Year (short => 2 bytes)
     * [Title] size (int => 4 bytes)
     * [Author_1,Author_2, ...,Author_k] size (int => 4 bytes)
     * [AuthorID_1, AuthorID_2, ...,Author_ID_k] size (int => 4 bytes)
     * [Journal name] size (short => 2 bytes / UTF-8)
     * Title (variable bytes / UTF-8)
     * Author_1,Author_2, ...,Author_k (variable bytes / UTF-8)
     * AuthorID_1, AuthorID_2, ...,Author_ID_k (variable bytes / ASCII)
     * Journal name (variable bytes / UTF-8)
     *
     * Authors are separated by a comma
     * Author ids are separated by a comma */
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
                sb_authorNames.append(',');
                sb_authorIds.append(',');
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

    /* Deletes everything in indexPath including indexPath */
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
    private void updateVSMweights() throws IOException {
        long startTime = System.nanoTime();
        Themis.print(">>> Calculating VSM weights\n");

        /* load the vocabulary terms */
        BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(getVocabularyPath()), "UTF-8"));
        Map<String, Integer> vocabulary = new HashMap<>();
        String line;
        String[] split;
        while ((line = vocabularyReader.readLine()) != null) {
            split = line.split(" ");
            vocabulary.put(split[0], Integer.parseInt(split[1]));
        }
        vocabularyReader.close();

        /* open the required files: documents_meta, doc_tf */
        __DOCMETA_BUFFERS__ = new DocBuffers(getDocumentsMetaFilePath(), MemBuffers.MODE.WRITE, DocumentMetaEntry.totalSize);
        BufferedReader docTfReader = new BufferedReader(new InputStreamReader(new FileInputStream(getDocTfPath()), "UTF-8"));

        int totalArticles = Integer.parseInt(_INDEX_META__.get("articles"));
        double logArticles = Math.log(totalArticles);
        long offset = 0;

        /* read a line from the doc_tf file and calculate the weight */
        while ((line = docTfReader.readLine()) != null) {
            List<String> splitList = ProcessText.splitString(line);
            double weight = 0;
            int maxTf = 0;
            for (int i = 0; i < splitList.size(); i += 2) {
                int df = vocabulary.get(splitList.get(i));
                int tf = Integer.parseInt(splitList.get(i + 1));
                if (tf > maxTf) {
                    maxTf = tf;
                }
                double x = tf * (logArticles - Math.log(df));
                weight += x * x;
            }
            weight = Math.sqrt(weight) / maxTf;

            //update the documents_meta file
            ByteBuffer buffer = __DOCMETA_BUFFERS__.getBufferLong(offset + DocumentMetaEntry.VSM_WEIGHT_OFFSET);
            buffer.putDouble(weight);
            buffer = __DOCMETA_BUFFERS__.getBufferLong(offset + DocumentMetaEntry.MAX_TF_OFFSET);
            buffer.putInt(maxTf);
            offset += DocumentMetaEntry.totalSize;
        }

        /* close files */
        docTfReader.close();
        __DOCMETA_BUFFERS__.close();
        __DOCMETA_BUFFERS__ = null;

        Themis.print("VSM weights calculated in " + new Time(System.nanoTime() - startTime) + "\n");
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
            return false;
        }

        Themis.print(">>> Index path: " + __INDEX_PATH__ + "\n");

        //load index meta file
        _INDEX_META__ = loadMeta();
        Themis.print("Stemming: " + _INDEX_META__.get("use_stemmer") + "\n");
        Themis.print("Stopwords: " + _INDEX_META__.get("use_stopwords") + "\n");

        if (__CONFIG__.getUseQueryExpansion()) {
            Themis.print("Default query expansion model: " + __CONFIG__.getQueryExpansionModel() + "\n");
        }
        else {
            Themis.print("Default query expansion model: None\n");
        }
        Themis.print("Default retrieval model: " + __CONFIG__.getRetrievalModel() + "\n");
        Themis.print(">>> Loading index...");

        //load vocabulary file
        __VOCABULARY__ = new HashMap<>();
        String line;
        String[] fields;

        BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(getVocabularyPath()), "UTF-8"));
        while ((line = vocabularyReader.readLine()) != null) {
            fields = line.split(" ");
            __VOCABULARY__.put(fields[0], new VocabularyStruct(Integer.parseInt(fields[1]), Long.parseLong(fields[2])));
        }
        vocabularyReader.close();

        __POSTINGS__ = new RandomAccessFile(getPostingsPath(), "r");
        __DOCUMENTS__ = new RandomAccessFile(getDocumentsFilePath(), "r");

        __DOCMETA_BUFFERS__ = new DocBuffers(getDocumentsMetaFilePath(), MemBuffers.MODE.READ, DocumentMetaEntry.totalSize);
        __DOCUMENT_META_ARRAY__ = new byte[DocumentMetaEntry.totalSize];
        __DOCUMENT_META_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_META_ARRAY__);
        __DOCID_BUFFERS__ = new DocBuffers(getDocumentsIDFilePath(), MemBuffers.MODE.READ, DocumentIDEntry.totalSize);
        __DOCUMENT_ID_ARRAY__ = new byte[DocumentIDEntry.totalSize];
        __DOCUMENT_ID_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_ID_ARRAY__);
        Themis.print("DONE\n");

        return true;
    }

    /**
     * Returns a Map of the index meta information as found in the specified filename
     * @return
     * @throws IOException
     */
    public Map<String, String> loadMeta() throws IOException {
        if (!hasIndex()) {
            return null;
        }
        BufferedReader indexMetaReader = new BufferedReader(new FileReader(getMetaPath()));
        Map<String, String> meta = new HashMap<>();
        String[] split;
        String line;
        while((line = indexMetaReader.readLine()) != null) {
            split = line.split("=");
            meta.put(split[0], split[1]);
        }
        indexMetaReader.close();
        return meta;
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
        if (__DOCMETA_BUFFERS__ != null) {
            __DOCMETA_BUFFERS__.close();
            __DOCMETA_BUFFERS__ = null;
        }
        if (__DOCID_BUFFERS__ != null) {
            __DOCID_BUFFERS__.close();
            __DOCID_BUFFERS__ = null;
        }
        __VOCABULARY__ = null;
        _INDEX_META__ = null;
    }

    /**
     * Deletes the index and index_tmp directories.
     * @throws IOException
     */
    public void deleteIndex() throws IOException {
        Themis.print(">>> Deleting previous index...");
        deleteDir(new File(getIndexPath()));
        deleteDir(new File(getIndexTmpPath()));
        Themis.print("DONE\n");
    }

    /**
     * Returns true if index and index_tmp directories exist and are empty, false otherwise.
     * @returnn
     */
    public boolean isIndexDirEmpty() {
        File file = new File(__INDEX_PATH__);
        File[] fileList = file.listFiles();
        if (fileList != null && fileList.length != 0) {
            return false;
        }
        file = new File(getIndexTmpPath());
        fileList = file.listFiles();
        return fileList == null || fileList.length == 0;
    }

    /**
     * Returns the string ID of a document based on its intID
     * @param intID
     * @return
     * @throws UnsupportedEncodingException
     */
    public String getDocID(int intID) throws UnsupportedEncodingException {
        long docIdOffset = DocInfo.getDocIdOffset(intID);
        ByteBuffer buffer = __DOCID_BUFFERS__.getBufferLong(docIdOffset);
        buffer.get(__DOCUMENT_ID_ARRAY__);
        String docId = new String(__DOCUMENT_ID_ARRAY__, 0, DocumentIDEntry.ID_SIZE, "ASCII");

        return docId;
    }

    /**
     * Reads the documents file and adds the docInfo properties specified by props to each of the docInfo objects.
     * The function assumes that all DocInfo objects have the same props.
     *
     * @param props
     * @throws IOException
     */
    public void updateDocInfo(List<Pair<DocInfo, Double>> docInfos, Set<DocInfo.PROPERTY> props) throws IOException {
        if (docInfos.size() == 0) {
            return;
        }
        Set<DocInfo.PROPERTY> firstProps = docInfos.get(0).getL().getProps();

        //these props will be deleted from each docinfo
        Set<DocInfo.PROPERTY> delProps = new HashSet<>(firstProps);
        delProps.removeAll(props);

        //these props will be added to each docinfo
        Set<DocInfo.PROPERTY> addProps = new HashSet<>(props);
        addProps.removeAll(firstProps);

        //add flags for each prop
        boolean ADD_CITATIONS_PAGERANK = addProps.contains(DocInfo.PROPERTY.CITATIONS_PAGERANK);
        boolean ADD_VSM_WEIGHT = addProps.contains(DocInfo.PROPERTY.VSM_WEIGHT);
        boolean ADD_MAX_TF = addProps.contains(DocInfo.PROPERTY.MAX_TF);
        boolean ADD_TOKEN_COUNT = addProps.contains(DocInfo.PROPERTY.TOKEN_COUNT);
        boolean ADD_AVG_AUTHOR_RANK = addProps.contains(DocInfo.PROPERTY.AVG_AUTHOR_RANK);
        boolean ADD_TITLE = addProps.contains(DocInfo.PROPERTY.TITLE);
        boolean ADD_AUTHORS_NAMES = addProps.contains(DocInfo.PROPERTY.AUTHORS_NAMES);
        boolean ADD_JOURNAL_NAME = addProps.contains(DocInfo.PROPERTY.JOURNAL_NAME);
        boolean ADD_AUTHORS_IDS = addProps.contains(DocInfo.PROPERTY.AUTHORS_IDS);
        boolean ADD_YEAR = addProps.contains(DocInfo.PROPERTY.YEAR);
        boolean ADD_DOC_SIZE = addProps.contains(DocInfo.PROPERTY.DOCUMENT_SIZE);

        //delete flags for each prop
        boolean DEL_CITATIONS_PAGERANK = delProps.contains(DocInfo.PROPERTY.CITATIONS_PAGERANK);
        boolean DEL_VSM_WEIGHT = delProps.contains(DocInfo.PROPERTY.VSM_WEIGHT);
        boolean DEL_MAX_TF = delProps.contains(DocInfo.PROPERTY.MAX_TF);
        boolean DEL_TOKEN_COUNT = delProps.contains(DocInfo.PROPERTY.TOKEN_COUNT);
        boolean DEL_AVG_AUTHOR_RANK = delProps.contains(DocInfo.PROPERTY.AVG_AUTHOR_RANK);
        boolean DEL_TITLE = delProps.contains(DocInfo.PROPERTY.TITLE);
        boolean DEL_AUTHORS_NAMES = delProps.contains(DocInfo.PROPERTY.AUTHORS_NAMES);
        boolean DEL_JOURNAL_NAME = delProps.contains(DocInfo.PROPERTY.JOURNAL_NAME);
        boolean DEL_AUTHORS_IDS = delProps.contains(DocInfo.PROPERTY.AUTHORS_IDS);
        boolean DEL_YEAR = delProps.contains(DocInfo.PROPERTY.YEAR);
        boolean DEL_DOC_SIZE = addProps.contains(DocInfo.PROPERTY.DOCUMENT_SIZE);

        for (Pair<DocInfo, Double> docInfoPair : docInfos) {
            DocInfo docInfo = docInfoPair.getL();

            //delete props
            if (DEL_CITATIONS_PAGERANK) {
                docInfo.clearProperty(DocInfo.PROPERTY.CITATIONS_PAGERANK);
            }
            if (DEL_VSM_WEIGHT) {
                docInfo.clearProperty(DocInfo.PROPERTY.VSM_WEIGHT);
            }
            if (DEL_MAX_TF) {
                docInfo.clearProperty(DocInfo.PROPERTY.MAX_TF);
            }
            if (DEL_TOKEN_COUNT) {
                docInfo.clearProperty(DocInfo.PROPERTY.TOKEN_COUNT);
            }
            if (DEL_AVG_AUTHOR_RANK) {
                docInfo.clearProperty(DocInfo.PROPERTY.AVG_AUTHOR_RANK);
            }
            if (DEL_TITLE) {
                docInfo.clearProperty(DocInfo.PROPERTY.TITLE);
            }
            if (DEL_AUTHORS_NAMES) {
                docInfo.clearProperty(DocInfo.PROPERTY.AUTHORS_NAMES);
            }
            if (DEL_JOURNAL_NAME) {
                docInfo.clearProperty(DocInfo.PROPERTY.JOURNAL_NAME);
            }
            if (DEL_AUTHORS_IDS) {
                docInfo.clearProperty(DocInfo.PROPERTY.AUTHORS_IDS);
            }
            if (DEL_YEAR) {
                docInfo.clearProperty(DocInfo.PROPERTY.YEAR);
            }
            if (DEL_DOC_SIZE) {
                docInfo.clearProperty(DocInfo.PROPERTY.DOCUMENT_SIZE);
            }

            long documentMetaOffset = -1;
            int documentSize = 0;
            long documentOffset = 0;

            //add props -> read the document_meta file
            if (ADD_CITATIONS_PAGERANK || ADD_VSM_WEIGHT || ADD_MAX_TF || ADD_TOKEN_COUNT || ADD_AVG_AUTHOR_RANK || ADD_DOC_SIZE) {
                documentMetaOffset = DocInfo.getMetaOffset(docInfo.getId());
                ByteBuffer buffer = __DOCMETA_BUFFERS__.getBufferLong(documentMetaOffset);
                buffer.get(__DOCUMENT_META_ARRAY__);
                documentSize = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                documentOffset = __DOCUMENT_META_BUFFER__.getLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET);

                if (ADD_CITATIONS_PAGERANK) {
                    double pagerank = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.CITATIONS_PAGERANK_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.CITATIONS_PAGERANK, pagerank);
                }
                if (ADD_VSM_WEIGHT) {
                    double weight = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.VSM_WEIGHT_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.VSM_WEIGHT, weight);
                }
                if (ADD_MAX_TF) {
                    int maxTf = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.MAX_TF_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.MAX_TF, maxTf);
                }
                if (ADD_TOKEN_COUNT) {
                    int length = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.TOKEN_COUNT_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.TOKEN_COUNT, length);
                }
                if (ADD_AVG_AUTHOR_RANK) {
                    double authorRank = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.AVG_AUTHOR_RANK_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.AVG_AUTHOR_RANK, authorRank);
                }
                if (ADD_DOC_SIZE) {
                    docInfo.setProperty(DocInfo.PROPERTY.DOCUMENT_SIZE, documentSize);
                }
            }

            //add props -> read the documents file
            if (ADD_TITLE || ADD_AUTHORS_NAMES || ADD_JOURNAL_NAME || ADD_AUTHORS_IDS || ADD_YEAR) {
                if (documentMetaOffset == -1) {
                    documentMetaOffset = DocInfo.getMetaOffset(docInfo.getId());
                    ByteBuffer buffer = __DOCMETA_BUFFERS__.getBufferLong(documentMetaOffset);
                    buffer.get(__DOCUMENT_META_ARRAY__);
                    documentSize = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                    documentOffset = __DOCUMENT_META_BUFFER__.getLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET);
                }

                //read the whole entry in the documents file
                __DOCUMENT_ARRAY__ = new byte[documentSize];
                __DOCUMENT_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_ARRAY__);
                __DOCUMENTS__.seek(documentOffset);
                __DOCUMENTS__.readFully(__DOCUMENT_ARRAY__, 0, documentSize);

                //grab the props
                if (ADD_YEAR) {
                    short year = __DOCUMENT_BUFFER__.getShort(DocumentEntry.YEAR_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.YEAR, year);
                }
                int titleSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.TITLE_SIZE_OFFSET);
                int authorNamesSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.AUTHOR_NAMES_SIZE_OFFSET);
                int authorIdsSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.AUTHOR_IDS_SIZE_OFFSET);
                short journalNameSize = __DOCUMENT_BUFFER__.getShort(DocumentEntry.JOURNAL_NAME_SIZE_OFFSET);
                int offset;
                if (ADD_TITLE) {
                    String title = new String(__DOCUMENT_ARRAY__, DocumentEntry.TITLE_OFFSET, titleSize, "UTF-8");
                    docInfo.setProperty(DocInfo.PROPERTY.TITLE, title);
                }
                if (ADD_AUTHORS_NAMES) {
                    offset = DocumentEntry.TITLE_OFFSET + titleSize;
                    String authorNames = new String(__DOCUMENT_ARRAY__, offset, authorNamesSize, "UTF-8");
                    docInfo.setProperty(DocInfo.PROPERTY.AUTHORS_NAMES, authorNames);
                }
                if (ADD_AUTHORS_IDS) {
                    offset = DocumentEntry.TITLE_OFFSET + titleSize + authorNamesSize;
                    String authorIds = new String(__DOCUMENT_ARRAY__, offset, authorIdsSize, "ASCII");
                    docInfo.setProperty(DocInfo.PROPERTY.AUTHORS_IDS, authorIds);
                }
                if (ADD_JOURNAL_NAME) {
                    offset = DocumentEntry.TITLE_OFFSET + titleSize + authorNamesSize + authorIdsSize;
                    String journalName = new String(__DOCUMENT_ARRAY__, offset, journalNameSize, "UTF-8");
                    docInfo.setProperty(DocInfo.PROPERTY.JOURNAL_NAME, journalName);
                }
            }
        }
    }

    /**
     * Method that checks if the index has been loaded
     *
     * @return
     */
    public boolean loaded() {
        return  __VOCABULARY__ != null &&
                __POSTINGS__ != null &&
                __DOCUMENTS__ != null &&
                __DOCMETA_BUFFERS__ != null &&
                __DOCID_BUFFERS__ != null &&
                _INDEX_META__ != null;
    }

    /**
     * Returns an array of the document frequencies (df) for each term in the specified list.
     * @param query
     * @return
     */
    public int[] getDf(List<QueryTerm> query) {
        int[] dfs = new int[query.size()];
        VocabularyStruct vocabularyValue;
        for (int i = 0; i < query.size(); i++) {
            vocabularyValue = __VOCABULARY__.get(query.get(i).getTerm());
            if (vocabularyValue != null) {
                dfs[i] = vocabularyValue.get_df();
            }
        }
        return dfs;
    }

    /**
     * Returns an object that represents the postings of a term in the posting file. These are:
     * 1) An array of TF
     * 2) An array of intID of the relevant documents
     *
     * @param term
     * @return
     * @throws IOException
     */
    public Posting getPostings(String term) throws IOException {
        VocabularyStruct vocabularyValue = __VOCABULARY__.get(term);
        if (vocabularyValue == null) {
            return new Posting(new int[0], new int[0]);
        }

        int df = vocabularyValue.get_df();
        __POSTINGS__.seek(vocabularyValue.get_offset());
        byte[] postings = new byte[df * PostingStruct.SIZE];
        __POSTINGS__.readFully(postings);
        ByteBuffer bb = ByteBuffer.wrap(postings);
        int[] intIDs = new int[df];
        int[] tfs = new int[df];
        for (int i = 0; i < df; i++) {
            tfs[i] = bb.getInt();
            intIDs[i] = bb.getInt();
        }
        return new Posting(tfs, intIDs);
    }

    /**
     * Returns an object that holds all essential properties in a document for the Vector space model. These are:
     * 1) Max TF in a document
     * 2) Citations pagerank score
     * 3) Document weight
     *
     * @param intIDs array of the intIDs of documents
     * @return
     */
    public VSMprops getVSMprops(int[] intIDs) {
        int[] maxTfs = new int[intIDs.length];
        double[] VSMweights = new double[intIDs.length];
        double[] citationsPagerank = new double[intIDs.length];
        for (int i = 0; i < intIDs.length; i++) {
            long offset = DocInfo.getMetaOffset(intIDs[i]);
            ByteBuffer buffer = __DOCMETA_BUFFERS__.getBufferLong(offset);
            buffer.get(__DOCUMENT_META_ARRAY__);
            citationsPagerank[i] = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.CITATIONS_PAGERANK_OFFSET);
            VSMweights[i] = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.VSM_WEIGHT_OFFSET);
            maxTfs[i] = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.MAX_TF_OFFSET);
        }
        return new VSMprops(maxTfs, citationsPagerank, VSMweights);
    }

    /**
     * Returns an object that holds all essential properties for the Okapi model. These are:
     * 1) Citations pagerank score
     * 2) Token count
     *
     * @param intIDs array of the intIDs of documents
     * @return
     */
    public OKAPIprops getOKAPIprops(int[] intIDs) {
        int[] tokenCount = new int[intIDs.length];
        double[] citationsPagerank = new double[intIDs.length];
        for (int i = 0; i < intIDs.length; i++) {
            long offset = DocInfo.getMetaOffset(intIDs[i]);
            ByteBuffer buffer = __DOCMETA_BUFFERS__.getBufferLong(offset);
            buffer.get(__DOCUMENT_META_ARRAY__);
            citationsPagerank[i] = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.CITATIONS_PAGERANK_OFFSET);
            tokenCount[i] = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.TOKEN_COUNT_OFFSET);
        }
        return new OKAPIprops(tokenCount, citationsPagerank);
    }

    /**
     * Returns the total number of articles of this index
     * @return
     */
    public int getTotalArticles() {
        if (_INDEX_META__ != null) {
            return Integer.parseInt(_INDEX_META__.get("articles"));
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
        if (_INDEX_META__ != null) {
            return Double.parseDouble(_INDEX_META__.get("avgdl"));
        } else {
            __LOGGER__.error("Meta index info file is not loaded!");
            return 0;
        }
    }

    /**
     * Returns true if stopwords is enabled.
     * Returns null if meta index info file is not loaded.
     * @return
     */
    public Boolean useStopwords() {
        if (_INDEX_META__ != null) {
            return Boolean.parseBoolean(_INDEX_META__.get("use_stopwords"));
        } else {
            __LOGGER__.error("Meta index info file is not loaded!");
            return null;
        }
    }

    /**
     * Returns true if stemming is enabled.
     * Returns null if meta index info file is not loaded.
     * @return
     */
    public Boolean useStemmer() {
        if (_INDEX_META__ != null) {
            return Boolean.parseBoolean(_INDEX_META__.get("use_stemmer"));
        } else {
            __LOGGER__.error("Meta index info file is not loaded!");
            return null;
        }
    }

    /**
     * Returns the timestamp of the index.
     * @return
     */
    public String getIndexTimestamp() {
        if (_INDEX_META__ != null) {
            return _INDEX_META__.get("timestamp");
        } else {
            __LOGGER__.error("Meta index info file is not loaded!");
            return null;
        }
    }

    /**
     * Returns the configuration file this indexer uses
     * @return
     */
    public Config getConfig() {
        return __CONFIG__;
    }

    /**
     * Returns the full path of the vocabulary file of the index
     * @return
     */
    public String getVocabularyPath() {
        return __INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__;
    }

    /**
     * Returns the full path of the postings file of the index
     * @return
     */
    public String getPostingsPath() {
        return __INDEX_PATH__ + "/" + __POSTINGS_FILENAME__;
    }

    /**
     * Returns the full path of the documents file of the index
     * @return
     */
    public String getDocumentsFilePath() {
        return __INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__;
    }

    /**
     * Returns the full path of the documents meta file of the index
     * @return
     */
    public String getDocumentsMetaFilePath() {
        return __INDEX_PATH__ + "/" + __DOCUMENTS_META_FILENAME__;
    }

    /**
     * Returns the full path of the documents ID file of the index
     * @return
     */
    public String getDocumentsIDFilePath() {
        return __INDEX_PATH__ + "/" + __DOCUMENTS_ID_FILENAME__;
    }

    /**
     * Returns the full path of the meta file of the index
     * @return
     */
    public String getMetaPath() {
        return __INDEX_PATH__ + "/" + __META_FILENAME__;
    }

    /**
     * Returns the full path of the term_df tmp file that is created during the indexing process
     * @return
     */
    private String getTermDfPath() {
        return getIndexTmpPath() + "term_df";
    }

    /**
     * Returns the full path of the doc_df tmp file that is created during the indexing process
     * @return
     */
    private String getDocTfPath() {
        return getIndexTmpPath() + "doc_tf";
    }

    /**
     * Returns the full path of the partial index folder specified by the given index
     * @param index
     * @return
     */
    private String getPartialIndexPath(int index) {
        return getIndexTmpPath() + index + "/";
    }

    /**
     * Returns the full path of the postings file found inside the partial index folder specified
     * by the given index
     *
     * @param index
     * @return
     */
    private String getPartialPostingPath(int index) {
        return getPartialIndexPath(index) + __POSTINGS_FILENAME__;
    }

    /**
     * Returns the full path of the vocabulary file found inside the partial index folder specified
     * by the given index
     *
     * @param index
     * @return
     */
    private String getPartialVocabularyPath(int index) {
        return getPartialIndexPath(index) + __VOCABULARY_FILENAME__;
    }

    /**
     * Returns the full path of the index
     * @return
     */
    public String getIndexPath() {
        return __CONFIG__.getIndexPath() + "/";
    }

    /**
     * Returns the full path of the data set files of the collection
     * @return
     */
    public String getDataSetPath() {
        return __CONFIG__.getDatasetPath() + "/";
    }

    /**
     * Returns the full path of the partial index folder that is created during the indexing process
     * @return
     */
    private String getIndexTmpPath() {
        return __CONFIG__.getIndexTmpPath() + "/";
    }
}
