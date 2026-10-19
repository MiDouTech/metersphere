# MCP 接口与 AI 技能包统一完善方案

日期：2026-10-19

适用项目：MeterSphere MSP

状态：方案已合并并按可行性评审补充；本专项工程修复及新增能力尚未完成。以下新增名称、字段、迁移和版本均为设计，不表示已上线。

本文件是评审、七类用例操作及 AI 技能包的唯一实施方案，合并原两份方案。保留 R1～R7 和 C1～C8 编号用于需求与验收追踪；不再单独维护用例操作专项方案。


## 1. 目标与边界

先修复 MCP 授权、参数、幂等和返回契约，再补齐评审与新建用例、修改用例、删除用例、附件、执行结果、评论、Excel 导入七类操作，最后同步平台下载的 AI 技能包。远程 MCP、Node MCP 和 Agent HTTP 共用业务契约，结果能在 MSP 现有页面和审计历史核对。

采用现有 Spring 服务、MySQL、对象存储、MCP 注册中心和 Node 适配层，不增加微服务，不重写现有 Excel 解析器。复用现有评审、回收站、导入和执行业务规则。不开放永久删除、任意改写执行或评审结果、代他人审批或绕过质量门禁的旧直写；不实现独立 AI 语义审核，不允许 Agent 修改平台质量策略。

基线来自 2026-10-19 的仓库审计和当时的本地记录，不代表线上已部署版本。授权问题是源码校验缺口，不是已复现的线上越权事件。工程完成以本方案统一验收条件为准。

## 2. 已确认基线

| 项目 | 当前状态 | 依据 |
| --- | --- | --- |
| 评审创建、关联、查询 | 远程 MCP 已注册；Node MCP 已注册创建和关联，查询只有客户端方法 | BuiltinAgentMcpToolConfig、metersphere-mcp/src/index.ts |
| 提交评审结论 | 普通页面有批量评审接口，MCP 未接入 | CaseReviewFunctionalCaseController |
| 评审授权 | Agent 服务未见完整项目归属与用户 RBAC 校验；查询直接按 ID 读取 | AgentCaseReviewWriteService |
| 写工具幂等 | 分发要求幂等键；部分 schema 不声明 requestId；缓存写入位于业务执行后 | AgentMcpStreamableService、AgentIdempotencyService |
| 参数及输出契约 | MCP DTO 转换不等同于 Controller 的 @Valid；project.list 声明数组但实际返回分页对象 | BuiltinAgentMcpToolConfig |
| 旧结果直写 | 旧服务明确拒绝，但工具目录和技能包仍有推荐路径 | AgentFunctionalCaseSubmitService、AgentMcpBundleService |
| 技能包 | 固定 1.0.0；手写目录仅 22 项；缺少当前执行、证据和门禁工作流 | AgentMcpBundleService |
| 当前测试 | 前次审计 Node 构建及 4 项测试通过；后端定向测试无输出后中止，未获得新结果 | 2026-10-19 审计 |

内置配置文件中静态识别的 73 个名称不等同于运行时可用工具数量：其中包含被个人 MCP 禁用的 trigger，另有独立 Handler。新目录必须以实际暴露策略生成，不能直接复制静态名称。

### 2.1 修订基线与外部依赖

本次补充基线为 v3.2，HEAD `5d2bcbc1c70f6ef1024a8ce6e4e9b7f9c9f66652`，同时存在未提交的质量门禁改造。上表是原审计时间点的记录，不是当前完整测试清单。实施前必须登记提交 SHA、工作区差异摘要、工具目录与 schema 摘要、迁移版本、Node/技能包版本和实际服务制品，不得只记录 HEAD 而忽略未提交实现。

质量门禁依赖以 [质量门禁修复实施记录](summary/质量门禁修复实施记录-20261019.md) 为依据：本地已有冻结事实、回写恢复、v2 断言和结果只读查询等变更，但仍为部分完成；v2 创建默认关闭，Runner 镜像及完整受管执行尚未全部验收。C 批次不得把“源码存在”当成稳定依赖，也不能把该记录中的策略页面测试当成执行链路验收。接口契约以批次 0 固定的实际版本为准，后续依赖变化必须重新跑受影响契约测试。

## 3. 需求追踪与验收

### 3.1 共享能力与评审需求

| 编号 | 需求及验收条件 | 前端或客户端入口 | 后端实现 | 数据及配置 | 验证 |
| --- | --- | --- | --- | --- | --- |
| R1 | Scope、用户 RBAC、项目白名单与资源归属同时成立 | MCP、Agent HTTP；现有 Token 配置页 | 统一评审及用例访问校验服务 | 拟新增评审读取、提交与导入 Scope | 跨项目、撤权、禁用用户、只读角色拒绝 |
| R2 | 相同写请求重复或并发只产生一次业务副作用 | 两种 MCP 客户端 | 原子幂等占位及业务事务 | 扩展幂等记录状态与索引 | 并发、崩溃、同键异参、响应丢失 |
| R3 | schema 与实际输入输出一致 | tools/list、tools/call、Node adapter | 统一参数验证、显式响应 DTO | 工具契约版本 | schema 正反例与真实序列化结果验证 |
| R4 | 创建、关联、提交评审结论并查询结果 | MCP；结果在现有评审页可见 | 复用评审业务服务 | Scope 与必要审计关联字段 | SINGLE、MULTIPLE、重复、归档及无权限场景 |
| R5 | 技能包指引能完成当前合法流程 | AI 技能包下载页 | BundleService 与共享目录 | 技能包版本、兼容版本、校验和 | ZIP 内容、工作流、无密钥、目录一致性 |
| R6 | 旧接口明确拒绝且提供迁移信息 | 旧客户端 | 保留拒绝逻辑和安全错误 | 弃用状态及迁移说明 | 旧工具不可绕过门禁 |
| R7 | 用户能定位失败并安全重试 | MCP 错误及现有页面 | 统一错误码、traceId、审计 | 错误码表与日志字段 | 业务失败、网络、500、恢复重试 |

所有需求当前状态均为“待实施／待验收”，不得用此表表示功能已完成。

### 3.2 七类用例操作与客户端需求

| 编号 | 需求 | 现有入口与实现 | 数据或配置变更 | 验收要求 |
| --- | --- | --- | --- | --- |
| C1 | 单条和批量创建 | 远程 case.create/batch_create、Node 创建工具 → AgentCaseWriteService → FunctionalCaseService | 来源审计；批量结果关联 | 模板、模块、字段、部分失败及重试一致 |
| C2 | 局部修改 | 远程 case.update → AgentCaseManageService；Node 缺失 | 原子版本控制，必要时增 revision | 非修改字段不丢失，并发不覆盖 |
| C3 | 删除到回收站 | 普通页面有删除；MCP 未实现 | 删除审计和确认摘要 | 明确版本范围，不永久删除，不影响范围外数据 |
| C4 | 附件上传与关联 | HTTP 临时上传 + 远程 attachment.attach/list/delete；Node 仅旧执行上传 | 上传主体、用途、消费状态约束 | 同一客户端完整上传、关联、查看和取消关联 |
| C5 | 执行结果 | 旧 submit 拒绝；任务、步骤、证据链路已有 | 复用尝试绑定及门禁数据 | 真实执行链路完成后再回写，可核对失败原因 |
| C6 | 评论和回复 | 远程 comment.create/comments.list 直接调用普通服务；Node 缺失 | 复用评论与附件关系，补审计 | 项目、父评论、附件及通知对象校验 |
| C7 | Excel 导入 | 页面模板、pre-check/excel、import/excel；MCP 未实现 | 文件登记、预检快照、任务及逐行结果 | 预检与提交同文件同规则，覆盖需明确授权 |
| C8 | 使用说明和双客户端一致 | 技能包及 Node 能力落后 | 工具契约与包版本 | 从技能指引完成七类路径且无失效工具推荐 |

