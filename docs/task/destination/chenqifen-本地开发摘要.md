# MeterSphere 本地开发与 Nacos 改造摘要

> 分支：`chenqifen`（不与主分支合并）  
> 文档生成自开发对话记录，便于后续本地运行与维护。

---

## 1. 任务目标

- 检视并调整项目，使其可在本地运行
- 改造为支持从 **Nacos** 读取配置
- 修复运行后可能出现的前端静态资源、SQL 等问题
- 提供一键启动 / 停止脚本

---

## 2. 配置架构

```
application.properties          → 基础配置（应用名、兼容性开关等）
application-local.properties    → 本地 profile：禁用 Nacos，读本地文件
application-nacos.properties    → Nacos profile：从配置中心拉取
commons.properties              → 框架固定配置（端口、MyBatis、Flyway 等）
local-runtime/conf/metersphere.properties → 本地业务配置（DB/Redis/Kafka/MinIO）
deploy/nacos/dev/metersphere.properties   → Nacos 开发环境配置模板
deploy/nacos/prod/metersphere.properties  → Nacos 生产环境配置模板
```

**默认本地模式**：使用 `local` profile，通过 `Application.java` 的 `@PropertySource` 加载 `local-runtime/conf/metersphere.properties`，不依赖 Nacos 认证。

**Nacos 模式**：启动时加 `-UseNacos`，激活 `nacos` profile，从 Nacos 拉取 `metersphere.properties`（namespace=dev, group=METERSPHERE）。

---

## 3. 主要代码与配置变更

| 类别 | 文件/位置 | 说明 |
|------|-----------|------|
| Nacos | `backend/app/pom.xml` | 增加 `spring-cloud-starter-alibaba-nacos-config` |
| Nacos | `application.properties` / `application-local.properties` / `application-nacos.properties` | 配置分层，local/nacos 分离 |
| 本地路径 | `commons.properties` | 日志、Redisson、JMeter 默认指向 `./local-runtime/` |
| 本地路径 | `Application.java` | 增加 `local-runtime/conf/metersphere.properties` 回退 |
| 日志 | `logback-spring.xml` | 使用 `springProperty` 读取日志路径 |
| SQL | `ExtProjectMemberMapper.xml` | 修复 `SELECT DISTINCT` + `ORDER BY` 冲突（MySQL ONLY_FULL_GROUP_BY） |
| 前端 | `.env.development` | 修正 `VITE_API_BASE_URL`、`VITE_DEV_DOMAIN` 格式 |
| 前端 | `.env.development.local.example` | 本地环境变量示例 |
| 中间件 | `dev/docker-compose.yml` | MySQL / Redis / Kafka / MinIO / Nacos |
| 环境变量 | `dev/env.ps1` | MS_CONFIG_DIR、日志路径等 |

---

## 4. 一键脚本

| 脚本 | 作用 |
|------|------|
| **`start.cmd` / `start.ps1`** | 一键启动（中间件 + 配置 + 后端 + 前端） |
| **`stop.cmd` / `stop.ps1`** | 一键停止 |
| `scripts/setup-local-env.ps1` | 初始化 `local-runtime` 并推送 Nacos（可选） |
| `scripts/start-local-deps.ps1` | 仅启停 Docker 中间件 |
| `scripts/check-local-env.ps1` | 环境检查 |

### 常用命令

```powershell
# 全量启动
.\start.cmd

# 中间件已运行时，只启前后端
.\start.ps1 -SkipDeps

# 从 Nacos 读配置（需 Nacos 认证正确）
.\start.ps1 -UseNacos

# 仅后端 / 仅前端
.\start.ps1 -BackendOnly -SkipDeps
.\start.ps1 -FrontendOnly -SkipDeps

# 停止全部（含 Docker）
.\stop.cmd

# 仅停前后端，保留中间件
.\stop.ps1 -KeepDeps
```

### 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 | http://localhost:8081 |
| Nacos 控制台 | http://localhost:8848/nacos |

### 日志

- 后端：`local-runtime/logs/backend-startup.log`
- 前端：`local-runtime/logs/frontend-startup.log`

---

## 5. 本地中间件默认账号

