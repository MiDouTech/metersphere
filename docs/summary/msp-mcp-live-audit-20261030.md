# MCP真实环境诊断与修复建议（2026-10-30）

## 结论

环境为配置中明确核对的 `metersphere_test → https://msp.ebcone.net/api/mcp`，未调用生产连接。按用户反馈重点检查创建、查询、个人执行、图片证据和重试。当前不能说“无法创建所有用例”：单条/批量正常参数均可创建，已发现4项服务端或协议一致性缺陷，以及4类客户端易踩问题。

19项定向检查：15项符合预期、4项失败；失败为执行接口项目标识不一致、空名称写入、非法等级写入、显式非法模板静默回落。另将策略提示矛盾、截图格式、搜索语义、文档矛盾单列。不是全部MCP接口回归、压力/长时稳定性或真实业务执行正确性证明。

最关键结论：三步用例仅上传一张用例级JPEG，不传每步证据、不传前后角色、不传客户端Hash，提交返回ACCEPTED/PUBLISHED，报告保留3步和1张图，重试相同submissionId，原图读回字节完全一致。因此当前个人直接执行接口不要求每步截图。提交SUCCESS仅为明确标记的协议诊断记录，不表示真实业务流程已独立执行通过。

技能：使用 `C:/Users/midoo/.codex/skills/metersphere-agent/SKILL.md`。本轮客户端为Codex已连接的原生Streamable HTTP工具，不以旧stdio封装冒充原生接口。证据 `docs/testing/msp-mcp-live-audit-20261030/evidence.json`；不包含Token或图片Base64。

## 范围与检查点

|ID|实际调用/预期|实际结果|判定|
|---|---|---|---|
|M01|project.search定位独立验收项目|MSP需求验收-20261023，内部ID1704667279923322880、编号100021|通过|
|M02|case.create项目名称，三步骤+新目录|创建100120，读回步骤/标签/等级正确|通过|
|M03|case.create项目编号，不传priority/num|创建100121，默认P0；步骤num读回null，执行契约按位置解释|通过（默认与编号须说明）|
|M04|case.create内部ID+root|创建100122|通过|
|M05|同requestId同创建参数重放|同caseId/num，无新建|通过|
|M06|同requestId不同创建参数|IDEMPOTENCY_CONFLICT|通过（拒绝符合预期）|
|M07|批量两行：有效行+空名称，应拒绝坏行|两行均创建，errors=[]，100125名称为空，get确认落库|失败|
|M08|priority=P9，应校验项目等级选项|100123保存P9，get中options仍仅P0～P3|失败|
|M09|显式不存在templateId，应拒绝明确的非法引用|创建100126并使用默认字段，无任何降级警告|失败|
|M10|不存在moduleId|AGENT_RESOURCE_NOT_ACCESSIBLE，未创建|通过（拒绝符合预期）|
|M11|创建用项目名/编号后同参数读取direct contract|项目名与编号均资源不可访问；内部ID成功|失败（标识契约不一致）|
|M12|三步仅一张用例级图、顶层evidenceIds、不带Hash|ACCEPTED/PUBLISHED，CASE，三条事实完整|通过|
|M13|JPEG字节声明PNG→改匹配的JPEG声明|前者QUALITY_CASE_EVIDENCE_INVALID；后者成功STAGED|通过（格式校验正确，提示需细化）|
|M14|无任何图片证据|QUALITY_CASE_EVIDENCE_REQUIRED，明确“一张图片可覆盖所有步骤”|通过（拒绝符合预期）|
|M15|原3步仅提交2步、带有效单图|QUALITY_FINAL_FACTS_INCOMPLETE，未发布；后续完整提交成功|通过（拒绝符合预期）|
|M16|执行提交同键同参数重放|同submissionId与同附件，无重复写入|通过|
|M17|读取已发布附件|image/jpeg，Base64与原始字节一致|通过|
|M18|execution.get读取报告|3步事实、单张证据、AGENT_REPORTED、STRUCTURE_ONLY_V1|通过|
|M19|内部ID连续串行读取contract 5次|5/5成功，每次350～361ms、stepEvidenceRequired=false；非压力测试|通过（有限样本）|

总计15通过/4失败。负向调用返回错误不自动等于缺陷；本表已区分合理拒绝。没有请求反馈方真实Token；客户端、具体失败参数/traceId尚待补充，因此尚不能断言已复现反馈方全部报错。

## 缺陷与修复方案

### D01 项目标识处理不一致（P1）

