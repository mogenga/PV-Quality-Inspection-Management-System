package com.pvqims.standard;

import java.math.BigDecimal;
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
class StandardApiTest {

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

    /** 数值列用 JSON 片段传入：传 "3.0" 或 null（null 表示 JSON null）。 */
    private static String num(String value) {
        return value == null ? "null" : value;
    }

    private String standardBody(long projectId, String scene, String itemCode, String itemName,
                                String valueType, String judgeType,
                                String lower, String upper, String target, String tolerance) {
        return """
            {"projectId":%d,"scene":"%s","processName":"层压","itemCode":"%s","itemName":"%s",
             "unit":"mm","valueType":"%s","judgeType":"%s",
             "lowerLimit":%s,"upperLimit":%s,"targetValue":%s,"tolerance":%s,
             "isEnabled":true,"sortOrder":0}
            """.formatted(projectId, scene, itemCode, itemName, valueType, judgeType,
                          num(lower), num(upper), num(target), num(tolerance));
    }

    private long createStandard(String token, long projectId, String scene, String itemCode, String itemName,
                                String valueType, String judgeType,
                                String lower, String upper, String target, String tolerance) throws Exception {
        String body = mvc.perform(post("/api/standards").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, scene, itemCode, itemName, valueType, judgeType,
                    lower, upper, target, tolerance)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    @Test
    void inspectorAndAdminCanReadButOnlyEngineerCanWrite() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        String admin = tokenFor("admin", "Admin@123");

        // 附录 B：GET /api/standards 对全部已登录角色开放，P4 录入页需要检验员读标准。
        for (String token : List.of(engineer, inspector, admin)) {
            mvc.perform(get("/api/standards").header("Authorization", "Bearer " + token))
               .andExpect(status().isOk());
        }

        long projectId = createProject(engineer, "PV-STD-RD", "标准权限项目", "ACTIVE");
        long standardId = createStandard(engineer, projectId, "PQC", "PERM-1", "权限项",
            "NUMERIC", "GT", "1", null, null, null);
        assertThat(standardId).isPositive();

        // 4.8：标准配置对检验员与管理员都是只读，写操作一律 403 + 40300。
        for (String token : List.of(inspector, admin)) {
            mvc.perform(post("/api/standards").header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content(standardBody(projectId, "PQC", "PERM-2", "越权新增",
                        "NUMERIC", "GT", "1", null, null, null)))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.code").value(40300));
            mvc.perform(put("/api/standards/{id}", standardId).header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content(standardBody(projectId, "PQC", "PERM-1", "越权修改",
                        "NUMERIC", "GT", "2", null, null, null)))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.code").value(40300));
            mvc.perform(patch("/api/standards/{id}/enabled", standardId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content("{\"isEnabled\":false}"))
               .andExpect(status().isForbidden())
               .andExpect(jsonPath("$.code").value(40300));
        }
    }

    @Test
    void engineerCreatesRangeStandardAndItAppearsInList() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-RANGE", "区间标准项目", "ACTIVE");
        long standardId = createStandard(engineer, projectId, "PQC", "FRAME-T", "边框厚度",
            "NUMERIC", "RANGE", "3.0", "3.5", null, null);
        assertThat(standardId).isPositive();

        mvc.perform(get("/api/standards").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records.length()").value(1))
           .andExpect(jsonPath("$.data.records[0].judgeType").value("RANGE"))
           .andExpect(jsonPath("$.data.records[0].lowerLimit").value(3.0))
           .andExpect(jsonPath("$.data.records[0].upperLimit").value(3.5))
           .andExpect(jsonPath("$.data.records[0].isEnabled").value(true))
           .andExpect(jsonPath("$.data.records[0].unit").value("mm"));
    }

    @Test
    void judgeTypeDecidesWhichNumericFieldsAreRequired() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-REQ", "必填校验项目", "ACTIVE");

        mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "REQ-1", "GT 缺下限",
                    "NUMERIC", "GT", null, null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001))
           .andExpect(jsonPath("$.message").value("判定方式为 GT 时必须填写下限"));

        mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "REQ-2", "LT 缺上限",
                    "NUMERIC", "LT", null, null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("上限")));

        mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "REQ-3", "RANGE 缺上限",
                    "NUMERIC", "RANGE", "3.0", null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("上下限")));

        mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "REQ-4", "STD_TOL 缺公差",
                    "NUMERIC", "STD_TOL", null, null, "710", null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("公差")));

        assertThat(createStandard(engineer, projectId, "PQC", "REQ-5", "GT 有下限",
            "NUMERIC", "GT", "100", null, null, null)).isPositive();
    }

    @Test
    void lowerMustBeLessThanUpper() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-LU", "上下限项目", "ACTIVE");

        // 第二组为边界：lower == upper 同样拒绝。
        for (String[] limits : new String[][] {{"3.5", "3.0"}, {"3.0", "3.0"}}) {
            mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                    .contentType(APPLICATION_JSON)
                    .content(standardBody(projectId, "PQC", "LU-1", "下限不小于上限",
                        "NUMERIC", "RANGE", limits[0], limits[1], null, null)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value(40001))
               .andExpect(jsonPath("$.message").value("下限必须小于上限"));
        }
    }

    @Test
    void toleranceMustBePositive() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-TOL", "公差项目", "ACTIVE");

        for (String tolerance : List.of("0", "-1")) {
            mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                    .contentType(APPLICATION_JSON)
                    .content(standardBody(projectId, "PQC", "TOL-1", "公差非正",
                        "NUMERIC", "STD_TOL", null, null, "710", tolerance)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.code").value(40001))
               .andExpect(jsonPath("$.message").value("公差必须大于 0"));
        }
    }

    @Test
    void valueTypeMustMatchJudgeType() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-VT", "值类型项目", "ACTIVE");

        mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "VT-1", "TEXT 标准值",
                    "TEXT", "STD_TOL", null, null, "710", "5")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001))
           .andExpect(jsonPath("$.message", containsString("NUMERIC")));

        mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "VT-2", "NUMERIC 人工判定",
                    "NUMERIC", "PASS_FAIL", null, null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001))
           .andExpect(jsonPath("$.message", containsString("TEXT")));
    }

    @Test
    void unusedNumericFieldsAreClearedOnSave() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-CLR", "数值规整项目", "ACTIVE");

        // 建单：RANGE 带上无关的 target/tolerance，入库前必须清空，避免 P4 判定拿到过期数值。
        long standardId = createStandard(engineer, projectId, "PQC", "CLR-1", "区间项",
            "NUMERIC", "RANGE", "3.0", "3.5", "9.9", "1.1");
        Map<String, Object> created = jdbc.queryForMap(
            "SELECT lower_limit, upper_limit, target_value, tolerance FROM inspection_standard WHERE id = ?",
            standardId);
        assertThat((BigDecimal) created.get("lower_limit")).isEqualByComparingTo("3.0");
        assertThat((BigDecimal) created.get("upper_limit")).isEqualByComparingTo("3.5");
        assertThat(created.get("target_value")).isNull();
        assertThat(created.get("tolerance")).isNull();

        // 改单：RANGE → GT，旧上限与 target/tolerance 必须写成 SQL NULL。
        mvc.perform(put("/api/standards/{id}", standardId).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "CLR-1", "区间项",
                    "NUMERIC", "GT", "3.0", "5.0", "9.9", "1.1")))
           .andExpect(status().isOk());

        Map<String, Object> updated = jdbc.queryForMap(
            "SELECT lower_limit, upper_limit, target_value, tolerance FROM inspection_standard WHERE id = ?",
            standardId);
        assertThat((BigDecimal) updated.get("lower_limit")).isEqualByComparingTo("3.0");
        assertThat(updated.get("upper_limit")).isNull();
        assertThat(updated.get("target_value")).isNull();
        assertThat(updated.get("tolerance")).isNull();

        // 改单：GT → LT，这次留下限必须被清空。
        mvc.perform(put("/api/standards/{id}", standardId).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "CLR-1", "区间项",
                    "NUMERIC", "LT", "3.0", "5.0", null, null)))
           .andExpect(status().isOk());

        Map<String, Object> cleared = jdbc.queryForMap(
            "SELECT lower_limit, upper_limit FROM inspection_standard WHERE id = ?", standardId);
        assertThat(cleared.get("lower_limit")).isNull();
        assertThat((BigDecimal) cleared.get("upper_limit")).isEqualByComparingTo("5.0");
    }

    @Test
    void stdTolStandardKeepsTargetAndToleranceAndClearsLimits() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-STDTOL", "标准值公差项目", "ACTIVE");

        // STD_TOL 带无关上下限：入库前必须清空，target/tolerance 必须保留。
        long standardId = createStandard(engineer, projectId, "PQC", "TOL-OK", "功率",
            "NUMERIC", "STD_TOL", "3.0", "9.9", "710", "5");
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT lower_limit, upper_limit, target_value, tolerance FROM inspection_standard WHERE id = ?",
            standardId);
        assertThat(row.get("lower_limit")).isNull();
        assertThat(row.get("upper_limit")).isNull();
        assertThat((BigDecimal) row.get("target_value")).isEqualByComparingTo("710");
        assertThat((BigDecimal) row.get("tolerance")).isEqualByComparingTo("5");
    }

    @Test
    void passFailStandardClearsAllNumericColumns() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-PASSFAIL", "人工判定项目", "ACTIVE");

        // PASS_FAIL 四个数值列都无意义：即使请求里带了，也必须全部写成 NULL。
        long standardId = createStandard(engineer, projectId, "OQC", "PF-OK", "外观",
            "TEXT", "PASS_FAIL", "1.0", "2.0", "3.0", "4.0");
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT lower_limit, upper_limit, target_value, tolerance FROM inspection_standard WHERE id = ?",
            standardId);
        assertThat(row.get("lower_limit")).isNull();
        assertThat(row.get("upper_limit")).isNull();
        assertThat(row.get("target_value")).isNull();
        assertThat(row.get("tolerance")).isNull();
    }

    @Test
    void itemCodeIsUniqueWithinProjectButNotAcrossProjects() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long first = createProject(engineer, "PV-UQ-1", "唯一项目一", "ACTIVE");
        long second = createProject(engineer, "PV-UQ-2", "唯一项目二", "ACTIVE");

        createStandard(engineer, first, "PQC", "PMax", "功率", "NUMERIC", "GT", "700", null, null, null);
        createStandard(engineer, second, "PQC", "PMax", "功率", "NUMERIC", "GT", "700", null, null, null);

        // 唯一索引使用 utf8mb4_0900_ai_ci，仅大小写不同也算重复。
        mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(first, "OQC", "pmax", "功率",
                    "NUMERIC", "GT", "700", null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40008))
           .andExpect(jsonPath("$.message").value("该项目下检验项编码已存在"));
    }

    @Test
    void renamingItemCodeToExistingIsRejected() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-RN", "改编码项目", "ACTIVE");
        createStandard(engineer, projectId, "PQC", "CODE-A", "标准 A", "NUMERIC", "GT", "1", null, null, null);
        long target = createStandard(engineer, projectId, "PQC", "CODE-B", "标准 B",
            "NUMERIC", "GT", "1", null, null, null);

        mvc.perform(put("/api/standards/{id}", target).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "CODE-A", "标准 B",
                    "NUMERIC", "GT", "1", null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40008));

        assertThat(jdbc.queryForObject("SELECT item_code FROM inspection_standard WHERE id = ?",
            String.class, target)).isEqualTo("CODE-B");
    }

    @Test
    void closedProjectRejectsStandardWrites() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-CLS-STD", "关闭标准项目", "ACTIVE");
        long standardId = createStandard(engineer, projectId, "PQC", "CLS-1", "关闭项",
            "NUMERIC", "GT", "1", null, null, null);

        // 项目更新是整体提交，body 需带 projectCode/projectName/status。
        mvc.perform(put("/api/projects/{id}", projectId).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectCode", "PV-CLS-STD", "projectName", "关闭标准项目", "status", "CLOSED"))))
           .andExpect(status().isOk());

        mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "CLS-2", "关闭后新增",
                    "NUMERIC", "GT", "1", null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40005))
           .andExpect(jsonPath("$.message", containsString("项目已关闭")));

        mvc.perform(put("/api/standards/{id}", standardId).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "CLS-1", "关闭后修改",
                    "NUMERIC", "GT", "2", null, null, null)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40005));

        mvc.perform(patch("/api/standards/{id}/enabled", standardId)
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content("{\"isEnabled\":false}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40005));

        // 重新开启项目后恢复可写。
        mvc.perform(put("/api/projects/{id}", projectId).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "projectCode", "PV-CLS-STD", "projectName", "关闭标准项目", "status", "ACTIVE"))))
           .andExpect(status().isOk());
        mvc.perform(patch("/api/standards/{id}/enabled", standardId)
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content("{\"isEnabled\":false}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.isEnabled").value(false));
    }

    @Test
    void listFiltersBySceneAndKeywordWithoutLeakingPastEachOther() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-FILT", "筛选项目", "ACTIVE");
        createStandard(engineer, projectId, "PQC", "PV-EL", "EL 隐裂", "NUMERIC", "GT", "100", null, null, null);
        createStandard(engineer, projectId, "OQC", "EL-APPEAR", "外观EL检测",
            "NUMERIC", "GT", "100", null, null, null);
        createStandard(engineer, projectId, "IQC", "DIM-1", "边框尺寸", "NUMERIC", "GT", "100", null, null, null);

        // 关键字 EL 同时命中 PQC 与 OQC 两行的编码；OR 未分组时 OQC 行会绕过 scene 过滤返回 2 条。
        mvc.perform(get("/api/standards").param("projectId", String.valueOf(projectId))
                .param("keyword", "EL").param("scene", "PQC")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].itemCode").value("PV-EL"));

        // 「边框」只出现在 IQC 行的 item_name，证明关键字确实搜索了名称。
        mvc.perform(get("/api/standards").param("projectId", String.valueOf(projectId))
                .param("keyword", "边框")
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].itemCode").value("DIM-1"));
    }

    @Test
    void listOrdersBySortOrderThenId() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-SORT", "排序项目", "ACTIVE");

        // 乱序创建（sortOrder 2 → 0 → 1），ID 顺序与展示顺序不同，只有按 sortOrder 排序才能通过。
        for (String[] row : new String[][] {{"S-2", "2"}, {"S-0", "0"}, {"S-1", "1"}, {"S-3A", "3"}, {"S-3B", "3"}}) {
            mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"projectId":%d,"scene":"PQC","processName":"层压","itemCode":"%s","itemName":"排序 %s",
                         "valueType":"NUMERIC","judgeType":"GT","lowerLimit":1,"isEnabled":true,"sortOrder":%s}
                        """.formatted(projectId, row[0], row[0], row[1])))
               .andExpect(status().isOk());
        }

        // 两条 sortOrder 相同（3）时按 id 升序，即创建顺序 S-3A → S-3B。
        mvc.perform(get("/api/standards").param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.records[*].itemCode")
               .value(contains("S-0", "S-1", "S-2", "S-3A", "S-3B")));
    }

    @Test
    void updateNeverMovesStandardToAnotherProject() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long home = createProject(engineer, "PV-MV-1", "原项目", "ACTIVE");
        long other = createProject(engineer, "PV-MV-2", "目标项目", "ACTIVE");
        long standardId = createStandard(engineer, home, "PQC", "MOVE", "移动项",
            "NUMERIC", "GT", "1", null, null, null);

        mvc.perform(put("/api/standards/{id}", standardId).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(other, "PQC", "MOVE", "移动项", "NUMERIC", "GT", "1", null, null, null)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.projectId").value(home));

        assertThat(jdbc.queryForObject("SELECT project_id FROM inspection_standard WHERE id = ?",
            Long.class, standardId)).isEqualTo(home);
    }

    @Test
    void patchEnabledOnlyChangesEnabled() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-EN", "启停项目", "ACTIVE");
        long standardId = createStandard(engineer, projectId, "PQC", "EN-1", "启停项",
            "NUMERIC", "RANGE", "3.0", "3.5", null, null);

        mvc.perform(patch("/api/standards/{id}/enabled", standardId)
                .header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content("{\"isEnabled\":false}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.isEnabled").value(false));

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM inspection_standard WHERE id = ? AND is_enabled = 0",
            Long.class, standardId)).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT lower_limit, upper_limit FROM inspection_standard WHERE id = ?", standardId);
        assertThat((BigDecimal) row.get("lower_limit")).isEqualByComparingTo("3.0");
        assertThat((BigDecimal) row.get("upper_limit")).isEqualByComparingTo("3.5");

        // 停用后 enabled 筛选能查到它，启用筛选查不到（BR-06 后续录入页据此隐藏）。
        mvc.perform(get("/api/standards").param("projectId", String.valueOf(projectId))
                .param("enabled", "false").header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(1))
           .andExpect(jsonPath("$.data.records[0].itemCode").value("EN-1"));
        mvc.perform(get("/api/standards").param("projectId", String.valueOf(projectId))
                .param("enabled", "true").header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void rangeStandardWithNegativeToleranceFailsValidationBeforeNormalization() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-ORD", "校验顺序项目", "ACTIVE");

        // RANGE 的规整会把 tolerance 清成 null。若校验读的是规整后的值，-1 会被静默洗掉并入库；
        // 只有「先校验原始输入、再规整」才会在配置期拒绝它。
        mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "ORD-1", "顺序项",
                    "NUMERIC", "RANGE", "3.0", "3.5", null, "-1")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001))
           .andExpect(jsonPath("$.message", containsString("公差必须大于 0")));
    }

    @Test
    void unknownStandardOrProjectReturnsNotFound() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-404", "不存在的资源", "ACTIVE");

        // 更新不存在的标准 → 404。
        mvc.perform(put("/api/standards/{id}", 987654321L).header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, "PQC", "NF-1", "不存在",
                    "NUMERIC", "GT", "1", null, null, null)))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value(40400));

        // 新增到不存在的项目 → 404，与项目模块保持一致。
        mvc.perform(post("/api/standards").header("Authorization", "Bearer " + engineer)
                .contentType(APPLICATION_JSON)
                .content(standardBody(987654321L, "PQC", "NF-2", "项目不存在",
                    "NUMERIC", "GT", "1", null, null, null)))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value(40400));
    }
}
