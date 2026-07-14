# MeterSphere AI Agent 协作平台 — 标准操作规范（SOP）

> **文档版本**：v1.0  
> **编写日期**：2026-07-08  
> **适用范围**：MeterSphere Agent 协作平台全体使用者  
> **文档性质**：产品标准操作规范  
> **当前平台版本**：V3.x（Agent 集成 MVP + MCP 已完成，P2 增强开发中）

---

## 1. 产品概述

### 1.1 平台定位

MeterSphere AI Agent 协作平台是 MeterSphere V3 的 Agent 集成能力层，允许外部 AI Agent（Cursor、Claude、GPT、Codex 等）通过标准化接口与 MeterSphere 平台协作完成**功能测试用例的检索、执行与结果回写**。

### 1.2 核心价值

| 传统模式 | Agent 协作模式 |
|---------|---------------|
| 人工在 Web UI 逐个查找用例 | Agent 自然语言一键检索 |
| 人工逐条点击"执行"并填写结果 | Agent 外部自动执行 + 回写 |
| 执行记录分散，难以追溯 | 统一审计日志，区分人工/Agent |
| 回归测试耗费大量人力 | Agent 自动回归，人工仅复核 |

### 1.3 核心链路

```
用户 → 「提取订单模块 P0 用例，执行后回写」
  ↓
Agent 理解意图 → 调用 search 接口检索用例（含步骤 + testPlanCaseId）
  ↓
Agent 外部执行（Playwright / Selenium / 手动）
  ↓
Agent 调用 submit 接口回写结果
  ↓
平台更新执行历史 → 审计日志记录
```

---

## 2. 适用对象与角色

| 角色 | 职责 | 典型操作 |
|------|------|---------|
| **测试负责人 / PM** | 制定测试策略、审核 Token 申请、查看审计日志 | 创建 Token、分配 Scope、审阅执行报告 |
| **测试工程师** | 编写用例、配置测试计划、人工复核 Agent 结果 | 创建用例、关联计划、抽查执行质量 |
| **自动化工程师** | 配置 MCP/Agent 环境、编写 Playwright 脚本 | 配置 Cursor MCP、维护执行脚本 |
| **开发工程师** | 日常自测、冒烟验证 | 说「帮我跑一下登录模块的冒烟用例」 |

---

## 3. 平台能力矩阵

### 3.1 已上线能力（P0 + P1）

| 能力 | 说明 | 接口 |
|------|------|------|
| 🔍 用例检索 | 自然语言 + 结构化条件组合检索 | `POST /api/agent/v1/functional/search` |
| 📋 模块查询 | 列出项目下全部模块树 | `GET /api/agent/v1/functional/modules` |
| 📝 用例详情 | 按 ID 获取单条用例完整信息 | `GET /api/agent/v1/functional/{caseId}` |
| ✅ 结果回写（计划内） | Agent 执行后回写测试计划 | `POST /api/agent/v1/functional/submit` |
| 🔐 Bearer Token 认证 | 简化 Agent 鉴权，SHA-256 存储 | `Authorization: Bearer msat_xxx` |
| 📖 OpenAPI 文档 | 供模型平台自动发现 Tool | `/v3/api-docs/agent` |
| 🔌 MCP Server | Cursor/Claude 原生接入 | npm `@midoo/metersphere-mcp` |

### 3.2 规划中能力（P2）

| 能力 | 说明 | 状态 |
|------|------|:--:|
| 📊 审计日志查询 | 按用例/Agent 追溯执行历史 | 待开发 |
| 🔄 计划外回写 | 无需关联测试计划即可回写 | 待开发 |
| 📦 批量回写 | 一次请求回写多条用例 | 待开发 |
| 🖼️ 执行证据附件 | 失败截图/日志关联 | 待开发 |
| 🎛️ Token 管理 UI | 可视化创建/禁用 Token | 待开发 |

### 3.3 平台接入矩阵

