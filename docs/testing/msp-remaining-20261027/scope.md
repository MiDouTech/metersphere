# 本周代码与剩余验收范围

基准：2026-10-26 起提交；HEAD 580b7aed02，主功能提交 fd09cbc8e1（146 文件）。前次证据见 ../msp-acceptance-20261027/report.md。本轮只测试及留证，不修改产品代码，不启动本地环境、不重建。

| 编号 | 本周改动与入口 | 剩余检查 | 依据及状态 |
|---|---|---|---|
| REM-UI01 | management-review.less、caseManagementFeature、testPlan、caseReview | 最终 Demo 目录宽度、主内容留白、表头、行高、批量条位置与底色、详情页 | UI-BASE；待执行 |
| REM-UI02 | MsTable、MsSplitBox、Agent配置 | 宽/窄视口遮挡、固定列、滚动条、常驻按钮 | 既有 Token 缺陷不重复认定；其他页待执行 |
| REM-SYNC | plan detail、有效视图与同步服务 | 可见页刷新、返回激活、草稿、共享字段/解绑 | AC001–003 尚未完整覆盖 |
| REM-ASSET | cases.vue、asset-case-detail、batch service | 分类批改、来源回写/脱离、逐项失败、筛选范围 | AC021–023；导出/持续同步已失败，无改动不重复压测 |
| REM-COMPAT | caseManagement router | 旧生成入口重定向、跨项目详情缓存 | AC006/017 |
| REM-MCP | DirectExecution/Operation/Import/Export/Attachment | 文本、空步骤、101定义、幂等规范化、删除确认、评论冲突、剩余写入口 | AC013/016/026/029/031及T未执行矩阵；已通过一图100步不重复 |
| REM-ENV | 迁移123–133、CI、长期清理 | 崩溃恢复、跨账号、到期及备份 | 当前浏览器无法直接证明；保留未执行，不虚报 |

代码预检：management-review.less 的 footer 选择器是 `.ms-table-footer-wrapper`，真实模板是同名 id，疑似规则未命中；Demo 上方批量条与正式表格下方批量条不一致；Demo 优化目录252px/窄屏220px，正式计划目录仍min-width300px。以上先列待实测差异，不能代替实际页面证据。
