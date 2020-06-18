package gr.csd.uoc.hy463.themis.ui.View;

import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class View extends JFrame {

    /* true when we see the search bar and button */
    private boolean searchInitialized = false;

    /* true when we see only a text area */
    private boolean onlyResultsInitialized = false;

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

    /* the "retrieval model" menu */
    private JMenu _retrievalModel;

    /* the "document properties" menu */
    private JMenu _documentProperties;

    public View() {
        initMenu();
        pack();
        setTitle("Themis search engine v0.1");
        setSize(1000, 800); //initial frame size
        _mainPane = new JLayeredPane();
        initTitleArea();
        getContentPane().add(_mainPane);
    }

    /* Initializes the menu bar */
    private void initMenu() {
        Font font = new Font("SansSerif", Font.PLAIN, 14);
        UIManager.put("MenuItem.font", font);
        UIManager.put("Menu.font", font);
        UIManager.put("CheckBoxMenuItem.font", font);
        UIManager.put("RadioButtonMenuItem.font", font);

        /* index menu */
        JMenu index = new JMenu("Index");
        _createIndex = new JMenuItem("Create index");
        _loadIndex = new JMenuItem("Load index");
        index.add(_createIndex);
        index.add(_loadIndex);

        /* search menu */
        JMenu search = new JMenu("Search");
        _queryCollection = new JMenuItem("Query collection");

        _retrievalModel = new JMenu("Retrieval model");
        ButtonGroup group = new ButtonGroup();
        MenuRadioButton modelBoolean = new MenuRadioButton("Boolean");
        MenuRadioButton modelVSM = new MenuRadioButton("VSM");
        MenuRadioButton modelBM25 = new MenuRadioButton("BM25+");
        group.add(modelBoolean);
        group.add(modelVSM);
        group.add(modelBM25);
        _retrievalModel.add(modelBoolean);
        _retrievalModel.add(modelVSM);
        _retrievalModel.add(modelBM25);

        _documentProperties = new JMenu("Document properties");
        MenuCheckbox title = new MenuCheckbox("Title");
        MenuCheckbox authors = new MenuCheckbox("Authors");
        MenuCheckbox authorIds = new MenuCheckbox("Author ids");
        MenuCheckbox journal = new MenuCheckbox("Journal");
        MenuCheckbox year = new MenuCheckbox("Year");
        MenuCheckbox pagerank = new MenuCheckbox("Pagerank");
        MenuCheckbox weight = new MenuCheckbox("Weight");
        MenuCheckbox length = new MenuCheckbox("Length");
        MenuCheckbox maxTF = new MenuCheckbox("Max term frequency");
        _documentProperties.add(title);
        _documentProperties.add(authors);
        _documentProperties.add(authorIds);
        _documentProperties.add(journal);
        _documentProperties.add(year);
        _documentProperties.add(pagerank);
        _documentProperties.add(weight);
        _documentProperties.add(length);
        _documentProperties.add(maxTF);

        search.add(_queryCollection);
        search.add(_retrievalModel);
        search.add(_documentProperties);

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
        _titleArea.setFont(new Font("SansSerif", Font.PLAIN, 20));
        _titleArea.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));
        setTitleAreaBounds();
        _mainPane.add(_titleArea);
    }

    /* Initializes the area that shows the results of create/load index or search */
    private void initResultsArea() {
        if (searchInitialized || onlyResultsInitialized) {
            return;
        }
        _resultsArea = new JTextArea();
        _resultsArea.setEditable(false);
        _resultsArea.setFont(new Font("SansSerif", Font.PLAIN, 16));
        _resultsPane = new JScrollPane(_resultsArea);
        _mainPane.add(_resultsPane);
    }

    /**
     * Modifies the view when the "create index", "load index", "evaluate VSM", "evaluate BM25" menu items
     * are clicked.
     */
    public void initOnlyResultsView() {
        if (onlyResultsInitialized) {
            clearResultsArea();
            return;
        }
        if (searchInitialized) {
            _mainPane.remove(_searchButton);
            _mainPane.remove(_searchField);
            _mainPane.remove(_resultsPane);
            searchInitialized = false;
        }
        initResultsArea();
        onlyResultsInitialized = true;
        setOnlyResultsBounds();
    }

    /**
     * Modifies the view when the "query collection" menu item is clicked.
     */
    public void initSearchView() {
        if (searchInitialized) {
            clearResultsArea();
            return;
        }
        if (onlyResultsInitialized) {
            _mainPane.remove(_resultsPane);
            onlyResultsInitialized = false;
        }
        _searchButton = new JButton("Search");
        _searchButton.setEnabled(false);
        _searchButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        _searchField = new JTextField();
        _searchField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        _mainPane.add(_searchField);
        _mainPane.add(_searchButton);
        initResultsArea();
        searchInitialized = true;
        setSearchViewBounds();
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
     * Sets the proper bounds of the results area when the "create index", "load index", "evaluate VSM",
     * "evaluate BM25" menu items are clicked.
     */
    public void setOnlyResultsBounds() {
        if (!onlyResultsInitialized) {
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
     * Sets the proper bounds of the search results area
     */
    public void setSearchViewBounds() {
        if (!searchInitialized) {
            return;
        }
        Dimension frameDim = getBounds().getSize();

        int searchButtonWidth = 150;
        int searchButtonHeight = 40;
        int searchButtonX = 0;
        int searchButtonY = _titleArea.getHeight();

        int resultsPaneWidth = frameDim.width - getInsets().left - getInsets().right;
        int resultsPaneHeight = frameDim.height - getInsets().top - getInsets().bottom -
                _titleArea.getHeight() - _menu.getHeight() - searchButtonHeight;
        int resultsPaneX = 0;
        int resultsPaneY = searchButtonHeight + _titleArea.getHeight();

        int searchFieldWidth = frameDim.width - getInsets().left - getInsets().right - searchButtonWidth;
        int searchFieldHeight = searchButtonHeight;
        int searchFieldX = searchButtonWidth;
        int searchFieldY = _titleArea.getHeight();

        _resultsPane.setBounds(resultsPaneX, resultsPaneY, resultsPaneWidth, resultsPaneHeight);
        _searchButton.setBounds(searchButtonX, searchButtonY, searchButtonWidth, searchButtonHeight);
        _searchField.setBounds(searchFieldX, searchFieldY, searchFieldWidth, searchFieldHeight);
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

    /**
     * Returns the "retrieval model" menu
     * @return
     */
    public JMenu get_retrievalModel() {
        return _retrievalModel;
    }

    /**
     * Returns the "document properties" menu
     * @return
     */
    public JMenu get_documentProperties() {
        return _documentProperties;
    }

    /**
     * Checks the menu radio button that corresponds to the specified retrieval model
     * @param model
     */
    public void checkRetrievalModel(ARetrievalModel.MODEL model) {
        for (int i = 0; i < _retrievalModel.getItemCount(); i++) {
            if (((MenuRadioButton) _retrievalModel.getItem(i)).get_model() == model) {
                _retrievalModel.getItem(i).setSelected(true);
                break;
            }
        }
    }
}
