# 测试资产缺陷修复与补充验证计划（2026-11-24）
依据：用户本轮“修复缺陷”、新增审计MCP、排查空EVIDENCE、退出/手机验证码登录；批准产品基线v1.0-20261119；部署验收ASSET-AUDIT-20261123-03。本文是实施记录草稿，不新增产品审批范围。

| 需求 | 前端入口/实现 | 后端/API | 数据或配置 | 验证 |
|---|---|---|---|---|
|100012 空校验不关弹窗|用例目录create、来源治理modal|原有保存接口|不生成空记录|真实组件校验、cancel、成功/失败保持|
|100013 覆盖不重复、100014等级保留、100016损坏Excel失败|CaseAssetFileImport|CaseAssetImportJobService/Worker、CaseAssetService、导入适配|原件/任务/资产映射保留|真实Excel解析→覆盖旧ID/P1回读、损坏失败、幂等|
|100015保存不遮挡、100018输入可访问名称、100019左操作右检索|case-detail/shared-configuration/case-library|保持已有真实API|无迁移|组件、几何命中、DOM标签、生产构建|
|100017版本停用400|版本列表|Asset版本deprecate controller|已有版本状态|请求参数契约及权限/持久化|
|100020历史同步失败|历史同步提示/重试|CaseAssetHistoryCaseSyncService/Worker|旧源映射唯一性和历史保护|重复映射重现、修复保留引用/历史、错误诊断|
|审计查询MCP|MCP非UI消费者|SharedConfigurationService、SharedConfigurationMcpTools、Node工具包|既有shared_configuration_audit；查询不新增审计|分页/事件版本过滤/项目归属/RBAC/无敏感值/当前选择不新增行|
|EVIDENCE空目录排查并修复链路|资产详情来源证据、历史兼容目录|TestAssetCatalogService、执行器/直接提交证据存储|查询现用证据而非已退出受控执行旧表；不伪造或重复写入|真实上传→提交/关联→列表→预览→下载字节一致；暂存未提交不冒充资产|
|退出→验证码登录|现有测试站登录流程|原有退出/验证码登录接口|只在操作内存使用用户给定凭据|退出后受保护入口、成功登录、身份/项目恢复|
|只读角色负例|用户明确本轮不考虑|不删除权限校验|不改Token/角色|仅移出新增回归范围，原报告历史不改|

- [ ] 各域先定位根因，先写能复现的失败测试，再最小修复。
- [ ] 相关单元/集成、Node测试、类型检查、定向lint、前端生产构建、后端打包。
- [ ] 独立审查绑定具体diff/hash，修复复审；按现有约定提交合并自有分支/上游PR不自行合并。
- [ ] 现有测试站执行新增退出/验证码登录；新代码正式验收等待部署，不能用旧站测试证明新代码。
- Docker镜像构建按用户要求不执行。保留原有未提交改动，起始差异快照.tmp/asset-remediation-20261124/preexisting.patch。凭据不进入任何交付文件。
