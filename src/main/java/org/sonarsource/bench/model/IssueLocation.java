package org.sonarsource.bench.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a location of an issue in a file, roughly similar to RaisedIssueDto's locations.
 */
public class IssueLocation implements Serializable {
    private static final long serialVersionUID = 1L;

    private String filePath;
    private Integer startLine;
    private Integer endLine;
    private Integer startOffset;
    private Integer endOffset;
    private String message;

    public IssueLocation() { }

    public IssueLocation(String filePath, Integer startLine, Integer endLine, Integer startOffset, Integer endOffset, String message) {
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.message = message;
    }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Integer getStartLine() { return startLine; }
    public void setStartLine(Integer startLine) { this.startLine = startLine; }

    public Integer getEndLine() { return endLine; }
    public void setEndLine(Integer endLine) { this.endLine = endLine; }

    public Integer getStartOffset() { return startOffset; }
    public void setStartOffset(Integer startOffset) { this.startOffset = startOffset; }

    public Integer getEndOffset() { return endOffset; }
    public void setEndOffset(Integer endOffset) { this.endOffset = endOffset; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IssueLocation that = (IssueLocation) o;
        return Objects.equals(filePath, that.filePath) &&
                Objects.equals(startLine, that.startLine) &&
                Objects.equals(endLine, that.endLine) &&
                Objects.equals(startOffset, that.startOffset) &&
                Objects.equals(endOffset, that.endOffset) &&
                Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, startLine, endLine, startOffset, endOffset, message);
    }

    @Override
    public String toString() {
        return "IssueLocation{" +
                "filePath='" + filePath + '\'' +
                ", startLine=" + startLine +
                ", endLine=" + endLine +
                ", startOffset=" + startOffset +
                ", endOffset=" + endOffset +
                ", message='" + message + '\'' +
                '}';
    }
}
