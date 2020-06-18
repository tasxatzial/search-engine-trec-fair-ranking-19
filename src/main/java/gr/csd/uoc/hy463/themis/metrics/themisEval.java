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
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.utils.Time;
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
    }

    /**
     * Returns true if judgements file exists, false otherwise
     * @return
     */
    public boolean hasJudgements() {
        File file = new File(__JUDGEMENTS_FILENAME__);
        if (!file.exists()) {
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
            return true;
        }
        return false;
    }

    /**
     * Sets the search model to the specified model and the query expansion dictionary to the specified
     * dictionary. Also opens the appropriate files for writing the results of the evaluation.
     * @param model
     * @param dictionary
     * @throws IOException
     */
    public void evaluateInit(ARetrievalModel.MODEL model, QueryExpansion.DICTIONARY dictionary) throws IOException {
        __JUDGEMENTS_FILENAME__ = __CONFIG__.getJudgmentsFileName();
        String __INDEX_PATH__ = __CONFIG__.getIndexPath();
        if (!hasJudgements()) {
            if (dictionary == QueryExpansion.DICTIONARY.NONE) {
                __LOGGER__.error("No judgements file found! " + model + " evaluation failed");
                Themis.print("No judgements file found! " + model + " evaluation failed\n");
            }
            else if (dictionary == QueryExpansion.DICTIONARY.GLOVE) {
                __LOGGER__.error("No judgements file found! " + model + "/Glove evaluation failed");
                Themis.print("No judgements file found! " + model + "/Glove evaluation failed\n");
            }
            return;
        }
        String evaluationFilename = null;
        if (dictionary == QueryExpansion.DICTIONARY.NONE) {
            if (model == ARetrievalModel.MODEL.VSM) {
                evaluationFilename = __INDEX_PATH__ + "/" + __CONFIG__.getVSMEvaluationFilename();
            }
            else if (model == ARetrievalModel.MODEL.BM25) {
                evaluationFilename = __INDEX_PATH__ + "/" + __CONFIG__.getBM25EvaluationFilename();
            }
        }
        else if (dictionary == QueryExpansion.DICTIONARY.GLOVE) {
            if (model == ARetrievalModel.MODEL.VSM) {
                evaluationFilename = __INDEX_PATH__ + "/" + __CONFIG__.getVSMGloveEvaluationFilename();
            }
            else if (model == ARetrievalModel.MODEL.BM25) {
                evaluationFilename = __INDEX_PATH__ + "/" + __CONFIG__.getBM25GloveEvaluationFilename();
            }
        }
        if (hasEvaluation(evaluationFilename)) {
            if (dictionary == QueryExpansion.DICTIONARY.NONE) {
                __LOGGER__.error(model + " evaluation file already exists! Evaluation failed");
                Themis.print(model + " evaluation file already exists! Evaluation failed\n");
            }
            else if (dictionary == QueryExpansion.DICTIONARY.GLOVE) {
                __LOGGER__.error(model + "/Glove evaluation file already exists! Evaluation failed");
                Themis.print(model + "/Glove evaluation file already exists! Evaluation failed\n");
            }
            return;
        }
        _search.setRetrievalModel(model);
        _search.setExpansionModel(dictionary);
        __EVALUATION_FILENAME__ = evaluationFilename;
        evaluate();
    }

    /* the evaluation function */
    private void evaluate() throws IOException {
        BufferedReader judgementsReader = new BufferedReader(new InputStreamReader(new FileInputStream(__JUDGEMENTS_FILENAME__), "UTF-8"));
        BufferedWriter evaluationWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(__EVALUATION_FILENAME__), "UTF-8"));
        Themis.print("------------------------------------------------\n");
        Themis.print(">>> Saving results in " + __EVALUATION_FILENAME__ + "\n\n");
        evaluationWriter.write(">>> Using options:\n");
        evaluationWriter.write("Retrieval model weight: " + __CONFIG__.getRetrievalModelWeight() + "\n");
        evaluationWriter.write("Pagerank citations weight: " + __CONFIG__.getPagerankPublicationsWeight() + "\n");
        evaluationWriter.write("Pagerank authors weight: " + __CONFIG__.getPagerankAuthorsWeight() + "\n");
        evaluationWriter.write("------------------------------------------------\n");
        String line;
        JSONParser parser = new JSONParser();
        List<Double> aveps = new ArrayList<>();
        List<Double> ndcgs = new ArrayList<>();
        List<Pair<String, Time>> queryTime = new ArrayList<>();
        Time totalTime = new Time(0);
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
            evaluationWriter.write(">>> Search query: " + query + "\n");
            Themis.print(">>> Search query: " + query + "\n");
            long startTime = System.nanoTime();
            List<Pair<Object, Double>> results = _search.search(query);
            long endTime = System.nanoTime();

            //calculate the time needed
            Time time = new Time(endTime - startTime);
            totalTime.addTime(time);
            queryTime.add(new Pair<>(query, time));
            evaluationWriter.write("Time: " + time + "\n");

            //calculate average precision, nDCG
            double avep = computeAveP(results, relevanceMap);
            avep = (Double.isNaN(avep)) ? Double.NaN : avep;
            aveps.add(avep);
            double ndcg = computeNdcg(results, relevanceMap);
            ndcg = (Double.isNaN(ndcg)) ? Double.NaN : ndcg;
            ndcgs.add(ndcg);
            evaluationWriter.write("Average precision: " + avep + "\n");
            evaluationWriter.write("nDCG: " + ndcg + "\n");
            evaluationWriter.flush();
        }

        double averageAvep = calculateAverage(aveps);
        double minAvep = calculateMin(aveps);
        double maxAvep = calculateMax(aveps);

        double averageNdcg = calculateAverage(ndcgs);
        double minNdcg = calculateMin(ndcgs);
        double maxNdcg = calculateMax(ndcgs);

        Pair<String, Time> minTime = calculateMinTime(queryTime);
        Pair<String, Time> maxTime = calculateMaxTime(queryTime);
        Time averageTime = calculateAverageTime(queryTime);

        Themis.print("\n>>> End of evaluation\n");
        evaluationWriter.write("------------------------------------------------\n");
        evaluationWriter.write("Summary:\n\n");
        evaluationWriter.write("Average precision:\n");
        evaluationWriter.write("Average: " + averageAvep + "\n");
        evaluationWriter.write("Min: " + minAvep + "\n");
        evaluationWriter.write("Max: " + maxAvep + "\n\n");
        evaluationWriter.write("nDCG:\n");
        evaluationWriter.write("Average: " + averageNdcg + "\n");
        evaluationWriter.write("Min: " + minNdcg + "\n");
        evaluationWriter.write("Max: " + maxNdcg + "\n\n");
        evaluationWriter.write("Time:\n");
        evaluationWriter.write("Total: " + totalTime + "\n");
        evaluationWriter.write("Average: " + averageTime + "\n");
        evaluationWriter.write("Min: " + minTime.getR() + " for query: " + minTime.getL() + "\n");
        evaluationWriter.write("Max: " + maxTime.getR() + " for query: " + maxTime.getL() + "\n");
        evaluationWriter.close();
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
    private static Pair<String, Time> calculateMaxTime(List<Pair<String, Time>> list) {
        if (list.isEmpty()) {
            return new Pair("", new Time(0));
        }
        Pair<String, Time> max = list.get(0);
        for (Pair<String, Time> pair : list) {
            if (pair.getR().getValue() > max.getR().getValue()) {
                max = pair;
            }
        }
        return max;
    }

    /* returns the maximum search time and the corresponding query as a pair */
    private static Pair<String, Time> calculateMinTime(List<Pair<String, Time>> list) {
        if (list.isEmpty()) {
            return new Pair("", new Time(0));
        }
        Pair<String, Time> min = list.get(0);
        for (Pair<String, Time> pair : list) {
            if (pair.getR().getValue() < min.getR().getValue()) {
                min = pair;
            }
        }
        return min;
    }

    /* returns the average search time */
    private static Time calculateAverageTime(List<Pair<String, Time>> list) {
        if (list.isEmpty()) {
            return new Time(0);
        }
        Time time = new Time(0);
        for (Pair<String, Time> pair : list) {
            time.addTime(pair.getR());
        }
        return new Time(time.getValue() / list.size());
    }
}
