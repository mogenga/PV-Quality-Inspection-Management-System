# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目现状

前后端均有代码，阶段一（工程骨架与认证）与阶段二（项目与批次）已完成。

```
backend/    Spring Boot 4.1.2 + MyBatis-Plus + MySQL + Redis，Maven 单模块
frontend/   React 19 + Vite 8 + TypeScript 6 + MUI 9.5，pnpm
design/     视觉合同与令牌（aesthetic 生成，视觉唯一事实源）
docs/       需求与设计文档
```

**已实现**：登录/登出（JWT + Redis 可撤销会话）、用户管理（FR-01、FR-02）、项目与批次（FR-03、FR-04）。
**未实现**：标准配置、校验录入与判定、缺陷闭环、看板、导出。这些页面目前是占位页。

`docs/需求规格说明书.md` 是唯一需求来源。实现前先读对应章节：

| 章节 | 内容 |
|---|---|
| 2.3 | 范围与**明确的范围外清单**（不做权限框架、Excel 导入、MES/ERP 对接、SPC 等） |
| 3 | 业务闭环、判定时序、缺陷状态机 |
| 4 | 16 条功能需求（FR-01~16）、15 个用例、业务规则 BR-01~10 |
| 5 | 6 张表结构（含 defect）、2 个视图、索引、关键 SQL、枚举字典 |
| 6 | 7 个页面、8 张 ECharts 图 |
| 8 | 验收标准 AC-01~10 |
| 附录 B | REST 接口清单（路径与角色） |

## 必须遵守的架构约束

这些是跨文件的设计决定，改动前先确认没有违反 SRS：

- **6 张表**：`app_user`、`project`、`batch`、`inspection_standard`、`inspection_record`、`defect`（SRS V2.1 5.7–5.8）。判定为 FAIL 的记录在同一事务内生成 1:1 的缺陷行；缺陷统计从 `defect` 聚合，明细走视图 `v_defect_detail`，合格率走 `v_batch_pass_rate`。缺陷状态流转的操作人/时间写应用日志，不建流转表。
- **判定只在服务端执行**（BR-01）。前端结果仅展示，提交后以服务端重算为准。
- **记录保存标准快照**（`std_lower/std_upper/std_target/std_tolerance/std_unit`）。改标准不得回溯历史记录（BR-02、AC-04）。
- **只作废不物理删除**（BR-08）；作废记录不参与任何统计。
- **三场景共用一套模型**：IQC/PQC/OQC 是 `scene` 字段，不是三套流程、三套页面（SRS 3.1、10 章风险项）。
- **缺陷状态严格流转**：OPEN → PROCESSING → VERIFIED → CLOSED，VERIFIED 可退回 PROCESSING，禁止跳级（BR-05）。
- **权限职责分离**：工程师定标准/处理缺陷、检验员录数据、管理员管账号；检验员不能改标准，管理员不能改检验数据（4.8）。至少做到接口级角色校验，不能只隐藏菜单。
- 数据库 MySQL 8.0+、InnoDB、utf8mb4、时间为 Asia/Shanghai、密码 BCrypt。
- 看板 8 张图用 ECharts，实时查询、不做预聚合表；帕累托累计线终点 100%，控制图控制限取标准上下限（6.3）。
- 判定规则集中在一个服务类，新增判定方式只改一处（7 章可维护性）。
- **视觉令牌唯一来源**：`design/aesthetic/tokens.css`。前端颜色/字号/圆角/阴影必须引用令牌，不得写裸十六进制值；MUI 主题由 `frontend/src/theme/muiTheme.ts` 从令牌构建，`tokenSource.test.ts` 会校验它与设计源字节一致。

术语约定：界面文案用"校验"，质量体系角色与概念（检验、检验员、来料检验）用"检验"（SRS 1.4 注）。

## 命令

```bash
# 后端（需先配好 application-local.yml）
cd backend
mvn spring-boot:run
mvn test

# 前端
cd frontend
pnpm dev
pnpm vitest run
pnpm lint
pnpm build
pnpm exec prettier --check src
```

后端测试连本机 `pvqims_test` 库与 Redis 第 15 号库；凭据在 git-ignored 的 `application-test.yml` 里。

## 协作约束（用户明确要求）

1. **禁止 `git worktree`** 开发，除非用户明确特批。
2. **库/框架/API 用法先查 Context7 MCP** 的最新官方文档，不要凭记忆写。
3. **提交信息**：`type(scope): description`
   - `type` 仅限 `feat` / `fix` / `docs` / `refactor` / `perf` / `test` / `chore`
   - `scope` 用真实模块名，禁止 `misc` / `stuff`
   - `description` 用中文、无句号、≤ 50 字
   - 破坏性变更用 `feat(scope)!:` 或脚注 `BREAKING CHANGE:`
   - （既有历史提交早于本规则，为英文；新提交按此执行）
