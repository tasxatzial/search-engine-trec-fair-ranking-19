package gr.csd.uoc.hy463.themis.indexer.model;

/**
 * Represents info about an entry in DOCUMENTS_FILENAME.
 *
 * For example, the size of a title is 4 bytes and can be found in position 2 of the entry.
 * The title itself starts at position 16.
 */
public class DocumentEntry {
    /* Year (short => 2 bytes) */
    public static int YEAR_SIZE = 2;
    public static int YEAR_OFFSET = 0;

    /* Title size (int => 4 bytes) */
    public static int TITLE_SIZE_SIZE = 4;
    public static int TITLE_SIZE_OFFSET = 2;

    /* Author_1,Author_2, ...,Author_k size (int => 4 bytes) */
    public static int AUTHOR_NAMES_SIZE_SIZE = 4;
    public static int AUTHOR_NAMES_SIZE_OFFSET = 6;

    /* AuthorID_1, AuthorID_2, ... ,Author_ID_k size (int => 4 bytes) */
    public static int AUTHOR_IDS_SIZE_SIZE = 4;
    public static int AUTHOR_IDS_SIZE_OFFSET = 10;

    /* Journal name size (short => 2 bytes / UTF-8) */
    public static int JOURNAL_NAME_SIZE_SIZE = 2;
    public static int JOURNAL_NAME_SIZE_OFFSET = 14;

    /* Title offset */
    public static int TITLE_OFFSET = 16;

    /* Author names offset -> unknown but comes after title */

    /* Author ids offset -> unknown but comes after author names */

    /* Journal name offset -> unknown but comes after author ids */
}
