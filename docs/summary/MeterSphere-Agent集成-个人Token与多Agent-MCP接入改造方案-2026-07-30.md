# MeterSphere Agent 集成：个人 Token 与多 Agent MCP 接入改造方案

> 初稿日期：2026-07-30；修订日期：2026-10-20
> 适用版本：MeterSphere 3.7.x
> 范围：系统设置 → 系统 → Agent 集成、个人 Token、MCP 服务、技能包
> 文档性质：产品与技术联合改造方案，不包含本次代码实施

## 0. 修订基准与约束

本次修订以[质量门禁修复与完善方案](质量门禁修复与完善方案-20261019.md)为最高业务约束，承接[MCP 与 AI 技能包整改方案](../mcp-and-ai-skill-remediation-plan-20261019.md)及[用例库能力方案](../metersphere-mcp-case-library-improvement-plan-2026-10-20.md)。§19 为本次实施契约；历史现状表和初稿估算不作为当前实现或验收证明。

- 全局质量门禁统一生效，不按项目采用不同放行标准。纯系统管理员能访问系统级治理和门禁；业务资源仍按项目、实际归属和当前权限校验。
- 正式结果只通过冻结合同、有效 attempt/租约、事实与证据、服务端判定和持久化正式回写产生。保留 QUALITY_LEGACY_SUBMIT_FORBIDDEN；管理员、人工确认、Token scope 和客户端审批不能替代事实或绕过门禁。
- 执行中删除沿用用例库方案的预检及用户决定：取消、等待或具备停止权限时停止后删除；不能用 Token 创建时的风险确认替代具体业务确认。
- 本次仅修订方案，不改变现有实现的部分完成状态，不包含代码修改、部署或运行验收。

## 1. 结论

本方案在已有个人 Token、远程 MCP 与技能包基础上补齐安全和业务链路。已有入口不代表通过验收；当前基线及修订契约见 §19。目标为：

1. **个人自助凭据**：登录用户只能为自己创建、查看、禁用、轮换和删除 Token，不能选择或冒充其他用户。
2. **管理面与调用面分轨**：Web 会话管理 Token；Agent 使用个人 Token 调用 MCP，个人 Token 不能反向管理 Token。
3. **原生远程 MCP**：MeterSphere 直接提供标准 Streamable HTTP MCP 服务，Codex、ChatGPT、Cursor、WorkBuddy 等支持远程 MCP 的客户端可直接接入。
4. **远程优先、兼容迁移**：本轮保留 Node.js MCP 与本地 `stdio` 适配器及验收；技能包必须能指导 AI 根据用户提供的地址和 Token 正确配置并连接远程 MCP。
5. **技能包与密钥分离**：用户可下载技能包交给 AI，技能包包含操作知识、连接步骤、Tool 说明和各客户端配置模板，但永不包含真实 Token。
6. **普通用户自主选权**：用户可自行选择 Token scopes，包括 `AGENT_ALL`；最终权限仍为用户实时 RBAC ∩ Token scopes ∩ Token 项目范围 ∩ MCP Tool 安全策略，Token 永远不能提升用户权限。

目标形态：

```text
个人用户
  ├─ Web 会话 ──► 创建/禁用/轮换/删除自己的 Token
  ├─ 下载技能包 ──► 交给 Codex/Cursor/WorkBuddy 等 AI
  └─ 复制 MCP 地址与配置 ──► Agent 使用个人 Token

Codex / ChatGPT / Cursor / WorkBuddy
  └─ Streamable HTTP ──► /api/mcp

/api/mcp
  └─ Token 身份适配 ──► 原有用户/RBAC/项目权限 ──► 既有业务 Service
```

## 2. 参考设计原则

本方案吸收以下两份参考文档的设计模式：

- `个人API密钥模块-设计模式与设计思路-2026-07-30.md`
- `Agent与IDE集成说明.md`

采用的原则：

| 原则 | MeterSphere 落地方式 |
|---|---|
| 双轨鉴权 | Web 会话管理个人 Token；Agent Token 只用于 MCP/Agent 调用 |
| 换身份证，不换规则 | Token 验证后注入当前用户身份，复用 RBAC 和领域服务 |
| 一次性明文 | Token 完整值仅创建/轮换成功时展示一次 |
| 密钥不落明文 | 公共 ID 定位记录，Argon2id/BCrypt 验证秘密段 |
| 技能包不含凭据 | zip 只有 `SKILL.md`、说明、manifest 和占位配置 |
| 权限不高于本人 | 用户 RBAC、项目成员关系和 Token scope 同时校验 |
| 热路径轻量 | 最近使用时间与调用次数异步、原子更新 |
| 可立即吊销 | 禁用、删除、用户停用或权限撤回后立即失效 |

## 3. 历史差距记录（不作为当前实施基线）

以下为初稿历史记录，已部分过时。当前已有个人 Token 控制器、BCrypt 验证及远程 MCP；实施必须以 §19.2 的增量核对为准，不重复建设。

### 3.1 初稿实现概况

当前仓库已经具备：

- `agent_token` 数据表；
- Token 创建、分页、更新和删除接口；
- `msat_` Bearer Token 鉴权；
- Token scope、项目白名单和限流；
- `/api/agent/v1/**` REST 接口；
- 可下载的 `metersphere-mcp` Node.js `stdio` 包；
- 用例、测试计划、评审、缺陷和项目相关 MCP Tools。

### 3.2 核心差距

| 当前设计 | 问题 | 新设计 |
|---|---|---|
| 依赖 `SYSTEM_USER:*` 权限 | 普通用户通常无法创建自己的 Token | 所有已登录且状态正常的用户可管理自己的 Token |
| 创建表单可选择 `userId` | 实质是管理员代用户创建/冒充身份 | 移除 `userId`，后端强制绑定 `SessionUtils.getUserId()` |
| Token 列表查询全表 | 用户可能看见或操作他人 Token | 默认只查询当前用户；管理员另走治理接口 |
| 更新/删除只按 Token ID | 缺少所有权校验 | 每次操作校验 `token.user_id == currentUserId` |
| scope 直接使用内部枚举 | 用户难以理解能力边界 | 以业务能力解释 scope，同时允许用户直接选择 `AGENT_ALL` |
| 不选项目表示全部项目 | 容易形成过宽访问范围 | 默认选择当前项目；“全部本人可访问项目”需显式确认 |
| `SHA-256(rawToken)` 直接查找 | 数据库泄露后可离线直接比对候选 Token | 公共 ID 索引 + 慢哈希验证秘密段 |
| 仅支持 Bearer | 某些客户端或脚本使用专用 API Key 头 | 同时支持 Bearer 和 `X-API-Key` |
| MCP 包只有本地 `stdio` | ChatGPT、云端 Codex 等无法运行用户本地 Node 进程 | 新增公网/内网可达的 Streamable HTTP MCP |
| 技能包定位为本地 MCP 程序包 | “知识技能”和“协议实现”混在一起 | 技能包独立交付，Node 兼容链路按迁移条件保留 |
| 无调用次数/最近使用/最后 IP | 用户无法判断凭据是否被滥用 | 增加使用统计和安全审计 |
| 工具未完整标注读写属性 | 客户端难以实施审批策略 | MCP Tool 增加 `readOnlyHint`、`destructiveHint` 等 annotations |

