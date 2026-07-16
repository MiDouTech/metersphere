# MeterSphere 本地与线上环境对齐 — 改造方案摘要

> **用途**：供其他 AI 对话或开发者直接读取，理解当前仓库的本地开发体系、线上部署边界，以及「本地/线上差异化最小化」的改造任务清单。  
> **适用分支**：含 `start.ps1`、`deploy/nacos/`、`application-local.properties` 等改动的功能分支（如 `chenqifen`、`feature/v3.x-task001-community-xpack-license` 等）。  
> **最后更新**：2026-07-03

---

## 0. 阅读指引（给其他聊天）

若你被要求「按本文档改造仓库」，请优先完成 **§6 待办清单** 中的 P0 项，并在改动后验证：

1. `.\start.ps1 -UseNacos` 能正常启动后端
2. `.\scripts\seed-nacos-config.ps1` 能推送配置到本地 Nacos
3. `git status` 不出现 `local-runtime/`、`backend/app/local-runtime/` 下的日志与生成配置

---

## 1. 项目配置架构（现状）

### 1.1 配置加载链路

```
classpath:commons.properties                    ← 框架固定项（端口 8081、Flyway、MyBatis 等）
file:${MS_CONFIG_DIR}/metersphere.properties    ← 本地默认 ./local-runtime/conf/
file:/opt/metersphere/conf/metersphere.properties ← 线上容器/VM 路径
```

`Application.java` 通过 `@PropertySource` 同时声明上述路径，`ignoreResourceNotFound=true`，存在则加载。

### 1.2 Spring Profile 分层

| 文件 | Profile | 作用 |
|------|---------|------|
| `application.properties` | 默认 | 应用名、兼容性开关 |
| `application-local.properties` | `local` | **禁用 Nacos**，仅读本地文件 |
| `application-nacos.properties` | `nacos` | **启用 Nacos** 配置中心 |

### 1.3 配置模板位置

| 路径 | 用途 |
|------|------|
| `deploy/nacos/dev/metersphere.properties` | 本地/开发 Nacos 配置模板（namespace=`dev`） |
| `deploy/nacos/prod/metersphere.properties` | 生产 Nacos 配置模板（namespace=`prod`） |
| `local-runtime/conf/metersphere.properties` | **运行时生成**，由 `start.ps1` 从 dev 模板复制，**不提交 Git** |
| `local-runtime/conf/redisson.yml` | Redis 连接，首次启动时脚本生成，**不提交 Git** |

### 1.4 默认启动行为（当前）

```powershell
.\start.ps1              # spring.profiles.active=local，读 local-runtime/conf/
.\start.ps1 -UseNacos     # spring.profiles.active=nacos，从 Nacos 拉取
```

**问题**：默认 `local` 模式与线上 Nacos 模式不一致，是本地/线上差异的主要来源。

---

## 2. 内容分类：线上 / 本地 / 不必提交

### 2.1 与线上部署相关（应纳入版本库）

| 类别 | 路径 | 说明 |
|------|------|------|
| 镜像构建 | `Dockerfile`、`frontend/Dockerfile` | 后端 JAR + 前端 Nginx |
| 生产配置模板 | `deploy/nacos/prod/metersphere.properties` | 上传 Nacos prod namespace，非直接运行 |
| Nacos Profile | `application-nacos.properties` | 线上配置加载方式 |
| 线上路径约定 | `Application.java` 中 `/opt/metersphere/conf/` | 容器内配置目录 |
| 业务源码 | `backend/`、`frontend/src/` | 编译进镜像 |
| CI | `.github/workflows/*` | PR 校验等 |

线上典型连接（prod 模板使用环境变量占位）：

```properties
spring.datasource.url=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/metersphere?...
kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS}
minio.endpoint=${MINIO_ENDPOINT}
logging.file.path=/opt/metersphere/logs/metersphere
spring.redis.redisson.file=file:/opt/metersphere/conf/redisson.yml
jmeter.home=/opt/jmeter
run.mode=standalone
logger.sql.level=info
```

### 2.2 本地调试相关（工具链，建议提交以便协作）

