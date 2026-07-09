# task011 - P1 metersphere-mcp 服务

> **阶段**：P1  
> **预估工期**：2 天  
> **前置依赖**：[task010](task010-P0-集成测试与MVP验收.md)  
> **阻塞任务**：task012  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §7

---

## 1. 任务目标

发布独立 npm 包 `@midoo/metersphere-mcp`，实现 4 个 MCP Tool，仅做 HTTP 转发，不含业务逻辑。

---

## 2. 目录结构

```
metersphere-mcp/          # 建议放在仓库根目录或 packages/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.ts          # MCP Server 入口
│   ├── client.ts         # REST HTTP Client
│   └── tools/
│       ├── searchFunctionalCases.ts
│       ├── getFunctionalCase.ts
│       ├── submitFunctionalResult.ts
│       └── listModules.ts
└── README.md
```

---

## 3. 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| MS_BASE_URL | 是 | 如 `http://localhost:8081` |
| MS_AGENT_TOKEN | 是 | `msat_xxx` |
| MS_PROJECT_ID | 是 | 默认项目 ID |
| MS_TEST_PLAN_ID | 否 | 默认测试计划，search 时自动带入 |

---

## 4. MCP Tools

| Tool | REST | 参数 |
|------|------|------|
| `search_functional_cases` | `POST .../search` | query, filters, testPlanId, includeSteps, current, pageSize |
| `get_functional_case` | `GET .../{caseId}` | caseId, includeSteps, testPlanId |
| `submit_functional_result` | `POST .../submit` | 与 AgentCaseSubmitRequest 一致 |
| `list_modules` | `GET .../modules` | projectId（默认 MS_PROJECT_ID） |

### 4.1 client.ts

```typescript
const headers = {
  'Authorization': `Bearer ${process.env.MS_AGENT_TOKEN}`,
  'X-MS-PROJECT': process.env.MS_PROJECT_ID,
  'Content-Type': 'application/json',
};
```

错误处理：透传 HTTP 状态码与响应 body，便于 Agent 诊断。

---

## 5. package.json

```json
{
  "name": "@midoo/metersphere-mcp",
  "version": "0.1.0",
  "bin": { "metersphere-mcp": "./dist/index.js" },
  "dependencies": {
    "@modelcontextprotocol/sdk": "^1.x"
  }
}
```

---

## 6. README 内容

- 安装与 Cursor 配置示例  
- 环境变量说明  
- 4 个 Tool 用法与示例话术  
- 常见问题（401、缺 testPlanCaseId）  

---

## 7. 验收标准

- [x] `npm run build` 通过，MCP Server 可启动  
- [x] 4 个 Tool 定义完成，HTTP 转发至 Agent API  
- [x] README 含完整 Cursor `mcp.json` 配置  
- [x] 包内无业务逻辑，仅 HTTP 转发  

---

## 8. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
