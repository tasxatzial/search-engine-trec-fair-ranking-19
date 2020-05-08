package gr.csd.uoc.hy463.themis.utils;

/**
 * Class that holds basic information about a document entry.
 * Currently it holds the size and the offset of each of the fields in a document entry.
 */
public class DocumentEntry {
    public static int ID_SIZE = 40;
    public static int PAGERANK_SIZE = 8;
    public static int WEIGHT_SIZE = 8;
    public static int LENGTH_SIZE = 4;
    public static int AVG_AUTHOR_RANK_SIZE = 8;
    public static int YEAR_SIZE = 2;
    public static int TITLE_SIZE_SIZE = 4;
    public static int AUTHOR_NAMES_SIZE_SIZE = 4;
    public static int AUTHOR_IDS_SIZE_SIZE = 4;
    public static int JOURNAL_NAME_SIZE_SIZE = 2;
    //title -> variable size
    //author names -> variable size
    //author ids -> variable size
    //journal name -> variable size

    public static int ID_OFFSET = 0;
    public static int PAGERANK_OFFSET = 40;
    public static int WEIGHT_OFFSET = 48;
    public static int LENGTH_OFFSET = 56;
    public static int AVG_AUTHOR_RANK_OFFSET = 60;
    public static int YEAR_OFFSET = 68;
    public static int TITLE_SIZE_OFFSET = 70;
    public static int AUTHOR_NAMES_SIZE_OFFSET = 74;
    public static int AUTHOR_IDS_SIZE_OFFSET = 78;
    public static int JOURNAL_NAME_SIZE_OFFSET = 82;
    public static int TITLE_OFFSET = 84;
    //author names
    //author ids
    //journal name
}
