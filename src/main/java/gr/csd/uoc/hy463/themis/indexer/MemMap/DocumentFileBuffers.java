package gr.csd.uoc.hy463.themis.indexer.MemMap;

import gr.csd.uoc.hy463.themis.indexer.model.DocumentMetaEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the memory mapped file concept for the 'documents' file.
 */
public class DocumentFileBuffers extends MemoryBuffers {

    /**
     * Constructor.
     *
     * @param documentsPath The full path of the 'documents' file
     * @param mode READ or WRITE
     * @param documentMetaBuffers Object that corresponds to the memory mapped file 'documents_meta'
     * @throws IOException
     */
    public DocumentFileBuffers(String documentsPath, MemoryBuffers.MODE mode, MemoryBuffers documentMetaBuffers)
            throws IOException {
        _filePath = documentsPath;
        createBuffers(createBufferOffsets(documentMetaBuffers), mode);
    }

    /* Creates the necessary buffer offsets that can be used for splitting the file into ByteBuffers */
    private List<Long> createBufferOffsets(MemoryBuffers documentMetaBuffers) {
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
