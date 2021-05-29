package gr.csd.uoc.hy463.themis.indexer.MemMap;

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
public abstract class MemoryBuffers {
    public enum MODE {
        READ, WRITE
    }
    protected List<ByteBuffer> _buffers;
    protected List<Long> _offsets;
    protected RandomAccessFile _file;
    protected String _filePath;

    /**
     * Creates a list of ByteBuffers using the specified list of bufferOffsets. These are the file offsets
     * that determine the size of each ByteBuffer. For example if the list is (0, 10, 20), two ByteBuffers
     * will be created, one for bytes 1-10 and one for bytes 11-20.
     *
     * @param bufferOffsets A list of file offsets
     * @param mode READ or WRITE
     * @throws IOException
     */
    protected void createBuffers(List<Long> bufferOffsets, MemoryBuffers.MODE mode)
            throws IOException {
        if (bufferOffsets.size() < 2) {
            throw new IllegalArgumentException("offsets size < 2");
        }
        _buffers = new ArrayList<>();
        _offsets = new ArrayList<>();
        FileChannel.MapMode openMode;
        if (mode == MODE.WRITE) {
            _file = new RandomAccessFile(_filePath, "rw");
            openMode = FileChannel.MapMode.READ_WRITE;
        }
        else {
            _file = new RandomAccessFile(_filePath, "r");
            openMode = FileChannel.MapMode.READ_ONLY;
        }
        FileChannel documentsChannel = _file.getChannel();
        for (int i = 0; i < bufferOffsets.size() - 1; i++) {
            MappedByteBuffer buffer = documentsChannel.map(openMode, bufferOffsets.get(i),
                    bufferOffsets.get(i + 1) - bufferOffsets.get(i)).load();
            _buffers.add(buffer);
            _offsets.add(bufferOffsets.get(i));

        }
        _offsets.add(bufferOffsets.get(bufferOffsets.size() - 1));
        documentsChannel.close();
    }

    /**
     * Returns the ByteBuffer that contains specified offset. It also sets the
     * position of the ByteBuffer so that we can start reading from the specified offset.
     *
     * @param offset
     * @return
     */
    public ByteBuffer getBufferLong(long offset) {
        if (offset < 0 || offset >= _offsets.get(_offsets.size() - 1)) {
            return null;
        }
        for (int i = _offsets.size() - 2; i >= 0; i--) {
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
     * Unmaps the current file from memory and closes all the corresponding files
     *
     * @throws IOException
     */
    public void close()
            throws IOException {
        for (int i = 0; i < _buffers.size(); i++) {
            CloseDirectBuffer.closeDirectBuffer(_buffers.get(i));
        }
        _file.close();
        _offsets.clear();
    }

    /* Returns the total number of Bytebuffers for the current mapped file */
    protected final int getTotalBuffers() {
        return _buffers.size();
    }

    /* Returns the ByteBuffer with the specified index */
    protected final ByteBuffer getBuffer(int index) {
        return _buffers.get(index);
    }

    /* Returns the size of the specified buffer */
    protected final int getBufferSize(ByteBuffer buffer) {
        int i = _buffers.indexOf(buffer);
        return (int) (_offsets.get(i + 1) - _offsets.get(i));
    }

    /* Returns the size of the current mapped file */
    protected final long getDocumentsSize() {
        return new File(_filePath).length();
    }
}
