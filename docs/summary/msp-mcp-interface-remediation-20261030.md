# MCP接口整改追踪（2026-10-30）

依据：msp-mcp-full-interface-audit-20261030.md及同日定向报告。MCP客户端是用户入口，本轮不改无关前端，不新增数据库结构。

|需求/验收|入口与后端实现|数据/配置|验证计划|状态|
|---|---|---|---|---|
|F01 创建拒绝空名称、非法等级和无效/越权模板；批量返回行级错误|case.create/batch_create → AgentCaseWriteService、模板校验；更新接口同步校验|沿用用例表|正常、非法、混合行验证；无效行不调用持久化|代码已实现、定向测试通过；待部署复验|
|F02/F03 附件删除契约与授权一致|attachment.delete → AgentGenericMcpWriteService → 原附件服务|保持原参数和幂等哈希|公开resourceId、权限、冲突字段|代码已实现、定向测试通过；真实删除/重放待复验|
|F04 缺陷导出|bug.export → AgentBugTransferService → BugService/ExtBugCustomFieldMapper → 文件存储|显式MyBatis resultMap；无DDL|真实XML映射、字段保留和异常映射拒绝|日志已定位、代码及映射测试通过；真实ZIP/存储待复验|
|F05 状态响应新鲜|bug.transition → BugWorkflowRuntimeService|原状态/历史表；清理事务内MyBatis缓存|缓存失效后返回新状态/版本|代码已实现、单元回归通过；真实DB事务待复验|
|F06 重试拒绝可理解|export.retry → FunctionalCaseExportTaskService → AgentSafeErrorMapper|稳定错误码EXPORT_TASK_NOT_RETRYABLE|安全错误映射测试|代码已实现、错误映射测试通过；失败任务重试待复验|
|F07/F08 公开Schema覆盖handler所需参数|human_request.create/events.batch → leasedSchema补全原DTO Schema|无|Schema必填、缺参、正确透传|代码已实现、契约测试通过；真实租约待复验|
|直接执行项目标识统一|execution.*、operation.get、attachment.read、外部图片 → 项目解析与当前权限|原幂等记录；内部ID参数兼容|名称/编号/ID解析、查询权限、图片读取|代码已实现、单元/契约测试通过；持久重放待复验|
|有效证据规则清晰|execution.contract → effectiveEvidencePolicy|保留原历史policy和Hash|单图规则、历史快照不变|代码已实现、契约测试通过|
|缺陷本地附件上传闭环|新增bug.attachment.upload.prepare/complete → UploadIntentService → 原attach|沿用intent/purpose和配额|BUG_DETAIL/BUG_COMMENT、独立BUG_ATTACHMENT权限、未授权不预留|代码已实现、契约/权限测试通过；存储闭环待复验|
|关联后可解除|bug.case.relate/alias → 返回relations|原关联表|返回relationId查询使用绑定参数、限定当前缺陷和请求用例|代码已实现、编译通过；关联/解绑实际闭环待复验|
|客户端指导一致|MCP README/工具说明/错误提示|无|移除旧直写入口广告，补规则优先级、并发与原键重试说明|文档已更新；未做压测，不放宽保护阈值|
|补充：用例导出NULL字段|页面导出及MCP export → FunctionalCaseFileService|保留空字段语义，不默认P0|真实字段构建器、NULL/空串/普通值、重复冲突、现有导出映射/权限回归|日志已定位、代码与测试通过；真实文件待复验|
|补充：详情响应字段|bug.get补updateTime；test_plan.get补associatedCaseCount|读取原表，沿用原权限|编译及计划详情计数测试|代码已实现；部署后读回确认|

异常流程：不放宽权限，不吞异常，不改变失败为成功；参数错误返回安全稳定码；副作用仍由现有幂等事务保护。无有效执行环境/租约的运行验收仍须部署后进行，不使用伪ID冒充通过。原报告的29项前置阻塞不等同29个已确认缺陷。

## 两类导出故障溯源

1. 用户提供的86e73db7-d1c3-4b29-becb-f05cfc6e7e43是**用例导出任务ID**；对应请求traceId为97ff4cea-4fb5-4f09-ad74-16f7998b8b7f。日志明确在FunctionalCaseFileService.buildExportCustomField的Collectors.toMap(value)触发空指针。可选字段SQL NULL属于合法空值，不能用要求填写全部字段或默认等级绕过。现对NULL保留空单元格语义，空串不进入选项解析；重复相同值可合并，冲突值返回稳定错误，不静默覆盖。
2. 本轮重新真实调用测试MCP bug.export得到a56e3e27-3f73-4b27-9768-69052388a993。用户提供日志显示BugService.handleCustomField按BugCustomFieldDTO.bugId分组时遇到null key。查询返回bug_id，原resultType依赖全局自动映射。现在两个相关查询均使用显式resultMap；真实MyBatis XML测试在关闭自动下划线映射时仍读回bugId、fieldId和值。对缺失/跨请求缺陷ID的异常行明确拒绝，不通过丢弃字段伪造导出成功。当前未取得服务器实际MyBatis配置，因此不声称已证明是哪项部署配置造成字段丢失；已修复这条映射依赖并增加运行保护。
3. AgentBugTransferService保留已知业务错误，并区分未知文件生成与存储失败；完整技术异常只进服务端日志，客户端拿安全消息和traceId。

## 修改范围

