# MSP 整合实施方案 · 仓库复核修订版

日期：2026-10-23。基准：本地HEAD `104fb6be2645bb47dc27e515254803398ce458ad`及当前工作区。**状态：源码复核与方案修订完成；业务功能待实施、待验证。** 本轮没有重新访问测试环境，源码证据不替代现网验收。

## 1. 本次修订结论

上一版存在重复建设、对现有状态机了解不足、数据迁移不具体，以及将建议默认当成业务规则的问题。本版按“仓库现状→缺口→最小改造→兼容→验收”重写。[上一版存档](msp-integrated-delivery-plan-20261023-v1-archive.md)仅供追溯，不再作为实施依据。

| 上一版不足 | 实际代码 | 本版修正 |
| --- | --- | --- |
| 项目创建新增事件主链 | CommonProjectService已有资源创建钩子和DefaultHubFolderService | 统一已有目录解析及初始化顺序，不再新增第二条主链 |
| 导入新增任务/outbox | 已有functional_import_job/item、functional_import_effect_outbox及另一套枢纽导入任务 | 保留任务，增加关联子流程及来源适配 |
| 资产同步只需补事件 | 实时镜像与历史回填的映射键、写入者不同 | 先统一资产身份，防止重复资产，再补可靠事件 |
| 删除统一解绑，恢复不关联 | 软删保留关系，评审有恢复重算，计划SQL过滤deleted | 保留现有恢复语义，补过滤、计数、刷新可靠性 |
| 删除后永久保留所有历史 | 彻底删除方法会清理评审、计划及执行历史 | 区分软删与彻底删除，不承诺未做的永久快照 |
| 名称增加点击即可 | 计划名称列已有INPUT行内编辑 | 导航与编辑触发分离，保留排序和分组行为 |
| 重复导入按项目+用户 | 用户未确定此口径 | 使用显式关联组，操作者不擅自作为唯一分组维度 |
| 来源资产全部只读、置顶个人化 | 都是上一版自行增加的规则 | 保留功能，列出待定项及推荐策略，不擅自限制 |

## 2. 需求基线和边界

用户已确定：采用[最后一版Demo](../demo/msp-real-frontend-style-review-20261023.html)优化样式；原按钮常驻、功能保留；目录移动；导出右侧全选所有页且旧箭头保留；计划/评审名称导航；导入后创建/更新计划与评审；源删后两者更新；项目同名资产目录与持续同步；资产置顶。

前期14问题继续依据[现场回归报告](msp-full-regression-20261023.md)及[详细修复方案](msp-remediation-assets-batch-ui-review-plan-20261023.md)。[22日需求](requirements-consolidated-2026-10-22.md)、[缺陷](defect-remediation-consolidated-2026-10-22.md)、[事故整改](msp-incident-remediation-20261021.md)、[SQL稳定性](msp-sql-stability-optimization-plan-20261023.md)继续保留。已通过项按后续影响面复验，不重复无关构建。

Demo虽加载真实Vue/Arco库，但没有挂载原业务SFC、Ms组件、权限、路由和API。6条示例的全选不是跨页验收，模拟导入不是正式编排。本轮不修改原型来冒充后端闭环。

## 3. 已核实的真实链路

### 3.1 导入不是单一入口

| 入口 | 当前链路 | 可用成功事实 | 接入位置 |
| --- | --- | --- | --- |
| 用例页文件导入 | components/import/index.vue → /ai/case-imports/precheck → AgentCaseImportService → functional_import_job/item → commit/drain | item.state=SUCCESS的target_id；CREATE与UPDATE都可能成功 | commit前持久策略，完成/部分完成后关联成功ID |
| 资产导入业务用例 | importFromDefaultModal → CaseAssetService.importToProject → DefaultHubCaseImportService | 枢纽任务结果里的真实目标caseId | 增加适配器，不拿勾选的资产ID直接关联计划 |
| 旧文件导入 | FunctionalCaseController旧导入 → FunctionalCaseService/FunctionalLegacyImportService | 旧响应有jobId和计数，无完整成功ID契约 | 在写入事务收集ID，不用名称反查或计数推断 |
| MCP文件导入 | AgentCaseImportTools/Service | 同一持久导入结果 | 显式policy，不能等待浏览器弹窗 |
| 资产目录自身文件导入 | CaseAssetImportJobService/Worker | 枢纽内资产 | 默认不触发业务计划/评审，防止错误作用系统枢纽 |

