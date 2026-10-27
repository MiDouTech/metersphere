# 真实前端样式评审第二版

> 最新整合入口：[缺陷、需求与交互整合方案](msp-integrated-delivery-plan-20261023.md)。本轮新增规则优先，采用最终优化样式；按钮常驻，源绑定资产字段以持续同步规则为准。
本版按用户最新要求替代上一版Demo的UI方向：**只调整样式与布局，不改功能；按钮常驻，不因未选中而消失，不把原有按钮重新收进更多菜单。** 原有权限、归档状态、配置条件等业务显隐与禁用规则保留，不把“常驻”解释为越权显示所有功能。

[打开第二版](../demo/msp-real-frontend-style-review-20261023.html)。页面右上角开关仅切换基础/优化CSS，控件及数据保持一致。基础模式为相同组件的基础样式参考，不是现网逐像素截图。

## 读取的真实代码

- `frontend/src/views/case-management/caseManagementFeature/index.vue`：执行用例/XMind、模块树、回收站。
- 同目录`components/caseTable.vue`：列表/详情、MsAdvanceFilter、新建、导入、导出历史、同步状态、条件AI、进度概览、批量动作及字段列。
- `frontend/src/views/test-plan/testPlan/index.vue`与`components/planTable.vue`：计划树、计划/计划组筛选与列表。
- `frontend/src/views/test-plan/testPlan/detail/index.vue`：标题、编辑/复制/关注/更多、进度及页签。
- 同详情目录`featureCase/components/caseTable.vue`：关联用例、执行、执行人、取消关联、缺陷操作；移动按钮仍受集合树上下文约束，原型示例按集合上下文展示。
- `frontend/src/components/pure/ms-table/batchAction.vue`：常驻批量按钮、权限与禁用机制。
- `frontend/src/components/pure/ms-button/index.vue`、`frontend/src/theme/default.less`、`frontend/src/assets/style/global.less`及`arco-reset.less`：字体、紫色主题、按钮与表格规范。

## 实现边界和样式方案

新HTML直接加载仓库node_modules的Vue 3全局版本、Arco Vue JS和Arco CSS，真实渲染a-button/a-tabs/a-table/a-dropdown/a-pagination等组件。保留紫色#811fa3、14px字体和现有导航/分栏结构，优化只涉及间距、目录宽度、批量条底色、表头、边框圆角和对齐；宽表由Arco横向滚动与固定右列处理。

这是从真实源码结构制作的组件级样式原型，**没有直接挂载原业务SFC、Ms组件、权限Store或真实请求**，不能称为真实业务环境或逐像素还原。依赖当前仓库安装好的node_modules，单独把HTML移出仓库不能离线运行。操作按钮打开明确的原入口说明，不伪装提交成功；示例数据不持久化。计划列表数据和详情内容为示例，不作为真实统计。

正式落地时仅在原SFC附加局部样式/布局类，不改事件处理、接口、路由、权限、任务状态或数据模型。原报告按钮继续按之前需求隐藏；已经存在的“更多”和下拉结构保持，不新增按钮收纳。上一版方案中“选中后出现批量工具条”和“次要动作归更多”不再作为本轮UI改造要求，均以此修订为准。资产批量新需求独立评审，不混入纯样式变更。

## 实际验证

执行Ego脚本：`.codex-tmp/ui-real-style-20261023/round-01.mjs`、02、03、04、05、06。04因自动化选择器使用错误的thead类失败；05读取真实DOM后06改为`thead .arco-checkbox`，验收通过。不是业务故障。

关键复验命令（PowerShell，仓库根目录；任务已结束，未来重验需新建任务ID）：

```powershell
& C:/Users/midoo/.local/share/ego/active_version_dir/ego-browser.exe nodejs -e "await import('file:///C:/SoftWare/JetBrains/metersphere/.codex-tmp/ui-real-style-20261023/round-06.mjs')"
```

06退出码0，6项断言通过：1366无页面水平溢出、零选中批量按钮常驻且禁用、全选后启用、基础/优化切换不改按钮列表、计划批量按钮常驻、计划文档保存/预览/重置/导出存在。已查看截图，证据`outputs/msp-real-style-20261023/`，包括checks.json、cases-1366-final.png和plan-1366-final.png。浏览器保留结果页供评审，任务结束；无Docker或业务环境启动。

本轮新增第二版HTML和本文，未改生产前后端代码或API。未运行生产构建、后端测试/迁移/容器/真实E2E，因为本轮交付为样式原型而非功能实施；权限矩阵和全部业务动作仍需正式改造后验证。状态：**第二版原型可供评审，正式样式改造未实施**。
