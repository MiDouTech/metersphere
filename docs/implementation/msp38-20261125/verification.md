# MSP-38 开发实现与验证报告 v1.4

日期：2026-11-25。类型：开发文档-草稿；未获用户对本报告整版批准。

代码分支：`codex/msp38-assets-20261125`；基线：`155818422f`。本报告记录送审时快照：当时尚未提交、推送或部署。本轮技术复核未发现剩余重要阻断；不代表指定审查人左殇批准，也不代表部署验收通过。后续审查回执及Git交付以对应记录为准。

## 需求来源与范围

交接总结v1.0（20261125，草稿）SHA256 `f68d0e2d5eb7e94a6e710bcff8ed6a7458211659c3e332d8c399e66911dc1072`；已读取MSP-38在线完整正文、原始证据包和MSP-28查重材料。用户当前聊天明确授权“继续完成开发任务”，随后明确要求“恢复项目目录、系统目录双标签及对应分类功能”。交接材料的指令未被当作独立授权。

产品基线：`docs/product_md/MSP-业务资产重建与受控执行清理-产品文档-v1.0-20261119.md`，批准来源按AGENTS.md记录；文件SHA256：a252a6aa700820ac07b8fdd092f52c1e1ebc938f9f1b3938c48def341c79fe9f。

历史同步两个项目、低权限人工测试、CDS/Kafka与生产发布未纳入本任务。工作区原有22项改动保留，不纳入本次差异。

## 需求追踪与前后端对应

|需求|入口及前端|后端/数据|实际开发验证|
|---|---|---|---|
|附件保留草稿|资产详情上传/删除；仅刷新附件，混合上传等待全部结果，失败也复位选择框|现有CaseAssetController附件接口→CaseAssetService→FunctionalCaseAttachmentService；保留expectedUpdateTime|失败回归先红后绿；完整SFC脚本覆盖全部字段、失败、保存重读、并发冲突；生产包浏览器模拟API路径通过|
|Excel往返|项目目录选择资产导出、覆盖导入|FunctionalCaseFileService→STEP分段/精确边界元数据→原导入监听器；Agent多工作表预检也接入|原export.xlsx真实EasyExcel解析；STEP金额20.50、编号、多行、整步空白、预期配对；TEXT原文换行与空steps保留；xls与接近32767字符回读|
|人员导出|原资产导出创建人/更新人列|仅解析已授权资产实际引用的用户ID，复用详情姓名解析方式；不返回更大人员列表|创建人可解析、不可用用户安全空值；未改变人员/资产可见范围|
|导出≤15MB|原导出任务错误提示|最终xlsx或zip上传前检查≤15,000,000字节；EXPORT_TOO_LARGE持久化及安全提示|真实临时文件边界15,000,000/15,000,001；原生MySQL异步任务失败状态、归属拒绝及提示验证|
|恢复双目录|项目/系统两个路由入口，按资源可见性显示；系统及模块树、搜索分页、分类移动解除、维护弹窗|CaseAssetSystemController/Service；SYSTEM列表仅当前组织Hub的真实资产目录及子模块；复用历史系统、模块、关系表及fc字段|H2真实SQL、同ID/项目模块保持、跨组织/错属拒绝；原生MySQL Spring事务代理故障回滚和分类/删除并发；生产包浏览器分类及创建失败重试通过|
|正式权限诊断|正式/测试现有MCP连接，只读|正式连接当前可见70个项目，未见测试验收项目；测试连接可见1704667279923322880|不能据此断言管理员权限BUG或项目绝对不存在；正式目标映射待确认，未向无关项目提单|
|结束指定会话|原测试MCP executor.event，随后执行器监控页面回读|同一现有连接合法COMPLETE/PASSED；会话b6f64333-54b1-4dd2-b36b-09ea7c7bed30|recorded=true，页面回读R1125-retry-local为PASSED；只描述原固定版本分支结束，不表示MSP-38整体通过|