AgentCaseImportService已有文件/模板指纹、requestId、目标版本、逐项事务和重试。FunctionalCaseService在导入新增/覆盖后已入队FunctionalImportEffectOutbox：CASE_SYNC/NOTICE，每轮最多5项，SKIP LOCKED，退避重试，成功7日清理。该表目前**没有导入job关联、plan/review分项、业务去重键**，不能只增加一个effectType就认为编排可靠。

### 3.2 项目目录与资产存在双轨

CommonProjectService.add先插入project，调用ProjectServiceInvoker.invokeCreateServices，其中CaseAssetProjectResourceService会upsertForProject；之后又调用DefaultHubFolderService.onBizProjectCreated创建本项目和枢纽目录。两条路径都涉及枢纽模块，必须统一解析。

- 实时：DefaultHubCaseSyncService以default_hub_case_map按具体bizCaseId定位；DefaultHubModuleResolver处理目录；哈希变化才更新；imported_from_hub副本跳过以防环；关闭sync、缺目录等情况直接返回。
- 历史：CaseAssetService.syncHistoricalCaseSource以case_asset_source_relation按source_project_id+COALESCE(ref_id,id)定位，然后独立add/update资产。
- case_asset_lineage是“资产→项目副本”的血缘，不能当作“业务源→资产”的唯一主映射。
- TestAssetPublicationListener负责版本和来源治理，不等同于目录镜像同步。

代码中的具体风险：upsertCatalog按组织+规范化名称复用，创建module时ref_project_id=NULL；catalog_project_rel允许多个项目共用一个目录。实时和历史路径可能为同源生成不同hub case。实时镜像创建/更新复制header/blob/customFields，所读方法没有附件复制；历史路径按hub模板fieldId交集过滤字段，不同项目等级字段ID不一致可能被丢弃。编辑钩子存在不代表独立附件、整目录移动、恢复都已覆盖。

### 3.3 删除已有恢复机制

FunctionalCaseService.handDeleteFunctionalCase保留requireDeletionIdle执行任务保护，软删设置deleted，并发DELETE_FUNCTIONAL_CASE给CaseReviewCaseProvider；provider是@Async，错误仅日志。FunctionalCaseTrashService.recoverVersions会发RECOVER_FUNCTIONAL_CASE重算评审。计划Mapper多处已有functional_case.deleted过滤。

因此应先核对“当前列表和计数是否都排除已删源”，而非先删关联。DeleteFunctionalCaseService.deleteFunctionalCaseResource是彻底删除：清文件、评审关系与历史、计划关系与执行历史，不能与软删混为一谈。

## 4. 需求追踪与改动清单

| ID | 前端 | 后端/接口 | 数据或配置 | 验收 |
| --- | --- | --- | --- | --- |
| UI-BASE | 原case/plan/review SFC局部样式 | 不替换API和Ms组件 | 无业务迁移 | 常驻按钮、权限、列配置无回退 |
| UX-01目录移动 | caseTree更多+父目录弹窗 | 复用锁树/版本/循环检查，补显式父级接口 | parent_id/pos/update_time | 子树/移根/旧版本/跨项目 |
| UX-02全选 | 导出后按钮、旧箭头 | useTable同一选择事件，原selectAll协议 | 无需新建全选表 | 跨页排除/筛选清空 |
| UX-03名称 | plan/review名称slot | 复用openDetail | 无 | 编辑不导航、分组不误跳 |
| IMP-01/02 | 导入预检及任务结果 | 原任务+新增关联编排适配 | group/followup/item及任务扩展 | 分项、幂等、部分成功恢复 |
| DEL-01 | 删除/恢复后刷新 | 现有过滤及provider补可靠重算 | 先不改物理关系 | 列表/统计/恢复一致 |
| AST-01 | 源/同步时间/错误展示 | 统一已有目录与case upsert | 映射去重/版本/墓碑 | 同源唯一、增改删恢复 |
| AST-02 | 目录置顶菜单 | pageCatalogs服务端排序、偏好接口 | 范围确定后建偏好表 | 跨页稳定排序/撤权 |
| ASSET-BATCH | 资产复选/批量/导出 | 独立资产scope适配原任务 | 权限/任务/审计 | XLSX内容、引用、逐项失败 |

