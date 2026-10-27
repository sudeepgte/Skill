-- ============================================================================
-- SkillHub Platform: Least-Privilege Database Account Provisioning
-- Target RDBMS: MySQL 8.0+
-- File: production_least_privilege_user.sql
-- ============================================================================

-- 1. Create dedicated application user with native password encryption
-- Replace '${PROD_DB_PASSWORD}' with strong secret injected via environment variable
CREATE USER IF NOT EXISTS 'skillhub_app'@'%' IDENTIFIED BY '${PROD_DB_PASSWORD}';

-- 2. Grant DML and stored execution privileges strictly to skillhub database
-- Notice: DDL commands (CREATE, DROP, ALTER, REFERENCES, INDEX) and administrative
-- privileges (SUPER, RELOAD, SHUTDOWN, PROCESS, GRANT OPTION) are strictly NOT granted.
GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE, SHOW VIEW 
    ON `skillhub`.* 
    TO 'skillhub_app'@'%';

-- 3. Dedicated Migration / Schema Management Account (for CI/CD pipeline / DB migration only)
CREATE USER IF NOT EXISTS 'skillhub_migrator'@'%' IDENTIFIED BY '${MIGRATOR_DB_PASSWORD}';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, LOCK TABLES, EXECUTE
    ON `skillhub`.* 
    TO 'skillhub_migrator'@'%';

-- 4. Apply privilege updates
FLUSH PRIVILEGES;

-- Verification:
-- SHOW GRANTS FOR 'skillhub_app'@'%';
-- SHOW GRANTS FOR 'skillhub_migrator'@'%';