| AI 平台 | 接入方式 | 检索 | 回写 | 适用场景 |
|---------|---------|:--:|:--:|---------|
| Cursor | MCP（原生） | ✅ | ✅ | 开发自测、日常回归 |
| Claude Desktop | MCP + REST | ✅ | ✅ | 需求分析 + 执行 |
| ChatGPT/Codex | OpenAPI + Function Calling | ✅ | ✅ | 跨平台任务编排 |
| Dify / Coze | 自定义 Tool | ✅ | ✅ | 工作流自动化 |
| CI/CD 脚本 | curl REST | ⚠️ | ✅ | 流水线集成 |

---

## 4. 接入流程

### 4.1 前提条件

在开始之前，请确认：

- [ ] MeterSphere V3 服务已部署并运行（默认 `http://localhost:8081`）
- [ ] 数据库中已存在项目和功能用例
- [ ] 已创建 Agent 专用测试计划（可选，但强烈推荐）

### 4.2 第一步：获取 Agent Token

> **当前阶段**：Token 需通过 SQL 直接插入，未来会提供 UI 管理界面。

**提交 Token 申请（向管理员）：**

| 信息 | 示例 | 说明 |
|------|------|------|
| Token 名称 | `Cursor-张三-自测` | 便于审计识别 |
| 关联用户 | `admin` | Token 以此用户身份操作 |
| 默认项目 | `100001100001` | 可选，大多数操作需项目上下文 |
| 权限范围 | `FUNCTIONAL_READ,FUNCTIONAL_SUBMIT` | 只读 / 只写 / 全部 |
| 有效期 | 永久 / 2026-12-31 | NULL = 永不过期 |

**管理员执行：**

```sql
INSERT INTO agent_token (id, name, token_prefix, token_hash, user_id, project_id, scopes, enable, create_time)
VALUES (
    'tok-001',
    'Cursor-张三-自测',
    'msat',
    SHA2('msat_YOUR_TOKEN_HERE', 256),
    'admin',
    '100001100001',
    'FUNCTIONAL_READ,FUNCTIONAL_SUBMIT',
    1,
    UNIX_TIMESTAMP() * 1000
);
```

> ⚠️ **重要**：将 `msat_YOUR_TOKEN_HERE` 换成平台创建时展示的真实 Token；明文仅创建时展示一次，请妥善保管。平台只存储 SHA-256 哈希，无法找回。**切勿把真实 Token 写回仓库文档。**

### 4.3 第二步：配置 MCP（以 Cursor 为例）

在项目根目录创建 `.cursor/mcp.json`（该文件已列入 `.gitignore`，勿提交）：

```json
{
  "mcpServers": {
    "metersphere": {
      "command": "npx",
      "args": ["-y", "@midoo/metersphere-mcp"],
      "env": {
        "MS_BASE_URL": "http://localhost:8081",
        "MS_AGENT_TOKEN": "msat_YOUR_TOKEN_HERE",
        "MS_PROJECT_ID": "100001100001"
      }
    }
  }
}
```

配置完成后重启 Cursor，MCP 自动加载。

### 4.4 第三步：验证连接

在 Cursor 中发送：

> 列出 MeterSphere 的所有测试模块

Agent 应调用 `list_modules` 并返回模块列表。若返回 401，检查 Token 是否有效。

---

## 5. 日常操作规范

### 5.1 测试用例管理规范

**模块组织：**

| 规范 | 正确示例 | 错误示例 |
|------|---------|---------|
| 按业务域划分顶层模块 | `登录/` `订单/` `支付/` | `测试1/` `杂项/` |
| 子模块不超过 3 层 | `订单/创建/正常流程` | `订单/创建/PC端/Chrome/V1` |
| 模块名简洁明确 | `用户管理` | `用户相关的一些功能` |

**用例编写：**

| 规范 | 说明 |
|------|------|
| 每条用例必须挂模块 | 未挂模块的用例无法被模块检索命中 |
| 优先级使用 `functional_priority` 字段 | P0 / P1 / P2 / P3 |
| 标签统一规范 | 如 `["smoke", "regression", "P0"]` |
| 使用 Step 模式 | Agent 按步骤执行；Text 模式会被转为虚拟步骤 |

**测试计划：**

