package gr.csd.uoc.hy463.themis.ui.View;

import javax.swing.*;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    /* The "initialize search" menu item */
    private JMenuItem _initSearch;

    /* The "evaluate VSM" menu item */
    private JMenuItem _evaluateVSM;

    /* The "evaluate VSM/Glove" menu item */
    private JMenuItem _evaluateVSM_Glove;

    /* The "evaluate BM25" menu item */
    private JMenuItem _evaluateOkapi;

    /* The "evaluate BM25/Glove" menu item */
    private JMenuItem _evaluateOkapi_Glove;

    /* The "evaluate VSM/WordNet" menu item */
    private JMenuItem _evaluateVSM_WordNet;

    /* The "evaluate BM25/WordNet" menu item */
    private JMenuItem _evaluateOkapi_WordNet;

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
    private JMenu _expansionModel;

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
        setTitle("Themis search engine v1.1");
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
        _loadIndex = new JMenuItem("Load index");
        index.add(_createIndex);
        index.add(_loadIndex);

        /* search menu */
        JMenu search = new JMenu("Search");
        _initSearch = new JMenuItem("Initialize search");

        _retrievalModel = new JMenu("Retrieval model");
        ButtonGroup group = new ButtonGroup();
        JRadioButtonMenuItem modelBoolean = new JRadioButtonMenuItem("Existential");
        JRadioButtonMenuItem modelVSM = new JRadioButtonMenuItem("VSM");
        JRadioButtonMenuItem modelOkapi = new JRadioButtonMenuItem("Okapi BM25+");
        group.add(modelBoolean);
        group.add(modelVSM);
        group.add(modelOkapi);
        _retrievalModel.add(modelBoolean);
        _retrievalModel.add(modelVSM);
        _retrievalModel.add(modelOkapi);

        _expansionModel = new JMenu("Query expansion");
        group = new ButtonGroup();
        JRadioButtonMenuItem noneModel = new JRadioButtonMenuItem("None");
        JRadioButtonMenuItem gloveModel = new JRadioButtonMenuItem("GloVe");
        JRadioButtonMenuItem extWordNetModel = new JRadioButtonMenuItem("WordNet");
        group.add(noneModel);
        group.add(gloveModel);
        group.add(extWordNetModel);
        _expansionModel.add(noneModel);
        _expansionModel.add(gloveModel);
        _expansionModel.add(extWordNetModel);

        _documentProperties = new JMenu("Document properties");
        DocInfoRadioButton title = new DocInfoRadioButton("Title");
        DocInfoRadioButton authors = new DocInfoRadioButton("Authors");
        DocInfoRadioButton authorIds = new DocInfoRadioButton("Author ids");
        DocInfoRadioButton journal = new DocInfoRadioButton("Journal");
        DocInfoRadioButton year = new DocInfoRadioButton("Year");
        DocInfoRadioButton pagerank = new DocInfoRadioButton("Citation Pagerank");
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
        search.add(_initSearch);
        search.add(_retrievalModel);
        search.add(_expansionModel);
        search.add(_documentProperties);

        /* evaluate menu */
        JMenuItem evaluate = new JMenu("Evaluate");
        _evaluateVSM = new JMenuItem("VSM");
        _evaluateOkapi = new JMenuItem("Okapi BM25+");
        _evaluateVSM_Glove = new JMenuItem("VSM/GloVe");
        _evaluateOkapi_Glove = new JMenuItem("Okapi BM25+/GloVe");
        _evaluateVSM_WordNet = new JMenuItem("VSM/WordNet");
        _evaluateOkapi_WordNet = new JMenuItem("Okapi BM25+/WordNet");
        evaluate.add(_evaluateVSM);
        evaluate.add(_evaluateOkapi);
        evaluate.add(_evaluateVSM_Glove);
        evaluate.add(_evaluateOkapi_Glove);
        evaluate.add(_evaluateVSM_WordNet);
        evaluate.add(_evaluateOkapi_WordNet);

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
     * Returns the "initialize search" menu item.
     * @return
     */
    public JMenuItem getInitSearch() {
        return _initSearch;
    }

    /**
     * Returns the "load index" menu item.
     * @return
     */
    public JMenuItem getLoadIndex() {
        return _loadIndex;
    }

    /**
     * Returns the "evaluate VSM" menu item.
     * @return
     */
    public JMenuItem getEvaluateVSM() {
        return _evaluateVSM;
    }

    /**
     * Returns the "evaluate Okapi BM25+" menu item.
     * @return
     */
    public JMenuItem getEvaluateOkapi() {
        return _evaluateOkapi;
    }

    /**
     * Returns the "evaluate VSM/GloVe" menu item.
     * @return
     */
    public JMenuItem getEvaluateVSM_Glove() {
        return _evaluateVSM_Glove;
    }

    /**
     * Returns the "evaluate Okapi BM25+/Glove" menu item.
     * @return
     */
    public JMenuItem getEvaluateOkapi_Glove() {
        return _evaluateOkapi_Glove;
    }

    /**
     * Returns the "evaluate VSM/WordNet" menu item.
     * @return
     */
    public JMenuItem getEvaluateVSM_WordNet() {
        return _evaluateVSM_WordNet;
    }

    /**
     * Returns the "evaluate Okapi BM25+/WordNet" menu item.
     * @return
     */
    public JMenuItem getEvaluateOkapi_WordNet() {
        return _evaluateOkapi_WordNet;
    }

    /**
     * Returns the search button.
     * @return
     */
    public JButton getSearchButton() {
        return _searchButton;
    }

    /**
     * Returns the text content of the search field.
     * @return
     */
    public String getQuery() {
        return _searchField.getText();
    }

    /**
     * Returns the name of the selected retrieval model.
     * @return
     */
    public String getRetrievalModel() {
        for (int i = 0; i < _retrievalModel.getItemCount(); i++) {
            if (_retrievalModel.getItem(i).isSelected()) {
                return _retrievalModel.getItem(i).getText();
            }
        }
        return null;
    }

    /**
     * Returns a list of all checked document properties.
     *
     * @return
     */
    public List<String> getCheckedDocumentProps() {
        List<String> props = new ArrayList<>();
        for (int i = 0; i < _documentProperties.getItemCount(); i++) {
            if (_documentProperties.getItem(i).isSelected()) {
                props.add(_documentProperties.getItem(i).getText());
            }
        }
        return props;
    }

    /**
     * Returns the name of the selected query expansion model.
     *
     * @return
     */
    public String getExpansionModel() {
        for (int i = 0; i < _expansionModel.getItemCount(); i++) {
            if (_expansionModel.getItem(i).isSelected()) {
                return _expansionModel.getItem(i).getText();
            }
        }
        return null;
    }

    /**
     * Checks the menu radio button that corresponds to the Existential retrieval model.
     */
    public void checkExistentialRetrievalModel() {
        _retrievalModel.getItem(0).setSelected(true);
    }

    /**
     * Checks the menu radio button that corresponds to the VSM retrieval model.
     */
    public void checkVSMRetrievalModel() {
        _retrievalModel.getItem(1).setSelected(true);
    }

    /**
     * Checks the menu radio button that corresponds to the Okapi retrieval model.
     */
    public void checkOkapiRetrievalModel() {
        _retrievalModel.getItem(2).setSelected(true);
    }

    /**
     * Checks the menu radio button that corresponds to the no expansion model.
     */
    public void checkNoneExpansionModel() {
        _expansionModel.getItem(0).setSelected(true);
    }

    /**
     * Checks the menu radio button that corresponds to the GloVe model.
     */
    public void checkGloVeExpansionModel() {
        _expansionModel.getItem(1).setSelected(true);
    }

    /**
     * Checks the menu radio button that corresponds to the WordNet model.
     */
    public void checkWordNetExpansionModel() {
        _expansionModel.getItem(2).setSelected(true);
    }

    /**
     * Unchecks all document properties.
     */
    public void uncheckAllDocumentProps() {
        for (int i = 0; i < _documentProperties.getItemCount(); i++) {
            _documentProperties.getItem(i).setSelected(false);
        }
    }
}
