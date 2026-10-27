# MSP 两轮验收结果汇总与详细修复方案

日期：2026-10-27。代码核对基线：`580b7aed02`，主要功能提交 `fd09cbc8e1`。

**本文是待实施方案，不是修复完成报告。** 本次只整理测试结果、复核代码和制定方案，不修改业务实现、不发布、不更改测试环境配置。

## 1. 依据、去重与交付边界

### 1.1 两轮结果

| 来源 | 实际范围 | 结果与证据 |
|---|---|---|
| [第一轮需求验收](../testing/msp-acceptance-20261027/report.md) | 页面、同步、导入、资产、原生 MCP、连接器契约 | 36 条主需求：16 通过、8 失败、1 阻塞、1 待确认、10 未完整执行；发现 D01～D10 |
| [第二轮剩余补测](../testing/msp-remaining-20261027/report.md) | 本周代码核对、Demo 对照、1280 窄屏、自动刷新、旧链接、跨项目、MCP 边界/并发契约 | 27 个子检查：17 通过、7 失败、2 阻塞、1 待确认；新增 D11～D13、配置阻塞 E01、原型问题 P01；补充 D09 的实际写入失败 |
| [累积需求状态](../testing/msp-remaining-20261027/cumulative-requirements.json) | 同一套 36 条需求，未改变分母 | 17 通过、9 失败、1 阻塞、9 未完整执行；通过覆盖 17/36=47.2% |

两轮统计不能相加：第二轮含对第一轮主需求的子项补测；同一个 UI 问题存在多处差异。归并后为 **13 项产品/接入问题 D01～D13、1 项环境配置阻塞 E01、1 项原型问题 P01**。未验证内容另列验收债务，不伪造新缺陷，也不当作通过。

需求基准：

- [现网问题详细方案](msp-live-issues-detailed-remediation-plan-20261023.md)。
- [整合交付方案](msp-integrated-delivery-plan-20261023.md)，第 14 节已确认规则优先。
- [个人执行与证据方案](personal-execution-case-evidence-implementation-plan-20261027.md)。
- [最终确认的样式 Demo](../demo/msp-real-frontend-style-review-20261023.html)及[说明](msp-real-frontend-style-review-20261023.md)。

### 1.2 已通过能力保护清单

修复时必须保留：用例级一张图片覆盖多步骤；客户端无必填 Hash/逐步骤图片；100 步原始覆盖、ERROR 聚合；真实受控图片读取；写请求同 key 重放与当前授权复核；计划 A/B 隔离、进入/返回及可见页自动更新；旧生成页兼容跳转；分页四档；按钮常驻；导出右侧全选所有页及原下拉；计划/评审名称跳转；导入新建/追加/仅导入及源删除恢复关联。

“采用 Demo 样式”不能删减原入口、权限条件、列配置、编辑与执行功能。组织目录置顶、明确关联组、来源资产显式回写/脱离、同名项目以身份独立关联等已确认规则不重开选项。

### 1.3 根因可信度

区分三个层次：①测试已复现；②当前源码可确定的缺陷；③线上根因待日志/版本核对。线上前端清单 revision 不在当前本地对象库，后端 SHA 未提供；不能直接认为本地代码与线上逐行一致，也不能仅凭 SHA 不同认定发错包。

本次新增代码定位：

1. **分类 INSERT 确定不匹配。** `TestAssetGovernanceService.create` 为 13 列，VALUES 为 14 个表达式（13 个占位符加一个常量），实际绑定 12 个参数。应是 12 个占位符加一个常量。与分类创建 500 高度一致，线上仍用 trace 核实。
2. **导出身份契约确定不一致。** 工具/DTO/任务层仍为 64，任务正则只接受 `[A-Za-z0-9_-]`，不接受其他 MCP 已支持的 `.`、`:`。首轮导出请求 `acceptance.20261027:export` 会被当前源码拒绝；无需先猜文件生成器出错。
3. **Node 创建工具也确实缺字段。** `caseWrite.ts` 中用例/目录/批量创建 schema 没有 requestId，`client.ts` 直接透传，没有补齐稳定身份。D09 不能仅靠“重连清缓存”收尾。
4. **持续同步诊断有确定盲区。** `CaseAssetService.syncFailures` 只列已关联目录、attempts>0 的 PENDING/FAILED；未入队、尚未尝试、目录关系缺失等情况会显示空数组。
5. **嵌套目录版本在公共构树时丢失。** `project-management/.../ModuleTreeService.buildTreeAndCountResource` 对子节点用四参构造函数新建 BaseTreeNode，只复制 id/name/type/parentId；updateTime、projectId等原属性没有带入。这解释了顶层正常、嵌套节点缺revision的差异。公共构树修改需扩大相关目录回归。

## 2. 问题追踪与整体影响

