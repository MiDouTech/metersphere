# MSP 本周提交复测结果（2026-10-28）

## 结论与范围

状态：**部分完成，存在失败项**。本轮在 `https://msp.ebcone.net` 使用同一个 ego 任务串行执行，没有启动本地服务、Docker 或第二套环境。

以本周提交 `fd09cbc8e1`、`580b7aed02`、`dcd9c9b018`、`7fa2c33034`、`808be981dd` 对应需求为基准：主需求 40 条，通过 15、失败 7、部分完成 16、阻塞 2。40 条均已建立追踪，但完整验收覆盖率仅 15/40（37.5%），不能据此认定平台整体通过。补充检查 6 项单列；历史 75 条复合用例本轮仅完整验证通过 2、失败 1，其余 72 条没有完整重跑，未沿用历史通过结论。

逐条范围、前后端入口及证据见 [主追踪表](../testing/msp-weekly-regression-20261028/scope.md)、[主需求数据](../testing/msp-weekly-regression-20261028/requirements.json)、[补充检查](../testing/msp-weekly-regression-20261028/supplemental-checks.json)、[复合用例](../testing/msp-weekly-regression-20261028/composite-cases-current.json)。失败需求可能对应同一个问题，不能将需求失败数直接当成独立缺陷数。

## 部署识别

运行中刷新后，页面入口从 `index-CZ8F7k2B.js` 变为 `index-ClLd1kJ2.js`；新版单计划下拉、原文件预检和停止/重试参数问题的修复随之可见。这只能证明浏览器实际加载的资源发生变化，不能确认服务器切版时刻或缓存原因。

`build-manifest.json` 标记 revision 为 `d6e3bd9c06ca4f61992d0f8fa32f409008741b2a`，该对象在当前本地仓库不存在，也不是本次查询到的远端 main、chenqifen、v3.2 分支头。尚不能据此认定发错包，需要流水线构建来源佐证。以下主要失败项已在新资源加载后复核。

## 主要问题

|优先级|问题、实际结果与影响|排查或修复方向|证据文件（位于本轮证据目录）|
|---|---|---|---|
|P1|MCP 执行事实中嵌套 JSON 字符串的 password 未被敏感检测识别。在 facts key 未配置时，合成测试内容仍发布成功，读取返回原字符串；普通 `password=...` 对照被正确阻止。影响敏感内容保护。未检查数据库，不能声称已经证实数据库明文存储。|统一字符串 JSON 解析、递归检测与脱敏，限制解析深度及大小；加密前与响应前使用一致规则，补嵌套、转义和数组回归。|sensitive-facts-readiness.json、sensitive-plain-control.json|
|P1|旧导入用例 100025～100021 逐条 MCP 带步骤查询失败；100025 的 MCP get 与网页详情也失败。新导入用例可打开。影响历史用例读取及批量 MCP 查询。|按 traceId 比较历史与新记录的模板、版本、步骤结构；修复兼容读取及坏记录隔离，评估数据修复。无服务日志，具体异常根因尚未证实。|mcp-search-single-row-diagnosis.json、mcp-search-single-row-diagnosis-2.json、legacy-source-ui-responses.json、annotated/W05-detail.svg|
|P1|网页导入在仅完成预检、没有点击最终提交时，停止后重试直接入队并完成 1 条导入，followup 为 LEGACY，未沿用用户选择的计划/评审关联决策。|持久化是否已提交及停止前状态；未提交任务重试回到预检/决策步骤，禁止直接执行；已提交重试保留原策略和关联组。|web-import-stable.json、web-import-stop.json、web-import-retry.json、web-retry-final-browser.json|
|P1|资产显示 UP_TO_DATE、missing=0，但取证时源用例及计划为 ERROR，镜像仍 PENDING，且缺少源新增的外部图片附件。后续合成协议测试又将源改为 SUCCESS；ERROR 是当时快照，非报告时最新状态。|按共享字段版本验证同步成功，补执行事实和附件变更事件及补偿；不能仅按缺失记录数判断一致。|asset-current-state.json、asset-readiness-api.json、source-attachments-current.json、asset-reconcile-later.txt、plan-after-sync.json|
|P2|个人中心 Token 列表在 971、1366、1908 三种宽度均裁切启用/操作列，右侧不可完整操作。1908 宽时操作列 x=1958～2098，超出窗口。|修复容器 min-width/overflow、表格横向滚动及固定操作列；覆盖抽屉和窄视口。|token-new-geometry.json、raw/token-new-1908.png、annotated/W01-token.svg|
|P2|资产真实导出任务成功、文件有效，但“用例等级”为空；同一记录在列表/API 中为 P3。ID、名称、标签与步骤存在。|统一资产字段到导出 DTO 的等级映射，并对导出单元格与源数据逐字段断言。|asset-export-content.json、asset-export.xlsx、asset-priority-wide.txt|
|P2|当前连接器暴露的执行提交仍要求 hash、逐步骤证据；原生 tools/list 支持可选 hash 和用例级 evidenceIds。连接器创建接口也缺少原生要求的 requestId。|核对连接器绑定端点及工具缓存，刷新工具契约并做客户端兼容回归。未核查 npm 发布状态，不能归因为具体包版本。|connector-schema-observed.json、native-tools-list.json、native-tools-expanded.json|
|P2|参照 Demo 的样式仍不完整：实页表头白色、批量按钮约 20px、搜索选择器未按样例限宽；971 宽计划名称被固定操作列遮挡。1366 宽名称导航成功。|按真实组件统一表头、按钮尺寸、间距和弹性布局；固定操作列不得覆盖名称可点击区域。仅做 DOM/视觉核对，未做像素差分。|current-layout-styles.json、plan-name-hit.json、annotated/AC18-layout.svg、raw/approved-demo-case-section-1908.png|

