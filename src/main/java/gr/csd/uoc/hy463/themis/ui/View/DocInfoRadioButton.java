package gr.csd.uoc.hy463.themis.ui.View;

import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * Used for associating a document property to a menu item.
 */
public class DocInfoRadioButton extends JCheckBoxMenuItem {
    private DocInfo.PROPERTY _prop;

    public DocInfoRadioButton(String name) {
        super(name);
        switch (name) {
            case "Title":
                _prop = DocInfo.PROPERTY.TITLE;
                break;
            case "Authors":
                _prop = DocInfo.PROPERTY.AUTHORS_NAMES;
                break;
            case "Author ids":
                _prop = DocInfo.PROPERTY.AUTHORS_IDS;
                break;
            case "Journal":
                _prop = DocInfo.PROPERTY.JOURNAL_NAME;
                break;
            case "Year":
                _prop = DocInfo.PROPERTY.YEAR;
                break;
            case "Pagerank":
                _prop = DocInfo.PROPERTY.PAGERANK;
                break;
            case "Weight":
                _prop = DocInfo.PROPERTY.WEIGHT;
                break;
            case "Length":
                _prop = DocInfo.PROPERTY.LENGTH;
                break;
            case "Max term frequency":
                _prop = DocInfo.PROPERTY.MAX_TF;
                break;
            case "Document size":
                _prop = DocInfo.PROPERTY.DOCUMENT_SIZE;
                break;
        }
    }

    /**
     * Returns the document property associated with the checkbox
     * @return
     */
    public DocInfo.PROPERTY get_prop() {
        return _prop;
    }

    /* Changes the default action of clicking on a JCheckBoxMenuItem (the menu is closed).
    This class changes this behavior so that the menu remains open.*/
    @Override
    protected void processMouseEvent(MouseEvent evt) {
        if (evt.getID() == MouseEvent.MOUSE_RELEASED && contains(evt.getPoint())) {
            doClick();
            setArmed(true);
        } else {
            super.processMouseEvent(evt);
        }
    }
}