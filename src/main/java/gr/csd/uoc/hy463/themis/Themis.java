package gr.csd.uoc.hy463.themis;

import gr.csd.uoc.hy463.themis.examples.GloveExample;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.metrics.themisEval;
import gr.csd.uoc.hy463.themis.queryExpansion.Glove;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.ui.CreateIndex;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.ui.View;
import gr.csd.uoc.hy463.themis.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.print.Doc;
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
                    view.setIndexViewBounds();
                    view.setTitleAreaBounds();
                    view.setSearchViewBounds();
                }
            });

            /* maximized window listeners */
            view.addWindowStateListener(e -> view.setIndexViewBounds());
            view.addWindowStateListener(e -> view.setTitleAreaBounds());
            view.addWindowStateListener(e -> view.setSearchViewBounds());

            /* add listeners on menu items */
            view.get_createIndex().addActionListener(new createIndexListener());
            view.get_queryCollection().addActionListener(new searchListener());
            view.get_loadIndex().addActionListener(new loadIndexListener());
            view.get_evaluateBM25().addActionListener(new evaluateBM25Listener());
            view.get_evaluateVSM().addActionListener(new evaluateVSMListener());

            view.setVisible(true);
        }
        else { //non GUI version
            /*search = new Search();
            List<Pair<Object, Double>> results;
            Set<DocInfo.PROPERTY> props = new HashSet<>();

            props.add(DocInfo.PROPERTY.MAX_TF);
            props.add(DocInfo.PROPERTY.LENGTH);
            props.add(DocInfo.PROPERTY.YEAR);
            props.add(DocInfo.PROPERTY.WEIGHT);
            props.add(DocInfo.PROPERTY.AVG_AUTHOR_RANK);
            props.add(DocInfo.PROPERTY.PAGERANK);

            Set<DocInfo.PROPERTY> props1 = new HashSet<>();
            props1.add(DocInfo.PROPERTY.TITLE);

            results = search.search("supernatural", props1);
            results = search.search("supernatural", props, 0, 10);
            search.printResults(results, 0, 12);*/
            try {
                search = new Search();
                search.setExpansionModelGlove();
                List<Pair<Object, Double>> results = search.search("mal");
                search.printResults(results, 0, 12);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
            view.initIndexView();
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
        }
    }

    /* The listener for the "load index" menu item */
    private static class loadIndexListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (_task != null) {
                return;
            }
            view.initIndexView();
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
            view.initIndexView();
            Evaluate_runnable evaluateVSM = new  Evaluate_runnable(ARetrievalModel.MODEL.VSM);
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
            view.initIndexView();
            Evaluate_runnable evaluateBM25 = new Evaluate_runnable(ARetrievalModel.MODEL.BM25);
            Thread runnableEvaluateBM25 = new Thread(evaluateBM25);
            runnableEvaluateBM25.start();
        }
    }

    private static class Evaluate_runnable implements Runnable {
        private ARetrievalModel.MODEL _model;

        public Evaluate_runnable(ARetrievalModel.MODEL model) {
            _model = model;
        }

        @Override
        public void run() {
            _task = TASK.EVALUATE;
            if (search == null) {
                createIndex = null;
                try {
                    search = new Search();
                } catch (IOException e) {
                    __LOGGER__.error(e.getMessage());
                    print("Failed to initialize search\n");
                    _task = null;
                    return;
                }
            }
            themisEval evaluator;
            try {
                evaluator = new themisEval(search);
            } catch (IOException e) {
                __LOGGER__.error(e.getMessage());
                print("Failed to initialize evaluator\n");
                _task = null;
                return;
            }
            try {
                if (_model == ARetrievalModel.MODEL.VSM) {
                    evaluator.evaluateVSM();
                } else if (_model == ARetrievalModel.MODEL.BM25) {
                    evaluator.evaluateBM25();
                }
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
            try {
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
            try {
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
            try {
                search = new Search();
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Failed to initialize\n");
            } finally {
                _task = null;
            }
        }
    }

    private static class Search_runnable implements Runnable {
        @Override
        public void run() {
            _task = TASK.SEARCH;
            List<Pair<Object, Double>> results;
            long startTime = System.nanoTime();
            String query = view.get_searchField().getText();
            print("Searching for: " + query + " ... ");
            try {
                results = search.search(view.get_searchField().getText());
                print("DONE\nSearch time: " + Math.round((System.nanoTime() - startTime) / 1e4) / 100.0 + " ms\n");
                print("Found " + results.size() + " results\n");
                search.printResults(results,0, 19);
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Search failed\n");
            } finally {
                _task = null;
            }
        }
    }
}
