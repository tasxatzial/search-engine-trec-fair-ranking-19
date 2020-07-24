package gr.csd.uoc.hy463.themis.examples;

import gr.csd.uoc.hy463.themis.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import java.io.File;
import java.util.Collection;

/**
 * Class that showcases example of Glove usage
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class GloveExample {

    private static final Logger __LOGGER__ = LogManager.getLogger(GloveExample.class);

    public static void main(String[] args) throws Exception {
        Config __CONFIG__ = new Config();  // reads info from themis.config file

        File gloveModel = new File(__CONFIG__.getGloveModelFileName());
        // This will take some time!
        // Wikipedia 2014 + Gigaword 5  from https://nlp.stanford.edu/projects/glove/
        __LOGGER__.info("Loading  model! This will take some time and memory. Please wait...");
        WordVectors model = WordVectorSerializer.readWord2VecModel(gloveModel);

        // Again you can use the stanford pos tagger to identify specific POS to expand...
        // Check the EXTJWNL example

        // For this example just get the two nearest words
        Collection<String> stringList = model.wordsNearest("information", 2);
        System.out.println(stringList);
        stringList = model.wordsNearest("tsdfsdfsdf", 2);
        System.out.println(stringList);
    }
}
