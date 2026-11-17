# 阶段一 · 工程骨架与认证 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立前后端可运行骨架，交付登录/登出（FR-01）与用户管理（FR-02），阶段结束时能登录、按角色进入不同首页、管理员维护账号。

**Architecture:** 单仓库 `backend/`（Maven 单模块 Spring Boot 4）+ `frontend/`（pnpm + Vite + React 19）。后端 `Controller → Service → Mapper`，MyBatis-Plus 访问 MySQL，JWT + Redis 做无状态但可撤销的会话，角色在方法级用 `@PreAuthorize` 校验。前端 axios 拦截器统一注入令牌并按 `ApiResult` 信封解包，路由守卫按登录态与角色控制。

**Design tokens:** `design/aesthetic/tokens.css` + `design/aesthetic/contract.md`（由 aesthetic 生成，实现时不得出现合同外的颜色/字号/圆角/阴影）。

**Tech Stack:** Java 21 / Spring Boot 4.1.2 / Spring Security 7.1.2 / MyBatis-Plus 3.5.18.1 / jjwt 0.12.6 / Flyway 12.4.0 / MySQL 8.0 / Redis 5.0；React 19.3.1 / Vite 8.3.8 / TypeScript 6.0.3 / MUI 9.5.0 / React Router 8.5.1 / Zustand 5.1.0 / TanStack Query 5.107.1 / Vitest 5.1.0 + MSW 3.0.5。

**Spec:** `docs/需求规格说明书.md`（PVQIMS-SRS-001 V2.1，唯一需求来源）与 `docs/superpowers/specs/2026-11-12-tech-stack-design.md`（技术选型 V1.1）。版本号以 `docs/技术选型.md` 为准。

---

## 阶段路线图

后续阶段的计划在上一阶段完成后再写，每份计划单独一个文件。

| 阶段 | 计划文件 | 交付 | 覆盖需求 |
|---|---|---|---|
| 一（本计划） | `2026-11-16-foundation-auth.md` | 骨架、登录、用户管理 | FR-01、FR-02、P1、P7 |
| 二 | `YYYY-MM-DD-master-data.md` | 项目与批次主数据 | FR-03、FR-04、P2 |
| 三 | `YYYY-MM-DD-standard-inspection.md` | 标准配置、录入、判定、记录查询 | FR-05~FR-09、P3、P4 |
| 四 | `YYYY-MM-DD-defect-loop.md` | 缺陷闭环、记录作废、CSV 导出 | FR-10~FR-12、FR-15、P5 |
| 五 | `YYYY-MM-DD-dashboard-demo.md` | 合格率汇总、8 张图、演示数据 | FR-13、FR-14、FR-16、P6 |

---

## Global Constraints

以下约束对本计划每个任务生效，逐条照抄自 SRS 与 CLAUDE.md：

- **不开分支、不用 `git worktree`**：全部提交直接落在 `main`。
- **业务表恰好 6 张**：`app_user`、`project`、`batch`、`inspection_standard`、`inspection_record`、`defect`；另 2 个视图 `v_defect_detail`、`v_batch_pass_rate`。任何"顺手加张表"都违反 SRS 5.1。
- **判定只在服务端执行**（BR-01），前端结果仅展示；本阶段无判定逻辑，但任何写接口都不得信任前端算出的结果。
- **接口级角色校验**，不能只隐藏菜单（BR-07、AC-07）。
- 数据库 MySQL 8.0+、InnoDB、utf8mb4；时间统一 Asia/Shanghai，列类型 `DATETIME`；密码 BCrypt。
- 界面文案用"**校验**"，质量体系角色与概念（检验、检验员、来料检验）用"**检验**"（SRS 1.4 注）。
- 库/框架/API 用法先查 Context7 MCP，不凭记忆写。
- 提交信息格式 `type(scope): description`，type 限 `feat|fix|docs|refactor|perf|test|chore`，scope 用真实模块名，description 中文、无句号、≤50 字；提交信息末尾附 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。
- **过程留痕**：写入 `.superpowers/sdd/2026-11-16-foundation-auth/progress.md`（该目录 git-ignore，不入仓库）；`dev-notes/` 已由用户移除，**不要创建**。
- 依赖版本一律取自 `docs/技术选型.md`。若某个版本在仓库中不存在，取最近的可用补丁版，并在 SDD 台账记录偏差（不静默改版本）。
- **视觉令牌唯一来源**：`design/aesthetic/tokens.css`。前端所有颜色、字号、圆角、阴影必须引用令牌变量；不得出现十六进制裸值。MUI 主题通过 `createTheme` 从令牌构建，组件库默认皮肤必须被覆盖。

**测试环境约定**（已与用户确认）：

- 后端集成测试连本机 `pvqims_test` 库与 Redis 第 15 号库；纯逻辑用单元测试。
- 集成测试类统一 `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional`，数据库改动随测试回滚。
- Redis 测试数据在 `@BeforeEach` 里 `flushDb()` 清空（只影响 test 配置指向的 15 号库）。
- 凭据走环境变量 `MYSQL_USERNAME`（默认 root）、`MYSQL_PASSWORD`、`REDIS_PASSWORD`，不写入仓库。

---

## Review Focus

SRS 没写、但用户一定会碰到并因此判定"这系统坏了"的五类输入，按可能性排序。每条都要落到拥有该代码的任务的测试里。

1. **不存在的用户名 / 已停用账号 / 密码错误 → 必须一律 401 + 同一句"用户名或密码错误"**，不能 500，也不能暴露账号是否存在。（Task 3）
2. **登出后再用旧令牌、Redis 会话丢失后旧令牌、前端持有失效登录态 → 401 并回登录页**，绝不能仍被视为已登录。（Task 3、Task 8）
3. **不带令牌、或 `Authorization` 格式不对访问受保护接口 → 401 JSON 信封**，而不是 Spring 默认的 HTML 错误页。（Task 2、Task 3）
4. **重复用户名（含仅大小写不同）→ 400 + 明确中文提示**，不是 500；并发插入同一个用户名也一样。（Task 4）
5. **中文姓名、表情符号、超长输入 → 正确存储或给出校验提示**，不出现乱码、截断异常或 500。（Task 4）

---

## 文件结构（阶段一）

```
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/pvqims/
    │   │   ├── PvQimsApplication.java
    │   │   ├── common/
    │   │   │   ├── api/ApiResult.java, PageResult.java
    │   │   │   └── exception/ErrorCode.java, BizException.java, GlobalExceptionHandler.java
    │   │   ├── config/MybatisPlusConfig.java, MyMetaObjectHandler.java
    │   │   ├── security/
    │   │   │   ├── JwtService.java, TokenStore.java, JwtAuthenticationFilter.java
    │   │   │   ├── LoginUser.java, SecurityConfig.java
    │   │   │   └── RestAuthEntryPoint.java, RestAccessDeniedHandler.java
    │   │   └── user/
    │   │       ├── entity/AppUser.java
    │   │       ├── mapper/AppUserMapper.java
    │   │       ├── dto/UserSummary.java, LoginRequest.java, LoginResponse.java,
    │   │       │        CreateUserRequest.java, UpdateUserRequest.java, ResetPasswordRequest.java
    │   │       ├── AuthController.java, UserController.java
    │   │       └── UserService.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml.example
    │       └── db/migration/V1__init_schema.sql, V2__seed_users.sql
    └── test/
        ├── java/com/pvqims/...（SchemaTest、GlobalExceptionHandlerTest、JwtServiceTest、AuthApiTest、UserApiTest）
        └── resources/application-test.yml

frontend/
├── package.json, vite.config.ts, tsconfig.json, eslint.config.js, .prettierrc, index.html
└── src/
    ├── main.tsx, App.tsx（Task 5 的临时外壳，Task 6 删除）
    ├── api/client.ts, types.ts, auth.ts, users.ts
    ├── components/AppLayout.tsx, RequireAuth.tsx, RequireRole.tsx
    ├── pages/LoginPage.tsx, PlaceholderPage.tsx, users/UserListPage.tsx
    ├── routes/index.tsx
    ├── stores/authStore.ts
    ├── utils/labels.ts
    └── test/setup.ts, server.ts, handlers.ts, renderWithProviders.tsx
```

---

### Task 1: 后端骨架、Flyway 建表与种子用户

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/pvqims/PvQimsApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml.example`
- Create: `backend/src/main/resources/db/migration/V1__init_schema.sql`
- Create: `backend/src/main/resources/db/migration/V2__seed_users.sql`
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/test/java/com/pvqims/SchemaTest.java`
- Modify: `.gitignore`
- Create: `README.md`（当前为占位内容，整体替换为启动说明）

**Interfaces:**
- Consumes: 无
- Produces: 可启动的 Spring Boot 应用；6 张表 + 2 个视图；3 个种子账号 `admin/Admin@123`（ADMIN）、`engineer/Engineer@123`（ENGINEER）、`inspector/Inspector@123`（INSPECTOR）

- [ ] **Step 1: 创建数据库**

用本机 MySQL 管理员账号执行（密码取自 `MYSQL_PASSWORD` 环境变量，避免交互式输入）：

