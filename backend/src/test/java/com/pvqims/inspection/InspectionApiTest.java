package com.pvqims.inspection;

import java.math.BigDecimal;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InspectionApiTest {

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

    private long recordCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inspection_record", Long.class);
    }

    private long defectCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM defect", Long.class);
    }

    @Test
    void tasksReturnOnlyEnabledStandardsOfTheBatchProjectInSortOrder() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-TASK-1", "待检项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "TB-1");

        // 乱序创建 sortOrder 2 → 0 → 1，再建一条停用的（sortOrder 3）：
        // 按 id 排会是 T-2、T-0、T-1，只有按 sortOrder 排才能得到 T-0、T-1、T-2。
        createStandard(engineer, projectId, "PQC", "层压", "T-2", "排序 2",
            "GT", "1", null, null, null, 2);
        createStandard(engineer, projectId, "PQC", "层压", "T-0", "排序 0",
            "GT", "1", null, null, null, 0);
        createStandard(engineer, projectId, "PQC", "层压", "T-1", "排序 1",
            "GT", "1", null, null, null, 1);
        long disabledId = createStandard(engineer, projectId, "PQC", "层压", "T-D", "停用项",
            "GT", "1", null, null, null, 3);
        mvc.perform(patch("/api/standards/{id}/enabled", disabledId)
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content("{\"isEnabled\":false}"))
           .andExpect(status().isOk());

        mvc.perform(get("/api/inspection/tasks")
                .param("projectId", String.valueOf(projectId))
                .param("batchId", String.valueOf(batchId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(3))
           .andExpect(jsonPath("$.data[*].itemCode").value(contains("T-0", "T-1", "T-2")));
    }

    @Test
    void tasksIncludeLastMeasuredValueOfThatBatch() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-TASK-2", "上次实测项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "TB-2");
        long pmax = createStandard(engineer, projectId, "PQC", "层压", "P-MAX", "功率",
            "GT", "100", null, null, null);
        long other = createStandard(engineer, projectId, "PQC", "层压", "P-MIN", "最小功率",
            "GT", "100", null, null, null);

        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, pmax, "101", null, null, null)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.result").value("PASS"));

        mvc.perform(get("/api/inspection/tasks")
                .param("projectId", String.valueOf(projectId))
                .param("batchId", String.valueOf(batchId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(2))
           .andExpect(jsonPath("$.data[0].standardId").value(pmax))
           .andExpect(jsonPath("$.data[0].lastMeasuredValue").value(101))
           .andExpect(jsonPath("$.data[0].lastResult").value("PASS"))
           .andExpect(jsonPath("$.data[1].standardId").value(other))
           .andExpect(jsonPath("$.data[1].lastMeasuredValue").value(nullValue()))
           .andExpect(jsonPath("$.data[1].lastResult").value(nullValue()));
    }

    @Test
    void tasksFilterBySceneAndProcessName() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-TASK-3", "筛选待检项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "TB-3");
        createStandard(engineer, projectId, "PQC", "层压", "PQC-1", "层压项", "GT", "1", null, null, null);
        createStandard(engineer, projectId, "OQC", "包装", "OQC-1", "包装项", "GT", "1", null, null, null);

        mvc.perform(get("/api/inspection/tasks").param("batchId", String.valueOf(batchId))
                .param("scene", "PQC").header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].itemCode").value("PQC-1"));

        mvc.perform(get("/api/inspection/tasks").param("batchId", String.valueOf(batchId))
                .param("processName", "包装").header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].itemCode").value("OQC-1"));
    }

    @Test
    void tasksRejectBatchFromAnotherProject() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectA = createProject(engineer, "PV-TASK-4A", "项目 A", "ACTIVE");
        long projectB = createProject(engineer, "PV-TASK-4B", "项目 B", "ACTIVE");
        long batchA = createBatch(engineer, projectA, "TB-4");

        mvc.perform(get("/api/inspection/tasks")
                .param("projectId", String.valueOf(projectB))
                .param("batchId", String.valueOf(batchA))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40010));
    }

    @Test
    void judgePreviewReturnsVerdictWithoutPersisting() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-JUDGE", "判定预览项目", "ACTIVE");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "J-1", "判定项",
            "RANGE", "3.0", "3.5", null, null);
        long before = recordCount();

        mvc.perform(post("/api/inspection/judge").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content("{\"standardId\":%d,\"measuredValue\":3.5}".formatted(standardId)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.result").value("PASS"));

        mvc.perform(post("/api/inspection/judge").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content("{\"standardId\":%d,\"measuredValue\":3.6}".formatted(standardId)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.result").value("FAIL"));

        assertThat(recordCount()).isEqualTo(before);
    }

    @Test
    void adminCannotJudgeOrCreateRecords() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-PERM", "录入权限项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "PB-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "PERM-1", "权限项",
            "GT", "1", null, null, null);

        // 4.8：管理员不能录入检验数据。
        mvc.perform(post("/api/inspection/judge").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"standardId\":%d,\"measuredValue\":2}".formatted(standardId)))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value(40300));
        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value(40300));

        // 工程师与检验员都能录入。
        for (String token : List.of(engineer, inspector)) {
            mvc.perform(post("/api/inspection/judge").header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content("{\"standardId\":%d,\"measuredValue\":2}".formatted(standardId)))
               .andExpect(status().isOk());
            mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content(recordBody(batchId, standardId, "2", null, null, null)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.result").value("PASS"));
        }
    }

    @Test
    void createRecordCopiesStandardSnapshot() throws Exception {
        String inspector = tokenFor("inspector", "Inspector@123");
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-SNAP", "快照项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "SB-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "SNAP-1", "快照项",
            "RANGE", "3.0", "3.5", null, null);

        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + inspector)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "3.2", null, null, null)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.result").value("PASS"))
           .andExpect(jsonPath("$.data.stdLower").value(3.0))
           .andExpect(jsonPath("$.data.stdUpper").value(3.5))
           .andExpect(jsonPath("$.data.stdUnit").value("mm"))
           .andExpect(jsonPath("$.data.projectId").value(projectId))
           .andExpect(jsonPath("$.data.scene").value("PQC"))
           .andExpect(jsonPath("$.data.processName").value("层压"))
           .andExpect(jsonPath("$.data.itemName").value("快照项"));

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT project_id, batch_id, standard_id, scene, process_name, item_name, inspector_id, "
                + "std_lower, std_upper, std_unit, result FROM inspection_record WHERE batch_id = ?",
            batchId);
        assertThat((BigDecimal) row.get("std_lower")).isEqualByComparingTo("3.0");
        assertThat((BigDecimal) row.get("std_upper")).isEqualByComparingTo("3.5");
        assertThat(row.get("std_unit")).isEqualTo("mm");
        assertThat(row.get("result")).isEqualTo("PASS");
        assertThat(((Number) row.get("project_id")).longValue()).isEqualTo(projectId);
        assertThat(((Number) row.get("standard_id")).longValue()).isEqualTo(standardId);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM inspection_record WHERE batch_id = ? AND is_void = 0", Long.class, batchId))
            .isEqualTo(1);
        long inspectorId = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'inspector'", Long.class);
        assertThat(((Number) row.get("inspector_id")).longValue()).isEqualTo(inspectorId);
    }

    @Test
    void snapshotSurvivesStandardEdit() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-SURV", "快照存续项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "SURV-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "SURV-1", "存续项",
            "RANGE", "3.0", "3.5", null, null);

        String created = mvc.perform(post("/api/inspection/records")
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "3.2", null, null, null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.result").value("PASS"))
            .andReturn().getResponse().getContentAsString();
        long recordId = ((Number) JsonPath.read(created, "$.data.id")).longValue();

        // 标准改成 9.0–9.5：历史记录的快照与判定结果都不得回溯（BR-02、AC-04）。
        mvc.perform(put("/api/standards/{id}", standardId).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "层压", "SURV-1", "存续项",
                    "RANGE", "9.0", "9.5", null, null)))
           .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT std_lower FROM inspection_record WHERE id = ?",
            BigDecimal.class, recordId)).isEqualByComparingTo("3.0");
        assertThat(jdbc.queryForObject("SELECT std_upper FROM inspection_record WHERE id = ?",
            BigDecimal.class, recordId)).isEqualByComparingTo("3.5");
        assertThat(jdbc.queryForObject("SELECT result FROM inspection_record WHERE id = ?",
            String.class, recordId)).isEqualTo("PASS");
    }

    @Test
    void failRecordCreatesDefectInSameTransaction() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-DEFECT", "缺陷项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "DEF-1", "缺陷项",
            "RANGE", "3.0", "3.5", null, null);

        String body = mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "4.0", null, "CRACK", "MAJOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.result").value("FAIL"))
            .andReturn().getResponse().getContentAsString();
        long recordId = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        Map<String, Object> defect = jdbc.queryForMap(
            "SELECT record_id, defect_type, defect_grade, defect_status, handler_id, handle_note, resolved_at "
                + "FROM defect WHERE record_id = ?", recordId);
        assertThat(((Number) defect.get("record_id")).longValue()).isEqualTo(recordId);
        assertThat(defect.get("defect_type")).isEqualTo("CRACK");
        assertThat(defect.get("defect_grade")).isEqualTo("MAJOR");
        assertThat(defect.get("defect_status")).isEqualTo("OPEN");
        assertThat(defect.get("handler_id")).isNull();
        assertThat(defect.get("handle_note")).isNull();
        assertThat(defect.get("resolved_at")).isNull();
    }

    @Test
    void failWithoutDefectInfoSavesNothing() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-NODEF", "无缺陷信息项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "ND-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "ND-1", "缺缺陷项",
            "RANGE", "3.0", "3.5", null, null);
        long recordsBefore = recordCount();
        long defectsBefore = defectCount();

        // 只填类型不填等级同样拒绝：两项都必填。
        for (String[] defectInfo : new String[][] {{null, null}, {"CRACK", null}, {null, "MAJOR"}}) {
            mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                    .contentType(APPLICATION_JSON)
                    .content(recordBody(batchId, standardId, "4.0", null, defectInfo[0], defectInfo[1])))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value(40011));
        }

        assertThat(recordCount()).isEqualTo(recordsBefore);
        assertThat(defectCount()).isEqualTo(defectsBefore);
    }

    @Test
    void disabledStandardIsRejectedOnCreate() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-DIS", "停用标准项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DIS-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "DIS-1", "停用项",
            "GT", "1", null, null, null);
        mvc.perform(patch("/api/standards/{id}/enabled", standardId)
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content("{\"isEnabled\":false}"))
           .andExpect(status().isOk());
        long recordsBefore = recordCount();

        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40009));

        assertThat(recordCount()).isEqualTo(recordsBefore);
    }

    @Test
    void standardFromAnotherProjectIsRejected() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectA = createProject(engineer, "PV-MIS-1", "批次项目", "ACTIVE");
        long projectB = createProject(engineer, "PV-MIS-2", "标准项目", "ACTIVE");
        long batchA = createBatch(engineer, projectA, "MIS-1");
        long standardB = createStandard(engineer, projectB, "PQC", "层压", "MIS-1", "别项目标准",
            "GT", "1", null, null, null);
        long recordsBefore = recordCount();

        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchA, standardB, "2", null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40010));

        assertThat(recordCount()).isEqualTo(recordsBefore);
    }

    @Test
    void closedProjectRejectsNewRecords() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-CLOSED-R", "关闭录入项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "CR-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "CR-1", "关闭项",
            "GT", "1", null, null, null);

        mvc.perform(put("/api/projects/{id}", projectId).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectCode", "PV-CLOSED-R", "projectName", "关闭录入项目", "status", "CLOSED"))))
           .andExpect(status().isOk());
        long recordsBefore = recordCount();

        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40005))
           .andExpect(jsonPath("$.message", containsString("项目已关闭")));

        assertThat(recordCount()).isEqualTo(recordsBefore);
    }

    @Test
    void numericJudgeTypeRequiresMeasuredValue() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-NOMEAS", "缺实测值项目", "ACTIVE");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "NM-1", "缺实测项",
            "GT", "100", null, null, null);

        mvc.perform(post("/api/inspection/judge").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content("{\"standardId\":%d}".formatted(standardId)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("实测值")));
    }

    @Test
    void clientSuppliedResultIsIgnoredAndServerRecalculates() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-BR01", "BR01 项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "BR-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "BR-1", "服务端判定项",
            "RANGE", "3.0", "3.5", null, null);

        // BR-01：请求体里带 result=PASS，但实测 4.0 超范围，落库结果必须是服务端判定的 FAIL。
        String body = mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"batchId":%d,"standardId":%d,"measuredValue":4.0,"result":"PASS",
                     "defectType":"CRACK","defectGrade":"MAJOR"}
                    """.formatted(batchId, standardId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.result").value("FAIL"))
            .andReturn().getResponse().getContentAsString();
        long recordId = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        assertThat(jdbc.queryForObject("SELECT result FROM inspection_record WHERE id = ?",
            String.class, recordId)).isEqualTo("FAIL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM defect WHERE record_id = ?",
            Long.class, recordId)).isEqualTo(1);
    }

    @Test
    void recordQueryFiltersByBatchSceneProcessAndResult() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-RQ-1", "记录筛选项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "RQ-1");
        long pqc = createStandard(engineer, projectId, "PQC", "层压", "RQ-PQC", "层压项",
            "RANGE", "3.0", "3.5", null, null);
        long oqc = createStandard(engineer, projectId, "OQC", "包装", "RQ-OQC", "包装项",
            "PASS_FAIL", null, null, null, null);

        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, pqc, "3.2", null, null, null)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.result").value("PASS"));
        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, oqc, null, "FAIL", "CRACK", "MAJOR")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.result").value("FAIL"));

        mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(2));

        mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                .param("scene", "OQC").header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].itemName").value("包装项"));

        mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                .param("processName", "层压").header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].itemName").value("层压项"));

        mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                .param("result", "FAIL").header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].itemName").value("包装项"));
    }

    @Test
    void recordQueryFiltersByInspectDateRange() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-RQ-2", "记录日期项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "RQ-2");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "RQ-D", "日期项",
            "GT", "1", null, null, null);

        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
           .andExpect(status().isOk());

        String today = LocalDate.now().toString();
        mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                .param("from", today).param("to", today)
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                .param("from", LocalDate.now().plusDays(1).toString())
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(0))
           .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void recordQueryIsOrderedNewestFirst() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-RQ-3", "记录排序项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "RQ-3");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "RQ-O", "排序项",
            "GT", "1", null, null, null);

        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
           .andExpect(status().isOk());
        String second = mvc.perform(post("/api/inspection/records")
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "3", null, null, null)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long secondId = ((Number) JsonPath.read(second, "$.data.id")).longValue();

        // 同秒时 inspect_time 相同，断开平局靠 id DESC，因此这一条也能覆盖 tie-break。
        mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.records[0].id").value(secondId));
    }

    @Test
    void recordQueryIsOpenToAllRoles() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-RQ-4", "记录角色项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "RQ-4");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "RQ-R", "角色项",
            "GT", "1", null, null, null);
        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
           .andExpect(status().isOk());

        // FR-09：记录查询对全部已登录角色开放。
        for (String token : List.of(admin, inspector, engineer)) {
            mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                    .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.total").value(1));
        }
    }

    @Test
    void recordQueryNeverReturnsVoidedRecords() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-RQ-5", "记录作废项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "RQ-5");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "RQ-V", "作废项",
            "GT", "1", null, null, null);
        String created = mvc.perform(post("/api/inspection/records")
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "2", null, null, null)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long recordId = ((Number) JsonPath.read(created, "$.data.id")).longValue();

        // 本阶段没有作废接口（FR-10 属阶段五），直接改库模拟。
        jdbc.update("UPDATE inspection_record SET is_void = 1 WHERE id = ?", recordId);

        mvc.perform(get("/api/inspection/records").param("batchId", String.valueOf(batchId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(0))
           .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void outOfVocabularyDefectGradeIsRejectedWithoutPersisting() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-GRADE", "缺陷等级枚举项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "GR-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压", "GR-1", "等级项",
            "RANGE", "3.0", "3.5", null, null);
        long recordsBefore = recordCount();
        long defectsBefore = defectCount();

        // SRS 5.12：defect_grade 只允许 CRITICAL/MAJOR/MINOR，越界值必须在入口返回 400 而非落库失败 500。
        mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "4.0", null, "CRACK", "SEVERE")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001))
           .andExpect(jsonPath("$.message", containsString("缺陷等级")));

        assertThat(recordCount()).isEqualTo(recordsBefore);
        assertThat(defectCount()).isEqualTo(defectsBefore);
    }
}
