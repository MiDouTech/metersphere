# 本周代码核对与剩余验收补测（2026-10-27）

## 1. 结论与范围

**UI 未达到最终 Demo 的样式要求；测试及被测功能均为部分完成。** 本轮已补测此前遗漏的真实页面对照、窄屏布局、可见计划自动刷新、旧链接、跨项目切换、MCP 契约边界和部分幂等路径。不能把这些子项通过扩大为整份方案验收通过。

环境为 https://msp.ebcone.net，账号角色为陈启芬／米多公司，沿用现有登录态。使用 ego 0.6.0.16，同一 TaskSpace 9 串行操作；未启动本地业务环境或构建。测试浏览器提示有更新，本轮未升级。

本周按 2026-10-26 起提交核对：

| 提交 | 内容 | 验收影响 |
|---|---|---|
| fd09cbc8e1 | 146 个文件，整合资产、导入联动、UI、MCP 用例级证据及幂等改造 | 不是只验 MCP；需覆盖原业务页面、任务、权限、关联数据及异常路径 |
| 580b7aed02 | CaseAssetBatchActions 表单绑定 model | 资产批量弹框应能打开并提交实际任务 |

已检查提交文件清单并阅读关键入口和实现：case/plan/review SFC、公共表格与 management-review.less、路由兼容、导入与刷新逻辑、资产批量请求/服务、Agent 参数规范化、用例更新、评论并发及工具 schema。全部改动文件见 code-inventory.json；没有把文件清单核对冒充逐行审计所有实现。

基准仍为前次三份方案及已确认 Demo，整合方案第 14 节优先；输入 AT01～28、T01～47 及原 36 条需求保留，未重新删减。范围安排见 [scope.md](scope.md)，原验收报告见 [前次报告](../msp-acceptance-20261027/report.md)。本轮不重测无后续改动的既有通过项，也不重复触发已知导入循环或反复启动失败导出。

## 2. UI 对照：不符合的具体位置

基准为仓库 docs/demo/msp-real-frontend-style-review-20261023.html，当前文件含交互实验区。原型仅使用示例数据，不作为业务统计或权限证据。实测大屏 1920×963，Demo 为 1908×951；窄屏用 CDP 仿真后确认 innerWidth=1280、innerHeight=900。没有进行跨尺寸的像素相等判定，也没有修改 Demo 或给正式页面注入样式来制造通过结果。

| 区域 | Demo／方案要求 | 实际结果 | 判断 |
|---|---|---|---|
| 用例列表批量条 | 位于表格上方；优化 CSS 有 12px 内边距、底色、边框与圆角 | 表格下方；padding=0、透明背景、圆角=0 | 不符合；常驻及禁用交互的既有通过不能抵消样式缺失 |
| 左侧目录 | 优化 CSS 大屏 252px、1400 以下 220px | 用例/计划约 298.5px，1280 下计划仍约 298.5px | 优化未完整落地 |
| 表头/行高 | 优化表头 #f8f9fc、表格单元格 height=56px | 实际列表表头白色，计划列表行高 49px | 不一致；不同内容造成高度变化须另外区分，不能只看像素差 |
| 用例详情 | Demo 用左右两栏组织正文及属性 | 现网保留原基本信息/详情等页签及纵向进度区域，没有该布局 | 样式布局差异；不能为贴近简化 Demo 而删除现有入口或业务字段 |
| 计划详情 | 优化 CSS 含顶部紫色强调线、20px 内容间距 | 未完整应用这些优化；文档保存/预览/重置/导出等原入口保留 | 样式未全覆盖；按钮存在不等于动作均已验收 |
| 评审 | 原业务 SFC 应采用统一局部样式 | 列表/详情已留真实截图；宽窄屏操作区可见 | Demo 没有独立评审页面，按统一风格做补充检查，不伪造一一像素基线 |

**代码可定位原因：** management-review.less 写的是 `.ms-table-footer-wrapper` 类选择器，但 base-table.vue 是 `id="ms-table-footer-wrapper"`，不存在这个类，批量区样式不命中。方案里的目录宽度、概览卡片、详情优化也没有被该文件完整覆盖。修复应核对 SFC 实际 DOM、scoped/deep 的作用范围及主题覆盖顺序，保留事件、权限、按钮和所有原业务入口。

