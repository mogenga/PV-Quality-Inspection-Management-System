# PV Quality Inspection Management System

光伏质量检验管理系统。

## 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 21 |
| Maven | 3.9+ |
| Node.js | 22+（前端，暂未实现） |
| pnpm | 9+（前端，暂未实现） |
| MySQL | 8.0+ |
| Redis | 7+ |

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

# 前端（尚未实现）
pnpm --dir frontend dev
```

## 测试

```bash
cd backend && mvn test
```

后端测试连接本机 `pvqims_test` 库与 Redis 第 15 号库（见 `application-test.yml`）。

## 默认账号

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `Admin@123` | 系统管理员 |
| `engineer` | `Engineer@123` | 工程师 |
| `inspector` | `Inspector@123` | 检验员 |

以上为开发环境默认账号，正式部署前须由管理员重置密码或停用。

## License

[MIT](LICENSE) © 2026 mogenga
