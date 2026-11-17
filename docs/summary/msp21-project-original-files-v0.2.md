# MSP-21 项目原件协议实现与修订验证说明 v0.2

日期：2026-11-05（Asia/Shanghai）。作者/实现者：测试开发-埼玉 c92997d2-06f5-4822-9791-4845858c6b29。状态：开发文档草稿，作者修复与自测记录，待独立复审，不是缺陷关闭或产品验收结论。

## 1. 固定依据与版本边界

工作项MSP-21，1fded413-3f16-4e47-9c1d-21e48b54a99c，共享会话5c7b3857-f0d4-4e57-8751-88085301bf94。仓库chenqifen-miduo/metersphere，任务分支task/MSP-21-project-files；基线HEAD保持7371f3fc87d2dda6e79940d5c5e91fd79685dc17，无新提交SHA/PR，目标仍为自有chenqifen/main及上游MiDouTech/main。未提交、推送、合并、部署或标任务完成。

唯一批准产品全文为MSP-19 v1.0（2026-11-05），文档33d95ddf-0c8b-4b09-b82b-a92b80ab8619，49810字节，SHA256 DE1A45113D511ED08C8F138FEE80FA6CF8C8F6F64A7C7679639A378FC0335EDC。作者弗利萨8a03127f-b0be-4a63-92a1-df04944dcf05；用户Chenqf批准消息710ea369-b44f-4a93-a284-fe70dfe9520c（2026-11-05T08:28:35Z）。当前会话用户另明确继续执行。未修改获批全文或改变FR/AC。

独立初审者左殇0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0；报告v0.1文档6e213857-0a2c-4925-bd74-6b51c35030d1，实算SHA256 03F1FD6DAA643B4582BFE239570FBAD5295AFA44F69D8EC86E4E56E92FA1A50A；独立复现ZIP文档845280ec-889f-4232-ba2a-4633cba7aa9b，实算SHA256 DD6E2789766E9338E3D8266461FDFD6AA0BE7D49FF605A896BE9425561829BD3。本轮从文档库附件下载、完整读取并核对，与会话副本相同。原初审结论为2项P1、2项P2“需修改”；作者不代评审者关闭。

被审v0.1完整补丁SHA256 7846BD3574DF6AF47572F6C72144171EB6F949FA26724585193025756B80E47D保持不变。为产生可核对增量，在独立只读复核工作树从基线应用该补丁，重新导出的完整diff哈希仍逐字节相同。本次另交付v0.2完整补丁及九个代码/测试文件相对v0.1的增量对照；本说明为可独立阅读的新全文，旧说明保留历史。

## 2. 本包完整实现与调用流

MCP九个project.file工具（upload.prepare/chunk/complete/cancel/get、search/get/versions/download）→ AgentProjectFileService当前身份/项目权限 → 持久会话和块 → 全hash与容器核验 → 既有FileMetadataService.upload/reUpload → 同事务保存不可变摘要及完成回执。FileManagement.refId是文件族，versionId/systemVersion是不可变文件记录ID，V3等业务标签另存sourceLabel，不伪造数字版本号。

本包新增PROJECT_FILE_READ/WRITE/DOWNLOAD显式scope，AGENT_ALL/FUNCTIONAL_ALL不隐含取得。查询需当前READ权限；写需READ及ADD或UPDATE；下载需READ及DOWNLOAD。会话绑定用户/Token，禁止跨Token接管。每次请求重验，文件事务持有实际鉴权依赖行的锁，见第4节。项目文件普通上传、重传、转存和删除继续复用既有服务，相关入口使用项目锁；重传对当前版本做锁定查询。

## 3. 上传、版本与读取契约

- prepare接projectId/requestId/fileName/size/sha256，moduleId默认既有默认目录，sourceLabel独立。仅创建24h临时会话，不写正式文件或用例。上限取upload.file.size与50MiB较小值；配置未知拒绝。每项目最多5个有效待处理会话。
- chunk按uploadId+offset+chunkHash接收，最多1MiB原始字节。允许乱序；空块、负数、越界、重叠和异内容重放拒绝。完成后保留块区间/hash清单、清除临时字节；异内容重放仍拒绝。
- complete校验缺块、完整SHA256、容器及目录后才生成正式版本，写入与回执同事务。原key重放及upload.get回查原操作。cancel在完成前清临时块；完成后不删正式文件。取消与完成由项目/会话锁串行化。
- 新版本必须existingFileId+明确不可变expectedVersion+confirmNewVersion=true，目录和原始名一致；写入禁止latest（含大小写变体），准备与完成核对同一冻结版本。旧expectedVersion冲突不生成新版本；旧字节仍可按原版本读回。修订前已完成事实保留，旧pending会话若含latest则拒绝完成，不刷新期望、不回滚旧结果。
- 同项目、同hash/业务标签先给可读候选，由reuseVersionId显式复用。saveSeparateOriginal仍受同名不覆盖约束。FILE_NAME_CONFLICT附当前有权候选，不按名字自动合并。
- search/get/versions按项目过滤。读latest解析为具体versionId并回传；写期望版本无此别名语义。下载按offset/length逐块重验，返回chunkHash/fullHash/eof，不返物理路径、密钥或永久公开URL。offset==EOF返回空块/eof；越界/负数/非法length拒绝。旧摘要未知标NOT_RECORDED，不伪造hash。
- 内部readOriginal(project,fileId,versionId,expectedHash)供C适配，禁止latest并重验下载权和hash。C须把精确版本写入自身job；本包未修改C/D业务任务、未重建项目。
- 页面沿用项目文件详情，显示/复制系统版本和SHA256、业务标签、来源operationId；旧文件未知摘要保持空值。Token管理新增显式文件授权选项，不改已有Token。

