package gr.csd.uoc.hy463.themis.indexer.MemMap;

import gr.csd.uoc.hy463.themis.config.Config;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the memory mapped file concept.
 */
public abstract class MemBuffers {
    public enum MODE {
        READ, WRITE
    }
    protected List<ByteBuffer> _buffers;
    protected List<Long> _offsets;
    protected RandomAccessFile _documents;
    protected Config _config;
    protected String _documentsPath;

    protected MemBuffers(Config config) {
        _config = config;
    }

    /**
     * Creates the appropriate buffers so that the file specified by filename is treated
     * as a memory mapped file.
     * @param documentBufferOffsets A list of file offsets
     * @throws IOException
     */
    protected void createBuffers(List<Long> documentBufferOffsets, DocumentBuffers.MODE mode) throws IOException {
        if (documentBufferOffsets.size() < 2) {
            throw new IllegalArgumentException("offsets size < 2");
        }
        _buffers = new ArrayList<>();
        _offsets = new ArrayList<>();
        FileChannel.MapMode openMode;
        if (mode == MODE.WRITE) {
            _documents = new RandomAccessFile(_documentsPath, "rw");
            openMode = FileChannel.MapMode.READ_WRITE;
        }
        else {
            _documents = new RandomAccessFile(_documentsPath, "r");
            openMode = FileChannel.MapMode.READ_ONLY;
        }
        FileChannel documentsChannel = _documents.getChannel();
        for (int i = 0; i < documentBufferOffsets.size() - 1; i++) {
            MappedByteBuffer buffer = documentsChannel.map(openMode, documentBufferOffsets.get(i),
                    documentBufferOffsets.get(i + 1) - documentBufferOffsets.get(i)).load();
            _buffers.add(buffer);
            _offsets.add(documentBufferOffsets.get(i));

        }
        _offsets.add(documentBufferOffsets.get(documentBufferOffsets.size() - 1));
        documentsChannel.close();
    }

    /**
     * Returns the buffer that corresponds to the specified offset. It also sets the
     * position of the buffer so that we can start reading from this offset.
     * @param offset
     * @return
     */
    public ByteBuffer getBufferLong(long offset) {
        for (int i = _offsets.size() - 1; i >= 0; i--) {
            long currentOffset = _offsets.get(i);
            if (offset >= currentOffset) {
                ByteBuffer buffer = _buffers.get(i);
                buffer.position((int) (offset - currentOffset));
                return buffer;
            }
        }
        return null;
    }

    /**
     * Clears all resources and closes all files associated with this object.
     * @throws IOException
     */
    public void close() throws IOException {
        for (int i = 0; i < _buffers.size(); i++) {
            CloseDirectBuffer.closeDirectBuffer(_buffers.get(i));
        }
        _documents.close();
        _offsets.clear();
    }

    /**
     * Returns the number of buffers that this object uses to map a file into memory
     * @return
     */
    protected final int getTotalBuffers() {
        return _buffers.size();
    }

    /**
     * Returns the buffer that has the specified index
     * @param index
     * @return
     */
    protected final ByteBuffer getBuffer(int index) {
        return _buffers.get(index);
    }

    /**
     * Returns the size of the specified buffer
     * @param buffer
     * @return
     */
    protected final int getBufferSize(ByteBuffer buffer) {
        int i = _buffers.indexOf(buffer);
        return (int) (_offsets.get(i + 1) - _offsets.get(i));
    }

    /**
     * Returns the size of the file that corresponds to the current memory mapped file
     * @return
     */
    protected final long getDocumentsSize() {
        return new File(_documentsPath).length();
    }
}