实施状态：C1、C2、C4、C5、C6 有部分代码，尚缺本专项完整验收；C3、C7 的 MCP 能力待新增；C8 待更新。

R1～R3、R7 对全部 C 类操作生效；R4 覆盖评审，R6 与 C5 共同约束执行结果，R5 与 C8 为同一技能包交付，不重复计量。

## 4. 统一授权与接口契约

### 4.1 共享授权入口

拟新增 AgentCaseReviewAccess 和用例访问校验组件，或按项目现有模式实现等价的共享服务。MCP 和 Agent HTTP 必须调用同一应用服务，不依赖普通页面 Controller 上的注解完成授权。

每次操作按顺序验证：Token 有效和操作 Scope；绑定用户有效；用户对目标项目的权限；Token 项目白名单；评审真实所属项目；用例、模块、附件和评审人是否在允许范围。请求中的 projectId 仅为选择条件，不能作为资源归属证明。

查询先加载资源所属项目再授权，未授权与不存在返回一致的安全提示，避免泄露资源存在性。关联操作要求评审、用例与目标项目一致，拒绝已删除用例及无效评审人。评审关联和评审结论批量提交先校验全部对象再写入，第一版采用全有或全无事务语义；批量创建与 Excel 导入采用第 5 节的逐项事务规则。

全量检查其他 MCP 写工具是否只校验 Scope；按统一矩阵逐项修复，不能仅修评审后宣称所有 MCP 都已安全。

### 4.2 Scope 与兼容

拟新增 REVIEW_READ、REVIEW_SUBMIT；保留 REVIEW_WRITE 用于创建和关联。所有 Scope 仍与用户 RBAC 取交集。

- 读取：REVIEW_READ 或 REVIEW_WRITE；一个明确的兼容周期内接受旧 FUNCTIONAL_READ，但仍执行完整评审读取权限与项目检查。
- 创建、关联：REVIEW_WRITE 加对应业务权限。
- 提交结论：REVIEW_SUBMIT 加评审权限及当前用户作为评审人的资格；不因 REVIEW_WRITE 自动获得审批能力。
- AGENT_ALL 保留已授权能力范围内的总授权语义，不绕过用户权限、评审人资格或业务状态规则；新增高风险能力按下述授权版本规则处理，不能依靠旧解析器的无条件放行。

同步更新已知 Scope 集合、解析器、Token 创建与编辑界面、国际化、服务端校验和测试。旧 Token 不批量扩大授权，不直接重写生产 Token。

沿用 CASE_WRITE、CASE_UPDATE、CASE_DELETE、CASE_COMMENT、CASE_ATTACHMENT、FUNCTIONAL_READ。拟新增 CASE_IMPORT；覆盖导入还需 CASE_UPDATE 及用户对应编辑权限。执行使用现有任务和证据 Scope，不借用 CASE_UPDATE 修改执行结果。新增 Scope 同步更新集合、解析器、Token 配置页面、国际化和测试。

#### 4.2.1 旧 Token 的授权兼容决策

本期选择“旧 Token 不自动获得新增高风险能力”。现有解析器对 AGENT_ALL 直接放行，必须先修正此行为再开放新工具。拟引入 `authorizationContractVersion`，历史空值按 legacy 处理；通过增量字段或等价的可审计授权记录实现，不更改 Token 明文。

|授权情况|REVIEW_SUBMIT / CASE_IMPORT 的结果|
|---|---|
|旧 Token 仅有 AGENT_ALL 或其他总 Scope|不自动纳入新增能力，即使用户 RBAC 足够也拒绝|
|用户显式编辑 Token，增加对应新 Scope|重新校验授权资格，保存授权版本及变更审计后允许；仍受项目/RBAC/业务状态限制|
|用户显式确认新版 AGENT_ALL 范围|记录新版授权范围、确认时间与操作者后生效；未来新增敏感能力仍需新的版本决策|
|未绑定有效用户、用户禁用或已撤权|无论 Scope/授权版本均拒绝|

总 Scope 的展开使用版本化能力清单，不能把所有未来新增 Scope 自动纳入。历史 FUNCTIONAL_READ 读取评审的兼容入口限定为首个新契约服务端发布后的 90 个自然日，发布清单登记实际起止日期；到期服务端拒绝并给出迁移提示。允许提前迁移，不允许因只更新技能包而无限延期。该兼容仅影响读取，不扩大提交权限。

### 4.3 参数与错误协议

tools/call 统一验证 inputSchema；DTO 转换后执行 Bean Validation。处理必填、空白、空数组、枚举、长度、批量数量、未知字段和字段类型。业务层继续验证资源关系及状态，不把 schema 当作业务授权。

所有写工具显式声明必填 requestId，统一长度与格式约束；保留 HTTP 幂等头作为兼容入口，头和参数均存在但不一致时拒绝。JSON-RPC id 只用于响应关联，不能替代 requestId。

按工具声明输出 DTO，修正 project.list 等返回结构。业务失败按工具错误返回安全的 code、message、details 和 traceId；协议解析错误与未知方法保留 JSON-RPC 错误。兼容适配器必须同时识别两类错误，避免将失败包装为成功。

Node 旧 HTTP 包装器不得直接展示原始 body 或异常堆栈。业务字段错误保留用户输入，未知错误显示安全提示并提供 traceId。

### 4.4 双客户端与页面对应

Node 新工具优先适配远程 MCP 业务契约，避免另建业务逻辑。二进制上传仍走受授权 HTTP，客户端读取本地文件后上传，不把本机路径交给远程服务器读取。

沿用现有页面入口：用例列表／详情／附件／评论、回收站、导入结果、执行详情与计划执行历史。只补缺失的来源、错误、异步状态和版本信息；不新增重复业务页面。用户界面处理 loading、empty、success、validation-error、permission-denied、network-error、server-error。

## 5. 幂等与并发设计

幂等键作用域为 tokenId + operationId + requestId。operationId 是服务端定义的稳定业务操作标识，不是客户端传入值，也不是工具别名或 HTTP 路径。请求摘要使用递归规范化 JSON：对象键排序，数组保留原顺序，排除 requestId 和该操作明确列出的传输元数据；按业务语义规范化，避免把有意义的数组顺序抹掉。规范化算法版本必须持久化。

