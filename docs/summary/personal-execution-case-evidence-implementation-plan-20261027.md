# 个人 Agent 用例级图片证据调整：详细实现方案

后续验收修订：[两轮测试汇总与详细修复方案](msp-two-round-test-remediation-plan-20261027.md)，其中D04/D08/D09/D10/D13/E01细化MCP导出身份、导入追加、客户端契约、安全错误及事实加密；原T01～T47验收矩阵保持有效，不因子项通过缩减。

日期：2026-10-27  
状态：部分实施，核心代码与聚焦验证已落地；完整部署验收和剩余治理项待完成  
范围：个人 Agent 通过 MCP 直接执行功能用例的证据上传、提交和查询链路，以及关联 MCP 契约、错误、导入联动和幂等治理

修订说明：2026-10-27 补充第 16～23 节，整合 [MCP 补充审查](mcp-business-contract-audit-20261027.md)。第 1～15 节的业务基线继续有效；涉及快照、requestId、幂等、读取、客户端交付和验收的细节以新增章节为补充。新增内容均为待实施方案，不是修复完成记录。

复审说明：同日再次对照实际代码，修正需求编号、证据数量、事实结构及读取协议的歧义，新增第 24 节明确版本持久化、计划同步、脱敏、执行身份、状态查询和灰度门槛。第 24 节列出的约束是实施必需项，不是已实现行为。

## 1. 需求依据与目标

本方案以用户明确的业务要求为准：同一条测试用例，不论包含多少步骤，仅需一张图片附件证据即可。无需为不同步骤重复上传图片，也无需为绑定不同步骤更换 requestId。

截图中的历史答复仅作为问题线索，不作为需求来源。现有代码中的逐步骤证据约束、Hash 必填约束，不代表用户曾经提出这些要求。

目标行为：

1. 每次用例执行提交至少关联一张有效图片，允许主动关联多张。
2. 图片属于本次用例执行，不强制属于某一步骤。
3. 步骤保留实际结果、状态和覆盖校验，不要求各自携带图片。
4. 同一图片上传一次即可覆盖整条用例，附件只发布一份。
5. 上传与提交不要求调用方提供 contentHash、policyHash、stepNum、purpose 等证据绑定参数。
6. requestId 仅用于操作幂等，不作为步骤绑定或图片数量要求。
7. 保留资源权限、文件安全、事务一致性和可追溯性。

不扩展调整 Runner 任务、租约、断言协议；不将结构验收成功表述为独立核验了图片内容或真实执行。

## 2. 当前实现与问题定位

以下为本地工作区源码观察，不代表已验证线上部署版本。

| 当前行为 | 实现位置 | 问题性质 |
|---|---|---|
| contract 返回 contentHash、policyHash | AgentDirectExecutionService.contract | 技术协议，不是图片业务要求 |
| 上传必须携带 contentHash、stepNum、purpose | Node MCP schema、原生 MCP schema、upload | 步骤绑定与用户需求不符 |
| 每条步骤记录必须携带非空 evidenceIds | AgentDirectExecutionService.submit | 后端硬编码，无法仅调整门禁消除 |
| 查询证据时匹配 step_num | AgentDirectExecutionService.submit | 跨步骤共用 ID 被拒绝 |
| 根据 requiredRoles 检查前后截图 | GlobalQualityGate.verifyDirectEvidence | 可能额外要求多张图片，线上配置尚未核实 |
| 同图可以再次上传，生成新证据 ID | AgentDirectExecutionService.upload | 没有按 sha256 禁止重复，但不应要求用户这样操作 |
| 幂等按 token_id、tool_name、request_id 隔离 | AgentOperationExecutor | 上传和提交可使用相同字符串；同操作同 ID 改参数会冲突 |
| 证据表 step_num、purpose 为 NOT NULL | V3.7.2_102__personal_execution_evidence.sql | 需要增量迁移支持用例级证据 |

相关源码（路径相对仓库根目录）：

- `metersphere-mcp/src/tools/caseLibrary.ts`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/tool/functional/AgentDirectExecutionTools.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentDirectExecutionService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentOperationExecutor.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/quality/GlobalQualityGate.java`
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_102__personal_execution_evidence.sql`

## 3. 需求追踪表

| 编号 | 需求 | 入口/前端实现 | 后端接口与实现 | 数据/配置 | 验收证据 |
|---|---|---|---|---|---|
| R01 | 一张图片覆盖任意步骤数 | MCP evidence.upload、submit | upload、submit | CASE 作用域 | 1/10/100 步各用一张图提交 |
| R02 | 步骤不强制配图 | 两套 MCP steps schema | 删除逐步骤必图及 step_num 匹配 | 旧证据可兼容 | 无步骤 evidenceIds 仍成功 |
| R03 | 不重复上传、发布 | MCP 重试及工具说明 | AgentOperationExecutor、附件写入 | 复用幂等表与关联表 | 重试不增加证据、附件和执行记录 |
| R04 | Hash 不必由调用方提供 | 上传、提交 schema | 服务端记录并检查版本 | 保留内部 content_hash、policy_hash | 不传 Hash 的完整闭环 |
| R05 | 门禁按用例验图 | 无需新增人工页面 | 独立的直接执行证据规则 | 与 Runner 的用途规则隔离 | 单图通过，无图拒绝 |
| R06 | 查询证据与结果 | 现有用例详情附件区、execution.get | report、附件列表与读取 | facts 增加用例级证据字段 | 元数据可见且图片可打开 |
| R07 | 权限与失败一致 | 客户端安全错误提示 | 资源权限、事务、错误映射 | 不放宽归属约束 | 越权、过期、冲突及回滚测试 |
| R08 | 旧请求和历史记录兼容 | 旧 MCP 请求继续接受 | 旧字段适配、历史 report 兼容 | 增量迁移，不改历史事实 | 旧请求、历史图片回归 |

当前各项均为计划实施，尚无本轮运行验收证据。

## 4. 目标链路与数据归属

```text
读取用例（contract 可选）
  → 执行全部步骤
  → evidence.upload 上传一张图片
  → 返回 evidenceId
  → submit 提交 steps + 顶层 evidenceIds
  → 权限/版本/覆盖/用例级证据校验
  → 事务内写执行事实、证据关联、普通附件及执行结果
  → execution.get / attachments.list 查询
```

证据模型区分三个实体：

- 用例：业务归属，决定项目权限和用例权限。
- 暂存证据：上传后的图片，尚未发布，保留归属、期限和服务端版本快照。
- 执行提交：关联本次图片及步骤结果，形成可追溯执行事实。

一张证据可以覆盖本次提交的所有步骤。不同执行提交仍使用各自证据，避免无意复用已消费的旧图片冒充本次执行；对同一次提交的网络重试，必须幂等返回原提交结果。

## 5. MCP 接口协议

### 5.1 contract

保留 `metersphere.functional.execution.contract`，用于读取用例、步骤和执行说明；不再是上传的强制前置调用。

响应增加可发现的协议描述：

```json
{
  "evidenceScope": "CASE",
  "minimumEvidenceCount": 1,
  "stepEvidenceRequired": false,
  "clientHashesRequired": false
}
```

现有 case、contentHash、policyHash 等字段继续返回以兼容旧客户端。说明中明确：内部 Hash 用于追溯，不是标准上传和提交的必填字段。contract 内部错误仍需单独定位，不以本调整已经解决 100211 错误为结论。

### 5.2 evidence.upload

保留工具名 `metersphere.functional.execution.evidence.upload`。

```json
{
  "projectId": "project-id",
  "caseId": "case-id",
  "requestId": "execution-001",
  "fileName": "执行截图.png",
  "contentType": "image/png",
  "base64": "<图片Base64>"
}
```

Node 工具继续支持 filePath/base64 二选一；原生 MCP 接收 base64，不接收服务器本地任意路径。文件路径不进入后端请求。

| 字段 | 新要求 |
|---|---|
| projectId、caseId | 必填，服务端核验真实归属 |
| requestId | 必填；重试复用；无需按步骤变化 |
| fileName、contentType、图片内容 | 必填，沿用 PNG/JPEG、5 MiB 及现有图像校验 |
| contentHash | 旧客户端可传；新请求不要求，由服务端记录版本 |
| stepNum、purpose | 兼容接收但不作为新证据绑定条件 |

响应保留 evidenceId、sha256、sizeBytes、expiresAt，并增加 evidenceScope=CASE。上传成功仅表示暂存成功，不表示附件已正式发布。

