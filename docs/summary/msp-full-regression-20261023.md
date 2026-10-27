# MSP 近两日需求、修复及优化发布回归

状态：**测试交付部分完成；本轮已执行的核心回归和页面巡检未通过。** 环境：https://msp.ebcone.net；日期2026-10-23。55个实际检查点：39通过、16失败；归并14项问题（含客户端契约与发布识别问题），另2项待明确口径。此数字不是近两日全部需求的验收通过率。

优先处理：Token操作不可达、资产同步目录冲突、MCP执行事实不完整、用例详情触发计划HTTP500、组织分页错误、导出成功但内容丢失。已通过项包括正常A/B切换、配置校验和保存、评论版本冲突、直接执行结构校验、源/计划当前结果及附件一致、手工同步、删除确认路由及取消、两条用例异步导入。

## 基线与边界

依据22日需求实现汇总、缺陷修复汇总，23日LIVE详细修复方案、SQL稳定性优化方案及两份实施记录。后者是实现进度证据，不替代需求。主基线不重复统计：REQ-01、LIVE-01～14、SYNC-01～03、DEF-02/03、INC-R01～11、O01～09，共40项；REQ-02～06及DEF-01映射LIVE/SYNC。新增探索项UI-01 Agent Token裁切、UI-02全站同类布局、ASSET-01同步失败与原因展示。

未提供额外输入用例；从文档派生检查。默认使用现有测试账号及隔离项目，业务写操作只作用于本轮专用数据，不修改现有业务执行结果、不做负载压测、不停启本地容器、不发送外部消息。完整长期保留/备份、跨实例故障恢复等没有现场证据不得判通过。

## 检查范围与当前结论

| 基线 | 页面/接口及检查 | 方法 | 结果 |
| --- | --- | --- | --- |
| REQ-01 | 外链图片转存、附件展示、拒绝私网、删除一致性 | MCP+页面+接口 | 已有通过证据；源失效/备份/全部目标类型待验 |
| LIVE-01/14、SYNC-01 | 计划A/B、切项目、自动刷新 | 页面/请求/前后数据 | 正常路径通过；深链接发现缓存计划误请求500；草稿/慢网络仍待验 |
| LIVE-02～05 | 计划报告、生成用例Tab、资产AI入口隐藏 | 页面/路由 | 本轮可达入口通过，全部旧路由/角色矩阵待验 |
| LIVE-06/10/11/12 | MCP幂等、评论冲突、自定义字段、计划查询 | 真实MCP/REST | 评论及缺陷类型修复通过；创建契约、统一执行事实仍失败 |
| LIVE-07、DEF-02/03 | 平台/登录配置校验及合法保存 | 页面+请求 | 所测校验、单次提示、保存/返回列表通过 |
| LIVE-08/09 | 分页、偏好、批量按钮 | 页面 | 四档及20条重入、0/1选中通过；跨页/全筛选/多角色待验 |
| LIVE-13 | 删除确认路由及取消 | MCP+页面 | 链接可打开、取消不删除；未执行真人确认后的实际删除/恢复 |
| SYNC-02/03 | 共享字段、执行、附件、缺陷、任务 | 页面+MCP+接口 | REST当前结果/附件与3/3任务通过；MCP/源详情执行事实仍不完整 |
| INC-R01～11 | 事故整改、调度、导入、SQL、断连、日志、停止 | 读接口/小规模导入 | 仅部分业务冒烟，故障注入及服务端日志验收未执行 |
| O01～09 | SQL优化、计数、批量、保护、导入、鉴权、恢复、指标 | 小规模真实调用+源码定位 | 基本查询/汇总/导入/撤权通过；规模、计划和监控待验 |
| UI-01 | Token操作可达性 | 1908/1366截图+DOM边界 | 失败 |
| UI-02 | 主要模块、系统/组织及个人设置巡检 | 页面及1366窄屏 | 发现资产空态裁切、组织分页；未宣称所有子页面/弹窗均验收 |
| ASSET-01 | 资产同步失败与原因 | 重试+真实接口+源码追因 | 两项目仍失败，原因展示仍不足 |

