# 2026-08-05 BUG-AI-CASE-GENERATION - AI 生成用例需求实现偏差巡检

## 文档目的

本文用于记录 `MeterSphere_AI生成用例改造方案.md`、`docs/task/ai_case_generation_20260805/` 与当前代码实现之间的偏差。

本文不是完成证明。凡未完成、未验证、部分实现内容均按偏差或风险记录，后续修复前不得标记为已完成。

## 巡检范围

- 需求文档：`docs/summary/MeterSphere_AI生成用例改造方案.md`
- 任务目录：`docs/task/ai_case_generation_20260805/`
- 前端实现：
  - `frontend/src/router/routes/modules/caseManagement.ts`
  - `frontend/src/views/case-management/caseGenerate/index.vue`
  - `frontend/src/views/case-management/caseGenerate/components/DraftDetailForm.vue`
  - `frontend/src/api/modules/case-management/caseGenerate.ts`
  - `frontend/src/models/caseManagement/caseGenerate.ts`
- 后端实现：
  - `backend/services/case-management/src/main/java/io/metersphere/functional/controller/FunctionalCaseAiDraftController.java`
  - `backend/services/case-management/src/main/java/io/metersphere/functional/controller/AiSourceDocumentController.java`
  - `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseAiDraftService.java`
  - `backend/services/case-management/src/main/java/io/metersphere/functional/service/AiSourceDocumentService.java`
  - `backend/services/case-management/src/main/java/io/metersphere/functional/service/AiSourceDocumentParserService.java`
  - `backend/services/system-setting/src/main/java/io/metersphere/system/service/ai/provider/DefaultAiProviderAdapter.java`
  - `backend/services/system-setting/src/main/java/io/metersphere/system/service/ai/provider/AiAgentGatewayService.java`
- 数据库迁移：
  - `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_23__functional_case_ai_generation.sql`
- 权限常量：
  - `backend/framework/sdk/src/main/java/io/metersphere/sdk/constants/PermissionConstants.java`

## 总体结论

当前实现认定为“AI 生成用例 P0 骨架 + Phase1 偏差补齐中”。路由、页面、草稿表、生成接口、草稿 CRUD、批量保存、独立权限、模型下拉、JSON Schema、取消生成、操作日志、草稿分页与模块/模板控件已存在，但与需求文档完整方案仍有差距，尤其是文件 OCR/Office、Provider/OAuth、配额与真实 E2E 验收。

不能标记为完整实现的原因：

- P1/P2：PDF、Office、图片 OCR、多 Provider、OAuth、Agent 网关、额度未实现。
- 前端“连接 AI”完整体验（测试连接、新增连接、重新授权）未实现，当前仅为模型下拉选择。
- 自定义字段模板级校验仍主要依赖正式入库服务。
- 缺少真实数据库、真实 AI、真实页面、权限越权和端到端自动化验证证据。

## 偏差清单

