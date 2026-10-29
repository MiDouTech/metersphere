# MSP今日提交部署回归结果（2026-10-28）

测试交付状态：**部分完成**，上游失败使3个检查点阻塞。被测功能状态：**部分完成/未通过本轮验收**。不能认定今日修复已经在线生效。

## 基准、范围和环境

- 按当日Git日志，当前仓库当日唯一提交为 `808be981dd`，修复Excel辅助工作表预检、重复错误提示、导入停止/重试校验，以及单计划下拉概览。范围按该提交与用户原需求固定，未扩展至历史全平台回归。
- 环境：`https://msp.ebcone.net`；实际路由 `/#/case-management/featureCase?orgId=100001&pId=1704667279923322880`；项目“MSP需求验收-20261023”；页面角色为现有登录用户陈启芬。复用ego登录态，没有重复发送验证码，也未保存凭据或Token。
- 执行工具：用户指定ego浏览器，同一任务11串行操作；未另起Playwright浏览器。与技能建议的Playwright执行器存在工具差异；没有生成或运行Playwright spec。实际ego执行脚本保存于本机忽略目录 `.codex-tmp/live-regression-20261028/round01.mjs` 至 `round15.mjs`，失败的06、08轮属于脚本定位/文件生成错误，不计产品缺陷。
- 实际视口971×852，最终取证时间2026-10-28 13:39:30北京时间。无已确认线上像素基准，本轮做需求、交互、截图与DOM核对，未进行像素差异评分。未清除登录态；临时禁用页面网络缓存一次并已恢复。
- 用户原始Excel未改动，使用此前校验字节一致的本机只读副本 `source-original.xlsx` 上传；原工作簿为智能营销T3.1.21测试用例V4，含测试用例和质量报告。敏感工作簿未放入证据目录或Git。

## 追踪与统计

[检查点追踪表](../testing/msp-live-regression-20261028/checkpoints.md)为本轮验收明细。无独立输入用例，全部由需求派生，没有需求/用例冲突。主基准固定3项（EXCEL-01、IMPORT-STOP-01、OVERVIEW-01），共8个检查点：**通过0、失败5、阻塞3、待确认0、未执行0、范围外0**。映射覆盖率3/3=100%；已验证需求覆盖率0/3=0%。实际执行检查点通过率0/5=0%；阻塞3项保留在总数8的分母中，不能算通过。

补充控制检查单独统计：单条Excel预检1项通过，不混入主基准覆盖率。失败均为一次业务执行；计划展示另做一次禁缓存诊断刷新，仍失败，无“不稳定后通过”项。

## 失败与归因

|ID|预期与实际|复现步骤/证据|归因和影响|
|---|---|---|---|
|F01 / EXCEL-01a|原V4文件跳过报告表并进入逐行预检；实际HTTP400 BUSINESS_ERROR，两个相同trace提示|测试用例→导入→选择原V4副本→校验文件；[响应](../testing/msp-live-regression-20261028/original-precheck.json)、[原图](../testing/msp-live-regression-20261028/raw/original-precheck.png)、[标注①②](../testing/msp-live-regression-20261028/annotated/01-import.svg)|原问题在线仍存在；traceId `6d937685-1d45-48cc-b8f7-dc10de34289b`。未取服务日志，不能断言仍为同一后端根因；阻塞原文件完整导入、持久化验收|
|F02 / EXCEL-01b|仅报告表应返回IMPORT_CASE_SHEET_NOT_FOUND及可理解说明；实际仍为BUSINESS_ERROR、重复提示|上传本轮合成仅报告表xlsx→校验；[响应](../testing/msp-live-regression-20261028/report-only-precheck.json)|错误映射/去重验收失败；traceId `3537b47b-dadb-4b3c-9875-9978bd5143d4`|
|F03 / IMPORT-STOP-01a|停止不要求关联策略；实际HTTP400 VALIDATION_ERROR|有效单条预检→停止导入；[响应](../testing/msp-live-regression-20261028/stop-ui.json)、[截图](../testing/msp-live-regression-20261028/raw/stop-ui.png)|停止链路仍失败；traceId `b521346f-a6c7-4026-89a5-6b01dfc2302f`。加IMPORT_ONLY策略后可停止，支持旧DTO误校验疑点，仍需日志定位|
|F04 / IMPORT-STOP-01b|重试不要求无关关联策略；实际HTTP400 VALIDATION_ERROR|在本轮已停止任务点击重试未成功条目；[响应](../testing/msp-live-regression-20261028/retry-ui.json)|重试链路仍失败；traceId `bcfff809-5291-41a5-9cf3-85ddee80aa5e`|
|F05 / OVERVIEW-01a|仅一个计划、下拉可切换；实际2条计划进度、0个下拉|进入测试用例→等待用例加载→观察→禁缓存刷新后复验；[DOM](../testing/msp-live-regression-20261028/overview-live.json)、[刷新证据](../testing/msp-live-regression-20261028/cache-disabled-reload.json)、[原图](../testing/msp-live-regression-20261028/raw/overview-after-reload.png)、[标注④](../testing/msp-live-regression-20261028/annotated/04-overview.svg)|实际前端与808be981dd实现不同；单计划选择、切换及生命周期均无法验收|

