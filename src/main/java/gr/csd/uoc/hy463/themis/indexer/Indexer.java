package gr.csd.uoc.hy463.themis.indexer;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.config.Exceptions.ConfigLoadException;
import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocumentBlockBuffers;
import gr.csd.uoc.hy463.themis.indexer.MemMap.MemoryBuffers;
import gr.csd.uoc.hy463.themis.indexer.indexes.Index;
import gr.csd.uoc.hy463.themis.indexer.model.*;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntryTokens;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
import gr.csd.uoc.hy463.themis.linkAnalysis.Exceptions.PagerankException;
import gr.csd.uoc.hy463.themis.linkAnalysis.Pagerank;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.OKAPIprops;
import gr.csd.uoc.hy463.themis.retrieval.model.Postings;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;
import gr.csd.uoc.hy463.themis.retrieval.model.VSMprops;
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
 * b) Given a path load the indexes and provide information about the
 * indexed data. Can be used for implementing any kind of retrieval models.
 */
public class Indexer {
    private static final Logger __LOGGER__ = LogManager.getLogger(Indexer.class);
    private double _documentPagerankWeight;
    private double[] _documentsPagerank;
    private final Config __CONFIG__;

    /* The full path of the final index folder */
    private final String __INDEX_PATH__;

    /* The name of the files in the final index folder */
    private final String __VOCABULARY_FILENAME__;
    private final String __POSTINGS_FILENAME__;
    private final String __DOCUMENTS_FILENAME__ ;
    private final String __DOCUMENTS_META_FILENAME__;
    private final String __DOCUMENTS_ID_FILENAME__;
    private final String __INDEX_META_FILENAME__;

    /* Metadata/options related to the final index */
    private Map<String, String> _INDEX_META__ = null;

    /* Use this struct to load VOCABULARY_FILENAME into memory */
    private HashMap<String, VocabularyEntry> __VOCABULARY__ = null;

    /* The final POSTINGS_FILENAME and DOCUMENTS_FILENAME */
    private RandomAccessFile __POSTINGS__ = null;
    private RandomAccessFile __DOCUMENTS__ = null;

    /* Use DOCUMENTS_META_FILENAME as a memory mapped file */
    private DocumentBlockBuffers __DOCMETA_BUFFERS__ = null;
    private byte[] __DOCUMENT_META_ARRAY__;
    private ByteBuffer __DOCUMENT_META_BUFFER__;

    /* Use DOCUMENTS_ID_FILENAME as a memory mapped file */
    private DocumentBlockBuffers __DOCID_BUFFERS__ = null;
    private byte[] __DOCUMENT_ID_ARRAY__;
    private ByteBuffer __DOCUMENT_ID_BUFFER__; /* currently unused */

    /**
     * Constructor.
     *
     * Reads configuration options from themis.config file and initializes the filenames in the final index.
     *
     * @throws ConfigLoadException
     */
    public Indexer()
            throws ConfigLoadException {
        try {
            this.__CONFIG__ = new Config();
        }
        catch (IOException e) {
            throw new ConfigLoadException();
        }
        __INDEX_PATH__ = getIndexPath();
        __VOCABULARY_FILENAME__ = __CONFIG__.getVocabularyFileName();
        __POSTINGS_FILENAME__ = __CONFIG__.getPostingsFileName();
        __DOCUMENTS_FILENAME__ = __CONFIG__.getDocumentsFileName();
        __DOCUMENTS_META_FILENAME__ = __CONFIG__.getDocumentsMetaFileName();
        __DOCUMENTS_ID_FILENAME__ = __CONFIG__.getDocumentsIDFileName();
        __INDEX_META_FILENAME__ = __CONFIG__.getIndexMetaFileName();
    }

    /**
     * Indexes the collection found in DATASET_PATH.
     *
     * @throws IOException
     * @throws PagerankException
     */
    public void index()
            throws IOException, PagerankException {
        index(getDataSetPath());
    }

    /**
     * Indexes the collection found in the given path and writes the final index to INDEX_PATH. Will immediately
     * abort if INDEX_PATH is not empty.
     * INDEX_TMP_PATH will be used to store any temp files including the partial indexes.
     * If PARTIAL_INDEX_MAX_DOCS_SIZE is less than the number of documents in the collection then more
     * than 1 partial indexes will be created. Each partial index will contain data from at most
     * PARTIAL_INDEX_MAX_DOCS_SIZE documents. Finally, all partial indexes are merged and the final index
     * is created.
     *
     * @param path
     * @throws IOException
     * @throws PagerankException
     */
    public void index(String path)
            throws IOException, PagerankException {
        if (!areIndexDirEmpty()) {
            __LOGGER__.error("Previous index found. Aborting...");
            Themis.print("Previous index found. Aborting...\n");
            return;
        }

        __DOCUMENT_META_ARRAY__ = new byte[DocumentMetaEntry.SIZE];
        __DOCUMENT_META_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_META_ARRAY__);

        /* the ID of each document. The N-th parsed document will have ID = N (starting from 0) */
        int docID = 0;

        /* the total number of tokens in the collection (required by the Okapi retrieval model) */
        long tokenCount = 0;

        /* offset to DOCUMENTS_FILENAME */
        long documentsOffset = 0;

        /* maximum number of documents in a partial index */
        int maxDocsPerIndex = __CONFIG__.getPartialIndexSize();