| ID / 优先级 | 影响用户与业务 | 修改范围 | 数据/配置影响 | 关联需求与验收 |
|---|---|---|---|---|
| D01 P1 | Excel 导入预检请求风暴，策略不出、无法提交 | 导入弹窗、分页监听、请求生命周期 | 默认不改表 | IMP-01/02；AT19、T30 |
| D02 P1 | 个人 Token 不能完整查看启用/操作列 | 个人中心容器、MsTable 滚动布局 | 无 | REG-UI-01；宽窄屏操作可达 |
| D03 P1 | 资产导出任务失败，不能获得 XLSX | 资产导出授权、快照、生成、存储、任务错误展示 | 视日志决定，不预设缺表 | ASSET-BATCH；真实文件内容验收 |
| D04 P1 | 原生 MCP 导出请求内部错误，长 key 不一致 | 工具、DTO、任务校验、Node、错误映射、迁移 | export_task.request_key 扩到128 | X03/X05；T23/T31 |
| D05 P1 | 源用例未持续进入资产，诊断空白 | 项目目录身份、outbox、镜像、诊断、补偿 | 复用现有映射/事件，必要时补状态字段 | AST-01；增改删恢复及失败原因 |
| D06 P2 | 等级已保存但列表显示“-” | 资产字段响应/展示映射 | 不改实际等级数据 | ASSET-BATCH；中英文读写一致 |
| D07 P1 | 子目录移入后无法移出，重复报错 | 递归树 DTO、节点转换、移动及错误层 | 原则上无新表 | UX-01；子树/根/冲突/权限 |
| D08 P2 | 追加既有计划评审组仍隐式要求评审人 | 导入编排、前端选择、原生/Node schema | 通常无迁移，旧任务兼容 | IMP/X04；T30 |
| D09 P1 | 当前连接器无法创建用例/消费新证据契约 | Node 工具及 client、包发布、连接器发现 | 包版本/连接刷新 | X10；T35及真实客户端写入 |
| D10 P2 | CASE 证据错误仍说“本步骤”，误导逐步配图 | 安全错误上下文与文案 | 无 | X01/X05；CASE/Runner分别验证 |
| D11 P1 | 正式 UI 未采用已确认布局 | 原 case/plan/review SFC、局部样式、通用组件可选布局 | 无业务表变更 | UI-BASE；结构/交互/视觉 |
| D12 P1 | 新建分类500，阻断非空批量分类 | 分类服务 SQL、真实 MySQL 测试、抽屉错误展示 | 修 SQL；不默认重建分类表 | ASSET-BATCH/分类治理 |
| D13 P2 | 同名目录输入产生内部错误 | 目录业务码、MCP/HTTP错误映射、前端提示 | 无；若补唯一约束先审计重复 | UX/MCP安全错误 |
| E01 配置阻塞 | 敏感执行事实无法加密发布 | 部署密钥配置、能力检查、运维与恢复 | 密钥长期保留，严禁明文入库 | T42；安全拒绝本身不是缺陷 |
| P01 原型问题 | 优化开关未真正生效，视觉基线不可靠 | Demo 根包装、切换验证、基线记录 | 不连业务接口 | UI-BASE参考基准 |

所有详细节中的文件路径均相对仓库根目录。未明确列入的模块不做全局重构。

## 3. 分项详细方案

### 3.1 D01：导入预检请求循环

**事实与影响：** 首轮 job `9d548938-946a-4d91-a611-a43532727d56`，约67秒3443次 job/options 请求，部分被取消。影响当前弹窗可用性，也会给同环境其他请求带来压力。不能用增大超时掩盖循环。

**修改范围：**

- `frontend/src/views/case-management/caseManagementFeature/components/import/importJob.vue`。
- `frontend/src/hooks/useClientTablePaging.ts`（仅在确需暴露稳定分页 ref 时改；须回归其他调用者）。
- 同目录 `ImportFollowupChoice.vue`、`importFromDefaultModal.vue`：核对是否有相同监听/策略校验，避免两套入口行为不同。

**具体内容：**

1. 把返回新数组的 getter watch 改成两个独立标量源：`watch([() => current, () => pageSize], ...)`。`job.total` 更新不应导致相同分页重复 load。
2. 为一次打开定义 generation，捕获 jobId、组织/项目、分页；响应只在上下文仍一致时应用。不同 job 重置策略；同 job 的普通轮询不得清除用户选择。
3. 将 `loadJob`、`loadFollowupOptions`、`loadReviewers` 分开，options 用 jobId 缓存单个在途 Promise，失败解除占位以允许手动重试。分页变化只查条目，不重拉已经成功的策略/人员。
4. 仅 QUEUED/RUNNING 或确实 PENDING 的关联任务按现有3秒节奏轮询；纯 PRECHECKED 不设轮询。请求完成后再调度下一次，禁止重叠计时器；关闭/换 job/卸载取消或废弃旧响应并清定时器。
5. 分开错误状态：策略失败、人员失败、任务失败。人员失败时“仅导入”仍可选；提交是否需评审人按 D08 的策略分支判断。
6. 取消请求不弹业务失败 Toast；同一失败只由约定错误层展示一次。提交有 in-flight 锁，未知结果沿用原任务/请求身份查询。

**验收：** PRECHECKED 无操作停留10秒，除初始化/用户触发外没有新增查询；翻页一次只产生对应查询；快速 A→B 不串任务；关闭后不再请求；新建/追加/仅导入、仅重试关联均真实完成；超时、无权限和重试状态正确。请求数量按实际动作定义预算，不用任意放宽阈值。

**风险与回退：** 分页偏好异步恢复可能触发一次必要读取，应去重而非禁用偏好。回退只回前端，不停止已提交后台导入任务。

### 3.2 D02：个人 Agent Token 操作被裁切

**事实与影响：** 1908px 下表从 x444 开始宽1660，操作列超容器；971px也复现。影响创建后维护、禁用和删除入口；不改变 Token 权限语义。

**修改范围：** `frontend/src/views/setting/system/agentIntegration/index.vue`；按实际故障链审查 `frontend/src/components/pure/ms-table/{base-table.vue,useTable.ts}`、`frontend/src/components/pure/ms-card/index.vue` 及个人中心父容器。公共组件仅增加可选/正确透传能力，不全局清除 overflow。

**具体内容：**

1. 固定列表 viewport 为父容器可用宽度，flex 子项一路 `min-width:0`；确认 `scroll.x=1660` 最终传到 a-table，未被 `$attrs`、`scrollObj`、`viewportScroll` 的合并顺序覆盖。
2. 表格内容宽度和 viewport 分离，由 Arco 实际滚动容器承接横向溢出；容器外层不再把末列裁掉。不得只给最外层加滚动而让“固定右列”跟随整张表滑走。
3. 启用列和操作列作为右侧固定区，明确总宽240px；长 Token 名称/权限范围有省略与完整提示。窄屏允许表内横向滚动，不隐藏业务列。
4. 横向滚动条处于可达区域；需要时提供 sticky 滚动承接，但不创造两条不同步滚动条。启用操作失败回到原值，按钮 loading 防重复。
5. 几何断言同时计算列与可见 clip 区交集，并用键盘/点击验证，不能只看配置里写了 fixed。

**验收：** 1920/1366/1280及约971、200%缩放、空/多行/长文本；横向滚动至两端后操作仍可见；用本轮临时 Token 真正启用/禁用并刷新持久化。公共表格若修改，回归用例、计划、评审和资产，防固定列/分页回退。

### 3.3 D03：资产导出任务失败

