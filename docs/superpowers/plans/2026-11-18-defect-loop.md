# 阶段五 · 缺陷闭环与记录作废 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付记录作废（FR-10）、缺陷列表（FR-11）、缺陷处理与验证（FR-12）、CSV 导出（FR-15）：管理员可凭原因作废记录且记录保留；工程师按等级/状态/工序/时间段查看缺陷，并按 OPEN→PROCESSING→VERIFIED→CLOSED 严格流转；工程师按当前筛选导出 CSV；P5 缺陷处理页替换占位页。

**Architecture:** 缺陷读写都落在 `com.pvqims.inspection`（`defect` 实体与 mapper 已在阶段四生成）。列表走已建好的视图 `v_defect_detail`，用 `@Select` + `IPage` 分页、`<if>` 拼条件；状态机用「一个端点对应一种目标状态」的方式把 BR-05 变成结构性约束——每个端点只接受固定的起始状态集合，跳级在类型上就走不通。导出单独成 `com.pvqims.export` 包：CSV 拼装是纯函数，可脱离 Spring 直测；控制器只负责取数与写响应头。

**Tech Stack:** Java 21 / Spring Boot 4.1.2 / Spring Security 7.1.2 / MyBatis-Plus 3.5.18.1 / Flyway 12.4.0 / MySQL 8.0；React 19.3.1 / TypeScript 6.0.3 / MUI 9.5.0 / React Router 8.5.1 / TanStack Query 5.107.1 / Vitest 5.1.0 + MSW 3.0.5。

**Spec:** `docs/需求规格说明书.md`（PVQIMS-SRS-001 V2.1）：3.5（缺陷状态机）、3.6（设计原因）、4.2（UC-10~UC-12、UC-15）、4.6（FR-11/FR-12）、4.8（权限矩阵）、4.9（BR-05、BR-08）、5.7（`is_void`/`void_reason`）、5.8（defect 表）、5.11（`v_defect_detail`）、5.12（枚举字典）、6.1（P5）、8（AC-05、AC-06）、附录 B（接口清单）。

---

## 阶段路线图

| 阶段 | 计划文件 | 交付 | 覆盖需求 |
|---|---|---|---|
| 一（已完成） | `2026-11-16-foundation-auth.md` | 骨架、登录、用户管理 | FR-01、FR-02 |
| 二（已完成） | `2026-11-17-master-data.md` | 项目与批次主数据 | FR-03、FR-04、P2 |
| 三（已完成） | `2026-11-18-standard-config.md` | 标准配置、启停、排序 | FR-05、FR-06、P3 |
| 四（已完成） | `2026-11-18-inspection-entry.md` | 任务选择、录入判定、记录查询 | FR-07~FR-09、P4 |
| 五（本计划） | `2026-11-18-defect-loop.md` | 记录作废、缺陷闭环、CSV 导出 | FR-10~FR-12、FR-15、P5 |
| 六 | `YYYY-MM-DD-dashboard-demo.md` | 合格率汇总、8 张图、演示数据 | FR-13、FR-14、FR-16、P6 |

---

## Global Constraints

以下约束对本计划每个任务生效，逐条照抄自 SRS 与 CLAUDE.md：

- **不开分支、不用 `git worktree`**：全部提交直接落在 `main`。
- **不新增数据库迁移**：`inspection_record.is_void`/`void_reason` 与 `defect` 表、视图 `v_defect_detail`、索引 `idx_defect_status_grade` 均已由 `V1__init_schema.sql` 建好。V1/V2 校验和已冻结，**任何修改都会导致 Flyway 失败**。
- **业务表恰好 6 张**：不新增表、不新增视图、不加列。
- **缺陷状态严格流转**（BR-05、AC-05）：`OPEN → PROCESSING → VERIFIED → CLOSED`，`VERIFIED → PROCESSING` 可退回，**禁止跳级**（OPEN 不能直接 VERIFIED 或 CLOSED，PROCESSING 不能直接 CLOSED，CLOSED 是终态）。非法流转返回 400。
- **只作废不物理删除**（BR-08）：作废只写 `is_void = 1` 与 `void_reason`，**不存在 DELETE 记录或缺陷的接口**；作废记录不参与任何统计（AC-06）。
- **作废原因必填**（SRS 5.7 `void_reason` 注释）：空或全空白拒绝。
- **作废的角色边界**（SRS 4.8）：`POST /api/inspection/records/{id}/void` 仅 `ADMIN`；`ENGINEER`/`INSPECTOR` 调接口一律 403，不能只靠隐藏按钮。
- **缺陷处理的角色边界**（SRS 4.8）：`/process`、`/verify`、`/close` 仅 `ENGINEER`；`INSPECTOR` 与 `ADMIN` 只读，写接口 403。
- **导出角色**（附录 B）：`GET /api/export/records.csv` 仅 `ENGINEER`。
- **缺陷列表对全部已登录角色开放**（附录 B「全部」），只读。
- **关单必须填处理说明**（SRS 4.6「关闭时必须填写处理说明」）。**验证不通过（VERIFIED→PROCESSING）同样必须填说明**——退回不给原因，处理人无从整改。这是对 SRS 下限的加严，不是新规则。
- **`handle_note` 是单列覆盖写**（6 表模型不建流转表，SRS 5.8 注）：每次流转把新说明写入该列；未提供说明的流转**不得清空**已有说明。每次流转的操作人与时间写应用日志。
- **`handler_id` 表示当前状态的责任人**，每次流转更新为当前操作人。
- **`resolved_at` 只在 CLOSED 时写入**（SRS 5.8「关闭时间」），退回 PROCESSING 时保持 NULL。
- **数值比较必须用 `BigDecimal.compareTo`**，不要用 `equals`（`3.0` 与 `3.00` 在 `equals` 下不等）。
- **作废会连带隐藏其缺陷**：`defect` 表没有作废列，无法单独标记。缺陷列表联查 `inspection_record.is_void = 0`——记录作废即「这条记录不算数」，由它派生的缺陷自然也不成立；缺陷行仍在库中，不物理删除。作废已关闭缺陷不额外限制：SRS 未给该规则，凭空虚构限制会让真正需要修错数据的管理员无路可走。
- **导出上限 10000 条**：超出返回 400 提示缩小范围，不静默截断。单实例部署下无上限的 `selectList` 是 OOM 入口。
- **CSV 必须带 UTF-8 BOM**：否则中文 Windows 的 Excel 双击打开是乱码。
- **CSV 公式注入防护**：文本字段以 `= + - @ \t \r` 开头时前置一个 `'`；**数值字段不做此前缀**（负数的 `-` 是符号不是公式）。
- **前端不得用 `<a href>` 直接下载导出文件**：认证是 `Authorization: Bearer`（非 Cookie），原生导航带不上令牌。导出走 `fetch` + Blob + `URL.createObjectURL`。
- **视觉令牌唯一来源**：`design/aesthetic/tokens.css`。前端不得出现令牌外的颜色/圆角/阴影，不得写裸十六进制。复用 `LabeledField`、`LabeledSelect`、`FormErrorSnackbar`、`ConfirmDialog`、`findMissing`/`missingMessage`、`shadow.card`。
- **既有测试不得削弱**：后端现有 113 个 `@Test`、前端现有全部 `it(...)` 的**既有断言一个字都不改**；只允许新增用例（可追加到既有测试文件的新 `it(...)` 块，例如 `labels.test.ts`）。特别是 `InspectionApiTest.recordQueryNeverReturnsVoidedRecords` 用直接改库模拟作废——本阶段加了作废接口后它必须继续通过。
- 数据库 MySQL 8.0+、InnoDB、utf8mb4；时间 Asia/Shanghai；界面文案用「校验」，质量体系概念（检验、检验员、来料检验）用「检验」。
- 库/框架/API 用法先查 Context7 MCP，不凭记忆写。
- 提交信息格式 `type(scope): description`，type 限 `feat|fix|docs|refactor|perf|test|chore`，scope 用真实模块名（`inspection` / `defect` / `export` / 前端同名），description 中文、无句号、≤50 字；末尾附 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。
- **过程留痕**：写入 `.superpowers/sdd/2026-11-18-defect-loop/progress.md`（该目录已 git-ignore）。

