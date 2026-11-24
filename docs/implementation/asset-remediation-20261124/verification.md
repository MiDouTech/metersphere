# 测试资产整改验证记录（开发草稿）

> SSO范围已由用户于2026-11-24重新确定并批准：SSO只验证登录身份、MSP独立管理14天及多浏览器会话、退出仅当前MSP。下文“退出后必须重新验证”的判定属于此前需求历史，当前不再作为验收条件；当前实现和验证见 [独立会话验证记录](sso-independent-session-verification.md)。

日期：2026-11-24。分支 `codex/asset-defect-remediation-20261124`，起点 `e1fd443ec737968c6e20eba29171c5d4a3038346`。依据为用户本轮修复指令、[批准产品基线](../../product_md/MSP-业务资产重建与受控执行清理-产品文档-v1.0-20261119.md)和[部署测试报告](../../testing/asset-deployed-acceptance-20261123-03/report.md)。旧部署验收记录保留原貌，本记录区分新代码验证与部署验收。

## 需求、实现与当前证据

| 要求 | 对应链路与行为 | 当前验证 |
|---|---|---|
|100012 校验失败保留弹窗|目录创建/重命名和来源治理 `before-ok` 等待必填及真实请求，失败可补填重试|本地真实SFC和生产包浏览器用例通过，含503故障注入|
|100013 覆盖导入不重复|当前资产目录内解析身份；Excel写事务锁定目录后重查，覆盖复用旧ID，跳过不新增|真实Excel解析、事务内重查及数据库回读|
|100014 等级保留|多语言等级表头归一→模板字段转换→MyBatis写入|真实Excel→H2回读P1|
|100015 保存按钮可点击|用例详情操作移至MsCard页头，避开正文滚动条|1920/720宽几何命中及物理中心点击发PUT|
|100016 损坏/空导入不假成功|POI验证真实工作簿；空解析/全部失败任务为失败，合法跳过与新增分开|伪xlsx、空结果、全失败、合法skip回归|
|100017 版本停用请求契约|POST的projectId/assetType/assetId进入URL查询参数，与现有控制器一致|浏览器实际请求参数；后端权限/持久化回归纳最终包|
|100018 表单可访问名称|原生输入ID与可见label的for绑定|点击标签聚焦原生控件|
|100019 操作左、检索右|目录和用例两级工具栏顺序一致，窄屏折行|真实组件1920/720布局回归|
|100020 历史同步|只对可证明无历史/引用/人工改动的同内容镜像纠正映射，保留双方记录及审计；锁定当前快照后重算；无法证明安全时明确冲突，保留失败|H2真实SQL、双线程锁等待及故障注入；真实旧数据根因限制见下|
|新增审计MCP|`metersphere.shared_configuration.audits`→现有用户角色+配置归属→既有审计表只读投影|H2/MCP/Node；真实MySQL验证原因及重复选择不新增记录|
|EVIDENCE空目录|统一读取个人已提交、执行器已引用、缺陷已关联图片及旧artifact；预览/下载/版本/任务引用/追溯均复用来源授权|H2/MyBatis与真实MySQL；当前来源权限、MIME、暂存排除、字节一致及关系count/分页|
|只读身份负例|用户明确本轮不考虑，不删除服务端权限校验|移出本轮人工身份测试；旧报告阻塞历史不改|
|MSP退出/手机验证码登录|仅MSP平台，经正常单点登录，不针对BDE|2026-11-24真实浏览器：手机验证码经SSO返回MSP、退出显示已退出；重新登录复用旧SSO，按用户明确的必须重新验证要求判定失败。凭据未写入文件。|

## 审计MCP协议

输入必填 `projectId`、配置 `id`；可选 `page`（默认1，1..1000000）、`pageSize`（默认20，1..100）、`eventType`（SAVE/SELECT_VERSION/EXECUTOR_RESOLVE/RETRY_PARSE）、`version`（正整数）。输出 `items/total/page/pageSize`；条目只包含 `id/configurationId/versionNo/actorId/eventType/createTime/reason`，SQL不选择配置值或密文。查询不写审计。原生服务与Node包装同步，沿用当前用户的公共配置读取权限，不新增Token功能授权。

数据库变更仅新增 `V3.7.2_158__shared_configuration_audit_paging.sql` 配置/时间/ID分页索引。既有审计内容不重写。

## EVIDENCE为空的原因

[证据链路追踪](evidence-trace.md)列出上一轮真实回执：个人执行图片经submission-evidence绑定保存，缺陷图片经bug附件保存，执行器图片经独立加密存储并被业务理解引用。旧目录只查 `ai_execution_artifact JOIN ai_execution_task`，前端也只请求旧artifact下载，因此目录为空不能推导上传失败。修复采用只读投影，不复制上传、不伪造数据、不将暂存图片计为已发布资产。

来源权限分别沿用用例读取、业务资产读取、缺陷读取、旧执行证据读取，并保留Token项目边界及执行器同Token/actor限制。关联追溯两端、总数和分页使用相同授权SQL。仅验证有效的PNG/JPEG可inline，其余按附件下载，响应no-store/nosniff。

## 实际验证

