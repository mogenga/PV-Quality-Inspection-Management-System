# 阶段二 · 项目与批次主数据 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付项目管理（FR-03）与批次管理（FR-04）：工程师可增删改查两级主数据，检验员与管理员只读；批次受 BR-09（有记录不可删）与 BR-10（关闭项目下只读）约束；P2 页面替换占位页。

**Architecture:** 沿用阶段一的 `Controller → Service → Mapper` 分层与 `ApiResult` 信封。新增 `com.pvqims.masterdata` 特性包，项目与批次各一个 Controller/Service，写操作在方法级用 `@PreAuthorize("hasRole('ENGINEER')")` 校验（SRS 4.8 规定管理员对本模块也只读，这是与用户管理相反的方向）。删除保护依赖数据库外键（`batch.project_id`、`inspection_standard.project_id`、`inspection_record.batch_id` 均为 RESTRICT），服务层捕获 `DataIntegrityViolationException` 翻译为业务错误，避免外键异常冒成 500。前端在 P2 页做上下两段主从结构：上段项目表，点「查看批次」把选中项目交给下段批次表。

**Tech Stack:** Java 21 / Spring Boot 4.1.2 / Spring Security 7.1.2 / MyBatis-Plus 3.5.18.1 / Flyway 12.4.0 / MySQL 8.0；React 19.3.1 / TypeScript 6.0.3 / MUI 9.5.0 / React Router 8.5.1 / TanStack Query 5.107.1 / Vitest 5.1.0 + MSW 3.0.5。

**Spec:** `docs/需求规格说明书.md`（PVQIMS-SRS-001 V2.1）：4.3（FR-03/FR-04 需求与约束）、4.8（权限矩阵）、4.9（BR-09、BR-10）、5.4（project 表）、5.5（batch 表）、6.1（P2 页面）、附录 B（接口清单）。

---

## 阶段路线图

| 阶段 | 计划文件 | 交付 | 覆盖需求 |
|---|---|---|---|
| 一（已完成） | `2026-11-16-foundation-auth.md` | 骨架、登录、用户管理 | FR-01、FR-02 |
| 二（本计划） | `2026-11-17-master-data.md` | 项目与批次主数据 | FR-03、FR-04、P2 |
| 三 | `YYYY-MM-DD-standard-inspection.md` | 标准配置、录入、判定、记录查询 | FR-05~FR-09 |
| 四 | `YYYY-MM-DD-defect-loop.md` | 缺陷闭环、记录作废、CSV 导出 | FR-10~FR-12、FR-15 |
| 五 | `YYYY-MM-DD-dashboard-demo.md` | 合格率汇总、8 张图、演示数据 | FR-13、FR-14、FR-16、P6 |

---

## Global Constraints

以下约束对本计划每个任务生效，逐条照抄自 SRS 与 CLAUDE.md：

- **不开分支、不用 `git worktree`**：全部提交直接落在 `main`。
- **不新增数据库迁移**：`project`、`batch` 表已由 `V1__init_schema.sql` 建好（SRS 5.4、5.5）。V1/V2 已在开发者库执行过，**任何修改都会导致 Flyway 校验和失败**。本阶段只写 Java 代码。
- **业务表恰好 6 张**：本阶段不新增表、不新增视图。
- **接口级角色校验，不能只隐藏菜单**（BR-07、AC-07）。本模块方向与用户管理相反：**写操作仅 `ENGINEER`**，`INSPECTOR` 与 `ADMIN` 只读（SRS 4.8 权限矩阵）；**读操作对所有已登录角色开放**（附录 B 标注「全部」）。
- **BR-09**：已有检验记录的批次不得删除，拒绝删除。
- **BR-10**：项目关闭（`status = 'CLOSED'`）后其批次只读，写操作返回错误。
- **不实现 SRS 未要求的状态机**：批次状态可在 `PLANNED / PRODUCING / COMPLETED` 之间任意设置；项目 `ACTIVE ↔ CLOSED` 可互相切换。不要自创流转规则。
- 数据库 MySQL 8.0+、InnoDB、utf8mb4；时间 Asia/Shanghai；界面文案用「校验」、质量体系概念用「检验」（SRS 1.4 注）。
- 库/框架/API 用法先查 Context7 MCP，不凭记忆写。
- 提交信息格式 `type(scope): description`，type 限 `feat|fix|docs|refactor|perf|test|chore`，scope 用真实模块名，description 中文、无句号、≤50 字；末尾附 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。
- **过程留痕**：写入 `.superpowers/sdd/2026-11-17-master-data/progress.md`（该目录已 git-ignore，不入仓库）。
- **视觉令牌唯一来源**：`design/aesthetic/tokens.css`。前端不得出现令牌外的颜色/圆角/阴影，不得写裸十六进制。复用 `LabeledField`、`LabeledSelect`、`shadow.card`。
- **既有测试不得修改**：后端 43 个、前端 26 个测试原样保留且必须继续通过。

