# MSP 测试环境需求验收报告 · 2026-10-27

**结论：仍有阻断正常交付的问题，不能判定近期需求全部实现。测试交付与被测功能均为部分完成。** 本轮通过 ego 实际登录、浏览器交互、真实业务接口和原生 MCP 执行，未使用 Mock 冒充测试环境。

## 1. 范围、版本与统计口径

- 环境：`https://msp.ebcone.net`；浏览器 ego lite 0.6.0.16；任务 `MSP requirements acceptance 20261027`，Space 8。单账号陈启芬、米多公司，项目 `MSP需求验收-20261023`（1704667279923322880）。新增数据均带 ACCEPTANCE-20261027 标识。
- 采用需求来源：`msp-live-issues-detailed-remediation-plan-20261023.md`、`msp-integrated-delivery-plan-20261023.md`、`personal-execution-case-evidence-implementation-plan-20261027.md`；前期回归14项问题补充引用 `msp-remediation-assets-batch-ui-review-plan-20261023.md`。**需求优先于现网、代码和旧连接器契约。** 整合方案第14节“组织共享置顶、明确关联组、保留编辑、同名后缀”覆盖文中早期候选建议。
- 没有另外提供外部测试用例文件；将方案中的 AT01～AT28、T01～T47 原文保留为75条复合验收清单。部分子项执行成功不能将整个复合用例标记通过。原始R01～R08在第6节保留映射，不与X编号相互覆盖。
- 本地源码基线 `580b7aed02`；线上前端清单 revision 为 `129de8747bd0bf800357c57cb65fde5641c4c35c`，builtAt `2026-10-27T05:58:33.388Z`。线上 index SHA256 与清单一致，但该revision不在当前本地对象库，**不能直接说线上等于本地HEAD，也不能仅凭不同SHA认定部署错误**。后端实际SHA未提供。
- 实际截图视口以 [证据索引](evidence-index.json) 的像素尺寸为准：主要1908×895，窄窗口971×796；最初983×864的观察图未作为1366px基准。本轮未完成精确1366/1280、200%缩放和多浏览器验收。
- 无已确认的像素截图基线；对照已确认Demo的结构/布局要求做人工检查，不声称像素视觉回归通过。SVG标注只叠加编号与框，原始PNG单独保存，不补画控件。

| 统计层 | 总数 | 通过 | 失败 | 阻塞 | 待确认 | 未完整执行 |
|---|---:|---:|---:|---:|---:|---:|
| 主需求/检查点 | 36 | 16 | 8 | 1 | 1 | 10 |
| 方案复合验收用例（AT/T） | 75 | 3 | 4 | 0 | 0 | 68 |

主需求映射覆盖率 **36/36=100%**；已验证覆盖率 **16/36=44.4%**。主需求执行通过率按已有通过/失败结论统计 **16/(16+8)=66.7%**，不能替代覆盖率。复合用例未完整执行不等于没有任何子项证据：例如T01只做了100步一图，未分别做1/10步，因此仍保留在未完整执行分母。范围外：Runner租约/断言改造、生产环境、故障注入和历史数据库迁移操作；本轮没有模拟停服务或清理生产存储。

## 2. 已验证的重要路径

- CASE证据：新建100步骤隔离用例，一张真实平台截图、不传contentHash/policyHash/stepNum/purpose，步骤不带图片，成功发布；第100步声明ERROR，整例汇总ERROR。这里是证据协议测试，**不代表100条真实业务功能已被执行**。
- 上传与提交使用同一个requestId，重试返回原ID、改参返回IDEMPOTENCY_CONFLICT；仅凭requestId能查询SUCCEEDED和CONSUMED。执行前后定义Hash稳定；新事实含独立definition及100条steps。
- 普通/历史读取均得到MCP `image` 内容，105733字节，SHA256与上传一致；跨项目读图被拒绝。外部公网PNG自动转存8090字节，重放fileId/associationId不变。
- 真正两个并发新建目录请求只产生同一ID。通用请求key长1/64/65/128成功，129安全拒绝。Token撤去写scope后，原计划创建请求重放被SCOPE_DENIED拒绝，而读报告仍允许；删除临时Token后HTTP401。
- 计划名称进入、A/B标题及URL隔离、隐藏生成报告通过；源名称修改后离开并返回计划，无需手工刷新即可显示新名称。计划页面正确显示100条执行事实和“用例证据1项”。
- 全选所有页：11条、第二页仍选中；排除该页一条后10条；改变搜索后清零。旧下拉箭头保留，分页四档可见，批量按钮常驻并按选择禁用。
- 原生导入：CREATE_NEW_PAIR创建计划/评审并关联；APPEND_EXISTING在补传reviewers后成功，IMPORT_ONLY完成且不建关联。页面评审名称可进入真实详情。软删本轮导入A后计划/评审2→1，恢复源后计划回到2。
- 手工全量同步5/5完成，本轮新缺陷在计划中可见；平台新增及登录配置新增持久化成功，非法URL在前端单次安全提示；本轮新增配置最终均停用。
- 历史同步中两个旧失败项目点击“重试失败项”后成功，任务状态SUCCESS。**这不代表持续同步全部正确**，见D05。
- 人工资产标签追加持久化、两条明确资产批量删除均成功。下载的技能包1.4.0包含“一图/无需逐步骤Hash”的新说明。

