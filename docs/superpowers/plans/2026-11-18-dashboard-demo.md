# 阶段六 · 质量看板与演示数据 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付批次合格率汇总（FR-13）、8 张质量看板图与指标卡（FR-14）、演示数据初始化（FR-16）：P6 页面替换占位页，全角色可查看板，管理员可一键生成 30 天演示数据。

**Architecture:** 新增 `com.pvqims.dashboard` 包。所有图表的取数是**只读聚合查询**，用 `@Select` + `<script>` 动态条件（与 `DefectMapper.selectDetailPage` 同一写法），不做预聚合表（SRS 4.7）。前端新增一个 40 行的 `EChart` 组件封装 `echarts/core`，**图表 option 由纯函数生成**（`options.ts`）——这样 AC-09 的「帕累托累计终点 100%」「控制限等于标准上下限」能脱离浏览器直接断言，不必在 jsdom 里渲染 canvas。演示数据走一个 ADMIN 专属的 `POST /api/demo/seed`，用固定种子的 `Random` 保证可重复。

**Tech Stack:** Java 21 / Spring Boot 4.1.2 / Spring Security 7.1.2 / MyBatis-Plus 3.5.18.1 / MySQL 8.0；React 19.3.1 / TypeScript 6.0.3 / MUI 9.5.0 / React Router 8.5.1 / TanStack Query 5.107.1 / ECharts 6.1.0 / Vitest 5.1.0 + MSW 3.0.5。

**Spec:** `docs/需求规格说明书.md`（PVQIMS-SRS-001 V2.1）：3.3（端到端流程步骤 8）、4.7（FR-13/FR-14）、4.8（看板全角色可看）、5.10（关键查询）、5.11（`v_batch_pass_rate`）、5.12（枚举字典）、6.1（P6）、6.3（8 张图的取数口径与实现要求）、8（AC-06、AC-09、AC-10）、附录 A（演示数据建议）、附录 B（9 个 dashboard 端点）。

---

## 阶段路线图

| 阶段 | 计划文件 | 交付 | 覆盖需求 |
|---|---|---|---|
| 一~三（已完成） | — | 骨架、认证、主数据、标准配置 | FR-01~FR-06、P1~P3 |
| 四（已完成） | `2026-11-18-inspection-entry.md` | 任务选择、录入判定、记录查询 | FR-07~FR-09、P4 |
| 五（已完成） | `2026-11-18-defect-loop.md` | 作废、缺陷闭环、CSV 导出 | FR-10~FR-12、FR-15、P5 |
| 六（本计划） | `2026-11-18-dashboard-demo.md` | 指标卡、8 张图、批次汇总、演示数据 | FR-13、FR-14、FR-16、P6 |

---

## Global Constraints

以下约束对本计划每个任务生效，逐条照抄自 SRS 与 CLAUDE.md：

- **不开分支、不用 `git worktree`**：全部提交直接落在 `main`。
- **不新增数据库迁移**：`inspection_record`、`defect`、两个视图与全部索引均已由 `V1__init_schema.sql` 建好，V1/V2 校验和已冻结。**演示数据不能写成 Flyway 迁移**，只能是运行期动作。
- **业务表恰好 6 张**：不新增表、不新增视图。
- **看板 8 张图用 ECharts，实时查询、不做预聚合表**（SRS 4.7、6.3）。
- **帕累托累计线终点必须为 100%**（SRS 6.3、AC-09）。
- **控制图的控制限取自标准的上下限，不另行计算**（SRS 6.3、AC-09）。
- **无数据时显示空状态提示，不显示空白坐标轴**（SRS 6.3）。
- **作废记录不参与任何统计**（BR-08、AC-06）：每一条聚合查询都必须带 `is_void = 0`。
- **看板对全部已登录角色开放**（SRS 4.8、附录 B）：`/api/dashboard/**` 不加 `@PreAuthorize`，只要求已认证。
- **演示数据初始化仅 `ADMIN`**（SRS 4.2 FR-16 角色列）：`POST /api/demo/seed` 加 `@PreAuthorize("hasRole('ADMIN')")`。
- **AC-10：初始化后有 300 条以上记录，8 张图均有数据**。
- **数值比较必须用 `BigDecimal.compareTo`**，不要用 `equals`。
- **前端图表配色只能取 `frontend/src/theme/tokens.ts` 的 `color.*`**：不得写裸十六进制、`rgb()`、`oklch()`。`tokenSource.test.ts` 会校验令牌副本与设计源字节一致。
- **复用既有组件**：`LabeledField`、`LabeledSelect`、`FormErrorSnackbar`、`shadow.card`、MUI 主题色名。`Stack` 筛选行必须带 `useFlexGap`。
- **既有测试不得削弱**：后端现有 146 个 `@Test`、前端现有 89 个 `it(...)` 的既有断言一个字都不改；只允许新增用例。
- 数据库 MySQL 8.0+、InnoDB、utf8mb4；时间 Asia/Shanghai；界面文案用「校验」，质量体系概念（检验、检验员、来料检验）用「检验」。
- 库/框架/API 用法先查 Context7 MCP，不凭记忆写。
- 提交信息格式 `type(scope): description`，type 限 `feat|fix|docs|refactor|perf|test|chore`，scope 用真实模块名（`dashboard` / `demo` / `frontend` 对应模块），description 中文、无句号、≤50 字；末尾附 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。
- **过程留痕**：写入 `.superpowers/sdd/2026-11-18-dashboard-demo/progress.md`（该目录已 git-ignore）。

**测试环境约定**（前五个阶段已验证）：后端集成测试连本机 `pvqims_test` 库，类注解固定为
`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional`；
种子账号 `admin/Admin@123`、`engineer/Engineer@123`、`inspector/Inspector@123`。
**测试库是共享的**：任何计数断言必须带本用例新建的唯一筛选值（唯一项目编号 / 唯一工序名），不能依赖绝对总数。

