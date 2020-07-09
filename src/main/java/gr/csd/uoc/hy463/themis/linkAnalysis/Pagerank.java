package gr.csd.uoc.hy463.themis.linkAnalysis;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocumentMetaBuffers;
import gr.csd.uoc.hy463.themis.indexer.model.DocumentMetaEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.linkAnalysis.graph.PagerankNode;
import gr.csd.uoc.hy463.themis.utils.Time;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;

public class Pagerank {
    private static final Logger __LOGGER__ = LogManager.getLogger(Indexer.class);
    private Indexer _indexer;

    public Pagerank(Indexer indexer) {
        _indexer = indexer;
    }

    /**
     * Computes the pagerank scores based on the citations
     * @throws IOException
     */
    public void citationsPagerank() throws IOException {
        Themis.print(">>> Calculating Pagerank\n");
        if (!_indexer.hasIndex()) {
            __LOGGER__.error("Index is not constructed correctly!");
            Themis.print("Index is not constructed correctly!\n");
            return;
        }
        long startTime = System.nanoTime();
        Themis.print("> Constructing graph\n");
        String graphFileName = _indexer.getConfig().getIndexPath() + "/graph";
        int documents = dumpCitations(graphFileName);
        PagerankNode[] graph = initCitationsGraph(documents, graphFileName);
        Themis.print("Graph created in " + new Time(System.nanoTime() - startTime) + '\n');
        startTime = System.nanoTime();
        Themis.print("> Iterating\n");
        double[] scores = computeCitationsPagerank(graph);
        Themis.print("Iterations completed in " + new Time(System.nanoTime() - startTime) + '\n');
        writeCitationsScores(scores);
        Files.deleteIfExists(new File(graphFileName).toPath());
    }

