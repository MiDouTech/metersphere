# MSP MCP能力补全与可靠性优化实施方案

日期：2026-10-30。状态：**待实施方案，非实现完成报告**。

## 1. 基线、目标与边界

以修复提交`d8e6f26fe0`及当前仓库实现为代码基线。该提交已合并到chenqifen与main；67项定向测试、修改源文件编译及提交阶段前端类型检查通过，但完整后端构建、真实数据库、容器和部署后全接口复验尚未完成，不能据此认为线上接口均正常。

关联资料：

- [上一版整改与验证限制](msp-mcp-interface-remediation-20261030.md)
- [全接口审计与逐项结果](msp-mcp-full-interface-audit-20261030.md)
- [个人执行、单图证据实测](msp-mcp-live-audit-20261030.md)
- [上一版源码哈希及测试证据](../testing/msp-mcp-remediation-20261030/evidence.json)

本方案目标：让Agent能够发现可用能力、选择正确执行路径、得到可恢复的错误、确认写入结果，并通过MCP完成查询、维护和文件传输闭环。

约束：

1. 个人直接执行继续允许一张有效用例级图片覆盖全部原步骤；不新增逐步截图、前后截图或强制客户端Hash要求。
2. 保留冻结定义和历史事实；结构接受不等于平台独立证明业务执行成功。
3. 复用原权限、业务服务、幂等记录及文件存储；不通过放宽权限、提高并发阈值或自动启用共享环境解决问题。
4. 不新建无关Vue页面。MCP客户端是本轮用户入口；共享后端变化必须回归其已有页面调用。
5. 首期不新增数据库结构。确需索引或历史资源引用迁移时，单独提交有验证证据的迁移，禁止修改已发布迁移文件。
6. 下文标记“拟新增”的工具、字段和类均为设计，不代表已经存在。实际可见工具数量以已部署版本和当前Token权限为准，不能把历史117项清单当作固定总数。

## 2. 已有实现与新增工作的分界

|领域|基线已有能力|本轮新增工作|
|---|---|---|
|创建校验|已补空名称、等级、模板、长度和批量数量校验|统一公开Schema与运行校验，覆盖其余接口，保留行级失败|
|个人执行|契约、上传/URL导入、提交、报告、图片读取、有效证据规则|工具分组、能力发现、轻量读取、跨写操作结果对账|
|幂等|AgentOperationExecutor、资源自有状态、任务租约等多种策略|集中声明策略；增加只读查询适配器，不另建通用写缓存|
|错误|AgentApiErrorDTO及安全错误映射|保留details、统一traceId，补恢复指令及字段错误|
|限流|数据库入口默认并发8；503已带Retry-After:3；Token另有频率限制|统一错误码和客户端退避；暂不改变保护阈值|
|缺陷文件|已补本地上传prepare/complete；导出返回鉴权下载地址|补MCP分块下载和最终文件校验|
|评审|创建、关联、详情|补搜索、用例查询、更新和解除关联|
|缺陷关联|关联响应已新增relationId|补历史关联只读查询，避免用写操作查ID|
|版本发现|initialize的serverInfo.version固定1.0.0|构建身份、契约版本、能力清单与清单Hash|

## 3. 需求追踪表

