package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Class that holds information about an entry in the documents file.
 */
public class DocumentEntry {
    public static int YEAR_SIZE = 2;
    public static int TITLE_SIZE_SIZE = 4;
    public static int AUTHOR_NAMES_SIZE_SIZE = 4;
    public static int AUTHOR_IDS_SIZE_SIZE = 4;
    public static int JOURNAL_NAME_SIZE_SIZE = 2;

    public static int YEAR_OFFSET = 0;
    public static int TITLE_SIZE_OFFSET = 2;
    public static int AUTHOR_NAMES_SIZE_OFFSET = 6;
    public static int AUTHOR_IDS_SIZE_OFFSET = 10;
    public static int JOURNAL_NAME_SIZE_OFFSET = 14;

    public static int TITLE_OFFSET = 16;
    //author names -> variable size
    //author ids -> variable size
    //journal name -> variable size
}
