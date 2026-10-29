# MSP 用例正文压缩与复测遗留问题详细修复方案

日期：2026-10-28。状态：方案已整理；产品整改整体仍为部分完成。本文新增设计均为待实施，不将设计代码视作已实现。

## 1. 范围、基线与结论

本方案合并本次历史用例详情故障与此前用户要求“按建议修复”的遗留工作，基于当前工作区，而非仅基于已提交 HEAD。当前存在尚未提交的产品代码、测试和迁移；实施时必须保留，不能重置覆盖。

依据：

- [本周复测报告](msp-weekly-commit-regression-results-20261028.md)。
- [已部署版本复测报告](msp-deployed-regression-results-20261028.md)。
- [整改评审基线](msp-weekly-regression-remediation-review-20261028.md)。
- [本轮代码实施记录](msp-weekly-remediation-implementation-20261028.md)。
- [既有验证摘要](../testing/msp-weekly-fixes-20261028/validation-summary.json)。
- 用户提供的服务器异常日志、用例数据库查询与 370 字节 HEX；日志 traceId 为 cabd5523-01e2-4c73-8916-1c45cb31447f。

结论：仍有未完成工作。必须区分“尚缺代码”“已有整改代码但尚未完整验收”“需要环境配置或历史数据恢复”，不能将这些一概描述为已修复或都未实现。本文不重新统计历史报告中的通过条数，也不把未覆盖的测试矩阵直接判定成代码缺陷。

## 2. 需求与实现追踪总表

路径简写：CASE=`backend/services/case-management/src/main/java/io/metersphere/functional`；AGENT=`backend/services/agent-integration/src/main/java/io/metersphere/agent`；SYSTEM=`backend/services/system-setting/src/main/java/io/metersphere/system`。以下文件均相对于仓库根目录。

|编号|需求与入口|前端/API/后端对应|数据或配置|当前状态与下一步|
|---|---|---|---|---|
|C1 / R6|历史详情、MCP get/search、导出|详情抽屉 → `/functional/case/detail/{id}` → FunctionalCaseService；Agent 搜索/导出 enrichment|functional_case_blob 五个正文字段|仅安全提示已有；压缩规范化、实际损坏记录恢复尚未实施，见第 3～7 节|
|R1|执行事实敏感保护|MCP submit/get → AgentSensitiveDataSanitizer / AgentFactCipher|既有 facts-key Secret、历史事实|递归识别和一致加密判定已有代码；环境密钥、历史扫描及真实持久化验收未完成|
|R2|导入停止/重试不绕过提交|importJob → AgentCaseImportController → AgentCaseImportService / followup|迁移 135 committed_at、stopped_from|阶段门禁已改；真实多客户端并发、关联失败恢复及旧任务升级仍待验收|
|R3|源/计划/资产共享状态一致|源修改、附件、执行 → outbox → DefaultHubCaseSyncService；资产 readiness|迁移 136、来源关系、可靠事件|部分漏点及映射已修；缺陷、解绑、删除恢复、全字段投影等仍未完整闭环|
|R4|资产导出等级|资产导出 → FunctionalCaseFileService / ExportRowMapper|functional_priority 语义映射|已有按批查询映射和单测；真实 XLSX 全字段比对未完成|
|R5|MCP 客户端实际契约一致|原生 tools/list、Node stdio、技能下载|Node 0.8.2、连接器端点/缓存|代码版本声明修复和 stdio 测试已有；发布、实际绑定与双客户端业务回放未完成|
|R7|Token 列可操作、Demo 样式、窄屏布局|Agent 配置 MsCard、case/plan/review → MsTable|无业务迁移|已有样式代码；部署后多视口、缩放、真实操作未验收|
|UX1|全选/取消全选所有页|batchAction → base-table → useTable 原 ALL/CANCEL_ALL|无新增 API|代码及隔离组件证据已有；真实跨页批量请求及切筛选验收未完成|
|UX2|计划名称自适应、概览对齐|caseTable → 原 useSinglePlanOverview 和统计 API|无新增 API|自然宽度及同组不拆行已改；上线/缩放/慢响应验收未完成|

## 3. 压缩问题的证据与原因边界

### 3.1 已证实

