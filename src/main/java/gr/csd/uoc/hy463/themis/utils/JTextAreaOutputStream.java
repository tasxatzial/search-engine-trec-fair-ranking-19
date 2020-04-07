package gr.csd.uoc.hy463.themis.utils;

import javax.swing.*;
import java.io.OutputStream;

public class JTextAreaOutputStream extends OutputStream {
    private final JTextArea destination;

    public JTextAreaOutputStream(JTextArea destination) {
        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }
        this.destination = destination;
    }

    @Override
    public void write(byte[] buffer, int offset, int length) {
        final String text = new String(buffer, offset, length);
        SwingUtilities.invokeLater(() -> destination.append(text));
    }

    @Override
    public void write(int b) {
        write(new byte [] {(byte) b}, 0, 1);
    }
}
