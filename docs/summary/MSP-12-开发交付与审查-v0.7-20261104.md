# MSP-12 开发交付与独立代码审查材料

版本：v0.7；日期：2026-11-04；状态：开发文档草稿，未获用户对本文整版批准。本文不替代产品方案，不代表正式验收或发布通过。

作者/实现：测试开发-埼玉 `c92997d2-06f5-4822-9791-4845858c6b29`。指定独立审查者：左殇 `0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0`；本版审查结论待其实际交付，作者不自行判通过。

本版为独立代码初审 R1.0 的修订稿。R1.0 对 v0.6 结论为“需修改”，3 项 P1 与 1 项 P2 由独立审查者复核后才能关闭。完整报告 `55c53c3a-21af-4cca-89c4-83963c5393f7`（21292 字节，SHA256 `d576d3b20c34c32c50b2126798719d63c006643b4b7f2dfe16540e2ccf9e558c`）与证据 `d343ca34-866d-4e2d-b548-1b18e45abe9b`（8349 字节，SHA256 `1144eca70aa0797f0e9f60a42e612349618f4d8ea996858b839d61d36a33e17b`）已从文档库下载、核对哈希并完整读取。没有提交、合并、推送或开展正式部署验收。

## R1.0 逐项修复说明（新增差异）

| 编号 | 行为修复 | 真实入口回归定位 |
| --- | --- | --- |
| CR12-01 / P1 | 取消关联先在同一事务中核实当前账号的成员维护权限、实际计划项目、状态、测试集和整批成员归属；校验完成前不推进源水位、不清理历史或缺陷关系。实际删除同时限定计划和成员 ID，数量不符整体回滚。保留仅有 PC 的合法源维护权，不新增 RU/RC/SG 要求。 | `PlanReviewEntryBoundaryMySqlTests`：单项异计划/异项目/不存在、混合批量、异测试集、完成/归档、权限撤销、直接回调；合法单项与测试集全选；并发撤权及解除关系围栏。拒绝请求比对 P/R、成员、历史、缺陷、源水位、代次和审计事实。 |
| CR12-02 / P1 | 新建和原预留恢复共用当前组/目录归属校验，锁住项目授权与目标位置；组须属于当前项目、仍为可用组且其当前目录有效，容量沿用 20。原目录失效继续停止恢复，禁止转投根目录。已有计划补缺失评审也重验原位置，但不把已有计划重复计入新增容量。 | 同类测试：跨项目组/目录、同项目组引用异项目目录、合法根目录/组、预留后目录删除与恢复原目录、组归属/状态/目录漂移、目录删除事务围栏及成功结果重放。 |
| CR12-03 / P1 | Hub 首次及恢复执行 OVERWRITE 时，在原 M04 创建门槛和状态检查之外，再核实既有目标当前 PU；未知冲突策略在预留和工作执行前拒绝。SKIP 与无 PU 的拒绝不再提前创建目标目录，原计划/文档不被改写。 | 同类测试：无 PU 首次执行与 M06 恢复拒绝、补回 PU 后沿用原任务成功、开始后撤回 PU、无 PU 的 SKIP、未知/null/空策略、不能用 PU 绕过原 M04。 |
| CR12-04 / P2 | 旧 IMPORT_ONLY 失败恢复仅要求当前 I、原项目/执行人及原认证仍有效；不再误要求 APPEND、SG 或计划/评审 Token 作用域。原仅导入 Token 仍须 CASE_IMPORT_V2、当前项目和有效身份；关联策略保持原强权限与作用域要求。 | 同类测试：FILE/ASSET、SG 关闭与 I-only 合法恢复；原仅导入 Token 合法恢复；缺 I、异执行人/项目、过期/吊销/停用/缺导入作用域/异项目/异持有人认证拒绝，恢复线程认证上下文还原。 |