**事实与影响：** task `28ae898b-3b35-4922-85dd-1a5b3b78d6d0`，FAILED/EXPORT_FAILED/count=0。不能仅凭 HTTP200或任务创建成功关闭问题。

**修改范围：**

- `backend/services/case-management/src/main/java/io/metersphere/functional/asset/{controller/CaseAssetController.java,service/CaseAssetService.java,service/CaseAssetExportAccess.java}`。
- 同服务的 `functional/service/FunctionalCaseExportTaskService.java`、`FunctionalCaseFileService.java`、`FunctionalCaseExportRowMapper.java`，及日志命中的文件生成/存储实现。
- `frontend/src/views/test-asset/cases.vue`、导出任务 DTO/API；已有 `FunctionalCaseExportMySqlTests`、`CaseAssetExportAccessTests`。

**先诊断后改动：** 按 taskId 查快照阶段与生成阶段日志、请求快照、资产目录范围、选中ID、导出列、用户/组织上下文、存储结果。分别判定授权、列解析、快照持久化、XLSX生成、对象存储写入，禁止把 D03 与 D04 直接认成同根因。

**具体内容：**

1. 资产目录授权与业务项目授权分开，后台仍复核当前用户组织/目录权限及快照用例归属；不得为了导出通行扩大到枢纽项目全部用例。
2. 列 id 在冻结前按模板元数据校验/归一化；优先复用现有列解析，不把 `{id,name}` 对象误作字符串，也不靠中文列名识别字段。来源资产与人工资产都走真实行映射。
3. 修复日志命中的实际故障；补对应真实数据库/文件测试。已有任务幂等、租约、终态 CAS、清理机制保留。
4. 失败响应保留 taskId/traceId、安全业务码；区分无可导出项、权限变化、列失效、存储暂不可用与未知故障。详细异常仅日志记录，不把 SQL 或堆栈回传。
5. Web 可查看任务失败原因和显式重试；重试沿用已冻结选择/字段的既有重试协议，明确读取当前内容并关联 sourceTaskId。原请求重放返回原任务；显式重试使用新请求身份，二者不得混淆。
6. 支持真实 XLSX 下载并用解析器检查行数、编号、名称、等级、标签、步骤、中文/换行。文件下载仍做当前授权与期限校验，不发公共存储 URL。

**验收：** 人工/来源资产、当前选中/筛选、空集、无权限、执行中撤权、字段变更、生成失败后恢复重试、下载过期/越权。无日志前该修复项保持“待定位”，不能用重启存储或全量重建代替根因修复。

### 3.4 D04：导出 requestId 统一与安全错误

**当前确定问题：** `AgentCaseExportTools` schema max64；`FunctionalCaseExportRequest` @Size64；任务 start/retry 使用 `[A-Za-z0-9_-]{1,64}`；数据库 `export_task.request_key` 为 VARCHAR64。其他个人 MCP 接受 `^[A-Za-z0-9_.:-]{1,128}$`。首轮“公共契约合法”的点号/冒号请求，在当前导出实现会被拒绝并落入内部错误。

**修改范围：** 上述工具/DTO/任务服务、`FunctionalCaseExportRetryService`、`AgentSafeErrorMapper`；`metersphere-mcp/src/tools/caseExport.ts` 及 client 参数类型；导出相关 HTTP Controller；新的 Flyway 迁移。

**具体内容：**

1. Java、原生 JSON schema、Node Zod 共同使用长度1～128、上述字符集、区分大小写的身份规则。校验在副作用发生前执行，不 trim、不截断、不转小写，不为错误请求自动生成另一 key。
2. 新迁移将 request_key 扩为 VARCHAR128，保留二进制比较和原唯一索引语义。核对现有 charset/collation、索引长度及历史 NULL/64位 key，不改已执行迁移，不清空幂等记录。
3. start、retry、REST和原生路径一致；非法 key 返回 AGENT_VALIDATION_ERROR/字段 details，变参返回 IDEMPOTENCY_CONFLICT，处理中/未知结果有明确可重试语义。不要仅扩大 schema 而漏数据库/任务层。
4. 对缺 key 的 Web 兼容调用，可保留既有服务端生成 UUID 的行为；要求客户端幂等的 MCP 写工具必须显式传 requestId。请求重放继续校验当前授权和业务对象，不从缓存旁路权限。
5. 任务 hash 对对象键序稳定；导出列顺序有意义，不能为“规范化”排序列数组。选择ID集合是否归一化按已批准通用契约复用，不改排序/排除语义。
6. 使用原失败参数重验；若身份规则修复后仍在生成阶段失败，继续按 D03 的诊断链定位，不把参数修复宣称为文件链路通过。

**验收：** start/retry 的1/64/65/128成功、129及空白/非法字符拒绝，点/冒号可用，大小写独立；同 key同参返回原任务、变参冲突；并发一次任务；原64位任务仍可查/下载/重试。读真实文件验证内容。

**迁移与回退：** 数据库扩列先于新应用；新增长 key 后不允许直接缩列，应用回退也应保留扩展列和旧记录。不能删除任务来“修复”冲突。

### 3.5 D05：资产持续同步及诊断闭环

**事实与影响：** 源项目已有11条，资产只见人工数据；历史同步重试成功不能证明持续镜像收敛。涉及 Web、MCP、文件导入、附件、删除/恢复及版本切换。

**修改范围：**

- `backend/services/case-management/src/main/java/io/metersphere/functional/asset/service/{CaseAssetService.java,CaseAssetHistorySyncWorker.java}`。
- `functional/hub/service/{DefaultHubCaseSyncService.java,DefaultHubModuleResolver.java,DefaultHubCaseImportService.java}` 及 `DefaultHubCaseMapDao`。
- `functional/service/{FunctionalImportEffectOutbox.java,FunctionalCaseService.java,FunctionalCaseAttachmentService.java,FunctionalCaseTrashService.java}`；MCP直接写入时遗漏的现有 outbox 调用点。
- `frontend/src/views/test-asset/{cases.vue,asset-case-detail.vue}`、对应 API/DTO。

**定位顺序：** 指定源项目→目录 project_rel/hubModuleId→最新物理用例与逻辑 refId→outbox CASE_SYNC 是否入队/调度→镜像 map/source_relation→列表过滤与诊断关联。只读核对每一步及日志；映射冲突先输出修复候选，不合并或覆盖历史共享资产。

