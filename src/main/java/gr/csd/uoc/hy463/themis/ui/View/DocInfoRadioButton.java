package gr.csd.uoc.hy463.themis.ui.View;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * Used for associating a document property to a menu item.
 */
public class DocInfoRadioButton extends JCheckBoxMenuItem {
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