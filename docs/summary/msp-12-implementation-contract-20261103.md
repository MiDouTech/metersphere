# MSP-12 实施与契约记录

> 本文保留各次开发检查点及历史契约。当前实现、证据边界与送审材料以《MSP-12-开发交付与审查-v0.6-20261104》为准；下文各次“尚未实现/未闭合”只代表该检查点，不代表后续实现状态。已交付的 v0.4 文档与附件保留，不追溯修改。

版本：工程草案v0.5，2026-11-04。作者：测试开发-埼玉（c92997d2-06f5-4822-9791-4845858c6b29）；独立评审者：左殇（0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0），本版未送最终代码审查、未获用户整版批准。产品基线MSP-9完整v1.0（80213字节，SHA256 FD6A63397C12657DCCF8E99167E35B03C90461AF02F7C4CF1F52A32B32798799）；2026-11-04从文档库正式版本b19a748d-715e-48ae-9796-eaebf3fd8ee6重新下载，内容哈希一致。产品批准消息21ee1bf7-6b70-460d-bffe-ec9b14053af0，2026-11-03T06:51:34Z；批准产品不等于批准本开发文档。v0.2补充S06-01—08；v0.3修订R1.1契约；v0.4记录入口、冻结与并发实现以及隔离MySQL开发自测，不声称完整方案已实现。
基础代码：chenqifen-miduo/metersphere main c625c3d119f341fe0cab3647df453c2fc033dff7。业务分支agent/msp-12-plan-review，独立于尚未合并的CDS PR7；不携带PR7差异。
用户最新流程（已再次明确）：开发/自测 → 独立审查/修复 → 通过后提交 → 正常合并并推送至自有仓库chenqifen和main → 自有main向MiDouTech/metersphere的main提PR → 用户手动审查合并该上游PR并部署测试环境。Agent不合并上游PR；CDS暂停。

## 入口及复用

1. Web TestPlanController → TestPlanService.add/copy/batchCopy/import；AgentTestPlanController → 业务服务；metersphere-mcp/src/tools/planReview.ts → 同一Agent API。计划组不配评审；每个真实新子计划均独立配对。
2. CaseReviewController → CaseReviewService 创建、复制、意见、成员增删；评审复制不复制关系。关联后拒绝独立改成员，但不扩大意见操作权限。
3. 文件CaseImportFollowupBridge与资产AssetImportFollowupBridge → ImportFollowupCoordinator；保留原成功ID和任务，去掉双侧独立追加，改为源写后统一同步。
4. TestPlanFunctionalCaseService/批量服务和功能用例删除恢复 → 有效成员版本与持久同步事件；全集按有效functional_case.id去重，不改执行记录。
5. Maven test-plan依赖case-management；关系引擎位于case-management可见的低层，计划端通过提供者提交源事实。不得添加case-management对test-plan/agent-integration的反向依赖。

## 拟定统一契约（S06独立审查后方可定稿）

新API前缀`/plan-review`。服务器从当前登录身份读取操作者；用户/项目/两对象和每项权限在服务层重验，不信请求userId。Agent入口还需现有token作用域及项目约束。

| 操作 | 请求与结果 | 权限/副作用 |
| --- | --- | --- |
| GET /{side}/{id} | side=plan/review；返回权限过滤摘要和真实阻塞状态 | 本侧读；缺对侧读仅返回受限标记，不含对侧ID/名称/差异 |
| POST /candidates | 当前侧ID、keyword、页码/页大小 | 双读和候选侧更新、同项目E状态真实未占用对象；预留不可选 |
| POST /preview | planId/reviewId（新评审用创建设置）、用途、expectedGeneration | M02/M03/M08按用途分别验证；只读业务，持久10分钟预览绑定源版本/意见修订/g |
| POST /confirm | previewId、requestKey、acknowledged | 原子重验，首次建关系；后续仅批准同g剩余差异，不创建替代g |
| POST /unlink | relationId、expectedGeneration、requestKey | M02+E×E；终止旧g，保留两侧集合/历史，旧导入停止 |
| POST /retry | 同g新预览确认 | M08；不以M02代替、不要求RU、预算新轮且审计 |
| POST /restore-preview /restore | 本侧ID、预览ID、恢复权限 | 第一阶段仅本侧状态；第二阶段另用M08新预览，不能复用 |
| GET /operations；GET /operations/{key} | 原key/本人项目操作，分页 | 当前读取权限；首响应丢失复用已生成ID，缺权安全暂停 |
| POST /grant；POST /grant/revoke | 项目、授权代次、显式确认 | 新独立授权管理资源；服务身份不冒用用户创建/追加权限 |
| GET /history；GET /notices；POST /notices/retry | 分页只读历史和独立通知预算 | 意见沿原读权；补通知独立恢复权，绝不恢复成员同步 |
| POST /migration/preview /execute | 项目、清单哈希、批准版本 | 默认只读盘点；写入需限定清单批准、双读双更/SG，隔离验证后使用 |

创建请求新增明确reviewSettings和requestKey；缺设置在写前拒绝。原编辑省略关系不清关系，解除独立；旧无策略导入仍仅导入。

## 持久化与并发设计

统一relation保留每代历史；活动关系plan/review各唯一约束（结束置空活动键，不能删除历史代次）。独立source版本和target意见修订；preview绑定精确修订，确认后批准屏障持续到该轮全部成功。每批在同事务锁对象/关系/授权，重验后同时写R、退出快照、统计、appliedVersion。解除、撤权、锁定和批提交有同一锁顺序及可证明先后。

membership_epoch区分退出重入；当前行保持(review_id,case_id)唯一，退出快照独立存储，旧意见不挂入新epoch；冻结期间持久记录成员退出/重入，不能只比较最终集合。普通保留成员意见不触发永久确认屏障。

operation项目/来源/类型/requestKey唯一，预留ID持久，创建和源追加每阶段重验原用户。SG只写评审成员；撤销后无新目标提交；恢复后M08重新预览。源成功事务持久待同步，目标错误不回滚源。

