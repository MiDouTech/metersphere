# 修订方案实施记录（2026-10-23）

基线：[仓库复核方案](msp-integrated-delivery-plan-20261023.md)。基线提交 `104fb6be2645bb47dc27e515254803398ce458ad`。**整体状态：部分完成，尚未完成应用启动及端到端验收，不作为可直接部署的结论。** 本轮没有提交、推送或合并 Git 分支；保留原有未提交文档、Demo 和周报。

## 1. 已确认的业务规则

- 重复导入查当前项目明确关联组，多组显式选择。
- 来源资产保留编辑，显式选择回写源或脱离；回写仍校验源编辑权限和版本。
- 目录置顶对组织全部成员生效，使用目录更新权限，排序在服务端分页前完成。
- 历史共享目录保留；新增项目按身份独立关联，允许短 ID 后缀区分同名。

## 2. 需求追踪与前后端对应

下表“接入”表示代码链路存在，不能替代运行验收。

| 需求 | 前端入口 / 实现 | 后端对应 | 数据 / 配置 | 证据与状态 |
| --- | --- | --- | --- | --- |
| UI-BASE | 用例、计划、评审原 SFC；局部 management-review.less；保留按钮权限和业务条件 | 复用原 API | 不改全局主题 | 组件编译、ESLint、样式检查通过；未做真实页面对照 |
| UX-01 | caseTree 更多菜单“移动”，选父目录或根目录，排除自身和后代 | POST functional/case/module/move-to-parent；共享树锁、版本、同项目和循环检查 | parent/pos/update_time；子树 CASE_SYNC 事件 | Java 编译通过；移动事务/页面交互待验收 |
| UX-02 | 导出右侧常驻“全选所有页”；旧箭头保留 | 原 useTable selectAll/selectIds/excludeIds 协议 | 同一受控选择状态 | 组件编译通过；跨页排除/筛选重置待浏览器验收 |
| UX-03 | 计划/评审名称可点击；计划组保持原行为，编辑入口保留 | 原详情路由、openDetail | 无迁移 | 路由静态检查通过；权限和缓存场景待运行验收 |
| IMP-01/02 文件 | 导入预检两步询问、关联组选取、评审人、任务结果、关联重试 | ImportFollowupCoordinator；原导入成功 target_id；计划/评审原创建和关联服务 | 123、130；group/followup/item 持久化 | MySQL 编排测试 3 项通过（原生实体服务为契约替身）；实际创建、权限和通知待验收 |
| IMP-01/02 资产 | 从资产导入增加相同决策、关联状态和重试；关闭时停止轮询 | 下层 CaseImportFollowupBridge → Agent 适配；真实 case_asset_import_result 成功 ID | 130 source_kind；导入事务提交后才启动异步任务 | 同上包含资产结果适配；旧资产导入原生任务的完整宕机恢复仍未补齐 |
| IMP-01/02 MCP | commit.followup 可选；followup.options / followup.retry；get 返回状态 | 同一编排；额外检查 PLAN_WRITE / REVIEW_WRITE；后台重新核验 Token | 旧调用不传策略仍仅导入，并标记 LEGACY | 编译通过；真实 MCP 客户端发现和调用待验收 |
| DEL-01 | 沿用现有软删 / 恢复入口 | 目录删除复用用例删除服务；评审持久重算；计划沿现有有效视图和统计 SQL | 127 重算队列；不永久解绑历史关系 | MySQL 软删、恢复、回滚、重放 2 项通过；所有删除入口、活动任务和计划汇总待验收 |
| AST-01 目录与同步 | 目录页；持续同步失败原因和重试 | 以项目关系认领目录；实时和历史复用 DefaultHubCaseSyncService；附件/字段 hash；旧版本删除保护 | 128 错误说明；CASE_SYNC 复用；历史映射冲突显式失败 | 目录身份 MySQL 3 项、outbox 1 项通过；字段/文件/版本完整同步仍需端到端验收 |
| AST-01 编辑 | 详情选择 WRITE_BACK / DETACH，源选择和版本；来源附件在源维护或先脱离 | 源权限和双版本校验；审计；脱离后重新排队源镜像 | 124 编辑审计 | 编译通过；多来源、附件与并发编辑待验收 |
| AST-02 | 组织置顶 / 取消，服务端排序 | PUT/DELETE catalog/{id}/pin，CASE_ASSET_UPDATE | 129 pinned/pin_time | 迁移通过；多用户、多页和权限待验收 |
| ASSET-BATCH | 当前页复选，删除/分类/等级/标签增删替换，结果、继续和重试 | case-asset-batches；冻结目标版本，逐项事务、操作权限、来源策略、幂等键 | 126 任务和逐项结果 | 校验与权限单测 3 项通过；实际数据修改、跨版本、引用保护待验收 |
| ASSET-EXPORT | 目录内勾选后导出，状态、下载 | 资产专用 scope → 原异步 XLSX/ZIP 导出；创建、执行、下载重新鉴权 | 125 新权限资源，不批量授予所有角色 | 权限边界单测 4 项、导出字段映射单测 2 项通过；真实文件内容和下载待验收 |
| REG-UI-01/02 | Token 表格横向滚动 / 操作列；资产 min-width、空态、窄屏折行 | 原接口 | 局部宽度与 table scroll | SFC/静态布局检查通过；未做真实窄屏截图验收 |
| REG-MCP-01/03 | MCP tools/list、导出请求 | 通用写操作 requestId schema；参数安全校验；导出嵌套列 schema | 状态协议继续独立校验，避免套通用缓存 | MCP 定向测试通过；客户端缓存和权限变更重放仍有未覆盖风险 |
| REG-MCP-02/SYNC-01 | 源详情与计划详情展示 executionFacts；MCP 查询返回同一字段 | FunctionalExecutionViewService / 原 provider / 计划有效视图 | 定义和提交事实分开 | 编译通过；托管/手工事实适配和批量性能未完整验证 |
| REG-FE-01 | 缓存计划详情 watcher 校验路由身份；资产切换隔离旧响应 | 缺失计划返回安全业务错误 | 请求序列隔离 | 列表刷新测试通过；实际路由切换待验收 |
| REG-ORG-01 | 原组织成员页 | 权限校验后紧贴目标 SQL 开启 PageHelper | 无迁移 | 定向单测通过；真实分页待验收 |
| REG-REL-01 | build-manifest 与静态资源 | CI 构建后显式生成 manifest；nginx JSON/HTML 缓存和资源 404 | cnb-build.yml / nginx.conf | manifest 测试通过；实际测试环境 SHA、网关配置未验收 |
| REG-EXP-01/02 | 原导出复用 | 稳定字段 ID 映射，不依赖翻译标题；编号、正文映射 | 无迁移 | 映射单测通过；中英 XLSX 内容待验收 |