**测试环境约定**（阶段一至四已验证）：后端集成测试连本机 `pvqims_test` 库，类注解固定为 `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional`；种子账号 `admin/Admin@123`、`engineer/Engineer@123`、`inspector/Inspector@123`。

**已知陷阱**（前四个阶段踩过）：
- 实体可空列必须 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`，否则清空字段静默失败；**稀疏实体 `updateById` 会把所有 ALWAYS 列写成 NULL**——只想改几列时用 `LambdaUpdateWrapper.set(...)`。
- `LambdaQueryWrapper` 的关键字 OR 必须 `.and(...)` 包住，否则绕过同级 `eq`。
- `PaginationInnerInterceptor` 的全局 `maxLimit` 是 100（`MybatisPlusConfig`），**任何 `selectPage` 都拿不到超过 100 条**；需要「取前 N 条」而非分页时用 `LambdaQueryWrapper.last("LIMIT n")`。
- `Map.of` 不接受 null；构造带可空值的请求体用文本块。
- MUI `Stack` 筛选行必须带 `useFlexGap`。
- 数字输入要 `step="any"`，否则浏览器原生校验拦住小数。
- 浏览器对 `Content-Disposition` 里的非 ASCII 文件名处理不一致；导出文件名固定用 ASCII。

---

## Review Focus

SRS 没写、但用户一定会碰到并因此判定「这系统坏了」的五类情况，按可能性排序。每条都落到拥有该代码的任务的测试里。

1. **导出的 CSV 在 Excel 里是乱码**：工程师双击打开看到「锟斤拷」。→ Task 4 的 `csvStartsWithBomAndUsesCrLf`（纯函数）与 `returnsCsvAttachmentWithBom`（端点）。
2. **检验项名或备注里带逗号/引号/换行时列数错位**：一行被拆成多行多列，整张表错位。→ Task 4 的 `csvEscapesCommaQuoteAndNewline`。
3. **跳级流转被接受**（OPEN 直接关闭，PROCESSING 直接关闭）：AC-05 的核心验收项。→ Task 3 的 `illegalTransitionsAreRejected` 逐条覆盖。
4. **作废后缺陷仍留在缺陷列表**：作废记录不参与任何统计，由它派生的缺陷却还挂在待办里，统计口径与工作台对不上。→ Task 2 的 `voidedSourceRecordHidesItsDefect`。
5. **作废的记录仍出现在默认记录列表 / 重复作废覆盖原因**：作废就是要它从台账上消失；重复作废会把第一次的原因悄悄冲掉。→ Task 1 的 `adminVoidsRecordAndItLeavesDefaultList`、`voidingTwiceIsRejected`。

补充覆盖（同属上述类目，一并落在对应任务）：表单值以 `=` 开头被 Excel 当公式执行（Task 4 `textFieldStartingWithEqualsIsNeutralised`）；负数被公式防护误加前缀（Task 4 `negativeNumbersAreNotNeutralised`）；退回不给原因（Task 3 `rejectRequiresHandleNote`）；导出超上限被静默截断（Task 4 `exportBeyondCapIsRejected`）。

---

## 文件结构（阶段五）

```
backend/src/main/java/com/pvqims/inspection/
├── dto/VoidRecordRequest.java               （新建：作废原因）
├── dto/DefectDetail.java                    （新建：v_defect_detail 行，POJO 非 record）
├── dto/DefectActionRequest.java             （新建：处理说明）
├── DefectMapper.java                        （修改：+ selectDetailPage）
├── DefectService.java                       （新建：列表 + 状态机）
├── DefectController.java                    （新建：GET /api/defects + 三个流转端点）
├── InspectionService.java                   （修改：+ voidRecord、includeVoid、导出取数）
├── InspectionController.java                （修改：+ 作废端点、includeVoid 参数）
├── dto/InspectionRecordSummary.java         （修改：+ voidReason）
└── JudgmentRules.java                       （不改）

backend/src/main/java/com/pvqims/export/
├── RecordCsvWriter.java                     （新建：纯函数拼 CSV）
└── ExportController.java                    （新建：GET /api/export/records.csv）

backend/src/main/java/com/pvqims/common/exception/ErrorCode.java （修改：+ 3 个错误码）

backend/src/test/java/com/pvqims/inspection/RecordVoidApiTest.java
backend/src/test/java/com/pvqims/inspection/DefectListApiTest.java
backend/src/test/java/com/pvqims/inspection/DefectTransitionApiTest.java
backend/src/test/java/com/pvqims/export/RecordCsvWriterTest.java
backend/src/test/java/com/pvqims/export/RecordCsvApiTest.java

frontend/src/api/types.ts                    （修改：+ DefectStatus/DefectDetail、记录加 voidReason）
frontend/src/api/defects.ts                  （新建）
frontend/src/api/inspection.ts               （修改：+ voidRecord、includeVoid）
frontend/src/api/export.ts                   （新建：fetch + Blob）
frontend/src/utils/labels.ts                 （修改：+ DEFECT_STATUS_LABELS）
frontend/src/utils/labels.test.ts            （修改：+ 一个 it 块）
frontend/src/pages/defects/DefectListPage.tsx        （新建）
frontend/src/pages/defects/DefectListPage.test.tsx   （新建）
frontend/src/pages/inspection/VoidRecordDialog.tsx   （新建）
frontend/src/pages/inspection/RecordListPage.tsx     （修改：+ 作废/含作废/导出）
frontend/src/pages/inspection/RecordListPage.test.tsx（修改：+ 用例）
frontend/src/routes/index.tsx                （修改：/defects 指向新页面）
frontend/src/test/handlers.ts                （修改：+ 缺陷默认处理器）
```

---

### Task 1: 记录作废（FR-10）

**Files:**
- Create: `backend/src/main/java/com/pvqims/inspection/dto/VoidRecordRequest.java`
- Modify: `backend/src/main/java/com/pvqims/inspection/dto/InspectionRecordSummary.java`（+ `voidReason`）
- Modify: `backend/src/main/java/com/pvqims/common/exception/ErrorCode.java`（+ `RECORD_ALREADY_VOID`）
- Modify: `backend/src/main/java/com/pvqims/inspection/InspectionService.java`（+ `voidRecord`、`pageRecords` 加 `includeVoid`）
- Modify: `backend/src/main/java/com/pvqims/inspection/InspectionController.java`
- Test: `backend/src/test/java/com/pvqims/inspection/RecordVoidApiTest.java`

**Interfaces:**
- Consumes: `InspectionRecordMapper`、`LoginUser`、`BizException`、`ErrorCode`、`LambdaUpdateWrapper`
- Produces:
  - `ErrorCode.RECORD_ALREADY_VOID(40012, "该记录已作废")`
  - `InspectionService.voidRecord(Long id, String reason)` → `InspectionRecordSummary`（返回 `isVoid = true`、`voidReason` 已写入的那一行）
  - `InspectionService.pageRecords(Long projectId, Long batchId, String scene, String processName, String result, LocalDate from, LocalDate to, boolean includeVoid, long page, long pageSize)` → `PageResult<InspectionRecordSummary>`
  - `InspectionRecordSummary` 新增末位字段 `String voidReason`
  - API：`POST /api/inspection/records/{id}/void`，body `{"reason":"..."}` → `ApiResult<InspectionRecordSummary>`；`GET /api/inspection/records?includeVoid=true` 才返回作废行（默认 `false`）

- [ ] **Step 1: 写失败测试**

新建 `RecordVoidApiTest`，复用 `InspectionApiTest` 的辅助方法写法（`tokenFor` / `createProject` / `createBatch` / `createStandard` / `recordBody` / `jsonOrNull` / `num` 原样照抄到本类，测试类之间不共享辅助代码是本项目既有做法）：

```java
@Test
void adminVoidsRecordAndItLeavesDefaultList() throws Exception {
    // 工程师建项目/批次/标准并录一条 PASS 记录 → 管理员作废
    // 1) POST /void 返回 200，$.data.isVoid = true，$.data.voidReason = "录入错批次"
    // 2) GET /records?batchId=N 默认 -> total = 0
    // 3) 库中该行 is_void = 1 且 void_reason = '录入错批次'（记录保留，未物理删除）
}