|编号/优先级|需求与入口|后端修改点|数据/配置|主要验收证据|
|---|---|---|---|---|
|R01/P0|所有tools/call参数与公开Schema一致|AgentMcpStreamableService、BuiltinAgentMcpToolConfig、AgentMcpToolSchemas；新增校验服务|分工具校验模式；无DDL|必填、类型、未知字段、边界、批量部分失败契约测试|
|R02/P0|错误可定位、可恢复|AgentSafeErrorMapper、AgentApiErrorDTO、分发错误响应、RequestTraceFilter、DatabaseWorkloadFilter|恢复策略元数据|HTTP/MCP/日志trace一致；限流和未知异常分类测试|
|R03/P0|主要写操作可查询结果|PersonalOperationGetTool、AgentOperationExecutor；新增受控查询适配器|复用幂等/业务表；必要索引另审|响应丢失、同键重试、旧记录、撤权、跨项目测试|
|R04/P0|知道部署版本与可用能力|initialize、拟新增server.capabilities.get|复用构建信息；缺失返回UNKNOWN|两版本环境对比、Token变化、工具清单Hash测试|
|R05/P1|区分个人执行与任务执行|工具描述、能力元数据、客户端技能包及README|无需启用环境|客户端按推荐路径完成单图个人执行；任务前置不足可理解|
|R06/P1|缺陷导出纯MCP下载闭环|拟新增bug.export.download；复用AgentTempAttachmentService与文件下载服务|原临时文件、TTL和权限|分块拼接、ZIP/XLSX内容、校验和、越权、过期|
|R07/P1|评审完整维护|AgentCaseReviewWriteService、新增查询服务和工具；复用CaseReviewService|原评审/关联表；版本校验|查找、分页、更新冲突、解绑、归档拒绝|
|R08/P1|只读查询历史缺陷关联|拟新增bug.case.list；原关联服务/Mapper|原关联表|授权查询、关系ID、重复关联及解除读回|
|R09/P1|核心响应有具体类型|AgentMcpToolHandler、核心工具outputSchema和DTO|兼容字段不直接删除|真实响应Schema验证、状态枚举、分页与空值测试|
|R10/P1|发现权限与运行要求一致|AgentMcpToolRegistry、工具元数据、AgentScopeAssert|沿用现有Scope展开；多Scope逻辑|allOf/anyOf、旧Token、撤权、不可见资源测试|
|R11/P1|注册、别名与幂等策略集中声明|Registry、StreamableService、GenericMcpWriteService|工具策略清单；无新写缓存|重复注册启动失败、别名同义、所有写工具策略覆盖|
|R12/P2|查询响应轻量、分页稳定|functional.search/get及相关查询DTO/服务|可选view/fields；既有快照复用|旧响应兼容、体积上限、翻页重复/遗漏验证|
|R13/P0起贯穿|可重复全链路验收|契约测试、隔离种子数据、已有CI与runtime验收|独立测试项目和测试资源|逐接口矩阵，未执行项明确列出；不得以缺参拒绝充当正向通过|

## 4. R01：统一参数校验，分批收紧

### 4.1 调用顺序

解析JSON-RPC基本结构 → 识别正式工具及兼容别名 → 校验当前Token可调用范围 → 统一请求标识冲突检查 → 分层参数校验 → 项目/资源归属与业务状态检查 → 原幂等或资源自有协议 → 原业务服务 → 响应转换。

权限检查不能被Schema替代；原键重放仍须重新鉴权。写操作必须在副作用之前完成对应参数校验。

### 4.2 实现方式

- 拟新增`AgentMcpArgumentValidator`，从工具公开的有效Schema读取required、type、enum、长度、数量、范围及additionalProperties等约束；不另手写第二份业务字段表。
- 仓库已引入FGE json-schema-validator 2.2.14，并有CaseGenerationJsonSchemaValidator用例。先验证其对现有Schema关键字的支持并复用；不因方便加入未经兼容验证的新方言关键字，也不直接扩大为依赖升级项目。
- Schema按工具/契约版本预编译缓存；拒绝远程Schema引用，限制嵌套、错误数量和请求大小，避免每次构建工厂或回显大段Base64。
- DTO Bean Validation和业务规则继续生效，禁止把反序列化成功视为校验成功。批量行级错误增加安全路径，例如`cases[2].priority`，不回显敏感字段值。

### 4.3 批量与旧请求兼容

1. 普通接口可完整校验；支持部分成功的批量接口先校验外层结构/数量，再逐行执行item Schema与业务校验。不能因一个空名称而把全部合法行拒绝。
2. 对已提交的幂等请求，按原历史契约和原载荷Hash查找重放，并检查当前权限；不能在升级后用新Schema误拒绝历史成功响应。
3. 首先对核心工具启用enforce；其他已知工具可短期shadow记录字段路径和规则码，不能记录载荷。shadow不是参数校验通过的证据。
4. 完成有效历史样本、旧客户端和公开契约对比后逐工具enforce；不使用永久宽松模式掩盖契约不一致。
5. 验收包含null与缺失的区别、整数/小数、空白名称、未知字段、100/101条、原键原参和原键变参。

## 5. R02：错误、追踪和退避协议

### 5.1 保留现有错误外壳

不立即改变既有JSON-RPC error结构。利用AgentApiErrorDTO已有details，在现有`error.data.code/traceId`旁增加details；修正safeError只保留code/traceId而丢失details的路径。现有客户端仍可读取原字段。

拟定安全错误示例：

