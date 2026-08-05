# task012 - P0/P1/P2 - 全链路测试、验收与 AI 执行治理

## 状态

部分完成

## 目标

建立 AI 生成用例改造的测试、验收和执行治理机制，确保每个任务有明确验证证据，未完成、部分完成、未验证内容不得标记为已完成。

## 验收标准

- 【生成用例】位于【用例】和【评审】之间，并拥有独立可访问路由。
- 页面不显示执行用例、Xmind 用例、模块树和回收站。
- 文本需求和已支持的产品方案均可触发 AI 生成。
- 生成结果进入草稿列表，且字段与手工新建功能用例一致。
- 未经用户确认，正式用例表中不得产生新记录。
- 批量保存后，可在【用例】Tab 查询到用例，且 `ai_create=true` 或等效 AI 来源标识正确。
- 上传文件在对象存储和数据库中均有可追溯记录。
- AI 超时、断流、格式错误或解析失败时不产生残缺正式用例。
- 不同项目和用户之间的文件、会话、草稿和凭据不可越权访问。
- 上传、生成、修改、删除、保存和授权均形成审计记录。
- 离开页面再次进入后，可恢复未保存草稿和历史对话。
- 并发、数量、文件大小和 Token 配额均受到限制并有明确提示。
- AI 任务状态、验证证据和剩余事项如实记录。

## 测试范围

- 前端页面测试。
- 后端接口测试。
- 数据库迁移测试。
- 权限越权测试。
- 文件上传与解析测试。
- 模型调用异常测试。
- 草稿入库一致性测试。
- 审计日志测试。
- 回归测试。

## AI 执行治理规则

- 不得以任何理由敷衍、欺瞒用户。
- 不得将未完成内容或部分完成内容标记为完成。
- 接口调用前必须查阅接口文档、类型定义、源代码或可验证示例。
- 范围、参数、影响不明确时必须确认或先验证，不得执行高风险操作。
- 新增接口、服务或数据模型前必须检索现有能力，优先复用。
- 修改后必须执行与风险匹配的编译、单元测试、接口测试或页面验证。
- 无法测试时必须说明原因、未覆盖范围和潜在风险。
- 输出必须区分事实、推断和建议。

## 交付要求

- 每个实现任务完成后需补充：
  - 实际修改文件。
  - 实际实现范围。
  - 未实现内容。
  - 验证命令。
  - 验证结果。
  - 风险与剩余事项。

## 验证要求

- 建议为 P0 闭环新增端到端测试：
  - 输入文本需求。
  - 生成草稿。
  - 编辑草稿。
  - 保存到用例库。
  - 在【用例】Tab 查询正式用例。
  - 校验 AI 来源标识、操作日志和权限隔离。

## 执行记录

### 实际修改文件

- `frontend/src/views/case-management/caseGenerate/index.vue`
  - 补充历史对话、会话 ID、已选来源文档的本地恢复与持久化。
  - 页面切换项目时先恢复本地状态，再加载后端草稿和来源文档。

### 验证命令

- 后端编译：
  - `.\mvnw.cmd -pl backend/framework/domain,backend/framework/sdk,backend/services/system-setting,backend/services/case-management -am -DskipTests compile`
- 前端静态检查：
  - `pnpm.cmd exec eslint src/views/case-management/caseGenerate/index.vue src/views/case-management/caseGenerate/components/DraftDetailForm.vue src/api/modules/case-management/caseGenerate.ts src/models/caseManagement/caseGenerate.ts`
- 前端类型检查：
  - `pnpm.cmd run type:check`
- 路由静态核对：
  - `rg -n "caseGenerate|caseReview|CASE_REVIEW|isTopMenu|featureCase" frontend/src/router/routes/modules/caseManagement.ts`

### 验证结果

- 后端编译：通过。
- 前端 ESLint：通过。
- 前端 TypeScript 类型检查：通过。
- 路由顺序静态核对：通过；当前顺序为：
  - `featureCase`
  - `caseGenerate`
  - `caseReview`

## 验收矩阵