故障用例：ID 1712038715113627712，编号 100025，STEP 模式。服务器调用栈明确落在旧版本 `FunctionalCaseService.getFunctionalCaseDetail:525` 的 `JSON.parseArray(caseDetailSteps, FunctionalCaseStepDTO.class)`；同一记录多次 500。

用户查询所得 steps 非 NULL，370 字节。原始 HEX 以 `504B0304` 开头；本地 ZIP 解压成功，条目为 `zip`，得到 457 字节。该内容仍以 ZIP 头开头，内层含 `EF BF BD` UTF-8 替换字符。外层可读不代表内层可恢复；出现替换字符是二进制曾经发生有损文本转换的证据线索，不能据此断言具体操作来源或恢复成功。

当前代码已有自动压缩设计：

- `SYSTEM/config/interceptor/FunctionalCaseInterceptor.java` 为 FunctionalCaseBlob 的 steps、textDescription、expectedResult、prerequisite、description 注册 CompressUtils.zip/unzip。
- `CASE/config/FunctionalCaseManageInterceptor.java` 为 FunctionalCaseMindDTO 正文字段注册相同转换。
- `backend/framework/sdk/src/main/java/io/metersphere/sdk/util/CompressUtils.java` 的 zip 使用名为 `zip` 的条目，与样本一致；unzip 只处理当前一层，异常被吞掉并返回剩余或原数据。
- `SYSTEM/interceptor/MybatisInterceptor.java` 在 mapper 写入前 process、查询后 undo，按运行时类型匹配配置。
- FunctionalCaseService 在 mapper 查询后将 steps 按 UTF-8 转 String 再解析数组；因此拿到内层 ZIP/损坏字节就会失败。

所以：数据库中单层 ZIP 是正常设计，不能以 JSON_VALID(blob)=0 判断坏数据；此次不能简单描述为系统完全没有解压。前述“请求文本直接保存导致坏 JSON”也不足以解释这个具体样本。

### 3.2 尚待复现锁定的产生路径

候选机制：原始压缩字节被 JDBC/特殊 DTO 查询绕过解压，然后在编辑/导入/同步/快照代码中转成 UTF-8 字符串，再经过 mapper 二次压缩。也可能是其他历史路径错误变换。需要用实际路径复现后才能冻结根因。

重点排查：

1. `FunctionalCaseService` 新增、修改、saveImportData、getAgentCaseDetails；是否存在压缩字节转字符串。
2. `DefaultHubCaseSyncService.insertBlob/updateHubMirror`、`DefaultHubCaseImportService`；输入对象来自哪个查询，是否确实已经解压。
3. `CaseAssetService` 回写源与脱离来源编辑、FunctionalCaseResourceEditAdapter。
4. FunctionalCaseAIService、FunctionalCaseAiDraftService、XMind/Excel 解析器、快照与归档构造。
5. MybatisInterceptor 参数 Map 别名、返回 ArrayList 与其他 List 实现、一级/二级缓存、代理是否重复注册。当前 process 会修改 Map；同一对象多别名是需测试风险，尚不能认定为本次根因。当前 ArrayList 分支遇首个不处理对象即 break，也需验证混合结果是否截断，但不得直接扩大为全局重写。

复现时只记录 caseId、mappedStatementId、字段、字节长度、头部类型、摘要、解压层数；不记录完整正文/凭据。用合成正文，依次执行新增→查询→编辑→复制→资产同步→回写→导出→再次读取；每一步用原始 JDBC 检查存储，mapper 检查逻辑内容，找到首次变化点。

## 4. 压缩代码的具体改造

### 4.1 存储契约与职责

保持数据库现有单层 ZIP 格式，不迁移成全库明文。业务层持有逻辑 UTF-8 正文；mapper 配置负责逻辑字节与存储字节转换。新增实现限定于功能用例，先不改变所有模块使用的公共 CompressUtils 行为。

新增建议文件：

- `SYSTEM/utils/FunctionalCaseBlobCodec.java`：公共类型只依赖 JDK/既有 SDK，避免 system-setting 反向依赖 case-management；提供 public static Object pack(Object)、unpack(Object)，适配现有拦截器反射签名。
- `CASE/utils/FunctionalCaseBodyValidator.java`：步骤结构与模式校验，复用 FunctionalCaseStepDTO、既有 JSON 配置和请求限制。
- `CASE/service/FunctionalCaseBodyService.java`：集中正文构建、解码后验证、复制；不另做第二次压缩。

