# Excel导入与单计划概览修复记录（2026-10-28）

状态：**部分完成：修复代码、定向测试和原问题取证已完成，完整构建及部署后E2E待执行。** 本轮未提交Git、未发布应用；先前未提交的构建复盘文档保留。

## 需求追踪与修改范围

| ID/需求 | 用户入口与前端 | 后端及权限 | 数据变更 | 验证 |
|---|---|---|---|---|
| EXCEL-01：Excel预检报错 | 测试用例→从Excel导入→校验文件；import/index.vue、exportCaseModal.vue | POST /ai/case-imports/precheck；AgentCaseImportService、FunctionalCaseWorkbookSheets；保留FUNCTIONAL_CASE:READ+IMPORT与原项目/Token校验 | 无迁移；仍使用原导入任务/条目表 | 原文件线上复现；原报告表本地反验；真实XLSX/XLS筛选测试 |
| OVERVIEW-01：最多展示一个计划并下拉切换 | caseTable.vue、useSinglePlanOverview.ts | 复用原列表返回的testPlans及权限，不新增请求 | 无 | 5项选择状态/严格语义测试；真实模板/组件库浏览器隔离交互 |
| IMPORT-STOP-01：同链路发现停止/重试误校验 | 导入预检任务→停止/重试；前端原事件不变 | AgentCaseImportController拆开commit与stop/retry路由；仍继承原导入权限 | 无 | MockMvc验证stop/retry空JSON可用，commit空策略仍被拒绝 |

## EXCEL-01根因及证据

用户提供的V4工作簿包含“测试用例”和“质量报告”两张表。该文件没有被修改，也没有将其内容复制进Git测试资料。

原AgentCaseImportService枚举所有工作表，再逐张调用FunctionalCaseImportEventListener；后者要求用例名称列。质量报告只有项目/覆盖项等报告表头，会触发缺少用例表头异常，使整个预检失败。异常处理把非稳定错误码的业务异常变为BUSINESS_ERROR，因此用户看不到具体内容原因。全局错误提示与页面直接Message.error又同时展示，出现同一traceId两条提示。

实际取证：

- ego在同一“MSP需求验收-20261023”项目先用旧单条测试文件预检成功，没有提交导入；原V4文件预检复现HTTP400和BUSINESS_ERROR，同一traceId两条消息。见 [接口失败证据](../testing/msp-import-plan-20261028/original-file-precheck-failure.json)。
- 只读复制原文件字节作本机诊断，SHA256一致；真实旧解析器读取其质量报告时复现缺少名称表头；新筛选器只选择索引0的测试用例表。见 [原文件反验](../testing/msp-import-plan-20261028/original-workbook-repro.txt)。没有据此声称已验证原文件所有行均能写入目标项目。

修复内容：

1. 新增FunctionalCaseWorkbookSheets：先读取每张表第一行，使用现有多语言NAME字段字典识别用例表；只将用例表交给原预处理/校验/导入解析器。报告在第一张也不影响后续用例表，多个用例表仍全部处理，XLS和XLSX均支持。
2. 表头探测后立即停止读取该表，不为分类扫描整张报告；保留最多100张表、解析期限、文件/解压大小、用例数量等原限制。
3. 全部不是用例表时明确返回IMPORT_CASE_SHEET_NOT_FOUND；表数、时限、读取/解析错误使用稳定码和受控可理解消息，保留traceId，不返回堆栈或数据库信息。
4. 导入弹窗明确说明只处理标准用例名称列所在表；没有改变用例行本身的字段校验、覆盖规则、关联策略或权限。行级校验失败继续进入原预检明细，不强行导入。
5. 页面错误提示复用showHttpErrorMessage统一去重，避免全局和页面同文案重复弹出，不吞异常或清空所选文件。

## OVERVIEW-01展示规则

- 继续使用原来当前用例列表返回的关联计划集合，不增加越权项目查询。
- 无计划显示“-”；有计划默认选择第一项，只渲染一个选择框、一条计划进度及一个百分比。
- 下拉包含当前关联计划，显示名称和编号，允许搜索；选择后进度/百分比/完整提示同步更新。长名称限制宽度并保留完整title，窄宽度允许换行。
- 刷新指标时保留仍存在的选择；筛选/翻页使当前计划不再存在时回退第一项；空列表清空；项目切换重置。
- 切换只改变概览展示，不隐式筛选用例、不写入计划、不改变个人执行进度，也不影响现有批量按钮、权限及跨页选择。

## 同链路发现的停止校验问题

复验后尝试停止本轮单条测试预检任务，原请求会被ImportFollowupRequest.policy的@NotBlank拦截：前端请求包装器将无data的POST转换为{}，而原通用action路由对stop/retry也执行提交关联策略的@Valid。

修复将 `/{id}/commit` 独立保留@Valid；`/{id}/{action:stop|retry}` 不绑定关联策略DTO，仍由原业务服务校验任务归属、权限、状态与有效期。未放宽commit的策略校验。

测试环境仍是旧代码，清理时仅对本轮自建预检任务使用兼容的IMPORT_ONLY策略体执行stop，返回STOPPED；没有提交导入或创建计划/评审。该兼容调用是测试数据清理，不作为新路由已在线生效的证据。

## 实际验证与边界

