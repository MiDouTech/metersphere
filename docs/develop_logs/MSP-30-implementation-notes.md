# MSP-30 CR-MSP30-04修订说明 v0.4

日期：2026-11-09；作者：测试开发-埼玉 c92997d2-06f5-4822-9791-4845858c6b29。
开发文档-草稿，待质量-左殇0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0对新SHA独立复审。
沿用完整产品v1.4及原批准，不重开B01；无合并/发布/部署/切换/任务完成。

输入R1.1：https://app.hifox.com/miduo/docs/72f9ebb1-eec9-43a5-96f7-da94dc5e70e9 。
被审旧SHA 771a0ea592a3a1055446d2953fb38985bf01d471；前三项主场景关闭，但新增P2 CR-MSP30-04需修改。
报告及证据包已读，原文件SHA256分别为8B06F16680FDE7065DC2A8CB4F7849EF0113F6988DDD30CFC522663BDC31675F、
63D4A408704410F4EF609C572D1DB7A061456974F2CED99B9A57EE365262E3A7，与来件一致。

修订：接收人保存使用稳定行key的reactive pending集合；函数、选择/重试/取消按钮一致依据该状态。
列表转换将pending恢复到新行，记录当前key的最新行引用。请求及成功回读完成后才finally释放；
旧回调同时恢复当前行及旧行loading，保存结果/草稿同步当前行，取消也更新当前行。
不新增服务端锁、版本、队列或全局幂等；其他业务行仍可独立保存/回读。

FR05；AC07，以及AC05/06失败恢复。实际TC位于test-msp30-review-regressions.mjs：
- CR-04 failed draft retry survives other-row GET and releases current row after success/failure（2例）：
  甲→乙失败→延迟重试→另一业务行成功保存触发真实行转换→再重试零新增、取消无效；
  新行loading保留；额外清本地loading也不能绕过稳定pending；成功/失败结束后新旧行均解锁，失败可再次重试。
- CR-04 initial save callback restores rebuilt row on success/failure（2例）：首次保存进行中GET重建，
  不增加请求；旧回调同步当前行最新选择，失败后可取消回甲，成功回读乙。

本轮仅1个前端生产组件及回归脚本/本记录变化。最终62项=原58项+新增4项，不把复审重复25项算作新增场景。
完整类型检查退出0，检查期间生产组件哈希不变；该组件ESLint退出0，0错误/1项既有警告；差异检查通过。精确新SHA及日志见文档库v0.4。
后端未改、未重跑Maven。实际浏览器/API、MySQL、部署SHA/worker、真实队列、离线投递、回退及CI仍未验证。

## 历史v0.3（原结论及限制保留）

# MSP-30 代码初审修订答复 v0.3

日期：2026-11-09；作者：测试开发-埼玉 c92997d2-06f5-4822-9791-4845858c6b29。
状态：开发文档-草稿，待左殇0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0对新提交复审。
产品正式完整v1.4及批准记录沿用，下方v0.2保留历史；无B01重新取舍、无合并/发布/切换。

输入：独立代码初审R1.0 https://app.hifox.com/miduo/docs/84e4950b-2868-4452-a0b9-43e618ed811d ，
被审SHA 7befe0d11b51248ed8903adfb25b0f4dd0272bcf，结论需修改，1项P1、2项P2。
报告及证据原附件已实际读取且SHA256与来件一致；本修订不代表独立初审通过。

|发现|本次修订|受影响规则/验收|实际回归|
|---|---|---|---|
|CR-MSP30-01/P1|system、parameter聚合三种企微READ；两层accessRouter统一按SYSTEM核对权限与UI可见性，阻止项目/组织管理员旁路；保留分区及API边界|FR01/D01；AC01/09|test-msp30-review-regressions.mjs：三种仅READ分别运行真实路由/权限工具/直达守卫；无权、项目/组织管理员拒绝、admin允许、UI隐藏仍拒绝；实际企微初始子TAB及通知分区模板SSR检查|
|CR-MSP30-02/P2|用户及群测试发送分别使用按目标的pending键；按钮loading/disabled与函数守卫一致，检查加载失败、原更新权限及目标启用；finally释放|FR05；AC05.1/07|延迟成功/失败双调用只发一次、失败无成功反馈且可重试、不同用户/群独立、加载/读取失败/停用/只读无请求；实际SFC脚本+API替身|
|CR-MSP30-03/P2|失败不回读覆盖选择，保存行级草稿及已保存快照；明确错误、重试/取消；其他行回读重挂草稿，继续编辑同步最新草稿；成功才清草稿并回读；草稿未解决禁切机器人/模板避免隐式保存|FR05；AC05/06/07|A→B失败：持久A/界面B；独立GET后仍B；重试保存B回读B；取消恢复A不写；失败后选C再回读仍C；实际SFC脚本及表格行转换，网络模拟|

