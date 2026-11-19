# MSP MCP 接口效率与可靠性详细修复方案

日期：2026-11-16  
状态：方案文档，尚未实施。  
范围：接口契约、错误反馈、可观测性、证据上传、提交事务、客户端恢复与验收。本文代码及 SQL 均为设计示意，不能当作已经合并或验证的实现。

## 1. 结论与证据边界

接口侧存在可确认的契约缺口，也存在并发性能敏感路径。尚无原慢任务的请求日志、数据库锁等待与客户端时间线，不能把截图中的 9 分 52 秒全部归因于 MCP。

| 事项 | 已核查事实 | 判断 |
|---|---|---|
| 截图耗时 | 耗时显示在搜索本地 Python 文件关键字的操作上 | 不能当作 HTTP 请求耗时 |
| project.list | 前次只读实测 680/500/491/381 ms | 当前基础查询无分钟级延迟，不代表写接口性能 |
| functional.search | 本次只读实测 655 ms，每页 1 条 | 小规模查询可用 |
| execution.contract | 同一用例实测 643/408/384 ms | 当前契约查询无分钟级延迟 |
| case.update | patch 为 objectAny；业务白名单只有 8 个字段 | 对外 schema 信息不足，确认存在 |
| 参数错误 | 非法 patch 统一抛 AGENT_VALIDATION_ERROR | 有通用恢复提示，但缺少字段级指导 |
| evidence.upload | 同 Token 上传锁 agent_token 行，图片以 BLOB 写库 | 串行点真实存在，是否造成线上瓶颈未证实 |
| execution.submit | 锁用例与证据，同步更新所有未归档关联计划 | 更新放大路径真实存在 |
| AI 图片审核 | 当前直接提交使用结构校验 | 未发现直接提交等待外部模型审核 |
| 超时恢复 | 已有 metersphere.operation.get 和事务内幂等记录 | 应增强现有能力，不重复建设 |

本文只根据当前本地源码和小样本只读实测制定方案。实施前必须核对线上镜像版本、迁移版本、工具清单与源码是否一致。

## 2. 需求追踪表

| ID | 需求 | 用户入口/前端 | 后端实现位置 | 数据或配置 | 验收 |
|---|---|---|---|---|---|
| R1 | 明确可编辑字段 | MCP 消费者，现有用例编辑页面回归 | FunctionalCaseUpdateToolHandler、AgentCaseManageService | 无迁移 | schema 与运行校验一致 |
| R2 | 可修正的错误反馈 | MCP 客户端及既有错误展示 | AgentSafeErrorMapper、AgentApiErrorDTO、MCP 分发 | 新安全错误码与 details | 一次错误指出字段和恢复动作 |
| R3 | 分段耗时可追踪 | 运维日志/指标 | AgentMcpStreamableService、AgentOperationExecutor、上传/提交服务 | 日志、指标与阈值配置 | 慢请求可定位阶段 |
| R4 | 降低上传串行成本 | evidence.upload | AgentDirectExecutionService | 配额表、索引 | 并发配额与吞吐测试 |
| R5 | 高效上传文件 | 新 prepare/complete，旧上传兼容 | 新上传服务、复用文件存储 | 上传意图、存储引用 | 大图、失败、重试、清理 |
| R6 | 控制提交更新范围 | 用例库/计划执行与详情 | publishPersonalExecution、submit | 默认保持既有语义；可选 outbox | 多计划结果一致性 |
| R7 | 超时后安全恢复 | operation.get、客户端 | PersonalOperationGetTool、client.ts | 保留原幂等协议 | 响应丢失仅产生一次写入 |

这些能力有独立 MCP 入口，不需新增业务页面。前端需验证附件、步骤实际结果、用例最近执行结果、计划结果及错误状态展示。若启用异步同步状态，则必须补齐对应前端状态展示后才可交付。

## 3. 文件定位约定

以下路径均相对仓库根目录。定义缩写用于定位，文件修改时使用真实路径。

- A = `backend/services/agent-integration/src/main/java/io/metersphere/agent`
- T = `backend/services/agent-integration/src/test/java/io/metersphere/agent`
- C = `backend/services/case-management/src/main/java/io/metersphere/functional`
- M = `backend/framework/domain/src/main/resources/migration/3.7.2/ddl`
- N = `metersphere-mcp/src`

新增类、配置和测试名称为建议名；迁移编号必须在实施时取当前未占用编号，禁止修改已发布迁移。

