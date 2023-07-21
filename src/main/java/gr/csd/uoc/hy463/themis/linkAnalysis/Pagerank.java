package gr.csd.uoc.hy463.themis.linkAnalysis;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocumentFixedBuffers;
import gr.csd.uoc.hy463.themis.indexer.MemMap.MemoryBuffers;
import gr.csd.uoc.hy463.themis.indexer.model.DocumentStringID;
import gr.csd.uoc.hy463.themis.indexer.model.DocumentMetaEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.utils.Time;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;

/**
 * Class for calculating the Pagerank scores of the citations.
 */
public class Pagerank {
    private final Indexer _indexer;
    private final int _totalDocuments;
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
        __CITATIONS_GRAPH_PATH__ = _indexer.getConfig().getIndexDir() + "graph";
        _totalDocuments = _indexer.getTotalDocuments();
    }

    /**
     * Parses the collection and:
     * 1) Writes to 'INDEX_DIR/graph' the necessary data for the Pagerank algorithm.
     * 2) Loads the graph and computes the scores of the citations.
     * 3) Writes the scores to DOCUMENTS_META_FILENAME.
     *
     * Requires both DOCUMENTS_META_FILENAME and DOCUMENTS_ID_FILENAME to be present.
     */
    public void citationsPagerank()
            throws IOException {
        long startTime = System.nanoTime();
        Themis.print("-> Constructing Pagerank graph of the citations...\n");
        dumpCitationsData();
        PagerankNode[] graph = initCitationsGraph();
        Themis.print("Graph created in " + new Time(System.nanoTime() - startTime) + '\n');
        startTime = System.nanoTime();
        Themis.print("-> Calculating Pagerank scores...\n");
        double[] scores = computeCitationsPagerank(graph);
        Themis.print("Iterations completed in " + new Time(System.nanoTime() - startTime) + '\n');
        writeDocumentsScore(scores);
        Files.deleteIfExists(new File(__CITATIONS_GRAPH_PATH__).toPath());
    }

    /* Parses the collection and saves the graph data to 'INDEX_DIR/graph' (random access file).
    For each document it stores:
    1) (int) => size of the rest of the data in this entry
    2) (int) => number of Out citations
    3) (int[]) => [In citation1 ID, In citation2 ID, ...]
    Note: Only the data required for initializing the Pagerank graph are saved */
    private void dumpCitationsData()
            throws IOException {
        /* get the list of files in the collection */
        List<File> corpus = _indexer.getCorpus();
        if (corpus == null || corpus.size() == 0) {
            Themis.print("No dataset files found in " + _indexer.getConfig().getDatasetDir() + "\n");
            return;
        }

        Map<String, Integer> strToIntID = stringIDMap();
        BufferedOutputStream graphWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(__CITATIONS_GRAPH_PATH__, "rw").getFD()));

        /* parse the collection and write the required data to 'INDEX_DIR/graph' */
        for (File corpusFile : corpus) {
            Themis.print("Parsing file: " + corpusFile + "\n");
            BufferedReader corpusReader = new BufferedReader(new InputStreamReader(new FileInputStream(corpusFile), "UTF-8"));
            String json;
            while ((json = corpusReader.readLine()) != null) {
                S2TextualEntry entry = S2JsonEntryReader.readCitationsEntry(json);
                if (entry.getID() == null) {
                    continue;
                }

                /* count out citations */
                List<String> outCitations = entry.getOutCitations();
                int numOutCitations = 0;
                for (int i = 0; i < outCitations.size(); i++) {
                    if (!skipCitation(i, outCitations, strToIntID, entry.getID())) {
                        numOutCitations++;
                    }
                }

                /* count in citations */
                List<String> inCitations = entry.getInCitations();
                int numInCitations = 0;
                for (int i = 0; i <inCitations.size(); i++) {
                    if (!skipCitation(i, inCitations, strToIntID, entry.getID())) {
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
                    if (!skipCitation(i, inCitations, strToIntID, entry.getID())) {
                        Integer citationID = strToIntID.get(inCitations.get(i));
                        citationDataBuf.putInt(4 * (2 + k), citationID);
                        k++;
                    }
                }
                graphWriter.write(citationData);
            }
            corpusReader.close();
        }
        graphWriter.close();
    }

    /* Memory maps DOCUMENTS_ID_FILENAME and returns a map of [(string) doc ID -> (int) doc ID] */
    private Map<String, Integer> stringIDMap()
            throws IOException {
        /* memory map DOCUMENTS_ID_FILENAME */
        String documentsIDPath = _indexer.getDocumentsIDFilePath();
        DocumentFixedBuffers docIDBuffers = new DocumentFixedBuffers(documentsIDPath, MemoryBuffers.MODE.READ, DocumentStringID.SIZE);
        byte[] docIDArr = new byte[DocumentStringID.SIZE];

        /* parse DOCUMENTS_ID_FILENAME */
        Map<String, Integer> stringIDMap = new HashMap<>();
        int intID = 0;
        long offset = 0;
        long maxOffset = DocumentStringID.SIZE * (long) _totalDocuments;
        while (offset != maxOffset) {
            ByteBuffer buffer = docIDBuffers.getMemBuffer(offset);
            buffer.get(docIDArr);
            String stringID = new String(docIDArr, 0, DocumentStringID.SIZE, "ASCII");
            stringIDMap.put(stringID, intID);
            offset += DocumentStringID.SIZE;
            intID++;
        }

        docIDBuffers.close();
        return stringIDMap;
    }

    /* Returns true if the citation should not be added to the specified list of citations, false otherwise. */
    private boolean skipCitation(int citationIdx, List<String> citations, Map<String, Integer> strToIntID, String docID) {
        String citationID = citations.get(citationIdx);

        /* skip if citation does not exist */
        if (strToIntID.get(citationID) == null) {
            return true;
        }

        /* skip if citation is referencing itself */
        if (citationID.equals(docID)) {
            return true;
        }

        /* skip if citation is already in the list of citations */
        boolean found = false;
        for (int j = 0; j < citationIdx; j++) {
            if (citationID.equals(citations.get(j))) {
                found = true;
                break;
            }
        }
        return found;
    }

    /* Reads 'INDEX_DIR/graph' and initializes the Pagerank graph */
    private PagerankNode[] initCitationsGraph()
            throws IOException {
        BufferedInputStream graphReader = new BufferedInputStream(new FileInputStream(new RandomAccessFile(__CITATIONS_GRAPH_PATH__, "r").getFD()));
        PagerankNode[] graph = new PagerankNode[_totalDocuments];
        for (int i = 0; i < _totalDocuments; i++) {
            graph[i] = new PagerankNode();
        }

        byte[] entrySizeArr = new byte[4];
        ByteBuffer entrySizeBuf = ByteBuffer.wrap(entrySizeArr);

        /* parse 'INDEX_DIR/graph' and create the graph */
        for (int i = 0; i < _totalDocuments; i++) {
            PagerankNode node = graph[i];
            graphReader.read(entrySizeArr);
            int entrySize = entrySizeBuf.getInt(0);
            byte[] entryArr = new byte[entrySize];
            ByteBuffer entryBuf = ByteBuffer.wrap(entryArr);
            graphReader.read(entryArr);
            int numOutCitations = entryBuf.getInt(0);
            node.setOutNodes(numOutCitations);
            int numInCitations = entrySize / 4 - 1;
            node.initializeInNodes(numInCitations);
            for (int j = 0; j < numInCitations; j++) {
                int inCitation = entryBuf.getInt(4 * j + 4);
                node.getInNodes()[j] = graph[inCitation];
            }
        }
        graphReader.close();
        return graph;
    }

    /* Computes the Pagerank scores */
    private double[] computeCitationsPagerank(PagerankNode[] graph) {
        double threshold = _indexer.getConfig().getPagerankThreshold();
        double dampingFactor = _indexer.getConfig().getPagerankDampingFactor();
        double teleportScore = (1 - dampingFactor) / graph.length;
        double[] scores = new double[graph.length];

        /* initialize scores */
        for (PagerankNode node : graph) {
            node.setScore(1.0 / graph.length);
        }

        boolean maybeConverged = false;
        int iteration = 1;
        while (!maybeConverged) {
            if (iteration != 1 && iteration % 10 == 1) {
                Themis.print("\n");
            }
            Themis.print(iteration + " ");

            /* collect the scores from all sink nodes, these should be distributed evenly to all nodes */
            double totalSinkScore = 0;
            for (int i = 0; i < graph.length; i++) {
                if (graph[i].getOutNodes() == 0) {
                    totalSinkScore += graph[i].getScore();
                }
            }

            /* iterate over all nodes */
            for (int j = 0; j < graph.length; j++) {
                PagerankNode node = graph[j];

                /* initialize current node score to the score from the sinks */
                double nodeScore = totalSinkScore / graph.length;

                /* add to the score the contributions of the In nodes of the current node */
                PagerankNode[] inNodes = node.getInNodes();
                for (int k = 0; k < inNodes.length; k++) {
                    nodeScore += inNodes[k].getScore() / inNodes[k].getOutNodes();
                }
                
                scores[j] = nodeScore * dampingFactor + teleportScore;
            }

            /* check for convergence */
            maybeConverged = true;
            for (int j = 0; j < graph.length; j++) {
                PagerankNode node = graph[j];
                if (maybeConverged && Math.abs(scores[j] - node.getScore()) > threshold) {
                    maybeConverged = false;
                }
                node.setScore(scores[j]);
            }

            iteration++;
        }
        Themis.print("\n");

        /* write the final scores to the tmp score array */
        for (int i = 0; i < graph.length; i++) {
            scores[i] = graph[i].getScore();
        }

        return scores;
    }

    /* writes the Pagerank scores to DOCUMENTS_META_FILENAME */
    private void writeDocumentsScore(double[] scores)
            throws IOException {
        long offset = 0;
        String documentsMetaPath = _indexer.getDocumentsMetaFilePath();
        DocumentFixedBuffers documentMetaBuffers = new DocumentFixedBuffers(documentsMetaPath, MemoryBuffers.MODE.WRITE, DocumentMetaEntry.SIZE);
        for (int i = 0; i < scores.length; i++) {
            ByteBuffer buffer = documentMetaBuffers.getMemBuffer(offset + DocumentMetaEntry.DOCUMENT_PAGERANK_OFFSET);
            buffer.putDouble(scores[i]);
            offset += DocumentMetaEntry.SIZE;
        }
        documentMetaBuffers.close();
    }
}
