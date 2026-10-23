# 2026-10-23 实测问题修复实施记录

依据：[详细方案](msp-live-issues-detailed-remediation-plan-20261023.md)。状态：实施中，部分完成，不代表已发布。

## 需求追踪

| 范围 | 前端/消费入口 | 后端/数据 | 测试与当前状态 |
| --- | --- | --- | --- |
| LIVE-01/14、SYNC-01 | 计划列表/详情/用例页、跨项目详情 | 现有REST查询 | 已增加进入/激活/焦点刷新、项目/计划/用例身份与响应序号隔离、未保存内容保护；刷新机制单测通过，页面E2E待验 |
| SYNC-02/03 | 当前状态/附件/缺陷/步骤及手工同步 | 有效视图、同步任务/迁移120～121 | 已实现活动计划源摘要读取、缺陷来源视图、持久任务及进度/取消/重试；托管执行事实、可靠revision/outbox等仍未完成，不能判为全量完成 |
| LIVE-02～05 | 计划报告、生成用例、资产AI入口 | 现有能力/路由 | 已提交移除代码；需源码检查和构建/运行验证 |
| LIVE-06 | 原生MCP创建 | DTO/schema、通用幂等层 | 项目/用例创建DTO暴露必填requestId；schema测试通过，真实原生客户端重试及冲突待验 |
| LIVE-07 | 平台配置表单 | URL/Origin校验与错误协议 | 已有前置校验，待核错误重复 |
| LIVE-08/09 | 用例分页、通用批量动作 | 分页权限及批量范围 | 已有配置，待检查链路和回归 |
| LIVE-10 | 评论编辑/删除 | 稳定冲突码映射 | typed CONFLICT映射VERSION_CONFLICT，安全错误测试通过；真实并发编辑待验 |
| LIVE-11 | MCP缺陷创建 | typed map schema及参数校验 | Map<String,String>字段schema为字符串，转换失败映射参数错误；schema测试通过，真实模板创建待验 |
| LIVE-12 | MCP计划用例查询 | 计划归属SQL/权限 | 移除不存在的deleted字段、限制TEST_PLAN类型及计划读取权限；真实MySQL SQL验证通过，MCP/REST逐字段比对待验 |
| LIVE-13 | 删除预检返回链接 | 路由注册与预检确认协议 | 已有路由/组件，待检查注册/权限链路 |

## 基线与资源

开始时已有commons.properties、AgentFunctionalCaseExportService、AgentFunctionalCaseSearchService、ExtFunctionalCaseMapper.java/xml及FunctionalCaseService的工作区改动；保留这些性能优化，不回退、不冒充本轮成果。其他既有文档改动亦保留。

本机起始空闲物理内存约850MiB；不同时启动多套环境，优先串行轻量检查。源码修改不等于运行通过；构建、迁移、容器健康和E2E证据将在实际执行后追加。

## 已落地链路与文件

下列路径均相对于仓库根目录；只列本轮涉及的文件，不将其他任务的SQL性能优化算入本轮成果。

