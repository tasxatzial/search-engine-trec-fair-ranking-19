 /*
    !! Code from mitos web search engine !!
 */
package gr.csd.uoc.hy463.themis.lexicalAnalysis.stemmer.english;

/**
 * English Stemmer
 *
 * @author Panagiotis Papadakos (github.com/papadako)
 */
public class EnglishStemmer {

    private static EnglishStemmer m_englishStemmer = null;
    private PorterStemmer m_PorterStemmer;

    private EnglishStemmer() {

        m_PorterStemmer = new PorterStemmer();
    }

    public static EnglishStemmer Singleton() {

        return (m_englishStemmer == null) ? (m_englishStemmer = new EnglishStemmer())
                : m_englishStemmer;
    }

    public String Stem(String _word) {

        String word = _word;
        m_PorterStemmer.add(word.toCharArray(), word.length());
        m_PorterStemmer.stem();
        return m_PorterStemmer.toString();
    }

} // class EnglishStemmer
