package gr.csd.uoc.hy463.themis.indexer.MemMap;

import gr.csd.uoc.hy463.themis.indexer.model.DocumentMetaEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the memory mapped file concept for the documents file.
 */
public class DocumentFileBuffers extends MemBuffers {

    /**
     * Constructor.
     *
     * @param docBuffers Object that corresponds to the memory mapped file documents_meta
     * @throws IOException
     */
    public DocumentFileBuffers(String documentsPath, MemBuffers.MODE mode, MemBuffers docBuffers) throws IOException {
        _documentsPath = documentsPath;
        createBuffers(createBufferOffsets(docBuffers), mode);
    }

    /**
     * Creates the necessary buffers.
     */
    private List<Long> createBufferOffsets(MemBuffers docBuffers) {
        int maxBufferSize = Integer.MAX_VALUE;
        List<Long> bufferOffsets = new ArrayList<>();
        bufferOffsets.add(0L);
        long totalDocumentsSize = 0;
        long documentOffset = 0;
        for (int i = 0; i < docBuffers.getTotalBuffers(); i++) {
            ByteBuffer buffer = docBuffers.getBuffer(i);
            int bufferSize = docBuffers.getBufferSize(buffer);
            for (int j = 0; j < bufferSize; j += DocumentMetaEntry.totalSize) {
                int documentSize = buffer.getInt(j + DocumentMetaEntry.DOCUMENT_SIZE_OFFSET);
                if (documentSize > maxBufferSize - totalDocumentsSize) {
                    bufferOffsets.add(documentOffset);
                    totalDocumentsSize = documentSize;
                }
                else {
                    totalDocumentsSize += documentSize;
                }
                documentOffset += documentSize;
            }
        }
        bufferOffsets.add(documentOffset);

        return bufferOffsets;
    }
}