## 4. 产品信息架构

### 4.1 用户使用面：个人 Agent 与 API

建议主入口从“系统设置 → 系统”移到：

```text
右上角个人头像 → 个人设置 → Agent 与 API
```

所有正常登录用户可进入，不要求系统用户管理权限。

如果必须保留原菜单路径，则“系统设置 → 系统 → Agent 集成”打开后也只能展示当前用户数据，并在页面顶部明确显示：

```text
当前身份：张三（zhangsan）
这里创建的 Token 仅代表你本人，权限不会超过你的 MeterSphere 权限。
```

页面拆为三个区域：

1. **快速接入**
2. **我的 Token**
3. **技能包与接入文档**

### 4.2 管理员治理面

管理员入口仍放在：

```text
系统设置 → 系统 → Agent 集成治理
```

该菜单和路由**仅系统管理员可见**。前端路由、菜单和按钮使用独立管理员权限控制，后端治理 API 再次执行管理员鉴权；普通用户即使手工输入 URL 也必须返回 403。

管理员可以：

- 查看 Token 元数据和所有者，但看不到完整 Token；
- 按用户、状态、客户端、最后使用时间筛选；
- 强制禁用/吊销；
- 配置是否允许个人 Token、最大数量、最大有效期、可用 scopes、允许的 MCP Tools 和限流；
- 查看审计与异常调用。

管理员不能：

- 查看用户 Token 明文；
- 为用户生成可长期使用的个人 Token；
- 使用用户 Token 登录；
- 修改 Token 所有者。

如确需服务账号，必须另建“服务账号凭据”模型，不得复用个人 Token。

## 5. 用户操作流程

### 5.1 首次接入向导

```text
选择客户端
  ↓
创建个人 Token
  ↓
选择项目范围和能力
  ↓
一次性复制 Token
  ↓
复制客户端配置 / 下载技能包
  ↓
连接测试
```

客户端选择卡片：

- ChatGPT
- Cursor
- WorkBuddy
- 其他 MCP 客户端

创建 Token 页面不提供 Codex 客户端选项。该调整仅影响 Token 的客户端用途标记，不删除平台对 Codex 的 MCP 接入能力；Codex 仍可使用创建后生成的通用 MCP 配置或技能包完成接入。

选择客户端后只展示该客户端所需的接入方式，不把所有 JSON 和说明堆在一个页面。页面删除以下提示，不再展示同义替代文案：

> 解压后按 INSTALL.md 配置本机 mcp.json（填入平台地址与 Token）。Token 仅本地保存，勿提交到 Git。

### 5.2 创建 Token

表单字段：

| 字段 | 规则 |
|---|---|
| Token 名称 | 必填，如“公司电脑测试助手” |
| 使用客户端 | ChatGPT / Cursor / WorkBuddy / Other，用于审计与生成配置；删除 Codex 选项 |
| 项目范围 | 默认当前项目；只能选择当前用户有权限的项目 |
| 权限范围 | 全部使用中文业务描述，用户可自主选择“全部 Agent 权限”“只读访问”“测试执行”“用例管理”“缺陷管理”“项目管理”或“自定义权限” |
| 有效期 | 默认 90 天；可选 7/30/90/180 天，永久需管理员策略允许 |
| IP 限制 | 可选，首版可仅预留 |

不再提供“关联用户”字段。

创建成功弹窗：

- 展示完整 Token 一次；
- 提供“复制 Token”；
- 根据当前平台地址和新 Token 即时生成完整 MCP 配置；
- 在独立代码框中展示 MCP 配置并提供“一键复制 MCP 配置”；
- 提供“下载技能包”；
- 要求用户勾选“我已保存 Token”后关闭；
- 关闭后不可再次查看完整 Token，只能轮换或新建。

MCP 配置生成规则：

- 配置中直接使用当前部署的 MCP 服务地址。
- Token 只在本次创建成功弹窗中注入配置并展示一次。
- 配置格式根据所选客户端生成；选择“其他”时生成注明用途的通用示意配置，不承诺可直接导入所有客户端。
- 创建页面虽不提供 Codex 选项，但用户仍可切换查看并复制 Codex 支持的 TOML 配置模板。
- Token 和 MCP 配置分别提供复制按钮，并显示复制成功反馈。
- 关闭弹窗前明确提示完整 Token 和含 Token 的配置均不可再次查看。
- 页面不得自动写入用户本机 Agent 配置；用户自行复制并在 Agent 中完成设置。

权限范围展示映射：

| 页面中文名称 | 内部 scope |
|---|---|
| 全部 Agent 权限 | `AGENT_ALL` |
| 功能测试只读 | `FUNCTIONAL_READ` |
| 受管执行与事实提交 | 按注册中心的任务/执行 scopes 生成；不开放旧结果直写 |
| 项目管理 | `PROJECT_READ` / `PROJECT_WRITE` |
| 测试用例管理 | 现有只读、创建、更新、评论及附件 scopes，按工具逐项映射 |
| 测试计划管理 | `PLAN_READ` / `PLAN_WRITE` |
| 用例评审管理 | 现有只读权限 / `REVIEW_WRITE`；不得发放未定义枚举 |
| 缺陷管理 | `BUG_READ` / `BUG_WRITE` |

前端选项、已选标签、Token 列表和确认弹窗均显示中文名称，不直接把 `AGENT_ALL`、`BUG_WRITE` 等内部枚举作为主文案。内部枚举仅用于接口传输、开发诊断和审计详情。

### 5.3 Token 列表

列表字段：

- 名称；
- Token 前缀，例如 `msat_ab12cd…`；
- 客户端；
- 项目范围；
- 能力范围；
- 状态；
- 创建时间；
- 到期时间；
- 最后使用时间；
- 调用次数；
- 最近来源 IP（可按隐私策略脱敏）。

操作：

- 启用/禁用；
- 编辑名称、项目范围和 scope；
- 轮换；
- 删除；
- 复制 MCP 地址/配置；
- 连接测试。