| 类别 | 路径 |
|------|------|
| 一键脚本 | `start.ps1`、`stop.ps1`、`start.cmd`、`stop.cmd` |
| 辅助脚本 | `scripts/*.ps1` |
| 本地中间件 | `dev/docker-compose.yml`（MySQL/Redis/Kafka/MinIO/Nacos） |
| 环境变量 | `dev/env.ps1` |
| 开发配置模板 | `deploy/nacos/dev/metersphere.properties` |
| 本地 Profile | `application-local.properties`（保留作紧急回退） |
| 前端开发 | `frontend/.env.development`、`frontend/config/vite.config.dev.ts` |

### 2.3 不必提交 Git（已在 `.gitignore`）

```
local-runtime/                          # 日志、PID、jmeter、生成的 conf
backend/app/local-runtime/              # 从 backend/app 启动时的日志
backend/app/src/main/resources/static   # 从 frontend/public 同步
frontend/.env.development.local         # 个人环境变量
**/logs/metersphere/
target/、node_modules/、.idea/ 等
```

---

## 3. 本地中间件能否直接部署上线？

**结论：组件种类相同，但不能原样搬 `dev/docker-compose.yml`。**

| 维度 | `dev/docker-compose.yml` | 线上要求 |
|------|--------------------------|----------|
| 密码 | 固定 `Password123@*` | 强密码 + Secret 管理 |
| Nacos | `NACOS_AUTH_ENABLE=false` | 开启认证 |
| Kafka | 单节点 | 多副本/集群（视规模） |
| 端口 | 映射到宿主机 | 内网访问 |
| 备份 | 无 | 必须有 |
| run.mode | dev 模板为 `local` | `standalone` |

**推荐上线方式**：中间件与应用分离部署；应用读 Nacos prod 配置；仅迁移 **MySQL 数据** 和 **MinIO 文件**（Redis/Kafka 通常不迁移）。

---

## 4. 本地 vs 线上：当前差异矩阵

| 维度 | 默认本地 | 线上 | 改造目标 |
|------|----------|------|----------|
| 配置来源 | `local` profile + 文件 | Nacos | **统一 Nacos** |
| run.mode | `local` | `standalone` | **统一 standalone** |
| SQL 日志 | `debug` | `info` | **统一 info** |
| 配置 key 结构 | dev 模板有额外 `sessionVariables` | prod 用 `${ENV}` 占位 | **dev 对齐 prod 结构** |
| 路径 | `./local-runtime/...` | `/opt/metersphere/...` | key 一致，值可不同 |
| 前端 | Vite 5173 + 代理 | Nginx 静态资源 | 日常 Vite；发版前 build 验证 |
| 后端启动 | `spring-boot:run` | JAR/Docker | 发版前可用 jar 验证 |
| Nacos 认证 | 关闭 | 开启 | 本地可选开启以进一步对齐 |

---

## 5. 改造方案：本地/线上差异化最小化

### 5.1 设计原则

1. **单一配置真相源**：`deploy/nacos/prod/metersphere.properties` 为结构基准；`dev` 从 `prod` 复制后仅改地址/密码/路径。
2. **本地默认走 Nacos**：与线上一致的配置加载链路。
3. **仅允许三类差异**：主机地址、密码/密钥、文件系统路径。
4. **`local` profile 保留但不作为默认**：Nacos 不可用时回退。

### 5.2 目标架构

```
dev/docker-compose.yml
        ↓
deploy/nacos/dev/metersphere.properties  ← 结构与 prod 一致
        ↓
scripts/seed-nacos-config.ps1  →  Nacos (namespace=dev)
        ↓
start.ps1 -UseNacos  →  spring.profiles.active=nacos
        ↓
Application  +  commons.properties  +  Nacos metersphere.properties
```

### 5.3 `deploy/nacos/dev/metersphere.properties` 目标内容

将现有 dev 模板改为与 prod **同 key、同 run.mode、同日志级别**，示例：

```properties
# 结构与 deploy/nacos/prod/metersphere.properties 一致
# 仅默认值指向本地 Docker

spring.datasource.url=jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/metersphere?autoReconnect=false&useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8&zeroDateTimeBehavior=convertToNull&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=${MYSQL_USER:root}
spring.datasource.password=${MYSQL_PASSWORD:Password123@mysql}

kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:127.0.0.1:9092}

minio.endpoint=${MINIO_ENDPOINT:http://127.0.0.1:9000}
minio.access-key=${MINIO_ACCESS_KEY:admin}
minio.secret-key=${MINIO_SECRET_KEY:Password123@minio}

# 路径：本地与线上唯一合理差异
logging.file.path=./local-runtime/logs/metersphere
spring.redis.redisson.file=file:./local-runtime/conf/redisson.yml
jmeter.home=./local-runtime/jmeter

run.mode=standalone
logger.level=INFO
logger.sql.level=info
```

