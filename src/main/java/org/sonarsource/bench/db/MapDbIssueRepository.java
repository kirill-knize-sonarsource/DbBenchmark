package org.sonarsource.bench.db;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.sonarsource.bench.model.Issue;

import java.io.File;
import java.util.*;

public class MapDbIssueRepository implements IssueRepository {
    private DB db;
    private Map<String, Issue> map;
    private Map<String, Set<String>> ruleIndex;
    private File file;

    @Override
    public String name() { return "MapDB"; }

    @Override
    public void init() {
        try {
            // Let MapDB create the file itself to avoid corruption errors on empty pre-created files
            File tmp = File.createTempFile("mapdb-issues", ".db");
            String path = tmp.getAbsolutePath();
            // delete the empty file so MapDB can create a proper store
            if (tmp.exists()) tmp.delete();
            file = new File(path);
            db = DBMaker.fileDB(file)
                    .fileMmapEnableIfSupported()
                    .make();
            map = db.hashMap("issues", Serializer.STRING, Serializer.JAVA).createOrOpen();
            ruleIndex = db.hashMap("ruleIndex", Serializer.STRING, Serializer.JAVA).createOrOpen();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void insertAll(List<Issue> issues) {
        int i = 0;
        for (Issue is : issues) {
            map.put(is.getId(), is);
            if (is.getRuleKey() != null) {
                Set<String> ids = ruleIndex.get(is.getRuleKey());
                if (ids == null) {
                    ids = new HashSet<>();
                } else {
                    // create a modifiable copy in case underlying set is immutable snapshot
                    ids = new HashSet<>(ids);
                }
                ids.add(is.getId());
                ruleIndex.put(is.getRuleKey(), ids);
            }
            if ((++i % 5000) == 0) {
                // Periodic flush to avoid gigantic single transaction/write log
                db.commit();
            }
        }
        db.commit();
    }

    @Override
    public List<Issue> readAll() {
        return new ArrayList<>(map.values());
    }

    @Override
    public List<Issue> searchByRule(String ruleKey) {
        List<Issue> out = new ArrayList<>();
        if (ruleIndex != null) {
            Set<String> ids = ruleIndex.get(ruleKey);
            if (ids != null && !ids.isEmpty()) {
                for (String id : ids) {
                    Issue is = map.get(id);
                    if (is != null) out.add(is);
                }
                return out;
            }
        }
        // Fallback: full scan if index missing or empty
        for (Issue is : map.values()) {
            if (ruleKey.equals(is.getRuleKey())) out.add(is);
        }
        return out;
    }

    @Override
    public Issue getById(String id) {
        return map.get(id);
    }

    @Override
    public void close() {
        try { if (db != null) db.close(); } catch (Exception ignored) {}
        if (file != null) file.delete();
    }
}
