# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目现状

仓库目前**只有文档，没有代码**：`docs/需求规格说明书.md`（PVQIMS-SRS-001，V2.0 评审稿）。没有构建、测试、lint 工具，因此在技术栈落地前**不要臆造命令**；选定栈后再补全本节。

`docs/需求规格说明书.md` 是唯一需求来源。实现前先读对应章节：

| 章节 | 内容 |
|---|---|
| 2.3 | 范围与**明确的范围外清单**（不做权限框架、Excel 导入、MES/ERP 对接、SPC 等） |
| 3 | 业务闭环、判定时序、缺陷状态机 |
| 4 | 16 条功能需求（FR-01~16）、15 个用例、业务规则 BR-01~10 |
| 5 | 5 张表结构、索引、关键 SQL、枚举字典 |
| 6 | 7 个页面、8 张 ECharts 图 |
| 8 | 验收标准 AC-01~10 |
| 附录 B | REST 接口清单（路径与角色） |

## 必须遵守的架构约束

这些是跨文件的设计决定，改动前先确认没有违反 SRS：

- **5 张表**：`app_user`、`project`、`batch`、`inspection_standard`、`inspection_record`。缺陷不是独立表，而是 `result='FAIL'` 的记录行（SRS 5.7）。若课程评分要求表数量，SRS 10 章给出备案：拆出第 6 张 `defect` 表，数据模型保持一致。
- **判定只在服务端执行**（BR-01）。前端结果仅展示，提交后以服务端重算为准。
- **记录保存标准快照**（`std_lower/std_upper/std_target/std_tolerance/std_unit`）。改标准不得回溯历史记录（BR-02、AC-04）。
- **只作废不物理删除**（BR-08）；作废记录不参与任何统计。
- **三场景共用一套模型**：IQC/PQC/OQC 是 `scene` 字段，不是三套流程、三套页面（SRS 3.1、10 章风险项）。
- **缺陷状态严格流转**：OPEN → PROCESSING → VERIFIED → CLOSED，VERIFIED 可退回 PROCESSING，禁止跳级（BR-05）。
- **权限职责分离**：工程师定标准/处理缺陷、检验员录数据、管理员管账号；检验员不能改标准，管理员不能改检验数据（4.8）。至少做到接口级角色校验，不能只隐藏菜单。
- 数据库 MySQL 8.0+、InnoDB、utf8mb4、时间为 Asia/Shanghai、密码 BCrypt。
- 看板 8 张图用 ECharts，实时查询、不做预聚合表；帕累托累计线终点 100%，控制图控制限取标准上下限（6.3）。
- 判定规则集中在一个服务类，新增判定方式只改一处（7 章可维护性）。

术语约定：界面文案用"校验"，质量体系角色与概念（检验、检验员、来料检验）用"检验"（SRS 1.4 注）。

## 协作约束（用户明确要求）

1. **禁止 `git worktree`** 开发，除非用户明确特批。
2. **过程留痕**：在 `dev-notes/ch01.md` 中**逐阶段**追加记录——brainstorm 定稿、计划评审通过、每个任务完成、code review 结论、finish——每段记四样：用户关键原话、关键产出（spec/plan 路径、评审结论）、被拒绝或纠偏的内容、翻车与返工。**禁止收尾时一次性补记**。
3. **库/框架/API 用法先查 Context7 MCP** 的最新官方文档，不要凭记忆写。
4. **提交信息**：`type(scope): description`
   - `type` 仅限 `feat` / `fix` / `docs` / `refactor` / `perf` / `test` / `chore`
   - `scope` 用真实模块名，禁止 `misc` / `stuff`
   - `description` 用中文、无句号、≤ 50 字
   - 破坏性变更用 `feat(scope)!:` 或脚注 `BREAKING CHANGE:`
   - （既有历史提交早于本规则，为英文；新提交按此执行）
5. **完结交付三样**：功能演示命令、测试结果、`dev-notes` 路径。
