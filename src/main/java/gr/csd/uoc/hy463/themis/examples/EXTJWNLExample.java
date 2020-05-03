/*
 * themis - A fair search engine for scientific articles
 *
 * Currently over the Semantic Scholar Open Research Corpus
 * http://s2-public-api-prod.us-west-2.elasticbeanstalk.com/corpus/
 *
 * Collaborative work with the undergraduate/graduate students of
 * Information Retrieval Systems (hy463) course
 * Spring Semester 2020
 *
 * -- Writing code during COVID-19 pandemic times :-( --
 *
 * Aiming to participate in TREC 2020 Fair Ranking Track
 * https://fair-trec.github.io/
 *
 * Computer Science Department http://www.csd.uoc.gr
 * University of Crete
 * Greece
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.examples;

import java.io.FileNotFoundException;
import java.util.List;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.dictionary.Dictionary;

/**
 * Class that showcases example of external java wordnet library usage
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class EXTJWNLExample {

    public static void main(String[] args) throws FileNotFoundException, JWNLException, CloneNotSupportedException {
        Dictionary dictionary = null;
        if (args.length != 1) {
            dictionary = Dictionary.getDefaultResourceInstance();
        }

        if (null != dictionary) {
            // Can get various synsets
            IndexWord iWord;
            iWord = dictionary.getIndexWord(POS.VERB, "accomplish");
            for (Synset synset : iWord.getSenses()) {
                List<Word> words = synset.getWords();
                for (Word word : words) {
                    System.out.println(word.getLemma());
                }
            }
            iWord = dictionary.getIndexWord(POS.NOUN, "dog");
            for (Synset synset : iWord.getSenses()) {
                List<Word> words = synset.getWords();
                for (Word word : words) {
                    System.out.println(word.getLemma());
                }
            }
            iWord = dictionary.lookupIndexWord(POS.ADJECTIVE, "funny");
            for (Synset synset : iWord.getSenses()) {
                List<Word> words = synset.getWords();
                for (Word word : words) {
                    System.out.println(word.getLemma());
                }
            }
        }
    }
}
