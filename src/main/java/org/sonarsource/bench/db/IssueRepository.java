package org.sonarsource.bench.db;

import org.sonarsource.bench.model.Issue;

import java.io.Closeable;
import java.util.List;

public interface IssueRepository extends Closeable {
    String name();
    void init() throws Exception;
    void insertAll(List<Issue> issues) throws Exception;
    List<Issue> readAll() throws Exception;
    List<Issue> searchByRule(String ruleKey) throws Exception;
    Issue getById(String id) throws Exception;
    @Override
    void close();
}
