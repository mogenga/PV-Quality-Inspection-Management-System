# 技术选型设计 · 光伏组件校验数字化平台

- 文档版本：V1.1
- 日期：2026-11-13
- 状态：待评审
- 关联需求：`docs/需求规格说明书.md`（PVQIMS-SRS-001 V2.0）

---

## 1. 目的与范围

本文确定**技术栈与工程骨架**。数据库表结构、接口契约、页面交互细节不在本文范围，由后续设计与 SRS 第 5、6 章承接。

本文回答三个问题：

1. 用什么框架、什么版本；
2. 哪些是用户已定的、哪些是设计者决定的；
3. 哪些尚未决定，需要后续补齐。

---

## 2. 目标 / 非目标

**目标**

- 一个人能在 Windows 开发机上从零跑起来，不需要额外基础设施；
- 覆盖 SRS 全部 P0 需求（16 条 FR 中的 12 条 P0）；
- 业务表控制在 **6 张以内**；
- 权限做到接口级角色校验，满足 BR-07 与 AC-07。

**非目标**

- 不做微服务、不做多租户、不做通用权限框架（与 SRS 2.3 一致）；
- 不做代码生成器、动态表单、工作流引擎；
- 不追求高并发架构（SRS 7 章指标为 10 人并发）。

**反目标（即使做出来也算失败）**

- 数据库设计被框架绑架，业务表数量失控；
- 需要改框架源码才能实现业务；
- 前后端版本互相不兼容，环境装不起来。

---

## 3. 环境约束（已实测）

| 项 | 实测结果 | 影响 |
|---|---|---|
| JDK | 21.0.12.1 LTS | Spring Boot 4 要求 Java 17+，兼容至 26 ✅ |
| Maven | 3.9.16 | Boot 4 要求 3.6.3+ ✅ |
| Node | 24.14.1 | Vite 8 要求 `^20.19 \|\| >=22.12` ✅ |
| pnpm | 11.28.5 | 前端包管理器 |
| MySQL | 8.0.41，本机安装 | 满足 SRS "8.0 及以上" |
| Redis | 5.0.14.1，本机常驻服务，`127.0.0.1:6379` | 复用现有实例，不另起容器 |
| Redis 认证 | 已启用密码认证 | **凭据不进仓库**，通过环境变量注入 |
| Docker | 29.9.2 | 备用，本方案不依赖 |

---

## 4. 确定的技术栈

### 4.1 后端

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | **4.1.2** | 4.1.x 最新稳定版；要求 Java 17+，本机 21 满足 |
| Spring Framework | 7.0.10 | Boot 4.1.2 托管 |
| Spring Security | 7.1.2 | Boot 4.1.2 托管；BCrypt、方法级授权 |
| MyBatis-Plus | **3.5.18.1** | 使用 `mybatis-plus-spring-boot4-starter`；配套 `mybatis-plus-jsqlparser`（JDK 11+ 版） |
| JWT | jjwt 0.12.6 | 无状态令牌 |
| MySQL Driver | 9.7.0 | Boot 4.1.2 托管，兼容 MySQL 8.0 服务端 |
| 连接池 | HikariCP 7.0.2 | Boot 托管默认 |
| Redis 客户端 | Lettuce 7.5.2.RELEASE | Boot 托管；兼容 Redis 5.0 服务端 |
| Flyway | **12.4.0** | Boot 托管；需额外引入 `flyway-mysql`（MySQL 方言自 10.x 起为独立模块） |
| 接口文档 | springdoc-openapi 3.2.0 | Boot 4 / Spring Framework 7 对应版本线；使用 `-starter-webmvc-ui` |
| 测试 | JUnit 6.0.3 / Mockito 5.23.0 | Boot 托管 |

> [!IMPORTANT]
> MyBatis-Plus 的 starter 与 Spring Boot 大版本一一对应，必须使用 `mybatis-plus-spring-boot4-starter`。`mybatis-plus-boot-starter`（Boot 2）与 `mybatis-plus-spring-boot3-starter`（Boot 3）在本项目不可用。

### 4.2 前端

