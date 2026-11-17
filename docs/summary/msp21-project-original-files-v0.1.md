# MSP-21 项目原件协议实现与验证说明 v0.1

日期：2026-11-05。作者：测试开发-埼玉 c92997d2-06f5-4822-9791-4845858c6b29。审批状态：开发文档草稿；不是独立审查结论或验收通过记录。

基线：MSP-19 完整产品方案v1.0，文档33d95ddf-0c8b-4b09-b82b-a92b80ab8619，SHA256 DE1A45113D511ED08C8F138FEE80FA6CF8C8F6F64A7C7679639A378FC0335EDC。用户批准消息710ea369-b44f-4a93-a284-fe70dfe9520c。本会话用户随后明确“继续执行任务，没有异常”，作为恢复执行授权。

代码基线：chenqifen-miduo/metersphere main与chenqifen在检出时均为7371f3fc87d2dda6e79940d5c5e91fd79685dc17。受管checkout两次以本地/repo/checkout请求发送失败退出，备用Git浅克隆成功；任务分支task/MSP-21-project-files。未切换到上游仓库开发。

## 入口与数据流

MCP九个project.file工具 → AgentProjectFileService当前Token/项目权限 → 持久upload/chunk → 全hash及容器校验 → 既有FileMetadataService.upload/reUpload → 同事务保存精确版本/hash/操作回执。读取通过既有getFileByte，绝不返回物理存储路径。FileManagement的refId是文件族；versionId、systemVersion均为真实不可变记录ID，不伪造数字版本号；用户V3标签另存sourceLabel。

新增PROJECT_FILE_READ/WRITE/DOWNLOAD三个显式scope，AGENT_ALL/FUNCTIONAL_ALL不隐含获得。查询检查READ；写检查READ+ADD或READ+UPDATE；下载检查READ及READ+DOWNLOAD。每次调用重读当前权限。上传会话绑定原Token和用户，不跨Token接管。

## 精确协议

- prepare：projectId/requestId/fileName/size/sha256，moduleId默认现有默认目录，sourceLabel可选。仅创建24h临时会话。大小取upload.file.size与50MiB较小值，未知配置拒绝。每项目最多5个有效待处理会话。
- chunk：uploadId/offset/base64/chunkHash；1MiB原始字节上限，拒绝空块、负数、越界、重叠。允许乱序；同offset同字节重放。完成后保留区间/hash清单，清临时字节；异内容仍拒绝。
- complete/cancel：上传会话与项目行锁串行；全hash及容器核验后才写正式版本。取消在完成前清临时块；完成后取消返回COMPLETE，不删正式文件。原requestId重放及upload.get返回原操作。
- 新版本：existingFileId+expectedVersion+confirmNewVersion=true，要求目录和原文件名一致；旧expectedVersion拒绝。普通网页重新上传也使用项目锁和当前锁定版本检查。
- 同内容/标签先返回可读候选，由reuseVersionId显式复用；saveSeparateOriginal显式选择独立原件仍受同名冲突约束。同名不覆盖，返回FILE_NAME_CONFLICT。
- search/get/versions/download：精确族/版本与项目匹配；latest解析后返回具体versionId；分块下载返回fullHash/chunkHash/eof。offset==EOF返回空块/eof=true；offset超EOF、负数或非法length拒绝。旧记录没有摘要时标NOT_RECORDED，不捏造hash。
- readOriginal(project,fileId,versionId,expectedHash)为C包内部适配点，拒绝latest且重验下载权，调用方须将精确版本/hash写入自身job。本包不改C包预检或旧job。
- XLS/XLSX/XMind仅作原件保存。验证签名及容器，限制ZIP展开总量，拒绝宏/外链/嵌入对象风险入口和路径穿越；XML禁DTD及外部实体；不执行XMind、公式或外链。未声称具备杀毒扫描服务。

页面沿用项目文件详情：显示/复制系统版本与SHA256、原件标签及来源operationId；未记录摘要的旧文件保持空值。管理Token入口新增显式文件授权选项，不自动改已有Token。

## 迁移与回退

新增V3.7.2_144__project_original_upload.sql：临时会话、块与不可变摘要三表。不改旧file_metadata版本身份，不回填伪造hash。需先在隔离MySQL执行、验证索引与重跑，再随正常升级先迁移后开放代码。

过期清理仅限临时块，操作key、指纹、成功版本和摘要不按24h删除。正式原件仍沿FileManagement生命周期；手动删除后回查区分原COMPLETE事实和当前RESOURCE_GONE，不补建。

回退前停止新文件上传/重传和新入口消费者，保留三表及已有对象，禁止DROP新表或全库回滚。旧版本写路径不共享新屏障，混合版本同时写入的安全性尚需隔离验证，不允许据此宣称滚动降级安全。对象存储不与数据库共享事务：存储成功后数据库失败可能遗留孤立对象，沿既有文件服务机制，须在实际存储故障注入中核查并设计受控清理；不得清理成功版本。