## 3. 发现的问题与复现、证据

### D01 / P1：导入预检弹窗请求循环，无法正常提交

基准：IMP-01/02、X04；AT19、T30。步骤：测试用例→导入→下载真实Excel模板→填一条隔离用例→校验文件→导入预检与任务。

预期：稳定加载关联策略、评审人及提交按钮，等待用户选择；待提交状态不持续高频轮询。实际：job GET和followup-options反复触发/取消，67秒内3443次请求；“提交有效用例”一直禁用，策略未出现。本轮完整网络记录5248次包含这段循环，不能把这个总数都当作服务端错误。关闭弹窗后停止，未反复打开制造压力。

[原图](raw/import-followup-missing.png) · [编号标注](D01-annotated.svg) · [网络计数](import-request-loop.json)。jobId `9d548938-946a-4d91-a611-a43532727d56`，收尾已停止。

代码推断：`importJob.vue` 的分页watch返回新数组，依赖 `useClientTablePaging` 里随job.total失效的computed；每次job赋值可能再次load并取消followup-options。此解释与实测循环一致，尚未通过修改代码复验。建议改成多个独立标量watch源，增加同job请求去重和顺序隔离，PRECHECKED不轮询。先加“待提交10秒不超出预期初始请求数、策略可选、提交可用”的自动化断言。

### D02 / P1：个人Agent Token启用/操作列仍被裁切

基准：REG-UI-01。在1908px，表格宽1660，从x444开始，启用列x1864，操作列x1958，超出容器；Card overflow hidden，滚动层未正确承接宽度。实际截图看不到完整开关和操作。971px也复现。

[1908原图](raw/token-1908.png) · [窄窗口原图](raw/token-narrow-final.png) · [编号标注](D02-annotated.svg)。不能凭固定列配置已写入源码判定通过。建议修复 `ms-card-container` / arco-scrollbar /表格viewport宽度链与实际scroll属性，把固定右列和滚动条置于可见容器；用几何边界、键盘可达及开关失败恢复验收。

### D03 / P1：资产导出任务FAILED

基准：ASSET-BATCH、REG-EXP-01/02。选择人工隔离资产→导出；创建任务成功，随后FAILED，无法取得XLSX验内容。任务 `28ae898b-3b35-4922-85dd-1a5b3b78d6d0`，errorCode EXPORT_FAILED，count=0。

[原图（含失败任务）](raw/asset-priority-display.png) · [真实任务及列表响应](asset-diagnostics.json)。根因未取得服务日志，不能直接断言存储、权限或字段映射中的哪一项。建议按taskId追踪worker、asset scope授权、字段解析、对象存储写入；修复后读取真实XLSX断言编号/名称/等级/标签/步骤与行数，不能只看HTTP200。

### D04 / P1：MCP合法导出内部错误，requestId契约也未统一

基准：X03/X05、REG-MCP-03。合法请求：format=xlsx、selectIds为本轮有权用例、systemFields为{id:num}/{id:name}；返回AI_EXECUTION_INTERNAL_ERROR，traceId `dc0b66a6-f8df-4aca-b0ed-fc5c3fb56a3d`。65字符requestId同样返回内部错误，traceId `640b52ed-0b9a-426c-8f08-0323a7cddd16`；其他业务65/128字符正常，导出schema仍max64。

[原始响应](native-export-start.json) · [其他工具key边界](mcp-key-boundaries-concurrency.json)。字符串数组非法列能安全报AGENT_VALIDATION_ERROR，这条修复有效，不能因此将合法导出判通过。建议统一DTO/schema/持久任务key长度与错误映射，并查两条trace；D03/D04可能共享链路，但在日志确认前不认定同根因。

### D05 / P1：资产持续同步仍缺失，且无可解释的诊断

基准：AST-01、REG-ASSET-02。验收项目源用例最终11条；对应目录1704667279923322961在本轮人工资产创建前为0，随后只显示人工创建项，没有本轮MCP新建/更新及导入的源镜像，sync-failures返回空数组。两条人工资产批删后恢复为空不是“源用例已同步”。

