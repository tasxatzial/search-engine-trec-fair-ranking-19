package gr.csd.uoc.hy463.themis.utils;

/**
 * Class that holds basic information about a document entry.
 * Currently it holds:
 * the size of docId (bytes),
 * the size of year (bytes),
 * the size of a variable length field (bytes)
 */
public class DocumentEntry {
    public static int ID_SIZE = 40;
    public static int VAR_FIELD_SIZE = 2;
    public static int YEAR_SIZE = 2;
}