## 6. Token 安全模型

### 6.1 Token 格式

建议升级为：

```text
msat_{publicId}_{secret}
     └─可索引─┘ └─仅慢哈希存储─┘
```

示例仅用于说明：

```text
msat_k7Q2p9_4Hq...高熵随机秘密...
```

`publicId` 不是秘密，用于 O(1) 定位数据库记录；`secret` 至少 256 bit 随机熵。

### 6.2 数据模型

建议将 `agent_token` 演进为：

| 字段 | 说明 |
|---|---|
| `id` | 内部主键 |
| `name` | 用户命名 |
| `user_id` | 固定为创建用户 |
| `public_id` | Token 公共定位 ID，唯一索引 |
| `secret_hash` | 复用现有 BCrypt，参数及性能预算经验证固定 |
| `display_prefix` | 列表脱敏展示 |
| `client_type` | CHATGPT / CURSOR / WORKBUDDY / OTHER；历史 `CODEX` 值继续兼容读取，但创建页面不再新增 |
| `project_ids` | 项目白名单 JSON |
| `scopes` | scope 数组 JSON，不再用字符串包含判断 |
| `expire_time` | 到期时间 |
| `status` | ENABLED / DISABLED / REVOKED / EXPIRED |
| `last_used_at` | 最近成功鉴权时间 |
| `invocation_count` | 成功调用累计次数 |
| `last_ip` | 最近来源 IP，可选 |
| `create_time/create_user` | 审计字段 |
| `update_time/update_user` | 审计字段 |
| `revoked_at/revoked_by` | 吊销审计 |

### 6.3 旧 Token 迁移

现有 `SHA-256` Token 无法恢复明文，也无法直接转换成新慢哈希。

采用双版本过渡：

1. 新 Token 全部使用 v2 格式和新验证方式。
2. 旧 Token 标记 `token_version = 1`，在 30～60 天迁移窗口内继续验证。
3. 页面提示旧 Token “需要轮换”，一键生成 v2 Token。
4. 到期后关闭 v1 验证并吊销未轮换 Token。

### 6.4 鉴权头

同时支持：

```http
Authorization: Bearer msat_...
```

```http
X-API-Key: msat_...
```

优先级：

1. 按端点隔离认证机制，MCP 不因 Web/JWT 身份存在而放宽 Token 权限；
2. Bearer 与 X-API-Key 同传拒绝歧义输入；管理 API 拒绝个人 Token，包括同时携带会话的请求；
3. MCP 仅使用有效个人 Token 或受支持的 OAuth 凭据；无效 Token 不回退到 Web 会话；
4. Token 与会话同用户仍执行 Token 限制，不同用户拒绝；严格识别前缀，避免吞掉普通 JWT Bearer。

### 6.5 权限公式

```text
可执行 Tool
= 用户当前有效状态
∩ 用户当前 RBAC 权限
∩ 用户当前项目成员关系
∩ Token 项目白名单
∩ Token scopes
∩ 管理员全局 Agent 策略
```

关键规则：

- 创建 Token 时只能选择本人当前可访问项目。
- 调用时再次实时校验用户项目权限，创建后的权限撤回立即生效。
- 项目范围使用 SELECTED / ALL_ACCESSIBLE；历史空白名单迁移为 ALL_ACCESSIBLE，新建不再用空数组隐式表达全量范围。
- 写操作不能只检查 `AgentScopeAssert`，还必须执行原业务权限校验。
- 请求体中的 `executedBy/userId/createUser` 不可信，服务端统一以 Token 用户覆盖。
- 用户禁用、删除、锁定或离职后，其全部个人 Token 立即失效。

### 6.6 使用统计

鉴权成功后异步执行：

```sql
UPDATE agent_token
SET last_used_at = :now,
    invocation_count = invocation_count + 1,
    last_ip = :maskedIp
WHERE id = :tokenId;
```

高并发环境可先 Redis `INCR`，定时汇总刷库。统计失败不得阻塞 MCP 主调用。

## 7. 原生 MCP 服务设计

### 7.1 单一远程传输架构

原生服务提供 Streamable HTTP，Node/stdio 作为现有兼容客户端保留：

| 传输 | 地址/形态 | 面向客户端 |
|---|---|---|
| Streamable HTTP | `POST/GET/DELETE /api/mcp` | ChatGPT、Codex、Cursor、WorkBuddy 及其他远程 MCP 客户端 |

远程 MCP 为推荐方式；本轮继续维护 Node/stdio 兼容能力与制品测试。退役条件见 §19，不因本方案删除现有工程或降低用例库能力覆盖。

### 7.2 MCP 协议端点

建议独立于当前下载接口：

```text
/api/mcp                         标准 MCP Streamable HTTP
/api/personal/agent-package/manifest AI 技能包清单
/api/personal/agent-package/skill/download 下载 AI 技能包
```

避免当前 `/api/agent/mcp` 同时承担“下载控制器”和“协议服务”造成语义冲突。

远程 MCP 至少支持：

- `initialize`
- `notifications/initialized`
- `ping`
- `tools/list`
- `tools/call`

后续可扩展：

- `resources/list`
- `resources/read`
- `prompts/list`
- `prompts/get`

### 7.3 Tool 注册中心

后端建立统一 `McpToolRegistry`：

```java
interface MeterSphereMcpTool<I, O> {
    String name();
    String description();
    JsonSchema inputSchema();
    ToolAnnotations annotations();
    String requiredScope();
    String requiredPermission();
    O execute(I input, AgentPrincipal principal);
}
```

远程 HTTP MCP、技能包 Tool 文档和页面 manifest 由同一注册中心/规范生成，避免 Tool 描述漂移。

### 7.4 Tool 分组

#### 只读

- `list_projects`
- `search_projects`
- `list_functional_modules`
- `search_functional_cases`
- `get_functional_case`
- `get_test_plan`
- `get_case_review`
- `search_bugs`
- `get_bug`
- `get_execution_log`

#### 执行写入

- `upload_execution_attachment`
- 受管任务的事实提交、服务端门禁评估与正式回写（不新增直接结果提交工具）
- 批量受管执行仍逐项遵守冻结合同、证据、尝试及回写规则

#### 内容管理

- `create_functional_module`
- `create_functional_case`
- `batch_create_functional_cases`
- `create_test_plan`
- `associate_test_plan_cases`
- `create_case_review`
- `associate_case_review_cases`
- `create_bug`
- `update_bug`
- `relate_bug_case`

#### 高风险管理

- `create_project`
- `add_project_members`