同步与通知分开任务、预算、状态；各初试+3次，失败后10/30/60秒，不提前且最多延后5秒。耗尽不因源事件重置；人工恢复开启审计新轮。通知故障/恢复按g+故障轮+类型+账号去重，故障先于恢复可读；无合法账号记录原因，不自动替代。

## 验证与环境边界

FR01—16、全部AC及子项均为整体目标，不能以基础层或编译通过宣称交付。需关系唯一性/权限逐项缺失/状态矩阵/意见退出竞态/乱序/冻结重入/幂等/预算/通知/导入与MCP入口/页面回归/隔离迁移回退测试。
目前Docker不可用，无业务数据只读渠道；实际最大功能成员数及峰值并发尚未取得。先实现并用合成负载验证机制，但不将合成数据称实际容量，不宣称30秒/60秒目标已验收。生产迁移、数据裁决、部署由用户另行批准执行。

## 证据记录

2026-11-03：完整产品全文已读并核对哈希；计划/评审/导入服务及权限已有模式只读核对；本文件为工程草案，尚无业务实现或测试通过结论。

## v0.2：权限与返回字段合同（S06-01/08）

PR=`PROJECT_TEST_PLAN:READ`；PU=`PROJECT_TEST_PLAN:READ+UPDATE`；PA=`PROJECT_TEST_PLAN:READ+ADD`；PC=`PROJECT_TEST_PLAN:READ+ASSOCIATION`。RR=`CASE_REVIEW:READ`；RU=`CASE_REVIEW:READ+UPDATE`；RA=`CASE_REVIEW:READ+ADD`；RC=`CASE_REVIEW:READ+RELEVANCE`；I=`FUNCTIONAL_CASE:READ+IMPORT`，原用例新增/更新继续执行既有检查。上述值由PermissionConstants及服务资源核对，不将资源字符串包含READ当作已获得独立读取权。

新增独立PROJECT范围资源：`PLAN_REVIEW:RESTORE`、`PLAN_REVIEW:GRANT_MANAGE`、`PLAN_REVIEW:NOTICE_RECOVER`、`PLAN_REVIEW:TAKEOVER_APPROVE`、`PLAN_REVIEW:MIGRATION`。加入permission.json及中英文名称，但不迁移授予任何普通角色；有现有角色管理权者显式授权。SG为项目关系授权记录（id/revision/enabled/approvedBy/approvedAt/revokedAt），不是用户凭据；自动同步服务固定身份`plan-review-sync`，每批提交锁定该授权并检查启用/修订。用户创建/追加仍逐项验权，不能把有SG等价用户有权。

| 用途枚举 | 服务端当前用户必须具备 | 状态/范围 |
| --- | --- | --- |
| PAIR_EXISTING / UNLINK | PR+PU+RR+RU，前者另SG | 同项目真实未预留对象；E×E；确认/解除再重验 |
| CREATE_REVIEW_WITH_PLAN | RA+PR+PU+RR+SG | 计划E未占用；新评审不要求RU；独立新增仅RA |
| CREATE_PLAN_PAIR | PA+RA+PR+RR+SG，初始成员另PC | 真实新计划；组不建关系；原新增检查继续 |
| APPEND | I+PR+RR+PC+RC+SG | 同g、E×E，原用户每批重验，原用例导入权限继续 |
| RECONFIRM / RETRY / CATCH_UP | PR+RR+PU+RC+SG | M08，不要求RU；只允许当前g、E×E |
| RESTORE_PLAN | PR+RR+PU+PLAN_REVIEW:RESTORE | 仅本侧A→C或C→UNDERWAY |
| RESTORE_REVIEW | PR+RR+RU+PLAN_REVIEW:RESTORE | 仅本侧A→C或C→UNDERWAY |
| GRANT_MANAGE | PLAN_REVIEW:GRANT_MANAGE+当前项目访问 | 显式授权/撤销；恢复后仍需M08追平 |
| NOTICE_RETRY | PLAN_REVIEW:NOTICE_RECOVER+当前项目访问 | 不要求M08；详情另按读权过滤 |

候选搜索先验当前侧读取/更新，再按候选侧读取/更新、项目、状态、占用、预留过滤，分页前过滤；M03创建评审选择计划使用对应用途，不额外要求新评审RU。预览和确认均校验当前调用者，不把previewId当成可转让授权。

分页统一page>=1、size默认20/范围1—100；筛选keyword trim/最多255；非法边界400，count只统计已授权匹配项。请求对象ID长度1—50，UUID预览/操作ID；requestKey长度1—128、不允许空白。错误外层固定`{code,message,operationId?,retryable,fieldErrors?}`，不返回SQL/堆栈。401未登录；403缺权；404不可访问对象（不存在与跨项目统一）；409占用/版本/状态/key参数不符；410预览过期；400字段错误。缺权不排队、不改业务；可独立记拒绝审计。

摘要固定`{relationStatus,restricted,operation?,sync?,target?}`：未关联明确NONE；读取失败是错误响应，不输出NONE。缺本侧读404；缺对侧读`restricted:true`，完全省略target、relationId、generation、source/applied版本、差异、历史和通知关联信息；仅显示“无权查看关联对象”。双读返回target={id,num,name,status}及relation/g、sync={state,blockers[],sourceVersion,appliedVersion,lastSuccessAt,differenceCounts,attempts,nextRetryAt,safeError}；blockers全列而不是互相覆盖。意见正文还受原评审意见读取范围约束。

reviewSettings={name,moduleId,reviewers[],reviewPassRule}，name trim后1—255，默认计划名截至最多252个UTF-16代码单元再加“-评审”，截断避免孤立高代理；JS length与Java String.length同口径。用户手改后不跟随源名称。reviewers至少1、去重、全部当前启用且具评审权；moduleId必须本项目合法目录，root只在选项返回合法时使用；rule只能SINGLE/MULTIPLE，默认SINGLE仅选项支持时填。缺任一在写实体前400 REVIEW_SETTINGS_REQUIRED/INVALID。复制必须显式传新设置，不能从原关系默取。