## 3. 实际执行的验证

全部串行；Java 运行/编译限制堆 128 MiB、Metaspace 96 MiB，ESLint 堆 256 MiB，组件/脚本检查 192 MiB。未启动 Docker、应用或第二套依赖；MySQL 使用已存在的隔离端口 `127.0.0.1:13326`，仅创建随机测试库并在 finally/AfterEach 清理，未读取配置中的业务库。

| 检查 | 实际结果 | 边界 |
| --- | --- | --- |
| 变更主 Java 定向 javac | 41 个源文件编译通过；随后对 Coordinator / CaseAssetService 增量编译通过 | 使用已有 target/classes 与本地依赖，不等于完整 Maven 打包 |
| JUnit 单元/协议 | 24 项通过，0 跳过、0 失败 | 6 个定向测试类，不等于全仓单测 |
| FunctionalReviewRecountMySqlTests | 2 项通过 | 真实 MySQL 计数、软删恢复和事务 |
| ImportFollowupCoordinatorMySqlTests | 3 项通过 | 真实 MySQL 编排事务；计划/评审原生服务为契约替身 |
| SqlStabilityMySqlTests#effectsRollbackWithBusinessAndFailedEffectsRetryWithoutLosingCommittedRows | 1 项通过 | 已更新 127/128 迁移夹具，真实 outbox 回滚和重试 |
| CaseAssetCatalogIdentityMySqlTests | 3 项通过 | 同名独立、共享/手工名称保留、未认领旧目录不冒认；仅 ID 生成器和枢纽配置为替身 |
| Vue SFC | 20 个变更组件通过解析、script/template 编译及 TS 转译 | 不等于 vue-tsc 全量类型检查或生产构建 |
| ESLint | 21 个变更 Vue/TS 文件，0 error、22 warning | 现有 unused/console 警告未擅自清理 |
| Stylelint | 新 management-review.less 通过 | 尚未完成所有既有页面的样式检查 |
| test-build-manifest.mjs | 1 项通过 | manifest 与资源绑定 |
| test-list-refresh.mjs | 5 项通过 | 聚合刷新、暂停/恢复、失败重试和卸载 |
| verify-api-contracts / verify-layout-overflow / verify-route-tabs | 均通过 | 静态契约检查不代替页面 E2E |
| check-flyway-versions.ps1 | 无重复；下一个空闲版本为 131 | 修正本轮置顶 DDL 与原 DML 122 冲突，原已存在 122 保留 |
| Flyway 全量临时库 | 166 条到 3.7.2.129，通过 validate；再迁移 0 条 | 运行约 2 分钟；随后已清理临时库 |
| Flyway 130 增量临时库 | 基于真实 123 表结构、baseline 129，执行 130 一条、validate 通过、重放 0 条 | 没有重复运行前面未受影响的 166 条 |
| git diff --check | 通过（已有 LF/CRLF 提示） | 未提交 |