| 链路 | 文件与行为 |
| --- | --- |
| 计划进入刷新与串数据防护 | `frontend/src/views/test-plan/testPlan/components/planTable.vue`、`detail/index.vue`、`detail/featureCase/index.vue`、`detail/featureCase/components/caseTable.vue`、`detail/featureCase/detail/index.vue`；复用useListRefresh，隐藏/离线/编辑时暂停，返回页面刷新，旧身份响应不得回写 |
| 跨项目详情与草稿保护 | `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`、`caseDetailDrawer.vue`、`tabContent/tabDetail.vue`；切项目关闭旧详情，异步附件读取检查身份，暴露hasPendingChanges保护执行/评论/上传草稿 |
| 当前详情与执行事实 | `TestPlanFunctionalCaseService.java`、`PlanCaseEffectiveViewService.java`、`TestPlanCaseDetailResponse.java`、`FunctionalExecutionViewProvider.java`、`AgentDirectExecutionViewProvider.java`；活动计划读取源用例，直接提交执行事实单独展示，保留额外/重复步骤，不覆盖定义；归档保留原历史覆盖逻辑 |
| 状态及统计读取 | `ExtTestPlanFunctionalCaseMapper.java/xml`、`ExtTestPlanModuleMapper.xml`、`V3.7.2_120__plan_case_effective_views.sql`；活动计划有效摘要来自源用例，执行人分配仍来自计划，固定旧版本成员不因latest=false被删除 |
| 缺陷来源 | `TestPlanDefectSyncService.java`、`ExtTestPlanBugMapper.xml`、`ExtBugRelateCaseMapper.xml`、`BugProviderDTO.java`、`tabContent/tabBug/bugList.vue`；源关联去重可见、已知来源取消可收敛、手工关联保留；源来源关系在计划中只读，后端同样拒绝解绑 |
| 全项目手工同步 | `PlanCaseSyncJobController.java`、`PlanCaseSyncJobService.java`、`V3.7.2_121__plan_case_sync_jobs.sql`、`frontend/src/api/modules/test-plan/testPlan.ts`、源caseTable.vue及zh-CN.ts；创建任务→逐计划事务→持久进度→查询/取消/重试，刷新后恢复；每批重新核验用户/项目/权限/模块设置，不自动添加未关联用例 |
| MCP与安全错误 | `AgentBusinessAccess.java`、`AgentProjectCreateRequest.java`、`AgentCaseCreateRequest.java`、`BuiltinAgentMcpToolConfig.java`、`AgentSafeErrorMapper.java`；计划归属查询修正、计划读取权限、幂等参数schema、typed map及冲突映射 |
| 发布识别 | `frontend/scripts/write-build-manifest.mjs`、`test-build-manifest.mjs`、`frontend/package.json`；npm run build成功后生成含revision、index SHA256及资源列表的build-manifest.json，便于对账；尚未在测试环境发布 |
| 隐藏资源清理 | `V3.7.2_122__hide_functional_ai_generate_tab_resource.sql`；关闭已移除“生成用例”Tab的UI资源，保留现有权限ID和授权记录 |
| 验证 | `AgentMcpCreateSchemaTests.java`、`AgentSafeErrorMapperTests.java`、`TestPlanFunctionalCaseSyncServiceTests.java`、`PlanCaseEffectiveViewServiceTests.java`、`AgentPlanDefectSyncMySqlTests.java`、`scripts/verify-plan-sync-mysql.py` |

## 实现与原方案的差异及尚未完成项

1. 共享字段优先通过SQL有效视图直接读取源数据，避免每次源修改都复制整份用例与附件。详情仍使用原源用例读取服务；计划私有文档、分配、成员及历史不参与复制。手工任务目前负责摘要及已知来源缺陷的持久化校准，不能据此声称所有数据域已经有复制任务。
2. 原方案的sourceRevision/appliedRevision、变更outbox、附件来源引用、全部修改入口可靠事件及补偿扫描**尚未实施**。`syncState=CURRENT`仅表示此次读取采用当前源视图，不是revision已应用凭据。不能以SQL视图替代AT18事件故障恢复验收。
3. 直接提交事实目前通过case/project/接受状态及源lastExecuteTime匹配。尚未建立权威持久executionRef，不能完全排除同毫秒执行碰撞；托管AI执行的冻结事实适配器尚未补齐。普通执行沿用源steps，归档详情仍沿用历史覆盖逻辑，完整归档快照也未新增。
4. 旧缺陷关系默认LEGACY，无法证明来源的旧记录不会自动删除。新SOURCE_SYNC关联可随源取消而移除；旧同步留下的LEGACY关联需要依据审计证据迁移，不能猜测删除。源附件与计划私有附件的来源标记和生命周期验证仍待完成。
5. 新任务支持持久化、租约及逐计划事务，但状态机故障注入、多副本、权限撤销、取消重试、500+条规模及后台worker端到端尚未验证；失败详情仅返回前100条。大计划当前仍一次读取关联列表，源用例查询按200条分片，并非完整分页处理。
6. LIVE-02～05/07～09/13多数源码在本轮前已提交，本轮未把隐藏入口重复实现；仍需精确制品发布及浏览器验收。构建清单目前绑定npm run build，其他构建入口和部署缓存链路需继续核对。

## 已执行验证

所有命令串行执行；未启动、停止或重建任何Docker环境。MySQL检查复用已运行的隔离容器`msp-sql-optimization-mysql`，只创建并删除本脚本随机前缀测试库；未修改已有数据库。

