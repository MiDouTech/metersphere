# MeterSphere 外部 AI Agent 集成功能测试改造方案

> 本文档基于 v1.0 方案与现有代码库（`v3.x-task-metersphere-agent` 分支）对照评审后修订。  
> 文档版本：**v2.0**  
> 日期：2026-07-07  
> 适用范围：功能测试用例的自然语言提取、Agent 外部执行、结果回写  
> 状态：待技术负责人审核确认后实施

---

## 修订说明（v1.0 → v2.0）

| 修订项 | v1.0 问题 | v2.0 调整 |
|--------|-----------|-----------|
| 自然语言解析 | 假定服务端规则引擎可独立理解 NL | 改为 **Agent 拆意图 + 服务端解析检索条件** 的混合模式 |
| 检索解析器 | 模块/标签/keyword 互斥 return，无法组合 | 支持 **query + filters 组合**，模块命中含子树 |
| 优先级过滤 | 当作表字段 `priority` | 映射为自定义字段 `functional_priority` |
| 回写字段 | `steps` 数组直接提交 | 映射为 `TestPlanCaseRunRequest.stepsExecResult` JSON 字符串 |
| 计划关联 ID | search 未强调返回 `testPlanCaseId` | **MVP 强制返回**，避免回写前二次查询 |
| 用例步骤 | 仅考虑 Step 模式 | 增加 **Text 模式** 虚拟步骤适配 |
| 认证链路 | 未说明 CSRF | 明确 `AgentTokenFilter` 与 `apikey/csrf/authc` 集成方式 |
| 回写闭环 | 计划外回写描述偏乐观 | 区分 **计划内完整闭环** vs **计划外有限回写** |

---

## 1. 背景与目标

### 1.1 背景

MeterSphere 已具备完整的功能用例管理能力（模块树、标签、步骤、测试计划、执行记录），但现有 REST API 面向 Web UI 设计，存在以下问题：

- 端点分散（50+），Agent 接入成本高
- 列表接口不含完整 `steps`，需 N+1 拉详情
- 现有 API Key 需 `accessKey + AES signature + 时间戳`，不适合 Cursor/MCP 等场景
- 回写依赖测试计划上下文，外部 Agent 难以一次拿到 `testPlanCaseId`
- OpenAPI 默认关闭（`commons.properties` 中 `springdoc.*.enabled=false`）

### 1.2 目标

实现端到端链路：

```
用户自然语言（如「提取订单模块 P0 测试用例并执行，结果回写平台」）
    ↓
AI Agent 理解意图，构造结构化 search 请求
    ↓
REST Agent API 检索用例（含完整 steps + testPlanCaseId）
    ↓
Agent 在外部执行（Playwright / 浏览器等）
    ↓
REST Agent API 回写执行结果
    ↓
MeterSphere 更新用例状态、步骤结果与执行历史
```

### 1.3 范围

| 范围内 | 范围外 |
|--------|--------|
| 功能测试用例读取（Step + Text 模式） | 接口测试 / 场景自动化 |
| 结构化 + 自然语言混合检索 | 改造现有 UI API |
| 测试计划内结果回写（MVP 完整闭环） | 平台内 UI 自动化执行 |
| Agent Token（Bearer）认证 | 接口测试资源池执行 |
| OpenAPI Agent 分组 | |
| MCP Server 薄封装（Cursor） | |
| 计划外有限回写 + 审计日志（P1） | |

### 1.4 可行性结论

经与代码库对照，**本方案技术路径可行**，核心复用点均已验证存在：

- `FunctionalCaseModuleService.getTree()`、`FunctionalCaseService.getFunctionalCaseDetail()`、`TestPlanFunctionalCaseService.run()` 等可直接复用
- 新增 `agent-integration` 模块符合现有 Maven 多模块结构
- MVP 工期 **1–2 周**合理；含 MCP + 消歧增强建议 **2–3 周**

---

## 2. 现状分析（代码核实）

### 2.1 已有能力（可复用）

| 能力 | 现有 API / 类 | 代码核实说明 |
|------|--------------|-------------|
| 模块树 | `GET /functional/case/module/tree/{projectId}` | `FunctionalCaseModuleService.getTree(projectId)` → `BaseTreeNode` |
| 用例列表 | `POST /functional/case/page` | `FunctionalCasePageRequest` 支持 `moduleIds`、`keyword`（name/num/tags LIKE） |
| 用例详情 | `GET /functional/case/detail/{id}` | `FunctionalCaseDetailDTO.steps` 来自 `functional_case_blob` |
| 计划内列表 | `POST /test-plan/functional/case/page` | `TestPlanCasePageResponse.id` 即 `testPlanCaseId` |
| 计划内回写 | `POST /test-plan/functional/case/run` | `TestPlanFunctionalCaseService.run()` |
| 步骤结构 | `FunctionalCaseStepDTO` | `num` / `desc` / `result` / `actualResult` / `executeResult` |
| 结果枚举 | `ResultStatus` | `SUCCESS` / `ERROR` / `BLOCKED` / `FAKE_ERROR` |
| API Key | `ApiKeyHandler` | AES 签名 + 30 分钟时效，Agent 不友好 |
| Shiro 链路 | `ShiroConfig` | 默认 `apikey, csrf, authc` |
| OpenAPI | springdoc 依赖已存在 | 默认关闭，可配置开启 |