## 4. 改点一：编辑 schema 与运行校验统一（P0）

### 4.1 改哪里

- `A/tool/functional/FunctionalCaseUpdateToolHandler.java`：description()、inputSchema()。
- `A/service/AgentCaseManageService.java`：updateOnce() 的 patch 校验。
- `A/tool/AgentMcpToolSchemas.java`：仅在需要公共 schema 构造器时扩展。
- 新增 `A/validation/FunctionalCasePatchContract.java`：共享字段定义及校验描述。
- `N/tools/caseLibrary.ts`：搜索对应编辑工具注册，保持 Node 工具与原生 schema 一致。
- `metersphere-mcp/README.md`：更新允许字段、示例与执行结果提交入口。

### 4.2 怎么改

1. 将 objectAny 替换成带 properties 的对象，additionalProperties=false，minProperties=1。
2. 允许字段固定为 name/moduleId/prerequisite/description/tags/steps/customFields/priority。
3. 字段类型、最大长度、枚举从现有业务校验提取或复用，不重新猜测长度和优先级取值。
4. steps 明确为用例定义步骤，列出 desc/expected 等实际支持字段。执行事实 actualResult/executeResult 不作为编辑结果通道。
5. 对步骤内部未知字段同步校验；历史客户端传入只读字段时，先统计兼容影响，再决定版本化拒绝，不能直接静默丢弃。
6. 对字段缺省、null、空值分别建立测试。保留已支持的清空语义；如现有不同字段语义不一致，明确列出，不在本修复中顺带改变。
7. 在服务端调用 FunctionalCasePatchContract.validate(patch)，保证绕过 MCP schema 直调服务时仍受保护。
8. 描述补充“修改用例定义；实际执行结果通过 execution.contract → evidence.upload → execution.submit 提交”。
9. 核查 MCP 当前是否真正执行 JSON schema 校验。不能因为 tools/list 描述变严格，就假设 callToolInternal 自动校验；业务校验必须保留。

### 4.3 验收

- 8 个字段分别有合法输入测试；空 patch、未知字段、错误类型、嵌套非法字段均拒绝。
- tools/list 返回 schema 与 Node 工具一致。
- 不再需要搜索源代码确定 lastExecuteResult 能否写入。
- 修改定义时版本冲突和权限检查保持有效。

## 5. 改点二：字段级安全错误及恢复提示（P0）

### 5.1 改哪里

- 新增 `A/validation/AgentFieldValidationException.java`，携带受控字段路径和错误类型。
- `A/service/AgentSafeErrorMapper.java`：mapError()、toApiError()、recoveryDetails()、safeMessage()。
- `A/dto/AgentApiErrorDTO.java`：复用既有 details，不新增平行错误协议。
- `A/service/AgentMcpStreamableService.java`：检查所有异常出口及 JSON-RPC error.data 转换。
- `A/controller/AgentExecutionExceptionHandler.java`：保持 REST/MCP 安全错误转换一致。
- `N/client.ts`：MsApiError 和原生工具错误转发，保留 code/details/traceId。

### 5.2 怎么改

当前 toApiError() 会执行 setDetails(recoveryDetails(code))。仅在异常里增加字段详情仍会被覆盖，必须改成安全合并：先映射字段错误，再添加 retryable/nextAction，固定键冲突规则。

新增错误码 CASE_PATCH_FIELD_UNSUPPORTED、CASE_PATCH_TYPE_INVALID；分别返回 invalidFields、allowedFields 或 fieldErrors。字段名来自 schema 白名单；拒绝回显 Base64、自由文本字段值、SQL、原异常 message。

示例：

```json
{
  "code": "CASE_PATCH_FIELD_UNSUPPORTED",
  "message": "部分字段不能通过用例编辑接口修改。",
  "details": {
    "invalidFields": ["lastExecuteResult"],
    "allowedFields": ["name", "moduleId", "prerequisite", "description", "tags", "steps", "customFields", "priority"],
    "recommendedTool": "metersphere.functional.execution.submit",
    "retryable": false,
    "nextAction": "FIX_INPUT"
  },
  "traceId": "服务端生成的关联标识"
}
```

recommendedTool 只在命中执行结果类字段时提供。参数改正后是新的业务请求；写操作结果未知时使用原 requestId 和原参数恢复，两种情况必须分开说明。

检查现有 JSON-RPC 协议，保留当前合法错误封装；不要未经客户端兼容验证把全部错误从 error.data 改成 isError，或反向修改。新增测试覆盖两种已支持错误入口，保证业务失败不被识别成成功。

