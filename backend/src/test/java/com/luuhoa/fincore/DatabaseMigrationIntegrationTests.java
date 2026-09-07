package com.luuhoa.fincore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs against PostgreSQL through Testcontainers when RUN_INTEGRATION_TESTS=true.
 * It proves that Flyway migrations and Hibernate validation can boot together.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class DatabaseMigrationIntegrationTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesTheCurrentSchemaIncludingSplitBillTables() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("16");

        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = current_schema() "
                        + "and table_name in ('financial_transactions', 'split_bills', 'split_bill_participants', 'split_bill_payments')",
                String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "financial_transactions",
                "split_bills",
                "split_bill_participants",
                "split_bill_payments");
    }
}
