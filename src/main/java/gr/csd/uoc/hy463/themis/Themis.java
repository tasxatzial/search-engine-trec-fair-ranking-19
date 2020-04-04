package gr.csd.uoc.hy463.themis;

import gr.csd.uoc.hy463.themis.ui.CreateIndex;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.ui.View;

public class Themis {
    public static CreateIndex createIndex;
    public static Search search;
    public static View view;

    public static void main(String[] args) {
        createIndex = new CreateIndex();
        createIndex.create();

        view = new View();
        view.setVisible(true);
    }
}