普通用户可以选择包含高风险 Tool 的 `AGENT_ALL`。选择时必须展示能力清单和风险确认；客户端应对写入/高风险 Tool 启用审批，但服务端仍以用户 RBAC 和项目权限作为最终安全边界。

### 7.5 按项目名称模糊检索项目

新增只读 MCP Tool：`search_projects`。该 Tool 用于 Agent 在用户只提供项目名称或名称片段时，检索并确定后续业务 Tool 所需的 `projectId`。

#### 输入参数

```json
{
  "name": "资产云",
  "page": 1,
  "pageSize": 20,
  "includeArchived": false
}
```

| 参数 | 必填 | 规则 |
|---|---:|---|
| `name` | 是 | 项目名称关键词；去除首尾空格后长度 1～100 |
| `page` | 否 | 默认 1，必须大于 0 |
| `pageSize` | 否 | 默认 20，最大 100 |
| `includeArchived` | 否 | 默认 false；是否包含已归档但用户仍有查看权限的项目 |

#### 查询规则

- 按项目名称执行包含式模糊匹配，即 SQL 语义为安全转义后的 `LIKE '%关键词%'`。
- 根据数据库字符集执行中文、英文名称匹配；英文默认忽略大小写。
- `%`、`_`、反斜杠等通配符必须转义，不允许将用户输入直接拼接到 SQL。
- 返回结果必须同时满足：
  - 当前 Token 所属用户对项目具有查看权限；
  - 项目位于 Token 的项目白名单内；Token 未限制项目时使用用户当前全部可访问项目；
  - 项目状态符合 `includeArchived` 条件。
- 不返回仅因名称匹配但当前用户无权访问的项目，也不返回其数量、名称片段等旁路信息。
- 排序优先级为“名称完全匹配 → 名称前缀匹配 → 名称包含匹配 → 最近更新时间倒序 → projectId”，保证分页稳定。

#### 返回结构

```json
{
  "items": [
    {
      "projectId": "project-1",
      "name": "测试资产云平台",
      "status": "ACTIVE",
      "description": "资产管理与测试项目"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1,
  "hasMore": false
}
```

- 仅返回 Agent 识别项目所必需的信息，不附带项目成员、密钥或其他敏感配置。
- 未命中时返回空 `items`，不以 404 表示。
- 查询结果存在多个项目时，技能包必须指导 AI 向用户展示候选项目并确认，不得仅凭第一条结果执行写操作。

#### 权限与安全标注

| 项目 | 设计 |
|---|---|
| 内部 scope | `PROJECT_READ` 或 `AGENT_ALL` |
| RBAC | 复用当前用户的项目查看权限 |
| `readOnlyHint` | `true` |
| `destructiveHint` | `false` |
| 审计 | 记录 Tool、Token ID、用户、关键词摘要、结果数量、耗时和结果码；不记录完整 Token |
| 限流 | 计入 Token 查询类 Tool 限流，防止枚举项目 |

`list_projects` 继续保留，用于列出可访问项目和连接测试；`search_projects` 专门用于名称模糊检索，避免改变已有 Tool 的输入输出契约。

### 7.6 Tool 安全标注

每个 Tool 提供标准 annotations：

| Tool 类型 | `readOnlyHint` | `destructiveHint` | 建议审批 |
|---|---:|---:|---|
| 查询 | true | false | 可设为无需逐次审批 |
| 创建/关联 | false | false | 首次或每次审批 |
| 更新执行结果/缺陷 | false | false | 每次审批 |
| 删除/覆盖/批量高风险 | false | true | 强制每次审批 |

服务端不能依赖客户端审批作为安全边界，审批只是额外保护；服务端仍必须鉴权、限流、校验幂等。

### 7.7 会话与无状态

首版优先无状态实现：

- 每个 MCP 请求都验证 Token；
- 项目 ID作为 Tool 参数，不依赖服务端 HTTP Session；
- 无状态模式不签发虚构 MCP session id，业务上下文不放入全局 `SessionUtils` 长期保存；
- 请求结束必须清理线程上下文。

当前通过 `SessionUtils.setCurrentProjectId()` 注入项目的实现可作为过渡，但新 Tool 服务应显式传递 `AgentPrincipal` 和 `projectId`，降低串请求污染风险。

## 8. 技能包设计

### 8.1 知识交付物：MeterSphere AI 技能包

文件名：

```text
metersphere-agent-skill-{version}.zip
```

内容：

```text
metersphere-agent/
  SKILL.md
  README.md
  manifest.json
  references/
    tools.md
    permissions.md
    workflows.md
    troubleshooting.md
  examples/
    codex.config.example.toml
    chatgpt-remote-mcp.example.json
    cursor.remote-mcp.example.json
    workbuddy-mcp.example.json
    generic-streamable-http.example.json
  scripts/
    verify-mcp-connection.js
  checksums.txt
```

用途：

- 告诉 AI MeterSphere 的领域术语和业务流程；
- 说明何时调用哪个 Tool；
- 约束先查项目/模板再执行写操作；
- 提供端到端工作流；
- 提供错误码和恢复方式。
- 明确告诉 AI 如何指导用户在客户端秘密存储或本地环境变量中设置 `MS_BASE_URL`、`MS_AGENT_TOKEN` 和可选默认项目；
- 指导 AI识别 Codex、ChatGPT、Cursor、WorkBuddy 的配置位置并生成远程 MCP 配置；
- 提供只执行 `initialize`、`tools/list` 和只读 Tool 的连接验证脚本或步骤。

本轮不删除 `metersphere-mcp/**`、现有下载和打包链路。退役须完成用户迁移、完整能力等价验证、公告窗口与回退设计，并形成明确切换决定；仅通过远程只读连接测试不足以退役。

### 8.2 技能包安全规则

技能包禁止包含：

- 真实 Token；
- 用户 ID、真实项目 ID；
- 内部生产域名（公共发行包）；
- 管理员账号密码；
- 自动执行高风险写操作的隐藏提示。

示例统一使用：

```text
${MS_BASE_URL}
${MS_AGENT_TOKEN}
${MS_PROJECT_ID}
```

构建流水线增加秘密扫描和 zip 内容测试；下载响应附带 SHA-256 校验值和版本。

### 8.3 Tool 使用指导

`SKILL.md` 应规定：

1. 用户提供项目名称或名称片段时，先调用 `search_projects`；唯一命中后使用返回的 `projectId`，多条命中时先请用户确认目标项目。
2. 批量创建前先调用模板/字段查询 Tool。
3. 事实提交采用 Token 身份，正式结果由服务端门禁判定后回写；忽略模型自行填写的执行人，不允许直接提交 PASS。
4. 删除、覆盖、批量更新必须要求用户确认。
5. 错误不得无限重试；429 遵循 `Retry-After`。
6. 不在对话、日志或生成文件中复述完整 Token。