## 4. CR-B01—04逐项答复及修复

| 发现 | 作者处理 | 回归及FR/AC | 状态 |
|---|---|---|---|
| CR-B01 P1：latest绕过期望版本 | tool schema禁止latest各大小写写别名；服务prepare及complete分别用immutableVersion再次拒绝。expectedVersion固定进原会话，不在完成时刷新 | writeVersionsRejectLatestAndFreezeTheSameImmutableVersionUntilCommit：别名拒绝、两个明确V1会话第一成功/第二TARGET_CONFLICT、总版本2、旧V1字节不变、修订前pending别名拒绝；MCP schema独立回归。FR02/08，AC05 | 作者修复并自测，待复审 |
| CR-B02 P1：RBAC撤权可先提交而文件仍写 | 新增AgentBusinessAccess.lockProjectFileAuthorization，锁实际用户、项目/系统角色关系、角色及文件READ/ADD/UPDATE/DOWNLOAD权限行；锁持有到文件事务结束，再查询当前授权。原Token行锁保留，READ_COMMITTED下用当前锁定读取 | fileFirstHoldsActualAuthorizationDependenciesUntilCommit、revocationFirstBlocksAndRejectsFileCommitWithoutNewVersions，分别参数化关系移除、权限删除、用户停用、角色停用、Token撤销，共10项。真实AgentBusinessAccess SQL+双事务闩锁；存储替身。FR09，AC22 | 作者修复并自测，待复审；MySQL仍待证 |
| CR-B03 P2：伪XMind仅凭文件名放行 | 对content.json做严格语法/重复字段/尾随内容及sheet/rootTopic身份结构检查；对content.xml做安全SAX语法、2.0 namespace/xmap-content/sheet/topic结构检查。DTD/实体禁用、外部DTD/Schema访问为空。兼容形状依据仓库XMindZen/Legacy真实源码，不调用其内容转换或导入 | validatesXmindJsonAndLegacyXmlStructureWithoutExecutingContent、malformedXmindCannotCreateAnOfficialVersion及bothSupportedXmindFormatsStoreAndReadBackIdenticalBytes：合法两格式通过，空/错JSON、空数组/错误根、任意XML、DTD/实体及缺topic拒绝，正式文件和摘要0。FR02/04/09，AC04 | 作者修复并自测，待复审 |
| CR-B04 P2：拒绝审计缺原操作定位 | 已授权/归属核验后的异常携带服务器侧immutable审计上下文，经失败事务回滚后REQUIRES_NEW落库；即使随后撤权也保留原已核验定位。否则重新核验当前READ及原Token/用户的会话，未授权/未知资源不复制调用者元数据。统一生成trace用于响应及审计，错误响应不暴露私有审计上下文 | rejectionAuditResolvesOwnedOperationsAcrossProjectsAndSurvivesRollback：两项目各自定位、独立审计在外事务回滚后保留、匿名拒绝无元数据、失败后撤权仍保留可信原定位、伪造参数不覆盖；rejectionAuditAndErrorResponseShareTheSameGeneratedTrace。FR11/12，AC26 | 作者修复并自测，待复审 |

权限锁顺序固定：Token → 用户 → 当前项目/系统角色关系 → 排序后的角色 → 对应文件权限行 → 项目 → 上传会话。角色移除服务BaseUserRoleRelationService的DELETE、权限配置BaseUserRolePermissionService的DELETE及用户/角色停用的UPDATE在数据库层天然竞争这些真实记录锁；无须假定它们会主动获取Token/项目锁。文件先获得依赖锁时，撤权DML不能先提交；文件提交后撤权才能生效，已成功文件保留。撤权先获得行锁并提交时，文件等待后读取最新权限并拒绝，正式版本/摘要不新增。不能把“写前再查一次”当作本修复。

这一选择增加同用户及共享角色上的锁竞争；真实MySQL锁等待、死锁重试与容量尚须实测。数据库死锁/超时回滚时仍须原key查询，不换key创建；本轮不承诺吞吐、不自动改权限或配置。没有修改通用用户/角色管理行为，没有新增权限代次表或DDL。