关键追踪：旧详情网页 traceId `b7b7eda5-bafa-4146-8b37-de08bbd8fbee`；旧用例 MCP get traceId `ad76145f-2023-4e82-9b64-e1585ac4bf94`；嵌套字符串提交 ID `3c1ba8d1-683e-45dc-bfa3-7e62e5baaa21`；停止重试任务 ID `4199cf0e-c5eb-4b2b-8cac-db9f870116f7`。

另有两个待确认点：计划列表首次进入曾为空，手动刷新恢复，后续重入正常，尚无稳定根因；原 Excel 自带模块路径优先于选中的专用目录，89 条记录没有落到专用目录内，应明确产品的目录优先级与提示规则。

## 本轮已验证通过的路径

- 原 V4 Excel：预检 89 条，明确选择 IMPORT_ONLY 后任务完成，89 条均 SUCCESS；首尾用例详情返回 200、步骤可解析。没有逐条核对 89 条的全部业务字段。
- 单计划下拉展示及切换；新建计划/评审名称进入详情；生成报告、生成用例及指定资产 AI 入口隐藏；旧生成路由兼容跳转。
- 导入新建关联计划和评审、再次导入追加到明确关联组；未提供 reviewers 的追加路径成功。
- 用例级图片证据、独立执行事实、零 hash 提交、重复提交回放；无图片、伪图片、超步骤上限等失败校验。原生创建幂等重复返回同一记录，并发相同目录请求没有产生重复目录，变更载荷被拒绝；撤销权限后旧请求回放被拒绝。
- 外部图片导入并关联、重复调用复用记录、私网 URL 拒绝；真实 MCP 用例导出文件下载并检查内容。
- 目录移入及移回根、跨页全选及排除、筛选后清空选择、分页保持；资产批量等级/标签回写源、分类修改、导出任务、个人新建资产批量删除；目录组织置顶刷新保持并取消置顶。
- 计划打开期间源名称变更自动反映、手动全量同步 6/6；缺陷多长标签在 971 宽不与预计解决时间重叠。

