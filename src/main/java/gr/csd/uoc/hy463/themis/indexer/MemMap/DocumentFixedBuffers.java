package gr.csd.uoc.hy463.themis.indexer.MemMap;

import java.io.IOException;

/**
 * Class used for memory mapping of DOCUMENTS_META_FILENAME and DOCUMENTS_ID_FILENAME.
 * Each entry in these files has a fixed size DocumentMetaEntry.SIZE and DocumentStringID.SIZE
 * respectively.
 */
public class DocumentFixedBuffers extends MemoryBuffers {

    /**
     * Constructor.
     *
     * @param filePath The full path of the file
     * @param mode READ or WRITE
     * @param blockSize The size of an entry in the file
     * @throws IOException
     */
    public DocumentFixedBuffers(String filePath, MemoryBuffers.MODE mode, int blockSize)
            throws IOException {
        _filePath = filePath;
        createBufferOffsets(blockSize);
        createBuffers(mode);
    }

    /* Creates the necessary offsets that can be used for splitting the file into buffers */
    private void createBufferOffsets(int blockSize) {
        long fileSize = getFileSize();
        int maxBufferSize = (Integer.MAX_VALUE / blockSize) * blockSize;
        int totalBuffers = (int) (1 + fileSize / Integer.MAX_VALUE);
        long[] bufferOffsets = new long[totalBuffers + 1];
        long offset = 0L;
        int i;
        for (i = 0; i < totalBuffers; i++) {
            bufferOffsets[i] = offset;
            offset += maxBufferSize;
        }
        bufferOffsets[i] = fileSize;
        _offsets = bufferOffsets;
    }
}