### 2.2 核心缺口

| 缺口 | 影响 |
|------|------|
| 无 Agent 专用 API 层 | Agent 需理解分散端点与 UI 请求体 |
| 列表不含 steps | N+1 请求，Token 消耗大 |
| search 不返回 `testPlanCaseId` | 回写前需额外查计划关联 |
| 优先级为自定义字段 | 不能直接 `WHERE priority = 'P0'` |
| Text 模式用例 | 步骤在 `textDescription`/`expectedResult`，非 `steps` JSON |
| 无 Agent 审计 | 无法区分人工 vs Agent 执行 |
| 无 MCP / OpenAPI 分组 | Cursor 等难以 native 接入 |

### 2.3 功能用例执行特性

- MeterSphere **不在平台内执行 UI 自动化**；功能用例为手动执行模型
- Agent 在外部按 `steps` 逐步执行（Playwright 等）
- 回写通过 `TestPlanFunctionalCaseService.run()` 更新计划状态、用例状态、步骤 blob、执行历史

---

## 3. 目标架构

```
┌──────────────────────────────────────────────────────────────────┐
│  接入层                                                           │
│  Cursor │ Codex │ GPT │ Claude │ Qwen │ DeepSeek │ Dify │ CI    │
└────────────┬──────────────────────────────┬──────────────────────┘
             │ MCP（P1，薄封装）              │ REST（主通道）
             ▼                              ▼
┌─────────────────────┐         ┌──────────────────────────────────┐
│ metersphere-mcp      │  HTTP   │ agent-integration（新建模块）     │
│ 无业务逻辑           │ ──────▶ │ /api/agent/v1/*                  │
└─────────────────────┘         └──────────────┬───────────────────┘
                                               │
                    ┌──────────────────────────┼──────────────────────────┐
                    │                          │                          │
         AgentQueryResolver          AgentCaseSchemaMapper      AgentTokenFilter
         （检索条件解析）              （内外部 Schema 适配）        （Bearer 认证）
                    │                          │                          │
                    └──────────────────────────┼──────────────────────────┘
                                               │ 复用现有 Service
                              ┌────────────────▼───────────────────┐
                              │ case-management / test-plan           │
                              │ FunctionalCaseService                 │
                              │ FunctionalCaseModuleService           │
                              │ TestPlanFunctionalCaseService         │
                              └──────────────────────────────────────┘
```

### 3.1 设计原则

1. **REST 是唯一业务入口**，MCP 只做 HTTP 转发，不含业务逻辑。
2. **自然语言理解在 Agent 侧，检索条件解析在服务端**：Agent 负责拆意图；平台负责模块 ID、自定义字段、计划关联解析。
3. **不改现有 UI API**，新增 `/api/agent/v1` 专用层。
4. **一次 search 返回回写所需 ID**（`caseId` + `testPlanCaseId` + `testPlanId`）。
5. **必须走 Service 层回写**，禁止 Agent API 直接写库。
6. **返回消歧信息**（`matchedBy`、`matchedModules`、`warnings`）供 Agent 确认后再执行。

### 3.2 职责边界

| 层级 | 职责 | 不负责 |
|------|------|--------|
| 用户 / LLM | 理解「订单模块 P0 未执行」等自然语言 | 模块 ID、自定义字段解析 |
| Agent Tool / MCP | 构造 JSON 请求、编排执行与回写 | 用例 CRUD、权限校验 |
| Agent API | 解析检索条件、组装 Schema、鉴权、审计 | UI 自动化执行 |
| 现有 Service | 业务规则、事务、历史记录 | Agent 协议适配 |

---

## 4. 后端改造

### 4.1 新建模块 `agent-integration`

#### 4.1.1 目录结构