新类名称是方案建议，尚未存在；实现前检查模块依赖，若 CASE 不能使用 SYSTEM 的 codec，放到已有共同下层 SDK 的新独立类，保持配置仍仅对功能用例生效。

### 4.2 Codec 算法与接口

建议保守初值：最多 3 层 ZIP、每字段解压累计输出最多 8 MiB、只接受一个名为 zip 的文件条目、禁止目录/额外条目；与现有接口限制取更严格者。正式合入前统计最大合法历史正文并确定常量，不以拒绝正常历史数据为代价。归档 content 可能大于单字段限制，不能直接套同一阈值。

```java
// 设计示意，非可直接复制的完整实现。
public static Object pack(Object value) {
    // null 保留；非 byte[] 按既有反射契约返回，不修改调用者对象。
    // byte[] 为逻辑正文：严格 UTF-8 校验；拒绝 ZIP 头，要求调用者先正规读取。
    // 使用既有单条目 ZIP 格式写入；失败必须抛可识别领域异常，不能返回成功。
}
public static Object unpack(Object value) {
    // null 保留；复制输入引用边界，避免改缓存实体。
    // 明文：严格 UTF-8 解码验证后返回逻辑字节，兼容历史明文。
    // ZIP：分层有界解压，校验条目、CRC、大小、层数；尚为 ZIP 则继续。
    // 得到逻辑字节后严格 UTF-8 校验；错误/超限直接抛异常，不返回压缩残留。
}
```

关键实现细节：

- UTF-8 使用 CharsetDecoder + CodingErrorAction.REPORT，禁止 new String 对非法字节静默替换。
- ZIP 使用 try-with-resources；读取循环每次累加实际字节，达到限制立即中止；不能仅信任 ZipEntry.getSize()。
- 不调用 extractToDisk，不需要解压路径；正确关闭/读完条目以触发 CRC 检查。
- ZIP 头仅作分支检测，不等于内容有效；PK 开头的非法内容必须拒绝。
- EF BF BD 出现本身不等同损坏，因为合法正文可能含该字符；只有结合 ZIP 结构/CRC/解码失败判断。不能尝试删除替换字符猜测恢复。
- unpack 对已经解码的合法内容应幂等；pack 不接受存储字节，所以不会用“遇 ZIP 就原样存储”掩盖调用错误。
- 返回错误只含稳定业务码和可读提示；CRC、内部路径、原始字节等只进入受控日志。沿用统一 code/message/details/traceId 协议。

### 4.3 替换两处配置，避免双重处理

修改 `SYSTEM/config/interceptor/FunctionalCaseInterceptor.java` 中 FunctionalCaseBlob 五字段配置，将 CompressUtils.zip/unzip 改为新 codec.pack/unpack。CaseReviewHistory、CaseReviewFunctionalCaseArchive.content 暂不切换，先用测试确认其结构和容量，另行适配，避免影响归档。

修改 `CASE/config/FunctionalCaseManageInterceptor.java` 中 FunctionalCaseMindDTO 对应正文字段，复用同一 codec。`executeSteps`/`content` 先明确是否同一编码与容量契约，再决定采用单独配置，不能机械全部替换。

禁止在 FunctionalCaseService 再重复 unpack 所有 mapper 结果；对于绕过 mapper 的 JDBC 路径，必须明确调用 codec，且不得将存储字节直接 new String。对 codec 异常在业务边界翻译为既有 MSException/错误协议；若异常先在拦截器抛出，统一处理器需只映射该明确异常类型，不能将所有 RuntimeException 都改成数据异常。

如集成测试证实通用拦截器的 Map 别名、重复代理或结果列表有问题，再单独修改 MybatisInterceptor：按对象身份去重，复制 Map 而非修改调用者参数，不截断混合 List；补 API/计划/加密配置回归后合入。没有复现证据不先进行高风险公共重构。

### 4.4 统一业务校验与写入口

`FunctionalCaseBodyValidator.validate(mode, steps, textDescription, expectedResult)` 返回规范化的逻辑内容或领域错误：