测试使用实际服务、权限查询、MySQL 事务与成员删除 MyBatis 映射；计划/评审实体创建、排序/编号、异步调度及部分查询依赖使用明确替身。并发目录测试验证与现有模块删除共用的事务锁协议，不冒称已执行完整目录删除 HTTP 流程。它们是开发回归，不是部署后的浏览器端到端验收。旧生产数据、真实账号和 CDS 设置未操作。

R1 非阻塞观察保留：列表/候选查询的实际最大规模成本，以及原操作列表首 20 条的历史任务发现性，未在本轮扩大范围修改。容量/SLA 与完整页面路径仍待部署后验证。原 `TestPlanCreateRequest.java` 已有 CRLF；本轮保留原风格，不为消除工具换行提示重写整文件。

## R1 本轮实际验证结果

| 检查 | 实际命令与证据 | 结果及边界 |
| --- | --- | --- |
| 核心真实 MySQL | `python scripts/test-plan-review-mysql.py --port 13327`；`.build/mysql-selftest/eec7ba7a/suite.log` | 123 / 123 通过，0 失败/中止/跳过。覆盖源水位、冻结与意见 epoch、旧代次拒绝、迁移演练与回退、源保留及独立通知预算等既有规则。 |
| 原服务与本轮入口整合 | `python scripts/test-plan-review-mysql.py --native-only`；最终 `.build/mysql-selftest/077a98fb/native-suite.log` | 19 模块 BUILD SUCCESS；System 3 + Agent 83 = 86 / 86，0 失败/错误/跳过。含新增 `PlanReviewEntryBoundaryMySqlTests` 40、原导入协调 20、复制 5、Hub 恢复 4、迁移导入 6、通知 3、MCP schema 5 及收件箱隔离 3。该命令明确不重复核心套件，核心结果来自上一行的独立运行。 |
| H2 对照 | `python scripts/test-plan-review-rules.py --jdbc`；`.build/r1-core-h2.log` | 发现并启动 123 项，116 通过、7 项因 MySQL 专属前提中止、0 失败；不把中止项算通过，真实 MySQL 行补足该覆盖。 |
| 前端 setup 逻辑 | frontend 目录执行 `node --test scripts/test-plan-review-dialog.mjs scripts/test-plan-review-copy.mjs scripts/test-plan-review-takeover.mjs scripts/test-plan-review-notice.mjs scripts/test-plan-review-legacy.mjs`；`.build/r1-frontend.log` | 32 / 32 通过，非浏览器验收。 |
| MCP 契约 | metersphere-mcp 目录执行 `npm.cmd test`；`.build/r1-mcp.log` | 33 / 33 通过，stdio 子进程及 loopback HTTP fixture，非部署后端。 |
| 交付还原与完整性 | 完整补丁、增量补丁各在临时 Git index 中应用；逐个重新计算 150 个工作区文件哈希；`git write-tree` 与 `git rev-parse HEAD` 重读 | 两种补丁均还原 `13f6b3a9f930115f78a07dc43267c6306bb03144`；150 项哈希全匹配；HEAD 保持基准，未创建业务提交。凭据/临时连接文件未进入暂存清单。 |

最终整合完成时间为 2026-11-04 15:41:55 +08:00。相关隔离 MySQL 进程均由脚本前台收集后停止；eec7ba7a、c94a0d09、7e67744c、66bb1279、077a98fb 的临时连接文件重读均不存在。只使用专用 loopback 实例，不连接现有系统 MySQL80；本轮未修改全局环境变量、shell profile 或 PATH。

失败记录保留而不计通过：b9836cab 为新增测试构造器使用错误导致编译失败；948a77af 为测试替身泛型传参导致合法路径异常及并发等待超时；7e67744c 为测试缺少编号器；66bb1279 为查询替身未兼容实际生成条件中的反引号而误走新建路径。对照实际生成类修复替身并断言项目/名称查询条件后，077a98fb 全部 86 项通过。业务编号器未为测试而修改。c94a0d09 的原服务 46 项通过是中间证据，不替代最终整合结果。