## 5. UI及三个基础交互的实施

### 5.1 样式落实到原组件

修改caseManagementFeature/index.vue与components/caseTable.vue、testPlan/index.vue/components/planTable.vue/detail/index.vue及评审表的局部class/style。复用MsCard/MsSplitBox/MsAdvanceFilter/MsBaseTable/MsButton。不能用Demo的a-table替换业务表，否则会丢行内编辑、列配置、拖拽、自定义字段、权限和选择。

采用Demo紫色、间距、目录宽度、批量条、边框表头；窄屏按钮折行，固定操作列依附表格可见容器。首批不改全局arco-reset。按钮常驻、零选中禁用，权限/归档/AI配置显隐仍沿原规则；UI提交不混入同步业务重构。

### 5.2 目录移动

现有POST /functional/case/module/move接NodeMoveRequest：dragNodeId/dropNodeId/dropPosition/expectedUpdateTime，是拖拽位置协议。moveNode已有lockProjectTree、requireRevision、checkParent、跨项目及同父重名检查；moveToParent可显式设父级，但本身无expectedUpdateTime参数。

建议新增POST `/functional/case/module/move-to-parent`，DTO `{moduleId,parentId,expectedUpdateTime}`；parentId只能真实目录或根NONE，不接受all/recycle/root虚拟标记。沿用FUNCTIONAL_CASE_READ_UPDATE和CheckOwner；服务端同事务先requireCurrentRevision再共享移动逻辑，项目从实体读取，不信请求伪造项目。

弹窗禁选自身/后代，后端同样防循环、跨项目、重名和目标已删除；不额外发明仓库未约定的层级上限。移动只改子树根的parent/pos，子节点及用例moduleId不变。hub整棵目录要跟随，不能只同步根下直接用例。成功刷新树/面包屑/计数；409保留弹窗选择并提示刷新。

### 5.3 全选所有页

base-table.vue的handleSelectAllChange只发事件，真实状态在useTable.ts的propsEvent.selectAllChange，计数按total-excludeKeys。新按钮应接同一ALL状态转换；只改selectedKeys或另建allPages会与旧箭头不同步。将SelectALL的本地状态与selectorStatus受控同步，并给batchAction增加仅用例页启用的导出后扩展slot。

按钮位于导出右侧，零选中可用，数据为空/加载时禁用；旧箭头保留当前页/所有页。手工排除某行进入excludeIds，切页保持，项目/目录/搜索/筛选变化清空并说明。原后端已有selectAll+condition+selectIds/excludeIds，不为一个按钮强制全量改selectionToken；仅新异步批量在首次执行时冻结有权目标，预览和执行发生变化要报告。

### 5.4 名称导航

计划name列已有INPUT行内编辑；评审num已有openDetail，name需要增加slot。名称链接复用原路由及详情API，显式编辑入口仍调用原编辑逻辑，点击输入框/下拉/拖拽不冒泡跳页。保留名称排序、全文提示、键盘焦点。计划组仍按组展开/详情语义，不拿组ID当计划ID。组件测试必须验证名称跳转与编辑不跳转两条路径。

## 6. 导入后自动计划/评审

### 6.1 明确规则与待定口径

确定：首次创建一对并关联本批成功用例；再次第一问是否增量追加已有，否则第二问是否新建一对，否则仅导入。追加去重，不替换旧集合、执行或评审意见。

推荐项目内显式import group：planId/reviewId成对，操作者是审计和权限字段，不默认以用户分隔业务。只有一组可用时展示默认，多个必须明确选择。已有历史计划不能只按名称自动认领，可显式绑定有权目标。此分组范围待业务确认。

评审创建要求moduleId/reviewPassRule/reviewers及baseAssociateCaseRequest；计划有moduleId/groupId/repeatCase/automaticStatusUpdate/passThreshold。预检页补齐必填选项，从合法项目配置取默认，**不自动指定操作者为唯一评审人，不默改已有计划开关**。

### 6.2 状态机和失败行为

预检 → 持久CREATE_NEW_PAIR/APPEND_EXISTING/IMPORT_ONLY决策 → 原导入commit → 成功item真实target_id → FOLLOWUP_PENDING → PLAN/REVIEW/ASSOCIATE分项 → SUCCEEDED/PARTIAL_FAILED/BLOCKED_PERMISSION。

