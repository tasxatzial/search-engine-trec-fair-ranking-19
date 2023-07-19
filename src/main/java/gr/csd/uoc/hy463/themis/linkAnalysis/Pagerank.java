package gr.csd.uoc.hy463.themis.linkAnalysis;

import gr.csd.uoc.hy463.themis.Exceptions.IncompleteFileException;
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
 * Class for calculating the Pagerank scores of the documents.
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
     * @throws IncompleteFileException
     */
    public Pagerank(Indexer indexer)
            throws IOException, IncompleteFileException {
        _indexer = indexer;
        __CITATIONS_GRAPH_PATH__ = _indexer.getConfig().getIndexPath() + "/graph";
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
     */
    public void documentsPagerank()
            throws IOException {
        long startTime = System.nanoTime();
        Themis.print("-> Constructing Pagerank graph...\n");
        dumpCitationsData();
        PagerankNode[] graph = initCitationsGraph();
        Themis.print("Graph created in " + new Time(System.nanoTime() - startTime) + '\n');
        startTime = System.nanoTime();
        Themis.print("-> Calculating Pagerank scores...\n");
        double[] scores = computeDocumentsPagerank(graph);
        Themis.print("Iterations completed in " + new Time(System.nanoTime() - startTime) + '\n');
        writeDocumentsScore(scores);
        Files.deleteIfExists(new File(__CITATIONS_GRAPH_PATH__).toPath());
    }

    /* Parses the collection and saves the graph data to 'INDEX_PATH/graph' (random access file).
    * Note: Only the data required for initializing the Pagerank graph are saved */
    private void dumpCitationsData()
            throws IOException {
        List<File> corpus = _indexer.getCorpus();
        if (corpus == null || corpus.size() == 0) {
            Themis.print("No dataset files found in " + _indexer.getDataSetPath() + "\n");
            return;
        }

        /* memory map the DOCUMENTS_ID_FILENAME */
        String documentsIDPath = _indexer.getDocumentsIDFilePath();
        DocumentFixedBuffers documentIDBuffers = new DocumentFixedBuffers(documentsIDPath, MemoryBuffers.MODE.READ, DocumentStringID.SIZE);
        byte[] docIDArray = new byte[DocumentStringID.SIZE];

        /* 'INDEX_PATH/graph' stores for each document the number of Out citations and the (int) IDs of the In citations.
        Each entry in the file consists of:
        1) (int) => size of the rest of the data in this entry
        2) (int) => number of Out citations
        3) (int[]) => [In citation1 ID, In citation2 ID, ...] */
        BufferedOutputStream graphWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(__CITATIONS_GRAPH_PATH__, "rw").getFD()));

        /* parse DOCUMENTS_ID_FILENAME and create a map of [(string) doc ID -> (int) doc ID] */
        Map<String, Integer> strToIntID = new HashMap<>();
        int documents = 0;
        long offset = 0;
        long maxOffset = DocumentStringID.SIZE * (long) _totalDocuments;
        ByteBuffer buffer;
        while (offset != maxOffset) {
            buffer = documentIDBuffers.getMemBuffer(offset);
            buffer.get(docIDArray);
            String strID = new String(docIDArray, 0, DocumentStringID.SIZE, "ASCII");
            strToIntID.put(strID, documents++);
            offset += DocumentStringID.SIZE;
        }
        documentIDBuffers.close();

        /* Parse the collection and write the required data to 'INDEX_PATH/graph' */
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

    /* Reads 'INDEX_PATH/graph' and initializes the Pagerank graph */
    private PagerankNode[] initCitationsGraph()
            throws IOException {
        BufferedInputStream graphReader = new BufferedInputStream(new FileInputStream(new RandomAccessFile(__CITATIONS_GRAPH_PATH__, "r").getFD()));
        PagerankNode[] graph = new PagerankNode[_totalDocuments];
        for (int i = 0; i < _totalDocuments; i++) {
            graph[i] = new PagerankNode();
        }

        byte[] entrySizeArr = new byte[4];
        ByteBuffer entrySizeBuf = ByteBuffer.wrap(entrySizeArr);

        /* parse 'INDEX_PATH/graph' and create the graph */
        for (int i = 0; i < _totalDocuments; i++) {
            PagerankNode currNode = graph[i];
            graphReader.read(entrySizeArr);
            int entrySize = entrySizeBuf.getInt(0);
            byte[] entryArr = new byte[entrySize];
            ByteBuffer entryBuf = ByteBuffer.wrap(entryArr);
            graphReader.read(entryArr);
            int numOutCitations = entryBuf.getInt(0);
            currNode.setOutNodes(numOutCitations);
            int numInCitations = entrySize / 4 - 1;
            currNode.initializeInNodes(numInCitations);
            for (int j = 0; j < numInCitations; j++) {
                int inCitation = entryBuf.getInt(4 * j + 4);
                currNode.getInNodes()[j] = graph[inCitation];
            }
        }
        graphReader.close();
        return graph;
    }

    /* Computes the Pagerank scores of the documents */
    private double[] computeDocumentsPagerank(PagerankNode[] graph) {
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
