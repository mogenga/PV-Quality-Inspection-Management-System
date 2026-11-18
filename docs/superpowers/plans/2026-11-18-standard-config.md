# 阶段三 · 校验标准配置 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付校验标准配置（FR-05）与标准启停排序（FR-06）：工程师可为项目配置五种判定方式的检验项，启停与排序决定它是否出现在后续录入页；检验员无配置权限，管理员只读；P3 页面替换占位页。

**Architecture:** 新增 `com.pvqims.standard` 特性包，一个 `StandardController / StandardService / Mapper` 承载一条标准记录（`inspection_standard` 表）。配置期校验集中在 `StandardService.validateAndNormalize`：按判定方式检查必填数值列，再把与本判定方式无关的数值列清成 `null` 后落库，保证 P4 判定引擎拿不到过期上限。项目关闭后标准不可写，复用 `PROJECT_CLOSED` 错误码但换成本模块文案。标准**没有删除接口**——SRS 5.13 明确「本期标准只停用不删除」，`inspection_record.standard_id` 也是 RESTRICT 外键。

**Tech Stack:** Java 21 / Spring Boot 4.1.2 / Spring Security 7.1.2 / MyBatis-Plus 3.5.18.1 / Flyway 12.4.0 / MySQL 8.0；React 19.3.1 / TypeScript 6.0.3 / MUI 9.5.0 / React Router 8.5.1 / TanStack Query 5.107.1 / Vitest 5.1.0 + MSW 3.0.5。

**Spec:** `docs/需求规格说明书.md`（PVQIMS-SRS-001 V2.1）：4.4（FR-05/FR-06 需求、五种判定方式、约束）、4.8（权限矩阵）、5.6（inspection_standard 表）、6.1（P3 页面）、8（AC-01、AC-03、AC-07）、附录 A（每项目 10 条标准）、附录 B（接口清单）。

---

## 阶段路线图

| 阶段 | 计划文件 | 交付 | 覆盖需求 |
|---|---|---|---|
| 一（已完成） | `2026-11-16-foundation-auth.md` | 骨架、登录、用户管理 | FR-01、FR-02 |
| 二（已完成） | `2026-11-17-master-data.md` | 项目与批次主数据 | FR-03、FR-04、P2 |
| 三（本计划） | `2026-11-18-standard-config.md` | 标准配置、启停、排序 | FR-05、FR-06、P3 |
| 四 | `YYYY-MM-DD-inspection-entry.md` | 任务选择、录入、判定、记录查询 | FR-07~FR-09、P4 |
| 五 | `YYYY-MM-DD-defect-loop.md` | 缺陷闭环、记录作废、CSV 导出 | FR-10~FR-12、FR-15 |
| 六 | `YYYY-MM-DD-dashboard-demo.md` | 合格率汇总、8 张图、演示数据 | FR-13、FR-14、FR-16、P6 |

> 阶段二计划里把 FR-05~FR-09 合并在「三」，实际拆成两个：标准配置与录入判定是两套独立的读写模型，拆开后每个阶段都能独立验收。**AC-01（新增 RANGE 标准后录入页立即出现）本阶段只能验证前一半**——标准进入 `GET /api/standards` 与 P3 列表；「录入页出现」随 P4 落地。

---

## Global Constraints

以下约束对本计划每个任务生效，逐条照抄自 SRS 与 CLAUDE.md：

- **不开分支、不用 `git worktree`**：全部提交直接落在 `main`。
- **不新增数据库迁移**：`inspection_standard` 表已由 `V1__init_schema.sql` 建好（SRS 5.6）。V1/V2 已在开发者库执行过，**任何修改都会导致 Flyway 校验和失败**。本阶段只写 Java 代码。
- **业务表恰好 6 张**：不新增表、不新增视图。
- **不新增 `DELETE /api/standards/{id}`**：SRS 5.13「本期标准只停用不删除」，附录 B 也没有删除接口。FR-06 的停用就是删除的替代。
- **判定方式只能是这五个**：`GT / LT / RANGE / STD_TOL / PASS_FAIL`（SRS 4.4）。不要增加、不要改缩写。
- **配置期校验规则**（SRS 4.4 约束）：GT/LT/RANGE/STD_TOL 的上下限或目标值必填；`lower < upper`；`tolerance > 0`。
- **接口级角色校验，不能只隐藏菜单**（BR-07、AC-07）：写操作仅 `ENGINEER`；`INSPECTOR` 与 `ADMIN` 读接口可访问但写一律 403。
- **读接口角色**（附录 B「GET /api/standards 全部」）对全部已登录角色开放。SRS 4.8 表中检验员「无权限」、管理员「只读」指的是**配置功能**；P4 录入页由检验员执行，需要读标准。据此：**API 读开放给全部角色，P3 页面路由沿用现有菜单只对 ENGINEER / ADMIN 开放**。这是一条裁决，改动成本低。
- **BR-06 本期只落数据**：停用标准不出现在录入页——录入页还不存在，本阶段只保证 `is_enabled` 持久化且可筛选。
- **判定引擎不在本期**：SRS 7 章要求「判定规则集中在一个服务类」，那是 FR-08 的事。本期只有配置期校验，写在 `StandardService` 私有方法里；**Phase 4 落地判定时再把判定与校验一并抽成单一服务类**，本期不预先建空壳。
- 数据库 MySQL 8.0+、InnoDB、utf8mb4；时间 Asia/Shanghai；**界面文案用「校验」，质量体系概念（检验、检验员、来料检验）用「检验」**（SRS 1.4 注）。场景标签写作「来料检验 / 过程检验 / 出货检验」。
- 库/框架/API 用法先查 Context7 MCP，不凭记忆写。
- 提交信息格式 `type(scope): description`，type 限 `feat|fix|docs|refactor|perf|test|chore`，scope 用 `standard`（前端用 `standard` 亦可），description 中文、无句号、≤50 字；末尾附 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。
- **过程留痕**：写入 `.superpowers/sdd/2026-11-18-standard-config/progress.md`（该目录已 git-ignore，不入仓库）。
- **视觉令牌唯一来源**：`design/aesthetic/tokens.css`。前端不得出现令牌外的颜色/圆角/阴影，不得写裸十六进制。复用 `LabeledField`、`LabeledSelect`、`shadow.card`。
- **既有测试不得修改**：后端 65 个、前端 37 个测试原样保留且必须继续通过。

