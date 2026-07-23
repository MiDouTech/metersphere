# MeterSphere Agent Cursor 接入指南

> 关联任务：task012 / 对话闭环扩展  
> 前置：Agent API 已部署，MCP 包已构建

---

## 1. 前置条件

| 项 | 说明 |
|----|------|
| MeterSphere 后端 | 已启动，如 `http://localhost:8081` 或 `https://msp.ebcone.cn` |
| Flyway | `agent_token`、`agent_exec_log` 表已迁移 |
| Agent Token | 闭环全能力建议 `AGENT_ALL`，或按需组合 READ/SUBMIT + 各 WRITE scope |
| 专用测试计划 | 可用对话 `create_test_plan` 创建并关联，或预先创建 |
| Node.js | >= 18（运行 MCP Server） |

### 创建 Agent Token

参考 `docs/task/fixtures/agent_integration_test_data.sql`，向 `agent_token` 表插入记录（明文 Token 仅保存一次）。scopes 示例：`AGENT_ALL`。

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

### 5.4 对话闭环（一期扩展）

> 在组织 X 创建项目「Agent演示」，成员含我自己；生成 5 条登录用例并导入；创建测试计划与评审并关联；执行后回写并给失败用例提缺陷

期望依次调用：`create_project` → `batch_create_functional_cases` → `create_test_plan` / `create_case_review` → search/upload/submit → `create_bug`。

---

## 6. 自然语言示例

| 场景 | 示例输入 |
|------|---------|
| 创建项目 | 「在组织 xxx 创建项目 Demo，成员加上 userA」 |
| 生成导入 | 「根据登录需求生成并导入 P0 用例到 Demo」 |
| 计划/评审 | 「创建测试计划并关联刚导入的用例；再建评审」 |
| 模块检索 | 「提取订单模块 P0 未执行用例」 |
| 消歧 | 「提取财务相关用例」（Agent 应先 list_modules） |
| 执行回写 | 「执行完成后把结果回写 MeterSphere，并上传截图」 |
| 提缺陷 | 「给失败用例创建缺陷并关联」 |
| 单条详情 | 「查看用例 1001 的详细步骤」 |

---

## 7. 故障排查

| 现象 | 原因 | 处理 |
|------|------|------|
| MCP 服务未连接 | 路径或 Node 错误 | 检查 `mcp.json` 中 `args` 路径、`node -v` |
| 401 | Token 无效 | 重新生成 Token，更新 `MS_AGENT_TOKEN` |
| 403 submit/write | Scope 不足 | 增加对应 WRITE / SUBMIT 或 `AGENT_ALL` |
| 缺 testPlanCaseId | 未传 testPlanId 或用例未关联计划 | 先 `create_test_plan` 关联或配置 `MS_TEST_PLAN_ID` |
| 模块未命中 | 模块名不匹配 | 先 `list_modules`，用 `filters.moduleIds` |
| 缺陷必填字段 | 模板自定义字段 | 传 `customFields` |
| MODULE_NOT_MATCHED_KEYWORD_FALLBACK | 降级为 keyword 搜索 | 确认 `matchedModules`，必要时缩小范围 |

---

## 8. 数据规范建议

| 规范 | 做法 |
|------|------|
| 模块树 | 按业务域划分：财务/、订单/、用户中心/ |
| 标签 | 统一打 `P0`、`smoke`、`agent` 等 |
| 优先级 | 使用自定义字段 `functional_priority` |
| 回写计划 | 可用 Agent 创建的测试计划，或固定专用计划 |

---

## 9. 相关文档

- [metersphere-mcp README](../../metersphere-mcp/README.md)
- [对话闭环扩展方案](../../summary/MeterSphere-Agent对话闭环-扩展方案-2026-07-23.md)
- [curl 联调示例](./curl-examples.md)
- [改造方案 v2.0](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md)
- [工作流规则](../../../.cursor/rules/metersphere-agent.mdc)