| 编号 | 优先级 | 需求 / 任务要求 | 当前实现事实 | 偏差与影响 | 建议处理 |
| --- | --- | --- | --- | --- | --- |
| AI-CASE-DEV-001 | P0 | `task000` 总览状态应真实反映当前进度。 | `task000-任务总览.md` 仍显示“当前状态：未开始”，但 task001-task012 多数已记录“部分完成”。 | 文档状态不一致，会误导后续验收和交接。 | 修正 task000 状态，汇总每个 task 的真实状态与未完成项。 |
| AI-CASE-DEV-002 | P0 | 【生成用例】应有独立权限扩展点 READ / GENERATE / UPLOAD / SAVE / CONFIG，并支持后续独立配置。 | `PermissionConstants.java` 中 `FUNCTIONAL_CASE_AI_GENERATE` 等常量只是映射旧权限；路由仍使用 `FUNCTIONAL_CASE:READ`。 | 无法按 AI 生成用例能力独立授权；无权限边界无法单独验收。 | 新增独立权限点、权限 DML、菜单权限配置，并调整前后端权限判断。 |
| AI-CASE-DEV-003 | P0 | 页面入口应支持无权限隐藏、路由拦截。 | 前端路由 `caseGenerate` meta.roles 为 `FUNCTIONAL_CASE:READ`；`pathMap.ts` 中权限为空数组。 | 入口权限控制不符合独立 AI 权限设计。 | 完成独立权限落地后，更新路由和路径权限映射。 |
| AI-CASE-DEV-004 | P0 | 工作台左侧应提供“连接 AI”，支持系统模型、个人模型、新增连接、测试连接、重新授权。 | `caseGenerate/index.vue` 仅提供普通输入框填写 `chatModelId`。 | 用户无法按产品方案选择模型连接，使用门槛高，且无法验证连接有效性。 | 接入现有模型选择组件或新增 Provider Selector，支持系统/个人模型选择和连接测试入口。 |
| AI-CASE-DEV-005 | P0 | 文本需求应触发 AI 生成结构化草稿，生成中可停止，失败后可重试。 | 文本生成接口已接入；前端“停止生成”仅把 `generating=false`，没有取消后端请求或模型调用。 | 停止按钮只影响前端状态，不能真正停止生成任务。 | 增加取消请求或任务取消接口；生成任务状态支持 CANCELED。 |
| AI-CASE-DEV-006 | P0 | 草稿详情字段应与手工新建功能用例保持一致。 | `DraftDetailForm.vue` 是简单表单，模块 ID、模板 ID、自定义字段均为文本/JSON 输入。 | 用户体验与手工用例不一致，容易写入错误模块、模板、自定义字段。 | 复用手工新建用例表单组件或抽取公共表单；模块、模板、自定义字段改为真实控件。 |
| AI-CASE-DEV-007 | P0 | 后端应使用 JSON Schema 校验模型输出，失败时执行一次结构修复。 | `FunctionalCaseAiDraftService` 采用 JSON DTO 解析 + Markdown 兜底修复，没有独立 JSON Schema 引擎。 | 字段结构、类型、必填、枚举等校验不够严格，异常输出可能进入草稿。 | 引入 JSON Schema 文件和校验流程；结构修复后再次校验。 |
| AI-CASE-DEV-008 | P0 | 生成结果至少包含来源引用。 | `CaseGenerationResult` 链路中来源引用未完整落入草稿字段。 | 草稿无法追溯到产品方案具体章节或片段。 | 扩展 DTO、草稿表或 JSON 字段，保存 source references。 |
| AI-CASE-DEV-009 | P0 | 自定义字段应满足模板类型、必填、枚举和长度规则。 | AI 草稿层只保存 `customFields` JSON；批量保存时主要依赖 `FunctionalCaseService` 校验。 | 草稿阶段无法提前准确展示模板字段错误。 | 在草稿校验阶段加载模板字段规则并校验。 |
| AI-CASE-DEV-010 | P0 | 重复用例应提示，但不强制阻断用户确认。 | `validateDraft` 发现重复后加入 errors，并把 `validationStatus` 置为 `INVALID`；保存时 INVALID 会被阻断。 | 与任务验收标准冲突：重复提示被变成硬阻断。 | 将重复标记与校验失败拆分；重复只标识 warning，保存时允许用户确认。 |
| AI-CASE-DEV-011 | P0 | 草稿列表应支持分页查询和前端分页。 | 后端 page 接口存在；前端固定 `pageSize: 100`，无分页控件。 | 草稿数量超过 100 后不可完整管理。 | 增加分页控件、页码状态、筛选条件联动。 |
| AI-CASE-DEV-012 | P0 | 批量保存后，应能在【用例】Tab 查询到正式用例，且 `ai_create=true`。 | 后端已调用 `FunctionalCaseService.addFunctionalCase()` 并设置 `aiCreate=true`。 | 代码方向正确，但未完成真实页面/数据库闭环验证。 | 补充接口测试和真实环境验收：保存后在用例 Tab 查询并校验 AI 来源标识。 |
| AI-CASE-DEV-013 | P0 | 生成、编辑、删除、保存操作需审计。 | 当前 `FunctionalCaseAiDraftService.audit()` 只是 `log.info`。 | 不满足平台统一审计、查询、追责要求。 | 接入平台统一操作日志机制。 |
| AI-CASE-DEV-014 | P0 | AI 超时、断流、格式错误时不产生残缺正式用例。 | 正式用例只在 batch-save 创建，方向正确；但未覆盖超时/断流自动化测试。 | 缺少验证证据，不能证明异常场景安全。 | 补模型超时、断流、非法 JSON、保存失败测试。 |
| AI-CASE-DEV-015 | P1 | 产品方案上传后应显示解析状态，并自动触发解析。 | 上传接口和异步解析方法存在；前端上传后 `setTimeout` 刷新一次。 | 状态展示不是实时推送，长耗时解析时体验不可靠。 | 实现 WebSocket/SSE 或稳定轮询机制。 |
| AI-CASE-DEV-016 | P1 | 首期自动解析 PDF、DOC/DOCX、XLS/XLSX、PPT/PPTX、TXT、MD、HTML、JSON、XML、YAML 和常见图片。 | `AiSourceDocumentParserService` 仅支持文本类：TXT、MD、HTML、JSON、XML、YAML、`text/*`；PDF、Office、图片返回未配置。 | 与需求差距大；上传 PDF/Office/图片无法用于生成上下文。 | 接入 PDF/Office 解析和 OCR，未支持格式仅允许存档且明确提示。 |
| AI-CASE-DEV-017 | P1 | 上传文件应“均可存档，可解析范围另行说明”。 | `AiSourceDocumentService.validateFile()` 对不在白名单内的扩展名直接拒绝。 | 与“不可解析但可留档”的边界不完全一致。 | 区分“允许存档”和“允许自动解析”两套白名单。 |
| AI-CASE-DEV-018 | P1 | 文件安全应包含扩展名与 MIME 双重校验、数量、总容量、压缩包防护、病毒扫描预留。 | 已有扩展名限制、50MB 限制、部分 MIME 禁止；无数量/总容量、压缩包治理、病毒扫描真实接口。 | 文件滥用和安全治理不足。 | 增加会话文件数、项目容量、压缩包限制、病毒扫描 SPI。 |
| AI-CASE-DEV-019 | P1 | 解析结果应保存完整文本、章节索引、摘要。 | 解析结果 JSON 存文件服务；摘要和 sectionIndex 入库；章节为固定长度切片。 | 章节识别不是语义章节，引用质量有限。 | 增加标题/目录/段落结构识别。 |
| AI-CASE-DEV-020 | P1 | Provider Adapter 应统一能力声明、鉴权、请求转换、流式、超时、重试、限流、错误码和用量统计。 | `DefaultAiProviderAdapter` 只包装 `SystemAIConfigService` 和 `AiChatBaseService`；能力声明中 stream/oauth/gateway 都为 false。 | 不满足统一 Provider 架构目标。 | 抽象真实 Provider 策略，实现流式、限流、重试、错误码、Token 统计。 |
| AI-CASE-DEV-021 | P1 | 支持系统模型、个人模型、默认模型回退。 | 生成链路复用 `AiChatBaseService.getModule()`；未见生成用例页面上的系统/个人模型选择和默认回退逻辑。 | 页面无法明确选择模型来源；失败回退能力缺失。 | 前端接入模型源列表；后端明确回退策略。 |
| AI-CASE-DEV-022 | P2 | OAuth 服务应支持授权、回调、刷新、撤销。 | 未实现 OAuth 授权跳转、回调、token 加密存储、刷新、撤销。 | OAuth 能力未落地。 | 后续按 Provider 分阶段实现。 |
| AI-CASE-DEV-023 | P2 | Agent 网关应支持 MCP 或自定义协议适配。 | `AiAgentGatewayService` 仅返回 `configured=false` 占位能力声明。 | Agent 网关未实际接入。 | 明确企业网关协议后实现真实适配。 |
| AI-CASE-DEV-024 | P2 | 额度统计应支持项目、用户、Provider 维度。 | 未发现相关额度统计表、服务或调用记录。 | 无成本控制能力。 | 增加用量记录表和限额校验。 |
| AI-CASE-DEV-025 | P0 | 测试与验收要求包含迁移、接口、权限越权、端到端验证。 | 当前任务文档记录编译/类型检查为主；未发现新增 AI 草稿/解析/入库专项自动化测试。 | 缺少上线可信证据。 | 补 P0 端到端测试：文本生成、编辑、保存、用例列表查询、权限隔离。 |