**测试环境约定**（阶段一/二已验证）：后端集成测试连本机 `pvqims_test` 库，类注解固定为 `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional`，测试内数据随事务回滚；种子账号 `admin/Admin@123`、`engineer/Engineer@123`、`inspector/Inspector@123`。

**已知陷阱**（阶段二踩过，本阶段会再遇到）：
- MyBatis-Plus 默认 `updateStrategy = NOT_NULL`，可空列必须显式 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`，否则「把旧上限清空」会静默失败。
- `LambdaQueryWrapper` 的关键字 OR 必须用 `.and(...)` 包住，否则会绕过同级的 `eq` 过滤。
- `Map.of` 不接受 null 值；构造带可空数值的请求体时用文本块 + `num(String)` 辅助函数。
- MUI `Stack` 筛选行必须带 `useFlexGap`，否则子元素选择器会重置按钮的 `marginTop`。

---

## Review Focus

SRS 没写、但用户一定会碰到并因此判定「这系统坏了」的五类情况，按可能性排序。每条都落到 Task 1 的测试里。

1. **判定方式与数值列的匹配**：GT 只填下限、LT 只填上限、RANGE 上下限都必填、STD_TOL 标准值与公差都必填；`下限 ≥ 上限` 与 `公差 ≤ 0` 必须返回 400 中文提示，而不是静默入库。→ `judgeTypeDecidesWhichNumericFieldsAreRequired`、`lowerMustBeLessThanUpper`、`toleranceMustBePositive`。
2. **无关数值列不留残值**：把一条 RANGE 标准改成 GT 后，`upper_limit / target_value / tolerance` 必须是 SQL NULL，否则 P4 会拿过期上限去判定。→ `unusedNumericFieldsAreClearedOnSave`（含建单与改单两条路径）。
3. **检验项编码的唯一作用域**：同一项目内重复 → 40008；不同项目可同编码；把编码改成项目内已有的值同样被拒。→ `itemCodeIsUniqueWithinProjectButNotAcrossProjects`、`renamingItemCodeToExistingIsRejected`。
4. **已关闭项目下标准不可写**：增、改、启停全部 400 且提示「项目已关闭，标准不可修改」；项目重新开启后恢复可写。→ `closedProjectRejectsStandardWrites`。
5. **关键字与场景/启用筛选组合不互相绕过**：关键字同时匹配编码与名称，且 OR 必须分组，否则场景过滤失效。→ `listFiltersBySceneAndKeywordWithoutLeakingPastEachOther`。

---

## 文件结构（阶段三）

```
backend/src/main/java/com/pvqims/standard/
├── entity/InspectionStandard.java
├── mapper/InspectionStandardMapper.java
├── dto/StandardSummary.java, CreateStandardRequest.java,
│      UpdateStandardRequest.java, SetEnabledRequest.java
└── StandardController.java, StandardService.java

backend/src/main/java/com/pvqims/common/exception/ErrorCode.java   （修改：新增 40008）
backend/src/test/java/com/pvqims/standard/StandardApiTest.java