@Test
void includeVoidTrueReturnsVoidedRecordWithReason() throws Exception {
    // 作废后 GET /records?batchId=N&includeVoid=true -> total = 1
    // $.data.records[0].isVoid = true，$.data.records[0].voidReason = "录入错批次"
}

@Test
void voidReasonIsRequired() throws Exception {
    // reason 缺省、空串、全空白三种都 400 + code 40001 + message 含「作废原因」
    // 作废前后库中 is_void 仍为 0
}

@Test
void voidingTwiceIsRejected() throws Exception {
    // 第一次 200；第二次 400 + code 40012 + message 含「已作废」
    // 且 void_reason 仍是第一次的值（不被覆盖）
}

@Test
void engineerAndInspectorCannotVoid() throws Exception {
    // 4.8：作废只有管理员可操作。两个角色都 403 + code 40300，库中 is_void 仍为 0
}

@Test
void voidingMissingRecordReturns404() throws Exception {
    // POST /records/999999/void -> 404 + code 40400
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=RecordVoidApiTest`
Expected: 编译失败（`VoidRecordRequest` 不存在）或 404/405（端点不存在）

- [ ] **Step 3: 实现**

`VoidRecordRequest`：
```java
public record VoidRecordRequest(
    @NotBlank(message = "请填写作废原因")
    @Size(max = 255, message = "作废原因最多 255 字") String reason) {}
```

`InspectionRecordSummary` 在 `remark` 前插入 `String voidReason`，`from()` 传 `record.getVoidReason()`。

`InspectionService.voidRecord`：
```java
@Transactional
public InspectionRecordSummary voidRecord(Long id, String reason) {
    InspectionRecord record = recordMapper.selectById(id);
    if (record == null) throw new BizException(ErrorCode.NOT_FOUND);
    if (Boolean.TRUE.equals(record.getIsVoid())) throw new BizException(ErrorCode.RECORD_ALREADY_VOID);
    // 只 SET 两列：实体可空列是 ALWAYS 策略，走 updateById 会把它们一并写成 NULL
    recordMapper.update(new LambdaUpdateWrapper<InspectionRecord>()
        .eq(InspectionRecord::getId, id)
        .set(InspectionRecord::getIsVoid, true)
        .set(InspectionRecord::getVoidReason, reason));
    log.info("记录作废 recordId={} reason={}", id, reason);
    return InspectionRecordSummary.from(recordMapper.selectById(id));
}
```
类上加 `private static final Logger log = LoggerFactory.getLogger(InspectionService.class);`。

`pageRecords` 把 `.eq(InspectionRecord::getIsVoid, false)` 改为 `.eq(!includeVoid, InspectionRecord::getIsVoid, false)`，其余不动。

控制器：
```java
@PostMapping("/records/{id}/void")
@PreAuthorize("hasRole('ADMIN')")
public ApiResult<InspectionRecordSummary> voidRecord(@PathVariable Long id,
                                                     @Valid @RequestBody VoidRecordRequest request) {
    return ApiResult.ok(inspectionService.voidRecord(id, request.reason()));
}
```
`records(...)` 加 `@RequestParam(defaultValue = "false") boolean includeVoid` 并透传。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=RecordVoidApiTest`
Expected: 6 个用例全绿

- [ ] **Step 5: 跑全量后端测试，确认既有的 `recordQueryNeverReturnsVoidedRecords` 仍通过**

Run: `cd backend && mvn test`
Expected: 113 + 6 = 119 个测试全绿

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/pvqims/inspection backend/src/main/java/com/pvqims/common/exception/ErrorCode.java backend/src/test/java/com/pvqims/inspection/RecordVoidApiTest.java
git commit -m "feat(inspection): 管理员可凭原因作废记录" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 缺陷列表（FR-11）

**Files:**
- Create: `backend/src/main/java/com/pvqims/inspection/dto/DefectDetail.java`
- Modify: `backend/src/main/java/com/pvqims/inspection/mapper/DefectMapper.java`
- Create: `backend/src/main/java/com/pvqims/inspection/DefectService.java`
- Create: `backend/src/main/java/com/pvqims/inspection/DefectController.java`
- Test: `backend/src/test/java/com/pvqims/inspection/DefectListApiTest.java`

**Interfaces:**
- Consumes: 视图 `v_defect_detail`（列名见 `V1__init_schema.sql`）、`PageResult`、`IPage`/`Page`
- Produces:
  - `class DefectDetail`（POJO，字段见下；**必须是 POJO 带 setter，不能是 record**——MyBatis 按 setter 映射，record 的构造器自动映射需要 `argNameBasedConstructorAutoMapping`，本项目没开也不为本功能改全局配置）
  - `DefectMapper.selectDetailPage(IPage<DefectDetail> page, @Param("grade") String grade, @Param("status") String status, @Param("processName") String processName, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to)` → `IPage<DefectDetail>`
  - `DefectService.page(String grade, String status, String processName, LocalDate from, LocalDate to, long page, long pageSize)` → `PageResult<DefectDetail>`
  - API：`GET /api/defects?grade=&status=&processName=&from=&to=&page=&pageSize=` → `ApiResult<PageResult<DefectDetail>>`（全角色可读）

- [ ] **Step 1: 写失败测试**

新建 `DefectListApiTest`（辅助方法照抄自 `InspectionApiTest`；额外加一个 `createDefect(batchId, standardId, "CRACK", "MAJOR")` 辅助 = 录一条 FAIL 记录）：

```java
@Test
void listReturnsDefectWithJoinedNamesAndSnapshot() throws Exception {
    // 建项目「缺陷项目」/批次「DB-1」/RANGE 3.0-3.5 标准，工序名用全局唯一的 "层压-L1"，录实测 4.0 判 FAIL
    // GET /api/defects?processName=层压-L1 -> total = 1（测试库共享，必须带唯一筛选）
    // $.data.records[0].projectName = "缺陷项目"，batchNo = "DB-1"，itemName = "缺陷项"
    // defectType = "CRACK"，defectGrade = "MAJOR"，defectStatus = "OPEN"
    // inspectorName = 检验员 display_name（非 id）
    // handlerName 为 null（尚未认领）
    // stdLower = 3.0，stdUpper = 3.5（快照列来自记录，非当前标准）
}

@Test
void filtersByGradeStatusProcessAndDateRange() throws Exception {
    // 两个项目各一条缺陷：A 项目 PQC/层压-A1 CRACK+MAJOR，B 项目 OQC/包装-B1 BUBBLE+MINOR
    // 每条用例自建项目、批次与**全局唯一的工序名**——测试库 pvqims_test 是共享的，
    // 不加唯一筛选的绝对计数会被历史残留数据打破。
    // grade=MAJOR&processName=层压-A1 -> 1；processName=包装-B1 -> 1
    // grade=MINOR&processName=包装-B1 -> 1（证明 grade 过滤真的生效，而不是碰巧）
    // from=明天&processName=层压-A1 -> 0（缺陷时间取记录的 inspect_time）
}

@Test
void voidedSourceRecordHidesItsDefect() throws Exception {
    // 工序名用唯一值 "层压-V1"，录 FAIL 产生缺陷
    // GET /api/defects?processName=层压-V1 -> total = 1
    // 管理员作废该记录（走 Task 1 的端点）-> 同一查询 total = 0
    // 但 defect 表该行仍存在（按 record_id 查 count = 1，不物理删除）
}

@Test
void defectListIsOpenToAllRoles() throws Exception {
    // admin / engineer / inspector 三个令牌 GET /api/defects 都 200
}

@Test
void emptyResultReturnsEmptyPageNotError() throws Exception {
    // GET /api/defects?processName=不存在的工序-NONE -> total = 0，records 为空数组，HTTP 200
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=DefectListApiTest`
Expected: 404（`/api/defects` 不存在）

- [ ] **Step 3: 实现**

`DefectDetail` 字段（类型与视图列一一对应，全部 getter/setter）：

| Java 字段 | 类型 | 视图列 |
|---|---|---|
| `defectId` | `Long` | `defect_id` |
| `recordId` | `Long` | `record_id` |
| `projectId` | `Long` | `project_id` |
| `projectCode` / `projectName` | `String` | 同名 |
| `batchNo` | `String` | `batch_no` |
| `scene` / `processName` / `itemName` | `String` | 同名 |
| `serialNo` | `String` | `serial_no` |
| `inspectTime` | `LocalDateTime` | `inspect_time` |
| `stdLower` / `stdUpper` / `stdTarget` / `stdTolerance` | `BigDecimal` | 同名 |
| `stdUnit` / `defectType` / `defectGrade` / `defectStatus` / `handleNote` | `String` | 同名 |
| `resolvedAt` | `LocalDateTime` | `resolved_at` |
| `defectCreatedAt` | `LocalDateTime` | `defect_created_at` |
| `inspectorName` / `handlerName` | `String` | 同名 |

`DefectMapper.selectDetailPage`（`<script>` 必须以字符串第一个字符开始；`IPage` 必须是第一个参数）：

```java
@Select("""
    <script>
    SELECT d.defect_id, d.record_id, d.project_id, d.project_code, d.project_name, d.batch_no,
           d.scene, d.process_name, d.item_name, d.serial_no, d.inspect_time,
           d.std_lower, d.std_upper, d.std_target, d.std_tolerance, d.std_unit,
           d.defect_type, d.defect_grade, d.defect_status, d.handle_note, d.resolved_at,
           d.defect_created_at, d.inspector_name, d.handler_name
    FROM v_defect_detail d
    JOIN inspection_record r ON r.id = d.record_id
    WHERE r.is_void = 0
      <if test="grade != null and grade != ''">AND d.defect_grade = #{grade}</if>
      <if test="status != null and status != ''">AND d.defect_status = #{status}</if>
      <if test="processName != null and processName != ''">AND d.process_name = #{processName}</if>
      <if test="from != null">AND d.inspect_time &gt;= #{from}</if>
      <if test="to != null">AND d.inspect_time &lt; #{to}</if>
    ORDER BY d.defect_created_at DESC, d.defect_id DESC
    </script>
    """)
IPage<DefectDetail> selectDetailPage(IPage<DefectDetail> page,
                                     @Param("grade") String grade, @Param("status") String status,
                                     @Param("processName") String processName,
                                     @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
```

`DefectService.page`：`from` 取 `from.atStartOfDay()`，`to` 取 `to.plusDays(1).atStartOfDay()`（闭区间、不丢最后一秒，与 `InspectionService.pageRecords` 同一口径），`new Page<>(page, pageSize)`，返回 `PageResult<>(rows.getRecords(), rows.getTotal(), rows.getCurrent(), rows.getSize())`。

`DefectController`：`@RestController @RequestMapping("/api/defects")`，`@GetMapping` 方法签名与 `InspectionController.records` 同风格（`@RequestParam(required = false)` + 分页默认值）。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=DefectListApiTest`
Expected: 5 个用例全绿

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/pvqims/inspection backend/src/test/java/com/pvqims/inspection/DefectListApiTest.java
git commit -m "feat(defect): 缺陷列表按等级状态工序时间筛选" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: 缺陷状态机（FR-12）

**Files:**
- Create: `backend/src/main/java/com/pvqims/inspection/dto/DefectActionRequest.java`
- Modify: `backend/src/main/java/com/pvqims/common/exception/ErrorCode.java`（+ `DEFECT_STATUS_ILLEGAL`）
- Modify: `backend/src/main/java/com/pvqims/inspection/DefectService.java`（+ `process` / `verify` / `close`）
- Modify: `backend/src/main/java/com/pvqims/inspection/DefectController.java`（+ 三个端点）
- Test: `backend/src/test/java/com/pvqims/inspection/DefectTransitionApiTest.java`

**Interfaces:**
- Consumes: `DefectMapper`（`selectById` / `update`）、`LoginUser`、`LambdaUpdateWrapper`
- Produces:
  - `ErrorCode.DEFECT_STATUS_ILLEGAL(40013, "缺陷状态不允许该流转")`
  - `record DefectActionRequest(@Size(max = 255, message = "处理说明最多 255 字") String note)`
  - `DefectService.process(Long id, String note, LoginUser operator)` / `.verify(...)` / `.close(...)`，均返回 `void`
  - API：`POST /api/defects/{id}/process`、`/verify`、`/close`，body `{"note":"..."}` → `ApiResult<Void>`

**状态映射（本任务的核心契约，跳级靠它结构性不可能）：**

| 端点 | 允许的起始状态 | 目标状态 | 说明必填 |
|---|---|---|---|
| `/process` | `OPEN` | `PROCESSING` | 否 |
| `/process` | `VERIFIED` | `PROCESSING` | **是**（验证不通过退回） |
| `/verify` | `PROCESSING` | `VERIFIED` | 否 |
| `/close` | `VERIFIED` | `CLOSED` | **是**（SRS 4.6） |
| 其它 | — | — | 400 `DEFECT_STATUS_ILLEGAL` |

- [ ] **Step 1: 写失败测试**

新建 `DefectTransitionApiTest`（辅助方法照抄 `InspectionApiTest`，再加 `createDefect(...)` 返回 `defectId`）：

```java
@Test
void openToProcessingClaimsTheDefect() throws Exception {
    // engineer POST /api/defects/{id}/process {"note":"已安排返工"}
    // 200；库中 defect_status = PROCESSING，handler_id = engineer 的 id，
    // handle_note = "已安排返工"，resolved_at 仍为 NULL
}

@Test
void processingToVerifiedThenClosedSetsResolvedAt() throws Exception {
    // process -> verify -> close（close 带 note "返工完成并复检"）
    // 状态依次 PROCESSING / VERIFIED / CLOSED；只有 CLOSED 时 resolved_at 非空
}

@Test
void verifiedCanReturnToProcessingWithRejectionNote() throws Exception {
    // process -> verify -> process {"note":"边缘仍有气泡，重新返工"}
    // 状态回到 PROCESSING，handle_note 被新说明覆盖，resolved_at 仍为 NULL
}

@Test
void illegalTransitionsAreRejected() throws Exception {
    // 逐条断言 400 + code 40013，且库中状态不变：
    //   OPEN       -> /verify（跳级）
    //   OPEN       -> /close （跳级，AC-05 明写）
    //   PROCESSING -> /close （跳级）
    //   PROCESSING -> /process（重复认领）
    //   VERIFIED   -> /verify（重复验证）
    // 再走完 process -> verify -> close 到 CLOSED 后，三个端点各自再调一次，全部 400（终态）
    // message 必须同时含当前状态与动作的中文（如「待处理」与「验证」），不能只说「非法流转」
}

@Test
void closeRequiresHandleNote() throws Exception {
    // process -> verify -> close 不传 note / 传空串 / 传全空白：都 400 + message 含「处理说明」
    // 状态仍为 VERIFIED，resolved_at 仍为 NULL
}

@Test
void rejectRequiresHandleNote() throws Exception {
    // process(OPEN) -> verify -> process 不带 note：400 + message 含「处理说明」，状态仍 VERIFIED
}

@Test
void transitionWithoutNoteKeepsPreviousNote() throws Exception {
    // process 带 note "已安排返工" -> verify 不带 note
    // handle_note 仍是 "已安排返工"（未提供说明的流转不得清空已有说明）
}

@Test
void inspectorAndAdminCannotTransition() throws Exception {
    // 4.8：检验员与管理员只读。两个令牌对三个端点都 403 + code 40300，库中状态仍 OPEN
}

@Test
void transitioningMissingDefectReturns404() throws Exception {
    // POST /api/defects/999999/process -> 404 + code 40400
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=DefectTransitionApiTest`
Expected: 404（三个端点不存在）

- [ ] **Step 3: 实现**

`DefectService` 增加状态中文名静态映射（用于错误文案）与一个私有流转方法：

```java
private static final Map<String, String> STATUS_LABELS = Map.of(
    "OPEN", "待处理", "PROCESSING", "处理中", "VERIFIED", "待验证", "CLOSED", "已关闭");

/**
 * 流转的唯一样板：起始状态不在 allowedFrom 内即 400，其余字段一次 SET 完。
 * 三个公开方法各自只声明自己的 allowedFrom —— 跳级因此在结构上不可能，而不是靠 if 链拦。
 * 说明必填的判定写在方法内：关闭（目标 CLOSED）与验证不通过（VERIFIED → PROCESSING）
 * 都必须给原因，这不是三个调用点各记一遍的约定。
 */
private void transition(Long id, String action, Set<String> allowedFrom, String target,
                        String note, LoginUser operator) {
    Defect defect = defectMapper.selectById(id);
    if (defect == null) throw new BizException(ErrorCode.NOT_FOUND);
    String current = defect.getDefectStatus();
    if (!allowedFrom.contains(current)) {
        throw new BizException(ErrorCode.DEFECT_STATUS_ILLEGAL,
            "缺陷当前状态为「%s」，不能%s".formatted(STATUS_LABELS.get(current), action));
    }
    boolean noteRequired = "CLOSED".equals(target) || ("PROCESSING".equals(target) && "VERIFIED".equals(current));
    if (noteRequired && !StringUtils.hasText(note)) {
        throw new BizException(ErrorCode.PARAM_INVALID, "请填写处理说明");
    }
    LambdaUpdateWrapper<Defect> update = new LambdaUpdateWrapper<Defect>()
        .eq(Defect::getId, id)
        .set(Defect::getDefectStatus, target)
        .set(Defect::getHandlerId, operator.id())
        .set(Defect::getResolvedAt, "CLOSED".equals(target) ? LocalDateTime.now() : null);
    // 未填说明时不动 handle_note：单列覆盖写，清空会让上一环节的说明凭空消失
    if (StringUtils.hasText(note)) update.set(Defect::getHandleNote, note);
    defectMapper.update(update);
    log.info("缺陷流转 defectId={} {} -> {} operator={} note={}",
        id, current, target, operator.username(), note);
}
```

> `.set(Defect::getResolvedAt, null)` 在目标非 CLOSED 时会写 NULL——这是有意的：退回时 `resolved_at` 必须为 NULL 才符合「关闭时间」语义。用 `LambdaUpdateWrapper.set` 而不是实体 `updateById`，正是为了精确控制写哪些列。

三个公开方法只声明各自的起始状态集合：
```java
public void process(Long id, String note, LoginUser operator) {
    transition(id, "开始处理", Set.of("OPEN", "VERIFIED"), "PROCESSING", note, operator);
}
public void verify(Long id, String note, LoginUser operator) {
    transition(id, "验证", Set.of("PROCESSING"), "VERIFIED", note, operator);
}
public void close(Long id, String note, LoginUser operator) {
    transition(id, "关闭", Set.of("VERIFIED"), "CLOSED", note, operator);
}
```

`DefectController` 三个方法，全部 `@PreAuthorize("hasRole('ENGINEER')")`，body 为 `@Valid @RequestBody DefectActionRequest`，`@AuthenticationPrincipal LoginUser user`，返回 `ApiResult.ok(null)`。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd backend && mvn test -Dtest=DefectTransitionApiTest`
Expected: 9 个用例全绿

- [ ] **Step 5: 跑全量后端测试**

Run: `cd backend && mvn test`
Expected: 全部绿（含 Task 1、2 的用例）

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/pvqims/inspection backend/src/main/java/com/pvqims/common/exception/ErrorCode.java backend/src/test/java/com/pvqims/inspection/DefectTransitionApiTest.java
git commit -m "feat(defect): 缺陷状态机严格流转与说明约束" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: CSV 导出（FR-15）

**Files:**
- Create: `backend/src/main/java/com/pvqims/export/RecordCsvWriter.java`（全静态工具类，无 Spring 依赖）
- Create: `backend/src/main/java/com/pvqims/export/ExportController.java`
- Modify: `backend/src/main/java/com/pvqims/inspection/InspectionService.java`（+ `listForExport`，并把筛选条件抽成私有 `recordFilter`）
- Modify: `backend/src/main/java/com/pvqims/common/exception/ErrorCode.java`（+ `EXPORT_TOO_LARGE`）
- Modify: `backend/src/main/resources/application.yml`（+ `pvqims.export.max-rows: 10000`）
- Test: `backend/src/test/java/com/pvqims/export/RecordCsvWriterTest.java`（纯函数直测，不起 Spring）
- Test: `backend/src/test/java/com/pvqims/export/RecordCsvApiTest.java`（端点、权限、上限、筛选）

**Interfaces:**
- Consumes: `InspectionRecordMapper`、`InspectionRecord`、`BigDecimal`
- Produces:
  - `ErrorCode.EXPORT_TOO_LARGE(40014, "导出数据超过上限，请缩小筛选范围")`
  - `RecordCsvWriter.write(List<InspectionRecord> records)` → `String`（静态方法，含 BOM，行间 CRLF，末行也带 CRLF）
  - `InspectionService.listForExport(Long projectId, Long batchId, String scene, String processName, String result, LocalDate from, LocalDate to, int maxRows)` → `List<InspectionRecord>`（最多返回 `maxRows + 1` 条，调用方据此判断是否超限）
  - API：`GET /api/export/records.csv`（筛选参数同 `/api/inspection/records`，**无分页参数**）→ `text/csv;charset=UTF-8` 附件，文件名 `records-YYYYMMDD.csv`

**CSV 列（表头固定，顺序固定）：**
`记录ID,检验时间,项目ID,批次ID,场景,工序,检验项,序列号,实测值,结论,标准下限,标准上限,标准值,公差,单位,判定,是否作废,作废原因,备注`

其中 `场景` 输出中文（来料检验/过程检验/出货检验）、`判定` 输出中文（合格/不合格）、`是否作废` 输出 是/否；时间格式 `yyyy-MM-dd HH:mm:ss`；数值用 `BigDecimal.toPlainString()`（不要 `toString()`，避免科学计数法）。

- [ ] **Step 1: 写纯函数的失败测试**

`RecordCsvWriterTest` 直接 `new InspectionRecord()` 造行、调 `RecordCsvWriter.write(...)`，**不用 MockMvc**——转义与注入防护的边界值太多，每例启动一次 Spring 上下文不值得：

```java
@Test
void csvStartsWithBomAndUsesCrLf() {
    // write(List.of(record)) 以 "﻿" 开头；按 "\r\n" split 后无残留孤立 "\n"
}

@Test
void headerRowHasExpectedColumns() {
    // 去掉 BOM 后第一行等于 19 列表头（见上）
}

@Test
void csvEscapesCommaQuoteAndNewline() {
    // itemName = "外观,尺寸"，remark = "a\"b"，serialNo = "line1\nline2"
    // 断言字段被双引号包住、内部引号成对加倍；再用一个极简解析函数还原出原值
}

@Test
void textFieldStartingWithEqualsIsNeutralised() {
    // remark = "=1+1" -> 单元格为 "'=1+1"；"+x" "-x" "@x" 开头同样加前缀
}

@Test
void negativeNumbersAreNotNeutralised() {
    // measuredValue = -5 -> 输出 "-5"，不带 "'" 前缀（负号是符号不是公式）
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd backend && mvn test -Dtest=RecordCsvWriterTest`
Expected: 编译失败（`RecordCsvWriter` 不存在）

- [ ] **Step 3: 实现 `RecordCsvWriter`**

`public final class RecordCsvWriter`（私有构造器，全静态）。三个私有方法：

- `number(BigDecimal value)` — `null` → `""`，否则 `toPlainString()`，**不加任何前缀**。
- `text(String value)` — `null`/空 → `""`；首字符是 `= + - @ \t \r` 之一时前置 `'`；随后若含 `,` `"` `\n` `\r` 则整体加双引号并把内部 `"` 替换为 `""`。
- 场景/判定/是否作废的中文映射用 `private static final Map<String, String>`。

`write()` 用 `StringBuilder`：append `"﻿"` + 表头 + `"\r\n"`，每条记录一行，末行同样带 `"\r\n"`。

- [ ] **Step 4: 跑纯函数测试确认通过**

Run: `cd backend && mvn test -Dtest=RecordCsvWriterTest`
Expected: 5 个用例全绿

- [ ] **Step 5: 写端点的失败测试**

`RecordCsvApiTest`，类注解**额外加** `@TestPropertySource(properties = "pvqims.export.max-rows=2")`——超限分支用 3 条数据就能覆盖，不必插 10001 行（代价是本类会新建一个 Spring 上下文）：

```java
@Test
void returnsCsvAttachmentWithBom() {
    // 录 1 条记录后 GET /api/export/records.csv?batchId=<本用例新建的批次>
    // Content-Type 含 text/csv；Content-Disposition 含 attachment、records- 前缀与 .csv 后缀
    // 响应字节前三位是 EF BB BF
}

@Test
void exportHonoursFilters() {
    // 一个批次两条记录（PQC/层压 PASS、OQC/包装 FAIL）
    // 所有请求都带 batchId=<本用例批次>（测试库共享，唯一 batchId 是唯一的隔离手段）
    // result=FAIL -> 1 条数据行；scene=OQC -> 1 条；from=明天 -> 只导出表头
}

@Test
void exportExcludesVoidedRecords() {
    // 同一批次录 1 条 -> 管理员作废 -> 带 batchId 导出只剩表头
}

@Test
void exportBeyondCapIsRejected() {
    // max-rows=2，同一批次录 3 条 -> 带 batchId 导出 400 + code 40014 + message 含「上限」
}

@Test
void onlyEngineerCanExport() {
    // admin / inspector -> 403 + code 40300；engineer -> 200
}
```

- [ ] **Step 6: 跑端点测试确认失败**

Run: `cd backend && mvn test -Dtest=RecordCsvApiTest`
Expected: 404（`/api/export/records.csv` 不存在）

- [ ] **Step 7: 实现 `listForExport` 与 `ExportController`**

```java
public List<InspectionRecord> listForExport(Long projectId, Long batchId, String scene, String processName,
                                            String result, LocalDate from, LocalDate to, int maxRows) {
    // 不走 Page：拦截器全局 maxLimit 是 100，而这里要的是「上限 + 1 探测超限」。
    // last("LIMIT n") 拼在 ORDER BY 之后，n 来自配置的 int，不构成注入面。
    LambdaQueryWrapper<InspectionRecord> query = recordFilter(projectId, batchId, scene, processName,
        result, from, to, Boolean.FALSE);
    return recordMapper.selectList(query.last("LIMIT " + (maxRows + 1)));
}
```
`isVoid` 恒为 `false`：作废记录不参与统计（BR-08、AC-06）。

`ExportController`（`@RestController @RequestMapping("/api/export")`，方法上 `@PreAuthorize("hasRole('ENGINEER')")`）：
- 用 `@RequestParam(required = false)` 收筛选参数，调 `listForExport`；
- `records.size() > maxRows` → `throw new BizException(ErrorCode.EXPORT_TOO_LARGE, "导出数据超过 %d 条，请缩小筛选范围".formatted(maxRows))`；
- 返回 `ResponseEntity<byte[]>`：body 是 `writer.write(records).getBytes(StandardCharsets.UTF_8)`，header 为
  `Content-Type: text/csv;charset=UTF-8`、`Content-Disposition: attachment; filename="records-20261118.csv"`（**文件名只用 ASCII**）。

`maxRows` 由构造器注入 `@Value("${pvqims.export.max-rows:10000}")`；`application.yml` 增加：
```yaml
pvqims:
  export:
    max-rows: 10000
```

- [ ] **Step 8: 跑端点测试确认通过**

Run: `cd backend && mvn test -Dtest=RecordCsvApiTest`
Expected: 5 个用例全绿

- [ ] **Step 9: 跑全量后端测试**

Run: `cd backend && mvn test`
Expected: 全部绿（既有 113 个必须保持通过）

- [ ] **Step 10: 提交**

```bash
git add backend/src/main/java/com/pvqims/export backend/src/main/java/com/pvqims/inspection/InspectionService.java backend/src/main/java/com/pvqims/common/exception/ErrorCode.java backend/src/main/resources/application.yml backend/src/test/java/com/pvqims/export
git commit -m "feat(export): 校验记录按筛选条件导出 CSV" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: 缺陷处理页 P5（FR-11、FR-12 前端）

**Files:**
- Modify: `frontend/src/api/types.ts`（+ `DefectStatus`、`DefectDetail`）
- Create: `frontend/src/api/defects.ts`
- Modify: `frontend/src/utils/labels.ts`（+ `DEFECT_STATUS_LABELS`）
- Modify: `frontend/src/utils/labels.test.ts`（追加一个 `it` 块，不动既有断言）
- Create: `frontend/src/pages/defects/DefectListPage.tsx`
- Create: `frontend/src/pages/defects/DefectListPage.test.tsx`
- Modify: `frontend/src/routes/index.tsx`（从 `pages` 数组里删掉 `{ path: 'defects', title: '缺陷处理' }`——该数组整体映射成 `PlaceholderPage`；在 `records` 同级加 `{ path: 'defects', element: <DefectListPage /> }`，全角色可读，不包 `RequireRole`）
- Modify: `frontend/src/test/handlers.ts`（+ `GET /api/defects` 默认处理器）

**Interfaces:**
- Consumes: `apiClient`、`ApiError`、`LabeledSelect`、`LabeledField`、`FormErrorSnackbar`、`shadow.card`、`DEFECT_*_LABELS`
- Produces:
  - `type DefectStatus = 'OPEN' | 'PROCESSING' | 'VERIFIED' | 'CLOSED'`
  - `interface DefectDetail`（字段与后端 `DefectDetail` 同名，`defectId` 为数字）
  - `listDefects(params: DefectQuery): Promise<PageResult<DefectDetail>>`
  - `processDefect(id: number, note?: string): Promise<void>` / `verifyDefect(...)` / `closeDefect(...)`
  - `DEFECT_STATUS_LABELS: Record<DefectStatus, string>` = 待处理 / 处理中 / 待验证 / 已关闭

- [ ] **Step 1: 写失败测试**

`frontend/src/pages/defects/DefectListPage.test.tsx`：

```tsx
it('列出缺陷并用中文显示等级与状态', async () => {
  seedSession('ENGINEER'); renderAt(routes, '/defects');
  // 行内可见：项目名、批号、工序、检验项、隐裂、严重、待处理、检验员姓名
});

it('工程师按状态看到不同操作按钮', async () => {
  // OPEN 行有「认领」；PROCESSING 行有「提交整改」；VERIFIED 行有「验证通过」与「验证不通过」；CLOSED 行无按钮
});

it('检验员与管理员的缺陷列表没有操作按钮', async () => {
  // 两个角色都查不到「认领」「提交整改」「验证通过」「验证不通过」任何一个按钮
});

it('认领发出 process 请求并刷新列表', async () => {
  // 点「认领」-> 弹窗填说明 -> 确认 -> POST /api/defects/1/process，body {"note":"已安排返工"}
  // 成功后 invalidate 缺陷查询（断言 GET /api/defects 被再次调用）
});

it('验证通过即关闭，必须填处理说明，空说明不发请求', async () => {
  // VERIFIED 行点「验证通过」-> 弹窗 -> 不填说明点确认 -> 显示「请填写处理说明」，POST 未发出
  // 填「返工完成并复检」-> POST /api/defects/{id}/close，body {"note":"返工完成并复检"}
});

it('验证不通过必须填退回原因', async () => {
  // VERIFIED 行点「验证不通过」-> 不填说明 -> 「请填写处理说明」，POST 未发出
  // 填「边缘仍有气泡」-> POST /api/defects/{id}/process，body {"note":"边缘仍有气泡"}
});

it('筛选把等级与状态传给接口', async () => {
  // 选「致命」+「待处理」-> 断言查询参数 grade=CRITICAL&status=OPEN
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && pnpm vitest run src/pages/defects`
Expected: 失败（页面不存在）

- [ ] **Step 3: 实现**

`DefectListPage` 结构（与 `RecordListPage` 同构，保持视觉一致）：

- 顶部 `Typography variant="h3"` 标题「缺陷处理」；
- 筛选行 `Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'flex-start' }}`：等级 `LabeledSelect`、状态 `LabeledSelect`、工序 `LabeledSelect`（选项来自**当前页数据的去重**，`''` = 全部）、开始/结束日期 `LabeledField type="date"`；
- 表格列：项目 / 批号 / 场景 / 工序 / 检验项 / 缺陷类型 / 等级（`Chip`，CRITICAL 用 `error`、MAJOR 用 `warning`、MINOR 用 `default`）/ 状态 / 检验时间 / 处理说明 / 处理人 / 操作；
- 操作列按状态渲染，仅 `ENGINEER` 可见。**按钮文案即目标状态，不出现单独的「关闭」按钮**——`VERIFIED` 行上的「验证通过」本身就是 VERIFIED→CLOSED 那一步，再给一个「关闭」会让工程师以为中间还有一次确认，也容易让人在 OPEN 行上找关闭按钮：

| 状态 | 按钮 | 调用 |
|---|---|---|
| `OPEN` | 认领 | `processDefect` |
| `PROCESSING` | 提交整改 | `verifyDefect` |
| `VERIFIED` | 验证通过 / 验证不通过 | `closeDefect` / `processDefect` |
| `CLOSED` | — | — |

- 每个写操作走同一个 `DefectActionDialog`（本文件内私有组件）：`LabeledField label="处理说明"`，`required` 由动作决定（认领/提交整改为否，验证通过、验证不通过为是——后端对这三种情况的必填判定见 Task 3 的 `noteRequired`），确认前用 `findMissing`/`missingMessage` 拦一道，错误用 `FormErrorSnackbar`；成功后 `queryClient.invalidateQueries({ queryKey: ['defects'] })`；
- 加载中/错误/空结果三态都有占位行（照抄 `RecordListPage` 的三态写法）。

`DefectQuery`：`{ grade?: DefectGrade; status?: DefectStatus; processName?: string; from?: string; to?: string; page: number; pageSize: number }`。

`handlers.ts` 追加 `GET /api/defects` 默认处理器，返回两条行（一条 `OPEN` CRITICAL、一条 `VERIFIED` MINOR），字段与 `DefectDetail` 对齐。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && pnpm vitest run src/pages/defects`
Expected: 7 个用例全绿

- [ ] **Step 5: 跑 lint / 类型 / 格式化**

Run: `cd frontend && pnpm lint && pnpm exec prettier --check src && pnpm build`
Expected: 三项全过

- [ ] **Step 6: 提交**

```bash
git add frontend/src
git commit -m "feat(defect): 缺陷处理页按状态流转" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: 记录页作废与导出（FR-10、FR-15 前端）

**Files:**
- Create: `frontend/src/api/export.ts`
- Modify: `frontend/src/api/inspection.ts`（+ `voidRecord`、`RecordQuery.includeVoid`）
- Modify: `frontend/src/api/types.ts`（同 Task 5 的文件；此处 + `InspectionRecordSummary.voidReason`）
- Create: `frontend/src/pages/inspection/VoidRecordDialog.tsx`
- Modify: `frontend/src/pages/inspection/RecordListPage.tsx`
- Modify: `frontend/src/pages/inspection/RecordListPage.test.tsx`（追加用例，不动既有断言）

**Interfaces:**
- Consumes: `useAuthStore`（角色）、`apiClient`、`ApiError`、`LabeledField`、`FormErrorSnackbar`
- Produces:
  - `voidRecord(id: number, reason: string): Promise<InspectionRecordSummary>`
  - `downloadRecordsCsv(params: Omit<RecordQuery, 'page' | 'pageSize'>): Promise<void>`（**用 `fetch` 而非 `apiClient`**：`apiClient` 的响应拦截器统一取 `res.data.data`，二进制响应没有这层信封；且原生 `<a href>` 带不上 Bearer 令牌）
  - `VoidRecordDialog` 组件：`{ open, recordId, onClose, onDone }`

- [ ] **Step 1: 写失败测试**

`RecordListPage.test.tsx` 追加：

```tsx
it('管理员看到作废按钮，作废必须填原因', async () => {
  seedSession('ADMIN'); renderAt(routes, '/records');
  // 行内出现「作废 #101」按钮 -> 点击 -> 弹窗
  // 不填原因点确认 -> 显示「请填写作废原因」，POST 未发出
  // 填「录入错批次」-> POST /api/inspection/records/101/void，body {"reason":"录入错批次"}
});

it('检验员与工程师看不到作废按钮', async () => {
  // 两个角色都 queryByRole('button', { name: /作废/ }) 为 null
});

it('作废行默认不显示，打开含作废开关后显示并标注原因', async () => {
  // 默认查询不带 includeVoid；切开关后最后一次请求带 includeVoid=true
  // 返回 isVoid=true 的行显示「已作废」Chip 与作废原因
});

it('工程师看到导出按钮，导出带上当前筛选', async () => {
  // mock URL.createObjectURL 与 fetch
  // 选「不合格」+ 开始日期 2026-11-01 -> 点「导出 CSV」
  // 断言 fetch 的 URL 含 result=FAIL&from=2026-11-01，且 createObjectURL 被调用一次
});

it('导出失败时显示后端的中文原因', async () => {
  // fetch 返回 400 + {"code":40014,"message":"导出数据超过 10000 条，请缩小筛选范围"}
  // 页面显示该 message，而不是「网络异常」
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd frontend && pnpm vitest run src/pages/inspection/RecordListPage.test.tsx`
Expected: 新用例失败（按钮不存在）

- [ ] **Step 3: 实现**

`api/export.ts`：

```ts
/**
 * 导出走原生 fetch：apiClient 的响应拦截器统一解包 res.data.data，
 * 二进制响应没有这层信封；而 <a href> 导航带不上 Authorization 头（认证是 Bearer 不是 Cookie）。
 */
export async function downloadRecordsCsv(params: ...): Promise<void> {
  const query = new URLSearchParams();
  // 只拼有值的参数，与列表查询同口径
  const token = useAuthStore.getState().token;
  const res = await fetch(`/api/export/records.csv?${query}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) {
    // 错误响应是 JSON 信封，按文本读出来才能拿到后端中文原因
    const body = (await res.json().catch(() => null)) as { code?: number; message?: string } | null;
    throw new ApiError(body?.code ?? 0, body?.message ?? '导出失败，请稍后重试');
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'records.csv';
  link.click();
  URL.revokeObjectURL(url);
}
```

`RecordListPage` 增加：
- `includeVoid` 状态（默认 `false`）→ 进 `RecordQuery`；筛选行加一个 `FormControlLabel` + `Switch`「含作废」；
- `canVoid = role === 'ADMIN'`，`canExport = role === 'ENGINEER'`；
- 表格加一列「状态」：`isVoid ? <Chip size="small" color="default" label="已作废" /> : null`；作废行整行 `sx={{ opacity: 0.6 }}`；
- **新增「作废原因」列，仅在 `includeVoid` 为真时渲染**——「备注」是录入时填的备注，作废原因是管理员填的，两者语义不同，塞进同一列会让人以为备注就是作废理由；
- 操作列：`canVoid && !record.isVoid` 时渲染「作废 #{id}」按钮，打开 `VoidRecordDialog`；
- 筛选行末尾：`canExport` 时渲染「导出 CSV」按钮，`onClick` 调 `downloadRecordsCsv({ result, from, to })`，失败时把 `ApiError.message` 交给 `FormErrorSnackbar`；导出中禁用按钮防重复点击。

`VoidRecordDialog`：`Dialog` + `DialogTitle`「作废记录 #{id}」+ `LabeledField label="作废原因" required` + `DialogActions`（取消 / 确认作废），提交前 `findMissing` 拦截，`error` 用 `FormErrorSnackbar`。成功时 `invalidateQueries({ queryKey: ['records'] })` 再 `onDone()`。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd frontend && pnpm vitest run src/pages/inspection/RecordListPage.test.tsx`
Expected: 既有 5 个 + 新增 5 个全绿

- [ ] **Step 5: 跑全量前端校验**

Run: `cd frontend && pnpm vitest run && pnpm lint && pnpm exec prettier --check src && pnpm build`
Expected: 全部测试全绿（既有用例一个不减，另加本阶段新增 12 个），lint/prettier/build 全过

- [ ] **Step 6: 更新项目状态文档**

`CLAUDE.md`「项目现状」与 `README.md` 的功能清单：把缺陷闭环、CSV 导出从未实现移到已实现，注明未实现只剩看板与演示数据。

- [ ] **Step 7: 提交**

```bash
git add frontend/src CLAUDE.md README.md
git commit -m "feat(defect): 记录页支持作废与导出 CSV" -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 自检记录

**1. 规格覆盖**

| 需求 | 落点 |
|---|---|
| FR-10 记录作废（管理员、原因必填、记录保留） | Task 1、Task 6 |
| FR-11 缺陷列表（等级/状态/工序/时间段筛选） | Task 2、Task 5 |
| FR-12 缺陷处理与验证（四态流转、退回、关单必填说明） | Task 3、Task 5 |
| FR-15 CSV 导出（按当前筛选、工程师） | Task 4、Task 6 |
| AC-05 跳级被拒绝 | Task 3 `illegalTransitionsAreRejected` |
| AC-06 统计排除作废 | Task 1 默认列表过滤 + Task 2 缺陷列表过滤 + Task 4 导出过滤 |
| SRS 4.8 角色矩阵 | Task 1/3/4 的 403 用例 + Task 5/6 的按钮可见性用例 |
| P5 页面 | Task 5 |

**2. 明确不做（避免范围蔓延）**：不给记录页新增项目/批次/场景/工序筛选（FR-15 只要求「按当前筛选条件」；多维切片属 P6 看板）；不建缺陷流转表（SRS 5.8 明写写日志）；不做 Excel/Word 导出（SRS 2.3 范围外）；不新增 `defect` 的作废列（会动冻结的迁移）。

**3. 类型一致性**：`DefectDetail` 前后端字段同名同义；`DefectStatus` 四值与 SRS 5.12 一致；`ErrorCode` 新增 40012/40013/40014 三码在 Task 1/3/4 各自唯一；`pageRecords` 的 `includeVoid` 在 Task 1 改后端签名、Task 6 前端传参，两处一致；Task 5 与 Task 6 都改 `api/types.ts`，前者加 `DefectStatus`/`DefectDetail`，后者加 `InspectionRecordSummary.voidReason`（与 Task 1 的后端字段对应），互不冲突。

**4. Review Focus 回查**（每条都在拥有该代码的任务里有对应测试）：

| Review Focus | 测试 |
|---|---|
| CSV 在 Excel 里乱码 | Task 4 `csvStartsWithBomAndUsesCrLf`（纯函数） + `returnsCsvAttachmentWithBom`（端点） |
| 逗号/引号/换行导致列错位 | Task 4 `csvEscapesCommaQuoteAndNewline` |
| 跳级流转被接受 | Task 3 `illegalTransitionsAreRejected`（6 种跨级组合） |
| 作废后缺陷仍留在列表 | Task 2 `voidedSourceRecordHidesItsDefect` |
| 作废记录仍出现在默认列表 / 重复作废覆盖原因 | Task 1 `adminVoidsRecordAndItLeavesDefaultList`、`voidingTwiceIsRejected` |
| 表单值以 `=` 开头被当公式 | Task 4 `textFieldStartingWithEqualsIsNeutralised` |
| 负数被误加前缀 | Task 4 `negativeNumbersAreNotNeutralised` |
| 退回不给原因 | Task 3 `rejectRequiresHandleNote` |
| 导出超上限被静默截断 | Task 4 `exportBeyondCapIsRejected` |

**5. 篇幅**：979 行，与阶段四计划（736 行）同一量级；代码块集中在「唯一的流转样板」与「CSV 转义」两处真正需要决定写法的算法，其余是签名、测试名与断言值。

**6. 一处被有意压掉的复杂度**：SRS 6.2 的 P4 交互要求里有「Enter 跳到下一项，Ctrl+Enter 保存」「保存并继续录入」，阶段四未实现且不在本阶段范围——本阶段不碰录入页，改动集中在作废/缺陷/导出三处，避免计划外扩。