系统接口：GET/POST `/case-asset/systems`，PUT/DELETE `/case-asset/systems/{id}`；系统下模块POST、PUT、DELETE；POST `/case-asset/system-classification`；原 `/case-asset/case/page` 支持SYSTEM视角。原PROJECT查询仍要求有效catalogId。CRUD沿用CASE_ASSET组织权限；不新增Token Scope。分类同时维护原用例字段与系统关系，写失败回滚，目录删除与分类锁定同一组织Hub行，禁止悬空归属。

无新增数据库迁移或配置依赖，复用V3.7.2_42系统/模块/关系表及现有资产目录。未修改历史迁移，也未声称已经核对线上数据库。完整变更文件见`changed-files.txt`；19个生产源码/校验脚本的独立审查绑定见`review-source-sha256.txt`。

## Excel边界与安全

资产页面当前采用非合并导出。普通步骤保留兼容编号格式；可能歧义的正文用精确边界编码，写入不默认显示的单元格批注，步骤格仍显示可读原文。导入先核对实际原始格的完整SHA256，再核对编码重建的可见文本完全一致；用户改动正文或空白时不应用旧元数据，伪造异文、损坏格式和长度溢出安全拒绝。元数据只参与内容解析，不授予身份或资源权限。保留旧文件兼容读取与行中编号规则。

EasyExcel生成inlineStr，POI 5.5.1直接赋值会残留旧is节点；现先setBlank再赋值以保留正确内容。原件及真实EasyExcel写入/读取回归覆盖此问题。旧文件本身已经损坏或缺少可区分步骤边界的信息时，不能无依据还原原始内容；本次原件的已知STEP/TEXT分支已验证。

## 实际执行的检查

|检查|命令/结果|证据|
|---|---|---|
|后端相关回归及应用打包|下方Maven命令：116项，83实际通过、33条件跳过，0失败/错误，BUILD SUCCESS|`.tmp/msp38-app-v15.log`|
|原生MySQL|独立新数据目录；最终51项实际执行，0失败/错误/跳过（包括数据库事务、导出/导入任务、工作簿、诊断及MVC回归）；见proof/verification-summary.json|`C:/SoftWare/JetBrains/metersphere/.build/mysql-selftest/f80549e9/native-suite.log`|
|前端脚本回归|`node --test tests/asset-attachment*.mjs tests/asset-systems-sfc.test.mjs`：7通过|`.tmp/msp38-node-v14.log`|
|前端类型|`npm.cmd run type:check`无错误输出；本轮生产构建内`vue-tsc --noEmit`也成功后进入Vite并完成构建|`.tmp/msp38-type-v14.log`、当前生产构建日志|
|前端生产构建|`npm.cmd run build`：退出0，生产构建及postbuild完成|`.tmp/msp38-frontend-v14.log`|
|API/路由/资源|test:api-contracts、test:route-tabs、test:permission-resources均通过；83精确绑定、60兼容绑定、0未绑定|相应.tmp日志|
|定向lint/stylelint|相关Vue/TS/MJS及测试ESLint退出0，0错误；旧命令脚本3个console警告；Stylelint退出0|`.tmp/msp38-final-lint.log`、`msp38-stylelint-final.log`|
|开发服务器浏览器|3条MSP38路径通过|`.tmp/msp38-e2e-3.log`|
|最终生产包浏览器|3条MSP38路径通过|`.tmp/msp38-browser-v14.log`|

后端实际命令（仓库根目录）：

```powershell
.\mvnw.cmd -pl backend/app -am '-Dtest=CaseAsset*Tests,Msp38*Tests,FunctionalCaseFile*Tests,FunctionalCaseExport*Tests,AgentCaseImport*Tests,PermissionManifestCoverageTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-DskipAntRunForJenkins=true' '-Dplan.review.repo.root=C:/SoftWare/JetBrains/metersphere' package
```

原生SQL实际命令：

```powershell
python C:/Users/midoo/AppData/Local/Temp/mcp-rbac-mysql-run.py --native-only --native-test-filter CaseAssetSystemMySqlTests,FunctionalCaseExportMySqlTests,AgentCaseImportMySqlTests,Msp38StepMetadataTests,CaseAssetExcelRoundTripTests,Msp38StepRoundTripTests,Msp38AssetExportTests,Msp38ExportSafetyTests,CaseAssetOperationDiagnosticsTests,AgentCaseImportDiagnosticsTests
```

