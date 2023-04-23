package gr.csd.uoc.hy463.themis.linkAnalysis;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocumentBlockBuffers;
import gr.csd.uoc.hy463.themis.indexer.MemMap.MemoryBuffers;
import gr.csd.uoc.hy463.themis.indexer.model.DocumentStringID;
import gr.csd.uoc.hy463.themis.indexer.model.DocumentMetaEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.linkAnalysis.Exceptions.PagerankException;
import gr.csd.uoc.hy463.themis.utils.Time;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;

/**
 * Class for calculating the Pagerank scores of the documents.
 */
public class Pagerank {
    private final Indexer _indexer;
    private final int _totalDocuments;

    /* The full path of the Pagerank graph file */
    private final String __CITATIONS_GRAPH_PATH__;

    /**
     * Constructor.
     *
     * @param indexer
     * @throws IOException
     */
    public Pagerank(Indexer indexer)
            throws IOException {
        _indexer = indexer;
        String indexPath = _indexer.getConfig().getIndexPath();

        /* Save location of the graph file is 'INDEX_PATH/graph' */
        __CITATIONS_GRAPH_PATH__ = indexPath + "/graph";

        /* Read the total number of documents from INDEX_META_FILENAME */
        Map<String, String> __INDEX_META__ = indexer.loadIndexMeta();
        _totalDocuments = Integer.parseInt(__INDEX_META__.get("documents"));
    }

    /**
     * Parses the collection and:
     * 1) Writes to 'INDEX_PATH/graph' the necessary data for the Pagerank algorithm.
     * 2) Loads the graph and computes the scores of the documents.
     * 3) Writes the scores to DOCUMENTS_META_FILENAME.
     *
     * Requires both DOCUMENTS_META_FILENAME and DOCUMENTS_ID_FILENAME to be present.
     *
     * @throws PagerankException
     */
    public void documentsPagerank()
            throws PagerankException {
        try {
            long startTime = System.nanoTime();
            Themis.print("-> Constructing Pagerank graph...\n");
            dumpCitationsData();
            PagerankNode[] graph = initCitationsPagerankGraph();
            Themis.print("Graph created in " + new Time(System.nanoTime() - startTime) + '\n');
            startTime = System.nanoTime();
            Themis.print("-> Calculating Pagerank scores...\n");
            double[] scores = computeDocumentsPagerank(graph);
            Themis.print("Iterations completed in " + new Time(System.nanoTime() - startTime) + '\n');
            writeDocumentsScore(scores);
            Files.deleteIfExists(new File(__CITATIONS_GRAPH_PATH__).toPath());
        }
        catch (IOException e) {
            throw new PagerankException();
        }
    }

