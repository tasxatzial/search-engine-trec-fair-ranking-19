package gr.csd.uoc.hy463.themis.utils;

import java.io.*;

/**
 * Class is used during the merging of the partial dictionaries. It holds all required
 * information for each partial index (posting file, vocabulary file, partial index id).
 *
 * We can also use it to read the entries in the partial vocabulary file one by one.
 */
public class PartialIndex {
    private RandomAccessFile _postingFile;
    private BufferedReader _vocabularyReader;
    private int _id;

    public PartialIndex(String vocabularyPath, String postingsPath, int indexId)
            throws IOException {
        _postingFile = new RandomAccessFile(postingsPath, "r");
        _vocabularyReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(vocabularyPath), "UTF-8"));
        _id = indexId;
    }

    /**
     * Returns the next vocabulary entry from the vocabulary file each time the
     * method is called. Returns null if end of file is reached.
     *
     * @return
     * @throws IOException
     */
    public VocabularyEntry readNextVocabularyEntry() throws IOException {
        String line;
        String[] fields;

        line = _vocabularyReader.readLine();
        if (line != null) {
            fields = line.split(" ");
            return new VocabularyEntry(fields[0], Integer.parseInt(fields[1]), Long.parseLong(fields[2]), _id);
        }
        return null;
    }

    public RandomAccessFile get_postingFile() {
        return _postingFile;
    }

    public void closeFiles() throws IOException {
        _postingFile.close();
        _vocabularyReader.close();
    }
}
