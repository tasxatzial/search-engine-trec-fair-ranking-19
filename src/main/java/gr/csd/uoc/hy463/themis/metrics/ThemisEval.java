package gr.csd.uoc.hy463.themis.metrics;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.ExpansionDictionaryInitException;
import gr.csd.uoc.hy463.themis.retrieval.model.Result;
import gr.csd.uoc.hy463.themis.retrieval.models.ARetrievalModel;
import gr.csd.uoc.hy463.themis.ui.Search;
import gr.csd.uoc.hy463.themis.indexer.Exceptions.IndexNotLoadedException;
import gr.csd.uoc.hy463.themis.utils.Time;
import gr.csd.uoc.hy463.themis.utils.Pair;
import net.sf.extjwnl.JWNLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * The main class responsible for running an evaluation of the index.
 */
public class ThemisEval {
    private static final Logger __LOGGER__ = LogManager.getLogger(Indexer.class);
    private final Search _search;

    /**
     * Constructor.
     *
     * Reads configuration options from the given Search and sets its retrieval model and query expansion dictionary.
     *
     * @param search
     * @param model
     * @param dictionary
     * @throws IOException
     * @throws IndexNotLoadedException
     * @throws ExpansionDictionaryInitException
     */
    public ThemisEval(Search search, ARetrievalModel.MODEL model, QueryExpansion.DICTIONARY dictionary, double documentPagerankWeight)
            throws IOException, IndexNotLoadedException, ExpansionDictionaryInitException {
        _search = search;
        _search.setRetrievalModel(model);
        _search.setExpansionDictionary(dictionary);
        _search.setDocumentProperties(new HashSet<>());
        _search.setDocumentPagerankWeight(documentPagerankWeight);
    }