**测试环境约定**（阶段一已验证）：后端集成测试连本机 `pvqims_test` 库，类注解固定为 `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional`，测试内数据随事务回滚；种子账号 `admin/Admin@123`、`engineer/Engineer@123`、`inspector/Inspector@123`。

---

## Review Focus

SRS 没写、但用户一定会碰到并因此判定「这系统坏了」的五类情况，按可能性排序。每条都要落到拥有该代码的任务的测试里。

1. **删除仍有下级数据的项目（有批次，或有标准）→ 400 + 明确中文提示**，不能是 500 或裸外键异常。（Task 1）
2. **项目关闭后对其批次的任何写操作 → 400 + 提示；项目重新开启后恢复可写**，不能出现「关了再也改不回来」。（Task 2）
3. **批次下已有检验记录时删除 → 400 + 明确提示**（BR-09），记录不受影响。（Task 2）
4. **唯一性作用域**：项目编号全系统唯一（仅大小写不同也算重复，`utf8mb4_0900_ai_ci`）；批号仅在同一项目内唯一，不同项目可同名；关键字与状态筛选组合时不能互相绕过（OR 未分组的老问题）。（Task 1、Task 2）
5. **结束日期早于开始日期 → 400 中文提示**，而不是静默存进库。（Task 2）

---

## 文件结构（阶段二）

```
backend/src/main/java/com/pvqims/masterdata/
├── entity/Project.java, Batch.java
├── mapper/ProjectMapper.java, BatchMapper.java
├── dto/ProjectSummary.java, CreateProjectRequest.java, UpdateProjectRequest.java
│      BatchSummary.java, CreateBatchRequest.java, UpdateBatchRequest.java
├── ProjectController.java, ProjectService.java
└── BatchController.java, BatchService.java

backend/src/main/java/com/pvqims/common/exception/ErrorCode.java   （修改：新增 5 个错误码）
backend/src/test/java/com/pvqims/masterdata/ProjectApiTest.java, BatchApiTest.java

frontend/src/api/projects.ts, batches.ts                            （新建）
frontend/src/api/types.ts                                           （修改：+ 类型，PageResult 迁入）
frontend/src/api/users.ts                                           （修改：PageResult 改为从 types 导入）
frontend/src/utils/labels.ts                                        （修改：+ 项目/批次状态标签）
frontend/src/utils/labels.test.ts                                   （修改：+ 断言）
frontend/src/components/ConfirmDialog.tsx                           （新建）
frontend/src/pages/projects/ProjectBatchPage.tsx                    （新建）
frontend/src/pages/projects/ProjectFormDialog.tsx                   （新建）
frontend/src/pages/projects/BatchFormDialog.tsx                     （新建）
frontend/src/pages/projects/ProjectBatchPage.test.tsx               （新建）
frontend/src/routes/index.tsx                                       （修改：/projects 指向新页面）
frontend/src/test/handlers.ts                                       （修改：+ 项目/批次默认处理器）
```

---

### Task 1: 项目接口（FR-03）

**Files:**
- Create: `backend/src/main/java/com/pvqims/masterdata/entity/Project.java`
- Create: `backend/src/main/java/com/pvqims/masterdata/mapper/ProjectMapper.java`
- Create: `backend/src/main/java/com/pvqims/masterdata/dto/ProjectSummary.java`, `CreateProjectRequest.java`, `UpdateProjectRequest.java`
- Create: `backend/src/main/java/com/pvqims/masterdata/ProjectService.java`, `ProjectController.java`
- Modify: `backend/src/main/java/com/pvqims/common/exception/ErrorCode.java`
- Test: `backend/src/test/java/com/pvqims/masterdata/ProjectApiTest.java`

