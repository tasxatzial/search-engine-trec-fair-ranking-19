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
 * The histogram is then written to a file.
 *
 * Used to find the most frequently occurring characters.
 */
public class S2TextualEntryCharHistogram {

    /**
     * Reads the JSON entries from the files in the specified datasetPath and creates a histogram of the number of
     * occurrences of each character. One histogram is generated per JSON name.
     *
     * @param datasetPath The source folder path. Files have one JSON per line which can be parsed as a textual entry.
     * @param outPath The histogram will be saved in this folder
     * @throws IOException
     */
    public static void createCharMap(String datasetPath, String outPath)
            throws IOException {
        BufferedWriter charWriter = new BufferedWriter(new OutputStreamWriter
                (new FileOutputStream(outPath), "UTF-8"));

        /* the dataset file that is being parsed */
        BufferedReader currentDataFile;

        File folder = new File(datasetPath);
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        String json;
        S2TextualEntry entry;

        /* a character map for each field of the textual entry */
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
                addToCharMap(titles, entryTitle);

                String entryAbstract = entry.getPaperAbstract();
                addToCharMap(abstracts, entryAbstract);

                List<String> entryEntity = entry.getEntities();
                for (String s : entryEntity) {
                    addToCharMap(entities, s);
                }

                List<String> entryFieldOfStudy = entry.getFieldsOfStudy();
                for (String s : entryFieldOfStudy) {
                    addToCharMap(fieldsOfStudy, s);
                }

                List<Pair<String, List<String>>> entryAuthorName = entry.getAuthors();
                for (Pair<String, List<String>> stringListPair : entryAuthorName) {
                    addToCharMap(authorNames, stringListPair.getL());
                }

                String entryVenue = entry.getVenue();
                addToCharMap(venues, entryVenue);

                String entryJournalName = entry.getJournalName();
                addToCharMap(journalNames, entryJournalName);

                List<String> entrySource = entry.getSources();
                for (String s : entrySource) {
                    addToCharMap(sources, s);
                }
            }
        }
        writeField(charWriter, DocInfo.PROPERTY.TITLE, titles);
        writeField(charWriter, DocInfo.PROPERTY.ABSTRACT, abstracts);
        writeField(charWriter, DocInfo.PROPERTY.ENTITIES, entities);
        writeField(charWriter, DocInfo.PROPERTY.FIELDS_OF_STUDY, fieldsOfStudy);
        writeField(charWriter, DocInfo.PROPERTY.AUTHORS_NAMES, authorNames);
        writeField(charWriter, DocInfo.PROPERTY.VENUE, venues);
        writeField(charWriter, DocInfo.PROPERTY.JOURNAL_NAME, journalNames);
        writeField(charWriter, DocInfo.PROPERTY.SOURCES, sources);
        charWriter.close();
    }

    /* adds all characters of the specified string to the character map */
    private static void addToCharMap(Map<Integer, Integer> charMap, String field) {
        for (int pos = 0; pos < field.length(); ) {
            int cp = field.codePointAt(pos);
            int count = Character.charCount(cp);
            pos += count;
            charMap.merge(cp, 1, Integer::sum);
        }
    }

    /* writes to file the character map associated with the specified DocInfo property */
    private static void writeField(BufferedWriter writer, DocInfo.PROPERTY property, Map<Integer, Integer> charMap)
            throws IOException {
        Map<Integer, Integer> sortedCharMap = new TreeMap<>(new MapValueComparator(charMap));
        sortedCharMap.putAll(charMap);
        writer.write("--------------------" + property + "------------------\n");
        for (Map.Entry<Integer, Integer> c : sortedCharMap.entrySet()) {
            writer.write(new String(Character.toChars(c.getKey()))  + " " + c.getValue() + "\n");
        }
    }
}