**具体内容：**

1. 源变更和 outbox 入队同事务；覆盖创建、编辑、批改、Excel/资产导入、MCP更新/发布、附件新增删除、软删恢复和版本变更。未提交事务不能被消费，投递失败不能假报源操作回滚。
2. 项目目录按组织+项目身份定位，同名显示后缀；源逻辑身份与物理版本分别处理。复用现有目录与映射，不再另建一套同步表或靠名称匹配。
3. 消费单个用例时锁定/读取最新版本，幂等 upsert；事件重复或乱序不得覆盖较新版本。一次事件内镜像、附件映射、来源关系与事件完成状态一致；事务粒度限制为单项，避免整项目长事务。
4. 软删形成镜像墓碑，恢复复用同一镜像身份；旧物理版本删除不能删当前新版本镜像。来源绑定字段回写仍需源编辑权限和版本校验；脱离与后续独立镜像遵循原方案，不能把资产分类、置顶等独立元数据被源覆盖。
5. **补诊断盲区。** 不能仅返回 attempts>0 的失败事件。增加按目录授权的同步摘要：配置开关、目录身份、待同步/重试/失败数量、最早待处理时间、最后成功时间；区分 NOT_SCHEDULED、WAITING、RETRYING、FAILED、DISABLED、UP_TO_DATE，状态名作为拟定 DTO，实施时与既有协议对齐。查不到事件不得推断已同步。
6. 每条异常提供源用例/事件引用、尝试次数、可理解原因、traceId、下次重试或可重试标志。原始异常进入日志。目录关系缺失由项目授权后的诊断显示，不借诊断泄露其他目录数据。
7. 显式补偿扫描分页取差异、只补遗漏/过期事件，有限批次串行执行；失败重试仍校验当前权限。提供 dry-run 数量/映射冲突清单，再执行可审计修复；历史成功任务不改成全局健康状态。
8. 根据现表能否表达上述时间/版本再决定加字段/索引；先复用 `functional_import_effect_outbox`、`case_asset_catalog_project_rel`、`case_asset_source_relation` 和 map。新增字段使用新迁移，避免无依据扩表。

**验收：** 每个变更入口至少一条隔离源数据，观察同一资产ID在约定时限收敛；重复/乱序事件无重复；关闭同步、缺目录、无权限、附件复制失败都能解释；恢复配置后只重试失败项。跨项目/组织诊断拒绝；引用、手工分类及组织置顶不受同步覆盖。失败注入只在隔离CI，不在共享测试平台杀进程。

### 3.6 D06：等级显示与字段契约

**确定原因：** `frontend/src/views/test-asset/cases.vue:getPriority` 读取中文 fieldName 与 value；响应实为 `internalFieldKey=functional_priority`、`defaultValue=P1`。

**修改内容与范围：**

1. 优先采用后端明确 `priority` 字段；若 DTO 未统一，在现有资产 DTO 映射补标准字段。前端兼容层按 internalFieldKey 找值，正确区分 value/defaultValue，不依赖中文名；字段缺失显示“-”，非法值不默认P0。
2. 优先复用项目已有自定义字段读取工具；统一列表、详情和导出对等级的解析，不复制三份不同规则。涉及后端时限定资产响应/行映射，不改所有自定义字段含义。
3. 批改完成刷新当前筛选分页，重新读取服务端结果；仅更新本地文本不能算持久化通过。

**验收/风险：** P0～P3、null、历史 value、defaultValue、新顶层 priority及中英文；列表/详情/下载内容一致。无迁移，不改已经正确保存的P1数据。

### 3.7 D07：递归目录 revision 与移动失败

**修改范围：** `FunctionalCaseModuleService`、`functional/mapper/ExtFunctionalCaseModuleMapper.xml`、`system/dto/sdk/BaseTreeNode.java`、`project/service/ModuleTreeService.java` 中实际递归节点构造；前端 `caseTree.vue`、`featureCase.ts`、树节点模型和错误处理。

**具体内容：**

1. 当前 mapper 已查询 update_time、BaseTreeNode 已有 updateTime，不能简单再加同名字段。已定位公共构树的子节点四参重建遗漏版本：改为显式完整复制 updateTime/projectId/attachInfo等应保留属性，children重新按本次树构建，路径按父节点重算；不共享可变children/attachInfo引用。补SQL→构树→嵌套JSON→前端转换逐层断言。公共方法调用者包括其他业务目录，必须检查对新增非空字段/路径的兼容性。
2. 所有真实层级节点都带 revision，虚拟“全部/未规划”不作可移动实体。打开移动弹窗时读取当前节点版本；若前端缓存没有版本，刷新节点后再允许确认，不用0/null或当前时间代替。
3. 请求包含源节点、目标父节点和 expectedUpdateTime；服务端项目树锁内重新读取并校验版本、权限、同项目、非自身/后代及重名，再移动整个子树。核对当前 Web 路径版本检查是否与实际更新同一锁/事务，避免检查后才锁导致竞态；复用 MCP 同一领域方法。
4. 返回更新后的节点和 revision，前端原子更新/重载树，保持选中和展开；旧响应不覆盖新树。冲突保留用户目标选择并提示刷新，不能自动换版本强制覆盖。
5. 400缺参应被前端拦截并可恢复；业务冲突用稳定码。由一个错误层提示，修复同 trace 双 Toast；请求取消不显示失败。

**验收：** 至少三级树移入/移出至根、子树计数不变；移动后刷新再移动；同父/后代/跨项目/无权限拒绝；旧 revision冲突；两个并发移动不能同时覆盖。测试真实递归 JSON，不只测顶层节点。无必要不改共用树组件其他模块。

### 3.8 D08：追加关联组的评审人契约

**修改范围：** `ImportFollowupCoordinator`、`ImportFollowupRequest`、`AgentCaseImportTools`；`importJob.vue`、`ImportFollowupChoice.vue`、`importFromDefaultModal.vue`；Node导入工具及契约测试。

**具体内容：**