- STEP：遵循当前契约的空步骤规则；非空必须为数组，元素非 null，字段类型符合 DTO；未知字段按既有兼容规则，不擅自丢失扩展字段。
- TEXT：校验文本字段与大小；不把历史 steps 强行转换成空数组后回写，也不把原 HTML 当作步骤 JSON。
- 严格验证 JSON 尾部没有额外内容，限制深度、字节与步骤数量，错误定位只返回字段/行号等安全内容。
- 新增/修改在写主表前校验；导入预检和实际写入均校验，预检成功不能取代提交时校验。
- MCP、Web、AI 草稿确认、资产回写统一使用该校验；非法输入零写入、零 outbox 成功事件。
- ID 缺失的历史步骤可按现有规则补 ID，但读取不能随机生成后自动保存影响旧快照；写时规范化一次，查询稳定性单独测试。

主要修改：FunctionalCaseService.add/update/saveImportData/getFunctionalCaseDetail/getAgentCaseDetails；FunctionalCaseAIService、FunctionalCaseAiDraftService 的最终保存入口；资产复制/回写与导入落库入口。正文、主表版本更新及 outbox 必须在同一事务。

### 4.5 前端与 MCP 失败行为

已有 ms-prev-next-button.failed → ms-detail-drawer 安全错误展示，保留并补部署验收。补充明确“用例步骤内容异常，原始内容已保留，请联系管理员修复”、重试和 traceId；无数据/加载/无权限与内容异常分开。

MCP 单条 get 返回明确业务失败；搜索 enrichment 逐条隔离错误，沿用现有分页部分结果协议添加 warnings（对象 ID、稳定错误码，不含正文）；若当前协议不支持部分结果，先扩展并同步原生、Node、技能文档，不能悄悄省略失败记录或返回空步骤成功。导出任务列出失败用例，不能任务成功但静默缺行。

## 5. 历史数据诊断与恢复

新增离线工具建议：`scripts/maintenance/diagnose-functional-case-body` 与 `repair-functional-case-body`（具体语言按部署可用 runtime 选定）。诊断默认只读，直接 JDBC/数据库驱动读取原始 BLOB，不经过自动解压 mapper。

输出清单：caseId、字段、原始 SHA-256、长度、解压层数、类型（PLAIN_VALID/SINGLE_ZIP_VALID/NESTED_RECOVERABLE/CORRUPT/OVERSIZE）、验证结果、建议；正文不写普通日志。覆盖五个正文列，同时仅对 STEP.steps 做数组结构校验。扫描按主键游标分页，每批初始 100 条，可限速，不全量载入内存。

恢复分支：

1. 合法单层 ZIP：无需修复。
2. 完整可解的嵌套 ZIP：经 dry-run 和字段内容核对，规范化为一层 ZIP；正文语义不变。
3. 损坏内层：不得猜测填充；按用例版本、源/镜像/计划归档、原始 Excel、备份依次找可靠来源。镜像与快照可能也已损坏，不能只因存在副本就采用。
4. 本例先验证 CRC 和结构；如无法恢复，标为 NEEDS_SOURCE，保留原始 370 字节，等待可靠来源。不得承诺仅靠 HEX 恢复。

修复操作必须：

- 先备份原始 BLOB 至受控备份位置，含摘要、时间、操作人、来源；权限与生产备份一致。
- 预览清单确认后执行指定 caseId/字段集合；不能默认扫描即修复全部。
- 每条事务内重新校验原始摘要/版本，使用 CAS；原值变化则冲突退出，禁止覆盖新编辑。
- 通过正文正式写入链路传逻辑字节，由 codec 压缩一次；若离线直接 SQL，则只写已编码的一层 ZIP，不能再走 mapper 压缩。两种模式必须明确分开。
- 真实内容恢复推进 update_time 并事务内发同步事件；只变存储编码且语义完全相同，避免人为生成新的业务执行记录。
- 幂等键采用 caseId+field+beforeHash+afterHash，重复执行不再次更新、不重复投递。
- 记录 SUCCESS/CONFLICT/NEEDS_SOURCE/FAILED，失败不记成功；支持中断恢复。

默认不新增全表正文迁移、不修改现有 Flyway 文件。若需持久化修复审计表，实施时分配新的迁移号（先检查最大版本，不能复用 135/136），字段含对象、前后摘要、备份引用、状态、操作人及时间；正文不进入普通审计消息。