审计字段={projectId,relationId,generation,planId,reviewId,sourceVersion,operationId,requestKey,sourceKind,originalActor,executingActor,grantId,grantRevision,action,at,added,removed,result,safeReason}；读取受上面过滤，不存token/密钥/敏感意见正文。旧编辑省略关系保留；空字符串不解除；只接受显式unlink。

## v0.2：预览、批准与批提交（S06-02）

preview={id,projectId,purpose,planId,reviewId,expectedGeneration,sourceVersion,targetOpinionRevision,grantRevision,planStateRevision,reviewStateRevision,issuedAt,expiresAt,ackRequired,differenceHash}；首次generation=RESERVED且绑定本操作预留ID；RESTORE_*用途不能用于成员批准。状态NEW→CONSUMED/EXPIRED；preview仅预览记录落库，不建业务关系。

confirm={previewId,requestKey,acknowledged:true}。同操作复合键/相同规范化请求返回原operation和g，即便原预览后来过期；不同参数409 REQUEST_KEY_CONFLICT。不能用另一key消费已CONSUMED预览。首次确认事务内锁定两对象/授权/占用，检查所有绑定修订和状态；不匹配409 PREVIEW_STALE且零关系/成员写，过期410；有效才写关系并标消费。首次无变化也需确认。唯一冲突409 RELATION_OCCUPIED，失败事务不改R/H。

approval={id,relationId,generation,purpose,approvedSourceVersion,approvedTargetOpinionRevision,approvedGrantRevision,status,appliedBatches,lastBatchKey}。状态APPLYING→COMPLETE；外部修订变化转RECONFIRM_REQUIRED；解除/删除转ENDED。关系和approval分离，RECONFIRM_REQUIRED不释放占用、不回滚成功批。取消新预览/过期不改旧approval。

批提交顺序固定：锁project授权记录→plan对象行→review对象行→relation→source clock/target opinion clock→按caseId排序成员行。源写和意见写使用相同相关锁，事务前不可先外发通知。提交前原子比较g有效、两侧E、SG修订、sourceVersion、targetOpinionRevision、批准状态、任务protocolVersion。任一外部变化使未提交批拒绝，持久暂停状态；旧队列/重试只能读停止原因，新源事件只推进水位。自己批次只增加membershipRevision和appliedBatches，不增加外部opinionRevision；因此不会使自己批准失效。

最后一批仅在最新有效T=R、epoch匹配且无待处理项时标COMPLETE、appliedVersion=sourceVersion，退出人工批准屏障；以后普通源随动仍检查g/SG/状态，但不因保留项新增意见单独要求确认。任何后续人工恢复重新启用本屏障。RECONFIRM_REQUIRED的confirm要求purpose=RECONFIRM和M08，使用同g最新预览，只应用剩余差异；不新建g、不要求原确认人仍在职。

## v0.2：成员、意见和单调版本（S06-03）

source clock主键planId，version单调BIGINT；有效集合成员主键(planId,caseId)，字段epoch/present/lastVersion，epoch只在有效全集absent→present时+1。单个测试集删引用但全集仍有不推进退出；软删使有效全集缺席，恢复同ID算新epoch。锁定/失败期间一样持久记录所有有效集边沿，不依赖最终集合推断。

review current映射主键(reviewId,caseId)，字段epoch/sourceEpoch/membershipRevision；保持原case_review_functional_case唯一行。意见API新增expectedMembershipEpoch（关联对象必填，旧请求缺失409 MEMBER_REVISION_REQUIRED），提交事务锁review→relation→成员后比较当前epoch；退出行不存在409 MEMBER_EXITED；旧epoch409 MEMBER_REENTERED，不能挂新epoch。所有意见入口batch/mind/单条共用校验，并推进外部opinionRevision。

退出快照主键(reviewId,caseId,epoch)，保存当时用例身份/内容引用、结论、退出原因/时间及已提交history引用；旧history按映射表(historyId,reviewId,caseId,epoch)归档。当前结论/按人统计只读当前epoch且原deleted/abandoned规则有效的记录；历史只读，不反写当前。重新入队创建新epoch的待评审状态及原合法评审人配置，绝不把旧history重新激活。原表deleted标记不能替代历史分段身份。彻底删除沿既有合法清理契约，不新增自动过期任务。

每批唯一键(relationId,generation,approvalId,batchKey)，历史/通知使用唯一业务键，重放不能新增退出快照。appliedVersion只能`new>=old AND new<=locked sourceVersion`，已确认旧v11任务发现source已v12须重算最新全集或因批准过期暂停，不能按旧事件新增。成员/退出快照/统计/appliedVersion同事务；事务失败全部回滚但不回滚先前源提交。

## v0.2：状态操作（S06-04）

E严格PREPARED/UNDERWAY，C=COMPLETED，A=ARCHIVED；DELETED和UNKNOWN保护。旧PREPARE不在未盘点时猜成PREPARED；发布前只读列实际值，确认合法映射后显式兼容，不静默改数据。
E×E允许M02关联/解除、M05追加和SG自动同步；任一C/A都冻结目标，拒绝配对/解除/追加；源E仍可按PC改源；源C/A成员写拒绝。任一D终止g，不级联另一对象；任一U暂停且禁止新成员写。review自身E的当前成员意见仍按原权限允许，即便plan=C/A；review=C/A或退出历史意见拒绝。

restore-preview={side,objectId,action:ARCHIVE_TO_COMPLETED|COMPLETED_TO_UNDERWAY,expectedState,expectedStateRevision}，返回仅本侧影响及说明性差异；restore={previewId,acknowledged,requestKey}重验相应RESTORE_PLAN/REVIEW。本侧A仅能变C，C另一次预览才能变UNDERWAY；另一侧状态、R/H/统计、最终同步批准都不变。两侧E后另生成purpose=CATCH_UP/M08的新预览，第一阶段预览不接受confirm。

