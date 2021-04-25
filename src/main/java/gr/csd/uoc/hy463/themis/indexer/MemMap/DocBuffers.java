package gr.csd.uoc.hy463.themis.indexer.MemMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the memory mapped file concept for the documents_id and documents_meta file.
 */
public class DocBuffers extends MemBuffers {

    /**
     * Constructor.
     *
     * @param size The size (fixed) of an entry in the file. Currently it should be:
     *             DocumentMetaEntry.totalSize or DocumentIDEntry.totalSize
     * @throws IOException
     */
    public DocBuffers(String docPath, MemBuffers.MODE mode, int size) throws IOException {
        _documentsPath = docPath;
        createBuffers(createBufferOffsets(size), mode);
    }

    /**
     * Creates the necessary buffers.
     */
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
