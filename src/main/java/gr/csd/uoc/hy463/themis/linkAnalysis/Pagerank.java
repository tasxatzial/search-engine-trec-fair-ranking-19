package gr.csd.uoc.hy463.themis.linkAnalysis;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.model.DocumentEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.linkAnalysis.graph.PagerankNode;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

public class Pagerank {
    private String __DATASET_PATH__;
    private String __INDEX_PATH__;
    private String __DOCUMENTS_FILENAME__;

    public Pagerank() throws IOException {
        Config config = new Config();
        __DATASET_PATH__ = config.getDatasetPath();
        __INDEX_PATH__ = config.getIndexPath();
        __DOCUMENTS_FILENAME__ = config.getDocumentsFileName();
    }

    /**
     * Computes the pagerank scores based on the citations
     * @throws IOException
     */
    public void citationsPagerank() throws IOException {
        String graphName = __INDEX_PATH__ + "/graph";
        dumpCitations(graphName);
        Map<Integer, PagerankNode> graph = initCitationsGraph(graphName);
        computeCitationsPagerank(graph);
    }

    /* Creates a temp file 'graph' in the Index directory. Line N of this file corresponds to the Nth document
    that was parsed and it has the number of its Out citations followed by a list of integer ids that correspond to
    the ids of its In citations. Also a document that has id N in this file corresponds to line N */
    private void dumpCitations(String graphName) throws IOException {
        File folder = new File(__DATASET_PATH__);
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        // open documents files
        BufferedInputStream documentsReader = new BufferedInputStream(new FileInputStream
                (new RandomAccessFile(__INDEX_PATH__ + "/" + __DOCUMENTS_FILENAME__, "r").getFD()));

        // sort the files so that we parse them in a specific order
        List<File> corpus = new ArrayList<>(files.length);
        corpus.addAll(Arrays.asList(files));
        Collections.sort(corpus);

        // document string id -> int id
        Map<String, Integer> citationsIdsMap = new HashMap<>();

        /* for each document write: number of Out citations followed by a list of integer ids
        that correspond to the In citations */
        BufferedWriter graphWriter = new BufferedWriter(new FileWriter(graphName));

        /* read the documents file and create the map of string id -> int id */
        byte[] sizeB = new byte[4]; //size of a document entry
        int read;
        int intId = 1;
        while ((read = documentsReader.read(sizeB)) != -1) {
            ByteBuffer bb = ByteBuffer.wrap(sizeB);
            int size = bb.getInt();
            byte[] doc = new byte[size - 4];
            documentsReader.read(doc);
            String stringId = new String(doc, 0, DocumentEntry.ID_SIZE, "ASCII");
            citationsIdsMap.put(stringId, intId);
            intId++;
        }
        documentsReader.close();

        /* parse the dataset and write the appropriate info to the 'graph' file */
        for (File file : corpus) {
            if (file.isFile()) {
                Themis.print("Processing file: " + file + "\n");
                BufferedReader currentDataFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String json;
                while ((json = currentDataFile.readLine()) != null) {
                    S2TextualEntry entry = S2JsonEntryReader.readTextualEntry(json);

                    //out citations
                    List<String> outCitations = entry.getOutCitations();
                    int numOutCitations = outCitations.size();
                    graphWriter.write(numOutCitations + " ");

                    //in citations
                    List<String> inCitations = entry.getInCitations();
                    for (String citation : inCitations) {
                        if (citationsIdsMap.get(citation) != null) {
                            graphWriter.write(citationsIdsMap.get(citation) + " ");
                        }
                    }
                    graphWriter.write("\n");
                }
                currentDataFile.close();
            }
        }
        graphWriter.close();
        citationsIdsMap.clear(); // can be garbage collected
    }

    /* initialize the citations pagerank graph and its nodes */
    private Map<Integer, PagerankNode> initCitationsGraph(String graphName) throws IOException {
        BufferedReader graphReader = new BufferedReader(new FileReader(graphName));
        Map<Integer, PagerankNode> graph = new HashMap<>();
        String line;
        int intId = 1;

        /* Create the graph, a map of int id -> nodes */
        while ((line = graphReader.readLine()) != null) {
            String[] split = line.split(" ");
            int outNum = Integer.parseInt(split[0]);
            int inNum = split.length - 1;
            PagerankNode node = new PagerankNode(intId);
            node.initializeInNodes(inNum);
            node.setOutNodes(outNum);
            graph.put(intId, node);
            intId++;
        }
        graphReader.close();

        /* read the 'graph' file and add the nodes that correspond to the In citations in each node of the graph */
        intId = 1;
        graphReader = new BufferedReader(new FileReader(graphName));
        while ((line = graphReader.readLine()) != null) {
            String[] split = line.split(" ");
            PagerankNode node = graph.get(intId);
            for (int i = 1; i < split.length; i++) {
                node.addInNode(graph.get(Integer.parseInt(split[i])));
            }
            intId++;
        }
        graphReader.close();

        return graph;
    }

    /* computes the citations pagerank scores */
    private void computeCitationsPagerank(Map<Integer, PagerankNode> graph) {

        // initialize pagerank scores
        for (PagerankNode node : graph.values()) {
            node.setPrevScore(1.0);
        }

        boolean converged = false;
        int iteration = 1;
        while (!converged) {
            
            // calculate the scores
            for (PagerankNode node : graph.values()) {
                node.calculateScore();
            }

            // check for convergence
            converged = true;
            for (PagerankNode node : graph.values()) {
                if (Math.abs(node.getPrevScore() - node.getScore()) > 0.001) {
                    converged = false;
                    break;
                }
            }

            // update the previous scores (sets it to the current score)
            for (PagerankNode node : graph.values()) {
                node.updatePrevScore();
            }

            iteration++;
        }
    }
}