1. CREATE_NEW_PAIR：必须显式 reviewers 与通过规则，并验证人员属于当前项目且可选；创建/关联权限全校验。
2. APPEND_EXISTING：以 current project 的显式 groupId+revision定位既有plan/review，读取该评审当前人员、通过规则及状态；不要求外部重复提供人员，也不允许顺手重配既有评审。若旧客户端仍传相同值可兼容忽略，传不同值明确拒绝并提示去专门编辑入口。
3. 注意现有 `permissions()`、`advance()`、`reviews.associateCase()` 都使用 request.reviewers，不能只删除校验。追加时构造服务端有效评审上下文用于逐批关联，任务重试重新检查当前状态/权限；已完成项不重复评审或清执行历史。
4. IMPORT_ONLY不校验或创建关联；前端只在 CREATE_NEW_PAIR显示必填人员/规则，追加显示只读目标摘要，多组必须选择，不按名称猜测。
5. schema说明条件必填，校验器支持时用oneOf/分支schema；否则业务层也必须完整校验。选择组revision失效返回明确冲突，不能自动改为新建。
6. 旧任务反序列化容忍缺少新增可选字段；未成功的关联可以单独重试，不重新导入成功用例。若固化 reviewer 快照，仅作审计，执行时授权不靠快照放宽。

**验收：** 两端新建/追加/仅导入；追加不传人员成功并沿用ID；目标归档、人员被移除、组revision变化、无关联权限明确失败；导入部分成功后只关联成功项；仅重试失败项不重复plan/review/case。

### 3.9 D09：原生、Node、已连接客户端一致性

**影响范围：** 当前连接器无法创建用例，执行证据仍被旧 schema 限制；不仅是说明过时。Node源码部分已升级，但 `caseWrite.ts` 的创建入口仍缺 requestId，不能宣称全部是缓存问题。

**修改范围：** `metersphere-mcp/src/tools/{caseWrite.ts,caseLibrary.ts,planReview.ts,caseExport.ts,submitFunctionalResult.ts,submitFunctionalResultsBatch.ts}`、`client.ts`、`index.ts`、包版本/README/test；原生工具 schemas和下载技能包仅按比对差异修改。

**具体内容：**

1. 建立 public tool→Node schema→Node handler/client→REST/原生handler→领域操作名清单，检查所有写入口 requestId是否必填、透传、只生成一次及重放语义。特别补创建用例、目录、批量创建，不在网络重试内部生成新 key。
   同步审查 `caseLibrary.ts` 导入预检仍为64字符/旧字符集的公开身份分支：按统一公共契约调整；若后端将公开键映射为内部短hash，保留内部身份算法及历史记录，不能误把内部hash长度当作客户端限制，也不能盲目扩大所有同名数据库列。
2. 原生为当前正确契约参考，仍以需求为最终基准；CASE证据 Hash/stepNum/purpose非必填、case evidenceIds 与1000事实上限、读图真实image保持一致。不能因旧客户端限制倒退后端门禁。
3. 包含新建/追加/仅导入、operation.get、attachment.read、导出边界；确认client错误同时保留HTTP/JSON-RPC业务码、message、traceId，超时明确UNKNOWN。别将后台异常对象包装为成功文本。
4. 发布新包并明确版本，检查实际运行入口/dist与版本一致；然后刷新/重连已有连接器并重新 tools/list。缓存重连只是最后一步，必须先修包。不要记录或替换用户既有Token。
5. 契约自动比对 required/类型/枚举/长度/字符集与 image返回；工具名别名可不同，但映射必须显式且保持幂等操作身份，不能因为重命名工具重复写入。

**验收：** 同一真实客户端完成创建→查询→一图多步骤上传提交→取原图→原 key 重试→operation.get；新包与原生执行相同输入得到相同业务语义。仅Node编译通过、原生通过或技能zip文字正确都不足以关闭D09。

### 3.10 D10：证据提示区分 CASE 与步骤

**修改范围：** `AgentSafeErrorMapper`、`AgentDirectExecutionService`中实际抛错点及 Node错误映射；Runner/GlobalQualityGate仅做必要上下文适配，不改变租约/断言规则。

**具体内容：**

1. 在异常结构提供安全的 evidenceScope上下文，或定义个人CASE专用稳定错误码；不要把全局QUALITY_EVIDENCE_*文字一律改成CASE而误导Runner。
2. CASE缺图提示“请为本次用例执行补充有效图片证据，一张图片可覆盖所有步骤”；格式/大小非法提示对应字段要求；不要求人工技术Hash。
3. STEP/Runner保持步骤定位及原策略，文案不冒充AI已验证真实执行。traceId继续可复制，details不含URL签名/密钥。

**验收：** CASE无图/坏图、Runner缺步骤证据分别得到准确文案；业务码和拒绝行为不变，补证后可按既有身份安全完成。

### 3.11 D11：落实已确认的真实前端样式

**修改范围：**

- `frontend/src/assets/style/management-review.less`。
- `frontend/src/components/pure/ms-table/{base-table.vue,batchAction.vue}`、`ms-split-box/index.vue` 的可选布局参数。
- `frontend/src/views/case-management/caseManagementFeature/{index.vue,components/caseTable.vue,components/tabContent/tabDetail.vue}`。
- `frontend/src/views/test-plan/testPlan/{index.vue,components/planTable.vue,detail/index.vue,detail/plan/index.vue,detail/featureCase/components/caseTable.vue}`。
- `frontend/src/views/case-management/caseReview/{index.vue,detail.vue,components/index/reviewTable.vue}`。

**具体内容：**

