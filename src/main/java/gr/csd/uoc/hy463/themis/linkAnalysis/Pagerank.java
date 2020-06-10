package gr.csd.uoc.hy463.themis.linkAnalysis;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
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

    public Pagerank() throws IOException {
        Config config = new Config();
        __INDEX_PATH__ = config.getIndexPath();
        __DATASET_PATH__ = config.getDatasetPath();
        __META_INDEX_INFO__ = new HashMap<>();
        String __META_FILENAME__ = config.getMetaFileName();
        BufferedReader meta = new BufferedReader(new FileReader(__INDEX_PATH__ + "/" + __META_FILENAME__));
        String[] split;
        String line;
        while((line = meta.readLine()) != null) {
            split = line.split("=");
            __META_INDEX_INFO__.put(split[0], split[1]);
        }
    }

    /**
     * Computes the pagerank scores based on the citations
     * @throws IOException
     */
    public void citationsPagerank() throws IOException {
        long startTime = System.nanoTime();
        Themis.print(">>> Calculating citations pagerank scores\n");
        __DOCUMENTS_META_BUFFERS__ = new DocumentMetaBuffers(DocumentMetaBuffers.MODE.WRITE);
        String graphFileName = __INDEX_PATH__ + "/graph";
        dumpCitations(graphFileName);
        List<PagerankNode> graph = initCitationsGraph(graphFileName);
        computeCitationsPagerank(graph);
        writeCitationsScores(graph);
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
        while (intId < totalDocuments) {
            long documentMetaOffset = intId * DocumentMetaEntry.totalSize;
            ByteBuffer buffer = __DOCUMENTS_META_BUFFERS__.getBufferLong(documentMetaOffset);
            buffer.get(docIdArray);
            String stringId = new String(docIdArray, 0, DocumentMetaEntry.ID_SIZE, "ASCII");
            citationsIdsMap.put(stringId, intId);
            intId++;
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
                node.addInNode(graph.get(inCitation));
            }
        }

        graphReader.close();
        Files.deleteIfExists(new File(graphFileName).toPath());

        return graph;
    }

    /* computes the citations pagerank scores */
    private void computeCitationsPagerank(List<PagerankNode> graph) {

        // initialize pagerank scores
        for (PagerankNode node : graph) {
            node.setPrevScore(1.0);
        }

        boolean converged = false;
        int iteration = 1;
        while (!converged) {
            Themis.print("Pagerank iteration: " + iteration + "\n");

            // calculate the scores
            for (PagerankNode node : graph) {
                node.calculateScore();
            }

            // check for convergence
            converged = true;
            for (PagerankNode node : graph) {
                if (Math.abs(node.getPrevScore() - node.getScore()) > 0.001) {
                    converged = false;
                    break;
                }
            }

            // update the previous scores (set it to the current score)
            for (PagerankNode node : graph) {
                node.updatePrevScore();
            }

            iteration++;
        }
    }

    /* writes the scores to the documents_meta file */
    private void writeCitationsScores(List<PagerankNode> graph) {
        int totalDocuments = Integer.parseInt(__META_INDEX_INFO__.get("articles"));
        for (int i = 0; i < totalDocuments; i++) {
            long documentMetaOffset = i * DocumentMetaEntry.totalSize + DocumentMetaEntry.PAGERANK_OFFSET;
            ByteBuffer buffer = __DOCUMENTS_META_BUFFERS__.getBufferLong(documentMetaOffset);
            double score = Math.floor(graph.get(i).getScore() * 10000) / 10000;
            buffer.putDouble(score);
        }
    }
}
