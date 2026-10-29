# 本周提交需求追踪（本轮结果）

基准40条：原36条+4条新增。补充6项提交影响检查单列，不改变主分母。75条复合用例单独统计，不与主需求相加。

|ID|需求基准|本轮状态|实际结果/剩余范围|证据|
|---|---|---|---|---|
|AC-001| /  SYNC-01 新增  /  进入、返回、激活测试计划列表/详情/用例页自动显示最新数据  /  计划页面接入统一刷新，按组织/项目/计划/版本隔离状态  /  查询返回统一有效数据及revision  /  AT01～05  / |部分完成|保持计划页打开→修改本轮源名称，自动显示新名称；重入加载通过。首次计划列表曾为空，手刷后恢复，后续重入正常；慢响应/草稿矩阵未完整执行。|plan-visible-auto-refresh.txt, plan-reentry.json, plan-list-refresh-response.json|
|AC-002| /  SYNC-02 新增  /  源用例的全部共享内容、状态自动反映到已关联计划  /  同一DTO、附件/缺陷/步骤视图，变更后失效刷新  /  共享读取、事务内变更版本、可靠同步与统计修复  /  AT06～14  / |部分完成|关联本轮执行用例→查询计划；名称、P3、标签、ERROR和2条执行事实一致。全字段/附件解绑/归档矩阵未完整执行。|native-plan-evidence.json, plan-after-sync.json|
|AC-003| /  SYNC-03 新增  /  “同步状态至测试计划”同步全部共享内容  /  保留入口，增加范围说明、进度及逐项失败  /  项目/计划同步统一应用服务、持久任务、幂等重试  /  AT15～18  / |部分完成|源关联缺陷后点击全量同步，6/6完成；失败恢复、逐域统计和归档隔离未完整执行。|manual-sync-final.txt, plan-after-sync.json|
|AC-004| /  LIVE-01  /  计划A/B详情正确隔离  /  detail/index.vue及子页  /  详情/统计接口归属校验  /  AT01/03  / |部分完成|本轮A/B标题与URL核对正确，新计划名称可进入；完整A/B/A、浏览器前后退与全部子页身份矩阵未重跑。|plan-a-detail.txt, plan-b-detail.txt, plan-new-name-navigation.txt|
|AC-005| /  LIVE-02  /  隐藏生成报告  /  detail/index.vue、权限资源/工具栏  /  不新增后端逻辑  /  AT19  / |通过|打开新计划详情，生成报告入口不存在；其他编辑/复制入口仍可见。|plan-new-name-navigation.txt, raw/plan-new-1366.png|
|AC-006| /  LIVE-03  /  隐藏生成用例，旧链接兼容  /  caseManagement.ts、顶部页签  /  模型能力开关与接口权限  /  AT19  / |通过|访问旧caseGenerate地址自动进入featureCase；仅执行用例/Xmind页签。|legacy-generation-route.txt|
|AC-007| /  LIVE-04  /  隐藏业务文档AI生成  /  test-asset/documents.vue  /  复用能力配置  /  AT19  / |通过|打开业务文档页，上传和筛选保留，无AI生成用例入口。|documents.txt|
|AC-008| /  LIVE-05  /  隐藏用例资产四项AI控件  /  test-asset/cases.vue  /  复用能力配置，保留非模型MCP  /  AT19  / |通过|打开资产目录，AI执行环境、AI可执行性、页面对象、业务流控件均未出现，批量操作保留。|asset-priority-wide.txt|
|AC-009| /  LIVE-06  /  原生MCP创建可表达幂等参数  /  工具schema、客户端帮助  /  DTO/schema与写入幂等协议  /  AT20  / |通过|原生tools/list创建入口具有requestId；真实同key新建重放同一ID，改参冲突。|native-tools-list.json, native-case-idempotency.json|
|AC-010| /  LIVE-07  /  配置参数前置校验，错误只提示一次  /  environment-profile.vue、http层  /  参数错误协议  /  AT21  / |部分完成|无协议URL在前端一次提示，未发创建请求；本轮未新建合法平台/登录配置并验持久化。|invalid-platform-url.json|
|AC-011| /  LIVE-08  /  10/20/50/100分页及用户偏好  /  caseTable.vue、表格分页组件  /  分页上限校验  /  AT22  / |通过|四档分页可见；选择10后刷新保留，结束恢复20。|pagination-options.txt, pagination-preference.txt|
|AC-012| /  LIVE-09  /  批量按钮常驻，零选择禁用  /  业务表格/通用批量操作区  /  原批量权限、范围校验  /  AT23  / |通过|零选择批量按钮常驻并禁用；全选后启用。宽屏截图和按钮边界已保留。|case-selection.json, case-toolbar-1908.json|
|AC-013| /  LIVE-10  /  评论冲突明确提示  /  评论编辑交互  /  类型化业务码和错误映射  /  AT24  / |部分完成|新建评论→新版更新→旧版写入返回VERSION_CONFLICT且新内容保留；前端草稿保留分支未执行。|native-comment-conflict.json|
|AC-014| /  LIVE-11  /  自定义字段schema与后端一致  /  MCP工具schema与模板说明  /  Map<String,String>或显式归一化契约  /  AT25  / |通过|原生customFields为Map<string,string>；真实模板必填缺失拒绝、数组拒绝、有效字符串创建并可读回。|native-tools-list.json|
|AC-015| /  LIVE-12  /  MCP计划用例查询真实可用  /  无必需新UI，个人MCP消费  /  查询授权、分页、映射、步骤及错误日志  /  AT26  / |通过|MCP实际读取本轮计划，返回3个关联、原步骤与执行事实；跨项目访问安全拒绝。|native-plan-evidence.json, plan-after-sync.json, native-boundary-errors.json|
|AC-016| /  LIVE-13  /  删除预检确认链接可达  /  caseDeletion.ts、confirm页面、路由过滤  /  预检归属、版本、真人确认状态机  /  AT27  / |部分完成|删除预检返回真实确认链接，页面可打开并展示目标/过期时间；未冒充真人确认执行删除。|native-delete-preview.json, deletion-confirm-page.txt|
|AC-017| /  LIVE-14  /  跨项目详情不得显示旧项目内容  /  用例父页、列表/详情切换、缓存键  /  资源项目归属校验  /  AT03/28  / |通过|源详情切到ces项目，不再显示旧用例，随后恢复验收项目。|cross-project-ui.txt|
|AC-018| /  UI-BASE  /  原case/plan/review SFC局部样式  /  不替换API和Ms组件  /  无业务迁移  /  常驻按钮、权限、列配置无回退  / |失败|按批准Demo对照实际页面：表头仍白色、批量按钮20px，对照Demo灰表头/32px按钮；新选择框max-width为none，未满足完整样式落地。|current-layout-styles.json, annotated/AC18-layout.svg, raw/approved-demo-case-section-1908.png|
|AC-019| /  IMP-01/02  /  导入预检及任务结果  /  原任务+新增关联编排适配  /  group/followup/item及任务扩展  /  分项、幂等、部分成功恢复  / |失败|预检不再请求风暴，新建/追加联动成功；但停止待提交预检后点重试，未提交仍写入且followup=LEGACY，绕过关联策略。|web-import-stable.json, web-import-stop.json, web-import-retry.json, web-retry-final-browser.json|
|AC-020| /  DEL-01  /  删除/恢复后刷新  /  现有过滤及provider补可靠重算  /  先不改物理关系  /  列表/统计/恢复一致  / |阻塞|本轮只打开真人删除确认页，未替代真人审批；源软删/恢复后计划评审重算尚未重新验收。|native-delete-preview.json, deletion-confirm-page.txt|
|AC-021| /  AST-01  /  源/同步时间/错误展示  /  统一已有目录与case upsert  /  映射去重/版本/墓碑  /  同源唯一、增改删恢复  / |失败|补偿后诊断UP_TO_DATE且未收敛0，但镜像PENDING/无执行事实，源为ERROR；新外部图片未进入镜像。|asset-current-state.json, source-attachments-current.json, asset-reconcile-later.txt|
|AC-022| /  AST-02  /  目录置顶菜单  /  pageCatalogs服务端排序、偏好接口  /  范围确定后建偏好表  /  跨页稳定排序/撤权  / |部分完成|组织置顶→刷新仍置顶→取消置顶，恢复原状态；多成员/跨页/撤权未执行。|asset-pin.txt, asset-unpin-restored.txt|
|AC-023| /  ASSET-BATCH  /  资产复选/批量/导出  /  独立资产scope适配原任务  /  权限/任务/审计  /  XLSX内容、引用、逐项失败  / |失败|批量等级/标签回写源成功，分类成功，新建人工资产批量删除成功；导出下载可解析但等级列为空，与P3列表不一致。|asset-batch-priority.txt, source-after-batch.json, asset-batch-tags.txt, asset-batch-category.txt, asset-batch-delete.txt, asset-export-content.json|
|AC-024| /  X01  /  用例级图片、无必填技术 Hash  /  Node/原生 evidence.upload、import_url、submit  /  DirectExecutionService、ExternalImageService、质量门禁、CASE 迁移  /  T01～T20；依赖 X02/X03/X07  / |通过|一张CASE图片覆盖2个原步骤，不传客户端Hash或逐步图片，ERROR聚合正确；无图/伪图安全拒绝。|native-evidence-upload.json, native-submit.json, native-no-evidence.json, native-file-errors.json|
|AC-025| /  X02  /  定义快照不混入历史运行事实  /  contract、execution.get、用例详情  /  CaseDefinitionSnapshot、指纹版本、facts schemaVersion  /  连续执行不嵌套；定义变更可识别  / |部分完成|facts含独立definition与steps，修改定义后新快照反映P3，未嵌入旧执行事实；历史STEP兼容全矩阵未执行。|native-submit.json, sensitive-facts-readiness.json|
|AC-026| /  X03  /  统一参数和幂等身份  /  Node/原生工具及 HTTP 头  /  dispatcher、OperationExecutor、import job、upload intent  /  长度、字符、大小写、操作隔离  / |部分完成|1/128字符、129拒绝、同key重放/改参/并发通过；空白/大小写/所有REST及数据库比较矩阵未完整执行。|native-get-boundaries.json, native-boundary-errors.json, native-case-idempotency.json|
|AC-027| /  X04  /  导入后计划和评审联动  /  Node import 工具，不新增人工页面  /  CaseImportTools、ImportFollowupCoordinator、关联组表  /  新建/追加/仅导入/独立重试  / |部分完成|原生新建计划评审并关联、指定组追加不传reviewers成功；Node实际包、关联单独失败恢复未执行，Web重试问题另列。|native-import-final.json, native-append-no-reviewers.json, native-append-final.json|
|AC-028| /  X05  /  统一安全错误与未知结果处理  /  Node client、原生 MCP  /  业务错误映射、日志 traceId  /  HTTP/Holder/RPC 三条路径一致  / |失败|多类安全错误含trace；但步骤actual内嵌JSON字符串的合成password未脱敏且发布成功，普通password文本受缺密钥门禁拒绝。|sensitive-facts-readiness.json, sensitive-plain-control.json|
|AC-029| /  X06  /  幂等重放授权、并发和外部副作用  /  全部 MCP 写入口及关联 REST  /  通用缓存、OperationExecutor、上传状态机  /  第 21～22 节；不改 Runner 租约规则  / |部分完成|两个真正并发同key仅一ID；撤写scope后重放SCOPE_DENIED，读取允许；删除临时Token后401。完整权限/外部副作用恢复矩阵未覆盖。|native-get-boundaries.json, revoked-write-replay.json, token-revoked-check.json|
|AC-030| /  X07  /  图片可查可读、ID 清晰  /  attachments.list、execution.get、附件页面  /  EvidenceStore、受控内容读取、附件响应 DTO  /  Token 读图；跨项目及撤权拒绝  / |部分完成|普通/历史受控图片读取返回image，104112字节；新图跨项目、普通关联删除后历史读取全矩阵未执行。|native-history-read.json, native-external-image.json|
|AC-031| /  X08  /  文本用例及数量边界透明  /  contract、submit  /  类型能力声明、分类型定义和结果模型  /  文本/空步骤/100 与 101/1000 与 1001  / |部分完成|101原步骤contract明确不支持，1001提交条目拒绝；TEXT及1/10/100/1000全部边界组合未完整执行。|native-step-boundaries.json|
|AC-032| /  X09  /  长期证据保留与清理明确  /  历史执行和附件查询  /  引用关系、保留策略、清理审计  /  到期/解除关联/恢复不误删  / |阻塞|长期到期清理/历史恢复无法在本轮在线时间窗验证；敏感密钥未配置，普通敏感事实提交被拒绝。|asset-readiness-api.json, sensitive-plain-control.json|
|AC-033| /  X10  /  两端工具、文档和技能包一致  /  Node 包、原生 tools/list、下载包  /  schema 契约测试、README/模板  /  不含旧逐步骤强制规则  / |失败|原生CASE契约为可选Hash/步骤绑定，但当前连接器仍强制Hash/stepNum/purpose且缺顶层图片和创建requestId；两端契约不同。|connector-schema-observed.json, native-tools-list.json|
|AC-034| /  UX-01目录移动  /  caseTree更多+父目录弹窗  /  复用锁树/版本/循环检查，补显式父级接口  /  parent_id/pos/update_time  /  子树/移根/旧版本/跨项目  / |部分完成|空目录移入另一目录后再移回根，父关系和版本更新正确；循环/同名/跨项目/子树资源全组合未执行。|module-move-in.txt, module-move-out.txt|
|AC-035| /  UX-02全选  /  导出后按钮、旧箭头  /  useTable同一选择事件，原selectAll协议  /  无需新建全选表  /  跨页排除/筛选清空  / |通过|全选所有页后第二页10条均选中；排除一条30→29；改变搜索后清0。原下拉箭头截图仍保留。|select-all-cross-page.json, raw/cases-demo-comparison-1908.png|
|AC-036| /  UX-03名称  /  plan/review名称slot  /  复用openDetail  /  无  /  编辑不导航、分组不误跳  / |部分完成|1366视口计划/评审名称鼠标点击进入正确详情；971视口曾被固定操作列遮住，编辑/计划组行为未完整执行。|plan-new-name-navigation.txt, review-new-name-navigation.txt, plan-name-hit.json|
|NEW-01|原V4包含报告表的Excel预检和实际导入|通过|原V4进入89条预检，IMPORT_ONLY实际导入89条SUCCESS，首尾详情200且步骤JSON可解析；目录路径优先级另列待确认。|original-full-import-results.json, original-full-import-persistence.json|
|NEW-02|最多展示一个计划并下拉切换|通过|新资源下仅1个计划进度，展开列出关联计划并可切换，完整名称title保留。当前候选均0%未做非零率比较。|plan-switch-new.json, deployment-recheck.json, raw/overview-dropdown-new.png|
|NEW-03|导入停止/重试不误校验policy|失败|stop/retry不再误报policy参数；但未提交的PRECHECKED停止后retry直接QUEUED并写入，缺提交/关联策略门禁。|web-import-stop.json, web-import-retry.json, web-retry-final-browser.json|
|NEW-04|缺陷详情多标签不与预计解决时间重叠|通过|新建含6标签（含长标签）的本轮缺陷；标签区域在日期区域之下，无重叠。|bug-tags-geometry.json, annotated/NEW04-tags.svg|
