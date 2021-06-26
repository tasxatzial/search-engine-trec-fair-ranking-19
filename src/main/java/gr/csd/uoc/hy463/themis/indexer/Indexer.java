package gr.csd.uoc.hy463.themis.indexer;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.config.Exceptions.ConfigLoadException;
import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocumentBuffers;
import gr.csd.uoc.hy463.themis.indexer.MemMap.MemoryBuffers;
import gr.csd.uoc.hy463.themis.indexer.indexes.Index;
import gr.csd.uoc.hy463.themis.indexer.model.*;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntryTermFrequencies;
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
 * indexed data, that can be used for implementing any kind of retrieval models
 */
public class Indexer {
    private static final Logger __LOGGER__ = LogManager.getLogger(Indexer.class);
    private double _pagerankWeight;

    /* General configuration options */
    private final Config __CONFIG__;

    /* The final 'index' folder name */
    private final String __INDEX_PATH__;

    /* The name of the files in the final index */
    private final String __VOCABULARY_FILENAME__;
    private final String __POSTINGS_FILENAME__;
    private final String __DOCUMENTS_FILENAME__ ;
    private final String __DOCUMENTS_META_FILENAME__;
    private final String __DOCUMENTS_ID_FILENAME__;

    /* Index configuration options */
    private Map<String, String> _INDEX_META__ = null;

    /* The 'index_meta' file name */
    private final String __META_FILENAME__;

    /* The final 'vocabulary' file */
    private HashMap<String, Vocabulary> __VOCABULARY__ = null;

    /* The final 'postings' file */
    private RandomAccessFile __POSTINGS__ = null;

    /* The final 'documents' file */
    private RandomAccessFile __DOCUMENTS__ = null;

    /* Object for using the 'documents_meta' file as a memory mapped file */
    private DocumentBuffers __DOCMETA_BUFFERS__ = null;

    /* Object for using the 'documents_id' file as a memory mapped file */
    private DocumentBuffers __DOCID_BUFFERS__ = null;

    /* Stores all document information read from the 'documents_meta' file. See DocumentMetaEntry class */
    private byte[] __DOCUMENT_META_ARRAY__;
    private ByteBuffer __DOCUMENT_META_BUFFER__;

    /* Stores the document docID (string) read from the 'documents_id' file. See DocumentIDEntry class */
    private byte[] __DOCUMENT_ID_ARRAY__;
    private ByteBuffer __DOCUMENT_ID_BUFFER__;

    /**
     * Constructor.
     *
     * Reads general configuration options from themis.config file.
     *
     * Initializes the filenames in the final index.
     * Specifically the names of:
     * The 'index' folder
     * The 'vocabulary' file
     * The 'postings' file
     * The 'documents_meta' file
     * The 'documents_id' file
     * The 'documents' file
     * The 'index_meta' file
     *
     * Also initializes the citations pagerank weight to its default value.
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
        __META_FILENAME__ = __CONFIG__.getMetaFileName();
        _pagerankWeight = __CONFIG__.getPagerankPublicationsWeight();
    }

    /**
     * Indexes the collection found in the 'dataset' folder. See also method index(path).
     *
     * @throws IOException
     * @throws PagerankException
     */
    public void index()
            throws IOException, PagerankException {
        index(getDataSetPath());
    }

    /**
     * Indexes the collection found in the specified path. Final index is stored in the 'index' folder.
     *
     * If the number of files is larger than the PARTIAL_INDEX_MAX_DOCS_SIZE then we have to dump all data read up
     * to now to a partial index in 'index_tmp/index_id' folder and continue with a new index.
     * After creating all partial indexes then we have to merge them to create the final index.
     *
     * All partial indexes are stored in the 'index_tmp' folder and will have been deleted at the end of the
     * indexing process.
     *
     * When this methods returns, the following files will be present in the 'index' folder.
     * 1) A 'vocabulary' file
     * 2) A 'postings' file
     * 3) A 'documents_id' file
     * 4) A 'documents_meta' file
     * 5) A 'documents' file
     * 6) A 'index_meta' file for the index configuration options
     *
     * @param path
     * @throws IOException
     * @throws PagerankException
     */
    public void index(String path)
            throws IOException, PagerankException {
        if (!isIndexDirEmpty()) {
            __LOGGER__.error("Previous index found. Aborting...");
            Themis.print("Previous index found. Aborting...\n");
            return;
        }

        __DOCUMENT_META_ARRAY__ = new byte[DocumentMetaEntry.totalSize];
        __DOCUMENT_META_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_META_ARRAY__);

        /* the path of the 'index_tmp' folder */
        String indexTmpPath = getIndexTmpPath();

        /* the int ID of each document => the N-th parsed JSON entry has ID = N (starting from 0) */
        int docIntID = 0;

        /* the total number of tokens in the collection, required for the Okapi retrieval model */
        long totalTokens = 0;

        /* offset to the 'documents' file */
        long documentsOffset = 0;

        /* maximum number of documents in a partial index */
        int maxIndexDocuments = __CONFIG__.getPartialIndexSize();