## 执行记录与缺陷

脚本位于`.codex-tmp/msp-regression-20261023/round-01.mjs`起，单一Ego任务space=4。截图、脱敏响应及逐项结果保存于outputs/msp-full-regression-20261023/。截图未标注时明确记录，仅DOM存在不能判可见或可操作。

### 已取得结果

- 真实MCP服务端tools/list返回115项。原生项目/用例创建schema已要求requestId；隔离用例创建与同键重放返回同一ID，改参返回IDEMPOTENCY_CONFLICT。当前连接器缓存schema仍无requestId，创建失败IDEMPOTENCY_KEY_REQUIRED，traceId `99be093f-44c5-4402-ba3a-d97040b06fbc`。应区分服务端修复与客户端契约刷新。
- 计划创建服务端schema仍无requestId：原生无头调用失败，traceId `a9a21381-f59b-44e5-9325-591dfa56b48e`；补Idempotency-Key头后成功。静态工具清单发现20个写工具没有requestId，不能将全部20个都视为已复现失败，仍需逐个核对通用层豁免。
- LIVE-01基本A→返回→B→返回→A已通过，标题/编号/文档归属正确；LIVE-02～05对应入口本轮可见路径已隐藏；用例分页展示10/20/50/100并成功切20；零选中批量按钮常驻禁用。慢网络/多角色/全部旧路由及重新登录偏好仍待验证。
- 合法平台配置、登录配置均保存成功且保持停用；无协议URL及非法JSON前置拒绝，无新增请求，各显示一次提示。证据environment-invalid-url、login-invalid-json、environment-created；持久重入检查待补。
- LIVE-11：缺陷customFields数组被安全拒绝AGENT_VALIDATION_ERROR；字符串形式成功创建。MCP计划用例查询正常返回，旧内部错误未再出现。
- REQ-01：公网W3C PNG成功转存ATTACHED，返回内部文件/关联ID，页面附件区可见；回环地址拒绝EXTERNAL_IMAGE_TARGET_FORBIDDEN。外部源失效、备份恢复及各目标类型仍未验全。
- 个人直接执行：真实本轮MSP页面截图按步骤前后上传；缺原始步骤提交拒绝QUALITY_FINAL_FACTS_INCOMPLETE。乱序+重复的3条事实覆盖2个原始步骤，提交ACCEPTED/PUBLISHED SUCCESS。只改变本轮新建隔离用例。
- 未点击手工同步前，源用例与计划REST结果SUCCESS、执行时间相同，计划executionRef正确指向直接提交；executionFacts保留3条、附件5个、缺陷数1。手工同步UI后任务`1cf7d4fe-d955-44ce-9f4c-d1ecee660799`最终SUCCEEDED，3/3计划成功，updatedCount=1、defectAddedCount=1。
- **MCP读取口径仍不完整**：同一计划MCP查询只返回2条定义步骤，actualResult/executeResult均空，没有REST的3条executionFacts；见plan-cases-after-direct.json与source-plan-after-direct-submission.json。

### 已复现缺陷与根因证据

