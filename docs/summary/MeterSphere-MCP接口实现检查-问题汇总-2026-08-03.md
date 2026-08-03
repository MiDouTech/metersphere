# MeterSphere MCP 接口实现检查问题汇总

> 检查日期：2026-08-03  
> 检查范围：Agent MCP 远程接口、Token 鉴权、Tool 权限、项目检索、技能包、个人 Agent 页面及相关测试  
> 文档性质：问题汇总与整改验收清单，不包含代码修改

## 一、检查结论

当前 MCP 核心链路已经具备：远程 `/api/mcp`、JSON-RPC 初始化与 Tool 调用、个人 Token、技能包、MCP 配置复制，以及功能用例、缺陷、项目等业务 Tool。

但实现尚不能直接判定为生产验收通过，主要原因是：

1. 存在 Token scope 为空时放行、scope 字符串包含匹配等高风险授权问题；
2. Agent 请求结束时可能注销既有 Web 登录身份；
3. 项目名称模糊检索仅完成基础版本，没有实现方案要求的分页、独立权限、归档参数和稳定排序；
4. MCP 协议行为、真实客户端兼容性及完整自动化测试尚未验证；
5. Tool 注册、旧资源清理和管理员治理仍存在方案差距。

### 1.1 问题统计

| 等级 | 数量 | 说明 |
|---|---:|---|
| P0 | 2 | 可能造成越权或破坏当前用户登录状态，上线前必须修复 |
| P1 | 6 | 影响权限模型、项目检索契约、协议兼容和可扩展性 |
| P2 | 5 | 测试、遗留资源、治理及体验完整性问题 |
| 合计 | 13 | 包含已确认缺陷、方案差距和待验证风险 |

## 二、P0 问题

### MCP-001：Token scope 为空时权限校验直接放行

| 项目 | 内容 |
|---|---|
| 类型 | 已确认缺陷 |
| 位置 | `AgentScopeAssert.assertScope()`、`assertAnyScope()` |
| 现状 | 当 Token 不存在或 `scopes` 为空时直接 `return`，没有拒绝调用 |
| 影响 | 空权限 Token 可能调用需要授权的 Tool；若上游鉴权或上下文注入出现异常，会扩大为未授权访问风险 |
| 期望 | MCP Tool 必须存在有效 Token；scope 为空默认拒绝；仅明确拥有目标 scope 或 `AGENT_ALL` 时放行 |

整改建议：

- 将校验改为默认拒绝；
- 区分“非 MCP 内部调用”和“MCP Token 调用”，不得以 Token 为空作为跳过权限的条件；
- 为 null Token、空字符串、空数组、未知 scope 增加拒绝测试；
- 对每个写 Tool 增加缺少 scope 的 403 契约测试。

验收标准：

- 空 scope Token 调用任意受保护 Tool 均返回明确的 scope 不足错误；
- 未携带 Token 的 `tools/call` 不进入业务 Service；
- `AGENT_ALL` 和精确 scope 仍可正常调用。

### MCP-002：scope 使用字符串包含匹配

| 项目 | 内容 |
|---|---|
| 类型 | 已确认缺陷 |
| 位置 | `AgentScopeAssert.hasScope()` |
| 现状 | 使用 `StringUtils.contains` 判断 `AGENT_ALL`、`FUNCTIONAL_ALL` 和具体 scope |
| 影响 | 非法拼接值、名称前后缀或相似 scope 可能被误判为拥有权限，权限边界不可靠 |
| 期望 | 将数据库 scope 解析成集合后进行枚举白名单和精确匹配 |

整改建议：

- 统一支持历史分隔符后解析为 `Set<String>`；
- 去除空值、重复值并拒绝未知值；
- `AGENT_ALL`、读写覆盖关系通过显式映射实现；
- 长期将 scope 存储统一为 JSON 数组。

验收标准：

- `XAGENT_ALL`、`BUG_WRITE_EXT` 等相似字符串不能获得权限；
- 合法多 scope 组合可以精确识别；
- scope 覆盖矩阵具备完整单元测试。

## 三、P1 问题

### MCP-003：Agent 请求可能注销已有 Web 登录身份

| 项目 | 内容 |
|---|---|
| 类型 | 已确认缺陷 |
| 位置 | `AgentTokenFilter.postHandle()` |
| 现状 | 只要识别为 Agent Token 请求且 Subject 已认证，请求结束就执行 `logout()` |
| 影响 | 请求进入前已经存在的 Web/JWT 登录身份可能被一并注销，影响同浏览器或混合调用场景 |
| 期望 | 只清理由本次 Agent Filter 创建的临时认证身份 |

