package gr.csd.uoc.hy463.themis;

import gr.csd.uoc.hy463.themis.ui.CreateIndex;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.ui.View;

import javax.swing.*;
import java.awt.event.*;

public class Themis {
    public static CreateIndex createIndex;
    public static Search search;
    public static View view;

    public static void main(String[] args) {
        view = new View();

        /* close window button listener */
        view.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if ((createIndex != null && createIndex.isRunning() && !view.showQuitMessage(createIndex.getTask())) ||
                        (search != null && search.isRunning() && !view.showQuitMessage(search.getTask()))) {
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

    /* The listener for the "create index" menu item */
    private static class createIndexListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (init()) {
                createIndex = new CreateIndex();
                view.initIndexView();
                createIndex.create();
            }
        }

        private static boolean init() {
            if ((createIndex != null && createIndex.isRunning()) ||
                    (search != null && search.isRunning())) {
                return false;
            }
            if (createIndex != null) {
                createIndex.stop();
                while(createIndex.isRunning()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
            if (search != null) {
                search.stop();
                while (search.isRunning()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
                search.unloadIndex();
            }
            return true;
        }
    }

    /* The listener for the "query collection" menu item */
    private static class searchListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (createIndexListener.init()) {
                view.initSearchView();
            }
        }
    }

    /* The listener for the "load index" menu item */
    private static class loadIndexListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (createIndexListener.init()) {
                search = new Search();
                view.initIndexView();
                search.loadIndex();
            }
        }
    }
}