```json
{
  "code": "AGENT_RATE_LIMITED",
  "message": "请求较多，请稍后使用原请求标识重试",
  "traceId": "request-trace-id",
  "details": {
    "retryable": true,
    "retryAfterMs": 3000,
    "nextAction": "RETRY_ORIGINAL_REQUEST"
  }
}
```

示例为设计；不能把所有限流时间固定成3000ms。并发入口现有Retry-After为3秒；Token频率限制应按实际窗口给出等待时间或明确保守建议。

|类别|nextAction建议|允许自动重试|
|---|---|---|
|字段格式/必填错误|FIX_INPUT|否|
|权限不足|CHECK_PERMISSION|否；不泄露未授权资源是否存在|
|版本冲突|REFRESH_AND_COMPARE|否；不能自动覆盖他人修改|
|入口繁忙/频率限制|RETRY_ORIGINAL_REQUEST|仅有限退避；写操作须已具备可靠幂等|
|提交结果未知/网络中断|QUERY_OPERATION|先查询；不能直接换键|
|租约失效|QUERY_TASK_STATE|不能重放旧租约写请求|
|未知异常|CONTACT_ADMIN|不承诺自动重试安全|

### 5.2 traceId与业务ID分工

- RequestTraceFilter现已设置MDC traceId、请求属性和X-Request-ID。MCP错误映射优先沿用该ID；只有非请求场景才生成新的ID。
- 响应、过滤器、MCP日志及下游同步调用使用同一请求traceId；异步任务单独保留parentTraceId与任务ID，并负责清理线程上下文。
- requestId是客户端幂等键；taskId/submissionId/uploadId是资源ID。增加resourceType或明确类型说明，不直接改名破坏旧客户端。
- 不将租约密钥、Token、验证码、Base64、原始SQL/堆栈放入错误details。fieldErrors只返回路径、规则和安全说明。

### 5.3 并发策略

首期保留数据库入口并发8和现有Token限流；先将普通英文限流异常改为稳定错误码，统一HTTP与MCP恢复提示。客户端初始并发建议2，可配置降低，读请求有限退避并加随机抖动。2不是服务吞吐承诺，后续以测试环境测量为准。

不得把`/mcp`整体移出前置保护：认证本身也可能访问数据库。后续确需区分重查询、上传和元数据查询时，在保留前置保护的前提下增加非阻塞的工具级准入，不在持有外层名额时无限等待另一层名额。

## 6. R03：扩展写操作结果查询

扩展现有`metersphere.operation.get`，不新建一套通用响应缓存。保留原个人执行参数与返回字段；对新增工具按资源上下文声明所需projectId/caseId等字段。

### 6.1 查询适配器

拟新增`AgentOperationQueryAdapter`注册表，按正式工具名注册：

- 原三种个人执行操作继续使用已有实现；
- 用例单条/批量创建、缺陷创建、计划/评审创建与关联，读取原幂等记录并映射为安全资源引用；
- 导入和导出返回已有jobId/taskId，再由其status接口查询进度；
- 上传prepare/complete按uploadId和现有状态读取；
- 租约、任务领取、步骤提交等保留原任务查询和专用结果查询，不能套通用缓存回放密钥。

查询必须遵循现有正式名、别名、历史幂等键大小写及Hash版本处理，不直接以客户端输入拼接SQL。

### 6.2 结果语义

拟统一`SUCCEEDED、IN_PROGRESS、PARTIAL、FAILED、UNKNOWN`及resourceRefs；资源已过期与操作曾成功分开表达。

关键限制：AgentOperationExecutor的RUNNING记录可能与副作用同事务尚未提交，其他连接未必看得到。因此查不到记录只能返回UNKNOWN，不能保证返回IN_PROGRESS，也不能判定操作未执行。首期不为“显示处理中”拆坏现有原子事务。

只返回状态、资源类型/ID和安全失败摘要，不返回数据库response_json原文。重放前和查询前均检查当前Token、用户、项目和资源权限；成功后撤权也不能继续读取旧结果。旧记录无法安全解析时返回可理解的UNKNOWN/历史对账提示。

验收必须通过测试代理或连接中断模拟“服务端提交成功，客户端丢响应”，再证明查询和原键重试不会重复创建；仅模拟一个NOT_FOUND不算完成。

## 7. R04/R05/R10：版本、能力和工具发现

### 7.1 拟新增metersphere.server.capabilities.get

返回经过授权过滤的：serverVersion、buildCommit、contractVersion、toolManifestHash、当前可用工作流、有效限制和服务器时间。构建信息复用现有构建产物，缺失时返回UNKNOWN，不伪造版本。