[初始空目录](raw/assets-initial.png) · [目录/列表/诊断响应](asset-diagnostics.json) · [源项目11条](source-project-final.json)。建议排查project_rel→hubModuleId→source_relation/default_hub_case_map→CASE_SYNC事件与消费者；区分历史任务SUCCESS与项目源镜像收敛状态。未获日志，身份映射或事件遗漏均只是待验证原因。需要对新建/编辑/导入/附件/删除/恢复各入口做同一来源ID收敛验证，并让未同步/跳过/失败可见。

### D06 / P2：批量等级已存P1，但资产列表显示“-”

基准：ASSET-BATCH。选人工资产A→批量修改等级P1→完成；详情和列表接口customFields中 `internalFieldKey=functional_priority, defaultValue=P1`，页面仍“-”。标签追加则正确可见。

[原图](raw/asset-priority-display.png) · [标注](D06-annotated.svg) · [字段响应](asset-diagnostics.json)。当前仓库 `cases.vue:getPriority` 读fieldName和value，而接口给defaultValue，足以解释该显示问题；建议按internalFieldKey稳定映射，兼容旧value但不依赖中文列名，并补批改后刷新/中英文显示断言。

### D07 / P1：目录移入后缺revision，无法移出，错误重复提示

基准：UX-01。先将本轮子目录1710873353047252993移入父目录1710873353047252992（成功）；刷新树→展开→移动子目录→全部用例→确定。

实际：嵌套节点updateTime缺失；请求400、expectedUpdateTime不能为null；弹窗保留并出现同trace两条错误。首次移入HTTP200，移出HTTP400，不是目录功能全部不可用。

[原图](raw/directory-move-out-stuck.png) · [标注](D07-annotated.svg) · [嵌套节点/校验响应](restore-and-move-out.json) · [实际UI请求状态](scope-revocation-and-batch-final.json)。建议修复递归树DTO的updateTime传递与前端节点转换，缺revision时提示重新读取而非提交空值；错误提示只在统一层或局部层出现一次。

### D08 / P2：追加既有组隐含要求reviewers，与schema/语义不一致

基准：IMP-01/02、X04。followup-options返回有效组revision=1；commit传APPEND_EXISTING、组ID和revision，不传schema非必填reviewers，返回AGENT_VALIDATION_ERROR（traceId `4f0d8850-8e3e-4c82-829c-45922baff9ce`）；补传现有评审人后成功并沿用原plan/review ID。

[首次失败](native-import-append-only.json) · [补传成功](native-import-append-final.json)。当前 `ImportFollowupCoordinator.permissions` 所有非IMPORT_ONLY分支均校验reviewers。建议追加操作从现有目标取得并验证评审人，勿要求用户重新指定；创建新组才要求显式选择，并在schema或conditional schema中声明。

### D09 / P1（客户端接入）：当前连接器仍缓存旧MCP工具契约

基准：X10、REG-MCP-CLIENT-01。实际连接器仍把contentHash/policyHash/stepNum/purpose及每步evidenceIds设为必填，计划创建也未声明requestId。原生126工具清单和下载技能包已更新，原生闭环成功；因此不能将此归为“服务端仍强制逐步图”。

[原生新契约](native-tools-list-full.json) · [本轮实际连接器契约](connector-observed-schema.json)。需要刷新/重连测试连接器并核对真实schema指纹，再验Node或当前客户端的新图像工具、导入followup能力；本轮没有替用户重建其既有连接授权。

### D10 / P2：CASE证据错误仍提示“本步骤”

基准：X05/X10、一图覆盖整例。无图片/非法图片已被正确拒绝，但安全错误文案仍为“请补充本步骤的有效证据”。[响应](mcp-negative-boundaries.json)。建议个人执行使用用例级文案，保留Runner独立的步骤级错误，避免再次误导调用方逐步上传。

## 4. 需求—检查点追踪

以下保持原条目，不以页面现状改写预期。步骤、原文、方法、角色和脚本映射在 [机器可读追踪表](requirements-trace.json)。未完整执行的主条目即使有正向子项，也不进入通过分子。

