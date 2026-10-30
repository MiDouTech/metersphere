# 测试用例布局改造与验收记录（2026-10-30）

依据：已确认的 `docs/demo/msp-case-layout-review-20261030.html`；本次仅测试用例布局和操作入口整合，不改接口、数据库、路由和权限。

|需求|正式前端实现|后端/数据|验收尺度|状态|
|---|---|---|---|---|
|个人进度在计划前|caseTable.vue 概览顺序|原统计读取，无变更|个人在前；计划选择与进度对齐，名称自适应|实现，运行待验|
|新建导入在导出栏|ms-table 新增可选 batch-leading 插槽，仅此页面使用|新建/导入原接口及权限|未选用例仍可新建导入；导出等仍依选中状态禁用|实现，运行待验|
|导入双Tab|import/index + 原两个导入弹窗 modes 插槽|Excel /ai/case-imports/precheck，资产原 importCasesFromAssets|点击导入见两个Tab；可双向切换；校验/提交中禁切换；保留任务、跟进与错误流程|实现，运行待验|
|原编辑内加入执行结果/人|batchEditModal 选择属性新增两项，移除独立工具栏按钮|/functional/case/batch/edit、/functional/case/batch/update/executor|保留原属性；正确传选中/全页/排除/筛选/目录；失败不关闭；执行人真实成员加载|实现，运行待验|
|条数在页码左且完整显示|复用已有 ms-pagination 顺序，此页面限定112px宽及不收缩|原pageSizeChange，无变更|10/20/50/100条/页完整；变化后按原分页逻辑加载|实现，运行待验|
|列表详情Tab顶部|index.vue 右栏移除顶部16px padding|无变更|Tab位于右栏顶部，详情仍可切换|实现，运行待验|

## 修改范围

- frontend/src/views/case-management/caseManagementFeature/index.vue
- components/caseTable.vue、batchEditModal.vue、import/index.vue、import/exportCaseModal.vue、import/importFromDefaultModal.vue（均在上述目录下）
- frontend/src/components/pure/ms-table/{base-table,batchAction}.vue：仅增加可选插槽，其他页面默认内容不变。
- frontend/scripts/verify-weekly-remediation-sfc.mjs：增加本次组件编译检查。
- deploy/quality-verify/core-smoke.mjs：新增真实UI导入双Tab、分页宽度顺序、批量编辑结果提交检查。

## 边界

- 保留 FUNCTIONAL_CASE:READ+ADD / IMPORT / UPDATE / EXPORT 原权限。
- 更新执行结果沿用原排除 PENDING 的可选结果规则；不为布局变化增加业务状态。
- 执行人从原 GetTestPlanUsers 加载，不用示例成员；加载失败提示可理解信息，不能提交空执行人。
- 导入切换复用原两个弹窗及各自footer/关闭清理逻辑，不嵌套弹窗；切换Excel会按原关闭逻辑清理未提交文件。已经创建的导入任务保持服务端留存与最近任务入口。
- 无数据库/配置变化；不重跑未改后端全套单测。若后续有后端变更，此结论不再成立。

## 已执行验证

- `node --max-old-space-size=192 frontend/scripts/verify-weekly-remediation-sfc.mjs`：13组件SFC脚本/模板语法编译通过；不等同完整类型检查。
- `node --max-old-space-size=192 --test frontend/scripts/test-selection-toggle.mjs frontend/scripts/test-modal-contract.mjs`：11项通过。
- `node --max-old-space-size=192 --test frontend/scripts/test-case-layout-edit.mjs`：5项通过；执行真实组件confirmHandler的隔离单元检查，覆盖执行结果/人保持全页及排除/目录/筛选范围、无残留标签/自定义字段、失败不关闭、原标签路径。此单元使用接口替身，不当作真实后端证据。
- 目标8组件 ESLint：首次2条新错误已修正；保留既有console/未用变量警告，最终0错误、15条原有警告。
- `node --max-old-space-size=192 frontend/node_modules/stylelint/bin/stylelint.js .../caseTable.vue .../batchEditModal.vue --fix`：通过。
- `node --check deploy/quality-verify/core-smoke.mjs`、`git diff --check`：通过。

## 尚未验证

当前机器空闲物理内存约440MiB，禁止本地启动应用/Docker或高内存全量类型构建。完整 `cd frontend; pnpm run build` 及真实页面核心验收交由已有 GitHub `msp-runtime-verification.yml` 隔离环境；结果未返回前本次状态为部分完成。后端编译、镜像与173条迁移可由该流程严格按后端源身份复用包并新建临时库验收，不将旧版前端结果当新版本通过证据。尚未发布或复验用户测试环境。


### 远程验收阻塞

本次实现已提交工作分支 `codex/weekly-remediation-20261028`，未自动合并main/chenqifen。推送后尚未观察到新workflow运行；尝试 `gh workflow run msp-runtime-verification.yml --repo chenqifen-miduo/metersphere --ref codex/weekly-remediation-20261028 -f source_run=40876632979` 返回HTTP403（当前CLI账号没有触发权限）。没有更换身份或绕过权限。

具备权限的账号可执行上述准确命令，运行内含新的前端生产构建与扩充后的真实UI烟测；不需要在本机启动全套服务。本次13组件编译、16项相关单元、目标lint/stylelint和diff通过；完整vue-tsc/生产构建、此次前端搭配后端的容器健康及真实页面/UI取证尚未运行。因此不能标记全量验收完成，也未将上一版2123项或13条核心检查冒用作本次UI通过证据。