frontend/src/api/standards.ts                                      （新建）
frontend/src/api/types.ts                                          （修改：+ Scene/ValueType/JudgeType/StandardSummary）
frontend/src/utils/labels.ts                                       （修改：+ SCENE_LABELS/JUDGE_TYPE_LABELS）
frontend/src/utils/labels.test.ts                                  （修改：+ 断言）
frontend/src/utils/standards.ts                                    （新建：formatStandardValue）
frontend/src/utils/standards.test.ts                               （新建）
frontend/src/pages/standards/StandardListPage.tsx                  （新建）
frontend/src/pages/standards/StandardFormDialog.tsx                （新建）
frontend/src/pages/standards/StandardListPage.test.tsx             （新建）
frontend/src/routes/index.tsx                                      （修改：/standards 指向新页面）
frontend/src/test/handlers.ts                                      （修改：+ 标准默认处理器）
```

---

### Task 1: 标准接口（FR-05 / FR-06 后端）

**Files:**
- Create: `backend/src/main/java/com/pvqims/standard/entity/InspectionStandard.java`
- Create: `backend/src/main/java/com/pvqims/standard/mapper/InspectionStandardMapper.java`
- Create: `backend/src/main/java/com/pvqims/standard/dto/StandardSummary.java`, `CreateStandardRequest.java`, `UpdateStandardRequest.java`, `SetEnabledRequest.java`
- Create: `backend/src/main/java/com/pvqims/standard/StandardService.java`, `StandardController.java`
- Modify: `backend/src/main/java/com/pvqims/common/exception/ErrorCode.java`
- Test: `backend/src/test/java/com/pvqims/standard/StandardApiTest.java`

**Interfaces:**
- Consumes: `ApiResult`、`PageResult`、`BizException`、`ErrorCode`（阶段一）；`Project`、`ProjectMapper`（阶段二，`com.pvqims.masterdata.mapper.ProjectMapper`）
- Produces（Task 2 依赖，字段名与 JSON 完全一致）:
  - `class InspectionStandard`：字段 `Long id; Long projectId; String scene; String processName; String itemCode; String itemName; String unit; String valueType; String judgeType; BigDecimal lowerLimit; BigDecimal upperLimit; BigDecimal targetValue; BigDecimal tolerance; String samplingNote; Boolean isEnabled; Integer sortOrder; String remark; LocalDateTime createdAt`，`@TableName("inspection_standard")`，手写 getter/setter（与既有实体一致，不用 Lombok），`createdAt` 加 `@TableField(fill = FieldFill.INSERT)`
  - `record StandardSummary(Long id, Long projectId, String scene, String processName, String itemCode, String itemName, String unit, String valueType, String judgeType, BigDecimal lowerLimit, BigDecimal upperLimit, BigDecimal targetValue, BigDecimal tolerance, String samplingNote, Boolean isEnabled, Integer sortOrder, String remark, LocalDateTime createdAt)`，静态工厂 `from(InspectionStandard)`
  - `record CreateStandardRequest(@NotNull Long projectId, @NotBlank @Pattern(regexp="IQC|PQC|OQC", message="场景只能是 IQC、PQC 或 OQC") String scene, @NotBlank @Size(max=50) String processName, @NotBlank @Size(max=30) String itemCode, @NotBlank @Size(max=100) String itemName, @Size(max=20) String unit, @NotBlank @Pattern(regexp="NUMERIC|TEXT", message="值类型只能是 NUMERIC 或 TEXT") String valueType, @NotBlank @Pattern(regexp="GT|LT|RANGE|STD_TOL|PASS_FAIL", message="判定方式只能是 GT、LT、RANGE、STD_TOL 或 PASS_FAIL") String judgeType, @Digits(integer=8, fraction=4, message="数值最多 8 位整数、4 位小数") BigDecimal lowerLimit, ...同注解的 upperLimit/targetValue/tolerance, @Size(max=100) String samplingNote, @NotNull Boolean isEnabled, @NotNull @Min(0) Integer sortOrder, @Size(max=255) String remark)`
  - `record UpdateStandardRequest(...)`：与 `CreateStandardRequest` **相同但去掉 `projectId`**（标准不允许换项目，同批次先例）
  - `record SetEnabledRequest(@NotNull Boolean isEnabled)`
  - `class StandardService { PageResult<StandardSummary> page(Long projectId, String scene, String keyword, Boolean enabled, long page, long pageSize); StandardSummary create(CreateStandardRequest); StandardSummary update(Long id, UpdateStandardRequest); StandardSummary setEnabled(Long id, Boolean isEnabled); }`
  - REST：`GET /api/standards?projectId&scene&keyword&enabled&page&pageSize`（全部已登录角色）、`POST /api/standards`、`PUT /api/standards/{id}`、`PATCH /api/standards/{id}/enabled`（后三者仅 ENGINEER）
  - `ErrorCode.STANDARD_ITEM_EXISTS(40008, "该项目下检验项编码已存在")`，插在 `BATCH_HAS_RECORDS` 之后

**实现要点（签名与测试之外仍需决定的部分）：**

- **实体可空列**：`unit`、`lowerLimit`、`upperLimit`、`targetValue`、`tolerance`、`samplingNote`、`remark` 七个列加 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`。其余字段（含 `isEnabled`、`sortOrder`）不加——它们永远有值，稀疏更新不成立。`insertStrategy` 不动。
- **校验顺序：先校验原始输入，再规整。** 顺序反了会让「RANGE 标准传了 tolerance=-5」被静默洗掉。
- **校验规则**（`private void validate(...)`，失败一律 `BizException(PARAM_INVALID, 中文提示)`）：
  - `GT` → `lowerLimit` 必填，提示「判定方式为 GT 时必须填写下限」
  - `LT` → `upperLimit` 必填，提示「判定方式为 LT 时必须填写上限」
  - `RANGE` → 两个都必填，提示「判定方式为 RANGE 时必须填写上下限」
  - `STD_TOL` → `targetValue`、`tolerance` 都必填，提示「判定方式为 STD_TOL 时必须填写标准值与公差」
  - 只要两个都给了：`lowerLimit.compareTo(upperLimit) >= 0` → 「下限必须小于上限」
  - 只要给了 `tolerance`：`tolerance.compareTo(BigDecimal.ZERO) <= 0` → 「公差必须大于 0」
  - 值类型一致性：`PASS_FAIL` 必须 `TEXT`（「人工判定的值类型必须是 TEXT」）；其余四种必须 `NUMERIC`（「数值型判定方式的值类型必须是 NUMERIC」）
