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
package gr.csd.uoc.hy463.themis.utils;

import gr.csd.uoc.hy463.themis.model.S2TextualEntry;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Class responsible for reading textual entries from the json description of
 * entries
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class S2JsonEntryReader {

    private static final Logger __LOGGER__ = LogManager.getLogger(S2JsonEntryReader.class);

    // Method that reads all textual information from an entry
    public static S2TextualEntry readTextualEntry(String jsonToRead) {
        S2TextualEntry entry = new S2TextualEntry();
        JSONParser parser = new JSONParser();
        try {
            Object obj = parser.parse(jsonToRead);

            // This should be a JSON object.
            JSONObject jsonObject = (JSONObject) obj;

            // Get the id for example
            String id = (String) jsonObject.get("id");
            entry.setId(id);

            // Get the title for example
            String title = (String) jsonObject.get("title");
            entry.setTitle(title);

            // Get abstract for example
            String paperAbstract = (String) jsonObject.get("paperAbstract");
            entry.setPaperAbstract(paperAbstract);

            // Read entities. A JSONArray
            JSONArray entitiesArray = (JSONArray) jsonObject.get("entities");
            List<String> entities = new ArrayList<>();
            entitiesArray.forEach(entity -> {
                entities.add(entity.toString());
            });
            entry.setEntities(entities);

            // Read fieldsOfStudy. A JSONArray
            JSONArray fieldsArray = (JSONArray) jsonObject.get("fieldsOfStudy");
            List<String> fields = new ArrayList<>();
            fieldsArray.forEach(field -> {
                fields.add(field.toString());
            });
            entry.setFieldsOfStudy(fields);

            // Read authors. A JSONArray
            JSONArray authorsList = (JSONArray) jsonObject.get("authors");
            List<Pair<String, List<String>>> authors = new ArrayList<>();
            for (int i = 0; i < authorsList.size(); i++) {
                JSONObject authorInfo = (JSONObject) authorsList.get(i);
                String authorName = (String) authorInfo.get("name");
                // Now get all the ids
                JSONArray idsList = (JSONArray) authorInfo.get("ids");
                List<String> ids = new ArrayList<>();
                for (int j = 0; j < idsList.size(); j++) {
                    String ID = (String) idsList.get(j);
                    ids.add(ID);
                }
                Pair author = new Pair(authorName, ids);
                authors.add(author);
            }
            entry.setAuthors(authors);

            // Get journal for example
            String journal = (String) jsonObject.get("journalName");
            entry.setJournalName(journal);

            // Read sources. A JSONArray
            JSONArray sourcesArray = (JSONArray) jsonObject.get("sources");
            List<String> sources = new ArrayList<>();
            sourcesArray.forEach(source -> {
                sources.add(source.toString());
            });
            entry.setSources(sources);

            // Get year for example
            int year = ((Long) jsonObject.get("year")).intValue();
            entry.setYear(year);

            // Get venue for example
            String venue = (String) jsonObject.get("venue");
            entry.setVenue(venue);

        } catch (ParseException e) {
            __LOGGER__.error(e.getMessage());
        }

        return entry;
    }

    public static void main(String[] args) {
        String json = "{\n"
                + "  \"id\": \"4cd223df721b722b1c40689caa52932a41fcc223\",\n"
                + "  \"title\": \"Knowledge-rich, computer-assisted composition of Chinese couplets\",\n"
                + "  \"paperAbstract\": \"Recent research effort in poem composition has focused on the use of\n"
                + "   automatic language generation...\",\n"
                + "  \"entities\": [\n"
                + "    \"Conformance testing\",\n"
                + "    \"Natural language generation\",\n"
                + "    \"Natural language processing\",\n"
                + "    \"Parallel computing\",\n"
                + "    \"Stochastic grammar\",\n"
                + "    \"Web application\"\n"
                + "  ],\n"
                + "  \"fieldsOfStudy\": [\n"
                + "      \"Computer Science\"\n"
                + "  ],\n"
                + "  \"s2Url\": \"https://semanticscholar.org/paper/4cd223df721b722b1c40689caa52932a41fcc223\",\n"
                + "  \"s2PdfUrl\": \"\",\n"
                + "  \"pdfUrls\": [\n"
                + "    \"https://doi.org/10.1093/llc/fqu052\"\n"
                + "  ],\n"
                + "  \"authors\": [\n"
                + "    {\n"
                + "      \"name\": \"John Lee\",\n"
                + "      \"ids\": [\n"
                + "        \"3362353\"\n"
                + "      ]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"inCitations\": [\n"
                + "    \"c789e333fdbb963883a0b5c96c648bf36b8cd242\"\n"
                + "  ],\n"
                + "  \"outCitations\": [\n"
                + "    \"abe213ed63c426a089bdf4329597137751dbb3a0\",\n"
                + "    \"...\"\n"
                + "  ],\n"
                + "  \"year\": 2016,\n"
                + "  \"venue\": \"DSH\",\n"
                + "  \"journalName\": \"DSH\",\n"
                + "  \"journalVolume\": \"31\",\n"
                + "  \"journalPages\": \"152-163\",\n"
                + "  \"sources\": [\n"
                + "    \"DBLP\"\n"
                + "  ],\n"
                + "  \"doi\": \"10.1093/llc/fqu052\",\n"
                + "  \"doiUrl\": \"https://doi.org/10.1093/llc/fqu052\",\n"
                + "  \"pmid\": \"\"\n"
                + "}";

        System.out.println(json);
        System.out.println(S2JsonEntryReader.readTextualEntry(json));

    }

}
