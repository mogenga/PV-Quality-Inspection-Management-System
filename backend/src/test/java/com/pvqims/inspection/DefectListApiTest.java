package com.pvqims.inspection;

import java.time.LocalDate;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DefectListApiTest {

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

    /** 录一条实测 4.0 的 FAIL 记录（标准 RANGE 3.0-3.5），返回 recordId。 */
    private long createDefect(String token, long batchId, long standardId,
                              String defectType, String defectGrade) throws Exception {
        String body = mvc.perform(post("/api/inspection/records").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, "4.0", null, defectType, defectGrade)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.result").value("FAIL"))
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    @Test
    void listReturnsDefectWithJoinedNamesAndSnapshot() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long projectId = createProject(engineer, "PV-DEF-1", "缺陷项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DB-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-L1", "DEF-1", "缺陷项",
            "RANGE", "3.0", "3.5", null, null);
        createDefect(inspector, batchId, standardId, "CRACK", "MAJOR");

        // 测试库共享，绝对计数只在全局唯一的工序名筛选下断言。
        mvc.perform(get("/api/defects").param("processName", "层压-L1")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].projectName").value("缺陷项目"))
           .andExpect(jsonPath("$.data.records[0].batchNo").value("DB-1"))
           .andExpect(jsonPath("$.data.records[0].itemName").value("缺陷项"))
           .andExpect(jsonPath("$.data.records[0].defectType").value("CRACK"))
           .andExpect(jsonPath("$.data.records[0].defectGrade").value("MAJOR"))
           .andExpect(jsonPath("$.data.records[0].defectStatus").value("OPEN"))
           .andExpect(jsonPath("$.data.records[0].inspectorName").value("李检验员"))
           .andExpect(jsonPath("$.data.records[0].handlerName").value(nullValue()))
           .andExpect(jsonPath("$.data.records[0].stdLower").value(3.0))
           .andExpect(jsonPath("$.data.records[0].stdUpper").value(3.5));
    }

    @Test
    void filtersByGradeStatusProcessAndDateRange() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long projectA = createProject(engineer, "PV-DEF-2A", "筛选项目A", "ACTIVE");
        long batchA = createBatch(engineer, projectA, "DA-1");
        long standardA = createStandard(engineer, projectA, "PQC", "层压-A1", "DEF-2A", "筛选项A",
            "RANGE", "3.0", "3.5", null, null);
        createDefect(inspector, batchA, standardA, "CRACK", "MAJOR");

        long projectB = createProject(engineer, "PV-DEF-2B", "筛选项目B", "ACTIVE");
        long batchB = createBatch(engineer, projectB, "DB-2");
        long standardB = createStandard(engineer, projectB, "OQC", "包装-B1", "DEF-2B", "筛选项B",
            "RANGE", "3.0", "3.5", null, null);
        createDefect(inspector, batchB, standardB, "BUBBLE", "MINOR");

        mvc.perform(get("/api/defects").param("grade", "MAJOR").param("processName", "层压-A1")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(get("/api/defects").param("processName", "包装-B1")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(get("/api/defects").param("grade", "MINOR").param("processName", "包装-B1")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(get("/api/defects").param("status", "OPEN").param("processName", "包装-B1")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(get("/api/defects").param("status", "CLOSED").param("processName", "包装-B1")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(0));

        // 缺陷时间取记录的 inspect_time（今天），明天起筛选不应命中。
        mvc.perform(get("/api/defects").param("from", LocalDate.now().plusDays(1).toString())
                .param("processName", "层压-A1")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void voidedSourceRecordHidesItsDefect() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-DEF-3", "作废缺陷项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "DC-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-V1", "DEF-3", "作废项",
            "RANGE", "3.0", "3.5", null, null);
        long recordId = createDefect(inspector, batchId, standardId, "CRACK", "MAJOR");

        mvc.perform(get("/api/defects").param("processName", "层压-V1")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(post("/api/inspection/records/{id}/void", recordId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"录入错批次\"}"))
           .andExpect(status().isOk());

        mvc.perform(get("/api/defects").param("processName", "层压-V1")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(0));

        // BR-08：缺陷行仍在库中，只是不再出现在列表。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM defect WHERE record_id = ?",
            Long.class, recordId)).isEqualTo(1);
    }

    @Test
    void defectListIsOpenToAllRoles() throws Exception {
        for (String[] account : new String[][] {
            {"admin", "Admin@123"}, {"engineer", "Engineer@123"}, {"inspector", "Inspector@123"}}) {
            String token = tokenFor(account[0], account[1]);
            mvc.perform(get("/api/defects").header("Authorization", "Bearer " + token))
               .andExpect(status().isOk());
        }
    }

    @Test
    void emptyResultReturnsEmptyPageNotError() throws Exception {
        String inspector = tokenFor("inspector", "Inspector@123");

        mvc.perform(get("/api/defects").param("processName", "不存在的工序-NONE")
                .header("Authorization", "Bearer " + inspector))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(0))
           .andExpect(jsonPath("$.data.records").isEmpty());
    }
}