- **规整**（`validate` 通过后）：与本判定方式无关的数值列写 `null` —— GT 保留 lower、LT 保留 upper、RANGE 两个都留、STD_TOL 留 target+tolerance、PASS_FAIL 四个全清。建议用一个私有 `record Numeric(BigDecimal lower, BigDecimal upper, BigDecimal target, BigDecimal tolerance)` 承载结果，create 与 update 共用。
- **`page`**：`LambdaQueryWrapper` 上 `.eq(projectId != null, ...)` → `.and(hasText(keyword), w -> w.like(itemCode).or().like(itemName))` → `.eq(hasText(scene), ...)` → `.eq(enabled != null, ...)` → `.orderByAsc(sortOrder).orderByAsc(id)`。分组 `and(...)` 不能省；排序用 `sortOrder` 而不是 id（FR-06 的「排序」就是这条）。
- **`create`**：`ensureProjectWritable` → `validate` → 规整 → `existsByItemCode` 预查 → `insert`，捕获 `DuplicateKeyException` 翻成 `STANDARD_ITEM_EXISTS`（并发兜底）。
- **`update`**：`getExisting` → `ensureProjectWritable(existing.getProjectId())` → 校验规整同上 → `updateById`，捕获 `DuplicateKeyException` 翻成 `STANDARD_ITEM_EXISTS`；返回 `selectById` 重新组装。
- **`setEnabled`**：`getExisting` → `ensureProjectWritable` → 用 `LambdaUpdateWrapper` 显式 `.eq(InspectionStandard::getId, id).set(InspectionStandard::getIsEnabled, isEnabled)`，SQL 里只 SET `is_enabled` 一列。**不要改回「只含 `id` + `isEnabled` 的实体 `updateById`」**：这个实体上七个可空列（`unit`、`lowerLimit`、`upperLimit`、`targetValue`、`tolerance`、`samplingNote`、`remark`）正是加了 `FieldStrategy.ALWAYS` 的列，稀疏实体会把它们一并写成 SQL NULL——停用/启用一次就抹掉标准的全部数值。`patchEnabledOnlyChangesEnabled` 钉住这条。
- **`ensureProjectWritable(Long projectId)`**：项目不存在 → `NOT_FOUND`；`"CLOSED".equals(status)` → **`new BizException(ErrorCode.PROJECT_CLOSED, "项目已关闭，标准不可修改")`**。不改 `ErrorCode.PROJECT_CLOSED` 的默认文案——那条文案属于批次。
- **`existsByItemCode(projectId, itemCode)`**：`mapper.exists(...)`，命中抛 `STANDARD_ITEM_EXISTS`。唯一索引 `uk_std_project_item` 用 `utf8mb4_0900_ai_ci`，仅大小写不同也算重复，与阶段二同理。

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/pvqims/standard/StandardApiTest.java`，骨架与辅助方法：

```java
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

    private String tokenFor(String username, String password) throws Exception { /* 同 ProjectApiTest */ }

    private long createProject(String token, String code, String status) throws Exception { /* POST /api/projects，返回 id */ }

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
```

然后是 14 个测试，名字与断言就是验收口径：

```java
    @Test
    void inspectorAndAdminCanReadButOnlyEngineerCanWrite() throws Exception {
        // GET 对三个角色都 200（附录 B「全部」）；POST/PUT/PATCH 对 inspector、admin 一律 403 + 40300
    }

    @Test
    void engineerCreatesRangeStandardAndItAppearsInList() throws Exception {
        // 建项目 → 建 RANGE 标准（lower 3.0 upper 3.5）→ GET ?projectId=... 
        // 断言 records.length == 1、judgeType=RANGE、lowerLimit=3.0、upperLimit=3.5、isEnabled=true
    }

    @Test
    void judgeTypeDecidesWhichNumericFieldsAreRequired() throws Exception {
        // GT 无下限 → 400 + message 含「下限」；LT 无上限 → 400；RANGE 只有下限 → 400；
        // STD_TOL 只有标准值 → 400 + message 含「公差」；GT 带下限 → 200
    }

    @Test
    void lowerMustBeLessThanUpper() throws Exception {
        // RANGE lower 3.5 upper 3.0 → 400，code=40001，message 含「下限必须小于上限」
        // 边界：lower == upper 同样 400
    }

    @Test
    void toleranceMustBePositive() throws Exception {
        // STD_TOL tolerance 0 → 400，message 含「公差必须大于 0」；tolerance -1 同样 400
    }

    @Test
    void valueTypeMustMatchJudgeType() throws Exception {
        // STD_TOL + TEXT → 400，message 含「NUMERIC」；PASS_FAIL + NUMERIC → 400，message 含「TEXT」
    }

    @Test
    void unusedNumericFieldsAreClearedOnSave() throws Exception {
        // 建单：RANGE 同时带 target 9.9 与 tolerance 1.1 → 库里 target_value、tolerance 为 NULL
        // 改单：PUT 同一标准为 GT（upper 5.0、target 9.9、tolerance 1.1 一起送）
        //      → 库里 upper_limit、target_value、tolerance 全 NULL，lower_limit 保留
    }

    @Test
    void itemCodeIsUniqueWithinProjectButNotAcrossProjects() throws Exception {
        // 两个项目各建同编码 → 都 200；同项目再建同编码 → 400，code=40008
    }

    @Test
    void renamingItemCodeToExistingIsRejected() throws Exception {
        // 同项目 A、B 两条标准；PUT B 的 itemCode=A → 400 + 40008
    }

    @Test
    void closedProjectRejectsStandardWrites() throws Exception {
        // 建 ACTIVE 项目与一条标准 → PUT /api/projects/{id} 改 CLOSED（项目更新是整体提交，body 需带 projectCode/projectName/status）
        // POST 新标准 → 400，message 含「项目已关闭」；PUT 该标准 → 400；PATCH enabled → 400
        // PUT 项目改回 ACTIVE → PATCH enabled 200
    }

    @Test
    void listFiltersBySceneAndKeywordWithoutLeakingPastEachOther() throws Exception {
        // 项目内三条：PQC 行 item_code=PV-EL；OQC 行 item_code=APPEAR、item_name=外观EL检测；IQC 行 item_name=边框尺寸
        // GET ?keyword=EL&scene=PQC → total 1，只有 PV-EL
        // 未分组时 OR 会放过 OQC 那行（命中 item_name）→ 返回 2，测试即失败；
        // 同时这条也证明关键字确实搜索了 item_name，而不只是 item_code
    }

    @Test
    void listOrdersBySortOrderThenId() throws Exception {
        // 建三条 sortOrder 2 / 0 / 1（按乱序创建）→ records[*].itemCode 依次是 0、1、2 那条
    }

    @Test
    void updateNeverMovesStandardToAnotherProject() throws Exception {
        // PUT body 里额外塞 "projectId": 另一个项目 → 库里 project_id 不变
    }

    @Test
    void patchEnabledOnlyChangesEnabled() throws Exception {
        // 建 RANGE 标准 → PATCH {"isEnabled": false} → is_enabled=0 且 lower_limit/upper_limit 未变
    }