原导入COMPLETED/PARTIAL状态不改为“全部流程成功”，另返回followupState。CREATE和UPDATE成功行均计入；INVALID/FAILED不计入；SKIPPED默认不关联，除非预检明确选择。0条成功不建空计划。原任务retry带来新增成功行只补缺失关联，不重复导入或重建目标。

取消只停止未执行，已导入项的处理结果明确显示；关联失败保留用例成功，允许仅重试关联。文件24小时过期或成功outbox七日清理不能删掉未完followup的成功ID/参数。目标被删/归档/评审结束时返回明确冲突，重选，不自动复活。仅一方失效要展示，允许补建缺失方或新建一对，禁止默做决定。

### 6.3 接口改动（均为建议新增/扩展）

| 位置 | 变更 |
| --- | --- |
| /ai/case-imports/precheck响应 | 增加followupCapabilities/historyGroups/requiredFields，保留旧jobId |
| /ai/case-imports/{id}/commit | 原无体action增加可选validated body：policy、targetGroupId、expectedGroupRevision、创建参数、requestId；旧调用LEGACY仅导入兼容 |
| 原GET任务 | 增加followupState、两类目标ID/名称、分项计数/安全失败原因/traceId；保留items分页 |
| 建议POST /ai/case-imports/{id}/followup/retry | 仅重试关联、重新鉴权 |
| 资产到项目导入 | DefaultHubCaseImportRequest/结果扩同构策略，适配枢纽任务结果，不冒用functional_import_job ID |
| MCP | schema同步增加策略，旧调用LEGACY兼容并说明未建关联；不能强迫旧客户端新增必填而无升级策略 |

初期用持久job策略+groupRevision+目标版本足够，不再额外引入decisionToken体系。冲突以业务码+用户可理解message+traceId返回；字段错误有安全路径，不暴露SQL/堆栈。

### 6.4 编排所在模块与复用

test-plan Maven依赖case-management，不能在case-management反向注入TestPlanService再添依赖。编排放能访问两者的上层，建议agent-integration；case-management通过provider接口报告导入完成事实，旧/枢纽导入均有适配。

计划复用TestPlanService.add的savePlan/initPlan及TestPlanFunctionalCaseService.associateCases，后者解析FUNCTIONAL默认collection并去重；评审复用CaseReviewService.addCaseReview/associateCase，关联会补评审人关系并排除已关联case。worker内显式做项目/权限/DTO校验，不能以为绕过Controller仍会执行注解。禁止直接插关系表漏collection或reviewer。

### 6.5 数据和事务

建议逻辑实体：

- import_followup_group：org/project、planId/reviewId、revision、创建/更新人；不以actor做天然唯一业务组。
- import_followup_job：sourceType+sourceJobId唯一、policy、groupId、操作者与授权上下文、不可变创建参数、目标ID、分项状态/重试/错误。
- import_followup_item：jobId+targetType+caseId唯一、来源position、实际版本/关联ID、state。

新增目标与保存其ID在同数据库事务内提交，避免先建plan后崩溃丢ID又重建；关联分批短事务，锁group或唯一约束防并发。不把整批导入、两对象和全部关联塞进原25秒outbox事务。复用原调度/限流，但扩新effect类型前必须将当前“非CASE_SYNC全当NOTICE”的else改为显式分发，未知类型拒绝，防误投通知。需要业务去重键及成功ID追踪，不可只加JSON。

权限分别检查用例导入、计划ADD/ASSOCIATION、评审ADD/关联。预检无权时可显式仅导入；执行中撤权记录BLOCKED，不撤销成功用例，不假报完整成功。复用现有服务前核对通知副作用，本方案不新增额外外部消息发送需求。

## 7. 删除同步：补可靠性而不破坏恢复

软删当前版本：当前计划/评审列表按源deleted过滤，统计采用相同条件重算，页面/MCP同时失效刷新；关系/历史保留，恢复走现有RECOVER机制重新可见。所有版本删除须先由refId展开物理caseId，不把refId直接当关联ID。

不新增永久解绑，避免恢复后丢关联。CaseReviewCaseProvider异步异常仅日志应改为持久重算/可对账，计算以当前数据幂等覆盖，避免增减计数重放错误。目录删、批删、回收站、MCP入口都核对；保留requireDeletionIdle及锁。先读计划有效视图、列表和汇总SQL再改，不能只修前端列表。