不返回Token原文、服务密钥、服务器内部地址、数据库信息或其他用户权限。对于依赖项目的环境/功能前置，仅检查显式选择且可访问的项目，避免能力查询扫描全部项目。

initialize的固定1.0.0改用真实构建版本；toolManifestHash由正式名、公开Schema、策略和可见能力稳定计算，不含密钥。客户端发现Hash变化时重新读取工具清单。当前listChanged=false不直接改为true；只有实现并测试通知机制后才宣告相应能力。

### 7.2 两条执行工作流

|分组|推荐顺序|前置|
|---|---|---|
|PERSONAL_CASE_EXECUTION|search/get → execution.contract → evidence.upload/import_url → execution.submit → operation.get/execution.get/attachment.read|用例权限、FUNCTIONAL_SUBMIT与CASE_ATTACHMENT；不需要任务或租约|
|TASK_EXECUTION|resolve → preflight → create → claim → events/step/artifact → complete或fail|允许的环境、任务、有效租约和对应任务权限|

分组是发现/指导元数据，不改动原工具名，也不以“个人使用”绕过任务协议。个人执行仍遵循effectiveEvidencePolicy，原policy只是保留的历史快照。

### 7.3 多Scope与前置条件

拟增加工具的scopeRequirement（allOf/anyOf）和prerequisites元数据；兼容默认单requiredScope，复用现有旧Token权限展开规则。工具清单与运行鉴权使用同一逻辑。

具备Scope并不代表拥有所有项目权限。发现阶段可说明“仍须当前项目资源权限”，调用阶段仍实时校验。对于不能满足的任务前置，提供安全的配置引导，不能自动启用共享环境。

## 8. R06：缺陷导出文件读取闭环

拟新增`metersphere.bug.export.download`，复用现有导出返回的fileId及AgentTempAttachmentService.requireDownload。每个分块都重新检查当前Token/用户、项目、文件purpose=BUG_EXPORT、TTL及用户导出权限。

建议参数：projectId、fileId、offset（默认0）、length（默认32768，上限65536）；只读接口不强制要求业务幂等键。响应包含offset、nextOffset、eof、sizeBytes、contentType、sha256及该块base64。

- 不接受任意文件路径/任意URL，不把鉴权下载地址当作公开链接。
- 使用存储流的有界读取，不能为每个分块重新把整个文件读入内存；offset边界、EOF和空文件有明确结果。
- 客户端文件传输层拼接文件，不把ZIP全部交给模型当文本；完整下载后验证总长度、SHA-256、ZIP CRC及目标Excel内容。
- 下载失败从已确认offset继续；不能重新创建一次导出掩盖下载故障。
- 第一阶段沿用当前单次最多100条、文件最多5MiB。更大导出以后复用异步任务，不在本轮扩大数据量和内存上限。

新增工具必须接入同一发现/输出契约与权限测试；部署后刷新客户端清单。用例导出现有download接口保留，不重复建设。

## 9. R07/R08：评审和缺陷关联维护闭环

### 9.1 拟新增评审工具

|工具|功能与规则|
|---|---|
|case_review.search|当前项目筛选、名称/ID、状态及分页，稳定排序；不跨项目模糊猜测|
|case_review.cases|返回关联用例、reviewCaseId/associationId及必要状态；步骤按需读取|
|case_review.update|白名单patch、expectedUpdateTime、requestId；复用原修改权限与状态规则|
|case_review.disassociate_cases|按明确的关联ID批量解除，最多100条；不能用库用例ID替代，不删除评审历史或原用例|

实现前核对CaseReviewService现有入口及归档/执行规则；复用权限常量，不为方便把关联权限当作修改权限。旧记录缺版本号时先提供读回版本，不允许无条件覆盖。解除后重算数量和必要统计。

首期不新增评审删除或一键终结工具。已有评审结果能否修改、由谁修改仍按现有业务规则，不直接绕过评审人流程。

### 9.2 拟新增bug.case.list

按projectId、bugId分页返回relationId、caseId、caseType、可访问的用例摘要及计划上下文。复用当前缺陷读取权限和用例可见性；不能通过关联列表暴露无权访问用例的正文。关联写响应继续保留relations，查询则独立只读，不要求为了获取ID再执行关联写入。

端到端验收：找到已有评审 → 关联自有用例 → 分页查询 → 更新版本冲突 → 解除并读回；缺陷关联同样完成历史查询、关联、解除、数量读回。归档和越权路径必须明确拒绝。