## 6. 压缩修复影响范围与回滚

直接影响：用例详情、MCP 带步骤查询、Excel 导出、XMind、编辑/复制、资产同步/回写。间接影响：计划与评审读取的正文、历史执行快照/归档。执行快照保持历史定义，不因当前源修复而重写旧事实。

发布前：备份→停止相关写入/同步消费或维护窗口→部署所有写实例→验证→恢复消费；不要新旧 codec 混跑写入。新代码仍写单层既有 ZIP，正常记录保持格式兼容。

回滚：先停写；代码回退不会自动撤销已修历史内容。按审计中 afterHash 匹配后才能恢复 before 原始 BLOB；有后续用户编辑的记录不自动回退。恢复压缩链路缺陷后不能继续运行旧的坏写入路径。

## 7. 压缩专项测试与通过标准

|层级|必须覆盖|通过依据|
|---|---|---|
|Codec 单测|明文、单层、两/三层、超层、CRC 错、截断 ZIP、额外条目、超大展开、非法 UTF-8、合法替换字符|正确解码或明确拒绝；无无限解压/吞错|
|真实故障 fixture|本例 HEX 转 byte[]，保存为测试资源并标明脱敏来源|与真实数据同路径触发可识别异常，不能靠 Mock 跳过 codec|
|MyBatis + MySQL 集成|真实插件注册、insert/update/select/DTO/List/Map别名/缓存|原始库始终单层；业务端逻辑正文一致；重复操作无层数增长|
|业务集成|Web/MCP/AI确认/Excel/资产回写，同一正文往返|内容及事务/outbox 一致，失败零写入|
|查询与导出|5 条旧用例 100025～100021，混合正常/异常页|逐条正确返回或明确异常；不能一条坏数据拖垮整页且不说明|
|数据修复|dry-run、CAS冲突、中断、重复、备份回滚|不丢数据、不覆盖新编辑、不重复事件|
|安全/性能|有界解压、日志脱敏、分页限速、多角色授权|无敏感原文泄漏；越权不可读/不可修|

已知限制：本例及其他记录若原始字节已不可逆损坏且没有备份，不能以程序成功运行代替内容恢复；需逐条列明未恢复对象。

## 8. 其他整改的剩余实施方案

### 8.1 R1 敏感执行事实与留存（P1）

已有：AgentSensitiveDataSanitizer/AgentFactCipher 统一递归字符串 JSON 的检测、加密判定、脱敏和深度/大小失败关闭；定向测试通过。缺少：部署密钥验证、历史事实扫描与真实数据库/日志无明文证据。

继续修改或交付：复用现有 facts-key Secret，不提交密钥；readiness 必须区分未配置/可用/版本不可解密；历史扫描沿既有事实表实际 schema 编写工具，按执行/项目鉴权并分页，不在本文虚构表名。分类明文敏感、有效密文、缺密钥密文；明文整改应加密并保留内容，密文缺旧密钥不得用新密钥覆盖。响应始终脱敏；扫描结果仅记录 ID/摘要/类型。需要新增代码时限定在 AGENT/security、事实存储服务和维护工具。

通过：缺密钥敏感写入被拒绝且无业务记录；有密钥数据库为密文，响应/日志无合成密文前原值；重启仍可读；越权/撤权拒绝；到期清理不删除仍被历史执行引用的证据。长期留存需隔离时钟/保留策略测试，不能调整共享环境系统时间。

### 8.2 R2 导入状态机与关联幂等（P1）

已有：迁移 135、committed_at、停止来源、未提交重试回 PRECHECKED、worker 门禁、提交事务。继续检查 AgentCaseImportService、AgentCaseImportController、既有 followup coordinator 的锁与 CAS：提交/停止/重试/worker 竞争只允许一个合法状态转换；已提交策略及关联组持久化，重试不能改成 LEGACY；followup 失败不重新创建用例。

若缺并发约束，使用现有任务行事务锁和条件更新（受影响行数必须等于 1），不单靠前端禁用。计划/评审创建复用关联组与既有幂等标识，事务提交前后崩溃分别恢复。审计旧 STOPPED/RUNNING，无法证明人工提交的任务保留成功项并待确认；不能依据存在 SUCCESS 项回填提交。