彻底删除维持现有清理契约并在确认说明会清理哪些历史；若业务要求彻底删除后永久审计，需先独立快照/附件引用保留方案，不把其混入本轮“删除后计划和评审当前内容更新”的必要改动。

## 8. 资产持续同步：先统一身份，再补完整事件

### 8.1 只读盘点与冲突处理

按项目列出catalog/project_rel、hub_module.ref_project_id、default_hub_case_map、case_asset_source_relation、lineage、源id/refId/latest、资产ID、内容/字段/附件、引用项目和版本。两失败项目仍须查服务日志：唯一冲突是高可信源码推断，不冒称已获异常栈。

分类：仅可信module→补catalog；仅catalog→resolver复用同模块；两映射同资产→补缺；两映射不同资产→比较引用和内容，先输出合并预览；同名异组织/异来源→隔离不能认领。被引用重复资产不直接删除，先迁移引用或保留别名/历史，记录old/new ID。备份受影响行、数量与引用校验、条件迁移可重跑，不能INSERT IGNORE吞掉冲突。

### 8.2 一个目录解析器和一个upsert写入者

保留项目资源钩子，统一CaseAssetProjectResourceService和DefaultHubFolderService的枢纽目录解析；业务自身目录仍保留。system-setting不能反向依赖case-management，使用低层resolver/provider。修改初始化顺序前检查其他CreateProjectResourceService，不靠Bean顺序。

目录按project_rel身份优先，name只展示。现有组织名称唯一和目录多项目关系不能直接支持“每项目完全同名独立目录”。推荐历史共享先保留，新增按projectId独立，冲突加项目短ID后缀；若要求同名显示，应另拆display_name，待确认后迁移，不先删唯一约束。

统一DefaultHubCaseSyncService upsert，历史worker改为复用，不独立add资产。适配sourceProjectId+sourceRefId稳定身份及当前物理sourceCaseId/latest，兼容旧map，不能直接改旧主键造成丢版本。source_relation负责源→资产，lineage负责资产→副本，版本监听继续治理，不重复镜像。

### 8.3 字段/事件覆盖矩阵

| 变更 | 已有 | 需要补齐 |
| --- | --- | --- |
| 新建/编辑 | FunctionalCaseService实时钩子，失败日志 | 同事务持久登记，skip有原因，不计同步成功 |
| 文件新增/覆盖 | import outbox CASE_SYNC | 同一资产ID和可见副作用状态 |
| 资产导入项目 | imported_from_hub防环 | 保留lineage；副本变更是否重新资产化另定 |
| 附件增删、外链转存、解绑 | 不能凭case编辑推断覆盖 | 附件领域登记事件；关联引用管理，禁止删资产误删源文件 |
| 等级/自定义字段/标签 | 实时copy与历史fieldId交集不同 | internalFieldKey/类型/选项映射，不能静默丢等级；hash覆盖实际定义字段 |
| 整目录移动/改名 | 用例批移有钩子，但目录是另一入口 | 映射树整体移动，不把全量case拉入内存 |
| 删除/恢复/版本切换 | 当前hub删除会删map | 保留稳定身份/墓碑，恢复同ID，拒绝旧事件复活 |
| 执行/缺陷 | 内容快照有意排除运行状态 | 定义不写执行；需要展示时读统一执行事实 |

源持续同步是最终收敛，不在无实测前承诺30秒。展示状态/时间/错误，版本单调、删除墓碑、重试退避/死信、增量对账。复用现有outbox设施需扩业务键、源版本和查询结果，不能把当前导入表当已有通用事件总线；避免两个worker同时写同一源。

### 8.4 不擅自关闭资产编辑

上一版“一律只读”未确认，本版撤回。推荐源字段以源为准、资产分类/备注独立；在资产编辑源字段时明确“回写源（有源权限）”或“脱离来源成独立资产”，不能隐藏功能或下次静默覆盖。此规则需要定稿；纯人工资产维持完整编辑和批量能力。项目改名现有单关联/非manuallyRenamed条件也要与同名目标协调，不能绕过用户原确认直接改。

## 9. 置顶与资产批量/导出

pageCatalogs当前组织范围、update_time DESC、服务端分页；前端只排当前页不正确。置顶推荐个人偏好（是否组织共享待确认），不改目录update_time冒充排序。建议org+user+catalog唯一、pinOrder/id稳定排序，权限/关键词过滤后join偏好，再分页，COUNT不被join放大。建议PUT/DELETE /case-asset/catalog/{id}/pin；个人偏好用读权限即可，组织共享需要管理权。

