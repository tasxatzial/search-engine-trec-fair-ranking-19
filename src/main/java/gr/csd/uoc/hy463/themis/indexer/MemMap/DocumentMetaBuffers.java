package gr.csd.uoc.hy463.themis.indexer.MemMap;

import gr.csd.uoc.hy463.themis.indexer.model.DocumentMetaEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the memory mapped file concept for the documents_meta file.
 */
public class DocumentMetaBuffers extends MemBuffers {

    /**
     * Creates the appropriate buffers so that the documents_meta file is treated
     * as a memory mapped file.
     * @throws IOException
     */
    public DocumentMetaBuffers(DocumentBuffers.MODE mode) throws IOException {
        super();
        _documentsPath = _config.getIndexPath() + "/" + _config.getDocumentsMetaFileName();
        createBuffers(createBufferOffsets(), mode);
    }

    /**
     * Creates the appropriate buffers so that documents_meta file is treated as a memory mapped file.
     */
    private List<Long> createBufferOffsets() {
        int maxBufferSize = (Integer.MAX_VALUE / DocumentMetaEntry.totalSize) * DocumentMetaEntry.totalSize;
        List<Long> bufferOffsets = new ArrayList<>();
        bufferOffsets.add(0L);
        long fileSize = getDocumentsSize();
        long totalDocumentsSize = 0;
        long documentOffset = 0;
        for (long j = 0; j < fileSize; j += DocumentMetaEntry.totalSize) {
            if (DocumentMetaEntry.totalSize > maxBufferSize - totalDocumentsSize) {
                bufferOffsets.add(documentOffset);
                totalDocumentsSize = DocumentMetaEntry.totalSize;
            }
            else {
                totalDocumentsSize += DocumentMetaEntry.totalSize;
            }
            documentOffset += DocumentMetaEntry.totalSize;
        }
        bufferOffsets.add(documentOffset);

        return bufferOffsets;
    }
}
