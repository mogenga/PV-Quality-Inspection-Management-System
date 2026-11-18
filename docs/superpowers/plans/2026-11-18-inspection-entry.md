# 阶段四 · 校验录入与判定 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付校验任务选择（FR-07）、录入与自动判定（FR-08）、记录查询（FR-09）：检验员选批次与场景后看到待检项，逐项录入并由服务端判定；判定 FAIL 时同事务生成缺陷单；P4 页面替换占位页。

**Architecture:** 新增 `com.pvqims.inspection` 特性包。判定规则抽成单一 `JudgmentRules` 组件，同时承载**配置期规整/校验**与**运行期求值**——阶段三把这两件事分放在 `StandardService` 里，本阶段按 SRS 7 章「判定规则集中在一个服务类」合并，`StandardService` 改为委托，既有 18 条标准测试作为行为不变的证据。录入走一个 `@Transactional` 方法：读标准 → 校验启停与项目归属 → 复制快照 → 服务端求值 → FAIL 时写 defect，任何一步失败整体回滚。

**Tech Stack:** Java 21 / Spring Boot 4.1.2 / Spring Security 7.1.2 / MyBatis-Plus 3.5.18.1 / Flyway 12.4.0 / MySQL 8.0；React 19.3.1 / TypeScript 6.0.3 / MUI 9.5.0 / React Router 8.5.1 / TanStack Query 5.107.1 / Vitest 5.1.0 + MSW 3.0.5。

**Spec:** `docs/需求规格说明书.md`（PVQIMS-SRS-001 V2.1）：3.4（录入时序）、3.6（设计原因）、4.5（FR-07/FR-08）、4.8（权限矩阵）、4.9（BR-01~BR-09）、5.7（inspection_record 表）、5.8（defect 表）、5.10（关键查询）、5.12（枚举字典）、6.2（P4 交互要求）、8（AC-01~AC-04、AC-08）、附录 B（接口清单）。

---

## 阶段路线图

| 阶段 | 计划文件 | 交付 | 覆盖需求 |
|---|---|---|---|
| 一（已完成） | `2026-11-16-foundation-auth.md` | 骨架、登录、用户管理 | FR-01、FR-02 |
| 二（已完成） | `2026-11-17-master-data.md` | 项目与批次主数据 | FR-03、FR-04、P2 |
| 三（已完成） | `2026-11-18-standard-config.md` | 标准配置、启停、排序 | FR-05、FR-06、P3 |
| 四（本计划） | `2026-11-18-inspection-entry.md` | 任务选择、录入判定、记录查询 | FR-07~FR-09、P4 |
| 五 | `YYYY-MM-DD-defect-loop.md` | 缺陷闭环、记录作废、CSV 导出 | FR-10~FR-12、FR-15、P5 |
| 六 | `YYYY-MM-DD-dashboard-demo.md` | 合格率汇总、8 张图、演示数据 | FR-13、FR-14、FR-16、P6 |

---

## Global Constraints

以下约束对本计划每个任务生效，逐条照抄自 SRS 与 CLAUDE.md：

- **不开分支、不用 `git worktree`**：全部提交直接落在 `main`。
- **不新增数据库迁移**：`inspection_record`、`defect` 两表与两个视图已由 `V1__init_schema.sql` 建好（SRS 5.7、5.8、5.11）。V1/V2 校验和已冻结，**任何修改都会导致 Flyway 失败**。
- **业务表恰好 6 张**：不新增表、不新增视图。
- **判定只在服务端执行**（BR-01）：请求体里**不得**接受前端传来的 `result`；服务端收到也必须忽略。前端的实时判定走预览接口，结果只用于展示，保存时以服务端为准。
- **记录必须保存标准快照**（BR-02）：`std_lower / std_upper / std_target / std_tolerance / std_unit` 五列在插入时从标准复制，之后**永不更新**。改标准不回溯历史记录（AC-04）。
- **记录的项目必须与批次所属项目一致**（BR-03）：不一致返回 400。
- **FAIL 必须有缺陷类型与等级**（BR-04）：缺失时返回 400，且**不落库任何数据**（记录与缺陷同一事务）。
- **停用标准不可新增记录**（BR-06）：录入页不展示停用标准；直接调接口也拒绝。
- **只作废不物理删除**（BR-08）：本阶段不提供删除接口；查询排除 `is_void = 1`（本阶段该列恒为 0，阶段五启用作废）。
- **权限**（SRS 4.8）：录入接口 `ENGINEER` 与 `INSPECTOR` 可用，`ADMIN` 一律 403；查询接口对全部已登录角色开放。
- **判定方式只有五种**：`GT / LT / RANGE / STD_TOL / PASS_FAIL`（SRS 5.12）。求值规则逐条照 4.4 表：
  - `GT`：`measured > lower` 为 PASS
  - `LT`：`measured < upper` 为 PASS
  - `RANGE`：`lower <= measured <= upper` 为 PASS
  - `STD_TOL`：`|measured − target| <= tolerance` 为 PASS
  - `PASS_FAIL`：`textValue == "PASS"` 为 PASS；`"FAIL"` 为 FAIL
- **附录 B 只列了 `POST /api/inspection/records`，本阶段额外加一个 `POST /api/inspection/judge`**。理由：SRS 6.2 要求录入区「失焦即触发判定」，而 BR-01 禁止前端自己算。没有预览接口就只能等保存才知道结果。该接口只读不写，与保存走同一个 `JudgmentRules`，不会产生第二套判定逻辑。
- **数值比较必须用 `BigDecimal.compareTo`**，不要用 `equals`（`3.0` 与 `3.00` 在 `equals` 下不等）。
- **缺陷初始状态 `OPEN`**，`handler_id`、`handle_note`、`resolved_at` 一律为 NULL（SRS 5.8）。状态机属阶段五，本阶段只负责生成。
- 数据库 MySQL 8.0+、InnoDB、utf8mb4；时间 Asia/Shanghai；界面文案用「校验」，质量体系概念（检验、检验员、来料检验）用「检验」。
- 库/框架/API 用法先查 Context7 MCP，不凭记忆写。
- 提交信息格式 `type(scope): description`，type 限 `feat|fix|docs|refactor|perf|test|chore`，scope 用 `inspection`（前端同），description 中文、无句号、≤50 字；末尾附 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。
- **过程留痕**：写入 `.superpowers/sdd/2026-11-18-inspection-entry/progress.md`（该目录已 git-ignore）。
- **视觉令牌唯一来源**：`design/aesthetic/tokens.css`。前端不得出现令牌外的颜色/圆角/阴影，不得写裸十六进制。复用 `LabeledField`、`LabeledSelect`、`FormErrorSnackbar`、`findMissing`/`missingMessage`、`shadow.card`。
- **既有测试不得修改**：后端 83 个、前端 54 个测试原样保留且必须继续通过。特别是阶段三的 18 条 `StandardApiTest` —— 本阶段会重构 `StandardService` 的校验/规整，这些测试**一个字都不改**，它们是否继续通过就是重构是否行为不变的证据。

