package com.pvqims.inspection;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecordVoidApiTest {

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

    private int voidFlagOf(long recordId) {
        return jdbc.queryForObject("SELECT is_void FROM inspection_record WHERE id = ?",
            Integer.class, recordId);
    }

    @Test
    void adminVoidsRecordAndItLeavesDefaultList() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-VOID-1", "作废项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "VB-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "VOID-1", "作废项",
            "GT", "1", null, null, null);
        String created = mvc.perform(post("/api/inspection/records")
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long recordId = ((Number) JsonPath.read(created, "$.data.id")).longValue();

        mvc.perform(post("/api/inspection/records/{id}/void", recordId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"录入错批次\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.isVoid").value(true))
           .andExpect(jsonPath("$.data.voidReason").value("录入错批次"));

        mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(0));

        // BR-08：只作废不物理删除，行仍在库中且写入了原因。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_record WHERE id = ?",
            Long.class, recordId)).isEqualTo(1);
        assertThat(voidFlagOf(recordId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT void_reason FROM inspection_record WHERE id = ?",
            String.class, recordId)).isEqualTo("录入错批次");
    }

    @Test
    void includeVoidTrueReturnsVoidedRecordWithReason() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-VOID-2", "含作废项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "VB-2");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "VOID-2", "含作废项",
            "GT", "1", null, null, null);
        String created = mvc.perform(post("/api/inspection/records")
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long recordId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        mvc.perform(post("/api/inspection/records/{id}/void", recordId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"录入错批次\"}"))
           .andExpect(status().isOk());

        mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                .param("includeVoid", "true")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].isVoid").value(true))
           .andExpect(jsonPath("$.data.records[0].voidReason").value("录入错批次"));
    }

    @Test
    void voidReasonIsRequired() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-VOID-3", "原因必填项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "VB-3");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "VOID-3", "原因项",
            "GT", "1", null, null, null);
        String created = mvc.perform(post("/api/inspection/records")
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long recordId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        assertThat(voidFlagOf(recordId)).isZero();

        // 缺省、空串、全空白三种都不能作废。
        for (String body : new String[] {"{}", "{\"reason\":\"\"}", "{\"reason\":\"   \"}"}) {
            mvc.perform(post("/api/inspection/records/{id}/void", recordId)
                    .header("Authorization", "Bearer " + admin)
                    .contentType(APPLICATION_JSON)
                    .content(body))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value(40001))
               .andExpect(jsonPath("$.message", containsString("作废原因")));
        }

        assertThat(voidFlagOf(recordId)).isZero();
    }

    @Test
    void voidingTwiceIsRejected() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-VOID-4", "重复作废项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "VB-4");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "VOID-4", "重复项",
            "GT", "1", null, null, null);
        String created = mvc.perform(post("/api/inspection/records")
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long recordId = ((Number) JsonPath.read(created, "$.data.id")).longValue();

        mvc.perform(post("/api/inspection/records/{id}/void", recordId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"录入错批次\"}"))
           .andExpect(status().isOk());

        mvc.perform(post("/api/inspection/records/{id}/void", recordId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"重复作废\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40012))
           .andExpect(jsonPath("$.message", containsString("已作废")));

        // 第一次的原因不被覆盖。
        assertThat(jdbc.queryForObject("SELECT void_reason FROM inspection_record WHERE id = ?",
            String.class, recordId)).isEqualTo("录入错批次");
    }

    @Test
    void engineerAndInspectorCannotVoid() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long projectId = createProject(engineer, "PV-VOID-5", "作废权限项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "VB-5");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "VOID-5", "权限项",
            "GT", "1", null, null, null);
        String created = mvc.perform(post("/api/inspection/records")
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long recordId = ((Number) JsonPath.read(created, "$.data.id")).longValue();

        // 4.8：作废只有管理员可操作，接口级拒绝而不是仅隐藏入口。
        for (String token : List.of(engineer, inspector)) {
            mvc.perform(post("/api/inspection/records/{id}/void", recordId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content("{\"reason\":\"越权作废\"}"))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.code").value(40300));
        }

        assertThat(voidFlagOf(recordId)).isZero();
    }

    @Test
    void voidingMissingRecordReturns404() throws Exception {
        String admin = tokenFor("admin", "Admin@123");

        mvc.perform(post("/api/inspection/records/{id}/void", 999999)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"记录不存在\"}"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value(40400));
    }
}
