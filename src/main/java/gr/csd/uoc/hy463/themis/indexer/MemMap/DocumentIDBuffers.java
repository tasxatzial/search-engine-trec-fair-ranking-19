package gr.csd.uoc.hy463.themis.indexer.MemMap;

import gr.csd.uoc.hy463.themis.indexer.model.DocumentIDEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the memory mapped file concept for the documents_docID file.
 */
public class DocumentIDBuffers extends MemBuffers {

    /**
     * Creates the appropriate buffers so that the documents_docID file is treated as a memory mapped file.
     * @throws IOException
     */
    public DocumentIDBuffers(String documentsDocIDPath, DocumentBuffers.MODE mode) throws IOException {
        _documentsPath = documentsDocIDPath;
        createBuffers(createBufferOffsets(), mode);
    }

    /**
     * Creates the appropriate buffers so that documents_docID file is treated as a memory mapped file.
     */
    private List<Long> createBufferOffsets() {
        long fileSize = getDocumentsSize();
        int maxBufferSize = (Integer.MAX_VALUE / DocumentIDEntry.totalSize) * DocumentIDEntry.totalSize;
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