## 代码证据摘要

### 路由和菜单

- `frontend/src/router/routes/modules/caseManagement.ts`
  - `/case-management/caseGenerate` 已存在。
  - `meta.roles` 当前为 `FUNCTIONAL_CASE:READ`，不是独立 AI 权限。
- `frontend/src/config/pathMap.ts`
  - `CASE_MANAGEMENT_CASE_GENERATE` 已存在。
  - `permission: []`，没有绑定 AI 权限。

### 权限

- `backend/framework/sdk/src/main/java/io/metersphere/sdk/constants/PermissionConstants.java`
  - `FUNCTIONAL_CASE_AI_GENERATE = FUNCTIONAL_CASE_READ_ADD`
  - `FUNCTIONAL_CASE_AI_UPLOAD = FUNCTIONAL_CASE_READ_IMPORT`
  - `FUNCTIONAL_CASE_AI_SAVE = FUNCTIONAL_CASE_READ_ADD`
  - `FUNCTIONAL_CASE_AI_CONFIG = FUNCTIONAL_CASE_READ_UPDATE`
- 数据库迁移目录中仅发现 AI 三张业务表 DDL，未发现 AI 生成用例独立权限 DML。

### 生成与草稿

- `FunctionalCaseAiDraftController`
  - 已有 `/functional/case/ai/draft/generation/structured`
  - 已有 `/page`、`/update`、`/delete`、`/regenerate`、`/batch-save`