        /* create the list of files in the collection */
        File folder = new File(path);
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            __LOGGER__.info("No dataset files found in " + path);
            Themis.print("No dataset files found in " + path + "\n");
            return;
        }

        /* initialize metadata/options related to the final index */
        _INDEX_META__ = new HashMap<>();
        Themis.print("-> Indexing options:\n");
        _INDEX_META__.put("use_stemmer", String.valueOf(__CONFIG__.getUseStemmer()));
        Themis.print("Stemmer: " + __CONFIG__.getUseStemmer() + "\n");
        _INDEX_META__.put("use_stopwords", String.valueOf(__CONFIG__.getUseStopwords()));
        Themis.print("Stopwords: " + __CONFIG__.getUseStopwords() + "\n");
        _INDEX_META__.put("pagerank_damping", String.valueOf(__CONFIG__.getPagerankDampingFactor()));
        Themis.print("Pagerank damping factor: " + __CONFIG__.getPagerankDampingFactor() + "\n");
        _INDEX_META__.put("pagerank_threshold", String.valueOf(__CONFIG__.getPagerankThreshold()));
        Themis.print("Pagerank threshold: " + __CONFIG__.getPagerankThreshold() + "\n");

        Themis.print("-> Start indexing\n");
        long startTime = System.nanoTime();

        /* sort the files in the collection => determines the parsing order and thus the ID of each document */
        List<File> corpus = new ArrayList<>(files.length);
        corpus.addAll(Arrays.asList(files));
        Collections.sort(corpus);

        /* instantiate the object that calculates the frequencies of the tokens in a document */
        S2TextualEntryTokens textualEntryTokens = new S2TextualEntryTokens(__CONFIG__.getUseStemmer(), __CONFIG__.getUseStopwords());

        /* create the final index folder INDEX_PATH */
        Files.createDirectories(Paths.get(__INDEX_PATH__));

        /* create INDEX_TMP_PATH where all partial indexes and temporary files will be stored */
        Files.createDirectories(Paths.get(getIndexTmpPath()));

        /* open INDEX_META_FILENAME (normal sequential file) */
        BufferedWriter metaWriter = new BufferedWriter(new FileWriter(getMetaPath()));

        /* open DOCUMENTS_FILENAME, DOCUMENTS_ID_FILENAME, DOCUMENTS_META_FILENAME (random access files) */
        RandomAccessFile documents = new RandomAccessFile(getDocumentsFilePath(), "rw");
        BufferedOutputStream documents_out = new BufferedOutputStream(new FileOutputStream(documents.getFD()));
        RandomAccessFile documentsMeta = new RandomAccessFile(getDocumentsMetaFilePath(), "rw");
        BufferedOutputStream documentsMeta_out = new BufferedOutputStream(new FileOutputStream(documentsMeta.getFD()));
        RandomAccessFile documentsID = new RandomAccessFile(getDocumentsIDFilePath(), "rw");
        BufferedOutputStream documentsID_out = new BufferedOutputStream(new FileOutputStream(documentsID.getFD()));

        /* A 'doc_tf' file will be stored in INDEX_TMP_PATH (normal sequential file).
        Contains a sequence of <term1, TF1, term2, TF2, ...> (one line per document).
        Will be used during the calculation of VSM weights */
        BufferedWriter docTFWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getDocTFPath()), "UTF-8"));

        /* initialize a partial index */
        int indexID = 0;
        Index index = new Index(this, indexID);

        /* parse the collection */
        for (File file : corpus) {
            if (file.isFile()) {
                Themis.print("Parsing file: " + file + "\n");
                BufferedReader currFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String json;

                /* for each document */
                while ((json = currFile.readLine()) != null) {

                    /* Extract all textual info into a S2TextualEntry */
                    S2TextualEntry entry = S2JsonEntryReader.readTextualEntry(json);

                    /* create the frequencies map for the terms of the S2TextualEntry */
                    Map<String, Integer> TFMap = textualEntryTokens.createTFMap(entry);

                    /* update the partial index and 'INDEX_TMP_PATH/doc_tf' */
                    int documentTokens = index.add(TFMap, docTFWriter, docID);

                    /* update the total number of tokens */
                    tokenCount += documentTokens;

                    /* update DOCUMENTS_FILENAME */
                    long prevDocumentsOffset = documentsOffset;
                    documentsOffset = dumpDocuments(documents_out, entry, documentsOffset);

                    /* size of the entry in DOCUMENTS_FILENAME */
                    int documentSize = (int) (documentsOffset - prevDocumentsOffset);

                    /* update DOCUMENTS_META_FILENAME */
                    dumpDocumentsMeta(documentsMeta_out, docID, documentTokens, documentSize, prevDocumentsOffset);

                    /* update DOCUMENTS_ID_FILENAME */
                    dumpDocumentsID(documentsID_out, entry);

                    /* check if a dump of the current partial index is needed */
                    docID++;
                    if (docID % maxDocsPerIndex == 0) {
                        index.dump();
                        indexID++;
                        index = new Index(this, indexID);
                    }
                }
                currFile.close();
            }
        }

        /* if we just created a new index but there are no documents left then remove it */
        if (docID != 0 && docID % maxDocsPerIndex == 0) {
            indexID--;
        }
        else {
            index.dump(); //dump last index
        }

        documents_out.close();
        documentsMeta_out.close();
        documentsID_out.close();
        docTFWriter.close();

        /* calculate avgdl for the Okapi retrieval model */
        double avgdl = (0.0 + tokenCount) / docID;

        /* save the remaining metadata/options related to the final index and dump them to INDEX_META_FILENAME */
        _INDEX_META__.put("documents", String.valueOf(docID));
        _INDEX_META__.put("avgdl", String.valueOf(avgdl));
        _INDEX_META__.put("timestamp", Instant.now().toString());
        for (Map.Entry<String, String> pair : _INDEX_META__.entrySet()) {
            metaWriter.write(pair.getKey() + "=" + pair.getValue() + "\n");
        }
        metaWriter.close();

        Themis.print("Partial indexes created in " + new Time(System.nanoTime() - startTime) + "\n");

        /* merge the partial VOCABULARY_FILENAME and delete them */
        mergeVocabularies(indexID);
        try {
            for (int i = 0; i <= indexID; i++) {
                deleteDir(new File(getPartialVocabularyPath(i)));
            }
        } catch (IOException e) {
            __LOGGER__.error("Error deleting partial vocabularies");
            Themis.print("[Error deleting partial vocabularies]\n");
        }

        /* calculate VSM weights and update DOCUMENTS_META_FILENAME. Also, delete 'INDEX_TMP_PATH/doc_tf' */
        updateVSMWeights();
        deleteDir(new File(getDocTFPath()));

        /* merge the partial POSTINGS_FILENAME and delete them */
        mergePostings(indexID);
        try {
            for (int i = 0; i <= indexID; i++) {
                deleteDir(new File(getPartialPostingsPath(i)));
            }
        } catch (IOException e) {
            __LOGGER__.error("Error deleting partial postings");
            Themis.print("[Error deleting partial postings]\n");
        }

        /* delete 'INDEX_TMP_PATH/term_df' (has been created during the merge of the vocabulary files) */
        deleteDir(new File(getTermDFPath()));

        /* compute the document pagerank scores and update DOCUMENTS_META_FILENAME */
        Pagerank pagerank = new Pagerank(this);
        pagerank.documentsPagerank();

        /* delete INDEX_TMP_PATH */
        try {
            deleteDir(new File(getIndexTmpPath()));
        } catch (IOException e) {
            __LOGGER__.error("Error deleting index tmp folder");
            Themis.print("[Error deleting index tmp folder]\n");
        }

        Themis.print("-> End of indexing\n");
    }

    /* Merges the partial VOCABULARY_FILENAME found in 'INDEX_TMP_PATH/$/'  where '$' is a
    number from 0 to maxIndexID (inclusive). Creates the final VOCABULARY_FILENAME (normal sequential file)
    in INDEX_PATH */
    private void mergeVocabularies(int maxIndexID)
            throws IOException {
        long startTime =  System.nanoTime();
        Themis.print("-> Merging partial vocabularies...\n");

        /* when there's only 1 partial index */
        if (maxIndexID == 0) {
            finalizePartialVocabulary();
        }
        else {
            mergePartialVocabularies(maxIndexID);
        }
        Themis.print("Partial vocabularies merged in " + new Time(System.nanoTime() - startTime) + "\n");
    }

    /* Merges the partial VOCABULARY_FILENAME when there's only 1 partial index.

    To do this, we cannot simply copy VOCABULARY_FILENAME to its final location
    because it is missing the offsets to POSTINGS_FILENAME. However, the offsets can be determined
    using the following information:
    1) For each term we know its DF
    2) Each entry in POSTINGS_FILENAME has the same size
    So:
    (1st term offset)  = 0
    (2nd term offset) = (1st term offset) + (1st term DF)x(POSTING.SIZE)
    (3rd term offset) = (2nd term offset) + (2nd term DF)x(POSTING.SIZE)
    etc.
     */
    private void finalizePartialVocabulary()
            throws IOException {
        String partialVocabularyPath = getPartialVocabularyPath(0);
        BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getVocabularyPath()), "UTF-8"));
        BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(partialVocabularyPath), "UTF-8"));
        String line;
        String[] split;
        long offset = 0;
        while ((line = vocabularyReader.readLine()) != null) {
            split = line.split(" ");
            int df = Integer.parseInt(split[1]);
            vocabularyWriter.write(split[0] + ' ' + split[1] + ' ' + offset + '\n');
            offset +=  df * (long) Posting.SIZE;
        }
        vocabularyReader.close();
        vocabularyWriter.close();
    }

    /* Merges the partial VOCABULARY_FILENAME when there's more than 1 partial index.

    Also creates 'INDEX_TMP_PATH/term_df'. Will be used when merging the partial POSTINGS_FILENAME.

    Merge process:
    Assume we have K files.
    1) Open all files and create PartialVocabularyEntry objects to store the first line from each file.
    Since the files are sorted, we know that the min lexicographical term will correspond to
    one (or more) of those objects.
    2) Instead of performing a linear search to find the min term among K objects, we add them
    to a min priority queue sorted by term. Adding/removing subsequent objects costs log(K).
    3) We can use the queue to get the min lexicographical term but the same term might appear in many files.
    Therefore, we keep removing from the queue until we get a different term. Each time an object is removed,
    we add to the queue a new PartialVocabulary object from the partial index with the same index.
    4) All objects that have been removed from the queue in the previous step are added to a list which is sorted
    by the ID of the index (increasing). We then write all <index ID, DF> pairs to 'INDEX_TMP_PATH/term_df'
    (one line per term).
    5) Since we have all objects that correspond to the min lexicographical term, we can sum their DF and find the
    final DF of the term.
    6) See the doc in finalizePartialVocabulary() for an explanation of how to determine the offset to
    POSTINGS_FILENAME.
    7) Finally, we write to the final VOCABULARY_FILENAME a <term, DF, offset> line for the min
    lexicographical term, and repeat the procedure from 2) until all partial VOCABULARY_FILENAME have been parsed.
    */
    private void mergePartialVocabularies(int maxIndexID)
            throws IOException {
        /* open all partial VOCABULARY_FILENAME */
        BufferedReader[] vocabularyReader = new BufferedReader[maxIndexID + 1];
        for (int i = 0; i <= maxIndexID; i++) {
            String partialVocabularyPath = getPartialVocabularyPath(i);
            vocabularyReader[i] = new BufferedReader(new InputStreamReader(new FileInputStream(partialVocabularyPath), "UTF-8"));
        }

        /* open final VOCABULARY_FILENAME */
        BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getVocabularyPath()), "UTF-8"));

        /* open 'INDEX_TMP_PATH/term_df' */
        BufferedWriter termDFWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getTermDFPath()), "ASCII"));

        /* starting offset to the final POSTINGS_FILENAME */
        long postingsOffset = 0;

        /* object that corresponds to a min lexicographical term */
        PartialVocabularyEntry minTermObj;

        /* Read the first line from each partial VOCABULARY_FILENAME, create PartialVocabularyEntry objects,
        and put them in a priority queue */
        PriorityQueue<PartialVocabularyEntry> vocabularyQueue = new PriorityQueue<>();
        for (int i = 0; i <= maxIndexID; i++) {
            minTermObj = getNextVocabularyEntry(vocabularyReader[i], i);
            if (minTermObj != null) {
                vocabularyQueue.add(minTermObj);
            }
        }

        /* list of all objects that correspond to the same min lexicographical term */
        List<PartialVocabularyEntry> equalTerms = new ArrayList<>();

        /* the current min lexicographical term */
        String currMinTerm = null;

        /* poll the queue */
        while((minTermObj = vocabularyQueue.poll()) != null) {

            /* if the term of the polled object is not equal to the min term, we must process the list
            and update the final VOCABULARY_FILENAME. Also update 'INDEX_TMP_PATH/term_df' */
            if (!minTermObj.getTerm().equals(currMinTerm) && !equalTerms.isEmpty()) {
                postingsOffset = dumpEqualTerms(equalTerms, vocabularyWriter, termDFWriter, postingsOffset);
            }

            /* set the min term to the one from the last polled object */
            currMinTerm = minTermObj.getTerm();

            /* put the last polled object in the list */
            equalTerms.add(minTermObj);

            /* read the next line of the partial VOCABULARY_FILENAME that contained the last polled
            object and add a new object to the queue */
            int currIndexID = minTermObj.getIndexID();
            PartialVocabularyEntry nextObj = getNextVocabularyEntry(vocabularyReader[currIndexID], currIndexID);
            if (nextObj != null) {
                vocabularyQueue.add(nextObj);
            }
        }

        /* all partial VOCABULARY_FILENAME have been parsed. Process the list and write the remaining terms
        to the final VOCABULARY_FILENAME */
        if (!equalTerms.isEmpty()) {
            dumpEqualTerms(equalTerms, vocabularyWriter, termDFWriter, postingsOffset);
        }

        /* close any open files */
        for (int i = 0; i <= maxIndexID; i++) {
            vocabularyReader[i].close();
        }
        vocabularyWriter.close();
        termDFWriter.close();
    }

    /* Reads the next line from the partial VOCABULARY_FILENAME that is part of the partial index with
     the given ID and returns a new PartialVocabularyEntry object */
    private PartialVocabularyEntry getNextVocabularyEntry(BufferedReader vocabularyReader, int indexID)
            throws IOException {
        String line = vocabularyReader.readLine();
        if (line != null) {
            String[] fields = line.split(" ");
            return new PartialVocabularyEntry(fields[0], Integer.parseInt(fields[1]), indexID);
        }
        return null;
    }

    /* Processes the given list and writes a new line to the final VOCABULARY_FILENAME. Also, updates
    'INDEX_TMP_PATH/term_df' with a new line.

    Returns the new offset to POSTINGS_FILENAME that will be used during the next call of the method */
    private long dumpEqualTerms(List<PartialVocabularyEntry> equalTerms, BufferedWriter vocabularyWriter, BufferedWriter termDfWriter, long postingsOffset)
            throws IOException {
        int DF = 0;

        /* sort the list based on the index ID (increasing) */
        equalTerms.sort(PartialVocabularyEntry.IDComparator);

        /* calculate final DF, also write to 'INDEX_TMP_PATH/term_df' a sequence of <index ID, DF>
        for the current term */
        StringBuilder sb = new StringBuilder();
        for (PartialVocabularyEntry equalTerm : equalTerms) {
            DF += equalTerm.getDF();
            sb.append(equalTerm.getIndexID()).append(' ').append(equalTerm.getDF()).append(' ');
        }
        sb.append('\n');
        termDfWriter.write(sb.toString());

        /* write a new line to the final VOCABULARY_FILENAME */
        vocabularyWriter.write(equalTerms.get(0).getTerm() + ' ' + DF + ' ' + postingsOffset + '\n');

        /* calculate the new offset to POSTINGS_FILENAME */
        postingsOffset +=  DF * (long) Posting.SIZE;

        equalTerms.clear();
        return postingsOffset;
    }

    /* Merges the partial POSTINGS_FILENAME found in 'INDEX_TMP_PATH/$/' where '$' is a
    number from 0 to maxIndexID (inclusive). Creates the final POSTINGS_FILENAME (random access file)
    in INDEX_PATH */
    private void mergePostings(int maxIndexID)
            throws IOException {

        /* If there is only one partial index, just move POSTINGS_FILENAME to INDEX_PATH */
        long startTime =  System.nanoTime();
        Themis.print("-> Merging partial postings...\n");
        if (maxIndexID == 0) {
            String partialPostingPath = getPartialPostingsPath(0);
            Files.move(Paths.get(partialPostingPath), Paths.get(getPostingsPath()), StandardCopyOption.REPLACE_EXISTING);
        }
        else {
            mergePartialPostings(maxIndexID);
        }
        Themis.print("Partial postings merged in " + new Time(System.nanoTime() - startTime) + "\n");
    }

    /* Merges all partial POSTINGS_FILENAME when there's more than 1 partial index.
    *
    * Merge process:
    * We'll rely on 'INDEX_TMP_PATH/term_df' that has been created during the merging of the partial
    * VOCABULARY_FILENAME. This is the known information so far:
    *
    * 1) Each line in 'INDEX_TMP_PATH/term_df' consists of a sequence of <index ID, DF> with the ID
    * appearing in increasing order (done in function Indexer.dumpEqualTerms())
    * 2) Line N in 'INDEX_TMP_PATH/term_df' corresponds to line N in VOCABULARY_FILENAME file
    * (done in function Indexer.dumpEqualTerms())
    * 3) The blocks of postings in each partial POSTINGS_FILENAME are already sorted based on the
    * sorting of terms in the corresponding partial VOCABULARY_FILENAME (done in function Index.dumpPostings())
    * 4) The postings in each postings block are already sorted based on the ID of the relevant documents
    * (done in function Indexer.index())
    *
    * So for each term in the VOCABULARY_FILENAME file we can easily find:
    * 1) All partial indexes that contain its postings
    * 2) The size of its postings => (Sum of DF)x(POSTING.SIZE)
    * 3) The offset of the postings block for the next term
    */
    private void mergePartialPostings(int maxIndexID)
            throws IOException {
        /* open all partial POSTINGS_FILENAME */
        BufferedInputStream[] postingsStream = new BufferedInputStream[maxIndexID + 1];
        for (int i = 0; i <= maxIndexID; i++) {
            String partialPostingPath = getPartialPostingsPath(i);
            postingsStream[i] = new BufferedInputStream(new FileInputStream(new RandomAccessFile(partialPostingPath, "rw").getFD()));
        }

        /* open final POSTINGS_FILENAME */
        BufferedOutputStream postingsWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(getPostingsPath(), "rw").getFD()));

        /* open 'INDEX_TMP_PATH/term_df' */
        BufferedReader termDFReader = new BufferedReader(new InputStreamReader(new FileInputStream(getTermDFPath()), "ASCII"));

        /* parse each line of 'INDEX_TMP_PATH/term_df', grab the postings from the appropriate
        partial POSTINGS_FILENAME, and write them to the final POSTINGS_FILENAME */
        String line;
        while ((line = termDFReader.readLine()) != null) {
            List<String> split = ProcessText.splitSpace(line);
            for (int i = 0; i < split.size(); i+=2) {
                int DF = Integer.parseInt(split.get(i + 1));
                int indexID = Integer.parseInt(split.get(i));
                byte[] postings = new byte[DF * Posting.SIZE];
                postingsStream[indexID].read(postings);
                postingsWriter.write(postings);
            }
        }

        /* close any open files */
        for (BufferedInputStream bufferedInputStream : postingsStream) {
            bufferedInputStream.close();
        }
        postingsWriter.close();
        termDFReader.close();
    }

    /* Writes an entry to DOCUMENTS_META_FILENAME (random access file).
     * See class DocumentMetaEntry.
     *
     * Note: {PageRank, VSM weight, Max TF, Avg author rank} are all initialized to 0
     * */
    private void dumpDocumentsMeta(BufferedOutputStream out, int docID, int documentTokens, int documentSize, long documentsOffset)
            throws IOException {
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.DOCID_OFFSET, docID);
        __DOCUMENT_META_BUFFER__.putDouble(DocumentMetaEntry.VSM_WEIGHT_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.MAX_TF_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.TOKEN_COUNT_OFFSET, documentTokens);
        __DOCUMENT_META_BUFFER__.putDouble(DocumentMetaEntry.DOCUMENT_PAGERANK_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putDouble(DocumentMetaEntry.AVG_AUTHOR_RANK_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET, documentSize);
        __DOCUMENT_META_BUFFER__.putLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET, documentsOffset);
        __DOCUMENT_META_BUFFER__.position(0);
        out.write(__DOCUMENT_META_ARRAY__);
    }

    /* Writes the string ID of a S2TextualEntry to DOCUMENTS_ID_FILENAME (random access file) */
    private void dumpDocumentsID(BufferedOutputStream out, S2TextualEntry textualEntry)
            throws IOException {
        out.write(textualEntry.getID().getBytes("ASCII"));
    }

    /* Writes an entry to DOCUMENTS_FILENAME (random access file).
     * See class DocumentEntry.
     *
     * Author names are separated by commas.
     * Author IDs are separated by commas.
     *
     * Returns the new offset to DOCUMENTS_FILENAME.
     * */
    private long dumpDocuments(BufferedOutputStream out, S2TextualEntry textualEntry, long documentsOffset)
            throws IOException {
        int entrySize = 0;

        /* title */
        byte[] title = textualEntry.getTitle().getBytes("UTF-8");
        byte[] titleSize = ByteBuffer.allocate(DocumentEntry.TITLE_SIZE_SIZE).putInt(title.length).array();
        entrySize += title.length + DocumentEntry.TITLE_SIZE_SIZE;

        /* authors, authors IDs */
        List<Pair<String, List<String>>> authors;
        StringBuilder sb_authorNames;
        StringBuilder sb_authorIDs;

        authors = textualEntry.getAuthors();
        sb_authorNames = new StringBuilder();
        sb_authorIDs = new StringBuilder();
        for (int i = 0; i < authors.size(); i++) {
            sb_authorNames.append(authors.get(i).getL());
            sb_authorIDs.append(authors.get(i).getR());
            if (i != authors.size() - 1) {
                sb_authorNames.append(',');
                sb_authorIDs.append(',');
            }
        }

        byte[] authorNames = sb_authorNames.toString().getBytes("UTF-8");
        byte[] authorNamesSize = ByteBuffer.allocate(DocumentEntry.AUTHOR_NAMES_SIZE_SIZE).putInt(authorNames.length).array();
        entrySize += authorNames.length + DocumentEntry.AUTHOR_NAMES_SIZE_SIZE;

        byte[] authorIDs = sb_authorIDs.toString().getBytes("ASCII");
        byte[] authorIDsSize = ByteBuffer.allocate(DocumentEntry.AUTHOR_IDS_SIZE_SIZE).putInt(authorIDs.length).array();
        entrySize += authorIDs.length + DocumentEntry.AUTHOR_IDS_SIZE_SIZE;

        /* year */
        byte[] year = ByteBuffer.allocate(DocumentEntry.YEAR_SIZE).putShort((short) textualEntry.getYear()).array();
        entrySize += DocumentEntry.YEAR_SIZE;

        /* journal name */
        byte[] journalName = textualEntry.getJournalName().getBytes("UTF-8");
        byte[] journalNameSize = ByteBuffer.allocate(DocumentEntry.JOURNAL_NAME_SIZE_SIZE).putShort((short) journalName.length).array();
        entrySize += journalName.length + DocumentEntry.JOURNAL_NAME_SIZE_SIZE;

        /* write first the fixed size fields */
        out.write(year);
        out.write(titleSize);
        out.write(authorNamesSize);
        out.write(authorIDsSize);
        out.write(journalNameSize);

        /* write the variable size fields */
        out.write(title);
        out.write(authorNames);
        out.write(authorIDs);
        out.write(journalName);

        return entrySize + documentsOffset;
    }

    /* Deletes the given folder */
    private boolean deleteDir(File path)
            throws IOException {
        File[] contents = path.listFiles();
        if (contents != null) {
            for (File file : contents) {
                if (!deleteDir(file)) {
                    return false;
                }
            }
        }
        return Files.deleteIfExists(path.toPath());
    }

    /* Calculates the document weight (used by the Vector space model) and the max TF in each document
    and writes them to DOCUMENTS_META_FILENAME.

    To calculate the weight of a document we need:
    1) The DF of each term : Easily obtained from the final VOCABULARY_FILENAME.
    2) The TF of each term: 'INDEX_TMP_PATH/doc_tf' already contains a sequence of <term, TF>.
    */
    private void updateVSMWeights()
            throws IOException {
        long startTime = System.nanoTime();
        Themis.print("-> Calculating VSM weights...\n");

        /* load VOCABULARY_FILENAME */
        BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(getVocabularyPath()), "UTF-8"));
        Map<String, Integer> vocabulary = new HashMap<>();
        String line;
        String[] split;
        while ((line = vocabularyReader.readLine()) != null) {
            split = line.split(" ");
            vocabulary.put(split[0], Integer.parseInt(split[1]));
        }
        vocabularyReader.close();

        /* open DOCUMENTS_META_FILENAME and 'INDEX_TMP_PATH/doc_tf' */
        __DOCMETA_BUFFERS__ = new DocumentBlockBuffers(getDocumentsMetaFilePath(), MemoryBuffers.MODE.WRITE, DocumentMetaEntry.SIZE);
        BufferedReader docTFReader = new BufferedReader(new InputStreamReader(new FileInputStream(getDocTFPath()), "UTF-8"));

        int totalDocuments = Integer.parseInt(_INDEX_META__.get("documents"));
        double logDocuments = Math.log(totalDocuments);
        long offset = 0;

        /* read a line from the 'INDEX_TMP_PATH/doc_tf' and calculate the weight */
        while ((line = docTFReader.readLine()) != null) {
            List<String> splitList = ProcessText.splitSpace(line);
            double weight = 0;
            int maxTF = 0;
            for (int i = 0; i < splitList.size(); i += 2) {
                int DF = vocabulary.get(splitList.get(i));
                int TF = Integer.parseInt(splitList.get(i + 1));
                if (TF > maxTF) {
                    maxTF = TF;
                }
                double x = TF * (logDocuments - Math.log(DF));
                weight += x * x;
            }
            weight = Math.sqrt(weight) / maxTF;

            /* update DOCUMENTS_META_FILENAME */
            ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(offset + DocumentMetaEntry.VSM_WEIGHT_OFFSET);
            buffer.putDouble(weight);
            buffer = __DOCMETA_BUFFERS__.getMemBuffer(offset + DocumentMetaEntry.MAX_TF_OFFSET);
            buffer.putInt(maxTF);
            offset += DocumentMetaEntry.SIZE;
        }

        /* close files */
        docTFReader.close();
        __DOCMETA_BUFFERS__.close();
        __DOCMETA_BUFFERS__ = null;

        Themis.print("VSM weights calculated in " + new Time(System.nanoTime() - startTime) + "\n");
    }

    /**
     * Loads the index from INDEX_PATH. The following actions take place:
     * 1) VOCABULARY_FILENAME and INDEX_META_FILENAME are loaded in memory.
     * 2) POSTINGS_FILENAME and DOCUMENTS_FILENAME are opened.
     * 3) DOCUMENTS_ID_FILENAME and DOCUMENTS_META_FILENAME are memory mapped.
     * 4) The Pagerank scores of the documents are loaded in memory.
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public boolean load()
            throws IndexNotLoadedException {
        Themis.print("-> Index path: " + __INDEX_PATH__ + "\n");
        try {
            /* load index configuration options from INDEX_META_FILENAME */
            _INDEX_META__ = loadIndexMeta();
            Themis.print("Stemming: " + _INDEX_META__.get("use_stemmer") + "\n");
            Themis.print("Stopwords: " + _INDEX_META__.get("use_stopwords") + "\n");
            Themis.print("-> Loading index...");

            /* load VOCABULARY_FILENAME */
            __VOCABULARY__ = new HashMap<>();
            String line;
            String[] fields;
            BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(getVocabularyPath()), "UTF-8"));
            while ((line = vocabularyReader.readLine()) != null) {
                fields = line.split(" ");
                __VOCABULARY__.put(fields[0], new VocabularyEntry(Integer.parseInt(fields[1]), Long.parseLong(fields[2])));
            }
            vocabularyReader.close();

            /* open POSTINGS_FILENAME and DOCUMENTS_FILENAME */
            __POSTINGS__ = new RandomAccessFile(getPostingsPath(), "r");
            __DOCUMENTS__ = new RandomAccessFile(getDocumentsFilePath(), "r");

            /* memory map DOCUMENTS_META_FILENAME and DOCUMENTS_ID_FILENAME */
            __DOCMETA_BUFFERS__ = new DocumentBlockBuffers(getDocumentsMetaFilePath(), MemoryBuffers.MODE.READ, DocumentMetaEntry.SIZE);
            __DOCUMENT_META_ARRAY__ = new byte[DocumentMetaEntry.SIZE];
            __DOCUMENT_META_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_META_ARRAY__);
            __DOCID_BUFFERS__ = new DocumentBlockBuffers(getDocumentsIDFilePath(), MemoryBuffers.MODE.READ, DocumentStringID.SIZE);
            __DOCUMENT_ID_ARRAY__ = new byte[DocumentStringID.SIZE];
            __DOCUMENT_ID_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_ID_ARRAY__);

            /* load Pagerank scores of the documents */
            _documentPagerankWeight = __CONFIG__.getDocumentPagerankWeight();
            int documents = Integer.parseInt(_INDEX_META__.get("documents"));
            _documentsPagerank = new double[documents];
            for (int i = 0; i < documents; ++i) {
                long offset = DocInfo.getMetaOffset(i) + DocumentMetaEntry.DOCUMENT_PAGERANK_OFFSET;
                ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(offset);
                _documentsPagerank[i] = buffer.getDouble();
            }
        }
        catch (IOException e) {
            throw new IndexNotLoadedException();
        }
        Themis.print("Done\n");

        return true;
    }

    /**
     * Loads index metadata/options from INDEX_META_FILENAME.
     *
     * @return
     * @throws IOException
     */
    public Map<String, String> loadIndexMeta()
            throws IOException {
        Map<String, String> meta;
        BufferedReader indexMetaReader = new BufferedReader(new FileReader(getMetaPath()));
        meta = new HashMap<>();
        String[] split;
        String line;
        while((line = indexMetaReader.readLine()) != null) {
            split = line.split("=");
            meta.put(split[0], split[1]);
        }
        indexMetaReader.close();

        return meta;
    }

    /**
     * Clears all references to the loaded index so that the memory can be garbage collected.
     *
     * @throws IOException
     */
    public void unload()
            throws IOException {
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
        _documentsPagerank = null;
    }

    /**
     * Deletes INDEX_PATH and INDEX_TMP_PATH.
     *
     * @throws IOException
     */
    public void deleteIndex()
            throws IOException {
        Themis.print("-> Deleting previous index...");
        deleteDir(new File(getIndexPath()));
        deleteDir(new File(getIndexTmpPath()));
        Themis.print("Done\n");
    }

    /**
     * Returns true if INDEX_PATH and INDEX_TMP_PATH are empty, false otherwise.
     * @return
     */
    public boolean areIndexDirEmpty() {
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
     * Returns the string ID of a document given its int ID.
     *
     * @param docID
     * @return
     * @throws UnsupportedEncodingException
     * @throws IndexNotLoadedException
     */
    public String getDocID(int docID)
            throws UnsupportedEncodingException, IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        long docIdOffset = DocInfo.getDocIDOffset(docID);
        ByteBuffer buffer = __DOCID_BUFFERS__.getMemBuffer(docIdOffset);
        buffer.get(__DOCUMENT_ID_ARRAY__);

        return new String(__DOCUMENT_ID_ARRAY__, 0, DocumentStringID.SIZE, "ASCII");
    }

    /**
     * Reads DOCUMENTS_FILENAME and DOCUMENTS_META_FILENAME and adds the properties specified
     * by the given props to each of the DocInfo objects of the results.
     *
     * Pre-condition:
     * The first DocInfo object must have the same props as the rest of the objects (for efficiency purposes)
     *
     * When the method returns, only the given props will be present in every DocInfo object.
     *
     * @param props
     * @throws IOException
     * @throws IndexNotLoadedException
     */
    public void updateDocInfo(List<Result> results, Set<DocInfo.PROPERTY> props)
            throws IOException, IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        if (results.size() == 0) {
            return;
        }

        /* get the props of the first DocInfo */
        Set<DocInfo.PROPERTY> firstProps = results.get(0).getDocInfo().get_props();

        /* find the props that will be deleted from each DocInfo */
        Set<DocInfo.PROPERTY> delProps = new HashSet<>(firstProps);
        delProps.removeAll(props);

        /* find the props that will be added to each DocInfo */
        Set<DocInfo.PROPERTY> addProps = new HashSet<>(props);
        addProps.removeAll(firstProps);

        if (delProps.isEmpty() && addProps.isEmpty()) {
            return;
        }

        /* add & remove flags for each prop */
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

        for (Result result : results) {
            DocInfo docInfo = result.getDocInfo();

            /* delete props is straightforward */
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

            long documentsMetaOffset = -1;
            long documentsOffset = 0;
            int documentSize = 0;

            /* add props from DOCUMENTS_META_FILENAME (memory accessed) */
            if (ADD_CITATIONS_PAGERANK || ADD_VSM_WEIGHT || ADD_MAX_TF || ADD_TOKEN_COUNT || ADD_AVG_AUTHOR_RANK || ADD_DOC_SIZE) {

                /* go to the appropriate offset and read all document metadata */
                documentsMetaOffset = DocInfo.getMetaOffset(docInfo.get_docID());
                ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(documentsMetaOffset);
                buffer.get(__DOCUMENT_META_ARRAY__);

                documentSize = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                documentsOffset = __DOCUMENT_META_BUFFER__.getLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET);

                /* add the props */
                if (ADD_CITATIONS_PAGERANK) {
                    double pagerank = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.DOCUMENT_PAGERANK_OFFSET);
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

            /* add props from DOCUMENTS_FILENAME (memory accessed) */
            if (ADD_TITLE || ADD_AUTHORS_NAMES || ADD_JOURNAL_NAME || ADD_AUTHORS_IDS || ADD_YEAR) {

                /* In case we haven't already read props from DOCUMENTS_META_FILENAME, we need to do it now
                * because some of them are required for fetching props from DOCUMENTS_FILENAME */
                if (documentsMetaOffset == -1) {
                    documentsMetaOffset = DocInfo.getMetaOffset(docInfo.get_docID());
                    ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(documentsMetaOffset);
                    buffer.get(__DOCUMENT_META_ARRAY__);
                    documentSize = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                    documentsOffset = __DOCUMENT_META_BUFFER__.getLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET);
                }

                /* go to the appropriate offset and read all document props */
                byte[] __DOCUMENT_ARRAY__ = new byte[documentSize];
                ByteBuffer __DOCUMENT_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_ARRAY__);
                __DOCUMENTS__.seek(documentsOffset);
                __DOCUMENTS__.readFully(__DOCUMENT_ARRAY__, 0, documentSize);

                /* add the props */
                if (ADD_YEAR) {
                    short year = __DOCUMENT_BUFFER__.getShort(DocumentEntry.YEAR_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.YEAR, year);
                }
                int titleSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.TITLE_SIZE_OFFSET);
                int authorNamesSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.AUTHOR_NAMES_SIZE_OFFSET);
                int authorIDsSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.AUTHOR_IDS_SIZE_OFFSET);
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
                    String authorIDs = new String(__DOCUMENT_ARRAY__, offset, authorIDsSize, "ASCII");
                    docInfo.setProperty(DocInfo.PROPERTY.AUTHORS_IDS, authorIDs);
                }
                if (ADD_JOURNAL_NAME) {
                    offset = DocumentEntry.TITLE_OFFSET + titleSize + authorNamesSize + authorIDsSize;
                    String journalName = new String(__DOCUMENT_ARRAY__, offset, journalNameSize, "UTF-8");
                    docInfo.setProperty(DocInfo.PROPERTY.JOURNAL_NAME, journalName);
                }
            }
        }
    }

    /**
     * Returns true if the index has been loaded, false otherwise.
     *
     * @return
     */
    public boolean isloaded() {
        return  __VOCABULARY__ != null &&
                __POSTINGS__ != null &&
                __DOCUMENTS__ != null &&
                __DOCMETA_BUFFERS__ != null &&
                __DOCID_BUFFERS__ != null &&
                _INDEX_META__ != null &&
                _documentsPagerank != null;
    }

    /**
     * Returns an array of DF (document frequency) for the terms in the given list
     *
     * @param query
     * @return
     * @throws IndexNotLoadedException
     */
    public int[] getDF(List<QueryTerm> query)
            throws IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        int[] dfs = new int[query.size()];
        VocabularyEntry vocabularyEntry;
        for (int i = 0; i < query.size(); i++) {
            vocabularyEntry = __VOCABULARY__.get(query.get(i).get_term());
            if (vocabularyEntry != null) {
                dfs[i] = vocabularyEntry.getDF();
            }
        }
        return dfs;
    }

    /**
     * Returns a Postings object that represents the postings of a term in POSTINGS_FILENAME.
     *
     * @param term
     * @return
     * @throws IOException
     * @throws IndexNotLoadedException
     */
    public Postings getPostings(String term)
            throws IOException, IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        VocabularyEntry vocabularyEntry = __VOCABULARY__.get(term);
        if (vocabularyEntry == null) {
            return new Postings(new int[0], new int[0]);
        }

        int DF = vocabularyEntry.getDF();
        __POSTINGS__.seek(vocabularyEntry.getPostingsOffset());
        byte[] postings = new byte[DF * Posting.SIZE];
        __POSTINGS__.readFully(postings);
        ByteBuffer BB = ByteBuffer.wrap(postings);
        int[] docIDs = new int[DF];
        int[] TFs = new int[DF];
        for (int i = 0; i < DF; i++) {
            TFs[i] = BB.getInt();
            docIDs[i] = BB.getInt();
        }
        return new Postings(TFs, docIDs);
    }

    /**
     * Returns a VSMprops object that has the essential props required by the Vector space model.
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public VSMprops getVSMprops()
            throws IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        int documents = Integer.parseInt(_INDEX_META__.get("documents"));
        int[] maxTFs = new int[documents];
        double[] VSMweights = new double[documents];
        for (int i = 0; i < documents; i++) {
            long offset = DocInfo.getMetaOffset(i);
            ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(offset + DocumentMetaEntry.VSM_WEIGHT_OFFSET);
            VSMweights[i] = buffer.getDouble();
            buffer = __DOCMETA_BUFFERS__.getMemBuffer(offset + DocumentMetaEntry.MAX_TF_OFFSET);
            maxTFs[i] = buffer.getInt();
        }
        return new VSMprops(maxTFs, VSMweights);
    }

    /**
     * Returns a OKAPIprops object that has the essential props required by the Okapi model.
     *
     * @return
     */
    public OKAPIprops getOKAPIprops()
            throws IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        int documents = Integer.parseInt(_INDEX_META__.get("documents"));
        int[] tokenCount = new int[documents];
        for (int i = 0; i < documents; i++) {
            long offset = DocInfo.getMetaOffset(i) + DocumentMetaEntry.TOKEN_COUNT_OFFSET;
            ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(offset);
            tokenCount[i] = buffer.getInt();
        }
        return new OKAPIprops(tokenCount);
    }

    /**
     * Returns the total number of indexed documents.
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public int getTotalDocuments()
            throws IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        return Integer.parseInt(_INDEX_META__.get("documents"));
    }

    /**
     * Returns the average number of tokens in the loaded index (required by the Okapi retrieval model).
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public double getAvgDL()
            throws IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        return Double.parseDouble(_INDEX_META__.get("avgdl"));
    }

    /**
     * Returns true if the loaded index has been created with stopwords enabled, false otherwise.
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public Boolean useStopwords()
            throws IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        return Boolean.parseBoolean(_INDEX_META__.get("use_stopwords"));
    }

    /**
     * Returns true if the loaded index has been created with stemming enabled, false otherwise.
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public Boolean useStemmer()
            throws IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        return Boolean.parseBoolean(_INDEX_META__.get("use_stemmer"));
    }

    /**
     * Returns the timestamp of the loaded index
     *
     * @return
     */
    public String getIndexTimestamp()
            throws IndexNotLoadedException {
        if (!isloaded()) {
            throw new IndexNotLoadedException();
        }
        return _INDEX_META__.get("timestamp");
    }

    /**
     * Returns the general configuration options used by this Indexer.
     *
     * @return
     */
    public Config getConfig() {
        return __CONFIG__;
    }

    /**
     * Returns the INDEX_PATH.
     *
     * @return
     */
    public String getIndexPath() {
        return __CONFIG__.getIndexPath() + "/";
    }

    /**
     * Returns the DATASET_PATH.
     *
     * @return
     */
    public String getDataSetPath() {
        return __CONFIG__.getDatasetPath() + "/";
    }

    /**
     * Returns the INDEX_TMP_PATH.
     *
     * @return
     */
    public String getIndexTmpPath() {
        return __CONFIG__.getIndexTmpPath() + "/";
    }

    /**
     * Returns the full path of VOCABULARY_FILENAME. The file is in INDEX_PATH.
     *
     * @return
     */
    public String getVocabularyPath() {
        return __INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__;
    }

    /**
     * Returns the full path of POSTINGS_FILENAME. The file is in INDEX_PATH.
     *
     * @return
     */
    public String getPostingsPath() {
        return __INDEX_PATH__ + "/" + __POSTINGS_FILENAME__;
    }

    /**
     * Returns the full path of DOCUMENTS_FILENAME. The file is in INDEX_PATH.
     *
     * @return
     */
    public String getDocumentsFilePath() {
        return __INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__;
    }

    /**
     * Returns the full path of DOCUMENTS_META_FILENAME. The file is in INDEX_PATH.
     *
     * @return
     */
    public String getDocumentsMetaFilePath() {
        return __INDEX_PATH__ + "/" + __DOCUMENTS_META_FILENAME__;
    }

    /**
     * Returns the full path of DOCUMENTS_ID_FILENAME. The file is in INDEX_PATH.
     *
     * @return
     */
    public String getDocumentsIDFilePath() {
        return __INDEX_PATH__ + "/" + __DOCUMENTS_ID_FILENAME__;
    }

    /**
     * Returns the full path of INDEX_META_FILENAME. The file is in INDEX_PATH.
     *
     * @return
     */
    public String getMetaPath() {
        return __INDEX_PATH__ + "/" + __INDEX_META_FILENAME__;
    }

    /* Returns the full path of 'INDEX_TMP_PATH/term_df' */
    private String getTermDFPath() {
        return getIndexTmpPath() + "term_df";
    }

    /* Returns the full path of 'INDEX_TMP_PATH/doc_df' */
    private String getDocTFPath() {
        return getIndexTmpPath() + "doc_tf";
    }

    /* Returns the full path of the partial index folder 'INDEX_TMP_PATH/ID/' */
    private String getPartialIndexPath(int ID) {
        return getIndexTmpPath() + ID + "/";
    }

    /**
     * Returns the full path of the partial postings 'INDEX_TMP_PATH/ID/POSTINGS_FILENAME'
     *
     * @param ID
     * @return
     */
    public String getPartialPostingsPath(int ID) {
        return getPartialIndexPath(ID) + __POSTINGS_FILENAME__;
    }

    /**
     * Returns the full path of the partial vocabulary 'INDEX_TMP_PATH/ID/VOCABULARY_FILENAME'
     *
     * @param ID
     * @return
     */
    public String getPartialVocabularyPath(int ID) {
        return getPartialIndexPath(ID) + __VOCABULARY_FILENAME__;
    }

    /**
     * Sets the weight for the Pagerank scores of the documents
     *
     * @param weight
     */
    public void setDocumentPagerankWeight(double weight) {
        _documentPagerankWeight = weight;
    }

    /**
     * Gets the weight for the pagerank scores of the documents
     *
     * @return
     */
    public double getDocumentPagerankWeight() {
        return _documentPagerankWeight;
    }

    /**
     * Gets the pagerank scores of the documents
     *
     * @return
     */
    public double[] getDocumentsPagerank() {
        return _documentsPagerank;
    }
}
