package com.pvqims.demo;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.pvqims.inspection.JudgmentRules;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 演示数据初始化（FR-16、AC-10）。测试全部在测试事务内执行，播种结果随事务回滚，
 * 因此同一类里的用例互不影响，也不会给共享测试库留下数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoSeedApiTest {

    /** 演示项目的识别条件：项目编号是固定前缀，不会与其它用例的项目混在一起。 */
    private static final String DEMO_PROJECT = "p.project_code LIKE 'DEMO-2026-%'";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JudgmentRules judgmentRules;
    @Autowired PasswordEncoder passwordEncoder;

    private String tokenFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    private void seed(String token) throws Exception {
        mvc.perform(post("/api/demo/seed").header("Authorization", "Bearer " + token))
           .andExpect(status().isOk());
    }

    private long longOf(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private long demoRecordCount() {
        return longOf("SELECT COUNT(*) FROM inspection_record r JOIN project p ON p.id = r.project_id WHERE "
            + DEMO_PROJECT);
    }

    private long demoProjectCount() {
        return longOf("SELECT COUNT(*) FROM project p WHERE " + DEMO_PROJECT);
    }

    /** 取一个演示项目下的一条数值型标准，供控制图断言使用。 */
    private long standardIdOf(String projectCode) {
        return jdbc.queryForObject("""
            SELECT s.id FROM inspection_standard s JOIN project p ON p.id = s.project_id
            WHERE p.project_code = ? AND s.judge_type = 'STD_TOL'
            ORDER BY s.sort_order, s.id LIMIT 1
            """, Long.class, projectCode);
    }

    /** 某个图表端点必须返回非空数组（AC-10「8 张图均有数据」）。失败信息带上端点路径。 */
    private void assertChartHasData(String token, String path, long projectId) throws Exception {
        String body = mvc.perform(get(path).param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + token))
           .andExpect(status().isOk())
           .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.code")).as(path).isZero();
        assertThat((List<?>) JsonPath.read(body, "$.data")).as(path).isNotEmpty();
    }

    @Test
    void adminSeedsThreeHundredPlusRecordsAndAllChartsHaveData() throws Exception {
        String admin = tokenFor("admin", "Admin@123");

        mvc.perform(post("/api/demo/seed").header("Authorization", "Bearer " + admin))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.projectCount").value(2))
           .andExpect(jsonPath("$.data.recordCount").value(360))
           .andExpect(jsonPath("$.data.defectCount").value(greaterThan(0)));

        // AC-10：初始化后有 300 条以上记录。
        assertThat(demoRecordCount()).isGreaterThanOrEqualTo(300);

        long projectId = longOf("SELECT id FROM project WHERE project_code = 'DEMO-2026-001'");

        // 指标卡与七张按项目筛选的图。
        String summary = mvc.perform(get("/api/dashboard/summary").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + admin))
           .andExpect(status().isOk())
           .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(summary, "$.data.totalRecords")).isPositive();

        for (String path : List.of("/api/dashboard/pass-rate-trend", "/api/dashboard/process-comparison",
            "/api/dashboard/pareto", "/api/dashboard/defect-grade", "/api/dashboard/inspector-workload",
            "/api/dashboard/batch-gauge", "/api/dashboard/defect-duration")) {
            assertChartHasData(admin, path, projectId);
        }

        // CH-04 控制图按标准取数，不参与项目筛选。
        long standardId = standardIdOf("DEMO-2026-001");
        String control = mvc.perform(get("/api/dashboard/control-chart")
                .param("standardId", String.valueOf(standardId))
                .header("Authorization", "Bearer " + admin))
           .andExpect(status().isOk())
           .andReturn().getResponse().getContentAsString();
        assertThat((List<?>) JsonPath.read(control, "$.data.points")).as("CH-04 控制图").isNotEmpty();

        // CH-07 有三根柱：两名演示检验员 + 既有检验员。
        assertThat(longOf("SELECT COUNT(DISTINCT r.inspector_id) FROM inspection_record r "
            + "JOIN project p ON p.id = r.project_id WHERE " + DEMO_PROJECT)).isEqualTo(3);
    }

    @Test
    void seedingTwiceIsRejected() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        seed(admin);
        long recordsAfterFirst = demoRecordCount();

        mvc.perform(post("/api/demo/seed").header("Authorization", "Bearer " + admin))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40015))
           .andExpect(jsonPath("$.message").value("演示数据已存在，如需重新生成请先由管理员清理数据库"));

        // 被拒的第二次没有写入任何行。
        assertThat(demoRecordCount()).isEqualTo(recordsAfterFirst);
    }

    @Test
    void onlyAdminCanSeed() throws Exception {
        for (String[] account : new String[][] {
            {"engineer", "Engineer@123"}, {"inspector", "Inspector@123"}}) {
            String token = tokenFor(account[0], account[1]);
            mvc.perform(post("/api/demo/seed").header("Authorization", "Bearer " + token))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.code").value(40300));
        }
        // SRS 4.2 FR-16：接口级拒绝，不是只隐藏前端入口——被拒的请求不创建任何演示项目。
        assertThat(demoProjectCount()).isZero();
    }

    @Test
    void seededRecordsUseTheSharedJudgmentRules() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        seed(admin);

        // 把每条种子记录的快照按 JudgmentRules 重算，必须与库里的 result 一致（BR-01 未被绕过）。
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT r.measured_value, r.text_value, r.result,
                   r.std_lower, r.std_upper, r.std_target, r.std_tolerance,
                   s.judge_type, s.lower_limit, s.upper_limit, s.target_value, s.tolerance
            FROM inspection_record r
            JOIN inspection_standard s ON s.id = r.standard_id
            JOIN project p ON p.id = r.project_id
            WHERE %s
            """.formatted(DEMO_PROJECT));
        assertThat(rows).hasSize(360);

        for (Map<String, Object> row : rows) {
            JudgmentRules.Numeric snapshot = new JudgmentRules.Numeric(
                (BigDecimal) row.get("std_lower"), (BigDecimal) row.get("std_upper"),
                (BigDecimal) row.get("std_target"), (BigDecimal) row.get("std_tolerance"));
            String recomputed = judgmentRules.evaluate((String) row.get("judge_type"),
                (BigDecimal) row.get("measured_value"), (String) row.get("text_value"), snapshot);
            assertThat(recomputed).as("记录 result 必须由共享判定逻辑算出").isEqualTo(row.get("result"));

            // BR-02：五个 std_* 快照列照抄标准。
            assertThat(row.get("std_lower")).isEqualTo(row.get("lower_limit"));
            assertThat(row.get("std_upper")).isEqualTo(row.get("upper_limit"));
            assertThat(row.get("std_target")).isEqualTo(row.get("target_value"));
            assertThat(row.get("std_tolerance")).isEqualTo(row.get("tolerance"));
        }

        // FAIL 记录 1:1 生成缺陷，不单独插。
        long fails = longOf("SELECT COUNT(*) FROM inspection_record r JOIN project p ON p.id = r.project_id "
            + "WHERE " + DEMO_PROJECT + " AND r.result = 'FAIL'");
        long defects = longOf("SELECT COUNT(*) FROM defect d JOIN inspection_record r ON r.id = d.record_id "
            + "JOIN project p ON p.id = r.project_id WHERE " + DEMO_PROJECT);
        assertThat(defects).isEqualTo(fails);

        // 缺陷的 created_at 必须等于判定那一刻（记录的 inspect_time），否则 CH-08 的时长全为 0。
        assertThat(longOf("SELECT COUNT(*) FROM defect d JOIN inspection_record r ON r.id = d.record_id "
            + "JOIN project p ON p.id = r.project_id WHERE " + DEMO_PROJECT
            + " AND d.created_at <> r.inspect_time")).isZero();

        // CH-08 有数据：created_at + 48h 已经过去的记录，处理时长保留 4–48 小时的分布；
        // 距 now 不足 48h 的记录按此推算会落到未来，播种时截到 now 之前半小时。
        // 对所有记录统一断言：resolved_at 不晚于 now，且不早于 created_at。
        List<Map<String, Object>> closed = jdbc.queryForList("SELECT r.inspect_time, d.created_at, d.resolved_at "
            + "FROM defect d JOIN inspection_record r ON r.id = d.record_id "
            + "JOIN project p ON p.id = r.project_id WHERE " + DEMO_PROJECT
            + " AND d.defect_status = 'CLOSED'");
        assertThat(closed).isNotEmpty();
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> row : closed) {
            LocalDateTime createdAt = (LocalDateTime) row.get("created_at");
            LocalDateTime resolvedAt = (LocalDateTime) row.get("resolved_at");
            assertThat(resolvedAt).as("resolved_at 不能落在未来").isBeforeOrEqualTo(now);
            assertThat(resolvedAt).as("resolved_at 不能早于 created_at").isAfterOrEqualTo(createdAt);
            long hours = Duration.between(createdAt, resolvedAt).toHours();
            if (createdAt.plusHours(48).isBefore(now)) {
                // 截断不可能触发：4–48h 的原始分布必须保住，CH-08 的柱子才有意义
                assertThat(hours).as("历史缺陷处理时长").isBetween(4L, 48L);
            } else {
                assertThat(hours).as("近期缺陷处理时长").isBetween(0L, 48L);
            }
        }

        // 演示检验员用注入的 PasswordEncoder 编码 Demo@1234。
        List<String> hashes = jdbc.queryForList(
            "SELECT password_hash FROM app_user WHERE username LIKE 'demo-inspector-%'", String.class);
        assertThat(hashes).hasSize(2).allMatch(hash -> passwordEncoder.matches("Demo@1234", hash));
    }
}
