# MCP全接口清单审计与真实调用结果（2026-10-30）

## 结论与边界

测试连接为 `metersphere_test → https://msp.ebcone.net/api/mcp`，未调用生产环境。117个公开工具全部建立审计条目；本轮93次调用覆盖85个不同工具，另2个工具复用同日已保存的上传/提交证据。**不是117个接口全部测试通过**。

|结果|数量|
|---|---:|
|前置阻塞|29|
|本轮正向调用成功|75|
|发现缺陷|8|
|仅负向验证|1|
|源码契约缺陷|1|
|复用已执行证据|2|
|仅空结果验证|1|

“正向调用成功”仅表示已执行的用例得到成功响应，查询可能为空；不代表所有参数、权限边界、故障恢复、业务正确性均通过。正常写入尽量读回验证；没有读回的字段不做持久化正确性承诺。受阻接口不能判定正常。任务相关完整验证缺少启用环境、可执行PERSONAL_MCP任务及租约；本轮没有擅自启用共享环境、领取他人任务或伪造业务事实。

## 需求追踪

|用户要求|实际工作|证据与结论|
|---|---|---|
|逐项检查MCP接口|核对全部117个公开Schema；真实串行执行查询、项目/目录/用例/计划/评审/缺陷写入、文件操作、快照、归档等|逐接口表及evidence.json；未覆盖项逐项列明|
|无法创建用例|名称、项目编号、内部ID创建，批量创建、同键重放及读回|创建并非全部失败；非法等级、模板和空名称校验存在缺陷|
|经常报错|记录所有错误码/traceId，进行有限诊断重试及源码对照|新增导出、附件删除、人工协助契约问题；503并发保护另列|
|每步都要求截图|3原始步骤只用1张用例级图片提交、读回报告和图片|ACCEPTED/PUBLISHED；当前直接执行不要求每步截图，旧策略提示会误导|
|外部截图支持|实际从公共HTTPS导入图片到用例附件、缺陷附件和个人执行证据|三条导入链路成功；8090字节，读回SHA256一致|

## 重点缺陷及修复建议

### F01 用例创建参数校验不完整

实际正常创建成功，但priority=P9可持久化；批量空名称可持久化；显式无效templateId会静默使用默认模板。影响错误数据进入用例、后续展示/导出/执行不一致。

范围：AgentCaseWriteService、BuiltinAgentMcpToolConfig DTO转换入口、模板解析及单/批量公共校验。应统一执行Bean Validation；校验优先级属于模板允许值；显式模板不存在/越权应明确拒绝，只在未传时选择默认模板。批量逐行执行同一校验，返回行号、字段、用户可理解原因。验收必须证明无效行未新增，正常行可读回，同键重放不重复创建。详见前轮报告M02～M09。

### F02 普通用例附件删除无法按公开契约使用

真实已上传并关联附件，调用resourceId+attachmentId+confirm=true后返回AGENT_VALIDATION_ERROR，traceId=`12c12fcf-08dc-44fc-8960-f64ed727e9d1`。

源码：FunctionalAttachmentDeleteToolHandler公开并读取resourceId；AgentGenericMcpWriteService.authorize对所有functional.attachment读取caseId。影响附件无法删除，重复上传后难以清理。

修复：进入通用幂等与鉴权之前规范化resourceId→caseId，仅针对delete兼容旧参数；handler和授权使用同一规范化对象，拒绝冲突双字段；保留旧幂等哈希重放兼容。验证关联ID、fileId两种合法删除、非本项目拒绝、confirm=false拒绝、重放不重复删除。

### F03 缺陷附件删除同类字段冲突

真实URL导入缺陷图片后，按Schema传resourceId，返回AGENT_VALIDATION_ERROR，traceId=`a2c35524-603c-4e02-9c47-0bb412861f14`。BugAttachmentDeleteToolHandler读resourceId，通用授权读bugId。

按F02规范化resourceId→bugId并补权限/重放测试。现有测试图片仍保留，未使用非公开参数绕过校验强删。

### F04 缺陷导出内部错误

