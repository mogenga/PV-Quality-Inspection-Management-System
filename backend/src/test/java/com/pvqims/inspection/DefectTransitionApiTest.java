package com.pvqims.inspection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.pvqims.inspection.mapper.DefectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DefectTransitionApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired DefectMapper defectMapper;

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

    /** 一次建齐项目、批次、标准与 FAIL 记录，返回 OPEN 状态缺陷单的 id。 */
    private long createDefect(String engineerToken, String inspectorToken, String projectCode,
                              String processName, String itemCode) throws Exception {
        long projectId = createProject(engineerToken, projectCode, projectCode + "项目", "ACTIVE");
        long batchId = createBatch(engineerToken, projectId, projectCode + "-B");
        long standardId = createStandard(engineerToken, projectId, "PQC", processName, itemCode,
            itemCode + "项", "RANGE", "3.0", "3.5", null, null);
        long recordId = createDefect(inspectorToken, batchId, standardId, "CRACK", "MAJOR");
        return jdbc.queryForObject("SELECT id FROM defect WHERE record_id = ?", Long.class, recordId);
    }

    private ResultActions transition(String token, long defectId, String action, String body)
        throws Exception {
        return mvc.perform(post("/api/defects/{id}/" + action, defectId)
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(body));
    }

    private String statusOf(long defectId) {
        return jdbc.queryForObject("SELECT defect_status FROM defect WHERE id = ?",
            String.class, defectId);
    }

    private Long handlerIdOf(long defectId) {
        return jdbc.queryForObject("SELECT handler_id FROM defect WHERE id = ?",
            Long.class, defectId);
    }

    private String noteOf(long defectId) {
        return jdbc.queryForObject("SELECT handle_note FROM defect WHERE id = ?",
            String.class, defectId);
    }

    private LocalDateTime resolvedAtOf(long defectId) {
        return jdbc.queryForObject("SELECT resolved_at FROM defect WHERE id = ?",
            LocalDateTime.class, defectId);
    }

    @Test
    void openToProcessingClaimsTheDefect() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long engineerId = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'engineer'",
            Long.class);
        long defectId = createDefect(engineer, inspector, "PV-DT-1", "缺陷流转-1", "DT-1");

        transition(engineer, defectId, "process", "{\"note\":\"已安排返工\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        assertThat(statusOf(defectId)).isEqualTo("PROCESSING");
        assertThat(handlerIdOf(defectId)).isEqualTo(engineerId);
        assertThat(noteOf(defectId)).isEqualTo("已安排返工");
        assertThat(resolvedAtOf(defectId)).isNull();
    }

    @Test
    void processingToVerifiedThenClosedSetsResolvedAt() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long defectId = createDefect(engineer, inspector, "PV-DT-2", "缺陷流转-2", "DT-2");

        transition(engineer, defectId, "process", "{}").andExpect(status().isOk());
        assertThat(statusOf(defectId)).isEqualTo("PROCESSING");
        assertThat(resolvedAtOf(defectId)).isNull();

        transition(engineer, defectId, "verify", "{}").andExpect(status().isOk());
        assertThat(statusOf(defectId)).isEqualTo("VERIFIED");
        assertThat(resolvedAtOf(defectId)).isNull();

        transition(engineer, defectId, "close", "{\"note\":\"返工完成并复检\"}")
            .andExpect(status().isOk());
        assertThat(statusOf(defectId)).isEqualTo("CLOSED");
        assertThat(resolvedAtOf(defectId)).isNotNull();
        assertThat(noteOf(defectId)).isEqualTo("返工完成并复检");
    }

    @Test
    void verifiedCanReturnToProcessingWithRejectionNote() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long defectId = createDefect(engineer, inspector, "PV-DT-3", "缺陷流转-3", "DT-3");

        transition(engineer, defectId, "process", "{\"note\":\"已安排返工\"}").andExpect(status().isOk());
        transition(engineer, defectId, "verify", "{}").andExpect(status().isOk());

        transition(engineer, defectId, "process", "{\"note\":\"边缘仍有气泡，重新返工\"}")
            .andExpect(status().isOk());

        assertThat(statusOf(defectId)).isEqualTo("PROCESSING");
        assertThat(noteOf(defectId)).isEqualTo("边缘仍有气泡，重新返工");
        assertThat(resolvedAtOf(defectId)).isNull();
    }

    @Test
    void illegalTransitionsAreRejected() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long defectId = createDefect(engineer, inspector, "PV-DT-4", "缺陷流转-4", "DT-4");

        // OPEN 只能 /process，跳级被拒。
        transition(engineer, defectId, "verify", "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40013))
            .andExpect(jsonPath("$.message", containsString("待处理")))
            .andExpect(jsonPath("$.message", containsString("验证")));
        transition(engineer, defectId, "close", "{\"note\":\"跳过验证\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40013))
            .andExpect(jsonPath("$.message", containsString("待处理")))
            .andExpect(jsonPath("$.message", containsString("关闭")));
        assertThat(statusOf(defectId)).isEqualTo("OPEN");

        transition(engineer, defectId, "process", "{}").andExpect(status().isOk());

        // PROCESSING 只能 /verify。
        transition(engineer, defectId, "close", "{\"note\":\"跳过验证\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40013))
            .andExpect(jsonPath("$.message", containsString("处理中")))
            .andExpect(jsonPath("$.message", containsString("关闭")));
        transition(engineer, defectId, "process", "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40013))
            .andExpect(jsonPath("$.message", containsString("处理中")))
            .andExpect(jsonPath("$.message", containsString("开始处理")));
        assertThat(statusOf(defectId)).isEqualTo("PROCESSING");

        transition(engineer, defectId, "verify", "{}").andExpect(status().isOk());

        // VERIFIED 只能 /close 或退回 /process。
        transition(engineer, defectId, "verify", "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40013))
            .andExpect(jsonPath("$.message", containsString("待验证")))
            .andExpect(jsonPath("$.message", containsString("验证")));
        assertThat(statusOf(defectId)).isEqualTo("VERIFIED");

        transition(engineer, defectId, "close", "{\"note\":\"返工完成并复检\"}")
            .andExpect(status().isOk());
        assertThat(statusOf(defectId)).isEqualTo("CLOSED");

        // CLOSED 是终态，三个端点都拒绝。
        transition(engineer, defectId, "process", "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40013))
            .andExpect(jsonPath("$.message", containsString("已关闭")))
            .andExpect(jsonPath("$.message", containsString("开始处理")));
        transition(engineer, defectId, "verify", "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40013))
            .andExpect(jsonPath("$.message", containsString("已关闭")))
            .andExpect(jsonPath("$.message", containsString("验证")));
        transition(engineer, defectId, "close", "{\"note\":\"再次关闭\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40013))
            .andExpect(jsonPath("$.message", containsString("已关闭")))
            .andExpect(jsonPath("$.message", containsString("关闭")));
        assertThat(statusOf(defectId)).isEqualTo("CLOSED");
    }

    @Test
    void closeRequiresHandleNote() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long defectId = createDefect(engineer, inspector, "PV-DT-5", "缺陷流转-5", "DT-5");

        transition(engineer, defectId, "process", "{}").andExpect(status().isOk());
        transition(engineer, defectId, "verify", "{}").andExpect(status().isOk());

        for (String body : List.of("{}", "{\"note\":\"\"}", "{\"note\":\"   \"}")) {
            transition(engineer, defectId, "close", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message", containsString("处理说明")));
        }

        assertThat(statusOf(defectId)).isEqualTo("VERIFIED");
        assertThat(resolvedAtOf(defectId)).isNull();
    }

    @Test
    void rejectRequiresHandleNote() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long defectId = createDefect(engineer, inspector, "PV-DT-6", "缺陷流转-6", "DT-6");

        transition(engineer, defectId, "process", "{}").andExpect(status().isOk());
        transition(engineer, defectId, "verify", "{}").andExpect(status().isOk());

        transition(engineer, defectId, "process", "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40001))
            .andExpect(jsonPath("$.message", containsString("处理说明")));

        assertThat(statusOf(defectId)).isEqualTo("VERIFIED");
    }

    @Test
    void transitionWithoutNoteKeepsPreviousNote() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long defectId = createDefect(engineer, inspector, "PV-DT-7", "缺陷流转-7", "DT-7");

        transition(engineer, defectId, "process", "{\"note\":\"已安排返工\"}").andExpect(status().isOk());
        transition(engineer, defectId, "verify", "{}").andExpect(status().isOk());

        assertThat(noteOf(defectId)).isEqualTo("已安排返工");
    }

    /**
     * 读后写竞态：transition 读到 VERIFIED 后、UPDATE 执行前，行已被并发请求改成 PROCESSING。
     * 测试事务内 SqlSession 一级缓存让 /close 的 selectById 仍返回读取时的 VERIFIED 快照，
     * 精确复现「验证通过后内存值过期」的窗口；更新语句若只按 id 匹配会把行改回 CLOSED，
     * 带 defect_status = 读取值 的谓词后影响 0 行，必须以 40013 拒绝。
     */
    @Test
    void closeRejectsWhenStatusChangedAfterRead() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long defectId = createDefect(engineer, inspector, "PV-DT-9", "缺陷流转-9", "DT-9");

        transition(engineer, defectId, "process", "{}").andExpect(status().isOk());
        transition(engineer, defectId, "verify", "{}").andExpect(status().isOk());

        // 让一级缓存持有 VERIFIED 快照，再把库里的状态改回 PROCESSING（模拟另一请求先提交）。
        assertThat(defectMapper.selectById(defectId).getDefectStatus()).isEqualTo("VERIFIED");
        jdbc.update("UPDATE defect SET defect_status = 'PROCESSING' WHERE id = ?", defectId);
        assertThat(statusOf(defectId)).isEqualTo("PROCESSING");
        assertThat(defectMapper.selectById(defectId).getDefectStatus())
            .as("一级缓存仍返回读取时的快照，模拟 transition 的过期读")
            .isEqualTo("VERIFIED");

        transition(engineer, defectId, "close", "{\"note\":\"返工完成并复检\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40013))
            .andExpect(jsonPath("$.message", containsString("缺陷状态已变化")));

        // 旧代码只按 id 更新，这里会被改成 CLOSED；带状态谓词后 0 行受影响。
        assertThat(statusOf(defectId)).isEqualTo("PROCESSING");
        assertThat(resolvedAtOf(defectId)).isNull();
        assertThat(noteOf(defectId)).isNull();
    }

    @Test
    void inspectorAndAdminCannotTransition() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        String admin = tokenFor("admin", "Admin@123");
        long defectId = createDefect(engineer, inspector, "PV-DT-8", "缺陷流转-8", "DT-8");

        // SRS 4.8：缺陷流转仅工程师可操作，检验员与管理员只读。
        for (String token : List.of(inspector, admin)) {
            for (String action : List.of("process", "verify", "close")) {
                transition(token, defectId, action, "{\"note\":\"越权流转\"}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));
            }
        }

        assertThat(statusOf(defectId)).isEqualTo("OPEN");
    }

    @Test
    void transitioningMissingDefectReturns404() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");

        for (String action : List.of("process", "verify", "close")) {
            transition(engineer, 999999, action, "{\"note\":\"缺陷不存在\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
        }
    }
}