```bash
# Bash
mysql -u root -p"$MYSQL_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS pvqims CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE IF NOT EXISTS pvqims_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# PowerShell
mysql -u root -p"$env:MYSQL_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS pvqims CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE IF NOT EXISTS pvqims_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

- [ ] **Step 2: 写 `backend/pom.xml`**

parent `org.springframework.boot:spring-boot-starter-parent:4.1.2`，`<java.version>21</java.version>`，coordinates `com.pvqims:pvqims-backend:0.1.0-SNAPSHOT`。依赖（无版本号者由 Boot BOM 托管）：

```xml
spring-boot-starter-webmvc
spring-boot-starter-validation
spring-boot-starter-data-redis
spring-boot-starter-security
com.baomidou:mybatis-plus-spring-boot4-starter:3.5.18.1
com.baomidou:mybatis-plus-jsqlparser:3.5.18.1
io.jsonwebtoken:jjwt-api:0.12.6
io.jsonwebtoken:jjwt-impl:0.12.6（runtime）
io.jsonwebtoken:jjwt-jackson:0.12.6（runtime）
spring-boot-starter-flyway
org.flywaydb:flyway-mysql（MySQL 方言自 Flyway 10.x 起为独立模块）
org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0
com.mysql:mysql-connector-j（runtime）
org.springframework.boot:spring-boot-starter-test（test）
org.springframework.boot:spring-boot-starter-webmvc-test（test）
```

> Spring Boot 4 里 `spring-boot-starter-web` 已标记弃用、改名为 `spring-boot-starter-webmvc`；`@AutoConfigureMockMvc` 的注解包也迁到了 `org.springframework.boot.webmvc.test.autoconfigure`（Boot 4 拆出了 `-test` 模块）。两处都按上面写法，别用 Boot 3 的老包名。

> springdoc 3.2.0 若在中央仓库不存在，回退 3.1.1（Context7 文档确认存在），并在 SDD 台账记录偏差。

- [ ] **Step 3: 写启动类与配置**

`PvQimsApplication`：`@SpringBootApplication`（主类放在 `com.pvqims`，MyBatis-Plus 会自动扫描其子包下所有 `@Mapper` 接口，不需要 `@MapperScan`）。之后每个 Mapper 接口都加 `@Mapper`。

`application.yml` 关键内容（`server.port: 8080`）：

```yaml
spring:
  application:
    name: pvqims
  profiles:
    default: local
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/pvqims?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:}
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      password: ${REDIS_PASSWORD:}
  flyway:
    locations: classpath:db/migration
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
pvqims:
  jwt:
    secret: ${JWT_SECRET:pvqims-dev-secret-key-must-be-at-least-32-bytes}
    ttl: 8h
```

> `characterEncoding=UTF-8` 是 Connector/J 的合法写法；`utf8mb4` 不是合法值。utf8mb4 由服务端与连接排序规则保证。

`application-local.yml.example`：照抄上面 datasource/redis 段，把密码写成字面量占位 `your-mysql-password` / `your-redis-password`。`application-local.yml` 由开发者自行复制填写，加入 `.gitignore`。

`application-test.yml`：同结构，数据库 `pvqims_test`、Redis `database: 15`、`pvqims.jwt.secret: test-secret-key-at-least-32-bytes-long-1234`。

`.gitignore` 追加：

```
backend/target/
frontend/node_modules/
frontend/dist/
backend/src/main/resources/application-local.yml
```

- [ ] **Step 4: 写失败的表结构测试**

`backend/src/test/java/com/pvqims/SchemaTest.java`：

```java
@SpringBootTest
@ActiveProfiles("test")
class SchemaTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void hasExactlySixBusinessTablesAndTwoViews() {
        List<String> tables = jdbc.queryForList(
            "SELECT LOWER(table_name) FROM information_schema.tables " +
            "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' " +
            "AND table_name <> 'flyway_schema_history'", String.class);
        assertThat(tables).containsExactlyInAnyOrder(
            "app_user", "project", "batch",
            "inspection_standard", "inspection_record", "defect");