自有缺陷使用默认列导出失败，traceId=`e1c6ab48-a7d6-4faf-8c1d-298860267c40`；显式合法num/name列同样失败，traceId=`9df93632-998e-4fd9-9d1c-ecbaa8e6a938`。错误AI_EXECUTION_INTERNAL_ERROR，尚不能仅凭客户端响应确定唯一异常点。

代码链：AgentBugTransferService.export → BugService.export → 字段补充/人员状态映射/Excel ZIP生成 → AgentTempAttachmentService.upload(BUG_EXPORT)。建议先按traceId检索服务端完整堆栈，区分生成失败与上传失败；添加分阶段日志，不记录敏感内容；用同一最小缺陷增加真实导出集成测试。禁止用固定成功或吞异常替代。通过尺度：ZIP可解压、Excel包含目标缺陷和所选列、文件可读、权限及同键重放一致。

### F05 缺陷状态更新响应落后于实际写入

创建→处理中修改后，transition响应仍为创建及旧updateTime；随后transitions查询已为处理中并给出新版本。影响客户端认为修改未生效，误重试或用旧版本再次写入。

范围：AgentBugWriteService.transition、BugWorkflowRuntimeService.transition及其返回DTO组装。事务提交前重新读取最新状态和updateTime，并构建新状态可用转换列表；幂等记录保存最终响应。通过尺度：响应、紧随其后的查询、状态历史一致；旧expectedUpdateTime冲突返回明确业务错误。

### F06 导出任务不允许重试时错误协议错误

对已SUCCEEDED任务调用functional.export.retry，收到内部错误，traceId=`09f6e2e8-a685-496e-b5c7-c232f76b58d1`。源码FunctionalCaseExportTaskService.prepareRetry本应拒绝“只能重试失败、已停止或已过期”，当前安全错误映射丢失业务含义。

修复：使用稳定错误码EXPORT_TASK_NOT_RETRYABLE及安全中文message；AgentSafeErrorMapper明确映射。此处只验证了成功任务的拒绝路径，失败/取消/过期任务正常retry尚未验证，不能认定其正常或失败。

### F07 人工协助创建公开参数不满足handler

human_request.create公开AgentHumanCreateRequest不含taskId/leaseId/leaseToken；BuiltinAgentMcpToolConfig.humanRequestCreateTool必读这3项。按公开Schema的真实请求返回内部错误，traceId=`0ea4cc1f-b6cf-423f-a82d-fc7689eeb679`，未通知其他用户。

修复：专用MCP请求DTO包含任务和租约必填字段，复用现有服务；契约生成测试对照handler所需字段；错误参数使用AGENT_VALIDATION_ERROR。完整验收还需真实任务、有效租约及用户明确授权的通知收件人，验证首次响应胜出与过期/越权处理。

### F08 任务事件批量接口缺少租约密钥（源码确认）

公开execution.events.batch的AgentRunnerEventsRequest只有leaseId/requestId/events，没有leaseToken；handler却调用requiredString(args,"leaseToken")。未获得真实任务，未执行该接口的正向调用，结论限定为源码与公开Schema的确定性契约冲突。

修复：专用请求DTO增加leaseToken必填并保持不落日志；契约测试与真实任务事件顺序、重放、跨租约拒绝集成测试一并补齐。

## 其他契约和体验问题