在现有幂等表基础上设计增量迁移，先核查现有唯一约束、字段类型和清理任务。拟增加状态、请求摘要、创建／完成时间及必要的结果引用。历史完整响应记录映射为 SUCCEEDED；历史空响应不得直接认定成功或自动重放，进入待核对状态。

单条写操作及要求全有或全无的评审批量操作采用以下流程：

1. 在业务事务内插入唯一幂等占位，先占位再执行业务。
2. 相同键已完成且摘要相同：重新检查当前授权后返回原结果；不同摘要返回 IDEMPOTENCY_CONFLICT。
3. 同键正在执行：等待受控时间或返回 REQUEST_IN_PROGRESS，不能启动第二次业务执行。
4. 业务写入、审计和成功响应记录同事务提交；业务失败回滚业务写入。
5. 提交成功但响应丢失：相同键重试返回已保存结果。提交状态不明时先查记录，不盲目重放。

复用评审服务前必须核验其批量 SqlSession、事件和通知是否加入同一事务。涉及邮件、消息队列等外部副作用，采用事务 outbox 或业务事件唯一键消费去重；不能以数据库记录唯一就声称外部副作用只执行一次。

过期清理不得删除仍在执行或提交状态不明的记录。旧请求超过保留期的重试语义须在文档明确说明。回放缓存前重新做用户 RBAC、项目与资源权限校验，不能只重新检查 Token Scope。

#### 5.0.1 跨入口统一操作与事务边界

远程 MCP、Node MCP、Agent HTTP 必须调用同一带授权与幂等的应用入口。远程分发层不再在业务执行后另存一份成功缓存；直接 HTTP 调用也不能绕开占位。Node 仅传递 requestId 和业务参数，不实现另一套服务端幂等。

批次 0 为所有写操作建立映射表，至少包含 operationId、远程工具名/历史别名、Node 名称、HTTP 方法/路径、授权策略、幂等类别、摘要字段、响应保留期和事务负责人。例如三个客户端的创建评审均映射至 `case_review.create`；同一 Token、同一 requestId 从另一入口重试仍只有一次创建。不同业务操作不会因 requestId 相同互相冲突。

既有 `toolName` 幂等记录通过明确别名表映射，先检测归并后的重复键及摘要冲突；存在冲突进入核对状态，不静默覆盖。不能把“改名”或“换 HTTP 入口”当作允许重新执行的理由。跨 Token 不自动去重，客户端轮换 Token 后须先查询业务结果，不能承诺继续复用原幂等键。

同步占位与业务同事务时，其他连接可能只能等待未提交唯一键锁，而看不到 RUNNING 行；因此需设置锁等待上限并映射为可重试错误，失败后在新事务重新读取提交结果，禁止在失效事务中继续写入。异步批次的占位先单独提交，随后按第 5.2 节领取。失败是否可用原键重试及历史保留期必须逐操作声明；超过保留期不得无提示重新执行删除、评审提交等高风险操作。

#### 5.0.2 幂等分类与敏感返回值

|操作类别|保存与重试规则|
|---|---|
|普通资源写入、评审批量|保存安全结果 DTO 或不可变结果引用；业务、成功记录与必要审计同事务；回放重新授权|
|任务创建/认领、租约续期/释放/完成|调用现有执行状态机的原子操作；按租约和尝试判断有效性，不用通用缓存绕过校验；续期不能因为固定旧键永久返回旧到期时间|
|步骤事实提交|复用现有 executionId/stepId/requestId、payloadHash 及冻结检查；不得再套一层会提前返回的通用成功缓存|
|上传授权、下载授权|保存资源与授权记录引用，不无限缓存临时 URL/请求头；过期后重新授权签发，不能重新创建业务关联|
|异步批次/导入|返回稳定 batchId/importId 与状态查询方式，执行过程使用逐项结果与工作进程租约|

leaseToken、上传凭证、临时签名 URL 和必要请求头视为敏感数据，不进入明文通用响应缓存、审计或技能包。确需支持认领响应丢失重取原凭证时，使用专用加密记录，限定原 Token/租约/有效期，记录密钥标识并支持保留历史解密密钥；仅存摘要无法恢复原明文。过期或已结束租约只返回安全状态及查询入口，不返回旧凭证，也不自动新建执行尝试。

摘要字段逐操作冻结：排除 leaseToken、Authorization、traceId 等传输字段；执行身份、预期版本、业务内容和用于判定的采集时间必须纳入。持久化的摘要不能代替当前租约/授权检查。验收包括轮换传输凭证、敏感缓存检查、旧租约重放、过期 URL 重新授权及业务事实变更冲突。

### 5.1 部分成功批次和异步导入

批量创建与 Excel 导入不使用一个覆盖整批的长事务。先持久化带请求摘要的批次占位，再以批次 ID + clientItemId（导入使用行标识）唯一约束逐项处理，每项业务数据、审计及行结果同事务提交。批次聚合响应单独持久化；进程中断恢复时按逐项结果跳过已提交项，不能整批重放。

批量结果返回 total/succeeded/failed/notProcessed 及逐项资源 ID、安全错误和重试建议。同键已完成返回原结果，包括部分失败；执行中返回批次 ID 和可查询状态。修正后仅对失败或未处理项使用新 requestId，不能重新提交成功项。failFast 仅停止后续处理，不回滚已经成功的项。

### 5.2 异步任务领取、恢复与撤权

批量创建和 Excel 导入共用持久化工作协议，不依赖进程内队列保证可靠性。任务记录至少包含 status、workerId、leaseUntil、heartbeatAt、fencingVersion、attemptCount、nextRetryAt、授权主体/Token 引用、项目及契约版本。字段为设计名称，不能存 Token 明文。

1. 工作进程通过条件更新领取 QUEUED 或租约已过期的 RUNNING 任务；每次领取递增 fencingVersion。心跳续约需匹配 workerId 和版本。
2. 每项事务先锁定/校验当前任务拥有者、fencingVersion、租约和当前授权，再提交业务、行结果和幂等记录。旧工作进程即使恢复也不得提交。接管与行提交使用同一锁顺序，避免先检查后无保护写入。
3. 进程重启扫描可恢复任务，已提交项仅回读，不重新执行；聚合计数从持久化行结果计算，不依赖内存进度。空批次提交在入口拒绝。
4. 网络/存储暂态错误允许退避重试；默认自动重试最多 5 次，退避从 5 秒起指数增加、上限 5 分钟，均可配置。参数、授权和版本冲突不自动重放。具体租约时长/心跳间隔由批次 0 的最长单项事务验证确定并登记，不以超时自动判定事务未提交。
5. Token 撤销、用户禁用或权限丢失时停止后续项，已成功项保留。授权与版本冲突的未处理项通过新预检/新请求恢复；不得用管理员后台身份继续原用户操作。保留 NOT_PROCESSED 及稳定原因。
6. 耗尽重试或提交状态不明时记录 recoveryRequired 和原因，提供受授权的任务查询与恢复入口；状态不明项先核对幂等/行记录，确认未提交后才重试。不得直接将全部 RUNNING 改为 QUEUED。

