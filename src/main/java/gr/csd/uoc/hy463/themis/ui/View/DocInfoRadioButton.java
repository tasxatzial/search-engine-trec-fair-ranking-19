package gr.csd.uoc.hy463.themis.ui.View;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * A checkbox that corresponds to a document property from {@link gr.csd.uoc.hy463.themis.indexer.model.DocInfo}
 */
public class DocInfoRadioButton extends JCheckBoxMenuItem {
    /**
     * Associate the document property specified by the given name to this check box.
     * @param name
     */
    public DocInfoRadioButton(String name) {
        super(name);
    }

    /* Changes the default action when clicking on a JCheckBoxMenuItem (the menu is closed).
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