测试曾发现并已修正：新增迁移 122 重号；编排跨表 ID 排序规则冲突；短任务 ID 截取越界；新样式 lint 空行格式。夹具中的 ID 生成器初始化和 Java var 声明问题也已修正后重测。失败运行未计入通过数。

实际执行命令（仓库根目录；args 和日志为本机 `.codex-tmp` 证据，不包含业务凭据）：

```powershell
python .codex-tmp/integrated-implementation/prepare_compile.py
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/javac.exe' -J-Xmx128m -J-XX:MaxMetaspaceSize=96m '@.codex-tmp/integrated-implementation/javac.args'
python .codex-tmp/integrated-implementation/prepare_tests.py
& 'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/javac.exe' -J-Xmx128m -J-XX:MaxMetaspaceSize=96m '@.codex-tmp/integrated-implementation/test-compile.args'
# 以下逐条运行，均加 -Xmx128m -XX:MaxMetaspaceSize=96m，结果重定向至同目录日志
java '@.codex-tmp/integrated-implementation/tests.args'
java '@.codex-tmp/integrated-implementation/mysql-tests.args'
java '@.codex-tmp/integrated-implementation/followup-mysql.args'
java '@.codex-tmp/integrated-implementation/outbox-mysql.args'
java '@.codex-tmp/integrated-implementation/asset-identity.args'
java '@.codex-tmp/integrated-implementation/migration-run.args'
java '@.codex-tmp/integrated-implementation/migration-upgrade.args'
node --max-old-space-size=192 .codex-tmp/integrated-implementation/verify-sfc.mjs
./scripts/check-flyway-versions.ps1
git diff --check
# frontend 工作目录下逐条运行
$frontendChecks = @(git -C C:/SoftWare/JetBrains/metersphere ls-files -m -o --exclude-standard -- '*.vue' '*.ts' | Where-Object { $_ -like 'frontend/*' } | ForEach-Object { $_.Substring(9) })
node --max-old-space-size=256 node_modules/eslint/bin/eslint.js --fix $frontendChecks
node --max-old-space-size=192 node_modules/stylelint/bin/stylelint.js src/assets/style/management-review.less --custom-syntax postcss-less --fix
node --max-old-space-size=192 scripts/test-build-manifest.mjs
node --max-old-space-size=192 scripts/test-list-refresh.mjs
node --max-old-space-size=192 scripts/verify-api-contracts.mjs
node --max-old-space-size=192 scripts/verify-layout-overflow.mjs
node --max-old-space-size=192 scripts/verify-route-tabs.mjs
```

## 4. 未完成的实现、风险与运行门槛