状态主线为 QUEUED → RUNNING → SUCCEEDED / PARTIAL_FAILED / FAILED；重试、暂停恢复原因另行持久化，不把失败清空后伪装全新执行。清理任务不得删除持有效租约、待核对或仍引用文件的记录。必须测试双实例抢占、心跳丢失、接管后旧工作进程提交、逐项提交期间撤权以及聚合更新前崩溃。

## 6. 评审闭环接口设计

### 6.1 工具与 HTTP 映射

以下新增名称为拟定契约，实施前通过契约测试固定。

| 远程 MCP | Node MCP | Agent HTTP | 说明 |
| --- | --- | --- | --- |
| metersphere.case_review.search（新增） | search_case_reviews（新增） | GET /api/agent/v1/case-review/search | 分页、项目和名称筛选 |
| metersphere.case_review.get（现有） | get_case_review（新增注册） | GET /api/agent/v1/case-review/{id} | 返回版本与评审统计 |
| metersphere.case_review.cases（新增） | list_case_review_cases（新增） | GET /api/agent/v1/case-review/{id}/cases | 返回关联用例、本人及汇总评审结果 |
| metersphere.case_review.create（现有） | create_case_review | POST /api/agent/v1/case-review/create | 支持创建时关联，补 requestId |
| metersphere.case_review.associate_cases（现有） | associate_case_review_cases | POST /api/agent/v1/case-review/associate-cases | 返回实际新增、已关联及最终数量 |
| metersphere.case_review.submit（新增） | submit_case_review（新增） | POST /api/agent/v1/case-review/submit | 提交当前用户结论，不直接覆写最终状态 |

第一版不增加删除评审、代他人审批、强制全员通过、任意状态修改和静默解除关联能力。

### 6.2 提交参数与业务规则

拟定请求示例，ID 与 requestId 为示例值：

```json
{
  "projectId": "project-internal-id",
  "reviewId": "review-id",
  "caseIds": ["case-id-1", "case-id-2"],
  "decision": "PASS",
  "comment": "已核对步骤、预期结果和覆盖范围",
  "expectedVersion": "version-from-review-get",
  "expectedDecisionVersions": {"case-id-1": 0, "case-id-2": 0},
  "requestId": "review-submit-unique-id"
}
```

初版 decision 仅支持 PASS、UN_PASS，批量上限 100，comment 必填。字段限制同步写入 schema、DTO 和客户端。reviewPassRule、评审人身份及最终结果均从服务端读取，禁止调用方覆盖。expectedVersion 采用服务端可原子比较的版本；如现有更新时间不足以覆盖关联和评审人变更，应增加专用 revision 字段。

提交时锁定或乐观检查评审及关联版本；SINGLE 复用现有单人规则，MULTIPLE 保留每人历史并按现有规则汇总。即使调用者为管理员，MCP 初版也不提供隐式“代全部评审人通过”模式；适配现有服务时须避免管理员快捷路径改变这一契约。

评审计划状态是 PREPARED、UNDERWAY、COMPLETED、ARCHIVED；PASS 是用例评审结果。计划推进由现有业务规则决定，不增加“把计划改成 PASS”的接口。

归档评审、未关联用例、无评审资格、版本冲突均明确拒绝。重复同键不得重复写历史；不同键的改判按现有业务规则追加历史，不覆盖旧结论。若业务不允许当前阶段改判，则返回明确冲突，不自动重开评审。

响应返回本人提交结果、各用例最终汇总状态、计划状态、剩余待评审数、版本与 traceId。多人尚未全部通过时，必须明确显示仍在评审，不能返回含糊的“全部通过”。

审计保留真实操作者、Token 标识、来源 MCP、requestId、前后状态与原因，不记录 Token 明文。现有评审页和历史页面能读取这些结果；仅新增来源标签或必要错误状态，不新建重复管理页面。

### 6.3 评审版本粒度与现有服务适配准入

计划配置与个人结论采用不同并发边界：`expectedVersion` 对应评审配置/关联版本，覆盖评审规则、评审人、关联增删及可提交阶段变化；个人结论使用 `(reviewId, caseId, reviewerId)` 范围的 `expectedDecisionVersion`，首次提交为 0。批量提交通过必填 expectedDecisionVersions 映射为 caseIds 中每个用例提供该版本，键集合须与 caseIds 完全相同；获取接口同步返回。普通的其他评审人提交不递增配置版本；终结/归档等影响可提交性的状态变化必须递增并重新检查。

同一事务按评审主记录、关联用例 ID 排序、个人结论记录排序的顺序加锁；汇总从事务内有效的每人最新结论计算，保持历史追加。不同评审人对不同用例的合法提交可先串行处理，但不能仅因他人提交普通结论被无意义地拒绝。相同个人结论的竞争、配置变化、归档和最后两人同时提交必须产生确定结果。

B 批次启动前验证现有 `batchReview` 的批量 SqlSession、主事务、历史及通知边界。管理员若是指定评审人，只提交其本人结论；不是指定评审人则拒绝。不能依赖现有管理员快捷分支。若无法通过事务/身份试验，先抽出共享的“本人提交”服务，再接 MCP，不复制另一套汇总规则。第 6.2 节示例已包含逐用例个人结论版本；实际 schema 须同步约束键集合、非负整数及批量数量。

## 7. 七类用例操作设计

### 7.1 工具与接口映射

表中“新增”是计划项。已有路径保持兼容；最终名称在契约测试中固定。

| 功能 | 远程 MCP | Node MCP | 后端路径或服务 |
| --- | --- | --- | --- |
| 创建 | metersphere.functional.case.create | create_functional_case | 现有 /api/agent/v1/functional/case/create |
| 批量创建 | metersphere.functional.case.batch_create | batch_create_functional_cases | 现有 /api/agent/v1/functional/case/batch-create |
| 修改 | metersphere.functional.case.update | update_functional_case（新增） | AgentCaseManageService，共享 MCP 入口 |
| 删除影响预检 | metersphere.functional.case.delete_preview（新增） | preview_functional_case_delete（新增） | 拟新增删除应用服务 |
| 删除到回收站 | metersphere.functional.case.delete（新增） | delete_functional_case（新增） | 复用既有回收站业务语义 |
| 附件上传授权 | metersphere.functional.attachment.prepare（新增） | upload_functional_attachment（新增，封装上传） | 拟新增短期上传授权；复用临时附件存储 |
| 附件关联／列表／删除 | 现有 attachment.attach、attachments.list、attachment.delete | 对应三个新增 Node 工具 | AgentFunctionalCaseAttachmentService |
| 评论创建／查询 | 现有 comment.create、comments.list | create_functional_comment、list_functional_comments（新增） | 授权适配层 → FunctionalCaseCommentService |
| 执行结果 | 现有 task.claim、execution.step.submit、artifact.prepare/commit、execution.complete、writeback.status.get 等 | 补齐对应适配，具体以运行时目录为准 | 现有受控执行应用服务 |
| Excel 模板 | metersphere.functional.import.template（新增） | get_functional_import_template（新增） | 复用模板导出服务，返回受授权文件引用 |
| Excel 文件上传 | metersphere.functional.import.prepare（新增） | upload_functional_import_file（新增） | 拟新增导入文件登记与 HTTP 上传 |
| Excel 预检／提交／查询 | metersphere.functional.import.precheck/commit/get（新增） | precheck/commit/get_functional_import（新增） | 导入应用服务 → FunctionalCaseFileService |

