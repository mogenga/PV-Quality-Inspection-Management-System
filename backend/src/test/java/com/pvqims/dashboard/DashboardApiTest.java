package com.pvqims.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 看板指标卡与两张记录类图表的接口测试（FR-13、FR-14）。
 * 测试库共享：凡计数断言都用本用例自建的 projectId 过滤，不依赖绝对总数。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

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
}