复现：case.create(projectId="MSP需求验收-20261023")成功后，functional.execution.contract仍传该名称，返回AGENT_RESOURCE_NOT_ACCESSIBLE；换1704667279923322880成功。传100021也失败。

traceId：名称 `06410bae-d23a-4243-84cb-8610375bb48c`；编号 `565537d9-36e4-48e1-951c-00b08bbcef1b`。

源码：AgentCaseWriteService经AgentProjectService.resolveProjectId；AgentDirectExecutionService.authorize直接将原字符串传access.caseProject。当前工具说明没有统一标明仅内部ID，跨工具连用容易在已创建成功后报权限错误。这里不判定真实权限配置异常。

修改建议：在个人execution.contract/upload/submit/get及相关attachment入口统一规范化projectId，并使用规范化后的不可变参数副本执行访问校验、幂等指纹与持久化；或由统一业务边界处理，不能只改contract导致其余工具仍失败。保留用户RBAC和Token项目范围，歧义名称不得自动选第一个。返回resolvedProjectId，SDK优先沿用返回值。不能把权限检查改宽来消除错误。

验证：名称/编号/内部ID全链路等价；名称歧义拒绝；跨项目case/evidence仍拒绝；改别名后同业务幂等行为明确；Token收回权限后重放仍拒绝。

### D02 原生MCP绕过DTO必填校验（P1）

批量payload为一个正常名称和一个空字符串，failFast=false。响应created两项、errors=[]；空名称caseId1715405986653609989/100125，get确认空值真实保存。

源码：AgentCaseCreateItem.name有@NotBlank，batch.cases有@Valid；AgentCaseWriteController REST参数带@Valid，BuiltinAgentMcpToolConfig.convert仅JSON解析后直接调用service，未触发同等Bean Validation。服务createOne把name直接写入FunctionalCaseAddRequest，服务间调用不触发Controller校验。

修改建议：统一命令/服务边界强制校验，REST与原生工具共用；顶层项目/非空列表/数量/长度先校验。每行name去首尾空格后非空并限长，steps结构和字段限长同样校验。批量保留原failFast/部分成功协议：非法行返回index、field、稳定code、可理解message与traceId，正常行按既有约定写入；不要因直接全局级联校验意外改成所有行全拒绝。DTO Schema同步minLength/maxLength，但服务端不依赖客户端Schema。

验证：单条空名/空白名、批量混合行、过长内容、空列表、非法步骤及REST/MCP等价；拒绝行零副作用，重试不重复创建。

### D03 等级未校验真实选项（P1）

100123写入P9，而项目等级options只有P0/P1/P2/P3；get中的priority及字段defaultValue均P9。AgentCaseWriteService.buildPriorityFields直接采用非空priority；AgentCaseManageService的priority补丁亦直接映射，需要同时审查更新路径，不能只修创建。

修改建议：按已授权的实际模板内functional_priority字段解析允许选项，不硬编码某个项目枚举。显式传值不在选项则返回字段级校验错误；缺省采用有效模板默认值，默认值本身失效时给配置错误，不能静默写P0。单条/批量/更新共用验证器。

验证：合法选项、定制选项、非法P9、字段被禁用/缺失、默认值失效；原其他字段和版本不变。

### D04 显式无效模板静默回落（P2；跨范围安全性仍待专项验证）

传templateId="mcp-audit-nonexistent-template"仍创建100126。ProjectTemplateService.getTemplateDTOById在模板不存在时返回默认模板；AgentCaseWriteService保留原请求模板ID继续创建，具体底层持久化模板关联需进一步查询确认，本次未伪称已查数据库。

修改建议：MCP中仅省略templateId时允许默认模板；显式传ID时先查存在性、scene、组织/项目适用范围和权限，校验通过后使用实际templateDTO.id，返回resolvedTemplateId。优先在Agent边界加严格解析，不直接改变所有现有UI调用getTemplateDTOById的回落行为。

验证：省略模板正常；不存在/错误场景/其他租户模板明确拒绝且无用例/目录残留；合法共享模板按授权策略允许。

## 其他已证实的易踩问题

### C01 “每步截图”误导：有效规则与原始策略同时返回

contract顶层为evidenceScope=CASE、minimumEvidenceCount=1、stepEvidenceRequired=false、clientHashesRequired=false；同时policy包含requiredRoles=[BEFORE_ACTION,AFTER_ACTION]和FROZEN_CONTRACT断言参数。实测这些旧Runner规则没有阻止个人单图提交，但Agent阅读返回值容易采取更严格解释。

