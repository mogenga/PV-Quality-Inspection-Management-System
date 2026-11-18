package com.pvqims.export;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 导出端点测试。上限临时设为 2：3 条数据就能覆盖超限分支，不必插 10001 行。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = "pvqims.export.max-rows=2")
class RecordCsvApiTest {

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

    private void createRecord(String token, long batchId, long standardId, String measured,
                              String text, String defectType, String defectGrade) throws Exception {
        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, measured, text, defectType, defectGrade)))
            .andExpect(status().isOk());
    }

    /** 导出并返回原文；断言内容类型与 BOM 之外的用例共用。 */
    private MvcResult export(String token, long batchId, String... params) throws Exception {
        var request = get("/api/export/records.csv").param("batchId", String.valueOf(batchId))
            .header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request).andReturn();
    }

    /** 去掉 BOM 后按 CRLF 切行：1 行表头 + N 行数据。 */
    private static List<String> csvLines(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String text = body.startsWith("﻿") ? body.substring(1) : body;
        return text.isEmpty() ? List.of() : List.of(text.split("\r\n"));
    }

    @Test
    void returnsCsvAttachmentWithBom() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-EXP-1", "导出项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "EX-1");
        long standardId = createStandard(engineer, projectId, "IQC", "来料", "EXP-1", "导出项",
            "GT", "1", null, null, null);
        createRecord(engineer, batchId, standardId, "2", null, null, null);

        MvcResult result = export(engineer, batchId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).contains("text/csv");
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).contains("attachment").containsPattern("records-\\d{8}\\.csv");
        // 中文 Windows 的 Excel 靠 BOM 识别 UTF-8。
        assertThat(result.getResponse().getContentAsByteArray())
            .startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);

        List<String> lines = csvLines(result);
        assertThat(lines).hasSize(2);
        // 场景与判定输出中文。
        assertThat(lines.get(1)).contains("来料检验").contains("合格");
    }

    @Test
    void exportHonoursFilters() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-EXP-2", "导出筛选项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "EX-2");
        long pqc = createStandard(engineer, projectId, "PQC", "层压", "EX-PQC", "层压项",
            "RANGE", "3.0", "3.5", null, null);
        long oqc = createStandard(engineer, projectId, "OQC", "包装", "EX-OQC", "包装项",
            "PASS_FAIL", null, null, null, null);
        createRecord(engineer, batchId, pqc, "3.2", null, null, null);
        createRecord(engineer, batchId, oqc, null, "FAIL", "CRACK", "MAJOR");

        assertThat(csvLines(export(engineer, batchId))).hasSize(3);

        List<String> failOnly = csvLines(export(engineer, batchId, "result", "FAIL"));
        assertThat(failOnly).hasSize(2);
        assertThat(failOnly.get(1)).contains("包装项").contains("不合格");

        List<String> oqcOnly = csvLines(export(engineer, batchId, "scene", "OQC"));
        assertThat(oqcOnly).hasSize(2);
        assertThat(oqcOnly.get(1)).contains("出货检验");

        List<String> futureOnly = csvLines(export(engineer, batchId,
            "from", LocalDate.now().plusDays(1).toString()));
        assertThat(futureOnly).hasSize(1);
    }

    @Test
    void exportExcludesVoidedRecords() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-EXP-3", "导出作废项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "EX-3");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "EXP-3", "作废项",
            "GT", "1", null, null, null);
        long recordId = createRecordReturningId(engineer, batchId, standardId);

        assertThat(csvLines(export(engineer, batchId))).hasSize(2);

        mvc.perform(post("/api/inspection/records/{id}/void", recordId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"录入错批次\"}"))
           .andExpect(status().isOk());

        // BR-08、AC-06：作废记录不参与统计，导出只剩表头。
        assertThat(csvLines(export(engineer, batchId))).hasSize(1);
    }

    @Test
    void exportBeyondCapIsRejected() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-EXP-4", "导出超限项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "EX-4");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "EXP-4", "超限项",
            "GT", "1", null, null, null);
        for (int i = 0; i < 3; i++) {
            createRecord(engineer, batchId, standardId, "2", null, null, null);
        }

        // 上限是 2：不静默截断到 2 行，而是明确拒绝并提示缩小范围。
        mvc.perform(get("/api/export/records.csv").param("batchId", String.valueOf(batchId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40014))
           .andExpect(jsonPath("$.message", containsString("上限")));
    }

    @Test
    void onlyEngineerCanExport() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-EXP-5", "导出权限项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "EX-5");

        // 4.8：导出仅工程师可用，接口级拒绝而不是仅隐藏入口。
        for (String token : List.of(admin, inspector)) {
            mvc.perform(get("/api/export/records.csv").param("batchId", String.valueOf(batchId))
                    .header("Authorization", "Bearer " + token))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.code").value(40300));
        }

        mvc.perform(get("/api/export/records.csv").param("batchId", String.valueOf(batchId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk());
    }

    private long createRecordReturningId(String token, long batchId, long standardId) throws Exception {
        String body = mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }
}
