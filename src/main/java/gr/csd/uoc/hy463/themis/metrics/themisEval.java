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
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.*;

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
    public void evaluateVSM() throws IOException {
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
    public void evaluateBM25() throws IOException {
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

    /* the common evaluation function */
    private void evaluate() throws IOException {
        BufferedReader judgementsReader = new BufferedReader(new InputStreamReader(new FileInputStream(__JUDGEMENTS_FILENAME__), "UTF-8"));
        BufferedWriter evaluationWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(__EVALUATION_FILENAME__), "UTF-8"));
        Themis.print("Saving results in " + __EVALUATION_FILENAME__ + "\n");
        Themis.print("------------------------------------------------\n");
        evaluationWriter.write("Index directory: " + _search.getIndexDirectory() + "\n");
        evaluationWriter.write("Query expansion model: " + _search.getQueryExpansionModel() + "\n");
        String line;
        JSONParser parser = new JSONParser();
        List<Double> aveps = new ArrayList<>();
        //List<Double> bprefs = new ArrayList<>();
        List<Double> ndcgs = new ArrayList<>();
        List<Pair<String, Double>> queryTime = new ArrayList<>();
        while ((line = judgementsReader.readLine()) != null) {
            Object obj;
            try {
                obj = parser.parse(line);
            } catch (ParseException e) {
                __LOGGER__.error(e.getMessage());
                continue;
            }
            JSONObject jsonObject = (JSONObject) obj;
            JSONArray documentsArray = (JSONArray) jsonObject.get("documents");
            String query = (String) jsonObject.get("query");

            //construct a map of (docId, binary relevance value) for this query
            Map<String, Long> relevanceMap = new HashMap<>();
            for (Object o : documentsArray) {
                JSONObject doc = (JSONObject) o;
                relevanceMap.put((String) doc.get("doc_id"), (Long) doc.get("relevance"));
            }

            //perform a search
            evaluationWriter.write("------------------------------------------------\n");
            evaluationWriter.write("Search query: " + query + "\n");
            Themis.print("Search query: " + query + "\n");
            long startTime = System.nanoTime();
            List<Pair<Object, Double>> results = _search.search(query);
            long endTime = System.nanoTime();

            //calculate the time needed
            Double timeSecs = Math.round((endTime - startTime) / 1e7) / 100.0;
            queryTime.add(new Pair<>(query, timeSecs));
            evaluationWriter.write("Time: " + timeSecs + "s\n");

            //calculate average precision, nDCG
            double avep = computeAveP(results, relevanceMap);
            aveps.add(avep);
            /*double bpref = computeBpref(results, relevanceMap);
            bprefs.add(bpref);*/
            double ndcg = computeNdcg(results, relevanceMap);
            ndcgs.add(ndcg);
            evaluationWriter.write("Average precision: " + avep + "\n");
            //evaluationWriter.write("bpref: " + bpref + "\n");
            evaluationWriter.write("nDCG: " + ndcg + "\n");
            evaluationWriter.flush();
        }

        double averageAvep = calculateAverage(aveps);
        double minAvep = calculateMin(aveps);
        double maxAvep = calculateMax(aveps);

        /*double averageBpref = calculateAverage(bprefs);
        double minBpref = calculateMin(bprefs);
        double maxBpref = calculateMax(bprefs);*/

        double averageNdcg = calculateAverage(ndcgs);
        double minNdcg = calculateMin(ndcgs);
        double maxNdcg = calculateMax(ndcgs);

        Pair<String, Double> minTime = calculateMinTime(queryTime);
        Pair<String, Double> maxTime = calculateMaxTime(queryTime);
        double averageTime = calculateAverageTime(queryTime);

        Themis.print("------------------------------------------------\n");
        evaluationWriter.write("------------------------------------------------\n");
        evaluationWriter.write("Summary:\n\n");
        evaluationWriter.write("Average precision:\n");
        evaluationWriter.write("Average: " + averageAvep + "\n");
        evaluationWriter.write("Min: " + minAvep + "\n");
        evaluationWriter.write("Max: " + maxAvep + "\n\n");
        /*evaluationWriter.write("bpref:\n");
        evaluationWriter.write("Average: " + averageBpref + "\n");
        evaluationWriter.write("Min: " + minBpref + "\n");
        evaluationWriter.write("Max: " + maxBpref + "\n\n");*/
        evaluationWriter.write("nDCG:\n");
        evaluationWriter.write("Average: " + averageNdcg + "\n");
        evaluationWriter.write("Min: " + minNdcg + "\n");
        evaluationWriter.write("Max: " + maxNdcg + "\n\n");
        evaluationWriter.write("Time:\n");
        evaluationWriter.write("Average: " + Math.round(averageTime * 100) / 100 + "s\n");
        evaluationWriter.write("Min: " + minTime.getR() + "s for query: " + minTime.getL() + "\n");
        evaluationWriter.write("Max: " + maxTime.getR() + "s for query: " + maxTime.getL() + "\n");
        evaluationWriter.close();
        Themis.print("Evaluation results saved in " + __EVALUATION_FILENAME__ + "\n");
        judgementsReader.close();
    }

    /* calculates the average precision given a ranked list of results and a map of (docId, binary relevance value) */
    private static double computeAveP(List<Pair<Object, Double>> results, Map<String, Long> relevanceMap) {
        double avep = 0;
        int relevantDocuments = 0;
        int nonSkippedDocuments = 0;

        for (Pair<Object, Double> rankedDocument : results) {
            String docId = ((DocInfo) rankedDocument.getL()).getId();
            Long isJudged = relevanceMap.get(docId);
            if (isJudged != null) {
                nonSkippedDocuments++;
                if (isJudged == 1) {
                    relevantDocuments++;
                    avep += (0.0 + isJudged * relevantDocuments) / nonSkippedDocuments;
                }
            }
        }
        avep /= relevantDocuments;

        return avep;
    }

    /* calculates the bpref given a ranked list of results and a map of (docId, binary relevance value)
    private static double computeBpref(List<Pair<Object, Double>> results, Map<String, Long> relevanceMap) {
        double bpref = 0;
        int knownRelevantDocuments = 0;
        int knownIrrelevantDocuments = 0;
        int irrelevantDocuments = 0;

        for (Long relevance : relevanceMap.values()) {
            if (relevance == 1) {
                knownRelevantDocuments++;
            }
        }
        knownIrrelevantDocuments = relevanceMap.size() - knownRelevantDocuments;

        if (knownIrrelevantDocuments < knownRelevantDocuments) {
            bpref = computeBpref10(results, relevanceMap);
        }
        else {
            for (Pair<Object, Double> result : results) {
                String docId = ((DocInfo) result.getL()).getId();
                Long isJudged = relevanceMap.get(docId);
                if (isJudged != null) {
                    if (isJudged == 1) {
                        bpref += 1 - (0.0 + irrelevantDocuments) / knownIrrelevantDocuments;
                    } else {
                        irrelevantDocuments++;
                    }
                }
            }
            bpref /= knownRelevantDocuments;
        }

        return bpref;
    }

    calculates the bpref10 given a ranked list of results and a map of (docId, binary relevance value)
    private static double computeBpref10(List<Pair<Object, Double>> results, Map<String, Long> relevanceMap) {
        return 0;
    }*/

    /* calculates the nDCG given a ranked list of results and a map of (docId, binary relevance value) */
    private static double computeNdcg(List<Pair<Object, Double>> results, Map<String, Long> relevanceMap) {
        double dcg = 0;
        double idcg = 0;
        int nonSkippedDocuments = 0;
        int relevantDocuments = 0;
        for (Pair<Object, Double> result : results) {
            String docId = ((DocInfo) result.getL()).getId();
            Long isJudged = relevanceMap.get(docId);
            if (isJudged != null) {
                nonSkippedDocuments++;
                if (isJudged == 1) {
                    relevantDocuments++;
                    dcg += Math.log(2) / Math.log(nonSkippedDocuments + 1);
                }
            }
        }
        for (int i = 1; i <= relevantDocuments; i++) {
            idcg += Math.log(2) / Math.log(i + 1);
        }

        return dcg / idcg;
    }

    /* calculates the average in a list of doubles */
    private static double calculateAverage(List<Double> list) {
        double sum = 0;
        if (list.isEmpty()) {
            return 0;
        }
        int numbers = 0;
        for (Double value : list) {
            if (value > -Double.MIN_VALUE && Double.isFinite(value)) {
                numbers++;
                sum += value;
            }
        }
        return 1.0 * sum / numbers;
    }

    /* calculates the min in a list of doubles */
    private static double calculateMin(List<Double> list) {
        double min;
        if (list.isEmpty()) {
            return Double.NaN;
        }
        int i = 0;
        while (i < list.size() && (list.get(i) < -Double.MIN_VALUE || !Double.isFinite(list.get(i)))) {
            i++;
        }
        if (i == list.size()) {
            return Double.NaN;
        }
        min = list.get(i);
        for (Double value : list) {
            if (value > -Double.MIN_VALUE && Double.isFinite(value) && value < min) {
                min = value;
            }
        }
        return min;
    }

    /* calculates the max in a list of doubles */
    private static double calculateMax(List<Double> list) {
        double max;
        if (list.isEmpty()) {
            return Double.NaN;
        }
        int i = 0;
        while (i < list.size() && (list.get(i) < -Double.MIN_VALUE || !Double.isFinite(list.get(i)))) {
            i++;
        }
        if (i == list.size()) {
            return Double.NaN;
        }
        max = list.get(i);
        for (Double value : list) {
            if (value > -Double.MIN_VALUE && Double.isFinite(value) && value > max) {
                max = value;
            }
        }
        return max;
    }

    /* returns the minimum search time and the corresponding query as a pair */
    private static Pair<String, Double> calculateMaxTime(List<Pair<String, Double>> list) {
        if (list.isEmpty()) {
            return new Pair(0, "");
        }
        Pair<String, Double> max = list.get(0);
        for (Pair<String, Double> pair : list) {
            if (pair.getR() > max.getR()) {
                max = pair;
            }
        }
        return max;
    }

    /* returns the maximum search time and the corresponding query as a pair */
    private static Pair<String, Double> calculateMinTime(List<Pair<String, Double>> list) {
        if (list.isEmpty()) {
            return new Pair(0, "");
        }
        Pair<String, Double> min = list.get(0);
        for (Pair<String, Double> pair : list) {
            if (pair.getR() < min.getR()) {
                min = pair;
            }
        }
        return min;
    }

    /* returns the average search time */
    private static double calculateAverageTime(List<Pair<String, Double>> list) {
        if (list.isEmpty()) {
            return 0;
        }
        double time = 0;
        for (Pair<String, Double> stringDoublePair : list) {
            time += stringDoublePair.getR();
        }
        return time / list.size();
    }
}