**测试环境约定**（阶段一至三已验证）：后端集成测试连本机 `pvqims_test` 库，类注解固定为 `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional`；种子账号 `admin/Admin@123`、`engineer/Engineer@123`、`inspector/Inspector@123`。

**已知陷阱**（前三个阶段踩过）：
- 实体可空列必须 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`，否则清空字段静默失败；`insertStrategy` 不动。
- 稀疏实体 `updateById` 会把所有 ALWAYS 列写成 NULL——只想改一列时用 `LambdaUpdateWrapper.set(...)`。
- `LambdaQueryWrapper` 的关键字 OR 必须 `.and(...)` 包住，否则绕过同级 `eq`。
- `Map.of` 不接受 null；构造带可空数值的请求体用文本块 + `num(String)` 辅助。
- MUI `Stack` 筛选行必须带 `useFlexGap`。
- 数字输入要 `step="any"`，否则浏览器原生校验拦住小数。

---

## Review Focus

SRS 没写、但用户一定会碰到并因此判定「这系统坏了」的五类情况，按可能性排序。每条都落到拥有该代码的任务的测试里。

1. **判定的边界值**：`RANGE` 的上下限本身应判 PASS（闭区间，SRS 4.4 写的是 `下限 ≤ 实测值 ≤ 上限`）；`GT`/`LT` 是严格不等（`>`、`<`），恰好等于边界判 FAIL；`STD_TOL` 的 `|实测−目标| == 公差` 应为 PASS（`≤`）。→ Task 1 的 `rangeBoundariesAreInclusiveButGtLtAreStrict`、`stdTolBoundaryIsInclusive`。
2. **判定 FAIL 但没填缺陷信息 → 400 且什么都没存**：记录、缺陷都不得残留（同事务回滚，BR-04）。→ Task 2 的 `failWithoutDefectInfoSavesNothing`。
3. **改标准不影响历史记录**（AC-04）：先录一条 PASS，再把标准下限改到会让它 FAIL 的值，重新查询记录，快照与判定结果都不变。→ Task 2 的 `snapshotSurvivesStandardEdit`。
4. **标准与批次不属于同一项目 → 400**（BR-03、AC-08），且不落库。→ Task 2 的 `standardFromAnotherProjectIsRejected`。
5. **停用标准不能新增记录**（BR-06）：录入页不返回它，直接 POST 也拒绝。→ Task 2 的 `disabledStandardIsRejectedOnCreate` 与 Task 4 的页面不显示停用项。

---

## 文件结构（阶段四）

```
backend/src/main/java/com/pvqims/inspection/
├── JudgmentRules.java                       （判定与规整的唯一归属）
├── entity/InspectionRecord.java, Defect.java
├── mapper/InspectionRecordMapper.java, DefectMapper.java
├── dto/StandardTask.java, JudgeRequest.java, JudgeResult.java,
│      CreateRecordRequest.java, InspectionRecordSummary.java
└── InspectionController.java, InspectionService.java

backend/src/main/java/com/pvqims/standard/StandardService.java   （修改：委托 JudgmentRules）
backend/src/main/java/com/pvqims/common/exception/ErrorCode.java （修改：新增 3 个错误码）
backend/src/test/java/com/pvqims/inspection/JudgmentRulesTest.java
backend/src/test/java/com/pvqims/inspection/InspectionApiTest.java

frontend/src/api/inspection.ts                                  （新建）
frontend/src/api/types.ts                                       （修改：+ 记录/任务类型）
frontend/src/utils/labels.ts                                    （修改：+ RESULT_LABELS/DEFECT_* 标签）
frontend/src/utils/labels.test.ts                               （修改：+ 断言）
frontend/src/pages/inspection/InspectionPage.tsx                （新建）
frontend/src/pages/inspection/InspectionPage.test.tsx           （新建）
frontend/src/routes/index.tsx                                   （修改：/inspection 指向新页面）
frontend/src/test/handlers.ts                                   （修改：+ 任务/记录默认处理器）
```

---

### Task 1: 判定规则服务与标准校验合并

**Files:**
- Create: `backend/src/main/java/com/pvqims/inspection/JudgmentRules.java`
- Modify: `backend/src/main/java/com/pvqims/standard/StandardService.java`（把 `validate`/`normalize`/`Numeric` 改为委托）
- Test: `backend/src/test/java/com/pvqims/inspection/JudgmentRulesTest.java`

**Interfaces:**
- Consumes: `BizException`、`ErrorCode`、`BigDecimal`
- Produces（Task 2 依赖）:
  - `@Component class JudgmentRules`，包含：
    - `public record Numeric(BigDecimal lower, BigDecimal upper, BigDecimal target, BigDecimal tolerance)`
    - `public Numeric normalize(String judgeType, BigDecimal lower, BigDecimal upper, BigDecimal target, BigDecimal tolerance)` —— 按判定方式保留相关列、其余置 null
    - `public void validateNumeric(String judgeType, BigDecimal lower, BigDecimal upper, BigDecimal target, BigDecimal tolerance)` —— 必填、`lower < upper`、`tolerance > 0`，失败抛 `BizException(PARAM_INVALID, 中文提示)`
    - `public String evaluate(String judgeType, BigDecimal measured, String textValue, Numeric standard)` —— 返回 `"PASS"` 或 `"FAIL"`
  - `StandardService` 的公开签名**不变**，内部改为调用 `judgmentRules.normalize/validateNumeric`；`validate` 里的值类型一致性检查留在 `StandardService`（那是配置期规则，与求值无关）

**实现要点：**
- `evaluate` 按 Global Constraints 的五条规则逐条实现。数值比较一律 `compareTo`。
- `RANGE` 用 `measured.compareTo(lower) >= 0 && measured.compareTo(upper) <= 0`。
- `STD_TOL` 用 `measured.subtract(target).abs().compareTo(tolerance) <= 0`。
- `PASS_FAIL` 只认 `"PASS"` 为合格；其余（含 null、`"FAIL"`）一律 FAIL —— 宁可判严不判松。
- 求值入参的 `measured` 对 `PASS_FAIL` 可为 null；数值类判定若 `measured` 为 null，抛 `BizException(PARAM_INVALID, "数值型检验项必须填写实测值")`。
- `normalize` 与 `validateNumeric` 的**逻辑从 `StandardService` 原样搬来**，含全部中文提示原文，只把 `Numeric` 从 Service 内部类改成 `JudgmentRules` 的公有 record。搬完 `StandardService` 里不得残留第二个 switch。

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/pvqims/inspection/JudgmentRulesTest.java`（纯单元测试，不起 Spring 容器）：