本轮只改前端及本记录/回归脚本，未修改后端、数据库、投递/队列协议、平台权限模型或产品范围。
原后端168项是旧提交的开发及独立审查历史证据，本轮未重跑，不冒充新SHA后端回归。
本轮实际执行前端58项全部通过（25项新增），完整类型检查退出0，最终检查期间生产源码哈希一致；
4个改动前端生产文件ESLint退出0，0错误、1项既有spanMethod警告，git diff --check通过。
精确新SHA、逐项答复及最终日志见文档库v0.3送审答复。
真实MySQL、多角色浏览器、部署SHA/worker、历史三队列、离线/第三方投递、回退及CI仍未验证。

## 历史v0.2（保留原证据与当前批准产品基线）

# MSP-30 实现核查记录 v0.2（完整v1.4送审）

日期：2026-11-09；作者：测试开发-埼玉 c92997d2-06f5-4822-9791-4845858c6b29。
状态：开发文档草稿，待独立代码初审；非部署/切换/验收通过。

现行基线：产品完整v1.4 https://app.hifox.com/miduo/docs/3650511b-7ec7-4e83-97c5-c587f136942a 。
原附件SHA256 55405B95A6EC128597302F36FA6E5FC6AEBF26D7BAD78C76A130373DE5C6FFC7，已重算核对。
批准及继续开发：陈启芬消息9d326aaf-3dc7-4a48-b343-b567a30509c5，2026-11-09T09:03:59Z；
批准记录 https://app.hifox.com/miduo/docs/5d0171b4-ac89-4cf3-aee4-546839e5fa1b 。
独立产品审查者0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0；通过消息154077cb-a216-492d-8928-4b69f203d9ac。
代码独立审查尚未完成，不能用产品结论代替。以下历史v0.1中B01待批准为当时状态，已被以上记录关闭。

## v1.4补齐实现

- PlatformNotificationInitializer为ApplicationRunner，迁移后、应用就绪前在事务中准备六条内置站内信及空模板关联。
  不调用完整项目初始化，不创建邮件/普通规则；基于平台站内信机器人行锁串行准备，确定ID约束防止重复。
- 既有完整六条不做UPDATE，不覆盖自定义模板/规则/停用机器人。缺条、重复组合、错机器人/testId、缺模板明确失败；
  插入中失败回滚整批，异常使启动准备失败，禁止切换。应用启动本身仍不证明真实队列满足切换条件。
- @/回复解析仅有效、未删除的来源成员，排除操作人沿原设置，无合法人零prepareDeliveries/零入队，不增加兜底。
  六类事件分别参数化验证A/B源、空relatedUsers、非法成员、自己、机器人停用及恢复后仅新调用。
- 功能评论原冻结队列实现不改：新增H2持久化回归覆盖旧payload不重算、新A源去重、取消、各结果状态及人工retry权限/版本/次数。
- 已同步远端MSP-28修复，保留Webhook加密/来源就绪提示及错误细节；只将配置作用域文案改为平台。
- 消息管理说明自定义空态与内置通知的区别，没有新增内置规则编辑行或开关。

## 实际TC与AC映射