1. 直接执行的projectId与创建/查询语义不同：创建接受项目名称/编号，执行授权目前只接受内部ID。应共用resolveProjectId，再做项目权限校验；测试名称、编号、内部ID及重名消歧。
2. functional.execution.contract有效规则为CASE、minimumEvidenceCount=1、stepEvidenceRequired=false，但policy原始快照仍含BEFORE_ACTION/AFTER_ACTION。保持历史快照不可变；新增effectiveEvidencePolicy并让客户端说明明确其优先级，旧规则只作历史元数据。
3. bug.attachment.attach要求BUG_DETAIL临时附件，但公开普通prepare只允许CASE_DETAIL/CASE_COMMENT。URL导入可用不代表本地文件attach闭环完整。应增加缺陷专用prepare/complete或安全扩展purpose及权限，不得错误复用CASE_DETAIL附件。
4. bug.case.relate与其alias均成功但不返回relationId；当前工具未找到可安全使用的自有关系ID，解绑正向未验证。应返回创建/既有关系的ID并提供可查询关联列表，保持别名幂等规范一致。
5. 10个独立只读请求并发时2个收到HTTP503“当前任务较多”，traceId=`28879cc5-3cb7-4ff2-b5fc-fe11a4c3ae67`、`b95c8ea8-2be1-4a58-bf88-c3d4365ee38b`；各串行重试一次成功。不能据此断定服务故障或确定阈值，后续需要服务容量/保护配置日志。客户端应有界并发、指数退避；写请求仅原键原载荷重试，禁止改键绕过。
6. query自动目录匹配与filters.keyword范围不同；搜索说明应明确命中依据，不能把目录外结果未返回直接认定数据丢失。
7. README旧提交接口广告与当前禁用状态、幂等冲突提示与“保持原键”指导存在矛盾；文档和安全错误指导需同步。
8. bug.get.updateTime、test_plan.get.associatedCaseCount部分返回null，而其他查询能读到数值；本轮记录字段缺省，不在没有契约要求时直接判全部失败，建议统一DTO并增加响应契约测试。

## 测试数据与留存

仅使用MCP-AUDIT/MCP-FULL-AUDIT隔离数据。新项目`1715457577800769536`（编号100022），成员只增加当前用户本人；执行环境未改动。计划`1715457612160507904`已关联自有用例并验证解除后total=0；评审`1715457612160507914`保留；缺陷`1715458076016975872`及导入缺陷`1715464140510797824`保留以复核。快照已释放。测试URL图片和普通附件因删除接口缺陷仍保留，个人证据URL导入为STAGED，按平台TTL处理。没有伪造可复用业务归档数据。

## 每接口结果（117项）

|工具|判定|调用次数|说明|
|---|---|---:|---|
|artifact_commit|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|artifact_list|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|artifact_prepare|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|asset_catalog_search|本轮正向调用成功|3|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|asset_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|asset_relation_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|asset_version_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_attachment_attach|前置阻塞|0|缺BUG_DETAIL临时上传入口；公开prepare只允许CASE_DETAIL/CASE_COMMENT，正常链路受阻|
|bug_attachment_delete|发现缺陷|1|公开resourceId与通用权限层bugId不一致，真实删除被拒绝|
|bug_attachment_import_url|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_attachments_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_case_relate|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_case_unrelate|前置阻塞|0|关联成功但当前工具响应未提供relationId，未获得可安全解绑的自有关系ID|
|bug_comment_create|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_comments_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_create|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_export|发现缺陷|2|默认列与显式列均返回AI_EXECUTION_INTERNAL_ERROR|
|bug_export_columns|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_history_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_import_commit|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_import_preview|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_relate_case|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_search|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_template_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_transition|发现缺陷|1|修改已落库，响应仍是旧状态；后续transitions读到新状态|
|bug_transitions|本轮正向调用成功|3|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|bug_update|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|business_flow_get|前置阻塞|0|无已发布业务流测试资产|
|case_review_associate_cases|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|case_review_create|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|case_review_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|credential_metadata_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|dataset_metadata_get|前置阻塞|0|无已发布数据集测试资产|
|environment_profile_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|environment_profile_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|execution_cancel|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_checkpoint_create|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_checkpoint_resume|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_complete|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_create|仅负向验证|1|真实BLOCKED预检不能创建：PREFLIGHT_INVALID_EXPIRED_OR_ALREADY_USED；无正常启动验证|
|execution_events|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_events_batch|源码契约缺陷|0|源码确认Schema缺leaseToken而handler必读；未进入真实任务验证|
|execution_fail|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_get|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_pause|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_preflight|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|execution_resolve|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|execution_result_get|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_resume|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_retry|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_search|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|execution_step_result_get|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|execution_step_submit|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|functional_archive_data|前置阻塞|0|无真实可复用业务数据，不编造归档记录|
|functional_archive_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_archive_prepare|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_archive_retry|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_attachment_attach|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_attachment_delete|发现缺陷|1|公开resourceId与通用权限层caseId不一致，真实删除被拒绝|
|functional_attachment_import_url|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_attachment_read|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_attachment_upload_complete|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_attachment_upload_prepare|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_attachments_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_case_batch_create|发现缺陷|1|有效批量成功；空名称可持久化（前轮证据）|
|functional_case_create|发现缺陷|1|有效创建成功；非法等级P9及显式不存在模板未拒绝（前轮证据）|
|functional_case_update|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_comment_create|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_comments_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_execution_contract|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_execution_evidence_import_url|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_execution_evidence_upload|复用已执行证据|0|复用同日定向验收：JPEG上传成功、读回字节相等|
|functional_execution_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_execution_submit|复用已执行证据|0|复用同日定向验收：3步骤1图ACCEPTED/PUBLISHED，同键重试相同submissionId；项目名称/编号仍失败|
|functional_export_download|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_export_retry|发现缺陷|1|成功任务重试应返回业务拒绝，实际映射内部错误；正常可重试任务分支未验证|
|functional_export_start|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_export_status|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_export_stop|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_get|本轮正向调用成功|2|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_history_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_module_create|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_module_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_modules|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_overview_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_search|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_snapshot_create|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_snapshot_page|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_snapshot_release|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_template_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_trash_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|functional_versions_list|本轮正向调用成功|2|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|human_request_create|发现缺陷|1|公开Schema缺taskId/leaseId/leaseToken，handler必读，真实调用内部错误|
|human_request_get|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|human_request_list|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|human_request_respond|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|operation_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|page_object_get|前置阻塞|0|无已发布页面对象测试资产|
|project_create|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|project_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|project_list|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|project_members_add|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|project_search|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|task_claim|仅空结果验证|1|隔离项目无可领任务，返回{}；正常领取未验证|
|task_get|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|task_lease_heartbeat|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|task_release|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|
|task_search|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|test_plan_associate_cases|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|test_plan_cases|本轮正向调用成功|2|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|test_plan_create|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|test_plan_disassociate_cases|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|test_plan_get|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|test_plan_search|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|test_plan_update|本轮正向调用成功|1|至少一条真实成功响应；不是全部参数、权限、并发及业务正确性证明|
|writeback_status_get|前置阻塞|0|无可执行PERSONAL_MCP任务/有效租约；环境均停用，task.search/execution.search为空|