1. 给批量区增加真实、语义明确的 class/hook，修复当前“类选择器匹配id”问题；不要复制全局重复id继续做样式耦合。公共表格新增可选 `batchActionPlacement`（名称为方案建议），试点页采用表格上方，其他模块维持默认，避免全局布局变更。将批量区从当前footer布局中独立组织，只移动批量区，分页保持表格下方，不能连同整个footer搬到顶部。
2. 只渲染一份批量按钮，复用原选择状态/事件/权限；按钮常驻，零选中禁用；导出右侧全选所有页、原下拉、清空、分页和跨页排除逻辑不变。不把原按钮收进新的“更多”。
3. 试点目录默认宽度大屏252px、1101～1400为220px、1100及以下190px，保留原展开/收起与必要的用户拖动能力。MsSplitBox已提供size/min参数，但默认均300px；试点同时传入匹配的size与min，通过实际参数控制，不用覆盖变换制造假宽度；解除计划页强制 min-width300px。若用户已手调宽度，应定义保留优先级并测试，不破坏目录可读性。
4. 统一主内容20px、操作间距12px、批量区12px内边距/浅底色/边框/6px圆角；表头采用已确认浅色，常规单行行高56px。多行长文本可增高，不强截内容。只作用于试点，深色模式使用语义变量映射相应色，不全局硬写白色。
5. 概览与工具栏允许合理换行；进度多计划时避免挤压搜索。列宽保留用户配置，名称支持完整tooltip，表格宽度由自身滚动承接，操作固定区不会裁切。
6. 用例详情采用正文+属性侧栏的响应式布局，但保留基本信息/详情/前置用例/需求/缺陷/历史及原编辑、分享等全部入口，不能套用Demo的简化字段集合。窄屏顺序折叠为单列，不隐藏操作。
7. 计划详情应用顶部强调与卡片层次，文档原编辑器、自动保存状态、保存/预览/重置/导出保留；生成报告继续按已批准隐藏需求。评审沿用同一视觉规范，注明没有独立已确认评审像素Demo。
8. 先修P01并建立可复核的同视口截图。视觉对比屏蔽仅限时间/示例记录差异，不能遮住本次改动按钮、目录、表头；不得自动接受新截图或扩大阈值消除差异。

**验收：** case列表/详情、plan列表/文档/用例页、review列表/详情，1920/1366/1280及200%缩放；逐项核对边距、宽度、表头、行高、滚动与常驻按钮。操作动作与权限逐项回归，不能以截图相似代替功能测试。D02等共用MsTable消费者纳入回归。

**回退：** 按页面可选布局/局部class回退，不撤销已修的请求和数据逻辑；不存在数据库回退。

### 3.12 D12：分类创建 SQL 与批量分类

**确定修改：** `TestAssetGovernanceService.create` 的 VALUES 从 `VALUES(?,?,?,?,?,?,?,?,b'0',?,?,?,?,?)` 修为与13列匹配的 `VALUES(?,?,?,?,?,?,?,?,b'0',?,?,?,?)`，绑定12个值。用真实 MySQL 的插入、查询与事务回滚验证，不只断言字符串含某段SQL。

**修改范围：** 上述服务；`backend/services/agent-integration/src/test/java/io/metersphere/agent/service/TestAssetGovernanceServiceTests.java`及新增/扩展MySQL集成测试；`frontend/src/views/test-asset/components/TestAssetCategoryDrawer.vue`、批量分类任务及显示作为验收范围，只有实际存在缺陷才改。

**其他改动：**

1. 保留组织范围、父分类权限、同级规范化名称唯一、5层上限、审计同事务；根分类parentId空字符串与现表约束一致。
2. 新增/更新后树和下拉数据刷新，失败保留输入、恢复loading，统一错误只出现一次；不能用本地新增一行伪造后端成功。
3. 分类创建后真实批改到非空分类，再刷新/筛选/导出核对；清空分类SUCCEEDED只证明清空分支，不替代正向分类。
4. 若线上表结构与源码不一致，先核对历史迁移状态并新增修复迁移；此 SQL 问题本身不需要迁移或重建表。

**验收：** 根/子分类、重名、5层边界、父级不存在/跨组织、审计回滚；批量人工/来源资产分类、部分失败、移出分类、刷新及筛选计数。线上复验原trace对应流程，记录新taskId而不清除原失败证据。

### 3.13 D13：同名目录业务错误

**确定边界：** `FunctionalCaseModuleService.checkDataValidity` 已检查同项目/同父/同名并抛翻译后的 `node.name.repeat`；安全映射不识别该自由文案，落成内部错误。补测不同大小写 key + 不同名称成功，不能再归因幂等大小写。

**修改范围：** 目录service/Agent领域适配、`AgentErrorCode`（若复用现有码不足）、`AgentSafeErrorMapper`及HTTP业务错误映射；Node/前端提示、相应测试。

**具体内容：** 重名在领域边界返回稳定 `MODULE_NAME_CONFLICT`（拟定；复用同义现有码优先），message提示同级同名、允许用户改名或明确选既有目录；不同项目/父目录仍按原规则。避免通过匹配多语言字符串决定业务码，不自动复用同名目录掩盖调用意图。未知数据库异常仍保持内部错误，不能把全部DuplicateKey映成目录重名。

并发创建仍持有原项目树锁；如发现无锁写入口，先统一入口再考虑唯一索引。确需加索引时先审计已有重复及排序规则，不批量删目录。测试同key重放与不同key业务重名分别处理，Web/MCP/中英文得到同一语义。

### 3.14 E01：事实加密配置及运维恢复

**实际配置：** `backend/services/agent-integration/src/main/java/io/metersphere/agent/security/AgentFactCipher.java` 读取 `agent.execution.facts-key`，要求 Base64解码后为32字节，采用AES/GCM；缺失时拒绝敏感事实是正确保护，不能去掉。

**配置/代码范围：** 测试部署的Secret注入（环境变量 `AGENT_EXECUTION_FACTS_KEY` 或受控配置源），对应compose/发布模板仅写变量引用；AgentFactCipher、能力健康/管理诊断、运维文档。禁止写实际密钥、日志回显或把测试随机密钥当长期配置。

**执行步骤：**

1. 从受控秘密管理生成并保存32字节随机密钥的Base64值，先验证格式和持久保存策略，再注入所有读写同一数据库的实例；重启后配置保持一致。
2. 验证无敏感事实仍可提交；敏感事实持久化是密文，授权查询在解析后按字段脱敏，引号/换行/JSON结构完整。异常/日志不含明文或密钥。
3. 缺/错误/篡改密钥安全拒绝，不回退明文；失败提交不发布、不消费证据，重试仍按原身份与资源状态处理。
4. 原加密格式只有 `factenc:v1`，没有keyId。**本轮不要直接轮换密钥**；否则历史事实无法解密。未来轮换须另做keyId+旧密钥可读/新密钥可写的兼容方案和迁移验收。
5. 备份/恢复演练包含数据库、对象存储和受控密钥备份的协调，不能在报告放解密值。后台能力诊断仅显示就绪与否，公共健康接口不泄露配置。

