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

public class Themis {
    private static final Logger __LOGGER__ = LogManager.getLogger(Themis.class);

    private static CreateIndex createIndex;
    private static Search search;
    private static View view;

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("swing_window")) {
            view = new View();

            /* close window button listener */
            view.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if ((createIndex != null && createIndex.isRunning() && !view.showYesNoMessage(createIndex.getTask())) ||
                            (search != null && search.isRunning() && !view.showYesNoMessage(search.getTask()))) {
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
        else {
            //change the code here
            createIndex = new CreateIndex();
            createIndex.deleteIndex();
            createIndex.createIndex();
        }
    }

    /**
     * Prints a string to view or to console if view is null.
     * @param text
     */
    public static void print(String text) {
        try {
            if (view == null) {
                System.out.print(text);
            }
            else {
                view.print(text);
            }
        } catch (IOException e) {
            __LOGGER__.error(e.getMessage());
        }
    }

    /**
     * Displays a swing window showing an error text message.
     * @param text
     */
    public static void showError(String text) {
        if (view == null) {
            System.out.println(text);
        }
        else {
            view.showError(text);
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
            try {
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
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
            }
        }
    }

    /* The listener for the "create index" menu item */
    private static class createIndexListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if ((createIndex != null && createIndex.isRunning()) || (search != null && search.isRunning())) {
                return;
            }
            try {
                createIndex = new CreateIndex();
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                view.showError("Failed to initialize indexer");
                return;
            }
            if (createIndex.isIndexDirEmpty()) {
                view.initIndexView();
                createIndex.createIndex();
            }
            else {
                boolean delete = view.showYesNoMessage("Delete previous index folder?");
                if (delete) {
                    try {
                        view.initIndexView();
                        createIndex.deleteIndex();
                    } catch (IOException ex) {
                        __LOGGER__.error(ex.getMessage());
                        view.showError("Failed to delete previous index");
                        return;
                    }
                    try {
                        if (search != null) {
                            search.unloadIndex();
                        }
                    } catch (IOException ex) {
                        __LOGGER__.error(ex.getMessage());
                        view.showError("Failed to unload previous index");
                        return;
                    }
                    search = null;
                    createIndex.createIndex();
                }
            }
        }
    }

    /* The listener for the "query collection" menu item */
    private static class searchListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if ((createIndex != null && createIndex.isRunning()) || (search != null && search.isRunning())) {
                return;
            }
            if (search == null || !search.isIndexLoaded()) {
                view.showError("Index is not loaded!");
                return;
            }
            view.initSearchView();
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
            try {
                if (search != null) {
                    search.unloadIndex();
                }
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                view.showError("Failed to unload previous index");
                return;
            }
            try {
                view.initIndexView();
                search = new Search();
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                view.showError("Failed to initialize indexer");
            }
            createIndex = null;
            search.loadIndex();
        }
    }
}