| 实际命令/操作 | 结果 |
|---|---|
| `javac -parameters -J-Xmx160m @.codex-tmp/import-plan-20261028/compile.args` | 修改的生产文件及新增测试编译通过；本机已有依赖增量检查，非Maven全量构建 |
| `java -Xmx160m @.codex-tmp/import-plan-20261028/tests.args` | 10/10通过：7项工作簿/旧解析器反验、2项MockMvc操作校验、1项安全错误码映射 |
| `java -Xmx160m @.codex-tmp/import-plan-20261028/probe.args` | 原文件索引0被选择，旧解析器对其真实报告表复现表头失败，原文件字节未改变 |
| frontend执行 `node --max-old-space-size=160 --test scripts/test-single-plan-overview.mjs scripts/test-import-job-requests.mjs` | 9/9通过，含整个新composable的严格语义检查及原导入请求/上下文回归 |
| 对本轮5个前端文件运行 `node --max-old-space-size=192 node_modules/eslint/bin/eslint.js <文件列表>` | 0错误，12条既有console/未使用变量警告 |
| frontend执行 `node --max-old-space-size=160 node_modules/stylelint/bin/stylelint.js src/views/case-management/caseManagementFeature/components/caseTable.vue src/views/case-management/caseManagementFeature/components/import/index.vue src/views/case-management/caseManagementFeature/components/import/exportCaseModal.vue` | 通过 |
| `node --max-old-space-size=192 .codex-tmp/integrated-implementation/verify-sfc.mjs` | 3个修改SFC的script/template编译通过，不替代完整vue-tsc |
| ego组件隔离交互 | 提取真实生产概览模板、格式化函数、composable及本地Vue/Arco组件库，使用明确标注的测试数据；在983×836视口默认仅一条25%进度，下拉切到第二项仍仅一条进度，百分比和填充宽度均为75%。[DOM证据](../testing/msp-import-plan-20261028/overview-isolation-dom.json) |
| `git diff --check` | 通过 |

本机复用的旧产物classpath混入过不同代POI依赖，首次生成Excel测试夹具失败；本轮定向Excel测试显式采用与EasyExcel4.1.2读取路径匹配的POI4.1.2/XMLBeans3.1.0组合，未修改产品pom。此举仅隔离本机旧缓存问题，不证明新构建的产品依赖正确；正式Maven依赖解析、Excel读写及打包仍需在完整构建中验证。

## 未完成检查与风险

本轮仅约0.6–1GiB空闲内存，没有启动第二套服务或Docker，没有将低内存条件下的局部编译当成完整构建通过。

- 完整Vue模板类型检查、Vite生产构建未执行。在有足够资源的隔离CI/主机进入frontend执行 `pnpm type:check`、`pnpm build`。局部语义测试和SFC转译不能覆盖整个caseTable的组件契约。
- Maven完整编译/测试/打包未执行。根目录执行 `.\mvnw.cmd -T1 -pl backend/services/agent-integration -am test -Dtest=FunctionalCaseWorkbookSheetsTests,AgentCaseImportActionValidationTests -Dsurefire.failIfNoSpecifiedTests=false`，再执行 `.\mvnw.cmd -T1 -pl backend/app -am package -DskipTests`。需核对正式依赖树和实际读写Excel能力。
- 本次没有数据库结构变更；仍未进行真实数据库导入事务集成、镜像构建/启动/健康检查。沿用上一轮隔离验证环境，资源足够时串行补验，不连接共享库进行破坏性测试。
- 测试环境只用于复现旧错误及清理自建预检任务；新代码没有部署。原文件全部用例的预检/确认导入/关联计划评审，以及实际平台计划下拉的端到端验收仍待部署后执行，不能宣称已在线解决。
- 用户告知窗口恢复后，重新接管同一ego任务10；截图接口仍报告窗口最小化并拒绝截图。补测983×836视口下三个计划的下拉切换，百分比分别为25%、100%、75%，始终只有一个计划进度条；DOM边界检查显示下拉框、进度条和百分比无重叠。证据：`../testing/msp-import-plan-20261028/overview-switch-geometry.json`。当前未完成真实截图和视觉标注，不判定视觉验收通过。隔离组件使用测试数据，不替代真实平台页面/API验收；已交还同一任务窗口，保留登录态。

## 本轮文件

- backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentCaseImportController.java
- backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentCaseImportService.java
- backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentSafeErrorMapper.java
- backend/services/case-management/src/main/java/io/metersphere/functional/excel/util/FunctionalCaseWorkbookSheets.java
- backend/services/case-management/src/test/java/io/metersphere/functional/service/FunctionalCaseWorkbookSheetsTests.java
- backend/services/agent-integration/src/test/java/io/metersphere/agent/controller/AgentCaseImportActionValidationTests.java
- frontend/src/hooks/useSinglePlanOverview.ts
- frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue
- frontend/src/views/case-management/caseManagementFeature/components/import/index.vue
- frontend/src/views/case-management/caseManagementFeature/components/import/exportCaseModal.vue
- frontend/scripts/test-single-plan-overview.mjs
- 本文及docs/testing/msp-import-plan-20261028中的非敏感取证资料。

窗口恢复后补验：同一ego任务10截图成功，视口1908×895。已逐张查看 `raw/overview-plan-two.png`、`raw/overview-dropdown.png`、`raw/overview-plan-one.png`（均位于 `../testing/msp-import-plan-20261028/`）。下拉展开显示三个计划，收起时仅显示一个；从计划二75%切至计划一25%，进度与百分比对应更新，未观察到控件重叠。长名称在选择框和下拉选项中均存在截断；本轮未验证悬停提示是否展示全称，保留为待验项。此次为真实模板和组件库的隔离测试，不是已部署平台的完整页面截图；未制作标注副本或进行像素基准比较。此前最小化截图阻塞已解除，同一浏览器任务已结束，无新增业务写入。

功能状态：部分完成，等待完整构建及部署后E2E。2026-10-28用户明确要求提交代码，本次按授权提交源代码及验证记录，不代表完整验收通过；未执行分支合并或环境部署。提交前系统报告空闲物理内存约12MiB，未重复启动测试、完整构建或容器，以免本机资源耗尽。既有定向验证结果见上文。
