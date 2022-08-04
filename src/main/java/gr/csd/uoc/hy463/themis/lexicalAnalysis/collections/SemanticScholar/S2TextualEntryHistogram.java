package gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar;

import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.utils.MapValueComparator;
import gr.csd.uoc.hy463.themis.utils.Pair;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Creates a histogram of the number of occurrences of each character in the collection.
 *
 * Can be used to find the most frequent characters.
 */
public class S2TextualEntryHistogram {

    /**
     * Reads the JSON entries contained in the files located in inPath and creates a histogram of the number of
     * occurrences of each character (one histogram per JSON property).
     *
     * @param inPath Path of files that contain JSON
     * @param outPath Save path of the histograms
     * @throws IOException
     */
    public static void createHistograms(String inPath, String outPath)
            throws IOException {
        BufferedWriter outFile = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(outPath), "UTF-8"));

        /* the file that is being parsed */
        BufferedReader currentDataFile;

        File folder = new File(inPath);
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        String json;

        /* the parsed file has one JSON per line that can be parsed into a S2TextualEntry */
        S2TextualEntry entry;

        /* a character map for each field of the S2TextualEntry. A map contains
           pairs of <character, frequency>  */
        Map<Integer, Integer> titles = new HashMap<>();
        Map<Integer, Integer> abstracts = new HashMap<>();
        Map<Integer, Integer> entities = new HashMap<>();
        Map<Integer, Integer> fieldsOfStudy = new HashMap<>();
        Map<Integer, Integer> authorNames = new HashMap<>();
        Map<Integer, Integer> venues = new HashMap<>();
        Map<Integer, Integer> journalNames = new HashMap<>();
        Map<Integer, Integer> sources = new HashMap<>();

        for (File file : files) {
            currentDataFile = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            while ((json = currentDataFile.readLine()) != null) {
                entry = S2JsonEntryReader.readTextualEntry(json);

                String entryTitle = entry.getTitle();
                addToHistogram(titles, entryTitle);

                String entryAbstract = entry.getPaperAbstract();
                addToHistogram(abstracts, entryAbstract);

                List<String> entryEntity = entry.getEntities();
                for (String s : entryEntity) {
                    addToHistogram(entities, s);
                }

                List<String> entryFieldOfStudy = entry.getFieldsOfStudy();
                for (String s : entryFieldOfStudy) {
                    addToHistogram(fieldsOfStudy, s);
                }

                List<Pair<String, List<String>>> entryAuthorName = entry.getAuthors();
                for (Pair<String, List<String>> stringListPair : entryAuthorName) {
                    addToHistogram(authorNames, stringListPair.getL());
                }

                String entryVenue = entry.getVenue();
                addToHistogram(venues, entryVenue);

                String entryJournalName = entry.getJournalName();
                addToHistogram(journalNames, entryJournalName);

                List<String> entrySource = entry.getSources();
                for (String s : entrySource) {
                    addToHistogram(sources, s);
                }
            }
        }
        writeHistogram(outFile, DocInfo.PROPERTY.TITLE, titles);
        writeHistogram(outFile, DocInfo.PROPERTY.ABSTRACT, abstracts);
        writeHistogram(outFile, DocInfo.PROPERTY.ENTITIES, entities);
        writeHistogram(outFile, DocInfo.PROPERTY.FIELDS_OF_STUDY, fieldsOfStudy);
        writeHistogram(outFile, DocInfo.PROPERTY.AUTHORS_NAMES, authorNames);
        writeHistogram(outFile, DocInfo.PROPERTY.VENUE, venues);
        writeHistogram(outFile, DocInfo.PROPERTY.JOURNAL_NAME, journalNames);
        writeHistogram(outFile, DocInfo.PROPERTY.SOURCES, sources);
        outFile.close();
    }

    /* expands the histogram by taking into account the characters in the field string */
    private static void addToHistogram(Map<Integer, Integer> charMap, String field) {
        for (int pos = 0; pos < field.length(); ) {
            int cp = field.codePointAt(pos);
            int count = Character.charCount(cp);
            pos += count;
            charMap.merge(cp, 1, Integer::sum);
        }
    }

    /* writes the histogram to outFile */
    private static void writeHistogram(BufferedWriter outFile, DocInfo.PROPERTY property, Map<Integer, Integer> charMap)
            throws IOException {
        Map<Integer, Integer> sortedCharMap = new TreeMap<>(new MapValueComparator(charMap));
        sortedCharMap.putAll(charMap);
        outFile.write("--------------------" + property + "------------------\n");
        for (Map.Entry<Integer, Integer> c : sortedCharMap.entrySet()) {
            outFile.write(new String(Character.toChars(c.getKey()))  + " " + c.getValue() + "\n");
        }
    }
}