批量按原详细方案保留删除、分类、等级、标签增删替换、XLSX导出，归档为建议扩展。CaseAssetController当前为单条CRUD/导入，应新增资产scope适配，禁止借普通FUNCTIONAL_CASE权限读取枢纽全库。来源字段冲突按8.4处理；字段映射、导出稳定ID/locale共用修复。异步目标快照、分项结果、requestId、重试/下载再鉴权、文件过期行为完整。

被引用资产阻止物理删除是建议保护，不等于源用例软删需要阻止；预览要显示实际lineage。新导出权限资源和默认角色迁移明确，不自动授予所有人。CSV原型不替代真实XLSX内容验收。

## 10. 前期问题与新增需求的合并依赖

| 问题 | 实施关联 |
| --- | --- |
| REG-UI-01/UI-02 | 原Token抽屉、资产空态宽度链；Demo无溢出不能判已修 |
| REG-ASSET-01/02 | 第8节统一映射/失败原因和两项目条件迁移，先修再开持续同步 |
| REG-MCP-01/CLIENT-01 | 服务端幂等schema/客户端缓存分开；followup也要schema兼容 |
| REG-MCP-02/SYNC-01 | 源、计划、MCP统一definition与executionFacts，定义不能冒充执行 |
| REG-FE-01 | 缓存计划watch路由身份/项目校验、旧响应隔离；名称跳转复验 |
| REG-REL-01 | manifest真实SHA/资源hash/JSON缓存；本地HEAD不等于线上SHA |
| REG-ORG-01 | PageHelper紧贴目标SQL，独立修复，与置顶COUNT分别验收 |
| REG-MCP-03 | 导出嵌套schema/安全参数错误，不把非法请求当正常导出失败 |
| REG-EXP-01/02 | 字段ID取值、locale、编号非空、读取真实XLSX；资产导出复用 |

进入计划刷新、全内容同步和手工同步继续按已有详细方案；导入/删除完成走统一刷新失效，不覆盖文档草稿。14项各保留原编号和证据，不因整合重计或抹除未完成项。

## 11. 分阶段提交与迁移

S0：确认发布版本、服务日志、两同步映射及119迁移实际状态，输出只读盘点。

S1：局部样式、名称兼容编辑、同状态全选、显式父级移动独立提交；这些不依赖重复导入分组规则。

S2：P1修复，目录冲突/统一upsert先于持续同步；删除过滤、统计、恢复独立验收。

S3：先文件任务完整followup，再资产→项目/旧入口适配；只接一条入口不能宣称导入自动关联全完成。LEGACY兼容后按项目灰度启用。

S4：完整资产事件、置顶、批量、导出；来源编辑规则定稿后实施，不用只读规避未实现。

扫描现有迁移序号再分配，不修改已发布V61/112/119。历史任务不自动补造计划，不按名认领目标。迁移备份ID映射、引用、数量并验证重跑/回退。兼容schema→能识别新类型程序→开开关；旧worker遇新类型必须拒绝，不能else当NOTICE。回滚关新入口/消费者，保留成功实体、未完任务与审计。

## 12. 验证、资源约束及本轮实际记录

### 12.1 正式改造必须补齐

- ModuleService/Controller：旧版本、子树/根NONE、自身后代、跨项目、重名、并发移动、hub树一致。
- 表格组件：新按钮/旧箭头同状态、排除、分页、筛选清空、未选常驻；不能只扫描文字。
- plan/review：导航/编辑互不干扰、组展开、权限、缓存误请求、草稿不覆盖。
- 导入MySQL：成功真实ID、CREATE/UPDATE/INVALID/SKIPPED、三策略、部分成功重试、两对象单方失败、崩溃恢复、撤权/归档、清理不丢后处理。
- 计划/评审：默认FUNCTIONAL collection、reviewer关系、去重、原执行/意见不清空。
- 资产与outbox：原CASE_SYNC/NOTICE不退化、新类型不误通知、同源双映射、乱序墓碑、附件/等级字段、增量对账。
- 删除/恢复：各入口、当前/全部版本、活动任务保护、列表/统计/恢复一致；永久删另验清理契约。
- 导出：读取中英/默认locale的XLSX单元格，编号名称/步骤非空、行数、过滤/撤权/过期正确。