```java
package com.pvqims.inspection;

import java.math.BigDecimal;

import com.pvqims.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class JudgmentRulesTest {

    private final JudgmentRules rules = new JudgmentRules();

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void gtPassesOnlyAboveLower() {
        JudgmentRules.Numeric std = new JudgmentRules.Numeric(d("100"), null, null, null);
        assertThat(rules.evaluate("GT", d("100.0001"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("GT", d("100"), null, std)).isEqualTo("FAIL");
        assertThat(rules.evaluate("GT", d("99.99"), null, std)).isEqualTo("FAIL");
    }

    @Test
    void ltPassesOnlyBelowUpper() {
        JudgmentRules.Numeric std = new JudgmentRules.Numeric(null, d("3"), null, null);
        assertThat(rules.evaluate("LT", d("2.99"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("LT", d("3"), null, std)).isEqualTo("FAIL");
    }

    @Test
    void rangeBoundariesAreInclusive() {
        JudgmentRules.Numeric std = new JudgmentRules.Numeric(d("3.0"), d("3.5"), null, null);
        assertThat(rules.evaluate("RANGE", d("3.0"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("RANGE", d("3.5"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("RANGE", d("3.25"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("RANGE", d("3.51"), null, std)).isEqualTo("FAIL");
        assertThat(rules.evaluate("RANGE", d("2.99"), null, std)).isEqualTo("FAIL");
    }

    @Test
    void stdTolBoundaryIsInclusive() {
        JudgmentRules.Numeric std = new JudgmentRules.Numeric(null, null, d("710"), d("5"));
        assertThat(rules.evaluate("STD_TOL", d("715"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("STD_TOL", d("705"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("STD_TOL", d("710"), null, std)).isEqualTo("PASS");
        assertThat(rules.evaluate("STD_TOL", d("715.01"), null, std)).isEqualTo("FAIL");
        assertThat(rules.evaluate("STD_TOL", d("704.99"), null, std)).isEqualTo("FAIL");
    }

    @Test
    void passFailTrustsTextValue() {
        assertThat(rules.evaluate("PASS_FAIL", null, "PASS", JudgmentRules.Numeric.empty())).isEqualTo("PASS");
        assertThat(rules.evaluate("PASS_FAIL", null, "FAIL", JudgmentRules.Numeric.empty())).isEqualTo("FAIL");
        // 文字结论缺失时不能当成合格
        assertThat(rules.evaluate("PASS_FAIL", null, null, JudgmentRules.Numeric.empty())).isEqualTo("FAIL");
        assertThat(rules.evaluate("PASS_FAIL", null, "随便写的", JudgmentRules.Numeric.empty())).isEqualTo("FAIL");
    }

    @Test
    void numericJudgeTypesRequireMeasuredValue() {
        JudgmentRules.Numeric std = new JudgmentRules.Numeric(d("1"), null, null, null);
        assertThatThrownBy(() -> rules.evaluate("GT", null, null, std))
            .isInstanceOf(BizException.class)
            .hasMessage("数值型检验项必须填写实测值");
    }

    @Test
    void normalizeKeepsOnlyColumnsRelevantToJudgeType() {
        assertThat(rules.normalize("GT", d("1"), d("2"), d("3"), d("4")))
            .isEqualTo(new JudgmentRules.Numeric(d("1"), null, null, null));
        assertThat(rules.normalize("LT", d("1"), d("2"), d("3"), d("4")))
            .isEqualTo(new JudgmentRules.Numeric(null, d("2"), null, null));
        assertThat(rules.normalize("RANGE", d("1"), d("2"), d("3"), d("4")))
            .isEqualTo(new JudgmentRules.Numeric(d("1"), d("2"), null, null));
        assertThat(rules.normalize("STD_TOL", d("1"), d("2"), d("3"), d("4")))
            .isEqualTo(new JudgmentRules.Numeric(null, null, d("3"), d("4")));
        assertThat(rules.normalize("PASS_FAIL", d("1"), d("2"), d("3"), d("4")))
            .isEqualTo(JudgmentRules.Numeric.empty());
    }

    @Test
    void validateNumericRejectsMissingAndInvertedRanges() {
        assertThatThrownBy(() -> rules.validateNumeric("GT", null, null, null, null))
            .hasMessage("判定方式为 GT 时必须填写下限");
        assertThatThrownBy(() -> rules.validateNumeric("RANGE", d("3.5"), d("3.0"), null, null))
            .hasMessage("下限必须小于上限");
        assertThatThrownBy(() -> rules.validateNumeric("STD_TOL", null, null, d("710"), d("0")))
            .hasMessage("公差必须大于 0");
        // 合法输入不抛
        rules.validateNumeric("RANGE", d("3.0"), d("3.5"), null, null);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q -Dtest=JudgmentRulesTest test`
Expected: 编译失败（`com.pvqims.inspection` 不存在）

- [ ] **Step 3: 实现 `JudgmentRules`**

