package gr.csd.uoc.hy463.themis.ui;

import gr.csd.uoc.hy463.themis.utils.JTextAreaOutputStream;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class View extends JFrame {
    /* STATE is INDEX for the create/load index view
       STATE is SEARCH for the search view */
    public enum STATE {
        INDEX, SEARCH
    }

    private STATE _state;

    /* The main menu bar */
    private JMenuBar _menu;

    /* The "create index" menu item */
    private JMenuItem _createIndex;

    /* The "load index" menu item */
    private JMenuItem _loadIndex;

    /* The "query collection" menu item */
    private JMenuItem _queryCollection;

    /* The font of any text except the menu/title items */
    private Font _textFont;

    /* Title area (top) */
    private JLabel _titleArea;

    /* Everything sits on top of this pane */
    private JLayeredPane _mainPane;

    /* Contains the text area that shows the results of the indexing/searching tasks */
    private JScrollPane _resultsPane;

    /* shows results of indexing/searching */
    private JTextArea _resultsArea;

    /* The search button in search view */
    private JButton _searchButton;

    /* the search input area in search view */
    private JTextField _searchField;

    /* Prints the create/load index and search results */
    private JTextAreaOutputStream _viewOut;

    public View() {
        initMenu();
        pack();
        setTitle("Themis search engine v0.1");
        setSize(800, 600); //initial frame size
        _textFont = new Font("SansSerif", Font.PLAIN, 16);
        _mainPane = new JLayeredPane();
        initTitleArea();
        getContentPane().add(_mainPane);
    }

    /* Initializes the menu bar */
    private void initMenu() {

        /* index menu */
        JMenu index = new JMenu("Index");
        _createIndex = new JMenuItem("Create index");
        _loadIndex = new JMenuItem("Load index");
        index.add(_createIndex);
        index.add(_loadIndex);

        /* search menu */
        JMenu search = new JMenu("Search");
        _queryCollection = new JMenuItem("Query collection");
        search.add(_queryCollection);

        /* main menu bar */
        _menu = new JMenuBar();
        _menu.add(index);
        _menu.add(search);

        setJMenuBar(_menu);
    }

    /* Creates the title label */
    private void initTitleArea() {
        _titleArea = new JLabel("Themis");
        _titleArea.setFont(new Font("SansSerif", Font.PLAIN, 24));
        _titleArea.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));
        setTitleAreaBounds();
        _mainPane.add(_titleArea);
    }

    /**
     * Sets the proper bounds of the title area
     */
    public void setTitleAreaBounds() {
        Dimension frameDim = getBounds().getSize();
        int titleAreaWidth = frameDim.width - getInsets().left - getInsets().right;
        int titleAreaHeight = 50;
        int titleAreaX = 0;
        int titleAreaY = 0;
        _titleArea.setBounds(titleAreaX, titleAreaY, titleAreaWidth, titleAreaHeight);
        _titleArea.setHorizontalAlignment(SwingConstants.CENTER);
    }

    /**
     * Modifies the view when the "create index" menu item is clicked.
     */
    public void initIndexView() {
        if (_state == STATE.INDEX) {
            clearResultsArea();
            return;
        }
        if (_state == STATE.SEARCH) {
            _mainPane.remove(_searchButton);
            _mainPane.remove(_searchField);
            _searchField = null;
            _searchButton = null;
            _state = STATE.INDEX;
            clearResultsArea();
            setIndexViewBounds();
            return;
        }
        _state = STATE.INDEX;
        initResultsPane();
        setIndexViewBounds();
    }

    /**
     * Sets the proper bounds of the create/load index results area
     */
    public void setIndexViewBounds() {
        if (_state == STATE.INDEX) {
            Dimension frameDim = getBounds().getSize();
            int resultsPaneWidth = frameDim.width - getInsets().left - getInsets().right;
            int resultsPaneHeight = frameDim.height - getInsets().top - getInsets().bottom -
                    _titleArea.getHeight() - _menu.getHeight();
            int resultsPaneX = 0;
            int resultsPaneY = _titleArea.getHeight();
            _resultsPane.setBounds(resultsPaneX, resultsPaneY, resultsPaneWidth, resultsPaneHeight);
        }
    }

    /**
     * Modifies the view when the "query collection" menu item is clicked.
     */
    public void initSearchView() {
        if (_state == STATE.SEARCH) {
            return;
        }
        if (_state == STATE.INDEX) {
            clearResultsArea();
            _searchButton = new JButton("Search");
            _searchField = new JTextField();
            _searchField.setFont(_textFont);
            _mainPane.add(_searchField);
            _mainPane.add(_searchButton);
            _state = STATE.SEARCH;
            setSearchViewBounds();
            return;
        }
        _state = STATE.SEARCH;
        _searchButton = new JButton("Search");
        _searchButton.setFont(_textFont);
        _searchField = new JTextField();
        _searchField.setFont(_textFont);
        _mainPane.add(_searchField);
        _mainPane.add(_searchButton);
        initResultsPane();
        setSearchViewBounds();
    }

    /**
     * Sets the proper bounds of the search results area
     */
    public void setSearchViewBounds() {
        if (_state == STATE.SEARCH) {
            Dimension frameDim = getBounds().getSize();
            int searchButtonWidth = 150;
            int searchButtonHeight = 40;
            int searchButtonX = 0;
            int searchButtonY = _titleArea.getHeight();
            int searchFieldWidth = frameDim.width - getInsets().left - getInsets().right - searchButtonWidth;
            int searchFieldHeight = searchButtonHeight;
            int searchFieldX = searchButtonWidth;
            int searchFieldY = _titleArea.getHeight();
            int resultsPaneWidth = frameDim.width - getInsets().left - getInsets().right;
            int resultsPaneHeight = frameDim.height - getInsets().top - getInsets().bottom -
                    _titleArea.getHeight() - _menu.getHeight() - searchButtonHeight;
            int resultsPaneX = 0;
            int resultsPaneY = searchButtonHeight + _titleArea.getHeight();
            _resultsPane.setBounds(resultsPaneX, resultsPaneY, resultsPaneWidth, resultsPaneHeight);
            _searchButton.setBounds(searchButtonX, searchButtonY, searchButtonWidth, searchButtonHeight);
            _searchField.setBounds(searchFieldX, searchFieldY, searchFieldWidth, searchFieldHeight);
        }
    }

    /* Initializes the area that shows the results of create/load index or search */
    private void initResultsPane() {
        if (_resultsPane != null) {
            return;
        }
        _resultsArea = new JTextArea();
        _resultsArea.setEditable(false);
        _resultsArea.setFont(_textFont);
        _resultsPane = new JScrollPane(_resultsArea);
        _viewOut = new JTextAreaOutputStream(_resultsArea);
        _mainPane.add(_resultsPane);
    }

    /**
     * Clears the results of clear/load index, search
     */
    public void clearResultsArea() {
        if (_resultsArea != null) {
            _resultsArea.setText("");
        }
    }

    /**
     * Shows a yes/no selection dialog.
     *
     * @param message A question
     * @return true if the yes button is clicked, false otherwise.
     * @throws IllegalArgumentException if running task is null.
     */
    public boolean showYesNoMessage(String message) {
        if (message == null) {
            throw new IllegalArgumentException("task is null");
        }
        Object[] options = {"Yes", "No"};
        int quitDialog = JOptionPane.showOptionDialog(this, message, "Question",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return (quitDialog == JOptionPane.YES_OPTION);
    }

    /**
     * Prints the provided text to the create/load index and search areas
     * @param text
     * @throws IOException
     */
    public void print(String text) throws IOException {
        _viewOut.write(text.getBytes("UTF-8"));
    }

    /**
     * Shows an error dialog
     * @param text
     */
    public void showError(String text) {
        JOptionPane.showMessageDialog(this,
                text,
                "Inane error",
                JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Returns the "create index" menu item
     * @return
     */
    public JMenuItem get_createIndex() {
        return _createIndex;
    }

    /**
     * Returns the "query collection" menu item
     * @return
     */
    public JMenuItem get_queryCollection() {
        return _queryCollection;
    }

    /**
     * Returns the "load index" menu item
     * @return
     */
    public JMenuItem get_loadIndex() {
        return _loadIndex;
    }

    /**
     * Returns the search button
     * @return
     */
    public JButton get_searchButton() {
        return _searchButton;
    }

    /**
     * Returns the search input field
     * @return
     */
    public JTextField get_searchField() {
        return _searchField;
    }
}