|实际测试类/方法|FR/AC|证据及限制|
|---|---|---|
|PlatformNotificationInitializationTests.firstPreparationAndRestartHaveExactlySixLinkedDefaultsAndLeaveCustomAndProjectDataEmpty|FR04/06；AC05.2/11/11.1|真实历史DDL三表在H2执行，首次/重复/runner、六组合/默认值/关联断言；非MySQL部署|
|repeatDoesNotOverwriteDisabledRobotOrExistingRuleAndTemplate|FR04/05；AC11.2|停用机器人、已有规则和模板前后逐值一致|
|incompleteAndDuplicateRulesOrMissingAssociationFailWithoutRepair / wrongRobotOrWrongTestBindingFailsWithoutOverwriting / ambiguousDefaultRobotFailsBeforeAnyTaskWrite|FR04/05；AC11.2|缺条/重复/错关联/机器人冲突拒绝，不修改现有行|
|midInsertFailureRollsBackAllSixAndExplicitRetryCanSucceed / simultaneousPreparationKeepsOneSet|FR04/06；AC11.1/11.2|数据库约束故障注入、事务回滚及并发两次准备；H2行锁不是MySQL验收|
|eachBuiltInEventUsesPlatformRulesAndSourceRecipientsWithoutFallback（六组合分别执行）|FR04；AC11/11.1/11.2/12/13.2|真实初始化+MyBatis查询+实际prepareDeliveries/收件解析，A/B分离、零接收与停用；成员查询用模拟，未真实投递|
|PlatformNotificationCommentOutboxTests.oldFrozenPayloadStaysUnchangedAndNewEventsDeduplicateWithRealSource|FR04/06；AC13.2|真实H2 outbox迁移、加密payload、新记录去重/来源、旧成员冻结且发送不回查配置；发送器模拟|
|zeroRecipientsAndDisabledConfigurationProduceNoQueueEntries / deletedOrRevokedSourceCancelsWithoutDelivery（3例）|FR04/06；AC11.2/13.2|空结果无队列；源删除/权限撤回取消，权限提供者模拟|
|staleSendingBecomesUncertainAndTerminalsAreNeverAutomaticallyReplayed / externalAdapterReturnIsSubmittedAndAmbiguousFailureIsUncertain（2例）|FR06；AC13.2|超时/外部不确定及终态不自动重放，非第三方收据|
|authorizedRetryPreservesPayloadAndResetsAttemptsWithVersionCheck（3例）/ otherStatesCannotBeManuallyRetried（4例）|FR06；AC13.2|原允许状态、操作者/update_time、attempts重置和payload保留；H2/模拟权限|

其余FR/AC及未验证项保留以下v0.1逐项表；B01关闭不改变真实部署/历史队列/离线投递尚未运行事实。
最终代码实际验证：2026-11-09后端组合命令BUILD SUCCESS，14类共168项，0失败/错误/跳过；
前端node回归33项全部通过；pnpm run type:check退出0；11个改动前端文件ESLint退出0，
仅messageList.vue既有spanMethod警告；git diff --cached --check通过。
首次事件测试因测试SqlSession缓存屏蔽停用状态而失败，已用STATEMENT缓存范围让SQL变化可见并重新通过；
远端MSP-28合入后，前端测试框架缺少错误工具依赖的失败已改为加载真实appError实现并重新通过。
以上不是线上验收，不把模拟的接收集合当成实际投递成功。

最终基线已同步远端main b4892d7afbcd86015c537d7b5bf047cfd3976745；原任务分支保持。
PR/目标分支/实现SHA及本轮日志见文档库v0.2送审报告。

## 历史v0.1全文（保留当时证据，不代表现行审批状态）

# MSP-30 实现核查记录

日期：2026-11-09；作者：测试开发-埼玉（c92997d2-06f5-4822-9791-4845858c6b29）。
状态：开发草稿；主体改动与本地自测已形成，默认通知初始化存在产品边界冲突，未完成整体实现、未送独立代码审查、未获用户验收。禁止据此部署或切换。

## 固定基线

产品基线为 MSP-27 完整产品方案 v1.3，文档库文档
ad76ad9d-4d61-4f73-8911-cc37f25f084b。
实际读取文档库全文并与附件按换行归一化比较一致；附件 SHA256：
1154684720D91261B87CFBDB81C43C576FECE9BAF8B3A3647CDED5851BBFE84C。
批准依据为批准记录 81bae89a-a3e9-4fb1-a04a-8fda5abee349，
用户消息 2c9f1ef5-dde4-42d8-97dd-277f616fbd69，2026-11-09T07:51:02Z。
采用 D01、D02、D03 推荐项及企微选项 A，不扩展 X01—X05。

源码检出自 chenqifen-miduo/metersphere 的 main，
0965ecc07b3e169fad5d66ab6e6fd915da435c2f。
任务分支 task/MSP-30-platform-notifications。
产品源码证据基线 c066660239b05b76dacccea2f9b1a4c6bc271db8 不能冒充当前部署版本。

## 已核实的数据流与影响

1. 系统参数新增 TAB，复用企微四子页、消息管理两子页和模板编辑。
   旧扫码登录中只移除企微部分，其他集成保持原位置。
2. Webhook 配置读写经 HandoffController → HandoffService → HandoffStore；
   capture、后台 replay、dispatch 和结果收敛必须一起处理。
   原业务键含环境、项目、计划，必须保留。
3. HandoffWorker 每次派发与收敛都读取原项目配置；密文 AAD 含项目与字段。
   旧投递没有完整目标快照，不能将所有配置读取直接换为平台键。
   旧配置必须保护为只读；新投递需有明确配置引用，不能按当前页面项目决定。
