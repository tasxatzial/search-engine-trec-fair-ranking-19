package gr.csd.uoc.hy463.themis;

import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.retrieval.models.Existential;
import gr.csd.uoc.hy463.themis.ui.CreateIndex;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.ui.View;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.event.*;
import java.io.IOException;

public class Themis {
    private static final Logger __LOGGER__ = LogManager.getLogger(CreateIndex.class);

    public static CreateIndex createIndex;
    public static Search search;
    public static View view;

    public static void main(String[] args) {
        view = new View();

        /* close window button listener */
        view.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if ((createIndex != null && createIndex.isRunning() && !view.showYesNoMessage(createIndex.getTask())) ||
                        (search != null && search.isRunning() && !view.showYesNoMessage(search.getTask()))) {
                    view.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                }
                else {
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

    private static class searchButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                view.clearResultsArea();
                search.search(view.get_searchField().getText(), ARetrievalModel.RESULT_TYPE.PLAIN);
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
            view.initIndexView();
            if (search != null && !search.unloadIndex()) {
                view.showError("Failed to unload previous index");
                return;
            }
            try {
                search = null;
                createIndex = new CreateIndex();
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                view.showError("Failed to initialize indexer");
                return;
            }
            if (createIndex.isIndexDirEmpty()) {
                createIndex.createIndex();
            }
            else {
                boolean delete = view.showYesNoMessage("Delete previous index folder?");
                if (delete) {
                    try {
                        createIndex.deleteIndex();
                    } catch (IOException ex) {
                        __LOGGER__.error(ex.getMessage());
                        view.showError("Failed to delete previous index");
                        return;
                    }
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
            if (view.get_searchButton().getActionListeners().length == 0) {
                view.get_searchButton().addActionListener(new searchButtonListener());
            }
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
            if (search != null && !search.unloadIndex()) {
                view.showError("Failed to unload previous index");
                return;
            }
            try {
                createIndex = null;
                search = new Search();
            } catch (IOException ex) {
                __LOGGER__.error(ex.getMessage());
                view.showError("Failed to initialize indexer");
            }
            search.loadIndex();
        }
    }
}