| 验收项 | 结论 | 证据 / 说明 |
| --- | --- | --- |
| 【生成用例】位于【用例】和【评审】之间，并拥有独立可访问路由 | 通过 | `caseManagement.ts` 中 `featureCase` 后为 `caseGenerate`，随后为 `caseReview`；`caseGenerate` 配置 `isTopMenu: true`。 |
| 页面不显示执行用例、Xmind 用例、模块树和回收站 | 静态通过 | `caseGenerate/index.vue` 为独立三栏工作台，未引用执行用例、Xmind、模块树、回收站组件。 |
| 文本需求和已支持的产品方案均可触发 AI 生成 | 部分通过 | 文本生成已接后端；已解析文本类产品方案可作为 `sourceDocumentIds` 注入生成上下文。未做真实 AI 模型联调。 |
| 生成结果进入草稿列表，且字段与手工新建功能用例一致 | 部分通过 | 草稿表与接口覆盖名称、等级、编辑模式、前置条件、步骤、预期结果、标签、自定义字段；未完全复用手工新建表单组件。 |
| 未经用户确认，正式用例表中不得产生新记录 | 静态通过 | 生成接口只写 `functional_case_ai_draft`；正式用例仅由 `/batch-save` 调用 `FunctionalCaseService.addFunctionalCase()` 创建。 |
| 批量保存后，可在【用例】Tab 查询到用例，且 `ai_create=true` | 部分通过 | 后端保存路径设置 `addRequest.setAiCreate(true)`；未在真实页面/数据库环境完成查询验证。 |
| 上传文件在对象存储和数据库中均有可追溯记录 | 部分通过 | 上传复用 `FileMetadataService.transferFile()` 并写 `ai_source_document`；未在真实 MinIO/数据库环境手工验证。 |
| AI 超时、断流、格式错误或解析失败时不产生残缺正式用例 | 部分通过 | 生成失败只更新生成任务失败状态；解析失败只更新文档失败状态；正式用例只在 batch-save 创建。未做超时/断流自动化测试。 |
| 不同项目和用户之间的文件、会话、草稿和凭据不可越权访问 | 部分通过 | 新增接口使用 `@CheckOwner(project)`；草稿/文档查询校验 `projectId + currentUser`。未做越权自动化测试。 |
| 上传、生成、修改、删除、保存和授权均形成审计记录 | 部分通过 | 上传、解析、生成、编辑草稿、删除草稿、批量保存、Provider 测试、Agent 能力声明记录应用日志；未接入统一操作日志表。授权审计未实现。 |
| 离开页面再次进入后，可恢复未保存草稿和历史对话 | 部分通过 | 草稿从后端恢复；历史对话、会话 ID、已选来源文档通过 localStorage 按项目恢复。未从 AI 会话表拉取历史对话。 |
| 并发、数量、文件大小和 Token 配额均受到限制并有明确提示 | 部分通过 | 已实现生成数量上限和单文件 50MB 限制；并发限制、Token 配额、项目总容量未实现。 |
| AI 任务状态、验证证据和剩余事项如实记录 | 通过 | task004-task012 均记录“部分完成 / 未完成 / 未验证”项。 |

## 未完成 / 未验证

- 未新增真实端到端自动化测试。
- 未完成真实 AI 模型联调。
- 未完成真实数据库环境下生成草稿、编辑、批量保存、用例 Tab 查询的闭环验证。
- 未完成 MinIO/文件服务真实上传下载验证。
- 未完成权限越权自动化测试。
- 未完成统一操作日志表审计验证。
- 未实现并发限制、Token 配额、项目总容量限制。
- 未实现 PDF、Office、图片 OCR 自动解析。

## 风险与剩余事项

- 当前全链路主要通过编译、类型检查和源码静态证据验证，缺少真实运行环境接口调用证据。
- Provider Adapter 仍是对现有 AI 能力的包装，未形成多 Provider 独立策略和限流/重试/统计能力。
- 文档解析首期仅支持文本类文件，PDF/Office/OCR 如果被用户上传，会被明确标记解析失败，不能用于生成上下文。
