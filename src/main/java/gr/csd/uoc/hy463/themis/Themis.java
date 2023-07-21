package gr.csd.uoc.hy463.themis;

import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.metrics.ThemisEval;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.ui.View.View;
import gr.csd.uoc.hy463.themis.utils.Time;
import net.sf.extjwnl.JWNLException;

import javax.swing.*;
import java.awt.event.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * The main class.
 *
 * Results are printed in console, but we also have an option to use a GUI. It can be
 * enabled by passing 'gui' as first argument of the main function.
 */
public class Themis {
    private static Indexer _indexer;
    private static Search _search;
    private static View _view;
    private static ActionListener _searchButtonListener = null;
    private static TASK _task = null;

    /* one of the 5 available tasks. Only one task is allowed to run at a time */
    private enum TASK {
        CREATE_INDEX, LOAD_INDEX, INIT_SEARCH, SEARCH, EVALUATE
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
            _view.getInitSearch().addActionListener(new InitSearchListener());
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

            } catch (Exception ex) {
                print(ex + "\n");
            }
        }
    }

    /* Example of using Themis */
    private static void runSearchExample() {
        try {
            Indexer indexer = new Indexer();
            indexer.load();
            Search search = new Search(indexer);
            Set<DocInfo.PROPERTY> props = new HashSet<>();

            /* fetch the titles */
            props.add(DocInfo.PROPERTY.TITLE);
            search.setDocumentProperties(props);

            /* Query 'test' and retrieve info for the top 12 results */
            List<Result> results = search.search("test", 12);

            /* print results with index 5-20 (included). If there are only 10 results,
            it would print those with index 5-9 */
            search.printResults(results, 5, 20);
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
            Indexer indexer = new Indexer();
            indexer.load();
            for (QueryExpansion.DICTIONARY dictionary : dictionaries) {
                for (ARetrievalModel.MODEL model : models) {
                    for (double pagerank : pagerankWeights) {
                        ThemisEval eval = new ThemisEval(_indexer, model, dictionary, pagerank);
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

    /* The listener for the "initialize search" menu item */
    private static class InitSearchListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            if (_indexer == null || !_indexer.isLoaded()) {
                _view.initOnlyResultsView();
                print("Index is not loaded!");
                _task = null;
                return;
            }
            _view.initSearchView();
            if (_searchButtonListener == null) {
                _searchButtonListener = new searchButtonListener();
                _view.getSearchButton().addActionListener(_searchButtonListener);
            }
            Thread runnableInitQuery = new Thread(new InitSearch_runnable());
            runnableInitQuery.start();
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
            _searchButtonListener = null;
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
            try {
                if (_indexer == null) {
                    _indexer = new Indexer();
                }
                if (!_indexer.isLoaded()) {
                    _indexer.load();
                }
                ThemisEval eval = new ThemisEval(_indexer, _model, _dictionary, 0.25);
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
                if (_indexer == null) {
                    _indexer = new Indexer();
                }
                if (!_indexer.areIndexDirEmpty()) {
                    boolean deleteIndex = _view.showYesNoMessage("Delete previous index folders?");
                    if (!deleteIndex) {
                        _task = null;
                        return;
                    }
                }
                _indexer.unload();
                _indexer.deleteIndex();
                _indexer.index();
            } catch (Exception ex) {
                print(ex + "\n");
            } finally {
                _task = null;
            }
        }
    }

    /* runs when the "load default index" menu item is clicked */
    private static class LoadIndex_runnable implements Runnable {
        @Override
        public void run() {
            _task = TASK.LOAD_INDEX;
            try {
                if (_indexer == null) {
                    _indexer = new Indexer();
                } else {
                    _indexer.unload();
                }
                _indexer.load();
            } catch (Exception ex) {
                print(ex + "\n");
            } finally {
                _task = null;
            }
        }
    }

    /* runs when the "initialize search" menu item is clicked */
    private static class InitSearch_runnable implements Runnable {
        @Override
        public void run() {
            _task = TASK.INIT_SEARCH;
            try {
                _search = new Search(_indexer);
                setViewRetrievalModel();
                setViewExpansionDictionary();
                _view.uncheckAllDocumentProps();
            } catch (Exception ex) {
                print(ex + "\n");
            } finally {
                _task = null;
            }
        }

        /* reads the default retrieval model from config and updates view */
        private void setViewRetrievalModel() {
            String model = _indexer.getConfig().getRetrievalModel();
            switch (model) {
                case "Existential":
                    _view.checkExistentialRetrievalModel();
                    break;
                case "VSM":
                    _view.checkVSMRetrievalModel();
                    break;
                case "BM25":
                    _view.checkBM25RetrievalModel();
                    break;
            }
        }

        /* reads the default query expansion dictionary from config and updates view */
        private void setViewExpansionDictionary() {
            if (!_indexer.getConfig().getUseQueryExpansion()) {
                _view.checkNoneExpansionDictionary();
                return;
            }
            String model = _indexer.getConfig().getQueryExpansionModel();
            switch (model) {
                case "GloVe":
                    _view.checkGloVeExpansionDictionary();
                    break;
                case "WordNet":
                    _view.checkWordNetExpansionDictionary();
                    break;
            }
        }
    }

    /* runs when the "search" button is clicked */
    private static class Search_runnable implements Runnable {
        @Override
        public void run() {
            _task = TASK.SEARCH;
            try {
                setExpansionDictionary(_search);
                setDocumentProps(_search);
                setRetrievalModel(_search);
                String query = _view.getQuery();
                long startTime = System.nanoTime();
                print(">>> Searching for: " + query + " ...\n");

                /* maximum results that should be returned */
                int endResult = 50;

                /* return all results when the retrieval model is existential, else return top endResult results */
                List<Result> results;
                if (_search.getRetrievalmodel() == ARetrievalModel.MODEL.EXISTENTIAL) {
                    results = _search.search(_view.getQuery());
                } else {
                    results = _search.search(_view.getQuery(), endResult);
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

        /* reads the expansion dictionary from view and updates search */
        private void setExpansionDictionary(Search search)
                throws IndexNotLoadedException, FileNotFoundException, JWNLException {
            String dictionary = _view.getExpansionDictionary();
            switch (dictionary) {
                case "None":
                    search.setExpansionDictionary(QueryExpansion.DICTIONARY.NONE);
                    break;
                case "GloVe":
                    search.setExpansionDictionary(QueryExpansion.DICTIONARY.GLOVE);
                    break;
                case "WordNet":
                    search.setExpansionDictionary(QueryExpansion.DICTIONARY.EXTJWNL);
                    break;
            }
        }

        /* reads the selected document props from view and updates search */
        private void setDocumentProps(Search search)
                throws IndexNotLoadedException {
            Set<DocInfo.PROPERTY> props = new HashSet<>();
            List<String> checkedProps = _view.getCheckedDocumentProps();
            for (String checkedProp : checkedProps) {
                switch (checkedProp) {
                    case "Title":
                        props.add(DocInfo.PROPERTY.TITLE);
                        break;
                    case "Authors":
                        props.add(DocInfo.PROPERTY.AUTHORS_NAMES);
                        break;
                    case "Author ids":
                        props.add(DocInfo.PROPERTY.AUTHORS_IDS);
                        break;
                    case "Journal":
                        props.add(DocInfo.PROPERTY.JOURNAL_NAME);
                        break;
                    case "Year":
                        props.add(DocInfo.PROPERTY.YEAR);
                        break;
                    case "Citations Pagerank":
                        props.add(DocInfo.PROPERTY.CITATIONS_PAGERANK);
                        break;
                    case "VSM Weight":
                        props.add(DocInfo.PROPERTY.VSM_WEIGHT);
                        break;
                    case "Token count":
                        props.add(DocInfo.PROPERTY.TOKEN_COUNT);
                        break;
                    case "Max TF":
                        props.add(DocInfo.PROPERTY.MAX_TF);
                        break;
                    case "Document Size":
                        props.add(DocInfo.PROPERTY.DOCUMENT_SIZE);
                        break;
                }
            }
            search.setDocumentProperties(props);
        }

        /* reads the query retrieval model from view and updates search */
        private void setRetrievalModel(Search search)
                throws IndexNotLoadedException, IOException {
            String model = _view.getRetrievalModel();
            switch (model) {
                case "Existential":
                    search.setRetrievalModel(ARetrievalModel.MODEL.EXISTENTIAL);
                    break;
                case "VSM":
                    search.setRetrievalModel(ARetrievalModel.MODEL.VSM);
                    break;
                case "BM25":
                    search.setRetrievalModel(ARetrievalModel.MODEL.OKAPI);
                    break;
            }
        }
    }
}
