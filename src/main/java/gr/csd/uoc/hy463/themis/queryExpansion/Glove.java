package gr.csd.uoc.hy463.themis.queryExpansion;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.ProcessText;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Expands a query using the glove dictionary
 */
public class Glove extends QueryExpansion {
    private WordVectors _model;
    private int _nearest;

    public Glove() throws IOException {
        Config __CONFIG__ = new Config();  // reads info from themis.config file
        File gloveModel = new File(__CONFIG__.getGloveModelFileName());
        Themis.print(">>> Loading glove model data...");
        _model = WordVectorSerializer.readWord2VecModel(gloveModel);
        Themis.print("DONE\n");

        //default is to get the nearest 2 terms for each term
        _nearest = 2;
    }

    /**
     * Expands a given query by adding to it the nearest 2 terms for each term of the query
     * @param query
     * @return
     */
    public String expandQuery(String query) {
        List<String> splitQuery = ProcessText.split(query);
        StringBuilder sb = new StringBuilder();

        for (String s : splitQuery) {
            Collection<String> nearest = _model.wordsNearest(s, _nearest);
            Object[] nearestArray = nearest.toArray();
            for (Object o : nearestArray) {
                sb.append(o.toString()).append(" ");
            }
            sb.append(s);
        }

        return sb.toString();
    }
}