**Demo 自身也有问题：** `#app` 的 `:class="{optimized}"` 写在 Vue 挂载容器上；实际 DOM 保留字面指令但没有 `.optimized` 类。开关显示 true，连续切换后仍没有优化类，目录一直 300px。因此必须区分“当前文件实际渲染”与“源码声明的优化目标”；本轮没有虚构优化效果截图。这个问题不影响认定批量条位置等已在实际 Demo 出现的明确差异。证据：[开关诊断](demo-toggle-check.json)。

原图及标注：

- [用例现网](raw/cases-live.png)／[Demo](raw/cases-demo.png)／[差异指认](case-style-annotated.svg)。
- [计划列表现网](raw/plan-list-live.png)／[Demo](raw/plan-list-demo.png)。
- [用例详情现网](raw/case-detail-live.png)／[Demo](raw/case-detail-demo.png)。
- [计划详情现网](raw/plan-detail-live.png)／[Demo](raw/plan-detail-demo.png)。
- [评审列表](raw/review-list-live.png)／[评审详情](raw/review-detail-live.png)。
- 窄屏：[用例列表](raw/cases-1280.png)、[用例详情](raw/case-detail-1280.png)、[计划列表](raw/plan-list-1280.png)、[评审详情](raw/review-detail-1280.png)、[资产列表](raw/asset-1280.png)。

窄屏抽样未发现整页水平溢出。用例/计划表格的固定操作列和独立横向滚动条可见；资产存在外层与表格内层滚动并存、顶部动作贴右边缘的布局问题，建议收敛滚动容器。未对所有列逐一滚动点击，因此不外推为全平台所有操作可达。

## 3. 补测结果与证据

各子检查的步骤、预期、状态、来源和证据见 checkpoints.json。其计数是本轮子检查口径，不能与原 36 条需求或 75 条复合用例混用覆盖率。

本轮 27 个子检查：通过 17、失败 7、阻塞 2、待确认 1。失败含同一 UI 需求的多个差异和 Demo 自身问题，**不是新增 7 个独立生产缺陷**。累积原 36 条需求：通过 17、失败 9、阻塞 1、未完整执行 9；映射 36/36，全部必要项通过的需求占 17/36（47.2%）。本轮只把补齐旧链接的 AC-006 转为通过、把已有明确差异的 UI-BASE 转为失败，其他复合矩阵没有凭子项通过而升级。

| 项目 | 实际结果 | 证据 |
|---|---|---|
| 可见计划自动刷新 | 计划用例页保持打开，原生 MCP 修改隔离源用例的名称、P2 等级和标签；约 34 秒后的观察已自动显示新值，无手动刷新。结束恢复源字段 | refresh-source-update.json、visible-refresh-observation.json、raw/visible-plan-refresh.png、source-restored.json |
| 文档切页内容保留 | 输入测试文本→切测试用例页签→返回文档，内容保留；随后服务端查询发现已自动保存。**不计未保存草稿保护通过**。仅清除了本次追加标记，复查不存在 | draft-tab-check.json、plan-document-cleanup.json |
| 旧入口兼容 | case-management/caseGenerate 自动转到 featureCase，正常显示业务入口 | round-12.mjs；legacy-redirect.json |
| 跨项目详情 | 从当前用例详情经顶部项目选择切到“测试1”，再进入用例页，旧用例不显示；已恢复原项目。没有注入慢响应，因此迟到请求矩阵仍未验证 | cross-project-settled.json、raw/cross-project-settled.png |
| 批量分类弹框及移出分类 | 仅使用本轮人工资产，弹框提交空分类→任务 SUCCEEDED、逐项“处理成功”；不等于非空分类批改通过 | batch-category-result.json、raw/batch-category-result.png |
| 非空分类批改 | 新建分类失败，缺少有效目标分类，正向关联阻塞 | category-create-check.json |
| MCP 101 个原始步骤 | 创建后完整返回 101 步；contract supported=false 并说明只支持完整 1～100 步，没有截断接受；上传也拒绝 | mcp-101-canonical.json、mcp-update-unsupported.json |
| MCP 零步骤/空预期 | 可以作为源用例保存，但执行 contract 明确 supported=false 和原因 | mcp-boundaries.json |
| 幂等规范化 | 相同请求、嵌套对象字段换序仍返回同一 caseId；有序步骤倒序返回参数冲突 | mcp-101-canonical.json |
| 键大小写/空白 | 大小写不同的两个 key + 不同名称可独立成功；含空白的 key 安全拒绝。另见下文同名目录错误，不能将其归因键冲突 | mcp-key-case-diagnostic.json、mcp-boundaries.json |
| 用例更新重放/版本 | 同一更新请求返回原 updateTime；新 key 使用旧版本返回 VERSION_CONFLICT，未覆盖新字段 | mcp-update-unsupported.json |
| 连续三次普通事实发布 | 三次成功发布使用不同 submissionId；定义 Hash 一直相同，无历史执行嵌入 definition；事实约 1.6KB，仅随当前文字略变。此项未改显示字段，不覆盖 T21 全部场景 | mcp-consecutive-sanitize.json、mcp-consecutive-final.json |
| 引号/换行 | 非敏感实际结果含真实换行和引号，提交后查询 JSON 可解析，原文字保留 | mcp-consecutive-final.json |
| 敏感事实 | 虚构 password/token 标记触发 QUALITY_FACTS_KEY_NOT_CONFIGURED，安全拒绝；环境未配置密钥，不能验收加密后读取及完整 T42 | mcp-consecutive-sanitize.json |
| 未知操作状态 | 不存在的操作返回 NOT_FOUND + UNKNOWN + retryWithOriginalRequestId=true，不把未查到当未执行 | mcp-boundaries.json |
| 评论并发 | 创建隔离评论、编辑、原 key 重放成功；旧版本新 key 返回 VERSION_CONFLICT。未验证浏览器评论编辑冲突 UI | comment-create.json、comment-conflict.json |
| 删除确认链接 | 原生预检生成 confirmationPath，真实登录页面显示正确名称、待确认状态和确认/取消按钮。未代替真人确认，也未执行删除 | delete-preview.json、raw/delete-confirmation.png |