| 组件 | 版本 | 说明 |
|---|---|---|
| React | 19.3.1 | 当前稳定版 |
| 构建 | Vite 8.3.8 + `@vitejs/plugin-react` 6.3.0 | 官方推荐组合 |
| 语言 | **TypeScript 6.0.3** | `typescript-eslint@8.72.2` 的 peer 范围为 `>=4.8.4 <6.1.0` |
| UI | Ant Design 6.7.0 + `@ant-design/icons` 6.3.4 | 管理台组件库；peer 要求 React >= 18 |
| 路由 | React Router 8.5.1 | 要求 React >= 19.2.7，19.3.1 满足 |
| 状态 | Zustand 5.1.0 | 登录态与全局 UI 状态；服务端数据不进 store |
| 服务端数据 | TanStack Query 5.107.1 | 缓存、失效、轮询 |
| HTTP | axios 1.21.1 | 拦截器统一加 `Authorization` 与错误处理 |
| 图表 | ECharts 6.1.0 + `echarts-for-react` 3.0.6 | SRS 6.3 指定 ECharts |
| 日期 | dayjs 1.11.23 | AntD 6 内置依赖 |
| 测试 | Vitest 5.1.0 + Testing Library 16.3.3 + jsdom 31 + MSW 3.0.5 | Vitest peer 支持 Vite 8 |
| Lint | ESLint 10.14.0 + typescript-eslint 8.72.2 | 含类型感知规则 |
| 格式化 | Prettier 3.10.1 | 统一代码风格 |

### 4.3 工程结构

```
PV-Quality-Inspection-Management-System/
├── backend/                     Maven 单模块
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/pvqims/
│       │   ├── PvQimsApplication.java
│       │   ├── common/          统一响应、异常、分页
│       │   ├── security/        Security 配置、JWT 过滤器、当前用户
│       │   ├── user/            用户与认证
│       │   ├── project/         项目 + 批次
│       │   ├── standard/        校验标准
│       │   ├── inspection/      校验记录与判定
│       │   ├── defect/          缺陷闭环
│       │   └── dashboard/       看板聚合查询
│       └── resources/
│           ├── application.yml
│           ├── application-local.yml   （.gitignore）
│           └── db/migration/           Flyway 脚本 V1__init.sql …
├── frontend/                    pnpm + Vite
│   ├── src/
│   │   ├── api/                 按后端模块划分的请求与类型
│   │   ├── components/          通用组件
│   │   ├── pages/               P1~P7
│   │   ├── routes/              路由表
│   │   ├── stores/              Zustand
│   │   └── hooks/
│   └── package.json
├── docs/                        需求与设计文档
├── dev-notes/                   过程记录
└── docker-compose.yml           可选：仅用于日后 CI，本地开发不依赖
```

**分层**：`Controller → Service → Mapper`。Controller 只做参数校验与响应包装；判定逻辑集中在 `InspectionJudgeService`（SRS 7 章可维护性要求）。

### 4.4 认证方案

- Spring Security 7 + JWT 无状态认证；
- 登录成功签发 JWT，同时把会话写入 Redis（key 前缀 `pvqims:token:`，TTL 与令牌一致）；
- 登出即删除 Redis key，实现服务端可控失效（满足 FR-01）；
- 角色校验用方法级注解（`@PreAuthorize`），**接口层拒绝**而非前端隐藏（BR-07、AC-07）；
- 密码 BCrypt（SRS 5.3）。

---

## 5. 决定清单

**用户已决定**

1. 后端采用 Spring Boot + MyBatis-Plus + Redis；
2. 前端采用 React；
3. 业务表不超过 6 张；
4. 数据库设计延后，本文不涉及；
5. 纳入 Flyway；
6. Lint 采用 ESLint + typescript-eslint 路线。

**设计者决定（可推翻）**

1. 单仓库、`backend/` + `frontend/` 双目录，Maven 不拆多模块；
2. 认证用 Spring Security；
3. 前端用 TanStack Query 管理服务端状态，Zustand 只放登录态；
4. 使用原生 Ant Design 组件，不引入 AntD Pro 的 ProTable/ProForm；
5. TypeScript 锁 6.0.3（受 typescript-eslint peer 范围约束）；
6. 前端包管理用 pnpm；
7. Redis 密码走环境变量 `REDIS_PASSWORD`，并提供 `application-local.yml` 本地方案；
8. Flyway 管理建表与种子数据，不手写 SQL 导入。

---

## 6. 未决事项

| 事项 | 何时决定 |
|---|---|
| 缺陷是否拆为第 6 张表 | 数据库设计阶段 |
| 表结构、索引、字段命名 | 数据库设计阶段 |
| 接口契约细节（请求/响应体） | 详细设计阶段 |
| 5 种判定方式的实现与测试用例 | 实现阶段 |
| 演示数据规模与生成方式 | 测试阶段 |
| 是否需要 docker-compose 固化环境 | 部署阶段 |

---

## 7. 对 SRS 的后续修订建议

本文落地后，`docs/需求规格说明书.md` 2.5 节"技术约束"需补充具体栈名与版本；其余章节（含 5 章 5 表模型、2.4 三角色、4.8 权限矩阵）无需修改。

---

## 8. 留给实现者的空间

- 具体包名、类名命名风格；
- 统一响应体的字段结构；
- 前端目录内部细分；
- 日志格式与级别；
- 单元测试组织方式。

以上不影响架构，实现者可自行决定。