        /* create a list of files of the collection */
        File folder = new File(path);
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            __LOGGER__.info("No dataset files found in " + path);
            Themis.print("No dataset files found in " + path + "\n");
            return;
        }

        /* save the final index configuration options */
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

        Themis.print(">>> Start indexing\n");
        long startTime = System.nanoTime();

        /* sort the files of the collection (determines the parsing order) */
        List<File> corpus = new ArrayList<>(files.length);
        corpus.addAll(Arrays.asList(files));
        Collections.sort(corpus);

        /* instantiate the object that calculates the frequencies map for the terms in a S2TextualEntry */
        S2TextualEntryTermFrequencies termFrequencies = new S2TextualEntryTermFrequencies(__CONFIG__.getUseStemmer(), __CONFIG__.getUseStopwords());

        /* create the final 'index' folder */
        Files.createDirectories(Paths.get(__INDEX_PATH__));

        /* create the 'index_tmp' folder where all temporary files will be stored */
        Files.createDirectories(Paths.get(indexTmpPath));

        /* any info related to this index is written to 'index_meta' file in the 'index' folder */
        BufferedWriter metaWriter = new BufferedWriter(new FileWriter(getMetaPath()));

        /* 'documents', 'documents_id', 'documents_meta' files will be saved in the 'index' folder */
        RandomAccessFile documents = new RandomAccessFile(getDocumentsFilePath(), "rw");
        BufferedOutputStream documents_out = new BufferedOutputStream(new FileOutputStream(documents.getFD()));
        RandomAccessFile documentsMeta = new RandomAccessFile(getDocumentsMetaFilePath(), "rw");
        BufferedOutputStream documentsMeta_out = new BufferedOutputStream(new FileOutputStream(documentsMeta.getFD()));
        RandomAccessFile documentsID = new RandomAccessFile(getDocumentsIDFilePath(), "rw");
        BufferedOutputStream documentsID_out = new BufferedOutputStream(new FileOutputStream(documentsID.getFD()));

        /* The 'doc_tf' file in 'index_tmp' folder will store the <term, TF> of every term that appears in
        each document (one line per document). Will be used during the calculation of VSM weights */
        BufferedWriter docTfWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getDocTfPath()), "UTF-8"));

        /* initialize a partial index */
        int indexID = 0;
        Index index = new Index(this, indexID);

        /* parse the collection */
        for (File file : corpus) {
            if (file.isFile()) {
                Themis.print("Parsing file: " + file + "\n");
                BufferedReader currentDataFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String json;

                /* for each JSON entry */
                while ((json = currentDataFile.readLine()) != null) {

                    /* Extract all textual info into a S2TextualEntry */
                    S2TextualEntry entry = S2JsonEntryReader.readTextualEntry(json);

                    /* create the frequencies map for the terms of the S2TextualEntry */
                    Map<String, Integer> termTF = termFrequencies.createWordsMap(entry);

                    /* update the partial index (in memory) and the 'doc_tf' file */
                    int documentTokens = index.add(termTF, docTfWriter, docIntID);

                    /* update the number of tokens */
                    totalTokens += documentTokens;

                    /* update the 'documents' file */
                    long prevDocumentsOffset = documentsOffset;
                    documentsOffset = dumpDocuments(documents_out, entry, documentsOffset);

                    /* size of an entry in the 'documents' file */
                    int documentSize = (int) (documentsOffset - prevDocumentsOffset);

                    /* update the 'documents_meta' file. Use an integer as the ID of the document
                    * instead of the string docID */
                    dumpDocumentsMeta(documentsMeta_out, docIntID, documentTokens, documentSize, prevDocumentsOffset);

                    /* update the 'documents_id' file. Write only the string docID in this file */
                    dumpDocumentsID(documentsID_out, entry);

                    /* check if a dump of the current index is needed */
                    docIntID++;
                    if (docIntID % maxIndexDocuments == 0) {
                        index.dump();
                        indexID++;
                        index = new Index(this, indexID);
                    }
                }
                currentDataFile.close();
            }
        }

        /* remove the last index when the total number of documents is a multiple of maxIndexDocuments.
        This means we created a new index but there are no documents left */
        if (docIntID != 0 && docIntID % maxIndexDocuments == 0) {
            indexID--;
        }
        else {
            /* dump the remaining index data */
            index.dump();
        }

        documents_out.close();
        documentsMeta_out.close();
        documentsID_out.close();
        docTfWriter.close();

        /* calculate avgdl for the Okapi retrieval model */
        double avgdl = (0.0 + totalTokens) / docIntID;

        /* save the remaining final index configuration options */
        _INDEX_META__.put("documents", String.valueOf(docIntID));
        _INDEX_META__.put("avgdl", String.valueOf(avgdl));
        _INDEX_META__.put("timestamp", Instant.now().toString());

        /* finally, dump all final index configuration options to the 'index_meta' file */
        for (Map.Entry<String, String> pair : _INDEX_META__.entrySet()) {
            metaWriter.write(pair.getKey() + "=" + pair.getValue() + "\n");
        }
        metaWriter.close();

        Themis.print("Documents files & partial indexes created in " + new Time(System.nanoTime() - startTime) + "\n");

        /* merge the partial 'vocabulary' files and delete them */
        mergeVocabularies(indexID);
        try {
            for (int i = 0; i <= indexID; i++) {
                deleteDir(new File(getPartialVocabularyPath(i)));
            }
        } catch (IOException e) {
            __LOGGER__.error("Error deleting partial vocabularies");
            Themis.print("[Error deleting partial vocabularies]\n");
        }

        /* calculate VSM weights, update the 'document_meta' file, and delete the 'doc_tf' file */
        updateVSMweights();
        deleteDir(new File(getDocTfPath()));

        /* merge the partial 'postings' files and delete them */
        mergePostings(indexID);
        try {
            for (int i = 0; i <= indexID; i++) {
                deleteDir(new File(getPartialPostingPath(i)));
            }
        } catch (IOException e) {
            __LOGGER__.error("Error deleting partial postings");
            Themis.print("[Error deleting partial postings]\n");
        }

        /* also delete the 'term_df' file that has been created during the merge of the 'vocabulary' files in
        the 'index_tmp' folder */
        deleteDir(new File(getTermDfPath()));

        /* compute the citations pagerank scores and update the 'documents_meta' file */
        Pagerank pagerank = new Pagerank(this);
        pagerank.citationsPagerank();

        /* finally, delete the 'index_tmp' folder */
        try {
            deleteDir(new File(getIndexTmpPath()));
        } catch (IOException e) {
            __LOGGER__.error("Error deleting index tmp folder");
            Themis.print("[Error deleting index tmp folder]\n");
        }

        Themis.print(">>> End of indexing\n");
    }

    /* Merges the partial 'vocabulary' files found in the 'index_tmp/*' folders where '*' is a
    number from 0 to indexID (inclusive). Creates the final 'vocabulary' file (normal sequential file)
    in the 'index' folder.

    If there is only 1 partial index, we cannot simply copy the 'vocabulary' file to the final location
    because it is missing the offsets to the 'postings' file. However the offsets can be determined by using the
    following information:
    1) For each term we know its DF
    2) Each posting occupies the same fixed space in the 'postings' file.
    So:
    (1st term offset)  = 0
    (2nd term offset) = (1st term offset) + (1st term DF)x(posting size)
    (3rd term offset) = (2nd term offset) + (2nd term DF)x(posting size)
    etc. */
    private void mergeVocabularies(int indexID)
            throws IOException {
        long startTime =  System.nanoTime();
        Themis.print(">>> Start Merging of partial vocabularies\n");

        if (indexID == 0) {
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
                offset +=  df * (long) Posting.totalSize;
            }
            vocabularyReader.close();
            vocabularyWriter.close();
        } else {
            /* merge the partial 'vocabulary' files in case there are >1 partial indexes */
            combinePartialVocabularies(indexID);
        }
        Themis.print("Partial vocabularies merged in " + new Time(System.nanoTime() - startTime) + "\n");
    }

    /* Merges the partial 'vocabulary' files when there are more than 1 partial indexes.

    Also writes the 'term_df' file in the 'index_tmp' folder. It will come in handy later when the partial 'posting'
    files are merged.

    Merge process for the 'vocabulary' files:
    Assume we have K 'vocabulary' files. They can be merged in one go using the following procedure:
    1) Open all files and keep in PartialVocabulary objects the <term, DF, index ID> of the
    first line from each file. Since the files are sorted, we know that the min lexicographical term will
    correspond to one (or more) of those PartialVocabulary objects.
    2) Instead of performing a linear search to find the min lexicographical term among the K objects, we
    use a priority queue java struct and add the objects to it. Adding/removing costs log(K). Also removing
    an object will give us the min lexicographical term if the queue is sorted based on their term field.
    3) We now have the min lexicographical term but the same term might appear in many files.
    If so, some of the other objects in the queue must correspond to the same term (think why). Therefore we
    keep removing objects from the queue until we get a different term. Each time an object is removed,
    we add to the queue a new object from the partial index with the same ID.
    4) All the objects that we removed from the queue in the previous step are added to a list which is sorted
    by the index ID. We then write the <index ID, DF> pairs from the list to the 'term_df' file in a single line.
    Note that the partial index ID will appear in increasing order.
    5) Since we have all objects for the min lexicographical term, we can sum their DF and get the final DF of
    the term.
    6) Determining the offset to the 'postings' file is done the same way as when there is only one index to merge.
    See the mergeVocabularies() function.
    7) Finally, we write to the final 'vocabulary' file a <term, DF, postings file offset> line for the min
    lexicographical term and we repeat the procedure until all partial 'vocabulary' files have been parsed.
    */
    private void combinePartialVocabularies(int indexID)
            throws IOException {

        /* open the partial 'vocabulary' file found in every 'index_tmp/index_id' folder */
        BufferedReader[] vocabularyReader = new BufferedReader[indexID + 1];
        for (int i = 0; i <= indexID; i++) {
            String partialVocabularyPath = getPartialVocabularyPath(i);
            vocabularyReader[i] = new BufferedReader(new InputStreamReader(new FileInputStream(partialVocabularyPath), "UTF-8"));
        }

        /* the final 'vocabulary' file will be saved in the 'index' folder */
        BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getVocabularyPath()), "UTF-8"));

        /* the 'term_df' file will be saved in the 'index_tmp' folder */
        BufferedWriter termDfWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(getTermDfPath()), "ASCII"));

        /* starting offset to the final 'postings' file */
        long postingsOffset = 0;

        /* Keep the min lexicographical term in a PartialVocabulary object. The object also has the DF of the term
        * and knows the partial index this term belongs to */
        PartialVocabulary minTermObj;

        /* Read the first line from each 'vocabulary' file and create PartialVocabulary objects. The min
        * lexicographical term will be in one or more of those objects since all files have their terms sorted.
        *
        * First, put the objects in a priority queue */
        PriorityQueue<PartialVocabulary> vocabularyQueue = new PriorityQueue<>();
        for (int i = 0; i <= indexID; i++) {
            minTermObj = getNextVocabularyEntry(vocabularyReader[i], i);
            if (minTermObj != null) {
                vocabularyQueue.add(minTermObj);
            }
        }

        /* put in equalTerms list all PartialVocabulary objects that correspond to the same min lexicographical term */
        List<PartialVocabulary> equalTerms = new ArrayList<>();

        /* the current min lexicographical term */
        String prevMinTerm = null;

        /* Get an object from the queue. Queue is sorted by the term name so we'll get one that has the
        * min lexicographical term */
        while((minTermObj = vocabularyQueue.poll()) != null) {

            /* if the current term is not equal to the previous term, we must process
            the equalTerms list and write the previous term to the final 'vocabulary' file. Also
            update the 'term_df' file */
            if (!minTermObj.get_term().equals(prevMinTerm) && !equalTerms.isEmpty()) {
                postingsOffset = dumpEqualTerms(equalTerms, vocabularyWriter, termDfWriter, postingsOffset);
            }

            /* save the current term for the next iteration */
            prevMinTerm = minTermObj.get_term();

            /* put the current object in the equalTerms list */
            equalTerms.add(minTermObj);

            /* finally add a new object to the queue. It should be created from the next line of the 'vocabulary'
            * file of the current object */
            int currIndexID = minTermObj.get_indexID();
            PartialVocabulary nextObj = getNextVocabularyEntry(vocabularyReader[currIndexID], currIndexID);
            if (nextObj != null) {
                vocabularyQueue.add(nextObj);
            }
        }

        /* all 'vocabulary' files have been parsed at this moment. Process the equalTerms list and
        and write the remaining term to the final vocabulary file */
        if (!equalTerms.isEmpty()) {
            dumpEqualTerms(equalTerms, vocabularyWriter, termDfWriter, postingsOffset);
        }

        /* close any open files */
        for (int i = 0; i <= indexID; i++) {
            vocabularyReader[i].close();
        }
        vocabularyWriter.close();
        termDfWriter.close();
    }

    /* Reads the next line from the 'vocabulary' file in the partial index with the specified ID and returns a new
    PartialVocabulary object */
    private PartialVocabulary getNextVocabularyEntry(BufferedReader vocabularyReader, int indexID)
            throws IOException {
        String line = vocabularyReader.readLine();
        if (line != null) {
            String[] fields = line.split(" ");
            return new PartialVocabulary(fields[0], Integer.parseInt(fields[1]), indexID);
        }
        return null;
    }

    /* Writes a new line in the final 'vocabulary' file (one line per term). A line has (in the following order):
    1) Term
    2) DF = document frequency of this term = in how many documents this term is found
    3) Postings offset = Offset to the 'postings' file

    -> The term is any term from the object list since all terms are the same.
    -> The DF is the sum of the DF from each object in the list.
    -> The specified postingsOffset has been returned by the previous call of this method and is equal to:
         (postings offset of prev term) + (DF of previous term)x(posting size)

    Also writes a sequence of <partial index ID, DF> for the term to the 'term_df' file. This file will be used
    during the merging of the partial 'postings' files. Note that line N in the 'vocabulary' file corresponds
    to line N in the 'term_df' file.

    The method also clears the specified equalTerms list.

    Returns an offset to the 'postings' file that will be used during the next call of the method */
    private long dumpEqualTerms(List<PartialVocabulary> equalTerms, BufferedWriter vocabularyWriter, BufferedWriter termDfWriter, long postingsOffset)
            throws IOException {
        int df = 0;

        /* sort the equalTerms list based on the partial index ID */
        equalTerms.sort(PartialVocabulary.idComparator);

        /* calculate final DF, also write in the 'term_df' the sequence of (partial index ID, DF) for current term */
        StringBuilder sb = new StringBuilder();
        for (PartialVocabulary equalTerm : equalTerms) {
            df += equalTerm.get_df();
            sb.append(equalTerm.get_indexID()).append(' ').append(equalTerm.get_df()).append(' ');
        }
        sb.append('\n');
        termDfWriter.write(sb.toString());

        /* write a new line in the final vocabulary file */
        vocabularyWriter.write(equalTerms.get(0).get_term() + ' ' + df + ' ' + postingsOffset + '\n');

        /* calculate the postings offset for the call of the method */
        postingsOffset +=  df * (long) Posting.totalSize;

        equalTerms.clear();
        return postingsOffset;
    }

    /* Merges the partial 'postings' files found in the 'index_tmp/*' folders where '*' is a
    number from 0 to indexID (inclusive). Creates the final 'postings' file (random access file)
    in the 'index' folder */
    private void mergePostings(int indexID)
            throws IOException {

        /* If there is only one partial index, move the posting file in the final 'index' folder */
        long startTime =  System.nanoTime();
        Themis.print(">>> Start Merging of partial postings\n");
        if (indexID == 0) {
            String partialPostingPath = getPartialPostingPath(0);
            Files.move(Paths.get(partialPostingPath), Paths.get(getPostingsPath()), StandardCopyOption.REPLACE_EXISTING);
        } else {
            combinePartialPostings(indexID);
        }
        Themis.print("Partial postings merged in " + new Time(System.nanoTime() - startTime) + "\n");
    }

    /* Merges the partial 'postings' files when there are more than 1 partial indexes.
    *
    * The merging is straightforward. The 'term_df' file is parsed one line at a time, each line consists of
    * a sequence of <partial index ID, DF>. Line N corresponds to line N in the 'vocabulary' file and for the term in
    * that line we can find:
    * 1) The partial index ID that contains the postings of the term.
    * 2) The size of the postings in the partial index ID = (DF)x(posting size)
    *
    * Each line in 'term_df' is parsed and all postings for a term are collected and written to the final
    * 'postings' file.
    *
    * Note that the partial index ID already appear in the 'term_df' file in increasing order. But this order also
    * tells us the document parsing order. So if (index ID1) < (index ID2), all postings for a term in partial
    * index ID1 will have document ID < document ID in partial index ID2. That means if we visit the partial
    * 'postings' file based on the order indicated in the 'term_df' line, the document ID in the final postings block
    * of the final 'postings' file will also appear sorted.
    * */
    private void combinePartialPostings(int indexID)
            throws IOException {

        /* open the partial 'postings' file found in every 'index_tmp/index_id' folder */
        BufferedInputStream[] postingsStream = new BufferedInputStream[indexID + 1];
        for (int i = 0; i <= indexID; i++) {
            String partialPostingPath = getPartialPostingPath(i);
            postingsStream[i] = new BufferedInputStream(new FileInputStream(new RandomAccessFile(partialPostingPath, "rw").getFD()));
        }

        /* the final 'postings' file will be saved in the 'index' folder */
        BufferedOutputStream postingsWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(getPostingsPath(), "rw").getFD()));

        /* the 'term_df' file that has a sequence of <partial index ID, DF> in each line (one line per term) */
        BufferedReader termDfReader = new BufferedReader(new InputStreamReader(new FileInputStream(getTermDfPath()), "ASCII"));

        /* parse each line of the file, grab the postings from the appropriate partial 'postings' file, and
        * write them to the final 'postings' file */
        String line;
        while ((line = termDfReader.readLine()) != null) {
            List<String> split = ProcessText.splitString(line);
            for (int i = 0; i < split.size(); i+=2) {
                byte[] postings = new byte[Integer.parseInt(split.get(i + 1)) * Posting.totalSize];
                postingsStream[Integer.parseInt(split.get(i))].read(postings);
                postingsWriter.write(postings);
            }
        }

        /* close any open files */
        for (BufferedInputStream bufferedInputStream : postingsStream) {
            bufferedInputStream.close();
        }
        postingsWriter.close();
        termDfReader.close();
    }

    /* Writes the necessary meta info for a document to the 'documents_meta' file (random access file).
     * The total size of the records is fixed for each document and equal to DocumentMetaEntry.totalSize
     *
     * This is a random access file. It stores in the following order:
     * 1) The int ID of the document (int => 4 bytes)
     * 2) The weight (norm) of the document (double => 8 bytes)
     * 3) The max TF in the document (int => 4 bytes)
     * 4) Length of document (int => 4 bytes). This is the number of document tokens in the 'documents' file.
     * 5) PageRank Score (double => 8 bytes)
     * 6) Average author rank (double => 8 bytes). Currently unused.
     * 7) Size of the document (int => 4 bytes). This is the size of the document in the 'documents' file.
     * 8) Offset to the 'documents' file (long => 8 bytes)
     *
     * PageRank, weight, max TF, average author rank are all initialized to 0
     * */
    private void dumpDocumentsMeta(BufferedOutputStream out, int intID, int documentTokens, int documentSize, long documentsOffset)
            throws IOException {
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.INTID_OFFSET, intID);
        __DOCUMENT_META_BUFFER__.putDouble(DocumentMetaEntry.VSM_WEIGHT_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.MAX_TF_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.TOKEN_COUNT_OFFSET, documentTokens);
        __DOCUMENT_META_BUFFER__.putDouble(DocumentMetaEntry.CITATIONS_PAGERANK_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putDouble(DocumentMetaEntry.AVG_AUTHOR_RANK_OFFSET, 0);
        __DOCUMENT_META_BUFFER__.putInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET, documentSize);
        __DOCUMENT_META_BUFFER__.putLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET, documentsOffset);
        __DOCUMENT_META_BUFFER__.position(0);
        out.write(__DOCUMENT_META_ARRAY__);
    }

    /* Writes the string doc ID of a document to the 'documents_id' file (random access file).
     *
     * doc ID (40 ASCII chars => 40 bytes)
     * */
    private void dumpDocumentsID(BufferedOutputStream out, S2TextualEntry textualEntry) throws IOException {
        out.write(textualEntry.getId().getBytes("ASCII"));
    }

    /* Writes the necessary info for a document to the 'documents' file (random access file)
     *
     * It stores in the following order:
     * 1) Year (short => 2 bytes)
     * 2) [Title] size (int => 4 bytes)
     * 3) [Author_1,Author_2, ...,Author_k] size (int => 4 bytes)
     * 4) [AuthorID_1, AuthorID_2, ... ,Author_ID_k] size (int => 4 bytes)
     * 5) [Journal name] size (short => 2 bytes / UTF-8)
     * 6) Title (variable bytes / UTF-8)
     * 7) Author_1,Author_2, ...,Author_k (variable bytes / UTF-8)
     * 8) AuthorID_1, AuthorID_2, ...,Author_ID_k (variable bytes / ASCII)
     * 9) Journal name (variable bytes / UTF-8)
     *
     * Authors are separated by a comma.
     * Authors ID are separated by a comma.
     * */
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

    /* Deletes the specified indexPath folder */
    private boolean deleteDir(File indexPath)
            throws IOException {
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

    /* Calculates the document weight (used by the Vector space model) and the max TF in each document
    and writes them to the 'documents_meta' file.

    To calculate the weight we need two things:
    1) The DF of each term in a document
    2) The TF of each term in a document

    -> The DF is easily obtained since we already have the final 'vocabulary' file finalized.
    -> The 'doc_tf' file contains a sequence of <term, TF> for every term in the document.

    This process takes place after the merging of the partial 'vocabulary' files and before the merging of the
    partial 'postings' files. This is done as to minimize the disk usage before merging the postings since the
    'doc_tf' file is already quite large.
    */
    private void updateVSMweights()
            throws IOException {
        long startTime = System.nanoTime();
        Themis.print(">>> Calculating VSM weights\n");

        /* load the vocabulary terms and the DF */
        BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(getVocabularyPath()), "UTF-8"));
        Map<String, Integer> vocabulary = new HashMap<>();
        String line;
        String[] split;
        while ((line = vocabularyReader.readLine()) != null) {
            split = line.split(" ");
            vocabulary.put(split[0], Integer.parseInt(split[1]));
        }
        vocabularyReader.close();

        /* open the required files: 'documents_meta' and 'doc_tf' */
        __DOCMETA_BUFFERS__ = new DocumentBuffers(getDocumentsMetaFilePath(), MemoryBuffers.MODE.WRITE, DocumentMetaEntry.totalSize);
        BufferedReader docTfReader = new BufferedReader(new InputStreamReader(new FileInputStream(getDocTfPath()), "UTF-8"));

        int totalArticles = Integer.parseInt(_INDEX_META__.get("documents"));
        double logArticles = Math.log(totalArticles);
        long offset = 0;

        /* read a line from the 'doc_tf' file and calculate the weight */
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

            /* update the 'documents_meta' file */
            ByteBuffer buffer = __DOCMETA_BUFFERS__.memBuffer(offset + DocumentMetaEntry.VSM_WEIGHT_OFFSET);
            buffer.putDouble(weight);
            buffer = __DOCMETA_BUFFERS__.memBuffer(offset + DocumentMetaEntry.MAX_TF_OFFSET);
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
     * Loads the index from the 'index' folder. The following take place:
     * 1) The 'vocabulary' file is loaded in memory
     * 2) The 'postings' and 'documents' file are opened
     * 3) The 'documents_id' and 'documents_meta' files are memory mapped
     * 4) The index configuration options from 'index_meta' file are loaded in memory
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public boolean load()
            throws IndexNotLoadedException {
        Themis.print(">>> Index path: " + __INDEX_PATH__ + "\n");
        try {
            /* load index configuration options */
            _INDEX_META__ = loadMeta();
            Themis.print("Stemming: " + _INDEX_META__.get("use_stemmer") + "\n");
            Themis.print("Stopwords: " + _INDEX_META__.get("use_stopwords") + "\n");
            Themis.print(">>> Loading index...");

            /* load 'vocabulary' file */
            __VOCABULARY__ = new HashMap<>();
            String line;
            String[] fields;

            BufferedReader vocabularyReader = new BufferedReader(new InputStreamReader(new FileInputStream(getVocabularyPath()), "UTF-8"));
            while ((line = vocabularyReader.readLine()) != null) {
                fields = line.split(" ");
                __VOCABULARY__.put(fields[0], new Vocabulary(Integer.parseInt(fields[1]), Long.parseLong(fields[2])));
            }
            vocabularyReader.close();

            /* open 'postings', 'documents' files */
            __POSTINGS__ = new RandomAccessFile(getPostingsPath(), "r");
            __DOCUMENTS__ = new RandomAccessFile(getDocumentsFilePath(), "r");

            /* memory map the 'documents_meta', 'documents_id' files */
            __DOCMETA_BUFFERS__ = new DocumentBuffers(getDocumentsMetaFilePath(), MemoryBuffers.MODE.READ, DocumentMetaEntry.totalSize);
            __DOCUMENT_META_ARRAY__ = new byte[DocumentMetaEntry.totalSize];
            __DOCUMENT_META_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_META_ARRAY__);
            __DOCID_BUFFERS__ = new DocumentBuffers(getDocumentsIDFilePath(), MemoryBuffers.MODE.READ, DocumentIDEntry.totalSize);
            __DOCUMENT_ID_ARRAY__ = new byte[DocumentIDEntry.totalSize];
            __DOCUMENT_ID_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_ID_ARRAY__);
        }
        catch (IOException e) {
            throw new IndexNotLoadedException();
        }
        Themis.print("DONE\n");

        return true;
    }

    /**
     * Loads the index configuration options from the 'index_meta' file in the 'index' folder
     *
     * @return
     * @throws IOException
     */
    public Map<String, String> loadMeta()
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
     * The following take place:
     * 1) The 'postings', 'documents', 'documents_id', 'documents_meta' files are closed and their
     *    references are nullified
     * 2) The references to the data from the 'vocabulary' and 'index_meta' files are nullified.
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
    }

    /**
     * Deletes the 'index' and 'index_tmp' folders.
     *
     * @throws IOException
     */
    public void deleteIndex()
            throws IOException {
        Themis.print(">>> Deleting previous index...");
        deleteDir(new File(getIndexPath()));
        deleteDir(new File(getIndexTmpPath()));
        Themis.print("DONE\n");
    }

    /**
     * Returns false iff the 'index' and 'index_tmp' directories are not empty, true otherwise.
     * @return
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
     * Returns the string doc ID of a document based on the specified int ID.
     *
     * @param intID
     * @return
     * @throws UnsupportedEncodingException
     * @throws IndexNotLoadedException
     */
    public String getDocID(int intID)
            throws UnsupportedEncodingException, IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        long docIdOffset = DocInfo.getDocIdOffset(intID);
        ByteBuffer buffer = __DOCID_BUFFERS__.memBuffer(docIdOffset);
        buffer.get(__DOCUMENT_ID_ARRAY__);

        return new String(__DOCUMENT_ID_ARRAY__, 0, DocumentIDEntry.DOCID_SIZE, "ASCII");
    }

    /**
     * Reads the 'documents' and 'documents_meta' files and adds the properties specified by props to each of
     * the DocInfo objects.
     *
     * Pre-condition:
     * This method will return the expected result only if the first DocInfo object has the same props as the
     * rest of the objects. This is for efficiency purposes.
     *
     * When this method returns, only the specified props will be present in every DocInfo object.
     *
     * @param props
     * @throws IOException
     * @throws IndexNotLoadedException
     */
    public void updateDocInfo(List<Result> results, Set<DocInfo.PROPERTY> props)
            throws IOException, IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        if (results.size() == 0) {
            return;
        }

        /* get the props of the first DocInfo */
        Set<DocInfo.PROPERTY> firstProps = results.get(0).getDocInfo().get_props();

        /* find the props that will be deleted from each Docinfo */
        Set<DocInfo.PROPERTY> delProps = new HashSet<>(firstProps);
        delProps.removeAll(props);

        /* find the props that will be added to each Docinfo */
        Set<DocInfo.PROPERTY> addProps = new HashSet<>(props);
        addProps.removeAll(firstProps);

        if (delProps.isEmpty() && addProps.isEmpty()) {
            return;
        }

        /* flags for each prop that may be added */
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

        /* flags for each prop that may be deleted */
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

            /* read the 'documents_meta' file (memory accessed) and add props */
            if (ADD_CITATIONS_PAGERANK || ADD_VSM_WEIGHT || ADD_MAX_TF || ADD_TOKEN_COUNT || ADD_AVG_AUTHOR_RANK || ADD_DOC_SIZE) {

                /* go to the required offset and read all document data at once.
                * The size of the data will be DocumentMetaEntry.totalSize */
                documentsMetaOffset = DocInfo.getMetaOffset(docInfo.get_id());
                ByteBuffer buffer = __DOCMETA_BUFFERS__.memBuffer(documentsMetaOffset);
                buffer.get(__DOCUMENT_META_ARRAY__);

                documentSize = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                documentsOffset = __DOCUMENT_META_BUFFER__.getLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET);

                /* now add the props */
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

            /* read the 'documents' file (disk accessed) and add props */
            if (ADD_TITLE || ADD_AUTHORS_NAMES || ADD_JOURNAL_NAME || ADD_AUTHORS_IDS || ADD_YEAR) {

                /* In case we haven't already read data from the 'documents_meta' file, we need to do it now
                * because some of them are required for fetching data from the 'documents' file */
                if (documentsMetaOffset == -1) {
                    documentsMetaOffset = DocInfo.getMetaOffset(docInfo.get_id());
                    ByteBuffer buffer = __DOCMETA_BUFFERS__.memBuffer(documentsMetaOffset);
                    buffer.get(__DOCUMENT_META_ARRAY__);
                    documentSize = __DOCUMENT_META_BUFFER__.getInt(DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                    documentsOffset = __DOCUMENT_META_BUFFER__.getLong(DocumentMetaEntry.DOCUMENT_OFFSET_OFFSET);
                }

                /* go to the required offset in 'documents' file and read all document data at once.
                 * The size of the data will be documentSize */
                byte[] __DOCUMENT_ARRAY__ = new byte[documentSize];
                ByteBuffer __DOCUMENT_BUFFER__ = ByteBuffer.wrap(__DOCUMENT_ARRAY__);
                __DOCUMENTS__.seek(documentsOffset);
                __DOCUMENTS__.readFully(__DOCUMENT_ARRAY__, 0, documentSize);

                /* now add the props */
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
     * Returns true iff the index has been loaded, false otherwise.
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
     * Returns an array of the document frequencies (DF) for the terms in the specified list
     *
     * @param query
     * @return
     * @throws IndexNotLoadedException
     */
    public int[] getDf(List<QueryTerm> query)
            throws IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        int[] dfs = new int[query.size()];
        Vocabulary vocabularyValue;
        for (int i = 0; i < query.size(); i++) {
            vocabularyValue = __VOCABULARY__.get(query.get(i).get_term());
            if (vocabularyValue != null) {
                dfs[i] = vocabularyValue.get_df();
            }
        }
        return dfs;
    }

    /**
     * Returns a Postings object that represents the postings of a term in the 'postings' file.
     * See class Postings.
     *
     * @param term
     * @return
     * @throws IOException
     * @throws IndexNotLoadedException
     */
    public Postings getPostings(String term)
            throws IOException, IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        Vocabulary vocabularyValue = __VOCABULARY__.get(term);
        if (vocabularyValue == null) {
            return new Postings(new int[0], new int[0]);
        }

        int df = vocabularyValue.get_df();
        __POSTINGS__.seek(vocabularyValue.get_postingsOffset());
        byte[] postings = new byte[df * Posting.totalSize];
        __POSTINGS__.readFully(postings);
        ByteBuffer bb = ByteBuffer.wrap(postings);
        int[] intIDs = new int[df];
        int[] tfs = new int[df];
        for (int i = 0; i < df; i++) {
            tfs[i] = bb.getInt();
            intIDs[i] = bb.getInt();
        }
        return new Postings(tfs, intIDs);
    }

    /**
     * Returns a VSMprops object that has all essential props required by the Vector space model.
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public VSMprops getVSMprops()
            throws IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        int documents = Integer.parseInt(_INDEX_META__.get("documents"));
        int[] maxTfs = new int[documents];
        double[] VSMweights = new double[documents];
        for (int i = 0; i < documents; i++) {
            long offset = DocInfo.getMetaOffset(i);
            ByteBuffer buffer = __DOCMETA_BUFFERS__.memBuffer(offset + DocumentMetaEntry.VSM_WEIGHT_OFFSET);
            VSMweights[i] = buffer.getDouble();
            buffer = __DOCMETA_BUFFERS__.memBuffer(offset + DocumentMetaEntry.MAX_TF_OFFSET);
            maxTfs[i] = buffer.getInt();
        }
        return new VSMprops(maxTfs, VSMweights);
    }

    /**
     * Returns a OKAPIprops object that has the essential props required by the Okapi model.
     *
     * @return
     */
    public OKAPIprops getOKAPIprops()
            throws IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        int documents = Integer.parseInt(_INDEX_META__.get("documents"));
        int[] tokenCount = new int[documents];
        for (int i = 0; i < documents; i++) {
            long offset = DocInfo.getMetaOffset(i) + DocumentMetaEntry.TOKEN_COUNT_OFFSET;
            ByteBuffer buffer = __DOCMETA_BUFFERS__.memBuffer(offset);
            tokenCount[i] = buffer.getInt();
        }
        return new OKAPIprops(tokenCount);
    }

    /**
     * Returns an array of the Pagerank scores of the documents.
     * @return
     * @throws IndexNotLoadedException
     */
    public double[] getPagerank()
            throws IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        int documents = Integer.parseInt(_INDEX_META__.get("documents"));
        double[] pagerank = new double[documents];
        for (int i = 0; i < documents; ++i) {
            long offset = DocInfo.getMetaOffset(i) + DocumentMetaEntry.CITATIONS_PAGERANK_OFFSET;
            ByteBuffer buffer = __DOCMETA_BUFFERS__.memBuffer(offset);
            pagerank[i] = buffer.getDouble();
        }
        return pagerank;
    }

    /**
     * Returns the total number of documents in the loaded index
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public int getTotalDocuments()
            throws IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        return Integer.parseInt(_INDEX_META__.get("documents"));
    }

    /**
     * Returns the avgdl of the loaded index, used by the Okapi retrieval model
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public double getAvgdl()
            throws IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        return Double.parseDouble(_INDEX_META__.get("avgdl"));
    }

    /**
     * Returns true iff stopwords are enabled for the loaded index, false otherwise
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public Boolean useStopwords()
            throws IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        return Boolean.parseBoolean(_INDEX_META__.get("use_stopwords"));
    }

    /**
     * Returns true iff stemming is enabled for the loaded index, false otherwise
     *
     * @return
     * @throws IndexNotLoadedException
     */
    public Boolean useStemmer()
            throws IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        return Boolean.parseBoolean(_INDEX_META__.get("use_stemmer"));
    }

    /**
     * Returns the timestamp of the loaded index
     * @return
     */
    public String getIndexTimestamp()
            throws IndexNotLoadedException {
        if (!loaded()) {
            throw new IndexNotLoadedException();
        }
        return _INDEX_META__.get("timestamp");
    }

    /**
     * Returns the general configuration options used by this Indexer
     *
     * @return
     */
    public Config getConfig() {
        return __CONFIG__;
    }

    /**
     * Returns the full path of the 'index' folder as specified in the general configuration options
     *
     * @return
     */
    public String getIndexPath() {
        return __CONFIG__.getIndexPath() + "/";
    }

    /**
     * Returns the full path of the 'dataset' folder as specified in the general configuration options
     *
     * @return
     */
    public String getDataSetPath() {
        return __CONFIG__.getDatasetPath() + "/";
    }

    /**
     * Returns the full path of the 'index_tmp' folder as specified in the general configuration options
     *
     * @return
     */
    public String getIndexTmpPath() {
        return __CONFIG__.getIndexTmpPath() + "/";
    }

    /**
     * Returns the full path of the 'vocabulary' file of the index as specified in the general configuration options.
     * This file is in the 'index' folder.
     *
     * @return
     */
    public String getVocabularyPath() {
        return __INDEX_PATH__ + "/" + __VOCABULARY_FILENAME__;
    }

    /**
     * Returns the full path of the 'postings' file of the index as specified in the general configuration options.
     * This file is in the 'index' folder.
     *
     * @return
     */
    public String getPostingsPath() {
        return __INDEX_PATH__ + "/" + __POSTINGS_FILENAME__;
    }

    /**
     * Returns the full path of the 'documents' file of the index as specified in the general configuration options.
     * This file is in the 'index' folder.
     *
     * @return
     */
    public String getDocumentsFilePath() {
        return __INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__;
    }

    /**
     * Returns the full path of the 'documents_meta' file of the index as specified in the general
     * configuration options.
     * This file is in the 'index' folder.
     *
     * @return
     */
    public String getDocumentsMetaFilePath() {
        return __INDEX_PATH__ + "/" + __DOCUMENTS_META_FILENAME__;
    }

    /**
     * Returns the full path of the 'documents_id' file of the index as specified in the general configuration options.
     * This file is in the 'index' folder.
     *
     * @return
     */
    public String getDocumentsIDFilePath() {
        return __INDEX_PATH__ + "/" + __DOCUMENTS_ID_FILENAME__;
    }

    /**
     * Returns the full path of the 'index_meta' file of the index as specified in the general configuration options.
     * This file is in the 'index' folder.
     *
     * @return
     */
    public String getMetaPath() {
        return __INDEX_PATH__ + "/" + __META_FILENAME__;
    }

    /* Returns the full path of the 'term_df' file. This file is in the 'index_tmp' folder */
    private String getTermDfPath() {
        return getIndexTmpPath() + "term_df";
    }

    /* Returns the full path of the 'doc_df' file. This file is in the 'index_tmp' folder */
    private String getDocTfPath() {
        return getIndexTmpPath() + "doc_tf";
    }

    /* Returns the full path of a partial index specified by the given id.
    * This folder should be the 'index_tmp/index' */
    private String getPartialIndexPath(int index) {
        return getIndexTmpPath() + index + "/";
    }

    /**
     * Returns the full path of the 'postings' file in the partial index specified by the given index.
     * This file has the same name as the 'postings' file specified in the general configuration options
     * and is in the 'index_tmp/index' folder.
     *
     * @param index
     * @return
     */
    public String getPartialPostingPath(int index) {
        return getPartialIndexPath(index) + __POSTINGS_FILENAME__;
    }

    /**
     * Returns the full path of the 'vocabulary' file in the partial index specified by the given index.
     * This file has the same name as the 'vocabulary' file specified in the general configuration options
     * and is in the 'index_tmp/index' folder.
     *
     * @param index
     * @return
     */
    public String getPartialVocabularyPath(int index) {
        return getPartialIndexPath(index) + __VOCABULARY_FILENAME__;
    }

    /**
     * Set the citations pagerank weight
     *
     * @param weight
     */
    public void set_pagerankWeight(double weight) {
        _pagerankWeight = weight;
    }

    /**
     * Get the citations pagerank weight
     *
     * @return
     */
    public double get_pagerankWeight() {
        return _pagerankWeight;
    }
}