## 6. 改点三：执行契约与恢复指引（P0）

### 6.1 改哪里

- `A/tool/functional/AgentDirectExecutionTools.java`：contract/upload/submit/get 描述与 schema。
- `A/service/AgentDirectExecutionService.java`：contract() 响应。
- `A/tool/functional/PersonalOperationGetTool.java`：描述与恢复字段。
- `N/tools/maintenance.ts`、`N/tools/caseLibrary.ts`：检查对应注册并同步描述。

### 6.2 怎么改

保留现有 effectiveEvidencePolicy、comparison、validationMode 和 resultSource，新增简短 workflow 数组，列出动作、工具名、必须读取的返回标识。明确 CASE 级 1～10 张图、一张可覆盖原步骤、所有原步骤均需提交、额外和重复步骤保持既有语义。

明确 contentHash/policyHash 的现有可选语义。代码若没有按传入 hash 做并发校验，文档不得宣称传 hash 即保证版本一致；本次必须查清并补测试，若增加校验需独立说明兼容影响。

保留历史 policy 供审计，但增加字段说明：当前有效证据约束以 effectiveEvidencePolicy 为准；不能删除历史策略或重写历史报告。

成功响应继续返回 submissionId、publishStatus、result、attachments。operation.get 已存在，直接引导调用；不要新造同用途查询接口。NOT_FOUND 明确 outcome=UNKNOWN，不能作为未执行证明。

## 7. 改点四：耗时、SQL 与锁等待观测（P0）

### 7.1 改哪里

- `A/service/AgentMcpStreamableService.java`：在 callTool() 外围统一计时，覆盖被拒绝、异常和成功请求。
- `A/service/AgentOperationExecutor.java`：幂等查询、事务执行分段。
- `A/service/AgentDirectExecutionService.java`：上传 decode/quota/store；提交 source/evidence/publish 分段。
- 新增 `A/service/AgentMcpObservation.java`：统一计时与安全日志。
- `backend/app/src/main/resources/logback-spring.xml`：复用 MDC traceId。
- `deploy/conf/metersphere.properties.example`：新增配置示例，默认值需在实施时基于基线确定。

### 7.2 怎么改

使用单调时钟 System.nanoTime() 计算时长，在 finally 中结束观测，不能因业务异常漏记。只在最外层生成一次 traceId，进入 MDC；结束时恢复旧 MDC，避免线程池串号。

指标标签只保留低基数 tool/outcome/errorCode；Token、caseId、requestId、traceId 只作为脱敏日志字段，禁止作为指标标签。禁止记录参数 Map、Base64、上传 URL 签名和凭据。

建议分段：admission、authorization、idempotency、connection_acquire、image_decode、quota、evidence_store、source_read、evidence_check、publish、transaction_total。实际无法可靠拆分的阶段不伪造数值。

SQL 执行时间不等同锁等待。压测同时采集 MySQL performance_schema 的锁等待、连接池 active/pending 与慢 SQL，按数据库连接或时间窗关联 trace。监控只读账号单独配置，禁止生产开启无限制 SQL 参数日志。

可新增 agent.mcp.observation.enabled、slow-threshold-ms、sample-rate；这些是拟新增配置，不能在部署前认为已生效。计时器异常不得改变业务结果。

## 8. 改点五：上传预处理与配额锁（P1）

### 8.1 第一阶段：缩小现有事务成本

位置：`A/service/AgentDirectExecutionService.java` 的 upload()/decodeImage()。

1. 先检查请求大小、Token 和资源权限，再执行图片预处理，避免未授权请求占用解码资源。
2. 将图片解码、格式/尺寸验证、SHA-256 计算放到配额锁之前。当前解码本来就在 Token 行锁之前，不能将此描述成“当前持 Token 锁解码”；优化点是进一步移出写事务并减少重复计算。
3. 定义内部不可变 PreparedEvidence，仅在一次请求内保存校验结果和 bytes；提交写事务前重新校验资源权限及用例定义状态。
4. 当前 request fingerprint 可能涉及大 Base64。先测量开销，再引入版本化 fingerprint；不能直接替换 hash 算法导致旧 requestId 全部冲突。
5. 同次请求复用 SHA-256，去掉返回结果处重复计算。优先命中已完成幂等结果，再做昂贵图片解析；重放仍需验证参数一致和当前权限。

### 8.2 第二阶段：专用配额表

