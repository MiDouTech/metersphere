# task012 - P1 Cursor 接入与工作流规则

> **阶段**：P1  
> **预估工期**：1 天  
> **前置依赖**：[task011](task011-P1-metersphere-mcp服务.md)  
> **阻塞任务**：无  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §7.3、§7.4、§14

---

## 1. 任务目标

编写团队 Cursor 接入指南与 `.cursor/rules` 工作流规则，使开发者可通过自然语言完成「提取用例 → 执行 → 回写」端到端流程。

---

## 2. 交付物

| 文件 | 说明 |
|------|------|
| `docs/task/metersphere_agent/cursor-onboarding.md` | 团队接入指南 |
| `.cursor/rules/metersphere-agent.mdc` | Agent 默认工作流规则（或项目级 rules） |
| `.cursor/mcp.json.example` | MCP 配置模板（不含真实 token） |

---

## 3. Cursor MCP 配置模板

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

---

## 4. 工作流规则要点

写入 `.cursor/rules/metersphere-agent.mdc`：

1. **提取用例**：用户提及「提取用例」→ 表述模糊时先 `list_modules` → `search_functional_cases`  
2. **确认消歧**：检查 `matchedBy` / `warnings`；命中数 > 20 时 `includeSteps=false` 先摘要确认  
3. **执行前检查**：确认每条含 `testPlanCaseId`；无则提示关联到 Agent 专用测试计划  
4. **外部执行**：按 steps 逐步执行（Playwright 等），记录 actualResult  
5. **回写**：逐条 `submit_functional_result`，保留 `step.id`  
6. **汇总回复**：报告通过/失败数与失败用例编号  

---

## 5. 接入指南章节

1. 前置条件（MeterSphere 运行、Token、专用测试计划）  
2. MCP 安装与配置  
3. 验证步骤（list_modules → search → submit 单条）  
4. 自然语言示例话术  
5. 故障排查（401、403、缺 testPlanCaseId、模块未命中）  
6. 数据规范建议（模块划分、标签、priority）  

---

## 6. 端到端验证场景

| 场景 | 用户输入 | 期望 |
|------|---------|------|
| 模块检索 | 「提取订单模块 P0 用例」 | search 返回含 steps + testPlanCaseId |
| 消歧 | 「提取财务相关用例」（模糊） | 先 list_modules，再确认后 search |
| 回写 | 执行完成后 | submit 成功，平台执行历史可见 |

---

## 7. 验收标准

- [x] 接入指南文档完成（`docs/task/metersphere_agent/cursor-onboarding.md`）  
- [x] `.cursor/rules/metersphere-agent.mdc` 已创建  
- [x] `.cursor/mcp.json.example` 不含敏感信息  
- [ ] 端到端场景验证（需本地 MeterSphere + Token 实测）  

---

## 8. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