完成操作当待同步/失败/未处理差异存在409 SYNC_NOT_SETTLED，不默冻结为通过。归档预览返回当前快照hash和待追平差异，显式确认后保留快照与差异；删除立即结束g/停旧任务，恢复对象不复配也不补建评审。恢复SG只清授权阻塞，仍保留待重新确认/冻结/预算耗尽等其他阻塞。

## v0.2：操作幂等、恢复与接管（S06-05）

operation复合唯一(projectId,sourceKind,operationType,requestKey)，存canonicalPayloadHash、originalActor、executingActor、reservedPlanId/reviewId、relationId/g、stage、protocolVersion=2、safeError、counts、created/updated。operationId随机ID只定位不授访问权。GET /operations支持复合键精确查和本人项目分页；GET /operations/{id}仍验项目及当前读权限。相同key/hash返回原对象；hash不同409，不能换key自动重建。批量子项键=parentOperationId+sourceObjectId，逐项显示且不回滚其他成功。

阶段VALIDATED→PLAN_CREATED→REVIEW_CREATED→PAIRED→SOURCE_APPENDING→TARGET_PENDING→COMPLETED；失败保留stage并置BLOCKED_PERMISSION/RETRYABLE_FAILURE/TERMINATED，不能把目标待同步写成全部完成。预留归属operationId，不出现在候选；事务在创建前持久预留ID，恢复先查ID与原结果，不能二次生成。零导入成功直接SKIPPED_ZERO_SUCCESS，不造空对象。

POST /operations/{id}/resume {requestKey,expectedRevision}：锁原操作，当前调用者必须为executingActor且具本阶段全部M04/M05，后台调度服务也只能代理该executingActor而不能自行取得其权限。接管前executingActor=originalActor；合法接管后仅检查新executingActor的当前权限及其Agent token/项目/作用域（若由Web合法接管则使用新Web身份），原人只留追溯，不要求其继续有效，也不继承其token。每次阶段/接管/终止递增operation.revision，旧expectedRevision返回409 OPERATION_STALE；只补未成功阶段。source追加成功不回滚；SG只用于TARGET_PENDING的目标写。POST /operations/{id}/terminate {expectedRevision,reason}：当前executingActor具有本阶段所需权限才可终止未执行部分，只释放本操作尚未公开的预留，不删除成功实体、不释放他人占用；已解除g不可resume。

POST /operations/{id}/takeover-preview {newActor,expectedRevision}→审批记录需PLAN_REVIEW:TAKEOVER_APPROVE的项目负责人显式批准及双方读权，不能仅凭角色名；POST /operations/{id}/takeover {approvalId,expectedRevision}由新操作者持完整M04/M05确认，审批绑定项目/操作/原新身份/版本/10分钟有效期；消费审批与改变executingActor/递增revision同事务，重复相同请求返回原结果、不同接管409，旧审批不可二次改变身份。服务端记录originalActor、approver、executingActor；已结束g/变化操作拒绝，不能接管后复配旧g。无可用负责人走既有账号治理，不自动选管理员替代。

## v0.2：通知与独立恢复（S06-06）

fault主键(relationId,generation,reason,faultRound)，记录detectedAt、persistedAt、businessRecoveredAt；intent唯一(faultId,type:FAULT|RECOVERY,recipientId)，字段eligibility/reason、state=PENDING|UNKNOWN_RESULT|READABLE|SKIPPED|EXHAUSTED、attemptCount、round、lastAttemptFinishedAt、nextRetryAt、safeError、readableAt、originalOccurredAt。识别后60秒持久故障及待投事实；健康且合资格60秒内收件箱可读；通道故障不承诺可读。超30秒先按intentId查收件箱结果，UNKNOWN_RESULT不得直接另插，intentId唯一保证重试幂等。

同步与通知各初试+3次、失败完成起10/30/60秒、不提前且最多延后5秒；单次30秒超时。耗尽停止，通道健康恢复/新源事件不新建预算。POST /notices/retry-preview {faultId}验NOTICE_RECOVER和项目访问，读取当前账号/双侧读权，返回当前可补投未可读意图及资格摘要hash；POST /notices/retry {previewId,expectedRound,acknowledged,requestKey}再验身份及资格，变化409，不要求M08、不排成员任务。无双读者只见无对象信息的安全摘要。

原收件意图包括源操作者、两侧创建者、负责人，撤权另含授权管理员，按当前账号去重；失效不自动替代。负责人恢复/新任命仅由原项目成员管理授权流程生效，之后资格变化仍需通知恢复预览确认。无有效收件人记录SKIPPED及原因，不宣称已收到、不无限重试。业务真正恢复另生成RECOVERY；通道恢复本身不触发业务恢复。对每账号先原故障可读再恢复可读，补故障标原时间/已恢复；已READABLE不补。g结束可补脱敏历史故障和结束说明，不恢复关系。

## v0.2：历史与worker兼容（S06-07）

新关系/任务protocolVersion=2；迁移旧followup增加protocol_version默认1及terminated_generation字段，新worker仅消费明确v2；旧worker必须先升级为过滤protocol_version=1，后开启v2写入口。不能只加字段并指望旧二进制理解；旧二进制仍在运行则功能开启预检失败，不批准迁移。新兼容版本维持IMPORT_ONLY旧行为和原成功item IDs；旧group只作legacy映射，唯一权威来自relation。

只读migration preview返回项目、legacyGroupIds/revisions、原两ID/任务阶段、状态、有效集差异、意见影响计数、分类与proposedAction，canonical排序清单hash+schema/protocol版本+previewVersion。合法同项目唯一映射g且仍需批准追平；同对重复映射同g并保留每个legacy ID；多目标/矛盾继续隔离，不择边；孤儿待核实后显式失效；跨项目只提出解除错误边，不跨项目迁移；C/A只登记冻结差异；U暂停；未关联不补建。成员追平需限定清单审批、执行者PR+PU+RR+RU及SG，源/意见/清单变化项重新预览批准，不全批静默刷新。