**Interfaces:**
- Consumes: `ApiResult`、`PageResult`、`BizException`、`ErrorCode`、`GlobalExceptionHandler`（阶段一，签名不变）
- Produces（Task 2 / Task 3 依赖）:
  - `class Project`：字段 `Long id; String projectCode; String projectName; String productModel; String customerName; String manufacturerName; String location; String status; String remark; LocalDateTime createdAt`，`@TableName("project")`，手写 getter/setter（与 `AppUser` 一致，不用 Lombok），`createdAt` 加 `@TableField(fill = FieldFill.INSERT)`
  - `interface ProjectMapper extends BaseMapper<Project>`，`@Mapper`
  - `record ProjectSummary(Long id, String projectCode, String projectName, String productModel, String customerName, String manufacturerName, String location, String status, String remark, LocalDateTime createdAt)`，静态工厂 `from(Project)`
  - `record CreateProjectRequest(@NotBlank @Size(max=50) String projectCode, @NotBlank @Size(max=100) String projectName, @Size(max=50) String productModel, @Size(max=100) String customerName, @Size(max=100) String manufacturerName, @Size(max=100) String location, @NotBlank @Pattern(regexp="ACTIVE|CLOSED", message="项目状态只能是 ACTIVE 或 CLOSED") String status, @Size(max=255) String remark)`
  - `record UpdateProjectRequest(...)`：字段与校验同上（更新为整体提交，编号可改）
  - `class ProjectService { PageResult<ProjectSummary> page(String keyword, String status, long page, long pageSize); ProjectSummary create(CreateProjectRequest); ProjectSummary update(Long id, UpdateProjectRequest); void delete(Long id); }`
  - REST：`GET /api/projects?keyword&status&page&pageSize`（全部角色）、`POST /api/projects`、`PUT /api/projects/{id}`、`DELETE /api/projects/{id}`（仅 ENGINEER），响应体均为 `ApiResult<...>`
  - `ErrorCode.PROJECT_CODE_EXISTS(40003, "项目编号已存在")`、`ErrorCode.PROJECT_HAS_DEPENDENTS(40006, "项目下存在批次或标准，不能删除")`

**实现要点（签名与测试之外仍需决定的部分）：**
- `page`：`LambdaQueryWrapper` 上 `and(StringUtils.hasText(keyword), w -> w.like(projectCode).or().like(projectName))` 后再 `eq(hasText(status), status)`，`orderByAsc(Project::getId)`。分组 `and(...)` 不能省——否则关键字 OR 会绕过状态过滤（用户页已有同类测试）。
- `create`：先 `exists` 查重（大小写不敏感由库排序规则保证）→ 插入；捕获 `DuplicateKeyException` 翻译为 `PROJECT_CODE_EXISTS`（并发兜底）。
- `delete`：`selectById` 为空 → `NOT_FOUND`；随后 `deleteById` 并捕获 `DataIntegrityViolationException` → `PROJECT_HAS_DEPENDENTS`。**不要包 `@Transactional`**：单语句自动提交，捕获后不会污染事务。外键 `fk_batch_project`、`fk_std_project` 均为 RESTRICT，由数据库兜底。
- `update`：整体写入（`updateById` 带全部字段），不存在 → `NOT_FOUND`；编号被改成别人的值 → 捕获 `DuplicateKeyException` 翻成 `PROJECT_CODE_EXISTS`（先查重亦可，但并发下仍需这段兜底）。

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/pvqims/masterdata/ProjectApiTest.java`：

```java
package com.pvqims.masterdata;

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
        createProject(engineer, "PV-B-1", "青海海南", "CLOSED");

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
```

- [ ] **Step 2: 运行，确认失败**

Run: `cd backend && mvn -q test -Dtest=ProjectApiTest`
Expected: 编译失败，报 `com.pvqims.masterdata` 包不存在 / `Project`、`ProjectService` 等符号缺失。

- [ ] **Step 3: 实现**

按 Interfaces 与「实现要点」创建实体、Mapper、DTO、Service、Controller，并在 `ErrorCode` 中加 `PROJECT_CODE_EXISTS(40003, "项目编号已存在")`、`PROJECT_HAS_DEPENDENTS(40006, "项目下存在批次或标准，不能删除")`。Controller 写法对齐 `UserController`：`@RestController @RequestMapping("/api/projects")`，写方法加 `@PreAuthorize("hasRole('ENGINEER')")`，`create`/`update` 入参加 `@Valid`。

- [ ] **Step 4: 运行，确认通过**

Run: `cd backend && mvn -q test -Dtest=ProjectApiTest`
Expected: 8 个用例全部 PASS。

- [ ] **Step 5: 后端全量**

Run: `cd backend && mvn clean test`
Expected: BUILD SUCCESS，43 个既有用例 + 8 个新用例全部通过。

- [ ] **Step 6: 提交**

```bash
git add backend
git commit -m "feat(masterdata): 项目主数据接口

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 批次接口（FR-04）

**Files:**
- Create: `backend/src/main/java/com/pvqims/masterdata/entity/Batch.java`
- Create: `backend/src/main/java/com/pvqims/masterdata/mapper/BatchMapper.java`
- Create: `backend/src/main/java/com/pvqims/masterdata/dto/BatchSummary.java`, `CreateBatchRequest.java`, `UpdateBatchRequest.java`
- Create: `backend/src/main/java/com/pvqims/masterdata/BatchService.java`, `BatchController.java`
- Modify: `backend/src/main/java/com/pvqims/common/exception/ErrorCode.java`
- Test: `backend/src/test/java/com/pvqims/masterdata/BatchApiTest.java`

