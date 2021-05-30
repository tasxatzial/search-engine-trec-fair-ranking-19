package gr.csd.uoc.hy463.themis.indexer.MemMap;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implements the memory mapped file concept.
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
     * Creates a list of ByteBuffers using the specified list of bufferOffsets. These are the file offsets
     * that determine the size of each ByteBuffer. For example if the list is (0, 10, 20), two ByteBuffers
     * will be created, one for bytes 1-10 and one for bytes 11-20.
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
     * Returns the ByteBuffer that contains specified offset. It also sets the
     * position of the ByteBuffer so that we can start reading from the specified offset.
     *
     * @param offset
     * @return
     */
    public ByteBuffer memBuffer(long offset) {
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
     * Unmaps the current file from memory and closes all files
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

    /* Returns the size of the buffer at the specified index */
    protected final int getBufferSize(int index) {
        return (int) (_offsets[index + 1] - _offsets[index]);
    }

    /* Returns the size of the current memory mapped file */
    protected final long getFileSize() {
        return new File(_filePath).length();
    }

    /* Returns the number of buffers */
    protected int totalBuffers() {
        return _buffers.length;
    }

    /* Returned the buffer at the specified index */
    protected ByteBuffer getBuffer(int index) {
        return _buffers[index];
    }
}