**验收/回退：** 重启、双实例同key、历史查询、错误key拒绝、失败后资源状态。发布回退保留原key，不能重新生成；没有解密能力不得清理原密文。

### 3.15 P01：修正原型并固定样式基线

**文件：** `docs/demo/msp-real-frontend-style-review-20261023.html`、必要时同目录交互脚本和说明；无业务API或数据迁移。

**改动：** 将 `:class="{optimized}"` 移到 Vue挂载容器内部的真实根包装元素；保持初始优化选中、原功能结构不变。切换后验证真实 `.optimized` 类、目录宽度、批量区padding/底色发生变化，且按钮列表和选择状态不被重建改变。将交互实验区作为评审附加区域单独标识，不混入正式产品布局目标。

修复后导出同视口原图、关键区域标注、CSS值和来源提交，明确“恢复原已确认目标”而非另换设计。不需要为了修一个开关重做一版新Demo；有偏离原目标的设计才另交评审。

## 4. 共用规范：权限、错误、数据与发布身份

### 4.1 错误协议

复用现有 `{code,message,details?,traceId?}`。业务参数错、版本/重名冲突、资源不可见、无权限、网络未知、服务器未知分开。动态业务错误从类型/稳定码映射，不以中文字符串作唯一契约。Web只选择统一层或局部层之一展示；MCP HTTP、Holder、JSON-RPC都不能吞业务码后返回成功。日志关联job/task/sourceId与trace，技术异常不进前端。

### 4.2 数据修复限制

导出扩列需新迁移；分类SQL修正无必要不改表。映射补偿以只读差异清单、唯一身份、逐项审计和幂等重试执行，禁止清表重建、删历史事件/幂等结果、批量覆盖所有用户资产。继续保留原失败taskId/trace及两轮截图。

### 4.3 发布身份核对

部署前记录前端manifest版本、包SHA/构建SHA、后端镜像digest/commit、Node包版本与连接器实际tools/list。独立构建可不同SHA，但必须有可追溯来源。修复完成后将本地断言和测试环境同版本证据关联，不能拿原生端点的新schema替代旧连接器的实际状态。

## 5. 实施批次、依赖与风险控制

| 批次 | 内容 | 前置/后续 | 退出条件 |
|---|---|---|---|
| A | 日志/版本取证；D01止循环；D12修SQL；D07补revision；D13稳定业务码 | D01与D08共用弹窗需协调；D12解锁非空分类 | 原失败路径及负向用例通过，测试环境无请求风暴 |
| B | D04身份/迁移→D03导出生成；D05持续同步/诊断；D06字段显示 | 先扩列后应用；先修D12再完整批改；D05解锁来源资产回写/脱离 | 真实文件可解析、源镜像收敛、失败可解释 |
| C | D08策略分支、D09 Node/连接器、D10文案、E01受控配置 | 包修好后才刷新连接器；E01不影响普通事实测试 | 同一真实客户端核心链路及安全拒绝通过 |
| D | P01恢复可信原型→D02/D11样式与滚动 | 共用MsTable变动统一回归；业务修复不得被样式分支覆盖 | 逐页同视口对照及原功能无回退 |
| E | 累积未执行矩阵、容器/迁移/健康、测试环境回归 | 不重测无影响的历史构建；共用组件改动扩大相关范围 | 需求—实现—测试—证据逐行关闭 |

可拆小提交，但同一完整链路的前后端/迁移/测试要成组交付；不是只修UI或只改schema。低资源串行：静态检查→单模块测试→CI数据库与镜像→一套环境迁移/启动/健康→ego实际回归。禁止同时起多套环境、共享测试平台故障注入或为跑本机重构建耗尽内存。

## 6. 验收债务：不把“没测完”直接改成缺陷

| 待覆盖范围 | 实施前处理 | 修复后验收 |
|---|---|---|
| SYNC-01草稿/自动保存/慢响应 | 阅读useListRefresh与自动保存状态，区分dirty/saving/saved；现有切页结果不能证明未保存保护 | 控制响应乱序；dirty时不覆盖、恢复后正常更新；A/B/项目变更隔离 |
| SYNC-02/03全部共享字段 | 以既有有效DTO明确共享/计划独立字段；不另造状态真源 | 定义、标签、等级、附件增删/重命名、执行事实、缺陷解绑、版本与统计；失败重试 |
| 来源资产回写/脱离、组织置顶 | D05收敛后准备本轮镜像和第二成员账号 | 源权限、版本冲突、脱离后的新镜像、全组织置顶与撤权 |
| TEXT/旧STEP/未知指纹和策略 | 公布支持能力，不把 unsupported改为默默截断 | TEXT/空预期/101/1001、旧多图、未知版本、未知发布状态 |
| 幂等崩溃/外部副作用/长期保留 | 隔离CI准备旧迁移数据与故障点，复用原T21～47 | 同毫秒身份、响应丢失、撤权、旧URL迁移、七天边界、暂存额度、引用清理/备份恢复 |
| 评论/删除完整UI | 原生评论冲突通过仅作后端证据；预检已可达不等于真人状态机完成 | 编辑冲突保留内容；到期/跨身份拒绝；真人决定不由Agent伪造 |
| UI全页/角色/缩放 | 修P01，建立稳定数据和同视口来源 | 各试点页、全部按钮/列/空态/长文本、角色矩阵、200%缩放、多浏览器 |

沿用原75条AT/T复合清单逐项追加证据，不用本方案的小用例替换原复杂用例来抬高通过率。

## 7. 文件改造与验证交付要求

### 7.1 建议新增/扩展测试