        List<String> views = jdbc.queryForList(
            "SELECT LOWER(table_name) FROM information_schema.views " +
            "WHERE table_schema = DATABASE()", String.class);
        assertThat(views).containsExactlyInAnyOrder("v_defect_detail", "v_batch_pass_rate");
    }

    @Test
    void seededUsersStoreBcryptHashes() {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM app_user", String.class);
        assertThat(hashes).hasSize(3)
            .allMatch(h -> h.startsWith("$2a$") || h.startsWith("$2b$"));
    }
}
```

- [ ] **Step 5: 运行测试，确认失败**

此时 `db/migration` 尚未建，`pvqims_test` 里没有任何表。

Run: `cd backend && mvn -Dtest=SchemaTest test`

Expected: FAIL，`hasExactlySixBusinessTablesAndTwoViews` 断言得到空列表。

- [ ] **Step 6: 写 `V1__init_schema.sql`**

严格按 SRS 5.3–5.9、5.11 建表建视图，共 6 表 2 视图：

```sql
CREATE TABLE app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(50) NOT NULL,
  role VARCHAR(20) NOT NULL,
  is_active TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_code VARCHAR(50) NOT NULL,
  project_name VARCHAR(100) NOT NULL,
  product_model VARCHAR(50) NULL,
  customer_name VARCHAR(100) NULL,
  manufacturer_name VARCHAR(100) NULL,
  location VARCHAR(100) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_project_code (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  batch_no VARCHAR(50) NOT NULL,
  planned_qty INT NOT NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_batch_project_no (project_id, batch_no),
  CONSTRAINT fk_batch_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inspection_standard (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  scene VARCHAR(10) NOT NULL,
  process_name VARCHAR(50) NOT NULL,
  item_code VARCHAR(30) NOT NULL,
  item_name VARCHAR(100) NOT NULL,
  unit VARCHAR(20) NULL,
  value_type VARCHAR(10) NOT NULL,
  judge_type VARCHAR(20) NOT NULL,
  lower_limit DECIMAL(12,4) NULL,
  upper_limit DECIMAL(12,4) NULL,
  target_value DECIMAL(12,4) NULL,
  tolerance DECIMAL(12,4) NULL,
  sampling_note VARCHAR(100) NULL,
  is_enabled TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_std_project_item (project_id, item_code),
  CONSTRAINT fk_std_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inspection_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  standard_id BIGINT NOT NULL,
  scene VARCHAR(10) NOT NULL,
  process_name VARCHAR(50) NOT NULL,
  item_name VARCHAR(100) NOT NULL,
  serial_no VARCHAR(50) NULL,
  inspector_id BIGINT NOT NULL,
  inspect_time DATETIME NOT NULL,
  measured_value DECIMAL(12,4) NULL,
  text_value VARCHAR(100) NULL,
  result VARCHAR(10) NOT NULL,
  std_lower DECIMAL(12,4) NULL,
  std_upper DECIMAL(12,4) NULL,
  std_target DECIMAL(12,4) NULL,
  std_tolerance DECIMAL(12,4) NULL,
  std_unit VARCHAR(20) NULL,
  is_void TINYINT NOT NULL DEFAULT 0,
  void_reason VARCHAR(255) NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL,
  KEY idx_rec_project_time (project_id, inspect_time),
  KEY idx_rec_batch (batch_id),
  KEY idx_rec_standard_time (standard_id, inspect_time),
  KEY idx_rec_result (result),
  CONSTRAINT fk_rec_project FOREIGN KEY (project_id) REFERENCES project (id),
  CONSTRAINT fk_rec_batch FOREIGN KEY (batch_id) REFERENCES batch (id),
  CONSTRAINT fk_rec_standard FOREIGN KEY (standard_id) REFERENCES inspection_standard (id),
  CONSTRAINT fk_rec_inspector FOREIGN KEY (inspector_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE defect (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  record_id BIGINT NOT NULL,
  defect_type VARCHAR(50) NOT NULL,
  defect_grade VARCHAR(10) NOT NULL,
  defect_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  handler_id BIGINT NULL,
  handle_note VARCHAR(255) NULL,
  resolved_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_defect_record (record_id),
  KEY idx_defect_status_grade (defect_status, defect_grade),
  CONSTRAINT fk_defect_record FOREIGN KEY (record_id) REFERENCES inspection_record (id),
  CONSTRAINT fk_defect_handler FOREIGN KEY (handler_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE VIEW v_batch_pass_rate AS
SELECT r.batch_id,
       COUNT(*)                                           AS total,
       SUM(r.result = 'PASS')                             AS passed,
       ROUND(SUM(r.result = 'PASS') / COUNT(*) * 100, 2)  AS pass_rate
FROM inspection_record r
WHERE r.is_void = 0
GROUP BY r.batch_id;

CREATE VIEW v_defect_detail AS
SELECT d.id            AS defect_id,
       d.record_id,
       r.project_id,
       p.project_code,
       p.project_name,
       b.batch_no,
       r.scene,
       r.process_name,
       r.item_name,
       r.serial_no,
       r.inspect_time,
       r.std_lower,
       r.std_upper,
       r.std_target,
       r.std_tolerance,
       r.std_unit,
       d.defect_type,
       d.defect_grade,
       d.defect_status,
       d.handle_note,
       d.resolved_at,
       d.created_at    AS defect_created_at,
       i.display_name  AS inspector_name,
       h.display_name  AS handler_name
FROM defect d
JOIN inspection_record r ON r.id = d.record_id
JOIN batch b ON b.id = r.batch_id
JOIN project p ON p.id = r.project_id
JOIN app_user i ON i.id = r.inspector_id
LEFT JOIN app_user h ON h.id = d.handler_id;
```

- [ ] **Step 7: 写 `V2__seed_users.sql`**

先生成哈希。新建一次性测试 `backend/src/test/java/com/pvqims/tmp/HashGenTest.java`：

```java
class HashGenTest {
    @Test
    void printHashes() {
        var enc = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        for (String pwd : new String[] {"Admin@123", "Engineer@123", "Inspector@123"}) {
            System.out.println(pwd + " -> " + enc.encode(pwd));
        }
    }
}
```

Run: `cd backend && mvn -Dtest=HashGenTest test`
把打印出的三个哈希粘进种子 SQL，然后删除 `HashGenTest.java`。

种子 SQL 形如：

```sql
INSERT INTO app_user (username, password_hash, display_name, role, is_active, created_at) VALUES
('admin',    '<Admin@123 的 BCrypt 哈希>',    '系统管理员', 'ADMIN',     1, NOW()),
('engineer', '<Engineer@123 的 BCrypt 哈希>', '张工程师',   'ENGINEER',  1, NOW()),
('inspector','<Inspector@123 的 BCrypt 哈希>','李检验员',   'INSPECTOR', 1, NOW());
```

> `V1` 已执行过的库不会重跑；如果先跑了一次失败的 `mvn test` 导致 Flyway 记录与实际状态不一致，删除测试库重建（密码同样走环境变量）：`mysql -u root -p"$MYSQL_PASSWORD" -e "DROP DATABASE pvqims_test; CREATE DATABASE pvqims_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"`。

- [ ] **Step 8: 运行测试，确认通过**

Run: `cd backend && mvn -Dtest=SchemaTest test`
Expected: PASS，2 个用例通过。

> 首次运行需设置环境变量：PowerShell 下 `$env:MYSQL_PASSWORD = "..."`、`$env:REDIS_PASSWORD = "..."`（Bash 下 `export` 同名变量）。

- [ ] **Step 9: 写 README 启动说明**

内容至少包含：环境要求（JDK 21、Maven、Node 22+、pnpm、MySQL 8、Redis）、创建两个数据库的命令、`application-local.yml` 复制方法、环境变量清单、`mvn -f backend spring-boot:run` 与 `pnpm --dir frontend dev`、默认账号与密码。

- [ ] **Step 10: 提交**

```bash
git add backend .gitignore README.md
git commit -m "feat(backend): 建立 Spring Boot 骨架与六表迁移

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

> 本任务已在上一轮执行完毕（提交 `b47d03b`）。`dev-notes/` 由用户移除，不创建。

---

### Task 2: 统一响应信封与全局异常处理

**Files:**
- Create: `backend/src/main/java/com/pvqims/common/api/ApiResult.java`
- Create: `backend/src/main/java/com/pvqims/common/api/PageResult.java`
- Create: `backend/src/main/java/com/pvqims/common/exception/ErrorCode.java`
- Create: `backend/src/main/java/com/pvqims/common/exception/BizException.java`
- Create: `backend/src/main/java/com/pvqims/common/exception/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/pvqims/common/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `record ApiResult<T>(int code, String message, T data)` — 静态方法 `<T> ApiResult<T> ok(T data)`、`<T> ApiResult<T> ok()`、`static <T> ApiResult<T> fail(int code, String message)`
  - `record PageResult<T>(List<T> records, long total, long page, long pageSize)`
  - `enum ErrorCode { PARAM_INVALID, USERNAME_EXISTS, BAD_CREDENTIALS, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, INTERNAL_ERROR }`，每项带 `int code()` 与 `String defaultMessage()`
  - `class BizException extends RuntimeException`，构造器 `BizException(ErrorCode)`、`BizException(ErrorCode, String message)`，方法 `int code()`、`ErrorCode errorCode()`
  - `GlobalExceptionHandler`（`@RestControllerAdvice`）

- [ ] **Step 1: 定死错误码**

`ErrorCode` 枚举值（code 的百位即 HTTP 状态码，`statusOf(code) = HttpStatus.resolve(code / 100)`）：

| 枚举 | code | 默认消息 |
|---|---|---|
| PARAM_INVALID | 40001 | 参数校验失败 |
| USERNAME_EXISTS | 40002 | 用户名已存在 |
| BAD_CREDENTIALS | 40100 | 用户名或密码错误 |
| UNAUTHORIZED | 40101 | 未登录或登录已过期 |
| FORBIDDEN | 40300 | 无权限执行该操作 |
| NOT_FOUND | 40400 | 资源不存在 |
| INTERNAL_ERROR | 50000 | 服务器内部错误 |

- [ ] **Step 2: 写失败的异常处理测试**

```java
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {
    @Autowired MockMvc mvc;

    @Test
    void bizExceptionMapsToHttpStatusFromErrorCode() throws Exception {
        mvc.perform(get("/probe/biz"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40002))
           .andExpect(jsonPath("$.message").value("用户名已存在"))
           .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedExceptionHidesInternalDetail() throws Exception {
        mvc.perform(get("/probe/boom"))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.code").value(50000))
           .andExpect(jsonPath("$.message").value("服务器内部错误"))
           .andExpect(jsonPath("$.message").value(not(containsString("boom"))));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe/biz") void biz() { throw new BizException(ErrorCode.USERNAME_EXISTS); }
        @GetMapping("/probe/boom") void boom() { throw new IllegalStateException("boom"); }
    }
}
```

> Boot 4 的 `@WebMvcTest` 在 `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`，`@AutoConfigureMockMvc` 在同一包。`not` / `containsString` 来自 `org.hamcrest.Matchers`。

- [ ] **Step 3: 运行测试，确认失败**

Run: `cd backend && mvn -Dtest=GlobalExceptionHandlerTest test`

Expected: 编译失败（`ApiResult`、`BizException`、`GlobalExceptionHandler` 不存在）。

- [ ] **Step 4: 实现五个类**

- `ApiResult`：`record`，`code=0` 表示成功，`message` 成功时为 `"ok"`；**类上加 `@JsonInclude(JsonInclude.Include.NON_NULL)`**，使 `data` 为空时不出现该字段（前端解包与测试断言都依赖这一点）。
- `GlobalExceptionHandler`：
  - `@ExceptionHandler(BizException.class)` → 状态 `HttpStatus.resolve(code/100)`（解析为 null 时用 400），body `ApiResult.fail(code, message)`。
  - `@ExceptionHandler(MethodArgumentNotValidException.class)` → 400，取第一条字段错误消息作为 message，code 40001。
  - `@ExceptionHandler(HttpMessageNotReadableException.class)` → 400，message "请求体格式错误"，code 40001（否则非法 JSON 会落到兜底变成 500）。
  - 同时把 `MissingServletRequestParameterException`、`MethodArgumentTypeMismatchException` 一起映射为 400 / 40001，避免参数缺失被兜底成 500。
  - `@ExceptionHandler(Exception.class)` → 500，消息固定为 `INTERNAL_ERROR.defaultMessage()`，内部异常用 `log.error` 记录。
  - **注意**：`Exception.class` 兜底会把方法级 `@PreAuthorize` 抛出的 `AccessDeniedException` 也吞成 500。因此必须额外声明 `@ExceptionHandler(AccessDeniedException.class) void handleAccessDenied(AccessDeniedException e) throws AccessDeniedException { throw e; }`，把它重新抛回过滤器链，交给 `RestAccessDeniedHandler` 返回 403。
- `PageResult`：留空实现即可（本阶段 Task 4 才用到分页，但类型在此定义好）。

- [ ] **Step 5: 运行测试，确认通过**

Run: `cd backend && mvn -Dtest=GlobalExceptionHandlerTest test`
Expected: 2 个用例 PASS。

> 这条测试同时钉住 Review Focus 第 3 条的一半：错误响应必须是 JSON 信封。

- [ ] **Step 6: 提交**

```bash
git add backend
git commit -m "feat(common): 统一响应信封与全局异常处理

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: 认证全链路（JWT + Redis + 登录/登出）

**Files:**
- Create: `backend/src/main/java/com/pvqims/security/JwtService.java`
- Create: `backend/src/main/java/com/pvqims/security/TokenStore.java`
- Create: `backend/src/main/java/com/pvqims/security/LoginUser.java`
- Create: `backend/src/main/java/com/pvqims/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/pvqims/security/RestAuthEntryPoint.java`
- Create: `backend/src/main/java/com/pvqims/security/RestAccessDeniedHandler.java`
- Create: `backend/src/main/java/com/pvqims/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/pvqims/user/entity/AppUser.java`
- Create: `backend/src/main/java/com/pvqims/user/mapper/AppUserMapper.java`
- Create: `backend/src/main/java/com/pvqims/user/dto/UserSummary.java`
- Create: `backend/src/main/java/com/pvqims/user/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/pvqims/user/dto/LoginResponse.java`
- Create: `backend/src/main/java/com/pvqims/user/AuthController.java`
- Create: `backend/src/main/java/com/pvqims/config/MybatisPlusConfig.java`
- Create: `backend/src/main/java/com/pvqims/config/MyMetaObjectHandler.java`
- Modify: `backend/src/main/java/com/pvqims/PvQimsApplication.java`（追加 `@EnableConfigurationProperties(JwtService.JwtProperties.class)`）
- Test: `backend/src/test/java/com/pvqims/security/JwtServiceTest.java`
- Test: `backend/src/test/java/com/pvqims/user/AuthApiTest.java`

**Interfaces:**
- Consumes: `ApiResult`、`PageResult`、`ErrorCode`、`BizException`（Task 2）
- Produces:
  - `JwtService.issue(Long userId, String username, String role) → JwtService.IssuedToken(String token, String jti, Instant expiresAt)`
  - `JwtService.parse(String token) → io.jsonwebtoken.Claims`（无效时抛 `JwtException`）
  - `JwtService.JwtProperties(String secret, Duration ttl)` — 嵌套 `record`，`@ConfigurationProperties("pvqims.jwt")`，在 `PvQimsApplication` 上用 `@EnableConfigurationProperties(JwtService.JwtProperties.class)` 注册；`JwtService(JwtProperties)` 构造器注入
  - `TokenStore.save(String jti, Long userId, Duration ttl)` / `boolean isActive(String jti)` / `void revoke(String jti)`
  - `record LoginUser(Long id, String username, String role)`
  - `GET` 受保护接口的 401/403 都返回 `ApiResult` JSON
  - `record UserSummary(Long id, String username, String displayName, String role, Boolean isActive, LocalDateTime createdAt)` — Task 4、Task 6 复用
  - `POST /api/auth/login` → `ApiResult<LoginResponse(String token, UserSummary user)>`
  - `POST /api/auth/logout` → `ApiResult<Void>`
  - `AppUser` 实体映射 `app_user`，`AppUserMapper extends BaseMapper<AppUser>`
  - `MyMetaObjectHandler` 自动填 `createdAt`

- [ ] **Step 1: 写 JwtService 单元测试**

```java
class JwtServiceTest {
    private final JwtService jwt = new JwtService(
        new JwtService.JwtProperties("unit-test-secret-key-at-least-32-bytes", Duration.ofHours(8)));

    @Test
    void issuedTokenCarriesUserClaimsAndJti() {
        var issued = jwt.issue(7L, "engineer", "ENGINEER");
        Claims claims = jwt.parse(issued.token());
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("username", String.class)).isEqualTo("engineer");
        assertThat(claims.get("role", String.class)).isEqualTo("ENGINEER");
        assertThat(claims.getId()).isEqualTo(issued.jti());
        assertThat(issued.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void tamperedTokenIsRejected() {
        var issued = jwt.issue(7L, "engineer", "ENGINEER");
        assertThatThrownBy(() -> jwt.parse(issued.token() + "x"))
            .isInstanceOf(JwtException.class);
    }
}
```

- [ ] **Step 2: 运行，确认失败**

Run: `cd backend && mvn -Dtest=JwtServiceTest test`
Expected: 编译失败（`JwtService` 不存在）。

- [ ] **Step 3: 实现 security 包**

- `JwtProperties` 用 `@ConfigurationProperties("pvqims.jwt")` 绑定 `secret` 与 `ttl`（`Duration`），`JwtService` 构造器接收它，`Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`。
- `JwtService.issue`：`Jwts.builder().subject(userId.toString()).id(UUID.randomUUID().toString()).claim("username",...).claim("role",...).issuedAt(...).expiration(...).signWith(key).compact()`。
- `TokenStore`：`StringRedisTemplate`，key 前缀 `pvqims:token:`，值为 userId 字符串，TTL 与 JWT 一致。
- `JwtAuthenticationFilter extends OncePerRequestFilter`：无 `Authorization: Bearer ` 头则直接放行；解析成功且 `tokenStore.isActive(jti)` 才设置 `UsernamePasswordAuthenticationToken(LoginUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))`；解析异常吞掉并放行（交给入口点返回 401）。
- `RestAuthEntryPoint` / `RestAccessDeniedHandler`：向响应写 `ApiResult.fail(40101/40300)`，`Content-Type: application/json;charset=UTF-8`，HTTP 状态 401/403。
- `SecurityConfig`：`csrf` 关闭、`sessionCreationPolicy(STATELESS)`、`permitAll` 只放行 `/api/auth/login`、`/error`、`/v3/api-docs/**`、`/swagger-ui/**`、`/swagger-ui.html`，其余 `authenticated()`；注册两个异常处理器；过滤器加在 `UsernamePasswordAuthenticationFilter` 之前；`@EnableMethodSecurity`。
- `MyMetaObjectHandler`：`insertFill` 时若 `createdAt` 为空则填 `LocalDateTime.now()`。
- `MybatisPlusConfig`：`MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.MYSQL)`。

- [ ] **Step 4: 写登录/登出的集成测试**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTest {

    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private String loginBody(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    @Test
    void seededAdminCanLogInAndPasswordHashIsNeverReturned() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("admin", "Admin@123")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.token").isNotEmpty())
           .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
           .andExpect(jsonPath("$.data.user.displayName").value("系统管理员"))
           .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownUserBothReturnSameGeneric401() throws Exception {
        String wrong = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("admin", "wrong-password")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40100))
            .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("no-such-user", "whatever")))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40100))
           .andExpect(content().json(wrong));
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        jdbc.update("UPDATE app_user SET is_active = 0 WHERE username = 'inspector'");
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("inspector", "Inspector@123")))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void requestWithoutTokenGetsJson401NotHtml() throws Exception {
        mvc.perform(get("/api/users"))
           .andExpect(status().isUnauthorized())
           .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void malformedAuthorizationHeaderGets401() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", "Token abc"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void logoutRevokesTokenAndOldTokenIsRejected() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("engineer", "Engineer@123")))
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.token");

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
           .andExpect(status().isOk());

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void tokenWhoseRedisKeyDisappearedIsRejected() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content(loginBody("engineer", "Engineer@123")))
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.token");

        redis.getConnectionFactory().getConnection().serverCommands().flushDb();

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value(40101));
    }
}
```

> 类里声明 `@Autowired JdbcTemplate jdbc;`。本类覆盖 Review Focus 第 1、2、3 条。
> 导入注意（Boot 4 包路径变化）：`import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;`、`import com.jayway.jsonpath.JsonPath;`、`import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;`、`import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;`、`import static org.springframework.http.MediaType.APPLICATION_JSON;`。

- [ ] **Step 5: 运行，确认失败**

Run: `cd backend && mvn -Dtest=AuthApiTest test`
Expected: 编译失败（`AuthController` 等不存在）或 404。

- [ ] **Step 6: 实现认证接口与实体**

- `AppUser`：`@TableName("app_user")`，字段 `id, username, passwordHash, displayName, role, isActive, createdAt`，主键 `@TableId(type = IdType.AUTO)`，`createdAt` 加 `@TableField(fill = FieldFill.INSERT)`。
- `AuthController`（`/api/auth`）：
  - `login(@Valid @RequestBody LoginRequest)`：按 `username` 查 `AppUser`；不存在、`isActive != 1`、`passwordEncoder.matches` 失败，一律抛 `new BizException(ErrorCode.BAD_CREDENTIALS)`；成功则 `jwtService.issue(...)`、`tokenStore.save(jti, id, ttl)`、返回 `LoginResponse(token, userSummary)`。
  - `logout(@RequestHeader("Authorization") String header)`：去掉 `Bearer ` 前缀，`jwtService.parse` 取 jti，`tokenStore.revoke(jti)`，返回 `ApiResult.ok()`。
  - `LoginRequest`：`@NotBlank @Size(max=50) username`、`@NotBlank @Size(max=64) password`。
- `AppUserMapper extends BaseMapper<AppUser>`，加 `@Mapper`。
- `SecurityConfig` 里声明 `@Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }`。

- [ ] **Step 7: 运行测试，确认通过**

Run: `cd backend && mvn -Dtest=AuthApiTest test`
Expected: 7 个用例全部 PASS。

- [ ] **Step 8: 提交**

```bash
git add backend
git commit -m "feat(auth): 实现 JWT 登录与可撤销会话

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: 用户管理接口（FR-02）

