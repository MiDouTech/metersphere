# MCP 业务契约与接口链路补充审查

日期：2026-10-27  
基线：本地工作区（包含尚未提交的修改），HEAD `104fb6be2645bb47dc27e515254803398ce458ad`  
状态：源码审查完成；问题修复、运行验证与线上部署核对未执行

## 1. 结论与范围

以 [个人执行图片证据方案](personal-execution-case-evidence-implementation-plan-20261027.md) 为业务基线：个人 Agent 每次执行一条用例，只需至少一张本次执行图片，图片不强制绑定步骤；不要求调用者维护内容 Hash、策略 Hash。步骤覆盖、实际结果、结果汇总、文件安全、资源权限和事务一致性继续保留。Runner 的租约、任务及断言协议不在本次放宽范围。

原方案方向正确，已经覆盖上传、提交、旧证据兼容、排序锁行与回滚。不能仅修改证据数量和 Schema：本次继续发现入口校验冲突、执行快照污染、客户端能力落后、错误丢失，以及读图和 ID 契约缺口。只完成原方案中的上传与提交改造，仍不能宣称 MCP 满足业务需求。

本轮检查原生 `/api/mcp` 分发、Node MCP 注册与转发、个人执行、外部图片导入、普通附件、导入联动、通用幂等。没有逐个执行全部 MCP 工具；以下“确认”指源码行为确认，不代表线上复现。

## 2. 需求追踪

| 业务要求 | 消费入口 | 后端与数据链路 | 当前判断 | 必须补充的验收 |
|---|---|---|---|---|
| 一条用例一张图即可提交 | Node caseLibrary、原生 DirectExecutionTools | DirectExecutionService → evidence/submission/attachment | 当前仍逐步骤限制；原方案已覆盖 | 多步骤共用一张图；两个上传入口均通过 |
| 不维护技术 Hash | contract/upload/submit | 定义快照、策略快照、旧协议兼容 | 原方案可行，需改快照构造 | 执行状态变化不影响定义指纹；真实定义更新可识别 |
| 重试一致且仍有权限 | MCP 分发、各写工具 | 通用缓存或 OperationExecutor | 两种机制不一致 | 角色撤销后重放；并发同 key；超时后查询 |
| 图片可查、可读、可长期追溯 | execution.get、attachments.list、页面附件区 | EvidenceStore、文件预览/下载、证据关系表 | 页面已有读取逻辑，MCP 读取能力不足 | 提交→列表→取图→校验内容，删除/权限变化后再读 |
| 导入联动计划和评审 | Node import、原生 import | ImportFollowupCoordinator、关联组 | 后端与 Node 工具集不一致 | 两种客户端均可选择关联组、新建、仅导入、失败重试 |
| 失败原因可理解 | Node client、原生错误转换 | 统一业务错误、日志 traceId | Node 部分路径丢失信息 | 参数、权限、过期、冲突、网络未知结果分别验证 |

以上均为 Agent 工具入口，不要求新增人工录入页面；已有附件页面仅承担查询和显示。

## 3. 确认的问题及修复建议

### M01 / P1：旧的步骤级限制仍在，URL 上传也未例外

证据：`AgentDirectExecutionTools.inputSchema`、`AgentDirectExecutionService.source/upload/submit`；`AgentExternalImageTools.inputSchema`；`metersphere-mcp/src/tools/caseLibrary.ts`。

原生与 Node 均要求 contentHash、stepNum、purpose，提交每步 evidenceIds 必填。服务查询证据还匹配 step_num，质量门禁可能继续要求前后截图。URL 入口最终调用同一个 upload，并非绕过步骤限制。

按原方案一次打通两个上传入口、提交、门禁、查询和旧数据兼容，禁止只放宽客户端表单。验收至少包含：100 个原步骤、一张图、一次上传、一次提交，普通附件区仅发布一份；任一步 ERROR 仍汇总 ERROR。

### M02 / P1：定义快照混入历史执行事实，可能递归增长