```

`listFiltersBySceneAndKeywordWithoutLeakingPastEachOther` 的判据要能区分分组与不分组：关键字 `EL` 必须同时命中一条 PQC 行的 `item_code` 和一条 OQC 行的 `item_code`，这样未分组时会返回 2 条。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q -Dtest=StandardApiTest test`
Expected: 编译失败（`com.pvqims.standard` 包不存在）

- [ ] **Step 3: 实现实体、Mapper、DTO 与 ErrorCode**

按 Interfaces 段的字段与注解逐个写。`InspectionStandardMapper` 为 `@Mapper interface ... extends BaseMapper<InspectionStandard>`。`ErrorCode` 新增一行 `STANDARD_ITEM_EXISTS(40008, "该项目下检验项编码已存在"),`。

- [ ] **Step 4: 实现 StandardService 与 StandardController**

按「实现要点」写。控制器类注释写明「全部角色可查询，仅工程师可写（SRS 4.8、FR-05/FR-06）」。

- [ ] **Step 5: 跑测试确认通过**

Run: `cd backend && mvn -q -Dtest=StandardApiTest test`
Expected: 14 个测试全绿

- [ ] **Step 6: 跑全量后端测试**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS，测试数 = 既有 65 + 新增 14

- [ ] **Step 7: 自证两条关键测试不是空转**

临时把 `page` 里的 `.and(...)` 去掉跑 `listFiltersBySceneAndKeywordWithoutLeakingPastEachOther`，应看到 `expected 1 but was 2`；还原。临时把实体上 `lowerLimit` 的 `FieldStrategy.ALWAYS` 去掉跑 `unusedNumericFieldsAreClearedOnSave`，应失败；还原。**两条都还原后重跑整类。**

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pvqims/standard backend/src/main/java/com/pvqims/common/exception/ErrorCode.java backend/src/test/java/com/pvqims/standard
git commit -m "feat(standard): 标准配置接口与启停排序

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: P3 校验标准配置页面

