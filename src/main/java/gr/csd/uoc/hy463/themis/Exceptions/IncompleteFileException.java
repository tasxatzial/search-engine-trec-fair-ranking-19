package gr.csd.uoc.hy463.themis.Exceptions;

public class IncompleteFileException extends Exception {
    public IncompleteFileException(String fileName) {
        super(fileName + " is incomplete");
    }
}
