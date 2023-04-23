package gr.csd.uoc.hy463.themis.indexer.indexes;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.TermPostings;
import gr.csd.uoc.hy463.themis.indexer.model.Posting;

/**
 * This class stores all information about a partial index.
 * It also knows how to dump it to the appropriate files.
 */
public class Index {
    private final int _indexID;
    private final Indexer _indexer;
    private List<String> __SORTED_TERMS__ = null;

    /* use a map to store the index data */
    private Map<String, TermPostings> __INDEX__ = null;

    public Index(Indexer indexer, int ID) {
        _indexer = indexer;
        _indexID = ID;
        __INDEX__ = new HashMap<>();
    }

    /**
     * Dumps all index data to 'INDEX_TMP_PATH/index_id/' and creates the VOCABULARY_FILENAME
     * and POSTINGS_FILENAME files in the same directory.
     *
     * @throws IOException
     */
    public void dump()
            throws IOException {
        __SORTED_TERMS__ = new ArrayList<>(__INDEX__.keySet());

        /* sort the terms right before dumping to disk */
        Collections.sort(__SORTED_TERMS__);

        String vocabularyPath = _indexer.getPartialVocabularyPath(_indexID);
        String postingsPath = _indexer.getPartialPostingsPath(_indexID);

        Files.createDirectories(Paths.get(vocabularyPath).getParent());
        Files.createDirectories(Paths.get(postingsPath).getParent());
        dumpVocabulary(vocabularyPath);
        dumpPostings(postingsPath);
    }

    /**
     * Adds to this index the map of [term -> TF] for the document that has the given (int) doc ID.
     * Also writes this information to 'INDEX_TMP_PATH/doc_tf' as a sequence of <term1, TF1, term2, TF2, ...>
     * (one line per document)
     *
     * Returns the sum of all frequencies in the document, in other words, the total number of tokens.
     *
     * @param TFMap Map of term frequencies
     * @param docTFWriter
     * @param docID ID of the relevant document
     * @throws IOException
     * @return
     */
    public int add(Map<String, Integer> TFMap, BufferedWriter docTFWriter, int docID)
            throws IOException {
        StringBuilder SB = new StringBuilder();
        int TFSum = 0;
        for (Map.Entry<String, Integer> entry : TFMap.entrySet()) {
            int TF = entry.getValue();
            String term = entry.getKey();
            TermPostings termPostings = __INDEX__.get(term);
            TFSum += TF;
            if (termPostings != null) {
                termPostings.addPosting(new Posting(TF, docID));
            }
            else {
                termPostings = new TermPostings();
                termPostings.addPosting(new Posting(TF, docID));
                __INDEX__.put(term, termPostings);
            }
            SB.append(term).append(' ').append(TF).append(' ');
        }
        SB.append('\n');
        docTFWriter.write(SB.toString());
        return TFSum;
    }

    /* Dumps the appropriate vocabulary data to the given filePath (normal sequential file).
     * Each line contains:
     * 1) Term
     * 2) DF (document frequency of this term)
     *
     * Terms are saved in lexicographic order.
     * */
    private void dumpVocabulary(String filePath)
            throws IOException {
        BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), "UTF-8"));
        for (String term : __SORTED_TERMS__) {
            int DF = __INDEX__.get(term).getDF();
            vocabularyWriter.write(term + ' ' + DF + '\n');
        }
        vocabularyWriter.close();
    }

    /* Dumps the appropriate postings data to the given filePath (random access file).
     * For each term, a block of postings is written to the file. Each posting consists of:
     * 1) TF (frequency of the term in the relevant document)
     * 2) doc ID (int ID of the relevant document)
     *
     * The blocks of postings in the file will be sorted based on the sorting of terms:
     * 1st block is for the 1st vocabulary term, 2nd block is for the 2nd term etc.
     *
     * The ID of the documents in each block will appear sorted based on the document parsing order.
     * */
    private void dumpPostings(String filePath)
            throws IOException {
        BufferedOutputStream postingsWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(filePath, "rw").getFD()));
        for (String term : __SORTED_TERMS__) {
            List<Posting> postings = __INDEX__.get(term).getPostings();
            byte[] postingsArray = new byte[postings.size() * Posting.SIZE];
            ByteBuffer postingsBuf = ByteBuffer.wrap(postingsArray);
            int offset = 0;
            for (Posting posting : postings) {
                postingsBuf.putInt(offset, posting.getTF());
                postingsBuf.putInt(offset + Posting.DOCID_OFFSET, posting.getDocID());
                offset += Posting.SIZE;
            }
            postingsWriter.write(postingsArray);
        }
        postingsWriter.close();
    }
}
