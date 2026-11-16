package com.pvqims;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void hasExactlySixBusinessTablesAndTwoViews() {
        List<String> tables = jdbc.queryForList(
            "SELECT LOWER(table_name) FROM information_schema.tables " +
            "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' " +
            "AND table_name <> 'flyway_schema_history'", String.class);
        assertThat(tables).containsExactlyInAnyOrder(
            "app_user", "project", "batch",
            "inspection_standard", "inspection_record", "defect");

        List<String> views = jdbc.queryForList(
            "SELECT LOWER(table_name) FROM information_schema.views " +
            "WHERE table_schema = DATABASE()", String.class);
        assertThat(views).containsExactlyInAnyOrder("v_defect_detail", "v_batch_pass_rate");
    }

    @Test
    void seededUsersStoreBcryptHashes() {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM app_user", String.class);
        assertThat(hashes).hasSize(3)
            .allMatch(h -> h.startsWith("$2a$") || h.startsWith("$2b$"));
    }
}