**已知陷阱**（前五个阶段踩过）：
- 实体可空列必须 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`；**稀疏实体 `updateById` 会把它们写成 NULL**——只改几列时用 `LambdaUpdateWrapper.set(...)`。
- `LambdaQueryWrapper` 的关键字 OR 必须 `.and(...)` 包住。
- `PaginationInnerInterceptor` 全局 `maxLimit` 是 100；需要「取前 N 条」用 `wrapper.last("LIMIT n")`。
- `@Select` 的多行 SQL 用文本块，`<script>` 必须是字符串的第一个字符；`<if>` 里 `>` `<` 要写成 `&gt;` `&lt;`。
- MyBatis 按 setter 映射，**结果 DTO 用 POJO 不用 record**（record 需要 `argNameBasedConstructorAutoMapping`，本项目未开）。
- 前端 `fetch`/`apiClient` 的响应拦截器统一解包 `res.data.data`；二进制响应用原生 `fetch`。
- 测试库连接池与共享数据：新增用例一律自建项目、批次、标准。

---

## Review Focus

SRS 没写、但用户一定会碰到并因此判定「这系统坏了」的五类情况，按可能性排序。每条都落到拥有该代码的任务的测试里。

1. **看板把作废记录算进了合格率**：管理员作废一条不合格记录后，合格率必须上升。漏掉 `is_void = 0` 会让 AC-06 静默失效。→ Task 1 的 `voidedRecordsAreExcludedFromAllAggregates`、Task 3 的 `batchPassRateExcludesVoided`。
2. **帕累托累计线终点不是 100%**：四舍五入错误或除以了总数以外的分母。AC-09 明写。→ Task 2 的 `paretoCumulativeEndsAtExactlyOneHundred`。
3. **控制图画的是标准之外的线**：SRS 要求控制限等于标准上下限，代码里另算 3σ 就会与标准矛盾。→ Task 3 的 `controlLimitsEqualStandardBounds`。
4. **演示数据把作废/历史数据搅在一起，或跑两次就重复**：重复执行会让 AC-10 的数字翻倍、图表失真。→ Task 6 的 `seedingTwiceIsRejected`。
5. **某张图没数据时渲染成一片空白坐标轴**：用户以为系统坏了。→ Task 5 的 `everyChartShowsEmptyStateWithoutData`。

补充覆盖（同属上述类目）：缺陷类图表的项目筛选要经过 `inspection_record` 关联（`defect` 表没有 project_id）；日期筛选对缺陷类图表按记录的 `inspect_time` 而非 `defect.created_at`，否则两套图的筛选口径不一致。

---

## 文件结构（阶段六）

```
backend/src/main/java/com/pvqims/dashboard/
├── DashboardQuery.java                  （新建：projectId/batchId/from/to 四参数的载体）
├── dto/DashboardDtos.java               （新建：全部图表 DTO 作为嵌套 record）
├── DashboardMapper.java                 （新建：9 个 @Select 聚合查询）
├── DashboardService.java                （新建：日期边界换算、累计百分比、控制限推导）
└── DashboardController.java             （新建：9 个 GET 端点）

backend/src/main/java/com/pvqims/demo/
├── DemoDataService.java                 （新建：固定种子生成演示数据）
└── DemoController.java                  （新建：POST /api/demo/seed）

backend/src/main/java/com/pvqims/common/exception/ErrorCode.java （修改：+ 2 个错误码）

backend/src/test/java/com/pvqims/dashboard/DashboardApiTest.java
backend/src/test/java/com/pvqims/dashboard/ControlChartTest.java
backend/src/test/java/com/pvqims/demo/DemoSeedApiTest.java

frontend/package.json                          （修改：+ echarts 6.1.0）
frontend/src/api/dashboard.ts                  （新建）
frontend/src/api/demo.ts                       （新建：seedDemoData）
frontend/src/api/types.ts                      （修改：+ 图表数据类型）
frontend/src/components/EChart.tsx             （新建：echarts/core 封装）
frontend/src/components/EChart.test.tsx        （新建：生命周期与 aria）
frontend/src/pages/dashboard/options.ts        （新建：8 个纯函数 option 生成器）
frontend/src/pages/dashboard/options.test.ts   （新建：AC-09 断言在这里）
frontend/src/pages/dashboard/DashboardPage.tsx （新建：指标卡 + 筛选 + 8 图 + 批次表）
frontend/src/pages/dashboard/DashboardPage.test.tsx
frontend/src/routes/index.tsx                  （修改：/dashboard 指向新页面）
frontend/src/test/handlers.ts                  （修改：+ dashboard 默认处理器）
```

---

### Task 1: 看板骨架、指标卡与两张记录类图表

**Files:**
- Create: `backend/src/main/java/com/pvqims/dashboard/DashboardQuery.java`
- Create: `backend/src/main/java/com/pvqims/dashboard/dto/DashboardDtos.java`
- Create: `backend/src/main/java/com/pvqims/dashboard/DashboardMapper.java`
- Create: `backend/src/main/java/com/pvqims/dashboard/DashboardService.java`
- Create: `backend/src/main/java/com/pvqims/dashboard/DashboardController.java`
- Test: `backend/src/test/java/com/pvqims/dashboard/DashboardApiTest.java`

**Interfaces:**
- Consumes: `InspectionRecordMapper`（仅用于后续任务，本任务不用）、`ErrorCode`、`PageResult` 不涉及
- Produces（后续任务依赖）:
  - `record DashboardQuery(Long projectId, Long batchId, LocalDate from, LocalDate to)`
  - `class DashboardDtos`（`public final`，私有构造器，全部为嵌套 `record`）：
    - `Summary(long totalRecords, long passRecords, double passRate, long openDefects, Double avgHandleHours)`
    - `TrendPoint(LocalDate day, long total, long passed, double passRate)`
    - `ProcessComparison(String processName, long total, long passed, double passRate)`
  - `DashboardService.summary(DashboardQuery)` → `Summary`
  - `DashboardService.passRateTrend(DashboardQuery)` → `List<TrendPoint>`
  - `DashboardService.processComparison(DashboardQuery)` → `List<ProcessComparison>`
  - `DashboardMapper.selectTrend(@Param("projectId") Long, @Param("batchId") Long, @Param("from") LocalDateTime, @Param("to") LocalDateTime)` → `List<TrendPoint>`（`TrendPoint` 需要 getter/setter，**不能是 record**）
  - API（全部已认证可访问，不加 `@PreAuthorize`）：
    - `GET /api/dashboard/summary` → `ApiResult<Summary>`
    - `GET /api/dashboard/pass-rate-trend` → `ApiResult<List<TrendPoint>>`
    - `GET /api/dashboard/process-comparison` → `ApiResult<List<ProcessComparison>>`

> **DTO 形态的决定**：`DashboardDtos` 里的类型对外（JSON）是 record，但 MyBatis 要映射的 `TrendPoint` / `ProcessComparison` 必须是 POJO 带 setter。**两种都要**：MyBatis 映射用 POJO（内部行对象），Service 再转成对外 record。因此本任务实际是两套类型——`dto/DashboardDtos.java` 放对外 record，`mapper` 里的行对象放 `DashboardMapper` 同包的 `TrendRow` / `ProcessRow` POJO。**不要试图让一个类型兼任两职**。

- [ ] **Step 1: 写失败测试**

新建 `DashboardApiTest`（辅助方法照抄 `RecordVoidApiTest` 的 `tokenFor` / `createProject` / `createBatch` / `createStandard` / `standardBody` / `recordBody` / `num` / `jsonOrNull`，并新增 `createRecord(...)` 返回 `recordId`）：

```java
@Test
void summaryCountsRecordsPassRateAndOpenDefects() {
    // 自建唯一编号项目 + 批次 + RANGE 3.0–3.5 标准
    // 录 3 条 PASS（3.1/3.2/3.3）+ 2 条 FAIL（4.0/4.1，带 CRACK/MAJOR）
    // GET /api/dashboard/summary?projectId=<本项目>
    // totalRecords=5, passRecords=3, passRate=60.0, openDefects=2, avgHandleHours=null
}