这些路径的通过不覆盖追踪表中明确标注未执行的权限、并发、恢复和全字段组合。

## 测试数据与清理

测试限定项目 `1704667279923322880`（MSP需求验收-20261023）。源用例由 25 增至 119：本轮协议/导入边界等新增 5 条、原文件新增 89 条，均保留以便复现。原文件没有覆盖已有用例，但其目录路径产生了专用目录以外的新目录。保留的新计划、评审、关联组、分类和合成缺陷均带 WEEKLY-20261028 标识。

协议用例 `1712306377475530754` 的执行结果包括合成验证事实，不代表真实业务成功；测试缺陷 `1712316255900311552` 用于多标签布局。新建手工资产 `1712352728762589184` 已通过批量操作软删除。目录移动和置顶已还原；源名称/等级/原标签已还原至本轮基线。

临时项目限定 MCP Token `1712305312323641344` 已删除，后续调用确认 HTTP 401，本地私密 Token 文件已移除。未动其他 Token 或登录状态。一次被拒绝提交留下的暂存证据 `3d098a77-caad-44a3-b772-0e9686431fd1` 由正常 24 小时过期机制处理，未强制清理存储。源用例删除仅验证确认预览，没有代替人工确认，删除/恢复联动未执行。

## 修改文件、链路与验证

本轮没有修改产品前后端、接口、数据库或部署配置。新增本报告、本轮证据目录，以及 `frontend/e2e/msp-weekly-regression-20261028.spec.ts`（5 项只读回放用例）。此前已有未提交文档保持原状，没有提交、推送或合并。

实际验证使用 git 提交/远端分支查询、ego skill 及同任务浏览器脚本、原生 MCP 调用、Python XLSX 内容检查及截图标注。具体请求响应、时间、截图和命令脚本映射见主追踪 JSON；执行脚本保存在本机 `.codex-tmp/weekly-regression-20261028/`，其中 native 脚本依赖的临时 Token 已撤销，不能直接重跑。

已执行检查：

```powershell
git log --all --since=2026-10-26 --oneline
git ls-remote metersphere main chenqifen v3.2
python .codex-tmp/weekly-regression-20261028/check-export.py
python .codex-tmp/weekly-regression-20261028/check-asset-export.py
python .codex-tmp/weekly-regression-20261028/annotate.py
python .codex-tmp/weekly-regression-20261028/finalize-trace.py
node --max-old-space-size=128 frontend/node_modules/eslint/bin/eslint.js frontend/e2e/msp-weekly-regression-20261028.spec.ts --no-fix
git diff --check
```

目标 ESLint 最终通过；MCP 导出内容检查通过，资产导出等级断言发现上述缺陷；截图为真实页面，SVG 仅增加框选标注。浏览器实际执行使用 ego，新增 Playwright spec **未运行**，不得将 lint 通过当成 UI 测试通过。

未执行：剩余完整 75 用例矩阵、人工删除及恢复、多角色全权限组合、长期留存/重启恢复、服务端日志与数据库核验、加密 key 正常配置下的端到端验证。原因分别为本轮覆盖有限、未人工确认破坏操作、缺少其他角色及服务端权限、测试环境 facts key 未就绪；对应风险仍保留。后续只读回放可使用私密登录状态运行：

```powershell
$env:PW_BASE_URL='https://msp.ebcone.net'
$env:PW_STORAGE_STATE='<本机私密登录状态文件绝对路径>'
pnpm --dir frontend exec playwright test e2e/msp-weekly-regression-20261028.spec.ts --workers=1
```

产品单测、后端集成、生产构建、迁移、镜像与容器健康检查本轮没有重跑：本轮只新增测试与文档，且使用已部署环境，不声称这些工程检查已通过。本轮功能验收及测试交付均为**部分完成**；应优先处理敏感内容、历史详情、未提交导入重试和资产一致性，再回归 UI 与导出字段。
