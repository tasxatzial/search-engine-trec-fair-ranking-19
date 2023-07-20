package gr.csd.uoc.hy463.themis.indexer;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocumentFixedBuffers;
import gr.csd.uoc.hy463.themis.indexer.MemMap.MemoryBuffers;
import gr.csd.uoc.hy463.themis.indexer.indexes.Index;
import gr.csd.uoc.hy463.themis.indexer.model.*;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntryTokens;
import gr.csd.uoc.hy463.themis.linkAnalysis.Pagerank;
import gr.csd.uoc.hy463.themis.retrieval.QueryTerm;
import gr.csd.uoc.hy463.themis.retrieval.model.OKAPIprops;
import gr.csd.uoc.hy463.themis.retrieval.model.TermPostings;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;
import gr.csd.uoc.hy463.themis.retrieval.model.VSMprops;
import gr.csd.uoc.hy463.themis.utils.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;


/**
 * The main indexer class. It is responsible for two tasks:
 *
 * a) Create the appropriate index(es) given a directory with documents.
 * b) Load the index and provide information about the indexed data.
 * Can be used for implementing any kind of retrieval models.
 */
public class Indexer {
    private static final Logger __LOGGER__ = LogManager.getLogger(Indexer.class);
    private final Config __CONFIG__;
    private boolean __INDEX_IS_LOADED__ = false;
    private Map<String, String> __INDEX_META__ = null;

    private HashMap<String, VocabularyEntry> __VOCABULARY__ = null;
    private RandomAccessFile __POSTINGS__ = null;
    private RandomAccessFile __DOCUMENTS__ = null;

    /* Use DOCUMENTS_META_FILENAME as a memory mapped file */
    private DocumentFixedBuffers __DOCMETA_BUFFERS__ = null;
    private byte[] __DOCUMENT_META_ARRAY__;
    private ByteBuffer __DOCUMENT_META_BUFFER__;

    /* Use DOCUMENTS_ID_FILENAME as a memory mapped file */
    private DocumentFixedBuffers __DOCID_BUFFERS__ = null;
    private byte[] __DOCUMENT_ID_ARRAY__;
    private ByteBuffer __DOCUMENT_ID_BUFFER__; /* currently unused */

    private OKAPIprops __OKAPI_PROPS__ = null;
    private VSMprops __VSM_PROPS__ = null;
    private double[] __DocumentsPagerank__ = null;

    /**
     * Reads configuration options from themis.config file and sets the names of the final index files.
     *
     * @throws IOException
     */
    public Indexer()
            throws IOException {
        this.__CONFIG__ = new Config();
    }

    /**
     * Indexes the collection found in the DATASET_DIR and writes the final index to INDEX_DIR.
     * Aborts if INDEX_DIR is not empty.
     *
     * All temp files will be saved in INDEX_TMP_DIR and will be deleted at the end of the process.
     * If PARTIAL_INDEX_MAX_DOCS is less than the number of documents then >1 partial indexes will be created.
     * Each partial index will contain data from at most PARTIAL_INDEX_MAX_DOCS documents.
     * Finally, all partial indexes are merged to create the final index and temporary files are deleted.
     *
     * @throws IOException
     */
    public void index()
            throws IOException {
        if (!areIndexDirEmpty()) {
            Themis.print("Previous index found. Aborting...\n");
            return;
        }

        __DOCUMENT_META_ARRAY__ = new byte[DocumentMetaEntry.SIZE];
        __DOCUMENT_META_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_META_ARRAY__);
        int maxDocsPerPartialIndex = __CONFIG__.getPartialIndexMaxDocs();

        /* the (int) ID of each document. The N-th parsed document will have ID = N */
        int docID = 0;

        /* the total number of tokens in the collection (required by the Okapi retrieval model) */
        long tokenCount = 0;

        /* offset to DOCUMENTS_FILENAME */
        long documentsOffset = 0;

        /* create the list of files in the given path */
        List<File> corpus = getCorpus();
        if (corpus == null || corpus.size() == 0) {
            Themis.print("No dataset files found in " + __CONFIG__.getDatasetDir() + "\n");
            return;
        }

        __INDEX_META__ = new HashMap<>();
        Themis.print("-> Indexing options:\n");
        __INDEX_META__.put("use_stemmer", String.valueOf(__CONFIG__.getUseStemmer()));
        Themis.print("Stemmer: " + __CONFIG__.getUseStemmer() + "\n");
        __INDEX_META__.put("use_stopwords", String.valueOf(__CONFIG__.getUseStopwords()));
        Themis.print("Stopwords: " + __CONFIG__.getUseStopwords() + "\n");
        __INDEX_META__.put("pagerank_damping", String.valueOf(__CONFIG__.getPagerankDampingFactor()));
        Themis.print("Pagerank damping factor: " + __CONFIG__.getPagerankDampingFactor() + "\n");
        __INDEX_META__.put("pagerank_threshold", String.valueOf(__CONFIG__.getPagerankThreshold()));
        Themis.print("Pagerank threshold: " + __CONFIG__.getPagerankThreshold() + "\n");
        Themis.print("-> Start indexing\n");
        long startTime = System.nanoTime();

        /* use this object for calculating the frequencies of the tokens in a document */
        S2TextualEntryTokens textualEntryTokens = new S2TextualEntryTokens(__CONFIG__.getUseStemmer(), __CONFIG__.getUseStopwords());

        /* create the required index folders */
        Files.createDirectories(Paths.get(__CONFIG__.getIndexDir()));
        Files.createDirectories(Paths.get(__CONFIG__.getIndexTmpDir()));