建议：个人执行响应增加明确的effectivePolicy（CASE单图、结构覆盖、文本不比对），把旧原始策略放进明确标记“不适用于个人执行”的legacyPolicySnapshot，仅用于审计；同步SDK/技能包/前端文案，明确Runner任务通道的逐步策略不适用于个人直接执行。保持历史策略原文不篡改。三步一图是必要回归用例，含旧策略requiredRoles也应通过。

### C02 截图真实格式与扩展名/MIME不一致

用于协议验收的既有UI图片文件后缀.png，实际首字节FFD8/JFIF，为JPEG。按image/png+png上传被拒绝，traceId `5f6867e5-0306-4bed-b6f1-7deed86694b8`；相同字节改image/jpeg+.jpg成功。这个命名问题来自本轮此前截图保存方式，不能据此断言反馈方一定也有相同问题。

服务端ImageIO校验MIME、可解码、最大5MiB、像素64×64至2000万像素，检查本身应保留。应由截图保存器/SDK读取真实文件签名并匹配后缀与contentType；远程工具给出安全的details.reason，例如MIME_MISMATCH、TOO_SMALL、PIXEL_LIMIT、INVALID_BASE64，而非把所有情况统一成“证据无效”。文档应补64像素最小边长及像素限制。不能靠每步重截或把字节伪标PNG解决。

### C03 通用搜索query命中目录会缩小结果

本轮相同MCP-AUDIT-20261030前缀：query命中目录，matchedBy=[module]、total=3；filters.keyword按名称检索total=7，目录外100121/122/123/126实际已创建。现有返回已提供matchedBy，但没有warnings。此项是已观察的搜索语义与易用性，不直接当作丢数据。

建议明确searchMode=AUTO/MODULE/KEYWORD，界面/工具说明提示AUTO匹配目录，返回appliedFilters及warnings；用户明确“按名称搜”应使用filters.keyword。不能以查询未找到为理由换requestId重复创建。

### C04 文档与幂等冲突提示矛盾

metersphere-mcp/README.md第25行仍列functional.submit.batch，后文172行明确不注册旧submit工具。旧工具源码仍存在并不等于当前工具列表可调用。用户若缓存旧工具说明、旧技能或走Runner任务链路，可能收到禁用/租约/步骤校验错误，但本轮未取得反馈方客户端，不能当作已复现其具体根因。

AgentSafeErrorMapper对IDEMPOTENCY_CONFLICT提示“请使用新的请求标识”，而技能又要求不要换键绕过冲突。建议改为先核对原操作及参数，只有明确新业务操作才生成新键；文档工具表由实际注册工具生成，移除禁用旧接口推荐。原生MCP与stdio REST包装均需契约回归。

## 错误记录（合理拒绝）

- 无图片：QUALITY_CASE_EVIDENCE_REQUIRED，traceId a86ca651-36a6-41c2-8532-093d4f0e6de1；不是逐步图片要求。
- 漏1个原始步骤：QUALITY_FINAL_FACTS_INCOMPLETE，traceId d46444ac-b9b4-443b-a213-22f563e2247c；应补实际结果，不是补每步截图。
- 同键不同参数：IDEMPOTENCY_CONFLICT，traceId 88e0c0c4-6058-4ac6-ac37-505930a9dd7a。
- 目录不存在：AGENT_RESOURCE_NOT_ACCESSIBLE，traceId cf2942d0-1296-4a67-b0c6-e8e77118b5f0。统一资源/权限提示合理保护信息，但客户端需先解析目录而不是反复新建。

## 数据、边界与后续

仅独立验收项目新建目录MCP-AUDIT-20261030及7条用例100120～100126，1次个人协议执行记录、1张图片。为避免留下不可见脏数据，已将空名称100125更新为明确诊断名称；P9负向用例100123恢复P2，原始异常已留证。未删除既有用例、未改正常项目/计划、未修改Token或策略、未执行生产工具。

没有修改生产代码。本轮仅新增报告/JSON证据，不运行无关构建/迁移/容器，不启动本地服务。尚未覆盖限流/断网/并发负载/长时间稳定性、Token过期及不同角色、Runner全链路、错误复现所用真实客户端。反馈方需补客户端名、工具名、错误原文/traceId（不要Token），才能把其间歇性错误映射到具体原因。

功能状态：个人三步单图链路验证通过；MCP整体存在上述缺陷，不能判定全部可用。建议D02/D03/D01优先，随后D04及C01～C04；修复后必须用本报告负向与幂等路径回归，不以放宽校验掩盖问题。