最终生产包浏览器命令（frontend目录；先在独立终端启动preview）：

```powershell
npx.cmd vite preview --config config/vite.config.prod.ts --host 127.0.0.1 --port 5389
$env:PW_BASE_URL='http://127.0.0.1:5389'
npx.cmd playwright test e2e/asset-remediation.spec.ts --grep MSP38 --reporter=list
```

浏览器检查使用本地合成API响应，验证真实组件与请求、模拟保存后刷新，不替代真实后端持久化端到端验收；后端持久化、事务与授权归属边界由独立数据库测试覆盖。创建的本地Vite服务已停止，隔离MySQL进程及临时连接文件由测试脚本清理。

## 中间失败与修复记录

指定左殇v1.0审查发现HTML字面量保真缺口，结论为需修改，原报告见left-review-v1.0.md。真实EasyExcel/POI/导入监听器回归先复现`input<br>then submit`被改写，再修补：STEP凡经HTML兼容转换会变化的原文均使用精确编码；资产TEXT采用单段精确编码、隐藏批注及严格可见内容校验，读取时仅解码验证通过的元数据，不根据普通正文前缀解码。普通业务用例的历史HTML兼容行为保留。

指定左殇v1.1又发现合并STEP分支未经过精确编码，原报告见left-review-v1.1.md；已用实际生产生成函数、合并区域预处理及真实导入监听器复现后修补。资产合并导出逐行对描述及预期编码为单元素步骤，保留一行一条的对应关系；可见编号保证空白步骤不被当作空行跳过。回归同时覆盖单STEP（没有实际合并区域）和6STEP的多行、编号、空白、HTML原文与预期配对，16项定向回归通过。非资产合并导出仍用原始兼容路径。

指定左殇后续三份v1.2分组审查见left-system/left-frontend-excel/left-security-review-v1.2.md。两项性能P2已修订：系统摘要由SQL分组计数、模块按组织一次读取；导出人员采用每次snapshotExcel内创建、跨本任务批次共享的姓名去重缓存，缺失人员缓存空串，仍只查询已选资产引用的用户ID。真实SQL及查询次数测试先红后绿（模块2次改1次、重复作者3次改1次）。未扩大人员可见范围或跨任务复用缓存。

日志P1已按本任务边界修订：CaseAssetOperationDiagnostics仅记录白名单事件、阶段、关联ID、时间、分类、根因类型及合法SQLState/错误码；不输出原始message、Throwable或堆栈。资产限定DataAccess advice复用FAILED与requestId错误协议，HTTP500；导入监听器、Agent预检/逐项写入/失败恢复/扫描/过期、导出快照/生成/通知/清理/恢复/下载均接入安全边界。资产Excel及Agent技术异常去掉原始cause，避免全局处理器二次输出；正常业务校验及权限拒绝保留。调度失败仍按原计划重试，没有写成功状态；实际写入失败回滚及失败状态继续验证。全局异常处理器、日志工具和其他业务策略未修改。

合成秘密标记故障注入已先复现原listener/导出Throwable日志缺口，修订后27项定向原生回归通过；本轮新增维护重试、下载与Agent技术cause/普通校验分离测试。MVC测试首次因缺少JSON Accept而读取XML失败，已使用前端实际JSON协商重跑。最终证据以本报告检查表及proof摘要为准，不将中间运行或作者自测当作左殇最终批准。

左殇v1.3复审已在实现层关闭两项性能P2，仍要求补齐Agent非DataAccessException技术RuntimeException。已由实际AgentCaseImportController、服务及原global handler的MVC请求先复现原始Throwable输出，再补齐安全转换；原ShiroException交回既有401/403处理，无cause业务/治理拒绝仍原样抛出。测试检查原授权确实执行、技术失败500安全协议、无秘密标记/ThrowableProxy，以及Shiro和治理异常身份不变；文件读取失败也记录安全事件并保留原错误码。

