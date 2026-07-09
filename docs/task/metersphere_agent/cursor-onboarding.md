# MeterSphere Agent Cursor 接入指南

> 关联任务：task012  
> 前置：P0 Agent API 已部署（task001–010），MCP 包已构建（task011）

---

## 1. 前置条件

| 项 | 说明 |
|----|------|
| MeterSphere 后端 | 已启动，如 `http://localhost:8081` |
| Flyway | `agent_token`、`agent_exec_log` 表已迁移 |
| Agent Token | 已创建，`scope` 含 `FUNCTIONAL_ALL` 或 `FUNCTIONAL_READ` + `FUNCTIONAL_SUBMIT` |
| 专用测试计划 | 建议创建「Agent-功能测试-2026」，用例预先关联 |
| Node.js | >= 18（运行 MCP Server） |

### 创建 Agent Token

参考 `docs/task/fixtures/agent_integration_test_data.sql`，向 `agent_token` 表插入记录（明文 Token 仅保存一次）。

---

## 2. 构建 MCP 包

```bash
cd metersphere-mcp
npm install
npm run build
```

验证启动（应挂起等待 stdio，Ctrl+C 退出）：

```bash
set MS_BASE_URL=http://localhost:8081
set MS_AGENT_TOKEN=msat_xxx
set MS_PROJECT_ID=your-project-id
npm start
```

---

## 3. 配置 Cursor MCP

1. 复制 `.cursor/mcp.json.example` 为 `.cursor/mcp.json`（项目级）或配置到用户目录 `~/.cursor/mcp.json`。
2. 填写环境变量（**不要提交真实 Token 到 Git**）：

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

> 路径 `metersphere-mcp/dist/index.js` 相对于 MeterSphere 仓库根目录。若使用全局配置，请改为绝对路径。

3. **重启 Cursor**，在 Settings → MCP 中确认 `metersphere` 服务为绿色已连接。

---

## 4. 启用工作流规则

项目已包含 `.cursor/rules/metersphere-agent.mdc`。在 Cursor 对话中涉及 MeterSphere 用例时，规则会自动提供工作流指引。

也可在对话中明确说：「按 MeterSphere Agent 工作流执行」。

---

## 5. 验证步骤

按顺序在 Cursor Agent 对话中测试：

### 5.1 模块列表

> 列出当前项目有哪些功能用例模块

期望：`list_modules` 返回模块 id/name/path。

### 5.2 检索用例

> 提取订单模块 P0 测试用例，包含完整步骤

期望：`search_functional_cases` 返回 `cases[]`，每条含 `steps` 和 `testPlanCaseId`（需配置 `MS_TEST_PLAN_ID`）。

### 5.3 回写结果（单条）

> 将用例 #1001 的执行结果回写为 SUCCESS

期望：`submit_functional_result` 成功；平台「测试计划 → 执行历史」可见记录。

---

## 6. 自然语言示例

| 场景 | 示例输入 |
|------|---------|
| 模块检索 | 「提取订单模块 P0 未执行用例」 |
| 消歧 | 「提取财务相关用例」（Agent 应先 list_modules） |
| 执行回写 | 「执行完成后把结果回写 MeterSphere」 |
| 单条详情 | 「查看用例 1001 的详细步骤」 |

---

## 7. 故障排查

| 现象 | 原因 | 处理 |
|------|------|------|
| MCP 服务未连接 | 路径或 Node 错误 | 检查 `mcp.json` 中 `args` 路径、`node -v` |
| 401 | Token 无效 | 重新生成 Token，更新 `MS_AGENT_TOKEN` |
| 403 submit | Scope 不足 | Token 增加 `FUNCTIONAL_SUBMIT` |
| 缺 testPlanCaseId | 未传 testPlanId 或用例未关联计划 | 配置 `MS_TEST_PLAN_ID` 并关联用例 |
| 模块未命中 | 模块名不匹配 | 先 `list_modules`，用 `filters.moduleIds` |
| MODULE_NOT_MATCHED_KEYWORD_FALLBACK | 降级为 keyword 搜索 | 确认 `matchedModules`，必要时缩小范围 |

---

## 8. 数据规范建议

| 规范 | 做法 |
|------|------|
| 模块树 | 按业务域划分：财务/、订单/、用户中心/ |
| 标签 | 统一打 `P0`、`smoke` 等 |
| 优先级 | 使用自定义字段 `functional_priority` |
| 回写计划 | 固定 Agent 专用测试计划，用例预先关联 |

---

## 9. 相关文档

- [metersphere-mcp README](../../metersphere-mcp/README.md)
- [curl 联调示例](./curl-examples.md)
- [改造方案 v2.0](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md)