## 9. 多客户端接入设计

### 9.1 能力矩阵

| 客户端 | 接入方式 | 技能包交付内容 |
|---|---|---|
| Codex | 远程 Streamable HTTP MCP | MCP URL、Bearer 环境变量模板、Skill |
| ChatGPT | 远程 MCP/自定义连接 | HTTPS MCP URL、认证说明 |
| Cursor | 远程 Streamable HTTP MCP | 远程 `mcp.json` 模板 |
| WorkBuddy | 标准远程 MCP | 通用远程 MCP 配置模板 |
| 其他客户端 | 标准 Streamable HTTP | 协议参数与连接检查 |

注意：不同客户端版本、工作区策略和套餐可能限制自定义 MCP 或自定义请求头。页面需要按部署时验证过的版本维护接入说明，不应承诺所有版本天然可用。

### 9.2 Codex 配置

推荐使用远程 MCP URL：

```toml
[mcp_servers.metersphere]
url = "${MS_BASE_URL}/api/mcp"
bearer_token_env_var = "MS_AGENT_TOKEN"
```

Token 保存在用户环境变量或客户端秘密存储中，不写入项目仓库的 `.codex/config.toml`。

### 9.3 ChatGPT 配置

ChatGPT 必须连接可从 ChatGPT 所在网络访问的 HTTPS MCP URL：

```text
https://metersphere.example.com/api/mcp
```

ChatGPT 受保护接入按 OAuth 路径规划，静态 Bearer 不作为默认支持承诺。实现 Authorization Code + PKCE、资源/授权服务元数据、注册策略、授权同意、令牌刷新与撤销，并按实际工作区和客户端版本验证；未验证前标记待支持。不能将 Responses API 的认证头能力等同于 ChatGPT 配置能力。

如果 MeterSphere 仅在企业内网，不应直接暴露公网；应通过企业批准的反向代理、私有连接或受控 MCP gateway 暴露，并保留 WAF、IP 策略和审计。

### 9.4 Cursor 配置

远程方式：

```json
{
  "mcpServers": {
    "metersphere": {
      "url": "${MS_BASE_URL}/api/mcp",
      "headers": {
        "Authorization": "Bearer ${MS_AGENT_TOKEN}"
      }
    }
  }
}
```

不再要求全局固定 `MS_PROJECT_ID`；项目 ID优先作为 Tool 参数，由技能引导 AI 先查询并选择项目。可保留默认项目作为便利配置。

### 9.5 WorkBuddy 与通用客户端

提供两种通用模板：

```json
{
  "name": "metersphere",
  "transport": "streamable-http",
  "url": "${MS_BASE_URL}/api/mcp",
  "headers": {
    "Authorization": "Bearer ${MS_AGENT_TOKEN}"
  }
}
```

具体字段名由前端接入卡片按已验证客户端版本生成。若客户端不支持远程 Streamable HTTP，应提示使用已验证的 Node/stdio 兼容方式或升级客户端；不承诺未实测版本兼容。

## 10. API 设计

### 10.1 个人 Token 管理 API

管理 API 必须使用 Web/JWT 会话，不接受个人 Token：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/personal/agent-tokens` | 创建本人 Token |
| `GET` | `/api/personal/agent-tokens` | 查询本人 Token |
| `PATCH` | `/api/personal/agent-tokens/{id}` | 修改本人 Token 元数据 |
| `POST` | `/api/personal/agent-tokens/{id}/disable` | 禁用 |
| `POST` | `/api/personal/agent-tokens/{id}/enable` | 启用 |
| `POST` | `/api/personal/agent-tokens/{id}/rotate` | 轮换并一次性返回新 Token |
| `DELETE` | `/api/personal/agent-tokens/{id}` | 吊销/删除 |
| `POST` | `/api/personal/agent-tokens/{id}/test` | 测试连接 |

创建请求不包含 `userId`：

```json
{
  "name": "公司电脑测试助手",
  "clientType": "OTHER",
  "projectIds": ["project-1"],
  "scopes": ["FUNCTIONAL_READ", "BUG_READ"],
  "expireTime": 1790000000000
}
```

创建响应中的 `token` 仅出现一次：

```json
{
  "id": "token-id",
  "displayPrefix": "msat_k7Q2p9…",
  "token": "msat_k7Q2p9_secret",
  "mcpUrl": "https://host/api/mcp",
  "warning": "完整 Token 仅显示一次"
}
```

### 10.2 管理员治理 API

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/admin/agent-tokens` | 查询全局 Token 元数据 |
| `POST` | `/api/admin/agent-tokens/{id}/revoke` | 强制吊销 |
| `GET/PUT` | `/api/admin/agent-policy` | 查询/更新全局策略 |
| `GET` | `/api/admin/agent-audit` | 查询调用审计 |

### 10.3 MCP 与包 API

| 方法 | 路径 | 鉴权 |
|---|---|---|
| `POST/GET/DELETE` | `/api/mcp` | 个人 Token/OAuth |
| `GET` | `/api/personal/agent-package/manifest` | Web 会话 |
| `GET` | `/api/personal/agent-package/skill/download` | Web 会话 |

## 11. 前端改造

### 11.1 页面布局

```text
Agent 与 API
├─ 快速接入
│  ├─ Codex
│  ├─ ChatGPT
│  ├─ Cursor
│  ├─ WorkBuddy
│  └─ 其他 MCP
├─ 我的 Token
│  ├─ 创建
│  ├─ 列表/统计
│  └─ 轮换/禁用/删除
└─ 下载
   └─ AI 技能包
```

### 11.2 关键交互

- 页面不再搜索全局 Token 或用户。
- 创建 Token 不显示关联用户。
- 权限范围全部使用中文业务描述，同时允许普通用户直接选择“全部 Agent 权限”（内部值 `AGENT_ALL`）；选择后展示中文权限清单并二次确认。
- 高风险能力单独分组并展示风险说明。
- 创建 Token 页面删除 Codex 客户端选项，但快速接入和技能包继续保留 Codex 接入说明。
- 创建成功时根据已选客户端即时生成完整、可查看、可一键复制的 MCP 配置；用户自行粘贴到 Agent 中。
- 页面删除“解压后按 INSTALL.md 配置本机 mcp.json……”提示。
- Token 列表展示使用统计和安全状态。
- 真实“连接测试”由持有凭据的客户端调用只读工具；列表仅做元数据和配置检查，两者分别展示结果，不执行写操作。

## 12. 后端改造

### 12.1 管理服务