### 5.3 submit

保留工具名 `metersphere.functional.execution.submit`。

```json
{
  "projectId": "project-id",
  "caseId": "case-id",
  "requestId": "execution-001",
  "evidenceIds": ["evidence-id"],
  "steps": [
    {"stepNum": 1, "actual": "观察到页面已打开", "result": "SUCCESS"},
    {"stepNum": 2, "actual": "观察到保存成功提示", "result": "SUCCESS"}
  ]
}
```

校验规则：

1. 纯新格式顶层 evidenceIds 原始数组至少 1 项、最多 10 项。兼容请求可只使用步骤级字段；顶层存在时原始数组最多 10 项，每个步骤级数组最多 10 项，步骤最多 1000 条。合并去重后至少 1 项；兼容路径沿用现有最多 1000 个唯一证据、50 MiB 总图片限制。各原始数组先校验再去重。纯新格式不能因重复 ID 放宽到超过 10 项。
2. steps 继续要求 stepNum、actual、result；不再要求步骤级 evidenceIds。
3. 沿用原步骤至少覆盖一次、额外和重复步骤可保留、任一 ERROR 汇总 ERROR 的规则。
4. contentHash、policyHash 不再必填；内部记录仍必须完整。
5. 兼容期同时接收顶层和步骤级 evidenceIds，取并集去重；旧格式也不能要求每一步携带证据。
6. 证据 ID 仅在同项目、同用例、同 Token/用户、未过期且未被其他提交消费时可用。

响应继续使用 submissionId、result、gateStatus、publishStatus 等既有字段；增加 evidenceScope、evidenceIds。旧 attachmentIds 字段维持其既有语义，不在本次调整中静默更换 ID 类型。

### 5.4 get 与附件展示

execution.get 的新 facts 写入顶层 evidenceIds、evidenceScope 和去重证据清单。保留现有 steps 字段及每步结果，不伪造“图片证明了该步骤”的映射；不另改成 stepResults，避免现有组件读不到数据。

旧 facts 继续原样读取，缺少 scope 时标识为历史步骤级记录；不重写旧事实。附件列表继续通过既有资源权限访问图片。前端若依赖步骤截图必须适配为用例级证据区；如果仅使用普通附件区且无强依赖，不新增页面。

## 6. requestId 的明确语义

已核查 AgentOperationExecutor，当前幂等隔离键为 token_id + tool_name + request_id，参数摘要排除 requestId。

| 操作 | requestId | 预期 |
|---|---|---|
| 上传后再次上传相同参数 | 相同 | 返回原 evidenceId，不插入新图片 |
| 上传后提交 | 相同字符串 | 可以，工具操作名不同，互不冲突 |
| 提交后重试相同参数 | 相同 | 返回原 submissionId，不重复发布 |
| 同一操作改变图片或结果 | 相同 | IDEMPOTENCY_CONFLICT，不能返回旧成功冒充本次结果 |
| 不同步骤引用同张图片 | 无须新上传 | 只需在顶层引用一次 |

不修改全局幂等机制来允许“同操作、同 ID、不同内容”覆盖旧结果。该限制用于防重复写入，与强制逐步骤上传无关。

Node 工具若提供自动 ID，必须在一次逻辑操作开始时生成并在其重试中复用，不得每次发送随机新 ID。原生工具调用仍可由调用方提供 requestId；用户不需要手工为步骤设计 ID。

请求规范化不能破坏已存幂等摘要：旧请求先按原参数尝试回放，新请求采用确定的规范化输入。不要在共享 AgentOperationExecutor 中全局忽略 Hash、步骤等字段；必要时仅为本工具引入有版本的规范化适配。

## 7. 服务端实现步骤

### 7.1 上传

1. 校验 FUNCTIONAL_SUBMIT、CASE_ATTACHMENT 及用例更新权限。
2. 进入既有幂等事务，已完成重试直接返回原结果。
3. 读取用例并计算内部内容指纹；不用调用方 Hash 作为新流程前置条件。
4. 校验文件名、MIME、真实格式、尺寸、像素及大小；沿用现有 64 像素最小边、2000 万像素上限等安全检查。
5. 沿用 Token 锁及 50 MiB 暂存额度校验。
6. 插入 CASE 证据，step_num/purpose 置空，保留 sha256、payload、content_hash、期限及所有者。
7. 原子保存幂等响应。相同图片无需按步骤复制。

### 7.2 提交

建议处理顺序：

```text
鉴权
→ 原请求幂等回放
→ 开启事务与幂等记录锁
→ 锁用例，读取当前步骤和内部版本
→ 校验步骤覆盖和结果字段
→ 收集、去重用例证据 ID
→ 按 ID 排序锁证据行，校验归属/有效期/消费状态/版本
→ 读取当前个人通道门禁，检查至少一张图片
→ 保存执行快照、策略版本与 Agent 声明结果
→ 写关联和附件、消费证据、更新用例执行状态
→ 保存幂等响应并提交事务
```

查询证据移除 step_num 条件，其他归属条件保留。排序锁行降低不同提交以相反证据顺序获取锁造成死锁的概率。并发使用同一证据时只允许一个新提交成功，失败方收到可理解的冲突。

步骤逐条循环仅负责实际结果和状态，不再逐条调用证据门禁。用例级证据校验只执行一次。

### 7.3 Hash 与版本

contentHash、policyHash 不再是调用方必须理解或维护的协议条件，但内部审计信息保留。

- 上传时服务端计算并保存用例内容指纹。
- 提交时在用例锁内重算，并与所用证据版本比对；用例已变化则提示重新执行并上传证据。
- 多张证据必须对应同一当前用例版本，避免混用旧版本材料。
- 新请求按提交事务中读取的生效策略验收，并冻结该策略版本，不因调用方缺少 policyHash 被拒绝。
- 旧字段在兼容适配后不作为新版门禁的额外拒绝条件；版本一致性以服务端保存的证据版本为准。
- 指纹必须排除最后执行结果、执行用户、时间及展示字段等非用例内容，防止发布后因运行状态变化误判内容更新。复用原算法前补充稳定性测试；确需变更算法时增加版本标识，保留旧证据算法分支，不直接覆盖旧 Hash。

该自动检查只能覆盖上传至提交之间的版本变化；无法证明实际执行开始时的版本。响应和文档不得宣称提供了完整执行冻结保证。

## 8. 质量门禁调整

个人直接执行采用明确的 CASE 证据规则：最低一张有效图片、PNG/JPEG、沿用安全大小和数量限制。

不能只删除 Q-EVIDENCE-01 配置：逐步骤非空校验和 step_num 绑定仍在服务端代码中，必须一并修改。

建议拆出 verifyDirectCaseEvidence，避免直接修改 Runner 共用规则的意义。个人通道不执行 BEFORE_ACTION/AFTER_ACTION 或 BEFORE_STEP/AFTER_STEP 的成对用途要求。

若策略模型增加 directExecutionEvidence 段，应同步修改验证器、策略展示和默认配置；旧策略无该字段时，对个人通道采用明确且受测试覆盖的 CASE 默认规则，不修改已发布策略正文及其 Hash。提交快照同时记录基础策略 Hash 和实际个人证据规则版本，避免审计上无法解释采用的有效规则。

继续返回 STRUCTURE_ONLY_V1、AGENT_REPORTED 等已有语义。图片存在和格式正确不代表已独立核验图片内容。

## 9. 数据库迁移与兼容

### 9.1 增量结构

不得修改已执行的 V3.7.2_102 历史迁移。新增下一个可用 Flyway 版本，编号在实施时检查后确定，避免与工作区其他迁移冲突。

概念 SQL（实施前补充字段注释和项目所需语法验证）：

```sql
ALTER TABLE agent_case_evidence
  ADD COLUMN evidence_scope VARCHAR(16) NOT NULL DEFAULT 'STEP',
  MODIFY COLUMN step_num INT NULL,
  MODIFY COLUMN purpose VARCHAR(32) NULL;
```

历史行默认 STEP，新代码上传显式写 CASE。content_hash 保持内部存储；不删除历史字段。执行事实 JSON 增加 evidenceScope/evidenceIds，不另建图片存储表。

现有 agent_case_submission_evidence、functional_case_attachment 及证据读取通道继续复用。核查索引和关联唯一性；只有真实查询计划或并发测试表明需要时再增加索引/约束。

### 9.2 旧数据