| 编号 | 现象与影响 | 原因/证据等级 | 证据 |
| --- | --- | --- | --- |
| REG-UI-01 | Agent Token启用/操作列不可达，无横向滚动 | 已复现：1908px视口，table右边界2099，操作列1958～2098。源码agentIntegration/index.vue使用scroll.x=100%而列最小总宽度超容器；个人抽屉布局缺收缩约束。不是仅权限隐藏 | agent-token-1908.png、agent-token-layout.json |
| REG-ASSET-01 | 两个历史项目同步反复失败，caseCreated=0 | 重试后仍失败；失败项目准备阶段未提供底层错误。高可信源码定位：两项目已有枢纽根模块同名，但资产目录缺失；upsertCatalog仍INSERT同名根模块触发uq_name_project_parent，catch只查询case_asset_catalog，查不到后重抛。尚缺服务端异常栈，不能冒称已读日志 | asset-retry-final.json、all-asset-catalogs.json；根模块测试项目1=1569981005350461454、测试1=1569996467232727116；V3.0.0_10唯一索引与CaseAssetService.upsertCatalog |
| REG-ASSET-02 | “查看失败原因”只有“同步失败，请按编号查日志”，用户无法定位/处理 | 已复现：API与页面同为泛化文案。非MSException全部丢失分类，项目准备异常也无错误阶段/安全原因；历史已有泛化记录不会自动补具体内容 | asset-failure-details.png、asset-failed-job-detail.json；任务1636867424404733952，失败item1636867424404733958/3959 |
| REG-MCP-01 | 原生计划创建无法提供幂等键 | 已复现；schema缺requestId且strict additionalProperties=false，通用写入要求幂等键 | new-plan-native-no-key.json、new-plan-header-key.json |
| REG-MCP-02 | 计划MCP与REST执行事实不一致 | 已复现，MCP仍按源定义映射steps且未暴露统一executionRef/facts | plan-cases-after-direct.json、source-plan-after-direct-submission.json |
| REG-REL-01 | 发布清单URL返回HTML200，无法确认SHA；重载前旧index-Cql24jZA，重载后index-DHmDrRCI | 已观察。仅能确认已加载的新资源版本，不能仅凭此证明Git SHA；需要核对流水线是否执行postbuild及静态服务fallback | build-manifest.json、浏览器资源记录 |

本轮专用数据：项目1704667279923322880；用例1704903606203834370；计划1704904997773238272，关联1704904997773238281；缺陷1704905959845912576；直接提交18d9e743-ad82-4f42-b8f1-7ca899ed0fbb。另导入用例1704973425192198144/145。临时Token名称REGRESSION-20261023-temporary，ID1704900342028689408，限本验收项目；结束时已停用，并实测下一次请求HTTP401；本地临时密钥文件已删除，现有用户Token与网页登录态保留。

工作台、项目管理、业务文档、用例资产、测试数据、计划列表/详情、用例列表/详情、接口调试、缺陷列表、执行平台、登录/角色抽屉、执行记录、全局门禁、系统用户/权限/组织项目/组织架构/资源池/任务中心/插件/License/操作日志、组织成员/项目/集成/模板/任务/日志，以及个人本地执行/三方账号/模型/Agent页已访问。常规视口1908×895；Token另验1366×900，核心用例/文档/平台/缺陷及用例资产另验1366×900。其他宽表具有滚动容器，不把视口外列一律认定缺陷；平台凭据引用抽屉为空，仅确认宽表滚动结构，未声称有数据时操作已验。

## 补充问题、优先级与具体处理方向

以下与前表合计归并14项，不按重试次数或同一问题的两个分辨率重复计算：前表6项，新增8项。REG-MCP-CLIENT-01属于客户端契约，REG-REL-01属于发布识别缺口。