## FR/AC与测试对应

| 范围 | 本地自动化测试/检查 | 尚需真实环境证据 |
|---|---|---|
| FR02/03，AC02/03 | outOfOrderReplayAndConcurrentCompleteProduceOneExactVersion；精确下载hash拼接、同块重放、完成后取消 | 真实MinIO XLS/XLSX/XMind、网页定位与下载 |
| FR02/08/10，AC03及S01 | rejectsOverlapChangedReplayEmptyOutOfBoundsAndMissingChunksWithoutFiles；cancelAndCompleteRaceLeavesOneDeterminateResult；requestCaseAndFingerprintAreStrictAndHashFailureWritesNothing | MySQL多实例并发与网络丢响应 |
| FR02/04/09，AC04 | validator各签名/宏/外链/DTD/路径用例；capacityLimitUsesCurrentServerConfigurationAndRejectsUnknownLimit | 50MiB完整上传、真实扫描/存储容量与故障 |
| FR02/03/08，AC05 | newVersionRequiresExpectedLatestAndOldVersionStaysReadable | C包旧job钉版本与网页新版本并发 |
| FR03/09，AC06/22 | tokenAndProjectIsolationAndRevocationRejectNextChunk；fileScopesAreExplicitAndNotInheritedFromWildcards | 当前真实RBAC矩阵、并发用户角色撤销与提交屏障 |
| FR10/12，AC19/25 | cancelledAndExpiredUploadsCannotCreateFilesAndKeepOriginalRequest | 旧worker隔离、MySQL迁移/回退 |
| FR11，AC24/26 | 传输层严格schema/显式授权/安全错误码及恢复提示，结构化写/拒绝审计 | 页面截图、窄屏/切项目、拒绝审计查询与日志验证 |

H2 MySQL模式测试使用真实事务/SQL及模拟FileManagement业务写入，不是MySQL、MinIO或真实RBAC集成证明。产品AC及跨包AC仍未验收。正式验收等待用户明确已部署通知。

实际命令与最终结果将在同版本交付回执中列出；本文件不预填通过数量。完整构建、测试、UI结果以及独立审查状态以实际回执为准。

## 本轮验证记录（2026-11-05）

- 后端：Maven reactor（19模块）test-compile首轮成功。随后定向测试发现并修复SQL保留字、第三方XML解析器选择问题，以及测试替身的varargs类型推断错误。最终定向命令：`.\mvnw.cmd -T1 -pl backend/services/agent-integration -am test -Dmaven.compiler.useIncrementalCompilation=false -Dtest=ProjectOriginalFileValidatorTests,AgentProjectFileServiceTests,AgentProjectFileToolTests,AgentTokenScopeParserTests -Dsurefire.failIfNoSpecifiedTests=false -DskipAntRunForJenkins`。精确最终执行时间/结果以随附msp21-validation.log为准。
- 前端：改动5文件的ESLint退出0；`node --max-old-space-size=256 scripts/verify-project-file-sfc.mjs`对文件详情和Token授权页的真实SFC脚本/模板编译通过。非完整类型语义检查，不等于页面验收。
- 完整`pnpm type:check`已执行，因空闲内存不足且运行显著变慢，仅停止本任务vue-tsc子进程179020；退出4294967295，未完成，不通过。没有停止其他任务或服务。
- 前端依赖安装实际成功（ignore-scripts，无锁文件改动），现有无锁依赖解析报告Vue peer版本不匹配警告；不将安装成功视为构建通过，未为此升级产品依赖。
- `git diff --check`通过。Docker API连接失败，无本机13326/3306监听证据；未运行真实MySQL/MinIO故障注入、容量、迁移/回退或页面截图，未执行生产写入。
- 尚无CI、部署或独立代码初审通过事实；未提交/推送/合并/创建上游PR。按仓库AGENTS的“独立审查→修复及复审→审查通过后提交”顺序，以基线SHA和完整diff/hash送审；通过后再进行后续已授权流程，验证阻塞不强行发布。

剩余风险：对象存储孤立对象清理、真实角色撤销并发屏障、扫描与保留制度、实际容量、混合版本升级回退、A能力清单及C精确fileRef联调，均须实际环境验证；不以H2与模拟存储代替。正式验收须用户通知已部署后进行。

最终回执：2026-11-05T17:28:41+08:00，Maven 19模块BUILD SUCCESS；定向26项全部通过（0失败/0错误/0跳过），其中本包新增18项，既有scope回归8项。该结果绑定本轮源码差异，不代表产品全部AC、真实RBAC/MySQL/MinIO或部署后验收已通过。
