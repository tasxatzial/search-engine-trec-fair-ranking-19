package gr.csd.uoc.hy463.themis.ui;

import javax.swing.*;
import java.awt.*;

public class View extends JFrame {

    /* The main menu bar */
    private JMenuBar _menu;

    /* The "create index" menu item */
    private JMenuItem _createIndex;

    /* The "load index" menu item */
    private JMenuItem _loadIndex;

    /* The "query collection" menu item */
    private JMenuItem _queryCollection;

    /* The font of any text except the menu/title items */
    private Font _textFont;

    /* Title area (top) */
    private JLabel _titleArea;

    /* Everything sits on top of this pane */
    private JLayeredPane _mainPane;

    public View() {
        initMenu();
        pack();
        setTitle("Themis search engine v0.1");
        setSize(800, 600); //initial frame size
        _textFont = new Font("SansSerif", Font.PLAIN, 16);
        _mainPane = new JLayeredPane();
        initTitleArea();
        getContentPane().add(_mainPane);
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

    /* Creates the title label */
    private void initTitleArea() {
        _titleArea = new JLabel("Themis");
        _titleArea.setFont(new Font("SansSerif", Font.PLAIN, 24));
        _titleArea.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.BLACK));
        setTitleAreaBounds();
        _mainPane.add(_titleArea);
    }

    /**
     * Sets the proper bounds of the title area
     */
    public void setTitleAreaBounds() {
        Dimension frameDim = getBounds().getSize();
        int titleAreaWidth = frameDim.width - getInsets().left - getInsets().right;
        int titleAreaHeight = 50;
        int titleAreaX = 0;
        int titleAreaY = 0;
        _titleArea.setBounds(titleAreaX, titleAreaY, titleAreaWidth, titleAreaHeight);
        _titleArea.setHorizontalAlignment(SwingConstants.CENTER);
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