| 编号/优先级 | 复现与实际结果 | 根因证据及修复方向 | 验证证据 |
| --- | --- | --- | --- |
| REG-MCP-CLIENT-01 / P1 | 当前metersphere_test连接器创建用例无requestId而失败，原生调用成功 | 服务端新schema已提供必填参数，但已连接客户端仍暴露旧schema。刷新/重连并核对工具参数；不能把此项全部归咎服务器 | connector-create-error.json、native-mcp-tools.json、native-create-case.json |
| REG-ORG-01 / P1 | 组织架构请求pageSize=20返回154行、total=1；请求current=2&pageSize=10仍current=1且154行 | Controller在OrgStructureMemberService.page权限检查前startPage；validateReadable先读用户/授权Mapper，分页被提前消耗。鉴权完成后紧贴成员Mapper开启分页；页码/COUNT与数据同源 | department-members-response-valid.json、department-members-page2.json；OrgStructureMemberController、OrgStructureMemberService、OrgStructureAccessService |
| REG-MCP-03 / P2 | export.start的systemFields传字符串数组时得到AI_EXECUTION_INTERNAL_ERROR/-32603，traceId=7cbc45fe-a881-43bd-8137-e4de6dd51e6e | 这是明确的非法输入，但内部request只定义object且JSON转DTO异常未归一为参数错误。补嵌套字段schema与Bean校验，转换失败映射安全参数错误；不把负例输入说成合法导出失败 | export-start.json、AgentCaseExportTools |
| REG-EXP-01 / P1 | 不带Accept-Language的合法导出任务SUCCEEDED/count=1，但文件表头Not Support Key、数据行全空 | Locale默认/资源兜底需核对；导出行值按翻译后的表头识别字段，翻译失败同时丢数据。按稳定字段ID取值，显式语言兜底；不能只把任务成功当导出成功 | export-smoke-result.xlsx、export-workbook-content-verification.json；FunctionalCaseFileService.parseExcelData2List |
| REG-EXP-02 / P1 | 带zh-CN后名称正确、ID列仍空，源用例num=100005 | 导出列num翻译名为ID，导入字段匹配用例ID/Case ID，表头匹配失败。导出按字段ID映射，展示标题独立；验证中英文及ID非空 | export-zh.xlsx、export-workbook-content-verification.json；FunctionalCaseExportColumns、FunctionalCaseImportFiled |
| REG-SYNC-01 / P1 | 直接提交SUCCESS且含3条事实，但源详情两条原步骤均未执行、实际值为空，没有该次facts展示 | 源详情仍只展示定义steps；目前执行适配器只接到计划REST。应统一source/plan/MCP执行Ref和facts读模型，在独立执行区域展示，不把重复事实塞回作者定义 | source-after-direct-execution.png、source-after-direct-ui-status.json、execution-complete-repeated.json |
| REG-FE-01 / P1 | 先访问计划再打开带id的源用例深链接，产生GET /test-plan/1704903606203834370及POST /test-plan/statistics的500；页面弹两条服务异常 | 缓存计划detail/index.vue的route.query.id watch未限制当前路由，错把用例ID当计划ID。watch与请求均加计划路由/项目身份门槛、旧响应隔离；后端不存在计划返回安全404/业务错误，不能NPE。截图traceId为5450f98a-42a0-41d0-9a0e-4245f9e39a0c及eeb5fb4d-41f0-4129-b87e-fc1367ffa7c7 | browser-audit-after-source-execution.json、source-after-direct-execution.png |
| REG-UI-02 / P2 | 1366资产空列表提示仅在最右露出“当前”，全文无法直接读 | 空态文本占1598px、右边界2170px，跟随最小宽表居中；可见内容区约787px。空态绑定可见容器宽度，宽表滚动不应影响空态布局 | case-assets-1366.png、case-asset-empty-state-bounds.json |

### 前表重点问题的修复约束

- REG-UI-01：个人内容区已有overflow-x-hidden，内部Card/表格实际撑宽。沿flex链检查min-width与实际宽度约束，给表格明确最小总宽及自己的横向滚动容器，固定操作列必须相对可见表格边界。不要靠隐藏“启用/操作”或扩大窗口规避。复验1908、1366及常见窄屏、长名称与多权限。
- REG-ASSET-01：先查可信的既有catalog/module/project映射，再决定复用历史FOLDER。若同名模块属于别的项目或用途，返回安全可理解冲突或创建稳定唯一模块，不能直接把任意同名模块据为己用。目录和映射建立放同一事务，处理并发唯一冲突及自己创建的孤儿模块。先为测试项目1、测试1做只读映射核对，再迁移/补建catalog；保留既有用例和引用，重试只作用失败项。
- REG-ASSET-02：返回结构化stage/errorCode/projectId/caseId/安全原因/traceId及重试建议，技术异常进日志。至少区分目录冲突、项目/模板/权限不可用、字段不兼容、存储及超时；失败行分页查询，历史泛化错误重试后补新原因。界面应显示可理解原因，不直接显示SQL/堆栈，也不以“查日志”代替原因。
- REG-MCP-01：逐一核对通用层确实要求幂等键的写工具；将requestId加入严格schema，或明确协议自己的状态机豁免。不能要求标准客户端额外塞HTTP头，也不能无差别把20个候选全部判失败。保留同键同参、同键变参、头参冲突、撤权后重试验收。
- REG-MCP-02/REG-SYNC-01：定义steps与executionFacts分别返回；direct/managed/manual路径统一适配，携带case版本及权威executionRef，额外/重复步骤完整保留。当前可接受结构校验不等于独立验证业务执行，保留STRUCTURE_ONLY_V1/AGENT_REPORTED说明。
- REG-REL-01：核对实际CI命令是否触发生成清单，静态包是否包含manifest，以及网关不存在文件的fallback。清单至少绑定真实Git SHA、index hash、资源列表。现网URL返回HTML200不能当JSON版本证据。