| 命令/范围 | 实际结果 |
| --- | --- |
| 前端目录：`node --max-old-space-size=128 --test scripts/test-build-manifest.mjs scripts/test-list-refresh.mjs scripts/test-platform-configuration.mjs` | 42项通过 |
| `python scripts/verify-plan-sync-mysql.py` | 13项通过；执行120/121迁移与122资源UPDATE；活动摘要、归档摘要、固定旧版本、源缺陷立即可见、去重、幂等、手工关联保留、MCP计划SQL及任务初态通过；证据`outputs/plan-sync-mysql-20261023.json`，随机测试库已清理 |
| 前端目录：`node --max-old-space-size=128 scripts/verify-route-tabs.mjs` | 通过 |
| 前端目录：`node --max-old-space-size=128 scripts/verify-api-contracts.mjs` | 通过 |
| 前端目录：`node --max-old-space-size=128 scripts/verify-permission-resource-coverage.mjs` | 初次失败：生成用例Tab残留启用资源；新增122迁移后通过，81个精确绑定、61个兼容绑定、0未绑定 |
| Maven离线compile，agent-integration及依赖模块，`-Xmx512m -XX:ActiveProcessorCount=2` | 阶段性通过；之后新增权限检查和EffectiveView测试，最终版本仍需复验 |
| Maven离线test：`AgentMcpCreateSchemaTests,AgentSafeErrorMapperTests,TestPlanFunctionalCaseSyncServiceTests`，`-DforkCount=0` | 10项通过；原同步测试先因旧预期失败，调整为保留固定版本/可恢复成员/历史后通过 |
| 前端定向ESLint：本轮12个TS/Vue/MJS文件 | 初次3个错误已自动修复；最终exit 0，0错误、24条既有console/unused警告，不扩大清理范围 |
| `node --max-old-space-size=1024 node_modules/vue-tsc/bin/vue-tsc.js --noEmit --skipLibCheck` | 失败：Node堆达到1GiB上限OOM；不能视为类型检查通过，不调高内存反复尝试 |
| `git diff --check` | 通过，仅有其他改动文件的LF/CRLF提示 |

Maven实际入口：`C:/Users/midoo/.m2/wrapper/dists/apache-maven-3.8.3-bin/5a6n1u8or3307vo2u2jgmkhm0t/apache-maven-3.8.3/bin/mvn.cmd`。执行参数如下（仓库根目录）：

```powershell
$env:MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2'
& 'C:/Users/midoo/.m2/wrapper/dists/apache-maven-3.8.3-bin/5a6n1u8or3307vo2u2jgmkhm0t/apache-maven-3.8.3/bin/mvn.cmd' -o -pl backend/services/agent-integration -am test '-Dtest=AgentMcpCreateSchemaTests,AgentSafeErrorMapperTests,TestPlanFunctionalCaseSyncServiceTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-DforkCount=0' '-DskipAntRunForJenkins=true' -q
```

## 未执行验证与风险

本机空闲内存后续多次下降到约0.5～1.4GiB，不具备继续重型构建及运行验证的余量。未停止其他任务的容器、未增加第二套环境、未降低原内存保护门槛。

- 最终Java编译和新增`PlanCaseEffectiveViewServiceTests`尚未执行；待资源允许，在上面Maven命令的`-Dtest`追加该类；MySQL Java集成测试可追加`AgentPlanDefectSyncMySqlTests`及`-Dquality.mysql=true`（仅使用既有隔离13326测试库配置）。风险：最新改动仍可能存在编译、权限兼容或服务装配问题。
- 前端完整类型检查、生产构建未通过。待独占资源窗口，在frontend目录串行运行`npm run type:check`、`npm run build`。风险：Vue类型、模板编译及打包问题尚未排除。
- 只执行新增迁移片段及真实业务SQL，没有跑完整应用的全部Liquibase迁移。风险：现有生产数据形态、视图执行计划、升级顺序及部署账号权限仍需集成验证。
- 当前代码镜像构建、单实例启动/健康、页面和真实MCP端到端未执行。已有其他任务容器健康不能作为本轮版本的证据。应先按已核验的部署compose配置运行`docker compose -f <实际部署文件> config`，再串行构建、单实例启动、健康检查及AT01～AT28；实际部署文件尚未选定，不能提供未经核对的可执行启动命令。
- 未提交、推送或发布本轮改动。测试环境仍需部署新制品后取证，不能声称线上问题已解决。

**功能状态：部分完成。** 已有源码、单测及真实SQL证据覆盖部分核心路径，未满足完整方案和AGENTS完成定义；以上遗留项与运行验收必须继续完成。