        /* open INDEX_META_FILENAME (normal sequential file) and
        DOCUMENTS_FILENAME, DOCUMENTS_ID_FILENAME, DOCUMENTS_META_FILENAME (random access files) */
        BufferedWriter metaWriter = new BufferedWriter(new FileWriter(getIndexMetaPath()));
        RandomAccessFile documents = new RandomAccessFile(getDocumentsFilePath(), "rw");
        BufferedOutputStream documentsOutStream = new BufferedOutputStream(new FileOutputStream(documents.getFD()));
        RandomAccessFile documentsMeta = new RandomAccessFile(getDocumentsMetaFilePath(), "rw");
        BufferedOutputStream documentsMetaOutStream = new BufferedOutputStream(new FileOutputStream(documentsMeta.getFD()));
        RandomAccessFile documentsID = new RandomAccessFile(getDocumentsIDFilePath(), "rw");
        BufferedOutputStream documentsIDOutStream = new BufferedOutputStream(new FileOutputStream(documentsID.getFD()));

        /* A 'doc_tf' file will be stored in INDEX_TMP_DIR (normal sequential file).
        Contains a sequence of <term1, TF1, term2, TF2, ...> (one line per document).
        Will be used during the calculation of VSM weights */
        BufferedWriter docTFWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getDocTFPath()), "UTF-8"));

        int indexID = 0;
        Index partialIndex = new Index(this, indexID);

        /* parse the collection */
        for (File corpusFile : corpus) {
            Themis.print("Parsing file: " + corpusFile + "\n");
            BufferedReader corpusReader = new BufferedReader(new InputStreamReader(new FileInputStream(corpusFile), "UTF-8"));
            String json;

            while ((json = corpusReader.readLine()) != null) {
                S2TextualEntry entry = S2JsonEntryReader.readTextualEntry(json);
                if (entry.getID() == null) {
                    continue;
                }
                Map<String, Integer> TFMap = textualEntryTokens.createTFMap(entry);
                int documentTokens = partialIndex.add(TFMap, docTFWriter, docID);
                tokenCount += documentTokens;
                long prevDocumentsOffset = documentsOffset;
                documentsOffset = dumpDocuments(documentsOutStream, entry, documentsOffset);

                /* size of the entry in DOCUMENTS_FILENAME for the currently parsed document */
                int documentSize = (int) (documentsOffset - prevDocumentsOffset);

                dumpDocumentsMeta(documentsMetaOutStream, docID, documentTokens, documentSize, prevDocumentsOffset);
                documentsIDOutStream.write(entry.getID().getBytes("ASCII"));
                docID++;
                if (docID % maxDocsPerPartialIndex == 0) {
                    partialIndex.dump();
                    indexID++;
                    partialIndex = new Index(this, indexID);
                }
            }
            corpusReader.close();
        }

        /* decrease the index id if a new index has just been created but there are no documents left */
        if (docID != 0 && docID % maxDocsPerPartialIndex == 0) {
            indexID--;
        }
        else {
            partialIndex.dump();
        }

        documentsOutStream.close();
        documentsMetaOutStream.close();
        documentsIDOutStream.close();
        docTFWriter.close();
        Themis.print("Partial indexes created in " + new Time(System.nanoTime() - startTime) + "\n");
        __INDEX_META__.put("documents", String.valueOf(docID));
        __INDEX_META__.put("avgdl", String.valueOf((float) tokenCount / docID)); // (average number of tokens)

        mergeVocabularies(indexID);
        try {
            for (int i = 0; i <= indexID; i++) {
                deleteDir(new File(getPartialVocabularyPath(i)));
            }
        } catch (IOException e) {
            __LOGGER__.error(e);
        }

        updateVSMWeights();
        try {
            deleteDir(new File(getDocTFPath()));
        } catch (IOException e) {
            __LOGGER__.error(e);
        }

        mergePostings(indexID);
        try {
            for (int i = 0; i <= indexID; i++) {
                deleteDir(new File(getPartialPostingsPath(i)));
            }
            deleteDir(new File(getTermDFPath()));
            deleteDir(new File(__CONFIG__.getIndexTmpDir()));
        } catch (IOException e) {
            __LOGGER__.error(e);
        }

        Pagerank pagerank = new Pagerank(this);
        pagerank.citationsPagerank();

        /* write index metadata to INDEX_META_FILENAME */
        __INDEX_META__.put("timestamp", Instant.now().toString());
        for (Map.Entry<String, String> pair : __INDEX_META__.entrySet()) {
            metaWriter.write(pair.getKey() + "=" + pair.getValue() + "\n");
        }
        metaWriter.close();

        Themis.print("-> End of indexing\n");
    }

    /* Merges all partial VOCABULARY_FILENAME and creates the final VOCABULARY_FILENAME
    (normal sequential file) in INDEX_DIR */
    private void mergeVocabularies(int maxIndexID)
            throws IOException {
        long startTime =  System.nanoTime();
        Themis.print("-> Merging partial vocabularies...\n");

        /* no need to merge if there's only one partial index */
        if (maxIndexID == 0) {
            finalizeVocabulary();
        }
        else {
            mergePartialVocabularies(maxIndexID);
        }
        Themis.print("Partial vocabularies merged in " + new Time(System.nanoTime() - startTime) + "\n");
    }

    /* Creates the final VOCABULARY_FILENAME when there's only 1 partial index. In this case the only thing we
    need to do is to calculate the offsets to POSTINGS_FILENAME.

    Calculation of the offsets is easy:
    1) For each term we know its DF
    2) Each entry in POSTINGS_FILENAME has the same size
    So:
    (1st term offset)  = 0
    (2nd term offset) = (1st term offset) + (1st term DF)x(POSTING.SIZE)
    (3rd term offset) = (2nd term offset) + (2nd term DF)x(POSTING.SIZE)
    ...
    */
    private void finalizeVocabulary()
            throws IOException {
        String partialVocabularyPath = getPartialVocabularyPath(0);
        BufferedWriter finalVocabularyWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getVocabularyPath()), "UTF-8"));
        BufferedReader partialVocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(partialVocabularyPath), "UTF-8"));
        String line;
        long postingsOffset = 0;
        while ((line = partialVocabularyReader.readLine()) != null) {
            String[] splitLine = line.split(" ");
            int DF = Integer.parseInt(splitLine[1]);
            finalVocabularyWriter.write(splitLine[0] + ' ' + splitLine[1] + ' ' + postingsOffset + '\n');
            postingsOffset +=  DF * (long) Posting.SIZE;
        }
        partialVocabularyReader.close();
        finalVocabularyWriter.close();
    }

    /* Merges all partial VOCABULARY_FILENAME when there's >1 partial index. Also creates 'INDEX_TMP_DIR/term_df',
    this file contains all <index ID, DF> for each term and will be used when merging all partial POSTINGS_FILENAME.

    Process:
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
    by the ID of the index (increasing). We then write all <index ID, DF> pairs to 'INDEX_TMP_DIR/term_df'
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
        /* open files */
        BufferedReader[] partialVocabularyReader = new BufferedReader[maxIndexID + 1];
        for (int i = 0; i <= maxIndexID; i++) {
            partialVocabularyReader[i] = new BufferedReader(new InputStreamReader(new FileInputStream(getPartialVocabularyPath(i)), "UTF-8"));
        }
        BufferedWriter finalVocabularyWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getVocabularyPath()), "UTF-8"));
        BufferedWriter termDFWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getTermDFPath()), "ASCII"));

        /* Read the first line from each partial VOCABULARY_FILENAME and put the entries in a priority queue */
        PriorityQueue<PartialVocabularyEntry> vocabularyQueue = new PriorityQueue<>();
        for (int i = 0; i <= maxIndexID; i++) {
            PartialVocabularyEntry entry = getNextVocabularyEntry(partialVocabularyReader[i], i);
            if (entry != null) {
                vocabularyQueue.add(entry);
            }
        }

        List<PartialVocabularyEntry> equalMinLexEntries = new ArrayList<>();
        String minLexTerm = null;
        PartialVocabularyEntry polledEntry;
        long postingsOffset = 0;
        while((polledEntry = vocabularyQueue.poll()) != null) {

            /* if the term of the polled entry is not equal to the current min term, we must process the current
            list of entries and update the final VOCABULARY_FILENAME. Also update 'INDEX_TMP_DIR/term_df' */
            if (!polledEntry.getTerm().equals(minLexTerm) && !equalMinLexEntries.isEmpty()) {
                postingsOffset = dumpEqualTerms(equalMinLexEntries, finalVocabularyWriter, termDFWriter, postingsOffset);
            }

            minLexTerm = polledEntry.getTerm();
            equalMinLexEntries.add(polledEntry);

            /* read the next line of the partial VOCABULARY_FILENAME that contained the last polled
            entry and add a new entry to the queue */
            int polledEntryIndexID = polledEntry.getIndexID();
            PartialVocabularyEntry entry = getNextVocabularyEntry(partialVocabularyReader[polledEntryIndexID], polledEntryIndexID);
            if (entry != null) {
                vocabularyQueue.add(entry);
            }
        }

        /* all partial VOCABULARY_FILENAME have been parsed. Process the list of remaining entries and write
        what is left to the final VOCABULARY_FILENAME */
        if (!equalMinLexEntries.isEmpty()) {
            dumpEqualTerms(equalMinLexEntries, finalVocabularyWriter, termDFWriter, postingsOffset);
        }

        /* close files */
        for (int i = 0; i <= maxIndexID; i++) {
            partialVocabularyReader[i].close();
        }
        finalVocabularyWriter.close();
        termDFWriter.close();
    }

    /* Reads the next line from the partial VOCABULARY_FILENAME with the given ID and returns a new
    PartialVocabularyEntry */
    private PartialVocabularyEntry getNextVocabularyEntry(BufferedReader vocabularyReader, int indexID)
            throws IOException {
        String line = vocabularyReader.readLine();
        if (line != null) {
            String[] fields = line.split(" ");
            return new PartialVocabularyEntry(fields[0], Integer.parseInt(fields[1]), indexID);
        }
        return null;
    }

    /* Processes the given list of min lex entries (all correspond to the same term) and writes a new line
    to the final VOCABULARY_FILENAME. Also updates 'INDEX_TMP_DIR/term_df' with a new line for the min term.
    Returns the new offset to POSTINGS_FILENAME. It will be used in the next call of the method */
    private long dumpEqualTerms(List<PartialVocabularyEntry> equalMinLexEntries, BufferedWriter finalVocabularyWriter, BufferedWriter termDFWriter, long postingsOffset)
            throws IOException {
        int DF = 0;

        /* sort the list based on the index ID (increasing). This is required so that the postings
        * of the term appear sorted based on the (int) doc ID */
        equalMinLexEntries.sort(PartialVocabularyEntry.IDComparator);

        /* calculate final DF and write to 'INDEX_TMP_DIR/term_df' all <index ID, DF> for the current term */
        StringBuilder SB = new StringBuilder();
        for (PartialVocabularyEntry equalTerm : equalMinLexEntries) {
            DF += equalTerm.getDF();
            SB.append(equalTerm.getIndexID()).append(' ').append(equalTerm.getDF()).append(' ');
        }
        SB.append('\n');
        termDFWriter.write(SB.toString());

        finalVocabularyWriter.write(equalMinLexEntries.get(0).getTerm() + ' ' + DF + ' ' + postingsOffset + '\n');
        postingsOffset +=  DF * (long) Posting.SIZE;
        equalMinLexEntries.clear();
        return postingsOffset;
    }

    /* Merges all partial POSTINGS_FILENAME and creates the final POSTINGS_FILENAME
    (random access file) in INDEX_DIR */
    private void mergePostings(int maxIndexID)
            throws IOException {
        long startTime =  System.nanoTime();
        Themis.print("-> Merging partial postings...\n");

        /* no need to do anything if there's only one partial index */
        if (maxIndexID == 0) {
            String partialPostingsPath = getPartialPostingsPath(0);
            Files.move(Paths.get(partialPostingsPath), Paths.get(getPostingsPath()), StandardCopyOption.REPLACE_EXISTING);
        }
        else {
            mergePartialPostings(maxIndexID);
        }
        Themis.print("Partial postings merged in " + new Time(System.nanoTime() - startTime) + "\n");
    }

    /* Merges all partial POSTINGS_FILENAME when there's >1 partial index.

    Process:
    We'll use 'INDEX_TMP_DIR/term_df' that has been created during the merging of all partial
    VOCABULARY_FILENAME. This is the known information so far:
    1) Each line in 'INDEX_TMP_DIR/term_df' consists of a sequence of <index ID, DF> with the ID
    appearing in increasing order (done in function Indexer.dumpEqualTerms())
    2) Line N in 'INDEX_TMP_DIR/term_df' corresponds to line N in VOCABULARY_FILENAME
    (done in function Indexer.dumpEqualTerms())
    3) The blocks of postings in each partial POSTINGS_FILENAME are already sorted based on the
    sorting of terms in the corresponding partial VOCABULARY_FILENAME (done in function Index.dumpPostings())
    4) Each postings block has its postings already sorted based on the (int) ID of the relevant documents
    (done in function Indexer.index())

    So, for each term in the VOCABULARY_FILENAME file we can easily find:
    1) All partial indexes that contain its postings
    2) The size of its postings: (Sum of DF)x(POSTING.SIZE)
    3) The offset of the postings block for the next term
    */
    private void mergePartialPostings(int maxIndexID)
            throws IOException {
        /* open files */
        BufferedInputStream[] partialPostingsInStream = new BufferedInputStream[maxIndexID + 1];
        for (int i = 0; i <= maxIndexID; i++) {
            partialPostingsInStream[i] = new BufferedInputStream(new FileInputStream(new RandomAccessFile(getPartialPostingsPath(i), "rw").getFD()));
        }
        BufferedOutputStream finalPostingsOutStream = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(getPostingsPath(), "rw").getFD()));
        BufferedReader termDFReader = new BufferedReader(new InputStreamReader(new FileInputStream(getTermDFPath()), "ASCII"));

        /* parse each line of 'INDEX_TMP_DIR/term_df', grab the postings from the appropriate
        partial POSTINGS_FILENAME, and write them to the final POSTINGS_FILENAME */
        String line;
        while ((line = termDFReader.readLine()) != null) {
            String[] splitLine = line.split(" ");
            for (int i = 0; i < splitLine.length; i+=2) {
                int DF = Integer.parseInt(splitLine[i + 1]);
                int indexID = Integer.parseInt(splitLine[i]);
                byte[] postings = new byte[DF * Posting.SIZE];
                partialPostingsInStream[indexID].read(postings);
                finalPostingsOutStream.write(postings);
            }
        }

        /* close files */
        for (BufferedInputStream bufferedInStream : partialPostingsInStream) {
            bufferedInStream.close();
        }
        finalPostingsOutStream.close();
        termDFReader.close();
    }

    /* Writes an entry to DOCUMENTS_META_FILENAME (random access file). See class DocumentMetaEntry.
    PageRank, VSM weight, Max TF, Avg author rank are all initialized to 0. */
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

    /* Writes an entry to DOCUMENTS_FILENAME (random access file). See class DocumentEntry.
    Author names are separated by commas. Author IDs are separated by commas.
    Returns the new offset to DOCUMENTS_FILENAME.
    */
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
    To calculate the weight we need:
    1) The DF of each term : Obtained from the final VOCABULARY_FILENAME.
    2) The TF of each term: 'INDEX_TMP_DIR/doc_tf' already contains a sequence of <term, TF>.
    */
    private void updateVSMWeights()
            throws IOException {
        long startTime = System.nanoTime();
        Themis.print("-> Calculating VSM weights...\n");

        /* load VOCABULARY_FILENAME */
        BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(getVocabularyPath()), "UTF-8"));
        Map<String, Integer> vocabulary = new HashMap<>();
        String line;
        while ((line = vocabularyReader.readLine()) != null) {
            String[] splitLine = line.split(" ");
            vocabulary.put(splitLine[0], Integer.parseInt(splitLine[1]));
        }
        vocabularyReader.close();

        /* open DOCUMENTS_META_FILENAME and 'INDEX_TMP_DIR/doc_tf' */
        __DOCMETA_BUFFERS__ = new DocumentFixedBuffers(getDocumentsMetaFilePath(), MemoryBuffers.MODE.WRITE, DocumentMetaEntry.SIZE);
        BufferedReader docTFReader = new BufferedReader(new InputStreamReader(new FileInputStream(getDocTFPath()), "UTF-8"));

        int documentCount = Integer.parseInt(__INDEX_META__.get("documents"));
        double logDocumentCount = Math.log(documentCount);
        long documentsMetaOffset = 0;

        /* read a line from the 'INDEX_TMP_DIR/doc_tf' and calculate the weight */
        while ((line = docTFReader.readLine()) != null) {
            String[] splitLine = line.split(" ");
            double weight = 0;
            int maxTF = 0;
            for (int i = 0; i < splitLine.length; i += 2) {
                int DF = vocabulary.get(splitLine[i]);
                int TF = Integer.parseInt(splitLine[i + 1]);
                if (TF > maxTF) {
                    maxTF = TF;
                }
                double x = TF * (logDocumentCount - Math.log(DF));
                weight += x * x;
            }
            weight = Math.sqrt(weight) / maxTF;

            /* update DOCUMENTS_META_FILENAME */
            ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(documentsMetaOffset + DocumentMetaEntry.VSM_WEIGHT_OFFSET);
            buffer.putDouble(weight);
            buffer = __DOCMETA_BUFFERS__.getMemBuffer(documentsMetaOffset + DocumentMetaEntry.MAX_TF_OFFSET);
            buffer.putInt(maxTF);
            documentsMetaOffset += DocumentMetaEntry.SIZE;
        }

        /* close files */
        docTFReader.close();
        __DOCMETA_BUFFERS__.close();
        __DOCMETA_BUFFERS__ = null;

        Themis.print("VSM weights calculated in " + new Time(System.nanoTime() - startTime) + "\n");
    }

    /**
     * Loads the index from INDEX_DIR. The following actions take place:
     * 1) VOCABULARY_FILENAME and INDEX_META_FILENAME are loaded.
     * 2) POSTINGS_FILENAME and DOCUMENTS_FILENAME are opened.
     * 3) DOCUMENTS_ID_FILENAME and DOCUMENTS_META_FILENAME are memory mapped.
     *
     * @throws IOException
     */
    public void load()
            throws IOException {
        Themis.print("-> Index path: " + __CONFIG__.getIndexDir() + "\n");
        Themis.print("-> Loading index...");

        /* load index metadata from INDEX_META_FILENAME */
        __INDEX_META__ = loadIndexMeta();
        Themis.print("Stemming: " + __INDEX_META__.get("use_stemmer") + "\n");
        Themis.print("Stopwords: " + __INDEX_META__.get("use_stopwords") + "\n");

        /* load VOCABULARY_FILENAME */
        __VOCABULARY__ = new HashMap<>();
        String line;
        BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(getVocabularyPath()), "UTF-8"));
        while ((line = vocabularyReader.readLine()) != null) {
            String[] fields = line.split(" ");
            __VOCABULARY__.put(fields[0], new VocabularyEntry(Integer.parseInt(fields[1]), Long.parseLong(fields[2])));
        }
        vocabularyReader.close();

        /* open POSTINGS_FILENAME and DOCUMENTS_FILENAME */
        __POSTINGS__ = new RandomAccessFile(getPostingsPath(), "r");
        __DOCUMENTS__ = new RandomAccessFile(getDocumentsFilePath(), "r");

        /* memory map DOCUMENTS_META_FILENAME and DOCUMENTS_ID_FILENAME */
        __DOCMETA_BUFFERS__ = new DocumentFixedBuffers(getDocumentsMetaFilePath(), MemoryBuffers.MODE.READ, DocumentMetaEntry.SIZE);
        __DOCUMENT_META_ARRAY__ = new byte[DocumentMetaEntry.SIZE];
        __DOCUMENT_META_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_META_ARRAY__);
        __DOCID_BUFFERS__ = new DocumentFixedBuffers(getDocumentsIDFilePath(), MemoryBuffers.MODE.READ, DocumentStringID.SIZE);
        __DOCUMENT_ID_ARRAY__ = new byte[DocumentStringID.SIZE];
        __DOCUMENT_ID_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_ID_ARRAY__);

        __INDEX_IS_LOADED__ = true;
        Themis.print("Done\n");
    }

    /**
     * Loads index metadata from INDEX_META_FILENAME.
     *
     * @return
     * @throws IOException
     */
    public Map<String, String> loadIndexMeta()
            throws IOException {
        Map<String, String> meta;
        BufferedReader indexMetaReader = new BufferedReader(new FileReader(getIndexMetaPath()));
        meta = new HashMap<>();
        String line;
        while((line = indexMetaReader.readLine()) != null) {
            String[] split = line.split("=");
            meta.put(split[0], split[1]);
        }
        indexMetaReader.close();
        return meta;
    }

    /**
     * Clears all references to the loaded index.
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
        __INDEX_META__ = null;
        __VSM_PROPS__ = null;
        __OKAPI_PROPS__ = null;
        __DocumentsPagerank__ = null;
        __INDEX_IS_LOADED__ = false;
    }

    /**
     * Returns a list of all files in DATASET_DIR.
     *
     * @return
     */
    public List<File> getCorpus() {
        File folder = new File(__CONFIG__.getDatasetDir());
        File[] files = folder.listFiles();
        if (files == null) {
            return null;
        }
        List<File> corpus = new ArrayList<>();
        for (File f : files) {
            if (f.isFile()) {
                corpus.add(f);
            }
        }
        Collections.sort(corpus);

        return corpus;
    }

    /**
     * Deletes INDEX_DIR and INDEX_TMP_DIR.
     *
     * @throws IOException
     */
    public void deleteIndex()
            throws IOException {
        Themis.print("-> Deleting previous index...");
        deleteDir(new File(__CONFIG__.getIndexDir()));
        deleteDir(new File(__CONFIG__.getIndexTmpDir()));
        Themis.print("Done\n");
    }

    /**
     * Returns true if INDEX_DIR and INDEX_TMP_DIR are empty, false otherwise.
     *
     * @return
     */
    public boolean areIndexDirEmpty() {
        File file = new File(__CONFIG__.getIndexDir());
        File[] fileList = file.listFiles();
        if (fileList != null && fileList.length != 0) {
            return false;
        }
        file = new File(__CONFIG__.getIndexTmpDir());
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
        if (!isLoaded()) {
            throw new IndexNotLoadedException();
        }
        long docIDOffset = DocInfo.getDocIDOffset(docID);
        ByteBuffer buffer = __DOCID_BUFFERS__.getMemBuffer(docIDOffset);
        buffer.get(__DOCUMENT_ID_ARRAY__);

        return new String(__DOCUMENT_ID_ARRAY__, 0, DocumentStringID.SIZE, "ASCII");
    }

    /**
     * Reads DOCUMENTS_FILENAME and DOCUMENTS_META_FILENAME and adds the properties specified
     * by the given props to each of the {@link Result}s.
     *
     * For efficiency purposes, the function expects that all Results already have the same props.
     * It then proceeds by comparing the existing props of the first Result to the new props. Each Result will receive
     * only the props that are not already present in it, and will get rid of any props that are not in the new props.
     *
     * When the method returns, every Result will have the given props only.
     *
     * @param props
     * @throws IOException
     * @throws IndexNotLoadedException
     */
    public void updateDocInfo(List<Result> results, Set<DocInfo.PROPERTY> props)
            throws IOException, IndexNotLoadedException {
        if (!isLoaded()) {
            throw new IndexNotLoadedException();
        }
        if (results.size() == 0) {
            return;
        }
        Set<DocInfo.PROPERTY> firstResultProps = results.get(0).getDocInfo().getProps();
        Set<DocInfo.PROPERTY> deletedProps = new HashSet<>(firstResultProps);
        deletedProps.removeAll(props);
        Set<DocInfo.PROPERTY> addedProps = new HashSet<>(props);
        addedProps.removeAll(firstResultProps);
        if (deletedProps.isEmpty() && addedProps.isEmpty()) {
            return;
        }

        /* add & remove flags for each prop */
        boolean ADD_CITATIONS_PAGERANK = addedProps.contains(DocInfo.PROPERTY.CITATIONS_PAGERANK);
        boolean ADD_VSM_WEIGHT = addedProps.contains(DocInfo.PROPERTY.VSM_WEIGHT);
        boolean ADD_MAX_TF = addedProps.contains(DocInfo.PROPERTY.MAX_TF);
        boolean ADD_TOKEN_COUNT = addedProps.contains(DocInfo.PROPERTY.TOKEN_COUNT);
        boolean ADD_AVG_AUTHOR_RANK = addedProps.contains(DocInfo.PROPERTY.AVG_AUTHOR_RANK);
        boolean ADD_TITLE = addedProps.contains(DocInfo.PROPERTY.TITLE);
        boolean ADD_AUTHORS_NAMES = addedProps.contains(DocInfo.PROPERTY.AUTHORS_NAMES);
        boolean ADD_JOURNAL_NAME = addedProps.contains(DocInfo.PROPERTY.JOURNAL_NAME);
        boolean ADD_AUTHORS_IDS = addedProps.contains(DocInfo.PROPERTY.AUTHORS_IDS);
        boolean ADD_YEAR = addedProps.contains(DocInfo.PROPERTY.YEAR);
        boolean ADD_DOC_SIZE = addedProps.contains(DocInfo.PROPERTY.DOCUMENT_SIZE);

        boolean DEL_CITATIONS_PAGERANK = deletedProps.contains(DocInfo.PROPERTY.CITATIONS_PAGERANK);
        boolean DEL_VSM_WEIGHT = deletedProps.contains(DocInfo.PROPERTY.VSM_WEIGHT);
        boolean DEL_MAX_TF = deletedProps.contains(DocInfo.PROPERTY.MAX_TF);
        boolean DEL_TOKEN_COUNT = deletedProps.contains(DocInfo.PROPERTY.TOKEN_COUNT);
        boolean DEL_AVG_AUTHOR_RANK = deletedProps.contains(DocInfo.PROPERTY.AVG_AUTHOR_RANK);
        boolean DEL_TITLE = deletedProps.contains(DocInfo.PROPERTY.TITLE);
        boolean DEL_AUTHORS_NAMES = deletedProps.contains(DocInfo.PROPERTY.AUTHORS_NAMES);
        boolean DEL_JOURNAL_NAME = deletedProps.contains(DocInfo.PROPERTY.JOURNAL_NAME);
        boolean DEL_AUTHORS_IDS = deletedProps.contains(DocInfo.PROPERTY.AUTHORS_IDS);
        boolean DEL_YEAR = deletedProps.contains(DocInfo.PROPERTY.YEAR);
        boolean DEL_DOC_SIZE = addedProps.contains(DocInfo.PROPERTY.DOCUMENT_SIZE);

        for (Result result : results) {
            DocInfo docInfo = result.getDocInfo();

            /* delete props */
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

            /* add props from DOCUMENTS_META_FILENAME */
            if (ADD_CITATIONS_PAGERANK || ADD_VSM_WEIGHT || ADD_MAX_TF || ADD_TOKEN_COUNT || ADD_AVG_AUTHOR_RANK || ADD_DOC_SIZE) {

                /* go to DOCUMENTS_META_FILENAME offset and fetch the required document metadata props */
                documentsMetaOffset = DocInfo.getMetaOffset(docInfo.getDocID());
                ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(documentsMetaOffset);
                buffer.get(__DOCUMENT_META_ARRAY__);
                documentSize = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                documentsOffset = __DOCUMENT_META_BUFFER__.getLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET);

                if (ADD_CITATIONS_PAGERANK) {
                    double pagerank = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.DOCUMENT_PAGERANK_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.CITATIONS_PAGERANK, pagerank);
                }
                if (ADD_VSM_WEIGHT) {
                    double weight = __DOCUMENT_META_BUFFER__.getDouble(DocumentMetaEntry.VSM_WEIGHT_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.VSM_WEIGHT, weight);
                }
                if (ADD_MAX_TF) {
                    int maxTF = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.MAX_TF_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.MAX_TF, maxTF);
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

            /* add props from DOCUMENTS_FILENAME */
            if (ADD_TITLE || ADD_AUTHORS_NAMES || ADD_JOURNAL_NAME || ADD_AUTHORS_IDS || ADD_YEAR) {

                /* In case we haven't already read props from DOCUMENTS_META_FILENAME, we need to do it now
                because some of them are required for fetching props from DOCUMENTS_FILENAME */
                if (documentsMetaOffset == -1) {
                    documentsMetaOffset = DocInfo.getMetaOffset(docInfo.getDocID());
                    ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(documentsMetaOffset);
                    buffer.get(__DOCUMENT_META_ARRAY__);
                    documentSize = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                    documentsOffset = __DOCUMENT_META_BUFFER__.getLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET);
                }

                /* go to DOCUMENTS_FILENAME offset and fetch the required document props */
                byte[] __DOCUMENT_ARRAY__ = new byte[documentSize];
                ByteBuffer __DOCUMENT_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_ARRAY__);
                __DOCUMENTS__.seek(documentsOffset);
                __DOCUMENTS__.readFully(__DOCUMENT_ARRAY__, 0, documentSize);

                if (ADD_YEAR) {
                    short year = __DOCUMENT_BUFFER__.getShort(DocumentEntry.YEAR_OFFSET);
                    docInfo.setProperty(DocInfo.PROPERTY.YEAR, year);
                }
                int titleSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.TITLE_SIZE_OFFSET);
                int authorNamesSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.AUTHOR_NAMES_SIZE_OFFSET);
                int authorIDsSize = __DOCUMENT_BUFFER__.getInt(DocumentEntry.AUTHOR_IDS_SIZE_OFFSET);
                short journalNameSize = __DOCUMENT_BUFFER__.getShort(DocumentEntry.JOURNAL_NAME_SIZE_OFFSET);
                int documentLocalOffset;
                if (ADD_TITLE) {
                    String title = new String(__DOCUMENT_ARRAY__, DocumentEntry.TITLE_OFFSET, titleSize, "UTF-8");
                    docInfo.setProperty(DocInfo.PROPERTY.TITLE, title);
                }
                if (ADD_AUTHORS_NAMES) {
                    documentLocalOffset = DocumentEntry.TITLE_OFFSET + titleSize;
                    String authorNames = new String(__DOCUMENT_ARRAY__, documentLocalOffset, authorNamesSize, "UTF-8");
                    docInfo.setProperty(DocInfo.PROPERTY.AUTHORS_NAMES, authorNames);
                }
                if (ADD_AUTHORS_IDS) {
                    documentLocalOffset = DocumentEntry.TITLE_OFFSET + titleSize + authorNamesSize;
                    String authorIDs = new String(__DOCUMENT_ARRAY__, documentLocalOffset, authorIDsSize, "ASCII");
                    docInfo.setProperty(DocInfo.PROPERTY.AUTHORS_IDS, authorIDs);
                }
                if (ADD_JOURNAL_NAME) {
                    documentLocalOffset = DocumentEntry.TITLE_OFFSET + titleSize + authorNamesSize + authorIDsSize;
                    String journalName = new String(__DOCUMENT_ARRAY__, documentLocalOffset, journalNameSize, "UTF-8");
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
    public boolean isLoaded() {
        return __INDEX_IS_LOADED__;
    }

    /**
     * Returns an array of DF (document frequency) for the given terms list.
     *
     * @param query
     * @return
     * @throws IndexNotLoadedException
     */
    public int[] getDFs(List<QueryTerm> query)
            throws IndexNotLoadedException {
        if (!isLoaded()) {
            throw new IndexNotLoadedException();
        }
        int[] DFs = new int[query.size()];
        VocabularyEntry vocabularyEntry;
        for (int i = 0; i < query.size(); i++) {
            vocabularyEntry = __VOCABULARY__.get(query.get(i).get_term());
            if (vocabularyEntry != null) {
                DFs[i] = vocabularyEntry.getDF();
            }
        }
        return DFs;
    }

    /**
     * Returns a {@link TermPostings} object that represents the postings of a term in POSTINGS_FILENAME.
     *
     * @param term
     * @return
     * @throws IOException
     * @throws IndexNotLoadedException
     */
    public TermPostings getPostings(String term)
            throws IOException, IndexNotLoadedException {
        if (!isLoaded()) {
            throw new IndexNotLoadedException();
        }
        VocabularyEntry vocabularyEntry = __VOCABULARY__.get(term);
        if (vocabularyEntry == null) {
            return new TermPostings(new int[0], new int[0]);
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
        return new TermPostings(TFs, docIDs);
    }

    /**
     * Returns a {@link VSMprops} object that has the essential props required by the Vector space model.
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public VSMprops getVSMprops()
            throws IndexNotLoadedException {
        if (!isLoaded()) {
            throw new IndexNotLoadedException();
        }
        if (__VSM_PROPS__ != null) {
            return __VSM_PROPS__;
        }
        int documentCount = Integer.parseInt(__INDEX_META__.get("documents"));
        int[] maxTFs = new int[documentCount];
        double[] VSMweights = new double[documentCount];
        for (int i = 0; i < documentCount; i++) {
            long offset = DocInfo.getMetaOffset(i);
            ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(offset + DocumentMetaEntry.VSM_WEIGHT_OFFSET);
            VSMweights[i] = buffer.getDouble();
            buffer = __DOCMETA_BUFFERS__.getMemBuffer(offset + DocumentMetaEntry.MAX_TF_OFFSET);
            maxTFs[i] = buffer.getInt();
        }
        __VSM_PROPS__ = new VSMprops(maxTFs, VSMweights);
        return __VSM_PROPS__;
    }

    /**
     * Returns a {@link OKAPIprops} object that has the essential props required by the Okapi model.
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public OKAPIprops getOKAPIprops()
            throws IndexNotLoadedException {
        if (!isLoaded()) {
            throw new IndexNotLoadedException();
        }
        if (__OKAPI_PROPS__ != null) {
            return __OKAPI_PROPS__;
        }
        int documentCount = Integer.parseInt(__INDEX_META__.get("documents"));
        int[] tokenCount = new int[documentCount];
        for (int i = 0; i < documentCount; i++) {
            long offset = DocInfo.getMetaOffset(i) + DocumentMetaEntry.TOKEN_COUNT_OFFSET;
            ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(offset);
            tokenCount[i] = buffer.getInt();
        }
        __OKAPI_PROPS__ = new OKAPIprops(tokenCount);
        return __OKAPI_PROPS__;
    }

    /**
     * Returns an array of the pagerank scores of the documents.
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public double[] getDocumentsPagerank()
            throws IndexNotLoadedException {
        if (!isLoaded()) {
            throw new IndexNotLoadedException();
        }
        if (__DocumentsPagerank__ != null) {
            return __DocumentsPagerank__;
        }
        int documentCount = Integer.parseInt(__INDEX_META__.get("documents"));
        double[] documentsPagerank = new double[documentCount];
        for (int i = 0; i < documentCount; i++) {
            long offset = DocInfo.getMetaOffset(i) + DocumentMetaEntry.DOCUMENT_PAGERANK_OFFSET;
            ByteBuffer buffer = __DOCMETA_BUFFERS__.getMemBuffer(offset);
            documentsPagerank[i] = buffer.getDouble();
        }
        __DocumentsPagerank__ = documentsPagerank;
        return __DocumentsPagerank__;
    }

    /**
     * Returns the total number of indexed documents.
     *
     * @return
     * @throws IOException
     */
    public int getTotalDocuments()
            throws IOException {
        if (__INDEX_META__ == null) {
            loadIndexMeta();
        }
        return Integer.parseInt(__INDEX_META__.get("documents"));
    }

    /**
     * Returns the average number of tokens per document (required by the Okapi retrieval model).
     *
     * @return
     * @throws IOException
     */
    public double getAvgDL()
            throws IOException {
        if (__INDEX_META__ == null) {
            loadIndexMeta();
        }
        return Double.parseDouble(__INDEX_META__.get("avgdl"));
    }

    /**
     * Returns true if the index has been created with stopwords enabled, false otherwise.
     *
     * @return
     * @throws IOException
     */
    public Boolean useStopwords()
            throws IOException {
        if (__INDEX_META__ == null) {
            loadIndexMeta();
        }
        return Boolean.parseBoolean(__INDEX_META__.get("use_stopwords"));
    }

    /**
     * Returns true if the index has been created with stemming enabled, false otherwise.
     *
     * @return
     * @throws IOException
     */
    public Boolean useStemmer()
            throws IOException {
        if (__INDEX_META__ == null) {
            loadIndexMeta();
        }
        return Boolean.parseBoolean(__INDEX_META__.get("use_stemmer"));
    }

    /**
     * Returns the timestamp of the index.
     *
     * @return
     * @throws IOException
     */
    public String getIndexTimestamp()
            throws IOException {
        if (__INDEX_META__ == null) {
            loadIndexMeta();
        }
        return __INDEX_META__.get("timestamp");
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
     * Returns the full path of VOCABULARY_FILENAME. The file is in INDEX_DIR.
     *
     * @return
     */
    public String getVocabularyPath() {
        return __CONFIG__.getIndexDir() + __CONFIG__.getVocabularyFileName();
    }

    /**
     * Returns the full path of POSTINGS_FILENAME. The file is in INDEX_DIR.
     *
     * @return
     */
    public String getPostingsPath() {
        return __CONFIG__.getIndexDir() + __CONFIG__.getPostingsFileName();
    }

    /**
     * Returns the full path of DOCUMENTS_FILENAME. The file is in INDEX_DIR.
     *
     * @return
     */
    public String getDocumentsFilePath() {
        return __CONFIG__.getIndexDir() + __CONFIG__.getDocumentsFileName();
    }

    /**
     * Returns the full path of DOCUMENTS_META_FILENAME. The file is in INDEX_DIR.
     *
     * @return
     */
    public String getDocumentsMetaFilePath() {
        return __CONFIG__.getIndexDir() + __CONFIG__.getDocumentsMetaFileName();
    }

    /**
     * Returns the full path of DOCUMENTS_ID_FILENAME. The file is in INDEX_DIR.
     *
     * @return
     */
    public String getDocumentsIDFilePath() {
        return __CONFIG__.getIndexDir() + __CONFIG__.getDocumentsIDFileName();
    }

    /**
     * Returns the full path of INDEX_META_FILENAME. The file is in INDEX_DIR.
     *
     * @return
     */
    public String getIndexMetaPath() {
        return __CONFIG__.getIndexDir() + __CONFIG__.getIndexMetaFileName();
    }

    /* Returns the full path of 'INDEX_TMP_DIR/term_df' */
    private String getTermDFPath() {
        return __CONFIG__.getIndexTmpDir() + "term_df";
    }

    /* Returns the full path of 'INDEX_TMP_DIR/doc_df' */
    private String getDocTFPath() {
        return __CONFIG__.getIndexTmpDir() + "doc_tf";
    }

    /* Returns the full path of the partial index folder 'INDEX_TMP_DIR/ID/' */
    private String getPartialIndexDir(int ID) {
        return __CONFIG__.getIndexTmpDir() + ID + "/";
    }

    /**
     * Returns the full path of the partial postings file 'INDEX_TMP_DIR/ID/POSTINGS_FILENAME'.
     *
     * @param ID
     * @return
     */
    public String getPartialPostingsPath(int ID) {
        return getPartialIndexDir(ID) + __CONFIG__.getPostingsFileName();
    }

    /**
     * Returns the full path of the partial vocabulary file 'INDEX_TMP_DIR/ID/VOCABULARY_FILENAME'.
     *
     * @param ID
     * @return
     */
    public String getPartialVocabularyPath(int ID) {
        return getPartialIndexDir(ID) + __CONFIG__.getVocabularyFileName();
    }
}
