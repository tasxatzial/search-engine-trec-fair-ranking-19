package gr.csd.uoc.hy463.themis.ui;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class View extends JFrame {

    /* The main menu bar */
    private JMenuBar _menu;

    /* The "create index" menu item */
    private JMenuItem _createIndex;

    /* The "load index" menu item */
    private JMenuItem _loadIndex;

    /* The "query collection" menu item */
    private JMenuItem _queryCollection;

    /* The "evaluate VSM" menu item */
    private JMenuItem _evaluateVSM;

    /* The "evaluate VSM/Glove" menu item */
    private JMenuItem _evaluateVSMGlove;

    /* The "evaluate BM25" menu item */
    private JMenuItem _evaluateBM25;

    /* The "evaluate BM25/Glove" menu item */
    private JMenuItem _evaluateBM25Glove;

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

        /* evaluate menu */
        JMenuItem evaluate = new JMenu("Evaluate");
        _evaluateVSM = new JMenuItem("VSM");
        _evaluateBM25 = new JMenuItem("BM25");
        _evaluateVSMGlove = new JMenuItem("VSM/Glove");
        _evaluateBM25Glove = new JMenuItem("BM25/Glove");
        evaluate.add(_evaluateVSM);
        evaluate.add(_evaluateBM25);
        evaluate.add(_evaluateVSMGlove);
        evaluate.add(_evaluateBM25Glove);

        /* main menu bar */
        _menu = new JMenuBar();
        _menu.add(index);
        _menu.add(search);
        _menu.add(evaluate);

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
     * Modifies the view when the "create index", "load index", "evaluate VSM", "evaluate BM25" menu items
     * are clicked.
     */
    public void initIndexView() {
        if (_searchField != null) {
            _mainPane.remove(_searchField);
            _searchField = null;
        }
        if (_searchButton != null) {
            _mainPane.remove(_searchButton);
            _searchButton = null;
        }
        if (_resultsArea != null) {
            _mainPane.remove(_resultsArea);
            _resultsArea = null;
        }
        initResults();
        setIndexViewBounds();
    }

    /**
     * Sets the proper bounds of the results area when the "create index", "load index", "evaluate VSM",
     * "evaluate BM25" menu items are clicked.
     */
    public void setIndexViewBounds() {
        if (_resultsPane == null) {
            return;
        }
        Dimension frameDim = getBounds().getSize();
        int resultsPaneWidth = frameDim.width - getInsets().left - getInsets().right;
        int resultsPaneHeight = frameDim.height - getInsets().top - getInsets().bottom -
                _titleArea.getHeight() - _menu.getHeight();
        int resultsPaneX = 0;
        int resultsPaneY = _titleArea.getHeight();
        _resultsPane.setBounds(resultsPaneX, resultsPaneY, resultsPaneWidth, resultsPaneHeight);
    }

    /**
     * Modifies the view when the "query collection" menu item is clicked.
     */
    public void initSearchView() {
        if (_searchField != null) {
            _mainPane.remove(_searchField);
            _searchField = null;
        }
        if (_searchButton != null) {
            _mainPane.remove(_searchButton);
            _searchButton = null;
        }
        if (_resultsPane != null) {
            _mainPane.remove(_resultsPane);
            _resultsPane = null;
        }
        _searchButton = new JButton("Search");
        _searchButton.setEnabled(false);
        _searchField = new JTextField();
        _searchField.setFont(_textFont);
        _mainPane.add(_searchField);
        _mainPane.add(_searchButton);
        initResults();
        setSearchViewBounds();
    }

    /**
     * Enables the search button
     */
    public void enableSearchButton() {
        if (_searchButton != null) {
            _searchButton.setEnabled(true);
        }
    }

    /**
     * Sets the proper bounds of the search results area
     */
    public void setSearchViewBounds() {
        if (_resultsPane == null || _searchButton == null || _searchField == null) {
            return;
        }
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

    /* Initializes the area that shows the results of create/load index or search */
    private void initResults() {
        if (_resultsPane != null) {
            _mainPane.remove(_resultsPane);
        }
        _resultsArea = new JTextArea();
        _resultsArea.setEditable(false);
        _resultsArea.setFont(_textFont);
        _resultsPane = new JScrollPane(_resultsArea);
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
     * Prints the provided text to the results text area
     * @param text
     * @throws IOException
     */
    public void print(String text) {
        if (_resultsArea != null) {
            _resultsArea.append(text);
            _resultsArea.setCaretPosition(_resultsArea.getDocument().getLength());
        }
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
     * Returns the "evaluate VSM" menu item
     * @return
     */
    public JMenuItem get_evaluateVSM() {
        return _evaluateVSM;
    }

    /**
     * Returns the "evaluate BM25" menu item
     * @return
     */
    public JMenuItem get_evaluateBM25() {
        return _evaluateBM25;
    }

    /**
     * Returns the "evaluate VSM/Glove" menu item
     * @return
     */
    public JMenuItem get_evaluateVSMGlove() {
        return _evaluateVSMGlove;
    }

    /**
     * Returns the "evaluate BM25/Glove" menu item
     * @return
     */
    public JMenuItem get_evaluateBM25Glove() {
        return _evaluateBM25Glove;
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