本轮未重复前端类型、生产构建和 ESLint，相关源码未改变，历史执行证据见第 5 节。浏览器、完整原文件/资产工厂、实际容量/SLA、部署预览与正式验收均未运行；生产迁移、CDS、提交、合并和推送均未执行。四项修复等待左殇对本版实际代码树复审，作者不自行关闭问题。

## 1. 唯一批准基线与交付边界

- 工作项：MSP-12，`3a4fb502-5886-4e71-9bf5-baac702a2c47`；来源需求 MSP-9。
- 产品：MSP-9 完整产品方案 v1.0，80213 字节，SHA256 `FD6A63397C12657DCCF8E99167E35B03C90461AF02F7C4CF1F52A32B32798799`。
- 正式文档：`b19a748d-715e-48ae-9796-eaebf3fd8ee6`，https://app.hifox.com/miduo/docs/b19a748d-715e-48ae-9796-eaebf3fd8ee6 。批准消息 `21ee1bf7-6b70-460d-bffe-ec9b14053af0`，2026-11-03T06:51:34Z，完整版本及 P01—P08 已批准。旧文中的“待批准”由此后确认记录更新，原件未改。
- 仓库：`chenqifen-miduo/metersphere`；开发分支 `agent/msp-12-plan-review`；基准提交 `c625c3d119f341fe0cab3647df453c2fc033dff7`。
- 审查前不提交业务代码，使用基准提交、完整代码补丁 SHA256 与独立文档哈希绑定范围。审查通过并修复复审后再提交，随后按用户既有授权合并/推送自有 chenqifen、main，并从自有 main 向 MiDouTech/metersphere main 提 PR。上游由人类合并。
- CDS/Kafka 工作暂停。正式验收在用户通知已部署后才开始；开发自测不等于正式验收。没有当前业务代码的已部署预览地址。

## 2. 入口、数据流与可复用实现

Web 列表/详情/新增/复制/导入 → 原 Controller/原业务工厂 → 持久操作预留 → 原计划/评审表及单一关系代次 → 分批成员对账 → 原站内消息表。Agent API、Java MCP 与独立 stdio MCP 工具接入相同服务，不建立第二套关系权限或成员写入权威。

保留现有 test_plan、case_review、功能成员/意见与导入原成功记录。关系只保存单一权威，不向双方实体各塞一个可漂移外键。原导入 FILE/ASSET 来源及成功 ID 保留；只有目标随动使用服务授权。后台创建/追加仍检查实际执行人的 M04/M05、导入权限和原执行认证。

新增主要代码区域：

| 区域 | 实际行为与调用方 |
| --- | --- |
| case-management/functional/planreview | 关系、源/目标版本及 epoch、差异预览、意见围栏、冻结恢复、操作恢复/接管、通知轮、历史迁移 |
| test-plan/service 与 hub/service | 原工厂的稳定 ID 预留、真实计划自动评审、逐项复制事务、原 Hub 任务恢复及迟到失败隔离 |
| agent-integration/service 与 tool | 原导入任务迁移/恢复、当前认证元数据检查、Agent API/MCP 同契约 |
| frontend/components/business/plan-review | 关联卡、预览确认、新评审、复制、通知、负责人接管及历史迁移受控入口 |
| system-setting/NotificationService | 收件人强制锁定当前用户，读全部的资源条件保持 AND，复用原消息中心 |
| migration/3.7.2/ddl 137—143 | 增量表结构；Flyway 不自动配对、追平、迁移业务数据或授予权限 |

## 3. 实现范围与 FR / AC 映射

下表说明代码覆盖与自测定位，不把 AC 编号齐全当成全部验收通过。真实浏览器、部署环境、完整文件/资产工厂和实际峰值时效仍见第 6 节。