### 待明确口径，未计入14项确认问题

1. 手工同步后源详情bugCount=2，直接关联列表1条，计划bugListCount=1，同一bug在源/计划两种上下文有关系。方案要求按bugId去重；若页签本意展示关系数则必须明确标注。应统一源列表/页签/计划统计口径，不能仅凭数字2就认定存在两个独立缺陷。证据source-plan-defect-counts.json、source-defect-relations.txt。
2. functional.get(caseId, testPlanId=不包含该用例的计划B)返回源详情，testPlanId/testPlanCaseId均null且无警告。当前工具描述未明示“必须属于计划”，不直接定为越权；需明确是补充计划上下文还是严格计划过滤。证据case-not-in-plan.json。

## 已通过路径与证据边界

1. 计划A/B/A往返标题、编号及文档归属正确。另在计划用例页停留，MCP修改源名称/描述/标签，未点击刷新，后续120秒观察窗口内页面已显示新名称；REST字段也一致。此测试没有精确采样第一次刷新时间，不能宣称已证明某个秒级SLA。
2. 源及活动计划REST执行结果SUCCESS、执行时间相同、正确direct submissionRef；保留3条乱序/重复事实。附件删除前两端5个，删除本轮公网测试附件后两端4个，计划不残留该附件。未改旧业务项目的执行结果。
3. 配置输入不合法没有发业务请求，仅一条安全提示；合法平台及登录配置保持停用，离开页面重入仍能查到。所测定位器/断言只用于存储，没有执行自动登录。
4. 缺陷字符串字段创建、数组安全拒绝；评论创建/编辑成功，旧版本被VERSION_CONFLICT拒绝，测试评论随后按当前版本删除。未向外部收件人发送消息。
5. 新项目用例MCP分页总数5，2/2/1三页，hasMore正确，无重复ID；includeSteps=false不返回步骤正文。该观察发生于导入两用例前；导入后项目数为7，不能拿前后不同数据阶段的总数做错误对比。
6. 手工同步3/3成功；新导入任务442ef7ba-9fce-409a-9704-549ed73dd9aa预检2条，提交后COMPLETED，两个item均SUCCESS且有真实caseId；未验证断进程、多副本、500+条规模与补偿队列最终状态。
7. 删除确认链接加载目标与状态，不再404；选择取消后MCP继续调用返回CANCELLED，源用例仍可读，未执行真正删除。受限Token访问其他项目被拒绝，结束停用后下一次原生MCP请求401。

## 环境、脚本与质量限制

- 执行：`C:/Users/midoo/.local/share/ego/active_version_dir/ego-browser.exe skill`及`nodejs -e "await import('file:///C:/SoftWare/JetBrains/metersphere/.codex-tmp/msp-regression-20261023/round-NN.mjs')"`，NN=01～94；每轮独立文件，未为恢复页面创建第二个TaskSpace。部分轮次因定位歧义/输入值与文本定位差异/异步路由时序失败，均读取页面后修正，未计为产品失败。
- 第一批系统巡检少数截图发生于路由切换中，已补稳定页面证据；不能仅凭初次瞬间空表判失败。系统架构初次快速切换捕获两条通用未处理拒绝，稳定重入未再现、接口均200，保留待调查，不纳入确认缺陷。
- 实际错误取证：页面截图、DOM几何、XHR返回码/URL、真实MCP协议、REST读回、工作簿XML解析及源码定位。没有读取服务端运行日志或数据库，不把静态推导冒充已取得异常栈。
- 仅新增本报告，未修改前后端业务源码、迁移或配置；因此本轮不重跑单元测试、类型检查、构建、镜像或迁移，不把已有构建证据当成此次部署证明。数据库故障、锁/慢SQL执行计划、调度/恢复、日志增长、停止宽限及备份恢复需要服务端观测/隔离演练窗口。
- 真实测试数据、截图和脱敏响应在outputs目录（已被Git忽略）；临时密钥已删除，文本中的手机号型企微ID已脱敏；组织架构截图采用不改变布局的覆盖遮罩。Token标注图是证据索引，不作为原图像素比较依据。
- 登录态复用并保留；本轮没有扫码/验证码登录，没有清缓存。Ego任务已finish，未留下额外代理页面；未启动、停止或重建本地Docker/应用，没有本机压测。
- 归档/固定版本、全部角色拒绝、取消/重试失败路径、源链接实际失效、全部业务目标、托管AI冻结事实、备份/大规模/多副本尚未覆盖。测试结果仅适用于本账号、所述视口与数据；不能声明MSP全部功能无其他问题。