旧任务映射：未建plan补设置+M04；已有plan缺review先查原预留结果，仅补一个；两对象未配对固定原两ID，冲突隔离；已配对追加未完重验原用户M05/同g，只补未成功源项；解除/删除映射TERMINATED且不再生成对象。保留group.revision→legacyRevision及operation→legacyJobId索引；每一步输出前后对象数/唯一性/源集合/R/H/水位，不以INSERT IGNORE吞掉冲突。

执行顺序固定：隔离备份与恢复演练→兼容schema→全worker协议过滤能力确认→按清单映射/冲突隔离→受控追平→新入口。备份包括受影响关系/原group映射/源成员和修订/评审成员与意见引用/历史快照/任务参数和成功ID/水位/授权审批；不导出token/意见正文到公开报告。迁移重跑按legacyGroupId/operationId唯一映射返回已执行记录，不重复epoch。

回退先关闭新入口/停止新消费者、锁定停机水位并保留成功源提交；仅回到识别v2/终止g的兼容版本，不能回到未过滤旧worker。无后续写入且另获负责人批准才可还原限定备份；有新写入必须按差异补偿，不能全库覆盖。迁移/回退均本次未执行，隔离演练通过前不提供生产执行许可。

## 实现中的纯规则测试（不代表接入完成）

### v0.3新增合同（R11-02—04）

通知提交：每次初投/自动重试/人工补投在实际生成收件箱可读内容的事务内重新查询账号启用/删除、项目资格及双方读取权；资格及权限记录与投递提交需要同一可证明的版本检查，变化重试该资格判定，不能用旧预览缓存。账号失效SKIPPED/ACCOUNT_INACTIVE；当前仍有接收资格但缺读只允许白名单`{intentId,originalOccurredAt,type,safeReason:"关联同步状态发生变化，请联系项目负责人"}`，省略对象/项目名称、对象ID、关系ID、差异和意见。新增接收人必须经过已批准的资格变更预览，不能在worker里自动替代负责人。同故障轮重复识别只返回原fault/intent，不推进faultRound/retryRound，不恢复耗尽预算。

旧任务移交：兼容worker先为受影响job取得行锁并核对protocol_version/owner_fence/g终止标记，再在同一事务完成业务批次和水位提交。迁移器为限定job取得同一行锁，等待已提交的旧批完成；将protocol切到MIGRATING、owner_fence递增并冻结未提交批，持久原成功ID后再生成唯一v2映射，最后同事务切为2。旧worker每批提交前必须比较领取时fence和当前fence/protocol，失配拒绝零新写；仅启动时过滤v1不够。迁移崩溃重跑按legacyJobId唯一映射/原fence恢复，不重新预留ID；MIGRATING任务只由迁移恢复器持相同所有权恢复。启用v2前需证据：限定job无旧租约可提交、旧worker均支持提交fence检查、映射及水位重读一致。无关IMPORT_ONLY不参加冻结。未完成此改造前不执行迁移，也不修改旧任务协议。

所有分页响应`{items,total,page,size}`；精确详情不存在/跨项目/缺必要读统一404，列表在权限过滤后统计total。固定枚举syncState=`PENDING|APPLYING|SYNCED|DELAYED|WAITING_RETRY|FAILED_MANUAL|RECONFIRM_REQUIRED|ENDED`；blockers=`PLAN_LOCKED|REVIEW_LOCKED|UNKNOWN_STATE|GRANT_REVOKED|RECONFIRM_REQUIRED|RETRY_EXHAUSTED|OBJECT_DELETED|LEGACY_CONFLICT`，可叠加。operation实体含单调revision；路由统一`/operations/{id}`，复合key通过GET /operations?projectId&sourceKind&operationType&requestKey精确查，不把key当id。

| API | 固定请求/定位 | 响应item/结果（仍受读权过滤） |
| --- | --- | --- |
| GET /history | projectId,relationId,generation,reviewId,caseId?,epoch?,page,size | id,relationId,g,reviewId,caseId,epoch,sourceEpoch,exitAt,exitReason,identitySnapshot,conclusion,historyRefs；意见内容单独原权限查询；明确旧g可读，不混当前epoch |
| GET /notices | projectId,relationId,generation,faultId?,page,size | intentId,faultId,type,recipientEligibility,state,attemptCount,retryRound,detectedAt,persistedAt,readableAt?,nextRetryAt?,safeError；收件人身份按原用户读取能力过滤 |
| GET /notices/{id} | projectId,id | 上述字段及可执行动作/缺权理由；缺对象读仅安全摘要，不含关系或对象信息 |
| GET /grant | projectId | grantId=projectId,revision,enabled,approvedBy?,updatedAt；仅GRANT_MANAGE可管理详情 |
| POST /grant /grant/revoke | projectId,expectedRevision,requestKey,acknowledged,reason | grantId,revision,enabled,affectedRelationCount,operationId；旧revision409 GRANT_STALE，事务内零部分更新 |
| POST /migration/preview | projectId,legacyGroupIds[],expectedSchemaVersion | previewId,previewVersion,expiresAt,manifestHash,items分类/两ID/差异计数/拟动作,totals,requiredApprovals |
| POST /migration/execute | projectId,previewId,previewVersion,manifestHash,approvalId,requestKey,acknowledged | operationId,state,revision,items[{legacyId,relationId?,generation?,state,safeReason}],counts,backupEvidenceRef；重读清单变化409 MIGRATION_STALE、零写 |

