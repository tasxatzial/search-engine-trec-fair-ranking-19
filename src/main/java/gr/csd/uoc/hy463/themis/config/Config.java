package gr.csd.uoc.hy463.themis.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class that holds configuration options for the themis system
 */
public class Config {
    private Properties __PROP__;

    public Config()
            throws IOException {
        __PROP__ = new Properties();
        String propFileName = "/themis.config";
        InputStream input = getClass().getResourceAsStream(propFileName);
        if (input == null) {
            throw new FileNotFoundException("Config file not found");
        }
        __PROP__.load(input);
    }

    /**
     * Returns the path of the final index folder
     *
     * @return
     */
    public String getIndexDir() {
        return __PROP__.getProperty("INDEX_DIR");
    }

    /**
     * Returns the path of the temp index folder
     *
     * @return
     */
    public String getIndexTmpDir() {
        return __PROP__.getProperty("INDEX_TMP_DIR");
    }

    /**
     * Returns the path of the collection folder
     *
     * @return
     */
    public String getDatasetDir() {
        return __PROP__.getProperty("DATASET_DIR");
    }

    /**
     * Returns the name of the vocabulary file
     *
     * @return
     */
    public String getVocabularyFileName() {
        return __PROP__.getProperty("VOCABULARY_FILENAME");
    }

    /**
     * Returns the name of the postings file
     *
     * @return
     */
    public String getPostingsFileName() {
        return __PROP__.getProperty("POSTINGS_FILENAME");
    }

    /**
     * Returns the name of the documents file
     *
     * @return
     */
    public String getDocumentsFileName() {
        return __PROP__.getProperty("DOCUMENTS_FILENAME");
    }

    /**
     * Returns the name of the documents metadata file
     *
     * @return
     */
    public String getDocumentsMetaFileName() {
        return __PROP__.getProperty("DOCUMENTS_META_FILENAME");
    }

    /**
     * Returns the name of the documents ID file
     *
     * @return
     */
    public String getDocumentsIDFileName() {
        return __PROP__.getProperty("DOCUMENTS_ID_FILENAME");
    }

    /**
     * Returns the name of the index metadata file
     *
     * @return
     */
    public String getIndexMetaFileName() {
        return __PROP__.getProperty("INDEX_META_FILENAME");
    }

    /**
     * Returns the default retrieval model
     *
     * @return
     */
    public String getRetrievalModel() {
        return __PROP__.getProperty("RETRIEVAL_MODEL");
    }

    /**
     * Returns the weight of the retrieval model scores
     *
     * @return
     */
    public double getRetrievalModelWeight() {
        return Double.parseDouble(__PROP__.getProperty("RETRIEVAL_MODEL_WEIGHT"));
    }

    /**
     * Returns the weight of the pagerank scores of the documents
     *
     * @return
     */
    public double getDocumentPagerankWeight() {
        return Double.parseDouble(__PROP__.getProperty("PAGERANK_PUBLICATIONS_WEIGHT"));
    }

    /**
     * Returns the weight of the pagerank scores of the authors
     *
     * @return
     */
    public double getAuthorPagerankWeight() {
        return Double.parseDouble(__PROP__.getProperty("PAGERANK_AUTHORS_WEIGHT"));
    }

    /**
     * Returns the threshold that determines when the pagerank scores have converged
     *
     * @return
     */
    public double getPagerankThreshold() {
        return Double.parseDouble(__PROP__.getProperty("PAGERANK_THRESHOLD"));
    }

    /**
     * Returns the pagerank damping factor
     *
     * @return
     */
    public double getPagerankDampingFactor() {
        return Double.parseDouble(__PROP__.getProperty("PAGERANK_DAMPING_FACTOR"));
    }

    /**
     * Returns true if the stemmer should be used when creating the index
     *
     * @return
     */
    public boolean getUseStemmer() {
        return Boolean.parseBoolean(__PROP__.getProperty("USE_STEMMER"));
    }

    /**
     * Returns true if the list of stopwords should be used when creating the index
     *
     * @return
     */
    public boolean getUseStopwords() {
        return Boolean.parseBoolean(__PROP__.getProperty("USE_STOPWORDS"));
    }

    /**
     * Returns the directory that will store all files related to the analysis of the graph structure
     * of the citations.
     *
     * @return
     */
    public String getCitationsStatsDir() {
        return __PROP__.getProperty("CITATIONS_STATS_DIR");
    }

    /**
     * Number of max number of documents per each partial index
     *
     * @return
     */
    public int getPartialIndexSize() {
        String size = __PROP__.getProperty("PARTIAL_INDEX_MAX_DOCS_SIZE");
        if (size != null) {
            return Integer.parseInt(size);
        } else {
            return 0;
        }
    }

    /**
     * Returns the path to the compressed Word2Vec word vector model file
     *
     * @return
     */
    public String getWord2VecModelPath() {
        return __PROP__.getProperty("WORD2VEC_PATH");
    }

    /**
     * Returns the path to the compressed GloVe word vector model file
     *
     * @return
     */
    public String getGloVeModelPath() {
        return __PROP__.getProperty("GLOVE_PATH");
    }

    /**
     * Returns the path to the judgements file for the evaluation of the index
     *
     * @return
     */
    public String getJudgmentsPath() {
        return __PROP__.getProperty("JUDGEMENTS_PATH");
    }

    /**
     * Returns the name of the evaluation results file
     *
     * @return
     */
    public String getEvaluationFilename() {
        return __PROP__.getProperty("EVALUATION_FILENAME");
    }

    /**
     * Returns true if a query expansion model should be used when querying the index
     *
     * @return
     */
    public boolean getUseQueryExpansion() {
        return Boolean.parseBoolean(__PROP__.getProperty("QUERY_EXPANSION_ENABLED"));
    }

    /**
     * Returns the name of the query expansion model
     *
     * @return
     */
    public String getQueryExpansionModel() {
        return __PROP__.getProperty("QUERY_EXPANSION_MODEL");
    }

    /**
     * number of max memory to use
     *
     * @return
     */
    public long getMaxMemory() {
        String size = __PROP__.getProperty("MAX_MEMORY");
        // make it lowercase
        size = size.toUpperCase().trim();
        Pattern p = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([KMGTP]?B)");
        Matcher m = p.matcher(size);
        List<String> parts = new ArrayList<>();

        // We have two groups in the pattern
        while (m.find()) {
            parts.add(m.group(1));
            parts.add(m.group(2));
        }

        System.out.println(parts);
        long bytes = 0;
        // metric unit should be given
        if (parts.size() == 2) {
            String unit = parts.get(1);
            boolean useFloat = true;

            switch (unit) {
                case "B":
                    bytes = 1;
                    useFloat = false;
                    break;
                case "KB":
                    bytes = 1024;
                    break;
                case "MB":
                    bytes = 1024 * 1024;
                    break;
                case "GB":
                    bytes = 1024 * 1024 * 1024;
                    break;
                case "TB":
                    // keep dreaming....!
                    bytes = 1024 * 1024 * 1024 * 1024;  // not supported by long!
                    break;
                case "PB":
                    // probably in another life!
                    bytes = 1024 * 1024 * 1024 * 1024 * 1024; // not supported by long!
                    break;
            }
            if (useFloat) {
                return Math.round(bytes * Float.parseFloat(parts.get(0)));
            } else {
                return bytes * ((long) Float.parseFloat(parts.get(0)));
            }
        }
        return bytes;
    }
}