按 Interfaces 段写。`Numeric` 提供一个静态工厂 `public static Numeric empty()` 返回四列全 null。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q -Dtest=JudgmentRulesTest test`
Expected: 8 个测试全绿

- [ ] **Step 5: 把 `StandardService` 改为委托**

`StandardService` 注入 `JudgmentRules`。删除它自己的 `normalize`、`Numeric` record，以及 `validate` 里的数值列规则部分（判定方式必填、`lower < upper`、`tolerance > 0`），改为调用 `judgmentRules.validateNumeric(...)` 与 `judgmentRules.normalize(...)`。`validate` 中**保留**值类型一致性检查（`PASS_FAIL` → TEXT、其余 → NUMERIC），它不属于判定求值。

- [ ] **Step 6: 确认标准测试一字未改且仍全绿**

Run: `cd backend && git diff --stat src/test/java/com/pvqims/standard/` —— 应无输出（测试文件未改）。
Run: `cd backend && mvn test`
Expected: BUILD SUCCESS，测试数 = 既有 83 + 新增 8

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pvqims/inspection/JudgmentRules.java \
        backend/src/main/java/com/pvqims/standard/StandardService.java \
        backend/src/test/java/com/pvqims/inspection/JudgmentRulesTest.java
git commit -m "feat(inspection): 判定规则服务与标准校验合并

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 待检项与录入（FR-07 / FR-08）

**Files:**
- Create: `backend/src/main/java/com/pvqims/inspection/entity/InspectionRecord.java`、`Defect.java`
- Create: `backend/src/main/java/com/pvqims/inspection/mapper/InspectionRecordMapper.java`、`DefectMapper.java`
- Create: `backend/src/main/java/com/pvqims/inspection/dto/StandardTask.java`、`JudgeRequest.java`、`JudgeResult.java`、`CreateRecordRequest.java`、`InspectionRecordSummary.java`
- Create: `backend/src/main/java/com/pvqims/inspection/InspectionService.java`、`InspectionController.java`
- Modify: `backend/src/main/java/com/pvqims/common/exception/ErrorCode.java`
- Test: `backend/src/test/java/com/pvqims/inspection/InspectionApiTest.java`

**Interfaces:**
- Consumes: `JudgmentRules`（Task 1）、`StandardMapper`/`BatchMapper`/`ProjectMapper`（阶段三/二）、`LoginUser`（`com.pvqims.security`）、`ApiResult`、`PageResult`
- Produces:
  - `class InspectionRecord`：字段 `Long id; Long projectId; Long batchId; Long standardId; String scene; String processName; String itemName; String serialNo; Long inspectorId; LocalDateTime inspectTime; BigDecimal measuredValue; String textValue; String result; BigDecimal stdLower; BigDecimal stdUpper; BigDecimal stdTarget; BigDecimal stdTolerance; String stdUnit; Boolean isVoid; String voidReason; String remark; LocalDateTime createdAt`，`@TableName("inspection_record")`，`createdAt` 加 `@TableField(fill = FieldFill.INSERT)`，手写 getter/setter
  - `class Defect`：字段 `Long id; Long recordId; String defectType; String defectGrade; String defectStatus; Long handlerId; String handleNote; LocalDateTime resolvedAt; LocalDateTime createdAt`，`@TableName("defect")`，`createdAt` 加 fill
  - `record StandardTask(Long standardId, String itemCode, String itemName, String unit, String valueType, String judgeType, BigDecimal lowerLimit, BigDecimal upperLimit, BigDecimal targetValue, BigDecimal tolerance, Integer sortOrder, BigDecimal lastMeasuredValue, String lastResult, LocalDateTime lastInspectTime)`
  - `record JudgeRequest(@NotNull Long standardId, BigDecimal measuredValue, @Size(max = 100) String textValue)`
  - `record JudgeResult(String result)`
  - `record CreateRecordRequest(@NotNull Long batchId, @NotNull Long standardId, BigDecimal measuredValue, @Size(max = 100) String textValue, @Size(max = 50) String serialNo, String defectType, String defectGrade, @Size(max = 255) String remark)`
  - `record InspectionRecordSummary(Long id, Long projectId, Long batchId, Long standardId, String scene, String processName, String itemName, String serialNo, Long inspectorId, LocalDateTime inspectTime, BigDecimal measuredValue, String textValue, String result, BigDecimal stdLower, BigDecimal stdUpper, BigDecimal stdTarget, BigDecimal stdTolerance, String stdUnit, Boolean isVoid, String remark, LocalDateTime createdAt)`，静态工厂 `from(InspectionRecord)`
  - `class InspectionService`：
    - `List<StandardTask> tasks(Long projectId, Long batchId, String scene, String processName)`
    - `String judge(JudgeRequest request)` —— 返回 `"PASS"` 或 `"FAIL"`，控制器包成 `JudgeResult`
    - `InspectionRecordSummary createRecord(CreateRecordRequest request, LoginUser inspector)`
  - REST：`GET /api/inspection/tasks?projectId&batchId&scene&processName`（全部登录角色）、`POST /api/inspection/judge`（响应体 `ApiResult<JudgeResult>`）、`POST /api/inspection/records`（响应体 `ApiResult<InspectionRecordSummary>`）；后两者 `@PreAuthorize("hasAnyRole('ENGINEER','INSPECTOR')")`
  - `ErrorCode` 新增三条，插在 `STANDARD_ITEM_EXISTS` 之后：
    - `STANDARD_DISABLED(40009, "该检验项已停用，不能新增记录")`
    - `RECORD_PROJECT_MISMATCH(40010, "检验标准与批次不属于同一项目")`
    - `DEFECT_INFO_REQUIRED(40011, "判定不合格时必须填写缺陷类型与等级")`

**实现要点：**
- **`tasks`**：`batchId` 必填，查批次；批次不存在 → `NOT_FOUND`。`projectId` 若传入且与批次所属项目不同 → `BizException(RECORD_PROJECT_MISMATCH)`。标准查询：`eq(projectId, batch.getProjectId())` → `eq(isEnabled, true)` → `eq(hasText(scene), scene)` → `eq(hasText(processName), processName)` → `orderByAsc(sortOrder).orderByAsc(id)`。
- **上次实测值**：一次查出该 `batchId` 下 `is_void = 0` 的全部记录（只取 `standard_id, measured_value, result, inspect_time`），在内存里按 `standardId` 分组取 `inspectTime` 最大的一条。不要逐条标准发 N+1 查询。
- **`judge`（预览）**：读标准 → 不存在 `NOT_FOUND`；停用 → `STANDARD_DISABLED`；`judgmentRules.evaluate(...)` 返回结果。**不落库**。
- **`createRecord`**（`@Transactional`）：
  1. 读批次 → 不存在 `NOT_FOUND`
  2. 读标准 → 不存在 `NOT_FOUND`；停用 → `STANDARD_DISABLED`
  3. `standard.getProjectId()` 与 `batch.getProjectId()` 不等 → `RECORD_PROJECT_MISMATCH`
  4. 项目已关闭（`batch.projectId` 查 `ProjectMapper`，`"CLOSED".equals(status)`）→ `new BizException(ErrorCode.PROJECT_CLOSED, "项目已关闭，不能录入校验记录")`
  5. `Numeric std = judgmentRules.normalize(...)` 不需要——标准里存的已经是规整过的值，直接用 `standard.getLowerLimit()` 等构造 `new JudgmentRules.Numeric(...)`
  6. `String result = judgmentRules.evaluate(standard.getJudgeType(), request.measuredValue(), request.textValue(), numeric)`
  7. `"FAIL".equals(result)` 且（`defectType` 或 `defectGrade` 为空）→ `DEFECT_INFO_REQUIRED`（此时事务内未插入任何行，天然不留残留）
  8. 组装 `InspectionRecord`：`projectId` 取 `batch.getProjectId()`；`scene/processName/itemName` 从标准复制；`inspectorId = inspector.id()`；`inspectTime = LocalDateTime.now()`；五列快照从标准复制；`isVoid = false`；插入
  9. 若 FAIL：组装 `Defect`（`recordId` = 刚插入的记录 id，`defectStatus = "OPEN"`，其余 null）并插入
  10. 返回 `InspectionRecordSummary.from(record)`
- **`inspectTime` 由服务端取当前时间**，不接受请求体传入——客户端时钟不可信。
- **请求体里即使带了 `result` 也忽略**（BR-01）：`CreateRecordRequest` 不定义该字段即可，Jackson 会静默丢弃未知字段（当前配置未开启 `FAIL_ON_UNKNOWN_PROPERTIES`）。
- 控制器类注释写明「录入仅工程师与检验员可用，管理员不可录入（SRS 4.8）」。

- [ ] **Step 1: 写失败测试**

`backend/src/test/java/com/pvqims/inspection/InspectionApiTest.java`。骨架复用 `StandardApiTest` 的辅助方法形状（`tokenFor`、`createProject`、`num`、文本块 JSON），另加：

```java
    /** 建项目 → 建批次 → 返回 batchId。 */
    private long createBatch(String token, long projectId, String batchNo) throws Exception { /* POST /api/batches，body 需 batchNo/plannedQty/status */ }

    /** 建项目 → 建标准 → 返回 standardId。 */
    private long createStandard(String token, long projectId, String scene, String processName,
                                String itemCode, String itemName, String judgeType,
                                String lower, String upper, String target, String tolerance) throws Exception { /* 同 StandardApiTest */ }