| 检查点 | 基准（文件行号） | 结果 | 实际、证据边界 |
|---|---|---|---|
| AC-001 / SYNC-01 新增 | msp-live-issues-detailed-remediation-plan-20261023.md:41 | 未执行 | 进入/返回后源名称自动刷新已通过；可见页实时更新、慢响应隔离、草稿保留未完整验证 [图](raw/plan-auto-refresh.png) |
| AC-002 / SYNC-02 新增 | msp-live-issues-detailed-remediation-plan-20261023.md:42 | 未执行 | 源/计划100条执行事实、证据、缺陷正向路径通过；完整字段、解绑、版本矩阵未执行 [图](raw/execution-facts-page.png) |
| AC-003 / SYNC-03 新增 | msp-live-issues-detailed-remediation-plan-20261023.md:43 | 未执行 | 手工同步5/5完成，缺陷列表一致；逐域统计、失败恢复和全部共享字段尚未完整验证 [图](raw/plan-defect-synced.png) |
| AC-004 / LIVE-01 | msp-live-issues-detailed-remediation-plan-20261023.md:44 | 通过 | 名称进入A→列表→B；标题、编号、URL分别匹配 [图](raw/plan-b.png) |
| AC-005 / LIVE-02 | msp-live-issues-detailed-remediation-plan-20261023.md:45 | 通过 | 计划详情生成报告入口隐藏 [图](raw/plan-a.png) |
| AC-006 / LIVE-03 | msp-live-issues-detailed-remediation-plan-20261023.md:46 | 未执行 | 仅执行用例/Xmind页签可见；旧生成用例深链接兼容未测 [图](raw/case-list-final.png) |
| AC-007 / LIVE-04 | msp-live-issues-detailed-remediation-plan-20261023.md:47 | 通过 | 业务文档页面无AI生成用例按钮 |
| AC-008 / LIVE-05 | msp-live-issues-detailed-remediation-plan-20261023.md:48 | 通过 | 用例资产无AI环境/可执行性/页面对象/业务流四项控件 [图](raw/assets-initial.png) |
| AC-009 / LIVE-06 | msp-live-issues-detailed-remediation-plan-20261023.md:49 | 通过 | 原生schema含requestId；计划创建无额外请求头成功、重试同ID |
| AC-010 / LIVE-07 | msp-live-issues-detailed-remediation-plan-20261023.md:50 | 通过 | 非法URL前端单次提示；合法平台、登录配置持久化成功 [图](raw/login-profile-created.png) |
| AC-011 / LIVE-08 | msp-live-issues-detailed-remediation-plan-20261023.md:51 | 通过 | 四档分页可见；10页长跨模块保留，结束恢复20 [图](raw/select-all-page2.png) |
| AC-012 / LIVE-09 | msp-live-issues-detailed-remediation-plan-20261023.md:52 | 通过 | 批量按钮常驻；零选禁用，选择后可用 [图](raw/case-list-final.png) |
| AC-013 / LIVE-10 | msp-live-issues-detailed-remediation-plan-20261023.md:53 | 未执行 | 评论并发版本冲突未执行 |
| AC-014 / LIVE-11 | msp-live-issues-detailed-remediation-plan-20261023.md:54 | 通过 | 真实缺陷模板读取；字符串字段创建成功，缺必填安全拒绝 |
| AC-015 / LIVE-12 | msp-live-issues-detailed-remediation-plan-20261023.md:55 | 通过 | MCP计划用例查询可用，返回完整执行事实和定义分区 |
| AC-016 / LIVE-13 | msp-live-issues-detailed-remediation-plan-20261023.md:56 | 未执行 | MCP删除预检/真人确认链接未执行；本轮仅Web自身测试数据软删/恢复 |
| AC-017 / LIVE-14 | msp-live-issues-detailed-remediation-plan-20261023.md:57 | 未执行 | 跨项目缓存与旧响应覆盖未执行；仅MCP越权读图已拒绝 |
| AC-018 / UI-BASE | msp-integrated-delivery-plan-20261023.md:63 | 待确认 | 原业务按钮/表格保留并人工检查基础布局；未建立经确认像素基线 [图](raw/case-list-final.png) |
| AC-019 / IMP-01/02 | msp-integrated-delivery-plan-20261023.md:67 | 失败 | 浏览器预检产生请求循环，策略缺失、提交禁用；原生导入服务成功 [图](raw/import-followup-missing.png) |
| AC-020 / DEL-01 | msp-integrated-delivery-plan-20261023.md:68 | 通过 | 隔离当前版本软删后计划/评审2→1；恢复源后计划重新显示2 [图](raw/review-after-source-delete.png) |
| AC-021 / AST-01 | msp-integrated-delivery-plan-20261023.md:69 | 失败 | 源项目11条，资产目录只出现本轮人工资产；无对应源镜像，诊断为空 [图](raw/assets-initial.png) |
| AC-022 / AST-02 | msp-integrated-delivery-plan-20261023.md:70 | 阻塞 | 组织置顶保存/首位排序/取消通过；无第二成员账号验证组织共享可见性 [图](raw/asset-priority-display.png) |
| AC-023 / ASSET-BATCH | msp-integrated-delivery-plan-20261023.md:71 | 失败 | 两条人工资产批删通过、标签持久化；等级列表仍“-”，导出FAILED；分类批改未测 [图](raw/asset-batch-delete-final.png) |
| AC-024 / X01 | personal-execution-case-evidence-implementation-plan-20261027.md:399 | 通过 | 100步骤一图、无Hash/逐步图片；结构门禁、重放、ERROR聚合与读图通过 [图](raw/execution-facts-page.png) |
| AC-025 / X02 | personal-execution-case-evidence-implementation-plan-20261027.md:400 | 通过 | 执行前后定义Hash相同；事实100步独立definition；源名称更新后历史快照不被重写 |
| AC-026 / X03 | personal-execution-case-evidence-implementation-plan-20261027.md:401 | 失败 | 通用目录key允许1/64/65/128并拒绝129；导出65字符却内部错误，未统一 |
| AC-027 / X04 | personal-execution-case-evidence-implementation-plan-20261027.md:402 | 失败 | 原生新建/追加/仅导入成功；追加漏传schema非必填reviewers却被拒绝，Node连接器仍旧契约 [图](raw/review-import-linked.png) |
| AC-028 / X05 | personal-execution-case-evidence-implementation-plan-20261027.md:403 | 失败 | 多数错误安全保留trace；导出参数错误归内部错误，CASE证据错误仍提示“本步骤” |
| AC-029 / X06 | personal-execution-case-evidence-implementation-plan-20261027.md:404 | 未执行 | 双并发新建同ID、撤scope后重放拒绝已通过；全部REST/外部副作用/崩溃窗口未完整验收 |
| AC-030 / X07 | personal-execution-case-evidence-implementation-plan-20261027.md:405 | 通过 | 普通/历史image内容、SHA一致；跨项目拒绝，附件关联ID/fileId明确 |
| AC-031 / X08 | personal-execution-case-evidence-implementation-plan-20261027.md:406 | 未执行 | 100步和1001事实边界已测；TEXT/101定义/空预期未完整测试 |
| AC-032 / X09 | personal-execution-case-evidence-implementation-plan-20261027.md:407 | 未执行 | 当前历史读图成功；七天过期、清理与备份恢复不能在本轮证明长期留存 |
| AC-033 / X10 | personal-execution-case-evidence-implementation-plan-20261027.md:408 | 失败 | 原生/下载技能包是新契约；现有连接器仍Hash和逐步骤字段必填 |
| AC-034 / UX-01目录移动 | msp-integrated-delivery-plan-20261023.md:64 | 失败 | 顶层移入成功；嵌套节点updateTime缺失，移出400且双提示 [图](raw/directory-move-out-stuck.png) |
| AC-035 / UX-02全选 | msp-integrated-delivery-plan-20261023.md:65 | 通过 | 导出右侧常驻按钮；11条跨页保持，排除后10条，筛选后0；旧箭头保留 [图](raw/select-all-page2.png) |
| AC-036 / UX-03名称 | msp-integrated-delivery-plan-20261023.md:66 | 通过 | 计划与评审名称均可进入对应真实详情 [图](raw/review-import-linked.png) |