| 规范 | 说明 |
|------|------|
| 创建 Agent 专用测试计划 | 名称如「Agent-功能测试-2026」，固定 planId |
| 用例预先关联到计划 | 确保 search 能返回 `testPlanCaseId` |
| 每条用例不重复关联 | 同一计划内一条用例只关联一次 |

### 5.2 Agent 使用流程规范

#### 标准 5 步流程

```
Step 1：表述需求
  ├─ 明确：「提取登录模块 P0 用例」
  └─ 模糊：先 list_modules → 确认模块 → search

Step 2：检索用例
  ├─ Agent 调用 search_functional_cases
  ├─ 检查 matchedBy / warnings / total
  └─ total > 20：先用 includeSteps: false 摘要确认

Step 3：确认范围
  ├─ 核对 testPlanCaseId 是否存在
  ├─ 无 testPlanCaseId → 提示关联计划
  └─ 确认无误后告知用户「共 N 条，开始执行」

Step 4：外部执行
  ├─ 按 steps 逐条执行（Playwright 等）
  ├─ 逐步记录 actualResult + executeResult
  └─ 汇总 lastExecResult：全 SUCCESS → SUCCESS，否则 ERROR

Step 5：回写结果
  ├─ 逐条调用 submit_functional_result
  ├─ testPlanCaseId 用 search 返回的值（不是 caseId）
  └─ 汇总报告：总数/通过/失败/失败用例列表
```

#### 常用指令速查

| 你说 | Agent 做的事 |
|------|------------|
| 「提取XX模块的用例」 | search(query="XX") |
| 「找所有 P0 未执行的」 | search(filters: {priority:["P0"], lastExecuteResult:["PENDING"]}) |
| 「执行后回写 MeterSphere」 | 执行 → submit × N |
| 「只看摘要，不要步骤」 | search(includeSteps: false) |
| 「有哪些模块」 | list_modules |

---

## 6. Token 管理规范

### 6.1 Token 生命周期

```
申请 → 审批 → 创建（明文仅展示一次）→ 使用 → 到期/禁用 → 归档
```

### 6.2 Scope 权限对照

| Scope | 允许操作 | 建议分配给 |
|-------|---------|-----------|
| `FUNCTIONAL_READ` | 检索、查看用例、列出模块 | 只读分析场景 |
| `FUNCTIONAL_SUBMIT` | 回写执行结果 | 仅回写场景 |
| `FUNCTIONAL_ALL` | 全部操作 | 日常 Agent 使用 |

### 6.3 安全规范

| 规范 | 说明 |
|------|------|
| Token 即密码 | 明文仅创建时可见，妥善保管 |
| 一人一 Token | 便于审计追溯，不共用 |
| 最小权限原则 | 只授予必要的 Scope |
| 定期轮换 | 建议每季度更换 Token |
| 及时禁用 | 人员离职或不再使用时立即禁用（`enable=0`） |
| 禁止硬编码 | Token 通过环境变量注入，不写入代码仓库 |

### 6.4 Token 禁用流程

```sql
UPDATE agent_token SET enable = 0 WHERE id = 'tok-001';
```

或由管理员在 Token 管理 UI（P2 待建设）操作。

---

## 7. 异常处理

### 7.1 常见错误与处理

| 现象 | HTTP 码 | 原因 | 处理方式 |
|------|:--:|------|---------|
| 无法连接 | — | 服务未启动 / 地址错误 | 检查 `MS_BASE_URL`，确认服务运行 |
| 认证失败 | 401 | Token 无效/过期/被禁用 | 检查 `MS_AGENT_TOKEN`，向管理员申请新 Token |
| 权限不足 | 403 | Token Scope 不含所需权限 | 确认 Scope 配置，联系管理员调整 |
| 参数错误 | 400 | query + filters 均为空 / pageSize > 500 | 检查请求体参数 |
| 检索无结果 | 200（total=0） | 条件无匹配用例 | 调 list_modules 确认模块名，放宽条件 |
| 模块降级 | 200（warning） | 模块名未匹配，已降级 keyword | 确认 warning 信息，必要时用 moduleIds |
| 回写后平台不显示 | — | testPlanCaseId 错误 / 计划外回写 | 确认 search 返回的 testPlanCaseId，检查是否关联计划 |

