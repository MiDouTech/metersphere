# MSP38 部署回归缺陷修复 v1.0（开发文档-草稿）

2026-11-26。分支codex/msp38-regression-20261126。仅修复#100022、#100023；历史同步失败和低权限人工检查由用户明确排除，保留原验收记录不改成通过。

## 追踪与实现

1. #100022：用例资产项目目录→导出任务→FunctionalCaseFileService→Excel批注→FunctionalCaseImportEventListener→原持久化接口。新增内容快照AssetStepSnapshot，记录原步骤num和独立expectedResult，描述与步骤预期仍以可读文本展示；批注读取同时校验描述与步骤预期两列。用户改动任一列时不应用旧快照；旧步骤/TEXT编码兼容。覆盖0/1/3步骤、空步骤、HTML字面量、合并/非合并文件及原编号7起始。快照仅内容，不含用例身份、授权或版本号，后端原权限、目录归属、同名解析、并发版本校验不变。
2. #100023：asset-case-detail.vue将catalogId/caseId改为响应式参数；路由变化立即清空上一对象草稿并重新加载，失败显示安全提示并禁用保存。读取、保存回调、附件刷新/删除/下载均绑定发起时对象与revision，旧响应不能覆盖新页。写请求沿用PUT /case-asset/case及原来源/版本校验，不在切页时取消已经发出的后端写入，不将取消浏览器请求当作后端回滚。
3. 前后端接口没有新增字段或路由；Excel传输字段仅为内部内容快照。无新增SQL迁移、配置和依赖；不新增Token Scope。

## 已执行证据

|检查|实际命令/结果|日志|
|---|---|---|
|前端回归先红后绿|node --test tests/asset-attachment-sfc.test.mjs：切页测试先报saved≠new；最终与附件/系统组件共10项通过|frontend-tests-final.log；最初失败在本会话命令输出|
|后端先红后绿|Msp38StepRoundTripTests先失败：独立预期为null；后真实工作簿空步骤分支失败，再修复并回归|backend-red.txt、backend-green.log、backend-green-v2.log|
|后端应用打包|.\\mvnw.cmd -o -pl backend/app -am '-Dtest=CaseAsset*Tests,Msp38*Tests,FunctionalCaseFile*Tests,FunctionalCaseExport*Tests,AgentCaseImport*Tests,PermissionManifestCoverageTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-DskipAntRunForJenkins=true' '-Dplan.review.repo.root=C:/Users/midoo/.codex/worktrees/msp38-integration/metersphere' package；BUILD SUCCESS。131项中92实际执行通过、39条件跳过|backend-package.log|
|原生MySQL集成|python C:/Users/midoo/AppData/Local/Temp/msp38-regression-native-20261126.py --native-only --native-test-filter CaseAssetImportJobServiceTests,CaseAssetExcelRoundTripTests,Msp38StepRoundTripTests,Msp38StepMetadataTests,Msp38AssetExportTests,Msp38ExportSafetyTests,CaseAssetOperationDiagnosticsTests,AgentCaseImportDiagnosticsTests；32项实际通过，0跳过，BUILD SUCCESS|native-run.log；.build/mysql-selftest/d376de85/native-suite.log|
|类型、生产构建|npm.cmd run type:check；npm.cmd run build；均退出0，含vue-tsc、Vite及postbuild|frontend-type.log、frontend-build.log|
|定向lint|npx.cmd eslint src/views/test-asset/asset-case-detail.vue tests/asset-attachment-sfc.test.mjs tests/asset-attachment-draft.test.mjs；退出0|frontend-lint-final.log|
|API/路由|npm.cmd run test:api-contracts、npm.cmd run test:route-tabs；退出0|api-contracts.log、route-tabs.log|
|生产包浏览器回归|本地Vite preview :5390；PW_BASE_URL=http://127.0.0.1:5390 npx.cmd playwright test e2e/asset-remediation.spec.ts --grep MSP38 --reporter=list；3通过，API为本地合成响应，不替代部署端真实后端验收|browser-production-v2.log|

原生MySQL仅新数据目录，运行后停止准确进程并删除临时连接文件；未碰既有数据库。首次浏览器3项均因preview进程退出而连接拒绝，原因是并行构建/preview生成auto-import文件竞争；构建结束后独立preview重跑3项通过，首轮日志保留browser-production.log。生成的auto-import文件仅换行变化，已恢复。构建仍有未改动模块/第三方eval与包大小警告，未扩范围处理。

故障可诊断性：沿用原导入/导出任务安全错误状态、阶段/错误分类及日志，不记录快照正文或原始异常cause；已有诊断故障注入和原生数据库失败测试通过。新快照格式损坏返回固定安全提示；双列内容校验失效回退可见内容，不用元数据覆盖用户编辑。没有接入外部监控/告警。

## 未执行与交付门禁

- Docker镜像/容器构建：按用户要求不执行。当前无部署主机入口及本机Docker命令；未执行容器启动/健康检查。可由部署方执行既有命令：`docker inspect metersphere --format 'status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}'`（以部署实际容器名为准）。
- 修复尚未部署；本轮不修改线上失败报告、不自动关闭两缺陷。用户通知新部署后按原CASE-02/CASE-03/SEC-01实际路径复验，忽略已排除步骤。
- 未运行生产数据迁移全量重放（无迁移）、真实Excel桌面往返或压力测试；工作簿集成为真实EasyExcel/POI/原监听器与数据库测试，浏览器回归为合成API。
- 必须按AGENTS.md先由指定左殇0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0独立审查具体差异，通过后才提交、正常合并推送自有chenqifen/main，再建上游PR；不得自行合并上游PR。CLI读取原HiFox会话401、桌面UI坐标/截图异常，已用登录浏览器定位原MSP-38共享对话，不新建窗口、不改运行绑定。

## Modern Web Guidance

本轮检索取消过期请求并读取js指南。我在异步竞态回归中使用Promise.withResolvers()，遵循“instead of declaring mutable outer variables”；生产代码沿用项目已有请求序号模式绑定页面对象，不新增浏览器兼容要求。安全错误遵循用户更严格的白名单要求，不记录原cause链。

精确文件与差异SHA以同目录review-source-sha256.json、changes.patch及bundle-manifest.json为准。本文与计划均为开发草稿，不冒充产品整版批准或独立审查通过。