## 5. 75条方案验收用例逐项结果

原文、原位置完整保留在 [验收用例清单](acceptance-cases-trace.json)。下表“未执行”统一表示**未完整执行复合用例**，实际列说明已执行的子项；不把未测条件省略或从分母中删除。用例原文与需求不冲突；旧连接器的强制参数属于产品差异D09，不用来替换需求预期。

| 用例 | 来源行 | 结果 | 实际与尚待覆盖 |
|---|---|---|---|
| AT01 | msp-live-issues-detailed-remediation-plan-20261023.md:290 | 未执行 | A→B正确隔离；文档保存/浏览器前后退矩阵未完整测试。 |
| AT02 | msp-live-issues-detailed-remediation-plan-20261023.md:291 | 未执行 | MCP改源名称后返回计划无需刷新可见；状态变更及筛选页码组合未完整执行。 |
| AT03 | msp-live-issues-detailed-remediation-plan-20261023.md:292 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT04 | msp-live-issues-detailed-remediation-plan-20261023.md:293 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT05 | msp-live-issues-detailed-remediation-plan-20261023.md:294 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT06 | msp-live-issues-detailed-remediation-plan-20261023.md:295 | 未执行 | 名称新版本可见；其他定义字段全量矩阵未测。 |
| AT07 | msp-live-issues-detailed-remediation-plan-20261023.md:296 | 未执行 | 图像读图SHA通过；新增删除重命名跨计划矩阵未测。 |
| AT08 | msp-live-issues-detailed-remediation-plan-20261023.md:297 | 未执行 | 直提路径完整，其他三入口未执行。 |
| AT09 | msp-live-issues-detailed-remediation-plan-20261023.md:298 | 未执行 | 单图100步任一ERROR聚合正确；其余组合未完整执行。 |
| AT10 | msp-live-issues-detailed-remediation-plan-20261023.md:299 | 未执行 | 新缺陷关联在手动同步后计划可见；状态变化/解除未测。 |
| AT11 | msp-live-issues-detailed-remediation-plan-20261023.md:300 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT12 | msp-live-issues-detailed-remediation-plan-20261023.md:301 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT13 | msp-live-issues-detailed-remediation-plan-20261023.md:302 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT14 | msp-live-issues-detailed-remediation-plan-20261023.md:303 | 未执行 | 来源用例当前版本软删/恢复通过；归档/固定版本未测。 |
| AT15 | msp-live-issues-detailed-remediation-plan-20261023.md:304 | 未执行 | 5/5任务完成，缺陷和现有执行读取通过；所有域变更未覆盖。 |
| AT16 | msp-live-issues-detailed-remediation-plan-20261023.md:305 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT17 | msp-live-issues-detailed-remediation-plan-20261023.md:306 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT18 | msp-live-issues-detailed-remediation-plan-20261023.md:307 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT19 | msp-live-issues-detailed-remediation-plan-20261023.md:308 | 未执行 | 四类隐藏入口DOM可见性通过；旧路由/权限组合未完整执行。 |
| AT20 | msp-live-issues-detailed-remediation-plan-20261023.md:309 | 未执行 | 真实原生计划/用例创建重放通过；头参冲突未测。 |
| AT21 | msp-live-issues-detailed-remediation-plan-20261023.md:310 | 未执行 | 前端无协议安全提示、合法配置和登录保存通过；后端非法请求完整矩阵未测。 |
| AT22 | msp-live-issues-detailed-remediation-plan-20261023.md:311 | 未执行 | 四档可见，10/20偏好和跨页通过；重新登录/空14条/越界未完整测试。 |
| AT23 | msp-live-issues-detailed-remediation-plan-20261023.md:312 | 未执行 | 0/1/11跨页选择、排除及筛选清空通过；无权限组合未测。 |
| AT24 | msp-live-issues-detailed-remediation-plan-20261023.md:313 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT25 | msp-live-issues-detailed-remediation-plan-20261023.md:314 | 未执行 | 字符串字段成功、缺必填安全拒绝；数组/对象/未知值未完整测试。 |
| AT26 | msp-live-issues-detailed-remediation-plan-20261023.md:315 | 未执行 | 非空计划MCP查询含100步事实通过；空页、多页、版本对比未完整执行。 |
| AT27 | msp-live-issues-detailed-remediation-plan-20261023.md:316 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| AT28 | msp-live-issues-detailed-remediation-plan-20261023.md:317 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T01 | personal-execution-case-evidence-implementation-plan-20261027.md:328 | 未执行 | 100步骤一图已通过，1/10原步骤组合本轮未分别执行。 |
| T02 | personal-execution-case-evidence-implementation-plan-20261027.md:329 | 通过 | 顶层单图、100步全部无evidenceIds，发布成功。 |
| T03 | personal-execution-case-evidence-implementation-plan-20261027.md:330 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T04 | personal-execution-case-evidence-implementation-plan-20261027.md:331 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T05 | personal-execution-case-evidence-implementation-plan-20261027.md:332 | 通过 | 上传/提交都不传Hash/stepNum/purpose，完成闭环。 |
| T06 | personal-execution-case-evidence-implementation-plan-20261027.md:333 | 通过 | 上传/提交共用同一requestId，均成功。 |
| T07 | personal-execution-case-evidence-implementation-plan-20261027.md:334 | 未执行 | 上传、提交相同请求重放及提交改参冲突通过；字段重排组合未执行。 |
| T08 | personal-execution-case-evidence-implementation-plan-20261027.md:335 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T09 | personal-execution-case-evidence-implementation-plan-20261027.md:336 | 未执行 | 非图片及无图拒绝；11图/体积边界未完整测试。 |
| T10 | personal-execution-case-evidence-implementation-plan-20261027.md:337 | 未执行 | 跨项目读图拒绝；跨用户/Token/用例写入矩阵未测。 |
| T11 | personal-execution-case-evidence-implementation-plan-20261027.md:338 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T12 | personal-execution-case-evidence-implementation-plan-20261027.md:339 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T13 | personal-execution-case-evidence-implementation-plan-20261027.md:340 | 未执行 | 只改运行状态时Hash稳定一次；三个连续执行与所有显示字段未测。 |
| T14 | personal-execution-case-evidence-implementation-plan-20261027.md:341 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T15 | personal-execution-case-evidence-implementation-plan-20261027.md:342 | 未执行 | 100步含ERROR聚合ERROR；乱序/重复/额外组合未完整复测。 |
| T16 | personal-execution-case-evidence-implementation-plan-20261027.md:343 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T17 | personal-execution-case-evidence-implementation-plan-20261027.md:344 | 未执行 | 现行policy含前后图角色要求时CASE单图通过；Runner本轮范围外。 |
| T18 | personal-execution-case-evidence-implementation-plan-20261027.md:345 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T19 | personal-execution-case-evidence-implementation-plan-20261027.md:346 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T20 | personal-execution-case-evidence-implementation-plan-20261027.md:347 | 未执行 | 普通附件URL转存、执行base64上传均通过；执行证据URL路径未执行。 |
| T21 | personal-execution-case-evidence-implementation-plan-20261027.md:580 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T22 | personal-execution-case-evidence-implementation-plan-20261027.md:581 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T23 | personal-execution-case-evidence-implementation-plan-20261027.md:582 | 失败 | 导出65字符返回内部错误；其他业务1/64/65/128正常，129安全拒绝。 |
| T24 | personal-execution-case-evidence-implementation-plan-20261027.md:583 | 未执行 | 两个真正并发请求只生成同一目录ID；提交后断开响应未模拟。 |
| T25 | personal-execution-case-evidence-implementation-plan-20261027.md:584 | 未执行 | 同key变参冲突已测，递归键序/集合矩阵未测。 |
| T26 | personal-execution-case-evidence-implementation-plan-20261027.md:585 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T27 | personal-execution-case-evidence-implementation-plan-20261027.md:586 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T28 | personal-execution-case-evidence-implementation-plan-20261027.md:587 | 未执行 | 撤scope后写重放拒绝、Token删除401；撤成员权限及资源删除矩阵未测。 |
| T29 | personal-execution-case-evidence-implementation-plan-20261027.md:588 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T30 | personal-execution-case-evidence-implementation-plan-20261027.md:589 | 失败 | 原生追加未传schema非必填reviewers被拒绝；浏览器导入被循环阻断，Node契约仍旧。 |
| T31 | personal-execution-case-evidence-implementation-plan-20261027.md:590 | 失败 | 合法导出/65字符参数产生内部错误；其他已测错误保留trace。 |
| T32 | personal-execution-case-evidence-implementation-plan-20261027.md:591 | 未执行 | 普通/历史图片SHA正确、跨项目拒绝；解除关联后读图未执行。 |
| T33 | personal-execution-case-evidence-implementation-plan-20261027.md:592 | 未执行 | 1001事实拒绝，TEXT/空预期/101原步骤未测。 |
| T34 | personal-execution-case-evidence-implementation-plan-20261027.md:593 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T35 | personal-execution-case-evidence-implementation-plan-20261027.md:594 | 失败 | 原生和技能包更新，现有连接器仍旧契约。 |
| T36 | personal-execution-case-evidence-implementation-plan-20261027.md:708 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T37 | personal-execution-case-evidence-implementation-plan-20261027.md:709 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T38 | personal-execution-case-evidence-implementation-plan-20261027.md:710 | 未执行 | CASE新事实100条页面可读；STEP/体积上限未完整执行。 |
| T39 | personal-execution-case-evidence-implementation-plan-20261027.md:711 | 未执行 | 原生真实image通过SHA；Node旧连接器无新读图工具，未独立验收。 |
| T40 | personal-execution-case-evidence-implementation-plan-20261027.md:712 | 未执行 | 活动计划列表/详情/事实及重放通过；归档与筛选全矩阵未测。 |
| T41 | personal-execution-case-evidence-implementation-plan-20261027.md:713 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T42 | personal-execution-case-evidence-implementation-plan-20261027.md:714 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T43 | personal-execution-case-evidence-implementation-plan-20261027.md:715 | 未执行 | 仅凭requestId可查上传/提交；并发不可见窗口未模拟。 |
| T44 | personal-execution-case-evidence-implementation-plan-20261027.md:716 | 未执行 | 上传CONSUMED状态可查；到期清理后重放未测。 |
| T45 | personal-execution-case-evidence-implementation-plan-20261027.md:717 | 未执行 | 仅FUNCTIONAL_READ可读、撤写scope重放拒绝；最小写scope/撤项目角色未完整复验。 |
| T46 | personal-execution-case-evidence-implementation-plan-20261027.md:718 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |
| T47 | personal-execution-case-evidence-implementation-plan-20261027.md:719 | 未执行 | 未完整执行；相应正向子项不代表整个复合用例通过。 |

