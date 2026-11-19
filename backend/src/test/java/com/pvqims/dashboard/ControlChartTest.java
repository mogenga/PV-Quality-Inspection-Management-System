package com.pvqims.dashboard;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 控制图、检验员工作量与批次合格率的接口测试（FR-13、CH-04、CH-06、CH-07）。
 * 测试库共享：凡计数断言都用本用例自建的 projectId / standardId 过滤，不依赖绝对总数。
 * 辅助方法与 {@link DashboardApiTest} 同形：各接口测试类自带一份，互不依赖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ControlChartTest {

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

    private long createStandard(String token, long projectId, String scene, String processName,
                                String itemCode, String itemName, String judgeType,
                                String lower, String upper, String target, String tolerance) throws Exception {
        String body = mvc.perform(post("/api/standards").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(standardBody(projectId, scene, processName, itemCode, itemName, judgeType,
                    lower, upper, target, tolerance)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private static String num(String value) {
        return value == null ? "null" : value;
    }

    private static String standardBody(long projectId, String scene, String processName, String itemCode,
                                       String itemName, String judgeType,
                                       String lower, String upper, String target, String tolerance) {
        String valueType = "PASS_FAIL".equals(judgeType) ? "TEXT" : "NUMERIC";
        return """
            {"projectId":%d,"scene":"%s","processName":"%s","itemCode":"%s","itemName":"%s","unit":"mm",
             "valueType":"%s","judgeType":"%s",
             "lowerLimit":%s,"upperLimit":%s,"targetValue":%s,"tolerance":%s,
             "isEnabled":true,"sortOrder":0}
            """.formatted(projectId, scene, processName, itemCode, itemName, valueType, judgeType,
                          num(lower), num(upper), num(target), num(tolerance));
    }

    private String recordBody(long batchId, long standardId, String measured, String defectType,
                              String defectGrade) {
        return """
            {"batchId":%d,"standardId":%d,"measuredValue":%s,"textValue":null,
             "defectType":%s,"defectGrade":%s}
            """.formatted(batchId, standardId, num(measured), jsonOrNull(defectType),
                          jsonOrNull(defectGrade));
    }

    private static String jsonOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private long createRecord(String token, long batchId, long standardId, String measured,
                              String defectType, String defectGrade) throws Exception {
        String body = mvc.perform(post("/api/inspection/records")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(recordBody(batchId, standardId, measured, defectType, defectGrade)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private void voidRecord(String adminToken, long recordId) throws Exception {
        mvc.perform(post("/api/inspection/records/{id}/void", recordId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"控制图不含作废记录\"}"))
           .andExpect(status().isOk());
    }

    @Test
    void controlLimitsEqualStandardBounds() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-CH-1", "控制图项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "CB-1");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-C1", "CH-1", "控制项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, standardId, "3.1", null, null);
        createRecord(engineer, batchId, standardId, "3.2", null, null);
        createRecord(engineer, batchId, standardId, "3.3", null, null);

        // AC-09：控制限等于标准上下限，均值即中心线，不另行计算统计控制限。
        String body = mvc.perform(get("/api/dashboard/control-chart")
                .param("standardId", String.valueOf(standardId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.itemName").value("控制项"))
           .andExpect(jsonPath("$.data.unit").value("mm"))
           .andExpect(jsonPath("$.data.ucl").value(3.5))
           .andExpect(jsonPath("$.data.lcl").value(3.0))
           .andExpect(jsonPath("$.data.cl").value(3.25))
           .andExpect(jsonPath("$.data.points.length()").value(3))
           .andExpect(jsonPath("$.data.points[0].measuredValue").value(3.1))
           .andExpect(jsonPath("$.data.points[1].measuredValue").value(3.2))
           .andExpect(jsonPath("$.data.points[2].measuredValue").value(3.3))
           .andReturn().getResponse().getContentAsString();
        // 取数按时间倒序再反转：返回给前端的是时间升序。
        List<String> times = JsonPath.read(body, "$.data.points[*].inspectTime");
        assertThat(times).isSorted();
    }

    @Test
    void stdTolLimitsAreTargetPlusMinusTolerance() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-CH-2", "标准公差控制项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "CB-2");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-C2", "CH-2", "标准公差项",
            "STD_TOL", null, null, "710", "5");
        createRecord(engineer, batchId, standardId, "710", null, null);
        createRecord(engineer, batchId, standardId, "712", null, null);

        mvc.perform(get("/api/dashboard/control-chart")
                .param("standardId", String.valueOf(standardId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.ucl").value(715))
           .andExpect(jsonPath("$.data.lcl").value(705))
           .andExpect(jsonPath("$.data.cl").value(710))
           .andExpect(jsonPath("$.data.points.length()").value(2));
    }

    @Test
    void gtStandardHasNoUpperLimit() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-CH-3", "单侧控制项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "CB-3");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-C3", "CH-3", "单侧项",
            "GT", "100", null, null, null);
        createRecord(engineer, batchId, standardId, "101", null, null);
        createRecord(engineer, batchId, standardId, "150", null, null);

        // GT 只有下限：不画上限，中心线压在下限上。
        mvc.perform(get("/api/dashboard/control-chart")
                .param("standardId", String.valueOf(standardId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.ucl").value(nullValue()))
           .andExpect(jsonPath("$.data.lcl").value(100))
           .andExpect(jsonPath("$.data.cl").value(100));
    }

    @Test
    void passFailStandardIsRejected() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-CH-4", "人工判定项目", "ACTIVE");
        long standardId = createStandard(engineer, projectId, "OQC", "包装-C4", "CH-4", "外观项",
            "PASS_FAIL", null, null, null, null);

        mvc.perform(get("/api/dashboard/control-chart")
                .param("standardId", String.valueOf(standardId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message").value(containsString("人工判定")));
    }

    @Test
    void controlChartKeepsOnlyLastThirtyPointsInAscendingOrder() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        long projectId = createProject(engineer, "PV-CH-5", "控制图截断项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "CB-5");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-C5", "CH-5", "截断项",
            "RANGE", "0", "100", null, null);
        for (int i = 1; i <= 35; i++) {
            createRecord(engineer, batchId, standardId, String.valueOf(i), null, null);
        }

        // DESC + LIMIT 30 取最近的 30 条，反转后是第 6..35 条的时间正序。
        mvc.perform(get("/api/dashboard/control-chart")
                .param("standardId", String.valueOf(standardId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.points.length()").value(30))
           .andExpect(jsonPath("$.data.points[0].measuredValue").value(6))
           .andExpect(jsonPath("$.data.points[29].measuredValue").value(35));
    }

    @Test
    void controlChartExcludesVoidedRecords() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-CH-6", "控制图作废项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "CB-6");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-C6", "CH-6", "作废项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, standardId, "3.1", null, null);
        long voided = createRecord(engineer, batchId, standardId, "3.2", null, null);
        createRecord(engineer, batchId, standardId, "3.3", null, null);
        voidRecord(admin, voided);

        mvc.perform(get("/api/dashboard/control-chart")
                .param("standardId", String.valueOf(standardId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.points.length()").value(2))
           .andExpect(jsonPath("$.data.points[0].measuredValue").value(3.1))
           .andExpect(jsonPath("$.data.points[1].measuredValue").value(3.3));
    }

    @Test
    void inspectorWorkloadCountsRecordsPerInspector() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String inspector = tokenFor("inspector", "Inspector@123");
        long projectId = createProject(engineer, "PV-CH-7", "人员工作量项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "CB-7");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-C7", "CH-7", "工作量项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, standardId, "3.1", null, null);
        createRecord(engineer, batchId, standardId, "3.2", null, null);
        for (int i = 1; i <= 3; i++) {
            createRecord(inspector, batchId, standardId, "3.3", null, null);
        }

        String body = mvc.perform(get("/api/dashboard/inspector-workload")
                .param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andReturn().getResponse().getContentAsString();
        // 按姓名找行，不依赖返回顺序与总数（共享库还有其他记录）。
        List<Map<String, Object>> rows = JsonPath.read(body, "$.data");
        Map<String, Object> inspectorRow = rows.stream()
            .filter(row -> "李检验员".equals(row.get("inspectorName")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("检验员工作量里缺少李检验员"));
        Map<String, Object> engineerRow = rows.stream()
            .filter(row -> "张工程师".equals(row.get("inspectorName")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("检验员工作量里缺少张工程师"));
        assertThat(((Number) inspectorRow.get("count")).longValue()).isEqualTo(3);
        assertThat(((Number) engineerRow.get("count")).longValue()).isEqualTo(2);
    }

    @Test
    void batchPassRateExcludesVoided() throws Exception {
        String engineer = tokenFor("engineer", "Engineer@123");
        String admin = tokenFor("admin", "Admin@123");
        long projectId = createProject(engineer, "PV-CH-8", "批次合格率项目", "ACTIVE");
        long batchId = createBatch(engineer, projectId, "CB-8");
        long standardId = createStandard(engineer, projectId, "PQC", "层压-C8", "CH-8", "合格率项",
            "RANGE", "3.0", "3.5", null, null);
        createRecord(engineer, batchId, standardId, "3.1", null, null);
        createRecord(engineer, batchId, standardId, "3.2", null, null);
        long failed = createRecord(engineer, batchId, standardId, "4.0", "CRACK", "MAJOR");

        // 作废前：2/3 -> 66.7。
        mvc.perform(get("/api/dashboard/batch-gauge")
                .param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].batchId").value(batchId))
           .andExpect(jsonPath("$.data[0].batchNo").value("CB-8"))
           .andExpect(jsonPath("$.data[0].total").value(3))
           .andExpect(jsonPath("$.data[0].passed").value(2))
           .andExpect(jsonPath("$.data[0].passRate").value(66.7));

        voidRecord(admin, failed);

        // BR-08：作废记录不参与合格率，2/2 -> 100.0。
        mvc.perform(get("/api/dashboard/batch-gauge")
                .param("projectId", String.valueOf(projectId))
                .header("Authorization", "Bearer " + engineer))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.length()").value(1))
           .andExpect(jsonPath("$.data[0].total").value(2))
           .andExpect(jsonPath("$.data[0].passed").value(2))
           .andExpect(jsonPath("$.data[0].passRate").value(100.0));
    }
}