| 组件 | 连接 | 账号/密码 |
|------|------|-----------|
| MySQL | localhost:3306 / `metersphere` | root / `Password123@mysql` |
| Redis | localhost:6379 | `Password123@redis` |
| Kafka | localhost:9092 | — |
| MinIO | http://localhost:9000 | admin / `Password123@minio` |
| Nacos | http://localhost:8848/nacos | 视 Docker 配置（dev 模板默认关闭认证） |

---

## 6. 启动调试中遇到的问题与处理

| 问题 | 原因 | 处理 |
|------|------|------|
| Maven `Unable to find main class` | `spring-boot:run` 跑在父 POM 上 | 改为 `-f backend/app/pom.xml` |
| `frontend/dist does not exist` | 本地开发未构建前端静态资源 | 加 `-DskipAntRunForJenkins=true` |
| Nacos + Logback 冲突 | Nacos 客户端重复初始化日志 | JVM 参数 `-Dnacos.logging.default.config.enabled=false` |
| Nacos 无限重试 `User nacos not found` | 本地 Nacos 认证与配置不一致 | 默认 `local` profile 禁用 Nacos |
| Spring Cloud 版本校验失败 | Boot 3.5 与 Cloud 版本不匹配 | `spring.cloud.compatibility-verifier.enabled=false` |
| PowerShell 解析 Maven 参数失败 | `-Dspring-boot.run.profiles=local` 未加引号 | 参数加引号或写入 `$args` 数组 |
| `Start-Process` 重定向失败 | stdout/stderr 不能指向同一文件 | stderr 改为 `*.err` |
| 前端 `pnpm` 启动失败 | Windows 需使用 `pnpm.cmd` | `Resolve-DevCommand` 解析 `.cmd` 路径 |
| Docker 未运行 / 镜像拉取失败 | Docker Desktop 未启动或网络问题 | 先启 Docker；已有容器可直接 `-SkipDeps` |
| MySQL Connection refused | 中间件未启动 | 先 `docker compose up -d` 或 `.\start.cmd` |
| 前端静态资源 / Logo 裂图 | 后端或 MinIO 不可用、`.env` 格式错误 | 修 env、确保 MinIO 与 `/base-display` 接口可达 |
| SQL 500 导致页面异常 | `ExtProjectMemberMapper` DISTINCT+ORDER BY | 子查询先排序再 DISTINCT |

---

## 7. 开发与构建说明

- **JDK**：21  
- **前端包管理**：`pnpm`（勿用 `npm install`，项目含 `link:` 协议依赖）  
- **前后端分离开发**：无需 `frontend/dist`，后端启动需 `-DskipAntRunForJenkins=true`  
- **一体化部署**（后端托管静态资源）：需先 `cd frontend && pnpm run build`  
- **后端编译**：`.\mvnw.cmd -f backend\pom.xml install -pl app -am -DskipTests -DskipAntRunForJenkins=true`  
- **IDE 启动**：运行 `io.metersphere.Application`，Active profiles 设为 `local`，并加载 `dev/env.ps1` 中的环境变量

---

## 8. 分支说明

- 所有改动仅在 **`chenqifen`** 分支进行  
- **请勿与主分支合并**（按任务要求）

---

## 9. 推荐日常流程

```powershell
# 首次或长时间未用
.\start.cmd

# 日常开发（中间件已在跑）
.\start.ps1 -SkipDeps

# 只改后端
.\stop.ps1 -KeepDeps
.\start.ps1 -BackendOnly -SkipDeps

# 收工
.\stop.cmd          # 全停
# 或
.\stop.ps1 -KeepDeps  # 只停前后端
```

---

## 10. 相关文件索引

```
metersphere/
├── start.cmd / start.ps1      # 一键启动
├── stop.cmd / stop.ps1        # 一键停止
├── dev/
│   ├── docker-compose.yml
│   └── env.ps1
├── deploy/nacos/
│   ├── dev/metersphere.properties
│   └── prod/metersphere.properties
├── local-runtime/
│   └── conf/metersphere.properties
├── backend/app/src/main/resources/
│   ├── application.properties
│   ├── application-local.properties
│   └── application-nacos.properties
└── docs/
    └── chenqifen-本地开发摘要.md   # 本文档
```
