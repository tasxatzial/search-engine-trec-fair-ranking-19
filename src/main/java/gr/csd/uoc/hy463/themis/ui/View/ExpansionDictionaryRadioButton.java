package gr.csd.uoc.hy463.themis.ui.View;

import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;

import javax.swing.*;

/**
 * Used for associating a query expansion dictionary to a menu item.
 */
public class ExpansionDictionaryRadioButton extends JRadioButtonMenuItem {
    private QueryExpansion.DICTIONARY _dictionary;

    public ExpansionDictionaryRadioButton(String name) {
        super(name);
        switch (name) {
            case "None":
                _dictionary = QueryExpansion.DICTIONARY.NONE;
                break;
            case "Glove":
                _dictionary = QueryExpansion.DICTIONARY.GLOVE;
                break;
            case "extJWNL":
                _dictionary = QueryExpansion.DICTIONARY.EXTJWNL;
                break;
        }
    }

    /**
     * Returns the query expansion dictionary associated with this radiobutton
     * @return
     */
    public QueryExpansion.DICTIONARY get_dictionary() {
        return _dictionary;
    }
}
