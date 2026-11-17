# MSP-12 开发交付与独立代码审查材料

版本：v0.6；日期：2026-11-04；状态：开发文档草稿，未获用户对本文整版批准。本文不替代产品方案，不代表正式验收或发布通过。

作者/实现：测试开发-埼玉 `c92997d2-06f5-4822-9791-4845858c6b29`。指定独立审查者：左殇 `0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0`；本版审查结论待其实际交付，作者不自行判通过。

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

## 5. 开发自测证据

本轮开发自测结果已前台收集，具备独立代码送审条件；不代表全部 AC 已完成正式验收。最后三处 ESLint 修复只移动相同导入项，没有改变业务逻辑；最终类型和代码规范检查均通过。

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

代码快照：基准 `c625c3d119f341fe0cab3647df453c2fc033dff7`，暂存代码树 `8cd4d09d25a7a6c915656763721a25856df44344`，149 个代码/规则/测试文件，未创建业务提交。代码补丁 `MSP-12-code-v0.6.patch`，974466 字节，SHA256 `e72b0a9f9a3c7a9bcb754b01234271e753a6105efb942b7021529ef07bbe69a9`。代码树不包含本文及历史开发文档；文档单独以版本和交付哈希绑定，避免自引用哈希。

证据根目录为本任务工作区 `metersphere-msp12/.build/`：最终核心 `mysql-selftest/3f7cb526/suite.log`、原服务 `mysql-selftest/b27df467/native-suite.log`、前端 `frontend-32-current.log` / `frozen-types-final.log` / `frozen-eslint-final.log` / `complete-scope-vite.log`、MCP `complete-scope-mcp.log`。代码清单 `review/MSP-12-code-v0.6-manifest.json` 记录逐文件哈希。原始日志不整段投递，审查者可按明确路径读取；会话交付本文、补丁及清单的 artifact 副本。

文档库归档：MSP 协作文档库 `be213357-80fe-42de-ac85-435aa7d590ca` / 开发文档-草稿 `08f41e9c-54e2-43f5-8453-320790abaaca`。实际上传后的文档 ID、链接、本文 SHA256 由本次交付消息记录；未经用户对本文整版批准不移正式。