**Files:**
- Create: `backend/src/main/java/com/pvqims/user/dto/CreateUserRequest.java`
- Create: `backend/src/main/java/com/pvqims/user/dto/UpdateUserRequest.java`
- Create: `backend/src/main/java/com/pvqims/user/dto/ResetPasswordRequest.java`
- Create: `backend/src/main/java/com/pvqims/user/UserService.java`
- Create: `backend/src/main/java/com/pvqims/user/UserController.java`
- Test: `backend/src/test/java/com/pvqims/user/UserApiTest.java`

**Interfaces:**
- Consumes: `UserSummary`、`AppUser`、`AppUserMapper`、`TokenStore`（Task 3）；`PageResult`、`BizException`、`ErrorCode`（Task 2）；`BCryptPasswordEncoder`（Task 3 的 Bean）
- Produces:
  - `GET /api/users?keyword=&role=&isActive=&page=1&pageSize=10` → `ApiResult<PageResult<UserSummary>>`
  - `POST /api/users`（body `CreateUserRequest(username, password, displayName, role)`）→ `ApiResult<UserSummary>`，HTTP 200
  - `PUT /api/users/{id}`（body `UpdateUserRequest(displayName, role, isActive)`）→ `ApiResult<UserSummary>`
  - `PATCH /api/users/{id}/password`（body `ResetPasswordRequest(newPassword)`）→ `ApiResult<Void>`
  - 四个接口均 `@PreAuthorize("hasRole('ADMIN')")`
  - `UserService.page(String keyword, String role, Boolean isActive, long page, long pageSize)` 等四个方法