**Interfaces:**
- Consumes: `Project`、`ProjectMapper`（Task 1）；`ApiResult`、`PageResult`、`BizException`
- Produces（Task 3 依赖）:
  - `class Batch`：字段 `Long id; Long projectId; String batchNo; Integer plannedQty; LocalDate startDate; LocalDate endDate; String status; String remark; LocalDateTime createdAt`，`@TableName("batch")`，手写 getter/setter，`createdAt` 自动填充
  - `interface BatchMapper extends BaseMapper<Batch>`
  - `record BatchSummary(Long id, Long projectId, String batchNo, Integer plannedQty, LocalDate startDate, LocalDate endDate, String status, String remark, LocalDateTime createdAt)`，静态工厂 `from(Batch)`
  - `record CreateBatchRequest(@NotNull Long projectId, @NotBlank @Size(max=50) String batchNo, @NotNull @Min(1) Integer plannedQty, LocalDate startDate, LocalDate endDate, @NotBlank @Pattern(regexp="PLANNED|PRODUCING|COMPLETED", message="批次状态只能是 PLANNED、PRODUCING 或 COMPLETED") String status, @Size(max=255) String remark)`
  - `record UpdateBatchRequest(...)`：同上但**没有 projectId**——批次不允许换项目（记录里冗余存了 project_id，换项目会让历史数据错位）
  - `class BatchService { PageResult<BatchSummary> page(Long projectId, String keyword, String status, long page, long pageSize); BatchSummary create(CreateBatchRequest); BatchSummary update(Long id, UpdateBatchRequest); void delete(Long id); }`
  - REST：`GET /api/batches?projectId&keyword&status&page&pageSize`（全部角色）、`POST /api/batches`、`PUT /api/batches/{id}`、`DELETE /api/batches/{id}`（仅 ENGINEER）
  - `ErrorCode.BATCH_NO_EXISTS(40004, "该项目下批号已存在")`、`PROJECT_CLOSED(40005, "项目已关闭，批次不可修改")`、`BATCH_HAS_RECORDS(40007, "批次下已有检验记录，不能删除")`

**实现要点：**
- `create`：`projectMapper.selectById(projectId)` 为空 → `NOT_FOUND`；`status == "CLOSED"` → `PROJECT_CLOSED`；同日/区间校验：两端非空且 `endDate.isBefore(startDate)` → `BizException(PARAM_INVALID, "结束日期不能早于开始日期")`；`exists(projectId + batchNo)` 查重 → `BATCH_NO_EXISTS`；插入并捕获 `DuplicateKeyException` → `BATCH_NO_EXISTS`。
- `update`：`selectById` 为空 → `NOT_FOUND`；加载所属项目，关闭则 `PROJECT_CLOSED`；同样的日期校验；`updateById` 写 `batchNo/plannedQty/startDate/endDate/status/remark`（`id` 之外不含 `projectId`）。
- `delete`：`selectById` 为空 → `NOT_FOUND`；项目已关闭 → `PROJECT_CLOSED`；`deleteById` 捕获 `DataIntegrityViolationException` → `BATCH_HAS_RECORDS`（唯一引用 batch 的外键是 `inspection_record.batch_id`）。
- `page`：`eq(projectId != null, ...)` + `and(hasText(keyword), w -> w.like(Batch::getBatchNo, keyword))` + `eq(hasText(status), ...)`，`orderByAsc(Batch::getId)`。注意 `and(...)` 分组。

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/pvqims/masterdata/BatchApiTest.java`：

```java
package com.pvqims.masterdata;

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
        jdbc.update("INSERT INTO batch (project_id, batch_no, planned_qty, status, created_at) "
            + "VALUES (?, ?, 100, 'PLANNED', NOW())", projectId, batchNo);
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
```

- [ ] **Step 2: 运行，确认失败**

Run: `cd backend && mvn -q test -Dtest=BatchApiTest`
Expected: 编译失败，`Batch` / `BatchService` 等符号缺失。

- [ ] **Step 3: 实现**

按 Interfaces 与「实现要点」创建代码，`ErrorCode` 补 `BATCH_NO_EXISTS(40004)`、`PROJECT_CLOSED(40005)`、`BATCH_HAS_RECORDS(40007)`。

- [ ] **Step 4: 运行，确认通过**

Run: `cd backend && mvn -q test -Dtest=BatchApiTest`
Expected: 8 个用例 PASS。

- [ ] **Step 5: 后端全量**

Run: `cd backend && mvn clean test`
Expected: BUILD SUCCESS（43 + 8 + 8 = 59 个用例）。

- [ ] **Step 6: 提交**

```bash
git add backend
git commit -m "feat(masterdata): 批次接口与关闭项目只读

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: P2 项目与批次页面

