package com.subtrack.support;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests. Runs against the local MySQL database
 * {@code subtrack_test} (start it with {@code scripts/dev-services.ps1 start}).
 * The schema is wiped and re-migrated once per Spring test context.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.CleanMigrateConfig.class)
public abstract class AbstractIntegrationTest {

    @TestConfiguration
    static class CleanMigrateConfig {

        @Bean
        FlywayMigrationStrategy cleanMigrate() {
            return (Flyway flyway) -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }
}
