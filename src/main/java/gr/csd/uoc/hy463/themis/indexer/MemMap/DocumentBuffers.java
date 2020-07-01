package gr.csd.uoc.hy463.themis.indexer.MemMap;

import gr.csd.uoc.hy463.themis.indexer.model.DocumentMetaEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the memory mapped file concept for the documents file.
 */
public class DocumentBuffers extends MemBuffers {

    /**
     * Creates the appropriate buffers so that the documents file is treated
     * as a memory mapped file.
     * @param documentMetaBuffers Object that corresponds to a memory mapped documents_meta file
     * @throws IOException
     */
    public DocumentBuffers(String documentsPath, DocumentBuffers.MODE mode, DocumentMetaBuffers documentMetaBuffers) throws IOException {
        _documentsPath = documentsPath;
        createBuffers(createBufferOffsets(documentMetaBuffers), mode);
    }

    /**
     * Creates the appropriate buffers so that the documents file is treated as a memory mapped file.
     */
    private List<Long> createBufferOffsets(DocumentMetaBuffers documentMetaBuffers) {
        int maxBufferSize = Integer.MAX_VALUE;
        List<Long> bufferOffsets = new ArrayList<>();
        bufferOffsets.add(0L);
        long totalDocumentsSize = 0;
        long documentOffset = 0;
        for (int i = 0; i < documentMetaBuffers.getTotalBuffers(); i++) {
            ByteBuffer buffer = documentMetaBuffers.getBuffer(i);
            int bufferSize = documentMetaBuffers.getBufferSize(buffer);
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