源码链路：

`AgentFunctionalCaseSearchService.getById` → `AgentFunctionalCaseExportService.enrichWithSteps` → `FunctionalExecutionViewService` / `AgentDirectExecutionViewProvider.current` → DTO.executionRef/executionFacts。

`AgentDirectExecutionService.source`（188 行起）只清空步骤实际结果及最后执行结果、用户，没有清除新加入的 executionRef/executionFacts。`hash`（198 行）对整个 DTO 计算；`submit`（153 行）又把 source 放入新 facts，且限制 facts 为 1 MiB。

源码可确认：历史执行事实进入定义来源对象。由此推断的运行影响：执行变化可导致定义指纹变化；新快照可包含上次快照，重复执行后增长并触发大小限制。是否已在线上发生仍需复现，不能直接归因此前的 100211。

修复：定义独立的 CaseDefinitionSnapshot，采用字段白名单；仅保留有业务定义意义的用例、步骤、前置条件及必要自定义字段。运行事实、执行引用、用户显示名、目录展示路径等不进入定义指纹和新执行的 case 快照。查询 DTO 继续返回执行事实，不能为了计算 Hash 删除查询功能。算法版本和旧协议迁移沿用原方案。

验收：连续执行至少三次，case 快照不嵌套 executionFacts，大小不随历史次数累计；另一个执行仅更新运行状态时不误报内容冲突；修改原步骤时仍检测变化。

### M03 / P1：requestId 对外声明 128，入口实际最多 64

`caseLibrary.ts:7` 允许 1～128 字符；DirectExecutionTools、ExternalImageTools 与 OperationExecutor 也使用 128。`AgentMcpStreamableService:174` 在分流前统一拒绝超过 64 字符，即使工具绕过通用幂等也无法进入处理器。HTTP 幂等头也有 64 限制。

修复：明确统一长度、字符和空白规则，检查通用幂等表、操作表字段长度后选择兼容的标准；不能只改一处数字。内部自带状态机的工具不应被另一套隐藏规则拒绝。请求体与幂等头并存时仍检查一致性。

验收：长度 1、64、65、128、129；两种客户端、头/body 一致和不一致、同 key 同参数与不同参数。保留“上传与提交是两个操作，可以使用相同 requestId”的业务行为。

### M04 / P1：Node MCP 没有暴露导入后的计划/评审联动

`AgentCaseImportTools:34–64` 已提供 followup.options、followup.retry 和 commit.followup，支持 CREATE_NEW_PAIR / APPEND_EXISTING / IMPORT_ONLY。

但 `caseLibrary.ts:46` 的 commit/stop/retry 仍仅声明 jobId，没有上述 followup 工具；`src/index.ts:55,100` 使用静态工具数组注册，没有动态代理服务端 tools/list。Node 客户端无法按已设计协议完整选择关联组或重试联动。

修复：补 Node 工具声明、参数与返回说明；保留旧调用仅导入的兼容行为，新增客户端主动查询可选关联组并按用户选择提交。加入 Node 与原生工具契约差异检查；不能以服务端 Schema 更新代替客户端发包。

验收：首次导入新建、重复导入指定关联组、多组选取、仅导入、缺 PLAN_WRITE/REVIEW_WRITE、用例导入成功但联动失败后的独立重试。导入成功和联动成功分别展示，不合并为固定“成功”。

### M05 / P1：通用幂等缓存重放没有重新进入资源鉴权，且执行与缓存保存分离

`AgentMcpStreamableService:195–207` 在缓存命中前检查工具 scope，命中后直接返回；`AgentIdempotencyService:29` 只核对 token/tool/request 和参数 Hash。资源级业务鉴权位于处理器内，缓存命中不会再次调用处理器。

影响对象是仍走通用幂等的写工具，例如 BuiltinAgentMcpToolConfig 中 test_plan.create、test_plan.associate_cases；不是所有写接口。DirectExecution 等使用 OperationExecutor 的路径已有重放鉴权，不应重复误报。

