package gr.csd.uoc.hy463.themis;

import gr.csd.uoc.hy463.themis.ui.CreateIndex;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.ui.View;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Themis {
    public static CreateIndex createIndex;
    public static Search search;
    public static View view;

    public static void main(String[] args) {
        view = new View();

        /* add a listeners menu items */
        view.get_createIndex().addActionListener(new createIndexListener());
        view.get_queryCollection().addActionListener(new searchListener());

        view.setVisible(true);
    }

    /* The listener for the "create index" menu item */
    private static class createIndexListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (init()) {
                createIndex.create();
            }
        }

        private static boolean init() {
            if ((createIndex != null && createIndex.isRunning())) {
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
            createIndex = new CreateIndex();
            view.initIndexView();
            return true;
        }
    }

    private static class searchListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (createIndexListener.init()) {
                view.initSearchView();
            }
        }
    }
}