**Files:**
- Create: `frontend/src/api/projects.ts`, `frontend/src/api/batches.ts`
- Modify: `frontend/src/api/types.ts`（类型集中 + `PageResult` 迁入）
- Modify: `frontend/src/api/users.ts`（`PageResult` 改从 `./types` 导入，删除本地定义）
- Modify: `frontend/src/utils/labels.ts`、`frontend/src/utils/labels.test.ts`
- Create: `frontend/src/components/ConfirmDialog.tsx`
- Create: `frontend/src/pages/projects/ProjectBatchPage.tsx`, `ProjectFormDialog.tsx`, `BatchFormDialog.tsx`
- Modify: `frontend/src/routes/index.tsx`
- Modify: `frontend/src/test/handlers.ts`
- Test: `frontend/src/pages/projects/ProjectBatchPage.test.tsx`

**Interfaces:**
- Consumes: `apiClient`、`renderAt`、`seedSession`、`LabeledField`、`LabeledSelect`、`useAuthStore`、Task 1/2 的 REST 形状（字段名与 DTO 一致：camelCase）
- Produces:
  - `api/types.ts`：`PageResult<T>`（迁入）、`ProjectStatus = 'ACTIVE' | 'CLOSED'`、`ProjectSummary { id; projectCode; projectName; productModel: string | null; customerName: string | null; manufacturerName: string | null; location: string | null; status: ProjectStatus; remark: string | null; createdAt: string }`、`BatchStatus = 'PLANNED' | 'PRODUCING' | 'COMPLETED'`、`BatchSummary { id; projectId; batchNo; plannedQty; startDate: string | null; endDate: string | null; status: BatchStatus; remark: string | null; createdAt: string }`
  - `api/projects.ts`：`ProjectQuery { keyword?; status?: ProjectStatus; page; pageSize }`、`ProjectRequest { projectCode; projectName; productModel?; customerName?; manufacturerName?; location?; status; remark? }`、`listProjects(params): Promise<PageResult<ProjectSummary>>`、`createProject(req): Promise<ProjectSummary>`、`updateProject(id, req): Promise<ProjectSummary>`、`deleteProject(id): Promise<void>`
  - `api/batches.ts`：`BatchQuery { projectId?: number; keyword?; status?: BatchStatus; page; pageSize }`、`CreateBatchRequest { projectId; batchNo; plannedQty; startDate?; endDate?; status; remark? }`、`UpdateBatchRequest = Omit<CreateBatchRequest, 'projectId'>`、`listBatches`、`createBatch`、`updateBatch`、`deleteBatch`
  - `labels.ts`：`PROJECT_STATUS_LABELS: Record<ProjectStatus, string> = { ACTIVE: '进行中', CLOSED: '已关闭' }`、`BATCH_STATUS_LABELS: Record<BatchStatus, string> = { PLANNED: '计划', PRODUCING: '生产中', COMPLETED: '已完成' }`
  - `components/ConfirmDialog.tsx`：`{ open: boolean; title: string; content: string; onCancel: () => void; onConfirm: () => void; pending?: boolean }`，确认按钮文案「确认删除」

**页面结构（决定，不再讨论）：**

```
项目与批次                                    （h1）
共 N 个项目 · 当前显示第 x–y 条
[关键字] [项目状态]                    [新增项目]
项目表：项目编号 | 项目名称 | 组件型号 | 客户 | 状态 | 创建时间 | 操作
        操作 = 查看批次 / 编辑 / 删除（后两者仅工程师可见）
──────────────────────────────
批次 · {项目名称}                              （h2）
[批次状态]                              [新增批次]
（项目关闭时：Alert「项目已关闭，批次只读」，且新增/编辑/删除按钮 disabled）
批次表：批号 | 计划数量 | 开始日期 | 结束日期 | 状态 | 操作（编辑/删除）
TablePagination
```

**实现要点：**
- 行操作按钮的 `aria-label` 带业务键，避免跨行同名：`查看批次 ${projectCode}`、`编辑项目 ${projectCode}`、`删除项目 ${projectCode}`、`编辑批次 ${batchNo}`、`删除批次 ${batchNo}`；按钮可见文字仍是「查看批次」「编辑」「删除」。
- 项目表与批次表各一个 `useQuery`：`queryKey: ['projects', params]`、`['batches', selectedProject?.id, batchParams]`，批次查询 `enabled: selectedProject !== null`。
- 变更后 `invalidateQueries({ queryKey: ['projects'] })` / `['batches']`（前缀匹配即可）。
- 选中项目被删除后清空 `selectedProject`；选择新项目时批次筛选与页码复位。
- 筛选行沿用用户页：`<Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>`，按钮 `sx={{ marginLeft: 'auto', marginTop: '28px' }}`。**不要去掉 `useFlexGap`**（Stack 子元素选择器会重置按钮的 margin-top）。
- 只读角色（INSPECTOR / ADMIN）不渲染新增/编辑/删除按钮；查看批次对所有人可见。
- 表单弹窗用 `LabeledField` / `LabeledSelect`；可选字段提交前把空白字符串规整为 `null`；日期用 `LabeledField type="date"`，计划数量用 `type="number"` 并 `Number()` 转换。
- 只使用既有令牌与组件，不新增颜色；卡片 `Paper` 上加 `boxShadow: shadow.card`。