- 历史已发布记录保持原样，不重新审核、不重复生成附件。
- 历史 STEP 暂存证据在归属和版本有效时可被新提交当作 CASE 证据使用，不检查其 step_num 是否等于当前步骤。
- 过期和消费规则保持；已发布图片不能被暂存清理任务删除。
- 旧步骤级 evidenceIds 汇总后去重，重复引用只发布一次。

### 9.3 发布与回退

先备份并执行增量迁移，再部署兼容新旧协议的服务端，最后升级 Node MCP 包与工具说明，刷新客户端工具 schema 缓存。

建议同一服务端版本先实现完整兼容，再启用 CASE 写入。旧服务端不了解 CASE 证据时不得无条件直接回滚；应停止新写入、保留数据，由支持两种 scope 的兼容版本承接。禁止为回滚把空 step_num 伪造为步骤 1，或删除已发布证据。

## 10. 权限、异常与用户展示

统一遵循现有安全错误协议，必要字段为 code、message，可包含 details、traceId。技术堆栈和 SQL 只记日志。

| 场景 | 预期行为 |
|---|---|
| 没有任何证据 | QUALITY_EVIDENCE_REQUIRED：请上传至少一张用例执行截图 |
| 图片格式、尺寸或大小非法 | QUALITY_EVIDENCE_INVALID：图片不符合上传要求 |
| 其他用户/项目/用例证据 | 使用现有拒绝访问协议，不泄漏证据是否存在 |
| 图片过期或已消费 | 返回现有安全业务码；缺少合适编码时新增并同步错误映射 |
| 上传后用例内容发生变化 | VERSION_CONFLICT：用例内容已更新，请重新执行并上传截图 |
| 同操作同 ID 改变参数 | IDEMPOTENCY_CONFLICT：请求标识已用于其他内容 |
| 锁等待或请求仍处理中 | REQUEST_IN_PROGRESS：提示稍后使用原 ID 重试 |
| 数据库或存储未知异常 | 安全通用提示和 traceId；事务回滚，不返回成功 |

附件 UI 如有改动，覆盖 loading、empty、success、validation-error、permission-denied、network-error、server-error。网络超时视为结果不确定，使用原 requestId 查询/重试，不自动创建第二份提交。

## 11. 文件级实施清单

| 文件或组件 | 计划修改 |
|---|---|
| metersphere-mcp/src/tools/caseLibrary.ts | 放宽 Hash/步骤字段，增加顶层 evidenceIds，同步说明；保留文件读取限制 |
| AgentDirectExecutionTools.java | 同步原生 JSON Schema 的 properties/required，避免两套协议不一致 |
| AgentDirectExecutionService.java | CASE 上传、顶层收集证据、去除步骤绑定、内部版本检查、report 兼容 |
| GlobalQualityGate.java | 新增个人用例级证据校验方法，与 Runner 角色校验隔离 |
| 策略验证器及策略展示（如涉及） | 识别个人证据规则并解释旧策略默认行为 |
| 错误映射及国际化资源（如涉及） | 补齐可理解的版本、证据和幂等错误 |
| 新 Flyway DDL | 作用域及字段可空调整，验证历史数据 |
| AgentDirectExecutionTests.java | 用例级规则、版本及边界测试 |
| AgentDirectExecutionMySqlTests.java | 事务、附件、并发、幂等和历史兼容 |
| metersphere-mcp/test/directExecution.test.mjs | 新旧 schema、上传转换和 requestId 行为 |
| 外部图片导入工具及测试 | 搜索并同步其 contentHash/stepNum/purpose 依赖，防止旁路仍要求逐步骤证据 |
| metersphere-mcp/README.md | 替换标准调用示例和证据要求 |
| 用例附件/执行事实展示组件（按依赖检索结果） | 支持 CASE 证据，不伪造逐步骤关联 |

## 12. 实施顺序

1. 阅读仓库实际 AGENTS、已有迁移、错误协议和构建脚本；保护工作区已有未提交修改。
2. 补充依赖检索：evidence.upload、import_url、step_num、purpose、evidenceIds、Hash 必填、report 和附件读取的全部调用点。
3. 先补充新协议、兼容和事务的验收测试，记录修改前失败原因。
4. 实施增量迁移和后端 CASE 行为，确保新旧请求均可处理。
5. 同步原生 MCP 与 Node schema、说明及文件/URL 导入适配。
6. 核查展示与权限链路，完成必要前端适配。
7. 运行分层验证，修复失败后复测；部署测试环境执行真实 MCP 冒烟。
8. 按需求追踪表逐项归档证据，披露未执行项，方可判断实现状态。

## 13. 测试与验收矩阵

| 编号 | 场景 | 预期 |
|---|---|---|
| T01 | 1/10/100 个原始步骤，一张图 | 提交成功，各新增一条执行记录和一份附件 |
| T02 | 全部步骤不含 evidenceIds，顶层一张图 | 成功 |
| T03 | 旧格式仅一个步骤携图 | 成功，其余步骤不因无图失败 |
| T04 | 多步骤重复相同 ID | 去重后只发布一份 |
| T05 | 不传两个 Hash 和上传步骤/用途 | 完整闭环成功 |
| T06 | 上传与提交使用相同 requestId 字符串 | 均成功，操作间隔离 |
| T07 | 相同上传/提交分别重试和并发重试 | ID 稳定，记录、附件不重复 |
| T08 | 同操作同 ID 改图片或结果 | 明确幂等冲突，无第二次副作用 |
| T09 | 无图、非图片、超限、损坏图片 | 安全拒绝，无正式发布 |
| T10 | 跨用户/Token/项目/用例证据 | 拒绝且不泄漏详情 |
| T11 | 过期或其他提交已消费的证据 | 拒绝；原提交重试仍回放成功 |
| T12 | 上传后修改用例，或混合不同版本证据 | 版本冲突 |
| T13 | 仅执行结果或展示信息变化 | 不误判用例内容变化 |
| T14 | 步骤遗漏、非法状态、空 actual | 保持原有业务校验 |
| T15 | 额外/重复步骤、任一 ERROR | 保持覆盖规则并汇总 ERROR |
| T16 | 附件插入或结果更新中途失败 | 提交、消费、附件、幂等全部回滚 |
| T17 | 老策略含前后截图要求 | 个人 CASE 单图仍可通过，Runner 原行为不变 |
| T18 | 老数据迁移、历史 report、图片下载 | 可读、不重写、不丢附件 |
| T19 | 清理过期暂存证据 | 不删除已发布证据 |
| T20 | URL 导入及本地文件两条上传入口 | 均使用同一 CASE 规则 |

真实端到端验收使用专用测试用例和真实图片，不使用固定成功结果冒充后端通过。记录请求、响应、submissionId、附件列表、可读取图片及数据库计数；归档时脱敏 Token。

## 14. 验证命令与执行记录要求

以下命令为实施阶段执行计划，不是本次已执行结果。

仓库根目录：

```powershell
mvn -pl backend/services/agent-integration -am test '-Dtest=AgentDirectExecutionTests,AgentDirectExecutionMySqlTests' -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl backend/services/agent-integration -am package -DskipTests
```

MySQL 测试必须先读取测试类实际环境变量和启用条件，连接专用测试库；测试被跳过不能记为通过。补充迁移测试和门禁测试类后，应加入上述选择列表。

`metersphere-mcp` 目录：

```powershell
npm run build
node --test test/directExecution.test.mjs
npm test
```

前端类型检查、lint、生产构建，以及 Docker 镜像构建、容器启动/健康检查、迁移验证的准确命令，实施时必须从实际 package.json、Dockerfile、Compose 或 CI 配置解析后填入验收记录。本方案未核实这些命令，不提供可能错误的部署指令。

每条验证记录包含：执行目录、准确命令、环境与版本、退出码、通过/失败/跳过数量、日志位置。不能执行时记录原因、风险及用户可复现的准确命令。核心 MCP 流程另附真实请求和响应证据。

## 15. 已有证据、风险及完成定义

此前分析已执行 MCP TypeScript 构建和 `node --test test/directExecution.test.mjs`，当时两项测试通过；它们证明旧包装逻辑及旧 schema，不证明本方案已经实现或线上单图提交成功。本次文档编写仅进行了源码和迁移检索，没有执行业务改造、数据库写入或线上提交。

已知风险：