新增 `M/V3.7.2_<新编号>__agent_evidence_quota.sql`、`A/service/AgentEvidenceQuotaService.java`，调整上传和 expirePendingEvidence()。

建议表 agent_evidence_quota：token_id 主键，pending_active_bytes、pending_physical_bytes、version、update_time。使用 BIGINT，非负检查并在服务端校验溢出。active 与 physical 不能混为一项。

初期仍在一个数据库事务内完成：锁配额行 → 校验当前统计 → 写证据 → 更新计数 → 提交。只替换锁对象不一定提高同 Token 吞吐；真正收益来自缩短临界区、避免反复 SUM 和后续存储外移，必须通过压测证明。

过期会使 active 计数自然失真：不能只在插入和删除时增减。建议第一版只缓存 physical，active 继续查询；后续采用显式 EXPIRED 状态和一次性释放事件后再缓存 active，避免过度复杂化。

提交消费证据、过期删除、管理员清理均需要在同一事务内释放对应 pending 计数。锁顺序统一，例如 quota → case → evidence；需全局核查其他写路径，不能只改上传导致死锁环。

### 8.3 索引与迁移

对两条 SUM 查询执行 EXPLAIN，统计单 Token 行数、过期比例与扫描行数。候选索引 `(token_id, submission_id, expires_at)`，是否包含 size_bytes 由实测决定；避免未经评估的宽覆盖索引。

上线先建表/索引，再分批回填和对账，最后启用新配额实现。开关关闭后回到原配额算法，禁止新旧扣减同时执行。回滚保留表，避免丢失恢复信息。

## 9. 改点六：二进制证据上传（P2，可独立延期）

### 9.1 改哪里

- `A/tool/functional/AgentDirectExecutionTools.java`：注册新增 evidence.upload.prepare/complete。
- 新增 `A/service/AgentExecutionEvidenceUploadService.java`。
- 新增上传意图迁移；先评估复用现有 `agent_attachment_upload_intent` 设计与文件服务，不直接挪用缺陷用途权限。
- `C/service/FunctionalCaseEvidenceStore.java`：先核查下载兼容，再扩展证据读取为数据库/文件存储双实现。
- `N/tools/caseLibrary.ts`：提供本地文件流上传辅助，避免图片字节进入模型文本。

### 9.2 协议与状态机

prepare 输入 projectId/caseId/fileName/contentType/sizeBytes/sha256/requestId，返回 uploadId、短期上传地址和必要请求头。凭据绑定主体、用例、大小和用途，禁止任意路径覆盖。

状态：PREPARED → UPLOADED → STAGED → CONSUMED；过期进入 EXPIRED，失败可回收。complete 必须服务端验证真实对象长度、格式、hash 和资源归属，不能相信客户端宣称 UPLOADED。

对象存储不能参加数据库事务：先写临时对象，complete 原子创建 STAGED 记录，失败保留可清理临时对象；提交成功后只改变正式可见性。数据库记录引用失败、对象写成功的孤儿由 TTL 清理。

旧 Base64 上传保留兼容；旧 BLOB 继续下载。不要在一次发布中强制迁移全部历史图片。下载、导出和正式附件关联必须同时支持两类存储。

## 10. 改点七：提交事务与计划同步（P1/P2）

### 10.1 默认修复：保持现有业务语义

位置：`A/service/AgentDirectExecutionService.java` submit()；`C/service/FunctionalCaseService.java` publishPersonalExecution()；`A/service/AgentOperationExecutor.java`。

1. 将纯 JSON 结构、步骤数量和文本长度校验提前；依赖当前原步骤数、版本及证据状态的检查仍在锁内复核。
2. 按固定顺序锁 case 和排序后的 evidence；检查所有相关写路径是否使用相容顺序。
3. 将逐证据 SELECT 改为有界批量查询并锁定，校验返回 ID 集合等于请求去重集合，同时检查归属、过期、未消费和定义指纹。
4. 批量插入 submission_evidence、functional_case_attachment，批量消费证据。任何一条失败，提交和幂等记录全部回滚。
5. 保留正式报告、用例状态和计划同步的原子性，先通过索引及 SQL 优化降低成本。
6. 对计划更新 SQL 执行 EXPLAIN，核查 test_plan_functional_case 的 functional_case_id/test_plan_id 索引。记录实际更新行数；不要任意限制行数后返回全量成功。
7. 当前更新所有未归档计划是既有行为，不在性能补丁中默认改成只更新一个计划。

### 10.2 可选业务升级：显式执行上下文