- `FunctionalCaseAiDraftService`
  - 已创建生成任务和草稿。
  - 解析 AI 输出为 DTO，失败后尝试旧 Markdown 格式修复。
  - 未使用独立 JSON Schema 引擎。
  - 草稿保存为正式用例时使用 `FunctionalCaseService.addFunctionalCase()`，方向正确。

### 前端工作台

- `caseGenerate/index.vue`
  - 三栏布局存在。
  - 模型选择为手输 `chatModelId`。
  - 草稿列表无分页控件。
  - 停止生成只修改前端 `generating` 状态。
- `DraftDetailForm.vue`
  - 详情表单为基础输入框。
  - 模块、模板、自定义字段不是业务选择控件。
  - 未复用手工新建用例表单组件。

### 文件上传和解析

- `AiSourceDocumentService`
  - 支持上传、分页、重试、删除、下载。
  - 单文件限制 50MB。
  - 非白名单扩展名直接拒绝。
- `AiSourceDocumentParserService`
  - 支持文本类解析。
  - PDF、Office、图片 OCR 未实现，返回未配置错误。
  - 解析状态异步更新，但前端未实现 WebSocket/SSE 实时推送。

### Provider / OAuth / Agent

- `DefaultAiProviderAdapter`
  - 仅默认包装现有模型服务。
  - `streamSupported=false`、`oauthSupported=false`、`agentGatewaySupported=false`。
- `AiAgentGatewayService`
  - 返回 `configured=false`。
  - 未实现真实 MCP 或自定义协议。

## 建议修复顺序

### 第一阶段：P0 闭环验收修复（2026-08-05 已代码补齐，待真实环境验收）

1. ~~修正任务总览状态~~（已更新为部分完成 + 分任务缺口）。
2. ~~落地独立 AI 生成用例权限点、菜单权限和前后端权限判断~~（常量/permission.json/DML/路由/pathMap/接口注解已改；待无权限账号验证）。
3. ~~前端接入真实模型选择~~（复用 `aiStore.aiSourceNameList` 下拉；连接测试/新增连接仍属 P1）。
4. ~~草稿详情对齐手工用例控件~~（模块树、模板下拉、标签输入已接；步骤编辑器与自定义字段表单仍简化）。
5. ~~引入 JSON Schema 校验和结构修复后复验~~（`case-generation-result.json` + `CaseGenerationJsonSchemaValidator`）。
6. ~~修复“重复提示变硬阻断”~~（duplicate 仅警告，READY 仍可保存）。
7. ~~增加草稿分页~~。
8. 补齐 P0 端到端验证：生成、编辑、保存、正式用例查询、AI 来源标识、权限隔离（**仍未完成**）。

附加已补：
- 停止生成：前端取消请求 + `POST /generation/cancel`，任务状态 `CANCELED`。
- 审计：接入 `OperationLogService` + `CASE_MANAGEMENT_CASE_GENERATE`。
- 来源引用：草稿表新增 `source_references`，DTO/生成链路贯通。

### 第二阶段：P1 文件解析与 Provider（未开始）

### 第三阶段：P2 OAuth / Agent 网关（未开始）

## 当前不可宣称完成的内容

- 不能宣称 AI 生成用例需求完整实现。
- 不能宣称 PDF、Office、图片 OCR 已支持。
- 不能宣称 Provider Adapter、多 Provider、OAuth、Agent 网关已完成。
- 不能宣称已通过真实端到端验收（自动化测试与人工验收证据仍缺）。
- 独立权限代码已落地，但缺角色配置与无权限账号实测证据。

