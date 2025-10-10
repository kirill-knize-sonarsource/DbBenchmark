package org.sonarsource.bench;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.bench.db.*;
import org.sonarsource.bench.model.Issue;
import org.sonarsource.bench.model.IssueFlow;
import org.sonarsource.bench.model.IssueLocation;
import org.sonarsource.bench.model.QuickFix;
import org.sonarsource.bench.util.DataGenerator;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IssuePersistenceTest {

    private IssueRepository repoUnderTest;

    @AfterEach
    void tearDown() {
        if (repoUnderTest != null) {
            try { repoUnderTest.close(); } catch (Exception ignored) {}
        }
    }

    @Test
    void h2_storeAndFetchById_allFieldsMatch() throws Exception {
        repoUnderTest = new H2IssueRepository();
        runStoreFetchAndAssert(repoUnderTest);
    }

    @Test
    void hsqldb_storeAndFetchById_allFieldsMatch() throws Exception {
        repoUnderTest = new HsqldbIssueRepository();
        runStoreFetchAndAssert(repoUnderTest);
    }

    @Test
    void derby_storeAndFetchById_allFieldsMatch() throws Exception {
        repoUnderTest = new DerbyIssueRepository();
        runStoreFetchAndAssert(repoUnderTest);
    }

    @Test
    void mapdb_storeAndFetchById_allFieldsMatch() throws Exception {
        repoUnderTest = new MapDbIssueRepository();
        runStoreFetchAndAssert(repoUnderTest);
    }

    @Test
    void nitrite_storeAndFetchById_allFieldsMatch() throws Exception {
        repoUnderTest = new NitriteIssueRepository();
        runStoreFetchAndAssert(repoUnderTest);
    }

    private static void runStoreFetchAndAssert(IssueRepository repo) throws Exception {
        String[] ruleKeys = {"java:S100", "java:S101"};
        DataGenerator gen = new DataGenerator(123L, ruleKeys);
        Issue input = gen.generate(1).get(0);

        repo.init();
        repo.insertAll(List.of(input));
        Issue fetched = repo.getById(input.getId());
        assertNotNull(fetched, "Fetched Issue must not be null");
        assertIssueDeepEquals(input, fetched);
    }

    private static void assertIssueDeepEquals(Issue expected, Issue actual) {
        assertEquals(expected.getId(), actual.getId(), "id");
        assertEquals(expected.getRuleKey(), actual.getRuleKey(), "ruleKey");
        assertEquals(expected.getSeverity(), actual.getSeverity(), "severity");
        assertEquals(expected.getMessage(), actual.getMessage(), "message");
        assertEquals(expected.getFilePath(), actual.getFilePath(), "filePath");
        assertEquals(expected.getLine(), actual.getLine(), "line");
        assertEquals(expected.getCreationDateEpochMillis(), actual.getCreationDateEpochMillis(), "creationDateEpochMillis");
        assertEquals(expected.getAssignee(), actual.getAssignee(), "assignee");
        assertIterableEquals(expected.getTags(), actual.getTags(), "tags");

        // Primary location
        assertLocationEquals(expected.getPrimaryLocation(), actual.getPrimaryLocation(), "primaryLocation");

        // Flows
        List<IssueFlow> exFlows = nvlFlows(expected.getFlows());
        List<IssueFlow> acFlows = nvlFlows(actual.getFlows());
        assertEquals(exFlows.size(), acFlows.size(), "flows.size");
        for (int i = 0; i < exFlows.size(); i++) {
            List<IssueLocation> exLocs = nvlLocs(exFlows.get(i).getLocations());
            List<IssueLocation> acLocs = nvlLocs(acFlows.get(i).getLocations());
            assertEquals(exLocs.size(), acLocs.size(), "flow[" + i + "].locations.size");
            for (int j = 0; j < exLocs.size(); j++) {
                assertLocationEquals(exLocs.get(j), acLocs.get(j), "flow[" + i + "].locations[" + j + "]");
            }
        }

        // Quick fixes
        List<QuickFix> exQf = nvlQf(expected.getQuickFixes());
        List<QuickFix> acQf = nvlQf(actual.getQuickFixes());
        assertEquals(exQf.size(), acQf.size(), "quickFixes.size");
        for (int i = 0; i < exQf.size(); i++) {
            assertEquals(exQf.get(i).getMessage(), acQf.get(i).getMessage(), "quickFixes[" + i + "].message");
            List<IssueLocation> exLocs = nvlLocs(exQf.get(i).getLocations());
            List<IssueLocation> acLocs = nvlLocs(acQf.get(i).getLocations());
            assertEquals(exLocs.size(), acLocs.size(), "quickFixes[" + i + "].locations.size");
            for (int j = 0; j < exLocs.size(); j++) {
                assertLocationEquals(exLocs.get(j), acLocs.get(j), "quickFixes[" + i + "].locations[" + j + "]");
            }
        }
    }

    private static List<IssueFlow> nvlFlows(List<IssueFlow> list) {
        return list == null ? new ArrayList<>() : list;
    }
    private static List<IssueLocation> nvlLocs(List<IssueLocation> list) {
        return list == null ? new ArrayList<>() : list;
    }
    private static List<QuickFix> nvlQf(List<QuickFix> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private static void assertLocationEquals(IssueLocation expected, IssueLocation actual, String ctx) {
        if (expected == null && actual == null) return;
        assertNotNull(expected, ctx + " expected not null");
        assertNotNull(actual, ctx + " actual not null");
        assertEquals(expected.getFilePath(), actual.getFilePath(), ctx + ".filePath");
        assertEquals(expected.getStartLine(), actual.getStartLine(), ctx + ".startLine");
        assertEquals(expected.getEndLine(), actual.getEndLine(), ctx + ".endLine");
        assertEquals(expected.getStartOffset(), actual.getStartOffset(), ctx + ".startOffset");
        assertEquals(expected.getEndOffset(), actual.getEndOffset(), ctx + ".endOffset");
        assertEquals(expected.getMessage(), actual.getMessage(), ctx + ".message");
    }
}