前端 importJob 需保留策略、显示 PRECHECKED 待确认/部分失败、独立关联重试；Web与MCP同服务。验收三策略、新建/追加多组、双客户端、运行中停止、失效关联组、缺reviewers、重启；成功项不重复写。

### 8.3 R3 资产与计划全字段一致（P1）

已有：执行/评审状态不再强制重置、hash增加执行字段、Minio上传事件、源update_time推进、执行事实来源引用、迁移136及分批补偿；真实 MySQL 测试已复现并修复新补偿 SQL 混合排序规则问题。

未完整闭环：所有附件入口/解绑、缺陷关联、删除恢复、版本切换、失败收敛、资产保留编辑语义。readiness 目前使用来源 update_time、关系 source_update_time 和 outbox状态；不应描述为完全没有版本比较，但时间戳只能在所有变更入口都推进时可靠。

具体修改清单：

1. 对名称/步骤/TEXT正文/等级/标签/评审/执行/附件/缺陷/删除恢复制作字段矩阵，列出源表、变更服务、事件、投影表与读取方。
2. 每个共享变更在原事务推进同一源 revision 并投递 CASE_SYNC；既有 update_time 单调推进继续复用，若不能覆盖所有写入口再新增 dedicated revision，避免两套版本各算各的。
3. DefaultHubCaseSyncService 映射缺陷关系与附件解绑，用来源身份唯一 upsert；禁止复制源对象 ID 到不允许跨项目的关系，需规范来源引用/授权读取。计划与评审同样核验，不只修资产镜像。
4. worker 成功后才更新 applied revision/source_update_time；失败不推进，旧事件不能覆盖新投影；失败可重试，重试成功不复制有效对象。
5. 软删墓碑及恢复显式同步；用户主动脱离来源后不再被后台覆盖；分类/组织置顶等资产私有字段保持。
6. CaseAssetService readiness 对过期、pending、failed分别显示；失败清单返回安全原因、对象、traceId、可重试性。历史补偿只修确实过期条目，稳定游标与限速。

通过：共享字段逐个新增/修改/解除/恢复对比源、计划、资产；建议10秒收敛作为验收目标（非已有SLA），超时显示处理中或失败；重启/重复消费/并发旧新事件不重复、不回退；正文压缩修复必须先完成，避免补偿传播坏数据。

### 8.4 R4 真实资产导出（P2）

已有 FunctionalCaseFileService 以 functional_priority 语义对齐源/资产模板字段、批量读取，P0～P3/未设置/冲突测试通过。继续用真实 XLSX 比对 ID、名称、等级、标签、分类、步骤、预期与页面/API；不同模板、手工资产、来源绑定分别覆盖。

若分类/标签仍缺映射，在既有 ExportRowMapper/行映射器修正，禁止在鉴权 CaseAssetExportAccess 硬编码等级。正文异常按第4.5节返回失败清单；任务部分失败不能显示完全成功。权限/跨组织/选择范围仍沿用原导出策略，不重新开放宽泛查询。

### 8.5 R5 实际 MCP 契约与客户端（P2）

已有 Node 声明版本读取 package.json，准备0.8.2，stdio 实际工具列表测试通过，未发布。原生、Node、技能版本独立，不能要求版本字符串相同来证明契约一致。

继续：记录实际客户端绑定端点、包版本、tools/list摘要；比较required/requestId/evidenceIds/hash/stepNum/purpose；发布经验证Node产物并刷新实际客户端工具缓存；下载技能示例也跑契约测试。若实际连接器仍暴露旧字段，先定位绑定来源，不在服务端重新强制旧字段迎合缓存。

两客户端分别真实执行创建→用例级证据上传→无hash提交→读取→同key回放；改参冲突、撤权后回放、上传响应丢失均验证。相同业务码语义一致，不要求传输层协议完全同形。发布/绑定与真实回放不完成，不计关闭。

### 8.6 R7 / UX1 / UX2 样式与交互（P2）

已有：Token MsCard auto-width/min-width修复；试点表头和32px批量按钮；窄表格固定列适配；ALL/CANCEL_ALL切换；计划名称自然宽度和同组水平对齐。

继续：