1. 本地存在其他未提交改动，实施必须逐文件检查并保留。
2. 线上版本与本地可能不同，100211 契约内部错误尚未定位，本方案不能替代 traceId 日志排查。
3. 两套 MCP schema、URL 导入和客户端缓存若未同步，会继续出现旧参数必填。
4. 指纹内容边界不稳定会造成假冲突，必须以真实更新/执行回归验证。
5. CASE 迁移写入后，旧服务端回退存在兼容风险。
6. 一张图片满足附件要求，不代表系统独立证明了所有步骤真实执行。

只有需求追踪全部有实现和验收证据，相关单元/集成测试、构建、迁移、容器健康和核心端到端检查通过，才可将功能标记为“完成”。否则按实际证据标记“部分完成”或“阻塞”，不得将本方案当作已交付功能。

本文交付状态：详细实现方案已形成；功能尚未实施。

## 16. 本次整合的交付范围与追踪

执行快照用于记录“当次执行的用例定义、提交事实、图片和规则”，支持历史查看与排查。定义快照、定义指纹和运行事实是三种不同概念：快照保留依据，指纹检测定义变化，运行事实记录结果。它们都不能作为强制调用者逐步传图或管理内部 Hash 的理由。

| 编号 | 需求与当前缺口 | 客户端/页面入口 | 后端、数据或配置 | 验收与依赖 |
|---|---|---|---|---|
| X01 | 用例级图片、无必填技术 Hash | Node/原生 evidence.upload、import_url、submit | DirectExecutionService、ExternalImageService、质量门禁、CASE 迁移 | T01～T20；依赖 X02/X03/X07 |
| X02 | 定义快照不混入历史运行事实 | contract、execution.get、用例详情 | CaseDefinitionSnapshot、指纹版本、facts schemaVersion | 连续执行不嵌套；定义变更可识别 |
| X03 | 统一参数和幂等身份 | Node/原生工具及 HTTP 头 | dispatcher、OperationExecutor、import job、upload intent | 长度、字符、大小写、操作隔离 |
| X04 | 导入后计划和评审联动 | Node import 工具，不新增人工页面 | CaseImportTools、ImportFollowupCoordinator、关联组表 | 新建/追加/仅导入/独立重试 |
| X05 | 统一安全错误与未知结果处理 | Node client、原生 MCP | 业务错误映射、日志 traceId | HTTP/Holder/RPC 三条路径一致 |
| X06 | 幂等重放授权、并发和外部副作用 | 全部 MCP 写入口及关联 REST | 通用缓存、OperationExecutor、上传状态机 | 第 21～22 节；不改 Runner 租约规则 |
| X07 | 图片可查可读、ID 清晰 | attachments.list、execution.get、附件页面 | EvidenceStore、受控内容读取、附件响应 DTO | Token 读图；跨项目及撤权拒绝 |
| X08 | 文本用例及数量边界透明 | contract、submit | 类型能力声明、分类型定义和结果模型 | 文本/空步骤/100 与 101/1000 与 1001 |
| X09 | 长期证据保留与清理明确 | 历史执行和附件查询 | 引用关系、保留策略、清理审计 | 到期/解除关联/恢复不误删 |
| X10 | 两端工具、文档和技能包一致 | Node 包、原生 tools/list、下载包 | schema 契约测试、README/模板 | 不含旧逐步骤强制规则 |

前端只改必要的事实和图片展示、错误交互；没有新增人工录入入口的需求。Agent 工具本身是用户入口，不以“没有新增页面”为由判定接口缺失。

第 3 节 R01～R08 为原始需求，本表 X01～X10 为扩展追踪编号，两组不得互相覆盖。

## 17. 定义快照与内部版本模型

### 17.1 当前根因与修改边界

源码链路为 getById → AgentFunctionalCaseExportService.enrichWithSteps → executionViews/provider → DTO.executionRef/executionFacts。DirectExecutionService.source 清除了旧运行字段，但没有清除新增运行事实；hash 对整个 DTO 计算，submit 再把整个 DTO 写入 facts.case。

这是已确认的对象污染路径。误报版本冲突、快照递归变大是该路径推导的风险，尚未通过运行复现，也不能据此认定线上 100211 的根因。

### 17.2 实现结构

1. 新增明确的定义快照模型和构造器（建议 CaseDefinitionSnapshot/CaseDefinitionSnapshotFactory），直接从已授权用例定义构造，不从包含运行事实的查询 DTO 整体复制。
2. 定义采用白名单：身份/内容版本、名称、定义类型、前置条件、结构化步骤及预期或原文本、具有业务定义含义的自定义字段。优先级、标签等是否参与指纹按字段语义显式登记；不默认把所有元数据当作执行依据。
3. executionRef、executionFacts、最后执行结果/人/时间、实际结果、显示名称、目录展示路径、查询包装字段不得进入定义指纹。updateTime 可作审计元数据，不单独作为内容指纹，避免非定义更新造成假冲突。
4. 新 facts 至少区分 schemaVersion、definition、steps、evidenceIds、evidenceScope、evidence、policySnapshot、actor、submittedAt、validationMode、resultSource。步骤结果的序列化名称仍为 steps。历史兼容读取同时支持原 facts.case/policy；通过统一适配器转换成明确展示 DTO，不把任意存储对象直接交给页面。
5. 历史执行通过 submissionId 查询，不内嵌上次 facts。禁止截断超大事实后返回成功，或写入空快照冒充完整记录。
6. 指纹采用版本化的稳定序列化。定义 Hash 是内部字段，CASE 标准调用无需提交；旧显式 Hash 按原方案兼容分支处理。指纹算法升级不得使旧证据直接失效。

上传时冻结定义；提交锁定用例并比较当前业务定义与证据冻结定义，混合不同定义的证据拒绝。本方案不强制执行前准备，因此只保证上传至提交期间的定义一致性；可选 contract/准备资源能够保留更早依据，但不能声称未调用准备时也知道真实执行开始时间。

验收：连续三次执行的 definition 无 executionFacts，大小不随历史次数累计；仅修改执行状态不冲突；修改步骤/预期发生冲突；查询接口仍能返回最新执行事实。

## 18. 参数、客户端能力与错误协议

### 18.1 requestId 统一方案

当前数据库 agent_idempotency_record 和 upload intent 已为 VARCHAR(128)，functional_import_job 为 VARCHAR(64)。dispatcher 限制 64；OperationExecutor/Node 部分工具允许 128；导入服务仅接受 `[A-Za-z0-9_-]{1,64}`，还拒绝其他工具允许的点号、冒号。

实施标准：MCP 业务 requestId 使用 `[A-Za-z0-9_.:-]{1,128}`，禁止前后空白，不自动 trim 或转小写。HTTP 幂等头与 body 均按同标准校验；同时出现时逐字节一致。新旧客户端原有合法值继续合法。导入请求字段通过新迁移扩至 128，同步 Bean/Schema/REST 校验。Runner 自己的任务事件 ID、租约和其他非 MCP 业务 ID 不机械替换。

新增共享 RequestId 校验常量和测试样例，Node 可共享生成的规格或通过契约测试核对。校验失败给出字段及允许规则，不再统一伪装“缺少标识”。操作命名决定隔离：相同字符串用于上传与提交合法，同操作换参数拒绝。

数据库大小写策略见 21.5：对新的操作身份采用区分大小写的存储/比较；不能只在 Java 中改校验而保留数据库隐式大小写折叠。

### 18.2 Node 导入联动

补充 followup.options、followup.retry 和 commit.followup 的 Node Schema；policy 支持 CREATE_NEW_PAIR/APPEND_EXISTING/IMPORT_ONLY。APPEND_EXISTING 必须提交明确 targetGroupId 和 expectedGroupRevision；多组先查选项供用户选择，不按名称或最近时间猜测。CREATE_NEW_PAIR 使用有效计划/评审名称、评审人和规则，服务端继续校验权限和归属。

原生工具已有部分实现，优先复用，不新造第二套联动业务。无 followup 的旧调用维持仅导入，返回 LEGACY/明确未请求联动；新客户端按照用户选择显式传入。不能静默将旧调用改成自动新增计划。

返回分别列出 importState、planState、reviewState、失败原因、可重试动作和关联组。重试联动以 jobId 和已冻结决策执行，不重导已成功用例；改变决策属于明确的新操作，不能用重试绕过原决策。详情仍以现有服务可提供的数据为准，新增字段需同步 DTO/Schema。

### 18.3 安全错误