1. 原生“资产→项目”导入任务仍沿用既有异步 worker，尚未补齐完整的宕机恢复/租约隔离；本轮的后处理编排本身可持久恢复。旧接口不传策略仍是 LEGACY，不宣称旧入口已自动建计划。
2. 资产批量首版为请求驱动持久分片，当前页最多 100 条；关闭页面暂停，可继续和重试，不是无人值守后台任务。资产所有页批量/所有页条件导出尚未实现。测试用例原表的“全选所有页”不受此限制。
3. 来源附件目前需在源维护，或先保存脱离后编辑；没有实现来源附件直接双向编辑。
4. 旧实时映射和历史映射不一致时显式失败，尚未自动合并、迁移引用或物理删除重复资产。历史共享目录保留。目录改名/移动按事件重解析目标路径，尚未建立稳定的子目录身份映射，可能留下空旧目录。
5. 托管、手工执行事实适配尚未完整扩展；MCP 批量事实读取可能增加查询次数。通用幂等缓存的资源级再鉴权、实际客户端 schema 缓存刷新仍须专项验证与补齐。
6. 新导出权限不会自动赋予所有组织角色，管理员需配置 `CASE_ASSET:READ+EXPORT` / `CASE_ASSET_EXPORT_BUTTON`。
7. 新增数据和后台消费者应随迁移后的同版应用上线，不能在旧 worker 与新语义混跑时直接宣称兼容。真实生产数据的重名、版本、双映射预检尚未执行。
8. 全量迁移期间主机空闲曾降到约 625 MiB，结束后回升。没有继续启动应用；保留原资源门槛，不通过降低保护阈值换取验收。

## 5. 未执行的验证与准确后续命令

尚未执行完整前端类型检查/生产构建、全仓后端编译打包、完整原生服务集成、镜像构建、容器启动与健康检查、浏览器页面和真实 MCP 冒烟。因此组件类型、依赖注入、真实文件存储、消息/通知、权限交互及部署资源配置仍存在未验证风险。

资源满足 [运行手册](implementation-resource-runbook-20261022.md) 后逐条串行执行；MySQL 集成测试只允许隔离端口 13326：

```powershell
# 仓库根；本机使用完整 Maven 路径，避免依赖 PATH
& 'C:/SoftWare/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd' -f backend/pom.xml -pl services/case-management -am '-Dquality.mysql=true' '-Dtest=FunctionalCaseModuleServiceTests,FunctionalCaseModuleControllerTests,FunctionalCaseExportRowMapperTests,FunctionalReviewRecountMySqlTests,CaseAssetExportAccessTests,CaseAssetCatalogIdentityMySqlTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
& 'C:/SoftWare/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd' -f backend/pom.xml -pl services/agent-integration -am '-Dquality.mysql=true' '-Dtest=AgentMcpStreamableServiceTests,CaseAssetBatchServiceTests,ImportFollowupCoordinatorMySqlTests,AgentCaseImportMySqlTests,SqlStabilityMySqlTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
pnpm -C frontend run type:check
pnpm -C frontend run build
& 'C:/SoftWare/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd' -f backend/pom.xml -pl app -am -DskipTests package
# 在以上检查通过后才构建镜像、启动；不能用旧 JAR 证明本次改动
./scripts/verify-msp-remediation.ps1 -Stage Image
./scripts/verify-msp-remediation.ps1 -Stage Start
./scripts/verify-msp-remediation.ps1 -Stage Smoke
```

最后三个脚本会实际操作隔离容器，执行前须先按运行手册做资源预检，并核对脚本使用的镜像/compose 项目；本轮未执行。完成后还须按主方案逐项运行目录移动、跨页选择、三类导入决策、删除恢复、资产附件/字段、批量逐项失败、XLSX 内容、Token 宽度、页面/MCP 权限撤销和当前部署 manifest 验收。

## 6. 本轮修改文件

以下列出本轮代码、测试、迁移和配置；原有未提交周报和 Demo 不包含在本轮修改声明中。

