package gr.csd.uoc.hy463.themis.linkAnalysis;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.model.DocumentEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2CitationsGraphEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;
import gr.csd.uoc.hy463.themis.linkAnalysis.graph.PagerankNode;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;

public class Pagerank {
    private String __DATASET_PATH__;
    private String __INDEX_PATH__;
    private String __DOCUMENTS_FILENAME__;
    private Map<String, String> __META_INDEX_INFO__;

    public Pagerank() throws IOException {
        Config config = new Config();
        __DATASET_PATH__ = config.getDatasetPath();
        __INDEX_PATH__ = config.getIndexPath();
        __DOCUMENTS_FILENAME__ = config.getDocumentsFileName();
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
        String graphPath = __INDEX_PATH__ + "/graph";
        dumpCitations(graphPath);
        List<PagerankNode> graph = initCitationsGraph(graphPath);
        computeCitationsPagerank(graph);
    }

    /* Creates a temp file 'graph' in the Index directory. Line N of this file corresponds to the Nth document
    that was parsed and it has the number of its Out citations followed by a list of integer ids that correspond to
    the ids of its In citations. Also a document that has id N in this file corresponds to line N (starting from 0) */
    private void dumpCitations(String graphPath) throws IOException {
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
        BufferedWriter graphWriter = new BufferedWriter(new FileWriter(graphPath));

        /* read the documents file and create the map of string id -> int id */
        byte[] sizeB = new byte[4]; //size of a document entry
        int read;
        int intId = 0;
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
                    S2CitationsGraphEntry entry = S2JsonEntryReader.readCitationsGraphEntry(json);

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
    private List<PagerankNode> initCitationsGraph(String graphPath) throws IOException {
        BufferedReader graphReader = new BufferedReader(new FileReader(graphPath));
        List<PagerankNode> graph = new ArrayList<>(Integer.parseInt(__META_INDEX_INFO__.get("articles")));
        String line;

        /* Create the graph -> a list of nodes */
        while ((line = graphReader.readLine()) != null) {
            String[] split = line.split(" ");
            int outNum = Integer.parseInt(split[0]);
            int inNum = split.length - 1;
            PagerankNode node = new PagerankNode();
            node.initializeInNodes(inNum);
            node.setOutNodes(outNum);
            graph.add(node);
        }
        graphReader.close();

        /* read the 'graph' file and add the nodes that correspond to the In citations in each node of the graph */
        int intId = 0;
        graphReader = new BufferedReader(new FileReader(graphPath));
        while ((line = graphReader.readLine()) != null) {
            String[] split = line.split(" ");
            PagerankNode node = graph.get(intId);
            for (int i = 1; i < split.length; i++) {
                node.addInNode(graph.get(Integer.parseInt(split[i])));
            }
            intId++;
        }
        graphReader.close();
        Files.deleteIfExists(new File(graphPath).toPath());

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

            // update the previous scores (sets it to the current score)
            for (PagerankNode node : graph) {
                node.updatePrevScore();
            }

            iteration++;
        }
    }
}