MsApiError 和 callNativeTool 使用统一解析器，兼容 HTTP、ResultHolder 和 JSON-RPC 包装，保留受控业务 code/message/details/traceId。数字 RPC code 与字符串业务 code 分开返回，不用 400 固定推导权限和冲突含义。details 只允许字段错误、资源状态和安全的建议动作，不透传数据库/异常类/栈。

已知业务错误提供可执行提示；未知错误使用通用提示和 traceId。网络超时、响应解码失败等写入结果不明时标记 outcome=UNKNOWN，不自动换 requestId；优先查询操作状态或用原身份重试。前端错误展示复用同一语义。新增业务码需同步服务端映射、国际化和客户端测试。

## 19. 证据读取、类型边界与留存

### 19.1 受控读取与附件 ID

新响应增加 attachments 数组，每项明确 evidenceId（如适用）、fileId、associationId、fileName、contentType、sizeBytes、sha256、submissionId 和 availableActions。保留旧 attachmentIds 的现有语义，不直接改成关联 ID。普通暂存 attachmentId 与已发布 associationId 分开说明。

增加受控附件内容读取工具/接口，复用现有项目和资源权限、EvidenceStore 及文件服务。建议工具输入 projectId、caseId、fileId；按存在的附件关系或允许访问的执行关系查验，不仅凭 fileId 返回。原生可返回有大小限制的 MCP 图片内容，Node 透传相同内容；若采用短期链接，必须验证网关、认证及有效期，不能把 Token 拼到永久 URL。最终选择以客户端实际兼容验证为准。

页面预览与 MCP 读取共享授权与内容解析逻辑；Pending 图片默认不作为公开附件读取。禁止在普通 JSON 中返回数据库原始 payload。图片内容不可读时返回明确错误，不以“有 ID”计为证据链路完成。

### 19.2 类型和数量

contract 增加 definitionType、supported、limitations 与 unsupportedReason。结构化用例继续检查原步骤覆盖；原步骤数上限与提交记录数上限分别声明，1000 条提交记录不代表允许 1000 个原步骤。

文本型用例建议纳入后续完整支持：保留原文本定义，提交 caseActual 和 caseResult，不虚构一条步骤替代原内容，仍要求本次图片。此扩展必须同时更新服务、两端 Schema、页面展示和验收；在实现前返回明确不支持，不能声称本次证据放宽自然覆盖文本用例。结构化用例是否允许空预期、是否扩展原步骤上限，要通过现有数据规则和容量测试确定并记录，禁止静默丢步骤。

### 19.3 留存

暂存期限、最低审计保留期、页面可见性和最终清理条件分离。保留原方案 24 小时暂存；已发布图片随执行历史保留，不因暂存到期清理。现有 retain_until=30 天只作为最低保护条件，不能写成已存在的自动删除周期。

取消页面关联不等于删除证据；历史执行仍引用时必须保留底层文件，并通过授权的历史读取路径可读。业务删除/恢复、组织退出及合规清理均按显式策略处理，不默认无限公开或自动删库。清理前复核全部有效引用，记录原因和数量，增加容量告警。

## 20. MCP 幂等机制现状与目标

本次检索覆盖 dispatcher、AgentIdempotencyService/Mapper、AgentOperationExecutor、批量提交项、导入作业、普通上传准备/完成和 URL 导入。没有运行并发或数据库验证。旧 functional.submit/batch 已在原生 MCP 暴露层禁止，批量项服务不能直接当成当前可调用 MCP 缺陷；关联 REST 是否可达及权限还需另行核对。

| 机制 | 已有保护 | 缺口/待验证 |
|---|---|---|
| 通用 dispatcher 缓存 | token/tool/key 唯一索引、参数摘要、scope 检查 | 执行与缓存保存分离；未进入资源重放鉴权；摘要和期限问题 |
| OperationExecutor | 原子写入与响应提交、唯一键和锁、递归键排序、重放授权 | 旧操作别名不完整；与其他摘要版本不能混用；长期记录治理 |
| Import job | user/token/key、事务内锁用户并复查、每项状态 | 64 字符及字符集差异；有效期与结果保留应明确 |
| Upload intent | token/key、准备锁 Token、完成锁 intent、内容摘要校验 | SHA 大小写假冲突；已完成结果先受准备过期限制；清理丢身份 |
| URL import | 下载在事务外，外层和上传内层操作记录 | 两套命名及旧名映射；同 URL 不保证内容永远不变；外部存储副作用需核实 |

统一目标不是所有写入强行使用同一表，而是每个操作具有明确身份、稳定请求摘要、当前授权、原子业务提交、状态查询、重放期限和已知失败行为。Runner 的租约凭证不得进入普通缓存重放。

## 21. 继续排查发现的幂等问题及实现方案

### 21.1 I01：通用写操作存在并发及提交窗口风险 / P1

证据：AgentMcpStreamableService.callTool 先 findCachedResponse，再调用 handler，最后 save；唯一索引只保护缓存记录，不能自动保护之前的业务动作。save 的竞争分支发现同 Hash 记录时直接返回，不比较两次业务产生的资源 ID。

修复：逐工具登记副作用、业务事务和资源授权，优先将计划创建/关联等数据库写入迁入 OperationExecutor 或等价业务原子操作。业务动作与 SUCCEEDED 响应在同一事务提交；并发 loser 读取 winner 原响应，禁止返回第二份资源。关联 REST 入口也必须复用业务层，不能只在 MCP 外层防重。

失败窗口测试：写入前、业务中途、业务完成但响应记录前、提交后响应断开；均核对业务表数量和原响应 ID。当前仅确认分发顺序，具体工具是否已有独立去重需逐项取证，不能笼统认定全部重复。

### 21.2 I02：重放缺少当前资源授权 / P1

通用缓存命中只检查 scope，未执行处理器中的项目/资源校验。增加独立 authorizeReplay，不通过再次执行业务来鉴权。创建类操作检查当前项目权限及缓存结果资源归属；读取原结果不能恢复已撤销权限。OperationExecutor 已有该机制，保留并复用。

批量项旧服务命中缓存直接 return 的行为需在确认 REST 可达后覆盖同样验证，但不把已禁用旧 MCP 工具重新开放作为修复。

### 21.3 I03：参数规范化不足造成假冲突 / P2

AgentIdempotencyService.hashArguments 仅顶层 TreeMap，嵌套对象直接序列化；嵌套 Map 键顺序是否保持受 JSON 实现影响，但算法本身没有保证递归规范化。OperationExecutor 已递归排序，两者不一致。

另外 AgentAttachmentUploadIntentService.prepare 的 request_hash 对原始 sha256 字符串计算，expected_sha256 却转小写。调用方第一次用大写摘要、重试用等价小写摘要，会得到不同请求 Hash。

修复：按工具定义语义构造规范请求，再递归排序对象；数组默认保留顺序，只有业务明确为集合的字段才能去重排序。SHA 十六进制转小写后计算；不要任意 trim 正文、改状态大小写或删除有效参数。缺省值、null、可选旧字段与新顶层 evidenceIds 的规范化须有版本标识。新版本先按旧记录算法验证重试，再兼容升级，禁止重算覆盖历史 Hash。

### 21.4 I04：重放期限与业务结果期限混用 / P1

AgentConstants.IDEMPOTENCY_TTL_MS 为七天；Mapper.deleteExpired 仅删除 status=SUCCEEDED/hash_version=legacy-v0 的旧机制记录，不会清理 canonical-v1，不能误报“所有幂等记录七天都被删”。但通用写工具在七天后失去原 key 身份，旧请求可作为新写入执行。canonical-v1 则没有这条清理路径，需要容量规划。

普通 upload.complete 在检查 COMPLETED 前先检查 15 分钟 expires_at；cleanup 又删除全部过期 intent。已上传成功但稍晚重试可能收到过期/不存在，继而重新上传。不能简单把 COMPLETED 判断移到所有检查之前，否则会绕过当前权限和内容一致性。

修复：区分业务执行期限、完整响应保留期、操作身份保留期。已过执行期限不得新执行；已成功且当前仍有权，可返回原状态和稳定资源引用，但不谎称已失效临时文件仍可关联。完整响应压缩/删除后保留最小 tombstone（主体、操作、key、摘要版本、结果引用、终态），过期重试返回原引用或明确不可重放，不能自动重复写。

upload intent 清理拆分为 PREPARED 超期清理与 COMPLETED 结果归档；完成后已关联的附件查询应指向发布关系。原暂存附件本身已过期时返回明确状态，不延长旧文件生命、不创建第二份。tombstone 保存期限按业务历史确定并可配置，最终删除意味着不再保证该窗口外防重，必须在协议中披露。