- `.github/workflows/cnb-build.yml`
- `backend/framework/sdk/src/main/java/io/metersphere/sdk/constants/PermissionConstants.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentCaseImportController.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/dto/AgentCaseDTO.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentFunctionalCaseExportService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentFunctionalCaseSearchService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentMcpStreamableService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/tool/functional/AgentCaseExportTools.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/tool/functional/AgentCaseImportTools.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentMcpStreamableServiceTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/SqlStabilityMySqlTests.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/asset/controller/CaseAssetController.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/asset/dto/CaseAssetSaveRequest.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/asset/service/CaseAssetHistorySyncWorker.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/asset/service/CaseAssetService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/controller/FunctionalCaseModuleController.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/dto/FunctionalCaseDetailDTO.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/hub/dto/DefaultHubCaseImportRequest.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/hub/service/DefaultHubCaseImportService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/hub/service/DefaultHubCaseSyncService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/provider/CaseReviewCaseProvider.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/request/FunctionalCaseExportRequest.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseAttachmentService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseExportTaskService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseFileService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseModuleService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseTrashService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalImportEffectOutbox.java`
- `backend/services/system-setting/src/main/java/io/metersphere/system/controller/OrgStructureMemberController.java`
- `backend/services/system-setting/src/main/java/io/metersphere/system/service/department/OrgStructureMemberService.java`
- `backend/services/test-plan/src/main/java/io/metersphere/plan/service/PlanCaseEffectiveViewService.java`
- `frontend/nginx.conf`
- `frontend/src/api/modules/case-management/featureCase.ts`
- `frontend/src/components/pure/ms-table/base-table.vue`
- `frontend/src/components/pure/ms-table/batchAction.vue`
- `frontend/src/components/pure/ms-table/select-all.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/caseTree.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/import/importFromDefaultModal.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/import/importJob.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabDetail.vue`
- `frontend/src/views/case-management/caseManagementFeature/index.vue`
- `frontend/src/views/case-management/caseReview/components/index/reviewTable.vue`
- `frontend/src/views/case-management/caseReview/index.vue`
- `frontend/src/views/setting/system/agentIntegration/index.vue`
- `frontend/src/views/test-asset/asset-case-detail.vue`
- `frontend/src/views/test-asset/cases.vue`
- `frontend/src/views/test-plan/testPlan/components/planTable.vue`
- `frontend/src/views/test-plan/testPlan/detail/index.vue`
- `frontend/src/views/test-plan/testPlan/index.vue`
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_123__functional_import_followup.sql`
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_124__asset_source_edit_audit.sql`
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_126__case_asset_batch_tasks.sql`
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_127__review_recount_queue.sql`
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_128__asset_sync_failure_detail.sql`
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_129__case_asset_organization_pin.sql`
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_130__asset_import_followup_adapter.sql`
- `backend/framework/domain/src/main/resources/migration/3.7.2/dml/V3.7.2_125__case_asset_export_permission.sql`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/CaseAssetBatchController.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/dto/CaseAssetBatchRequest.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/dto/ImportFollowupRequest.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AssetImportFollowupBridge.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/CaseAssetBatchService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/ImportFollowupCoordinator.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/CaseAssetBatchServiceTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/ImportFollowupCoordinatorMySqlTests.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/asset/service/CaseAssetExportAccess.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/request/FunctionalCaseModuleMoveRequest.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/CaseImportFollowupBridge.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseExportRowMapper.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalExecutionViewService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalReviewRecountService.java`
- `backend/services/case-management/src/test/java/io/metersphere/functional/asset/service/CaseAssetCatalogIdentityMySqlTests.java`
- `backend/services/case-management/src/test/java/io/metersphere/functional/asset/service/CaseAssetExportAccessTests.java`
- `backend/services/case-management/src/test/java/io/metersphere/functional/service/FunctionalCaseExportRowMapperTests.java`
- `backend/services/case-management/src/test/java/io/metersphere/functional/service/FunctionalReviewRecountMySqlTests.java`
- `frontend/src/assets/style/management-review.less`
- `frontend/src/components/business/ms-execution-facts/index.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/import/ImportFollowupChoice.vue`
- `frontend/src/views/test-asset/components/CaseAssetBatchActions.vue`

本轮还更新主方案第 14 节和本实施记录。所有条目仍须按上述完成定义验收后才能改为“完成”。
