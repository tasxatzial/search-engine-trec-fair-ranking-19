package gr.csd.uoc.hy463.themis.metrics;

import gr.csd.uoc.hy463.themis.Themis;
import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.config.Exceptions.ConfigLoadException;
import gr.csd.uoc.hy463.themis.indexer.Indexer;
import gr.csd.uoc.hy463.themis.indexer.model.DocInfo;
import gr.csd.uoc.hy463.themis.queryExpansion.QueryExpansion;
import gr.csd.uoc.hy463.themis.queryExpansion.Exceptions.ExpansionDictionaryInitException;
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
import java.time.Instant;
import java.util.*;

/**
 * Runs the evaluation for the specified retrieval model and query expansion dictionary that are
 * specified in the constructor
 */
public class themisEval {
    private static final Logger __LOGGER__ = LogManager.getLogger(Indexer.class);
    private Search _search;
    private Config __CONFIG__;
    private String __JUDGEMENTS_PATH__;
    private String __EVALUATION_PATH__;
    private ARetrievalModel.MODEL _model;
    private QueryExpansion.DICTIONARY _dictionary;

    public themisEval(Search search, ARetrievalModel.MODEL model, QueryExpansion.DICTIONARY dictionary) throws IOException {
        _search = search;
        __CONFIG__ = new Config();
        _model = model;
        _dictionary = dictionary;
    }