### 21.5 I05：requestId 大小写与数据库排序规则不一致 / P2

V3.7.2_21 和 _110 使用 utf8mb4_general_ci，唯一键包含 request_id；Java/Schema 接受大小写但未声明是否同一身份。因此 `JobA` 与 `joba` 在数据库可被视为相同，而客户端可能按不同 key 使用。部署库实际 collation 仍需查询确认。

修复：新契约规定区分大小写，request_id 采用二进制比较的列排序规则或合适的二进制存储；新迁移核对所有相关索引和长度。旧请求在旧语义命中时进入兼容查找/对账，不因升级执行第二次；保留原身份与别名映射。禁止全库改排序规则或批量转小写影响其他字段。

验收：大小写 key、点/冒号、空白、边界长度在两种客户端、两种入口、数据库唯一索引中语义一致。

### 21.6 I06：旧工具名向内部操作名的映射不完整 / P1

OperationExecutor.execute 用 `metersphere.`+operation 查旧记录。对 functional.execution.submit 等同名映射成立；对 ExternalImageService 的 `external.image.case/bug/execution`，实际公开名分别为 functional.attachment.import_url、bug.attachment.import_url、functional.execution.evidence.import_url，不能由该拼接命中。

这是映射缺口，不代表已确认存在相应旧线上记录。实施前按公开名查询旧记录数量/状态；有旧成功或未知结果时，禁止当作从未执行。

修复：建立显式 OperationDescriptor（canonicalName、publicNames、legacyAliases、hashVersions、replayAuthorizer）。先查兼容记录并锁定身份，能核实原结果则回放/迁移引用；不能核实则返回需要对账，不自动重做。URL 导入外层与证据上传内层不得随意共用操作名；建立父子 operationId 以便追踪。

### 21.7 I07：外部副作用与数据库幂等不是同一事务 / 风险待复现

URL 普通附件导入调用 TempAttachmentService.upload，后者上传文件存储后再写数据库。数据库回滚不等于对象存储自动回滚。但项目已有 AgentUploadStorageGuard，不能只凭 upload 调用就判定产生永久孤儿文件。

进一步阅读 AgentUploadStorageGuard 已确认：reserve 使用独立事务保留文件身份，调用事务锁住 guard；published 删除 guard；清理器对超过 30 分钟记录使用 FOR UPDATE SKIP LOCKED，并检查 agent_temp_attachment 引用后清理。这些是已有补偿保护，不列为缺失实现。剩余需在隔离环境验证外层事务回滚、进程中断、删除存储失败及重复清理的恢复行为。保留该保护，不另造重复清理体系。保持下载在长数据库事务外，不为幂等持锁下载网络图片。

URL 的 Hash 应绑定用户请求语义；首次成功后同 URL 重试返回首次结果，不重新抓取并覆盖。首次没有成功且 URL 内容变化时，需固定已暂存内容或明确本次未成功，不能宣称两次图片相同。该场景补断网、源内容变化和存储故障测试。

### 21.8 已核查的保护与禁止误改

OperationExecutor 的业务写入与成功记录同事务、唯一键锁行和重放授权应保留；import precheck 已在事务内锁用户并复查，不能仅因事务前查询就误报重复创建。其摘要计算在补默认 versionId/importLocale 前，修改规范化时必须保持“调用者请求身份”与“服务端冻结定义”分开，避免重试受后来默认配置影响。

同 requestId 不同参数仍报冲突；失败事务无已提交副作用时允许原 key 重试。未知结果不重做，不能吞异常后写 SUCCEEDED。幂等状态 RUNNING/UNKNOWN 不能仅因时间到期直接删除后执行。

## 22. 文件改造、迁移与验收矩阵

### 22.1 实施文件分组

| 分组 | 现有文件/新增模型 | 主要改动 |
|---|---|---|
| 定义与事实 | AgentDirectExecutionService、AgentCaseDTO、ExportService、ViewProvider；新增定义模型/构造器 | 查询与定义隔离，版本化事实兼容 |
| 证据 | DirectExecutionTools、ExternalImageTools/Service、GlobalQualityGate、EvidenceStore | CASE 规则、双上传入口、受控读取 |
| 客户端 | metersphere-mcp/src/tools/caseLibrary.ts、client.ts、index.ts、README 和测试 | Schema、导入联动、图片响应、安全错误 |
| 幂等基础 | AgentMcpStreamableService、AgentIdempotencyService、AgentOperationExecutor、Mapper XML | 统一身份/摘要、别名、当前鉴权、原子响应 |
| 业务状态机 | AgentAttachmentUploadIntentService、AgentCaseImportService、计划写入服务及相关控制器 | 完成重放、过期、同层 REST 防重 |
| 页面与文档 | execution-facts、附件预览及生成技能包的实际模板 | 新旧事实适配、清晰 ID、清除旧说明 |

迁移使用实施时下一个未占用序号；此前建议 131 仅是当时观察，不能固定覆盖他人迁移。分开提交 CASE 证据字段、import request_id 长度、幂等身份比较/别名/tombstone 所需字段。先核对生产数据和 MySQL 索引约束，再添加新迁移，不改旧脚本。不要在方案阶段虚构已存在的新表。

### 22.2 新增验收（与 T01～T20 一起执行）

| 编号 | 场景 | 验收证据 |
|---|---|---|
| T21 | 连续执行三次，仅改运行状态/显示字段 | 指纹稳定、定义无历史嵌套、体积不累计 |
| T22 | 改真实步骤、混合版本证据 | 冲突、无消费和发布 |
| T23 | requestId 长度 1/64/65/128/129，含点冒号、空白、大小写 | Node/原生/REST/数据库规则一致 |
| T24 | 通用写工具两个并发相同 key，及提交后断开响应 | 一个业务写入、原结果 ID 一致 |
| T25 | 同 key 改参数、嵌套对象换键顺序、集合与有序数组 | 真变化冲突；语义相同不假冲突 |
| T26 | sha256 大小写等价；upload.complete 在完成后超时重试 | 原资源状态可查，不产生重复文件 |
| T27 | 七天边界、响应归档、tombstone、UNKNOWN | 不因清缓存自动重做 |
| T28 | 用户撤权、项目范围变更、资源删除后重放 | 当前资源授权生效，返回不泄漏 |
| T29 | 三种旧 URL 公开名记录迁移 | 原结果回放或对账，不重复导入 |
| T30 | Node 与原生导入新建/指定组追加/仅导入/联动失败重试 | 两端同能力，导入项不重复 |
| T31 | HTTP/Holder/RPC 错误及网络超时 | 业务码和 traceId 保留，未知结果明确 |
| T32 | MCP 查询→取原图→校验摘要；解除关联及跨项目读取 | 正向可读，越权拒绝，历史保留规则正确 |
| T33 | 文本型/空预期/101 原步骤/1001 记录 | 与公布能力一致，无隐式截断 |
| T34 | URL 变化、下载失败、存储异常、提交回滚、进程中断恢复 | 无重复发布，可追踪和清理暂存资源 |
| T35 | Node tools 与原生 tools/list 契约及下载技能包 | 必填/枚举/说明一致，无旧逐步强制规则 |

数据库并发、排序规则和过期测试必须使用专用真实 MySQL；通过可注入时钟验证时间边界，不等待真实七天，不修改生产时钟。网络、存储故障测试须在隔离环境注入，不对在线用户数据做破坏实验。

## 23. 交付顺序与本次记录

1. 固定工作区基线，保留已有未提交代码，建立所有写工具分类清单及 REST 对照，先增加失败回归测试。
2. 交付定义快照、统一 requestId/摘要和安全错误，再交付用例级图片上传/提交/读图闭环。
3. 补 Node 导入联动；按工具迁移通用写幂等、旧名兼容及期限治理。I01/I02/I04/I06 的发布风险必须验证后才能放行相关写工具，不因拆批遗漏。
4. 验证数据迁移与旧版本兼容，逐批发布；新身份语义和 CASE 数据落库后只能回退到可读新旧协议的兼容版本，不能删除幂等记录来回滚。
5. 低资源串行运行针对性测试、构建、迁移、单环境健康检查和核心端到端；未受修改影响且已有可信证据的构建不重复运行。不同时启动多套环境。

