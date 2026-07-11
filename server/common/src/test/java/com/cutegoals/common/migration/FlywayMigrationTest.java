package com.cutegoals.common.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flyway 迁移脚本集成测试。
 * <p>
 * 默认使用 H2 内存数据库（PostgreSQL 兼容模式）执行所有迁移脚本，
 * 验证表结构、索引、唯一约束和外键正确创建。
 * 每个测试方法建立独立连接，H2 通过 DB_CLOSE_DELAY=-1 保持数据。
 * <p>
 * 可通过系统属性指定真实 PostgreSQL 连接：
 * -Dflyway.test.url=jdbc:postgresql://localhost:35432/cutegoals
 * -Dflyway.test.user=cutegoals
 * -Dflyway.test.password=cutegoals
 * 无需 Docker。
 */
class FlywayMigrationTest {

    private static final String H2_JDBC_URL =
            "jdbc:h2:mem:cutegoals_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";
    private static final String H2_USER = "sa";
    private static final String H2_PASSWORD = "";

    private static String jdbcUrl;
    private static String dbUser;
    private static String dbPassword;

    /** Case-insensitive set for table/column lookups. */
    private static final class CIMap {
        final Map<String, String> lowerToOriginal = new HashMap<>();
        void add(String name) { lowerToOriginal.put(toLower(name), name); }
        boolean contains(String name) { return lowerToOriginal.containsKey(toLower(name)); }
        private static String toLower(String s) { return s == null ? null : s.toLowerCase(Locale.ROOT); }
    }

    @BeforeAll
    static void runMigrations() {
        // Determine target database: use real PostgreSQL if system properties set, otherwise H2
        jdbcUrl = System.getProperty("flyway.test.url", H2_JDBC_URL);
        dbUser = System.getProperty("flyway.test.user", H2_USER);
        dbPassword = System.getProperty("flyway.test.password", H2_PASSWORD);

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, dbUser, dbPassword)
                .locations("classpath:db/migration")
                .load();
        MigrateResult result = flyway.migrate();

        assertEquals(9, result.migrationsExecuted,
                "Expected exactly 9 migrations (V1 through V9) to be executed");

        // Verify all migration versions
        MigrationInfo[] applied = flyway.info().applied();
        Set<String> versions = new HashSet<>();
        for (MigrationInfo info : applied) {
            versions.add(info.getVersion().toString());
            assertNotNull(info.getDescription(), "Migration description should not be null");
        }

        assertTrue(versions.contains("1"), "V1 baseline should be applied");
        assertTrue(versions.contains("2"), "V2 auth tables should be applied");
        assertTrue(versions.contains("3"), "V3 family tables should be applied");
        assertTrue(versions.contains("4"), "V4 task template tables should be applied");
        assertTrue(versions.contains("5"), "V5 submission and review tables should be applied");
        assertTrue(versions.contains("6"), "V6 points tables should be applied");
        assertTrue(versions.contains("7"), "V7 prize and exchange tables should be applied");
        assertTrue(versions.contains("8"), "V8 instance management tables should be applied");
        assertTrue(versions.contains("9"), "V9 assignment occurrence_key unique constraint should be applied");