## 10. R09/R12：响应契约与轻量读取

先覆盖创建、查询、执行提交、操作查询、文件传输和评审维护，再逐步覆盖其他工具。使用具体响应DTO生成或维护对应outputSchema；未迁移接口不能声明已经强类型化。

注意当前默认outputSchema用oneOf同时包含number和integer：若开始严格输出校验，整数会匹配两个分支。必须先消除重叠分支或改为符合实际语义的兼容Schema，不能直接开启全量严格校验。

兼容原则：

- 保留原结构和字段；新增字段可选，不整体把旧数组改包成分页对象。
- 新增分页接口统一items、current/page、pageSize、total或hasMore，并明确total是否计算；旧接口按原约定保留，不用0冒充未知数量。
- 当前可返回完整详情的接口维持原默认；新增可选view=SUMMARY/DETAIL和允许列表fields，客户端显式选择轻量模式。后续默认行为调整必须提升契约版本。
- summary不包含完整步骤、执行facts和历史policy。执行提交仍读取完整冻结契约，不能用摘要代替定义或删减历史快照。
- 搜索明确区分query自动目录匹配与filters.keyword字段过滤，并返回matchedBy。大范围查询优先复用已有快照机制。
- 附件优先返回元数据；仅显式读取时返回图片或二进制。限制最大响应和嵌套深度，过大时返回继续读取方式，禁止静默截断关键步骤。

输出校验先在契约测试和shadow启用；不能在写事务成功后仅因响应校验失败返回“操作失败”并诱发重复写。业务结果不确定时通过R03对账，修复响应而非重新执行副作用。

## 11. R11：注册与幂等策略单一来源

AgentMcpToolRegistry当前使用Map.put，重名会覆盖。改为重复正式名启动失败；别名集中登记，检查别名冲突、目标存在和权限/幂等语义一致。

拟增加工具策略元数据：

|策略|适用|约束|
|---|---|---|
|READ_ONLY|普通查询/下载|实时权限，不写业务幂等缓存|
|ATOMIC_DB|原数据库原子写入|复用AgentOperationExecutor，副作用与结果同事务|
|RESOURCE_STATE|上传、导入、导出等自有状态机|复用uploadId/jobId/taskId及原重放协议|
|LEASE_STATE|领取、事件、步骤、终态等|每次验证租约，不缓存/回放密钥|
|TEMP_RESOURCE|快照/预览等有界临时资源|明确TTL、释放及是否允许每次新建；不虚称严格幂等|

工具策略驱动发现元数据、requestId要求、分发路径和对账适配器，逐步替代bypassesGenericIdempotency及其他分散名单。迁移时保持原工具名、请求Hash、历史键比较和成功响应不变；不能因归类变动重新执行历史请求。

对涉及网络或文件系统副作用的操作，继续沿用已有资源状态、存储保护与补偿流程；不得简单包装在通用数据库事务里就宣称全部原子。

## 12. 数据、配置、回滚和影响范围

首期只增加代码、DTO、元数据与测试，不默认新增DDL。新增校验模式配置需按工具控制，默认只对经过验证的工具enforce；生产不能依赖测试Mock或硬编码账号。

若发现现有幂等查询缺索引，先用隔离数据EXPLAIN确认，再在`backend/framework/domain/src/main/resources/migration/`中新建未占用编号的迁移。历史response_json按适配器只读解析，不批量改写。若不能可靠恢复资源引用，返回UNKNOWN并列入人工对账，而非猜测。

回滚：保留旧字段和工具；可关闭新增发现/查询入口、回退指定工具校验模式。权限校验、安全修复、租约校验和幂等约束不能作为回滚开关关闭。schema收紧前保留契约版本映射，数据库新增索引可独立回滚，禁止清空原幂等记录。

影响：原生MCP与旧stdio封装、技能包、测试调用器；共享错误映射、缺陷/评审/文件业务可能影响已有REST页面。前端无改动不等于共享服务无页面影响；需回归页面创建/更新、评审维护及两类导出。

## 13. 分阶段实施与退出标准