本次实际执行为 Get-Content/rg 源码与迁移检索、方案文档编辑及 git diff --check；没有业务代码修改、数据库写入、构建、容器或线上测试。不存在把本次源码分析算成并发测试通过的结论。第 14 节原命令继续作为计划；实施后加入实际新增测试类和客户端测试，记录未执行项、原因、风险和准确复现命令。

本次方案交付：已整合扩展需求、I01～I07 和新增验收；修复功能状态仍为**尚未实施/部分完成**。存储补偿、部署库排序规则、旧名历史记录及各业务自身去重仍需实施前取证。复审新增问题和交付条件见第 24 节。

## 24. 再次对照代码后的修订与实施门槛

### 24.1 复审结论与范围划分

原方案覆盖了主要业务方向，但原先仍存在实现歧义和漏项，不能据此直接宣称可完整交付。本节补充其必须满足的设计约束。

本轮核心范围为结构化个人直接执行：保留当前 1～100 个原步骤、最多 1000 条提交记录，支持一图提交，并完成查询、权限、幂等和计划状态一致性。文本型用例的完整执行是后续扩展；本轮只交付可发现的不支持原因。调整这些上限须另有容量证据，不能由 Schema 的 1000 误推支持 1000 个原步骤。

R01～R08、X01～X10、I01～I07 分别独立追踪；下面以 A 编号记录本次新增修正。未完成的依赖不得因文档写了“复用”就算已实现。

### 24.2 A01：数量限制与旧客户端兼容不能相互矛盾

第 5.3 节已明确新格式最多 10 张、旧步骤数组各最多 10 张、兼容合并最多 1000 张且总量 50 MiB。旧服务原本允许跨步骤合计超过 10 张，不能把顶层限制误套到所有旧请求导致回归。

解析器先限制 JSON 请求大小、步骤条数、各原始数组长度，再收集和去重；同图重复引用只计一份存储和附件。服务端必须校验所有步骤对象、ID 类型和长度，不能只依赖公开 Schema（当前 callToolInternal 直接 execute，并未进行通用 JSON Schema 校验）。Node、原生和 REST 复用同一业务验证器。步骤级空数组允许，但所有来源合并后无证据拒绝。

保留 facts 1 MiB 总预算、图片单文件 5 MiB、图片总量 50 MiB；1000 条 × 20000 字的字段上限不保证聚合后可提交。contract 公布总事实预算和安全错误，达到预算时拒绝整个提交，不能截断实际结果或通过删历史定义绕过。预算检查覆盖兼容字段、策略及定义快照。

### 24.3 A02：指纹算法版本必须落库，旧证据不能猜测重算

现有 _102 证据表只有 content_hash，没有指纹版本或冻结定义字段；仅新增 evidence_scope 不足以实现第 17 节的算法升级。

新迁移为证据和提交补充 definition_hash_version（历史默认标记 legacy-dto-v1），新 CASE 上传显式写入 definition-v2；提交还记录 facts_schema_version。冻结定义可随证据保存结构化快照或引用独立不可变定义快照；实施时选定一种并给出真实表结构、容量和清理规则，不能只留模型名。建议本轮直接保存受大小限制的 definition_snapshot，避免新建整套快照服务。

新证据以同一算法对比当前业务定义。历史已发布 facts 不重算、不改写。历史 STEP 暂存证据只有在能使用原算法可靠验证时才允许消费；由于原 Hash 包含运行事实，如果历史上下文已无法重建，返回明确“旧暂存版本无法确认，请重新上传”，不能直接把它改成 definition-v2 或跳过版本验证。该失败不消费证据；这是安全兼容边界，不承诺所有历史暂存图片一定可复用。

提交事务中的兼容解析只能识别已支持的算法版本；未知版本拒绝并保留数据。新旧算法分支和幂等请求摘要版本是不同概念，分别存储，不能混用同一个 hash_version 字段解释两者。

### 24.4 A03：查询 DTO 与现有页面必须有真实适配

当前 ms-execution-facts/index.vue 仅读取 facts.steps，且每步显示“关联证据 N 项”；无 steps 时组件不显示。不能写入 stepResults 后只声称页面已兼容。

新协议保留 steps；新增顶层用例证据区及明确“本次用例执行证据”说明，移除对 CASE 记录逐步骤零张图的误导提示。旧 STEP 记录仍显示原映射，不能伪造历史 CASE 归属。统一读取适配器处理旧 case/policy 和新 definition/policySnapshot，MCP get、用例页面及计划页面共享适配后的语义。

列表、详情、历史和归档计划的事实来源需分别检查：非归档计划按既有源跟随规则刷新；归档计划保持其冻结事实，不借本次升级覆盖历史。每个入口补 loading、空态、解密/格式错误和读图失败展示。

### 24.5 A04：原生图片响应不能用现有 JSON 包装器直接返回

AgentMcpStreamableService.toolResponse 当前把任何 handler 结果包装成 text JSON 和 structuredContent；Node client 的 content 类型也只声明 text。即使 handler 返回图片对象，现状仍会被序列化为普通文本，不能算 MCP 图片读取已实现。

本轮选定明确协议：新增 metersphere.functional.attachment.read，输入 projectId/caseId/fileId，可选 submissionId 指定历史执行；输出标准 MCP image 内容（data、mimeType），并附安全文本元数据。最大原图 5 MiB，计入 Base64 膨胀和网关响应预算。超过传输预算返回明确错误，不悄悄裁切原证据。普通非图片附件返回明确类型不支持，二进制通用下载另行扩展。

实现显式的 MCP 内容结果类型，仅该类型绕过普通 JSON 包装；其他工具输出保持不变。更新 Java handler/dispatcher 结果处理、Node 内容联合类型和透传、outputSchema 适配，确保 structuredContent 不重复承载大图。不能对任意业务 Map 自动识别 image，否则会引入输出混淆。

普通附件读取校验有效 case/attachment 关系；历史证据读取额外校验 submission 属于所选 case/project、submission_evidence 确实引用该文件及发布状态。现有 EvidenceStore.read 强制 JOIN functional_case_attachment，解除关联后无法按该路径读历史，因此新增历史分支，而不是移除所有关联约束。项目读权限不等于只能读取本人历史：暂存上传/消费严格同用户同 Token，已发布执行按项目用例读权限共享；若未来增加私有执行，再引入独立可见性规则。

### 24.6 A05：直接 SQL 更新遗漏计划执行状态同步

AgentDirectExecutionService.submit 当前仅直接 UPDATE functional_case 的最后执行字段。FunctionalCaseService 的常规执行更新则调用 syncAssociatedPlanCaseExec；PlanCaseEffectiveViewService 只覆盖部分详情视图，不能据此证明计划列表、筛选和统计的持久化行同步。

新增共享的源用例执行发布方法，接收 result、executor、同一执行时间及稳定执行引用，在当前提交事务中更新用例并复用计划同步语义；不得调用会覆盖原始步骤定义的普通编辑接口。同步方法应支持传入同一个 now，不能由各层重新生成不同毫秒时间。补测关联计划列表、详情、筛选、统计及附件显示的一致性。

先检查 extFunctionalCaseMapper.syncPlanExecByCaseIds 的归档过滤及统计更新逻辑，再决定复用或收窄；禁止盲目同步所有关联覆盖归档结果。同步采用事务内写入，或项目已有可靠 outbox 并明确 PENDING/失败状态；未找到可靠机制时不写“最终一致已保证”。失败时不发布部分成功，也不修改评审结果。MCP 重放不再次更新执行时间、不重复发送事件。

### 24.7 A06：毫秒时间不能作为唯一执行身份

AgentDirectExecutionViewProvider.current 通过 case/project/create_time 与 lastExecuteTime 相等选记录，并 ORDER BY id LIMIT 1。UUID 排序不是执行顺序；相同毫秒多次执行、普通执行与直接执行切换时，单靠时间不能稳定确定当前事实。

在共享执行发布层保存明确 current_execution_type/current_execution_id（可采用项目既有等价引用，实施先检索）；直接提交指向 submissionId，普通执行明确切换 SOURCE_CASE/其他来源。与结果更新同事务；provider 优先按引用读取并验证归属和发布状态。所有会更新执行结果的入口都必须维护或清除该引用，不能只改 MCP。

历史记录没有引用时，仅在时间匹配唯一且来源可确认时兼容读取；匹配多条不猜选，返回可理解的未知引用状态并记录日志。新提交幂等重试只回放原引用。此设计不能用“把时间精度调高”替代。

