package gr.csd.uoc.hy463.themis.lexicalAnalysis;

import java.io.*;
import java.util.HashSet;

/**
 * Class responsible for identifying stop words
 */
public class StopWords {
    private static StopWords _instance = null;
    private HashSet<String> __WORDS__;

    private StopWords()
            throws IOException {
        String fileName = "/stopwords.txt";
        InputStream input = getClass().getResourceAsStream(fileName);
        if (input == null) {
            throw new FileNotFoundException("Stopwords file not found");
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(input, "UTF-8"));;
        __WORDS__ = new HashSet<>();
        String line;
        while ((line = br.readLine()) != null) {
            __WORDS__.add(line);
        }
    }

    public static StopWords Singleton()
            throws IOException {
        return _instance == null
                ? (_instance = new StopWords())
                : _instance;
    }

    /**
     * Checks if a word is a stop word
     *
     * @param word
     * @return true if the term is a stop word, false otherwise
     */
    public boolean isStopWord(String word) {
        return __WORDS__.contains(word);
    }
}
