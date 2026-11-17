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
class ProjectApiTest {

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

    @Test
    void inspectorAndAdminCanReadButOnlyEngineerCanWrite() throws Exception {
        String inspector = tokenFor("inspector", "Inspector@123");
        String admin = tokenFor("admin", "Admin@123");
        String engineer = tokenFor("engineer", "Engineer@123");

        // 4.8：项目管理对检验员与管理员都是只读
        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + inspector))
           .andExpect(status().isOk());
        mvc.perform(get("/api/projects").header("Authorization", "Bearer " + admin))
           .andExpect(status().isOk());

        for (String t : List.of(inspector, admin)) {
            mvc.perform(post("/api/projects").header("Authorization", "Bearer " + t)
                    .contentType(APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of(
                        "projectCode", "PV-403", "projectName", "越权项目", "status", "ACTIVE"))))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.code").value(40300));
            mvc.perform(put("/api/projects/{id}", 1).header("Authorization", "Bearer " + t)
                    .contentType(APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of(
                        "projectCode", "PV-403", "projectName", "越权项目", "status", "ACTIVE"))))
               .andExpect(status().isForbidden());
            mvc.perform(delete("/api/projects/{id}", 1).header("Authorization", "Bearer " + t))
               .andExpect(status().isForbidden());
        }

        // 工程师可写
        long id = createProject(engineer, "PV-OK", "正常项目", "ACTIVE");
        assertThat(id).isPositive();
    }

    @Test
    void engineerCreatesProjectAndItAppearsInList() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        mvc.perform(post("/api/projects").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectCode", "PV-2026-001", "projectName", "青海格尔木 100MW",
                    "productModel", "710W 双玻", "customerName", "国电投",
                    "manufacturerName", "晶科能源", "location", "青海格尔木",
                    "status", "ACTIVE", "remark", "一期"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.id").isNumber())
           .andExpect(jsonPath("$.data.projectCode").value("PV-2026-001"))
           .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mvc.perform(get("/api/projects").param("keyword", "格尔木")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].customerName").value("国电投"));
    }

    @Test
    void duplicateProjectCodeRejectedEvenWithOnlyCaseDifference() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        createProject(engineer, "PV-2026-001", "青海格尔木", "ACTIVE");

        mvc.perform(post("/api/projects").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectCode", "pv-2026-001", "projectName", "重名", "status", "ACTIVE"))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40003))
           .andExpect(jsonPath("$.message").value("项目编号已存在"));
    }

    @Test
    void projectWithBatchesCannotBeDeleted() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long id = createProject(engineer, "PV-DEL-1", "待删项目", "ACTIVE");
        jdbc.update("INSERT INTO batch (project_id, batch_no, planned_qty, status, created_at) "
            + "VALUES (?, 'B-1', 100, 'PLANNED', NOW())", id);

        mvc.perform(delete("/api/projects/{id}", id).header("Authorization", "Bearer " + engineer))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40006))
           .andExpect(jsonPath("$.message").value("项目下存在批次或标准，不能删除"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id = ?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void projectWithStandardsCannotBeDeleted() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long id = createProject(engineer, "PV-DEL-2", "有标准项目", "ACTIVE");
        jdbc.update("INSERT INTO inspection_standard "
            + "(project_id, scene, process_name, item_code, item_name, value_type, judge_type, created_at) "
            + "VALUES (?, 'PQC', '层压', 'PMax', 'Pmax', 'NUMERIC', 'RANGE', NOW())", id);

        mvc.perform(delete("/api/projects/{id}", id).header("Authorization", "Bearer " + engineer))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40006));
    }

    @Test
    void emptyProjectCanBeDeletedAndUnknownIdReturns404() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long id = createProject(engineer, "PV-DEL-3", "空项目", "ACTIVE");

        mvc.perform(delete("/api/projects/{id}", id).header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id = ?", Long.class, id)).isZero();

        mvc.perform(delete("/api/projects/{id}", 999999L).header("Authorization", "Bearer " + engineer))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value(40400));
        mvc.perform(put("/api/projects/{id}", 999999L).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectCode", "PV-X", "projectName", "无此人", "status", "ACTIVE"))))
           .andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByKeywordAndStatusWithoutLeakingPastEachOther() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        createProject(engineer, "PV-A-1", "青海格尔木", "ACTIVE");
        createProject(engineer, "PV-A-2", "宁夏中卫", "CLOSED");
        // 编号含「青海」而状态 CLOSED：只有编号这一条命中路径，且会被状态过滤掉。
        createProject(engineer, "PV-青海-1", "宁夏银川", "CLOSED");

        mvc.perform(get("/api/projects").param("keyword", "青海")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(jsonPath("$.data.total").value(2));

        mvc.perform(get("/api/projects").param("status", "CLOSED")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(jsonPath("$.data.total").value(2))
           .andExpect(jsonPath("$.data.records[0].status").value("CLOSED"));

        // 关键字 OR 未分组会被状态条件绕过：不分组时这里会返回 2。
        mvc.perform(get("/api/projects").param("keyword", "青海").param("status", "ACTIVE")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].projectCode").value("PV-A-1"));
    }

    @Test
    void updateCanClearOptionalFieldsWithNull() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        mvc.perform(post("/api/projects").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectCode", "PV-CLEAR", "projectName", "可清空项目",
                    "productModel", "710W 双玻", "customerName", "国电投",
                    "manufacturerName", "晶科能源", "location", "青海格尔木",
                    "status", "ACTIVE", "remark", "一期"))))
           .andExpect(status().isOk());
        long id = jdbc.queryForObject("SELECT id FROM project WHERE project_code = 'PV-CLEAR'", Long.class);

        // HashMap 允许 null 值；前端把留空的可选输入规范化为 null 提交。
        Map<String, Object> body = new HashMap<>();
        body.put("projectCode", "PV-CLEAR");
        body.put("projectName", "可清空项目");
        body.put("productModel", null);
        body.put("customerName", null);
        body.put("manufacturerName", null);
        body.put("location", null);
        body.put("status", "ACTIVE");
        body.put("remark", null);
        mvc.perform(put("/api/projects/{id}", id).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(body)))
           .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT product_model, customer_name, manufacturer_name, location, remark "
                + "FROM project WHERE id = ?", id);
        assertThat(row).containsEntry("product_model", null)
            .containsEntry("customer_name", null)
            .containsEntry("manufacturer_name", null)
            .containsEntry("location", null)
            .containsEntry("remark", null);
    }

    @Test
    void renamingProjectToExistingCodeIsRejected() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        createProject(engineer, "PV-R-1", "原项目", "ACTIVE");
        long other = createProject(engineer, "PV-R-2", "待改项目", "ACTIVE");

        // 大小写不敏感：与新增时同一条唯一索引约束。
        for (String code : List.of("PV-R-1", "pv-r-1")) {
            mvc.perform(put("/api/projects/{id}", other).header("Authorization", "Bearer " + engineer)
                    .contentType(APPLICATION_JSON)
                    .content(om.writeValueAsString(Map.of(
                        "projectCode", code, "projectName", "待改项目", "status", "ACTIVE"))))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value(40003))
               .andExpect(jsonPath("$.message").value("项目编号已存在"));
        }

        assertThat(jdbc.queryForObject("SELECT project_code FROM project WHERE id = ?", String.class, other))
            .isEqualTo("PV-R-2");
    }

    @Test
    void invalidStatusIsRejectedWith400() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        mvc.perform(post("/api/projects").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectCode", "PV-BAD", "projectName", "非法状态", "status", "ARCHIVED"))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001));
    }
}