**删除项**（相对当前 dev 模板）：

- `run.mode=local`
- `logger.sql.level=debug`
- JDBC URL 中的 `sessionVariables=sql_mode=...`（MySQL 已在 `docker-compose.yml` 的 `command` 中设置）

### 5.4 `dev/env.ps1` 补充（可选）

```powershell
$env:NACOS_SERVER_ADDR = "127.0.0.1:8848"
$env:NACOS_NAMESPACE = "dev"
$env:NACOS_GROUP = "METERSPHERE"
# 本地 MySQL 等（与 prod 环境变量名一致）
$env:MYSQL_HOST = "127.0.0.1"
$env:MYSQL_PORT = "3306"
$env:MYSQL_USER = "root"
$env:MYSQL_PASSWORD = "Password123@mysql"
```

### 5.5 `start.ps1` 改造建议

| 改动 | 说明 |
|------|------|
| **默认启用 Nacos** | 去掉 `-UseNacos` 开关或默认 `$UseNacos = $true` |
| 新增 `-UseLocalFile` | 显式回退到 `local` profile（Nacos 故障时） |
| `Initialize-LocalConfig` | 仅在 `-UseLocalFile` 时复制 dev 模板到 `local-runtime/conf/` |
| 启动前自动 `seed-nacos-config` | Nacos 模式启动前推送/更新 dev 配置 |

### 5.6 前端策略

| 模式 | 命令 | 与线上相似度 |
|------|------|--------------|
| 日常开发 | `pnpm run dev`（5173） | 中（API 路径一致即可） |
| 发版前验证 | `pnpm build` → 同步 static → 仅启后端 8081 | 高 |

发版前验证命令：

```powershell
cd frontend && pnpm build
.\start.ps1 -BackendOnly -SkipDeps   # Nacos 模式
# 访问 http://localhost:8081
```

### 5.7 MySQL 参数对齐

`dev/docker-compose.yml` 已配置：

- `lower_case_table_names=1`
- `sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION`

线上 MySQL 须保持相同参数，避免大小写、SQL 模式导致迁移脚本或查询行为不一致。

### 5.8 `redisson.yml` 对齐

本地与线上使用相同 YAML 结构（`singleServerConfig` 或集群配置），仅 `address`、`password`、`database` 不同。

---

## 6. 待办清单（给其他聊天的实施任务）

### P0 — 配置对齐（必做）

- [ ] 重写 `deploy/nacos/dev/metersphere.properties`，结构与 `prod` 一致（见 §5.3）
- [ ] 修改 `start.ps1`：默认 Nacos 模式；`-UseLocalFile` 作为回退
- [ ] 确认 `scripts/seed-nacos-config.ps1` 在启动流程中被调用
- [ ] 验证 `.\start.ps1` 后后端可通过 Nacos 连接 MySQL/Redis/Kafka/MinIO

### P1 — 文档与体验

- [ ] 更新 `docs/chenqifen-本地开发摘要.md` 中的「默认本地模式」描述
- [ ] 在 `start.ps1` 帮助文本中说明 Nacos 为默认
- [ ] `dev/env.ps1` 补充与 prod 一致的环境变量名

### P2 — 发版前验证链路

- [ ] 在 `scripts/` 增加 `build-and-verify.ps1`：frontend build + 后端 static 同步 + 仅启后端
- [ ] 可选：本地 Nacos 开启 `NACOS_AUTH_ENABLE=true` 进一步对齐线上

### P3 — 不建议做

- ❌ 将 `dev/docker-compose.yml` 原样用于生产
- ❌ 提交 `local-runtime/conf/` 到 Git
- ❌ 长期维护两套完全不同的 dev/prod 配置 key

---

## 7. 验证检查表

改造完成后逐项检查：

