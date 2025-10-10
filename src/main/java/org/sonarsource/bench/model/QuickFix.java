package org.sonarsource.bench.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a suggested quick fix with optional affected locations.
 */
public class QuickFix implements Serializable {
    private static final long serialVersionUID = 1L;

    private String message;
    private List<IssueLocation> locations = new ArrayList<>();

    public QuickFix() { }

    public QuickFix(String message, List<IssueLocation> locations) {
        this.message = message;
        if (locations != null) this.locations = new ArrayList<>(locations);
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<IssueLocation> getLocations() { return locations; }
    public void setLocations(List<IssueLocation> locations) {
        this.locations = (locations == null) ? new ArrayList<>() : new ArrayList<>(locations);
    }
}