批量删除暂不作为第一批必要项；单次明确对象和版本范围后再扩展，避免同时引入过多破坏性操作。

### 7.2 七类操作的实现设计

#### 7.2.1 新建用例

复用 AgentCaseWriteService，新增前置校验：moduleId、parentId、templateId 的归属和可用状态；名称、步骤、必填自定义字段；模块路径节点长度、层级和总批量数量。字段与模板使用服务端定义，未知字段不得静默丢弃。

第一版批量上限建议 100 条，配置和 schema 保持一致。增加 clientItemId，明确默认允许部分成功；failFast=true 只表示停止后续，不表示回滚已经成功的项。每项独立事务，公共授权与批次幂等先完成；内部调用需使用独立 Bean 或显式事务，避免 Spring 自调用事务失效。结果记录未处理项。

用例生成来源记为 MCP，并保留真实发起用户及 Token 标识。核查 aiCreate 字段实际业务含义后再决定赋值，不能为了标记来源随意改变审批或生成流程。默认值和自定义字段合法性错误使用安全提示。

#### 7.2.2 修改用例

定义严格 patch DTO：name、moduleId、prerequisite、description、tags、steps、priority、customFields。列明每个字段的省略、不为空、null 清空语义；空 patch、未知字段、非法模块和模板字段值直接拒绝。不得允许 patch 携带执行状态、评审结果、创建人或所属项目。

expectedVersion 必填。优先复用能覆盖用例正文、步骤及自定义字段变更的现有版本机制；不足时增加 revision。更新用例主记录、Blob 和字段前原子比较版本，或在同事务加锁比较，不能继续只做事务外读后比较。所有页面及导入写路径也必须维护该版本，防止只约束 MCP 而漏掉其他写入。

保留未修改内容、附件和自定义字段。复用既有重新评审逻辑，响应说明是否触发重新评审。模块变更若影响同一用例其他版本，必须在契约和预览中披露，并验证所有受影响版本；不得宣称仅更新单个版本。

#### 7.2.3 删除到回收站

不得直接透传现有 FunctionalCaseDeleteRequest 的 deleteAll=true 默认值。新工具显式使用 versionScope=CURRENT 或 ALL；第一版默认 CURRENT，ALL 只有客户端明确传入且预检列出所有版本后才能执行。

delete_preview 返回用例及版本列表、计划／评审／资产关联影响、是否可恢复、预检版本与短期 previewId。delete 携带 previewId、expectedVersion、reason、confirm=true、requestId，执行前再次授权及核对摘要；变化时返回冲突并重新预检。

复用回收站和既有关联处理，不直接物理删表。对正在执行、不可删除状态或存在禁止删除的关联明确拒绝。删除成功后页面回收站可见，历史执行证据仍可追溯。恢复入口沿用页面；永久清除不在本期。用户已明确授权的删除可由客户端完成确认参数，不强制增加重复人工确认步骤。

#### 7.2.4 上传与关联附件

明确区分 CASE_DETAIL、CASE_COMMENT 与任务执行证据；旧执行附件上传不作为用例详情附件或新任务证据的替代。

远程工具 prepare 返回 uploadId、受授权上传地址、必要请求头、用途、大小限制和过期时间；二进制通过 HTTP 上传，服务端校验实际字节数、文件类型、摘要和上传主体。Node 工具负责本地文件读取和上传，但不将本地路径写入审计或向远程工具传送。

默认临时文件仅允许上传用户／Token 使用；如果产品需要同项目共享，应另有显式授权，不能因为知道 attachmentId 就可消费。上传、关联、下载均重新检查项目访问权。用途、过期、已消费检查必须服务端执行。

附件状态采用上传中／可用／已关联／过期的明确语义。关联使用数据库条件更新原子占用并与关联事务一致提交；对象存储写成功但事务失败的文件由补偿清理处理。重复同键返回原关联结果，不再次消费。

删除只移除目标用例附件关联；共享文件有其他引用时不得删底层对象。附件 ID 与 associationId 明确区分，拒绝模糊回退到任意文件 ID。列表返回可用于安全操作的 associationId。

#### 7.2.5 执行结果与状态

不新增 set_status=PASS 一类工具，也不解除 QUALITY_LEGACY_SUBMIT_FORBIDDEN。提供完整使用链：定位计划与用例 → 创建／认领个人任务 → 获得租约与冻结合同 → 准备、上传及提交证据 → 提交实际观察值 → 完成任务 → 查询门禁及正式回写状态。

复用当前支持的合同版本和 typed actual 校验，工具目录和技能包不得写死旧版断言格式。步骤提交不能由客户端覆盖 expected、operator、策略版本或最终判定。

Node 补齐任务及证据适配，不伪装旧 submit 已兼容新链路。状态明确区分步骤已接收、任务结束、门禁判定及回写结果；失败、阻塞、租约过期、响应丢失分别提供查询或重试方式。响应丢失优先读取已持久化结果，不能直接重放有副作用的执行动作。

#### 7.2.6 评论与回复

新增 Agent 评论适配服务，先验证项目、用例未删除、操作权限，再调用既有评论服务。查询也须验证同样的资源读取边界，不仅检查 FUNCTIONAL_READ Scope。

回复必须验证 parentId 属于当前用例；replyUser、notifier 仅允许合法且可访问的用户。评论附件通过 CASE_COMMENT 临时文件流程验证并转换为真实文件 ID，不接受未经核验的 uploadFileIds。增加内容长度、空白和富文本安全处理，防止显示时执行不可信内容。

评论写入、附件关系、审计同事务；通知在提交后发送并去重。Node 补创建、回复和列表。修改／删除评论作为后续能力，不混入本期创建工具。

#### 7.2.7 Excel 导入

复用既有模板、预检和导入解析器，但新增 MCP 应用层 DTO。现有 FunctionalCaseImportRequest 的布尔 cover 使用 @NotBlank，类型不匹配；接入前修正为适当约束，并补覆盖普通页面的回归。客户端 count 不可信，行数由服务端计算；workspaceId 从授权项目解析。

第一阶段完整交付只新增模式 CREATE_ONLY，禁止覆盖；第二阶段单独交付 UPDATE_MATCHED。不能把 JSON 批量创建或本地解析脚本宣称为完整 Excel 文件导入。

流程：

1. 读取项目导入模板与字段定义，上传文件到专用用途的临时存储，绑定用户、项目和文件摘要。
2. precheck 解析文件，返回总行数、可导入数、错误行及字段、重复项、预计新增／修改数、字段映射和 previewId；不写业务用例。
3. 预检快照绑定文件摘要、模板版本、模块、映射、重复处理模式和目标用例版本，设置过期时间。
4. commit 仅提交 previewId、requestId 及明确覆盖授权；服务端重新检查权限、文件、快照和目标版本，变化则拒绝并重新预检。
5. 导入任务持久化后返回 importId，由 get 分页查询逐行结果及计数；Node 超时后查询任务，不重新导入。