### 24.8 A07：完整 JSON 正则脱敏可能破坏事实读取

report 和 ViewProvider 均先对完整 JSON 字符串调用 AgentSensitiveDataSanitizer.sanitize 再解析。其正则的值匹配包含引号，可把实际结果 password=abc 后的 JSON 结束引号吞掉。

本轮使用 PowerShell 对等正则做了合成文本检查：输入 `{"actual":"password=abc"}` 变为 `{"actual":"password=***}`，ConvertFrom-Json 失败。这是合成规则验证，不是 Java 后端或线上复现；源码存在同类模式，需要服务端回归确认。

修复为先解析 JSON，再递归处理允许展示的字符串值/敏感属性，最后由 JSON 序列化器输出；不对序列化结构直接做文本替换。原始受保护审计数据与脱敏展示分离，不因展示脱敏改变定义指纹、请求 Hash 或步骤结果状态。历史已损坏数据不能静默当空事实成功返回，应给安全错误和 traceId。图片本身可能包含敏感信息，本轮只做权限保护，不宣称文本脱敏会自动处理图片内容。

新增 AgentSensitiveDataSanitizer 的对象级入口，并替换 report/provider 及检索到的同类调用点；覆盖引号、换行、中文、cookie/token、嵌套结构、空值和未知字段。敏感属性采用受控规则，避免吞掉测试正文或改变业务结果。

### 24.9 A08：操作状态查询与过期重放必须有明确接口

原方案要求超时先查询，但 submit 仅返回成功后才知道 submissionId；没有定义如何由丢失响应的 requestId 找回。

新增 metersphere.operation.get：输入受控公开工具名和 requestId（不允许客户端传任意数据库 operation 名），通过 OperationDescriptor 查 canonical/legacy 身份，重新做当前资源授权。返回 NOT_FOUND/IN_PROGRESS/SUCCEEDED/UNKNOWN/EXPIRED 及稳定业务引用、可安全重放标记，不回传凭证或原始参数。状态映射以现有记录为准，不能宣称未提交 RUNNING 行一定可见。

原子事务中的 RUNNING 未提交时可能查不到；NOT_FOUND 明确表示“暂无可见记录，不证明此前请求没有执行”，允许原 key 重试，不允许换 key。查询遇到别名多条、Hash 版本未知或结果不可验证则 UNKNOWN/需对账。对没有独立资源上下文可鉴权的历史记录，拒绝返回详情或仅给不泄漏的状态。必要的 projectId/resource 作为受校验上下文或记录的安全授权元数据，不从未验证的结果字符串猜测。

完成上传但证据已过期/消费：重放保持原 evidenceId 和原操作结果，另标 resourceState=EXPIRED/CONSUMED 及 expiresAt，不延长期限，不伪装仍可提交。底层文件已清理时保留操作引用和安全状态；已完成 submit 重放不因证据后来已消费而再次执行有效性校验，但当前主体/项目授权仍检查。

### 24.10 A09：隐藏权限依赖与暂存额度

DirectExecutionService.source 调用公开 cases.getById，后者额外要求 FUNCTIONAL_READ；写工具公开约定却主要为 FUNCTIONAL_SUBMIT + CASE_ATTACHMENT。新增定义构造器采用经过当前写权限授权后的内部读取，不绕经另一个公开工具的 Token scope。不能为了“修复权限”放宽用户真实用例 READ/UPDATE 角色校验。contract/get/图片读工具仍各自要求对应读 scope；在 tools/list 和下载技能包列清楚最小权限组合。

当前上传额度 SUM 计入所有未消费证据，包括已过期但尚未被定时任务删除的证据；清理每小时最多 1000 条，可能出现过期后仍被占额。采用有效暂存额度和待清理物理容量双指标：有效额度过滤 expires_at>now；另有物理容量硬保护和清理积压告警，不能仅过滤额度后允许磁盘无限增长。重放不重复占额；用相同 Token 锁保护并发配额，按安全批量清理，避免请求内全表清理。

### 24.11 A10：发布状态和策略版本要显式映射

report 当前把除 NEEDS_REVIEW 外的任何 gate_status 都显示 PUBLISHED。新读适配器显式识别已发布 ACCEPTED、历史未发布 NEEDS_REVIEW 及已存在的其他状态；未知值显示 UNKNOWN，不默认成功。旧已接受记录不重新审核；历史未通过记录不补附件，不因显示适配变成已发布。

个人 CASE 规则版本与全局基础策略分别保存。策略获取当前通过全局 singleton FOR UPDATE，提交将用例锁、证据锁与策略锁的获取顺序固定并写入测试；不得持有全局锁进行图片解码、远程下载或大规模查询。上传不获取提交策略锁，提交冻结一个完整生效策略；原子性和并发发布测试需覆盖滚动升级旧节点。

### 24.12 新增验收与发布顺序

| 编号 | 新增场景 | 预期 |
|---|---|---|
| T36 | 旧多步骤合计超过 10 张、混合顶层/步骤、超长重复数组 | 合法旧请求兼容；原始与总量上限生效 |
| T37 | 新旧定义指纹版本、未知版本、旧上下文不可重建 | 按版本验证或明确重新上传；不静默跳过 |
| T38 | CASE/STEP、新旧事实、事实总量超限 | 页面正确展示；超限无截断或部分发布 |
| T39 | 原生/Node 真实 image 内容及网关预算 | 客户端收到图片内容而不是文本对象 |
| T40 | MCP 发布→计划列表/详情/筛选/统计；归档与重放 | 活跃计划一致、归档不变、重放无新时间/事件 |
| T41 | 同毫秒两次提交、直接与普通执行切换 | 稳定 executionId 定位，无法确定不猜测 |
| T42 | 含 password/token/引号/换行的执行事实 | 脱敏后 JSON 可解析、语义字段不破坏 |
| T43 | 丢失响应后仅凭 requestId 查询、并发尚不可见 | 找回引用或明确不确定；不自动换 key |
| T44 | 成功上传后过期/消费/清理再重放 | 原身份不变，资源状态准确，无重复上传 |
| T45 | 最小写 scope、最小读 scope、撤销项目权限 | 无隐藏 scope 依赖；真实授权不放宽 |
| T46 | 暂存已过期未清理、积压和并发占额 | 额度可解释，物理容量仍受保护 |
| T47 | 历史 NEEDS_REVIEW、未知 gate_status、策略并发发布 | 不误报 PUBLISHED，策略快照完整 |

发布采用 expand → 所有节点可读新旧数据 → 验证数据库索引和别名 → 开启 CASE/新身份写入 → 升级 Node/文档/技能包。多节点混跑时不得提前开启新证据或新 key 大小写语义；旧节点可能无法理解新数据或执行另一套去重。迁移需有开关、版本检测及回退验证，退回仅支持 STEP 的旧服务不可作为有效回滚。

本轮改动仅本方案文档；实际执行 Get-Content/rg、合成正则与 JSON 解析检查、文档结构检查。未执行 Java/Node 测试、类型检查、构建、真实数据库迁移、Docker 或 E2E；其运行结论仍待验证。方案已补充以上设计约束，功能状态仍为部分完成/待实施，不将本次审查当作运行验收。


## 25. 本轮实施记录（2026-10-27）

本节记录实际状态；第 1～24 节中的“待实施”描述保留为设计基线，不能据此认为所有内容均已交付。详细代码、验证证据与遗留项见 [实施报告](personal-execution-case-evidence-implementation-20261027.md)。

已落地：CASE 级图片上传/提交、定义白名单及冻结快照、覆盖与结果门禁、图片读取、稳定执行身份与活动计划同步、JSON 脱敏、请求契约统一、个人执行操作查询、业务 MCP 原子幂等与当前权限复核、导入/上传身份迁移、Node 0.8.0 与事实面板适配。

仍未完成：全部 Agent REST 写接口共用幂等协议、所有业务操作统一查询、canonical 记录归档与容量告警、完整 Maven/前端生产构建、完整 Flyway 链、镜像启动健康检查，以及新版本真实 HTTP/页面/MCP 联调验收。不得将本轮标记为整体完成。

发布约束：停止并排空写流量，备份与核查历史 requestId/别名冲突，执行完整迁移，统一升级所有节点与 Node 客户端，再开放写入。不得以新旧节点混跑作为灰度方式；新增 CASE 数据后不得回退到只理解 STEP 的旧版本。两个写开关均为启动配置，不能替代动态流量控制。