前轮目录树刷新竞争场景已补确定性SFC回归并复现：旧请求会提前关闭新请求loading或覆盖新目录。刷新现与列表一样使用请求序号，仅最新响应可以写入树、错误和loading；卸载使请求失效。回归覆盖旧请求先返回、新请求先返回和旧请求迟到失败，节点回归共7项通过。

Modern Web Guidance已检索异步响应/取消并阅读全文js指南；本次异步测试闸门采用Promise.withResolvers()，对应规则“instead of declaring mutable outer variables”。生产刷新沿用已有请求序号，不新增浏览器API或依赖；未据指南扩大UI改造范围。敏感错误处理遵循用户更严格的白名单日志要求，不记录原cause链。

新增真实生产生成函数测试又先复现合并TEXT暴露内部编码，修补为所有资产工作簿隐藏传输编码；随后发现默认批注A1与正式模板表头批注冲突，已改为创建批注前指定实际行列。最终16项回归实际通过，覆盖原空白/编号、HTML字面量及富文本、嵌套和无元数据TEXT前缀、带模板批注的合并导出文件；红绿摘要见proof/msp38-*.txt。子Agent复核这些增量未发现重要阻断，不代替左殇完整差异批准。

保留红绿及中间失败日志，不把首次失败写为通过：附件草稿覆盖、步骤金额/编号/空白丢失、显式TEXT被改写、伪造隐藏内容均建立失败证据后修复。其余测试夹具问题包括P0选项遗漏、UID未初始化、H2列名大小写丢失及前端请求/选择器拦截不匹配；已修复后重跑。原生导入任务测试又发现夹具漏应用已有V138表迁移，补齐真实迁移后35项重跑通过；未改生产迁移或断言。两次并行Maven曾导致共享target中间类缺失，随后改串行验证。POI inlineStr根因已通过实际classpath独立小程序确认。旧路由检查只允许3入口，与获批5入口冲突，已精确同步五路径和双目录检查，没有改动产品一级导航。

## 未执行的检查及真实限制

- Docker镜像/容器构建：按用户要求不执行。
- 新版本容器启动、健康与真实前后端全链路/部署正式验收：未部署；用户规则要求等部署通知。Docker命令在本机不可用，不擅自部署或修改基础设施。风险是尚未证明部署依赖、文件存储及真实浏览器持久化集成。部署后由用户通知，再运行核心路径与以下检查：

```bash
docker inspect metersphere --format 'status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}'
docker logs --since 15m metersphere 2>&1 | grep 'asset_system_write_failure\|case_export_failure\|case_asset_failure\|asset_operation_failure'
```

- 未重放完整历史数据库迁移（本次无新增迁移），未验证实际Excel桌面客户端打开/保存；已通过POI/HSSF/XSSF与EasyExcel读写及边界测试。
- 前端构建仍报告未改动模块/第三方库的eval和包大小警告；未扩展整改范围。
- 正式环境目标项目仍需确认正确项目映射，不能把当前连接不可见直接归因为权限错误。
- 本次复用应用日志，未接入外部采集或告警；安全诊断事件及内部状态不等同已建立外部告警。

## 审查与交付约束

子Agent msp38_review曾技术复核，后续参与Agent诊断实施，不能作为该部分独立批准，未冒充质量-左殇。19个生产源码/校验脚本清单SHA256：`32ec668bafa4b9fe243d142afca182286493a4f4e9b6ba1e3010f532a8d22ed5`。指定左殇对本v1.4完整差异的最终结论待回执；不以自测自动关闭前轮问题。

指定左殇0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0的HiFox身份已在原MSP-38共享对话核对（conversationId=c4b35a0f-b30a-40af-af6e-330eb3d01e21）；未新建协作会话。Codex本机聊天API不识别此HiFox Agent ID，不据此宣称审查人不存在。依AGENTS.md“审查通过后提交”，送审快照尚未提交/推送、未创建上游PR、未部署。原共享对话点名交接的消息与附件回执另行记录；在取得该指定身份的明确结论前，不用本轮子Agent技术复审替代其批准。任何后续生产代码改动必须重新绑定审查。