| FR | 对应 AC | 已实现行为 / 自测入口 |
| --- | --- | --- |
| FR-01 / 03 | 01/02/10/18a—f | 同项目一对一、代次唯一、首次/重配差异确认、解除永久停止旧代次；Application/Persistence tests |
| FR-02 | 03/30 | 双方列表权限过滤关联列、详情关联卡及当前同步/失败信息；前端类型/契约检查，浏览器待部署 |
| FR-04 | 04/15/26 | 原计划新增自动评审；组壳不建评审、真实子计划逐项提交；成功子项不回滚，失败复用原 ID；CopyCoordinatorMySqlTests / copy setup tests |
| FR-05 | 05/09/20 | 关联评审的 API、表格、批量、思维导图成员写入受限，意见仍按本侧权限；原入口 guard 与应用层测试 |
| FR-06 | 06/07/15 | FILE / ASSET 三策略，主导入成功 ID 才追加源；零成功不建空对象；原队列/原进度恢复；ImportFollowupCoordinatorMySqlTests / LegacyImportMySqlTests |
| FR-07 | 08/19 | 退出保留只读历史、重入新 epoch 待评审、连续保留成员意见不清空；Application/Persistence tests |
| FR-08 | 09/10/18/23/27/28 | 稳定最新全集、旧版本不倒退、初试后最多三次自动重试、失败后 10/30/60 秒，不因新源重置预算；规则及真实 SQL 故障测试 |
| FR-09 | 11/21/29 | 完成/归档冻结；本侧恢复与双方可编辑后 M08 是两个独立批准；删除结束代次不级联；Lifecycle tests |
| FR-10 | 03/13/18/23/30/32a—f | 来源已保存与目标未追平分开显示；通知故障/意图/资格/预算独立持久；Notice tests / native channel tests / 前端 setup tests |
| FR-11 | 02/11/14/18/20/24a—g/32c—f | 当前账号/项目/角色/资源与 SG 检查；源 PC 不额外借 RU/RC；人工重试/接管按原动作；权限资源扫描无漏绑 |
| FR-12 | 12/22/31 | 历史盘点、显式冲突选择、受控备份/真实隔离演练、原对象映射与旧任务围栏、重跑/回退；LegacyTests / LegacyMigrationTests / LegacyImportMySqlTests |
| FR-13 | 04/13/25/31 | 原对象、请求键、执行人/原人/审批人、代次与状态审计；兼容组壳/仅导入路径，不接受缺设置的真实新计划；原调用与 schema tests |
| FR-14 | 04/14/15/26 | 名称/目录/评审人/规则完整确认，当前资格失效不改配非法默认；Agent DTO 与两种 MCP 同契约 |
| FR-15 | 07/14/16/24e/26 | 持久原操作恢复、仅停止未来步骤、负责人预览批准后原子接手；原认证引用留追溯，显式 Web 接手才清执行引用；Operation tests / import/copy/hub native tests |
| FR-16 | 08/17/18—24/27—29/32 | T 仅为有效同项目功能用例 ID 去重全集；保留跨测试集引用；不写 API/场景、内容或执行结果；MySQL 围栏/并发与成员测试 |

## 4. 历史迁移与部分任务恢复

产品作者澄清消息 `1616a224-8608-4e5c-b279-7c51f3612524`：v1.0 的“本轮不执行迁移”指产品设计交付阶段，并不豁免开发实现或隔离演练；不要求不停机在线切换。本实现采用先停旧入口/旧消费者、核实在途提交结果及水位，再执行准确清单的受控方式。没有执行生产迁移。

