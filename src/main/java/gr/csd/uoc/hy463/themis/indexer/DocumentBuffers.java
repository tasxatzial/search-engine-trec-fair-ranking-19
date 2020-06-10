package gr.csd.uoc.hy463.themis.indexer;

import gr.csd.uoc.hy463.themis.indexer.model.DocumentMetaEntry;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for implementing the memory mapped file concept
 */
public class DocumentBuffers {
    public enum MODE {
        READ, WRITE
    }
    private List<ByteBuffer> _buffers;
    private List<Long> _offsets;
    private RandomAccessFile _documents;

    /**
     * Creates the appropriate buffers so that the file specified by filename is treated
     * as a memory mapped file. Use this constructor for the documents file.
     * @param documentBufferOffsets A list of offsets to file positions
     * @param filename The name of the file that we want to treat as a memory mapped file
     * @throws IOException
     */
    public DocumentBuffers(List<Long> documentBufferOffsets, String filename, DocumentBuffers.MODE mode) throws IOException {
        if (documentBufferOffsets.size() < 2) {
            throw new IllegalArgumentException("offsets size < 2");
        }
        _buffers = new ArrayList<>();
        _offsets = new ArrayList<>();
        FileChannel.MapMode openMode;
        if (mode == MODE.WRITE) {
            _documents = new RandomAccessFile(filename, "rw");
            openMode = FileChannel.MapMode.READ_WRITE;
        }
        else {
            _documents = new RandomAccessFile(filename, "r");
            openMode = FileChannel.MapMode.READ_ONLY;
        }
        FileChannel documentsChannel = _documents.getChannel();
        for (int i = 0; i < documentBufferOffsets.size() - 1; i++) {
            ByteBuffer buffer = documentsChannel.map(openMode, documentBufferOffsets.get(i),
                    documentBufferOffsets.get(i + 1) - documentBufferOffsets.get(i));
            _buffers.add(buffer);
            _offsets.add(documentBufferOffsets.get(i));
        }
    }

    /**
     * Creates the appropriate buffers so that the file specified by filename is treated
     * as a memory mapped file. Use this constructor for the documents meta file.
     * @param filename
     * @param mode
     * @throws IOException
     */
    public DocumentBuffers(String filename, DocumentBuffers.MODE mode) throws IOException {
        this(createDocumentBufferOffsets(filename), filename, mode);
    }

    private static List<Long> createDocumentBufferOffsets(String filename) {
        int maxBufferSize = (Integer.MAX_VALUE / DocumentMetaEntry.totalSize) * DocumentMetaEntry.totalSize;
        List<Long> documentBufferOffsets = new ArrayList<>();
        long fileSize = new File(filename).length();
        long size = fileSize;
        long i = 0;
        while (size > 0) {
            documentBufferOffsets.add(i);
            i += maxBufferSize;
            size -= maxBufferSize;
        }
        documentBufferOffsets.add(fileSize);
        System.out.println(documentBufferOffsets);
        return documentBufferOffsets;
    }

    /**
     * Returns the buffer that corresponds to the specified offset. It also sets the
     * position of the buffer so that we can start reading from this offset.
     * @param offset
     * @return
     */
    public ByteBuffer getBuffer(long offset) {
        for (int i = _offsets.size() - 1; i >= 0; i--) {
            if (offset >= _offsets.get(i)) {
                _buffers.get(i).position((int) (offset - _offsets.get(i)));
                return _buffers.get(i);
            }
        }
        return null;
    }

    /**
     * Clears all resources and closes all files associated with this object.
     * @throws IOException
     */
    public void close() throws IOException {
        _documents.close();
        _buffers.clear();
        _offsets.clear();
    }
}
