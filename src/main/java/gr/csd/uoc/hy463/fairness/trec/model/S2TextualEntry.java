/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gr.csd.uoc.hy463.fairness.trec.model;

import java.util.List;

/**
 *
 * @author papadako
 */
public class S2TextualEntry {

    private String id = null;
    private String title = null;
    private String paperAbstract = null;
    private List<String> entities = null;
    private List<String> fieldsOfStudy = null;
    private List<String> authors = null;
    private int year = 0;
    private String venue = null;
    private String journalName = null;
    private List<String> sources = null;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPaperAbstract() {
        return paperAbstract;
    }

    public void setPaperAbstract(String paperAbstract) {
        this.paperAbstract = paperAbstract;
    }

    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities;
    }

    public List<String> getFieldsOfStudy() {
        return fieldsOfStudy;
    }

    public void setFieldsOfStudy(List<String> fieldsOfStudy) {
        this.fieldsOfStudy = fieldsOfStudy;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getJournalName() {
        return journalName;
    }

    public void setJournalName(String journalName) {
        this.journalName = journalName;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }

    /**
     *
     * @return
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append("Title: ").append(title).append("\n");
        }
        if (paperAbstract != null) {
            sb.append("Abstract: ").append(paperAbstract).append("\n");
        }
        if (entities != null) {
            boolean first = true;
            sb.append("Entities:");
            for (String entity : entities) {
                sb.append(entity);
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
            }
            sb.append("\n");
        }
        if (fieldsOfStudy != null) {
            boolean first = true;
            sb.append("Fields Of Study:");
            for (String field : fieldsOfStudy) {
                sb.append(field);
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
            }
            sb.append("\n");
        }
        if (authors != null) {
            boolean first = true;

            sb.append("Author Names:");
            for (String author : authors) {
                sb.append(author);
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
            }
            sb.append("\n");
        }
        if (journalName != null) {
            sb.append("Journal Name: ").append(journalName).append("\n");
        }
        if (venue != null) {
            sb.append("Venue: ").append(venue).append("\n");
        }
        if (sources != null) {
            boolean first = true;
            sb.append("Sources:");
            for (String source : sources) {
                sb.append(source);
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
            }
            sb.append("\n");
        }
        if (year != 0) {
            sb.append("Year: ").append(year).append("\n");
        }

        return sb.toString();
    }

}