## 6. 原始R编号与前期回归补充项

R01/R02/R04/R05→AC-024、AC-031；R03→AC-024/026/029；R06→AC-030；R07→AC-028/029/032；R08→AC-032/033。R条目仍以原方案第3节原文为准，未用扩展X编号覆盖原始需求。

前期14项回归：F01失败(D02)；F02两个失败项目重试通过；F03新失败原因全矩阵未执行（历史泛化消息已记录）；F04原生幂等schema/创建通过；F05/F06新事实MCP及源API、计划页面正向读取通过，完整四端矩阵未完；F07清单/index摘要一致，源码revision来源及缓存策略待确认；F08失败(D09)；F09组织分页未执行；F10非法列参数安全拒绝，但长key错误分类失败(D04)；F11/F12因导出失败而阻塞文件内容/多语言验收；F13普通计划→用例→评审跳转未见用例ID被误作计划ID，延迟/草稿矩阵未完；F14仅大屏空态正确，窄屏资产空态未完整验证。

## 7. 验证方式、脚本与修改文件

实际执行：ego-browser skill；ego nodejs执行同一Space 8的round-01～108脚本；Node低堆串行执行native-01～25（native-19仅两个轻量HTTP并发验证去重）。脚本位于本机 `.codex-tmp/msp-acceptance-20261027/`，原始登录步骤不复制到公开证据，临时Token密钥文件已删除。执行期间的定位歧义、过早观察和stale ref均按脚本问题处理，重新观察后定位；没有把这些工具错误认作产品缺陷，也未把最后成功覆盖真实产品失败。

