package gr.csd.uoc.hy463.themis;

import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.metrics.ThemisEval;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.ui.CreateIndex;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.ui.View.ExpansionDictionaryRadioButton;
import gr.csd.uoc.hy463.themis.ui.View.RetrievalModelRadioButton;
import gr.csd.uoc.hy463.themis.ui.View.View;
import gr.csd.uoc.hy463.themis.ui.View.DocInfoRadioButton;
import gr.csd.uoc.hy463.themis.utils.Time;

import javax.swing.*;
import java.awt.event.*;
import java.util.*;

/**
 * The main class.
 *
 * Results are printed in console, but we also have an option to use a GUI. It can be
 * enabled by passing 'gui' as first argument of the main function.
 */
public class Themis {
    private static CreateIndex _createIndex;
    private static Search _search;
    private static View _view;
    private static ActionListener _searchButtonListener = null;
    private static TASK _task = null;

    /* one of the 4 available tasks. The GUI lets us run only one task at a time. */
    private enum TASK {
        CREATE_INDEX, LOAD_INDEX, SEARCH, EVALUATE
    }

    public static void main(String[] args) {

        /* Display the GUI if 'gui' has been passed as a program argument */
        if (args.length > 0 && args[0].equals("gui")) {
            _view = new View();

            /* listener for the top-right close button of the GUI */
            _view.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (_task != null && !_view.showYesNoMessage(_task + " is in progress. Quit?")) {
                        _view.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                    } else {
                        _view.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                        System.exit(0);
                    }
                }
            });

            /* resize listeners for the GUI window (does not include maximizing) */
            _view.addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent e) {
                    _view.setOnlyResultsBounds();
                    _view.setTitleAreaBounds();
                    _view.setSearchViewBounds();
                }
            });

            /* toggle maximize listeners for the GUI window */
            _view.addWindowStateListener(e -> _view.setOnlyResultsBounds());
            _view.addWindowStateListener(e -> _view.setTitleAreaBounds());
            _view.addWindowStateListener(e -> _view.setSearchViewBounds());

            /* listeners for the menu items */
            _view.getCreateIndex().addActionListener(new createIndexListener());
            _view.getQueryCollection().addActionListener(new searchListener());
            _view.getLoadIndex().addActionListener(new loadIndexListener());
            _view.getEvaluateVSM().addActionListener(new evaluateListener(ARetrievalModel.MODEL.VSM, QueryExpansion.DICTIONARY.NONE));
            _view.getEvaluateVSM_Glove().addActionListener(new evaluateListener(ARetrievalModel.MODEL.VSM, QueryExpansion.DICTIONARY.GLOVE));
            _view.getEvaluateVSM_JWNL().addActionListener(new evaluateListener(ARetrievalModel.MODEL.VSM, QueryExpansion.DICTIONARY.EXTJWNL));
            _view.getEvaluateBM25().addActionListener(new evaluateListener(ARetrievalModel.MODEL.OKAPI, QueryExpansion.DICTIONARY.NONE));
            _view.getEvaluateBM25_Glove().addActionListener(new evaluateListener(ARetrievalModel.MODEL.OKAPI, QueryExpansion.DICTIONARY.GLOVE));
            _view.getEvaluateBM25_JWNL().addActionListener(new evaluateListener(ARetrievalModel.MODEL.OKAPI, QueryExpansion.DICTIONARY.EXTJWNL));

            _view.setVisible(true);
        }
        /* non GUI version */
        else {
            try {
                System.out.println("Write code in this block");
            } catch (Exception ex) {
                print(ex + "\n");
            }
        }
    }

    /* Example of using Themis */
    private static void runExample() {
        try {
            _search = new Search();
            Set<DocInfo.PROPERTY> props = new HashSet<>();

            /* fetch the titles */
            props.add(DocInfo.PROPERTY.TITLE);
            _search.setDocumentProperties(props);

            /* Query 'test' and retrieve info for the top 12 results */
            List<Result> results = _search.search("test", 12);

            /* print results with index 5-20 (included). If there are only 10 results,
            it would print those with index 5-9 */
            _search.printResults(results, 5, 20);
        }
        catch (Exception ex) {
            print(ex + "\n");
        }
    }

    /* Runs a complete set of evaluations. Weight for the document pagerank scores takes values 0 and 0.25 */
    private static void runFullEval() {
        ARetrievalModel.MODEL[] models = {ARetrievalModel.MODEL.VSM, ARetrievalModel.MODEL.OKAPI};
        QueryExpansion.DICTIONARY[] dictionaries = {QueryExpansion.DICTIONARY.NONE, QueryExpansion.DICTIONARY.GLOVE, QueryExpansion.DICTIONARY.EXTJWNL};
        double[] pagerankWeights = {0, 0.25};
        try {
            _search = new Search();
            _search.search("1"); // warm-up
            for (QueryExpansion.DICTIONARY dictionary : dictionaries) {
                for (ARetrievalModel.MODEL model : models) {
                    for (double pagerank : pagerankWeights) {
                        ThemisEval eval = new ThemisEval(_search, model, dictionary, pagerank);
                        eval.run();
                    }
                }
            }
        } catch (Exception ex) {
            print(ex + "\n");
        }
    }

    /**
     * Prints a string to view or to console if view is null.
     *
     * @param text
     */
    public static void print(String text) {
        if (_view == null) {
            System.out.print(text);
        }
        else {
            _view.print(text);
        }
    }

    /* The listener for the "search" button */
    private static class searchButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            _view.clearResultsArea();
            Thread runnableSearch = new Thread(new Search_runnable());
            runnableSearch.start();
        }
    }

    /* The listener for the "create index" menu item */
    private static class createIndexListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            _view.initOnlyResultsView();
            _searchButtonListener = null;
            Thread runnableCreate = new Thread(new CreateIndex_runnable());
            runnableCreate.start();
        }
    }

    /* The listener for the "query collection" menu item */
    private static class searchListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            _view.initSearchView();
            if (_searchButtonListener == null) {
                _searchButtonListener = new searchButtonListener();
                _view.getSearchButton().addActionListener(_searchButtonListener);
            }
            if (_search == null) {
                Thread runnableLoad = new Thread(new LoadIndex_runnable());
                runnableLoad.start();
            }
        }
    }

    /* The listener for the "load index" menu item */
    private static class loadIndexListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            _view.initOnlyResultsView();
            Thread runnableLoad = new Thread(new LoadIndex_runnable());
            runnableLoad.start();
        }
    }

    /* The listener for the items on the "evaluate" sub menu */
    private static class evaluateListener implements ActionListener {
        private final ARetrievalModel.MODEL _model;
        private final QueryExpansion.DICTIONARY _dictionary;
        public evaluateListener(ARetrievalModel.MODEL model, QueryExpansion.DICTIONARY dictionary) {
           _model = model;
           _dictionary = dictionary;
        }
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            _view.initOnlyResultsView();
            _searchButtonListener = null;
            Evaluate_runnable evaluateVSM = new Evaluate_runnable(_model, _dictionary);
            Thread runnableEvaluateVSM = new Thread(evaluateVSM);
            runnableEvaluateVSM.start();
        }
    }

    /* runs when one of the "evaluate" menu items is clicked */
    private static class Evaluate_runnable implements Runnable {
        private final ARetrievalModel.MODEL _model;
        private final QueryExpansion.DICTIONARY _dictionary;

        public Evaluate_runnable(ARetrievalModel.MODEL model, QueryExpansion.DICTIONARY dictionary) {
            _model = model;
            _dictionary = dictionary;
        }

        @Override
        public void run() {
            _task = TASK.EVALUATE;
            _createIndex = null;
            if (_search == null) {
                try {
                    _search = new Search();
                } catch (Exception ex) {
                    print(ex + "\n");
                    _task = null;
                    return;
                }
            }
            try {
                ThemisEval eval = new ThemisEval(_search, _model, _dictionary, 0.25);
                eval.run();
            } catch (Exception ex) {
                print(ex + "\n");
            } finally {
                _task = null;
            }
        }
    }

    /* runs when the "create index" menu item is clicked */
    private static class CreateIndex_runnable implements Runnable {
        @Override
        public void run() {
            _task = TASK.CREATE_INDEX;
            try {
                _createIndex = new CreateIndex();
            } catch (Exception ex) {
                print(ex + "\n");
                _task = null;
                return;
            }
            boolean deleteIndex = false;
            if (!_createIndex.isIndexDirEmpty()) {
                deleteIndex = _view.showYesNoMessage("Delete previous index folders?");
                if (!deleteIndex) {
                    _task = null;
                    return;
                }
            }
            try {
                if (_search != null) {
                    _search.unloadIndex();
                    _search = null;
                }
            } catch (Exception ex) {
                print(ex + "\n");
                _task = null;
                return;
            }
            if (deleteIndex) {
                try {
                    _createIndex.deleteIndex();
                } catch (Exception ex) {
                    print(ex + "\n");
                    _task = null;
                    return;
                }
            }
            try {
                _createIndex.createIndex();
            } catch (Exception ex) {
                print(ex + "\n");
            } finally {
                _task = null;
            }
        }
    }

    /* runs when the "load default index" or "query collection" menu items are clicked. When the index
    * has been loaded, the menu items for the retrieval model and expansion dictionary are set to their
    * default values from the config file */
    private static class LoadIndex_runnable implements Runnable {
        @Override
        public void run() {
            _task = TASK.LOAD_INDEX;
            try {
                if (_search != null) {
                    _search.unloadIndex();
                    _search = null;
                }
            } catch (Exception ex) {
                print(ex + "\n");
                _task = null;
                return;
            }
            _createIndex = null;
            try {
                _search = new Search();
            } catch (Exception ex) {
                print(ex + "\n");
            } finally {
                _task = null;
                if (_search != null) {
                    _view.checkRetrievalModel(_search.getRetrievalmodel());
                    _view.checkExpansionDictionary(_search.getExpansionDictionary());
                }
            }
        }
    }

    /* runs when the "search" button is clicked */
    private static class Search_runnable implements Runnable {
        @Override
        public void run() {
            if (_search == null) {
                return;
            }
            _task = TASK.SEARCH;
            List<Result> results;

            /* set the query expansion dictionary */
            JMenu expansionDictionary = _view.getExpansionDictionary();
            for (int i = 0; i < expansionDictionary.getItemCount(); i++) {
                if (expansionDictionary.getItem(i).isSelected()) {
                    try {
                        _search.setExpansionDictionary(((ExpansionDictionaryRadioButton) expansionDictionary.getItem(i)).getExpansionDictionary());
                    } catch (Exception ex) {
                        print(ex + "\n");
                        _task = null;
                        return;
                    }
                    break;
                }
            }

            /* set the document properties we want to retrieve */
            Set<DocInfo.PROPERTY> props = new HashSet<>();
            JMenu documentProperties = _view.getDocumentProperties();
            for (int i = 0; i < documentProperties.getItemCount(); i++) {
                if (documentProperties.getItem(i).isSelected()) {
                    props.add(((DocInfoRadioButton) documentProperties.getItem(i)).getProp());
                }
            }
            try {
                _search.setDocumentProperties(props);
            } catch (Exception ex) {
                print(ex + "\n");
                _task = null;
                return;
            }

            /* set the query retrieval model */
            JMenu retrievalModel = _view.getRetrievalModel();
            for (int i = 0; i < retrievalModel.getItemCount(); i++) {
                if (retrievalModel.getItem(i).isSelected()) {
                    try {
                        _search.setRetrievalModel(((RetrievalModelRadioButton) retrievalModel.getItem(i)).getRetrievalModel());
                    } catch (IndexNotLoadedException ex) {
                        print(ex + "\n");
                        _task = null;
                        return;
                    }
                    break;
                }
            }

            /* get the query text from the GUI text box */
            String query = _view.getSearchField().getText();
            long startTime = System.nanoTime();
            print(">>> Searching for: " + query + " ...\n");

            /* maximum results that should be returned */
            int endResult = 50;
            try {
                if (_search.getRetrievalmodel() == ARetrievalModel.MODEL.EXISTENTIAL) {

                    /* return all results when the retrieval model is existential */
                    results = _search.search(_view.getSearchField().getText());
                } else {

                    /* return top endResult results when the retrieval model is VSM/Okapi */
                    results = _search.search(_view.getSearchField().getText(), endResult);
                }
                print("Search time: " + new Time(System.nanoTime() - startTime) + "\n");
                print("Found " + _search.getTotalResults() + " results\n");
                if (_search.getTotalResults() != 0 && _search.getRetrievalmodel() != ARetrievalModel.MODEL.EXISTENTIAL) {
                    print("Returning top " + Math.min(_search.getTotalResults(), endResult) + " results\n");
                }

                /* print top 10 results */
                _search.printResults(results, 0, 9);
            } catch (Exception ex) {
                print(ex + "\n");
            } finally {
                _task = null;
            }
        }
    }
}
