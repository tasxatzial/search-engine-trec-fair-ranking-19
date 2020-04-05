package gr.csd.uoc.hy463.themis.ui;

import javax.swing.*;

public class View extends JFrame {

    /* The main menu bar */
    private JMenuBar _menu;

    /* The "create index" menu item */
    private JMenuItem _createIndex;

    /* The "load index" menu item */
    private JMenuItem _loadIndex;

    /* The "query collection" menu item */
    private JMenuItem _queryCollection;

    public View() {
        initMenu();
        pack();
        setTitle("Themis search engine v0.1");
        setSize(800, 600); //initial frame size
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
        _queryCollection = new JMenuItem("Query collection");
        search.add(_queryCollection);

        /* main menu bar */
        _menu = new JMenuBar();
        _menu.add(index);
        _menu.add(search);

        setJMenuBar(_menu);
    }

    /**
     * Modifies the view when the "create index" menu item is clicked.
     */
    public void initIndexView() {

    }

    /**
     * Returns the "create index" menu item
     * @return
     */
    public JMenuItem get_createIndex() {
        return _createIndex;
    }
}
