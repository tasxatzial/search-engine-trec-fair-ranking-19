package gr.csd.uoc.hy463.themis.indexer;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for implementing the memory mapped file concept. Currently used for loading
 * the documents file into memory.
 */
public class DocumentBuffers {
    private List<ByteBuffer> _buffers;
    private List<Long> _offsets;
    private RandomAccessFile _documents;

    /**
     * Creates the appropriate buffers so that the file specified by filename is treated
     * as a memory mapped file
     * @param documentsBuffersOffsets A list of offsets to file positions
     * @param filename The name of the file that we want to treat as a memory mapped file
     * @throws IOException
     */
    public DocumentBuffers(List<Long> documentsBuffersOffsets, String filename) throws IOException {
        if (documentsBuffersOffsets.size() < 2) {
            throw new IllegalArgumentException("offsets size < 2");
        }
        _buffers = new ArrayList<>();
        _offsets = new ArrayList<>();
        _documents = new RandomAccessFile(filename, "r");
        FileChannel documentsChannel = _documents.getChannel();
        for (int i = 0; i < documentsBuffersOffsets.size() - 1; i++) {
            ByteBuffer buffer = documentsChannel.map(FileChannel.MapMode.READ_ONLY, documentsBuffersOffsets.get(i),
                    documentsBuffersOffsets.get(i + 1) - documentsBuffersOffsets.get(i));
            _buffers.add(buffer);
            _offsets.add(documentsBuffersOffsets.get(i));
        }
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