整改建议：在 request attribute 中记录 Filter 是否执行过登录；`postHandle` 只对本次建立的身份执行清理，同时始终清理 Agent Token 和项目线程上下文。

### MCP-004：项目检索没有真正分页

| 项目 | 内容 |
|---|---|
| 类型 | 方案差距 |
| 位置 | `AgentProjectSearchRequest`、`AgentProjectService.search()` |
| 现状 | 仅支持 `keyword + limit`，直接返回列表 |
| 缺失 | `page`、`pageSize`、`total`、`hasMore` |
| 影响 | 项目数量较多时无法稳定翻页，Agent 无法判断是否还有候选项目 |

整改建议：增加分页请求和统一分页响应；数据库层完成 count、排序和分页，默认每页 20，最大 100。

### MCP-005：项目检索未使用独立 PROJECT_READ scope

| 项目 | 内容 |
|---|---|
| 类型 | 方案差距 |
| 现状 | `metersphere.project.search/list/get` 使用 `FUNCTIONAL_READ` |
| 影响 | 项目读取能力与功能用例读取权限耦合，无法按最小权限独立授权 |
| 期望 | 新增 `PROJECT_READ`，`AGENT_ALL` 显式覆盖该 scope |

兼容建议：迁移期可让 `FUNCTIONAL_READ` 临时覆盖 `PROJECT_READ`，记录废弃提示；新 Token 和前端权限选项使用独立中文“项目查看”权限。

### MCP-006：项目模糊检索契约未完全符合方案

当前实现支持项目名称、内部 ID 和数字编号包含匹配，但仍存在以下差异：

- 没有 `includeArchived` 参数；
- 排序使用创建时间，而不是最近更新时间；
- 排序顺序同时优先编号和内部 ID，与“名称完全匹配 → 名称前缀匹配 → 名称包含匹配”的方案口径不同；
- Tool 名称为 `metersphere.project.search`，方案使用 `search_projects`，尚未冻结正式名称及兼容策略；
- 未返回标准分页结构。

整改建议：冻结 `metersphere.project.search` 为正式名称，并在技能包声明；如必须兼容 `search_projects`，提供别名但共用同一 Handler。明确名称查询是否允许同时匹配 ID/编号。

### MCP-007：项目检索在内存中过滤和排序

| 项目 | 内容 |
|---|---|
| 类型 | 性能与扩展风险 |
| 现状 | 先查询用户全部候选项目，再在 Java Stream 中匹配、排序和 limit |
| 影响 | 项目量大时数据库返回和内存开销增加；无法提供准确、稳定的服务端分页 |
| 期望 | 由 Mapper/SQL 完成权限范围、模糊匹配、排序、count 和分页 |

查询必须参数化，并正确转义 `%`、`_` 和反斜杠，不能拼接原始 SQL。

### MCP-008：MCP JSON-RPC/Streamable HTTP 行为未完整验证

已实现 `POST /api/mcp`，并对 GET 返回 405，但仍需核查：

- JSON-RPC Notification 是否应返回空响应；当前 `notifications/initialized` 会生成响应体；
- Content-Type、Accept、批量请求、无 `id` Notification、错误结构是否符合目标协议版本；
- 是否需要 `Mcp-Session-Id`，以及目标客户端能否接受完全无状态实现；
- ChatGPT、Codex、Cursor、WorkBuddy 的当前版本是否均接受 GET 405 的实现。

验收必须使用真实客户端，而不能只验证 Controller 单元测试。

## 四、P2 问题

### MCP-009：Tool 注册存在两套机制

当前内置 Tool 通过 `switch` 注册和执行，扩展 Tool 通过 `AgentMcpToolHandler` 自动注入，导致：

- Tool 名称、schema、scope 和执行逻辑分散；
- manifest、技能包 Tool 文档和服务端实际 Tool 可能漂移；
- 新增 Tool 容易漏加写操作幂等清单或安全标注。

建议统一到 `McpToolRegistry`，所有 Tool 使用同一 Handler 契约，并由注册中心生成 `tools/list`、技能包清单和测试基线。

### MCP-010：项目检索缺少专用自动化测试

至少需要覆盖：

- 中文、英文、大小写、完全匹配、前缀和包含匹配；
- 多个同名/近似名项目；
- 用户权限与 Token 项目白名单交集；
- 无权限项目不可被枚举；
- 分页稳定性及空结果；
- `%`、`_`、反斜杠、超长输入和 SQL 注入字符；
- 归档、删除和禁用项目边界。

### MCP-011：真实客户端联调尚未形成验收证据

当前代码和技能包包含多客户端模板，但没有形成以下完整证据：