4. 企微报告事件、到期 timer、规则 Cron、周期计划、计划执行重试和启动恢复
   均可生成新投递。旧 PROJECT 规则只禁前端按钮不够，所有生成入口须拒绝。
   已入队 outbox 的 target/payload 与日志重试保留原行为。
5. MessageDetailService 是普通通知配置查询的共同入口；NoticeSendService
   的 prepareDeliveries、普通事件、定时发送、项目消息、缺陷消息均调用它。
   按 testId 查询的独立通知不可替换成平台普通配置。
   MessageDetail.projectId 与评论冻结队列 project_id 必须仍为真实来源项目，
   以保留 AbstractNoticeSender 的来源成员过滤及发送前权限复查。
6. 平台消息管理不能直接删除 CheckOwner 后放行任意机器人 ID。
   每个读写删、启停、模板接口须验证对象属于平台集合。
   默认站内信/邮件需显式初始化平台对象，不能从任一项目择取配置。

## 必要验证边界

尚未取得测试部署 SHA、worker 实际开关及来源地址配置、真实历史配置份数、
冲突与三类队列只读清单、合法多角色测试账号。
未查询数据库，不能声称队列为空；未证明旧记录配置引用、原凭据解密与目标稳定前禁止切换。
当前 Docker CLI 无法连接 Docker Desktop Linux Engine，不能据此宣称远端测试服务不可用。
本记录不包含凭据，不授权生产发布、数据迁移或清理。

## 已形成的实现与本地证据

系统参数新增消息通知入口，移除旧独立配置导航并内嵌模板编辑；三组平台配置
不依赖外层当前项目。Webhook 新投递用新增 config_scope 绑定平台配置；旧记录
NULL 继续读取原项目配置及密文 AAD，不覆盖旧目标。来源业务键保持不变。
企微 PROJECT 历史规则写操作及新入队拒绝，事件、到期、Cron 和恢复入口停止
新触发；已冻结队列和原日志重试不改目标。系统角色权限按既有权限项核查，
拒绝项目/组织管理员绕过。消息管理验证平台对象归属，普通消费读取平台任务后
恢复真实来源项目；按 testId 的独立路径保留。补充发现 BugSyncNoticeService
直接查询机器人，已一并切换平台集合并按来源有效成员过滤，无收件人不补兜底。

已执行组合 Maven 本地回归：139 个测试通过，0 失败、0 错误、0 跳过。
包括 H2 关系持久化、后台并发/租约/预算、原密文兼容、旧目标绑定、项目管理员
拒绝、企微旧规则写拒绝、空配置默认结构及缺陷同步特殊消费者。
这不是 MySQL 迁移或实际投递验收。
前端 Webhook 真实组件 setup/模板测试：29 个通过，含平台作用域、失败禁写、
重复提交、秘密输入保留、原来源记录筛选。ESLint 无错误，保留一项原有未使用
spanMethod 警告。完整前端类型检查最终状态见会话交付报告，不在此预报。

## 开发阻塞 B01：不可编辑的内置通知默认规则

真实源码 CreateRobotResourceService.setAtMessageTask 初始化以下 6 条站内信规则：
BUG_TASK 的 AT、REPLY；FUNCTIONAL_CASE_TASK 的 AT、REPLY、REVIEW_AT、EXECUTE_AT。
原规则 enable=true、receivers=[]，接收人由真实事件提供，并非任意兜底用户。
message_task.json 没有这些编辑行；仅靠系统参数界面无法重新创建这些内置规则。
FunctionalCaseCommentOutbox.enqueue 经 NoticeSendService.prepareDeliveries 查询配置，
没有旧 sendAt 的缺省站内信回退。因此严格平台全空会使相应新评论冻结通知无法入队。

批准方案 5.2 要求“Webhook/消息管理平台初始无配置”，4.3/AC-11 又要求保留原能力，
第7节将默认站内信/邮件初始化列为工程核实项；以上交叉边界尚未明确。
当前迁移仅创建两条平台默认机器人，没有擅自启用这6条规则或新增回退。
推荐产品澄清仅保留原6条内置默认规则的平台初始化，自定义配置仍空且不择取项目。
若默认发送边界属于关键规则变更，须按既定制度形成新全文并独立复审、用户批准。
此项未解决前，不交付为完整实现，不合并、推送发布或切换；其他已获批范围的
代码和验证证据保留，不回滚无关工作。