- [ ] **Step 1: 写集成测试**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String tokenFor(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    @Test
    void inspectorCannotReachUserManagement() throws Exception {
        String t = tokenFor("inspector", "Inspector@123");
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + t))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value(40300));
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + t)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"x1\",\"password\":\"Passw0rd!\",\"displayName\":\"X\",\"role\":\"INSPECTOR\"}"))
           .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesUserWhoCanThenLogIn() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"wang\",\"password\":\"Wang@1234\",\"displayName\":\"王工\",\"role\":\"ENGINEER\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.id").isNumber())
           .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"wang\",\"password\":\"Wang@1234\"}"))
           .andExpect(status().isOk());
    }

    @Test
    void duplicateUsernameReturns400NotServerError() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Passw0rd!\",\"displayName\":\"重名\",\"role\":\"ADMIN\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40002))
           .andExpect(jsonPath("$.message").value("用户名已存在"));

        // 仅大小写不同也算重复（utf8mb4_0900_ai_ci 大小写不敏感）
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"ADMIN\",\"password\":\"Passw0rd!\",\"displayName\":\"重名\",\"role\":\"ADMIN\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void resetPasswordMakesOldPasswordStopWorking() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'inspector'", Long.class);

        mvc.perform(patch("/api/users/{id}/password", id).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content("{\"newPassword\":\"NewInsp@123\"}"))
           .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"inspector\",\"password\":\"Inspector@123\"}"))
           .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"inspector\",\"password\":\"NewInsp@123\"}"))
           .andExpect(status().isOk());
    }

    @Test
    void disablingUserPreventsSubsequentLogin() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'inspector'", Long.class);

        mvc.perform(put("/api/users/{id}", id).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"displayName\":\"李检验员\",\"role\":\"INSPECTOR\",\"isActive\":false}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.isActive").value(false));

        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"inspector\",\"password\":\"Inspector@123\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void chineseAndEmojiDisplayNameRoundTripWithoutMojibake() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        String displayName = "王小明😀";

        String created = mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of(
                    "username", "wangxm", "password", "Wangxm@123",
                    "displayName", displayName, "role", "INSPECTOR"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.read(created, "$.data.displayName")).isEqualTo(displayName);

        mvc.perform(get("/api/users").param("keyword", "wangxm").header("Authorization", "Bearer " + admin))
           .andExpect(jsonPath("$.data.records[0].displayName").value(displayName));
    }

    @Test
    void overlongDisplayNameIsRejectedWithValidationError() throws Exception {
        String admin = tokenFor("admin", "Admin@123");
        mvc.perform(post("/api/users").header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"toolong\",\"password\":\"Passw0rd!\",\"displayName\":\"%s\",\"role\":\"ADMIN\"}"
                    .formatted("很".repeat(60))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(40001));
    }
}
```

> 需要在测试类里声明 `@Autowired JdbcTemplate jdbc;`。本类覆盖 Review Focus 第 4、5 条。

- [ ] **Step 2: 运行，确认失败**

Run: `cd backend && mvn -Dtest=UserApiTest test`
Expected: 编译失败或 404。

- [ ] **Step 3: 实现 DTO 与服务**

- `CreateUserRequest`：`username` `@NotBlank @Size(max=50)`；`password` `@NotBlank @Size(min=8, max=64)`；`displayName` `@NotBlank @Size(max=50)`；`role` `@NotBlank @Pattern(regexp = "INSPECTOR|ENGINEER|ADMIN", message = "角色只能是 INSPECTOR、ENGINEER 或 ADMIN")`。
- `UpdateUserRequest`：`displayName`、`role`、`isActive` 三个字段，校验同上（`isActive` `@NotNull`）。
- `ResetPasswordRequest`：`newPassword` `@NotBlank @Size(min=8, max=64)`。
- `UserService.create`：先 `exists(username)` 查重抛 `USERNAME_EXISTS`；`insert` 时捕获 `DuplicateKeyException` 同样抛 `USERNAME_EXISTS`（覆盖并发插入）。
- `UserService.update`：不存在抛 `NOT_FOUND`；只改 `displayName/role/isActive`，不动密码。
- `UserService.resetPassword`：不存在抛 `NOT_FOUND`；BCrypt 存入。
- `UserService.page`：`LambdaQueryWrapper` 按 `keyword` 模糊匹配 `username` 或 `displayName`，按 `role`、`isActive` 精确过滤，按 `id` 升序分页，转成 `UserSummary`。
- 角色变更与停用**不**主动撤销该用户已有令牌（SRS 未要求）；在 SDD 台账记下这一决定。
  > 后续更新（2026-11-17）：最终审查推翻了这条决定，见 `.superpowers/sdd/2026-11-16-foundation-auth/progress.md` 的 I4 裁定；实现已改为按用户吊销 Redis 会话。

- [ ] **Step 4: 运行测试，确认通过**

Run: `cd backend && mvn -Dtest=UserApiTest test`
Expected: 7 个用例全部 PASS。

- [ ] **Step 5: 跑全量后端测试**

Run: `cd backend && mvn test`
Expected: `BUILD SUCCESS`，Task 1~4 所有用例通过。

- [ ] **Step 6: 提交**

```bash
git add backend
git commit -m "feat(user): 用户管理接口与角色校验

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: 前端工程骨架

**Files:**
- Create: `frontend/package.json`、`frontend/vite.config.ts`、`frontend/tsconfig.json`、`frontend/tsconfig.node.json`、`frontend/eslint.config.js`、`frontend/.prettierrc`、`frontend/index.html`
- Create: `frontend/src/main.tsx`、`frontend/src/App.tsx`
- Create: `frontend/src/theme/tokens.ts`（从 tokens.css 读取的 TS 常量）
- Create: `frontend/src/theme/muiTheme.ts`（`createTheme` 覆盖 MUI 默认皮肤）
- Create: `frontend/src/utils/labels.ts`
- Create: `frontend/src/test/setup.ts`
- Copy: `design/aesthetic/tokens.css` → `frontend/src/styles/tokens.css`
- Test: `frontend/src/utils/labels.test.ts`
- Test: `frontend/src/theme/muiTheme.test.ts`

**Interfaces:**
- Consumes: 无
- Produces:
  - 可 `pnpm build` / `pnpm lint` / `pnpm test` 的前端工程
  - `Role = 'INSPECTOR' | 'ENGINEER' | 'ADMIN'`、`ROLE_LABELS: Record<Role, string>`
  - `color` / `radius` / `space` / `fontFamily` — 与 `tokens.css` 逐项对应的 TS 常量
  - `muiTheme: Theme` — `createTheme` 产物，`palette.primary.main` 必须等于 `color.accent`，`shape.borderRadius` 等于 `radius.md`，`typography.fontFamily` 以几何无衬线开头并以 `PingFang SC, Microsoft YaHei` 收尾

- [ ] **Step 1: 建工程与依赖**

`pnpm create vite frontend --template react-ts` 生成后，按 `docs/技术选型.md` 第 3 节改写 `package.json`。

运行时依赖：react 19.3.1、react-dom 19.3.1、`@mui/material` 9.5.0、`@mui/icons-material` 9.5.0、`@mui/x-data-grid` 9.18.0、`@emotion/react` ^11、`@emotion/styled` ^11、react-router 8.5.1、zustand 5.1.0、`@tanstack/react-query` 5.107.1、axios 1.21.1。

开发依赖：vite 8.3.8、`@vitejs/plugin-react` 6.3.0、typescript 6.0.3、vitest 5.1.0、`@testing-library/react` 16.3.3、`@testing-library/user-event`、`@testing-library/jest-dom`、jsdom 31、msw 3.0.5、eslint 10.14.0、typescript-eslint 8.72.2、prettier 3.10.1。

> 本阶段**不装** echarts / echarts-for-react / dayjs：ECharts 与它的 React 封装到阶段五做图表时再加；dayjs 由 MUI 传递提供，需要时再显式声明。每个依赖版本按技术选型文档写死，`pnpm install` 后把 `pnpm-lock.yaml` 一并提交。

scripts：

```json
{
  "dev": "vite",
  "build": "tsc -b && vite build",
  "lint": "eslint .",
  "format": "prettier --write .",
  "test": "vitest"
}
```

- [ ] **Step 2: 写 vite 配置**

```ts
/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
```

> 开发期靠 Vite 代理同源转发，浏览器不产生跨域，后端**不需要** CORS 配置。

- [ ] **Step 3: 写 `src/test/setup.ts`**

```ts
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
  localStorage.clear();
});
```

- [ ] **Step 4: 拷入视觉令牌并写主题映射**

```bash
mkdir -p frontend/src/styles frontend/src/theme
cp design/aesthetic/tokens.css frontend/src/styles/tokens.css
```

在 `frontend/src/main.tsx` 顶部 `import './styles/tokens.css';`（必须早于任何 MUI 组件渲染）。

`frontend/src/theme/tokens.ts`：把 `tokens.css` 里的变量逐项抄成 TS 常量（十六进制值以 `tokens.css` 注释中的 hex 为准），导出：

```ts
export const fontFamily =
  '"Century Gothic", "Avenir Next", "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif';

export const color = {
  bg: '#F6F7F9',
  surface1: '#FFFFFF',
  surface2: '#F9FAFB',
  surface3: '#F3F4F6',
  fg: '#1A1A19',
  fgMuted: '#667085',
  accent: '#2563EB',
  accentHover: '#1D4ED8',
  line1: '#E5E7EB',
  line2: '#D1D5DB',
  danger: '#B91C1C',
  success: '#15803D',
  warning: '#B45309',
  onAccent: '#FFFFFF',
} as const;

export const radius = { sm: 8, md: 12, lg: 20, full: 999 } as const;
export const space = { s1: 4, s2: 8, s3: 12, s4: 16, s5: 24, s6: 32, s7: 48, s8: 64 } as const;
```

> 上表 hex 必须与 `frontend/src/styles/tokens.css` 同名列完全一致。若不符，以 `tokens.css` 为准并更新本文件——`muiTheme.test.ts` 会校验这一点。

`frontend/src/theme/muiTheme.ts`：

```ts
import { createTheme } from '@mui/material/styles';
import { color, fontFamily, radius, space } from './tokens';

export const muiTheme = createTheme({
  palette: {
    mode: 'light',
    background: { default: color.bg, paper: color.surface1 },
    text: { primary: color.fg, secondary: color.fgMuted },
    primary: { main: color.accent, dark: color.accentHover, contrastText: color.onAccent },
    error: { main: color.danger },
    success: { main: color.success },
    warning: { main: color.warning },
    divider: color.line1,
  },
  shape: { borderRadius: radius.md },
  spacing: space.s2,
  typography: {
    fontFamily,
    button: { textTransform: 'none', fontWeight: 550 },
    h1: { fontSize: 32, fontWeight: 700, letterSpacing: '-0.02em' },
    h2: { fontSize: 24, fontWeight: 700, letterSpacing: '-0.01em' },
    h3: { fontSize: 20, fontWeight: 550 },
    body1: { fontSize: 14, lineHeight: 1.5 },
    body2: { fontSize: 13, lineHeight: 1.5 },
    caption: { fontSize: 12 },
  },
  components: {
    MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { root: { borderRadius: radius.lg } } },
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { borderRadius: radius.sm } } },
    MuiOutlinedInput: { styleOverrides: { root: { borderRadius: radius.sm, backgroundColor: color.surface1 } } },
    MuiTableCell: { styleOverrides: { root: { borderColor: color.line1 } } },
  },
});

export { color, fontFamily, radius, space };
```