- Codex 连接、`tools/list` 和只读调用；
- ChatGPT 远程 MCP 连接；
- Cursor Streamable HTTP 连接；
- WorkBuddy 连接；
- 四类客户端的 401、403、429、超时和写操作审批行为。

建议形成版本化兼容矩阵，记录客户端版本、配置格式、连接结果和限制。

### MCP-012：仍保留旧 MCP 静态包及旧命名入口

仓库中仍存在 `src/main/resources/mcp/metersphere-mcp-0.3.0.zip`；同时保留 `/api/agent/mcp/download` 等历史命名入口。当前 Controller 实际返回新生成的技能包，但遗留资源可能导致部署、运维或后续开发误用。

建议：

- 确认旧静态 zip 无运行时引用后删除；
- 将下载文案、文件名、API 说明统一称为“AI 技能包”；
- 旧 API 如需兼容，明确标注 deprecated 并转发到个人技能包服务；
- 新用户入口只暴露 `/api/personal/agent-package/skill/download`。

### MCP-013：管理员治理与审计能力尚不完整

当前管理员侧主要覆盖 Token 查询和强制吊销，与治理方案相比仍缺少：

- 全局 Token 策略；
- Tool 启停和风险等级策略；
- 用户/Token/项目/Tool 多维审计查询；
- 异常调用、失败率、`AGENT_ALL` 高频使用告警；
- 限流策略配置和应急暂停；
- 审计数据导出与留存周期。

该问题不阻塞最小只读 MCP 试用，但阻塞完整的企业治理验收。

## 五、已实现且本次检查未发现明显偏差的内容

- 远程 `/api/mcp` 入口及 `initialize`、`ping`、`tools/list`、`tools/call`；
- Bearer Token 和 `X-API-Key` 接入；
- Token V2 公共 ID + BCrypt 密钥校验；
- Token 启用状态、吊销状态和到期时间过滤；
- Token、IP、Tool 维度限流基础能力；
- 写操作幂等基础能力；
- 个人 Token 创建、列表、编辑、启停和删除；
- 创建 Token 页面已删除 Codex 客户端选项；
- 权限选项已使用中文业务文案；
- 创建成功后展示 Token 和 MCP 配置，并分别支持复制；
- 当前页面已删除旧 `INSTALL.md/mcp.json` 提示，使用新的远程 MCP 提示；
- AI 技能包包含 `SKILL.md`、Tool 清单、权限、工作流、故障排查和多客户端示例；
- 技能包不嵌入真实 Token；
- 项目检索已实现用户权限范围和 Token 项目白名单的交集过滤。

上述内容仍需通过构建、自动化测试和真实环境联调才能最终确认可交付。

## 六、测试与构建状态

本次使用项目 Maven Wrapper 执行：

```powershell
.\mvnw.cmd -f backend\pom.xml -pl services\agent-integration -am test -DskipTests=false
```

执行结果：

- 第一次在 120 秒超时；
- 第二次在 300 秒超时；
- 两次均处于首次下载大量 Maven 依赖阶段，尚未进入最终编译/测试结论；
- 因此不能声明当前模块编译通过或测试通过；
- 本次检查未修改业务代码。

后续应在依赖准备完成后重新执行，并保存 Surefire 报告、失败用例和最终 Reactor Summary。

## 七、整改优先顺序

### 第一批：上线阻断项

1. 修复空 scope 放行；
2. scope 改为集合精确匹配；
3. 修复 Agent Filter 注销已有 Web 身份；
4. 增加对应安全回归测试。

### 第二批：项目检索契约

1. 增加 `PROJECT_READ`；
2. 补齐分页响应；
3. 将过滤、排序和分页下沉数据库；
4. 冻结 Tool 名称、参数和结果结构；
5. 补齐模糊检索安全及权限测试。

### 第三批：协议与工程治理

1. 统一 Tool Registry；
2. 完成 JSON-RPC/Streamable HTTP 协议测试；
3. 完成四类真实 Agent 客户端联调；
4. 清理旧 MCP 静态包和旧入口；
5. 补齐管理员治理策略与审计中心。

## 八、总体完成标准

只有同时满足以下条件，MCP 接口才可标记为“完成”：

1. P0 问题全部关闭并完成安全回归；
2. 项目模糊检索满足冻结后的 Tool 契约；
3. 用户 RBAC、Token scope、Token 项目范围和 Tool 策略交集均由测试覆盖；
4. Maven 模块编译和全部测试通过；
5. Codex、ChatGPT、Cursor、WorkBuddy 至少各完成一次真实连接和只读调用；
6. 任一写 Tool 完成权限、审计、幂等和异常回滚验收；
7. 技能包 Tool 清单与服务端 `tools/list` 一致；
8. 不再向新用户交付或引导使用旧 MCP 集成程序包。