**Files:**
- Create: `frontend/src/api/standards.ts`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/utils/labels.ts`、`frontend/src/utils/labels.test.ts`
- Create: `frontend/src/utils/standards.ts`、`frontend/src/utils/standards.test.ts`
- Create: `frontend/src/pages/standards/StandardListPage.tsx`、`StandardFormDialog.tsx`
- Create: `frontend/src/pages/standards/StandardListPage.test.tsx`
- Modify: `frontend/src/routes/index.tsx`
- Modify: `frontend/src/test/handlers.ts`

**Interfaces:**
- Consumes: `apiClient`、`renderAt`、`seedSession`、`LabeledField`、`LabeledSelect`、`useAuthStore`；`GET /api/projects`（阶段二）；Task 1 的 REST 形状（camelCase 与 DTO 一致）
- Produces:
  - `api/types.ts` 追加：`Scene = 'IQC' | 'PQC' | 'OQC'`、`ValueType = 'NUMERIC' | 'TEXT'`、`JudgeType = 'GT' | 'LT' | 'RANGE' | 'STD_TOL' | 'PASS_FAIL'`、`StandardSummary { id: number; projectId: number; scene: Scene; processName: string; itemCode: string; itemName: string; unit: string | null; valueType: ValueType; judgeType: JudgeType; lowerLimit: number | null; upperLimit: number | null; targetValue: number | null; tolerance: number | null; samplingNote: string | null; isEnabled: boolean; sortOrder: number; remark: string | null; createdAt: string }`
  - `api/standards.ts`：`StandardQuery { projectId?: number; scene?: Scene; keyword?: string; enabled?: boolean; page: number; pageSize: number }`、`CreateStandardRequest { projectId: number; scene: Scene; processName: string; itemCode: string; itemName: string; unit?: string | null; valueType: ValueType; judgeType: JudgeType; lowerLimit?: number | null; upperLimit?: number | null; targetValue?: number | null; tolerance?: number | null; samplingNote?: string | null; isEnabled: boolean; sortOrder: number; remark?: string | null }`、`UpdateStandardRequest = Omit<CreateStandardRequest, 'projectId'>`、`listStandards`、`createStandard`、`updateStandard`、`setStandardEnabled(id: number, isEnabled: boolean): Promise<StandardSummary>`（PATCH `/standards/${id}/enabled`，体 `{ isEnabled }`）
  - `utils/labels.ts`：`SCENE_LABELS = { IQC: '来料检验', PQC: '过程检验', OQC: '出货检验' }`、`JUDGE_TYPE_LABELS = { GT: '大于', LT: '小于', RANGE: '区间', STD_TOL: '标准值±公差', PASS_FAIL: '人工判定' }`
  - `utils/standards.ts`：`formatStandardValue(std: Pick<StandardSummary, 'judgeType' | 'lowerLimit' | 'upperLimit' | 'targetValue' | 'tolerance' | 'unit'>): string`
  - 路由：`/standards` → `<RequireRole roles={['ENGINEER', 'ADMIN']}><StandardListPage /></RequireRole>`

**页面结构（决定，不再讨论）：**

```
校验标准配置                                  （h1）
[项目▾] [场景▾] [关键字] [状态▾]        [新增标准]
标准表：检验项编码 | 检验项名称 | 场景 | 工序 | 判定方式 | 标准值 | 排序 | 状态 | 操作
        操作 = 编辑 / 停用|启用（仅工程师）
TablePagination
```

**实现要点：**

- **项目为空时**：`listProjects({ page: 1, pageSize: 100 })` 返回 0 条 → 只显示 `Alert severity="info"`「还没有项目，请先在『项目与批次』中创建项目」，不渲染筛选行与表格。有项目时默认选第一条，之后用户改动才覆盖。
- **筛选行**：沿用既有页面结构 `<Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}>`，按钮 `sx={{ marginLeft: 'auto', marginTop: '28px' }}`。**不要去掉 `useFlexGap`。**
- **状态筛选**用 `LabeledSelect`，选项 `''(全部) / 'true'(启用) / 'false'(停用)`，转成 `enabled?: boolean` 时 `value === '' ? undefined : value === 'true'`。
- **`formatStandardValue`**：GT → `> {lower}{unit}`；LT → `< {upper}{unit}`；RANGE → `{lower} – {upper}{unit}`（两侧一个半角空格，用 en dash `–`）；STD_TOL → `{target} ± {tolerance}{unit}`；PASS_FAIL → `—`。数字用 `String(Number(v))` 去掉 `3.0000` 这类尾随零；`unit` 非空时前缀一个空格。任一必需数值缺失则返回 `—`。
- **表单弹窗** `StandardFormDialog({ projectId, standard, onClose })`：编辑时预填，提交 `UpdateStandardRequest`（**不含 projectId**）；新建时带 `projectId`。
  - 字段顺序：检验项编码、检验项名称、场景、工序、判定方式、判定方式相关数值、单位、抽样说明、排序、启用状态、备注。
  - `valueType` **由判定方式推导**，不单独给控件：`PASS_FAIL` → `'TEXT'`，其余 → `'NUMERIC'`。后端仍会校验一致性。
  - 数值字段按判定方式条件渲染，提交时**同样按判定方式归零**（GT/RANGE 才送 lower，LT/RANGE 才送 upper，STD_TOL 才送 target 与 tolerance，其余显式送 `null`）。用户先填了上限再改成 GT，提交体里不能残留上限。
  - 数字输入用 `LabeledField type="number"`，提交时 `blank → null`、否则 `Number(value)`；`sortOrder` 同理但 `blank → 0`。
  - 启用状态用 `LabeledSelect`，选项 `'true'(启用) / 'false'(停用)`，提交时转布尔。
  - 后端 400 的提示显示在弹窗内 `Alert`，不外弹。
- **停用/启用按钮**：行内 `Button`，`aria-label` 带业务键——`停用标准 ${itemCode}` / `启用标准 ${itemCode}`；点击直接 `setStandardEnabled(id, !isEnabled)`，成功后 `invalidateQueries({ queryKey: ['standards'] })`；失败用页面顶部 `Alert` 展示错误。只读角色不渲染编辑与启停按钮。
- **状态列**用 `Chip`：启用（`color="success"`）/ 停用（`color="default"`）。

- [ ] **Step 1: 写 `formatStandardValue` 与标签的失败测试**

`frontend/src/utils/standards.test.ts`：

```ts
import { expect, it } from 'vitest';
import { formatStandardValue } from './standards';

