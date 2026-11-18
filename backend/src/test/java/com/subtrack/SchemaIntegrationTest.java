package com.subtrack;

import static org.assertj.core.api.Assertions.assertThat;

import com.subtrack.support.AbstractIntegrationTest;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The context only starts if Hibernate's ddl-auto=validate agrees with the Flyway
 * schema, so starting it is the main assertion here.
 */
class SchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAppliedInitialMigration() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void allTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables).map(String::toLowerCase).contains(
                "users", "subscriptions", "reminder_settings", "billing_periods",
                "billing_period_history", "notification_log", "refresh_tokens",
                "user_tokens", "shedlock");
    }

    @Test
    void notificationLogHasClaimUniqueKey() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE()
                  AND table_name = 'notification_log'
                  AND constraint_name = 'uk_notification_log_claim'
                  AND constraint_type = 'UNIQUE'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