### 7.2 故障升级流程

```
用户发现问题
  → 自查：Token 有效？服务运行？参数正确？
    → 未解决：联系测试负责人
      → 未解决：提交 issue（附 Token ID、请求体、响应）
```

---

## 8. 最佳实践建议

### 8.1 测试策略

| 阶段 | 建议 |
|------|------|
| **冒烟测试** | Agent 每日自动回归 P0 用例，结果回写 |
| **迭代测试** | Agent 优先执行已稳定的用例，新用例人工首测 |
| **回归测试** | Agent 全量回归，人工抽查失败用例 |
| **上线验证** | Agent + 人工双轨并行，互为补充 |

### 8.2 效率提升技巧

1. **Agent 专用测试计划**：固定 `planId`，避免每次传参
2. **模块别名**：配置「登录」→ 精确模块，提升命中率
3. **标签体系**：统一 `["P0","smoke","regression"]`，Agent 过滤更精准
4. **批量执行**：Agent 一次性 search 后批量执行，减少往返

### 8.3 质量保障

- Agent 执行结果 ≠ 最终结论，关键用例需人工复核
- 失败用例必须有人工确认，排除环境问题
- 每周抽查 Agent 执行记录，评估执行质量
- 审计日志定期 Review，发现异常行为

---

## 9. 术语表

| 术语 | 说明 |
|------|------|
| **Agent** | 外部 AI 助手（Cursor、Claude、GPT 等），通过 API/MCP 与平台交互 |
| **MCP** | Model Context Protocol，AI 助手与平台间的标准通信协议 |
| **Token** | Agent 接入凭据，格式 `msat_xxx`，Bearer 认证 |
| **Scope** | Token 的权限范围（READ / SUBMIT / ALL） |
| **testPlanCaseId** | 用例在测试计划中的关联 ID（计划内回写的必填字段） |
| **caseId** | 功能用例在用例库中的唯一 ID |
| **计划内回写** | 通过测试计划关联，结果写入计划执行历史（平台可见） |
| **计划外回写** | 不关联计划，仅更新用例库状态（P2 待建设） |
| **审计日志** | `agent_exec_log` 表，记录 Agent 每次执行详情 |

---

## 10. 附录

### 附录 A：接口速查

| 接口 | 方法 | Scope | 说明 |
|------|:--:|------|------|
| `/api/agent/v1/functional/search` | POST | READ | 检索用例 |
| `/api/agent/v1/functional/{caseId}` | GET | READ | 单条详情 |
| `/api/agent/v1/functional/modules` | GET | READ | 模块树 |
| `/api/agent/v1/functional/submit` | POST | SUBMIT | 回写结果 |
| `/api/agent/v1/functional/health` | GET | — | 健康检查 |
| `/v3/api-docs/agent` | GET | — | OpenAPI 文档 |

### 附录 B：Agent 工作流规则摘要

```
1. 模糊表述 → 先 list_modules 消歧
2. 检索用例 → 检查 matchedBy + warnings
3. 命中 > 20 → 摘要确认后再拉详情
4. 执行前 → 确认 testPlanCaseId 存在
5. 回写时 → testPlanCaseId ≠ caseId
6. 失败用例 → 人工确认后再判定
```

### 附录 C：相关文档

| 文档 | 路径 |
|------|------|
| 改造方案 | `docs/summary/MeterSphere-Agent集成-改造方案-2026-07-07.md` |
| 实施总览 | `docs/task/metersphere_agent/task000-实施总览与依赖关系.md` |
| Cursor 工作流规则 | `.cursor/rules/metersphere-agent.mdc` |
| Cursor 接入指南 | `metersphere-mcp/docs/cursor-onboarding.md` |
| 用例文档 | `use-cases.md` |

---

*本文档由产品经理维护，随平台版本迭代更新。如有疑问或改进建议，请联系测试负责人。*
