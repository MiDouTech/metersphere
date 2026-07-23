# @midoo/metersphere-mcp

MeterSphere Agent API 的 MCP 薄封装，供 Cursor、Claude Desktop 等 MCP 客户端调用。

**不含业务逻辑**，仅将 MCP Tool 请求转发到 MeterSphere REST API（`/api/agent/v1`）。

## 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `MS_BASE_URL` | 是 | MeterSphere 地址，如 `https://msp.ebcone.cn` |
| `MS_AGENT_TOKEN` | 是 | Agent Token，格式 `msat_xxx` |
| `MS_PROJECT_ID` | 是 | 默认项目 ID（创建新项目后建议在调用中显式传 projectId） |
| `MS_TEST_PLAN_ID` | 否 | 默认测试计划 ID，`search` 时自动带入 |

## MCP Tools

### 读 / 执行回写

| Tool | 说明 |
|------|------|
| `search_functional_cases` | 检索功能用例 |
| `get_functional_case` | 单条用例详情 |
| `submit_functional_result` | 回写计划内执行结果 |
| `submit_functional_results_batch` | 批量回写 |
| `upload_execution_attachment` | 上传执行截图/附件 |
| `list_modules` | 模块列表 |
| `get_exec_log` | 执行审计日志详情 |

### 写闭环（一期）

| Tool | Scope | 说明 |
|------|-------|------|
| `create_project` | PROJECT_WRITE | 创建项目并加初始成员 |
| `add_project_members` | PROJECT_WRITE | 追加成员 |
| `create_functional_module` | CASE_WRITE | 创建模块 |
| `create_functional_case` | CASE_WRITE | 单条创建 |
| `batch_create_functional_cases` | CASE_WRITE | 批量导入 Agent 生成用例 |
| `create_test_plan` | PLAN_WRITE | 创建测试计划（可带 caseIds） |
| `associate_test_plan_cases` | PLAN_WRITE | 关联用例到计划 |
| `create_case_review` | REVIEW_WRITE | 创建评审 |
| `associate_case_review_cases` | REVIEW_WRITE | 关联用例到评审 |
| `create_bug` | BUG_WRITE | 创建缺陷（可关联用例） |
| `relate_bug_case` | BUG_WRITE | 缺陷补关联用例 |

## 本地开发

```bash
cd metersphere-mcp
npm install
npm run build
npm start
```

## Cursor 配置

```json
{
  "mcpServers": {
    "metersphere": {
      "command": "node",
      "args": ["metersphere-mcp/dist/index.js"],
      "env": {
        "MS_BASE_URL": "https://msp.ebcone.cn",
        "MS_AGENT_TOKEN": "msat_xxxx",
        "MS_PROJECT_ID": "your-project-id",
        "MS_TEST_PLAN_ID": "optional-plan-id"
      }
    }
  }
}
```

## 对话闭环示例

1. `create_project` → 得到 projectId  
2. 对话生成用例 → `batch_create_functional_cases`  
3. `create_test_plan` + caseIds；`create_case_review` + caseIds  
4. `search_functional_cases` → 外部执行 → `upload_execution_attachment` → `submit_functional_result`  
5. 失败则 `create_bug`（带 caseId）

## 常见问题

| 现象 | 处理 |
|------|------|
| 401 Unauthorized | 检查 `MS_AGENT_TOKEN` |
| 403 Scope 不足 | Token 需 AGENT_ALL 或对应 WRITE/SUBMIT scope |
| 缺少 testPlanCaseId | 先关联测试计划再 search |
| 缺陷必填字段 | 传 `customFields` |

## 关联文档

- [Cursor 接入指南](../docs/task/metersphere_agent/cursor-onboarding.md)
- [扩展方案](../docs/summary/MeterSphere-Agent对话闭环-扩展方案-2026-07-23.md)