- 导入组件测试：稳定watch、选项并发去重、关闭取消、策略分支和请求预算；真实Web导入E2E。
- `TestAssetGovernanceServiceTests`与真实MySQL分类创建测试，不能只Mock JDBC update成功。
- `ImportFollowupCoordinatorMySqlTests`：追加缺reviewers、当前组人员/权限变化、失败重试。
- `FunctionalCaseExportMySqlTests`、`FunctionalCaseExportValidationTests`、`CaseAssetExportAccessTests`、`FunctionalCaseExportRowMapperTests`：128key、资产范围、实际生成/下载。
- `FunctionalCaseModuleServiceTests`与API树集成测试：嵌套revision、移动与并发；Agent错误映射测试。
- 持续同步真实DB测试：所有入口入队、乱序/重复、墓碑恢复、诊断无事件盲区与映射冲突。
- `metersphere-mcp/test`：所有写schema身份、原生/Node契约对比、真实image与错误透传；不能只有字段存在性断言。
- 扩展 `frontend/e2e/msp-live-acceptance-20261027.spec.ts` 及新样式/资产/导入用例。fixture独立，不把既有用户的Token/资产当可删除数据。

### 7.2 可执行命令与限制

以下是实施后的验证命令，**本次写方案没有运行这些产品测试或构建**。依赖按仓库锁文件和既有CI准备，命令从仓库根目录执行；完整检查放具备资源的CI，低内存本机不强跑。

```powershell
# Node客户端编译与测试
npm --prefix metersphere-mcp test

# 前端类型、非修复性lint、相关现有检查、生产构建
pnpm --dir frontend run type:check
pnpm --dir frontend exec eslint src/views/test-asset src/views/setting/system/agentIntegration src/views/case-management src/views/test-plan src/components/pure/ms-table src/hooks/useClientTablePaging.ts
pnpm --dir frontend run test:list-refresh
pnpm --dir frontend run test:layout-overflow
pnpm --dir frontend run test:api-contracts
pnpm --dir frontend run build

# 后端相关模块（串行；测试选择名单随新增真实测试类更新）
.\mvnw.cmd -T 1 -pl backend/services/agent-integration,backend/services/case-management -am test
.\mvnw.cmd -T 1 -pl backend/app -am package -DskipTests

# 部分真实MySQL测试需要显式开关；先准备其代码要求的隔离MySQL
.\mvnw.cmd -T 1 -pl backend/services/agent-integration,backend/services/case-management -am test -Dquality.mysql=true

# CI已准备target/dependency后，镜像检查
docker build -f Dockerfile.backend -t msp-remediation:20261027 .
docker build -f frontend/Dockerfile -t msp-frontend-remediation:20261027 frontend

# 回归脚本：需私有登录态，不提交凭据
$env:PW_BASE_URL='https://msp.ebcone.net'
$env:PW_STORAGE_STATE='C:/private/msp-auth-state.json'
node frontend/node_modules/@playwright/test/cli.js test --config frontend/playwright.config.ts msp-live-acceptance-20261027.spec.ts --workers=1
```

后端MySQL集成测试须使用仓库测试要求的隔离数据库/配置，不能将上述test命令成功但集成用例被跳过当作已验证。镜像命令以前端Dockerfile实际COPY产物要求为准，构建前核对dist是否已生成。

迁移/容器验收复用一套隔离 `deploy/implementation-verify.compose.yml` 或当前已批准验收栈，不同时启动多个compose项目。先只验证 `docker compose -f deploy/implementation-verify.compose.yml config --quiet`（秘密由受控环境注入），再按该栈实际服务名做迁移→单实例启动→healthcheck→接口冒烟。须保存实际服务健康状态和迁移日志，不能用compose配置校验替代启动通过。

用户交互回归继续使用指定ego；上述Playwright脚本可用于隔离CI，未运行时标“未执行”。需要真人确认的动作遵循真实确认流程，不由脚本冒充决定。

### 7.3 发布准入与关闭证据

每项关闭记录：对应需求、修改文件/提交、前后端接口、迁移/配置版本、实际命令与结果、原失败路径复验、异常/权限用例、原图/标注和真实taskId/traceId。构建、单元、集成、迁移、镜像、启动健康及核心E2E按影响范围通过；缺一项须明确未执行原因、风险和补验动作。

D03必须交付可解析文件；D05必须交付源→镜像及诊断收敛证据；D09必须在原真实客户端通过；D11必须真实业务页对照，不能拿修好的静态Demo代替；E01必须包含重启后历史可读；未取得这些证据不允许宣称完成。

## 8. 本次方案整理记录

本次实际执行：读取两轮报告、累积追踪和原方案，使用git/rg/Get-Content核对关键实现、SQL、DTO、Node schemas、构建配置。仅新增本文及交叉索引，不改业务代码/数据库/环境；未执行产品单元/集成/构建/容器或新一轮浏览器测试，这些不是写方案阶段的完成证据。

实际文档检查：D01～D13/E01/P01共15个详细节齐全，相对链接存在、Markdown代码围栏成对、`git diff --check`通过；源码SQL静态计数确认为13列/14个值表达式/13占位符/12个绑定参数。本次没有以静态计数冒充真实数据库执行。

状态：**修复方案已整理；所有D/E/P项尚未按本方案实施，产品仍为部分完成。** 文档检查仅验证ID覆盖、路径/链接与内容一致性，不能替代后续交付验证。

## 9. 实施进展及新增D14（2026-10-27）

本方案已开始实施，当前状态为**部分完成**；第8节为最初整理时的历史记录。最新改动、链路、命令和未完成验收统一见 [本轮实施记录](msp-two-round-remediation-implementation-20261027.md)。

### D14：缺陷详情标签与预计解决时间重叠

影响范围：缺陷详情侧栏，多标签、长标签、窄宽度、只读或可编辑状态。修改范围：`frontend/src/views/bug-management/components/basicInfo.vue`，不改缺陷字段、更新接口或权限。

原因：标签使用固定32px高度的居中flex外包装，多行内容超出行框后侵入相邻字段。修正：标签独立自然高度容器，表单内容保持min-width:0/max-width:100%，输入标签容器最小32px但允许增高，长标签限制宽度；普通只读信息行由固定height改为min-height。

验收：空标签、单标签、10个以上标签、超长单标签；侧栏收窄及200%缩放；标签、预计解决时间、创建人等边界无重叠且可操作；修改标签后保存/刷新保持，权限受限时仍不可编辑。当前仅完成代码及组件编译/lint，真实页面验证待执行。