## 4. 新增问题及既有问题的补充证据

### D11 / P1：正式样式未完整采用 Demo

复现：进入测试用例列表，对照最终 Demo 的批量条；再查看目录宽度、用例详情和计划页。预期为采用确认布局并保留原功能；实际见第 2 节。与前次 UI-BASE 待确认不同，本轮已有明确差异证据，转为失败。建议集中在原 SFC 和公共样式修正，禁止用简化 Demo 替换原业务组件。

### D12 / P1：资产新建分类返回内部错误

复现：测试资产→用例资产→分类管理，输入 `REMAINING-20261027-批改分类`→新建分类。首次 UI traceId `a3c72bac-0a3f-4e62-965d-a015bdef2a0d`；一次诊断复测 traceId `e7dcc41c-c3e5-4042-ad67-9fb566c92656`。

实际请求 POST `/api/test-assets/categories`，body 为 name，HTTP 500，业务码 AI_EXECUTION_INTERNAL_ERROR；读取分类树为成功空数组。没有泄露数据库栈，但功能不可用，非空批量分类无法验收。服务日志及数据库状态未取得，**不能直接断言缺迁移**。应按 trace 检查 TestAssetGovernanceService、分类写入表/约束、组织上下文与部署迁移。留证：[原图](raw/category-create-failure.png)、[标注](category-failure-annotated.svg)、[HTTP](category-create-check.json)。

### D13 / P2：新建同名目录被映射成内部错误

两个不同大小写 key、相同目录名称：第二次内部错误 trace `3fc3d60d-9853-4d0c-a074-168ada4d1dcd`。诊断控制：不同大小写 key、不同名称均成功；再用独立 key 重复第二个名称，仍内部错误 trace `0041f53e-7041-45f5-b3a8-7aa0cd2faaae`。因此不能判为数据库幂等键大小写冲突；问题是同名目录输入的业务拒绝没有转为友好错误。应先核对目录唯一规则，再补显式重复名称校验及统一错误映射。

### D09 的新增实际影响：旧连接器不能创建用例

通过当前已连接的 metersphere_test 创建工具发起请求，服务端拒绝 IDEMPOTENCY_KEY_REQUIRED，trace `296e6b6f-a52f-40a6-8458-9d5e17e1918e`。连接器参数没有 requestId，无法满足新服务端必填契约；临时项目限定 Token 直接调用原生端点后创建成功。应更新 Node 工具 schema/连接器缓存，并验证同一客户端重新发现工具后写入成功；不能把原生端点通过等同现有连接器通过。见 connector-create-blocked.json。

### E01：敏感执行事实的环境配置阻塞

trace `2dece99d-0ea8-4233-a2af-7c39a9c4a42f`；错误码 QUALITY_FACTS_KEY_NOT_CONFIGURED。安全拒绝符合保护原则，但加密事实路径尚不可用。由部署侧按既有配置管理设置事实加密密钥后，在隔离数据上补验脱敏、加密持久化和历史读取；不要把密钥写入文档或 Git，不降低保护门槛。