**结论：本轮回归未通过，功能状态部分完成。** 先修复P1及错误原因展示，再按相同数据身份重测受影响链路，并补未覆盖验收；任务/HTTP成功不能替代文件内容、执行事实与页面可操作性的验证。

<!-- GENERATED_RESULTS -->

## 逐项需求追踪与检查点结果

主基线40项：通过9、失败3、待确认19、未执行9。基线映射40/40；基础目标取得通过证据9/40（22.5%），不代表扩大后的角色/边界/运维验收已全部覆盖。

| 基线ID | 本轮状态 | 实际覆盖与限制 |
| --- | --- | --- |
| REQ-01 | 待确认 | 公网转存、展示、安全拒绝、删除一致通过；源失效/备份/全部目标未验 |
| LIVE-01 | 待确认 | A/B/A通过；快速/慢请求及深链接缓存缺陷仍需复验 |
| LIVE-02 | 通过 | 本轮主要入口隐藏通过；旧路由/角色扩展检查另列未覆盖 |
| LIVE-03 | 通过 | 本轮主要入口隐藏通过；旧路由/角色扩展检查另列未覆盖 |
| LIVE-04 | 通过 | 本轮主要入口隐藏通过；旧路由/角色扩展检查另列未覆盖 |
| LIVE-05 | 通过 | 本轮主要入口隐藏通过；旧路由/角色扩展检查另列未覆盖 |
| LIVE-06 | 失败 | 服务端项目/用例schema已修，当前连接器未更新；同类计划写入schema仍缺幂等键 |
| LIVE-07 | 通过 | 所测无协议/JSON拒绝且单次提示；合法配置保存 |
| LIVE-08 | 待确认 | 四档与20条重入通过；重新登录、越界及组合筛选未验 |
| LIVE-09 | 待确认 | 0/1选中通过；多选跨页/全筛选/权限矩阵未验 |
| LIVE-10 | 通过 | 真实评论旧版本VERSION_CONFLICT，未覆盖新正文 |
| LIVE-11 | 通过 | 字符串成功，数组参数错误；非法选项/未知字段仍需扩展 |
| LIVE-12 | 失败 | 计划查询内部错误消失，但计划REST/MCP执行事实不同 |
| LIVE-13 | 待确认 | 链接与取消通过；实际删除恢复/过期/权限未验全 |
| LIVE-14 | 待确认 | 基本切项目不显示旧详情；快速/保存归属仍待验 |
| SYNC-01 | 待确认 | 源改名后计划无需手刷可见；草稿/隐藏离线/全部进入路径未验全 |
| SYNC-02 | 失败 | REST摘要/附件通过，源UI与MCP仍不展示完整当前执行facts |
| SYNC-03 | 待确认 | 3/3成功任务通过；取消、失败重试、全部数据域未验全 |
| DEF-02 | 通过 | 本轮合法创建与输入前置校验通过 |
| DEF-03 | 通过 | 本轮合法创建与输入前置校验通过 |
| INC-R01 | 未执行 | 无服务端异常栈/调度演练；不能用正常业务查询替代 |
| INC-R02 | 未执行 | 无服务端异常栈/调度演练；不能用正常业务查询替代 |
| INC-R03 | 待确认 | 异步新导入2条通过；传统同步入口和历史任务完整验收未做 |
| INC-R04 | 待确认 | 小规模导入/查询通过；资源与批量成本无服务端观测 |
| INC-R05 | 待确认 | 小规模导入/查询通过；资源与批量成本无服务端观测 |
| INC-R06 | 未执行 | 未执行慢SQL/锁/断连故障注入 |
| INC-R07 | 未执行 | 未执行慢SQL/锁/断连故障注入 |
| INC-R08 | 待确认 | 业务错误trace可取；全链路指标及故障现场未验 |
| INC-R09 | 未执行 | 日志增长/停机宽限/会话销毁均需隔离运维窗口 |
| INC-R10 | 未执行 | 日志增长/停机宽限/会话销毁均需隔离运维窗口 |
| INC-R11 | 未执行 | 日志增长/停机宽限/会话销毁均需隔离运维窗口 |
| O01 | 待确认 | 已做小规模分页、includeSteps、计划汇总；未做跨组织元数据/全筛选/大批量执行计划 |
| O02 | 待确认 | 已做小规模分页、includeSteps、计划汇总；未做跨组织元数据/全筛选/大批量执行计划 |
| O03 | 待确认 | 已做小规模分页、includeSteps、计划汇总；未做跨组织元数据/全筛选/大批量执行计划 |
| O04 | 待确认 | 已做小规模分页、includeSteps、计划汇总；未做跨组织元数据/全筛选/大批量执行计划 |
| O05 | 未执行 | 未对共享测试环境做过载/超时与连接池耗尽演练 |
| O06 | 待确认 | 2条导入成功；outbox最终状态/断进程/多实例未验 |
| O07 | 待确认 | 受限跨项目拒绝、Token停用立即401通过；角色撤权并发未验 |
| O08 | 未执行 | 未造恢复积压或中断既有执行 |
| O09 | 待确认 | 取得业务trace；指标采集/告警/高峰基线未验 |