|阶段|工作包|依赖|退出标准|
|---|---|---|---|
|S0 基线验收|上一版修复完整CI、隔离数据库、容器健康、部署MCP及页面复验|当前修复代码|有源码/镜像身份、实际结果、未通过清单；不以67项单元替代|
|S1 P0可靠性|R01/R02/R03/R04；先完成关键元数据支撑，避免再次散落名单|S0失败项优先修复|核心工具无契约脱节；响应丢失不重复创建；版本与trace可对账|
|S2 P1闭环|R05～R11的文件、评审、关联、权限发现与响应契约|S1|纯MCP完成业务与文件闭环；旧客户端兼容；每个新增写工具有策略与恢复测试|
|S3 P2效率|轻量读取、稳定分页、客户端退避及受控性能评估|S2基线通过|响应规模可测，翻页不丢项；在原保护阈值下运行，无未解释5xx|

每阶段独立提交、验证和发布，不一次重构所有工具。S0不是重新开发已修复功能；S1可以并行准备设计/测试数据，但后续发布必须说明基线尚存缺陷。排期按测试矩阵及环境准备情况评估，不以接口数量直接承诺工期。

## 14. R13：验证与测试数据方案

### 14.1 覆盖标准

每个工具必须有正式名、契约版本、所需Scope、业务权限、前置资源、正向场景、关键负向场景、持久化读回/文件校验、结果证据和源码提交。只返回空结果的查询不能证明非空分页正确；缺参拒绝不能证明正向功能通过。

必须覆盖：参数边界、跨项目/用户/Token、撤权后重放、同键同参、同键变参、并发同键、响应丢失、版本冲突、NULL历史字段、资源过期、租约失效及下载中断。批量必须验证部分成功后原键重试不重复成功行。

### 14.2 隔离数据

使用专用测试组织/项目及当前授权账号，创建可追踪的自有用例、计划、评审、缺陷、文件与关联；任务测试额外准备专用启用环境、无业务副作用页面、Runner及所需已发布资产。

种子资源记录runId、所有者与ID；清理只按该清单处理，不按名称模糊批量删除。不得启用共享环境、领取他人任务或借用生产数据填补29项原阻塞。测试资源未准备好时记录BLOCKED，不算通过。

### 14.3 层次和资源限制

1. 公开Schema及Handler入口契约测试，不能只测内部Service。
2. 真实MySQL映射、事务、幂等和迁移验证；模拟JDBC测试只作补充。
3. 前后端共享路径回归；旧stdio/原生MCP、旧请求样本的兼容测试。
4. 生产构建、Docker镜像、单套隔离环境启动、健康与核心端到端路径。
5. 受控并发测试区分准入拒绝、Token限流、业务异常和数据库故障；保留成功率、延迟、拒绝数和资源峰值。

本机低资源只串行执行定向检查；完整验证使用现有`.github/workflows/msp-remediation-verification.yml`的full范围，再由`.github/workflows/msp-runtime-verification.yml`使用本次成功门禁对应产物验收。不能复用源码不匹配旧镜像。已通过且未受改动影响的前端构建可按仓库现有源码身份规则复用。

候选测试类（均拟新增，实施时随实际拆分调整）：AgentMcpArgumentValidationTests、AgentMcpErrorRecoveryTests、AgentMcpTraceConsistencyTests、AgentOperationQueryMySqlTests、AgentMcpCapabilitiesTests、AgentMcpWorkflowDiscoveryTests、AgentBugExportDownloadTests、AgentCaseReviewLifecycleTests、AgentBugCaseListTests、AgentMcpResponseContractTests、AgentMcpToolPolicyCoverageTests。

实施后最小命令入口（当前文档阶段不执行）：

```powershell
.\mvnw.cmd -B -pl backend/services/agent-integration -am -DskipTests package
.\mvnw.cmd -B -pl backend/services/agent-integration -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

第二条不是无数据库的快速测试命令。必须先配置隔离依赖，并确认所需条件测试实际执行；0失败但关键集成测试被跳过不能作为完整通过。

## 15. 完成定义与交付物

交付物：本方案追踪表对应的实现、公开Schema/示例、旧客户端兼容说明、操作恢复指引、隔离种子及清理清单、逐接口测试矩阵、CI/镜像身份、部署报告与未验证项。

每一项只有在实现、当前权限、数据、错误、恢复和所需运行验证均有证据时才能标记完成。否则标注“已实现待验证”“部分完成”或“阻塞”。工具总数增加、静态编译通过、Mock测试通过或空响应成功，均不能单独证明全平台可用。

本次交付仅为可行实施方案：未修改业务代码，未执行构建、迁移、容器或新增在线测试，也未将上述拟新增能力计作已实现。