```

14 个测试，名字与断言就是验收口径：

```java
    @Test
    void tasksReturnOnlyEnabledStandardsOfTheBatchProjectInSortOrder() throws Exception {
        // 项目下建三条标准（sortOrder 2/0/1），其中一条停用
        // GET /api/inspection/tasks?projectId&batchId → 只返回两条启用的，顺序 0、1
    }

    @Test
    void tasksIncludeLastMeasuredValueOfThatBatch() throws Exception {
        // 先录一条 GT 标准 PASS 记录（实测 101），再调 tasks
        // → 该标准 lastMeasuredValue=101、lastResult=PASS；另一条没录过的是 null
    }

    @Test
    void tasksFilterBySceneAndProcessName() throws Exception {
        // 同项目 PQC/层压 与 OQC/包装 各一条 → scene=PQC 只回 1 条；processName=包装 只回 1 条
    }

    @Test
    void tasksRejectBatchFromAnotherProject() throws Exception {
        // 项目 A 的批次 + 项目 B 的 projectId → 400 + 40010
    }

    @Test
    void judgePreviewReturnsVerdictWithoutPersisting() throws Exception {
        // RANGE 3.0–3.5，实测 3.5 → PASS；实测 3.6 → FAIL
        // 两次调用后 inspection_record 表行数不变
    }

    @Test
    void adminCannotJudgeOrCreateRecords() throws Exception {
        // admin 令牌 POST /judge、POST /records → 403 + 40300
        // engineer 与 inspector 都能调
    }

    @Test
    void createRecordCopiesStandardSnapshot() throws Exception {
        // RANGE 3.0/3.5 单位 mm → 录一条 3.2
        // 断言响应与 DB 的 std_lower=3.0、std_upper=3.5、std_unit=mm、result=PASS
        // 且 project_id 来自批次、inspector_id 是当前登录用户
    }

    @Test
    void snapshotSurvivesStandardEdit() throws Exception {
        // 录一条 PASS（实测 100，GT 下限 100 不成立 → 用 RANGE 更好：先 3.0–3.5 录 3.2）
        // 把标准改成 9.0–9.5，再 GET 记录 → std_lower 仍 3.0、result 仍 PASS
    }

    @Test
    void failRecordCreatesDefectInSameTransaction() throws Exception {
        // RANGE 3.0–3.5 录 4.0，带 defectType=CRACK、defectGrade=MAJOR
        // → 响应 result=FAIL；defect 表有一条，record_id 指向该记录，defect_status=OPEN，handler_id 为 null
    }

    @Test
    void failWithoutDefectInfoSavesNothing() throws Exception {
        // RANGE 3.0–3.5 录 4.0，不带缺陷信息 → 400 + 40011
        // 断言 inspection_record 与 defect 行数均未增加
    }

    @Test
    void disabledStandardIsRejectedOnCreate() throws Exception {
        // PATCH 标准停用 → POST 记录 → 400 + 40009，且不落库
    }

    @Test
    void standardFromAnotherProjectIsRejected() throws Exception {
        // 项目 A 的批次 + 项目 B 的标准 → 400 + 40010，且不落库
    }

    @Test
    void closedProjectRejectsNewRecords() throws Exception {
        // 项目改 CLOSED → POST 记录 → 400 + 40005，message 含「项目已关闭」
    }

    @Test
    void numericJudgeTypeRequiresMeasuredValue() throws Exception {
        // GT 标准不传 measuredValue → 400，message 含「实测值」
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q -Dtest=InspectionApiTest test`
Expected: 14 个测试失败，全部是 `expected 200 but was 404`（路由不存在）

- [ ] **Step 3: 实现实体、Mapper、DTO、ErrorCode**

按 Interfaces 段写。

- [ ] **Step 4: 实现 `InspectionService` 与 `InspectionController`**

按「实现要点」写。

- [ ] **Step 5: 跑测试确认通过**

Run: `cd backend && mvn -q -Dtest=InspectionApiTest test`
Expected: 14 个测试全绿

- [ ] **Step 6: 自证两条关键测试不是空转**

临时把 `snapshotSurvivesStandardEdit` 相关代码里的快照来源改成「查询时联表取标准当前值」——测试应失败；还原。临时把 `@Transactional` 从 `createRecord` 去掉并让缺陷插入失败（例如把 `defectType` 写成一个超长字符串触发列宽错误）——`failWithoutDefectInfoSavesNothing` 之外的用例应能观察到记录残留；**验证后必须完整还原**，还原后重跑整类。

- [ ] **Step 7: 跑全量后端测试**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS，测试数 = 既有 83 + 8（Task 1）+ 14

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pvqims/inspection backend/src/main/java/com/pvqims/common/exception/ErrorCode.java backend/src/test/java/com/pvqims/inspection/InspectionApiTest.java
git commit -m "feat(inspection): 待检项与录入判定

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: 记录查询（FR-09）

**Files:**
- Modify: `backend/src/main/java/com/pvqims/inspection/InspectionService.java`、`InspectionController.java`
- Test: `backend/src/test/java/com/pvqims/inspection/InspectionApiTest.java`（追加）

**Interfaces:**
- Consumes: Task 2 的实体与 `InspectionRecordSummary`
- Produces:
  - `InspectionService.pageRecords(Long projectId, Long batchId, String scene, String processName, String result, LocalDate from, LocalDate to, long page, long pageSize)` → `PageResult<InspectionRecordSummary>`
  - REST：`GET /api/inspection/records?projectId&batchId&scene&processName&result&from&to&page&pageSize`（全部已登录角色）

**实现要点：**
- `LambdaQueryWrapper` 条件：`eq(projectId != null)` → `eq(batchId != null)` → `eq(hasText(scene))` → `eq(hasText(processName))` → `eq(hasText(result))` → `eq(isVoid, false)` → `ge(from != null, inspectTime, from.atStartOfDay())` → `le(to != null, inspectTime, to.plusDays(1).atStartOfDay())` → `orderByDesc(inspectTime).orderByDesc(id)`。
- **`is_void = 0` 是恒定的**：本阶段该列全是 0；写进查询是为了阶段五启用作废时无需再改这里。
- `to` 用 `plusDays(1).atStartOfDay()` 加 `le`，而不是 `atTime(23:59:59)`——后者会漏掉最后一秒内的记录。
- `result` 只接受 `PASS`/`FAIL`；非法值靠 `eq` 过滤成空结果即可，不必额外校验。

- [ ] **Step 1: 追加失败测试**

在 `InspectionApiTest` 追加 5 条（不改动既有 14 条）：

```java
    @Test
    void recordQueryFiltersByBatchSceneProcessAndResult() throws Exception {
        // 项目下两条标准（PQC/层压 RANGE、OQC/包装 PASS_FAIL），各录一条（一 PASS 一 FAIL）
        // batchId 过滤 → 2 条；scene=OQC → 1 条；processName=层压 → 1 条；result=FAIL → 1 条
    }

    @Test
    void recordQueryFiltersByInspectDateRange() throws Exception {
        // from/to 都填今天 → 能查到刚录的记录；from 填明天 → 查不到
    }

    @Test
    void recordQueryIsOrderedNewestFirst() throws Exception {
        // 连录两条 → records[0].id == 第二条的 id
    }

    @Test
    void recordQueryIsOpenToAllRoles() throws Exception {
        // admin、inspector、engineer 三个令牌 GET → 均 200
    }

    @Test
    void recordQueryNeverReturnsVoidedRecords() throws Exception {
        // 直接 UPDATE inspection_record SET is_void = 1（本阶段无作废接口）
        // GET → 该条不在结果里
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn -q -Dtest=InspectionApiTest test`
Expected: 新增 5 条失败（GET /api/inspection/records → 404 或 405），既有 14 条仍绿

- [ ] **Step 3: 实现 `pageRecords` 与控制器的 GET 方法**

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn -q -Dtest=InspectionApiTest test`
Expected: 19 个测试全绿

- [ ] **Step 5: 跑全量后端测试**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS，测试数 = 83 + 8 + 19

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pvqims/inspection/InspectionService.java backend/src/main/java/com/pvqims/inspection/InspectionController.java backend/src/test/java/com/pvqims/inspection/InspectionApiTest.java
git commit -m "feat(inspection): 校验记录查询与筛选

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: P4 校验录入页面

**Files:**
- Create: `frontend/src/api/inspection.ts`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/utils/labels.ts`、`frontend/src/utils/labels.test.ts`
- Create: `frontend/src/pages/inspection/InspectionPage.tsx`、`InspectionPage.test.tsx`
- Modify: `frontend/src/routes/index.tsx`
- Modify: `frontend/src/test/handlers.ts`

**Interfaces:**
- Consumes: `apiClient`、`renderAt`、`seedSession`、`LabeledSelect`、`FormErrorSnackbar`、`findMissing`、`missingMessage`、`useAuthStore`；`GET /api/projects`、`GET /api/batches`、Task 2/3 的 REST 形状
- Produces:
  - `api/types.ts` 追加：`RecordResult = 'PASS' | 'FAIL'`、`DefectGrade = 'CRITICAL' | 'MAJOR' | 'MINOR'`、`DefectType = 'CRACK' | 'SOLDER' | 'BUBBLE' | 'DELAMINATION' | 'LOW_POWER' | 'APPEARANCE' | 'OTHER'`、`StandardTask { standardId; itemCode; itemName; unit: string | null; valueType: ValueType; judgeType: JudgeType; lowerLimit: number | null; upperLimit: number | null; targetValue: number | null; tolerance: number | null; sortOrder: number; lastMeasuredValue: number | null; lastResult: RecordResult | null; lastInspectTime: string | null }`、`InspectionRecordSummary { id; projectId; batchId; standardId; scene: Scene; processName: string; itemName: string; serialNo: string | null; inspectorId: number; inspectTime: string; measuredValue: number | null; textValue: string | null; result: RecordResult; stdLower: number | null; stdUpper: number | null; stdTarget: number | null; stdTolerance: number | null; stdUnit: string | null; isVoid: boolean; remark: string | null; createdAt: string }`
  - `api/inspection.ts`：`RecordQuery { projectId?; batchId?; scene?; processName?; result?; from?; to?; page; pageSize }`、`CreateRecordRequest { batchId; standardId; measuredValue?: number | null; textValue?: string | null; serialNo?: string | null; defectType?: DefectType | null; defectGrade?: DefectGrade | null; remark?: string | null }`、`listTasks(params: { projectId: number; batchId: number; scene?: Scene; processName?: string }): Promise<StandardTask[]>`、`judge(req: { standardId: number; measuredValue?: number | null; textValue?: string | null }): Promise<{ result: RecordResult }>`、`createRecord(req): Promise<InspectionRecordSummary>`、`listRecords(params: RecordQuery): Promise<PageResult<InspectionRecordSummary>>`
  - `utils/labels.ts`：`RESULT_LABELS = { PASS: '合格', FAIL: '不合格' }`、`DEFECT_GRADE_LABELS = { CRITICAL: '致命', MAJOR: '严重', MINOR: '轻微' }`、`DEFECT_TYPE_LABELS = { CRACK: '隐裂', SOLDER: '虚焊', BUBBLE: '气泡', DELAMINATION: '脱层', LOW_POWER: '功率不足', APPEARANCE: '外观不良', OTHER: '其他' }`
  - 路由：`/inspection` → `<RequireRole roles={['ENGINEER', 'INSPECTOR']}><InspectionPage /></RequireRole>`

**页面结构（决定，不再讨论）：**

```
校验录入                                       （h1）
[项目▾] [批次▾] [场景▾] [工序▾]
（未选批次时：Alert info「请选择项目和批次」）
待检项表：检验项 | 单位 | 标准值 | 上次实测 | 实测值/结论 | 判定 | 序列号
TablePagination（仅记录查询区）
──────────────────────────────
校验记录                                       （h2）
[结果▾] [开始日期] [结束日期]
记录表：检验时间 | 批次 | 检验项 | 场景 | 实测值 | 判定 | 检验员
```

**实现要点：**
- **四级联动**：项目下拉（`listProjects pageSize 100`）→ 选项目后加载该项目批次（`listBatches projectId pageSize 100`）→ 场景下拉取 `SCENE_LABELS` → 工序下拉由 `tasks` 返回结果里的 `processName` 去重得到（先不带 `processName` 拉一次全量任务）。工序变化时重新拉任务。
- **默认带出最近一次组合**（SRS 6.2）：用 `localStorage` 存 `{projectId, batchId, scene}`，进入页面时若这些 id 仍存在于下拉数据中就恢复。读写在 `try/catch` 里，失败静默。
- **待检项行**：数值型渲染 `LabeledField type="number" step="any"`（只保留输入框，不重复标签——表格用列头）；文字型渲染两个切换按钮「合格」「不合格」。
- **失焦触发预览判定**：数值输入框 `onBlur` 调 `judge`，结果显示在「判定」列；文字型点选后直接本地得出 PASS/FAIL（无需请求，因为 `PASS_FAIL` 的判定就是所选值），但仍以保存时服务端结果为准。
- **判定列**：`Chip`，PASS 用 `color="success"`、FAIL 用 `color="error"`，文案用 `RESULT_LABELS`——颜色与文字同时表达（SRS 6.2）。
- **FAIL 时展开缺陷区**：该行下方多渲染缺陷类型与等级两个 `LabeledSelect`，两者都必填才能保存（前端用 `findMissing` 拦，后端也会拒）。
- **保存**：每行一个「保存」按钮；保存成功后 `invalidateQueries({ queryKey: ['records'] })` 并重新拉 `tasks`（更新「上次实测」）。失败用页面级 `FormErrorSnackbar`。
- **写权限**：页面路由已限 ENGINEER/INSPECTOR；不需要额外的按钮级判断。
- **记录查询区**独立 `useQuery(['records', params])`，筛选变化时页码复位。

- [ ] **Step 1: 补标签与失败测试**

`labels.test.ts` 追加：

```ts
it('判定结果与缺陷枚举有中文标签', () => {
  expect(RESULT_LABELS.PASS).toBe('合格');
  expect(RESULT_LABELS.FAIL).toBe('不合格');
  expect(DEFECT_GRADE_LABELS.CRITICAL).toBe('致命');
  expect(DEFECT_GRADE_LABELS.MAJOR).toBe('严重');
  expect(DEFECT_GRADE_LABELS.MINOR).toBe('轻微');
  expect(DEFECT_TYPE_LABELS.CRACK).toBe('隐裂');
  expect(DEFECT_TYPE_LABELS.OTHER).toBe('其他');
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && pnpm vitest run src/utils/labels.test.ts`
Expected: FAIL（`RESULT_LABELS` 未导出）

- [ ] **Step 3: 补齐类型、标签与 `api/inspection.ts`**

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && pnpm vitest run src/utils/labels.test.ts`
Expected: PASS

- [ ] **Step 5: 补 MSW 默认处理器**

`frontend/src/test/handlers.ts` 追加 `taskRows`（两条：一条 RANGE 数值型带 `lastMeasuredValue`，一条 PASS_FAIL 文字型）与 `http.get('/api/inspection/tasks', ...)`、`http.get('/api/inspection/records', ...)` 默认处理器；`POST` 不设默认，测试内 `server.use`。

- [ ] **Step 6: 写页面失败测试**

`frontend/src/pages/inspection/InspectionPage.test.tsx`，9 个用例：

```ts
it('未选批次时提示先选择项目和批次', ...)
it('选择项目与批次后列出待检项与上次实测值', ...)         // 断言「3 – 3.5 mm」「3.2」「合格」
it('数值输入失焦后显示服务端判定结果', ...)               // server.use 捕获 /judge，断言 Chip 文案「不合格」
it('判定不合格时展开缺陷类型与等级', ...)                 // 输入越界值并失焦 → 出现两个下拉
it('缺陷信息为空时保存被拦下并提示', ...)                 // 点保存 → FormErrorSnackbar 文案含「缺陷类型」
it('保存成功发出完整请求体并刷新记录', ...)               // 深比较 body：batchId/standardId/measuredValue/defectType/defectGrade
it('文字型检验项用合格与不合格按钮选择', ...)              // 点「不合格」→ 判定列显示「不合格」
it('记录区按结果筛选重新查询', ...)                       // 选「不合格」→ searchParams.result === 'FAIL'
it('页面按最近一次使用的项目批次恢复选择', ...)            // 预置 localStorage → 断言 tasks 查询参数
```

- [ ] **Step 7: 跑页面测试确认失败**

Run: `cd frontend && pnpm vitest run src/pages/inspection`
Expected: FAIL（页面模块不存在）

- [ ] **Step 8: 实现页面与路由**

按「页面结构」与「实现要点」写 `InspectionPage.tsx`；把 `routes/index.tsx` 的 `pages` 数组里 `inspection` 一行删掉，改为带 `RequireRole` 的独立路由。

- [ ] **Step 9: 跑页面测试确认通过**

Run: `cd frontend && pnpm vitest run src/pages/inspection`
Expected: 9 个测试全绿

- [ ] **Step 10: 跑全量前端校验**

Run: `cd frontend && pnpm vitest run && pnpm lint && pnpm exec prettier --check src && pnpm build`
Expected: 测试数 = 既有 54 + 新增（1 + 9），lint / prettier / build 全通过

- [ ] **Step 11: Commit**

```bash
git add frontend/src/api/inspection.ts frontend/src/api/types.ts frontend/src/utils/labels.ts \
        frontend/src/utils/labels.test.ts frontend/src/pages/inspection \
        frontend/src/routes/index.tsx frontend/src/test/handlers.ts
git commit -m "feat(inspection): 校验录入页面

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: 文档与阶段验收

**Files:**
- Modify: `CLAUDE.md`、`README.md`

- [ ] **Step 1: 更新 `CLAUDE.md`**

第 7 行阶段描述加上阶段四；「已实现」补 `校验录入与判定（FR-07~FR-09）`；「未实现」去掉校验录入与判定。保持既有句式。

- [ ] **Step 2: 更新 `README.md`**

- 「已实现范围」标题行改为「阶段一至阶段四交付（登录/登出、用户管理、项目与批次、校验标准、校验录入）」
- P4 行改为「已实现（工程师与检验员可录入，判定在服务端执行）」
- 测试计数按 Step 3 的实际结果填写

- [ ] **Step 3: 跑全量测试并记录真实计数**

Run: `cd backend && mvn test`
Run: `cd frontend && pnpm vitest run`
把实际数字写进 `README.md`，不要估算。

- [ ] **Step 4: 端到端验收（另起 8090 端口，不动正在运行的服务）**

先登录拿 `engineer` 令牌，再逐项核对：

1. `GET /api/inspection/tasks?projectId={有标准的项目}&batchId={该项目批次}` → 只返回启用标准，按 `sortOrder` 升序，含 `lastMeasuredValue`
2. `POST /api/inspection/judge` RANGE 3.0–3.5 传入 3.5 → `PASS`；传入 3.51 → `FAIL`；两次调用后 `inspection_record` 行数不变
3. `POST /api/inspection/records` 录一条 PASS → 响应里 `stdLower/stdUpper/stdUnit` 与标准一致，`inspectTime` 是当前时间
4. 把该标准上下限改掉，再 `GET /api/inspection/records` → 快照与判定**未变**
5. 录一条越界值且带 `defectType/defectGrade` → 记录 `result=FAIL`，`defect` 表新增一条 `defect_status=OPEN`
6. 录一条越界值但不带缺陷信息 → 400 + `40011`，且 `inspection_record`、`defect` 行数都不变
7. 另一个项目的标准配这个项目的批次 → 400 + `40010`
8. 标准停用后录 → 400 + `40009`
9. 项目改 CLOSED 后录 → 400 + `40005`，message 含「项目已关闭」
10. `admin` 令牌 POST `/judge` 与 `/records` → 403 + `40300`；三个角色 GET `/records` → 均 200

curl 带中文 JSON 在 Windows shell 会乱码：把请求体写进 UTF-8 文件，用 `--data-binary @file` 加 `Content-Type: application/json; charset=utf-8`。

- [ ] **Step 5: 前端走查（手动项，交由用户）**

`pnpm dev` 后用 `inspector` 登录进 `/inspection`：选项目与批次 → 待检项出现 → 输入合格值失焦显示「合格」→ 输入越界值显示「不合格」并展开缺陷区 → 填缺陷类型与等级后保存 → 记录区出现该条。

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs(inspection): 记录校验录入阶段成果

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 阶段四验收对照

| 验收项 | 本阶段覆盖 | 说明 |
|---|---|---|
| AC-01 标准可配置 | 是（闭环） | 新增 RANGE 标准后 `tasks` 立即返回它；停用的不返回 |
| AC-02 自动判定 | 是 | 录入越界值返回 FAIL 并同事务生成缺陷单（`failRecordCreatesDefectInSameTransaction`） |
| AC-03 判定方式覆盖 | 是 | 五种 judge_type 各有求值用例（Task 1 的 8 条单元测试） |
| AC-04 快照不可变 | 是 | `snapshotSurvivesStandardEdit` |
| AC-08 数据约束 | 是 | `standardFromAnotherProjectIsRejected`（BR-03） |
| BR-01 服务端判定 | 是 | 请求体无 `result` 字段；预览接口不落库 |
| BR-02 标准快照 | 是 | 五列在插入时复制，查询直接读记录列 |
| BR-04 FAIL 必带缺陷 | 是 | `failWithoutDefectInfoSavesNothing` |
| BR-06 停用标准不可录入 | 是 | `disabledStandardIsRejectedOnCreate` + 页面不展示 |
| BR-08 只作废不删除 | 数据层 | 查询恒带 `is_void = 0`；作废接口属阶段五 |

## 留给后续阶段的问题（本阶段不做）

- 缺陷状态机与处理/验证/关闭（FR-11/FR-12，阶段五）
- 记录作废接口与作废原因（FR-10，阶段五）
- CSV 导出（FR-15，阶段五）
- 批次合格率与 8 张看板图（FR-13/FR-14，阶段六）
- 「保存并继续录入」与 Enter/Ctrl+Enter 快捷键（SRS 6.2 的加分交互，本阶段先做逐行保存；若验收要求再加）