- [ ] **Step 1: 扩充 MSW 默认处理器**

在 `frontend/src/test/handlers.ts` 追加两组默认数据与处理器（放在现有 users 处理器之后）：

```ts
const projectRows = [
  {
    id: 1, projectCode: 'PV-2026-001', projectName: '青海格尔木 100MW',
    productModel: '710W 双玻', customerName: '国电投', manufacturerName: '晶科能源',
    location: '青海格尔木', status: 'ACTIVE', remark: null, createdAt: '2026-11-01T09:00:00',
  },
  {
    id: 2, projectCode: 'PV-2026-002', projectName: '宁夏中卫 50MW',
    productModel: '705W 单玻', customerName: '华能', manufacturerName: '隆基绿能',
    location: '宁夏中卫', status: 'CLOSED', remark: null, createdAt: '2026-11-02T09:00:00',
  },
];

const batchRows = [
  {
    id: 11, projectId: 1, batchNo: 'B-2026-001', plannedQty: 1200,
    startDate: '2026-11-01', endDate: '2026-11-10', status: 'PRODUCING',
    remark: null, createdAt: '2026-11-01T10:00:00',
  },
];

export const handlers = [
  // ...既有处理器...
  http.get('/api/projects', () =>
    HttpResponse.json({ code: 0, message: 'ok', data: { records: projectRows, total: 2, page: 1, pageSize: 10 } }),
  ),
  http.get('/api/batches', () =>
    HttpResponse.json({ code: 0, message: 'ok', data: { records: batchRows, total: 1, page: 1, pageSize: 10 } }),
  ),
];
```

- [ ] **Step 2: 写失败测试**

`frontend/src/pages/projects/ProjectBatchPage.test.tsx`（覆盖 7 个场景）：