### P01：原型开关未生效

属于仓库 Demo 的缺陷，和正式环境问题分开登记。修正挂载容器内的根包装元素绑定，并补切换时 computedStyle 确实变化的断言。应在保留已确认视觉目标的前提下修复，不能自动接受另一种样式基线。

前次 D01～D10 仍保持原状态。已知导出、连续资产同步、目录移动 revision 等没有部署新修复证据，本轮不反复触发，不视为已恢复。

## 5. 尚未完成的验收与边界

1. 样式仍失败；精确同视口字体与全部控件逐项对照、多浏览器、200% 缩放、权限角色矩阵尚未执行。Demo 优化开关须先修复，才能建立可信优化效果截图基线。
2. 未保存草稿被刷新覆盖、慢响应跨项目隔离、全部共享字段/附件解绑/版本矩阵仍未完整执行；自动保存后内容保留不能替代这些场景。
3. 非空资产分类被 D12 阻塞；来源回写/脱离缺少可靠的本轮来源镜像（前次 D05），未在现有其他人的资产上破坏性试验；跨成员组织置顶仍无第二账号。
4. MCP TEXT 用例、未知指纹/策略版本、旧 STEP 多图兼容、全部 REST 入口幂等、同毫秒身份、最小 scope 全矩阵、旧 URL 记录迁移、七天边界/清理/备份恢复尚未完整执行。
5. 未在共享测试环境注入存储宕机、杀进程或数据库故障；没有服务器/迁移日志。需要隔离环境及部署证据才能证明外部副作用和提交崩溃窗口。
6. 删除预检仅验证链接可达与显示，真人确认/过期/跨身份完整状态机未验收；本次预检不确认，等待到期。

这些限制均保留在原基准分母中。原需求映射仍为 36/36；不能用本轮子检查数量声称需求全部通过。新增累积状态见 cumulative-requirements.json，原报告及原始证据不改写。

## 6. 验证、修改文件与收尾

实际命令：`git status --short`、`git log --since=2026-10-26 ...`、`git show fd09cbc8e1 ...`、相关 rg/Get-Content；ego-browser skill；ego nodejs 执行 Space 9 的 round-01～48 中实际有效步骤；`node --max-old-space-size=128 .codex-tmp/msp-remaining-20261027/native-*.mjs` 的逐个指定脚本。并非所有 round 都成功：错误路径、过早观察、输入值不在 innerText、歧义定位及一次 PowerShell 引号错误均按脚本问题处理，修正后采证，没有当成产品缺陷。

本轮只新增 docs/testing/msp-remaining-20261027 下的测试范围、报告、JSON、截图和 SVG 标注，未改产品实现。页面与接口对应：分类管理→test-assets/categories；批量操作→case-asset-batches；计划文档→test-plan/{id}/document；MCP→/api/mcp；用例/评论写入包含明确 requestId、版本和项目范围。

未执行前后端全量构建、lint/类型检查、数据库迁移、单元/集成及容器启动验证：本轮没有产品代码改动，沿用线上部署做验收，且本机资源受限。风险是运行结果不能证明这些交付门禁。保留前次报告中的 CI/构建命令，不将上次静态测试发现当成本轮浏览器执行。回放本轮脚本会新建或修改隔离数据，TaskSpace 已结束且私钥文件已删除，不能批量盲跑。

收尾：源用例名称/等级/标签/描述恢复；自动保存的文档测试标记已清除并查询确认；原项目恢复；没有向其他用户发送评论通知。临时项目限定 Token 1710960094206763008 已删除，原生访问返回 401，私钥临时文件已移除；没有修改既有 Token、清理登录 Cookie 或启动其他环境。ego Space 9 已 finish，不留后台轮询。

保留本轮 REMAINING 前缀的人工资产目录/资产、4 条源用例及空测试目录供复核；三次成功执行属于协议隔离数据，不能当成真实业务通过。资产目录 1710955352562868225，资产 1710956400534888448；新源用例及执行 ID 见 JSON。零步骤用例的删除预检保持未确认。敏感事实未发布，其上传暂存状态见 mcp-consecutive-final.json；没有绕过保留/清理机制直接删存储。

**状态：测试交付部分完成；需求实现部分完成，UI 验收失败。**

证据交付检查：JSON 可解析、23 张原图的 SHA/尺寸一致、SVG 可解析、报告链接与子检查引用存在、Token/验证码扫描及临时密钥文件清理检查通过；`git diff --check` 通过。这些属于测试产物检查，不替代产品构建或运行验证。
