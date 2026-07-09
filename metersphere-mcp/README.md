# @midoo/metersphere-mcp

MeterSphere Agent API 的 MCP 薄封装，供 Cursor、Claude Desktop 等 MCP 客户端调用。

**不含业务逻辑**，仅将 MCP Tool 请求转发到 MeterSphere REST API（`/api/agent/v1`）。

## 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `MS_BASE_URL` | 是 | MeterSphere 地址，如 `http://localhost:8081` |
| `MS_AGENT_TOKEN` | 是 | Agent Token，格式 `msat_xxx` |
| `MS_PROJECT_ID` | 是 | 默认项目 ID |
| `MS_TEST_PLAN_ID` | 否 | 默认测试计划 ID，`search` 时自动带入 |

## MCP Tools

| Tool | 说明 |
|------|------|
| `search_functional_cases` | 检索功能用例（含 steps、testPlanCaseId） |
| `get_functional_case` | 单条用例详情 |
| `submit_functional_result` | 回写计划内执行结果 |
| `list_modules` | 模块列表（消歧） |

## 本地开发

```bash
cd metersphere-mcp
npm install
npm run build
npm start
```

## Cursor 配置

### 方式一：使用本地包（开发）

复制项目根目录 `.cursor/mcp.json.example` 为 `.cursor/mcp.json`，按注释填写环境变量。

```json
{
  "mcpServers": {
    "metersphere": {
      "command": "node",
      "args": ["metersphere-mcp/dist/index.js"],
      "env": {
        "MS_BASE_URL": "http://localhost:8081",
        "MS_AGENT_TOKEN": "msat_xxxx",
        "MS_PROJECT_ID": "your-project-id",
        "MS_TEST_PLAN_ID": "agent-plan-2026"
      }
    }
  }
}
```

### 方式二：npm 发布包

```json
{
  "mcpServers": {
    "metersphere": {
      "command": "npx",
      "args": ["-y", "@midoo/metersphere-mcp"],
      "env": {
        "MS_BASE_URL": "http://localhost:8081",
        "MS_AGENT_TOKEN": "msat_xxxx",
        "MS_PROJECT_ID": "your-project-id",
        "MS_TEST_PLAN_ID": "agent-plan-2026"
      }
    }
  }
}
```

配置后重启 Cursor，在 Agent 对话中即可使用上述 Tools。

## 示例话术

| 用户说法 | 推荐 Tool 调用 |
|---------|---------------|
| 有哪些业务模块 | `list_modules` |
| 提取订单模块 P0 用例 | `search_functional_cases({ query: "订单", filters: { priority: ["P0"] } })` |
| 查看用例 1001 详情 | `get_functional_case({ caseId: "..." })` |
| 回写执行结果 | `submit_functional_result({ caseId, testPlanCaseId, lastExecResult: "SUCCESS", steps: [...] })` |

## 常见问题

| 现象 | 处理 |
|------|------|
| 401 Unauthorized | 检查 `MS_AGENT_TOKEN` 是否有效、未过期 |
| 403 Scope 不足 | submit 需要 `FUNCTIONAL_SUBMIT` 或 `FUNCTIONAL_ALL` |
| 缺少 testPlanCaseId | search 时传入 `testPlanId`，或先将用例关联到 Agent 专用测试计划 |
| 模块未命中 | 先 `list_modules` 确认模块名，或用 `filters.moduleIds` 精确检索 |

## 关联文档

- [Cursor 接入指南](../docs/task/metersphere_agent/cursor-onboarding.md)
- [curl 联调示例](../docs/task/metersphere_agent/curl-examples.md)