```tsx
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it } from 'vitest';
import { routes } from '../../routes';
import { renderAt, seedSession } from '../../test/renderWithProviders';
import { server } from '../../test/server';

it('工程师看到项目列表与写操作入口', async () => {
  seedSession('ENGINEER');
  renderAt(routes, '/projects');

  const row = (await screen.findByText('青海格尔木 100MW')).closest('tr')!;
  expect(within(row).getByText('PV-2026-001')).toBeInTheDocument();
  expect(within(row).getByText('进行中')).toBeInTheDocument();
  expect(within(row).getByText('国电投')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '新增项目' })).toBeInTheDocument();
  expect(screen.getByText('共 2 个项目')).toBeInTheDocument();
});

it('检验员只读：没有增删改，但仍可查看批次', async () => {
  seedSession('INSPECTOR');
  renderAt(routes, '/projects');

  const row = (await screen.findByText('青海格尔木 100MW')).closest('tr')!;
  expect(screen.queryByRole('button', { name: '新增项目' })).not.toBeInTheDocument();
  expect(within(row).queryByRole('button', { name: /编辑项目/ })).not.toBeInTheDocument();
  expect(within(row).queryByRole('button', { name: /删除项目/ })).not.toBeInTheDocument();
  expect(within(row).getByRole('button', { name: '查看批次 PV-2026-001' })).toBeInTheDocument();
});

it('点查看批次后按该项目加载批次', async () => {
  seedSession('ENGINEER');
  const queries: URLSearchParams[] = [];
  server.use(
    http.get('/api/batches', ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json({
        code: 0, message: 'ok',
        data: {
          records: [{
            id: 11, projectId: 1, batchNo: 'B-2026-001', plannedQty: 1200,
            startDate: '2026-11-01', endDate: '2026-11-10', status: 'PRODUCING',
            remark: null, createdAt: '2026-11-01T10:00:00',
          }],
          total: 1, page: 1, pageSize: 10,
        },
      });
    }),
  );
  renderAt(routes, '/projects');

  await userEvent.click(await screen.findByRole('button', { name: '查看批次 PV-2026-001' }));

  expect(await screen.findByText('B-2026-001')).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: '批次 · 青海格尔木 100MW' })).toBeInTheDocument();
  expect(queries.at(-1)?.get('projectId')).toBe('1');
});

it('新增项目提交所需字段并刷新列表', async () => {
  seedSession('ENGINEER');
  const bodies: unknown[] = [];
  server.use(
    http.post('/api/projects', async ({ request }) => {
      bodies.push(await request.json());
      return HttpResponse.json({
        code: 0, message: 'ok',
        data: {
          id: 3, projectCode: 'PV-2026-003', projectName: '内蒙古库布齐 200MW',
          productModel: null, customerName: null, manufacturerName: null, location: null,
          status: 'ACTIVE', remark: null, createdAt: '2026-11-17T09:00:00',
        },
      });
    }),
  );
  renderAt(routes, '/projects');

  await userEvent.click(await screen.findByRole('button', { name: '新增项目' }));
  await userEvent.type(screen.getByLabelText('项目编号'), 'PV-2026-003');
  await userEvent.type(screen.getByLabelText('项目名称'), '内蒙古库布齐 200MW');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  await waitFor(() => expect(bodies).toHaveLength(1));
  expect(bodies[0]).toMatchObject({
    projectCode: 'PV-2026-003',
    projectName: '内蒙古库布齐 200MW',
    status: 'ACTIVE',
  });
});

it('关闭项目的批次区只读', async () => {
  seedSession('ENGINEER');
  server.use(
    http.get('/api/batches', () =>
      HttpResponse.json({ code: 0, message: 'ok', data: { records: [], total: 0, page: 1, pageSize: 10 } }),
    ),
  );
  renderAt(routes, '/projects');

  await userEvent.click(await screen.findByRole('button', { name: '查看批次 PV-2026-002' }));

  expect(await screen.findByText('项目已关闭，批次只读')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '新增批次' })).toBeDisabled();
});

it('新增批次携带所属项目', async () => {
  seedSession('ENGINEER');
  const bodies: unknown[] = [];
  server.use(
    http.post('/api/batches', async ({ request }) => {
      bodies.push(await request.json());
      return HttpResponse.json({
        code: 0, message: 'ok',
        data: {
          id: 12, projectId: 1, batchNo: 'B-2026-002', plannedQty: 800,
          startDate: null, endDate: null, status: 'PLANNED', remark: null, createdAt: '2026-11-17T09:00:00',
        },
      });
    }),
  );
  renderAt(routes, '/projects');

  await userEvent.click(await screen.findByRole('button', { name: '查看批次 PV-2026-001' }));
  await userEvent.click(await screen.findByRole('button', { name: '新增批次' }));
  await userEvent.type(screen.getByLabelText('批号'), 'B-2026-002');
  const qty = screen.getByLabelText('计划数量');
  await userEvent.clear(qty);
  await userEvent.type(qty, '800');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  await waitFor(() => expect(bodies).toHaveLength(1));
  expect(bodies[0]).toMatchObject({ projectId: 1, batchNo: 'B-2026-002', plannedQty: 800, status: 'PLANNED' });
});

it('删除项目需二次确认', async () => {
  seedSession('ENGINEER');
  const deleted: string[] = [];
  server.use(
    http.delete('/api/projects/:id', ({ params }) => {
      deleted.push(String(params.id));
      return HttpResponse.json({ code: 0, message: 'ok', data: null });
    }),
  );
  renderAt(routes, '/projects');

  await userEvent.click(await screen.findByRole('button', { name: '删除项目 PV-2026-002' }));
  expect(await screen.findByRole('heading', { name: '删除项目' })).toBeInTheDocument();
  expect(deleted).toHaveLength(0); // 未确认前不发请求

  await userEvent.click(screen.getByRole('button', { name: '确认删除' }));
  await waitFor(() => expect(deleted).toEqual(['2']));
});
```

- [ ] **Step 3: 运行，确认失败**

Run: `cd frontend && pnpm vitest run src/pages/projects/ProjectBatchPage.test.tsx`
Expected: 找不到模块 `pages/projects/ProjectBatchPage`。

- [ ] **Step 4: 实现**

按 Interfaces 与「页面结构」「实现要点」创建代码：`types.ts` 迁入 `PageResult` 并新增类型；`users.ts` 删除本地 `PageResult` 改从 `./types` 导入；补 `labels.ts`；写三个页面文件与 `ConfirmDialog`；`routes/index.tsx` 的 `pages` 数组移除 `projects` 项，改显式路由 `{ path: 'projects', element: <ProjectBatchPage /> }`。

- [ ] **Step 5: 补标签测试**

在 `frontend/src/utils/labels.test.ts` 追加：

