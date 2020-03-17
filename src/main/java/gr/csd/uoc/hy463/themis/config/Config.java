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

    private InputStream input;
    private static final Logger LOGGER = LogManager.getLogger(Config.class);
    private Properties prop;

    public Config() throws IOException {
        prop = new Properties();
        String propFileName = "/themis.config";

        input = getClass().getResourceAsStream(propFileName);

        if (input != null) {
            prop.load(input);
        } else {
            LOGGER.error("property file '" + propFileName + "' not found in the classpath");
        }
    }

    /**
     * Check if config is valid
     *
     * @return
     */
    public boolean valid() {
        return input != null;
    }

    /**
     * a property about the index path
     *
     * @return
     */
    public String getIndexPath() {
        return prop.getProperty("INDEX_PATH");
    }

    /**
     * a property about the dataset path
     *
     * @return
     */
    public String getDatasetPath() {
        return prop.getProperty("DATASET_PATH");
    }
}
