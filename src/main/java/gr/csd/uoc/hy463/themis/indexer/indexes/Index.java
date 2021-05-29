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
 * This class holds all information related to a specific partial index.
 * It also knows how to write it to the appropriate files.
 */
public class Index {

    /* the ID of this index */
    private final int _indexID;

    /* an Indexer instance */
    private final Indexer _indexer;

    /* store any information about the index in memory. Each term is associated with a TermPostings object
    * which is essentially a list of pairs consisting of:
    * 1) TF = frequency of the term in a relevant document
    * 2) ID = the ID of the relevant document
    */
    private Map<String, TermPostings> __INDEX__ = null;

    /* sorted list of the terms */
    private List<String> __SORTED_TERMS__ = null;

    public Index(Indexer indexer, int id) {
        _indexer = indexer;
        _indexID = id;
        __INDEX__ = new HashMap<>();
    }

    /**
     * This method is responsible for dumping all information held by this index
     * to the filesystem in the 'index_tmp/index_id' folder where 'index_id' is the ID of this index.
     *
     * Specifically for each index two files are created:
     * 1) A partial 'vocabulary' file
     * 2) A partial 'postings' file
     *
     * @throws IOException
     */
    public void dump()
            throws IOException {
        __SORTED_TERMS__ = new ArrayList<>(__INDEX__.keySet());

        /* sort the index before dumping to the disk. This is the only time this index is sorted */
        Collections.sort(__SORTED_TERMS__);

        String vocabularyName = _indexer.getPartialVocabularyPath(_indexID);
        String postingsName = _indexer.getPartialPostingPath(_indexID);

        Files.createDirectories(Paths.get(vocabularyName).getParent());
        Files.createDirectories(Paths.get(postingsName).getParent());
        dumpVocabulary(vocabularyName);
        dumpPostings(postingsName);
    }

    /**
     * Adds the map of term frequencies in the document specified by intID to this index (in memory).
     * Also writes this information to the 'doc_tf' file (one line per document):
     * term1 TF1 term2 TF2 ...
     *
     * Returns the total number of frequencies in the document.
     *
     * @param termTF The map of term frequencies
     * @param docTfWriter
     * @param intID The intID of the related document
     * @throws IOException
     * @return
     */
    public int add(Map<String, Integer> termTF, BufferedWriter docTfWriter, int intID)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        int totalTf = 0;
        for (Map.Entry<String, Integer> entry : termTF.entrySet()) {
            int tf = entry.getValue();
            String key = entry.getKey();
            TermPostings indexStruct = __INDEX__.get(key);
            totalTf += tf;
            if (indexStruct != null) {
                indexStruct.get_postings().add(new Posting(tf, intID));
            }
            else {
                indexStruct = new TermPostings();
                indexStruct.get_postings().add(new Posting(tf, intID));
                __INDEX__.put(key, indexStruct);
            }
            sb.append(key).append(' ').append(tf).append(' ');
        }
        sb.append('\n');
        docTfWriter.write(sb.toString());
        return totalTf;
    }

    /* Dumps the vocabulary data from the index stored in memory to the 'vocabulary' file indicated by the
     * specified filename.
     * This is a normal sequential file. Separated by space are written in each line:
     * 1) Term
     * 2) DF = document frequency of this term = in how many documents this term is found
     *
     * Terms appear in the file lexicographically sorted. Also, DF is only for the total documents that have
     * been parsed in this index.
     * */
    private void dumpVocabulary(String filename)
            throws IOException {
        BufferedWriter vocabularyWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), "UTF-8"));
        for (String term : __SORTED_TERMS__) {
            int df = __INDEX__.get(term).get_df();
            vocabularyWriter.write(term + ' ' + df + '\n');
        }
        vocabularyWriter.close();
    }

    /* Dumps the postings data from the index stored in memory to the 'postings' file indicated by the
     * specified filename.
     * This is random access file. For each term a block of postings is written. Each posting corresponds to a
     * relevant document and consists (in the following order) of:
     * 1) TF = frequency of the term in a relevant document -> int (4 bytes)
     * 2) ID = the ID of the relevant document -> int (4 bytes)
     *
     * The blocks of postings appear in the file sorted based on the order of the terms in the vocabulary file.
     * e.g. 1st block is for the first vocabulary term, 2nd block is for the 2nd term etc.
     *
     * Also, the ID of the documents in each postings block appear in increasing order since they match the
     * document parsing order.
     * */
    private void dumpPostings(String filename)
            throws IOException {
        BufferedOutputStream postingsWriter = new BufferedOutputStream(new FileOutputStream(new RandomAccessFile(filename, "rw").getFD()));
        for (String term : __SORTED_TERMS__) {
            List<Posting> postings = __INDEX__.get(term).get_postings();
            byte[] postingsArray = new byte[postings.size() * Posting.totalSize];
            ByteBuffer postingsBuf = ByteBuffer.wrap(postingsArray);
            int offset = 0;
            for (Posting posting : postings) {
                postingsBuf.putInt(offset, posting.get_tf());
                postingsBuf.putInt(offset + Posting.INTID_OFFSET, posting.get_intID());
                offset += Posting.totalSize;
            }
            postingsWriter.write(postingsArray);
        }
        postingsWriter.close();
    }
}