1. 项目盘点返回类别、成员差异摘要、原任务状态和逐项水位哈希。重复旧边不自动选目标，缺失/跨项目/未知状态不自动修复；历史未关联计划不补建。
2. 明确 KEEP / ISOLATE / RELEASE 与 ADOPT / ISOLATE / TERMINATE；预览绑定项目、请求键、原记录/成员/意见引用哈希、服务授权版本与执行人。重新登录可查最近迁移编号，也可读取原编号。
3. 备份仅包含迁移影响的关系/任务/成员元数据、成功水位及意见内容哈希引用；不导出真实意见正文或认证值。迁移和回退本身不覆盖业务内容，因此不假称这是全库灾备备份。
4. 演练仅在显式单独配置的空 MySQL schema 中执行，同一 server UUID + catalog 会被拒绝；非空库不清空，缺配置不退回主库。服务端在隔离库运行实际迁移执行器、幂等重跑、元数据回退及有后续意见时的保留事实补偿，才生成与备份哈希绑定的凭证。客户端不能上传“演练通过”标志。
5. 当前负责人按准确清单批准，并登记已停止旧入口/消费者及兼容程序 SHA 的证据哈希。执行时重新检查负责人、执行人、SG、备份/演练及当前源事实；在途旧提交改变水位时整次拒绝，保留其已提交事实。
6. 旧任务行锁定后转 MIGRATED_V2；新任务使用物理独立队列，旧消费者和旧 retry 的条件不匹配。旧组合元数据归档后，新关系只映射明确保留的原两 ID，不改 R/T/H；成员差异另走最新 M08。
7. 老任务的 NO_PLAN、PLAN_ONLY、已记录两对象但组合尚缺、FIXED_PAIR 分别保留原 ID / 成功标记，只为原来尚缺的实体预留 ID。已删除成功对象不重建。移交完成先暂停，显式恢复原操作后才续未成功步骤。
8. 没有后续写入时只恢复原先实际存在的合法旧组合元数据；旧任务仍保持停止，旧 g 仍结束。有新成员、意见、操作或水位写入时，只能另预览批准 COMPENSATE_KEEP_FACTS；不拿备份覆盖新事实、不触碰不属于本次的 g。
9. 已有/已结束新协议关系不能通过旧记录自动重配。原缺失设置只允许显式补齐，已有明确目录失效时停止恢复。产品作者消息 `fd920b75-e77f-450e-9a85-aab20035417c` 指出的“删除原目录后改配”属于额外业务例外，未加入本批准基线，不以默认根目录代替。

部署时若要实际使用迁移演练，需独立 runtime 配置 `plan-review.rehearsal.enabled/jdbc-url/username/password`；地址/用户名/密码来源分离，URL 不接受内嵌账号密码，不写入 Git。此配置仅启用独立演练器，不代表取得任何生产迁移或负责人批准。

## 5. v0.6 基线自测证据（历史，不替代本轮新增差异验证）

下表保留 v0.6 送审时的完整开发自测记录。本轮未修改前端或 MCP 源码，并重新运行其 32 / 33 项测试；后端本轮结果另行列出，不能用历史结果覆盖新增差异。v0.6 最后三处 ESLint 修复只移动相同导入项，没有改变业务逻辑；其类型和代码规范检查均通过，不代表全部 AC 已完成正式验收。

| 检查 | 实际命令 | 当前结果 |
| --- | --- | --- |
| 核心真实 MySQL | `python scripts/test-plan-review-mysql.py --port 13327` 内部调用 rules --mysql-config；固定代码最终轮 3f7cb526 | 123 项通过，0 跳过/0 失败；含 1001 成员分批、真实 SQL 故障、真实第二 schema 演练及旧消费者行锁在途提交 |
| 原服务事务及 schema | `python scripts/test-plan-review-mysql.py --native-tests`，b27df467，Maven 19 模块：NotificationIsolation / AgentMcpCreateSchema / ImportFollowupCoordinator / CopyCoordinator / HubOperation / NoticeChannel / LegacyImport | 19 模块构建成功；System 3 + Agent 43 = 46 项通过，0 失败/跳过；LegacyImport 6 项含实际 journal 补原缺失评审（评审实体工厂为 contract double） |
| 前端实际 setup 交互逻辑 | `node --test scripts/test-plan-review-{dialog,copy,takeover,notice,legacy}.mjs`（实际以五个完整文件名执行） | 32 项通过；不是浏览器渲染测试 |
| 前端类型 | `pnpm exec vue-tsc --noEmit`，项目 tsconfig 原有 skipLibCheck=true | 最终前台调用退出 0；日志 frozen-types-final.log |
| 前端生产构建 | `pnpm exec vite build --config ./config/vite.config.prod.ts`，随后 `node scripts/write-build-manifest.mjs` | 退出 0；日志 complete-scope-vite.log；这是未提交工作区的本地构建，不是已发布镜像 |
| 前端代码规范 | 对所有本次暂存的 frontend/src 下 Vue/TS 文件执行 ESLint；新组件/API 经 Prettier | 0 错误，保留 3 项既有警告：消息中心 console、旧归档函数、旧 ExecutionStatus 导入；未借机改无关行为 |
| API / 权限资源 | `node scripts/verify-api-contracts.mjs`；`node scripts/verify-permission-resource-coverage.mjs` | 通过；82 个直接绑定、61 个已审计兼容绑定、0 漏绑 |
| 独立 stdio MCP | metersphere-mcp 中 `npm test` | 33 项通过；实际 stdio 子进程 + loopback HTTP fixture，未调用部署后业务后端 |

