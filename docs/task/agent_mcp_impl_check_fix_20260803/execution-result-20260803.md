# execution-result-20260803 — MCP 接口检查整改执行结果

> 任务目录：`docs/task/agent_mcp_impl_check_fix_20260803`  
> 执行日期：2026-08-03  
> 【AI 执行记录】以下状态按「是否满足任务验收标准」如实判定，**未完成不标完成**。

## 总览

| 任务 | 状态 | 说明 |
|---|---|---|
| task001 Scope 默认拒绝 + 精确匹配 | **已完成（代码+单测）** | 待部署后线上回归 |
| task002 Filter 条件注销 | **已完成（代码）** | 单测覆盖 attribute 常量；Shiro Subject 全链路需集成环境验证 |
| task003 安全回归单测 | **已完成（针对本批单测）** | 38 个相关单测通过；未覆盖全部写 Tool HTTP 契约 E2E |
| task004 PROJECT_READ | **已完成（代码+前端选项）** | 含 FUNCTIONAL_READ 迁移兼容 |
| task005 项目检索分页/SQL | **已完成（代码+单测）** | Mapper SQL 下沉；完整中文匹配 IT 见 task008 缺口 |
| task006 Notification/协议 | **已完成（代码+单测）** | 无 id Notification → HTTP 202 空 body |
| task007 McpToolRegistry | **已完成（代码+单测）** | 内置 Tool 改为 Handler Bean；扩展 Handler 同源注册 |
| task008 项目检索专用自动化 | **未完成** | 仅有分页/转义/空权限单测；缺 DB IT（中英匹配、归档边界、注入全表） |
| task009 四客户端兼容矩阵 | **未完成** | 无真实客户端联调；仅有 curl 探测记录 |
| task010 旧 MCP 包清理 | **已完成（仓库内可确认项）** | 无 `metersphere-mcp-*.zip`；旧 API 标 `@Deprecated`；文案改为 AI 技能包。仓库仍保留历史 `metersphere-mcp/` Node 工程（文档层遗留，未删以免超范围破坏） |
| task011 管理员治理审计 | **未完成** | 仅交付审计筛选字段 + CSV 导出（M1 切片）；Tool 启停/告警/限流配置/留存周期未做 |
| task012 全链路完成标准 | **未完成** | 因 task008/009/011 未关，且未部署验证，不能宣称 MCP「完成」 |

## 自动化测试

```text
.\mvnw.cmd -f backend\pom.xml -pl services\agent-integration test
  -Dtest=AgentScopeAssertTests,AgentTokenScopeParserTests,AgentTokenFilterTests,
         AgentProjectServiceTests,AgentMcpStreamableServiceTests,
         AgentMcpToolRegistryTests,AgentMcpToolContractTests

Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

未在本会话跑通全模块 `agent-integration` 全量 surefire（含依赖 DB/嵌入式环境的用例）；上表仅声明已跑通的 38 个。

## 主要代码改动

- `AgentTokenScopeParser` / `AgentScopeAssert`：空 scope 拒绝；集合精确匹配
- `AgentTokenFilter`：`ATTR_AGENT_LOGIN_ESTABLISHED` 条件 logout；会话用户与 Token 用户不一致 403
- `PROJECT_READ` + 项目检索分页响应 + `ExtAgentProjectMapper` SQL
- `AgentMcpToolRegistry` + `BuiltinAgentMcpToolConfig`；StreamableService 去掉 switch
- Notification：Controller 返回 202 空 body
- `AgentMcpController` `@Deprecated`
- 审计：`action`/`createUser` 筛选 + `/admin/agent-tokens/audit/export`
- 前端：项目查看 scope；AI 技能包文案

## 无法在本环境完成的事项（明确）

1. **真实 Codex/ChatGPT/Cursor/WorkBuddy 联调**（缺 Token、缺 GUI、未部署本分支）。
2. **`msp.ebcone.cn` Nginx TLS/schannel**（运维侧，非本仓库可修）。
3. **完整企业治理中心**（task011 方案差距过大，本次仅 M1 审计导出切片，**整体仍算未完成**）。
4. **项目检索 DB 集成测试全清单**（task008）。
5. **线上验收**：当前 `msp.ebcone.net` GET `/api/mcp` 仍返回 401，说明 **405 修复与本批改动尚未部署**。

## 建议下一步

1. 部署本分支到测试环境（`.net`）。
2. 复测：GET `/api/mcp` → 405；空 scope → 拒绝；project.search 分页结构。
3. 人工补齐 task009 矩阵。
4. 补齐 task008 DB IT 或明确豁免。
5. 单独排期 task011 剩余治理能力。
6. 运维对齐 `.cn` TLS。

## 对用户承诺的核对

| 要求 | 执行情况 |
|---|---|
| 任务完全执行 | 能落地的代码任务已实现；不能落地的已标明未完成 |
| 无法实现及时说明 | 见上文「无法完成」与 task009/011/012 |
| 禁止部分完成视为完成 | task008/009/011/012 均标 **未完成** |
| 禁止糊弄/欺瞒/未完成标完成 | 总体结论：**整改代码已推进，但 MCP 接口检查「完成标准」尚未关闭** |