        // Verify reentrancy
        MigrateResult reentrantResult = flyway.migrate();
        assertEquals(0, reentrantResult.migrationsExecuted,
                "Second migration run should execute 0 migrations (already up-to-date)");
    }

    private Connection newConnection() throws Exception {
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }

    /** Collect all table names from the database into a case-insensitive set. */
    private CIMap getExistingTables(Connection conn) throws Exception {
        CIMap result = new CIMap();
        try (ResultSet tables = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                result.add(tables.getString("TABLE_NAME"));
            }
        }
        return result;
    }

    @Test
    void allTablesShouldBeCreated() throws Exception {
        String[] expectedTables = {
                "ACCOUNT", "ROLE_BINDING", "INITIALIZATION_TOKEN", "SESSION", "REFRESH_TOKEN", "LOGIN_RATE_LIMIT",
                "FAMILY", "FAMILY_MEMBER", "CHILD_PROFILE", "PARENT_INVITATION", "DEVICE_BINDING",
                "TASK_TEMPLATE", "TASK_DIFFICULTY", "TASK_RECURRENCE_RULE", "TASK_ASSIGNMENT", "TASK_ASSIGNMENT_SNAPSHOT",
                "TASK_ATTEMPT", "TASK_REVIEW",
                "POINTS_LEDGER", "POINTS_BALANCE",
                "PRIZE", "BLIND_BOX_POOL", "BLIND_BOX_ITEM", "EXCHANGE", "EXCHANGE_SNAPSHOT",
                "INSTANCE_CONFIG", "AUDIT_LOG", "BACKUP_RUN", "RECOVERY_DRILL"
        };

        try (Connection conn = newConnection()) {
            CIMap existingTables = getExistingTables(conn);
            for (String table : expectedTables) {
                assertTrue(existingTables.contains(table),
                        "Table '" + table + "' should exist after migration");
            }
        }
    }

    @Test
    void bigintPrimaryKeysShouldBePresent() throws Exception {
        String[] tables = {
                "ACCOUNT", "ROLE_BINDING", "INITIALIZATION_TOKEN", "SESSION", "REFRESH_TOKEN", "LOGIN_RATE_LIMIT",
                "FAMILY", "FAMILY_MEMBER", "CHILD_PROFILE", "PARENT_INVITATION", "DEVICE_BINDING",
                "TASK_TEMPLATE", "TASK_DIFFICULTY", "TASK_RECURRENCE_RULE", "TASK_ASSIGNMENT", "TASK_ASSIGNMENT_SNAPSHOT",
                "TASK_ATTEMPT", "TASK_REVIEW",
                "POINTS_LEDGER", "POINTS_BALANCE",
                "PRIZE", "BLIND_BOX_POOL", "BLIND_BOX_ITEM", "EXCHANGE", "EXCHANGE_SNAPSHOT",
                "INSTANCE_CONFIG", "AUDIT_LOG", "BACKUP_RUN", "RECOVERY_DRILL"
        };

        try (Connection conn = newConnection()) {
            CIMap existingTables = getExistingTables(conn);
            DatabaseMetaData meta = conn.getMetaData();
            for (String table : tables) {
                try (ResultSet pk = meta.getPrimaryKeys(null, null, table)) {
                    boolean found = false;
                    while (pk.next()) {
                        String colName = pk.getString("COLUMN_NAME");
                        if (colName != null && colName.equalsIgnoreCase("id")) {
                            found = true;
                            break;
                        }
                    }
                    assertTrue(found, table + " should have a primary key on 'id' column");
                }
            }
        }
    }

    @Test
    void authTablesShouldHaveRequiredConstraints() throws Exception {
        try (Connection conn = newConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            assertUniqueConstraint(meta, "ACCOUNT", "UK_ACCOUNT_PHONE",
                    "account table should have unique constraint on phone");

            assertIndexExists(meta, "INITIALIZATION_TOKEN", "IDX_INIT_TOKEN_CONSUMED",
                    "initialization_token should have composite index on (token_hash, consumed)");

            assertIndexExists(meta, "REFRESH_TOKEN", "IDX_REFRESH_TOKEN_FAMILY",
                    "refresh_token should have index for family chain revocation");
        }
    }

    @Test
    void familyTablesShouldHaveRequiredConstraints() throws Exception {
        try (Connection conn = newConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            assertUniqueConstraint(meta, "FAMILY_MEMBER", "UK_FAMILY_MEMBER",
                    "family_member should have unique constraint on (family_id, account_id)");

            assertUniqueConstraint(meta, "DEVICE_BINDING", "UK_DEVICE_BINDING",
                    "device_binding should have unique constraint on (family_id, device_id)");

            assertForeignKeyExists(meta, "CHILD_PROFILE", "FK_CHILD_FAMILY",
                    "child_profile should have foreign key to family table");
        }
    }

    @Test
    void taskTablesShouldHaveRequiredConstraints() throws Exception {
        try (Connection conn = newConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            assertUniqueConstraint(meta, "TASK_DIFFICULTY", "UK_DIFFICULTY_TEMPLATE_NAME",
                    "task_difficulty should have unique constraint on (template_id, name)");

            assertUniqueConstraint(meta, "TASK_RECURRENCE_RULE", "UK_RECURRENCE_TEMPLATE",
                    "task_recurrence_rule should have unique constraint on template_id");

            assertUniqueConstraint(meta, "TASK_ASSIGNMENT", "UK_ASSIGNMENT_OCCURRENCE_KEY",
                    "task_assignment should have unique constraint on occurrence_key");
        }
    }

    @Test
    void immutableTablesShouldNotHaveUpdatedAt() throws Exception {
        String[] immutableTables = {"TASK_ATTEMPT", "TASK_REVIEW", "POINTS_LEDGER", "EXCHANGE_SNAPSHOT"};

        try (Connection conn = newConnection()) {
            for (String table : immutableTables) {
                assertColumnAbsent(conn, table, "UPDATED_AT",
                        table + " is immutable and should not have updated_at column");
                assertColumnExists(conn, table, "CREATED_AT",
                        table + " should have created_at column");
            }
        }
    }

    @Test
    void mutableTablesShouldHaveBothCreatedAndUpdated() throws Exception {
        String[] mutableTables = {
                "ACCOUNT", "FAMILY", "FAMILY_MEMBER", "CHILD_PROFILE",
                "PARENT_INVITATION", "DEVICE_BINDING", "TASK_TEMPLATE",
                "TASK_DIFFICULTY", "TASK_RECURRENCE_RULE", "TASK_ASSIGNMENT",
                "POINTS_BALANCE", "PRIZE", "BLIND_BOX_POOL", "EXCHANGE",
                "INSTANCE_CONFIG", "BACKUP_RUN"
        };

        try (Connection conn = newConnection()) {
            for (String table : mutableTables) {
                assertColumnExists(conn, table, "CREATED_AT",
                        table + " should have created_at column");
                assertColumnExists(conn, table, "UPDATED_AT",
                        table + " should have updated_at column");
            }
        }
    }

    @Test
    void pointsTablesShouldHaveRequiredConstraints() throws Exception {
        try (Connection conn = newConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            assertUniqueConstraint(meta, "POINTS_LEDGER", "UK_LEDGER_BUSINESS_REF",
                    "points_ledger should have unique constraint on (child_id, business_ref)");

            assertUniqueConstraint(meta, "POINTS_LEDGER", "UK_LEDGER_REFUND_SOURCE",
                    "points_ledger should have unique refund_source_id constraint");

            assertColumnExists(conn, "POINTS_BALANCE", "VERSION",
                    "points_balance should have version column for optimistic locking");

            assertUniqueConstraint(meta, "POINTS_BALANCE", "UK_BALANCE_CHILD",
                    "points_balance should have unique constraint on child_id");
        }
    }

    @Test
    void exchangeTablesShouldHaveRequiredConstraints() throws Exception {
        try (Connection conn = newConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            assertUniqueConstraint(meta, "EXCHANGE", "UK_EXCHANGE_IDEMPOTENCY",
                    "exchange should have unique constraint on (child_id, idempotency_key)");

            assertUniqueConstraint(meta, "EXCHANGE_SNAPSHOT", "UK_SNAPSHOT_EXCHANGE",
                    "exchange_snapshot should have unique constraint on exchange_id");

            assertColumnExists(conn, "BLIND_BOX_ITEM", "WEIGHT",
                    "blind_box_item should have weight column");
        }
    }

    @Test
    void auditLogTableShouldHaveRequiredIndexes() throws Exception {
        try (Connection conn = newConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            assertIndexExists(meta, "AUDIT_LOG", "IDX_AUDIT_ACTOR_EVENT",
                    "audit_log should have composite index on (actor_id, event_type, created_at)");

            assertIndexExists(meta, "AUDIT_LOG", "IDX_AUDIT_EVENT_TIME",
                    "audit_log should have index on (event_type, created_at)");
        }
    }

    // -- Helper assertion methods --

    private void assertUniqueConstraint(DatabaseMetaData meta, String table, String indexName, String message)
            throws Exception {
        try (ResultSet rs = meta.getIndexInfo(null, null, table, true, false)) {
            boolean found = false;
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                if (idxName != null && idxName.toUpperCase(Locale.ROOT).contains(indexName.toUpperCase(Locale.ROOT))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, message + " (expected unique index: " + indexName + ")");
        }
    }

    private void assertIndexExists(DatabaseMetaData meta, String table, String indexName, String message)
            throws Exception {
        try (ResultSet rs = meta.getIndexInfo(null, null, table, false, false)) {
            boolean found = false;
            while (rs.next()) {
                String idxName = rs.getString("INDEX_NAME");
                if (idxName != null && idxName.toUpperCase(Locale.ROOT).contains(indexName.toUpperCase(Locale.ROOT))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, message + " (expected index: " + indexName + ")");
        }
    }

    private void assertForeignKeyExists(DatabaseMetaData meta, String table, String fkName, String message)
            throws Exception {
        // H2 returns foreign keys via getImportedKeys (the table that has the FK references)
        // PostgreSQL uses getExportedKeys when table is the parent
        try (ResultSet rs = meta.getImportedKeys(null, null, table)) {
            boolean found = false;
            while (rs.next()) {
                String fk = rs.getString("FK_NAME");
                if (fk != null && fk.toUpperCase(Locale.ROOT).contains(fkName.toUpperCase(Locale.ROOT))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, message + " (expected foreign key: " + fkName + ")");
        }
    }

    private void assertColumnExists(Connection conn, String table, String column, String message) throws Exception {
        // Use information_schema which works reliably across H2 PostgreSQL mode and real PostgreSQL
        String sql = "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)";
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next() && rs.getInt(1) > 0, message);
            }
        }
    }

    private void assertColumnAbsent(Connection conn, String table, String column, String message) throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)";
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next() && rs.getInt(1) == 0, message);
            }
        }
    }
}