`frontend/src/theme/muiTheme.test.ts`（防漂移闸门，不要删）：

```ts
import { describe, expect, it } from 'vitest';
import { muiTheme, color, fontFamily } from './muiTheme';

describe('MUI 主题吃视觉令牌', () => {
  it('primary 与 accent 同值', () => {
    expect(muiTheme.palette.primary.main).toBe(color.accent);
  });
  it('页面基底与卡片背景分别取 bg 与 surface1', () => {
    expect(muiTheme.palette.background.default).toBe(color.bg);
    expect(muiTheme.palette.background.paper).toBe(color.surface1);
  });
  it('字族以几何无衬线开头并含中文字族', () => {
    expect(fontFamily.startsWith('"Century Gothic"')).toBe(true);
    expect(fontFamily).toContain('PingFang SC');
    expect(fontFamily).toContain('Microsoft YaHei');
  });
  it('圆角来自令牌而非 MUI 默认 4px', () => {
    expect(muiTheme.shape.borderRadius).toBe(12);
  });
});
```

- [ ] **Step 5: 写失败的标签测试**

```ts
import { describe, expect, it } from 'vitest';
import { ROLE_LABELS } from './labels';

describe('ROLE_LABELS', () => {
  it('用界面文案翻译三种角色', () => {
    expect(ROLE_LABELS.ENGINEER).toBe('质量工程师');
    expect(ROLE_LABELS.INSPECTOR).toBe('检验员');
    expect(ROLE_LABELS.ADMIN).toBe('管理员');
  });
});
```

- [ ] **Step 6: 运行，确认失败**

Run: `cd frontend && pnpm install && pnpm vitest run src/utils/labels.test.ts src/theme/muiTheme.test.ts`
Expected: 找不到 `./labels` 模块。

- [ ] **Step 7: 实现 `labels.ts` 与最小 `main.tsx` / `App.tsx`**

```ts
export type Role = 'INSPECTOR' | 'ENGINEER' | 'ADMIN';

export const ROLE_LABELS: Record<Role, string> = {
  INSPECTOR: '检验员',
  ENGINEER: '质量工程师',
  ADMIN: '管理员',
};
```

`main.tsx`：先 `import './styles/tokens.css'`，再依次包 `QueryClientProvider` → MUI `ThemeProvider theme={muiTheme}` → `CssBaseline` → `<App />`。`App.tsx` 暂时只渲染一行标题文字。

- [ ] **Step 8: 跑通三类检查**

Run:
```bash
cd frontend
pnpm vitest run
pnpm lint
pnpm build
```
Expected: 测试 2 个文件通过（`labels.test.ts` + `muiTheme.test.ts`）；lint 无 error；`dist/` 生成。

- [ ] **Step 9: 提交**

```bash
git add frontend .gitignore
git commit -m "feat(frontend): 建立 Vite React 工程骨架

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: 登录页、登录态与路由守卫

**Files:**
- Create: `frontend/src/api/types.ts`、`frontend/src/api/client.ts`、`frontend/src/api/auth.ts`
- Create: `frontend/src/stores/authStore.ts`
- Create: `frontend/src/components/RequireAuth.tsx`
- Create: `frontend/src/components/AppLayout.tsx`（本任务先做最小骨架：只渲染 `<Outlet />`）
- Create: `frontend/src/pages/LoginPage.tsx`
- Create: `frontend/src/pages/PlaceholderPage.tsx`（MUI `Typography` + `Box`）
- Create: `frontend/src/routes/index.tsx`
- Modify: `frontend/src/main.tsx`
- Delete: `frontend/src/App.tsx`（内容并入 `main.tsx` 与 `routes/`）
- Create: `frontend/src/test/server.ts`、`frontend/src/test/handlers.ts`、`frontend/src/test/renderWithProviders.tsx`
- Test: `frontend/src/pages/LoginPage.test.tsx`

**Interfaces:**
- Consumes: `Role`、`ROLE_LABELS`（Task 5）
- Produces:
  - `interface UserSummary { id: number; username: string; displayName: string; role: Role; isActive: boolean; createdAt: string }`
  - `interface LoginResponse { token: string; user: UserSummary }`
  - `apiClient`（axios 实例）：成功响应自动解包为 `data`；401 时清空登录态（**登录接口本身的 401 除外**，留给表单展示错误），清空后由 `RequireAuth` 声明式重定向到 `/login`（**不在拦截器里调用 `window.location`**，避免测试环境与浏览器整页刷新）
  - `useAuthStore`：`{ token: string | null; user: UserSummary | null; setSession(token, user): void; clear(): void }`（zustand `persist`，`localStorage` 键 `pvqims.auth`）
  - `login(req: { username: string; password: string }): Promise<LoginResponse>`、`logout(): Promise<void>`
  - `HOME_BY_ROLE: Record<Role, string>`：`ENGINEER → /projects`、`INSPECTOR → /inspection`、`ADMIN → /users`
  - `PlaceholderPage({ title }: { title: string })`：MUI `Typography variant="h3" component="h1"` 渲染 `title`，下方 `Typography variant="body2" color="text.secondary"` 文案"功能开发中"
  - `export const routes: RouteObject[]`、`createAppRouter()`
  - 测试工具：`renderAt(routes, path)`、`server`（MSW 3.0.5，选项名为 `onUnhandledFrame: 'error'`；`onUnhandledRequest` 是 MSW 2.x 的旧名，会 TS2353）

- [ ] **Step 1: 写 MSW 基础设施与失败测试**

`src/test/server.ts`：

```ts
import { setupServer } from 'msw/node';
import { handlers } from './handlers';

export const server = setupServer(...handlers);
```

`src/test/handlers.ts`（本阶段的默认处理器）：

```ts
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { username: string; password: string };
    if (body.password !== 'Engineer@123') {
      return HttpResponse.json(
        { code: 40100, message: '用户名或密码错误', data: null },
        { status: 401 },
      );
    }
    return HttpResponse.json({
      code: 0,
      message: 'ok',
      data: {
        token: 'fake-jwt',
        user: {
          id: 2, username: 'engineer', displayName: '张工程师',
          role: 'ENGINEER', isActive: true, createdAt: '2026-11-01T09:00:00',
        },
      },
    });
  }),
  http.post('/api/auth/logout', () => HttpResponse.json({ code: 0, message: 'ok', data: null })),
  http.get('/api/users', () =>
    HttpResponse.json({
      code: 0,
      message: 'ok',
      data: {
        records: [
          { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN', isActive: true, createdAt: '2026-11-01T09:00:00' },
          { id: 2, username: 'engineer', displayName: '张工程师', role: 'ENGINEER', isActive: true, createdAt: '2026-11-01T09:00:00' },
        ],
        total: 2, page: 1, pageSize: 10,
      },
    }),
  ),
];
```

`src/test/setup.ts` 追加：

```ts
import { server } from './server';
import { beforeAll, afterEach, afterAll } from 'vitest';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

`src/pages/LoginPage.test.tsx`：

```tsx
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it } from 'vitest';
import { renderAt } from '../test/renderWithProviders';
import { routes } from '../routes';

it('工程师登录后进入项目与批次首页', async () => {
  renderAt(routes, '/login');
  await userEvent.type(screen.getByLabelText('用户名'), 'engineer');
  await userEvent.type(screen.getByLabelText('密码'), 'Engineer@123');
  await userEvent.click(screen.getByRole('button', { name: '登录' }));

  expect(await screen.findByRole('heading', { name: '项目与批次' })).toBeInTheDocument();
});

it('密码错误时提示且停留在登录页', async () => {
  renderAt(routes, '/login');
  await userEvent.type(screen.getByLabelText('用户名'), 'engineer');
  await userEvent.type(screen.getByLabelText('密码'), 'bad-password');
  await userEvent.click(screen.getByRole('button', { name: '登录' }));

  expect(await screen.findByText('用户名或密码错误')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '登录' })).toBeInTheDocument();
});

it('未登录访问受保护页面被重定向到登录页', async () => {
  renderAt(routes, '/users');
  expect(await screen.findByLabelText('用户名')).toBeInTheDocument();
});
```

`src/test/renderWithProviders.tsx`：

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import type { RouteObject } from 'react-router';

export function renderAt(routeObjects: RouteObject[], path: string) {
  const router = createMemoryRouter(routeObjects, { initialEntries: [path] });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}
```

> 注意用 `heading` 角色断言：菜单项与占位页标题文案相同，`findByText` 会命中多个元素。

- [ ] **Step 2: 运行，确认失败**

Run: `cd frontend && pnpm vitest run src/pages/LoginPage.test.tsx`
Expected: 找不到 `../routes` 等模块。

- [ ] **Step 3: 实现 axios 客户端与登录态**

`src/api/client.ts` 要点（`ApiError` 同文件导出）：

```ts
export const apiClient = axios.create({ baseURL: '/api', timeout: 10000 });

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