导入工作复用现有解析／持久化逻辑，但须确认能否提供逐行可重入事务。若不能，先重构适配边界，再开放异步提交，不能在异步线程中直接复用依赖已失效 Web Session 的方法。持久化操作者和项目上下文，并在任务执行前重新授权。

每行以 importId + 行标识唯一记录，业务写入与行结果同事务，崩溃恢复不重复创建。任务状态区分 QUEUED、RUNNING、SUCCEEDED、PARTIAL_FAILED、FAILED；批次不宣称全局原子，成功行保留，失败行可另起受控重试。

CREATE_ONLY 默认拒绝待处理重复项，不按名称自动覆盖。UPDATE_MATCHED 只使用稳定用例 ID，必须有更新权限、显式模式、预检列出的目标及版本保护；名称相同不构成匹配依据。所有行均校验项目和模块。

明确文件大小、支持格式、最大行数及解析超时，沿用可验证的现有上限，必要时配置独立导入上限。拒绝不支持的宏文件、损坏／加密文件；不执行公式或外部链接。错误报告按文本输出用户内容，避免导出时触发表格公式执行。

#### 7.2.8 Excel 实施前技术验证

当前 `FunctionalCaseFileService.importExcel` 通过 Listener 解析并写入，返回汇总结果；不能把该方法整体放进异步线程就宣称支持逐行恢复。E 批次前必须证明能拆成“只读解析与规范化 → 冻结导入条目 → 逐项事务写入 → 汇总结果”，并保持现有页面导入行为。

第一版固定单工作表规则：模板指定的工作表用于导入，其他非空工作表明确拒绝，不能静默忽略。预检记录 sheet 标识、模板/解析器版本、物理起止行和逻辑条目 ID。合并单元格/多步骤占多行时，一个完整逻辑用例为一个提交单元，所有相关物理行映射到同一条目；`importId + itemId` 唯一约束不得简单替换成每个物理行一次创建。重复项识别范围覆盖文件内与项目现存数据，预检后新增的重复数据在提交时仍校验。

在文件大小、行数与解析超时之外，增加 ZIP 解压后总量/条目数、单元格长度、列数、合并区域数量与内存预算；所有限值在批次 0 根据当前解析库与隔离压测固定为配置、schema/说明和测试断言，不留下“建议值”作为上线参数。解析超时必须能终止资源消耗并清理临时资源，不能仅让 HTTP 请求返回超时而后台继续解析。预检成功冻结规范化条目及摘要，提交不得用另一套解析规则重新解释同一文件。

### 7.3 参数示例

以下均为拟定契约，不能直接视为已上线接口。

```json
{
  "tool": "metersphere.functional.case.update",
  "arguments": {
    "projectId": "project-id",
    "caseId": "case-id",
    "expectedVersion": "version-from-get",
    "patch": {"name": "手机号登录失败提示"},
    "reason": "明确验证范围",
    "requestId": "unique-case-update-request"
  }
}
```

```json
{
  "tool": "metersphere.functional.import.commit",
  "arguments": {
    "previewId": "preview-id",
    "requestId": "unique-import-request"
  }
}
```

第二个示例仅提交已冻结 CREATE_ONLY 预检，不能通过 commit 临时切换为覆盖模式。

## 8. AI 技能包更新

### 8.1 目录与版本

保留平台 ZIP 下载能力，拟发布 1.1.0；在发布时确认最终版本。新增 minimumServerVersion 或能力契约版本、发布日期及变更记录。版本变化须与内容变化对应，不能持续覆盖同一 1.0.0 名称。

将“是否对个人 MCP 暴露、是否弃用、Scope、schema、版本”统一到工具描述来源，供 tools/list、技能包目录和契约测试共同使用。下载包是通用说明，不缓存用户授权；客户端启动时以 tools/list 确认可用能力。trigger 等禁用能力不作为可调用工具推荐。

### 8.2 文件更新范围

