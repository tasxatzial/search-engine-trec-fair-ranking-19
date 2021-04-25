package gr.csd.uoc.hy463.themis.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that holds configuration options for the themis system
 */
public class Config {

    private static final Logger __LOGGER__ = LogManager.getLogger(Config.class);
    private Properties __PROP__;

    public Config() throws IOException {
        __PROP__ = new Properties();
        String propFileName = "/themis.config";

        InputStream input = getClass().getResourceAsStream(propFileName);

        if (input != null) {
            __PROP__.load(input);
        } else {
            __LOGGER__.error("property file '" + propFileName + "' not found in the classpath");
        }
    }

    /**
     * Check if config is valid
     *
     * @return
     */
    public boolean valid() {
        return !__PROP__.isEmpty();
    }

    /**
     * a property about the index path
     *
     * @return
     */
    public String getIndexPath() {
        return __PROP__.getProperty("INDEX_PATH");
    }

    /**
     * a property about the temporary index path
     * @return
     */
    public String getIndexTmpPath() {
        return __PROP__.getProperty("INDEX_TMP_PATH");
    }

    /**
     * a property about the dataset path
     *
     * @return
     */
    public String getDatasetPath() {
        return __PROP__.getProperty("DATASET_PATH");
    }

    /**
     * Returns the filename of the vocabulary file
     *
     * @return
     */
    public String getVocabularyFileName() {
        return __PROP__.getProperty("VOCABULARY_FILENAME");
    }

    /**
     * Returns the filename of postings file
     *
     * @return
     */
    public String getPostingsFileName() {
        return __PROP__.getProperty("POSTINGS_FILENAME");
    }

    /**
     * Returns the filename of documents file
     *
     * @return
     */
    public String getDocumentsFileName() {
        return __PROP__.getProperty("DOCUMENTS_FILENAME");
    }

    /**
     * Returns the filename of documents meta file
     *
     * @return
     */
    public String getDocumentsMetaFileName() {
        return __PROP__.getProperty("DOCUMENTS_META_FILENAME");
    }

    /**
     * Returns the filename of documents docID file
     *
     * @return
     */
    public String getDocumentsIDFileName() {
        return __PROP__.getProperty("DOCUMENTS_ID_FILENAME");
    }

    /**
     * Returns the filename of the index meta file
     *
     * @return
     */
    public String getMetaFileName() {
        return __PROP__.getProperty("META_FILENAME");
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
     * Returns the weight for the retrieval model
     *
     * @return
     */
    public double getRetrievalModelWeight() {
        return Double.parseDouble(__PROP__.getProperty("RETRIEVAL_MODEL_WEIGHT"));
    }

    /**
     * Returns the weight for the publications pagerank weight
     *
     * @return
     */
    public double getPagerankPublicationsWeight() {
        return Double.parseDouble(__PROP__.getProperty("PAGERANK_PUBLICATIONS_WEIGHT"));
    }

    /**
     * Returns the weight for the authors pagerank weight
     *
     * @return
     */
    public double getPagerankAuthorsWeight() {
        return Double.parseDouble(__PROP__.getProperty("PAGERANK_AUTHORS_WEIGHT"));
    }

    /**
     * Returns the threshold that determines when the pagerank scores have converged
     * @return
     */
    public double getPagerankThreshold() {
        return Double.parseDouble(__PROP__.getProperty("PAGERANK_THRESHOLD"));
    }

    /**
     * Returns the pagerank damping factor
     * @return
     */
    public double getPagerankDampingFactor() {
        return Double.parseDouble(__PROP__.getProperty("PAGERANK_DAMPING_FACTOR"));
    }

    /**
     * Returns if we should user the stemmer
     *
     * @return
     */
    public boolean getUseStemmer() {
        return Boolean.parseBoolean(__PROP__.getProperty("USE_STEMMER"));
    }

    /**
     * Returns if we should use the stopwords
     *
     * @return
     */
    public boolean getUseStopwords() {
        return Boolean.parseBoolean(__PROP__.getProperty("USE_STOPWORDS"));
    }

    /**
     * Returns the directory that will have the files related to the citations graph
     * @return
     */
    public String getCitationsGraphPath() {
        return __PROP__.getProperty("CITATIONS_GRAPH_PATH");
    }

    /**
     * Returns if we should use the titles
     *
     * @return
     */
    public boolean getUseTitles() {
        return Boolean.parseBoolean(__PROP__.getProperty("TITLES_ENABLED"));
    }

    /**
     * Returns the path to the compressed glove model
     *
     * @return
     */
    public String getTitlesFileName() {
        return __PROP__.getProperty("TITLES_PATH");
    }

    /**
     * number of max number of files per each partial index
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
     * Returns the path to the compressed word2vec model
     *
     * @return
     */
    public String getWord2VecModelFileName() {
        return __PROP__.getProperty("WORD2VEC_FILENAME");
    }

    /**
     * Returns the path to the compressed glove model
     *
     * @return
     */
    public String getGloveModelFileName() {
        return __PROP__.getProperty("GLOVE_FILENAME");
    }

    /**
     * Returns the path to the compressed glove model
     *
     * @return
     */
    public String getJudgmentsPath() {
        return __PROP__.getProperty("JUDGEMENTS_PATH");
    }

    /**
     * Returns the path to the filename of the evaluation results
     * @return
     */
    public String getEvaluationFilename() {
        return __PROP__.getProperty("EVALUATION_FILENAME");
    }

    /**
     * Returns if an query expansion model will be used
     * @return
     */
    public boolean getUseQueryExpansion() {
        return Boolean.parseBoolean(__PROP__.getProperty("QUERY_EXPANSION_ENABLED"));
    }

    /**
     * Returns the name of the query expansion model
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