风险：同一 Token 对应用户失去项目角色后仍可能获得缓存结果；还需核对全局过滤器是否已覆盖该资源权限，才能认定线上越权。另一个源码风险是 handler.execute 完成后才 save 缓存，两个并发同 key 都可能先执行，最终保存唯一缓存不能撤销已发生的两次业务动作；各业务服务是否另有去重须逐项复核。

修复：写工具在重放前进行当前资源授权；创建目标尚不存在时，核对项目创建权限和目标归属。对并发执行使用原子占位/锁及可恢复状态，或复用已有 OperationExecutor 模式；保持原结果重放，不把重试变成重复创建。不能通过重放业务函数来做权限检查。

验收：当前角色撤销后重放、归档/删除后重放、两个并发同 key 仅一次写入、写成功但响应丢失后重试返回原结果。

### M06 / P1：错误信息在 Node 转发过程中丢失

`metersphere-mcp/src/client.ts:18–35` 的 MsApiError 按 HTTP 状态替换所有业务 message；JSON-RPC 错误在 callNativeTool 中固定按 400 转换。HTTP/ResultHolder 错误进入 catch 后，只返回 MCP_HTTP_ERROR 和 status，原业务 code、traceId 丢失。最外层注册器无法恢复已经丢弃的信息。

修复：建立受控业务错误映射，透传经服务端安全协议确认的 code/message/details/traceId，不透传异常栈或原始数据库错误。保留 RPC 外层码与业务码的区别。网络超时标记“结果未知”，提示以原 requestId 查询或重试；不要自动换 key 重做写操作。

验收：同一种过期、版本冲突、权限拒绝分别通过 HTTP 错误、ResultHolder、JSON-RPC 到达客户端，业务含义和 traceId 一致。未知错误仍使用安全通用提示。

### M07 / P2：普通附件 MCP 只有元数据，未形成图片读取闭环

`AgentResourceAttachmentDTO` 仅有 associationId、fileId、名称、大小等。工具集可检索到 functional.attachments.list / bug.attachments.list，没有找到对应普通附件内容下载工具。execution.get 返回事实与证据清单，不等同于返回图片内容。

正向事实：`FunctionalCaseEvidenceStore.read:11` 与 `FunctionalCaseAttachmentService.downloadPreviewImgById` 已有已发布证据的页面读取链路，不能声称“图片根本没有存储或页面无法读取”。

修复：增加有资源鉴权的附件内容读取能力，可选有期限的受控链接，或有大小上限的 MCP 内容响应；不得返回数据库 payload 或永久公开存储地址。明确 MIME、文件名、大小、校验值、期限、所属 submission。暂存图片默认不公开。

验收：个人 Agent 提交后，仅使用其 MCP Token 就能查清单并读取原图；跨项目、删除关联、失去权限后读取被拒；页面预览继续工作。具体采用链接还是内容响应需结合现有网关与客户端能力实现。

### M08 / P2：attachmentIds 在不同工具中不是同一种 ID

DirectExecutionService.submit:170 返回 attachmentIds=evidenceIds；附件列表则令 id/associationId 为关联记录 ID，fileId 为物理文件 ID。外部图片导入还同时返回 attachmentId（临时上传资源）、fileId、associationId。

这是现有兼容语义，不应直接重命名或把原 attachmentIds 改成另一种 ID。建议新增明确 attachments 数组，分别返回 evidenceId、fileId、associationId、来源和可用操作；旧字段保留并标注语义。拆分“取消附件关联”和“删除底层证据”，后者必须遵守历史证据保留规则。

验收：上传→提交→列表→读取→按允许方式解除关联，客户端无需猜测 ID；错误 ID 类型返回可理解错误。

### M09 / P2：支持的用例形式和步骤上限不透明

DirectExecutionService.source:190–192 要求 1～100 步，且每步 desc/expected 非空；提交 Schema 却允许 1000 条记录。1000 是提交记录上限，并不代表支持 1000 个原用例步骤。没有结构化步骤的文本型用例会被 CASE_CONTENT_MISSING 拒绝。