```ts
it('项目与批次状态有中文标签', () => {
  expect(PROJECT_STATUS_LABELS.ACTIVE).toBe('进行中');
  expect(PROJECT_STATUS_LABELS.CLOSED).toBe('已关闭');
  expect(BATCH_STATUS_LABELS.PLANNED).toBe('计划');
  expect(BATCH_STATUS_LABELS.PRODUCING).toBe('生产中');
  expect(BATCH_STATUS_LABELS.COMPLETED).toBe('已完成');
});
```

Run: `cd frontend && pnpm vitest run src/utils/labels.test.ts`
Expected: PASS。

- [ ] **Step 6: 运行页面测试，确认通过**

Run: `cd frontend && pnpm vitest run src/pages/projects/ProjectBatchPage.test.tsx`
Expected: 7 个用例 PASS。

- [ ] **Step 7: 提交**

```bash
git add frontend
git commit -m "feat(masterdata): 项目与批次管理页面

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

- [ ] **Step 8: 在提交状态上跑前端全量闸门**

Run:
```bash
cd frontend && git status --short
pnpm vitest run
pnpm lint
pnpm exec prettier --check src
pnpm build
```
Expected: `git status` 无输出（工作区干净）；既有 26 个用例 + 新增 7 个 + 标签 1 个全部通过；lint 无输出；prettier 无差异；build 成功。**必须在提交后跑这一遍**——阶段一出现过「检查后又改文件」导致的假绿，闸门只认已提交的内容。

---

### Task 4: 文档与阶段验收

**Files:**
- Modify: `CLAUDE.md`（「已实现」补 FR-03/FR-04）
- Modify: `README.md`（已实现范围表补 P2）

**说明：** 本任务是协调性文档工作，由控制器直接执行，不再派实现者。

- [ ] **Step 1: 更新文档**

`CLAUDE.md` 的「已实现 / 未实现」两行改为：已实现「登录/登出、用户管理（FR-01、FR-02）、项目与批次（FR-03、FR-04）」；未实现清单移除「项目/批次」。
`README.md` 的已实现范围表加一行：`项目与批次 | 工程师可增删改查，检验员/管理员只读；关闭项目下批次只读`。

- [ ] **Step 2: 全量验收**

Run:
```bash
cd backend && mvn clean test
cd ../frontend && pnpm vitest run && pnpm lint && pnpm exec prettier --check src && pnpm build
```
Expected: 后端 59 个用例 BUILD SUCCESS；前端全部通过。

- [ ] **Step 3: 真实联调（人工或 curl）**

启动后端（`cd backend && mvn spring-boot:run`）与前端（`cd frontend && pnpm dev`），验证：
- engineer 登录后落地 `/projects`（`HOME_BY_ROLE` 本来就把工程师送到这里），页面显示种子项目（无则空态）
- 页面新增一个项目 → 出现在表中；新增批次 → 出现在该项目的批次区
- 用 inspector 令牌 `POST /api/projects` → 403 + 40300
- 关闭项目（编辑状态为「已关闭」）→ 批次区出现只读提示，新增批次按钮禁用
- 删除有批次的项目 → 页面提示「项目下存在批次或标准，不能删除」

- [ ] **Step 4: 提交**

```bash
git add CLAUDE.md README.md
git commit -m "docs(masterdata): 记录项目与批次阶段成果

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 阶段二验收对照

| 需求/规则 | 落点 | 证明 |
|---|---|---|
| FR-03 项目增删改查 | Task 1 | `ProjectApiTest` 8 例 |
| FR-04 批次增删改查 | Task 2 | `BatchApiTest` 8 例 |
| 4.8 工程师可写、检验员/管理员只读 | Task 1、Task 2 | `inspectorAndAdminCanReadButOnlyEngineerCanWrite`、`inspectorCannotWriteBatches`；页面按角色隐藏写按钮 |
| 项目编号全系统唯一（大小写不敏感） | Task 1 | `duplicateProjectCodeRejectedEvenWithOnlyCaseDifference` |
| 批号项目内唯一、跨项目可同名 | Task 2 | `batchNoIsUniquePerProjectButRepeatableAcrossProjects` |
| BR-09 有记录的批次不可删 | Task 2 | `batchWithRecordsCannotBeDeleted` |
| BR-10 关闭项目下批次只读 | Task 2 | `closedProjectMakesItsBatchesReadOnly`（含重新开启后恢复） |
| 删除有下级数据的项目不 500 | Task 1 | `projectWithBatchesCannotBeDeleted`、`projectWithStandardsCannotBeDeleted` |
| 起止日期顺序校验 | Task 2 | `endDateBeforeStartDateIsRejected` |
| P2 页面两级主数据与批次进度 | Task 3 | `ProjectBatchPage.test.tsx` 7 例 |
| 视觉令牌不漂移 | 全阶段 | `tokenSource.test.ts` 继续通过 |
