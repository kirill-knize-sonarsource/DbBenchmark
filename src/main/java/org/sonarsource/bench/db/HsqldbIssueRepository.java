package org.sonarsource.bench.db;

import org.sonarsource.bench.model.Issue;
import org.sonarsource.bench.model.IssueFlow;
import org.sonarsource.bench.model.IssueLocation;
import org.sonarsource.bench.model.QuickFix;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class HsqldbIssueRepository implements IssueRepository {
    private Connection conn;
    private String dbPath;

    @Override
    public String name() { return "HSQLDB"; }

    @Override
    public void init() throws Exception {
        // Use file-based HSQLDB to ensure on-disk persistence
        File tmp = File.createTempFile("hsqldb-issues", ".db");
        String path = tmp.getAbsolutePath();
        if (tmp.exists()) tmp.delete();
        dbPath = path;
        conn = DriverManager.getConnection("jdbc:hsqldb:file:" + dbPath + ";shutdown=true");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS issues (" +
                    "id VARCHAR(64) PRIMARY KEY, " +
                    "ruleKey VARCHAR(64), " +
                    "severity VARCHAR(16), " +
                    "message VARCHAR(1024), " +
                    "filePath VARCHAR(512), " +
                    "line INT, " +
                    "creationDate BIGINT, " +
                    "assignee VARCHAR(128), " +
                    "tags VARCHAR(512), " +
                    "details CLOB)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_rule ON issues(ruleKey)");
        }
    }

    @Override
    public void insertAll(List<Issue> issues) throws Exception {
        String sql = "INSERT INTO issues(id, ruleKey, severity, message, filePath, line, creationDate, assignee, tags, details) VALUES(?,?,?,?,?,?,?,?,?,?)";
        // HSQLDB occasionally throws "statement is not in batch mode" with executeBatch in some environments.
        // To keep the benchmark stable for small runs, insert rows one-by-one.
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Issue is : issues) {
                ps.setString(1, is.getId());
                ps.setString(2, is.getRuleKey());
                ps.setString(3, is.getSeverity());
                ps.setString(4, is.getMessage());
                ps.setString(5, is.getFilePath());
                if (is.getLine() == null) ps.setNull(6, Types.INTEGER); else ps.setInt(6, is.getLine());
                ps.setLong(7, is.getCreationDateEpochMillis());
                ps.setString(8, is.getAssignee());
                ps.setString(9, String.join(",", is.getTags()));
                ps.setString(10, encodeDetails(is));
                ps.executeUpdate();
            }
        }
    }

    @Override
    public List<Issue> readAll() throws Exception {
        List<Issue> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM issues")) {
            while (rs.next()) out.add(fromRow(rs));
        }
        return out;
    }

    @Override
    public List<Issue> searchByRule(String ruleKey) throws Exception {
        List<Issue> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM issues WHERE ruleKey = ?")) {
            ps.setString(1, ruleKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(fromRow(rs));
            }
        }
        return out;
    }

    @Override
    public Issue getById(String id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM issues WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return fromRow(rs);
            }
        }
        return null;
    }

    private Issue fromRow(ResultSet rs) throws SQLException {
        Issue is = new Issue();
        is.setId(rs.getString("id"));
        is.setRuleKey(rs.getString("ruleKey"));
        is.setSeverity(rs.getString("severity"));
        is.setMessage(rs.getString("message"));
        is.setFilePath(rs.getString("filePath"));
        int line = rs.getInt("line");
        is.setLine(rs.wasNull() ? null : line);
        is.setCreationDateEpochMillis(rs.getLong("creationDate"));
        is.setAssignee(rs.getString("assignee"));
        String tags = rs.getString("tags");
        List<String> tagList = new ArrayList<>();
        if (tags != null && !tags.isEmpty()) {
            for (String t : tags.split(",")) tagList.add(t);
        }
        is.setTags(tagList);
        String details = rs.getString("details");
        if (details != null && !details.isEmpty()) decodeDetails(details, is);
        return is;
    }

    private String encodeDetails(Issue is) {
        StringBuilder sb = new StringBuilder();
        sb.append('P').append('|')
                .append(b64(is.getPrimaryLocation() != null ? is.getPrimaryLocation().getFilePath() : is.getFilePath())).append('|')
                .append(nv(is.getPrimaryLocation() != null ? is.getPrimaryLocation().getStartLine() : is.getLine())).append('|')
                .append(nv(is.getPrimaryLocation() != null ? is.getPrimaryLocation().getEndLine() : is.getLine())).append('|')
                .append(nv(is.getPrimaryLocation() != null ? is.getPrimaryLocation().getStartOffset() : null)).append('|')
                .append(nv(is.getPrimaryLocation() != null ? is.getPrimaryLocation().getEndOffset() : null)).append('|')
                .append(b64(is.getPrimaryLocation() != null ? is.getPrimaryLocation().getMessage() : is.getMessage()))
                .append('\n');
        if (is.getFlows() != null) {
            for (IssueFlow f : is.getFlows()) {
                sb.append('F');
                if (f != null && f.getLocations() != null) {
                    for (IssueLocation l : f.getLocations()) {
                        sb.append(';').append('L').append('|')
                                .append(b64(l.getFilePath())).append('|')
                                .append(nv(l.getStartLine())).append('|')
                                .append(nv(l.getEndLine())).append('|')
                                .append(nv(l.getStartOffset())).append('|')
                                .append(nv(l.getEndOffset())).append('|')
                                .append(b64(l.getMessage() == null ? "" : l.getMessage()));
                    }
                }
                sb.append('\n');
            }
        }
        if (is.getQuickFixes() != null) {
            for (QuickFix q : is.getQuickFixes()) {
                sb.append('Q').append(';').append(b64(q.getMessage() == null ? "" : q.getMessage()));
                if (q.getLocations() != null) {
                    for (IssueLocation l : q.getLocations()) {
                        sb.append(';').append('L').append('|')
                                .append(b64(l.getFilePath())).append('|')
                                .append(nv(l.getStartLine())).append('|')
                                .append(nv(l.getEndLine())).append('|')
                                .append(nv(l.getStartOffset())).append('|')
                                .append(nv(l.getEndOffset())).append('|')
                                .append(b64(l.getMessage() == null ? "" : l.getMessage()));
                    }
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private void decodeDetails(String details, Issue is) {
        String[] lines = details.split("\\n");
        for (String lineStr : lines) {
            if (lineStr.isEmpty()) continue;
            char kind = lineStr.charAt(0);
            String rest = lineStr.substring(1);
            if (kind == 'P') {
                String[] parts = rest.split("\\|");
                if (parts.length >= 7) {
                    is.setPrimaryLocation(new IssueLocation(ub64(parts[1]), parseInt(parts[2]), parseInt(parts[3]), parseInt(parts[4]), parseInt(parts[5]), ub64(parts[6])));
                }
            } else if (kind == 'F') {
                String[] tokens = rest.split(";");
                List<IssueLocation> locs = new ArrayList<>();
                for (String tok : tokens) {
                    if (tok.isEmpty() || tok.charAt(0) != 'L') continue;
                    String[] p = tok.substring(1).split("\\|");
                    if (p.length >= 7) {
                        locs.add(new IssueLocation(ub64(p[1]), parseInt(p[2]), parseInt(p[3]), parseInt(p[4]), parseInt(p[5]), ub64(p[6])));
                    }
                }
                if (!locs.isEmpty()) {
                    if (is.getFlows() == null) is.setFlows(new ArrayList<>());
                    is.getFlows().add(new IssueFlow(locs));
                }
            } else if (kind == 'Q') {
                String[] tokens = rest.split(";");
                String msg = tokens.length > 1 ? ub64(tokens[1]) : "";
                List<IssueLocation> locs = new ArrayList<>();
                for (int i = 2; i < tokens.length; i++) {
                    String tok = tokens[i];
                    if (tok.isEmpty() || tok.charAt(0) != 'L') continue;
                    String[] p = tok.substring(1).split("\\|");
                    if (p.length >= 7) {
                        locs.add(new IssueLocation(ub64(p[1]), parseInt(p[2]), parseInt(p[3]), parseInt(p[4]), parseInt(p[5]), ub64(p[6])));
                    }
                }
                if (is.getQuickFixes() == null) is.setQuickFixes(new ArrayList<>());
                is.getQuickFixes().add(new QuickFix(msg, locs));
            }
        }
    }

    private String b64(String s) {
        if (s == null) return "";
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private String ub64(String s) {
        if (s == null || s.isEmpty()) return null;
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private String nv(Integer i) { return i == null ? "" : String.valueOf(i); }

    private Integer parseInt(String s) { return (s == null || s.isEmpty()) ? null : Integer.parseInt(s); }

    @Override
    public void close() {
        try { if (conn != null) conn.close(); } catch (Exception ignored) { }
    }
}