| # | 检查项 | 期望 |
|---|--------|------|
| 1 | `git status` | 无 `local-runtime/`、`backend/app/local-runtime/` 跟踪文件 |
| 2 | Nacos 控制台 | `dev` namespace 存在 `metersphere.properties`（group=METERSPHERE） |
| 3 | 后端日志 | `run.mode=standalone`，无 Nacos 认证无限重试 |
| 4 | 登录 | http://localhost:8081/is-login 返回正常 |
| 5 | 前端 | http://localhost:5173 可访问（开发模式） |
| 6 | SQL 行为 | 无 ONLY_FULL_GROUP_BY 类错误（已知修复：`ExtProjectMemberMapper.xml`） |
| 7 | 发版前 | build 后仅后端 8081 可完整访问主要页面 |

---

## 8. 本地中间件默认连接信息

| 组件 | 地址 | 账号/密码 |
|------|------|-----------|
| MySQL | `127.0.0.1:3306` / 库 `metersphere` | root / `Password123@mysql` |
| Redis | `127.0.0.1:6379` | `Password123@redis` |
| Kafka | `127.0.0.1:9092` | — |
| MinIO | `http://127.0.0.1:9000` | admin / `Password123@minio` |
| Nacos | `http://127.0.0.1:8848/nacos` | dev compose 默认无认证 |

---

## 9. 常用命令速查

```powershell
# 【改造后推荐】Nacos 模式全量启动
.\start.ps1

# 中间件已运行
.\start.ps1 -SkipDeps

# 紧急回退本地文件模式（改造后）
.\start.ps1 -UseLocalFile

# 推送/更新 Nacos 配置
.\scripts\seed-nacos-config.ps1

# 仅 Docker 中间件
docker compose -f dev\docker-compose.yml up -d

# 停止
.\stop.ps1

# 后端编译
.\mvnw.cmd -f backend\pom.xml install -pl app -am -DskipTests -DskipAntRunForJenkins=true
```

---

## 10. 关键文件索引

```
metersphere/
├── start.ps1 / stop.ps1                 # 一键启停（改造重点）
├── dev/
│   ├── docker-compose.yml               # 本地中间件
│   └── env.ps1                          # MS_CONFIG_DIR、Nacos 等环境变量
├── deploy/nacos/
│   ├── dev/metersphere.properties       # 【改造】对齐 prod 结构
│   └── prod/metersphere.properties      # 生产模板（结构基准）
├── scripts/
│   ├── seed-nacos-config.ps1            # 推送 dev 配置到 Nacos
│   └── setup-local-env.ps1
├── backend/app/src/main/
│   ├── java/.../Application.java        # @PropertySource 多路径
│   └── resources/
│       ├── application-local.properties
│       ├── application-nacos.properties
│       └── commons.properties
├── local-runtime/                       # 生成目录，gitignore
├── .gitignore                           # 本地产物忽略规则
└── docs/
    ├── chenqifen-本地开发摘要.md         # 历史本地开发文档
    └── summary/
        └── 本地与线上环境对齐改造方案.md  # 本文档
```

---

## 11. 已知问题与修复记录（避免重复踩坑）

| 问题 | 处理 |
|------|------|
| Maven `Unable to find main class` | 使用 `-f backend/app/pom.xml` |
| `frontend/dist does not exist` | 加 `-DskipAntRunForJenkins=true` |
| Nacos 日志冲突 | JVM：`-Dnacos.logging.default.config.enabled=false` |
| Nacos 认证失败无限重试 | 使用无认证本地 Nacos，或正确配置 `NACOS_USERNAME/PASSWORD` |
| `SELECT DISTINCT` + `ORDER BY` MySQL 报错 | `ExtProjectMemberMapper.xml` 子查询修复 |
| 前端 Logo/静态资源裂图 | 检查 MinIO、`.env.development` 格式、`/base-display` 接口 |
| PowerShell 启动参数解析 | Maven 参数放入 `$args` 数组 |

---

## 12. 给其他聊天的 Prompt 模板

可直接复制以下段落开启新对话：

```
请阅读仓库中的 docs/summary/本地与线上环境对齐改造方案.md，
按 §6 待办清单 P0 项实施改造：
1. 重写 deploy/nacos/dev/metersphere.properties 对齐 prod 结构
2. 修改 start.ps1 默认使用 Nacos 模式，增加 -UseLocalFile 回退
3. 启动流程集成 seed-nacos-config.ps1
完成后按 §7 验证检查表自测。
```

---

*文档维护：改造落地后请同步更新 §6 待办勾选状态及 §1.4 默认启动行为描述。*