- Node工具包：隔离交付源码 `npm.cmd test`，49项通过（格式修订后再次执行通过）。共享工作区50项包含其他工作项，不作为本次精确提交树验证。
- 前端：类型、定向ESLint/Stylelint、API契约、Node28项和本地浏览器6项通过；最终生产包浏览器6项通过。详[前端验证](ui-verification.md)。浏览器网络响应为本地合成数据，不替代部署后持久化验收。
- 真MySQL：`python C:/Users/midoo/AppData/Local/Temp/mcp-rbac-mysql-run.py --native-only --native-test-filter EvidenceCatalogMySqlTests,SharedConfigurationAuditMySqlTests,CaseAssetCatalogIdentityMySqlTests`。最终9项执行通过、0跳过：证据5、审计1、目录身份3。日志 `.build/mysql-selftest/1bd974bd/native-suite.log`。服务使用全新隔离数据目录，结束已停止自己的进程并移除临时连接文件。首轮审计测试夹具缺project表失败，已补夹具后全轮重跑通过；没有将首轮失败说成通过。
- 后端隔离交付源码回归及应用打包：581项中528项执行通过、53项条件跳过，0失败/错误；其中权限定义完整性2项通过。53项中有9项另在上述原生MySQL轮实际执行，未将剩余条件跳过算作通过。共享工作区先前580项结果仅作对照。初期2处EVIDENCE夹具错误及单模块旧依赖类路径导致的权限定义检查失败均保留；最终隔离完整reactor已执行并通过对应检查。详[隔离结果](proof/isolated-final-results.json)。
- 独立审查：导入/同步22文件、审计/证据21文件、UI7文件的交叉独立复审均无剩余重要问题；Node单文件格式变化及新增原生锁测试另有精确SHA附录。详[导入复审](reviews/independent-import-sync-review-r2.json)、[审计证据复审](reviews/independent-audit-evidence-review-r2.json)、[UI复审](reviews/independent-ui-review.json)。未冒充指定外部审查人的身份或审批。

## 部署验收与真实限制

新修复尚未部署到测试站，不能用旧站点行为证明新代码通过。用户已明确由其审查上游PR并部署后通知正式验收。

100020中，本轮授权只读调用原MSP旧用例 `1712038715113627663` 的 `functional.get(includeSteps=false)` 也返回 `AI_EXECUTION_INTERNAL_ERROR`，traceId `ed9f23fe-b64d-4da7-955f-19847894a3b1`。这证明旧源详情当前也不可读，不能仅从历史同步错误编号猜根因。未直接改写业务库。新安全日志可在部署后按case/job定位SQLState、错误码及根因类型；仍须部署后的真实失败复测，不称存量故障已全部解决。

MSP登录先前出现504及工具超时，用户确认SSO登录方式后恢复浏览器验证，2026-11-24手机验证码登录和本地退出有真实观察；重新登录使用旧SSO自动回流，原通过结论更正为不满足主动退出后重新验证的需求。主动退出额外显示登录过期弹窗，标题为未翻译的 api.errorTip，已记录为提示缺陷；该现象不影响本次重新登录成功。安全证据见 proof/msp-sso-login-regression.json。

容器镜像构建按用户要求不执行。新版本容器启动/健康和真实端到端路径等待部署；可在测试主机执行：

```bash
docker inspect metersphere --format 'status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}'
```

本次没有读取服务器私密日志或改造运维基础设施。待故障版本部署后，只筛选新安全诊断行：

```bash
docker logs --since 15m metersphere 2>&1 | grep 'case_asset_failure'
```

记录仅包含阶段、资源ID、时间、稳定分类、根因类型及经校验的SQLState/错误码；不要在协作中粘贴完整容器日志、认证头、密钥或业务报文。


## 锁与交付源码补充验证

- 对映射修复另在隔离InnoDB中实际运行7项，明确断言REPEATABLE-READ并先建立旧MVCC视图，验证锁等待、提交后当前读、引用/历史/人工修改拒绝。0失败/错误/跳过，日志 `.build/mysql-selftest/30aa7f86/native-suite.log`；隔离进程已停止。首轮新增测试import包名写错的编译失败已修正后重跑，未称SQL通过。新测试不修改生产代码，详[原生锁记录](proof/import-native-test-manifest.json)。两轮原生MySQL共16项执行通过。
- 后端隔离源码树 `e84a4bab6cf5a69c29abaf6387496625650aa7c8`：不含工作区原有四态执行、部署密钥脚本等其他改动。后续仅格式化Node包装、新增原生MySQL测试及补全说明；后端生产源码未改变。Node格式后49项再次通过并有独立TypeScript语义AST复核。
- 实际隔离后端命令：

```powershell
.\mvnw.cmd -pl backend/app -am '-Dtest=Agent*Tests,SharedConfiguration*Tests,EvidenceCatalog*Tests,CaseAsset*Tests,FunctionalCaseImport*Tests,FunctionalCaseFile*Tests,DefaultHubCaseSync*Tests,TestAsset*Tests,ExternalExecutor*Tests,PermissionManifestCoverageTests' '-Dplan.review.repo.root=C:/Users/midoo/AppData/Local/Temp/asset-remediation-verified-20261124-uzwsm_6o/code' '-Dsurefire.failIfNoSpecifiedTests=false' '-DskipAntRunForJenkins=true' package
python C:/Users/midoo/AppData/Local/Temp/mcp-rbac-mysql-run.py --native-only --native-test-filter CaseAssetSourceMappingRepairMySqlTests
```

- 已产生的历史重名用例和不满足无损证明的映射不自动删除或合并；出现歧义时保留数据并明确报冲突。既有MSP旧用例内部错误须在部署后按上述安全诊断定位。