补充：此前隔离轮 e1a2cf95 为核心 118 全通过、System 3 + Agent 37 全通过；23237fd0 为核心 120 全通过、System 3 + Agent 42 全通过；0cf6f367 为新增三阶段导入移交 3 全通过。这些是对应时间的快照，不覆盖其后新增差异。初次 3227a96e 中幂等返回的 Java 集合类型断言失败已修正并在后续轮通过，原失败日志保留。

MySQL 8.0.46 使用新数据目录、loopback 13326 / 13327、独立 `msp12_pr_` schema 和限定测试账号；不接现有 MySQL80 服务。两轮精确实例均已停止，两处临时 connection.properties 检查均为不存在。临时认证材料未进入本文、代码补丁或会话；未修改全局环境、shell profile 或 PATH。

实际 TC 使用仓库中的可执行 `类名.方法名`，不生成空编号冒充覆盖。重点新增：

- `PlanReviewApplicationTests.schedulerCanCommitSeveralBoundedBatchesWithoutAddingOneSecondPerHundredMembers`：1001 个合成功能成员，真实 scheduler drain 分批事务，不是现网最大容量结论。
- `PlanReviewApplicationTests.actualTargetSqlFailureKeepsSourceAndHonorsTheOriginalIndependentRetryBudget`：真实约束失败保留源，次数/等待/耗尽及新源不重置预算。
- `PlanReviewLegacyMigrationTests.mysqlInFlightOldWorkerCommitIsReadAfterItsRowLockAndMakesTheBackupStale`：实际旧 PENDING 行锁协议与在途提交，不冒称运行旧完整应用二进制。
- `PlanReviewLegacyMigrationTests.mysqlRealBackupApplyReplayAndMetadataRollbackKeepBothMemberSetsAndOpinions` 及 `mysqlLaterOpinionForcesExplicitCompensationAndStaleRollbackApprovalCannotEraseIt`：真实第二 schema 演练、幂等、两种回退及过期批准拒绝。
- `PlanReviewLegacyImportMySqlTests`：原身份与原认证引用、显式 Web 接手、原 P/R ID、原成功位、零成功、ASSET 来源以及缺少旧组合的两对象映射。

## 6. 未运行与技术限制

- 正式部署验收、浏览器实际登录/宽窄屏/完整用户路径、当前分支预览：未运行，按用户要求等部署通知；没有虚构 URL 或验收账号。
- 完整运行应用的真实文件字节解析/资产工厂/外部 Agent 后端端到端：未运行。原服务测试中创建工厂或个别外部依赖使用明确 contract double；这不能替代部署后的真实路径。
- 实际生产最大功能成员数、峰值并发和 30 秒/60 秒时效：未取得与未验证。合成 1001 成员和集合算法测试不能替代实际容量证明。实现保留延迟/失败真实状态，不把目标当成已达 SLA。
- 迁移协议会持有当前项目的写锁；演练也在源事实校验事务内运行。管理员应安排维护窗口，大规模清单的锁持有时长未验证；不把小样演练耗时承诺给生产。其他项目使用独立项目锁。
- 生产备份、迁移、消费者停机、账号管理、CDS、发布：未执行。隔离数据库演练不代表生产切换已批准。
- 独立代码审查：待指定审查者对本次准确代码快照实际执行；旧契约/旧 SHA 的审查结论不外推。

