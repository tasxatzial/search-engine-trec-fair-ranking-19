package gr.csd.uoc.hy463.themis.lexicalAnalysis;

import opennlp.tools.stemmer.PorterStemmer;

/**
 * Class responsible for stemming
 */
public class Stemmer {
    private static Stemmer _instance;
    private static PorterStemmer _porterStemmer;

    private Stemmer()  {
        _porterStemmer = new PorterStemmer();
    }

    private PorterStemmer getPorterStemmer() {
        return _porterStemmer;
    }

    public static Stemmer Singleton() {
        return _instance == null
                ? (_instance = new Stemmer())
                : _instance;
    }

    public static String stem(String word) {
        if (word.length() > 3) {
            String stemmedWord = Stemmer.Singleton().getPorterStemmer().stem(word);
            if (stemmedWord.length() >= 3) {
                return stemmedWord;
            }
        }
        return word;
    }
}