const base = { unit: 'mm', lowerLimit: null, upperLimit: null, targetValue: null, tolerance: null };

it('按判定方式拼出可读的标准值', () => {
  expect(formatStandardValue({ ...base, judgeType: 'GT', lowerLimit: 100, unit: 'MΩ' })).toBe('> 100 MΩ');
  expect(formatStandardValue({ ...base, judgeType: 'LT', upperLimit: 3, unit: '%' })).toBe('< 3 %');
  expect(formatStandardValue({ ...base, judgeType: 'RANGE', lowerLimit: 3, upperLimit: 3.5 })).toBe('3 – 3.5 mm');
  expect(formatStandardValue({ ...base, judgeType: 'STD_TOL', targetValue: 710, tolerance: 5, unit: 'W' })).toBe(
    '710 ± 5 W',
  );
  expect(formatStandardValue({ ...base, judgeType: 'PASS_FAIL', unit: null })).toBe('—');
});

it('去掉尾随零且无单位时不留空格', () => {
  expect(formatStandardValue({ ...base, judgeType: 'GT', lowerLimit: 93.0, unit: '%' })).toBe('> 93 %');
  expect(formatStandardValue({ ...base, judgeType: 'RANGE', lowerLimit: 3, upperLimit: 3.5, unit: null })).toBe(
    '3 – 3.5',
  );
});

