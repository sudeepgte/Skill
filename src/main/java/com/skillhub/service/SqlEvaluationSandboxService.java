package com.skillhub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Isolated SQL Evaluation Sandbox Service.
 * 
 * Production Security Rules:
 * 1. NEVER executes candidate SQL against production MySQL databases.
 * 2. Runs exclusively inside an ephemeral, in-memory H2 sandbox with strict isolation.
 * 3. Enforces static security checks: rejects DROP, ALTER, TRUNCATE, UPDATE, DELETE, INSERT,
 *    stored procedures, file operations, sleep, and schema introspection.
 * 4. Resource bounds: 2-second query execution timeout, max 50 rows, zero disk persistence.
 */
@Service
public class SqlEvaluationSandboxService {

    private static final Logger log = LoggerFactory.getLogger(SqlEvaluationSandboxService.class);

    private static final int QUERY_TIMEOUT_SECONDS = 2;
    private static final int MAX_RESULT_ROWS = 50;

    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
            "\\b(DROP|ALTER|TRUNCATE|DELETE|UPDATE|INSERT|CREATE|GRANT|REVOKE|SHUTDOWN|EXEC|EXECUTE|CALL|INFORMATION_SCHEMA|INTO\\s+OUTFILE|LOAD_FILE|BENCHMARK|SLEEP)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public static class SqlEvaluationResult {
        private final boolean passed;
        private final int rowsMatched;
        private final long executionTimeMs;
        private final String message;

        public SqlEvaluationResult(boolean passed, int rowsMatched, long executionTimeMs, String message) {
            this.passed = passed;
            this.rowsMatched = rowsMatched;
            this.executionTimeMs = executionTimeMs;
            this.message = message;
        }

        public boolean isPassed() { return passed; }
        public int getRowsMatched() { return rowsMatched; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public String getMessage() { return message; }
    }

    /**
     * Evaluates candidate SQL against an isolated ephemeral database.
     */
    public SqlEvaluationResult evaluate(String candidateSql, String expectedSql, String sampleDataDdl) {
        if (candidateSql == null || candidateSql.trim().isEmpty()) {
            return new SqlEvaluationResult(false, 0, 0, "Candidate query is empty.");
        }

        String cleanCandidate = candidateSql.trim();

        // 1. Static Security Validation - Read-only query enforcement
        if (DANGEROUS_SQL_PATTERN.matcher(cleanCandidate).find()) {
            log.warn("SQL Sandbox: Prohibited keyword detected in candidate query: {}", cleanCandidate);
            return new SqlEvaluationResult(false, 0, 0, "Query rejected: Only read-only SELECT queries are permitted in this sandbox.");
        }

        if (!cleanCandidate.toUpperCase().startsWith("SELECT") && !cleanCandidate.toUpperCase().startsWith("WITH")) {
            return new SqlEvaluationResult(false, 0, 0, "Query rejected: Query must begin with SELECT or WITH.");
        }

        // 2. Ephemeral In-Memory Sandbox Connection
        String sandboxUrl = "jdbc:h2:mem:sql_sandbox_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL";
        long startTime = System.currentTimeMillis();

        try (Connection conn = DriverManager.getConnection(sandboxUrl, "sa", "")) {
            // Setup Sample Data
            if (sampleDataDdl != null && !sampleDataDdl.trim().isEmpty()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sampleDataDdl);
                }
            }

            // Execute Expected Query
            List<List<String>> expectedRows = new ArrayList<>();
            List<String> expectedCols = new ArrayList<>();
            if (expectedSql != null && !expectedSql.trim().isEmpty()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    stmt.setMaxRows(MAX_RESULT_ROWS);
                    try (ResultSet rs = stmt.executeQuery(expectedSql)) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        for (int i = 1; i <= colCount; i++) {
                            expectedCols.add(meta.getColumnLabel(i).toUpperCase());
                        }
                        while (rs.next()) {
                            List<String> row = new ArrayList<>();
                            for (int i = 1; i <= colCount; i++) {
                                row.add(String.valueOf(rs.getObject(i)));
                            }
                            expectedRows.add(row);
                        }
                    }
                }
            }

            // Execute Candidate Query
            List<List<String>> candidateRows = new ArrayList<>();
            List<String> candidateCols = new ArrayList<>();
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                stmt.setMaxRows(MAX_RESULT_ROWS);
                try (ResultSet rs = stmt.executeQuery(cleanCandidate)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    for (int i = 1; i <= colCount; i++) {
                        candidateCols.add(meta.getColumnLabel(i).toUpperCase());
                    }
                    while (rs.next()) {
                        List<String> row = new ArrayList<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.add(String.valueOf(rs.getObject(i)));
                        }
                        candidateRows.add(row);
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;

            // 3. Comparison
            if (expectedRows.isEmpty()) {
                // If no expected query is specified, successful execution without error passes
                return new SqlEvaluationResult(true, candidateRows.size(), elapsed, "Query executed successfully.");
            }

            if (candidateRows.size() != expectedRows.size()) {
                return new SqlEvaluationResult(false, candidateRows.size(), elapsed,
                        "Row count mismatch: expected " + expectedRows.size() + " rows, but got " + candidateRows.size());
            }

            for (int r = 0; r < expectedRows.size(); r++) {
                List<String> expRow = expectedRows.get(r);
                List<String> actRow = candidateRows.get(r);
                if (!expRow.equals(actRow)) {
                    return new SqlEvaluationResult(false, r, elapsed, "Data mismatch at row " + (r + 1));
                }
            }

            return new SqlEvaluationResult(true, candidateRows.size(), elapsed, "SQL solution verified successfully against sample database.");

        } catch (SQLException ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("SQL evaluation syntax/execution error: {}", ex.getMessage());
            return new SqlEvaluationResult(false, 0, elapsed, "SQL execution failed: " + ex.getMessage());
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("SQL sandbox evaluation error: {}", ex.getMessage(), ex);
            return new SqlEvaluationResult(false, 0, elapsed, "Evaluation failed: Internal sandbox error");
        }
    }
}
