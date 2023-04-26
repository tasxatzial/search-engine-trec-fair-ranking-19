package gr.csd.uoc.hy463.themis.ui.View;

import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;

import javax.swing.*;

/**
 * Used for associating a search retrieval model to a menu item.
 */
public class RetrievalModelRadioButton extends JRadioButtonMenuItem {
    private ARetrievalModel.MODEL _model;

    public RetrievalModelRadioButton(String name) {
        super(name);
        switch (name) {
            case "Existential":
                _model = ARetrievalModel.MODEL.EXISTENTIAL;
                break;
            case "VSM":
                _model = ARetrievalModel.MODEL.VSM;
                break;
            case "BM25":
                _model = ARetrievalModel.MODEL.OKAPI;
                break;
        }
    }

    /**
     * Returns the search retrieval model associated with this radiobutton
     * @return
     */
    public ARetrievalModel.MODEL getRetrievalModel() {
        return _model;
    }
}