需要确认个人执行、指定计划执行的传播规则后，新增版本化 targetContext：CASE_LIBRARY 或 TEST_PLAN；指定计划必须验证计划归属、状态、关联记录和权限。

旧客户端缺省保持历史传播策略，新客户端显式选择。将最终作用范围写入报告与返回值，避免用户看到计划变化却无法解释来源。

### 10.3 可选异步传播

若基线证明计划传播占主要耗时且允许最终一致性，再扩展既有 importEffectOutbox 或新增专用事件；先审查现有 outbox 的事务、重试与幂等能力，不能仅调用 enqueue 就认为计划同步已异步化。

事件唯一键包含 submissionId、targetPlanCaseId、eventType。消费者检查执行序列，避免旧事件覆盖新结果；不能只凭处理时间决定新旧。事件与报告同事务落库。

新增 planSyncStatus=PENDING/SUCCEEDED/FAILED/PARTIAL，查询可读失败原因及 traceId。前端必须处理同步中和失败状态。报告 PUBLISHED 只表示核心事实提交，不能表示全部计划已同步。

## 11. 改点八：超时与幂等恢复（P0/P1）

位置：`A/tool/functional/PersonalOperationGetTool.java`、`A/service/AgentOperationExecutor.java`、`N/client.ts`。

当前事务配置 15 秒，Node HTTP 超时 30 秒；都不是整个系统请求的绝对上限。连接池取连接、事务外校验、网关等待和响应读取需单独测量。

先建立超时矩阵：连接获取、SQL/锁等待、事务、服务端请求、反向代理、客户端。按内层先结束并留出错误返回余量配置；禁止直接整体放大超时。

operation.get 已有 NOT_FOUND/UNKNOWN/IN_PROGRESS/SUCCEEDED 等分支。RUNNING 记录在业务事务内插入，事务未提交时查询可能看不到，因此 NOT_FOUND 不能证明请求没开始；无需为“查询不到 RUNNING”单独提前提交幂等记录。

客户端规则：

- 参数错误：修正输入，按明确的新业务请求处理。
- 权限错误：停止自动重试。
- 网络中断/超时：查询 operation.get；找不到仍使用原 requestId、原参数有界重试。
- IDEMPOTENCY_CONFLICT：停止重试，对账原参数，不能换 ID 掩盖冲突。
- REQUEST_IN_PROGRESS：建议带 retryAfterMs 和 QUERY_OPERATION，不无间隔轮询。
- 已成功但证据 EXPIRED：明确需要新上传，不复用失效 evidenceId。

operation.get 只返回授权后的引用，不能返回原参数、Base64 或 lease 凭据。有限重试默认关闭自动写重试，或只对协议明确可恢复的错误启用。

## 12. 前端与客户端对应关系

前端核查入口：`frontend/src/router/routes/modules/caseManagement.ts`、`frontend/src/api/modules/case-management/featureCase.ts`、对应 requrls 与页面调用方。实施时使用 rg 追踪真实详情组件，不能仅按文件名猜测组件路径。

验证：MCP 提交成功 → 详情读取正式报告 → 图片可下载 → 步骤实际结果与总结果一致 → 用例列表更新 → 计划按既有或新协议更新。检查 loading/empty/success/validation-error/permission-denied/network-error/server-error，未知异常只显示安全提示和 traceId。

若本次仅后端优化且响应兼容，不新增页面；若响应新增异步状态，则新增前端类型、状态提示、刷新策略与失败恢复入口，不能返回 PENDING 后仍把页面显示为全部完成。

## 13. 测试设计与执行命令

### 13.1 需要修改/新增的测试

- 扩展 `T/service/AgentMcpStreamableServiceTests.java`、`T/controller/AgentMcpStreamableControllerTests.java`：tools/list、错误详情、trace、失败封装。
- 扩展 `T/service/AgentDirectExecutionTests.java`：覆盖、图片限制、重放、批量证据、过期与越权。
- 扩展 `T/service/AgentDirectExecutionMySqlTests.java`：真实事务回滚、唯一约束及计划同步。
- 新增多连接并发 MySQL 测试。现有测试使用 SingleConnectionDataSource 和临时表，不足以证明跨连接锁竞争；并发测试必须使用独立连接、普通测试表和专用随机 schema。
- 新增字段契约与安全错误测试；新增 quota/上传意图状态机测试。
- `metersphere-mcp/test`：Node schema、错误保留、超时恢复及二进制文件流上传。
- 前端补充报告/附件/计划状态端到端测试，优先扩展现有 `frontend/e2e/mcp-case-library.spec.ts`。

