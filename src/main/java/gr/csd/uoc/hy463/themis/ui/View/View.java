package gr.csd.uoc.hy463.themis.ui.View;

import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class View extends JFrame {

    /* true when the search bar and button are visible */
    private boolean _searchInitialized = false;

    /* true when the results area is visible */
    private boolean resultsAreaInitialized = false;

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
    private JMenuItem _evaluateVSM_Glove;

    /* The "evaluate BM25" menu item */
    private JMenuItem _evaluateBM25;

    /* The "evaluate BM25/Glove" menu item */
    private JMenuItem _evaluateBM25_Glove;

    /* The "evaluate VSM/WordNet" menu item */
    private JMenuItem _evaluateVSM_JWNL;

    /* The "evaluate BM25/WordNet" menu item */
    private JMenuItem _evaluateBM25_JWNL;

    /* Title area (top) */
    private JLabel _titleArea;

    /* Everything sits on top of this pane */
    private JLayeredPane _mainPane;

    /* Contains the text area that shows the results of the indexing/searching tasks */
    private JScrollPane _outputPane;

    /* the text area that shows the results of indexing/searching */
    private JTextArea _resultsArea;

    /* The search button */
    private JButton _searchButton;

    /* the search input area */
    private JTextField _searchField;

    /* the "retrieval model" menu */
    private JMenu _retrievalModel;

    /* the "document properties" menu */
    private JMenu _documentProperties;

    /* the "query expansion" menu */
    private JMenu _expansionDictionary;

    public View() {
        Font font = new Font("SansSerif", Font.PLAIN, 14);
        UIManager.put("OptionPane.messageFont", font);
        UIManager.put("OptionPane.buttonFont", font);
        UIManager.put("MenuItem.font", font);
        UIManager.put("Menu.font", font);
        UIManager.put("CheckBoxMenuItem.font", font);
        UIManager.put("RadioButtonMenuItem.font", font);
        initMenu();
        pack();
        setTitle("Themis search engine v1.0");
        setSize(800, 600); //initial frame size
        _mainPane = new JLayeredPane();
        initTitleArea();
        getContentPane().add(_mainPane);
    }

    /* Initializes the menu bar */
    private void initMenu() {

        /* index menu */
        JMenu index = new JMenu("Index");
        _createIndex = new JMenuItem("Create index");
        _loadIndex = new JMenuItem("Load default index");
        index.add(_createIndex);
        index.add(_loadIndex);

        /* search menu */
        JMenu search = new JMenu("Search");
        _queryCollection = new JMenuItem("New query");

        _retrievalModel = new JMenu("Retrieval model");
        ButtonGroup group = new ButtonGroup();
        RetrievalModelRadioButton modelBoolean = new RetrievalModelRadioButton("Existential");
        RetrievalModelRadioButton modelVSM = new RetrievalModelRadioButton("VSM");
        RetrievalModelRadioButton modelBM25 = new RetrievalModelRadioButton("BM25");
        group.add(modelBoolean);
        group.add(modelVSM);
        group.add(modelBM25);
        _retrievalModel.add(modelBoolean);
        _retrievalModel.add(modelVSM);
        _retrievalModel.add(modelBM25);

        _expansionDictionary = new JMenu("Query expansion");
        group = new ButtonGroup();
        ExpansionDictionaryRadioButton noneDictionary = new ExpansionDictionaryRadioButton("None");
        ExpansionDictionaryRadioButton gloveDictionary = new ExpansionDictionaryRadioButton("GloVe");
        ExpansionDictionaryRadioButton extJWNLDictionary = new ExpansionDictionaryRadioButton("WordNet");
        group.add(noneDictionary);
        group.add(gloveDictionary);
        group.add(extJWNLDictionary);
        _expansionDictionary.add(noneDictionary);
        _expansionDictionary.add(gloveDictionary);
        _expansionDictionary.add(extJWNLDictionary);

        _documentProperties = new JMenu("Document properties");
        DocInfoRadioButton title = new DocInfoRadioButton("Title");
        DocInfoRadioButton authors = new DocInfoRadioButton("Authors");
        DocInfoRadioButton authorIds = new DocInfoRadioButton("Author ids");
        DocInfoRadioButton journal = new DocInfoRadioButton("Journal");
        DocInfoRadioButton year = new DocInfoRadioButton("Year");
        DocInfoRadioButton pagerank = new DocInfoRadioButton("Citations Pagerank");
        DocInfoRadioButton weight = new DocInfoRadioButton("VSM Weight");
        DocInfoRadioButton length = new DocInfoRadioButton("Token count");
        DocInfoRadioButton maxTF = new DocInfoRadioButton("Max TF");
        DocInfoRadioButton documentSize = new DocInfoRadioButton("Document Size");
        _documentProperties.add(title);
        _documentProperties.add(authors);
        _documentProperties.add(authorIds);
        _documentProperties.add(journal);
        _documentProperties.add(year);
        _documentProperties.add(pagerank);
        _documentProperties.add(weight);
        _documentProperties.add(length);
        _documentProperties.add(maxTF);
        _documentProperties.add(documentSize);
        search.add(_queryCollection);
        search.add(_retrievalModel);
        search.add(_expansionDictionary);
        search.add(_documentProperties);

        /* evaluate menu */
        JMenuItem evaluate = new JMenu("Evaluate");
        _evaluateVSM = new JMenuItem("VSM");
        _evaluateBM25 = new JMenuItem("BM25");
        _evaluateVSM_Glove = new JMenuItem("VSM/GloVe");
        _evaluateBM25_Glove = new JMenuItem("BM25/GloVe");
        _evaluateVSM_JWNL = new JMenuItem("VSM/WordNet");
        _evaluateBM25_JWNL = new JMenuItem("BM25/WordNet");
        evaluate.add(_evaluateVSM);
        evaluate.add(_evaluateBM25);
        evaluate.add(_evaluateVSM_Glove);
        evaluate.add(_evaluateBM25_Glove);
        evaluate.add(_evaluateVSM_JWNL);
        evaluate.add(_evaluateBM25_JWNL);

        /* main menu bar */
        _menu = new JMenuBar();
        _menu.add(index);
        _menu.add(search);
        _menu.add(evaluate);

        setJMenuBar(_menu);
    }

    /* Initializes the title Area */
    private void initTitleArea() {
        _titleArea = new JLabel("Themis");
        _titleArea.setFont(new Font("SansSerif", Font.PLAIN, 20));
        _titleArea.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));
        setTitleAreaBounds();
        _mainPane.add(_titleArea);
    }

    /* Initializes the area that shows the results of create/load index or search */
    private void initResultsArea() {
        if (_searchInitialized || resultsAreaInitialized) {
            return;
        }
        _resultsArea = new JTextArea();
        _resultsArea.setEditable(false);
        _resultsArea.setFont(new Font("SansSerif", Font.PLAIN, 16));
        _outputPane = new JScrollPane(_resultsArea);
        _mainPane.add(_outputPane);
    }

    /**
     * Modifies the view when the "create index", "load index", "evaluate VSM", "evaluate BM25" menu items
     * are clicked
     */
    public void initOnlyResultsView() {
        if (resultsAreaInitialized) {
            clearResultsArea();
            return;
        }
        if (_searchInitialized) {
            _mainPane.remove(_searchButton);
            _mainPane.remove(_searchField);
            _mainPane.remove(_outputPane);
            _searchInitialized = false;
        }
        initResultsArea();
        resultsAreaInitialized = true;
        setOnlyResultsBounds();
    }

    /**
     * Modifies the view when the "query collection" menu item is clicked
     */
    public void initSearchView() {
        if (_searchInitialized) {
            clearResultsArea();
            clearQuery();
            return;
        }
        if (resultsAreaInitialized) {
            _mainPane.remove(_outputPane);
            resultsAreaInitialized = false;
        }
        _searchButton = new JButton("Search");
        _searchButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        _searchField = new JTextField();
        _searchField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        _mainPane.add(_searchField);
        _mainPane.add(_searchButton);
        initResultsArea();
        _searchInitialized = true;
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
     * "evaluate BM25" menu items are clicked
     */
    public void setOnlyResultsBounds() {
        if (!resultsAreaInitialized) {
            return;
        }
        Dimension frameDim = getBounds().getSize();
        int resultsPaneWidth = frameDim.width - getInsets().left - getInsets().right;
        int resultsPaneHeight = frameDim.height - getInsets().top - getInsets().bottom -
                _titleArea.getHeight() - _menu.getHeight();
        int resultsPaneX = 0;
        int resultsPaneY = _titleArea.getHeight();
        _outputPane.setBounds(resultsPaneX, resultsPaneY, resultsPaneWidth, resultsPaneHeight);
    }

    /**
     * Sets the proper bounds of the results area
     */
    public void setSearchViewBounds() {
        if (!_searchInitialized) {
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

        _outputPane.setBounds(resultsPaneX, resultsPaneY, resultsPaneWidth, resultsPaneHeight);
        _searchButton.setBounds(searchButtonX, searchButtonY, searchButtonWidth, searchButtonHeight);
        _searchField.setBounds(searchFieldX, searchFieldY, searchFieldWidth, searchFieldHeight);
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
     * Clears the search input area
     */
    public void clearQuery() {
        if (_searchField != null) {
            _searchField.setText("");
        }
    }

    /**
     * Shows a yes/no selection dialog
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
     * Prints the given text to the results area
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
    public JMenuItem getCreateIndex() {
        return _createIndex;
    }

    /**
     * Returns the "query collection" menu item
     * @return
     */
    public JMenuItem getQueryCollection() {
        return _queryCollection;
    }

    /**
     * Returns the "load index" menu item
     * @return
     */
    public JMenuItem getLoadIndex() {
        return _loadIndex;
    }

    /**
     * Returns the "evaluate VSM" menu item
     * @return
     */
    public JMenuItem getEvaluateVSM() {
        return _evaluateVSM;
    }

    /**
     * Returns the "evaluate BM25" menu item
     * @return
     */
    public JMenuItem getEvaluateBM25() {
        return _evaluateBM25;
    }

    /**
     * Returns the "evaluate VSM/GloVe" menu item
     * @return
     */
    public JMenuItem getEvaluateVSM_Glove() {
        return _evaluateVSM_Glove;
    }

    /**
     * Returns the "evaluate BM25/Glove" menu item
     * @return
     */
    public JMenuItem getEvaluateBM25_Glove() {
        return _evaluateBM25_Glove;
    }

    /**
     * Returns the "evaluate VSM/WordNet" menu item
     * @return
     */
    public JMenuItem getEvaluateVSM_JWNL() {
        return _evaluateVSM_JWNL;
    }

    /**
     * Returns the "evaluate BM25/WordNet" menu item
     * @return
     */
    public JMenuItem getEvaluateBM25_JWNL() {
        return _evaluateBM25_JWNL;
    }

    /**
     * Returns the search button
     * @return
     */
    public JButton getSearchButton() {
        return _searchButton;
    }

    /**
     * Returns the search input field
     * @return
     */
    public JTextField getSearchField() {
        return _searchField;
    }

    /**
     * Returns the "retrieval model" menu
     * @return
     */
    public JMenu getRetrievalModel() {
        return _retrievalModel;
    }

    /**
     * Returns the "document properties" menu
     * @return
     */
    public JMenu getDocumentProperties() {
        return _documentProperties;
    }

    /**
     * Returns the "query expansion" menu
     * @return
     */
    public JMenu getExpansionDictionary() {
        return _expansionDictionary;
    }

    /**
     * Checks the menu radio button that corresponds to the given retrieval model
     * @param model
     */
    public void checkRetrievalModel(ARetrievalModel.MODEL model) {
        for (int i = 0; i < _retrievalModel.getItemCount(); i++) {
            if (((RetrievalModelRadioButton) _retrievalModel.getItem(i)).getRetrievalModel() == model) {
                _retrievalModel.getItem(i).setSelected(true);
                break;
            }
        }
    }

    /**
     * Checks the menu radio button that corresponds to the given query expansion dictionary
     * @param dictionary
     */
    public void checkExpansionDictionary(QueryExpansion.DICTIONARY dictionary) {
        for (int i = 0; i < _expansionDictionary.getItemCount(); i++) {
            if (((ExpansionDictionaryRadioButton) _expansionDictionary.getItem(i)).getExpansionDictionary() == dictionary) {
                _expansionDictionary.getItem(i).setSelected(true);
                break;
            }
        }
    }
}
