# 证据目录整改追踪（开发草稿）
依据：批准基线v1.0-20261119章节1/2.3/6；ASSET-AUDIT-20261123-03平台真实回执。

|需求|入口|实现/API|存储|验证|
|现用证据可发现|历史兼容执行证据目录/资产详情|统一EVIDENCE投影，sourceKind稳定区分来源|只读现有表，无迁移/复制|真实提交/关联可见；暂存不可见；去重|
|预览下载|catalog预览/下载|统一受权content端点|读取原始字节/原加密通道|byte equal/跨项目/当前角色/Token会话|
|历史兼容|旧artifact|保留现有旧artifact读取，禁止要求新执行认领旧任务|ai_execution_artifact及原存储|legacy字段/权限回归|
|来源真实|详情sourceKind及relatedId|submission/bug/session精确关联|保留历史快照，不伪造来源|历史引用/不可变/不纳暂存|

根因：TestAssetMapper.CatalogUnion中EVIDENCE只查询ai_execution_artifact JOIN ai_execution_task；前端catalog preview/download也只调用旧任务artifact端点。现用上传已成功进入其他真实存储，但此查询不覆盖。

本轮实际落点：42张次直接提交证据在agent_case_evidence，经agent_case_submission_evidence关联17个ACCEPTED submission；18缺陷附件在bug_local_attachment/file_metadata等实际关联存储；executor.evidence在external_executor_evidence加密存储，由事件或business_asset_version.snapshot_json的branches.evidenceRefs引用。仅目录为空不能推导上传失败。原回执见本轮platform-published-receipts/platform-bug-attachments-published。

权限：个人证据FUNCTIONAL_CASE:READ且原用例当前可读；缺陷图片PROJECT_BUG:READ且真实未删除缺陷归属；executor BUSINESS_ASSET:READ并复用同Token/actor会话检查；legacy AI_EXECUTION:READ。Token项目白名单/有效性及当前角色不省略。暂存个人图片/未关联缺陷临时文件不纳入目录。

尚未部署。开发验证、运行验收分别记录；不以本地通过推导现网通过。

实际回执索引：个人17次提交/42张次证据，首evidenceId=83be584a-7351-4d21-aed9-f686f8e97212；缺陷9条/18附件，首associationId=1751330639305940994、fileId=1751330622126071808（不得混用两者）。BDE evidenceId=44c0c92c-3c10-4901-87e8-564a5fadc694，assetId=b6f21182-13d9-47ec-b8e6-2f36912ea5b1，sessionId=b98e9166-787a-4060-9ad9-b902f8fa7f93；sourceMatches=True，oldSnapshotPreserved=True。这些是本轮已回读结果，不是新代码部署验收。

### 2026-11-24 独立复审修订与验证边界

- EVIDENCE 列表、详情共享同一来源受权 projection 和治理元数据 joins；人工 creationSource 不改系统 sourceKind。
- 新来源用于任务时先重验当前来源权限，再由既有 getPublished 校验项目/类型/资源/固定版本；历史 snapshot 不重算。
- relations 两端若是 EVIDENCE，均在 SQL 中以相同来源 projection 过滤；count 和分页同谓词。SQL 片段仅由私有构造器创建，项目/用户/Token 均绑定参数。
- metadata/assign/governSource 经 catalog.detail；版本读取/废弃/发布均再验来源。组织分类树仍是既有组织级治理统计，不返回证据身份或内容。
- 实际失败证据：metadata detail 分类丢失；resolveContext 新 PERSONAL 来源 ASSET_NOT_FOUND；受限 case-only relations total 预期 1 实得 4。见 .tmp/asset-remediation-20261124/evidence-{metadata,context,relations}-red.log。
- 隔离 javac 编译当前 EvidenceCatalogService/TestAssetCatalogService/TestAssetMapper 和真实测试，执行真实 H2/MyBatis 服务回归 5 项通过（evidence-all-isolated-green.log）；未改 Maven target、未把 H2 JSON 兼容函数当 MySQL 证明。原 mapper XML 的真 MySQL 回归及全应用打包由主 Agent 排队执行。
- Spring 最小 context smoke 使用 ObjectProvider 且旧下载 bean 延迟构造，避免无关 Mockito 继承 @Resource 被测试容器注入；未启用全局循环依赖开关。