## 7. 审查重点与后续路径

请独立检查：所有真实新增/复制/导入/Agent/MCP 入口是否接同一权威；M02—M08 当前权限及认证上下文；源提交与目标失败的事务边界；旧代次/乱序/意见 epoch；逐子项复制与父子恢复；迁移准确清单、旧 worker 围栏及新事实补偿；通知资格、脱敏、预算和收件箱隔离。报告需绑定本文版本/哈希与代码补丁/目标分支/基准 SHA，新增修复差异另复审。

审查通过后由 Agent 提交、合并推送自有分支并创建上游 PR；上游不自行合并。用户合并及部署测试环境后，Agent 再执行正式验收并收集真实页面、导入、权限、容量、时效证据。不得把提交、构建、审查或阶段完成写成整体已验收。

代码快照：基准 `c625c3d119f341fe0cab3647df453c2fc033dff7`，暂存代码树 `13f6b3a9f930115f78a07dc43267c6306bb03144`，150 个代码/规则/测试文件，未创建业务提交。代码补丁 `MSP-12-code-v0.7.patch`，1024108 字节，SHA256 `e0ebb4aaee26a9c4e58e57d30306445e85c170808f463a8460a2ebea52736173`。代码树不包含本文及历史开发文档；文档单独以版本和交付哈希绑定，避免自引用哈希。

v0.6 开发文档为 `26fbffbc-a6d7-473c-bb77-8ba1ad46a0d7`，SHA256 `A463D39FFAC1B0B25B0537BD830D1BEA8CA89346F1638AD9496970DBCD81007E`，保留原件。相对 v0.6 代码树 `8cd4d09d25a7a6c915656763721a25856df44344` 的新增差异为 `MSP-12-code-v0.7-vs-v0.6.patch`，71063 字节，SHA256 `a27ac1d61a6d54df021c561baa143d50fa85dc7c861e85437bee895245406821`；仅 11 个文件，未扩大产品范围。完整补丁从基准提交、增量补丁从 v0.6 树，在独立临时 Git index 中分别应用，均还原为本版树 `13f6b3a9f930115f78a07dc43267c6306bb03144`，未创建业务提交或改动工作区。

清单 `MSP-12-code-v0.7-manifest.json` 为 54546 字节，SHA256 `e0f060eacee8126e356285f7a70663e70e8e606ffeae11d13186a69214824cef`，列出 150 个文件及 11 项增量。`sha256` 为当前工作区原始字节哈希，`indexSha256` 为补丁还原后的 Git 内容哈希；二者可因仓库原有 CRLF/LF 转换不同，不以换行差异冒充业务差异。按保留 CRLF 的项目风格执行 `git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab,cr-at-eol diff --cached --check` 通过。

历史证据根目录为本任务工作区 `metersphere-msp12/.build/`：v0.6 核心 `mysql-selftest/3f7cb526/suite.log`、原服务 `mysql-selftest/b27df467/native-suite.log`、前端 `frontend-32-current.log` / `frozen-types-final.log` / `frozen-eslint-final.log` / `complete-scope-vite.log`、MCP `complete-scope-mcp.log`。本轮证据另列，不用历史证据替代新增验证。原始日志不整段投递，审查者可按明确路径读取；会话交付本文、完整补丁、增量补丁及清单的 artifact 副本。

文档库归档：MSP 协作文档库 `be213357-80fe-42de-ac85-435aa7d590ca` / 开发文档-草稿 `08f41e9c-54e2-43f5-8453-320790abaaca`。实际上传后的文档 ID、链接、本文 SHA256 由本次交付消息记录；未经用户对本文整版批准不移正式。