    /* Creates a temp file 'graph' in the Index directory. Entry N of this file corresponds to the Nth document
    that was parsed and it contains the number of its Out citations followed by a list of integer Ids that correspond to
    the Ids of its In citations. A document that has Id N in this file corresponds to entry N (starting from 0).
    Returns the total number of citations */
    private int dumpCitations(String graphFileName) throws IOException {
        File folder = new File(_indexer.getConfig().getDatasetPath());
        File[] files = folder.listFiles();
        if (files == null) {
            return 0;
        }

        String documentsMetaPath = _indexer.getConfig().getIndexPath() + "/" + _indexer.getConfig().getDocumentsMetaFileName();
        DocumentMetaBuffers documentMetaBuffers = new DocumentMetaBuffers(documentsMetaPath, DocumentMetaBuffers.MODE.READ);
        byte[] docIdArray = new byte[DocumentMetaEntry.ID_SIZE];

        /* This is a temporary file that stores for each document the number of Out citations
        and the Ids of the In citations. Each entry in the file consists of:
        size (int) -> this is the size of the rest of the data in this entry |
        number of Out citations (int) |
        In citation Id 1 (int) | in citation Id 2 (int) ... */
        BufferedOutputStream graphWriter = new BufferedOutputStream(new FileOutputStream
                (new RandomAccessFile(graphFileName, "rw").getFD()));

        // sort the files so that we parse them in a specific order
        List<File> corpus = new ArrayList<>(files.length);
        corpus.addAll(Arrays.asList(files));
        Collections.sort(corpus);

        // document string Id -> int Id
        Map<String, Integer> citationsIdsMap = new HashMap<>();

        /* read the documents_meta file and create the map of string id -> int id */
        int documents = 0;
        long offset = 0;
        ByteBuffer buffer;
        while ((buffer = documentMetaBuffers.getBufferLong(offset)) != null) {
            buffer.get(docIdArray);
            String stringId = new String(docIdArray, 0, DocumentMetaEntry.ID_SIZE, "ASCII");
            citationsIdsMap.put(stringId, documents);
            documents++;
            offset += DocumentMetaEntry.totalSize;
        }

        /* parse the dataset and write the required data to the 'graph' file */
        for (File file : corpus) {
            if (file.isFile()) {
                Themis.print("Parsing file: " + file + "\n");
                BufferedReader currentDataFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String json;
                while ((json = currentDataFile.readLine()) != null) {
                    S2TextualEntry entry = S2JsonEntryReader.readCitationsEntry(json);

                    //count out citations
                    List<String> outCitations = entry.getOutCitations();
                    int numOutCitations = 0;
                    for (int i = 0; i < outCitations.size(); i++) {
                        if (!skipCitation(citationsIdsMap, outCitations, entry.getId(), outCitations.get(i), i)) {
                            numOutCitations++;
                        }
                    }

                    //count in citations
                    List<String> inCitations = entry.getInCitations();
                    int numInCitations = 0;
                    for (int i = 0; i <inCitations.size(); i++) {
                        if (!skipCitation(citationsIdsMap, inCitations, entry.getId(), inCitations.get(i), i)) {
                            numInCitations++;
                        }
                    }

                    //dump citations to file
                    byte[] citationData = new byte[4 * (2 + numInCitations)];
                    ByteBuffer citationDataBuf = ByteBuffer.wrap(citationData);
                    citationDataBuf.putInt(0, 4 * (1 + numInCitations));
                    citationDataBuf.putInt(4, numOutCitations);
                    int k = 0;
                    for (int i = 0; i <inCitations.size(); i++) {
                        if (!skipCitation(citationsIdsMap, inCitations, entry.getId(), inCitations.get(i), i)) {
                            Integer citation = citationsIdsMap.get(inCitations.get(i));
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
        documentMetaBuffers.close();
        documentMetaBuffers = null;

        return documents;
    }

    /* Returns true iff the citation_i should not be added to the list of citations. This can happen when:
    1) citation_i does not exist
    2) citation_i referencing itself
    3) citation_i is already in the list of citations
     */
    private boolean skipCitation(Map<String, Integer> citationsIds, List<String> citations, String docId, String citation_i, int citation_idx) {
        if (citationsIds.get(citation_i) == null) { //skip citation if the document does not exist
            return true;
        }
        if (citation_i.equals(docId)) { //skip citation if the document is referencing itself
            return true;
        }
        boolean found = false;
        for (int j = 0; j < citation_idx; j++) {
            if (citation_i.equals(citations.get(j))) {
                found = true;
                break;
            }
        }
        return found; //skip citation if we've already taken this citation into account
    }

    /* initialize the citations pagerank graph and its nodes */
    private PagerankNode[] initCitationsGraph(int documents, String graphFileName) throws IOException {
        BufferedInputStream graphReader = new BufferedInputStream(new FileInputStream
                (new RandomAccessFile(graphFileName, "r").getFD()));
        PagerankNode[] graph = new PagerankNode[documents];

        /* Create the graph -> a list of nodes */
        for (int i = 0; i < documents; i++) {
            graph[i] = new PagerankNode();
        }

        byte[] num = new byte[4];
        ByteBuffer numBuf = ByteBuffer.wrap(num);

        /* read the 'graph' file and update the In citations of each node of the graph */
        for (int i = 0; i < documents; i++) {
            PagerankNode node = graph[i];
            graphReader.read(num);
            int size = numBuf.getInt(0);
            byte[] citationData = new byte[size];
            ByteBuffer citationDataBuf = ByteBuffer.wrap(citationData);
            graphReader.read(citationData);
            int outCitationsNum = citationDataBuf.getInt(0);
            node.setOutNodes(outCitationsNum);
            int inCitationsNum = size / 4 - 1;
            node.initializeInNodes(inCitationsNum);
            for (int j = 0; j < inCitationsNum; j++) {
                int inCitation = citationDataBuf.getInt(4 * j + 4);
                node.getInNodes()[j] = graph[inCitation];
            }
        }
        graphReader.close();
        return graph;
    }

    /* computes the citations pagerank scores */
    private double[] computeCitationsPagerank(PagerankNode[] graph) {
        double threshold = _indexer.getConfig().getPagerankThreshold();
        double dampingFactor = _indexer.getConfig().getPagerankDampingFactor();
        double teleportScore = (1 - dampingFactor) / graph.length;
        double[] scores_tmp = new double[graph.length];

        // initialize scores
        for (PagerankNode node : graph) {
            node.setScore(1.0 / graph.length);
        }

        boolean maybeConverged = false;
        int iteration = 1;
        while (!maybeConverged) {
            if (iteration != 1 && iteration % 20 == 1) {
                Themis.print("\n");
            }
            Themis.print(iteration + " ");

            /* collect the scores from all sink nodes, these should be distributed to every other node */
            double sinksScore = 0;
            for (int i = 0; i < graph.length; i++) {
                if (graph[i].getOutNodes() == 0) {
                    sinksScore += graph[i].getScore();
                }
            }
            sinksScore /= graph.length;

            /* iterate over all nodes */
            for (int j = 0; j < graph.length; j++) {
                PagerankNode node = graph[j];

                /* initialize current node score to the average score of sinks */
                double score = sinksScore;

                /* we also need to add to the score the contributions of the In nodes of the current node */
                PagerankNode[] inNodes = node.getInNodes();
                for (int k = 0; k < inNodes.length; k++) {
                    score += inNodes[k].getScore() / inNodes[k].getOutNodes();
                }
                
                scores_tmp[j] = score * dampingFactor + teleportScore;
            }

            // check for convergence
            maybeConverged = true;
            for (int j = 0; j < graph.length; j++) {
                PagerankNode node = graph[j];
                if (maybeConverged && Math.abs(scores_tmp[j] - node.getScore()) > threshold) {
                    maybeConverged = false;
                }
                node.setScore(scores_tmp[j]);
            }

            iteration++;
        }
        Themis.print("\n");

        //write the final scores to the tmp file, this means that the graph can be garbage collected
        for (int i = 0; i < graph.length; i++) {
            scores_tmp[i] = graph[i].getScore();
        }

        return scores_tmp;
    }

    /* writes the citation scores to the documents_meta file */
    private void writeCitationsScores(double[] scores) throws IOException {
        long offset = 0;
        String documentsMetaPath = _indexer.getConfig().getIndexPath() + "/" + _indexer.getConfig().getDocumentsMetaFileName();
        DocumentMetaBuffers documentMetaBuffers = new DocumentMetaBuffers(documentsMetaPath, DocumentMetaBuffers.MODE.WRITE);

        for (int i = 0; i < scores.length; i++) {
            ByteBuffer buffer = documentMetaBuffers.getBufferLong(offset + DocumentMetaEntry.PAGERANK_OFFSET);
            buffer.putDouble(scores[i]);
            offset += DocumentMetaEntry.totalSize;
        }
        documentMetaBuffers.close();
        documentMetaBuffers = null;
    }
}