    /* Parses the collection and saves the graph data to 'INDEX_PATH/graph' (random access file).
    * Note: Only the data required for initializing the Pagerank graph are saved */
    private void dumpCitationsData()
            throws IOException {
        File folder = new File(_indexer.getDataSetPath());
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        /* memory map the DOCUMENTS_ID_FILENAME */
        String documentsIDPath = _indexer.getDocumentsIDFilePath();
        DocumentBlockBuffers documentIDBuffers = new DocumentBlockBuffers(documentsIDPath, MemoryBuffers.MODE.READ, DocumentStringID.SIZE);
        byte[] docIDArray = new byte[DocumentStringID.SIZE];

        /* 'INDEX_PATH/graph' stores for each document the number of Out citations and the (int) IDs of the In citations.
        Each entry in the file consists of:
        1) size (int) => size of the rest of the data in this entry
        2) number (int) of Out citations
        3) [In citation1 ID, In citation2 ID, ...]  (int[]) */
        BufferedOutputStream graphWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(__CITATIONS_GRAPH_PATH__, "rw").getFD()));

        /* sort the files of the collection. This is necessary so that the N-th parsed document is the one with
        ID = (N-1) since the same ordering was used to generate the (int) ID of each document during indexing */
        List<File> corpus = new ArrayList<>(files.length);
        corpus.addAll(Arrays.asList(files));
        Collections.sort(corpus);

        /* parse DOCUMENTS_ID_FILENAME and create a map of [(string) doc ID -> (int) doc ID].
        We'll use the map to get the (int) ID of a citation, this will save a lot of space/time later on */
        Map<String, Integer> docIDMap = new HashMap<>();
        int documents = 0;
        long offset = 0;
        long maxOffset = DocumentStringID.SIZE * (long) _totalDocuments;
        ByteBuffer buffer;
        while (offset != maxOffset) {
            buffer = documentIDBuffers.getMemBuffer(offset);
            buffer.get(docIDArray);
            String stringId = new String(docIDArray, 0, DocumentStringID.SIZE, "ASCII");
            docIDMap.put(stringId, documents++);
            offset += DocumentStringID.SIZE;
        }
        documentIDBuffers.close();

        /* Finally, parse the collection and write the required data to 'INDEX_PATH/graph' */
        for (File file : corpus) {
            if (file.isFile()) {
                Themis.print("Parsing file: " + file + "\n");
                BufferedReader currentDataFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String json;
                while ((json = currentDataFile.readLine()) != null) {
                    S2TextualEntry entry = S2JsonEntryReader.readCitationsEntry(json);

                    /* count out citations */
                    List<String> outCitations = entry.getOutCitations();
                    int numOutCitations = 0;
                    for (int i = 0; i < outCitations.size(); i++) {
                        if (!skipCitation(docIDMap, outCitations, i, entry.getID(), outCitations.get(i))) {
                            numOutCitations++;
                        }
                    }

                    /* count in citations */
                    List<String> inCitations = entry.getInCitations();
                    int numInCitations = 0;
                    for (int i = 0; i <inCitations.size(); i++) {
                        if (!skipCitation(docIDMap, inCitations, i, entry.getID(), inCitations.get(i))) {
                            numInCitations++;
                        }
                    }

                    /* dump data to disk */
                    byte[] citationData = new byte[4 * (2 + numInCitations)];
                    ByteBuffer citationDataBuf = ByteBuffer.wrap(citationData);
                    citationDataBuf.putInt(0, 4 * (1 + numInCitations));
                    citationDataBuf.putInt(4, numOutCitations);
                    int k = 0;
                    for (int i = 0; i <inCitations.size(); i++) {
                        if (!skipCitation(docIDMap, inCitations, i, entry.getID(), inCitations.get(i))) {
                            Integer citation = docIDMap.get(inCitations.get(i));
                            citationDataBuf.putInt(4 * (2 + k), citation);
                            k++;
                        }
                    }
                    graphWriter.write(citationData);
                }
                currentDataFile.close();
            }
        }
        graphWriter.close();
    }

    /* Returns true if the citation should not be added to the specified list of citations, false otherwise.
    This can happen when:
    1) citation does not exist
    2) citation is referencing itself
    3) citation is already in the list of citations
    */
    private boolean skipCitation(Map<String, Integer> citationsIDMap, List<String> citations, int maxSearchIndex, String docID, String citation) {

        /* skip if citation does not exist */
        if (citationsIDMap.get(citation) == null) {
            return true;
        }

        /* skip if citation is referencing itself */
        if (citation.equals(docID)) {
            return true;
        }

        /* skip if citation is already in the list of citations */
        boolean found = false;
        for (int j = 0; j < maxSearchIndex; j++) {
            if (citation.equals(citations.get(j))) {
                found = true;
                break;
            }
        }
        return found;
    }

    /* reads 'INDEX_PATH/graph' and initializes the Pagerank graph */
    private PagerankNode[] initCitationsPagerankGraph()
            throws IOException {
        BufferedInputStream graphReader = new BufferedInputStream(new FileInputStream(new RandomAccessFile(__CITATIONS_GRAPH_PATH__, "r").getFD()));
        PagerankNode[] graph = new PagerankNode[_totalDocuments];

        /* initialize the graph as an array of nodes */
        for (int i = 0; i < _totalDocuments; i++) {
            graph[i] = new PagerankNode();
        }

        byte[] fileEntrySize = new byte[4];
        ByteBuffer fileEntrySizeBuf = ByteBuffer.wrap(fileEntrySize);

        /* parse 'INDEX_PATH/graph' and create the graph */
        for (int i = 0; i < _totalDocuments; i++) {
            PagerankNode currNode = graph[i];
            graphReader.read(fileEntrySize);
            int entrySize = fileEntrySizeBuf.getInt(0);
            byte[] fileEntryData = new byte[entrySize];
            ByteBuffer fileEntryDataBuf = ByteBuffer.wrap(fileEntryData);
            graphReader.read(fileEntryData);
            int numOutCitations = fileEntryDataBuf.getInt(0);
            currNode.set_outNodes(numOutCitations);
            int numInCitations = entrySize / 4 - 1;
            currNode.initializeInNodes(numInCitations);
            for (int j = 0; j < numInCitations; j++) {
                int inCitation = fileEntryDataBuf.getInt(4 * j + 4);
                currNode.get_inNodes()[j] = graph[inCitation];
            }
        }
        graphReader.close();
        return graph;
    }

    /* computes the Pagerank scores of the documents */
    private double[] computeDocumentsPagerank(PagerankNode[] graph) {
        double threshold = _indexer.getConfig().getPagerankThreshold();
        double dampingFactor = _indexer.getConfig().getPagerankDampingFactor();
        double teleportScore = (1 - dampingFactor) / graph.length;
        double[] scores_tmp = new double[graph.length];

        /* initialize scores */
        for (PagerankNode node : graph) {
            node.set_score(1.0 / graph.length);
        }

        boolean maybeConverged = false;
        int iteration = 1;
        while (!maybeConverged) {
            if (iteration != 1 && iteration % 10 == 1) {
                Themis.print("\n");
            }
            Themis.print(iteration + " ");

            /* collect the scores from all sink nodes, these should be distributed evenly to all nodes */
            double sinksScore = 0;
            for (int i = 0; i < graph.length; i++) {
                if (graph[i].get_outNodes() == 0) {
                    sinksScore += graph[i].get_score();
                }
            }
            sinksScore /= graph.length;

            /* iterate over all nodes */
            for (int j = 0; j < graph.length; j++) {
                PagerankNode node = graph[j];

                /* initialize current node score to the score from the sinks */
                double score = sinksScore;

                /* add to the score the contributions of the In nodes of the current node */
                PagerankNode[] inNodes = node.get_inNodes();
                for (int k = 0; k < inNodes.length; k++) {
                    score += inNodes[k].get_score() / inNodes[k].get_outNodes();
                }
                
                scores_tmp[j] = score * dampingFactor + teleportScore;
            }

            /* check for convergence */
            maybeConverged = true;
            for (int j = 0; j < graph.length; j++) {
                PagerankNode node = graph[j];
                if (maybeConverged && Math.abs(scores_tmp[j] - node.get_score()) > threshold) {
                    maybeConverged = false;
                }
                node.set_score(scores_tmp[j]);
            }

            iteration++;
        }
        Themis.print("\n");

        /* write the final scores to the tmp score array, this means that the graph can now be garbage collected */
        for (int i = 0; i < graph.length; i++) {
            scores_tmp[i] = graph[i].get_score();
        }

        return scores_tmp;
    }

    /* writes the Pagerank scores to DOCUMENTS_META_FILENAME */
    private void writeDocumentsScore(double[] scores)
            throws IOException {
        long offset = 0;
        String documentsMetaPath = _indexer.getDocumentsMetaFilePath();
        DocumentBlockBuffers documentMetaBuffers = new DocumentBlockBuffers(documentsMetaPath, MemoryBuffers.MODE.WRITE, DocumentMetaEntry.SIZE);
        for (int i = 0; i < scores.length; i++) {
            ByteBuffer buffer = documentMetaBuffers.getMemBuffer(offset + DocumentMetaEntry.DOCUMENT_PAGERANK_OFFSET);
            buffer.putDouble(scores[i]);
            offset += DocumentMetaEntry.SIZE;
        }
        documentMetaBuffers.close();
    }
}
