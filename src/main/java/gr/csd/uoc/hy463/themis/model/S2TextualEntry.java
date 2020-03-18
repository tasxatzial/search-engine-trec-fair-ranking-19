/*
 * themis - A fair search engine for scientific articles
 *
 * Currently over the Semantic Scholar Open Research Corpus
 * http://s2-public-api-prod.us-west-2.elasticbeanstalk.com/corpus/
 *
 * Aiming to participate in TREC 2020 Fair Ranking Track
 * https://fair-trec.github.io/
 *
 * Writing code during COVID-19 pandemic times :-(
 *
 * Collaborative work with the undergraduate/graduate students of
 * Information Retrieval Systems (hy463) course
 * Spring Semester 2020
 *
 * Computer Science Department
 * University of Crete
 * http://www.csd.uoc.gr
 * Greece
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.model;

import java.util.List;

/**
 *
 * @author Panagiotis Papadakos <papadako at ics.forth.gr>
 */
public class S2TextualEntry {

    private String __ID__ = null;
    private String __TITLE__ = null;
    private String __ABSTRACT__ = null;
    private List<String> __ENTITIES__ = null;
    private List<String> __FIELDS_OF_STUDY__ = null;
    private List<String> __AUTHORS__ = null;
    private int __YEAR__ = 0;
    private String __VENUE__ = null;
    private String __JOURNAL_NAME__ = null;
    private List<String> __SOURCES__ = null;

    public String getId() {
        return __ID__;
    }

    public void setId(String id) {
        this.__ID__ = id;
    }

    public String getTitle() {
        return __TITLE__;
    }

    public void setTitle(String title) {
        this.__TITLE__ = title;
    }

    public String getPaperAbstract() {
        return __ABSTRACT__;
    }

    public void setPaperAbstract(String paperAbstract) {
        this.__ABSTRACT__ = paperAbstract;
    }

    public List<String> getEntities() {
        return __ENTITIES__;
    }

    public void setEntities(List<String> entities) {
        this.__ENTITIES__ = entities;
    }

    public List<String> getFieldsOfStudy() {
        return __FIELDS_OF_STUDY__;
    }

    public void setFieldsOfStudy(List<String> fieldsOfStudy) {
        this.__FIELDS_OF_STUDY__ = fieldsOfStudy;
    }

    public List<String> getAuthors() {
        return __AUTHORS__;
    }

    public void setAuthors(List<String> authors) {
        this.__AUTHORS__ = authors;
    }

    public int getYear() {
        return __YEAR__;
    }

    public void setYear(int year) {
        this.__YEAR__ = year;
    }

    public String getVenue() {
        return __VENUE__;
    }

    public void setVenue(String venue) {
        this.__VENUE__ = venue;
    }

    public String getJournalName() {
        return __JOURNAL_NAME__;
    }

    public void setJournalName(String journalName) {
        this.__JOURNAL_NAME__ = journalName;
    }

    public List<String> getSources() {
        return __SOURCES__;
    }

    public void setSources(List<String> sources) {
        this.__SOURCES__ = sources;
    }

    /**
     *
     * @return
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (__TITLE__ != null) {
            sb.append("Title: ").append(__TITLE__).append("\n");
        }
        if (__ABSTRACT__ != null) {
            sb.append("Abstract: ").append(__ABSTRACT__).append("\n");
        }
        if (__ENTITIES__ != null) {
            boolean first = true;
            sb.append("Entities:");
            for (String entity : __ENTITIES__) {
                sb.append(entity);
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
            }
            sb.append("\n");
        }
        if (__FIELDS_OF_STUDY__ != null) {
            boolean first = true;
            sb.append("Fields Of Study:");
            for (String field : __FIELDS_OF_STUDY__) {
                sb.append(field);
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
            }
            sb.append("\n");
        }
        if (__AUTHORS__ != null) {
            boolean first = true;

            sb.append("Author Names:");
            for (String author : __AUTHORS__) {
                sb.append(author);
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
            }
            sb.append("\n");
        }
        if (__JOURNAL_NAME__ != null) {
            sb.append("Journal Name: ").append(__JOURNAL_NAME__).append("\n");
        }
        if (__VENUE__ != null) {
            sb.append("Venue: ").append(__VENUE__).append("\n");
        }
        if (__SOURCES__ != null) {
            boolean first = true;
            sb.append("Sources:");
            for (String source : __SOURCES__) {
                sb.append(source);
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
            }
            sb.append("\n");
        }
        if (__YEAR__ != 0) {
            sb.append("Year: ").append(__YEAR__).append("\n");
        }

        return sb.toString();
    }

}