修复前需明确产品边界：若个人 MCP 需支持文本型用例，应设计文本定义和执行实际结果，不伪造步骤；若暂不支持，contract 返回明确类型及不可执行原因，而不是让 Agent 上传完才失败。原步骤数量、额外/重复记录数量分别声明，配置与工具说明一致。每步预期为空是否允许也应按用例业务规则确定。

验收：文本型、空步骤、缺预期、100/101 个原步骤、1000/1001 条提交记录，失败不得消耗图片或发布部分结果。

### M10 / P2：保留期限与清理规则不能只靠字段推断

submit 写入 submission_evidence.retain_until=30 天；expirePendingEvidence 只清理 submission_id 为空的过期证据；EvidenceStore.retained 对任何已提交证据均返回 true。就这些路径而言，30 天到期并不会删除已发布图片，不能把它表述成现有自动清理周期。

修复：按长期留存要求明确“暂存有效期、最低审计保留期、业务附件可见性、最终清理条件”四件事。历史执行仍引用时不得因解除页面关联误删底层文件；业务删除和恢复后的保留按产品策略处理。补容量监控和清理审计，不能为节省容量私自删除历史证据。

## 4. 对原方案应补充的交付内容

1. 将 M02、M03、M06、M08 列为个人执行改造的共同验收条件，避免只测试最短上传成功路径。
2. 将 M04 独立作为导入 MCP 契约修复，不与截图迁移捆绑发布；M05 逐工具迁移，先检查计划创建/关联等写操作。
3. 将 M07 纳入“证据查询可用”的定义；先对齐 MCP 内容读取与页面读取的权限和关联规则。
4. 明确 M09 支持边界、M10 留存策略；这些业务选择不能通过删校验或增加任意默认值代替。
5. 同步 Node 包、原生工具说明、README、下载技能包及用户配置说明。README:199–207 仍写“不可跨步骤复用证据”，实现更新时必须一同修订；生成技能包是否含旧规则需继续核对模板和实际下载产物。
6. 保留原方案对 100211 的单独排查要求：记录部署版本、工具名、最小参数、完整安全错误码和 traceId，再查服务端日志。本轮没有线上日志，不把版本指纹问题直接认定为该错误根因。

建议实施顺序：M02/M03/M06 契约基础 → M01 用例级证据全链路 → M07/M08 读图与 ID → M04 导入联动 → M05 通用写入重放 → M09/M10 边界与留存验收。M05 涉及权限与重复写入，应在上线前完成风险验证，不能因排序靠后忽略。

## 5. 验证与完成状态

本轮实际执行：`git status --short`、`rg -n` 搜索和 `Get-Content` 阅读上述源码、需求、工具注册、DTO、数据写入及读取路径。没有修改业务文件、未启动服务或容器。新增文件仅本审查文档。

未执行：单元测试、集成测试、类型/lint/生产构建、数据库迁移、镜像构建、容器健康检查和线上端到端。原因是本轮为源码分析，且保持低资源运行；因此不能证明当前部署行为，不能宣称问题已修复。现有工作区还有未提交修改，后续必须固定提交与部署版本再复测。

后续轻量客户端验证命令（从仓库根目录执行，尚未运行）：

```powershell
npm --prefix metersphere-mcp run build
node --test metersphere-mcp/test/directExecution.test.mjs metersphere-mcp/test/externalImages.test.mjs metersphere-mcp/test/caseLibrary.test.mjs
```

这些现有测试不能代替本文新场景，修复时需补测试。后端、数据库、Docker 与 E2E 的准确执行命令应随实施选定的模块、隔离环境及部署配置记录，本轮不提供未经核对的启动命令，也不要求现在启动这些环境。

功能状态：**部分完成（已有实现但存在上述缺口；本轮未实施修复）**。分析交付状态：完成本轮所列范围的源码审查；全工具运行验收、线上根因确认仍未完成。
