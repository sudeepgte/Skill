package com.skillhub.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SQL Evaluation Sandbox Security & Verification Tests")
public class SqlSandboxEvaluationTest {

    private final SqlEvaluationSandboxService sqlService = new SqlEvaluationSandboxService();

    @Test
    @DisplayName("SQL Sandbox: Valid query correctly matches expected result")
    void testValidSqlMatching() {
        String setupDdl = "CREATE TABLE employees (id INT, name VARCHAR(50), salary INT);" +
                "INSERT INTO employees VALUES (1, 'Alice', 90000), (2, 'Bob', 75000);";
        String candidateSql = "SELECT name, salary FROM employees WHERE salary > 80000";
        String expectedSql = "SELECT name, salary FROM employees WHERE salary > 80000";

        var result = sqlService.evaluate(candidateSql, expectedSql, setupDdl);
        assertTrue(result.isPassed());
        assertEquals(1, result.getRowsMatched());
        assertTrue(result.getMessage().contains("verified successfully"));
    }

    @Test
    @DisplayName("SQL Sandbox: Detects data and row count mismatches")
    void testDetectsDataMismatch() {
        String setupDdl = "CREATE TABLE users (id INT, role VARCHAR(20));" +
                "INSERT INTO users VALUES (1, 'ADMIN'), (2, 'STUDENT');";
        String candidateSql = "SELECT id, role FROM users WHERE role = 'STUDENT'";
        String expectedSql = "SELECT id, role FROM users WHERE role = 'ADMIN'";

        var result = sqlService.evaluate(candidateSql, expectedSql, setupDdl);
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("mismatch"));
    }

    @Test
    @DisplayName("SQL Sandbox: Security blocks dangerous DDL statements (DROP TABLE)")
    void testRejectsDropTable() {
        String setupDdl = "CREATE TABLE secrets (data VARCHAR(50));";
        String candidateSql = "DROP TABLE secrets; SELECT 1;";

        var result = sqlService.evaluate(candidateSql, "SELECT 1;", setupDdl);
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("rejected"));
    }

    @Test
    @DisplayName("SQL Sandbox: Security blocks DML modification statements (DELETE, UPDATE, INSERT)")
    void testRejectsDmlStatements() {
        String setupDdl = "CREATE TABLE accounts (balance INT);";
        
        var deleteResult = sqlService.evaluate("DELETE FROM accounts", "SELECT 1", setupDdl);
        assertFalse(deleteResult.isPassed());

        var updateResult = sqlService.evaluate("UPDATE accounts SET balance = 0", "SELECT 1", setupDdl);
        assertFalse(updateResult.isPassed());

        var insertResult = sqlService.evaluate("INSERT INTO accounts VALUES (100)", "SELECT 1", setupDdl);
        assertFalse(insertResult.isPassed());
    }

    @Test
    @DisplayName("SQL Sandbox: Security rejects non-SELECT / non-WITH query starters")
    void testRejectsNonSelectQuery() {
        var result = sqlService.evaluate("SHOW TABLES", "SELECT 1", null);
        assertFalse(result.isPassed());
        assertTrue(result.getMessage().contains("must begin with SELECT or WITH"));
    }
}