本轮只新增测试文档、截图证据、读用例脚本，**没有修改生产前后端、数据库迁移或容器配置**。页面操作与接口链路：目录移动→`functional/case/module/move-to-parent`；导入预检/关联策略→`ai/case-imports`；资产批改→`case-asset-batches`；资产导出→`case-asset/catalog/{id}/export`及task查询；计划全同步→`test-plan/functional/case/sync-jobs`；原生Agent→`/api/mcp`。失败调用已保留taskId/traceId。

补充 Playwright 读用例见 `frontend/e2e/msp-live-acceptance-20261027.spec.ts`：按实际DOM编写，用于后续CI复测。本轮遵循用户要求只使用ego，没有再启动Playwright浏览器；脚本发现/语法检查不能充当浏览器执行通过。

未执行：全量前后端构建、单元/数据库集成测试、镜像构建与新容器启动（本轮未改产品代码，且本机低内存）；服务故障注入、跨账号/跨组织、多浏览器、精确1280/1366/200%、七天到期清理、历史迁移升级、全部REST并发与崩溃恢复。风险是这些边界仍可能有缺陷，不能以本轮正向通过外推。可在隔离CI、具备资源与授权后按仓库既有流程补验。

后续读用例命令（需通过既有安全方式准备该账号登录态；不要把凭据或storageState提交Git）：

