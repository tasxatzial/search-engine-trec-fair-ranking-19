/*
 * themis - A fair search engine for scientific articles
 *
 * Computer Science Department
 *
 * University of Crete
 *
 * http://www.csd.uoc.gr
 *
 * Project for hy463 Information Retrieval Systems course
 * Spring Semester 2020
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that holds configuration options for the themis system
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
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
     * a property about the dataset path
     *
     * @return
     */
    public String getDatasetPath() {
        return __PROP__.getProperty("DATASET_PATH");
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
}
