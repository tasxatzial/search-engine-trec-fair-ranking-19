package gr.csd.uoc.hy463.themis.indexer.MemMap;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Provides general functionality for memory-mapped files.
 */
public abstract class MemoryBuffers {
    public enum MODE {
        READ, WRITE
    }
    protected ByteBuffer[] _buffers;
    protected long[] _offsets = new long[0];
    protected RandomAccessFile _file;
    protected String _filePath;

    /**
     * Initializes the array of _buffers using the array of _offsets. These are the file offsets
     * that determine the size of each buffer. For example if the offsets array is (0, 10, 20), two buffers
     * will be created, one for bytes 0-9 and one for bytes 10-19.
     *
     * @param mode READ or WRITE
     * @throws IOException
     */
    protected void createBuffers(MemoryBuffers.MODE mode)
            throws IOException {
        if (_offsets.length < 2) {
            throw new IllegalArgumentException("offsets size < 2");
        }
        _buffers = new ByteBuffer[_offsets.length - 1];
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
        for (int i = 0; i < _offsets.length - 1; i++) {
            MappedByteBuffer buffer = documentsChannel.map(openMode, _offsets[i], getBufferSize(i)).load();
            _buffers[i] = buffer;
        }
        documentsChannel.close();
    }

    /**
     * Returns the buffer that contains the data at the given file offset and sets its position
     * at the start of the data.
     *
     * @param offset
     * @return
     */
    public ByteBuffer getMemBuffer(long offset) {
        if (offset < 0 || offset >= _offsets[_offsets.length - 1]) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = _offsets.length - 2; i >= 0; --i) {
            long currentOffset = _offsets[i];
            if (offset >= currentOffset) {
                ByteBuffer buffer = _buffers[i];
                buffer.position((int) (offset - currentOffset));
                return buffer;
            }
        }
        return null;
    }

    /**
     * Unmaps the memory-mapped file and closes all files
     *
     * @throws IOException
     */
    public void close()
            throws IOException {
        for (ByteBuffer buffer : _buffers) {
            CloseDirectBuffer.closeDirectBuffer(buffer);
        }
        _file.close();
        _offsets = new long[0];
    }

    /* Returns the size of buffer[index] (in bytes) */
    protected final int getBufferSize(int index) {
        return (int) (_offsets[index + 1] - _offsets[index]);
    }

    /* Returns the buffer[index] */
    protected ByteBuffer getBuffer(int index) {
        return _buffers[index];
    }

    /* Returns the size of the memory mapped file (in bytes) */
    protected final long getFileSize() {
        return new File(_filePath).length();
    }

    /* Returns the number of buffers */
    protected int totalBuffers() {
        return _buffers.length;
    }
}