将当前 `AgentTokenManagementService` 拆为：

- `PersonalAgentTokenService`：所有方法自动使用当前用户并做 ownership 校验；
- `AdminAgentTokenGovernanceService`：只做查询、强制吊销和策略；
- `AgentCredentialService`：解析头、验证秘密、加载用户、计量；
- `AgentAuthorizationService`：计算 RBAC/scope/project/tool 交集。

### 12.2 鉴权

当前 `AgentTokenFilter` 需要调整：

- 支持 Bearer 与 `X-API-Key`；
- 严格判断前缀，不使用模糊 `contains`；
- 验证 `enable/status`、到期时间和用户状态；
- 令牌失败返回统一 401，不继续形成模糊未认证状态；
- 不在 Token 请求完成后误登出原有 Web/JWT 用户；
- 项目上下文使用 request scoped principal，避免线程上下文残留；
- 成功鉴权后异步更新计量；
- 返回标准 `WWW-Authenticate`、错误码和 `Retry-After`。

### 12.3 业务复用

不为 MCP 复制一套不受控的业务规则。MCP Tool handler 应调用现有 Application/Service，并保证：

- 原权限校验仍执行；
- 原项目边界仍执行；
- 原工作流状态校验仍执行；
- 原操作日志/审计仍记录；
- Agent 来源额外写入 `source = MCP`、`tokenId`、`clientType`。

## 13. 审计、限流与安全运营

### 13.1 审计事件

记录：

- Token 创建、修改、轮换、禁用、启用、吊销；
- 鉴权成功/失败；
- MCP Tool 名、调用用户、项目、耗时、结果码；
- 写操作目标资源 ID；
- 客户端类型、来源 IP、User-Agent；
- 管理员强制操作。

严禁记录：

- 完整 Token；
- Authorization/X-API-Key 请求头；
- 附件正文和敏感业务字段的全量副本。

### 13.2 限流

建议维度：

```text
Token + Tool
Token + IP
用户 + 项目
系统全局
```

只读与写操作分别限流；429 返回 `Retry-After`。批量接口额外限制单次条数和负载大小。

### 13.3 幂等

所有创建/批量写 Tool 支持：

```http
Idempotency-Key: <client-generated-id>
```

服务端沿用 Token ID + 固定 operationId + requestId 去重，并保存规范化参数摘要；同键异参冲突，成功重放仍重新授权。副作用与幂等结果同事务。头与参数的键同时存在必须一致；不得改成用户维度造成多个 Agent 相互命中。租约和临时凭证不使用普通结果缓存。

## 14. 实施阶段

| 阶段 | 内容 | 预估 |
|---|---|---:|
| M1 | 个人 Token API、所有权校验、页面自助化、默认最小权限 | 4～6 人日 |
| M2 | Token v2、安全存储、轮换、计量、审计和旧 Token 迁移 | 5～7 人日 |
| M3 | 原生 Streamable HTTP MCP、Tool Registry、协议与鉴权 | 7～10 人日 |
| M4 | AI 技能包重构、Codex/ChatGPT/Cursor/WorkBuddy 远程接入向导 | 3～5 人日 |
| M5 | 管理员治理、限流/幂等、安全测试与多客户端验收 | 5～8 人日 |

以上为初稿估算，不作为当前排期承诺。先完成 §19 的基线、OAuth 和迁移范围核对，再按实际增量重新估算；阶段退出标准见 §19。

优先顺序：

```text
个人自助与所有权修复
  → Token v2
  → 原生远程 MCP
  → 技能包和客户端向导
  → 管理治理与全面验收
```

## 15. 验收标准

### 15.1 个人 Token

- 普通登录用户可以创建自己的 Token，不依赖 `SYSTEM_USER:*` 权限。
- 创建 Token 页面客户端选项中不存在 Codex，保留 ChatGPT、Cursor、WorkBuddy 和其他。
- 权限范围的选项、已选结果和确认信息均使用中文业务描述。
- 请求中伪造 `userId` 无法为他人创建 Token。
- 用户列表只能看到自己的 Token。
- 修改、禁用、轮换、删除他人 Token 均返回 403。
- 完整 Token 只在创建/轮换响应中出现一次。
- 创建成功后同时展示完整 Token 和根据平台地址生成的 MCP 配置，两者均可独立一键复制。
- 复制的 MCP 配置可由用户直接粘贴到对应 Agent；页面不自动修改用户本机配置。
- 创建流程不再展示“解压后按 INSTALL.md 配置本机 mcp.json……”提示或同义提示。
- 数据库、日志、列表和技能包中均无明文 Token。
- 用户权限撤回或账号停用后 Token 立即失效。

### 15.2 MCP

- Codex 可通过远程 MCP 完成 `tools/list` 和至少一个只读 Tool。
- ChatGPT 可通过 HTTPS 远程 MCP 获取 Tools 并执行只读调用。
- Cursor 通过远程 Streamable HTTP MCP 完成连接和只读调用。
- WorkBuddy 按其支持方式完成连接和只读调用。
- `search_projects` 可以通过中文或英文项目名称片段进行模糊查询，并正确分页。
- 项目名称完全匹配、前缀匹配和包含匹配的结果顺序符合设计；相同条件重复查询顺序稳定。
- `search_projects` 只返回“用户可访问项目”和“Token 项目范围”的交集，无法通过关键词枚举无权项目。
- 项目名称中的 `%`、`_`、反斜杠和超长输入不会造成通配符绕过、SQL 注入或异常查询。
- 多个项目匹配时，技能指导 AI 先向用户确认，不直接对首条项目执行写操作。
- 写 Tool 同时受用户 RBAC、Token scope 和项目范围约束。
- Tool annotations 正确区分只读与写入。
- 401、403、429、超时和业务校验错误能被 Agent 理解。

### 15.3 技能包

- zip 中包含完整 `SKILL.md`、工具说明、工作流和各客户端模板。
- zip 不含 Token、用户 ID 和真实项目 ID。
- AI 获得技能包后能先查询项目/模板，再调用业务 Tool。
- 包版本、服务端 Tool manifest 和页面说明一致。

### 15.4 安全

- Token 越权、IDOR、项目越界、scope 绕过测试全部通过。
- 重放/重复提交由幂等机制拦截。
- 日志脱敏测试通过。
- 旧 Token 迁移窗口结束后 v1 验证关闭。
- 远程 MCP 经 HTTPS、WAF/网关和限流保护。

## 16. 已确认决策与剩余待确认项

### 16.1 已确认

