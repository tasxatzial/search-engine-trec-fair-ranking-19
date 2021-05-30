package gr.csd.uoc.hy463.themis.indexer.MemMap;

import java.io.IOException;

/**
 * Implements the memory mapped file concept for the 'documents_id' and 'documents_meta' files.
 *
 */
public class DocumentBuffers extends MemoryBuffers {

    /**
     * Constructor.
     *
     * @param filePath The full path of the file we want memory mapped
     * @param mode READ or WRITE
     * @param blockSize The size of an entry in the file. It should one of:
     *             DocumentMetaEntry.totalSize, DocumentIDEntry.totalSize
     * @throws IOException
     */
    public DocumentBuffers(String filePath, MemoryBuffers.MODE mode, int blockSize)
            throws IOException {
        _filePath = filePath;
        createBufferOffsets(blockSize);
        createBuffers(mode);
    }

    /* Creates the necessary buffer offsets that can be used for splitting the file into ByteBuffers */
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