apiClient.interceptors.response.use(
  (res) => res.data.data,
  (error) => {
    const url: string = error.config?.url ?? '';
    // 登录接口的 401 是"账号密码错误"，由表单展示，不当作会话失效
    if (error.response?.status === 401 && !url.includes('/auth/login')) {
      // 只清登录态；RequireAuth 观察到 token 为空后声明式重定向
      useAuthStore.getState().clear();
    }
    return Promise.reject(
      new ApiError(error.response?.data?.code ?? 0,
        error.response?.data?.message ?? '网络异常，请稍后重试'),
    );
  },
);
```

`src/stores/authStore.ts`：zustand `create` + `persist`，持久化键 `pvqims.auth`，只持久化 `token` 与 `user`。

- [ ] **Step 4: 实现登录页、占位页与路由**

- `LoginPage`：MUI `Box component="form"` + 两个 `TextField`（`label` 分别"用户名""密码"，密码 `type="password"`，`name` 分别 `username`/`password`，`required`）；提交按钮 `Button variant="contained"` 文案"登录"。提交调用 `login`，成功后 `setSession` 并按 `HOME_BY_ROLE[user.role]` 跳转；失败时把 `ApiError.message` 渲染进页内 `Alert severity="error"`（**不要用 `Snackbar`/toast**，便于测试断言）。
  - 布局：卡片居中，最大宽 400px，`Paper` 内边距 32px，垂直栅格间距 24px（`Stack spacing={3}`）。
  - 标签必须与 `TextField` 的 `label` 关联，`getByLabelText('用户名')` 才能定位——MUI 的 `TextField` 默认满足这一点。
- `routes`：
  - `/login` → `LoginPage`
  - `/` → `RequireAuth` 包 `AppLayout`，子路由 `dashboard`（质量看板）、`projects`（项目与批次）、`standards`（校验标准）、`inspection`（校验录入）、`defects`（缺陷处理）、`users`（用户管理），本阶段全部渲染对应 `PlaceholderPage`；`index` 重定向到 `HOME_BY_ROLE[currentRole]`
  - `*` → 重定向到 `/`
- `RequireAuth`：无 `token` 时 `<Navigate to="/login" replace />`，否则渲染 children。
- `AppLayout`（本任务最小版）：`function AppLayout() { return <Outlet />; }`。
- `main.tsx`：`QueryClientProvider` + `<RouterProvider router={createAppRouter()} />`；`createBrowserRouter` 从 `react-router` 导入，`RouterProvider` 从 `react-router/dom` 导入（v8 已移除 `react-router-dom`）。
- **MUI 没有 `ConfigProvider`**（那是 Ant Design 的 API）。中文本地化在 `muiTheme.ts` 里用 `createTheme(options, zhCN)` 实现，`zhCN` 从 `@mui/material/locale` 导入。

- [ ] **Step 5: 运行测试，确认通过**

Run: `cd frontend && pnpm vitest run src/pages/LoginPage.test.tsx`
Expected: 3 个用例 PASS。

- [ ] **Step 6: 提交**

```bash
git add frontend
git commit -m "feat(auth): 前端登录页与路由守卫

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: 应用布局与角色菜单

**Files:**
- Modify: `frontend/src/components/AppLayout.tsx`（替换 Task 6 的最小版）
- Create: `frontend/src/components/RequireRole.tsx`
- Modify: `frontend/src/routes/index.tsx`（`/users` 加 `RequireRole`）
- Modify: `frontend/src/test/renderWithProviders.tsx`（追加 `seedSession` 辅助）
- Test: `frontend/src/components/AppLayout.test.tsx`

**Interfaces:**
- Consumes: `useAuthStore`、`HOME_BY_ROLE`、`ROLE_LABELS`（Task 6）
- Produces:
  - `AppLayout`：MUI `Box display="flex"` 布局；左侧 `Drawer variant="permanent"`（宽 240px）内放 `List`，每项 `ListItemButton` + `ListItemText`；右侧 `AppBar position="static"` 内放 `Toolbar`，显示 `displayName`、角色 `Chip size="small"`、`Button` 文案"退出登录"（调用 `logout()` 后 `clear()` 并 `navigate('/login')`）；内容区 `Box component="main"` 内放 `<Outlet />`
  - 菜单项用 `getByRole('link', { name: label })` 定位：每个 `ListItemButton` 必须带 `component={RouterLink} to={key}`，否则测试无法按角色名断言
  - 配色全部来自主题：`Drawer` 的背景用 `palette.background.paper`（纯白），页面基底用 `palette.background.default`（浅灰）——这是"白卡片浮在浅灰页面"的令牌决定，不得改成两边同色
  - `RequireRole({ roles, children })`：角色不匹配时 `<Navigate to={HOME_BY_ROLE[currentRole]} replace />`
  - `MENU_ITEMS: { key: string; label: string; roles: Role[] }[]`（导出供测试断言）
  - `seedSession(role: Role): void`：向 `localStorage` 写入 `pvqims.auth`，用户数据固定为 `{ id: 99, username: 'seed-user', displayName: '测试管理员', role, isActive: true, createdAt: '2026-11-01T09:00:00' }`

**菜单可见性**（SRS 4.8）：质量看板、项目与批次、缺陷处理 → 全部角色；校验录入 → ENGINEER、INSPECTOR；校验标准 → ENGINEER、ADMIN；用户管理 → ADMIN。

- [ ] **Step 1: 写菜单过滤测试**

```tsx
import { screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import { renderAt, seedSession } from '../test/renderWithProviders';
import { routes } from '../routes';
import type { Role } from '../utils/labels';

it.each([
  ['INSPECTOR', ['质量看板', '项目与批次', '校验录入', '缺陷处理']],
  ['ENGINEER', ['质量看板', '项目与批次', '校验标准', '校验录入', '缺陷处理']],
  ['ADMIN', ['质量看板', '项目与批次', '校验标准', '缺陷处理', '用户管理']],
])('%s 看到属于自己的菜单项', async (role, expected) => {
  seedSession(role as Role);
  renderAt(routes, '/dashboard');
  for (const label of expected) {
    expect(await screen.findByRole('link', { name: label })).toBeInTheDocument();
  }
});

it('检验员直接访问 /users 会被送回自己的首页', async () => {
  seedSession('INSPECTOR');
  renderAt(routes, '/users');
  expect(await screen.findByRole('heading', { name: '校验录入' })).toBeInTheDocument();
});
```

`seedSession(role)` 是测试辅助：向 `localStorage` 写入 `pvqims.auth` 的持久化结构（token + user），再渲染，避免每个用例重复走登录接口。

- [ ] **Step 2: 运行，确认失败**

Run: `cd frontend && pnpm vitest run src/components/AppLayout.test.tsx`
Expected: 找不到 `AppLayout` / `seedSession`。

- [ ] **Step 3: 实现布局、菜单与角色守卫**

- `MENU_ITEMS: { key: string; label: string; roles: Role[] }[]`，key 即路由路径。
- `AppLayout` 用 `useLocation` 高亮当前项，点击 `navigate(key)`。
- `routes` 里 `/users` 用 `RequireRole roles={['ADMIN']}` 包裹。
- 占位页在 Task 6 已就位，本任务只改 `routes` 的包裹关系与布局。

- [ ] **Step 4: 运行测试，确认通过**

Run: `cd frontend && pnpm vitest run src/components/AppLayout.test.tsx`
Expected: 4 个用例 PASS。

- [ ] **Step 5: 提交**

```bash
git add frontend
git commit -m "feat(layout): 角色菜单与路由权限守卫

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 8: 用户管理页 P7

**Files:**
- Create: `frontend/src/api/users.ts`
- Create: `frontend/src/pages/users/UserListPage.tsx`
- Modify: `frontend/src/routes/index.tsx`（`/users` 指向 `UserListPage`）
- Test: `frontend/src/pages/users/UserListPage.test.tsx`

**Interfaces:**
- Consumes: `apiClient`、`UserSummary`、`ROLE_LABELS`（Task 5、6）
- Produces:
  - `listUsers(params: { keyword?: string; role?: Role; isActive?: boolean; page: number; pageSize: number }): Promise<PageResult<UserSummary>>`
  - `createUser(req: { username: string; password: string; displayName: string; role: Role }): Promise<UserSummary>`
  - `updateUser(id: number, req: { displayName: string; role: Role; isActive: boolean }): Promise<UserSummary>`
  - `resetPassword(id: number, newPassword: string): Promise<void>`
  - `interface PageResult<T> { records: T[]; total: number; page: number; pageSize: number }`

- [ ] **Step 1: 写页面测试**

`src/pages/users/UserListPage.test.tsx`（列表数据由 `src/test/handlers.ts` 里的默认 GET `/api/users` 处理器提供，本任务把它扩展为含"系统管理员""张工程师"两行）：

```tsx
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, it } from 'vitest';
import { server } from '../../test/server';
import { renderAt, seedSession } from '../../test/renderWithProviders';
import { routes } from '../../routes';

it('渲染接口返回的用户行并显示角色中文', async () => {
  seedSession('ADMIN');
  renderAt(routes, '/users');
  const adminRow = (await screen.findByText('系统管理员')).closest('tr')!;
  expect(within(adminRow).getByText('管理员')).toBeInTheDocument();
  expect(within(adminRow).getByText('admin')).toBeInTheDocument();
  expect(screen.queryByText(/\$2[aby]\$/)).not.toBeInTheDocument(); // 绝不显示密码哈希
});

