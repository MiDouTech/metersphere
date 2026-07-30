# MeterSphere MCP — 缺陷读写接口使用文档（供 AI / Agent 自配置）

> 【AI生成】已人工审核确认（工程落地后请复核 Token Scope 与环境地址）  
> 日期：2026-07-29  
> 适用包：`@midoo/metersphere-mcp`（仓库内 `metersphere-mcp/`）  
> 后端：`/api/agent/v1/bug/*`

本文目标：让 AI 读取后即可自行配置 MCP、调用缺陷读写工具，无需人工逐步教操作。

---

## 1. 一句话架构

```
AI Client (Cursor/Claude)  --stdio MCP-->  metersphere-mcp (Node)
                                              |
                                              | HTTPS + Authorization: Bearer msat_xxx
                                              v
                                         MeterSphere Agent API
                                         /api/agent/v1/bug/*
```

- MCP **无业务逻辑**，只做 Tool → REST 转发。
- 鉴权依赖 **Agent Token**（`msat_` 前缀），Scope 控制读/写。

---

## 2. 环境变量（必填）

| 变量 | 必填 | 说明 |
|------|------|------|
| `MS_BASE_URL` | 是 | 平台根地址，无尾斜杠。例：`https://msp.ebcone.cn` 或 `http://localhost:8081` |
| `MS_AGENT_TOKEN` | 是 | Agent Token，`msat_...` |
| `MS_PROJECT_ID` | 是 | 默认项目 ID；Tool 可覆盖 `projectId` |
| `MS_TEST_PLAN_ID` | 否 | 用例检索默认计划；缺陷工具不依赖 |

Token Scope 要求：

| 能力 | 所需 Scope |
|------|------------|
| 检索 / 详情 | `BUG_READ` 或 `BUG_WRITE` 或 `AGENT_ALL` |
| 创建 / 更新 / 关联用例 | `BUG_WRITE` 或 `AGENT_ALL` |

说明：`BUG_WRITE` **隐含** `BUG_READ`。

---

## 3. Cursor MCP 配置模板（复制即用）

### 3.1 项目级（仓库根）

复制 `.cursor/mcp.json.example` → `.cursor/mcp.json`（勿提交真实 Token）：

```json
{
  "mcpServers": {
    "metersphere": {
      "command": "node",
      "args": ["metersphere-mcp/dist/index.js"],
      "env": {
        "MS_BASE_URL": "https://YOUR_MS_HOST",
        "MS_AGENT_TOKEN": "msat_YOUR_TOKEN",
        "MS_PROJECT_ID": "YOUR_PROJECT_ID",
        "MS_TEST_PLAN_ID": ""
      }
    }
  }
}
```

**推荐（平台下发）**：登录 MeterSphere → **系统设置 → Agent 集成** → 下载 MCP 包 + 填写 Token/项目后「复制 mcp.json」。

路径相对**仓库根**。用户级配置请把 `args` 改为绝对路径。

### 3.2 首次装配步骤（AI 可按序执行）

1. 确认仓库存在目录 `metersphere-mcp/`。
2. 执行：
   ```bash
   cd metersphere-mcp
   npm install
   npm run build
   ```
3. 写入 MCP 配置（上表环境变量）。
4. 重启 Cursor / 重载 MCP；确认 `metersphere` 为已连接。
5. 用 `search_bugs` 做连通性冒烟（见第 5 节）。

---

## 4. 缺陷相关 MCP Tools

| Tool | HTTP | Scope | 用途 |
|------|------|-------|------|
| `search_bugs` | `POST /api/agent/v1/bug/search` | BUG_READ | 按关键词/状态/处理人分页检索 |
| `get_bug` | `GET /api/agent/v1/bug/{bugId}` | BUG_READ | 详情（含 description、tags、customFields） |
| `create_bug` | `POST /api/agent/v1/bug/create` | BUG_WRITE | 创建；可带 caseId 关联用例 |
| `update_bug` | `POST /api/agent/v1/bug/update` | BUG_WRITE | 更新标题/描述/标签/自定义字段 |
| `relate_bug_case` | `POST /api/agent/v1/bug/relate-case` | BUG_WRITE | 已有缺陷补关联用例 |