### 12.2 后续验证命令（本轮未执行）

在仓库根目录，使用项目已配置的Java/Maven，逐条串行。MySQL测试显式需quality.mysql=true，现有测试硬编码127.0.0.1:13326，运行前确认隔离实例，不能误连业务库。

```powershell
mvn -f backend/pom.xml -pl services/case-management -am -Dtest=FunctionalCaseModuleServiceTests,FunctionalCaseModuleControllerTests -Dsurefire.failIfNoSpecifiedTests=false test
mvn -f backend/pom.xml -pl services/agent-integration -am -Dquality.mysql=true -Dtest=AgentCaseImportMySqlTests,SqlStabilityMySqlTests -Dsurefire.failIfNoSpecifiedTests=false test
npm --prefix frontend run test:api-contracts
npm --prefix frontend run test:layout-overflow
npm --prefix frontend run test:list-refresh
Set-Location frontend
npx eslint src/views/case-management/caseManagementFeature src/views/test-plan/testPlan src/views/case-management/caseReview src/views/test-asset src/components/pure/ms-table --ext .vue,.ts
npm run build
Set-Location ..
mvn -f backend/pom.xml -pl app -am -DskipTests package
```

命令不代表已通过；新增followup测试在实现后补准确类名。现有lint脚本带--fix，纯检查用上述不修复命令；build含vue-tsc，不因本文变化重跑。镜像/迁移/启动沿[资源运行手册](implementation-resource-runbook-20261022.md)配置，实施记录写实际tag、命令、健康响应，不猜新compose路径。

保持“低资源单实例启动→全部迁移→健康检查→串行页面与MCP”，不同时启动多套环境，不自动重启ms-dev容器。不能运行的项说明原因、风险和操作命令，不能以静态检查代运行证据。

### 12.3 本轮实际完成及未执行

执行了rg源码检索、Get-Content读取、git rev-parse HEAD、方案和证据索引写入、文档引用检查。未编译、未执行单元/集成测试、未迁移/构建镜像/启动容器、未访问远程数据库或重测线上，因为本轮是方案复核且未改业务代码。风险仍是原14问题、待定业务口径及实际数据冲突；源码只能证明现有结构。

实际静态核验：Python逐文件/符号定位生成39处源码索引，全部匹配；Python检查本文和索引的50个本地链接，缺失0，退出码0。该检查仅验证引用可定位，不验证SQL运行结果或业务正确性。

本轮修改本文、新增[源码复核索引](msp-integrated-code-audit-20261023.md)和上一版存档；上游文档原有链接继续指向同一路径。不改业务SFC/API/SQL，不提交推送。**交付状态：方案复核完成；功能实施未完成。**

## 13. 进入相关实现前需要定稿的口径

| 口径 | 推荐 | 影响 |
| --- | --- | --- |
| 重复导入范围 | 项目内显式组，多组明确选，不按用户隐式分割 | 组查询/默认目标/权限 |
| 来源资产编辑 | 保留功能，源字段回写或脱离需明确选择，本地元数据独立 | 编辑API/冲突/批量范围 |
| 置顶范围 | 默认个人偏好，共享另授权 | 唯一键/排序/权限 |
| 同名项目与历史共享目录 | 历史先保留，新增按项目身份，重名后缀可区分 | 唯一约束/display_name/数据拆分 |

以上不是用户已确定行为，不应伪装已确认。它们不阻止先做不依赖口径的UI、名称、全选修复；本次请求仅重新分析方案，不自动执行业务改造。

## 附录A：实施前的数据预检契约

以下为依据现有字段拟定的**只读查询模板，未对数据库执行**。`:projectId`由DAO/SQL客户端绑定明确项目，禁止直接去掉范围跑全库。先验证表/索引存在，再在隔离库或受控环境执行并检查EXPLAIN；输出只含ID/名称/状态，不含Token、导入payload或附件凭据。