    /**
     * Initializes and runs the evaluation:
     * 1) Parses JUDGEMENTS_FILE
     * 2) Performs a search for each query
     * 3) Writes results to EVALUATION_FILENAME located in INDEX_PATH. The file will have the current date appended to it.
     *
     * @throws IndexNotLoadedException
     * @throws IOException
     * @throws ExpansionDictionaryInitException
     * @throws JWNLException
     */
    public void run()
            throws IndexNotLoadedException, IOException, ExpansionDictionaryInitException, JWNLException {
        String __JUDGEMENTS_FILE__ = _search.getConfig().getJudgmentsPath();
        if (!(new File(__JUDGEMENTS_FILE__).exists())) {
            __LOGGER__.info("No judgements file found!");
            Themis.print("No judgements file found!\n");
            return;
        }
        String evaluationFilename = _search.getConfig().getEvaluationFilename();
        String timestamp = Instant.now().toString().replace(':', '.');
        if (evaluationFilename.lastIndexOf('.') != -1) {
            evaluationFilename = evaluationFilename.substring(0, evaluationFilename.lastIndexOf('.')) + '_' +
                    timestamp + evaluationFilename.substring(evaluationFilename.lastIndexOf('.'));
        }
        else {
            evaluationFilename = evaluationFilename + '_' + timestamp;
        }
        String __EVALUATION_FILE__ =  _search.getConfig().getIndexPath() + "/" + evaluationFilename;
        BufferedReader judgementsReader = new BufferedReader(new InputStreamReader(new FileInputStream(__JUDGEMENTS_FILE__), "UTF-8"));
        BufferedWriter evaluationWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(__EVALUATION_FILE__), "UTF-8"));
        Themis.print("-> Starting evaluation\n");
        Themis.print("Saving evaluation results in " + __EVALUATION_FILE__ + "\n");
        Themis.print("-> Evaluation options:\n");
        Themis.print("Retrieval model: " + _search.getRetrievalmodel().toString() + "\n");
        Themis.print("Query expansion: " + _search.getExpansionDictionary().toString() +"\n");
        Themis.print("Pagerank weight (documents): " + _search.getDocumentPagerankWeight() + "\n\n");
        evaluationWriter.write("Index path: " + _search.getConfig().getIndexPath() + "\n");
        evaluationWriter.write("Index timestamp: " + _search.getIndexTimestamp() + "\n");
        evaluationWriter.write("-> Evaluation options:\n");
        evaluationWriter.write("Retrieval model: " + _search.getRetrievalmodel().toString() + "\n");
        evaluationWriter.write("Query expansion: " + _search.getExpansionDictionary().toString() +"\n");
        evaluationWriter.write("Pagerank weight (documents): " + _search.getDocumentPagerankWeight() + "\n\n");

        evaluate(judgementsReader, evaluationWriter);
    }

    /* Runs the evaluation */
    private void evaluate(BufferedReader judgementsReader, BufferedWriter evaluationWriter)
            throws IOException, ExpansionDictionaryInitException, IndexNotLoadedException, JWNLException {
        String line;
        JSONParser parser = new JSONParser();
        List<Double> aveps = new ArrayList<>();
        List<Double> ndcgs = new ArrayList<>();
        List<Pair<String, Time>> queryTime = new ArrayList<>();
        Time totalSearchTime = new Time(0);
        long evalStartTime = System.nanoTime();
        long totalResults = 0;
        while ((line = judgementsReader.readLine()) != null) {
            Object obj;
            try {
                obj = parser.parse(line);
            } catch (ParseException e) {
                __LOGGER__.error(e.getMessage());
                Themis.print("Unable to parse json\n");
                continue;
            }
            JSONObject jsonObject = (JSONObject) obj;
            JSONArray documentsArray = (JSONArray) jsonObject.get("documents");
            String query = (String) jsonObject.get("query");

            //construct a map of [(string) doc ID -> relevance] for this query
            Map<String, Long> relevanceMap = new HashMap<>();
            for (Object o : documentsArray) {
                JSONObject doc = (JSONObject) o;
                relevanceMap.put((String) doc.get("doc_id"), (Long) doc.get("relevance"));
            }

            //perform a search
            evaluationWriter.write("Query: " + query + "\n");
            Themis.print("Query: " + query + "\n");
            long startTime = System.nanoTime();
            List<Result> results = _search.search(query);
            long endTime = System.nanoTime();

            totalResults += results.size();

            //calculate the elapsed time for this query
            Time time = new Time(endTime - startTime);
            totalSearchTime.addTime(time);
            queryTime.add(new Pair<>(query, time));

            evaluationWriter.write("Search time: " + time + "\n");
            evaluationWriter.write("Results: " + results.size() + "\n");

            //calculate avg. precision, nDCG
            double avep = computeAveP(results, relevanceMap);
            avep = (Double.isNaN(avep)) ? Double.NaN : avep;
            aveps.add(avep);
            double ndcg = computeNdcg(results, relevanceMap);
            ndcg = (Double.isNaN(ndcg)) ? Double.NaN : ndcg;
            ndcgs.add(ndcg);

            evaluationWriter.write("Average precision: " + round(avep, 4) + "\n");
            evaluationWriter.write("nDCG: " + round(ndcg, 4) + "\n\n");
            evaluationWriter.flush();
        }

        //calculate the final stats
        double averageAvep = calculateAverage(aveps);
        double minAvep = findMin(aveps);
        double maxAvep = findMax(aveps);
        double averageNdcg = calculateAverage(ndcgs);
        double minNdcg = findMin(ndcgs);
        double maxNdcg = findMax(ndcgs);
        long resultsRate = Math.min(totalResults, 1000000);
        Pair<String, Time> minTime = findMinTime(queryTime);
        Pair<String, Time> maxTime = findMaxTime(queryTime);
        Time queryAverageTime = calculateAverageTime(queryTime);
        Time resultsAverageTime;

        if (totalResults == 0) {
            resultsAverageTime = new Time(totalSearchTime.getValue());
        }
        else {
            resultsAverageTime = new Time((totalSearchTime.getValue() / totalResults) * resultsRate);
        }

        long evalEndTime = System.nanoTime();

        Themis.print("\n-> End of evaluation\n");
        evaluationWriter.write("------------------------------------------------\n");
        evaluationWriter.write("-> Summary\n\n");
        evaluationWriter.write("[Average precision]\n");
        evaluationWriter.write("Average: " + round(averageAvep, 4) + "\n");
        evaluationWriter.write("Min: " + round(minAvep, 4) + "\n");
        evaluationWriter.write("Max: " + round(maxAvep, 4) + "\n\n");
        evaluationWriter.write("[nDCG]\n");
        evaluationWriter.write("Average: " + round(averageNdcg, 4) + "\n");
        evaluationWriter.write("Min: " + round(minNdcg, 4) + "\n");
        evaluationWriter.write("Max: " + round(maxNdcg, 4) + "\n\n");
        evaluationWriter.write("-> Search time\n");
        evaluationWriter.write("Total: " + totalSearchTime + "\n");
        evaluationWriter.write("Average per query: " + queryAverageTime + "\n");
        evaluationWriter.write("Average per " + resultsRate + " results: " + resultsAverageTime + "\n");
        evaluationWriter.write("Min: " + minTime.getR() + " for query: " + minTime.getL() + "\n");
        evaluationWriter.write("Max: " + maxTime.getR() + " for query: " + maxTime.getL() + "\n\n");
        evaluationWriter.write("-> Total time: " + new Time(evalEndTime - evalStartTime) + "\n");
        evaluationWriter.close();
        judgementsReader.close();
    }

    /* calculates the average precision given a ranked list of results and a map of [(string) doc ID -> relevance] */
    private double computeAveP(List<Result> results, Map<String, Long> relevanceMap)
            throws UnsupportedEncodingException, IndexNotLoadedException {
        double avep = 0;
        int foundRelevantDocuments = 0;
        int nonSkippedDocuments = 0;
        int relevantDocuments = 0;

        for (long relevance : relevanceMap.values()) {
            if (relevance == 1) {
                relevantDocuments++;
            }
        }
        if (relevantDocuments == 0) {
            return Double.NaN;
        }
        for (Result result : results) {
            String docId = _search.getDocID(result.getDocInfo().getDocID());
            Long isJudged = relevanceMap.get(docId);
            if (isJudged != null) {
                nonSkippedDocuments++;
                if (isJudged == 1) {
                    foundRelevantDocuments++;
                    avep += (0.0 + isJudged * foundRelevantDocuments) / nonSkippedDocuments;
                }
            }
        }
        avep /= relevantDocuments;

        return avep;
    }

    /* calculates the nDCG given a ranked list of results and a map of [(string) doc ID -> relevance] */
    private double computeNdcg(List<Result> results, Map<String, Long> docRelevance)
            throws UnsupportedEncodingException, IndexNotLoadedException {
        double dcg = 0;
        double idcg = 0;
        int foundRelevantDocuments = 0;
        int nonSkippedDocuments = 0;
        int relevantDocuments = 0;

        for (long relevance : docRelevance.values()) {
            if (relevance == 1) {
                relevantDocuments++;
            }
        }
        if (relevantDocuments == 0) {
            return Double.NaN;
        }
        for (Result result : results) {
            String docId = _search.getDocID(result.getDocInfo().getDocID());
            Long isJudged = docRelevance.get(docId);
            if (isJudged != null) {
                nonSkippedDocuments++;
                if (isJudged == 1) {
                    foundRelevantDocuments++;
                    dcg += Math.log(2) / Math.log(nonSkippedDocuments + 1);
                }
            }
        }
        for (int i = 1; i <= relevantDocuments; i++) {
            idcg += Math.log(2) / Math.log(i + 1);
        }

        return dcg / idcg;
    }

    /* calculates the average of a list of doubles */
    private static double calculateAverage(List<Double> list) {
        double sum = 0;
        if (list.isEmpty()) {
            return Double.NaN;
        }
        int count = 0;
        for (Double value : list) {
            if (Double.isFinite(value)) {
                count++;
                sum += value;
            }
        }
        return sum / count;
    }

    /* finds the min in a list of doubles */
    private static double findMin(List<Double> list) {
        double min;
        if (list.isEmpty()) {
            return Double.NaN;
        }
        int i = 0;
        while (i < list.size() && !Double.isFinite(list.get(i))) {
            i++;
        }
        if (i == list.size()) {
            return Double.NaN;
        }
        min = list.get(i);
        for (Double value : list) {
            if (Double.isFinite(value) && value < min) {
                min = value;
            }
        }
        return min;
    }

    /* finds the max in a list of doubles */
    private static double findMax(List<Double> list) {
        double max;
        if (list.isEmpty()) {
            return Double.NaN;
        }
        int i = 0;
        while (i < list.size() && !Double.isFinite(list.get(i))) {
            i++;
        }
        if (i == list.size()) {
            return Double.NaN;
        }
        max = list.get(i);
        for (Double value : list) {
            if (Double.isFinite(value) && value > max) {
                max = value;
            }
        }
        return max;
    }

    /* returns the minimum search time and the corresponding query as a pair */
    private static Pair<String, Time> findMaxTime(List<Pair<String, Time>> list) {
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
    private static Pair<String, Time> findMinTime(List<Pair<String, Time>> list) {
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

    /* calculates the average search time */
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

    /* rounds num to digits decimal places */
    private static double round(double num, int digits) {
        BigDecimal bd = new BigDecimal(Double.toString(num));
        bd = bd.setScale(digits, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