### 4.1 `search_bugs`

**入参**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projectId` | string | 否 | 默认取 `MS_PROJECT_ID` |
| `query` | string | 否 | 标题 / 编号 / 标签关键词 |
| `status` | string[] | 否 | 平台状态值列表 |
| `handleUserIds` | string[] | 否 | 处理人用户 ID |
| `current` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 1–100，默认 50（服务端强制上限 100） |

**出参要点**：`{ total, bugs: [{ id, num, title, status, statusName, handleUser, ... }] }`

### 4.2 `get_bug`

| 字段 | 类型 | 必填 |
|------|------|------|
| `bugId` | string | 是 |

**出参**：单条缺陷；`customFields` 为 `fieldId -> value`。

### 4.3 `create_bug`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | 是 | 标题 |
| `projectId` | string | 否 | 默认项目 |
| `description` | string | 否 | 描述 |
| `templateId` | string | 否 | 空则用项目默认缺陷模板 |
| `tags` | string[] | 否 | 标签 |
| `caseId` | string | 否 | 创建时关联功能用例 |
| `caseType` | string | 否 | 默认 `FUNCTIONAL` |
| `testPlanId` / `testPlanCaseId` | string | 否 | 计划内关联上下文 |
| `customFields` | object | 视模板 | 必填模板字段缺失会 4xx |

### 4.4 `update_bug`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `bugId` | string | 是 | |
| `projectId` | string | 否* | 须与缺陷所属项目一致（服务端校验） |
| `title` / `description` / `tags` | | 否 | `tags=null` 不改，空数组清空 |
| `templateId` | string | 否 | 默认沿用原模板 |
| `customFields` | object | 否 | **与已有自定义字段合并**（可改状态/处理人等字段 ID） |

\* MCP client 会注入默认 `projectId`；调用时建议显式传入。

### 4.5 `relate_bug_case`

| 字段 | 类型 | 必填 |
|------|------|------|
| `bugId` | string | 是 |
| `caseIds` | string[] | 是，至少 1 个 |
| `projectId` | string | 否 |
| `caseType` | string | 否，默认功能用例 |

---

## 5. 推荐调用顺序（Agent 工作流）

### 5.1 查缺陷

1. `search_bugs`（`query` / `status`）→ 取 `id`
2. `get_bug` → 读描述与 `customFields`

### 5.2 执行失败建缺陷

1. （可选）`get_functional_case` / 执行回写 `ERROR`
2. `create_bug`：`title` + `description` + `caseId` + 必要 `customFields`
3. 若创建时未关联：`relate_bug_case`

### 5.3 改状态 / 改处理人

1. `get_bug` 拿到当前 `customFields` 与字段 ID
2. `update_bug`，只传需要覆盖的 `customFields` 键值（服务端合并）

---

## 6. 等价 REST（调试 / 无 MCP 时）

Header：`Authorization: Bearer msat_xxx`，`Content-Type: application/json`

```http
POST {MS_BASE_URL}/api/agent/v1/bug/search
{"projectId":"...","query":"登录","current":1,"pageSize":20}

GET  {MS_BASE_URL}/api/agent/v1/bug/{bugId}

POST {MS_BASE_URL}/api/agent/v1/bug/create
{"projectId":"...","title":"...","description":"...","caseId":"..."}

POST {MS_BASE_URL}/api/agent/v1/bug/update
{"projectId":"...","bugId":"...","title":"...","customFields":{"fieldId":"value"}}