@Test
void voidedRecordsAreExcludedFromAllAggregates() {
    // 同上 5 条记录，管理员作废其中 1 条 FAIL
    // summary: totalRecords=4, passRecords=3, passRate=75.0, openDefects=1
}

@Test
void trendAggregatesByDayAndExcludesVoided() {
    // 同一天录 2 条（1 PASS 1 FAIL）-> pass-rate-trend 只有一个点：total=2 passed=1 passRate=50.0
    // 该点 day 等于今天
}

@Test
void processComparisonGroupsByProcessName() {
    // 两个工序各一条记录（层压-X PASS、包装-X FAIL）-> 两条，按 passRate 升序
}

@Test
void emptyResultReturnsEmptyListNotError() {
    // 用不存在的 projectId=999999 -> summary 全 0、两个列表为空数组、HTTP 200
}

@Test
void dashboardIsOpenToAllRoles() {
    // admin / engineer / inspector 三个令牌 GET /api/dashboard/summary 都 200
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=DashboardApiTest`
Expected: 404（端点不存在）

- [ ] **Step 3: 实现**

`DashboardQuery`：`record DashboardQuery(Long projectId, Long batchId, LocalDate from, LocalDate to)`。

`DashboardMapper`（POJO 行对象 + `@Select`）：
```java
@Select("""
    <script>
    SELECT DATE(inspect_time) AS day, COUNT(*) AS total, SUM(result = 'PASS') AS passed
    FROM inspection_record
    WHERE is_void = 0
      <if test="projectId != null">AND project_id = #{projectId}</if>
      <if test="batchId != null">AND batch_id = #{batchId}</if>
      <if test="from != null">AND inspect_time &gt;= #{from}</if>
      <if test="to != null">AND inspect_time &lt; #{to}</if>
    GROUP BY DATE(inspect_time)
    ORDER BY day
    </script>
    """)
List<TrendRow> selectTrend(@Param("projectId") Long projectId, @Param("batchId") Long batchId,
                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
```
`TrendRow` 字段：`LocalDate day; long total; long passed;`（MySQL `DATE()` 返回 `java.sql.Date`，MyBatis 能映射到 `LocalDate`；`SUM()` 可能为 null，用 `Long` 承接后在 Service 里 `null → 0`）。
`ProcessRow` 字段：`String processName; long total; long passed;`，SQL 同形但 `GROUP BY process_name ORDER BY passed / total`。

`DashboardService`：
- 私有 `LocalDateTime fromOf(DashboardQuery)` / `toOf(...)`：`from.atStartOfDay()`、`to.plusDays(1).atStartOfDay()`（与 `InspectionService.pageRecords` 同一闭区间口径）。
- `summary`：内部调 `selectTrend` 求和 `total`/`passed`，再单独查 `openDefects`（`defect` 联 `inspection_record`，`is_void = 0 AND defect_status <> 'CLOSED'`）与 `avgHandleHours`（`CLOSED` 且 `resolved_at` 非空时 `AVG(TIMESTAMPDIFF(HOUR, created_at, resolved_at))`，无关闭缺陷时返回 `null`）。`passRate` 保留一位小数：`Math.round(passed * 1000.0 / total) / 10.0`；`total == 0` 时为 `0.0`。
- `passRateTrend` / `processComparison`：把行对象转 record，`passed` 为 null 时按 0。

`DashboardController`：`@RestController @RequestMapping("/api/dashboard")`，三个 `@GetMapping`，方法签名收 `@RequestParam(required = false)` 的 `projectId`/`batchId`/`from`/`to`，统一 `ApiResult.ok(...)`。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=DashboardApiTest`
Expected: 6 个用例全绿

- [ ] **Step 5: 跑全量后端测试**

Run: `cd backend && mvn test`
Expected: 146 + 6 = 152 全绿

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/pvqims/dashboard backend/src/test/java/com/pvqims/dashboard
git commit -m "feat(dashboard): 指标卡与合格率趋势工序对比" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 缺陷类三张图（帕累托、等级构成、处理时长）

**Files:**
- Modify: `backend/src/main/java/com/pvqims/dashboard/dto/DashboardDtos.java`（+ `ParetoItem`、`GradeSceneCount`、`DefectDuration`）
- Modify: `backend/src/main/java/com/pvqims/dashboard/DashboardMapper.java`（+ 3 个查询）
- Modify: `backend/src/main/java/com/pvqims/dashboard/DashboardService.java`（+ 3 个方法）
- Modify: `backend/src/main/java/com/pvqims/dashboard/DashboardController.java`（+ 3 个端点）
- Test: `backend/src/test/java/com/pvqims/dashboard/DashboardApiTest.java`（追加用例）

**Interfaces:**
- Consumes: Task 1 的 `DashboardQuery`、`DashboardDtos`、`DashboardService` 骨架与私有日期换算
- Produces:
  - `DashboardDtos.ParetoItem(String defectType, long count, double cumulativePercent)`
  - `DashboardDtos.GradeSceneCount(String scene, String defectGrade, long count)`
  - `DashboardDtos.DefectDuration(String defectGrade, Double avgHours)`
  - `DashboardService.pareto(DashboardQuery)` → `List<ParetoItem>`（已按 count 降序、已算累计百分比）
  - `DashboardService.defectGradeByScene(DashboardQuery)` → `List<GradeSceneCount>`
  - `DashboardService.defectDuration(DashboardQuery)` → `List<DefectDuration>`
  - API：`GET /api/dashboard/pareto`、`GET /api/dashboard/defect-grade`、`GET /api/dashboard/defect-duration`

- [ ] **Step 1: 写失败测试**

`DashboardApiTest` 追加：

```java
@Test
void paretoCumulativeEndsAtExactlyOneHundred() {
    // 一个项目三个缺陷：CRACK×2、BUBBLE×1（三条 FAIL 记录，缺陷类型各异）
    // GET /api/dashboard/pareto?projectId=<本项目>
    // 两项：CRACK count=2 cumulativePercent=66.7；BUBBLE count=1 cumulativePercent=100.0
    // 最后一项的 cumulativePercent 必须严格等于 100.0（AC-09）
}

@Test
void defectGradeGroupsByScene() {
    // PQC 一条 CRITICAL、OQC 两条 MINOR
    // GET /api/dashboard/defect-grade -> 两条：{PQC,CRITICAL,1}、{OQC,MINOR,2}
}

@Test
void defectDurationAveragesOnlyClosedDefects() {
    // 一个缺陷走到 CLOSED（用工程师令牌 process->verify->close）
    // GET /api/dashboard/defect-duration -> 该等级一条，avgHours 非 null 且 >= 0
    // 另一个未关闭的缺陷不参与
}

@Test
void defectChartsIgnoreVoidedRecords() {
    // 作废掉那个 CRACK 记录后，pareto 只剩 BUBBLE，且 cumulativePercent=100.0
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=DashboardApiTest`
Expected: 新用例 404

- [ ] **Step 3: 实现**

帕累托的 SQL（SRS 5.10 原样）：
```sql
SELECT d.defect_type AS defectType, COUNT(*) AS cnt
FROM defect d JOIN inspection_record r ON r.id = d.record_id
WHERE r.is_void = 0
  <if projectId> AND r.project_id = #{projectId}
  <if batchId>    AND r.batch_id = #{batchId}
  <if from>       AND r.inspect_time &gt;= #{from}
  <if to>         AND r.inspect_time &lt; #{to}
GROUP BY d.defect_type
ORDER BY cnt DESC, d.defect_type
```
`ORDER BY cnt DESC, d.defect_type` —— 加第二排序键让并列时结果稳定（否则并发插入的两行顺序不定，测试会闪）。

`DashboardService.pareto` 里算累计：先求总数 `N`，再对每项 `cumulative += count`，百分比 = `Math.round(cumulative * 1000.0 / N) / 10.0`；**最后一项强制写成 `100.0`**——浮点累计到末尾可能得 99.9，AC-09 要的是确定值。同理若 `N == 0` 返回空列表。

等级构成 SQL：`SELECT r.scene AS scene, d.defect_grade AS defectGrade, COUNT(*) AS cnt FROM defect d JOIN inspection_record r ON r.id = d.record_id WHERE r.is_void = 0 <filter> GROUP BY r.scene, d.defect_grade ORDER BY r.scene, d.defect_grade`。

处理时长 SQL（SRS 5.10 原样 + 筛选）：`SELECT d.defect_grade AS defectGrade, AVG(TIMESTAMPDIFF(HOUR, d.created_at, d.resolved_at)) AS avgHours FROM defect d JOIN inspection_record r ON r.id = d.record_id WHERE r.is_void = 0 AND d.defect_status = 'CLOSED' AND d.resolved_at IS NOT NULL <filter> GROUP BY d.defect_grade`。`avgHours` 用 `Double`，Service 里保留一位小数。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=DashboardApiTest`
Expected: 10 个用例全绿

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/pvqims/dashboard backend/src/test/java/com/pvqims/dashboard
git commit -m "feat(dashboard): 缺陷帕累托等级构成与处理时长" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: 控制图、检验员工作量与批次合格率（FR-13、CH-04/06/07）

**Files:**
- Modify: `backend/src/main/java/com/pvqims/dashboard/dto/DashboardDtos.java`（+ `ControlChart`、`ControlPoint`、`InspectorWorkload`、`BatchPassRate`）
- Modify: `backend/src/main/java/com/pvqims/dashboard/DashboardMapper.java`（+ 3 个查询）
- Modify: `backend/src/main/java/com/pvqims/dashboard/DashboardService.java`（+ 3 个方法 + 控制限推导）
- Modify: `backend/src/main/java/com/pvqims/dashboard/DashboardController.java`（+ 3 个端点）
- Test: `backend/src/test/java/com/pvqims/dashboard/ControlChartTest.java`

**Interfaces:**
- Consumes: `InspectionStandardMapper`（读标准的四个数值列）、Task 1 的日期换算
- Produces:
  - `DashboardDtos.ControlPoint(LocalDateTime inspectTime, BigDecimal measuredValue)`
  - `DashboardDtos.ControlChart(String itemName, String unit, BigDecimal ucl, BigDecimal lcl, BigDecimal cl, List<ControlPoint> points)`
  - `DashboardDtos.InspectorWorkload(Long inspectorId, String inspectorName, long count)`
  - `DashboardDtos.BatchPassRate(Long batchId, String batchNo, long total, long passed, double passRate)`
  - `DashboardService.controlChart(Long standardId)` → `ControlChart`
  - `DashboardService.inspectorWorkload(DashboardQuery)` → `List<InspectorWorkload>`
  - `DashboardService.batchPassRates(DashboardQuery)` → `List<BatchPassRate>`
  - `ErrorCode.NOT_FOUND` 沿用（standardId 不存在时）
  - API：`GET /api/dashboard/control-chart?standardId=`、`GET /api/dashboard/inspector-workload`、`GET /api/dashboard/batch-gauge`

**控制限推导规则（本任务的核心契约，逐条来自 SRS 6.3「控制限取自标准的上下限」）：**

| `judgeType` | UCL | LCL | CL |
|---|---|---|---|
| `RANGE` | `upperLimit` | `lowerLimit` | 两者均值 |
| `GT` | `null`（不画上限） | `lowerLimit` | `lowerLimit` |
| `LT` | `upperLimit` | `null` | `upperLimit` |
| `STD_TOL` | `target + tolerance` | `target − tolerance` | `targetValue` |
| `PASS_FAIL` | — | — | 抛 `BizException(PARAM_INVALID, "人工判定项没有数值控制图")` |

`STD_TOL` 的上下限是「目标值 ± 公差」——标准的四个数值列里没有直接存 UCL/LCL，但这是标准**定义**的一部分，不是另行计算的统计控制限，符合 SRS 意图。

- [ ] **Step 1: 写失败测试**

`ControlChartTest`（继承同一套辅助方法）：

```java
@Test
void controlLimitsEqualStandardBounds() {
    // RANGE 3.0–3.5 标准，录 3 个值
    // GET /api/dashboard/control-chart?standardId=<id>
    // ucl=3.5, lcl=3.0, cl=3.25（AC-09：控制限等于标准上下限）
    // points.length=3 且按 inspectTime 升序
}

@Test
void stdTolLimitsAreTargetPlusMinusTolerance() {
    // STD_TOL target=710 tolerance=5 -> ucl=715, lcl=705, cl=710
}

@Test
void gtStandardHasNoUpperLimit() {
    // GT lower=100 -> ucl=null, lcl=100, cl=100
}

@Test
void passFailStandardIsRejected() {
    // PASS_FAIL 标准 -> 400 + message 含「人工判定」
}

@Test
void controlChartKeepsOnlyLastThirtyPointsInAscendingOrder() {
    // 录 35 条 -> points.length=30，且 points[29] 是最后录入的那条
}

@Test
void controlChartExcludesVoidedRecords() {
    // 录 3 条，作废 1 条 -> points.length=2
}

@Test
void inspectorWorkloadCountsRecordsPerInspector() {
    // 工程师录 2 条、检验员录 3 条（同一自建项目）
    // 断言检验员那一行 count=3（按 inspectorName 找，不依赖总数）
}

@Test
void batchPassRateExcludesVoided() {
    // 自建批次：2 PASS + 1 FAIL -> 作废那 1 条 FAIL -> passRate=100.0, total=2
    // GET /api/dashboard/batch-gauge?projectId=<本项目> -> 恰好本批次一条
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=ControlChartTest`
Expected: 404

- [ ] **Step 3: 实现**

控制图取数（SRS 5.10 原样）：`WHERE standard_id = ? AND measured_value IS NOT NULL AND is_void = 0 ORDER BY inspect_time DESC, id DESC LIMIT 30`——拿到后再在 Java 里反转成升序（DESC+LIMIT 取最近的 30 条，反转后画图从左到右是时间正序）。

`inspectorWorkload` 的 SQL：`SELECT r.inspector_id AS inspectorId, u.display_name AS inspectorName, COUNT(*) AS cnt FROM inspection_record r JOIN app_user u ON u.id = r.inspector_id WHERE r.is_void = 0 <filter on r> GROUP BY r.inspector_id, u.display_name ORDER BY cnt DESC`。

`batchPassRates` 的 SQL：`SELECT r.batch_id AS batchId, b.batch_no AS batchNo, COUNT(*) AS total, SUM(r.result = 'PASS') AS passed FROM inspection_record r JOIN batch b ON b.id = r.batch_id WHERE r.is_void = 0 <filter on r> GROUP BY r.batch_id, b.batch_no ORDER BY b.batch_no`。`passRate` 在 Service 里算，规则同 `summary`。
> 这个端点同时服务 FR-13 的批次汇总表与 CH-06 的仪表盘（前端从列表里挑出选中批次）。附录 B 只写了这一个路径，不再新增 `/dashboard/batches`。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=ControlChartTest`
Expected: 8 个用例全绿

- [ ] **Step 5: 跑全量后端测试**

Run: `cd backend && mvn test`
Expected: 全部绿

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/pvqims/dashboard backend/src/test/java/com/pvqims/dashboard
git commit -m "feat(dashboard): 控制图检验员工作量与批次合格率" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: 看板页骨架、EChart 组件与两张记录类图表

**Files:**
- Modify: `frontend/package.json`（+ `echarts@6.1.0`）
- Create: `frontend/src/api/dashboard.ts`
- Modify: `frontend/src/api/types.ts`（+ 图表数据类型）
- Create: `frontend/src/components/EChart.tsx`
- Create: `frontend/src/components/EChart.test.tsx`
- Create: `frontend/src/pages/dashboard/options.ts`
- Create: `frontend/src/pages/dashboard/options.test.ts`
- Create: `frontend/src/pages/dashboard/DashboardPage.tsx`
- Create: `frontend/src/pages/dashboard/DashboardPage.test.tsx`
- Modify: `frontend/src/routes/index.tsx`
- Modify: `frontend/src/test/handlers.ts`

**Interfaces:**
- Consumes: `apiClient`、`color`/`shadow`（`../../theme/tokens` 经 `muiTheme` 再导出）、`LabeledSelect`、`LabeledField`、`Alert`
- Produces（Task 5、6 依赖）:
  - `interface DashboardFilter { projectId?: number; batchId?: number; from?: string; to?: string }`
  - `getSummary` / `getPassRateTrend` / `getProcessComparison`（本任务），其余 6 个 API 函数由 Task 5 追加
  - `EChart({ option, height, ariaLabel })` 组件
  - `trendOption(points, ...)` / `processOption(rows, ...)` 纯函数（Task 5 追加其余六个同形函数）
  - `DashboardPage` 的图表卡片外壳 `<ChartCard title={...} empty={...}>`（本文件内私有组件，Task 5 复用）

- [ ] **Step 1: 安装依赖**

Run: `cd frontend && pnpm add echarts@6.1.0`
Expected: `package.json` 的 `dependencies` 中出现 `"echarts": "6.1.0"`。**不装 `echarts-for-react`**——见下。

> **技术选型的偏离**：`docs/技术选型.md` 列了 `echarts-for-react 3.0.6`。该包最后一个版本早于 React 19 与 ECharts 6，本项目不需要它的任何额外能力（只用到 init/setOption/resize/dispose 四件事）。自写 40 行组件比引入一个未维护的封装风险更低，且它的 API 是我们组件的子集，将来要换回去随时可以。**代价**：多写约 40 行。

- [ ] **Step 2: 写纯函数测试（先写，后实现）**

`frontend/src/pages/dashboard/options.test.ts`：

```ts
it('趋势图把日期映射到 x 轴、合格率映射到 y 轴', () => {
  const option = trendOption([
    { day: '2026-11-17', total: 10, passed: 9, passRate: 90 },
    { day: '2026-11-18', total: 4, passed: 3, passRate: 75 },
  ]);
  expect(option.xAxis.data).toEqual(['2026-11-17', '2026-11-18']);
  expect(option.series[0].data).toEqual([90, 75]);
  expect(option.yAxis.min).toBe(0);
  expect(option.yAxis.max).toBe(100);
});

it('工序对比是横向条形图并显示百分比', () => {
  const option = processOption([{ processName: '层压', total: 10, passed: 8, passRate: 80 }]);
  expect(option.yAxis.type).toBe('category');
  expect(option.xAxis.max).toBe(100);
  expect(option.series[0].type).toBe('bar');
  expect(option.series[0].data[0].value).toBe(80);
});
```

`EChart.test.tsx`：

```tsx
it('图表容器带上 role=img 与中文可访问名', () => {
  render(<EChart option={{}} ariaLabel="每日合格率趋势" />);
  expect(screen.getByRole('img', { name: '每日合格率趋势' })).toBeInTheDocument();
});
```
（jsdom 里 canvas 不可用：本测试文件顶部 `vi.mock('echarts/core', () => ({ init: () => ({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() }), use: vi.fn() }))`。断言的是我们自己的契约——可访问名和生命周期，不是 ECharts 的行为。）

- [ ] **Step 3: 跑测试确认失败**

Run: `cd frontend && pnpm vitest run src/pages/dashboard src/components/EChart.test.tsx`
Expected: 模块不存在

- [ ] **Step 4: 实现**

`EChart.tsx`（约 40 行）：`echarts.use([LineChart, BarChart, PieChart, GaugeChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, MarkLineComponent, CanvasRenderer])`；`useEffect` 里 `echarts.init(ref.current)` 并用 `ResizeObserver` 调 `resize()`，卸载时 `disconnect()` + `dispose()`；第二个 `useEffect` 依赖 `option` 调 `setOption(option, true)`（`true` = 不合并，避免切换筛选后旧 series 残留）。外层 `Box` 带 `role="img"` 与 `aria-label`，`sx={{ width: '100%', height }}`。

`options.ts` 的配色**只取 `color.*`**：主系列 `color.accent`，合格 `color.success`，不合格 `color.danger`，辅助线 `color.fgMuted`，网格线 `color.line1`，文字 `color.fgMuted`。两个函数签名：
```ts
export function trendOption(points: TrendPoint[]): EChartsOption
export function processOption(rows: ProcessComparison[]): EChartsOption
```

`DashboardPage.tsx`：标题「质量看板」+ 筛选行（项目 / 批次 / 开始日期 / 结束日期，`useFlexGap`）+ 指标卡（4 个 `Paper`：记录总数、合格率、未关闭缺陷、平均处理时长）+ 图表网格（`Box` 的 `display: 'grid'`，`gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' }`）。本任务只接 2 张图（CH-01、CH-03），其余位置留到 Task 5。`ChartCard` 私有组件：标题 + 数据为空时渲染 `<Alert severity="info">暂无数据</Alert>` + 否则 `<EChart>`。

`handlers.ts` 追加 `GET /api/dashboard/summary`、`/pass-rate-trend`、`/process-comparison` 默认处理器；`routes/index.tsx` 把 `pages` 数组整个删掉（`dashboard` 是最后一个占位页），改为 `{ path: 'dashboard', element: <DashboardPage /> }`，并删除 `PlaceholderPage` 的 import。**`src/pages/PlaceholderPage.tsx` 文件本身保留**——它已无引用，但删除它与本任务无关，按「不清理别人的代码」的约定留给用户决定。

- [ ] **Step 5: 跑测试确认通过**

Run: `cd frontend && pnpm vitest run src/pages/dashboard src/components/EChart.test.tsx`
Expected: 全部绿

- [ ] **Step 6: 跑全量校验**

Run: `cd frontend && pnpm vitest run && pnpm lint && pnpm exec prettier --check src && pnpm build`
Expected: 89 + 新增全绿；lint/prettier/build 全过

- [ ] **Step 7: 提交**

```bash
git add frontend
git commit -m "feat(dashboard): 看板页骨架与合格率趋势图" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: 其余六张图与空状态

**Files:**
- Modify: `frontend/src/api/dashboard.ts`（+ 6 个函数）
- Modify: `frontend/src/api/types.ts`（+ `ParetoItem`、`GradeSceneCount`、`ControlChart`、`ControlPoint`、`InspectorWorkload`、`BatchPassRate`、`DefectDuration`）
- Modify: `frontend/src/pages/dashboard/options.ts`（+ 6 个纯函数）
- Modify: `frontend/src/pages/dashboard/options.test.ts`
- Modify: `frontend/src/pages/dashboard/DashboardPage.tsx`
- Modify: `frontend/src/pages/dashboard/DashboardPage.test.tsx`
- Modify: `frontend/src/test/handlers.ts`

**Interfaces:**
- Consumes: Task 1~4 的全部后端端点与 `ChartCard` / `EChart`
- Produces: 完整 P6 页面（8 图 + 批次汇总表）

- [ ] **Step 1: 写纯函数测试**

`options.test.ts` 追加（**AC-09 的两条断言在这里，不在页面测试里**）：

```ts
it('帕累托累计线终点为 100', () => {
  const option = paretoOption([
    { defectType: 'CRACK', count: 2, cumulativePercent: 66.7 },
    { defectType: 'BUBBLE', count: 1, cumulativePercent: 100 },
  ]);
  // 柱状 series 用 count，折线 series 用 cumulativePercent 且挂右侧 y 轴
  expect(option.series[0].type).toBe('bar');
  expect(option.series[1].type).toBe('line');
  expect(option.series[1].yAxisIndex).toBe(1);
  expect(option.series[1].data.at(-1)).toBe(100);
  expect(option.yAxis[1].max).toBe(100);
});

it('控制图三条控制线取自传入的上下限', () => {
  const option = controlOption({
    itemName: '边框宽度', unit: 'mm', ucl: 3.5, lcl: 3, cl: 3.25,
    points: [{ inspectTime: '2026-11-18T10:00:00', measuredValue: 3.2 }],
  });
  const marks = option.series[0].markLine.data;
  expect(marks.map((m) => m.yAxis)).toEqual([3.5, 3.25, 3]);   // UCL / CL / LCL
});

it('等级构成是堆叠柱状图且按场景分组', () => {
  const option = gradeOption([
    { scene: 'PQC', defectGrade: 'CRITICAL', count: 1 },
    { scene: 'OQC', defectGrade: 'MINOR', count: 2 },
  ]);
  expect(option.series.every((s) => s.stack === 'grade')).toBe(true);
  expect(option.xAxis.data).toEqual(['过程检验', '出货检验']);
});

it('批次仪表盘取选中批次的合格率', () => {
  const option = gaugeOption({ batchId: 1, batchNo: 'B-1', total: 10, passed: 8, passRate: 80 });
  expect(option.series[0].data[0].value).toBe(80);
});
```

`DashboardPage.test.tsx` 追加：

```tsx
it('每张图无数据时显示空状态而不是空白坐标轴', async () => {
  // 所有 dashboard 处理器都返回空数组 / 全 0
  // 断言页面出现 8 处「暂无数据」
  expect(await screen.findAllByText('暂无数据')).toHaveLength(8);
});

it('批次合格率表按批号列出合格率', async () => {
  // batch-gauge 返回两条 -> 表格两行，含批号与百分比
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && pnpm vitest run src/pages/dashboard`
Expected: `paretoOption is not defined` 之类

- [ ] **Step 3: 实现**

六个 option 函数与对应图表：

| 函数 | 图型 | 关键约定 |
|---|---|---|
| `paretoOption(items)` | bar + line 双轴 | 左轴 count，右轴 0–100 百分比；折线 `yAxisIndex: 1` |
| `gradeOption(rows)` | 堆叠 bar | x 轴是场景中文名，三个 series（致命/严重/轻微）`stack: 'grade'` |
| `controlOption(chart)` | line + markLine | `markLine` 三条：UCL/CL/LCL，`yAxis` 为 `null` 的线不画 |
| `workloadOption(rows)` | bar | x 轴检验员姓名 |
| `durationOption(rows)` | bar | x 轴等级中文名，y 轴小时 |
| `gaugeOption(row)` | gauge | `max: 100`，`detail.formatter: '{value}%'` |

页面追加：其余 6 张 `ChartCard`、批次汇总表（`TableContainer` + `Paper` + `shadow.card`，列：批号 / 记录数 / 合格数 / 合格率）、控制图的标准选择器（`LabeledSelect`，选项来自 `/api/standards?projectId=` 且 `valueType === 'NUMERIC'`；未选项目时禁用）。

`handlers.ts` 追加其余 6 个 dashboard 处理器。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && pnpm vitest run src/pages/dashboard`
Expected: 全部绿

- [ ] **Step 5: 跑全量校验**

Run: `cd frontend && pnpm vitest run && pnpm lint && pnpm exec prettier --check src && pnpm build`
Expected: 全绿

- [ ] **Step 6: 提交**

```bash
git add frontend
git commit -m "feat(dashboard): 补齐八张图与批次合格率汇总" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: 演示数据初始化（FR-16）

**Files:**
- Create: `backend/src/main/java/com/pvqims/demo/DemoDataService.java`
- Create: `backend/src/main/java/com/pvqims/demo/DemoController.java`
- Modify: `backend/src/main/java/com/pvqims/common/exception/ErrorCode.java`（+ `DEMO_DATA_EXISTS(40015)`）
- Create: `frontend/src/api/demo.ts`
- Modify: `frontend/src/pages/dashboard/DashboardPage.tsx`（+ 管理员专属按钮）
- Modify: `frontend/src/pages/dashboard/DashboardPage.test.tsx`
- Test: `backend/src/test/java/com/pvqims/demo/DemoSeedApiTest.java`

**Interfaces:**
- Consumes: `JudgmentRules.evaluate(...)`（**判定的唯一来源，不许另写一套**）、各实体 mapper、`PasswordEncoder`
- Produces:
  - `ErrorCode.DEMO_DATA_EXISTS(40015, "演示数据已存在，请先清理")`
  - `DemoDataService.seed()` → `DemoSeedResult(recordCount, defectCount, projectCount)`
  - API：`POST /api/demo/seed` → `ApiResult<DemoSeedResult>`，`@PreAuthorize("hasRole('ADMIN')")`
  - 前端 `seedDemoData(): Promise<DemoSeedResult>`

**数据规模（固定，不随机取数量，保证 AC-10 可测）**：
- 项目 2 个：`DEMO-2026-001`、`DEMO-2026-002`
- 批次 6 个（每项目 3 个，覆盖 PLANNED / PRODUCING / COMPLETED）
- 标准 20 条（每项目 10 条：IQC 3 / PQC 4 / OQC 3，五种 judge_type 各至少一条）
- 记录 360 条（30 天 × 每天 12 条），`inspect_time` 分布在今天往前 29 天内
- 缺陷由 FAIL 记录自动生成（不单独插），等级比例约 1:3:6
- 其中约 40% 的缺陷走到 CLOSED 且 `resolved_at` 落在 `created_at` 之后 4–48 小时（供 CH-08 有数据）
- 额外创建 2 个演示检验员 `demo-inspector-1` / `demo-inspector-2`（密码 `Demo@1234`），让 CH-07 有三根柱

- [ ] **Step 1: 写失败测试**

`DemoSeedApiTest`：

```java
@Test
void adminSeedsThreeHundredPlusRecordsAndAllChartsHaveData() throws Exception {
    // admin 调 POST /api/demo/seed -> 200
    // 断言 inspection_record 增量 >= 300（AC-10）
    // 八个端点逐个断言有数据：summary.totalRecords > 0、trend 非空、pareto 非空、
    // process-comparison 非空、defect-grade 非空、inspector-workload 非空、
    // batch-gauge 非空、defect-duration 非空（AC-10「8 张图均有数据」）
}

@Test
void seedingTwiceIsRejected() throws Exception {
    // 第二次 -> 400 + code 40015；记录数不再增加
}

@Test
void onlyAdminCanSeed() throws Exception {
    // engineer / inspector -> 403 + 40300
}

@Test
void seededRecordsUseTheSharedJudgmentRules() throws Exception {
    // 抽样：查一条 seed 出来的记录，按它的快照重算 result 必须与库里一致
    // （证明没有另写一套判定逻辑，BR-01 未被绕过）
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=DemoSeedApiTest`
Expected: 404

- [ ] **Step 3: 实现后端**

`DemoDataService`：
- 幂等守卫：`projectMapper.exists(projectCode = "DEMO-2026-001")` → 抛 `DEMO_DATA_EXISTS`。
- 随机源：`new Random(20261118L)` 字段级常量，**保证同一次运行可复现**。
- 记录插入不走 `InspectionService.createRecord`（它把 `inspectTime` 钉成 `now()`），而是直接 `recordMapper.insert(row)`，但 `result` **必须**由 `judgmentRules.evaluate(...)` 计算，五个 `std_*` 快照列照抄标准——这样 BR-01/BR-02 都不破。
- 缺陷：`result == "FAIL"` 时插 `defect`，等级按 1:3:6 比例从随机数落点决定，类型从七种枚举里取五种轮转。
- 收尾：对约 40% 的缺陷用 `LambdaUpdateWrapper` 把 `defect_status` 置 `CLOSED`、`resolved_at` 置 `created_at + 4..48h`、`handle_note` 填一句中文说明；再把它们的 `created_at` 回填成对应记录的 `inspect_time`（演示历史才连续）。
- 概览接口 `DashboardService` 不受影响（它实时查）。

- [ ] **Step 4: 跑后端测试**

Run: `cd backend && mvn test -Dtest=DemoSeedApiTest`
Expected: 4 个用例全绿

- [ ] **Step 5: 实现前端按钮并写测试**

`DashboardPage` 在标题右侧渲染「初始化演示数据」按钮（仅 `role === 'ADMIN'`），点击弹 `ConfirmDialog`（复用既有组件：标题「初始化演示数据」、正文「将创建 2 个项目、6 个批次、360 条校验记录，仅用于演示」、确认按钮文案由 `ConfirmDialog` 固定为「确认删除」——**这不是删除操作，所以要改 `ConfirmDialog` 支持自定义确认文案**，加一个可选 prop `confirmLabel = '确认删除'`，不传时行为不变）。

`DashboardPage.test.tsx` 追加：

```tsx
it('管理员看到初始化演示数据按钮，其他人看不到', async () => {
  // ADMIN -> 有按钮；ENGINEER / INSPECTOR -> queryByRole 为 null
});
it('初始化成功后提示记录数并刷新看板', async () => {
  // mock POST /api/demo/seed -> {recordCount: 360}
  // 点按钮 -> 确认 -> 断言请求发出、成功提示含 360、dashboard 查询被重拉
});
it('初始化失败时显示后端原因', async () => {
  // 400 {code:40015, message:'演示数据已存在，请先清理'}
  // 页面显示该 message
});
```

- [ ] **Step 6: 跑全量前后端校验**

Run:
```bash
cd backend && mvn test
cd ../frontend && pnpm vitest run && pnpm lint && pnpm exec prettier --check src && pnpm build
```
Expected: 全部绿

- [ ] **Step 7: 提交**

```bash
git add backend frontend
git commit -m "feat(demo): 管理员一键生成演示数据" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 自检记录

**1. 规格覆盖**

| 需求 | 落点 |
|---|---|
| FR-13 批次合格率汇总 | Task 3（`batchPassRateExcludesVoided`）+ Task 5（批次汇总表） |
| FR-14 看板 8 图 | CH-01/03 → Task 1、4；CH-02/05/08 → Task 2、5；CH-04/06/07 → Task 3、5 |
| FR-16 演示数据 | Task 6 |
| AC-06 排除作废 | 每条聚合 SQL 带 `is_void = 0`；Task 1/2/3 各有一条作废断言 |
| AC-09 帕累托 100% / 控制限等于标准上下限 | Task 2 `paretoCumulativeEndsAtExactlyOneHundred`；Task 3 `controlLimitsEqualStandardBounds`；Task 5 的 option 断言 |
| AC-10 300+ 记录、8 图有数据 | Task 6 `adminSeedsThreeHundredPlusRecordsAndAllChartsHaveData` |
| SRS 4.8 看板全角色 | Task 1 `dashboardIsOpenToAllRoles` |
| SRS 6.3 无数据时空状态 | Task 5 `everyChartShowsEmptyStateWithoutData` |

**2. 明确不做**：不做预聚合表（SRS 4.7 明写实时查询）；不装 `echarts-for-react`（理由见 Task 4，已记录代价）；不做看板导出/分享链接（范围外）；不把演示数据写成 Flyway 迁移（迁移冻结）。

**3. 类型一致性**：`DashboardDtos` 的对外 record 与前端 `api/types.ts` 字段同名同义（`passRate` 一位小数、`avgHours` 可为 null）；MyBatis 映射行一律 POJO；`DashboardQuery` 是 Task 1 定义、Task 2/3 复用；`ChartCard` 是 Task 4 定义、Task 5 复用；`ConfirmDialog` 的新 prop 有默认值，既有调用点零改动。

**4. Review Focus 回查**（每条都在拥有该代码的任务里有对应测试）：

| Review Focus | 测试 |
|---|---|
| 作废记录被算进合格率 | Task 1 `voidedRecordsAreExcludedFromAllAggregates`、Task 2 `defectChartsIgnoreVoidedRecords`、Task 3 `batchPassRateExcludesVoided` |
| 帕累托终点不是 100% | Task 2 `paretoCumulativeEndsAtExactlyOneHundred`、Task 5 `paretoOption` 断言 |
| 控制图画标准之外的线 | Task 3 `controlLimitsEqualStandardBounds`、Task 5 `controlOption` 断言 |
| 演示数据重复/与真实数据混合 | Task 6 `seedingTwiceIsRejected` |
| 无数据时空白坐标轴 | Task 5 `everyChartShowsEmptyStateWithoutData` |
| 判定被绕过 | Task 6 `seededRecordsUseTheSharedJudgmentRules` |

**5. 篇幅**：818 行，六个任务；代码块只出现在需要决定写法的地方（控制限推导表、option 约定、SQL 骨架）。Task 1 的「两套 DTO 形态」说明是全篇最长的一段——那是唯一会让人写错的接口决定。

**6. 一处已知取舍**：控制图缺省选中「项目下第一条数值型标准」会让用户看到可能不相干的数据，所以本计划让标准选择器**默认空**、未选时该卡片显示「请选择检验项」而不是「暂无数据」——这与 SRS 6.3 的空状态要求不冲突（那不是"没数据"，是"还没选"）。
