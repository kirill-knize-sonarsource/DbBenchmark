package org.sonarsource.bench.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A flow contains a sequence of locations (e.g., taint path), similar to RaisedIssueDto.
 */
public class IssueFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<IssueLocation> locations = new ArrayList<>();

    public IssueFlow() { }

    public IssueFlow(List<IssueLocation> locations) {
        if (locations != null) this.locations = new ArrayList<>(locations);
    }

    public List<IssueLocation> getLocations() { return locations; }
    public void setLocations(List<IssueLocation> locations) {
        this.locations = (locations == null) ? new ArrayList<>() : new ArrayList<>(locations);
    }
}
