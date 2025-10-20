package org.sonarsource.bench.db;

import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.filters.FluentFilter;
import org.sonarsource.bench.model.Issue;
import org.sonarsource.bench.model.IssueFlow;
import org.sonarsource.bench.model.IssueLocation;
import org.sonarsource.bench.model.QuickFix;

import org.dizitart.no2.mvstore.MVStoreModule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NitriteIssueRepository implements IssueRepository {
    private Nitrite db;
    private NitriteCollection coll;
    private String filePath;

    @Override
    public String name() { return "Nitrite"; }

    @Override
    public void init() {
        try {
            File tmp = File.createTempFile("nitrite-issues", ".db");
            String path = tmp.getAbsolutePath();
            if (tmp.exists()) tmp.delete();
            filePath = path;

            // Direct Nitrite v4 initialization without reflection
            db = Nitrite.builder()
                    .loadModule(MVStoreModule.withConfig()
                            .filePath(filePath)
                            .compress(true)
                            .build())
                    .openOrCreate("user", "password");

            coll = db.getCollection("issues");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void insertAll(List<Issue> issues) {
        List<Document> docs = new ArrayList<>(issues.size());
        for (Issue is : issues) {
            docs.add(toDoc(is));
        }
        coll.insert(docs.toArray(new Document[0]));
    }

    @Override
    public List<Issue> readAll() {
        List<Issue> out = new ArrayList<>();
        for (Document d : coll.find()) out.add(fromDoc(d));
        return out;
    }

    @Override
    public List<Issue> searchByRule(String ruleKey) {
        var cur = coll.find(FluentFilter.where("ruleKey").eq(ruleKey));
        List<Issue> out = new ArrayList<>();
        for (Document d : cur) out.add(fromDoc(d));
        return out;
    }

    @Override
    public Issue getById(String id) {
        var cur = coll.find(FluentFilter.where("id").eq(id));
        for (Document d : cur) return fromDoc(d);
        return null;
    }

    private Document toDoc(Issue is) {
        Document d = Document.createDocument("id", is.getId());
        d.put("ruleKey", is.getRuleKey());
        d.put("severity", is.getSeverity());
        d.put("message", is.getMessage());
        d.put("filePath", is.getFilePath());
        d.put("line", is.getLine());
        d.put("creationDateEpochMillis", is.getCreationDateEpochMillis());
        d.put("assignee", is.getAssignee());
        d.put("tags", is.getTags() == null ? List.of() : new ArrayList<>(is.getTags()));

        // primaryLocation
        IssueLocation pl = is.getPrimaryLocation();
        if (pl != null) {
            Document pld = Document.createDocument("filePath", pl.getFilePath());
            pld.put("startLine", pl.getStartLine());
            pld.put("endLine", pl.getEndLine());
            pld.put("startOffset", pl.getStartOffset());
            pld.put("endOffset", pl.getEndOffset());
            pld.put("message", pl.getMessage());
            d.put("primaryLocation", pld);
        }

        // flows
        List<Document> flowDocs = new ArrayList<>();
        if (is.getFlows() != null) {
            for (IssueFlow f : is.getFlows()) {
                List<Document> locDocs = new ArrayList<>();
                if (f != null && f.getLocations() != null) {
                    for (IssueLocation l : f.getLocations()) {
                        Document ld = Document.createDocument("filePath", l.getFilePath());
                        ld.put("startLine", l.getStartLine());
                        ld.put("endLine", l.getEndLine());
                        ld.put("startOffset", l.getStartOffset());
                        ld.put("endOffset", l.getEndOffset());
                        ld.put("message", l.getMessage());
                        locDocs.add(ld);
                    }
                }
                Document fd = Document.createDocument("locations", locDocs);
                flowDocs.add(fd);
            }
        }
        d.put("flows", flowDocs);

        // quickFixes
        List<Document> qfDocs = new ArrayList<>();
        if (is.getQuickFixes() != null) {
            for (QuickFix qf : is.getQuickFixes()) {
                Document qfd = Document.createDocument("message", qf.getMessage());
                List<Document> locDocs = new ArrayList<>();
                if (qf.getLocations() != null) {
                    for (IssueLocation l : qf.getLocations()) {
                        Document ld = Document.createDocument("filePath", l.getFilePath());
                        ld.put("startLine", l.getStartLine());
                        ld.put("endLine", l.getEndLine());
                        ld.put("startOffset", l.getStartOffset());
                        ld.put("endOffset", l.getEndOffset());
                        ld.put("message", l.getMessage());
                        locDocs.add(ld);
                    }
                }
                qfd.put("locations", locDocs);
                qfDocs.add(qfd);
            }
        }
        d.put("quickFixes", qfDocs);

        return d;
    }

    private Issue fromDoc(Document d) {
        Issue is = new Issue();
        is.setId(d.get("id", String.class));
        is.setRuleKey(d.get("ruleKey", String.class));
        is.setSeverity(d.get("severity", String.class));
        is.setMessage(d.get("message", String.class));
        is.setFilePath(d.get("filePath", String.class));
        Integer line = d.get("line", Integer.class);
        is.setLine(line);
        Long ts = d.get("creationDateEpochMillis", Long.class);
        is.setCreationDateEpochMillis(ts == null ? 0L : ts);
        is.setAssignee(d.get("assignee", String.class));
        List<String> tags = d.get("tags", List.class);
        if (tags == null) tags = new ArrayList<>();
        is.setTags(tags);

        Document pld = d.get("primaryLocation", Document.class);
        if (pld != null) {
            IssueLocation pl = new IssueLocation();
            pl.setFilePath(pld.get("filePath", String.class));
            pl.setStartLine(pld.get("startLine", Integer.class));
            pl.setEndLine(pld.get("endLine", Integer.class));
            pl.setStartOffset(pld.get("startOffset", Integer.class));
            pl.setEndOffset(pld.get("endOffset", Integer.class));
            pl.setMessage(pld.get("message", String.class));
            is.setPrimaryLocation(pl);
        }

        List<Document> flowDocs = d.get("flows", List.class);
        if (flowDocs != null) {
            List<IssueFlow> flows = new ArrayList<>();
            for (Object o : flowDocs) {
                if (!(o instanceof Document fd)) continue;
                List<IssueLocation> locs = new ArrayList<>();
                List<Document> locDocs = fd.get("locations", List.class);
                if (locDocs != null) {
                    for (Object lo : locDocs) {
                        if (!(lo instanceof Document ld)) continue;
                        IssueLocation l = new IssueLocation();
                        l.setFilePath(ld.get("filePath", String.class));
                        l.setStartLine(ld.get("startLine", Integer.class));
                        l.setEndLine(ld.get("endLine", Integer.class));
                        l.setStartOffset(ld.get("startOffset", Integer.class));
                        l.setEndOffset(ld.get("endOffset", Integer.class));
                        l.setMessage(ld.get("message", String.class));
                        locs.add(l);
                    }
                }
                flows.add(new IssueFlow(locs));
            }
            is.setFlows(flows);
        }

        List<Document> qfDocs = d.get("quickFixes", List.class);
        if (qfDocs != null) {
            List<QuickFix> qfs = new ArrayList<>();
            for (Object o : qfDocs) {
                if (!(o instanceof Document qfd)) continue;
                QuickFix qf = new QuickFix();
                qf.setMessage(qfd.get("message", String.class));
                List<IssueLocation> locs = new ArrayList<>();
                List<Document> locDocs = qfd.get("locations", List.class);
                if (locDocs != null) {
                    for (Object lo : locDocs) {
                        if (!(lo instanceof Document ld)) continue;
                        IssueLocation l = new IssueLocation();
                        l.setFilePath(ld.get("filePath", String.class));
                        l.setStartLine(ld.get("startLine", Integer.class));
                        l.setEndLine(ld.get("endLine", Integer.class));
                        l.setStartOffset(ld.get("startOffset", Integer.class));
                        l.setEndOffset(ld.get("endOffset", Integer.class));
                        l.setMessage(ld.get("message", String.class));
                        locs.add(l);
                    }
                }
                qf.setLocations(locs);
                qfs.add(qf);
            }
            is.setQuickFixes(qfs);
        }

        return is;
    }

    @Override
    public void close() {
        try {
            if (db != null && !db.isClosed()) {
                try {
                    db.close();
                } catch (Throwable t) {
                    // Swallow any close-time errors to keep benchmark flow consistent across DBs
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
