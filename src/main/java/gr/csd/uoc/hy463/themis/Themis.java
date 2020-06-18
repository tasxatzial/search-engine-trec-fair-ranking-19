package gr.csd.uoc.hy463.themis;

import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.metrics.themisEval;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.ui.CreateIndex;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.ui.View.MenuRadioButton;
import gr.csd.uoc.hy463.themis.ui.View.View;
import gr.csd.uoc.hy463.themis.ui.View.MenuCheckbox;
import gr.csd.uoc.hy463.themis.utils.Time;
import gr.csd.uoc.hy463.themis.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.event.*;
import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The main class. Outputs results in terminal but we also have an option of using a GUI which can be
 * enabled by passing swing_window as first argument.
 */
public class Themis {
    private static final Logger __LOGGER__ = LogManager.getLogger(Themis.class);

    private static CreateIndex createIndex;
    private static Search search;
    private static View view;
    private enum TASK {
        CREATE_INDEX, LOAD_INDEX, SEARCH, EVALUATE
    }
    private static TASK _task = null;

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("swing_window")) { //GUI version
            view = new View();

            /* close window button listener */
            view.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (_task != null && !view.showYesNoMessage(_task + " is in progress. Quit?")) {
                        view.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                    } else {
                        view.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                        System.exit(0);
                    }
                }
            });

            /* resized window listeners */
            view.addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent e) {
                    view.setOnlyResultsBounds();
                    view.setTitleAreaBounds();
                    view.setSearchViewBounds();
                }
            });

            /* maximized window listeners */
            view.addWindowStateListener(e -> view.setOnlyResultsBounds());
            view.addWindowStateListener(e -> view.setTitleAreaBounds());
            view.addWindowStateListener(e -> view.setSearchViewBounds());

            /* add listeners on menu items */
            view.get_createIndex().addActionListener(new createIndexListener());
            view.get_queryCollection().addActionListener(new searchListener());
            view.get_loadIndex().addActionListener(new loadIndexListener());
            view.get_evaluateBM25().addActionListener(new evaluateBM25Listener());
            view.get_evaluateVSM().addActionListener(new evaluateVSMListener());
            view.get_evaluateVSMGlove().addActionListener(new evaluateVSMGloveListener());
            view.get_evaluateBM25Glove().addActionListener(new evaluateBM25GloveListener());

            view.setVisible(true);
        }
        else { //non GUI version

        }
    }

    /**
     * Prints a string to view or to console if view is null.
     * @param text
     */
    public static void print(String text) {
        if (view == null) {
            System.out.print(text);
        }
        else {
            view.print(text);
        }
    }

    /* The listener for the "search" button click */
    private static class searchButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            view.clearResultsArea();
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
            view.initOnlyResultsView();
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
            view.initSearchView();
            view.get_searchButton().addActionListener(new searchButtonListener());
            if (search == null) {
                Thread runnableLoad = new Thread(new LoadIndex_runnable());
                runnableLoad.start();
            }
            else {
                view.enableSearchButton();
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
            view.initOnlyResultsView();
            Thread runnableLoad = new Thread(new LoadIndex_runnable());
            runnableLoad.start();
        }
    }

    /* The listener for the "evaluate VSM" menu item */
    private static class evaluateVSMListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            view.initOnlyResultsView();
            Evaluate_runnable evaluateVSM = new  Evaluate_runnable(ARetrievalModel.MODEL.VSM, QueryExpansion.DICTIONARY.NONE);
            Thread runnableEvaluateVSM = new Thread(evaluateVSM);
            runnableEvaluateVSM.start();
        }
    }

    /* The listener for the "evaluate VSM/Glove" menu item */
    private static class evaluateVSMGloveListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            view.initOnlyResultsView();
            Evaluate_runnable evaluateVSM = new  Evaluate_runnable(ARetrievalModel.MODEL.VSM, QueryExpansion.DICTIONARY.GLOVE);
            Thread runnableEvaluateVSM = new Thread(evaluateVSM);
            runnableEvaluateVSM.start();
        }
    }

    /* The listener for the "evaluate BM25" menu item */
    private static class evaluateBM25Listener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            view.initOnlyResultsView();
            Evaluate_runnable evaluateBM25 = new Evaluate_runnable(ARetrievalModel.MODEL.BM25, QueryExpansion.DICTIONARY.NONE);
            Thread runnableEvaluateBM25 = new Thread(evaluateBM25);
            runnableEvaluateBM25.start();
        }
    }

    /* The listener for the "evaluate BM25/Glove" menu item */
    private static class evaluateBM25GloveListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            view.initOnlyResultsView();
            Evaluate_runnable evaluateBM25 = new Evaluate_runnable(ARetrievalModel.MODEL.BM25, QueryExpansion.DICTIONARY.GLOVE);
            Thread runnableEvaluateBM25 = new Thread(evaluateBM25);
            runnableEvaluateBM25.start();
        }
    }

    private static class Evaluate_runnable implements Runnable {
        private ARetrievalModel.MODEL _model;
        private QueryExpansion.DICTIONARY _dictionary;

        public Evaluate_runnable(ARetrievalModel.MODEL model, QueryExpansion.DICTIONARY dictionary) {
            _model = model;
            _dictionary = dictionary;
        }

        @Override
        public void run() {
            _task = TASK.EVALUATE;
            if (search == null) {
                createIndex = null;
                try { //todo: close files
                    search = new Search();
                } catch (Exception e) {
                    __LOGGER__.error(e.getMessage());
                    print("Failed to initialize search\n");
                    _task = null;
                    return;
                }
            }
            themisEval evaluator;
            try { //todo: close files
                evaluator = new themisEval(search);
            } catch (IOException e) {
                __LOGGER__.error(e.getMessage());
                print("Failed to initialize evaluator\n");
                _task = null;
                return;
            }
            try { //todo: close files
                evaluator.evaluateInit(_model, _dictionary);
            } catch (IOException e) {
                __LOGGER__.error(e.getMessage());
                print("Evaluation failed\n");
            } finally {
                _task = null;
            }
        }
    }

    private static class CreateIndex_runnable implements Runnable {
        @Override
        public void run() {
            _task = TASK.CREATE_INDEX;
            try { //todo: close files
                createIndex = new CreateIndex();
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Failed to initialize\n");
                _task = null;
                return;
            }
            boolean deleteIndex = false;
            if (!createIndex.isIndexDirEmpty()) {
                deleteIndex = view.showYesNoMessage("Delete previous index folders?");
                if (!deleteIndex) {
                    _task = null;
                    return;
                }
            }
            try {
                if (search != null) {
                    search.unloadIndex();
                    search = null;
                }
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Failed to unload previous index\n");
                _task = null;
                return;
            }
            if (deleteIndex) {
                try {
                    createIndex.deleteIndex();
                } catch (IOException ex) {
                    __LOGGER__.error(ex.getMessage());
                    print("Failed to delete previous index\n");
                    _task = null;
                    return;
                }
            }
            try { //todo: close files
                createIndex.createIndex();
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Failed to create index\n");
            } finally {
                _task = null;
            }
        }
    }

    private static class LoadIndex_runnable implements Runnable {
        @Override
        public void run() {
            _task = TASK.LOAD_INDEX;
            try {
                if (search != null) {
                    search.unloadIndex();
                    search = null;
                }
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Failed to unload previous index\n");
                _task = null;
                return;
            }
            createIndex = null;
            try { //todo: close files
                search = new Search();
            } catch (Exception ex) {
                __LOGGER__.error(ex.getMessage());
                print("Failed to initialize search\n");
            } finally {
                _task = null;
                if (search != null) {
                    view.checkRetrievalModel(search.get_model());
                    view.enableSearchButton();
                }
            }
        }
    }

    private static class Search_runnable implements Runnable {
        @Override
        public void run() {
            _task = TASK.SEARCH;
            List<Pair<Object, Double>> results;

            //set the document properties we want to retrieve
            Set<DocInfo.PROPERTY> props = new HashSet<>();
            JMenu documentProperties = view.get_documentProperties();
            for (int i = 0; i < documentProperties.getItemCount(); i++) {
                if (documentProperties.getItem(i).isSelected()) {
                    props.add(((MenuCheckbox) documentProperties.getItem(i)).get_prop());
                }
            }
            search.setDocumentProperties(props);

            //set the retrieval model
            JMenu retrievalModel = view.get_retrievalModel();
            for (int i = 0; i < retrievalModel.getItemCount(); i++) {
                if (retrievalModel.getItem(i).isSelected()) {
                    search.setRetrievalModel(((MenuRadioButton) retrievalModel.getItem(i)).get_model());
                }
            }

            String query = view.get_searchField().getText();
            long startTime = System.nanoTime();
            print("Searching for: " + query + " ... ");
            try { //todo: close files
                results = search.search(view.get_searchField().getText(), 0, 9);
                print("DONE\nSearch time: " + new Time(System.nanoTime() - startTime) + "\n");
                print("Found " + results.size() + " results\n");
                search.printResults(results, 0, 9);
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Search failed\n");
            } finally {
                _task = null;
            }
        }
    }
}
