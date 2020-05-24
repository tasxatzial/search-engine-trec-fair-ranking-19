/*
 * themis - A fair search engine for scientific articles
 *
 * Currently over the Semantic Scholar Open Research Corpus
 * http://s2-public-api-prod.us-west-2.elasticbeanstalk.com/corpus/
 *
 * Collaborative work with the undergraduate/graduate students of
 * Information Retrieval Systems (hy463) course
 * Spring Semester 2020
 *
 * -- Writing code during COVID-19 pandemic times :-( --
 *
 * Aiming to participate in TREC 2020 Fair Ranking Track
 * https://fair-trec.github.io/
 *
 * Computer Science Department http://www.csd.uoc.gr
 * University of Crete
 * Greece
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.metrics;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.ui.Search;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class themisEval {
    private static final Logger __LOGGER__ = LogManager.getLogger(Indexer.class);
    private Search _search;
    private Config __CONFIG__;
    private String __JUDGEMENTS_FILENAME__;
    private String __EVALUATION_FILENAME__;

    public themisEval(Search search) throws IOException {
        _search = search;
        __CONFIG__ = new Config();
        __JUDGEMENTS_FILENAME__ = __CONFIG__.getJudgmentsFileName();
    }

    /**
     * Returns true if judgements file exists, false otherwise
     * @return
     */
    public boolean hasJudgements() {
        File file = new File(__JUDGEMENTS_FILENAME__);
        if (!file.exists()) {
            __LOGGER__.error("No judgements file found!");
            return false;
        }
        return true;
    }

    /**
     * Returns true if evaluation filename exists, false otherwise
     * @param filename
     * @return
     */
    public boolean hasEvaluation(String filename) {
        File file = new File(filename);
        if (file.exists()) {
            __LOGGER__.error("Evaluation file already exists!");
            return true;
        }
        return false;
    }

    /**
     * Runs the VSM evaluator
     */
    public void evaluateVSM() {
        if (!hasJudgements()) {
            __LOGGER__.error("VSM evaluation failed");
            Themis.print("No judgements file found! Evaluation failed\n");
            return;
        }
        String evaluationFilename = __CONFIG__.getVSMEvaluationFilename();
        if (hasEvaluation(evaluationFilename)) {
            __LOGGER__.error("VSM evaluation failed");
            Themis.print("VSM evaluation file already exists! Evaluation failed\n");
            return;
        }
        __EVALUATION_FILENAME__ = evaluationFilename;
        _search.setModelVSM();
        evaluate();
    }

    /**
     * Runs the BM25 evaluator
     */
    public void evaluateBM25() {
        if (!hasJudgements()) {
            __LOGGER__.error("BM25 evaluation failed");
            Themis.print("No judgements file found! Evaluation failed\n");
            return;
        }
        String evaluationFilename = __CONFIG__.getBM25EvaluationFilename();
        if (hasEvaluation(evaluationFilename)) {
            __LOGGER__.error("BM25 evaluation failed");
            Themis.print("BM25 evaluation file already exists! Evaluation failed\n");
            return;
        }
        __EVALUATION_FILENAME__ = evaluationFilename;
        _search.setModelBM25();
        evaluate();
    }

    private void evaluate() {

    }
}
