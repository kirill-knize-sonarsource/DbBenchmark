package org.sonarsource.bench.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.io.Serializable;

/**
 * Issue model approximating SonarLint RaisedIssueDto, with primary location, flows, and quick fixes.
 */
public class Issue implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id; // unique id
    private String ruleKey;
    private String severity;
    private String message;

    // For backward compatibility with earlier benchmark shape
    private String filePath;
    private Integer line;

    private long creationDateEpochMillis;
    private String assignee;
    private List<String> tags = new ArrayList<>();

    // Richer structure
    private IssueLocation primaryLocation;
    private List<IssueFlow> flows = new ArrayList<>();
    private List<QuickFix> quickFixes = new ArrayList<>();

    public Issue() {
    }

    public Issue(String id, String ruleKey, String severity, String message, String filePath, Integer line,
                 long creationDateEpochMillis, String assignee, List<String> tags) {
        this.id = id;
        this.ruleKey = ruleKey;
        this.severity = severity;
        this.message = message;
        this.filePath = filePath;
        this.line = line;
        this.creationDateEpochMillis = creationDateEpochMillis;
        this.assignee = assignee;
        if (tags != null) this.tags = new ArrayList<>(tags);
        // initialize primaryLocation from legacy fields if available
        this.primaryLocation = new IssueLocation(filePath, line, line, null, null, message);
    }

    public static Issue of(String id, String ruleKey, String severity, String message, String filePath, Integer line,
                           Instant creationDate, String assignee, List<String> tags) {
        return new Issue(id, ruleKey, severity, message, filePath, line,
                creationDate.toEpochMilli(), assignee, tags);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Integer getLine() { return line; }
    public void setLine(Integer line) { this.line = line; }

    public long getCreationDateEpochMillis() { return creationDateEpochMillis; }
    public void setCreationDateEpochMillis(long creationDateEpochMillis) { this.creationDateEpochMillis = creationDateEpochMillis; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public IssueLocation getPrimaryLocation() { return primaryLocation; }
    public void setPrimaryLocation(IssueLocation primaryLocation) { this.primaryLocation = primaryLocation; }

    public List<IssueFlow> getFlows() { return flows; }
    public void setFlows(List<IssueFlow> flows) { this.flows = (flows == null) ? new ArrayList<>() : new ArrayList<>(flows); }

    public List<QuickFix> getQuickFixes() { return quickFixes; }
    public void setQuickFixes(List<QuickFix> quickFixes) { this.quickFixes = (quickFixes == null) ? new ArrayList<>() : new ArrayList<>(quickFixes); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Issue issue = (Issue) o;
        return Objects.equals(id, issue.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Issue{" +
                "id='" + id + '\'' +
                ", ruleKey='" + ruleKey + '\'' +
                ", severity='" + severity + '\'' +
                ", filePath='" + filePath + '\'' +
                ", line=" + line +
                '}';
    }
}