it('新增用户成功后重新拉取列表', async () => {
  seedSession('ADMIN');
  let created = false;
  server.use(
    http.post('/api/users', () => {
      created = true;
      return HttpResponse.json({
        code: 0, message: 'ok',
        data: { id: 3, username: 'newuser', displayName: '新同事', role: 'INSPECTOR', isActive: true, createdAt: '2026-11-16T10:00:00' },
      });
    }),
    http.get('/api/users', () =>
      HttpResponse.json({
        code: 0, message: 'ok',
        data: created
          ? { records: [newRow, adminRow], total: 2, page: 1, pageSize: 10 }
          : { records: [adminRow], total: 1, page: 1, pageSize: 10 },
      }),
    ),
  );
  renderAt(routes, '/users');
  await userEvent.click(await screen.findByRole('button', { name: '新增用户' }));
  await userEvent.type(screen.getByLabelText('登录名'), 'newuser');
  await userEvent.type(screen.getByLabelText('初始密码'), 'Newuser@123');
  await userEvent.type(screen.getByLabelText('显示姓名'), '新同事');
  await userEvent.click(screen.getByRole('button', { name: '确定' }));

  const newRowEl = (await screen.findByText('新同事')).closest('tr')!;
  expect(within(newRowEl).getByText('检验员')).toBeInTheDocument();
});
```

> `adminRow` / `newRow` 是测试文件顶部定义的两个 `UserSummary` 常量（内容与 `handlers.ts` 默认数据一致）。

- [ ] **Step 2: 运行，确认失败**

Run: `cd frontend && pnpm vitest run src/pages/users/UserListPage.test.tsx`
Expected: 找不到模块。

- [ ] **Step 3: 实现页面**

- MUI `Table`（`TableContainer component={Paper}` + `TableHead`/`TableBody`/`TableRow`/`TableCell`），列：登录名、显示姓名、角色、状态、创建时间、操作。**不要用 `@mui/x-data-grid`**：本页数据量小、操作列需要自定义按钮，原生 `Table` 更简单且测试更稳。
- 顶部筛选：`TextField size="small"`（关键字）+ 两个 `Select`（角色、状态）+ `Button variant="contained"` 文案"新增用户"。
- 底部分页：MUI `TablePagination`（`rowsPerPageOptions={[10, 20, 50]}`）。
- 新增/编辑弹窗：`Dialog` + `DialogTitle`/`DialogContent`/`DialogActions`；表单字段用 `TextField`，确定按钮文案"确定"，取消按钮文案"取消"。
- 行操作：两个 `Button size="small"`，文案"编辑"（弹窗改 displayName/role/isActive）、"重置密码"（弹窗输入新密码）。
- 所有写操作成功后 `queryClient.invalidateQueries({ queryKey: ['users'] })`。
- 角色列显示 `ROLE_LABELS[record.role]`；状态列用 `Chip size="small"`（启用/停用），`color="success"` / `color="default"`。
- 新增用户弹窗的 `TextField` 标签必须是"登录名""初始密码""显示姓名"，角色用 `Select` 配 `InputLabel` 文案"角色"；确定按钮文案"确定"（测试按这些文案定位）。

- [ ] **Step 4: 运行测试，确认通过**

Run: `cd frontend && pnpm vitest run src/pages/users/UserListPage.test.tsx`
Expected: 2 个用例 PASS。

- [ ] **Step 5: 前端全量检查**

Run:
```bash
cd frontend
pnpm vitest run
pnpm lint
pnpm build
```
Expected: 全部通过。

- [ ] **Step 6: 提交**

```bash
git add frontend
git commit -m "feat(user): 用户管理页面

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 9: 文档、留痕与阶段验收

**Files:**
- Modify: `CLAUDE.md`（「项目现状」与新增「命令」段）
- Modify: `README.md`
- Modify: `.superpowers/sdd/2026-11-16-foundation-auth/progress.md`（git-ignore，不入仓库）
- Test: 无新增测试，执行既有全部测试

**Interfaces:**
- Consumes: Task 1~8 全部产出
- Produces: 阶段交付三样（演示命令、测试结果、SDD 台账路径）

- [ ] **Step 1: 更新 `CLAUDE.md`**

把"仓库目前只有文档，没有代码"改为当前状态：后端 `backend/` 与前端 `frontend/` 已存在，写明目录职责；新增「命令」段：

```bash
# 后端（在 backend/ 下，需先设置 MYSQL_PASSWORD / REDIS_PASSWORD）
mvn spring-boot:run
mvn test

# 前端（在 frontend/ 下）
pnpm dev
pnpm vitest run
pnpm lint
pnpm build
```

- [ ] **Step 2: 更新 `README.md`**

补上默认账号表（`admin/Admin@123`、`engineer/Engineer@123`、`inspector/Inspector@123`）与本阶段已实现的功能范围（登录、登出、用户管理），并注明其余页面为占位。

- [ ] **Step 3: 启动联调验证**

一个终端 `cd backend && mvn spring-boot:run`，另一个 `cd frontend && pnpm dev`，浏览器打开 `http://localhost:5173`：
1. 用 `admin/Admin@123` 登录 → 进入 `/users`，能看到三个种子账号；
2. 新建一个 ENGINEER 账号 → 退出 → 用新账号登录 → 落到 `/projects`；
3. 用新账号直接访问 `/users` → 被送回 `/projects`；
4. 登出后浏览器后退或直接访问 `/users` → 回到登录页。

- [ ] **Step 4: 跑全量测试并留存结果**

Run:
```bash
cd backend && mvn test
cd ../frontend && pnpm vitest run
```
Expected: 后端 `BUILD SUCCESS`（SchemaTest 2、GlobalExceptionHandlerTest 2、JwtServiceTest 2、AuthApiTest 7、UserApiTest 7）；前端测试文件全部通过（labels、muiTheme、LoginPage、AppLayout、UserListPage）。把两份输出摘要写进 SDD 台账的「finish」记录。

- [ ] **Step 5: 补全 SDD 台账的 finish 记录**

写清：用户关键原话（"准备开发，不要开分支"、"分阶段多份计划"、"本机 MySQL 独立测试库 + Redis db15"、"纵向切片"）、关键产出（本计划路径、测试结果、AC-07 已验证）、被拒绝或纠偏（如 springdoc 版本回退）、翻车与返工。

- [ ] **Step 6: 提交**

```bash
git add CLAUDE.md README.md
git commit -m "docs(notes): 记录阶段一完成情况与运行命令

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 阶段一验收对照

| SRS 验收项 | 本阶段如何满足 | 验证位置 |
|---|---|---|
| AC-07 权限隔离（检验员访问标准编辑接口返回 403） | 现阶段先验证检验员访问用户管理返回 403 | `UserApiTest.inspectorCannotReachUserManagement` |
| BR-07 接口级角色校验 | `@PreAuthorize("hasRole('ADMIN')")` + 集成测试 | `UserApiTest` |
| FR-01 登录与登出（含服务端可控失效） | JWT + Redis 会话，登出删键 | `AuthApiTest.logoutRevokesTokenAndOldTokenIsRejected` |
| FR-02 用户管理 | 增改查、重置密码、停用即不能登录 | `UserApiTest` |
| UC-01 按角色进入不同首页 | `HOME_BY_ROLE` | `LoginPage.test.tsx` |
| SRS 5 表结构 | 6 表 2 视图由迁移建立 | `SchemaTest` |

标准编辑接口的 403 在阶段三补齐（届时 AC-07 才算完整闭合）。

---

## 阶段五前置规格 · P6 质量看板

本规格在 2026-11-17 的设计会话中定稿，签样页为 `design/aesthetic/preview/dashboard.html`，截图 `design/aesthetic/shots/dashboard-signoff.png`。阶段五实现看板时必须按此规格，不得另起布局。

### 布局（方案 A）

顶栏一行筛选（项目 / 批次 / 时间 / 刷新），其下 12 列 Bento 栅格，行高 132px，栏距 16px，卡内距 24px（主格 32px），内容最大宽 1360px，1440×900 首屏无纵向滚动。

```
K1 总检验数 ─ K2 合格率 ─ K3 未关闭缺陷 ─ K4 今日检验     （各 span 3）
HERO 批次合格率（span 6 × 2）   │ CH-01 趋势（span 6）
                                │ CH-02 帕累托（span 6）
CH-04 控制图（span 6）          │ CH-03 工序（span 3 × 2） │ CH-05 等级（span 3）
CH-08 处理时长（span 6）        │                          │ CH-07 人员（span 3）
```

### 实现要点

- 主格用 ECharts `gauge`（`startAngle: 180`、`endAngle: 0`、`radius: '92%'`），禁止用 SVG 手绘——签样页的弧长是手算的，ECharts 由库负责。
- CH-02 帕累托：柱为 `defect_type` 计数降序，累计折线终点必须为 100%（SRS 6.3）。
- CH-04 控制图：UCL/CL/LCL 取自该标准的 `std_upper` / (`std_upper`+`std_lower`)/2 / `std_lower`，**不另行计算**（SRS 6.3）。
- CH-03 横向条形：轴起点 85%，否则 91% 与 98% 视觉无差别（签样页踩过）。
- 所有 ECharts 的 `color` 数组从 `tokens.ts` 的 `color` 常量取，禁止写死色值；`textStyle.fontFamily` 用导出的 `fontFamily`。
- 空数据时渲染 `Typography color="text.secondary"` 文案"暂无数据"，不渲染空坐标轴（SRS 6.3 要求）。

### 断点

| 宽度 | 栅格 | 主格 |
|---|---|---|
| ≥1440 | 12 列，如上 | span 6 × 2 |
| 834–1439 | 6 列，KPI 2×2，图两两一行 | 全宽 × 2 |
| <834（390） | 单列，顺序 主格 → KPI → 其余图 | 全宽 |

### 验收

对照 `design/aesthetic/shots/dashboard-signoff.png` 逐格比对；`check-tokens.mjs` 与 `audit.mjs` 均需通过；六轴自评任一 <3 视为未完成。