审计保存适用的projectId、服务器requestId、uploadId/operationId、hash、已正式versionId或冻结fileId/expectedVersion，及actor/token、环境、阶段、结果、trace。可信上下文只进入服务端审计，不进入公开错误details。无权/未知资源仅安全错误、actor与trace，不记录文件名、正文、base64、密码或密钥。

## 5. 容器安全与兼容边界

接收xls/xlsx/xmind。原名不含路径/控制字符且完整保留中文。XLS核对POIFS/Workbook、拒绝加密/宏；XLSX校验ZIP及工作簿元数据，XML禁DTD/实体；XMind检查两种入口内容格式及结构，不执行主题指令、嵌入内容、公式或URL。ZIP重复项、路径穿越、宏/外链/嵌入对象入口拒绝；最多10000项、总展开100MiB。XLSX元数据单项2MiB；本次XMind流式JSON/XML最大嵌套128，JSON单字符串最多1Mi字符，超限拒绝，不截断。XMind支持JSON sheet/rootTopic结构及既有XML content:2.0结构，未知结构明确UNSUPPORTED_FORMAT，不调用业务内容解析。

新正向测试夹具改为符合该结构的JSON sheet/rootTopic，原26项断言保留；没有保留以前只含title的伪合法夹具来弱化校验。旧3项独立测试的断言是“原错误行为发生”，不能继续把该断言通过当作修复成功；本轮新增的是相反预期及业务数据不变断言。

## 6. 迁移、过期与回退

仍仅V3.7.2_144__project_original_upload.sql三表：临时会话、块、版本摘要。须先迁移后开放代码。此次修订不改DDL、不重写旧文件身份、不回填伪hash。24h过期仅清临时块，操作key、指纹、成功ID/摘要不删除；正式原件沿FileManagement生命周期。手动删除后区分原COMPLETE与当前RESOURCE_GONE，不自动补建。

回退前停止新入口、上传/重传及相关消费者，保留三表、历史操作和已成功对象，不DROP或全库回滚。旧版不包含本次RBAC锁与别名修复，混合版本写入不能声称安全。对象存储与DB不共事务，存储成功后DB失败可能遗留孤立对象；真实存储故障注入和受控清理待证，不清理成功版本。本轮未进行生产数据操作或发布。

## 7. 实际验证与证据边界

命令（PowerShell，仓库根目录）：

```powershell
.\mvnw.cmd -T1 -pl backend/services/agent-integration -am test '-Dmaven.compiler.useIncrementalCompilation=false' '-Dtest=ProjectOriginalFileValidatorTests,AgentProjectFileServiceTests,AgentProjectFileToolTests,AgentTokenScopeParserTests' '-Dsurefire.failIfNoSpecifiedTests=false' -DskipAntRunForJenkins
```

本轮最终日志记录19模块BUILD SUCCESS，43项、0失败/0错误/0跳过：服务22、容器8、MCP5、既有scope8。相对原26项新增17个执行场景，包含10个真实鉴权SQL并发场景；本包总35项及既有scope8项。最终日志和四份Surefire报告另交证据ZIP；早先40/42项及补齐XML业务不变断言前的中间结果不替代此最终结果。

并发测试是H2 MySQL模式、真实AgentBusinessAccess查询、Spring事务代理及两个事务的闩锁；FileManagement存储为替身。审计用适配替身向隔离审计表写真实事务，证明REQUIRES_NEW与上下文，而非已验证生产agent_exec_log查询/页面。合法XMind为构造夹具，不冒称已跑用户真实原件。原独立报告及其错误复现证据保留，本轮不改写其历史结论，也不把作者自测称为独立复审。

本轮git diff --check通过；前端文件未修改，v0.1的ESLint与两个SFC脚本/模板通过证据保留，未重复运行。完整vue-tsc此前因低内存终止，仍未完成；生产前端构建/真实页面本轮未运行。无CI或独立复审通过事实。

未运行/仍待证：真实MySQL/MinIO、多实例锁等待/容量、50MiB原件往返、扫描/保留制度、迁移重跑/回退及混合版本、对象存储孤立对象处理、A能力清单/C精确旧job引用联调、部署页面/窄屏及完整前端检查。CR-B01—04是本轮已改代码并有针对性自测的缺陷，不再只作为这些环境风险罗列。正式验收仍须用户通知已部署；作者测试通过不能替代独立复审或产品全部AC。

## 8. 交接要求

请左殇依据新全文、完整补丁及相对v0.1增量核对CR-B01—04和受影响调用链，复查新增断言及权限锁/审计事务边界，记录精确新hash。原v0.1“需修改”结论不覆盖新差异；关闭由独立审查者决定。复审通过前不提交，后续按既有自有分支推送、上游PR及人类决定合并的流程执行。不创建重复任务/会话、监听或定时器，不标任务完成。
