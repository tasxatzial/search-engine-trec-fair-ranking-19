package gr.csd.uoc.hy463.themis.linkAnalysis;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocumentMetaBuffers;
import gr.csd.uoc.hy463.themis.indexer.model.DocumentMetaEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2CitationsGraphEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.linkAnalysis.graph.PagerankNode;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;

public class Pagerank {
    private String __DATASET_PATH__;
    private String __INDEX_PATH__;
    private DocumentMetaBuffers __DOCUMENTS_META_BUFFERS__;
    private Map<String, String> __META_INDEX_INFO__;
    private Config __CONFIG__;

    public Pagerank(Config config) throws IOException {
        __CONFIG__ = config;
        __INDEX_PATH__ = config.getIndexPath();
        __DATASET_PATH__ = config.getDatasetPath();
        __META_INDEX_INFO__ = Indexer.loadMeta(__INDEX_PATH__ + "/" + config.getMetaFileName());
    }

    /**
     * Computes the pagerank scores based on the citations
     * @throws IOException
     */
    public void citationsPagerank() throws IOException {
        long startTime = System.nanoTime();
        Themis.print(">>> Calculating citations pagerank scores\n");
        __DOCUMENTS_META_BUFFERS__ = new DocumentMetaBuffers(__CONFIG__, DocumentMetaBuffers.MODE.WRITE);
        String graphFileName = __INDEX_PATH__ + "/graph";
        dumpCitations(graphFileName);
        List<PagerankNode> graph = initCitationsGraph(graphFileName);
        computeCitationsPagerank(graph);
        writeCitationsScores(graph);
        Files.deleteIfExists(new File(graphFileName).toPath());
        Themis.print("Pagerank scores calculated in: " + Math.round((System.nanoTime() - startTime) / 1e7) / 100.0 + " sec\n");
        __DOCUMENTS_META_BUFFERS__.close();
        __DOCUMENTS_META_BUFFERS__ = null;
    }

    /* Creates a temp file 'graph' in the Index directory. Entry N of this file corresponds to the Nth document
    that was parsed and it contains the number of its Out citations followed by a list of integer Ids that correspond to
    the Ids of its In citations. A document that has Id N in this file corresponds to entry N (starting from 0) */
    private void dumpCitations(String graphFileName) throws IOException {
        File folder = new File(__DATASET_PATH__);
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        int totalDocuments = Integer.parseInt(__META_INDEX_INFO__.get("articles"));
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
        int intId = 0;
        long offset = 0;
        while (intId < totalDocuments) {
            ByteBuffer buffer = __DOCUMENTS_META_BUFFERS__.getBufferLong(offset);
            buffer.get(docIdArray);
            String stringId = new String(docIdArray, 0, DocumentMetaEntry.ID_SIZE, "ASCII");
            citationsIdsMap.put(stringId, intId);
            intId++;
            offset += DocumentMetaEntry.totalSize;
        }

        /* parse the dataset and write the required data to the 'graph' file */
        for (File file : corpus) {
            if (file.isFile()) {
                Themis.print("Parsing file: " + file + "\n");
                BufferedReader currentDataFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String json;
                while ((json = currentDataFile.readLine()) != null) {
                    S2CitationsGraphEntry entry = S2JsonEntryReader.readCitationsGraphEntry(json);

                    //out citations
                    List<String> outCitations = entry.getOutCitations();
                    int numOutCitations = outCitations.size();

                    //in citations
                    List<String> inCitations = entry.getInCitations();
                    int numInCitations = 0;
                    for (String citation : inCitations) {
                        if (citationsIdsMap.get(citation) != null) {
                            numInCitations++;
                        }
                    }

                    //write all required the data into an array
                    byte[] citationData = new byte[4 * (2 + numInCitations)];
                    ByteBuffer citationDataBuf = ByteBuffer.wrap(citationData);
                    citationDataBuf.putInt(0, 4 * (1 + numInCitations));
                    citationDataBuf.putInt(4, numOutCitations);
                    for (int i = 0; i < numInCitations; i++) {
                        Integer citation = citationsIdsMap.get(inCitations.get(i));
                        if (citation != null) {
                            citationDataBuf.putInt(4 * i + 8, citation);
                        }
                    }

                    //finally, write the array to the file
                    graphWriter.write(citationData);
                }
                currentDataFile.close();
            }
        }
        graphWriter.close();
        citationsIdsMap.clear(); // can be garbage collected
    }

    /* initialize the citations pagerank graph and its nodes */
    private List<PagerankNode> initCitationsGraph(String graphFileName) throws IOException {
        BufferedInputStream graphReader = new BufferedInputStream(new FileInputStream
                (new RandomAccessFile(graphFileName, "r").getFD()));
        int totalDocuments = Integer.parseInt(__META_INDEX_INFO__.get("articles"));
        List<PagerankNode> graph = new ArrayList<>(totalDocuments);

        /* Create the graph -> a list of nodes */
        for (int i = 0; i < totalDocuments; i++) {
            graph.add(new PagerankNode());
        }

        byte[] num = new byte[4];
        ByteBuffer numBuf = ByteBuffer.wrap(num);

        /* read the 'graph' file and update the In citations of each node of the graph */
        for (int i = 0; i < totalDocuments; i++) {
            PagerankNode node = graph.get(i);
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
                node.addInNode(j, graph.get(inCitation));
            }
        }

        graphReader.close();

        return graph;
    }

    /* computes the citations pagerank scores */
    private void computeCitationsPagerank(List<PagerankNode> graph) {
        double dampingFactor = 0.85;
        double graphSize = graph.size();

        // initialize scores
        for (PagerankNode node : graph) {
            node.setPrevScore(1 / graphSize);
        }

        boolean maybeConverged = false;
        int iteration = 1;
        while (!maybeConverged) {
            Themis.print("Pagerank iteration: " + iteration + "\n");

            // calculate the scores
            double norm = 0;
            for (PagerankNode node : graph) {
                double score = node.calcInScore() + (1 - dampingFactor) / graphSize;
                node.setScore(score);
                norm += score;
            }

            // normalize and check for convergence
            maybeConverged = true;
            for (PagerankNode node : graph) {
                node.setScore(node.getScore() / norm);
                if (maybeConverged && Math.abs(node.getPrevScore() - node.getScore()) > 0.001) {
                    maybeConverged = false;
                }
            }

            // update the previous scores (set it to the current score)
            if (!maybeConverged) {
                for (PagerankNode node : graph) {
                    node.updatePrevScore();
                }
            }

            iteration++;
        }
    }

    /* writes the scores to the documents_meta file */
    private void writeCitationsScores(List<PagerankNode> graph) {
        int totalDocuments = Integer.parseInt(__META_INDEX_INFO__.get("articles"));
        long offset = 0;
        for (int i = 0; i < totalDocuments; i++) {
            ByteBuffer buffer = __DOCUMENTS_META_BUFFERS__.getBufferLong(offset + DocumentMetaEntry.PAGERANK_OFFSET);
            double score = Math.floor(graph.get(i).getScore() * 10000) / 10000;
            buffer.putDouble(score);
            offset += DocumentMetaEntry.totalSize;
        }
    }
}