1. 普通用户可自主选择 Token 权限，包括 `AGENT_ALL`。
2. 远程优先；本轮保留 Node/stdio，旧退役决定由 §19 的迁移与验收条件替代。
3. 页面拆分为“个人 Agent 与 API”和“管理员 Agent 集成治理”。
4. “管理员 Agent 集成治理”菜单、路由和接口仅系统管理员可见/可访问。
5. 创建 Token 页面删除 Codex 客户端选项，但 Codex MCP 接入能力继续保留。
6. 权限范围统一显示中文业务描述，内部 scope 枚举不作为主文案。
7. Token 创建成功后生成并展示可复制的 MCP 配置，由用户自行配置 Agent。
8. 删除“解压后按 INSTALL.md 配置本机 mcp.json……”提示。

### 16.2 剩余待确认

1. ChatGPT 按 OAuth 路径实施；实际工作区和版本适配结果决定放行，不默认承诺静态 Bearer。
2. Token 默认项目范围：本方案推荐当前项目，用户可显式扩展到本人其他项目。
3. Token 默认能力可以是只读，但用户可切换并选择 `AGENT_ALL`。
4. 旧 Token 迁移期限：建议 30～60 天。

## 17. 推荐最终产品文案

页面标题：

```text
Agent 与 API
```

说明：

```text
创建代表你本人身份的访问 Token，并将 MeterSphere 连接到 Codex、ChatGPT、
Cursor、WorkBuddy 或其他 MCP 客户端。Token 的权限不会超过你在
MeterSphere 中已有的权限。
```

安全提示：

```text
完整 Token 只显示一次。请保存到客户端的秘密存储或环境变量中，
不要写入代码仓库、技能包、聊天消息或截图。
```

技能包说明：

```text
技能包用于教 AI 正确使用 MeterSphere MCP，不包含你的 Token。
下载后可直接交给 AI。AI 应按照包内说明指导你在本地秘密存储中配置 MeterSphere 地址和个人 Token，不要求把完整 Token 发到聊天中，
生成对应客户端的远程 MCP 配置并执行安全的连接验证。
```

## 18. 方案依据

- 本地参考：`个人API密钥模块-设计模式与设计思路-2026-07-30.md`
- 本地参考：`Agent与IDE集成说明.md`
- 当前代码：`backend/services/agent-integration/**`
- 当前代码：`frontend/src/views/setting/system/agentIntegration/**`
- 当前代码：`metersphere-mcp/**`
- OpenAI 官方 Remote MCP API 定义支持通过 `server_url` 连接远程 MCP，并可携带认证信息或自定义 headers；客户端具体开放能力仍应按部署时版本和工作区策略实测。


## 19. 修订后的实施契约与验收要求

### 19.1 需求追踪

以下为目标契约，实施时补充准确文件、迁移编号和测试证据，不凭表格认定接口已实现。

| 需求 | 前端入口及交互 | 后端接口或工具 | 数据/配置 | 验收 |
|---|---|---|---|---|
| 本人凭据 | 个人 Agent 与 API、列表及一次性弹窗 | /api/personal/agent-tokens 创建、列表、修改、启停、删除 | 所有权、范围、revision、配额 | 普通用户、伪造身份、IDOR、并发配额 |
| 轮换 | 所有者轮换、版本冲突及丢失响应提示 | POST /api/personal/agent-tokens/{id}/rotate | 凭据代次、哈希及审计 | 并发、旧秘密失效、无明文重放 |
| 治理 | 系统治理菜单、策略、审计 | /api/admin/agent-tokens、agent-policy、agent-audit | 独立系统权限、持久化策略 | 普通用户拒绝、纯系统管理员可用 |
| 接入检查 | 配置检查与真实连接分开显示 | /{id}/test 检查元数据；客户端实际调用 MCP | 安全存储、支持矩阵 | 不以元数据检查冒充连接成功 |
| 多 Agent | 执行工作台、状态与冲突展示 | MCP 任务、事实及只读状态工具 | Token、generation、attempt、lease | 认领隔离、撤权、迟到回调 |
| 用例库与执行 | 用例库、回收站、确认页、门禁面板 | 用例库能力清单及受管执行链路 | 版本、确认记录、证据、正式同步 | 全部必选能力及门禁约束逐项验收 |
| 技能及客户端 | 下载、中文能力清单及配置 | /api/personal/agent-package/manifest、skill/download | 版本、校验和及能力摘要 | 真实下载制品、安装包与客户端验证 |

页面统一处理 loading、empty、success、validation-error、permission-denied、network-error、server-error。前后端复用安全错误协议，未知错误返回可理解提示和 traceId，不向用户展示 SQL、异常类名或堆栈。

### 19.2 增量基线

以 2026-10-20 当前工作区为文档核对基线，包含未提交的用例库实现；部署版本须另核对。已有 PersonalAgentTokenController、AgentTokenManagementService、AgentTokenService 的 BCrypt 验证、AgentMcpStreamableController/Service、AgentBusinessAccess、AgentOperationExecutor 和 AgentMcpBundleService。

本次重点补齐个人默认权限及管理认证隔离、轮换/迁移、全部入口实时授权、授权版本、同源契约与客户端验收，不要求为了类名而重写业务服务。CASE_MODULE_WRITE_V2 已有显式授权保护，通配 scope 不能覆盖。用例库查询/目录基础已有部分实现，删除恢复、导入导出及完整附件生命周期等仍按原方案登记缺口。

### 19.3 授权版本与项目范围

- 凭据格式版本 credentialVersion 与 authorizationContractVersion 分开。AGENT_ALL 只能覆盖用户已确认的能力版本，新增高风险能力重新授权，不能自动扩张。
- scopes、工具名和 Schema 从真实注册中心生成。CASE_READ、REVIEW_READ 等未定义枚举不能直接发放；现有只读权限及新增 scope 的迁移须逐工具映射。保留原生 metersphere.* 名称，下划线名称仅作为有迁移说明的别名，不能新增漂移目录。
- projectScopeMode 使用 SELECTED / ALL_ACCESSIBLE。SELECTED 必须非空；ALL_ACCESSIBLE 包含未来新获授权项目，创建/扩权时明确说明。历史空数组回填不扩大实时用户权限。
- 默认个人权限通过 SYSTEM_PERSONAL_AI_AGENT_* 及历史角色迁移落实，不授予用户管理权限。关闭新建或调用后，用户仍可查看并吊销本人凭据。
- 工具发现、调用、下载、异步任务及幂等重放均复核当前 Token、用户、scope、策略与资源权限。系统级授权不虚构项目成员要求。

### 19.4 数据、密码验证和迁移

在现有字段上补齐 credential_version、authorization_contract_version、credential_generation、project_scope_mode、revision、轮换和吊销审计，登记真实类型、默认值、唯一索引、回填、迁移编号及兼容读写，不重复添加已有字段。