截图已逐张查看：在971px视口，旧工具栏多行换行，计划展示两行；未发现该区域文字相互重叠，但名称截断且没有新下拉。标注基于实际截图/DOM位置，保留原图不修改。仅制作F01/F05标注副本；F03没有标注副本，F02/F04以接口及DOM日志取证，不宣称所有截图均已标注。

## 发布差异证据与下一步

前端入口资源为 `/assets/index-CZ8F7k2B.js`。页面普通刷新和禁网络缓存刷新后，仍缺少 `.case-overview-plan-select`，存在两个 `.case-plan-progress`；导入弹窗也缺少今日新增的“仅导入首行包含标准用例名称列的工作表”说明。三处行为共同表明**当前访问的前端产物没有表现出今日改动**。这不能单独证明是哪次构建、CDN回源、发布目录或反向代理配置导致，不能仅据资源文件名认定部署SHA。

建议先核对发布流水线实际checkout SHA是否包含808be981dd、前端ZIP构建SHA及目标静态目录、HTML所引用资源是否来自该ZIP、CDN/代理响应；不要先继续修改已存在的前端实现。后端需核对运行镜像digest/构建SHA，并按以上traceId查服务日志，确认请求究竟命中哪一实例、控制器及异常位置；前后端需要一并确认，不能假定前端发布正确即后端也正确。发布链路本轮未获日志或管理接口，归因为“疑似发布版本不一致”，不是已证实的根因。

确认正确产物上线后，先重跑F01/F02/F03/F04/F05，再补EXCEL-01c的隔离导入持久化及OVERVIEW-01b/c的选择刷新、筛选、空数据、切项目、长名称、完整页面布局。单条预检通过不代表原V4所有行均能导入。

## 数据、修改和验证边界

- 补充控制：同项目上传旧单条文件返回200 PRECHECKED、1条PENDING，证明非所有Excel均不可预检；[响应](../testing/msp-live-regression-20261028/single-case-precheck.json)。
- 本轮仅新建预检任务 `d92aaf90-ffc4-4b20-a1c6-f239673fdc5c`，未点击提交有效用例、未创建计划/评审、未删除任何既有数据。UI停止失败后，仅对该任务用兼容IMPORT_ONLY请求体清理，返回STOPPED；随后UI重试失败，未启动导入。[清理证据](../testing/msp-live-regression-20261028/cleanup.json)。预检审计记录仍保留，非物理删除。
- 修改文件：本文、检查点表及 `docs/testing/msp-live-regression-20261028/` 证据；无产品代码修改、无数据库迁移。前端Excel校验→POST `/api/ai/case-imports/precheck`；任务按钮→POST `/{id}/stop` 和 `/{id}/retry`；概览来自原用例列表关联计划，无新请求。
- 实际命令：`git log --since=2026-10-28T00:00:00 --format="%h %s"`确定范围；`ego-browser skill`读取已安装指南；`ego-browser nodejs -e "await import('file:///C:/SoftWare/JetBrains/metersphere/.codex-tmp/live-regression-20261028/roundNN.mjs')"`分轮执行；`python .codex-tmp/live-regression-20261028/make-report-only.py`生成合成负向工作簿；`python .codex-tmp/live-regression-20261028/annotate.py`制作标注索引副本。接口/截图结果见上表，不能称测试全通过。
- 未执行：原文件确认导入与持久化、新下拉生命周期/完整名称悬停、其他浏览器/宽视口、权限矩阵、服务器日志核查、像素回归；前三类依赖本轮失败的前置条件，其余超出这次定向验证或缺少对应证据。没有重新构建产品、启动容器或运行Maven；本轮只修改测试文档和证据，不将其误列为产品构建已通过。
- ego任务11已finish关闭，未清理用户Cookie。工具提示ego有可用更新，未升级。
