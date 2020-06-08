package gr.csd.uoc.hy463.themis.linkAnalysis;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.model.DocumentEntry;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2JsonEntryReader;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar.S2TextualEntry;

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
     * Computes the pagerank score of the citations
     * @throws IOException
     */
    public void computeCitationsPagerank() throws IOException {
        dumpCitations();

    }

    /* Creates a temp file 'graph' in the Index directory. Line N of this file corresponds to the Nth document
    that was parsed and it has the number of its Out citations followed by a list of integer ids that correspond to
    the ids of its In citations. Also a document with id N in this file corresponds to line N */
    private void dumpCitations() throws IOException {
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
        BufferedWriter graphWriter = new BufferedWriter(new FileWriter(__INDEX_PATH__ + "/graph"));

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
}