秘密由安全随机源生成，至少 256 bit 熵。高熵随机 Token 的 SHA-256 摘要不能等同于弱密码离线破解风险；复用现有 BCrypt 时必须定义参数、输入长度及性能预算。认证前 IP/全局限流和请求大小限制先于昂贵验证，认证后再按 Token/工具限流；可信代理列表决定是否采信转发 IP。

旧格式按显式版本验证，v2 失败不回退 v1。迁移公告启用后默认 30 天，审计延期最多 60 天，截止配置自动生效；不能安全归类的旧凭据停用后重新签发。迁移不恢复秘密，回退不重新启用已截止版本或已吊销凭据。

### 19.5 状态、轮换及运行中任务

ENABLED 可调用；DISABLED 可按规则启用；REVOKED 永不可恢复。EXPIRED 按 expire_time 在请求时计算，不允许启用绕过到期。用户停用、删除或权限撤回影响后续调用。

轮换保留内部 Token ID，递增 credential_generation/revision，更换 publicId 与秘密，提交后旧秘密立即失效；首版无重叠期。要求 Web 所有权和 expectedRevision，过期或吊销记录不能轮换复活。并发轮换只允许一项成功，吊销不能被后续轮换覆盖。

轮换响应不缓存明文。响应丢失时查看轮换元数据后显式进行下一代轮换，不能重放旧请求恢复秘密。普通业务幂等记录保留在稳定 Token ID 下；旧代次租约和上传凭证失效，不能由新秘密直接继承。任务进入可解释的待恢复状态，按质量门禁允许的新尝试/重新认领恢复，原事实与审计保留。

异步正式同步仍检查当前业务授权与 attempt，撤权后暂停受影响同步并展示原因；不能因任务曾获授权而继续写入，也不能删除冻结事实。恢复按门禁重新授权，不通过重放旧租约绕过校验。

### 19.6 多 Agent、统计及幂等

默认一 Agent 实例一 Token；client_type 与 User-Agent 仅为声明用途，不能作为可信身份。共享 Token 无法可靠区分实例，审计以用户、Token ID、代次、operationId、task/attempt 为依据。配额包含 Token、用户、项目、全局及并发维度，避免多 Token 绕过。

内容修改使用共享 revision/updateTime，网页、导入和 MCP 均维护；任务认领遵守租约及隔离栅栏，旧回调不能覆盖新 attempt。跨 Token 业务去重若有必要单独定义，不能混入请求重试键。

统计分别记录认证次数、调用次数、成功调用次数和对应时间，异步原子累计且队列有界。统计失败不阻塞业务；安全审计不能视为可丢失统计，应可靠持久化，明确保留期、脱敏及查询权限。

普通业务幂等使用现有事务执行器；定义保留期及过期重试规则，文件/通知/调度采用可靠事件与补偿。租约、短期凭证及完成后的确认沿用质量门禁专用规则；不能缓存旧 claim 凭据并恢复写权限。

### 19.7 协议、OAuth 和真实连接

固定协议版本，覆盖版本协商、Content-Type/Accept、Origin、通知、JSON-RPC ID、非法 Schema、大小/超时与错误分层。首版无状态，不为兼容虚构 session；无 SSE 的 GET 返回 405，DELETE 按所选协议定义，不返回伪关闭成功。上下文在 finally 清理，不能误登出 Web 用户。

管理 API 仅接受 Web/JWT 并检查所有权或系统权限；含个人 Token 的管理请求拒绝，Cookie 请求校验 CSRF。MCP 仅会话不放行，无效 Token 不回退会话；同用户混合凭据仍按 Token 限制，不同用户拒绝；双凭据头拒绝歧义。

OAuth 工作包包括元数据发现、Code + PKCE、注册策略、redirect URI、resource/audience/scope、授权同意、刷新轮换、撤销和错误发现。OAuth 同样受当前用户权限与全局门禁约束，不允许反向管理个人 Token。ChatGPT 未完成实测前标记待支持。

服务端只存哈希，不能凭 Token ID 重建真实连接。列表 test 仅检查状态与配置；真实认证由持有秘密的客户端执行 initialize/tools/list/只读工具。配置中的环境变量语法按客户端实测，不把通用示意 JSON 宣称为标准导入格式。关闭弹窗后仅复制占位配置。

平台外部 MCP 地址使用受信配置，不从任意 Host/转发头生成。技能包禁止要求用户在聊天中发送完整秘密。记录每个客户端的版本、认证方式、读写、文件传输、受管执行和错误恢复证据，不能用只读发现替代端到端验收。

### 19.8 阶段及验收

先做 M0：盘点工作区与部署差异、工具/权限/数据映射、OAuth 可行性及迁移设计，再执行个人管理与认证隔离、生命周期、协议/事务一致性、客户端与技能包、发布验收。管理员策略及限流是开放自助签发的前置条件，不能推迟到全部客户端开放之后。

必须增加以下验收：

1. 同用户多 Token、不同用户、混合会话及双认证头，无串身份和授权回退。
2. 并发创建/轮换/吊销、轮换响应丢失、过期及旧版本截止，不超配额、不复活凭据、不重放秘密。
3. 旧 AGENT_ALL 不获得新增风险能力；纯系统管理员能访问全局门禁，具体业务仍校验资源权限。
4. 撤权/吊销/轮换后旧 lease、文件凭证和幂等重放不能越权；事实保留、暂停及恢复状态真实。
5. 多 Agent 并发修改、认领、同键异参和迟到回调，无重复副作用和旧尝试覆盖。
6. 缺失事实、未提交证据、直接 complete、历史直写、管理员/人工审批均不能绕过门禁。完成状态与正式同步状态分别验证。
7. 执行中删除的取消、等待、停止后删除及预检后目标变化，沿用用户确认与并发协调验收。
8. 真实客户端、实际安装的 Node 包及下载技能包完成必选业务链路；日志、错误、压缩包及列表无秘密泄漏。

执行相关单元测试、后端集成、前端类型检查、只读 lint、生产构建、后端打包、真实数据库迁移、Docker 构建/启动/健康和核心 E2E。实施记录按需求编号登记准确命令、环境、结果、制品摘要及证据；未执行项说明原因、风险及可复现命令，不虚构尚不存在的测试命令。

回滚只允许支持已生成凭据/授权版本及冻结事实协议的兼容制品；否则暂停新签发、新准入或受影响回写并修复前进。严禁恢复项目差异化门禁、直接结果回写、已截止 v1 或吊销秘密。任何必选项缺乏证据，状态为部分完成或阻塞。
