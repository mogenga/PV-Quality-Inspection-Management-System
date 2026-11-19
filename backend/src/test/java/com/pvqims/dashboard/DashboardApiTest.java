package com.pvqims.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 看板指标卡、趋势、工序对比与三张缺陷类图表的接口测试（FR-13、FR-14）。
 * 测试库共享：凡计数断言都用本用例自建的 projectId 过滤，不依赖绝对总数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String tokenFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    private long createProject(String token, String code, String name, String status) throws Exception {
        String body = mvc.perform(post("/api/projects").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectCode", code, "projectName", name, "status", status))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 建项目 → 建批次 → 返回 batchId。 */
    private long createBatch(String token, long projectId, String batchNo) throws Exception {
        String body = mvc.perform(post("/api/batches").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"projectId":%d,"batchNo":"%s","plannedQty":100,"status":"PRODUCING"}
                    """.formatted(projectId, batchNo)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 建项目 → 建标准 → 返回 standardId。 */
    private long createStandard(String token, long projectId, String scene, String processName,
                                String itemCode, String itemName, String judgeType,
                                String lower, String upper, String target, String tolerance) throws Exception {
        return createStandard(token, projectId, scene, processName, itemCode, itemName, judgeType,
            lower, upper, target, tolerance, 0);
    }

    private long createStandard(String token, long projectId, String scene, String processName,
                                String itemCode, String itemName, String judgeType,
                                String lower, String upper, String target, String tolerance,
                                int sortOrder) throws Exception {
        String body = mvc.perform(post("/api/standards").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, scene, processName, itemCode, itemName, judgeType,
                    lower, upper, target, tolerance, sortOrder)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 数值列用 JSON 片段传入：传 "3.0" 或 null（null 表示 JSON null）。 */
    private static String num(String value) {
        return value == null ? "null" : value;
    }

    private String standardBody(long projectId, String scene, String processName, String itemCode,
                                String itemName, String judgeType,
                                String lower, String upper, String target, String tolerance) {
        return standardBody(projectId, scene, processName, itemCode, itemName, judgeType,
            lower, upper, target, tolerance, 0);
    }

    private String standardBody(long projectId, String scene, String processName, String itemCode,
                                String itemName, String judgeType,
                                String lower, String upper, String target, String tolerance,
                                int sortOrder) {
        String valueType = "PASS_FAIL".equals(judgeType) ? "TEXT" : "NUMERIC";
        return """
            {"projectId":%d,"scene":"%s","processName":"%s","itemCode":"%s","itemName":"%s","unit":"mm",
             "valueType":"%s","judgeType":"%s",
             "lowerLimit":%s,"upperLimit":%s,"targetValue":%s,"tolerance":%s,
             "isEnabled":true,"sortOrder":%d}
            """.formatted(projectId, scene, processName, itemCode, itemName, valueType, judgeType,
                          num(lower), num(upper), num(target), num(tolerance), sortOrder);
    }

    private String recordBody(long batchId, long standardId, String measured, String text,
                              String defectType, String defectGrade) {
        return """
            {"batchId":%d,"standardId":%d,"measuredValue":%s,"textValue":%s,
             "defectType":%s,"defectGrade":%s}
            """.formatted(batchId, standardId, num(measured), jsonOrNull(text),
                          jsonOrNull(defectType), jsonOrNull(defectGrade));
    }

    private static String jsonOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** 提交一条校验记录并返回 recordId；4.x 的实测值落在 RANGE 3.0–3.5 之外即 FAIL，须带缺陷信息。 */
    private long createRecord(String token, long batchId, long standardId, String measured,
                              String defectType, String defectGrade) throws Exception {
        String body = mvc.perform(post("/api/inspection/records")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, measured, null, defectType, defectGrade)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void voidRecord(String adminToken, long recordId) throws Exception {
        mvc.perform(post("/api/inspection/records/{id}/void", recordId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"看板统计不含作废\"}"))
           .andExpect(status().isOk());
    }

    /** FAIL 记录同事务生成 1:1 缺陷单，按 recordId 取 defectId。 */
    private long defectIdOf(long recordId) {
        return jdbc.queryForObject("SELECT id FROM defect WHERE record_id = ?", Long.class, recordId);
    }

    /** 工程师把缺陷走完整闭环 process -> verify -> close（SRS 3.3）。 */
    private void closeDefect(String engineerToken, long defectId) throws Exception {
        for (String action : List.of("process", "verify")) {
            mvc.perform(post("/api/defects/{id}/" + action, defectId)
                    .header("Authorization", "Bearer " + engineerToken)
                    .contentType(APPLICATION_JSON)
                    .content("{}"))
               .andExpect(status().isOk());
        }
        mvc.perform(post("/api/defects/{id}/close", defectId)
                .header("Authorization", "Bearer " + engineerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"note\":\"返工完成并复检\"}"))
           .andExpect(status().isOk());
    }

    @Test
    void summaryCountsRecordsPassRateAndOpenDefects() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-DASH-1", "看板指标项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-D1", "DASH-1", "指标项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, standardId, "3.1", null, null);
        createRecord(engineer, batchId, standardId, "3.2", null, null);
        createRecord(engineer, batchId, standardId, "3.3", null, null);
        createRecord(engineer, batchId, standardId, "4.0", "CRACK", "MAJOR");
        createRecord(engineer, batchId, standardId, "4.1", "CRACK", "MAJOR");

        mvc.perform(get("/api/dashboard/summary").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.totalRecords").value(5))
           .andExpect(jsonPath("$.data.passRecords").value(3))
           .andExpect(jsonPath("$.data.passRate").value(60.0))
           .andExpect(jsonPath("$.data.openDefects").value(2))
           // 本项目没有已关闭缺陷：平均处理时长是「没有数据」，不是 0 小时。
           .andExpect(jsonPath("$.data.avgHandleHours").value(nullValue()));
    }

    @Test
    void voidedRecordsAreExcludedFromAllAggregates() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-DASH-2", "看板作废项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-2");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-D2", "DASH-2", "作废项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, standardId, "3.1", null, null);
        createRecord(engineer, batchId, standardId, "3.2", null, null);
        createRecord(engineer, batchId, standardId, "3.3", null, null);
        createRecord(engineer, batchId, standardId, "4.0", "CRACK", "MAJOR");
        long voided = createRecord(engineer, batchId, standardId, "4.1", "CRACK", "MAJOR");
        voidRecord(admin, voided);

        mvc.perform(get("/api/dashboard/summary").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.totalRecords").value(4))
           .andExpect(jsonPath("$.data.passRecords").value(3))
           .andExpect(jsonPath("$.data.passRate").value(75.0))
           .andExpect(jsonPath("$.data.openDefects").value(1))
           .andExpect(jsonPath("$.data.avgHandleHours").value(nullValue()));
    }

    @Test
    void trendAggregatesByDayAndExcludesVoided() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-DASH-3", "看板趋势项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-3");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-D3", "DASH-3", "趋势项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, standardId, "3.1", null, null);
        createRecord(engineer, batchId, standardId, "4.0", "CRACK", "MAJOR");
        long voided = createRecord(engineer, batchId, standardId, "3.2", null, null);
        voidRecord(admin, voided);

        // 同一天只产生一个点，且作废的那条不计数（BR-08）。
        mvc.perform(get("/api/dashboard/pass-rate-trend").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].day").value(LocalDate.now().toString()))
           .andExpect(jsonPath("$.data[0].total").value(2))
           .andExpect(jsonPath("$.data[0].passed").value(1))
           .andExpect(jsonPath("$.data[0].passRate").value(50.0));
    }

    @Test
    void processComparisonGroupsByProcessName() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-DASH-4", "看板工序项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-4");
        long lamination = createStandard(engineer, projectId, "PQC", "层压-D4", "DASH-4A", "层压项",
            "RANGE", "3.0", "3.5", null, null);
        long packing = createStandard(engineer, projectId, "OQC", "包装-D4", "DASH-4B", "包装项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, lamination, "3.1", null, null);
        createRecord(engineer, batchId, packing, "4.0", "CRACK", "MAJOR");

        // 按合格率升序：不合格的包装工序在前。
        mvc.perform(get("/api/dashboard/process-comparison").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(2))
           .andExpect(jsonPath("$.data[0].processName").value("包装-D4"))
           .andExpect(jsonPath("$.data[0].total").value(1))
           .andExpect(jsonPath("$.data[0].passed").value(0))
           .andExpect(jsonPath("$.data[0].passRate").value(0.0))
           .andExpect(jsonPath("$.data[1].processName").value("层压-D4"))
           .andExpect(jsonPath("$.data[1].total").value(1))
           .andExpect(jsonPath("$.data[1].passed").value(1))
           .andExpect(jsonPath("$.data[1].passRate").value(100.0));
    }

    @Test
    void emptyResultReturnsEmptyListNotError() throws Exception {
        String inspector = tokenFor("inspector", "Inspector@123");

        mvc.perform(get("/api/dashboard/summary").param("projectId", "999999")
                .header("Authorization", "Bearer " + inspector))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.totalRecords").value(0))
           .andExpect(jsonPath("$.data.passRecords").value(0))
           .andExpect(jsonPath("$.data.passRate").value(0.0))
           .andExpect(jsonPath("$.data.openDefects").value(0))
           .andExpect(jsonPath("$.data.avgHandleHours").value(nullValue()));

        mvc.perform(get("/api/dashboard/pass-rate-trend").param("projectId", "999999")
                .header("Authorization", "Bearer " + inspector))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data").isEmpty());

        mvc.perform(get("/api/dashboard/process-comparison").param("projectId", "999999")
                .header("Authorization", "Bearer " + inspector))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void paretoCumulativeEndsAtExactlyOneHundred() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-DASH-5", "看板帕累托项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-5");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-D5", "DASH-5", "帕累托项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, standardId, "4.0", "CRACK", "MAJOR");
        createRecord(engineer, batchId, standardId, "4.1", "CRACK", "MAJOR");
        createRecord(engineer, batchId, standardId, "4.2", "BUBBLE", "MINOR");

        mvc.perform(get("/api/dashboard/pareto").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.length()").value(2))
           .andExpect(jsonPath("$.data[0].defectType").value("CRACK"))
           .andExpect(jsonPath("$.data[0].count").value(2))
           .andExpect(jsonPath("$.data[0].cumulativePercent").value(66.7))
           .andExpect(jsonPath("$.data[1].defectType").value("BUBBLE"))
           .andExpect(jsonPath("$.data[1].count").value(1))
           // AC-09：最后一项强制 100.0，不能是浮点累计出来的 99.9。
           .andExpect(jsonPath("$.data[1].cumulativePercent").value(100.0));
    }

    @Test
    void defectGradeGroupsByScene() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-DASH-6", "看板等级项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-6");
        long pqc = createStandard(engineer, projectId, "PQC", "层压-D6", "DASH-6A", "等级项A",
            "RANGE", "3.0", "3.5", null, null);
        long oqc = createStandard(engineer, projectId, "OQC", "包装-D6", "DASH-6B", "等级项B",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, pqc, "4.0", "CRACK", "CRITICAL");
        createRecord(engineer, batchId, oqc, "4.0", "BUBBLE", "MINOR");
        createRecord(engineer, batchId, oqc, "4.1", "BUBBLE", "MINOR");

        // ORDER BY scene, defect_grade：OQC 字典序在 PQC 之前。
        mvc.perform(get("/api/dashboard/defect-grade").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.length()").value(2))
           .andExpect(jsonPath("$.data[0].scene").value("OQC"))
           .andExpect(jsonPath("$.data[0].defectGrade").value("MINOR"))
           .andExpect(jsonPath("$.data[0].count").value(2))
           .andExpect(jsonPath("$.data[1].scene").value("PQC"))
           .andExpect(jsonPath("$.data[1].defectGrade").value("CRITICAL"))
           .andExpect(jsonPath("$.data[1].count").value(1));
    }

    @Test
    void defectDurationAveragesOnlyClosedDefects() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-DASH-7", "看板时长项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-7");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-D7", "DASH-7", "时长项",
            "RANGE", "3.0", "3.5", null, null);
        long closedRecord = createRecord(engineer, batchId, standardId, "4.0", "CRACK", "MAJOR");
        createRecord(engineer, batchId, standardId, "4.1", "BUBBLE", "MINOR");
        closeDefect(engineer, defectIdOf(closedRecord));

        // 只有已关闭的 MAJOR 参与统计；未关闭的 MINOR 不出现。
        mvc.perform(get("/api/dashboard/defect-duration").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].defectGrade").value("MAJOR"))
           .andExpect(jsonPath("$.data[0].avgHours").value(greaterThanOrEqualTo(0.0)));

        // 指标卡的平均处理时长走另一条查询，同样在存在已关闭缺陷时非 null。
        mvc.perform(get("/api/dashboard/summary").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.openDefects").value(1))
           .andExpect(jsonPath("$.data.avgHandleHours").value(greaterThanOrEqualTo(0.0)));
    }

    @Test
    void defectChartsIgnoreVoidedRecords() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-DASH-8", "看板缺陷作废项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-8");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-D8", "DASH-8", "缺陷作废项",
            "RANGE", "3.0", "3.5", null, null);
        long crackRecord = createRecord(engineer, batchId, standardId, "4.0", "CRACK", "MAJOR");
        createRecord(engineer, batchId, standardId, "4.1", "BUBBLE", "MINOR");

        mvc.perform(get("/api/dashboard/pareto").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(2));

        voidRecord(admin, crackRecord);

        // BR-08：作废记录的缺陷行仍在库中，但不参与任何缺陷类图表。
        mvc.perform(get("/api/dashboard/pareto").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].defectType").value("BUBBLE"))
           .andExpect(jsonPath("$.data[0].count").value(1))
           .andExpect(jsonPath("$.data[0].cumulativePercent").value(100.0));

        mvc.perform(get("/api/dashboard/defect-grade").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].scene").value("PQC"))
           .andExpect(jsonPath("$.data[0].defectGrade").value("MINOR"))
           .andExpect(jsonPath("$.data[0].count").value(1));
    }

    @Test
    void batchIdAndDateFiltersNarrowTheTrend() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-DASH-9", "看板筛选分支项目", "ACTIVE");
        long batchA = createBatch(engineer, projectId, "DB-9A");
        long batchB = createBatch(engineer, projectId, "DB-9B");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-D9", "DASH-9", "筛选分支项",
            "RANGE", "3.0", "3.5", null, null);
        // 批次 A：2 PASS + 1 FAIL -> 合格率 2/3 = 66.7（非整数，覆盖 rate() 的舍入）。
        createRecord(engineer, batchA, standardId, "3.1", null, null);
        createRecord(engineer, batchA, standardId, "3.2", null, null);
        createRecord(engineer, batchA, standardId, "4.0", "CRACK", "MAJOR");
        // 批次 B：1 PASS。batchId 是本用例自建，共享库里唯一。
        createRecord(engineer, batchB, standardId, "3.3", null, null);

        mvc.perform(get("/api/dashboard/pass-rate-trend").param("batchId", String.valueOf(batchA))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].total").value(3))
           .andExpect(jsonPath("$.data[0].passed").value(2))
           .andExpect(jsonPath("$.data[0].passRate").value(66.7));

        // from 分支：明天起没有任何记录。
        mvc.perform(get("/api/dashboard/pass-rate-trend").param("projectId", String.valueOf(projectId))
                .param("from", LocalDate.now().plusDays(1).toString())
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data").isEmpty());

        // to 分支：昨天为止没有任何记录（服务层把 to 转成次日零点，不含今天）。
        mvc.perform(get("/api/dashboard/pass-rate-trend").param("projectId", String.valueOf(projectId))
                .param("to", LocalDate.now().minusDays(1).toString())
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data").isEmpty());

        // 闭区间：from=to=今天命中两批共 4 条。
        mvc.perform(get("/api/dashboard/pass-rate-trend").param("projectId", String.valueOf(projectId))
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().toString())
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].total").value(4))
           .andExpect(jsonPath("$.data[0].passed").value(3));
    }

    @Test
    void defectChartsAreOpenToAllRolesAndEmptyForUnknownProject() throws Exception {
        // SRS 4.8：看板对全部已登录角色开放；未知项目返回空列表而不是错误（N == 0）。
        for (String[] account : new String[][] {
            {"admin", "Admin@123"}, {"engineer", "Engineer@123"}, {"inspector", "Inspector@123"}}) {
            String token = tokenFor(account[0], account[1]);
            for (String path : List.of("/api/dashboard/pareto", "/api/dashboard/defect-grade",
                "/api/dashboard/defect-duration")) {
                mvc.perform(get(path).param("projectId", "999999")
                        .header("Authorization", "Bearer " + token))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.code").value(0))
                   .andExpect(jsonPath("$.data").isEmpty());
            }
        }
    }

    @Test
    void dashboardIsOpenToAllRoles() throws Exception {
        // SRS 4.8：看板对全部已登录角色开放，不加角色校验。
        for (String[] account : new String[][] {
            {"admin", "Admin@123"}, {"engineer", "Engineer@123"}, {"inspector", "Inspector@123"}}) {
            String token = tokenFor(account[0], account[1]);
            for (String path : List.of("/api/dashboard/summary", "/api/dashboard/pass-rate-trend",
                "/api/dashboard/process-comparison")) {
                mvc.perform(get(path).header("Authorization", "Bearer " + token))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.code").value(0));
            }
        }
    }

    @Test
    void sceneAndProcessFiltersNarrowTheTrend() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-DASH-11", "看板场景筛选项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-11");
        long pqc = createStandard(engineer, projectId, "PQC", "层压-D11", "DASH-11A", "过程项",
            "RANGE", "3.0", "3.5", null, null);
        long oqc = createStandard(engineer, projectId, "OQC", "包装-D11", "DASH-11B", "出货项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, pqc, "3.1", null, null);
        createRecord(engineer, batchId, oqc, "4.0", "CRACK", "MAJOR");

        // SRS 4.7：按场景筛选后只剩 PQC 的那条，OQC 的记录不计数。
        mvc.perform(get("/api/dashboard/pass-rate-trend").param("projectId", String.valueOf(projectId))
                .param("scene", "PQC")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].total").value(1))
           .andExpect(jsonPath("$.data[0].passed").value(1))
           .andExpect(jsonPath("$.data[0].passRate").value(100.0));

        // 工序同理：只命中「层压-D11」。
        mvc.perform(get("/api/dashboard/pass-rate-trend").param("projectId", String.valueOf(projectId))
                .param("processName", "包装-D11")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].total").value(1))
           .andExpect(jsonPath("$.data[0].passed").value(0))
           .andExpect(jsonPath("$.data[0].passRate").value(0.0));

        // 不带参数 = 不过滤：两条记录都在。新增参数不能改变既有调用的结果。
        mvc.perform(get("/api/dashboard/pass-rate-trend").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].total").value(2))
           .andExpect(jsonPath("$.data[0].passed").value(1));

        // 场景筛选同样作用于缺陷类图表：PQC 没有缺陷，帕累托为空。
        mvc.perform(get("/api/dashboard/pareto").param("projectId", String.valueOf(projectId))
                .param("scene", "PQC")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void defectChartsApplyBatchAndDateFilters() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-DASH-10", "看板缺陷筛选项目", "ACTIVE");
        long batchA = createBatch(engineer, projectId, "DB-10A");
        long batchB = createBatch(engineer, projectId, "DB-10B");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-D10", "DASH-10", "缺陷筛选项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchA, standardId, "4.0", "CRACK", "MAJOR");
        createRecord(engineer, batchA, standardId, "4.1", "CRACK", "MINOR");
        createRecord(engineer, batchA, standardId, "4.2", "BUBBLE", "MINOR");
        createRecord(engineer, batchB, standardId, "4.3", "BUBBLE", "MINOR");

        // batchId 分支：只命中批次 A 的两条 CRACK 与 BUBBLE（批次 B 的 BUBBLE 不计入）。
        mvc.perform(get("/api/dashboard/pareto").param("batchId", String.valueOf(batchA))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(2))
           .andExpect(jsonPath("$.data[0].defectType").value("CRACK"))
           .andExpect(jsonPath("$.data[0].count").value(2))
           .andExpect(jsonPath("$.data[1].defectType").value("BUBBLE"))
           .andExpect(jsonPath("$.data[1].count").value(1));

        // from 分支：明天起没有记录，缺陷类图表同样返回空列表（不是错误）。
        mvc.perform(get("/api/dashboard/pareto").param("projectId", String.valueOf(projectId))
                .param("from", LocalDate.now().plusDays(1).toString())
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data").isEmpty());
    }
}