POST {MS_BASE_URL}/api/agent/v1/bug/relate-case
{"projectId":"...","bugId":"...","caseIds":["case-1"]}
```

---

## 7. 错误处理速查

| 现象 | 原因 | 处理 |
|------|------|------|
| 401 | Token 无效/过期 | 换发 Agent Token |
| 403 | Scope 不足 | 加 `BUG_READ`/`BUG_WRITE` 或 `AGENT_ALL` |
| 429 | Token 限流 | 全局约 120 次/分钟；检索约 30 次/分钟且间隔≥300ms；勿大页轮询 |
| 「缺陷必填自定义字段缺失」 | 模板必填未传 | `get_bug` 或查模板后补 `customFields` |
| 「缺陷不属于指定项目」 | projectId 与 bug 不一致 | 用缺陷真实 projectId |
| MCP 未连接 | dist 未构建 / 路径错 | `npm run build`，检查绝对路径 |

---

## 8. 与其它 MCP Tools 的边界

- 用例读写：`search_functional_cases` / `create_functional_case` 等（CASE_*）
- 计划/评审：`create_test_plan` / `create_case_review`（PLAN_/REVIEW_）
- 缺陷：**仅**本节 5 个 bug tools；不要用用例接口改缺陷

完整 Tool 列表见 `metersphere-mcp/README.md`。

---

## 9. MCP 是否做成 MeterSphere「集成插件」？（方案评估）

### 9.1 现状

| 项 | 现状 |
|----|------|
| 交付形态 | 仓库内 Node MCP（`metersphere-mcp`），本地 `npm build` + Cursor `mcp.json` |
| 鉴权 | 平台内 Agent Token（用户/管理员签发） |
| 装配 | **人工/AI 按文档配置**，非平台一键下发 |

### 9.2 目标形态（用户设想）

在 MeterSphere 做成**集成插件**：用户授权后，Agent 从平台**下载并装配** MCP（含地址、项目、Token 或短期凭证）。

### 9.3 结论建议

| 阶段 | 建议 | 理由 |
|------|------|------|
| **现在（P0）** | **保持独立 MCP 包 + 文档/示例配置** | 已满足 AI 自配置；改动面小；与 Cursor/Claude 生态一致 |
| **短期（P1）✅** | 平台「Agent 集成」页：一键复制 `mcp.json`、展示 Scope、**下载 zip**（classpath 托管） | 已落地：`GET /api/agent/mcp/manifest|download` + 前端 MCP 面板 |
| **中期（P2）** | 「插件化」凭证下发：用户授权 → 签发**短时 Agent Token** + 项目绑定 → 客户端拉取 bundle（含审计） | 安全可控；版本可灰度 |
| **不建议（过早）** | 把 MCP Server **嵌进** MeterSphere Java 进程用 SSE/WebSocket 直出 | 与现有 stdio MCP 客户端不兼容；运维/多租户隔离复杂；Node 与 Java 生命周期耦合 |

### 9.4 若做平台插件，推荐能力边界

1. **插件职责**：凭证与配置下发、版本清单、审计（谁下载了 bundle）；**不**在浏览器里跑 MCP。
2. **运行时仍在 Agent 侧**：Cursor/本地 Agent 下载后用 `node dist/index.js` 或 `npx` 启动。
3. **授权模型**：OAuth/会话用户确认 → 生成 `msat_`（可设过期、Scope、项目白名单）→ 写入一次性下载包或仅返回 env 片段。
4. **安全**：Token 仅展示一次；bundle 签名校验；禁止把长期 Token 写进可公开 CDN。

### 9.5 决策摘要（给产品/研发）

- **是否做成集成插件？** —— **值得做「配置/凭证下发插件」，不值得把 MCP 进程嵌进平台。**
- **当前交付**：缺陷读写已通过 Agent API + `metersphere-mcp` Tools 可用；用本文即可自配置。
- **下一步可选**：产品化「一键复制 MCP 配置 / 下载 MCP bundle」；与本文第 3 节配置格式对齐即可。

---

## 10. 版本与变更

| 版本 | 变更 |
|------|------|
| 2026-07-29 | 新增 `search_bugs` / `get_bug` / `update_bug`；后端 `BUG_READ` + search/get/update API |

关联：

- `metersphere-mcp/README.md`
- `docs/task/metersphere_agent/cursor-onboarding.md`
- `.cursor/mcp.json.example`
