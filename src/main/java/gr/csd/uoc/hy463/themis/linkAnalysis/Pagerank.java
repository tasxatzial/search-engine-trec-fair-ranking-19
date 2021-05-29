package gr.csd.uoc.hy463.themis.linkAnalysis;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.MemMap.DocumentBuffers;
import gr.csd.uoc.hy463.themis.indexer.MemMap.MemoryBuffers;
import gr.csd.uoc.hy463.themis.indexer.model.DocumentIDEntry;
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
 * Class for calculating the pagerank scores of the documents of the collection.
 */
public class Pagerank {
    private final Indexer _indexer;

    /* The full path of the citations 'graph' file */
    private final String __GRAPH_PATH__;

    private final int _totalDocuments;

    /**
     * Constructor.
     *
     * @param indexer
     * @throws IOException
     */
    public Pagerank(Indexer indexer)
            throws IOException {
        _indexer = indexer;

        /* Set the full path of the 'graph' file. The file is saved in the 'index' folder */
        String indexPath = _indexer.getConfig().getIndexPath();
        __GRAPH_PATH__ = indexPath + "/graph";

        /* Read the total number of documents from 'index_meta' file */
        Map<String, String> __INDEX_META__ = indexer.loadMeta();
        _totalDocuments = Integer.parseInt(__INDEX_META__.get("documents"));
    }

    /**
     * Parses the collection and creates the 'graph' file.
     * It then reads that file, loads the graph in memory and computes the pagerank scores. Finally, it updates the
     * corresponding records in the 'documents_meta' file.
     *
     * Requires both 'documents_meta' and 'documents_id' files to be present.
     *
     * @throws PagerankException
     */
    public void citationsPagerank()
            throws PagerankException {
        try {
            Themis.print(">>> Calculating Pagerank\n");
            long startTime = System.nanoTime();
            Themis.print("> Constructing graph\n");
            dumpCitations();
            PagerankNode[] graph = initCitationsGraph();
            Themis.print("Graph created in " + new Time(System.nanoTime() - startTime) + '\n');
            startTime = System.nanoTime();
            Themis.print("> Iterating\n");
            double[] scores = computeCitationsPagerank(graph);
            Themis.print("Iterations completed in " + new Time(System.nanoTime() - startTime) + '\n');
            writeCitationsScores(scores);
            Files.deleteIfExists(new File(__GRAPH_PATH__).toPath());
        }
        catch (IOException e) {
            throw new PagerankException();
        }
    }