- `frontend/src/views/setting/system/agentIntegration/index.vue`：真实抽屉971/1366/1908宽、125%缩放，横滚可达且启用/操作能点击；不能仅以DOM存在判通过。
- `frontend/src/components/pure/ms-table/base-table.vue`、batchAction.vue：三页真实数据，全选→排除→取消→再次全选；检查selectedKeys/excludeKeys/selectorStatus及发出的批量请求；切项目/目录/筛选不继承旧选择。零数据/加载禁用，原箭头保留。
- `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`：长短名称自适应父容器，不固定260/360上限；计划select、轨道、百分比中心误差≤2px；个人分组可以整体换行，不拆计划半组。极窄局部滚动，页面不被撑宽。
- 同组件/既有 useSinglePlanOverview：快速切换两个非零进度计划，旧请求不得覆盖新计划；失败不伪装0%；空计划明确空态。若测试发现竞争，再加请求序号/取消，不无证据重写。
- planTable/reviewTable：名称导航、编辑不误跳、组行行为、权限保持；固定列不覆盖名称；按批准Demo实际组件截图比对表头、间距、常驻按钮。

现有轨道90px、数字最小38px与建议100～140/44存在尺寸差异；上线前按可用空间核对可读性，优先用户要求的名称自然宽度，不为像素建议挤坏布局。

## 9. 前次测试其余未闭环项：先验证，失败再实施

下列内容不是本轮新证实缺陷，不能无依据全部重写，但不能漏出交付清单。

|编号|剩余工作与具体改动条件|通过标准|
|---|---|---|
|AC-001 自动刷新|检查计划进入/activated/源变更后刷新、请求序号；复现首次空列表后再补初始化就绪与响应防串逻辑，避免新增高频轮询|A/B/A、后台返回、慢旧响应不盖新数据；不丢未保存草稿|
|AC-002/003 计划共享字段/手动全同步|按R3字段矩阵验证；故障时逐项结果与独立重试，缺事件才补源事务事件|全部共享字段及解绑一致，部分失败原因准确，无重复关联|
|AC-004 详情隔离|核查路由key、组件缓存、取消请求；有串页证据再改|标题、用例、缺陷、统计及子页始终属于当前计划|
|AC-010 平台配置|此前只验证非法URL；合法平台/登录配置新增、编辑、启用、回读；有失败按真实接口日志定位|合法配置持久化，JSON/MFA/权限失败一次安全提示，无半条记录|
|AC-013 评论并发|接口冲突已有证据，补前端草稿保护；若丢草稿改冲突分支保存本地草稿/复制入口|两会话冲突不覆盖新值，旧草稿可取回|
|AC-016/020 删除及恢复|使用专用测试数据走原人工确认流程，不绕过删除授权；发现清理漏点补墓碑/事件|取消/过期不删，确认只删预览对象；计划/评审/资产计数收敛，恢复不重复|
|AC-022 组织置顶|第二成员、跨组织、跨页排序；仅当失败修组织作用域和稳定排序|同组织一致，其他组织不可写，取消恢复原排序|
|AC-025 快照|STEP/CASE历史读取使用正确正文解码；不能把当前源修复回灌历史快照|旧事实不被新定义覆盖，facts不递归嵌套历史facts，异常明确|
|AC-026 幂等键|规范化与DB比较规则一致；空白/大小写/超长/REST-MCP互通，若冲突修规范函数与唯一索引迁移|同key同参回放，异参冲突，大小写按既定契约不被collation意外折叠|
|AC-027 导入工具|按R2补Node实际产物、多组选择与followup恢复|三策略各自正确，无重复计划/评审/用例|
|AC-029 回放与副作用|每次回放重新鉴权，故障注入外部上传成功但响应丢失/DB提交中断|撤权拒绝、重复调用一次有效副作用、失败可追踪|
|AC-030 图片读取|当前/历史/撤权/删除/恢复，受控读取不是永久公开链接|权限正确、MIME正确、证据ID与内容一致|
|AC-031 步骤边界|TEXT/空STEP、1/10/100/101步骤、1000/1001facts及超字节分别测|contract与submit一致，错误可读、不截断|
|AC-032 留存|按R1密钥与受控时钟验证，检查引用和清理策略|历史有引用不误删，重启和过期行为可审计|
|AC-034 目录移动|有子目录/用例、同名、自身/后代、旧版本、跨项目、无权限|合法移动ID/计数不变，非法零写入|
|AC-036 名称导航|按R7补实际视口、编辑/组行/键盘|可进入正确详情，不被固定操作列遮挡|
|发布差异疑点|比对实际build-manifest、Git SHA、流水线制品与浏览器加载资源；没有证据不归因发错包|前后端发布身份可确认，测试记录绑定相同版本|
|Excel目录语义|现有文件模块路径优先；本轮不擅自改为当前选中目录优先|按已确认需求验证，有语义变更先明确规则并补预检说明|