it('必需数值缺失时返回占位符', () => {
  expect(formatStandardValue({ ...base, judgeType: 'RANGE', lowerLimit: 3 })).toBe('—');
});
```

`labels.test.ts` 追加：

```ts
it('场景与判定方式有中文标签', () => {
  expect(SCENE_LABELS.IQC).toBe('来料检验');
  expect(SCENE_LABELS.PQC).toBe('过程检验');
  expect(SCENE_LABELS.OQC).toBe('出货检验');
  expect(JUDGE_TYPE_LABELS.STD_TOL).toBe('标准值±公差');
  expect(JUDGE_TYPE_LABELS.PASS_FAIL).toBe('人工判定');
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && pnpm vitest run src/utils/standards.test.ts src/utils/labels.test.ts`
Expected: FAIL（`./standards` 不存在、`SCENE_LABELS` 未导出）

- [ ] **Step 3: 补齐类型、标签与格式化函数**

按 Interfaces 段实现 `api/types.ts` 的类型、`utils/labels.ts` 的两张标签表、`utils/standards.ts` 的格式化函数。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && pnpm vitest run src/utils/standards.test.ts src/utils/labels.test.ts`
Expected: PASS

- [ ] **Step 5: 写 `api/standards.ts` 并补 MSW 默认处理器**

`frontend/src/test/handlers.ts` 追加 `standardRows`（三条覆盖 RANGE / STD_TOL / PASS_FAIL，且最后一条 `isEnabled: false`，`projectId` 均为 1）与 `http.get('/api/standards', ...)`；`DELETE`/`PATCH` 不设默认，需要时在测试里 `server.use`。

- [ ] **Step 6: 写页面的失败测试**

`frontend/src/pages/standards/StandardListPage.test.tsx`，8 个用例：

```ts
it('工程师看到标准列表与新增入口', ...)          // 行内出现「组件功率」「710 ± 5 W」这类格式化文本、新增按钮存在
it('管理员只读没有编辑与启停按钮', ...)          // seedSession('ADMIN')，queryByRole 编辑/停用均为 null
it('没有项目时提示先去创建项目', ...)            // server.use 让 /api/projects 返回空 records
it('切换项目后按新项目查询', ...)                // 记录 searchParams.projectId
it('新增区间标准的提交体只含上下限', ...)        // 深比较 reqbody：RANGE 时 targetValue/tolerance 为 null
it('停用标准发出 PATCH 且只带 isEnabled', ...)   // server.use 捕获 PATCH，断言体 { isEnabled: false }
it('后端拒绝下限不小于上限时在弹窗内显示提示', ...) // server.use 返回 400 + message，断言弹窗内 Alert 文案
it('场景筛选把 scene 传给接口', ...)             // 选「过程检验」后断言 searchParams.scene === 'PQC'
```

- [ ] **Step 7: 跑页面测试确认失败**

Run: `cd frontend && pnpm vitest run src/pages/standards`
Expected: FAIL（页面模块不存在）

- [ ] **Step 8: 实现页面、弹窗与路由**

按「页面结构」与「实现要点」写 `StandardListPage.tsx`、`StandardFormDialog.tsx`；把 `routes/index.tsx` 的 `pages` 数组里的 `standards` 一行删掉，改为带 `RequireRole` 的独立路由。

- [ ] **Step 9: 跑页面测试确认通过**

Run: `cd frontend && pnpm vitest run src/pages/standards`
Expected: 8 个测试全绿

- [ ] **Step 10: 跑全量前端校验**

Run: `cd frontend && pnpm vitest run && pnpm lint && pnpm exec prettier --check src && pnpm build`
Expected: 测试数 = 既有 37 + 新增（3 + 1 + 8），lint / prettier / build 全部通过

- [ ] **Step 11: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/api/standards.ts frontend/src/utils/labels.ts \
        frontend/src/utils/labels.test.ts frontend/src/utils/standards.ts frontend/src/utils/standards.test.ts \
        frontend/src/pages/standards frontend/src/routes/index.tsx frontend/src/test/handlers.ts
git commit -m "feat(standard): 校验标准配置页面

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: 文档与阶段验收

**Files:**
- Modify: `CLAUDE.md`（已实现/未实现两行）
- Modify: `README.md`（P3 行、测试计数）

- [ ] **Step 1: 更新 `CLAUDE.md` 项目现状**

第 7 行的阶段描述加上阶段三；「已实现」补 `校验标准配置（FR-05、FR-06）`；「未实现」去掉标准配置。保持既有句式，不重写段落。

- [ ] **Step 2: 更新 `README.md`**

- 「已实现范围」标题行改为「阶段一至阶段三交付（登录/登出、用户管理、项目与批次、校验标准）」
- P3 行改为「已实现（工程师可增删改查与启停，管理员只读）」
- 测试计数按 Step 3 的实际结果填写

- [ ] **Step 3: 跑全量测试并记录真实计数**

Run: `cd backend && mvn test`
Run: `cd frontend && pnpm vitest run`
把两边的实际数字写进 `README.md`，不要估算。

- [ ] **Step 4: 端到端验收（后端已在运行的前提下）**

先登录拿令牌，再给开发库中状态为 `ACTIVE` 的项目灌 10 条标准（IQC 3 / PQC 4 / OQC 3，覆盖五种判定方式），然后逐项核对：

1. `GET /api/standards?projectId={activeId}` 返回 10 条，按 `sortOrder` 升序
2. RANGE 标准传 `lowerLimit=3.5, upperLimit=3.0` → 400，提示含「下限必须小于上限」
3. STD_TOL 传 `tolerance=0` → 400，提示含「公差必须大于 0」
4. 同项目重复 `itemCode` → 400 + `40008`；另一个项目用同编码 → 200
5. `PATCH /api/standards/{id}/enabled` `{"isEnabled": false}` → 200，`enabled=false` 筛选能查到它
6. 对已关闭项目 `POST` 标准 → 400，提示「项目已关闭，标准不可修改」
7. `inspector` 令牌 `POST` → 403 + `40300`；`GET` → 200
8. 某条 RANGE 标准改成 GT 后，`GET` 回来的 `upperLimit` 为 `null`

curl 带中文 JSON 在 Windows shell 会乱码：把请求体写进 UTF-8 文件，用 `--data-binary @file` 加 `Content-Type: application/json; charset=utf-8`。

- [ ] **Step 5: 前端走查**

`pnpm dev` 后在浏览器里用 `engineer` 打开 `/standards`：新建一条 RANGE 标准 → 列表出现「3 – 3.5 mm」；停用后状态变「停用」；切到另一个项目列表跟着变；换 `admin` 登录 → 无新增/编辑/启停按钮。

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs(standard): 记录校验标准阶段成果

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 阶段三验收对照

| 验收项 | 本阶段覆盖 | 说明 |
|---|---|---|
| AC-01 标准可配置 | 部分 | 新增 RANGE 标准后出现在 `GET /api/standards` 与 P3 列表；「录入页立即出现」随 P4 |
| AC-03 判定方式覆盖 | 部分 | 五种 judge_type 的配置期校验与持久化全部覆盖；判定计算随 P4 |
| AC-07 权限隔离 | 是 | 检验员 POST/PUT/PATCH 标准一律 403（`inspectorAndAdminCanReadButOnlyEngineerCanWrite`） |
| BR-06 停用标准不可新增记录 | 数据层 | `is_enabled` 持久化且可筛选；录入页随 P4 隐藏 |
| FR-05 标准配置 | 是 | 列表/新增/修改，配置期校验与数值列规整 |
| FR-06 启停与排序 | 是 | `PATCH .../enabled`；`sortOrder` 决定列表顺序 |

## 留给 Phase 4 的问题（本阶段不做）

- 判定服务类（SRS 7 章「判定规则集中在一个服务类」）与五种判定方式的求值
- `GET /api/inspection/tasks` 只返回启用标准
- 记录保存标准快照（BR-02）