## 文件、验证与未验证项

本轮只新增审计文档/脱敏证据，未修改业务代码、API或数据库迁移。前后端对应关系为已部署MCP→后端工具handler→服务→数据/文件存储；本轮未将浏览器UI测试计入MCP接口通过数。

实际操作：通过已连接工具真实调用93次；Python对functional.export.download返回Base64解码并检查ZIP CRC、13个条目及目标用例名，5460字节、全部检查通过；源码rg/Get-Content定位Schema、授权和错误映射链；完整调用参数、结果、耗时与traceId保存于 `docs/testing/msp-mcp-full-audit-20261030/evidence.json`，Token/图片Base64/邮箱已脱敏。同日复用依据为 `docs/testing/msp-mcp-live-audit-20261030/evidence.json`。

未执行构建、单元/后端集成测试、lint、数据库迁移、Docker构建启动及健康检查：本轮没有实施代码修改，目标是已部署接口验收，且本机资源紧张，不启动第二套环境。修复后应按仓库实际CI执行完整质量门禁。真实任务/租约、发布的页面对象/数据集/业务流、失败导出重试、归档真实复用记录的正向路径尚无运行证据；准备隔离前置数据后，按表逐条调用，不以伪ID校验响应冒充通过。

服务端进一步定位命令（在测试服务器）：

```bash
grep -a -n -B 80 -A 160 -E 'e1c6ab48-a7d6-4faf-8c1d-298860267c40|9df93632-998e-4fd9-9d1c-ecbaa8e6a938|09f6e2e8-a685-496e-b5c7-c232f76b58d1' /data/metersphere/logs/metersphere/info.log > msp-mcp-export-errors.log
```

功能状态：**部分完成（全清单已审计，真实正向覆盖受前置阻塞；已发现缺陷，未修复）**。不得将本报告作为全部117接口可正常运行的证明。