### 13.2 待实施后执行的命令

以下是拟执行命令，不代表本次已运行。PowerShell 中先进入仓库根目录。

```powershell
mvn -pl backend/services/agent-integration -am test '-Dtest=AgentMcpStreamableServiceTests,AgentMcpStreamableControllerTests,AgentDirectExecutionTests' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn -pl backend/services/agent-integration -am test '-Dtest=AgentDirectExecutionMySqlTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dquality.mysql=true'
mvn -pl backend/app -am package -DskipTests
npm --prefix metersphere-mcp test
npm --prefix metersphere-mcp run build
npm --prefix frontend run type:check
npm --prefix frontend exec -- eslint src --ext .vue,.js,.jsx,.ts,.tsx
npm --prefix frontend run build
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml config --quiet
git diff --check
```

真实 MySQL 测试当前连接本机 13326 的专用 quality_verify 数据库，执行前必须按照 `deploy/quality-verify/README.md` 启动测试依赖并核对隔离。不得指向生产库。

Docker build/up/健康检查/迁移命令需按照该 README 和实际 compose 服务填写到实施验收单；本方案未把未经核对的服务名写成可直接执行命令。最终实施报告必须补齐精确命令，缺少即未通过交付。

### 13.3 性能矩阵

| 场景 | 数据规模 | 必查结果 |
|---|---|---|
| 上传基线 | 100 KiB/1 MiB/5 MiB，PNG/JPEG | P50/P95/P99、传输与服务端时间分开 |
| 同 Token 并发 | 并发 1/2/4/8，不同用例 | 配额正确、吞吐、锁等待 |
| 不同 Token 并发 | 相同总负载 | 对比同 Token 串行影响 |
| 提交规模 | 1/10/100 原步骤，1/10 图 | SQL 次数、事务时间、结果一致 |
| 计划规模 | 0/1/10/100 个关联计划 | 扫描/更新行数、锁等待 |
| 故障 | 响应丢失、存储失败、死锁、数据库断连 | 无重复、无假成功、可恢复 |
| 安全 | 无权限、过期、他人证据、超大图片 | 拒绝且不泄漏内部信息 |

指标目标为待基线确认的建议：contract P95 ≤1 秒；常规 1 MiB 图片上传服务端 P95 ≤2 秒；100 步/10 张证据提交服务端 P95 ≤2 秒。固定硬件、网络、数据量和并发，记录错误率及样本数；单次小样本不作为达标证据。

## 14. 发布、兼容和回滚

PR1：契约、错误、追踪与测试。兼容字段语义，部署后刷新 MCP tools/list；Node 客户端同步发布版本。

PR2：上传预处理、索引、配额。迁移先于应用开关；先测试环境再小范围启用。发现额度不一致时关闭新路径，保留证据与对账数据。

PR3：提交批量 SQL 与索引。默认保留全计划同步语义。独立验证事务回滚、计划结果与附件一致性。

PR4（可选）：二进制上传、显式上下文或异步传播。每项独立开关和验收，不能捆绑替换旧协议。回滚前先处理在途上传/事件，保留旧下载兼容，不直接删表或对象。

发布后比较相同业务规模的请求耗时、锁等待、错误率、重试次数与 Agent 工具调用次数。契约修复的效果看无效调用和探索减少，服务端性能优化看真实请求分段，不能混用指标证明效果。

## 15. 完成定义及本次交付记录

每条 R1～R7 应附实现提交、测试结果、运行 trace、迁移记录及验收状态。任何必要项缺失均标为部分完成或阻塞，不把本方案当作已修复。

本次修改：仅新增本文档。未修改前后端业务代码、数据库或部署配置。

本次实际检查：使用 rg/Get-Content 核查上述工具、错误映射、上传/提交/幂等实现、测试文件、package.json、pom.xml 和验证文档；此前只读接口耗时见第 1 节。

未执行：单元/集成测试、lint、构建、迁移、Docker 启动与端到端测试。原因是本次交付为方案文档，无功能实现；风险是设计细节和性能目标尚未经实施验证。实施验证入口见第 13 节。

遗留决策：线上版本一致性、图片与计划规模基线、配额方案最终索引、是否改变计划传播语义、是否允许最终一致性。默认先完成 P0 和保持原语义的 P1，P2 依据运行证据安排。

交付状态：方案文档已整理；功能修复未实施。