```powershell
$env:PW_BASE_URL='https://msp.ebcone.net'
$env:PW_STORAGE_STATE='C:/private/msp-auth-state.json'
node --max-old-space-size=256 frontend/node_modules/@playwright/test/cli.js test --config frontend/playwright.config.ts msp-live-acceptance-20261027.spec.ts --workers=1
```

## 8. 数据、登录态与收尾

- 浏览器预检job9d548938已STOP；原生导入的3条隔离源用例、2个计划、1个评审、1个关联测试缺陷及证据保留供复核，未冒充真实业务用例结果。
- 删除验证的源用例A已恢复，计划重新显示2条。两个人工资产A/B通过批量删除进入回收站，仅处理本轮创建对象。目录移动失败后保留本轮空父子目录及边界/并发目录，未强制改库修复revision。
- 本轮平台/登录配置均停用；组织置顶已取消；用例搜索清空、选择清零、原20条分页偏好恢复。
- 临时Token1710856224717676544已删除，服务返回401；没有修改/删除既有Token，也未清理用户登录Cookie。ego任务已finish关闭，不留后台轮询。
- ego提示有新版本，本轮未升级，以免改变测试浏览器；若后续升级需另行授权。

## 9. 交付状态

**测试交付：部分完成。被测功能：部分完成。** 已留存正反向证据并发现10项问题/接入差异；不能宣称所有近两日需求已通过。建议优先修复D01请求循环、D02操作不可达、D03/D04导出和D05持续同步，再修D07目录revision；D06/D08/D09/D10补契约与显示一致性。之后先复测失败点，再补未完整验收矩阵，保持需求基准不变。


本轮新增脚本的静态检查结果：目标文件ESLint通过；`node --max-old-space-size=128 frontend/node_modules/@playwright/test/cli.js test --config frontend/playwright.config.ts msp-live-acceptance-20261027.spec.ts --list` 成功发现4条读用例。此命令只发现测试，不启动浏览器，**不计4条测试通过**。`git diff --check`和证据完整性检查通过。证据静态检查包括JSON可解析、原图SHA/尺寸一致、相对链接存在以及未包含Token明文或登录验证码。