| 文件 | 更新内容 |
| --- | --- |
| SKILL.md | 补 name/description 元数据，增加读取 references 的指引，清晰区分测试／生产环境 |
| references/tools.md | 从可暴露工具目录生成分组、参数、Scope 和弃用说明 |
| references/permissions.md | 用户权限交集、评审及导入 Scope、白名单和兼容期 |
| references/workflows.md | 项目定位、评审闭环、七类用例操作及任务认领到回写核对的完整操作顺序 |
| references/troubleshooting.md | 门禁、幂等、版本冲突、权限、限流及失败恢复 |
| manifest.json、CHANGELOG.md | 包版本、契约版本、兼容平台与迁移说明 |
| examples/* | 按各客户端实际配置格式维护，不将通用 JSON 当作所有客户端可直接导入的格式 |
| scripts/verify-mcp-connection.js | initialize、tools/list、必要能力检查、非零失败退出码；默认只读 |
| checksums.txt | 覆盖最终文件内容，构建后验证 |

不把未实施的评审 submit 写成已可用。可先发布纠错版，删除旧直写推荐、补当前质量门禁和幂等说明；评审新工具上线后再发布对应能力版本。

旧结果直写接口继续明确拒绝，不因回滚技能包而重新放开。正常工具发现中隐藏弃用直写工具，保留旧名称调用时的明确迁移错误。新执行流程必须说明：先发现能力与检查前置条件，再创建／认领任务，绑定执行尝试，准备与提交证据，提交 actual，完成后读取门禁及回写状态。步骤“提交成功”不等于最终用例通过。

客户端示例在实施时使用对应客户端版本实际验证；自检脚本声明所需 Node 版本，技能正文使用远程 MCP 本身不应被误写为必须安装 Node。禁止把令牌、生产用户或业务数据打包到示例。

各批次同步标注删除版本范围、附件用途、批量部分成功、Excel 新增／覆盖模式及重试方式。未上线的评审提交、删除或导入工具仅出现在规划中，不进入已可用工具目录。

## 9. 数据与文件变更计划

优先复用用例、Blob、自定义字段、回收站、评论、附件、执行任务及历史表。按实际 schema 缺口新增幂等约束、用例及评审 revision、上传凭证／消费约束、删除预检及 Excel 导入预检／任务／行结果记录。迁移编号实施时检查仓库，不预占现有编号。

需检查：历史版本初始化、所有写入口维护 revision、临时文件清理、任务索引及结果保留期。导入原文件和错误报告采用项目授权下载，不进入技能包或公开日志。

拟涉及目录：

- backend/services/agent-integration：评审与用例写入／管理、共享访问校验、附件、评论、导入适配、MCP 工具、Scope、安全错误及测试。
- backend/services/case-management：原子更新、回收站、Excel 解析适配、必要事务边界，保持现有页面兼容。
- backend/framework/domain：增量迁移、Mapper 和索引。
- metersphere-mcp/src/tools、src/client.ts、test：对应工具、上传与异步查询、契约测试。
- frontend：Token Scope、来源及结果展示、导入任务结果与必要错误状态、真实 E2E。
- AgentMcpBundleService、references 和项目文档：工具目录、七类工作流、版本、评审流程和迁移说明。

## 10. 统一实施批次

| 批次 | 交付范围 | 依赖及出口条件 |
| --- | --- | --- |
| 0 | 契约、兼容矩阵、状态机、评审与导入技术验证 | 按 10.1 节固定设计与可复现试验，先于 A |
| A | 统一授权、原子幂等、输入输出契约、安全错误；修复创建／修改／评论；技能包紧急纠错 | 项目隔离、并发、失败与重试测试通过 |
| B | 评审搜索、关联列表、提交本人结论、结果查询及 Scope UI | 依赖 A；SINGLE／MULTIPLE 与双客户端、页面共同验收 |
| C | 附件完整链路、Node 补齐、受控执行适配 | 依赖 A；真实存储、租约、门禁和回写验收 |
| D | 删除预检与回收站 | 依赖 A；版本范围、关联影响和恢复路径验证 |
| E | Excel CREATE_ONLY 完整链路 | 依赖 A 和上传基础；预检、异步结果、逐行恢复验收 |
| F | Excel UPDATE_MATCHED | 依赖 E 和原子版本保护；覆盖授权与冲突测试通过 |
| G | 技能包目录自动生成、版本及自检；全链路发布验收 | 随前述批次增量交付；最终覆盖 R1～R7、C1～C8 |

B、C、D 在共享基础稳定后可独立交付；不以某一批次完成代表整体完成。只发布 E 时明确“支持新增导入，覆盖导入未支持”。技能包只宣传当批实际暴露并验收的能力。

### 10.1 批次 0：契约与技术验证（A 的前置门槛）

本批次不开放新业务写能力，交付以下可评审产物：

|产物|必须固定的内容|未满足时的处理|
|---|---|---|
|统一操作目录|各入口 operationId 映射、schema、权限、幂等分类和缓存规则|A 不开放写入口|
|授权兼容矩阵|旧/新 Token、总 Scope、授权版本、兼容起止时间与迁移提示|新增 Scope 默认不可用|
|状态与恢复协议|同步事务、异步租约/隔离版本、逐项提交、退避及清理规则|批量异步能力不开启|
|评审事务试验|真实数据库回滚、管理员本人提交、多人并发、外部通知去重|B 先重构共享服务|
|导入技术试验|多行用例身份、无副作用预检、逐项恢复、容量与终止机制|E 不包装旧导入方法上线|
|版本与验证清单|服务/Node/技能包及依赖版本、fixture、命令、预期数量、允许跳过数|不能进入发布验收|

A～G 每批均建立“实现完成”和“验收通过”两列。C 以受控执行依赖验收为前提，E 依赖上述导入试验，G 随批次仅发布已通过验收的能力。批次 0 中的参数定稿必须写回本方案或受版本管理的契约清单；未定稿项不得以开发者临时选择代替发布决策。

## 11. 统一验证方案

### 11.1 必测场景

| 类别 | 必须覆盖 |
| --- | --- |
| 授权 | Scope 缺失、只读用户、跨项目 reviewId/caseId、Token 白名单、用户禁用、即时撤权、缓存回放时撤权 |
| 参数 | 空白名称、空数组、错误枚举、未知字段、超长评论、超过 100 条、非法 requestId |
| 幂等 | 同键同参串行／并发、同键异参、提交前异常、提交后响应丢失、外部通知去重、记录清理 |
| 评审 | 创建同时关联、重复关联、SINGLE 通过、MULTIPLE 部分及全部通过、拒绝、版本竞争、归档拒绝 |
| 契约 | 所有工具真实响应符合 outputSchema；远程／Node 对应参数语义一致；业务失败不包装成功 |
| 技能包 | ZIP 文件完整、元数据、无明文密钥、校验和、目录与暴露策略一致、自检失败退出码 |
| 迁移 | 空库与历史样本升级、历史幂等记录兼容、唯一键冲突检查、旧客户端明确迁移提示 |
| E2E | 两种 MCP 创建→关联→提交→查询；页面查看同一结果与历史；真实账号权限失败路径 |
| 故障体验 | loading、empty、success、validation-error、permission-denied、network-error、server-error；保留输入与 traceId |
| 新建 | 默认／指定模板、必填字段、模块路径、单条失败、混合批量、failFast、成功项不重复 |
| 修改 | 省略字段保留、显式清空、非法 patch、过期版本、并发、页面与导入同时修改 |
| 删除 | CURRENT／ALL、预检后对象变化、关联和执行限制、回收站可见、历史证据保留 |
| 附件 | 空文件、超限、用途错误、跨主体／项目、过期、并发关联、断网上传、存储失败与补偿 |
| 执行 | 真实任务成功和失败、缺证据拒绝、断言失败、租约过期、响应丢失、回写失败可追溯 |
| 评论 | 越权读取、异用例父评论、无效通知用户、附件归属、富文本、重复通知 |
| Excel | 多工作表／模板规则、中文、空行、损坏文件、重复项、非法模块、预检后文件变化、覆盖版本冲突、重启恢复 |

### 11.2 执行命令与证据要求

以下为实施阶段命令，不表示本方案编写时已运行。数据库测试仅连接隔离库，先核对环境和 fixture。

```powershell
npm.cmd --prefix metersphere-mcp test
npm.cmd --prefix metersphere-mcp pack --dry-run
.\mvnw.cmd -pl backend/services/agent-integration -am test '-Dtest=AgentMcp*Tests,AgentCase*Tests,Agent*Attachment*Tests,Agent*Comment*Tests,Agent*Import*Tests,AgentIdempotency*Tests' '-Dsurefire.failIfNoSpecifiedTests=false'
.\mvnw.cmd -pl backend/services/case-management -am test '-Dtest=CaseReview*Tests,FunctionalCase*Tests' '-Dsurefire.failIfNoSpecifiedTests=false'
pnpm.cmd -C frontend run type:check
pnpm.cmd -C frontend exec eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts
pnpm.cmd -C frontend run build
.\mvnw.cmd -pl backend/app -am package '-DskipTests'
pnpm.cmd -C frontend exec playwright test e2e/mcp-review.spec.ts e2e/mcp-case-operations.spec.ts
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml config --quiet
```

测试通配符中的新增类须实际存在，并核对执行数量，禁止以零测试通过验收。应用 package 的 skipTests 仅用于前述测试完成后的打包，不可替代测试。

镜像构建沿用 deploy/quality-verify/README.md：每次将本次 JAR 解包到新目录，使用其实际路径作为 DEPENDENCY，执行 Dockerfile.backend 构建；启动后运行 scripts/verify-quality-environment.ps1。测试前先核实依赖及隔离凭据，不把占位参数当作已验证命令。

拟新增 frontend/e2e/mcp-review.spec.ts 和 frontend/e2e/mcp-case-operations.spec.ts，使用真实隔离后端和正式账号；按上述 Playwright 命令执行。实施时固定 base URL 与 fixture 前置命令并写入验收记录。不得用 Mock 响应代替权限、迁移或正式回写验收。

证据至少包括：提交 SHA、工具契约版本、命令、时间、结果数量、日志、迁移前后统计、镜像 ID、健康检查、脱敏 requestId/traceId 和页面截图。长期证据进入受控归档并在仓库登记索引，不仅存放于 .codex-tmp。

用例操作 E2E 同时核对用例列表、详情、回收站、附件、评论、导入结果和执行历史；导入保存逐行结果，附件保存摘要。测试通配符命中不代表真实数据库集成已执行，应单独记录数据库连接、fixture 与隔离环境证据。

### 11.3 新增验收与证据门槛

|场景|通过条件|
|---|---|
|远程 MCP 写入后经 Node/HTTP 同键重试|同一 operationId 只产生一次业务副作用，别名也一致|
|旧 AGENT_ALL、显式新增 Scope、授权版本迁移|符合 4.2.1 矩阵，无静默扩权；RBAC 撤权仍即时拒绝|
|认领响应丢失、旧租约/上传地址过期|受授权恢复或明确失效，缓存/日志无明文敏感凭证|
|双工作进程接管、旧进程迟到、处理中撤权|仅当前持有者可提交；已成功项不重放，后续项停止|
|多评审人并发及管理员作为指定评审人|只修改本人结论，汇总正确；不靠共享版本制造无意义冲突|
|Excel 多行用例、合并单元格、解析资源耗尽|逻辑条目身份稳定、不会按步骤行重复创建、解析确实终止|
|关闭新操作后直接调用已知工具名/HTTP|服务端拒绝新写入，受授权历史查询与恢复策略明确|

前述命令只是通用入口，验收清单必须逐个测试集登记实际类名、隔离数据库地址（不含密码）、fixture 建立/清理命令、开关、预期测试数和允许跳过数。安全、真实数据库和核心 E2E 的必测项允许跳过数为 0；`surefire.failIfNoSpecifiedTests=false` 仅用于 reactor 的无匹配模块，不能用于接受整项零测试。

现有 `AgentQualityFinalizationMySqlTests` 等受 `-Dquality.mysql=true` 控制；需先建立其要求的隔离实例，再使用实际类名和该开关运行。不能将无开关的测试成功算为数据库验证。本专项新测试开关和 fixture 应在写测试时同步登记，不虚构尚不存在的命令。全仓 `mvnw.cmd verify` 及必要模块检查需登记通过或具体阻塞，相关定向测试不能替代全仓结果。

前端使用上述只读 ESLint 命令：当前 `run lint` 含 `--fix`，不作为只读验收命令。历史错误与本次引入错误分别登记；存在历史错误不代表全仓检查通过。

Node 除构建/测试及 pack dry-run 外，须生成实际 npm tarball，在干净临时目录安装并通过真实 MCP 客户端运行；核对 bin、依赖、Node 版本和七类业务操作。技能包需验证最终 ZIP 校验和、实际解压/加载、自检脚本非零失败退出码，并按指引完成真实操作。部署版本/能力不匹配时应先提示升级，不能继续调用不存在的工具。实际 tarball 文件名、安装命令、客户端版本及测试结果进入验收记录。

## 12. 发布与回退

1. 在隔离环境完成全链路，先确认现有评审与旧客户端行为不被意外破坏。
2. 在代表性历史克隆库验证迁移、并发和回退；记录旧幂等表重复／空响应的处理结果。
3. 先部署后端及权限支持，再部署 Node adapter 与前端，最后发布匹配版本的技能包；每步核对版本与 tools/list。
4. 先使用受控测试账号验证新评审与导入 Scope，再向需要的用户开放；不批量为旧 Token 自动增加提交权限。
5. 观察授权拒绝、幂等冲突、执行中超时、评审失败和下载失败，不记录敏感请求全文。

回退优先关闭新评审、删除与导入工具的暴露和写入口，保留已写数据与审计。已执行的评审结论不能因程序回滚而删除。新增数据库字段尽量保持向后兼容；若旧程序无法保证幂等与授权，禁止直接回退到旧写路径。质量门禁拒绝旧直写的行为始终保留。

用例和导入回退保留已创建／修改记录、附件引用、行结果和审计。既有用例的 revision、幂等和行级恢复机制若不兼容旧程序，不得直接滚回旧写入口。

### 12.1 操作开关与混合版本控制

服务端按 operationId 设置统一开关，默认关闭未验收的新评审提交、删除和导入。开关检查同时作用于 tools/list 暴露、tools/call 直接调用、Agent HTTP、Node 适配后的应用入口及后台任务领取；隐藏目录不是授权措施。旧写工具拒绝逻辑不随开关回退而恢复。

发布清单为每项能力登记最低服务契约、Node 包、技能包、授权版本、feature flag 和允许的合同协议。部署顺序仍为兼容后端/权限 → 客户端与前端 → 匹配技能包 → 受控开放开关。v2 消费者不足时不创建 v2；在途旧协议按其冻结契约处理。旧客户端遇到新增必填版本字段等破坏性变化须得到稳定迁移错误，兼容适配不能省略并发或权限保护。

关闭开关后停止新请求和新任务领取，RUNNING 任务在当前原子条目结束后停止后续条目并记录恢复原因；已提交数据与审计保留。排队任务保留且受授权可查询，不自动删除或改成成功。临时上传文件按引用与保留期清理，旧预检重新启用后仍需有效期/版本/授权校验。恢复写入须重新通过当前授权和恢复检查；清理、幂等结果查询和历史结果读取可独立开放，不能因关闭写入口而失去排障能力。

## 13. 风险与完成定义

主要风险包括既有评审批量事务与管理员分支兼容、外部通知重复、所有写路径的版本统一、删除多版本关联影响、导入崩溃恢复、临时附件共享规则、旧客户端迁移和线上部署差异。分别通过共享校验、事务及 outbox、版本条件更新、预检快照、逐项结果记录和分批发布控制。

R1～R7 与 C1～C8 均有实现及验收证据，前端、后端、两类 MCP 客户端和数据库契约一致，相关单元／集成测试、类型检查、lint、构建、迁移、镜像构建、容器健康、真实核心 E2E 与技能包使用验证通过后，方可标记整体完成。未实现项、未执行检查、未说明的占位实现或运行阻塞均需披露，整体标记部分完成或阻塞。

本次仅补充本方案文档，未修改业务代码、数据库、已安装技能包或部署配置；保留 R1～R7、C1～C8 追踪编号。本次静态校验仅验证章节、评审意见覆盖、命令和本地链接，不代表功能测试通过。本专项仍待按批次 0、A～G 实施验收。


### 2026-10-19 实施进度

已在 v3.2 开始实施，当前为**部分完成**。本轮授权/幂等基础、评审创建关联、评论与附件安全、用例更新保护、Node 适配及动态技能包的实际变更和验证见 [实施记录](summary/MCP与AI技能包实施记录-20261019.md)。本文各批次仍是验收目标，不因本次代码变更自动标记完成；评审结论、异步导入、删除预检、Scope 授权版本等仍待实施。
