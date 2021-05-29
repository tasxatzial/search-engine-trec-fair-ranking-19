package gr.csd.uoc.hy463.themis.indexer.MemMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the memory mapped file concept for the 'documents_id' and 'documents_meta' files
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
        createBuffers(createBufferOffsets(blockSize), mode);
    }

    /* Creates the necessary buffer offsets that can be used for splitting the file into ByteBuffers */
    private List<Long> createBufferOffsets(int size) {
        long fileSize = getDocumentsSize();
        int maxBufferSize = (Integer.MAX_VALUE / size) * size;
        long totalBuffers = 1 + fileSize / Integer.MAX_VALUE;
        long offset = 0L;
        List<Long> bufferOffsets = new ArrayList<>();
        for (int i = 0; i < totalBuffers; i++) {
            bufferOffsets.add(offset);
            offset += maxBufferSize;
        }
        bufferOffsets.add(fileSize);

        return bufferOffsets;
    }
}