```
backend/services/agent-integration/
├── pom.xml
└── src/main/java/io/metersphere/agent/
    ├── controller/
    │   └── AgentFunctionalCaseController.java
    ├── service/
    │   ├── AgentFunctionalCaseSearchService.java
    │   ├── AgentFunctionalCaseExportService.java      # Schema 组装、批量拉 steps
    │   ├── AgentFunctionalCaseSubmitService.java
    │   └── AgentExecLogService.java                   # P1 审计
    ├── dto/
    │   ├── AgentCaseSearchRequest.java
    │   ├── AgentCaseSearchResponse.java
    │   ├── AgentCaseDTO.java
    │   ├── AgentCaseStepDTO.java
    │   ├── AgentCaseSubmitRequest.java
    │   └── AgentSearchFilters.java
    ├── resolver/
    │   ├── AgentQueryResolver.java                    # 组合解析（替代 v1 互斥逻辑）
    │   └── ModuleTreeMatcher.java                     # 模块匹配 + 子树展开
    ├── mapper/
    │   └── AgentCaseSchemaMapper.java                 # 内外部字段映射
    └── security/
        ├── AgentTokenFilter.java
        └── AgentTokenService.java
```

#### 4.1.2 Maven 配置

**`backend/services/pom.xml`** 增加：

```xml
<module>agent-integration</module>
```

**`backend/app/pom.xml`** 增加依赖：

```xml
<dependency>
    <groupId>io.metersphere</groupId>
    <artifactId>metersphere-agent-integration</artifactId>
    <version>${revision}</version>
</dependency>
```

**`agent-integration/pom.xml`** 依赖：`metersphere-sdk`、`metersphere-case-management`、`metersphere-test-plan`、`metersphere-system-setting`。

---

### 4.2 REST API 设计

**统一前缀**：`/api/agent/v1`  
**认证**：`Authorization: Bearer msat_<token>`  
**项目上下文**：`X-MS-PROJECT: {projectId}`（必填；若 Token 绑定默认项目可省略）

#### 4.2.1 用例检索（核心）

```
POST /api/agent/v1/functional/search
```

**请求体：**