## 10. 实施顺序与验证命令

1. 首先冻结真实压缩fixture，完成读写全路径复现；修C1编码链路及失败协议，再恢复历史数据。
2. R1环境密钥/历史敏感扫描与R2并发恢复；R3补全字段并在正文修复之后进行补偿。
3. R4真实导出、R5实际客户端、R7/UX上线回归；第9节逐条补验并仅修实际失败。
4. 完整构建、迁移升级、单实例容器健康、部署E2E。资源不足时串行执行，不同时启动第二套环境，不降低保护阈值强跑。

以下是后续实施验证命令，不是本次已执行结果。新增测试类名是计划，创建后再运行：

```powershell
# 前端现有定向检查
pnpm --dir frontend run test:selection-toggle
pnpm --dir frontend run test:weekly-remediation-sfc
# 完整类型检查由生产构建前置执行
pnpm --dir frontend run build
# 既有MCP包测试；新增协议测试也纳入同一命令
npm.cmd --prefix metersphere-mcp run build
node --max-old-space-size=128 --test metersphere-mcp/test/stdioContract.test.mjs
# 新增codec单测建议在system-setting模块，配真实mapper的集成测试在case-management
.\mvnw.cmd -pl backend/services/system-setting -am -Dtest=FunctionalCaseBlobCodecTests -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl backend/services/case-management -am -Dtest=FunctionalCaseBlobRoundTripTests,FunctionalCaseDefinitionStepsTests -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl backend/services/agent-integration -am -Dquality.mysql=true -Dtest=WeeklyRemediationMigrationMySqlTests -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl backend/app -am -DskipTests=false package
```

压缩集成测试不得只是调用codec的Mock测试：需要真实MyBatis插件与MySQL。既有quality.mysql约定使用专用测试库，实际接入前核对配置，不写共享业务库。135/136测试通过不代表新codec集成已通过。

Docker构建、单实例compose启动与健康检查沿用[实施记录](msp-weekly-remediation-implementation-20261028.md)中带时间戳产物和override的完整命令；新构建必须使用本轮新jar解包目录，不能误用旧DEPENDENCY。部署后逐条记录Web、原生MCP、Node结果及manifest，不能以本地隔离组件代替测试环境验收。

## 11. 已有证据、尚未执行与交付状态

截至本方案，前一轮证据：后端定向测试24项通过（其中2项真实MySQL迁移/补偿SQL），前端选择/概览逻辑6项通过，8个SFC语法/模板检查通过，Node定向13项及stdio契约1项通过。目标lint无error但有既有warnings；完整结果见validation-summary.json。它们不包含本文新增codec、历史修复工具及全链路验收。

本次仅重新读取代码、日志/HEX结论和既有报告，生成本方案；未实施本文新增产品代码、未修数据库、未提交/发布。历史报告“等待日志”现在应由本文的压缩证据更新，但原测试证据保持原时点，不伪造改写通过结果。

未执行：新codec测试及真实MyBatis集成、全项目vue-tsc/生产构建、Maven全量打包、完整历史库升级、镜像构建/启动/健康、部署后E2E、历史内容恢复与长期留存矩阵。前轮未做重型检查的原因是本机内存不足；本次为方案任务，不启动环境。剩余风险：压缩损坏继续影响历史读取，部分内容不可恢复，状态同步完整性与客户端实际契约尚未验收。

最终完成条件：总表每项都有实现/权限/真实接口/数据配置/正反边界测试和部署证据；损坏记录逐条有恢复或明确不可恢复结论。任一关键项未满足，交付状态仍为部分完成。