本轮修改26个Java生产源文件、16个Java测试源文件、1个MyBatis XML以及MCP README。完整路径与SHA-256在`docs/testing/msp-mcp-remediation-20261030/evidence.json`；新增11个测试类。未新增依赖、表结构或配置项；新增2个原生缺陷附件上传工具，部署后需刷新客户端工具清单。创建名称长度和批量条数限制同时写入DTO注解与公开Schema，避免服务端校验与客户端契约再次分离。

前后端链路：原生MCP客户端 → /api/mcp → 动态工具注册/公开Schema → 当前Token/项目/资源权限 → 幂等事务/原业务服务 → 原表及文件存储 → 安全响应。缺陷字段映射同样影响缺陷列表和页面导出；用例字段空值修正同时覆盖页面与MCP导出。前端Vue未变更，错误展示沿用原协议；共享服务影响不能只按MCP孤立验收。

## 已执行检查

- `git diff --check`：通过。
- `powershell -NoProfile -ExecutionPolicy Bypass -File .codex-tmp/run-mcp-remediation-check.ps1`：尝试Maven reactor测试，堆192MiB/元空间128MiB/串行/不fork。未修改的system-setting重编译687个文件时堆不足，1分45秒后BUILD FAILURE；未达到agent模块，**不是构建通过**。没有加内存重跑全量。
- `python .codex-tmp/check-mcp-remediation.py`：最终本轮26个修改生产源与16个测试源定向javac编译通过，并执行67项测试，0失败、0错误、0跳过，21个测试类。使用现有目标目录/打包依赖及原Surefire classpath，覆盖输出目录优先加载。不是干净全量编译。早期测试暴露测试夹具缺少ID生成器/关联服务，已修正夹具；类路径缺测试目录也已修正。
- 中间还执行`python .codex-tmp/check-mcp-remediation.py --tests-only`完成测试夹具修正后的验证；最终已由上条完整定向检查替代。包含真实MyBatis XML映射（模拟JDBC行，非真实数据库）、公开Schema透传和长度/数量边界、权限拒绝、混合批量创建、图片读取、导出NULL与现有导出回归。
- 测试环境一次最小bug.export调用仍在旧部署版本失败，日志作为修复前证据；本地修复尚未部署，因此没有把旧环境响应算作修复后的结果。

可复核脚本、最终编译/测试日志、Maven失败日志及源码哈希已保存到`docs/testing/msp-mcp-remediation-20261030/`。脚本需要仓库既有Surefire报告、目标目录和本机依赖缓存；不得冒充独立干净环境质量门禁。

## 未执行验证、风险和准确后续命令

|检查|未执行/未通过原因|剩余风险/下一步|
|---|---|---|
|完整后端编译/打包、全量单元与集成测试|低资源Maven失败；不启动额外环境|资源充足CI执行下列Maven命令；仍可能有跨模块问题|
|真实MySQL迁移/事务/并发重放|本轮无DDL；本机不启动数据库/Docker|缓存、幂等重放和同项目关联需在隔离MySQL实际确认|
|Docker构建、启动、健康检查|本机资源紧张，未启动Docker|不能证明容器可运行；执行现有CI镜像/健康检查|
|真实MCP写入/导出文件回归|新代码未部署|部署后重测下方最小链；不能宣称117接口均正常|
|前端生产构建、完整lint|前端源码未修改，本机不重跑生产构建|提交阶段钩子已额外执行npm run type:check并通过；lint-staged无匹配文件。生产构建仍须现有CI门禁|
|29项原前置阻塞接口|缺启用环境、任务、租约和已发布专项资产|准备自有隔离数据后逐项复测，仍列未验证|

资源充足CI/开发机从仓库根目录执行（Windows用mvnw.cmd，Linux用./mvnw）：

```powershell
.\mvnw.cmd -B -pl backend/services/agent-integration -am -DskipTests package
.\mvnw.cmd -B -pl backend/services/agent-integration -am -Dtest=AgentCaseInputValidationTests,AgentCaseBatchValidationTests,AgentAttachmentDeleteContractTests,AgentAttachmentIntentPermissionTests,AgentMcpLeaseContractTests,AgentBugAttachmentUploadContractTests,PersonalExecutionProjectReferenceTests,AgentSafeErrorMapperTests,AgentDirectExecutionTests,AgentExternalImageServiceTests,AgentPlanDetailCountTests,BugExportCustomFieldMappingTests,BugTransitionFreshResponseTests,FunctionalCaseExportNullFieldTests,FunctionalCaseExportValidationTests,FunctionalCaseExportRowMapperTests -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

完整数据库质量门禁使用仓库现有`.github/workflows/msp-remediation-verification.yml`的full范围；容器启动/健康检查使用`.github/workflows/msp-runtime-verification.yml`，source_run必须来自本次源码的成功完整门禁及匹配产物，不能冒用旧包。不在本机额外启动。部署后按序验证：合法/非法创建 → 用例/缺陷附件本地上传与删除 → 名称/编号读取契约、单图提交及operation.get原键对账 → 缺陷状态响应/读回 → 缺陷默认/选列导出并解压读Excel → 含NULL字段用例导出并读空单元格 → 关联返回relationId、解除及读回 → 有效任务的事件/人工协助。并发保护保持原阈值。

功能状态：**部分完成：已确认缺陷的代码修正和67项定向测试完成；完整构建、数据库/容器与部署验收未完成。**