响应例：缺对侧读`{relationStatus:"RESTRICTED",restricted:true}`；历史旧g结果仍携请求g且无当前g跳转；超时`{code:"RESULT_UNKNOWN",message:"结果待确认，请查询原操作",operationId,retryable:false}`；修订冲突`{code:"PREVIEW_STALE",message:"数据已变化，请重新预览",retryable:false}`。用户自己的operationId可用于安全查阶段；不得因此返回无权目标ID。上述schema仍需最终DTO/实现与独立复审，不能冒称已联调。

### v0.3 历史检查点（2026-11-03，未达到完整送审条件）

已接入的实际调用链：

- Web 新建→TestPlanService.add→独立事务预留 operation/对象 ID→原计划、空评审与唯一关系原子提交→统一同步；Agent API/MCP 的同一创建 DTO 传递评审设置与原 requestId，并校验对应 token scope。
- 单条/批量/计划组复制→预先保留所有真实子计划 ID，随后使用同一复制上下文创建各自评审；组自身无评审。旧 GET 复制入口明确拒绝无设置请求，新增 POST 入口供设置对话框调用。
- 默认项目导入→稳定请求预留→原导入任务→创建目标业务计划时配对；覆盖现有计划不重新配对。文件/资产后处理使用独立 v2 队列表，只追加源计划成功 ID；目标达到最新水位后才报告后处理成功。
- 计划功能成员增删、原用例软删/恢复→同事务源集合时钟与轮次→持久同步器→退出只读历史/新轮待评审/当前统计/应用水位。评审页与服务端限制独立增删，意见提交校验页面所见轮次。
- 双方详情→候选/预览/确认/解除/服务授权/本人操作记录；差异详情按页读取编号与名称；评审历史只读抽屉。状态预览与本侧恢复独立于成员追平确认，归档→完成→进行中分两步；自动完成评审保存冻结快照。删除任一侧结束旧代次，不级联删除另一侧。

`python scripts/test-plan-review-rules.py --jdbc` 实际通过 59 项 Java21/JUnit 测试。包含规则层与隔离 H2 事务测试：源事务回滚、唯一关系、集合去重/软删、退出重入、保留意见、确认后修订屏障、重复请求、创建事务失败/原身份、预览所有权与分页、历史读取权限、源删除、完成快照、归档分步恢复和过期/变化零副作用。H2 使用实际新增 137—139 DDL（仅移除引擎/排序声明），不是目标 MySQL 验收；创建 journal 的 Spring REQUIRES_NEW、真实原服务装配和复制/导入全链还需独立测试。合成 10 万 ID 仅证明集合算法，不证明真实容量和时效。

已执行后端 `-pl backend/services/agent-integration -am compile` 与前端 `vue-tsc --noEmit --skipLibCheck`，历史编译通过不替代后续新差异检查；新关联组件 ESLint 已通过。最终送审须重新收齐最终工作区的编译/检查结果。

尚未闭合，禁止标成完整实现：

1. FR-01/04：双方列表列、独立新评审选择已有计划的 M03 预览创建入口及其 Agent/MCP 契约，仍需完成；全部原入口和批量分项结果需实际回归。
2. FR-09：源计划按执行进度自动完成的门禁/冻结快照与既有归档入口统一、归档/恢复快照可读展示仍需完成。当前状态服务及测试只覆盖显式命令与评审完成，不能代替源计划自动完成验证。
3. FR-15：失败操作恢复、受控终止、负责人批准接管与审批原子消费尚未实现；同 key 不同 payload 的冲突不是完整恢复流程。
4. FR-16/AC-31：v2 新任务物理隔离已实现；旧关系分类、审批清单、验证备份、在途旧任务门禁和原子迁移/回退未实现，绝不自动搬迁历史数据。
5. FR-14/AC-32：已有通知表和权限目录只是结构，故障事件/当前接收资格/收件箱去重、独立预算、恢复预览与补投还未实现，不能宣称通知已送达。
6. 真实 MySQL 并发与事务代理、页面操作、文件/资产真实导入、实际 MCP、最终 FR/AC 映射和容量时效证明未完成。正式部署验收按用户要求保持未运行，等用户通知部署后再开始；开发自测不因此省略。

以上差异仍在独立工作分支，尚未提交、合并或送最终代码审查。完整实现与开发自测后再交左殇；通过后提交并合并自有 chenqifen/main，再创建上游 main PR，由用户手动审查合并。CDS 保持暂停。

### v0.4 当前实现检查点（2026-11-04，仍在开发）

本节更新上述历史检查点，不能将旧检查点的59项测试或旧缺口作为本版完成结论。

- FR-01/04：双方列表已接统一权限过滤关联列，计划组显示不适用，缺对侧读取不显示对侧ID、名称或跳转。独立新评审可选择已有计划，经M03预览后才创建评审、配对并排同步；取消/过期/缺权不创建对象。Web、Agent API和MCP工具已注册同一链路，权限不额外要求PA、PC或RU。M03持久操作预留固定评审ID，重复确认复用原操作；实际Agent/MCP调用和浏览器交互尚未完成开发验证。
- FR-09：源执行结果保留，未追平时不能显示已完成；追平后由限定事务保存完成范围和统计冻结帧。原归档入口接入冻结检查，待追平差异必须另行预览确认。已完成/归档统计读取冻结帧，归档恢复为完成与恢复编辑是两次明确操作。独立评审也保存本侧冻结帧，不伪造关系；独立计划恢复编辑后保留旧执行结果，新的执行或有效成员变化发生后才允许再次派生完成并保存无关系的本侧冻结帧。
- 并发修复：源有效成员、目标成员/意见指纹和应用关系在项目锁后使用当前读取，避免MySQL可重复读事务等待前的快照撤回新成员；权限与服务授权也在写入前读取当前锁定记录。原用例删除/恢复先按项目加锁再读取当前引用；统计重算队列按项目/对象锁先于队列锁，避免与源写入排队形成反向等待。未知评审状态不视作已完成。退出历史不复制跨项目功能用例正文。
- 新增137—139 DDL只用于隔离自测，未在现有环境执行；旧关系/旧任务未迁移。MySQL80现有服务保持原状。开发脚本只启动已有MySQL 8.0.46程序的独立数据目录和回环端口，验证实例UUID后使用限库临时身份；结束关闭本次实例并删除连接文件，不读取现有服务账号或配置。