    /**
     * Initiates the evaluation
     * @throws IndexNotLoadedException
     * @throws IOException
     * @throws ExpansionDictionaryInitException
     */
    public void start() throws IndexNotLoadedException, IOException, ExpansionDictionaryInitException, JWNLException, ConfigLoadException {
        __JUDGEMENTS_PATH__ = __CONFIG__.getJudgmentsPath();
        if (!(new File(__JUDGEMENTS_PATH__).exists())) {
            __LOGGER__.info("No judgements file found!");
            Themis.print("No judgements file found!\n");
            return;
        }
        String evaluationFilename = __CONFIG__.getEvaluationFilename();
        String timestamp = Instant.now().toString().replace(':', '.');
        if (evaluationFilename.lastIndexOf('.') != -1) {
            evaluationFilename = evaluationFilename.substring(0, evaluationFilename.lastIndexOf('.')) + '_' +
                    timestamp + evaluationFilename.substring(evaluationFilename.lastIndexOf('.'));
        }
        else {
            evaluationFilename = evaluationFilename + '_' + timestamp;
        }
        _search.setRetrievalModel(_model);
        _search.setExpansionDictionary(_dictionary);
        _search.setDocumentProperties(new HashSet<>());
        __EVALUATION_PATH__ = __CONFIG__.getIndexPath() + "/" + evaluationFilename;

        BufferedReader judgementsReader = new BufferedReader(new InputStreamReader(new FileInputStream(__JUDGEMENTS_PATH__), "UTF-8"));
        BufferedWriter evaluationWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(__EVALUATION_PATH__), "UTF-8"));
        Themis.print(">>> Starting evaluation\n");
        Themis.print("Saving evaluation results in " + __EVALUATION_PATH__ + "\n");
        Themis.print(">>> Evaluation options:\n");
        Themis.print("Retrieval model: " + _search.getRetrievalmodel().toString() + "\n");
        Themis.print("Query expansion: " + _search.getExpansionDictionary().toString() +"\n");
        Themis.print("Retrieval model weight: " + __CONFIG__.getRetrievalModelWeight() + "\n");
        Themis.print("Pagerank citations weight: " + __CONFIG__.getPagerankPublicationsWeight() + "\n\n");
        evaluationWriter.write("Index path: " + __CONFIG__.getIndexPath() + "\n");
        evaluationWriter.write("Index timestamp: " + _search.getIndexTimestamp() + "\n");
        evaluationWriter.write(">>> Evaluation options:\n");
        evaluationWriter.write("Retrieval model: " + _search.getRetrievalmodel().toString() + "\n");
        evaluationWriter.write("Query expansion: " + _search.getExpansionDictionary().toString() +"\n");
        evaluationWriter.write("Retrieval model weight: " + __CONFIG__.getRetrievalModelWeight() + "\n");
        evaluationWriter.write("Pagerank citations weight: " + __CONFIG__.getPagerankPublicationsWeight() + "\n\n");

        evaluate(judgementsReader, evaluationWriter);
    }

    /* Runs the evaluation based on the configured parameters */
    private void evaluate(BufferedReader judgementsReader, BufferedWriter evaluationWriter) throws IOException, ExpansionDictionaryInitException, IndexNotLoadedException, JWNLException {
        String line;
        JSONParser parser = new JSONParser();
        List<Double> aveps = new ArrayList<>();
        List<Double> ndcgs = new ArrayList<>();
        List<Pair<String, Time>> queryTime = new ArrayList<>();
        Time totalTime = new Time(0);
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

            //construct a map of (docId, binary relevance value) for this query
            Map<String, Long> relevanceMap = new HashMap<>();
            for (Object o : documentsArray) {
                JSONObject doc = (JSONObject) o;
                relevanceMap.put((String) doc.get("doc_id"), (Long) doc.get("relevance"));
            }

            //perform a search
            evaluationWriter.write(">>> Search query: " + query + "\n");
            Themis.print("> Search query: " + query + "\n");
            long startTime = System.nanoTime();
            List<Pair<DocInfo, Double>> results = _search.search(query);
            long endTime = System.nanoTime();

            //calculate the elapsed time for the search
            Time time = new Time(endTime - startTime);
            totalTime.addTime(time);
            queryTime.add(new Pair<>(query, time));
            evaluationWriter.write("Search time: " + time + "\n");

            //calculate the number of results
            totalResults += results.size();
            evaluationWriter.write("Results: " + results.size() + "\n");

            //calculate average precision, nDCG
            double avep = computeAveP(results, relevanceMap);
            avep = (Double.isNaN(avep)) ? Double.NaN : avep;
            aveps.add(avep);
            double ndcg = computeNdcg(results, relevanceMap);
            ndcg = (Double.isNaN(ndcg)) ? Double.NaN : ndcg;
            ndcgs.add(ndcg);
            evaluationWriter.write("Average precision: " + avep + "\n");
            evaluationWriter.write("nDCG: " + ndcg + "\n\n");
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
        Time queryAverageTime = calculateAverageTime(queryTime);
        Time resultsAverageTime;
        if (totalResults == 0) {
            resultsAverageTime = new Time(0);
        }
        else {
            resultsAverageTime = new Time((totalTime.getValue() / totalResults) * calculateBaseResults(totalResults));
        }

        Themis.print("\n>>> End of evaluation\n");
        evaluationWriter.write("------------------------------------------------\n");
        evaluationWriter.write("Summary:\n\n");
        evaluationWriter.write("-> Average precision\n");
        evaluationWriter.write("Average: " + averageAvep + "\n");
        evaluationWriter.write("Min: " + minAvep + "\n");
        evaluationWriter.write("Max: " + maxAvep + "\n\n");
        evaluationWriter.write("-> nDCG\n");
        evaluationWriter.write("Average: " + averageNdcg + "\n");
        evaluationWriter.write("Min: " + minNdcg + "\n");
        evaluationWriter.write("Max: " + maxNdcg + "\n\n");
        evaluationWriter.write("-> Search time\n");
        evaluationWriter.write("Total: " + totalTime + "\n");
        evaluationWriter.write("Average per query: " + queryAverageTime + "\n");
        evaluationWriter.write("Average per " + calculateBaseResults(totalResults) + " results: " + resultsAverageTime + "\n");
        evaluationWriter.write("Min: " + minTime.getR() + " for query: " + minTime.getL() + "\n");
        evaluationWriter.write("Max: " + maxTime.getR() + " for query: " + maxTime.getL() + "\n");
        evaluationWriter.close();
        judgementsReader.close();
    }

    /* calculates the average precision given a ranked list of results and a map of (docId, binary relevance value) */
    private double computeAveP(List<Pair<DocInfo, Double>> results, Map<String, Long> relevanceMap) throws UnsupportedEncodingException, IndexNotLoadedException {
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
        for (Pair<DocInfo, Double> rankedDocument : results) {
            String docId = _search.getDocID(rankedDocument.getL().getId());
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

    /* calculates the nDCG given a ranked list of results and a map of (docId, binary relevance value) */
    private double computeNdcg(List<Pair<DocInfo, Double>> results, Map<String, Long> relevanceMap) throws UnsupportedEncodingException, IndexNotLoadedException {
        double dcg = 0;
        double idcg = 0;
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
        for (Pair<DocInfo, Double> result : results) {
            String docId = _search.getDocID(result.getL().getId());
            Long isJudged = relevanceMap.get(docId);
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
        return sum / numbers;
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

    /* returns the X in 'time per X results' based on the specified results size */
    private static long calculateBaseResults(long resultsSize) {
        if (resultsSize == 0) {
            return 0;
        }
        if (resultsSize > 1e6) {
            return 1000000;
        }
        return resultsSize;
    }
}