    /* Creates the 'graph' file. This is a random access file.
    An entry in this file contains the number of Out citations followed by a list of int IDs that correspond to
    the IDs of the In citations.

    The N-th entry corresponds to the document with int ID = N (starting from 0) */
    private void dumpCitations()
            throws IOException {
        File folder = new File(_indexer.getDataSetPath());
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        /* load the 'documents_id' file as a memory mapped file */
        String documentsIDPath = _indexer.getDocumentsIDFilePath();
        DocumentBuffers documentIDBuffers = new DocumentBuffers(documentsIDPath, MemoryBuffers.MODE.READ, DocumentIDEntry.totalSize);
        byte[] docIdArray = new byte[DocumentIDEntry.DOCID_SIZE];

        /* The 'graph' file stores for each document the number of Out citations and the IDs of the
        In citations. Each entry in the file consists of:
        1) size (int) -> this is the size of the rest of the data in this entry
        2) number of Out citations (int)
        3) In citation Id 1 (int) | in citation Id 2 (int) ... */
        BufferedOutputStream graphWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(__GRAPH_PATH__, "rw").getFD()));

        /* sort the files of the collection. This is necessary so that the N-th parsed document is the one with
        int ID = N (starting from 0) since the same ordering was used to generate the int ID of each document
        during the indexing */
        List<File> corpus = new ArrayList<>(files.length);
        corpus.addAll(Arrays.asList(files));
        Collections.sort(corpus);

        /* A map of string docID -> int ID */
        Map<String, Integer> citationsIntMap = new HashMap<>();

        /* read the 'documents_id' file and create the map of string docID -> int ID with int ID taking
        values from 0 to totalDocuments in increasing order. That way, the int ID is
        the actual int ID of the corresponding document (determined during the indexing process).
        We can now use an integer to refer to a document instead of its string docID */
        int documents = 0;
        long offset = 0;
        ByteBuffer buffer;
        while ((buffer = documentIDBuffers.getBufferLong(offset)) != null) {
            buffer.get(docIdArray);
            String stringId = new String(docIdArray, 0, DocumentIDEntry.DOCID_SIZE, "ASCII");
            citationsIntMap.put(stringId, documents);
            documents++;
            offset += DocumentIDEntry.totalSize;
        }
        documentIDBuffers.close();

        /* parse the collection and write the required data to the 'graph' file */
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
                        if (!skipCitation(citationsIntMap, outCitations, entry.getId(), outCitations.get(i), i)) {
                            numOutCitations++;
                        }
                    }

                    /* count in citations */
                    List<String> inCitations = entry.getInCitations();
                    int numInCitations = 0;
                    for (int i = 0; i <inCitations.size(); i++) {
                        if (!skipCitation(citationsIntMap, inCitations, entry.getId(), inCitations.get(i), i)) {
                            numInCitations++;
                        }
                    }

                    /* dump citations data to disk */
                    byte[] citationData = new byte[4 * (2 + numInCitations)];
                    ByteBuffer citationDataBuf = ByteBuffer.wrap(citationData);
                    citationDataBuf.putInt(0, 4 * (1 + numInCitations));
                    citationDataBuf.putInt(4, numOutCitations);
                    int k = 0;
                    for (int i = 0; i <inCitations.size(); i++) {
                        if (!skipCitation(citationsIntMap, inCitations, entry.getId(), inCitations.get(i), i)) {
                            Integer citation = citationsIntMap.get(inCitations.get(i));
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

    /* Returns true iff the citation_i should not be added to the specified list of citations. This can happen when:
    1) citation_i does not exist
    2) citation_i is referencing itself
    3) citation_i is already in the list of citations
    */
    private boolean skipCitation(Map<String, Integer> citationsIds, List<String> citations, String docId, String citation_i, int citation_idx) {

        /* skip citation if the corresponding document does not exist */
        if (citationsIds.get(citation_i) == null) {
            return true;
        }

        /* skip citation if it is the same as the doc ID */
        if (citation_i.equals(docId)) {
            return true;
        }

        boolean found = false;
        for (int j = 0; j < citation_idx; j++) {

            /* skip citation if we've already taken this citation into account */
            if (citation_i.equals(citations.get(j))) {
                found = true;
                break;
            }
        }
        return found;
    }

    /* initialize the citations pagerank graph */
    private PagerankNode[] initCitationsGraph()
            throws IOException {
        BufferedInputStream graphReader = new BufferedInputStream(new FileInputStream(new RandomAccessFile(__GRAPH_PATH__, "r").getFD()));
        PagerankNode[] graph = new PagerankNode[_totalDocuments];

        /* initialize the graph as an array of nodes */
        for (int i = 0; i < _totalDocuments; i++) {
            graph[i] = new PagerankNode();
        }

        byte[] num = new byte[4];
        ByteBuffer numBuf = ByteBuffer.wrap(num);

        /* read the 'graph' file and update the In citations of each node of the graph */
        for (int i = 0; i < _totalDocuments; i++) {
            PagerankNode node = graph[i];
            graphReader.read(num);
            int size = numBuf.getInt(0);
            byte[] citationData = new byte[size];
            ByteBuffer citationDataBuf = ByteBuffer.wrap(citationData);
            graphReader.read(citationData);
            int outCitationsNum = citationDataBuf.getInt(0);
            node.set_outNodes(outCitationsNum);
            int inCitationsNum = size / 4 - 1;
            node.initializeInNodes(inCitationsNum);
            for (int j = 0; j < inCitationsNum; j++) {
                int inCitation = citationDataBuf.getInt(4 * j + 4);
                node.get_inNodes()[j] = graph[inCitation];
            }
        }
        graphReader.close();
        return graph;
    }

    /* computes the pagerank scores based on the citations graph */
    private double[] computeCitationsPagerank(PagerankNode[] graph) {
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
            if (iteration != 1 && iteration % 20 == 1) {
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

    /* writes the citation pagerank scores to the 'documents_meta' file */
    private void writeCitationsScores(double[] scores)
            throws IOException {
        long offset = 0;
        String documentsMetaPath = _indexer.getDocumentsMetaFilePath();
        DocumentBuffers documentMetaBuffers = new DocumentBuffers(documentsMetaPath, MemoryBuffers.MODE.WRITE, DocumentMetaEntry.totalSize);

        for (int i = 0; i < scores.length; i++) {
            ByteBuffer buffer = documentMetaBuffers.getBufferLong(offset + DocumentMetaEntry.CITATIONS_PAGERANK_OFFSET);
            buffer.putDouble(scores[i]);
            offset += DocumentMetaEntry.totalSize;
        }
        documentMetaBuffers.close();
    }
}