```json
{
  "query": "订单",
  "includeSteps": true,
  "testPlanId": "plan-001",
  "filters": {
    "priority": ["P0"],
    "lastExecuteResult": ["PENDING"],
    "tags": ["smoke"]
  },
  "current": 1,
  "pageSize": 50
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | string | 否* | 自然语言片段：模块名/标签/用例名/编号；与 filters 可组合 |
| includeSteps | boolean | 否 | 默认 `true`；`false` 时仅返回摘要，用于消歧确认 |
| testPlanId | string | 否 | 传入时限定计划内用例，并返回 `testPlanCaseId` |
| filters | object | 否 | 结构化过滤，见下表 |
| current / pageSize | int | 否 | 分页；`pageSize` 最大 500 |

\* `query` 与 `filters` 至少一项非空。

**filters 字段映射（服务端实现）：**

| filters 字段 | 内部实现 |
|-------------|---------|
| `priority` | 自定义字段 `functional_priority`（`FunctionalCaseCustomField`） |
| `lastExecuteResult` | `functional_case.last_execute_result`（含 `PENDING`） |
| `tags` | `functional_case.tags` JSON 包含匹配 |
| `moduleIds` | 直接指定模块 ID（Agent 已知时跳过 NL 解析） |

**响应体：**

```json
{
  "code": 100200,
  "data": {
    "matchedBy": ["module", "filter"],
    "matchedModules": ["订单", "订单/下单流程"],
    "matchedModuleIds": ["mod-order", "mod-order-create"],
    "total": 8,
    "warnings": [],
    "cases": [
      {
        "caseId": "fc-001",
        "num": 1001,
        "name": "订单-创建订单",
        "modulePath": "订单/下单流程",
        "caseEditType": "STEP",
        "tags": ["订单", "P0"],
        "priority": "P0",
        "prerequisite": "已登录，购物车有商品",
        "testPlanId": "plan-001",
        "testPlanCaseId": "relate-001",
        "lastExecuteResult": "PENDING",
        "steps": [
          {
            "id": "step-uuid-1",
            "num": 1,
            "desc": "进入订单创建页",
            "expected": "页面正常展示",
            "actualResult": null,
            "executeResult": null
          }
        ]
      }
    ]
  }
}
```

**关键行为：**

1. `testPlanId` 有值时，每条 case 必须带 `testPlanCaseId`（来自 `test_plan_functional_case.id`）。
2. `includeSteps=true` 时由 `AgentFunctionalCaseExportService` 批量调用 `getFunctionalCaseDetail()`，避免 Agent 侧 N+1。
3. 返回 `warnings`：如「模块未命中，已降级为 keyword」「Text 模式已转为虚拟步骤」。

#### 4.2.2 单条用例详情

```
GET /api/agent/v1/functional/{caseId}?includeSteps=true&testPlanId={planId}
```

用于 search 摘要模式后的按需拉取；`testPlanId` 可选，用于附带 `testPlanCaseId`。

#### 4.2.3 模块树（消歧）

```
GET /api/agent/v1/functional/modules?projectId={projectId}
```

```json
{
  "code": 100200,
  "data": [
    { "id": "mod-1", "name": "财务", "path": "财务", "parentId": "ROOT" },
    { "id": "mod-2", "name": "报销", "path": "财务/报销", "parentId": "mod-1" }
  ]
}
```

Agent 在用户表述模糊时先调 `list_modules`，再带 `filters.moduleIds` 精确 search。

#### 4.2.4 结果回写

```
POST /api/agent/v1/functional/submit
```

**请求体（Agent 友好 Schema）：**

```json
{
  "projectId": "proj-001",
  "caseId": "fc-001",
  "testPlanId": "plan-001",
  "testPlanCaseId": "relate-001",
  "lastExecResult": "SUCCESS",
  "executedBy": "cursor-agent",
  "steps": [
    {
      "id": "step-uuid-1",
      "num": 1,
      "desc": "进入订单创建页",
      "actualResult": "页面正常加载，耗时 1.2s",
      "executeResult": "SUCCESS"
    }
  ],
  "content": "Agent 自动执行完成"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| projectId | 是 | 对应 `TestPlanCaseRunRequest.projectId` |
| caseId | 是 | 功能用例 ID |
| testPlanId | 计划内必填 | 测试计划 ID |
| testPlanCaseId | 计划内必填 | `test_plan_functional_case.id`（**不是 caseId**） |
| lastExecResult | 是 | `SUCCESS` / `ERROR` / `BLOCKED` / `FAKE_ERROR` |
| steps | 否 | Agent 侧数组；服务端转为 `stepsExecResult` JSON 字符串 |
| executedBy | 否 | 写入 `content` 前缀或 `agent_exec_log` |
| content | 否 | 执行备注 |

**内部映射（`AgentFunctionalCaseSubmitService`）：**

```java
TestPlanCaseRunRequest runRequest = new TestPlanCaseRunRequest();
runRequest.setProjectId(submit.getProjectId());
runRequest.setId(submit.getTestPlanCaseId());          // 注意：id = 计划关联 ID
runRequest.setCaseId(submit.getCaseId());
runRequest.setTestPlanId(submit.getTestPlanId());
runRequest.setLastExecResult(submit.getLastExecResult());
runRequest.setStepsExecResult(JSON.toJSONString(
    schemaMapper.toFunctionalCaseSteps(submit.getSteps())
));
runRequest.setContent(formatContent(submit.getExecutedBy(), submit.getContent()));
testPlanFunctionalCaseService.run(runRequest, logInsertModule);
```

**`run()` 实际写入（已有逻辑，不修改）：**

| 目标 | 表/字段 |
|------|---------|
| 计划用例状态 | `test_plan_functional_case.last_exec_result` |
| 用例库状态 | `functional_case.last_execute_result` |
| 步骤实际结果 | `functional_case_blob.steps` |
| 执行历史 | `test_plan_case_execute_history` |

**计划外回写（P1）：**

```
无 testPlanCaseId 时：
  → 更新 functional_case.last_execute_result
  → 写入 agent_exec_log（审计）
  → 不写入 test_plan_case_execute_history（平台计划报告不可见）
```

> **MVP 建议**：团队预置「Agent 专用测试计划」，用例关联后走计划内回写，确保 UI 可见完整执行历史。

---

### 4.3 检索条件解析器（修订）

`AgentQueryResolver` 采用 **组合解析**，不做 v1.0 的互斥 early-return。

#### 4.3.1 解析流程

```
输入: query + filters + projectId
    │
    ├─ 1. 若 filters.moduleIds 已有 → 直接使用
    │
    ├─ 2. 若 query 非空 → 模块树匹配（name/path contains）
    │      命中节点 → 展开所有子孙模块 ID → matchedBy += "module"
    │
    ├─ 3. 若 filters.tags 或 query 疑似标签 → tags 匹配
    │
    ├─ 4. 若仍未限定范围 → query 作为 keyword（name/num/tags LIKE）
    │
    └─ 5. 叠加 filters（priority / lastExecuteResult / tags）
           priority → functional_priority 自定义字段子查询
```

#### 4.3.2 用户说法与请求构造示例

| 用户说法 | Agent 构造的请求 |
|---------|-----------------|
| 提取财务模块测试用例 | `{ "query": "财务" }` |
| 提取订单模块 P0 用例 | `{ "query": "订单", "filters": { "priority": ["P0"] } }` |
| 找登录相关的用例 | `{ "query": "登录" }`（keyword 兜底） |
| 计划内未执行 P0 | `{ "testPlanId": "plan-001", "filters": { "priority": ["P0"], "lastExecuteResult": ["PENDING"] } }` |
| 用例编号 1001 | `{ "query": "1001" }` |
| 精确指定模块 | `{ "filters": { "moduleIds": ["mod-xxx"] } }` |

#### 4.3.3 伪代码（修订）

```java
public ResolvedSearchCondition resolve(AgentCaseSearchRequest request, String projectId) {
    ResolvedSearchCondition condition = new ResolvedSearchCondition();
    List<String> matchedBy = new ArrayList<>();

    if (CollectionUtils.isNotEmpty(request.getFilters().getModuleIds())) {
        condition.setModuleIds(request.getFilters().getModuleIds());
        matchedBy.add("moduleIds");
    } else if (StringUtils.isNotBlank(request.getQuery())) {
        ModuleMatchResult match = moduleTreeMatcher.match(projectId, request.getQuery());
        if (match.isHit()) {
            condition.setModuleIds(match.getExpandedModuleIds()); // 含子树
            condition.setMatchedModules(match.getPaths());
            matchedBy.add("module");
        }
    }

    if (StringUtils.isBlank(condition.getKeyword())
            && matchedBy.isEmpty()
            && StringUtils.isNotBlank(request.getQuery())) {
        condition.setKeyword(request.getQuery());
        matchedBy.add("keyword");
    }

    applyFilters(condition, request.getFilters(), projectId); // priority/tags/lastExecuteResult
    condition.setMatchedBy(matchedBy);
    return condition;
}
```

#### 4.3.4 后续增强（P2）

| 增强 | 说明 |
|------|------|
| 模块别名表 | 「CW」→「财务」 |
| LLM 辅助结构化 | 复杂 query 输出 `resolved` JSON，服务端只做 ID 解析 |
| 向量语义检索 | 「和支付流程相关的用例」 |

---

### 4.4 Schema 适配层（新增）

`AgentCaseSchemaMapper` 统一内外部字段，屏蔽 MeterSphere 内部差异。

#### 4.4.1 用例字段映射

| AgentCaseDTO | 内部来源 |
|-------------|---------|
| caseId | `FunctionalCase.id` |
| expected（步骤） | `FunctionalCaseStepDTO.result` |
| priority | 自定义字段 `functional_priority` |
| modulePath | 模块树 path 拼接 |
| testPlanCaseId | `TestPlanCasePageResponse.id` |
| lastExecuteResult | 计划内取 `test_plan_functional_case.last_exec_result`，否则取用例库 |

#### 4.4.2 Text 模式用例适配

当 `caseEditType = Text` 时，`steps` JSON 可能为空。适配规则：

```json
{
  "caseEditType": "TEXT",
  "steps": [
    {
      "num": 1,
      "desc": "{textDescription}",
      "expected": "{expectedResult}",
      "actualResult": null,
      "executeResult": null
    }
  ]
}
```

响应 `warnings` 增加：`"TEXT_MODE_CONVERTED"`。

#### 4.4.3 步骤回写映射

Agent 提交 `steps[]` → 转为 `FunctionalCaseStepDTO[]` → `JSON.stringify` → `stepsExecResult`。

- 保留 Agent 传入的 `step.id`；若为空，`run()` 内现有逻辑会补 UUID
- `executeResult` 枚举与 `ResultStatus` 一致

---

## 5. 认证改造

### 5.1 Agent Token（Bearer）

#### 5.1.1 数据库表

```sql
CREATE TABLE agent_token (
    id           VARCHAR(50)  PRIMARY KEY,
    name         VARCHAR(100) NOT NULL COMMENT 'Token 名称',
    token_prefix VARCHAR(10)  NOT NULL COMMENT '前缀 msat',
    token_hash   VARCHAR(128) NOT NULL COMMENT 'SHA-256(token)',
    user_id      VARCHAR(50)  NOT NULL COMMENT '关联用户',
    project_id   VARCHAR(50)           COMMENT '默认项目 ID',
    scopes       VARCHAR(255)          COMMENT 'FUNCTIONAL_READ,SUBMIT',
    expire_time  BIGINT                COMMENT '过期时间戳，NULL=永不过期',
    enable       TINYINT(1)   DEFAULT 1,
    create_time  BIGINT,
    create_user  VARCHAR(50)
);

CREATE TABLE agent_exec_log (
    id               VARCHAR(50) PRIMARY KEY,
    case_id          VARCHAR(50) NOT NULL,
    test_plan_id     VARCHAR(50),
    test_plan_case_id VARCHAR(50),
    last_exec_result VARCHAR(20) NOT NULL,
    executed_by      VARCHAR(100) COMMENT 'Agent 标识',
    steps_snapshot   LONGTEXT COMMENT '步骤执行快照 JSON',
    content          LONGTEXT COMMENT '执行备注',
    create_time      BIGINT,
    create_user      VARCHAR(50)
);
```

Flyway：`backend/framework/domain/src/main/resources/migration/3.x.x/ddl/V3.x.x__agent_integration.sql`

#### 5.1.2 Token 格式

```
msat_<random_32_chars>
```

仅存 `SHA-256(token)`，明文仅创建时展示一次。

#### 5.1.3 Scope

| Scope | 允许操作 |
|-------|---------|
| FUNCTIONAL_READ | search / get / modules |
| FUNCTIONAL_SUBMIT | submit |
| FUNCTIONAL_ALL | 全部 |

#### 5.1.4 Shiro 集成（修订）

当前链路：`/** → apikey, csrf, authc`（`ShiroConfig`）。

```java
// FilterChainUtils.java
filterChainDefinitionMap.put("/api/agent/v1/health", "anon");

// ShiroConfig.java
shiroFilterFactoryBean.getFilters().put("agentToken", new AgentTokenFilter());
filterChainDefinitionMap.put("/api/agent/v1/**", "agentToken");
```

`AgentTokenFilter` 职责：

1. 解析 `Authorization: Bearer msat_xxx`
2. 校验 `agent_token` 表（hash、过期、scope、enable）
3. 以关联 `user_id` 登录 Shiro Session
4. 设置 `SessionUtils.setCurrentProjectId()`（来自 header 或 token 默认项目）
5. **跳过 CSRF**（与 `ApiKeyFilter` 同类处理）

#### 5.1.5 Token 管理

- P0：SQL / 管理脚本创建
- P2：系统设置 → Agent 集成 → Token 管理 UI

---

## 6. OpenAPI 文档

### 6.1 配置

```properties
# commons.properties（可按环境覆盖）
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true
springdoc.api-docs.groups.enabled=true
```

生产环境可通过 profile 关闭 Swagger UI，保留 `/v3/api-docs/agent` 供 Bot 导入。

### 6.2 Agent 分组

```java
@Bean
public GroupedOpenApi agentApi() {
    return GroupedOpenApi.builder()
        .group("agent")
        .pathsToMatch("/api/agent/v1/**")
        .build();
}
```

| 文档 | URL |
|------|-----|
| Agent API Spec | `GET /v3/api-docs/agent` |
| Swagger UI | `/swagger-ui.html` |

---

## 7. MCP Server（P1，薄封装）

### 7.1 定位

- npm 包 `@midoo/metersphere-mcp`
- **仅 HTTP 转发**，参数原样传递
- 供 Cursor、Claude Desktop 使用

### 7.2 MCP Tools

| Tool | REST | 说明 |
|------|------|------|
| `search_functional_cases` | `POST .../search` | 支持 query + filters + testPlanId |
| `get_functional_case` | `GET .../{caseId}` | 按需拉详情 |
| `submit_functional_result` | `POST .../submit` | 回写结果 |
| `list_modules` | `GET .../modules` | 模块消歧 |

### 7.3 Cursor 配置

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

### 7.4 Agent 工作流规则（建议 `.cursor/rules`）

1. 用户提及「提取用例」→ 先 `list_modules`（表述模糊时）→ `search_functional_cases`
2. 检查响应 `matchedBy` / `warnings`；命中数 > 20 时先摘要确认
3. 执行前确认 `testPlanCaseId` 存在；无则提示关联到 Agent 专用计划
4. 执行完成后逐条 `submit_functional_result`，保留 `step.id`

---

## 8. 平台接入矩阵

| 平台 | 接入方式 | 提取用例 | 回写结果 |
|------|---------|---------|---------|
| Cursor | MCP | ✅ | ✅ |
| Codex / GPT | OpenAPI + Function Calling | ✅ | ✅ |
| Claude | API Tools + OpenAPI | ✅ | ✅ |
| Dify / Coze | 自定义 Tool + OpenAPI | ✅ | ✅ |
| CI 脚本 | curl REST | ⚠️ 需结构化参数 | ✅ |

---

## 9. Agent 执行模型

```
search（含 steps + testPlanCaseId）
    ↓
外部按 steps 执行（Playwright 等）
    ↓
逐步记录 actualResult / executeResult
    ↓
汇总 lastExecResult（全 SUCCESS → SUCCESS，否则 ERROR）
    ↓
submit 回写
```

### 9.1 推荐工具组合

| 组件 | 用途 |
|------|------|
| Cursor + MCP | 读用例 + 回写 |
| Playwright Skill / CLI | UI 步骤执行 |
| 截图附件 | P2 evidence |

---

## 10. 数据规范（并行推进）

| 规范 | 做法 | 示例 |
|------|------|------|
| 模块按业务域划分 | 顶层节点清晰 | 财务/、订单/ |
| 用例挂正确模块 | 创建时选对模块 | 报销 → 财务/报销 |
| 标签统一 | 每条用例打标签 | `["P0","smoke"]` |
| **Agent 专用测试计划** | 固定 planId，用例预先关联 | `Agent-功能测试-2026` |
| 优先级用自定义字段 | 使用 `functional_priority` | P0 / P1 / P2 |
| 模块别名（P2） | 配置映射 | CW→财务 |

---

## 11. 分阶段实施计划

### 阶段 1：MVP（1–2 周）

| 序号 | 任务 | 产出 |
|------|------|------|
| 1 | 新建 `agent-integration` 模块 | Maven 模块骨架 |
| 2 | `AgentQueryResolver` + `ModuleTreeMatcher` | 组合解析 + 子树展开 |
| 3 | `AgentCaseSchemaMapper` | Step/Text 适配、priority 映射 |
| 4 | 4 个 REST 接口 | search / get / modules / submit |
| 5 | search 返回 `testPlanCaseId` | testPlanId 条件下强制 |
| 6 | `AgentTokenFilter` + Flyway | Bearer 认证 + 表结构 |
| 7 | 计划内回写 | 映射 `TestPlanCaseRunRequest` 调 `run()` |
| 8 | OpenAPI agent 分组 | `/v3/api-docs/agent` |
| 9 | 集成测试 + curl 文档 | 端到端验收 |

**验收标准：**

```bash
# 1. 检索（含 steps + testPlanCaseId）
curl -X POST http://localhost:8081/api/agent/v1/functional/search \
  -H "Authorization: Bearer msat_xxx" \
  -H "X-MS-PROJECT: proj-001" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "订单",
    "testPlanId": "plan-001",
    "filters": { "priority": ["P0"] },
    "includeSteps": true
  }'

# 2. 回写
curl -X POST http://localhost:8081/api/agent/v1/functional/submit \
  -H "Authorization: Bearer msat_xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "proj-001",
    "caseId": "fc-001",
    "testPlanId": "plan-001",
    "testPlanCaseId": "relate-001",
    "lastExecResult": "SUCCESS",
    "executedBy": "cursor-agent",
    "steps": [{
      "id": "step-uuid-1",
      "num": 1,
      "actualResult": "通过",
      "executeResult": "SUCCESS"
    }],
    "content": "Agent 自动执行完成"
  }'
```

**MVP 完成定义：**

- [ ] Agent Token 可鉴权并通过 Shiro
- [ ] search 在含 `testPlanId` 时返回 `testPlanCaseId`
- [ ] submit 后平台「测试计划 → 执行历史」可见记录
- [ ] Text 模式用例可返回可执行 steps
- [ ] 无改动现有 `/functional/case/*` UI API 行为

### 阶段 2：Cursor 接入（1 周）

| 序号 | 任务 | 产出 |
|------|------|------|
| 1 | 发布 `metersphere-mcp` | 4 个 Tool |
| 2 | 团队接入文档 | MCP 配置 + 专用测试计划指引 |
| 3 | `.cursor/rules` | 默认 MeterSphere 工作流 |

### 阶段 3：增强（1–2 周）

| 序号 | 任务 | 产出 |
|------|------|------|
| 1 | 计划外回写 + `agent_exec_log` 查询 API | 有限闭环 |
| 2 | 截图/附件 evidence | 失败追溯 |
| 3 | 模块别名 | 提升 NL 命中率 |
| 4 | Token 管理 UI | 系统设置页 |
| 5 | 批量 submit | 减少往返 |

---

## 12. 不需要改动的部分

| 模块 | 原因 |
|------|------|
| 现有 UI API（`/functional/case/*`） | 向后兼容 |
| 功能用例 CRUD 核心 | 只读 + 回写，不改创建逻辑 |
| 接口测试模块 | 范围外 |
| Shiro 权限体系 | 扩展 Filter，不替换 |
| 前端页面 | P0/P1 无需改动 |

---

## 13. 风险与注意事项

| 风险 | 缓解措施 |
|------|---------|
| Agent Token 泄露 | Scope + 过期 + 审计；生产限 IP |
| 自然语言误匹配 | `matchedBy` + `matchedModules` + `warnings`；Agent 确认后再执行 |
| 大量用例超 context | 分页 + `includeSteps=false` 摘要模式 |
| 优先级过滤失效 | 明确映射 `functional_priority`；集成测试覆盖 |
| Text 模式步骤缺失 | Schema 适配层虚拟步骤 + warning |
| 回写 ID 混淆 | 文档与 API 明确 `testPlanCaseId` ≠ `caseId` |
| CSRF 拦截 Agent 请求 | `AgentTokenFilter` 与 `ApiKeyFilter` 同等处理 |
| 计划外回写不可见 | MVP 强制使用 Agent 专用测试计划 |
| 执行质量不可控 | 先覆盖冒烟/P0；关键用例人工复核 |

---

## 14. 完整交互示例

### 14.1 Cursor 用户

> 提取订单模块 P0 测试用例，在测试环境执行，结果回写 MeterSphere

```
1. search_functional_cases({
     query: "订单",
     testPlanId: "agent-plan-2026",
     filters: { priority: ["P0"] },
     includeSteps: true
   })
   → 5 条用例，均含 testPlanCaseId

2. Playwright 按 steps 执行

3. submit_functional_result × 5

4. 回复：「订单模块 P0 共 5 条，通过 4，失败 1（#1003）」
```

### 14.2 模块表述模糊时

```
1. list_modules() → 财务/报销/订单/用户中心
2. 用户确认「订单」
3. search_functional_cases({ query: "订单", ... })
```

---

## 15. 关键文件速查

| 用途 | 路径 |
|------|------|
| 功能用例实体 | `backend/framework/domain/.../functional/domain/FunctionalCase.java` |
| 用例详情 DTO | `backend/services/case-management/.../dto/FunctionalCaseDetailDTO.java` |
| 步骤 DTO | `backend/services/case-management/.../dto/FunctionalCaseStepDTO.java` |
| 用例列表请求 | `backend/services/case-management/.../request/FunctionalCasePageRequest.java` |
| 用例 Service | `backend/services/case-management/.../service/FunctionalCaseService.java` |
| 模块树 Service | `backend/services/case-management/.../service/FunctionalCaseModuleService.java` |
| 计划用例响应 | `backend/services/test-plan/.../dto/response/TestPlanCasePageResponse.java` |
| 计划回写 Service | `backend/services/test-plan/.../service/TestPlanFunctionalCaseService.java` |
| 回写请求 DTO | `backend/services/test-plan/.../dto/request/TestPlanCaseRunRequest.java` |
| 结果枚举 | `backend/framework/sdk/.../constants/ResultStatus.java` |
| API Key | `backend/services/system-setting/.../security/ApiKeyHandler.java` |
| Shiro 配置 | `backend/services/system-setting/.../config/ShiroConfig.java` |
| Filter 链 | `backend/framework/sdk/.../util/FilterChainUtils.java` |
| OpenAPI 配置 | `backend/app/src/main/resources/commons.properties` |
| 关键词 SQL | `backend/services/case-management/.../mapper/ExtFunctionalCaseMapper.xml` |

---

## 16. 总结

| 问题 | 答案 |
|------|------|
| 方案是否可行？ | **是**，复用现有 Service，增量新建 `agent-integration` |
| 能否对话提取用例？ | **能**，Agent 理解 NL + Agent API 检索；服务端负责条件解析与 Schema 适配 |
| 能否回写执行记录？ | **计划内完整支持**；计划外 P1 有限支持（建议 MVP 使用专用测试计划） |
| 最小改造路径？ | Agent API 4 接口 + Token + Schema 适配层 |
| 预计工期？ | MVP 1–2 周；含 MCP 2–3 周 |

---

## 附录 A：接口权限对照

| 接口 | Scope | 对应现有权限 |
|------|-------|-------------|
| `POST .../search` | FUNCTIONAL_READ | `FUNCTIONAL_CASE:READ` |
| `GET .../{caseId}` | FUNCTIONAL_READ | `FUNCTIONAL_CASE:READ` |
| `GET .../modules` | FUNCTIONAL_READ | `FUNCTIONAL_CASE:READ` |
| `POST .../submit` | FUNCTIONAL_SUBMIT | `FUNCTIONAL_CASE:READ` + 计划执行权限 |

## 附录 B：Agent API ↔ 内部 DTO 对照

| Agent 字段 | 内部字段 | 备注 |
|-----------|---------|------|
| `caseId` | `FunctionalCase.id` | |
| `testPlanCaseId` | `TestPlanCaseRunRequest.id` | **易混淆，必须文档化** |
| `steps[].expected` | `FunctionalCaseStepDTO.result` | |
| `steps[]`（请求） | `TestPlanCaseRunRequest.stepsExecResult` | JSON 字符串 |
| `filters.priority` | `functional_priority` 自定义字段 | 非表列 |
| `lastExecuteResult: PENDING` | `functional_case.last_execute_result` | 测试数据常用初始值 |

## 附录 C：响应 warnings 枚举（建议）

| code | 含义 |
|------|------|
| `MODULE_NOT_MATCHED_KEYWORD_FALLBACK` | 模块未命中，降级 keyword |
| `TEXT_MODE_CONVERTED` | Text 模式已转虚拟步骤 |
| `TEST_PLAN_CASE_ID_MISSING` | 未传 testPlanId，无回写 ID |
| `LARGE_RESULT_TRUNCATED` | 命中数超阈值，建议缩小范围 |

---

*文档结束 — v2.0*