```sql
-- 1. 项目目录关系与可信module来源；用于确认“已建目录”不等于映射完整
SELECT p.id AS project_id, p.name AS project_name,
       r.catalog_id, c.name AS catalog_name, c.hub_module_id,
       c.manually_renamed, m.ref_project_id, m.module_type
FROM project p
LEFT JOIN case_asset_catalog_project_rel r ON r.project_id=p.id
LEFT JOIN case_asset_catalog c ON c.id=r.catalog_id AND c.deleted=0
LEFT JOIN functional_case_module m ON m.id=c.hub_module_id
WHERE p.id=:projectId AND p.deleted=0;

-- 2. 同一当前源在实时/历史映射中是否指向不同资产
SELECT f.id AS source_physical_id, COALESCE(NULLIF(f.ref_id,''),f.id) AS source_ref_id,
       d.hub_case_id AS realtime_asset_id, s.asset_case_id AS history_asset_id
FROM functional_case f
LEFT JOIN default_hub_case_map d ON d.biz_case_id=f.id AND d.biz_project_id=f.project_id
LEFT JOIN case_asset_source_relation s ON s.source_project_id=f.project_id
  AND s.source_case_id=COALESCE(NULLIF(f.ref_id,''),f.id)
WHERE f.project_id=:projectId AND f.deleted=0 AND f.latest=1
  AND d.hub_case_id IS NOT NULL AND s.asset_case_id IS NOT NULL
  AND d.hub_case_id<>s.asset_case_id
ORDER BY f.id LIMIT 100;

-- 3. 不读取payload的副作用积压诊断；最早时间用于区分短延迟和长期失败
SELECT effect_type,state,COUNT(*) AS total,MIN(create_time) AS oldest,
       MAX(attempts) AS max_attempts
FROM functional_import_effect_outbox
WHERE project_id=:projectId
GROUP BY effect_type,state;
```

预检产物应包含：项目目录缺失/共享/异源冲突清单、源物理ID与稳定refId对照、两种资产ID冲突与引用数、未完成任务水位、拟合并映射及回滚对应。没有该清单，不执行“清重”或直接切换同步写入者。SQL返回0冲突也不能证明附件/字段全量一致，还需逐字段抽样和真实更新测试。

## 附录B：按结果判定而非按接口成功判定

| 场景 | 必须观察的最终结果 |
| --- | --- |
| 首次导入2条，1条失败 | 只关联成功目标；计划和评审各1条；导入失败行可下载具体原因 |
| 同一job提交/重试两次 | 目标计划/评审ID不变，关联不重复，原用例不再次创建 |
| 第一次2条，第二次追加2条 | 原目标仍存在，新增成功ID增量加入；旧执行与评审意见不重置 |
| 第二次选择新建 | 新的一对只关联第二批成功ID，第一对不变 |
| 两次都选否 | 只完成导入，无新目标、无额外关联 |
| 计划已成功、评审失败后重试 | 复用原planId，仅补失败分项，不产生多余计划 |
| 源软删再恢复 | 当前列表/计数先减少再恢复；原历史和关联不意外丢失 |
| 目录A移入B再移根 | 源与hub树最终一致，子用例ID及计划关联不变 |
| 全选筛选结果后排除一条 | 实际处理N-1条，跨页无遗漏，过滤外用例不受影响 |
| 历史/实时映射冲突 | 先展示迁移预览，已有业务引用可追溯，不靠删资产隐藏冲突 |
| 源附件删除或等级更新 | 对应资产不残留旧关联/旧等级；定义与执行事实不串写 |
| 置顶跨页和重新登录 | 服务端排序稳定，生效范围符合最终个人/组织规则 |


## 14. 本轮确认与实施偏差（2026-10-23）

用户已确认以下规则，覆盖上文仍标注“待确认”的候选建议：

1. 重复导入只查当前项目的明确关联组，多组时由用户选择，不按名称猜测。
2. 来源资产保留编辑，由用户明确选择回写源或脱离来源；回写仍须源用例编辑权限。
3. 目录置顶对组织所有成员生效，使用目录更新权限；不采用个人偏好表。
4. 历史共享目录保留；新增项目按项目身份独立关联，同名允许加项目短 ID 后缀。

当前实现与验收证据统一见 [实施记录](msp-integrated-implementation-20261023.md)。资产批量首版采用持久化、请求驱动的分片任务，每个请求一个独立事务处理一条，当前页明确选择最多 100 条；关闭页面暂停，重新进入可继续，失败项可重试。这是为低资源环境采用的实施调整，跨页批量和无人值守后台推进尚未实现，不计为完整交付。资产 XLSX 导出复用原异步导出服务，当前限定明确选择的目录内用例；尚未实现资产筛选条件下的所有页导出。
