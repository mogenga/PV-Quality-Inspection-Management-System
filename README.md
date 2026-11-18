# PV Quality Inspection Management System

光伏质量检验管理系统。

## 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 21 |
| Maven | 3.9+ |
| Node.js | 24.15+（jsdom 31 要求；24.14 上测试也能通过） |
| pnpm | 11+ |
| MySQL | 8.0+ |
| Redis | 5.0+（仅用于登录会话） |

## 创建数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pvqims CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE IF NOT EXISTS pvqims_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

表结构与种子账号由 Flyway 在应用启动（或测试运行）时自动创建。

## 本地配置

1. 复制配置模板并填入真实密码：

   ```bash
   cp backend/src/main/resources/application-local.yml.example backend/src/main/resources/application-local.yml
   ```

2. 运行测试前同样复制测试配置：

   ```bash
   cp backend/src/test/resources/application-test.yml.example backend/src/test/resources/application-test.yml
   ```

两个真实配置文件均已被 `.gitignore` 忽略，禁止提交。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MYSQL_USERNAME` | `root` | 数据库用户名 |
| `MYSQL_PASSWORD` | 空 | 数据库密码 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `JWT_SECRET` | 开发用默认值 | JWT 签名密钥，生产环境必须覆盖（至少 32 字节） |

`application-local.yml` 中的字面量配置优先于上述占位符默认值。

## 运行

```bash
# 后端（默认端口 8080，激活 local profile）
mvn -f backend spring-boot:run

# 前端（默认端口 5173，/api 由 Vite 代理到 8080）
pnpm --dir frontend dev
```

打开 `http://localhost:5173`，用下方默认账号登录。

## 已实现范围

阶段一至阶段四交付（登录/登出、用户管理、项目与批次、校验标准、校验录入）：

| 页面 | 状态 |
|---|---|
| P1 登录 | 已实现 |
| P7 用户管理 | 已实现（管理员） |
| P2 项目与批次 | 已实现（工程师可增删改查，检验员/管理员只读；关闭项目下批次只读） |
| P3 校验标准 | 已实现（工程师可增改与启停，管理员只读；关闭项目下标准只读） |
| P4 校验录入 | 已实现（工程师与检验员可录入，判定在服务端执行） |
| P5 缺陷处理 / P6 质量看板 | 占位页，显示"功能开发中" |

P3 的项目下拉一次只加载前 100 个项目（接口 `pageSize` 上限为 100），第 101 个及以后的项目暂时无法在该页选择。这是当前已接受的限制。

看板 P6 的布局与图表规格已定稿，见 `docs/superpowers/plans/2026-11-16-foundation-auth.md` 末尾「阶段五前置规格」，签样图 `design/aesthetic/shots/dashboard-signoff.png`。

## 测试

```bash
# 后端
cd backend && mvn test

# 前端
cd frontend && pnpm vitest run && pnpm lint && pnpm build
```

后端测试连接本机 `pvqims_test` 库与 Redis 第 15 号库（见 `application-test.yml`）。最后确认为 112 个后端测试、66 个前端测试全绿。

## 默认账号

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `Admin@123` | 系统管理员 |
| `engineer` | `Engineer@123` | 工程师 |
| `inspector` | `Inspector@123` | 检验员 |

以上为开发环境默认账号，正式部署前须由管理员重置密码或停用。

## License

[MIT](LICENSE) © 2026 mogenga