已核实的开发证据：

| 检查 | 命令/路径 | 结果及限制 |
| --- | --- | --- |
| 规则与真实SQL事务 | `python scripts/test-plan-review-mysql.py --native-tests` 的核心SQL套件 | 71项通过、零跳过，实际MySQL 8.0.46；包括旧快照下的权限撤销、服务撤销及源成员提交并发测试。本次不是部署正式验收 |
| 快速关系自测 | `python scripts/test-plan-review-rules.py --jdbc` | 71项中68项通过，3项仅MySQL适用的测试因明确条件跳过；零失败，不把跳过算通过 |
| 前端类型 | frontend下 `pnpm.cmd exec vue-tsc --noEmit --skipLibCheck` | 退出0；不能替代实际浏览器路径 |
| 新关联组件规范 | frontend下ESLint/Prettier | 已执行退出0；不等于功能验收 |
| API/权限资源覆盖 | frontend下 `node scripts/verify-api-contracts.mjs` 与 `node scripts/verify-permission-resource-coverage.mjs` | 退出0；权限资源82项精确绑定、61项已登记兼容绑定、0项未绑定 |
| 原服务编译和导入协调器 | Maven `-pl backend/services/agent-integration -am test -Dtest=ImportFollowupCoordinatorMySqlTests -Dsurefire.failIfNoSpecifiedTests=false -Dquality.mysql=true -Dplan.review.mysql.config=<忽略的临时文件>` | 完整19模块构建成功；4项协调器测试通过、零跳过。由隔离脚本传入连接文件路径，文件结束已删除；不包含凭据值 |
| 新评审对话框交互 | frontend下 `node --test scripts/test-plan-review-dialog.mjs` | 6项通过：预览零创建、显式确认、响应丢失同key重试、明细失败阻止确认、项目切换拒绝旧响应、卸载后不导航、过期阻止提交。其中首项同时覆盖预览/确认边界；执行组件真实setup逻辑，不是浏览器验收 |

原导入协调器集成测试只使用真实协调器和MySQL事务，计划/评审原业务服务为契约替身，不冒称完整应用装配。首轮4项中3项因替身插入遗漏DDL必填`source_changed_at`失败；修正测试夹具后4项全部通过。覆盖零成功不造空对象、重复处理仅复用成功ID、资产成功结果适配，以及追加沿用原评审人。最终证据位于忽略目录`.build/mysql-selftest/8f1acdeb/suite.log`与`native-suite.log`；本次实例已关闭，临时连接文件零残留。未连接现有MySQL80业务服务。

当前仍未闭合的完整范围：FR-15操作恢复/受控终止/负责人批准接管及原子消费；FR-14/AC-32故障事件、当前资格与脱敏收件箱、独立重试轮及恢复补投；FR-16/AC-31历史分类清单、备份演练与在途旧worker移交门禁；AC-26批量子项独立成功/失败事务与恢复；原服务事务代理、完整文件/资产导入和Agent/MCP实调、浏览器路径以及真实容量时效证明。不得因本版新增入口或SQL测试通过而声称以上内容完成。

本版保留草稿属性，尚不满足最终代码送审条件。后续完整送审复用左殇MSP-12既有审查会话；跨会话投递有权限拒绝时不新建替代窗口。正式验收仍未运行，等待用户明确通知部署；无新部署地址或验收账号可交付。

### v0.5 当前实现检查点（2026-11-04，工程草稿）

本节更新前文历史检查点，不追溯改写旧测试结果。本版作者仍为埼玉，独立审查者仍为左殇；**未完成完整范围，未送最终代码审查，业务差异未提交、未推送、未合并。** 2026-11-04共享会话制度已替代上节旧跨窗口描述：最终送审在当前MSP-12会话88f6a0e4-6e5f-4c15-9d20-84633a291079一次明确交接左殇，不再创建新审查窗口。

#### 本轮代码与业务行为