实际检查点55项：39通过、16失败，通过率39/55=70.9%；阻塞0、跳过0、待确认0、未执行0（仅指这55项实际执行检查，不包括上表未验收的主基线）。同一缺陷的不同视口/页码分别保留检查结果，但不重复计缺陷。

| 检查点 | 检查内容 | 结果 | 原始证据文件（outputs/msp-full-regression-20261023/） |
| --- | --- | --- | --- |
| CHK-01 | 发布清单是JSON且可对账 | 失败 | build-manifest.json |
| CHK-02 | Token操作列1908可达 | 失败 | agent-token-final-stable-layout.json |
| CHK-03 | Token操作列1366可达 | 失败 | agent-token-1366-attempt-layout.json |
| CHK-04 | 临时Token限定验收项目 | 通过 | temporary-token-metadata.json |
| CHK-05 | 公网图片转存为内部附件 | 通过 | attachment-public-import.json |
| CHK-06 | 回环图片地址拒绝 | 通过 | attachment-private-rejected.json |
| CHK-07 | 转存附件在页面可见 | 通过 | source-attachment-visible.txt |
| CHK-08 | 原生MCP创建用例 | 通过 | native-create-case.json |
| CHK-09 | 相同幂等键返回同一用例 | 通过 | native-create-case-replay.json |
| CHK-10 | 同键改参冲突 | 通过 | native-create-case-conflict.json |
| CHK-11 | 当前连接器创建参数可用 | 失败 | connector-create-error.json |
| CHK-12 | 原生无自定义头创建计划 | 失败 | new-plan-native-no-key.json |
| CHK-13 | 幂等头补偿创建计划 | 通过 | new-plan-header-key.json |
| CHK-14 | 缺陷数组字段安全拒绝 | 通过 | bug-array-invalid.json |
| CHK-15 | 缺陷字符串字段正常创建 | 通过 | bug-string-create.json |
| CHK-16 | 计划MCP查询不再内部报错 | 通过 | new-plan-cases.json |
| CHK-17 | 计划MCP暴露完整执行事实 | 失败 | plan-cases-after-direct.json |
| CHK-18 | 缺原始步骤提交拒绝 | 通过 | execution-missing-step.json |
| CHK-19 | 乱序重复执行事实结构提交 | 通过 | execution-complete-repeated.json |
| CHK-20 | 源及计划REST当前执行一致 | 通过 | source-plan-after-direct-submission.json |
| CHK-21 | 手工同步任务3/3成功 | 通过 | plan-sync-job-result.json |
| CHK-22 | 评论旧版本返回VERSION_CONFLICT | 通过 | comment-edit-stale.json |
| CHK-23 | 删除确认链接不再404 | 通过 | deletion-confirm-route.txt |
| CHK-24 | 取消预检不删除用例 | 通过 | case-after-cancel.json |
| CHK-25 | 计划A/B/A标题与文档归属 | 通过 | plan-a-after-b.txt |
| CHK-26 | 计划生成报告入口隐藏 | 通过 | plan-a-detail.txt |
| CHK-27 | 生成用例Tab隐藏 | 通过 | source-case-list.txt |
| CHK-28 | 业务文档AI生成入口隐藏 | 通过 | documents.txt |
| CHK-29 | 用例资产四项AI控件隐藏 | 通过 | case-assets-loaded.txt |
| CHK-30 | 无协议地址前置拒绝且无请求 | 通过 | environment-invalid-url.txt |
| CHK-31 | 合法平台配置持久可查 | 通过 | environment-created.txt |
| CHK-32 | 非法JSON前置拒绝且无请求 | 通过 | login-invalid-json.txt |
| CHK-33 | 合法登录配置保存 | 通过 | login-created.txt |
| CHK-34 | 分页四档与20条重入偏好 | 通过 | case-list-reentry.txt |
| CHK-35 | 零选择批量常驻禁用 | 通过 | source-bulk-disabled.json |
| CHK-36 | 单选后批量动作启用 | 通过 | source-bulk-one-selected.json |
| CHK-37 | 切项目不显示旧详情 | 通过 | case-cross-project-ces.txt |
| CHK-38 | 资产两个失败项目重试成功 | 失败 | asset-retry-final.json |
| CHK-39 | 资产失败展示具体原因 | 失败 | asset-failure-details.txt |
| CHK-40 | 组织成员第一页分页和总数 | 失败 | department-members-response-valid.json |
| CHK-41 | 组织成员第二页内容及页码 | 失败 | department-members-page2.json |
| CHK-42 | 导出错误字段结构返回参数错误 | 失败 | export-start.json |
| CHK-43 | 无语言头导出内容完整 | 失败 | export-workbook-content-verification.json |
| CHK-44 | 中文导出用例ID完整 | 失败 | export-workbook-content-verification.json |
| CHK-45 | 两条用例异步导入完成 | 通过 | import-final.json |
| CHK-46 | 源详情显示当前直接执行事实 | 失败 | source-after-direct-ui-status.json |
| CHK-47 | 缓存计划页不误用用例ID请求 | 失败 | browser-audit-after-source-execution.json |
| CHK-48 | 窄屏资产空态提示可读 | 失败 | case-asset-empty-state-bounds.json |
| CHK-49 | 源附件删除后计划一致 | 通过 | attachments-source-plan-after-remove.json |
| CHK-50 | 受限Token跨项目读取拒绝 | 通过 | limited-token-cross-project.json |
| CHK-51 | 停用临时Token立即401 | 通过 | revoked-token-denied.json |
| CHK-52 | 核心四页1366操作按钮可达 | 通过 | narrow-screen-layout-summary.json |
| CHK-53 | MCP分页总数/边界/includeSteps | 通过 | mcp-paging-summary.json |
| CHK-54 | 计划页面不手刷显示源名称更新 | 通过 | plan-auto-refresh-after-source-update.txt |
| CHK-55 | 计划概览统计与已执行状态一致 | 通过 | case-overview.json |

本轮只修改本Markdown报告；原始截图、标注图、JSON及工作簿为本地测试产物。未执行源码修复或发布。已知风险与未覆盖范围见前文，不能据此对整个平台签署全部通过。
