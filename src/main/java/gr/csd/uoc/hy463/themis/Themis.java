package gr.csd.uoc.hy463.themis;

import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.ui.CreateIndex;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.ui.View;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.HashSet;
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

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("swing_window")) { //GUI version
            view = new View();

            /* close window button listener */
            view.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if ((createIndex != null && createIndex.isRunning() &&
                            !view.showYesNoMessage(createIndex.get_task() + " is in progress. Quit?")) ||
                        (search != null && search.isRunning() &&
                            !view.showYesNoMessage(search.get_task() + " is in progress. Quit?"))) {
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

            /* add a listeners on menu items */
            view.get_createIndex().addActionListener(new createIndexListener());
            view.get_queryCollection().addActionListener(new searchListener());
            view.get_loadIndex().addActionListener(new loadIndexListener());

            view.setVisible(true);
        }
        else { //non GUI version
            createIndex = new CreateIndex();
            createIndex.createIndex(true);
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

    /**
     * Clears the results print area.
     */
    public static void clearResults() {
        if (view != null) {
            view.clearResultsArea();
        }
    }

    private static class searchButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            Set<DocInfo.PROPERTY> props = new HashSet<>();
            props.add(DocInfo.PROPERTY.PAGERANK);
            props.add(DocInfo.PROPERTY.WEIGHT);
            props.add(DocInfo.PROPERTY.LENGTH);
            props.add(DocInfo.PROPERTY.AVG_AUTHOR_RANK);
            props.add(DocInfo.PROPERTY.YEAR);
            props.add(DocInfo.PROPERTY.TITLE);
            props.add(DocInfo.PROPERTY.AUTHORS_NAMES);
            props.add(DocInfo.PROPERTY.AUTHORS_IDS);
            props.add(DocInfo.PROPERTY.JOURNAL_NAME);
            props.add(DocInfo.PROPERTY.MAX_TF);
            search.search(view.get_searchField().getText(), props, -1);
        }
    }

    /* The listener for the "create index" menu item */
    private static class createIndexListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if ((createIndex != null && createIndex.isRunning()) || (search != null && search.isRunning())) {
                return;
            }
            view.initIndexView();
            try {
                createIndex = new CreateIndex();
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Failed to initialize indexer\n");
                return;
            }
            boolean deletePreviousIndex = false;
            if (!createIndex.isIndexDirEmpty()) {
                deletePreviousIndex = view.showYesNoMessage("Delete previous index folders?");
                if (!deletePreviousIndex) {
                    createIndex = null;
                    return;
                }
            }
            try {
                if (search != null) {
                    search.unloadIndex();
                }
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Failed to unload previous index\n");
                return;
            }
            finally {
                search = null;
            }
            createIndex.createIndex(deletePreviousIndex);
        }
    }

    /* The listener for the "query collection" menu item */
    private static class searchListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if ((createIndex != null && createIndex.isRunning()) || (search != null && search.isRunning())) {
                return;
            }
            view.initSearchView();
            if (search == null || !search.isIndexLoaded()) {
                __LOGGER__.error("Index is not loaded!");
                print("Index is not loaded!\n");
                return;
            }
            view.get_searchButton().addActionListener(new searchButtonListener());
        }
    }

    /* The listener for the "load index" menu item */
    private static class loadIndexListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if ((createIndex != null && createIndex.isRunning()) || (search != null && search.isRunning())) {
                return;
            }
            view.initIndexView();
            try {
                if (search != null) {
                    search.unloadIndex();
                }
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Failed to unload previous index\n");
                return;
            }
            finally {
                search = null;
            }
            try {
                search = new Search();
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                print("Failed to initialize indexer\n");
                return;
            }
            createIndex = null;
            search.loadIndex();
        }
    }
}