| 范围 | 当前实际代码及行为 | 开发验证与边界 |
| --- | --- | --- |
| FR-15、M06、AC-24e | `PlanReviewOperationService`提供原操作恢复、受控终止、接管预览/负责人批准/新执行者确认。批准消费、执行者变更和审计同事务；原发起者及原凭据引用不可变。当前执行者重验M04/M05，Agent凭据重验元信息与项目作用域；只有显式接管可转为Web执行，不借原凭据 | `PlanReviewOperationTests`覆盖原账号撤销、重复确认、过期、修订变化、缺权及审计/原业务失败回滚。空计划组只需本侧计划权限，不额外要求评审读写或SG；接管审批资源仍需明确授权 |
| 文件/资产后处理恢复 | `ImportFollowupCoordinator`与`ImportFollowupOperationResumer`使用持久父操作、原成功ID和固定子操作。预留在业务事务外完成；父子接管原子切换当前执行身份，旧执行者不可继续预留/追加。已成功源批次不重做，目标追平后才标完成 | 真实MySQL、Spring代理的16项导入协调器测试通过。真实协调器/授权边界与数据库事务，原计划、评审、成员业务工厂仍为契约替身，不冒称完整应用装配 |
| FR-04/14/15、AC-26 | `PlanReviewCopyCoordinator`无外层业务事务，每个组壳/真实子计划独立提交；`PlanReviewCopyRequestJournal`固定整批输入，原子项预留固定两ID。组的原子项范围和次序固定，新增源子项不混入重试；成功子项不覆盖、不重记复制日志。空组无需评审设置、不建评审；真实计划统一重验显式设置 | 5项真实MySQL复制协调器测试通过：部分失败不回滚其他子项、失败项写入回滚、原ID及次序重试、外层回滚不抹掉成功事实、空组权限、改参/跨项目拒绝、已删除成功对象不重建。原复制工厂使用契约替身；实际完整复制服务路径仍需验证 |
| 复制入口与目录并发 | 列表及详情复制回调返回逐项结果；失败保持原请求和设置，不弹整批成功提示。`copy-scope`只读识别空组；原目录失效拒绝、不自动使用root。计划/评审目录删除与创建共享项目锁，删除前逐个验证目录所属项目 | 复制弹窗6项真实setup行为测试通过；目录当前读取及混合项目拒绝纳入核心SQL测试。目录永久删除后的显式改参不是已批准规则，见下文 |
| FR-10/11/13/16、AC-32 | `PlanReviewNoticeService`、`PlanReviewNoticeChannelAdapter`和142 DDL实现故障轮、冻结收件意图、独立初试加三次10/30/60秒重试、耗尽停止、当前账号/项目资格重验。通知投递和回执同事务；成员同步不因通知失败重做。真实业务追平才产生恢复意图，每收件人原故障先于恢复可读；原g结束只补安全历史，不复活g | 核心通知故障注入、预算、资格、补投、顺序、幂等和结束代次测试已执行；真实MySQL通知核心7项通过。原生收件箱适配、实际MyBatis查询和最终新增差异测试正在收集，尚不把“意图已持久”写为“收件箱已可读” |
| 通知入口与隐私 | 双方详情通知面板可查历史故障、预算、资格原因和业务恢复状态；补投须独立通知恢复权限、10分钟预览和显式确认。只增加受控项目管理中已生效的负责人/授权管理者，不自动任命其他管理员。收件箱只存无对象/项目/意见信息的安全摘要，详情每次鉴权；源真实成员变化记录当前执行者 | 通知面板6项setup测试通过：打开/预览不确认、响应丢失复用预览、缺权/过期拒绝、切项目/卸载不接旧响应。原收件箱查询强制当前receiver；分类全读保持receiver与类型同一条件，新增隔离回归测试待最终收集 |
| FR-12/16、AC-31 | `PlanReviewLegacyInventory`和`GET /plan-review/legacy/scan`只读分类合法唯一/同对重复/多目标/矛盾/孤儿/跨项目/锁定/未知、未关联计划和孤儿旧任务，输出确定清单哈希、成员差异计数及任务水位，不导出意见正文或Token。未受控移交的旧对象不允许新关联/另建评审，确认前再次检查；旧任务与新队列物理隔离 | 新增历史分类和迟到旧占用拒绝测试；没有实现或执行生产迁移、备份回写、在途旧worker接管或回退。`executionAllowed=false`是实际能力边界，不把盘点当作迁移完成 |

当前真实路由补充：`POST /operations/{id}/resume`、`terminate`、`takeover-preview`，`POST /operations/takeover-approve`、`takeover`；`GET /notices`、`/notices/object`，`POST /notices/preview`、`/notices/confirm`；`GET /legacy/scan`。前表中的拟定`/notices/retry`、`/migration/execute`等不代表已经存在的接口。137—142变更均未在现有部署执行。

#### 已收集与待收集的证据

- 隔离MySQL `.build/mysql-selftest/7caf6c6d`：核心89项、导入16项、复制5项全部通过，19模块构建成功；实例已关闭、临时连接删除。
- 隔离MySQL `.build/mysql-selftest/6fdae98a`：核心96项全部通过，19模块构建成功；该轮未包含后来新增的历史分类、原生收件箱查询等差异，不能覆盖当前工作区全文。实例已关闭、临时连接删除。
- 当前快速H2核心104项：100项通过，4项MySQL专用并发检查明确跳过，零失败。包括新增历史分类、旧占用确认拒绝、空组接管、当前源执行者和禁用项目暂停通知。
- 前端24项setup行为测试全部通过；新关联组件及新增两份脚本ESLint通过；API契约检查通过，权限资源82项精确绑定、61项兼容、0项未绑定。包含通知面板的前端类型检查退出0；后续新差异仍需对应检查。
- `.build/final-dev-mysql.log`对应最终差异的隔离MySQL、原生收件箱查询及通知隔离测试正在执行。未收集的测试不计入通过数量，最终送审前必须更新本节。
- 前期出现过H2不支持合并ALTER语法及一项测试把独立原因故障轮混算的问题；已拆成两条兼容ALTER，并按故障原因核对恢复意图。修正后H2通过；不删除失败记录，也不把失败轮算通过。

#### 待决策与未闭合范围

1. 产品负责人在同窗消息fd920b75-e77f-450e-9a85-aab20035417c核实正式v1.0全文后确认：两侧均未落实体、原目录永久删除时，是否允许显式重新选同项目目录并保留原预留ID，属于v1.0未明确的新规则，不能自行放行。已向用户提出单项选择，未获新增批准；当前保持拒绝失效目录、成功子项保留、不换ID、不自动改目录。若新增规则获准，需产品修订完整版本并按制度独立审查确认。
2. AC-31迁移执行、验证过的备份/隔离重跑及回退、旧worker在途提交的原子移交，仍未实现与验证。不得以新任务物理隔离或只读清单代替上述闭环；本轮未操作历史业务数据。
3. 完整原业务工厂装配、真实文件/资产导入、实际Agent/MCP调用、浏览器路径及匿名真实最大规模/高峰容量时效仍未完成开发验证。现有SQL/替身/集合算法测试只能证明其明确覆盖边界；不能称全AC已通过。
4. 正式验收、CDS部署、测试环境发布均未运行，按用户要求等待部署通知。无本版真实预览深链或验收账号，不猜测地址或密码。

本版继续保留开发文档草稿属性；批准产品基线不等于批准本工程草稿。完整范围及必要开发验证闭合后，在当前共享会话交左殇独立审查；新增差异重新审查，作者不自判通过。通过后按授权提交并推送自有chenqifen/main，再提上游main PR，留给用户审查合并与部署。
