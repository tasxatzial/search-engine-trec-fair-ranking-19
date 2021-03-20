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

    /* graph statistics file for the citations that exist in the collection */
    private String _citationsStats;

    /* histogram info file: Number of In edges -> Number of documents (duplicates and self references excluded) */
    private String _inEdgesTrue;

    /* histogram info file: Number of Out edges -> Number of documents (duplicates and self references excluded) */
    private String _outEdgesTrue;

    /* histogram info file: Number of In edges -> Number of documents */
    private String _inEdges;

    /* histogram info file: Number of Out edges -> Number of documents */
    private String _outEdges;

    /* histogram info file for the citations that do not exist in the collection: Number of In edges -> Number of documents */
    private String _inEdgesNotFound;

    /* histogram info file for the citations that do not exist in the collection: Number of Out edges -> Number of documents */
    private String _outEdgesNotFound;

    /* graph statistics file for the citations that do not exist in the collection */
    private String _citationsNotFoundStats;

    public CreateCitationsGraph() throws IOException {
        Config __CONFIG__ = new Config();
        __DATASET_PATH__ = __CONFIG__.getDatasetPath();
        String citationsGraphPath = __CONFIG__.getCitationsGraphPath();
        Files.createDirectories(Paths.get(citationsGraphPath));
        _citationsGraphBinary = citationsGraphPath + "/citations_graph_binary";
        _citationsGraph = citationsGraphPath + "/citations_graph";
        _citationsStats = citationsGraphPath + "/citations_stats";
        _citationsNotFoundStats = citationsGraphPath + "/citations_not_found_stats";
        _inEdgesTrue = citationsGraphPath + "/in_edges_true";
        _outEdgesTrue = citationsGraphPath + "/out_edges_true";
        _inEdges = citationsGraphPath + "/in_edges";
        _outEdges = citationsGraphPath + "/out_edges";
        _inEdgesNotFound = citationsGraphPath + "/in_edges_not_found";
        _outEdgesNotFound = citationsGraphPath + "/out_edges_not_found";
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
     *
     * Also the following files are created:
     * 1) citations_not_found_stats
     * 2) in_edges_not_found
     * 3) out_edges_not_found
     */
    public void dumpCitationsGraph() throws IOException {
        File folder = new File(__DATASET_PATH__);
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        Set<String> inEdgesNotFoundIds = new HashSet<>();
        Set<String> outEdgesNotFoundIds = new HashSet<>();
        Map<Integer, Integer> notFoundInEdgesHistogram = new HashMap<>();
        Map<Integer, Integer> notFoundOutEdgesHistogram = new HashMap<>();
        int totalNotFoundInEdges = 0;
        int totalNotFoundOutEdges = 0;

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

                    int i = 0;
                    int notFoundInCitations = 0;
                    for (int j = 0; j < inCitations.size(); j++) {
                        String currentInCitation = inCitations.get(j);
                        Integer citation = citationsIdsMap.get(currentInCitation);
                        if (citation != null) {
                            inCitationsDataBuf.putInt(4 * (1 + i), citation);
                            i++;
                        }
                        else { //exclude multiple same in citations
                            boolean found = false;
                            for (int k = 0; k < j; k++) {
                                if (currentInCitation.equals(inCitations.get(k))) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                notFoundInCitations++;
                                inEdgesNotFoundIds.add(currentInCitation);
                            }
                        }
                    }
                    notFoundInEdgesHistogram.merge(notFoundInCitations, 1, Integer::sum);
                    totalNotFoundInEdges += notFoundInCitations;
                    inCitationsDataBuf.putInt(0, numInCitations);

                    i = 0;
                    int notFoundOutCitations = 0;
                    for (int j = 0; j < outCitations.size(); j++) {
                        String currentOutCitation = outCitations.get(j);
                        Integer citation = citationsIdsMap.get(currentOutCitation);
                        if (citation != null) {
                            outCitationsDataBuf.putInt(4 * (1 + i), citation);
                            i++;
                        }
                        else { //exclude multiple same out citations
                            boolean found = false;
                            for (int k = 0; k < j; k++) {
                                if (currentOutCitation.equals(outCitations.get(k))) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                notFoundOutCitations++;
                                outEdgesNotFoundIds.add(currentOutCitation);
                            }
                        }
                    }
                    notFoundOutEdgesHistogram.merge(notFoundOutCitations, 1, Integer::sum);
                    totalNotFoundOutEdges += notFoundOutCitations;
                    outCitationsDataBuf.putInt(0, numOutCitations);

                    //finally, write the arrays to the file
                    graphWriter.write(outCitationsData);
                    graphWriter.write(inCitationsData);

                    totalDocuments++;
                }
                currentDataFile.close();
            }
        }

        //write the total document number to file
        byte[] totalDocumentsArr = new byte[4];
        ByteBuffer.wrap(totalDocumentsArr).putInt(totalDocuments);
        graphWriter.write(totalDocumentsArr);
        graphWriter.close();

        //finally, write info related to not found citations to file
        List<Integer> sortedNotFoundOutEdges = new ArrayList<>(notFoundOutEdgesHistogram.keySet());
        Collections.sort(sortedNotFoundOutEdges);
        List<Integer> sortedNotFoundInEdges = new ArrayList<>(notFoundInEdgesHistogram.keySet());
        Collections.sort(sortedNotFoundInEdges);
        BufferedWriter notFoundOutEdgesWriter = new BufferedWriter(new FileWriter(_outEdgesNotFound));
        BufferedWriter notFoundInEdgesWriter = new BufferedWriter(new FileWriter(_inEdgesNotFound));
        notFoundOutEdgesWriter.write("Number of not found Out edges -(multiple same references) | Number of documents\n");
        notFoundInEdgesWriter.write("Number of not found In edges -(multiple same references) | Number of documents\n");
        for (Integer integer : sortedNotFoundOutEdges) {
            notFoundOutEdgesWriter.write(integer + " " + notFoundOutEdgesHistogram.get(integer) + "\n");
        }
        for (Integer integer : sortedNotFoundInEdges) {
            notFoundInEdgesWriter.write(integer + " " + notFoundInEdgesHistogram.get(integer) + "\n");
        }
        notFoundOutEdgesWriter.close();
        notFoundInEdgesWriter.close();
        BufferedWriter statsWriter = new BufferedWriter(new FileWriter(_citationsNotFoundStats));
        statsWriter.write("Documents: " + totalDocuments + "\n");
        statsWriter.write("Total not found In edges: " + totalNotFoundInEdges + "\n");
        statsWriter.write("Unique not found In edges: " + inEdgesNotFoundIds.size() + "\n");
        statsWriter.write("Total not found Out edges: " + totalNotFoundOutEdges + "\n");
        statsWriter.write("Unique not found Out edges: " + outEdgesNotFoundIds.size() + "\n");
        statsWriter.close();
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
     * 1) citations_stats
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
        BufferedWriter statsWriter = new BufferedWriter(new FileWriter(_citationsStats));
        BufferedWriter inEdgesTrueWriter = new BufferedWriter(new FileWriter(_inEdgesTrue));
        BufferedWriter outEdgesTrueWriter = new BufferedWriter(new FileWriter(_outEdgesTrue));
        BufferedWriter inEdgesWriter = new BufferedWriter(new FileWriter(_inEdges));
        BufferedWriter outEdgesWriter = new BufferedWriter(new FileWriter(_outEdges));

        //In edges histogram, excludes self references and duplicate references
        Map<Integer, Integer> inEdgesTrueHistogram = new HashMap<>();

        //Out edges histogram, excludes self references and duplicate references
        Map<Integer, Integer> outEdgesTrueHistogram = new HashMap<>();

        //In edges histogram
        Map<Integer, Integer> inEdgesHistogram = new HashMap<>();

        //Out edges histogram
        Map<Integer, Integer> outEdgesHistogram = new HashMap<>();

        //In edges, excludes self references and duplicate references
        int totalInEdgesTrue = 0;

        //In edges
        int totalInEdges = 0;

        //Out edges, excludes self references and duplicate references
        int totalOutEdgesTrue = 0;

        //Out edges
        int totalOutEdges = 0;

        //In edges that are not listed in the Out edges of the corresponding node
        int totalInconsistentInEdges = 0;

        //Out edges that are not listed in the In edges of the corresponding node
        int totalInconsistentOutEdges = 0;

        //Duplicate In edges, excludes self references and duplicate references
        int totalMultipleTrueInEdges = 0;

        //Duplicate Out edges, excludes self references and duplicate references
        int totalMultipleTrueOutEdges = 0;

        //Documents that have at least one duplicate In edge, excludes self references and duplicate references
        int totalDocsWithMultipleTrueInEdges = 0;

        //Documents that have at least one duplicate Out edge, excludes self references and duplicate references
        int totalDocsWithMultipleTrueOutEdges = 0;

        //Documents that reference themselves in their In edges
        int totalSelfRefDocsViaInEdge = 0;

        //Documents that reference themselves in their Out edges
        int totalSelfRefDocsViaOutEdge = 0;

        //In edges that are self reference
        int totalSelfRefInEdges = 0;

        //Out edges that are self reference
        int totalSelfRefOutEdges = 0;

        for (int i = 0; i < graph.length; i++) {
            int[] currentInEdges = graph[i].getInNodes();
            int[] currentOutEdges = graph[i].getOutNodes();

            int selfOutReferences = 0;
            for (int node : currentOutEdges) {
                if (node == i) {
                    selfOutReferences++;
                }
            }
            if (selfOutReferences != 0) {
                totalSelfRefDocsViaOutEdge++;
            }

            int selfInReferences = 0;
            for (int node : currentInEdges) {
                if (node == i) {
                    selfInReferences++;
                }
            }
            if (selfInReferences != 0) {
                totalSelfRefDocsViaInEdge++;
            }

            int multipleTrueOutEdges = 0;
            for (int j = 0; j < currentOutEdges.length; j++) {
                for (int k = 0; k < j; k++) {
                    if (currentOutEdges[k] == currentOutEdges[j] && currentOutEdges[k] != i) {
                        multipleTrueOutEdges++;
                        break;
                    }
                }
            }
            if (multipleTrueOutEdges != 0) {
                totalDocsWithMultipleTrueOutEdges++;
            }

            int multipleTrueInEdges = 0;
            for (int j = 0; j < currentInEdges.length; j++) {
                for (int k = 0; k < j; k++) {
                    if (currentInEdges[k] == currentInEdges[j] && currentInEdges[k] != i) {
                        multipleTrueInEdges++;
                        break;
                    }
                }
            }
            if (multipleTrueInEdges != 0) {
                totalDocsWithMultipleTrueInEdges++;
            }

            int InEdgesTrue = currentInEdges.length - selfInReferences - multipleTrueInEdges;
            int OutEdgesTrue = currentOutEdges.length - selfOutReferences - multipleTrueOutEdges;
            int InEdges = currentInEdges.length;
            int OutEdges = currentOutEdges.length;
            totalInEdgesTrue += InEdgesTrue;
            totalOutEdgesTrue += OutEdgesTrue;
            totalInEdges += InEdges;
            totalOutEdges += OutEdges;
            totalMultipleTrueInEdges += multipleTrueInEdges;
            totalMultipleTrueOutEdges += multipleTrueOutEdges;
            totalSelfRefInEdges += selfInReferences;
            totalSelfRefOutEdges += selfOutReferences;

            inEdgesTrueHistogram.merge(InEdgesTrue, 1, Integer::sum);
            inEdgesHistogram.merge(InEdges, 1, Integer::sum);
            outEdgesTrueHistogram.merge(OutEdgesTrue, 1, Integer::sum);
            outEdgesHistogram.merge(OutEdges, 1, Integer::sum);

            for (int outEdge : currentOutEdges) {
                int[] edges = graph[outEdge].getInNodes();
                Set<Integer> inEdgesSet = new HashSet<>();
                for (int j = 0; j < edges.length; j++) {
                    inEdgesSet.add(edges[j]);
                }
                if (!inEdgesSet.contains(i)) {
                    totalInconsistentInEdges++;
                }
            }

            for (int inEdge : currentInEdges) {
                int[] edges = graph[inEdge].getOutNodes();
                Set<Integer> outEdgesSet = new HashSet<>();
                for (int j = 0; j < edges.length; j++) {
                    outEdgesSet.add(edges[j]);
                }
                if (!outEdgesSet.contains(i)) {
                    totalInconsistentOutEdges++;
                }
            }
        }

        List<Integer> sortedInEdgesTrue = new ArrayList<>(inEdgesTrueHistogram.keySet());
        Collections.sort(sortedInEdgesTrue);
        List<Integer> sortedOutEdgesTrue = new ArrayList<>(outEdgesTrueHistogram.keySet());
        Collections.sort(sortedOutEdgesTrue);
        inEdgesTrueWriter.write("Number of In edges -(self references + multiple same references) | Number of documents\n");
        for (Integer integer : sortedInEdgesTrue) {
            inEdgesTrueWriter.write(integer + " " + inEdgesTrueHistogram.get(integer) + "\n");
        }
        outEdgesTrueWriter.write("Number of Out edges -(self references + multiple same references) | Number of documents\n");
        for (Integer integer : sortedOutEdgesTrue) {
            outEdgesTrueWriter.write(integer + " " + outEdgesTrueHistogram.get(integer) + "\n");
        }

        List<Integer> sortedInEdges = new ArrayList<>(inEdgesHistogram.keySet());
        Collections.sort(sortedInEdges);
        List<Integer> sortedOutEdges = new ArrayList<>(outEdgesHistogram.keySet());
        Collections.sort(sortedOutEdges);
        inEdgesWriter.write("Number of In edges | Number of documents\n");
        for (Integer integer : sortedInEdges) {
            inEdgesWriter.write(integer + " " + inEdgesHistogram.get(integer) + "\n");
        }
        outEdgesWriter.write("Number of Out edges | Number of documents\n");
        for (Integer integer : sortedOutEdges) {
            outEdgesWriter.write(integer + " " + outEdgesHistogram.get(integer) + "\n");
        }

        inEdgesTrueWriter.close();
        outEdgesTrueWriter.close();
        inEdgesWriter.close();
        outEdgesWriter.close();

        statsWriter.write("Documents: " + graph.length + "\n");
        statsWriter.write("Sinks: " + (outEdgesHistogram.get(0) != null ? outEdgesHistogram.get(0) : 0) + "\n");
        statsWriter.write("Sinks +(self references): " + (outEdgesTrueHistogram.get(0) != null ? outEdgesTrueHistogram.get(0) : 0) + "\n");
        statsWriter.write("In edges -(self references + multiple same references): " + totalInEdgesTrue + "\n");
        statsWriter.write("In edges: " + totalInEdges + "\n");
        statsWriter.write("Out edges -(self references + multiple same references): " + totalOutEdgesTrue + "\n");
        statsWriter.write("Out edges: " + totalOutEdges + "\n");
        statsWriter.write("Duplicate In edges -(self references): " + totalMultipleTrueInEdges + "\n");
        statsWriter.write("Documents with duplicate In edges -(self references): " + totalDocsWithMultipleTrueInEdges + "\n");
        statsWriter.write("Duplicate Out edges -(self references): " + totalMultipleTrueOutEdges + "\n");
        statsWriter.write("Documents with duplicate Out edges -(self references): " + totalDocsWithMultipleTrueOutEdges + "\n");
        statsWriter.write("Self referencing documents via In edges: " + totalSelfRefDocsViaInEdge + "\n");
        statsWriter.write("Self referencing In edges: " + totalSelfRefInEdges + "\n");
        statsWriter.write("Self referencing documents via Out edges: " + totalSelfRefDocsViaOutEdge + "\n");
        statsWriter.write("Self referencing Out edges: " + totalSelfRefOutEdges + "\n");
        statsWriter.write("In edges which are not listed in the Out edges of the source node: " + totalInconsistentInEdges + "\n");
        statsWriter.write("Out edges which are not listed in the In edges of the source node: " + totalInconsistentOutEdges + "\n");
        statsWriter.close();
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
