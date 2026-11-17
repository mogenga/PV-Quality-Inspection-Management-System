package com.pvqims.masterdata;

import java.util.HashMap;
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
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BatchApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String tokenFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    private long insertProject(String code, String status) {
        jdbc.update("INSERT INTO project (project_code, project_name, status, created_at) VALUES (?, ?, ?, NOW())",
            code, "项目" + code, status);
        return jdbc.queryForObject("SELECT id FROM project WHERE project_code = ?", Long.class, code);
    }

    private long insertBatch(long projectId, String batchNo) {
        return insertBatch(projectId, batchNo, "PLANNED");
    }

    private long insertBatch(long projectId, String batchNo, String status) {
        jdbc.update("INSERT INTO batch (project_id, batch_no, planned_qty, status, created_at) "
            + "VALUES (?, ?, 100, ?, NOW())", projectId, batchNo, status);
        return jdbc.queryForObject("SELECT id FROM batch WHERE project_id = ? AND batch_no = ?",
            Long.class, projectId, batchNo);
    }

    private long insertRecord(long projectId, long batchId) {
        jdbc.update("INSERT INTO inspection_standard "
            + "(project_id, scene, process_name, item_code, item_name, value_type, judge_type, created_at) "
            + "VALUES (?, 'PQC', '层压', 'PMax', 'Pmax', 'NUMERIC', 'RANGE', NOW())", projectId);
        long standardId = jdbc.queryForObject(
            "SELECT id FROM inspection_standard WHERE project_id = ? AND item_code = 'PMax'", Long.class, projectId);
        long inspectorId = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'inspector'", Long.class);
        jdbc.update("INSERT INTO inspection_record "
            + "(project_id, batch_id, standard_id, scene, process_name, item_name, inspector_id, "
            + "inspect_time, measured_value, result, created_at) "
            + "VALUES (?, ?, ?, 'PQC', '层压', 'Pmax', ?, NOW(), 710.0, 'PASS', NOW())",
            projectId, batchId, standardId, inspectorId);
        return jdbc.queryForObject("SELECT id FROM inspection_record WHERE batch_id = ?", Long.class, batchId);
    }

    /** Map.of 不接受 null 值，起止日期可空，故手拼 JSON 文本。 */
    private String batchJson(long projectId, String batchNo, String startDate, String endDate) {
        return """
            {"projectId":%d,"batchNo":"%s","plannedQty":1200,"startDate":%s,"endDate":%s,"status":"PLANNED"}
            """.formatted(projectId, batchNo, jsonOrNull(startDate), jsonOrNull(endDate));
    }

    private static String jsonOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void engineerCreatesBatchAndListFiltersByProject() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long p1 = insertProject("PV-B-1", "ACTIVE");
        long p2 = insertProject("PV-B-2", "ACTIVE");

        mvc.perform(post("/api/batches").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(batchJson(p1, "B-2026-001", "2026-11-01", "2026-11-10")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.batchNo").value("B-2026-001"))
           .andExpect(jsonPath("$.data.startDate").value("2026-11-01"))
           .andExpect(jsonPath("$.data.projectId").value(p1));

        insertBatch(p2, "B-OTHER");

        mvc.perform(get("/api/batches").param("projectId", String.valueOf(p1))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].batchNo").value("B-2026-001"));
    }

    @Test
    void listFiltersByBatchNoAndStatusWithoutLeakingPastEachOther() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long p1 = insertProject("PV-F-1", "ACTIVE");
        long p2 = insertProject("PV-F-2", "ACTIVE");
        insertBatch(p1, "B-KW-1", "PLANNED");
        insertBatch(p2, "B-KW-2", "PRODUCING");

        mvc.perform(get("/api/batches").param("keyword", "B-KW")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(2));

        mvc.perform(get("/api/batches").param("status", "PLANNED")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].batchNo").value("B-KW-1"));

        // 关键字 OR 未分组会被状态条件绕过：不分组时两个 B-KW 都从编号分支进入，返回 2。
        mvc.perform(get("/api/batches").param("keyword", "B-KW").param("status", "PLANNED")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].batchNo").value("B-KW-1"));
    }

    @Test
    void batchNoIsUniquePerProjectButRepeatableAcrossProjects() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long p1 = insertProject("PV-U-1", "ACTIVE");
        long p2 = insertProject("PV-U-2", "ACTIVE");
        insertBatch(p1, "B-SAME");

        // 同项目重复（含仅大小写不同）→ 拒绝
        for (String batchNo : List.of("B-SAME", "b-same")) {
            mvc.perform(post("/api/batches").header("Authorization", "Bearer " + engineer)
                    .contentType(APPLICATION_JSON)
                    .content(batchJson(p1, batchNo, null, null)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value(40004))
               .andExpect(jsonPath("$.message").value("该项目下批号已存在"));
        }

        // 另一个项目可以同名
        mvc.perform(post("/api/batches").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(batchJson(p2, "B-SAME", null, null)))
           .andExpect(status().isOk());
    }

    @Test
    void renamingBatchToExistingNoIsRejected() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long pid = insertProject("PV-REN", "ACTIVE");
        insertBatch(pid, "B-REN-1");
        long bid = insertBatch(pid, "B-REN-2");

        mvc.perform(put("/api/batches/{id}", bid).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "batchNo", "B-REN-1", "plannedQty", 100, "status", "PLANNED"))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40004))
           .andExpect(jsonPath("$.message").value("该项目下批号已存在"));

        assertThat(jdbc.queryForObject("SELECT batch_no FROM batch WHERE id = ?", String.class, bid))
            .isEqualTo("B-REN-2");
    }

    @Test
    void closedProjectMakesItsBatchesReadOnly() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long pid = insertProject("PV-CLOSED", "CLOSED");
        long bid = insertBatch(pid, "B-1");

        mvc.perform(post("/api/batches").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(batchJson(pid, "B-2", null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40005))
           .andExpect(jsonPath("$.message").value("项目已关闭，批次不可修改"));

        mvc.perform(put("/api/batches/{id}", bid).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "batchNo", "B-1", "plannedQty", 200, "status", "PRODUCING"))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40005));

        mvc.perform(delete("/api/batches/{id}", bid).header("Authorization", "Bearer " + engineer))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40005));

        // 重新开启项目后恢复可写，不能出现「关了再也改不回来」
        mvc.perform(put("/api/projects/{id}", pid).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectCode", "PV-CLOSED", "projectName", "项目PV-CLOSED", "status", "ACTIVE"))))
           .andExpect(status().isOk());
        mvc.perform(put("/api/batches/{id}", bid).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "batchNo", "B-1", "plannedQty", 200, "status", "PRODUCING"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.plannedQty").value(200));
    }

    @Test
    void batchWithRecordsCannotBeDeleted() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long pid = insertProject("PV-REC", "ACTIVE");
        long bid = insertBatch(pid, "B-REC");
        insertRecord(pid, bid);

        mvc.perform(delete("/api/batches/{id}", bid).header("Authorization", "Bearer " + engineer))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40007))
           .andExpect(jsonPath("$.message").value("批次下已有检验记录，不能删除"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch WHERE id = ?", Long.class, bid)).isEqualTo(1);
    }

    @Test
    void engineerDeletesEmptyBatchUnderActiveProject() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long pid = insertProject("PV-DEL", "ACTIVE");
        long bid = insertBatch(pid, "B-DEL");

        mvc.perform(delete("/api/batches/{id}", bid).header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch WHERE id = ?", Long.class, bid)).isZero();
    }

    @Test
    void updateNeverMovesBatchToAnotherProject() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long p1 = insertProject("PV-M-1", "ACTIVE");
        long p2 = insertProject("PV-M-2", "ACTIVE");
        long bid = insertBatch(p1, "B-M");

        mvc.perform(put("/api/batches/{id}", bid).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectId", p2, "batchNo", "B-M", "plannedQty", 300, "status", "PRODUCING"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.projectId").value(p1));

        assertThat(jdbc.queryForObject("SELECT project_id FROM batch WHERE id = ?", Long.class, bid)).isEqualTo(p1);
    }

    @Test
    void updateCanClearOptionalFieldsWithNull() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long pid = insertProject("PV-CLEAR-B", "ACTIVE");

        mvc.perform(post("/api/batches").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"projectId":%d,"batchNo":"B-CLEAR","plannedQty":500,"startDate":"2026-11-01",
                     "endDate":"2026-11-10","status":"PLANNED","remark":"首件"}
                    """.formatted(pid)))
           .andExpect(status().isOk());
        long bid = jdbc.queryForObject("SELECT id FROM batch WHERE project_id = ? AND batch_no = 'B-CLEAR'",
            Long.class, pid);

        // HashMap 允许 null 值；前端把留空的可选输入规范化为 null 提交。
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", "B-CLEAR");
        body.put("plannedQty", 500);
        body.put("startDate", null);
        body.put("endDate", null);
        body.put("status", "PLANNED");
        body.put("remark", null);
        mvc.perform(put("/api/batches/{id}", bid).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(body)))
           .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT start_date, end_date, remark FROM batch WHERE id = ?", bid);
        assertThat(row).containsEntry("start_date", null)
            .containsEntry("end_date", null)
            .containsEntry("remark", null);
    }

    @Test
    void endDateBeforeStartDateIsRejected() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long pid = insertProject("PV-DATE", "ACTIVE");

        mvc.perform(post("/api/batches").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(batchJson(pid, "B-DATE", "2026-11-10", "2026-11-01")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001))
           .andExpect(jsonPath("$.message").value("结束日期不能早于开始日期"));
    }

    @Test
    void unknownProjectOrBatchReturns404() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");

        mvc.perform(post("/api/batches").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(batchJson(999999L, "B-X", null, null)))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value(40400));

        mvc.perform(put("/api/batches/{id}", 999999L).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("batchNo", "B-X", "plannedQty", 1, "status", "PLANNED"))))
           .andExpect(status().isNotFound());

        mvc.perform(delete("/api/batches/{id}", 999999L).header("Authorization", "Bearer " + engineer))
           .andExpect(status().isNotFound());
    }

    @Test
    void inspectorCannotWriteBatches() throws Exception {
        String inspector = tokenFor("inspector", "Inspector@123");
        long pid = insertProject("PV-RD", "ACTIVE");

        mvc.perform(get("/api/batches").param("projectId", String.valueOf(pid))
                .header("Authorization", "Bearer " + inspector))
           .andExpect(status().isOk());
        mvc.perform(post("/api/batches").header("Authorization", "Bearer " + inspector)
                .contentType(APPLICATION_JSON)
                .content(batchJson(pid, "B-RD", null, null)))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value(40300));
    }
}
