package gr.csd.uoc.hy463.themis.linkAnalysis.graph.utils;

import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CreateCitationsGraph {
    private String __DATASET_PATH__;

    /* graph file in binary format */
    private String _citationsGraphBinary;

    /* graph file in human readable format */
    private String _citationsGraph;

    /* graph statistics file */
    private String _citationsGraphStats;

    /* histogram info file: Number of In nodes -> Occurrences (duplicates and self references excluded) */
    private String _inNodesTrue;

    /* histogram info file: Number of Out nodes -> Occurrences (duplicates and self references excluded) */
    private String _outNodesTrue;

    /* histogram info file: Number of In nodes -> Occurrences */
    private String _inNodes;

    /* histogram info file: Number of Out nodes -> Occurrences */
    private String _outNodes;

    public CreateCitationsGraph() throws IOException {
        Config __CONFIG__ = new Config();
        __DATASET_PATH__ = __CONFIG__.getDatasetPath();
        String citationsGraphPath = __CONFIG__.getCitationsGraphPath();
        Files.createDirectories(Paths.get(citationsGraphPath));
        _citationsGraphBinary = citationsGraphPath + "/citations_graph_binary";
        _citationsGraph = citationsGraphPath + "/citations_graph";
        _citationsGraphStats = citationsGraphPath + "/citations_graph_stats";
        _inNodesTrue = citationsGraphPath + "/in_nodes_true";
        _outNodesTrue = citationsGraphPath + "/out_nodes_true";
        _inNodes = citationsGraphPath + "/in_nodes";
        _outNodes = citationsGraphPath + "/out_nodes";
    }

    /**
     * Creates the file 'citations_graph_binary' file in the citation graph directory specified in the config file.
     * The dataset files are parsed lexicographically. Entry N of this file corresponds to
     * the Nth document that was parsed and it contains in the following order:
     * 1) Total size of the In, Out citation data -> int (4 bytes)
     * 2) Number of Out citations -> int (4 bytes)
     * 3) Out citations -> a list of int
     * 4) Number of In citations -> int (4 bytes)
     * 5) In citations -> a list of int
     *
     * The last 4 bytes of the file contain the total number of documents.
     */
    public void dumpCitationsGraph() throws IOException {
        File folder = new File(__DATASET_PATH__);
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        /* sort the dataset files lexicographically */
        List<File> corpus = new ArrayList<>(files.length);
        corpus.addAll(Arrays.asList(files));
        Collections.sort(corpus);

        //parse the collection and create a docID -> Integer map
        Map<String, Integer> citationsIdsMap = getDocIDs(corpus);

        Files.deleteIfExists(Paths.get(_citationsGraphBinary));
        BufferedOutputStream graphWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(_citationsGraphBinary, "rw").getFD()));
        int totalDocuments = 0;

        /* parse the dataset and write the required citation data to the 'citations_graph_binary' file */
        for (File file : corpus) {
            if (file.isFile()) {
                BufferedReader currentDataFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String json;
                while ((json = currentDataFile.readLine()) != null) {
                    S2TextualEntry entry = S2JsonEntryReader.readCitationsEntry(json);

                    //out citations
                    List<String> outCitations = entry.getOutCitations();
                    int numOutCitations = 0;
                    for (String citation : outCitations) {
                        if (citationsIdsMap.get(citation) != null) {
                            numOutCitations++;
                        }
                    }

                    //in citations
                    List<String> inCitations = entry.getInCitations();
                    int numInCitations = 0;
                    for (String citation : inCitations) {
                        if (citationsIdsMap.get(citation) != null) {
                            numInCitations++;
                        }
                    }

                    //write citations data into an array
                    byte[] outCitationsData = new byte[4 * (1 + numOutCitations)];
                    byte[] inCitationsData = new byte[4 * (1 + numInCitations)];
                    ByteBuffer inCitationsDataBuf = ByteBuffer.wrap(inCitationsData);
                    ByteBuffer outCitationsDataBuf = ByteBuffer.wrap(outCitationsData);

                    byte[] size = new byte[4];
                    ByteBuffer sizeBuf = ByteBuffer.wrap(size);
                    sizeBuf.putInt(outCitationsData.length + inCitationsData.length);
                    graphWriter.write(size);

                    int j = 0;
                    for (String inCitation : inCitations) {
                        Integer citation = citationsIdsMap.get(inCitation);
                        if (citation != null) {
                            inCitationsDataBuf.putInt(4 * (1 + j), citation);
                            j++;
                        }
                    }
                    inCitationsDataBuf.putInt(0, numInCitations);

                    j = 0;
                    for (String outCitation : outCitations) {
                        Integer citation = citationsIdsMap.get(outCitation);
                        if (citation != null) {
                            outCitationsDataBuf.putInt(4 * (1 + j), citation);
                            j++;
                        }
                    }
                    outCitationsDataBuf.putInt(0, numOutCitations);

                    //finally, write the arrays to the file
                    graphWriter.write(outCitationsData);
                    graphWriter.write(inCitationsData);

                    totalDocuments++;
                }
                currentDataFile.close();
            }
        }
        byte[] totalDocumentsArr = new byte[4];
        ByteBuffer.wrap(totalDocumentsArr).putInt(totalDocuments);
        graphWriter.write(totalDocumentsArr);
        graphWriter.close();
    }

    /**
     * Returns an array that has the citations graph. Entry N of the array corresponds to document
     * with id N.
     * @return
     * @throws IOException
     */
    public CitationsNode[] loadCitationsGraph() throws IOException {
        if (!(new File(_citationsGraphBinary)).exists()) {
            return null;
        }

        /* read the number of total documents from the graph file */
        RandomAccessFile graphRA = new RandomAccessFile(_citationsGraphBinary, "r");
        graphRA.seek(graphRA.length() - 4);
        int totalDocuments = graphRA.readInt();
        graphRA.close();

        BufferedInputStream graphReader = new BufferedInputStream(new FileInputStream(new RandomAccessFile(_citationsGraphBinary, "r").getFD()));
        CitationsNode[] graph = new CitationsNode[totalDocuments];

        /* Initialize the citations graph */
        for (int i = 0; i < totalDocuments; i++) {
            graph[i] = new CitationsNode();
        }

        byte[] citationsSize = new byte[4];
        ByteBuffer citationsSizeBuf = ByteBuffer.wrap(citationsSize);

        /* read the citations graph file and populate the graph */
        for (int i = 0; i < totalDocuments; i++) {
            CitationsNode node = graph[i];
            graphReader.read(citationsSize);
            int size = citationsSizeBuf.getInt(0);
            byte[] citations = new byte[size];
            ByteBuffer citationsBuf = ByteBuffer.wrap(citations);
            graphReader.read(citations);
            int outCitationsNum = citationsBuf.getInt();
            node.initializeOutNodes(outCitationsNum);
            for (int j = 0; j < outCitationsNum; j++) {
                int outCitation = citationsBuf.getInt();
                node.getOutNodes()[j] = outCitation;
            }
            int inCitationsNum = citationsBuf.getInt();
            node.initializeInNodes(inCitationsNum);
            for (int j = 0; j < inCitationsNum; j++) {
                int inCitation = citationsBuf.getInt();
                node.getInNodes()[j] = inCitation;
            }
        }
        graphReader.close();
        return graph;
    }

    /**
     * Creates the file 'citations_graph' file in the citation graph directory specified in the config file.
     * @param graph
     * @throws IOException
     */
    public void writeCitationsGraph(CitationsNode[] graph) throws IOException {
        BufferedWriter graphWriter = new BufferedWriter(new FileWriter(_citationsGraph));

        for (int i = 0; i < graph.length; i++) {
            CitationsNode node = graph[i];
            graphWriter.write(">>> Node id: " + i + "\n");
            int[] inNodes = node.getInNodes();
            graphWriter.write("In Nodes [" + inNodes.length + "]: ");
            for (int j = 0; j < inNodes.length; j++) {
                graphWriter.write(inNodes[j] + " ");
            }
            graphWriter.write("\n");
            int[] outNodes = node.getOutNodes();
            graphWriter.write("Out Nodes [" + outNodes.length + "]: ");
            for (int j = 0; j < outNodes.length; j++) {
                graphWriter.write(outNodes[j] + " ");
            }
            graphWriter.write("\n\n");
        }
        graphWriter.close();
    }

    /**
     * Creates the following files:
     * 1) citations_graph_stats
     * 2) in_nodes_true
     * 3) out_nodes_true
     * 4) in_nodes
     * 5) out_nodes
     *
     * in the citation graph directory specified in the config file.
     * @param graph
     * @throws IOException
     */
    public void calculateGraphStats(CitationsNode[] graph) throws IOException {
        BufferedWriter statsWriter = new BufferedWriter(new FileWriter(_citationsGraphStats));
        BufferedWriter inNodesTrueWriter = new BufferedWriter(new FileWriter(_inNodesTrue));
        BufferedWriter outNodesTrueWriter = new BufferedWriter(new FileWriter(_outNodesTrue));
        BufferedWriter inNodesWriter = new BufferedWriter(new FileWriter(_inNodes));
        BufferedWriter outNodesWriter = new BufferedWriter(new FileWriter(_outNodes));

        //In nodes histogram, excludes self references and duplicate references
        Map<Integer, Integer> inNodesTrueHistogram = new HashMap<>();

        //Out nodes histogram, excludes self references and duplicate references
        Map<Integer, Integer> outNodesTrueHistogram = new HashMap<>();

        //In nodes histogram
        Map<Integer, Integer> inNodesHistogram = new HashMap<>();

        //Out nodes histogram
        Map<Integer, Integer> outNodesHistogram = new HashMap<>();

        //In nodes, excludes self references and duplicate references
        int totalInNodesTrue = 0;

        //In nodes
        int totalInNodes = 0;

        //Out nodes, excludes self references and duplicate references
        int totalOutNodesTrue = 0;

        //Out nodes
        int totalOutNodes = 0;

        //Sink nodes, excludes nodes that reference themselves
        int totalSinksNoSelf = 0;

        //Sink nodes
        int totalSinks = 0;

        //In nodes that are not listed in their Out nodes
        int totalInconsistentInNodes = 0;

        //Out nodes that are not listed in their In nodes
        int totalInconsistentOutNodes = 0;

        //Duplicate In nodes, excludes self references and duplicate references
        int totalMultipleTrueInNodes = 0;

        //Duplicate Out nodes, excludes self references and duplicate references
        int totalMultipleTrueOutNodes = 0;

        //Documents that have at least one duplicate In node, excludes self references and duplicate references
        int totalDocsWithMultipleTrueInNodes = 0;

        //Documents that have at least one duplicate Out node, excludes self references and duplicate references
        int totalDocsWithMultipleTrueOutNodes = 0;

        //Documents that reference themselves in their In nodes
        int totalSelfRefDocsViaInNode = 0;

        //Documents that reference themselves in the Out nodes
        int totalSelfRefInNodes = 0;

        //Out nodes that reference themselves
        int totalSelfRefOutNodes = 0;

        //In nodes that reference themselves
        int totalSelfRefDocsViaOutNode = 0;

        for (int i = 0; i < graph.length; i++) {
            int[] currentInNodes = graph[i].getInNodes();
            int[] currentOutNodes = graph[i].getOutNodes();

            if (currentOutNodes.length == 0) {
                totalSinks++;
            }

            int selfOutReferences = 0;
            for (int node : currentOutNodes) {
                if (node == i) {
                    selfOutReferences++;
                }
            }
            if (selfOutReferences != 0) {
                totalSelfRefDocsViaOutNode++;
            }

            int selfInReferences = 0;
            for (int node : currentInNodes) {
                if (node == i) {
                    selfInReferences++;
                }
            }
            if (selfInReferences != 0) {
                totalSelfRefDocsViaInNode++;
            }

            int multipleTrueOutNodes = 0;
            for (int j = 0; j < currentOutNodes.length; j++) {
                for (int k = 0; k < j; k++) {
                    if (currentOutNodes[k] == currentOutNodes[j] && currentOutNodes[k] != i) {
                        multipleTrueOutNodes++;
                        break;
                    }
                }
            }
            if (multipleTrueOutNodes != 0) {
                totalDocsWithMultipleTrueOutNodes++;
            }

            int multipleTrueInNodes = 0;
            for (int j = 0; j < currentInNodes.length; j++) {
                for (int k = 0; k < j; k++) {
                    if (currentInNodes[k] == currentInNodes[j] && currentInNodes[k] != i) {
                        multipleTrueInNodes++;
                        break;
                    }
                }
            }
            if (multipleTrueInNodes != 0) {
                totalDocsWithMultipleTrueInNodes++;
            }

            int InNodesTrue = currentInNodes.length - selfInReferences - multipleTrueInNodes;
            int OutNodesTrue = currentOutNodes.length - selfOutReferences - multipleTrueOutNodes;
            int InNodes = currentInNodes.length;
            int OutNodes = currentOutNodes.length;
            totalInNodesTrue += InNodesTrue;
            totalOutNodesTrue += OutNodesTrue;
            totalInNodes += InNodes;
            totalOutNodes += OutNodes;
            totalMultipleTrueInNodes += multipleTrueInNodes;
            totalMultipleTrueOutNodes += multipleTrueOutNodes;
            totalSelfRefInNodes += selfInReferences;
            totalSelfRefOutNodes += selfOutReferences;
            if (OutNodesTrue == 0) {
                totalSinksNoSelf++;
            }
            inNodesTrueHistogram.merge(InNodesTrue, 1, Integer::sum);
            inNodesHistogram.merge(InNodes, 1, Integer::sum);
            outNodesTrueHistogram.merge(OutNodesTrue, 1, Integer::sum);
            outNodesHistogram.merge(OutNodes, 1, Integer::sum);

            for (int outNode : currentOutNodes) {
                int[] nodes = graph[outNode].getInNodes();
                Set<Integer> inNodesSet = new HashSet<>();
                for (int j = 0; j < nodes.length; j++) {
                    inNodesSet.add(nodes[j]);
                }
                if (!inNodesSet.contains(i)) {
                    totalInconsistentInNodes++;
                }
            }

            for (int inNode : currentInNodes) {
                int[] nodes = graph[inNode].getOutNodes();
                Set<Integer> outNodesSet = new HashSet<>();
                for (int j = 0; j < nodes.length; j++) {
                    outNodesSet.add(nodes[j]);
                }
                if (!outNodesSet.contains(i)) {
                    totalInconsistentOutNodes++;
                }
            }
        }

        List<Integer> sortedInNodesTrueHistogram = new ArrayList<>(inNodesTrueHistogram.keySet());
        List<Integer> sortedOutNodesTrueHistogram = new ArrayList<>(outNodesTrueHistogram.keySet());
        inNodesTrueWriter.write("Number of In Nodes -(self references + multiple references) | Occurences\n");
        for (Integer integer : sortedInNodesTrueHistogram) {
            inNodesTrueWriter.write(integer + " " + inNodesTrueHistogram.get(integer) + "\n");
        }
        outNodesTrueWriter.write("Number of Out Nodes -(self references + multiple references) | Occurences\n");
        for (Integer integer : sortedOutNodesTrueHistogram) {
            outNodesTrueWriter.write(integer + " " + outNodesTrueHistogram.get(integer) + "\n");
        }

        List<Integer> sortedInNodesHistogram = new ArrayList<>(inNodesHistogram.keySet());
        List<Integer> sortedOutNodesHistogram = new ArrayList<>(outNodesHistogram.keySet());
        inNodesWriter.write("Number of In Nodes | Occurences\n");
        for (Integer integer : sortedInNodesHistogram) {
            inNodesWriter.write(integer + " " + inNodesHistogram.get(integer) + "\n");
        }
        outNodesWriter.write("Number of Out Nodes | Occurences\n");
        for (Integer integer : sortedOutNodesHistogram) {
            outNodesWriter.write(integer + " " + outNodesHistogram.get(integer) + "\n");
        }

        statsWriter.write("Documents: " + graph.length + "\n");
        statsWriter.write("Sinks: " + totalSinks + "\n");
        statsWriter.write("Sinks -(self references): " + totalSinksNoSelf + "\n");
        statsWriter.write("In nodes -(self references + multiple references): " + totalInNodesTrue + "\n");
        statsWriter.write("In nodes: " + totalInNodes + "\n");
        statsWriter.write("Out nodes -(self references + multiple references): " + totalOutNodesTrue + "\n");
        statsWriter.write("Out nodes: " + totalOutNodes + "\n");
        statsWriter.write("Duplicate In nodes -(self references): " + totalMultipleTrueInNodes + "\n");
        statsWriter.write("Documents with duplicate In nodes -(self references): " + totalDocsWithMultipleTrueInNodes + "\n");
        statsWriter.write("Duplicate Out nodes -(self references): " + totalMultipleTrueOutNodes + "\n");
        statsWriter.write("Documents with duplicate Out nodes -(self references): " + totalDocsWithMultipleTrueOutNodes + "\n");
        statsWriter.write("Self referencing documents via In nodes: " + totalSelfRefDocsViaInNode + "\n");
        statsWriter.write("Self referencing In nodes: " + totalSelfRefInNodes + "\n");
        statsWriter.write("Self referencing documents via Out nodes: " + totalSelfRefDocsViaOutNode + "\n");
        statsWriter.write("Self referencing Out nodes: " + totalSelfRefOutNodes + "\n");
        statsWriter.write("In nodes of a node which are not listed in their Out nodes: " + totalInconsistentInNodes + "\n");
        statsWriter.write("Out nodes of a node which are not listed in their In nodes: " + totalInconsistentOutNodes + "\n");

        statsWriter.close();
        inNodesTrueWriter.close();
        outNodesTrueWriter.close();
        inNodesWriter.close();
        outNodesWriter.close();
    }

    /* Parses the corpus and creates a map of doc Id -> Integer */
    private Map<String, Integer> getDocIDs(List<File> corpus) throws IOException {
        Map<String, Integer> citationsIdsMap = new HashMap<>();
        int ID = 0;
        for (File file : corpus) {
            if (file.isFile()) {
                BufferedReader currentDataFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String json;
                while ((json = currentDataFile.readLine()) != null) {
                    S2TextualEntry entry = S2JsonEntryReader.readDocIdEntry(json);
                    String docId = entry.getId();
                    citationsIdsMap.put(docId, ID);
                    ID++;
                }
                currentDataFile.close();
            }
        }

        return citationsIdsMap;
    }
}
