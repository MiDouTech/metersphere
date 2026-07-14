# 本地开发一键启动（已归档 / Deprecated）

> **状态**：2026-07 起团队改为**云开发**，日常不再维护本机 Docker 全量启动。  
> **生产/云环境部署**请看：[deploy/README.md](../../deploy/README.md)

本目录仅保留历史工具，便于应急对照，**不作为推荐开发路径**。

## 目录结构

```
legacy/local-dev/
├── start.ps1 / start.cmd      # 一键启动
├── stop.ps1 / stop.cmd        # 一键停止
├── dev/
│   ├── docker-compose.yml     # 本机 MySQL/Redis/Kafka/MinIO/Nacos
│   └── env.ps1                # 本机环境变量
├── nacos/
│   └── metersphere.properties # 本地 Nacos/文件配置模板（含弱口令，禁止用于生产）
└── scripts/
    ├── setup-local-env.ps1
    ├── start-local-deps.ps1
    ├── seed-nacos-config.ps1
    ├── check-local-env.ps1
    └── start-dev.ps1
```

## 应急用法（不推荐）

在**仓库根目录**执行：

```powershell
.\legacy\local-dev\start.cmd
.\legacy\local-dev\stop.cmd
```

或：

```powershell
.\legacy\local-dev\start.ps1 -SkipDeps
.\legacy\local-dev\scripts\check-local-env.ps1
```

根目录的 `start.ps1` / `stop.ps1` 已改为废弃提示，直接运行会退出并引导到本目录。

## 仍保留在主仓库的相关项

| 路径 | 说明 |
|------|------|
| `backend/.../application-local.properties` | Spring `local` profile，属运行时能力，未迁移 |
| `scripts/verify-agent-api.ps1` | 可用于对云端/本机 API 做校验 |
| `deploy/` | 云端/生产部署（Nacos 方案） |

## 安全提醒

- `nacos/metersphere.properties` 与 `docker-compose.yml` 中的 `Password123@*` **仅本机弱口令**，禁止用于共享/生产环境。
- 真实账号密码只放在服务器、发布平台、Nacos，勿写回 Git。
