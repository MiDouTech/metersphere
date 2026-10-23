# MSP 测试环境需求实测记录（2026-10-23）

状态：本轮低资源实测已收尾，整体验收部分完成，未通过。确认14条问题记录；其中包括同类隐藏入口的不同需求项，不等于14个独立根因。本文只记录部署环境证据，不以本地代码、构建通过或工具出现在列表中代替运行验收。

环境：https://msp.ebcone.net；使用同一个 Ego 任务“MSP requirements audit 20261023”。没有启动本地 Docker、后端或构建，没有改动业务源码。隔离项目为 `1704667279923322880`（MSP需求验收-20261023）。证据目录：`outputs/msp-live-audit-20261023/`，该目录为本机忽略文件，不随本文自动进入 Git。

## 1. 已确认问题

| 编号 | 优先级 | 对应需求 | 实测结果与复现 | 证据文件 |
| --- | --- | --- | --- | --- |
| LIVE-01 | P1 | DEF-01、R08 | 打开计划A详情，返回列表再打开B；URL为B，但标题和文档仍为A。等待8秒仍未纠正。未进行保存，避免误写。A=`1704670698717290496`，B=`1704670698717290508` | `026*`、`027*`、`028-stale-plan-settled.json` |
| LIVE-02 | P2 | REQ-02 | 计划详情仍显示“生成报告” | `026*`、`027*` |
| LIVE-03 | P2 | REQ-04、R06 | 测试用例顶栏仍显示“生成用例” | `008*`、`022*` |
| LIVE-04 | P2 | REQ-05 | 测试资产→业务文档仍显示“AI生成用例” | `029*` |
| LIVE-05 | P2 | REQ-06、R06 | 用例资产仍显示环境选择、检查AI可执行性、页面对象、业务流四项控件 | `031-current-asset-view.txt` |
| LIVE-06 | P1 | U06、U16、RA06、RA16 | 原生 MCP 的 project.create、functional.case.create 要求幂等键，但发布的严格 schema 不允许 requestId；原生工具调用失败 IDEMPOTENCY_KEY_REQUIRED。同一业务参数用额外 HTTP Idempotency-Key 后成功。不是整个 MCP 不可用 | `006-live-mcp-tools.json`；traceId `81915481-5de5-4f6b-9c46-a359a0caa064`、`b590c2ab-8660-415d-911e-e2bca8dffa30`；项目和用例创建响应 |
| LIVE-07 | P2 | DEF-02、错误协议 | 平台地址缺少协议时后端正确拒绝，但前端发出请求，并对同一 traceId 重复弹出两条相同错误 | `034*`，traceId `72f987b8-a3bf-45c5-a022-19283ce9fecc` |
| LIVE-08 | P2 | R01 | 用例列表14条数据时仅有页码1、2，没有10/20/50/100条数选择；2条和4条数据时也没有。偏好设置链路无法验收 | `068-fourteen-case-list.txt`、`051*`、`073-project-case-list-after-import.txt` |
| LIVE-09 | P2 | R05 | 同一账号同一列表，零选择时无批量操作；全选2条后才出现导出、编辑、批量修改执行结果/执行人、移动、复制等按钮。不符合常驻且零选择禁用 | `051-cases-zero-selection.txt` 对照 `054-selected-bulk-controls.txt` |
| LIVE-10 | P2 | U11、RA12 | 评论编辑成功后，携带旧expectedUpdateTime更新被拒绝，但错误码为AI_EXECUTION_INTERNAL_ERROR，缺少明确版本冲突提示。未发现旧内容覆盖成功 | `050-comment-updated.json`、`050-comment-stale.json`，traceId `6dd62b0f-8a8e-4559-abed-0b28d9c4df3f` |
| LIVE-11 | P2 | 缺陷MCP、U16 | bug.create的customFields schema允许任意值，但后端要求Map<String,String>；handleUser传数组时内部错误，传JSON字符串后创建成功。应明确schema类型并对非法类型返回参数错误 | `045-full-tools.json`、`049-bug-created.json`、`053-bug-string-fields.json`，traceId `aa99a7de-122a-4014-9405-6dc213309b63` |
| LIVE-12 | P1 | U10/U16、计划用例查询 | test_plan.cases使用已发布schema中的projectId/testPlanId/current/pageSize/includeSteps仍返回内部错误；同账号同计划REST分页200且有1条数据。不是计划无数据 | `063-plan-cases-correct-schema.json` 对照 `066-plan-page-rest.json`，traceId `04dd1f96-bf0c-44d4-a673-1e7db4f5d539` |
| LIVE-13 | P1 | U12、RA13 | MCP delete_preview成功返回confirmationPath，打开原样路径显示“网页不存在”。未执行删除，恢复链路未验证 | `065-delete-preview.json`、`070-delete-confirmation-page.txt`、`070-delete-page-url.json` |
| LIVE-14 | P1 | R08、U10 | 停留用例A详情时，在相同页面路由切换pId到另一项目；项目标题和目录数已变为另一项目14条，主详情仍为隔离项目用例A。下一轮取证仍未纠正；切回列表后才显示目标项目列表。未编辑旧详情 | `067-fourteen-case-paging.txt`、`068-cross-project-detail-stale.txt` |

优先处理LIVE-01/14的数据展示错位、LIVE-06/12的MCP核心契约和LIVE-13的断路，再修复隐藏入口、分页、批量按钮和错误分类。前端发布制品需先核对；当前证据尚不能判断问题来自源码、漏发制品还是缓存。

## 2. 已取得的正向及边界证据

| 范围 | 已实际验证 | 尚不能推导的结论 | 证据 |
| --- | --- | --- | --- |
| DEF-02 | 合法 https 地址新增平台成功，刷新后存在；无协议地址安全拒绝 | 真实站点验证、凭据绑定、权限矩阵尚未验完 | `034*`～`036*` |
| DEF-03 | 合法定位器/断言新增登录配置成功，刷新后存在；非法JSON在前端拦截，未发出保存请求 | 实际自动登录、MFA、凭据运行尚未验证 | `042*`～`044*` |
| REQ-01 用例图片 | 外部PNG转存为本地附件，记录8090字节和SHA256，重复请求返回相同附件ID且列表只有1条；127.0.0.1被拒绝 | UI预览/下载、缺陷和执行路径、长期保留及备份未验完 | `038*`～`040*` |
| U02/U06 | 个人中心有Agent配置；真实UI创建Token并验证MCP；限制项目；撤销后实际请求401 | 全部预设权限及多客户端兼容未验完 | `016*`～`021*` |
| U03/U07/U08 | 执行中心有真实质量门禁页面；contract返回STRUCTURE_ONLY_V1、AGENT_REPORTED、NONE_AGENT_REPORTED及步骤覆盖规则 | 未实际提交不能判定结果汇总与持久化通过 | `046-quality-settled.txt`、`048-execution-contract.json` |
| U05 | 执行任务页正常显示空状态及筛选；当前项目无任务 | 有任务详情、故障重试、调度未验证 | `047-tasks.txt` |
| INC恢复诊断 | GET /api/ai/recovery/status 返回200和WRITEBACK扫描成功记录 | 不等同于数据库断连恢复或全部扫描器通过 | `045-recovery-status.json` |
| 缺陷模板/创建 | 返回处理人、严重程度必填规则；缺少字段安全拒绝；按字符串格式补齐后真实创建、get及网页详情可见 | customFields类型协议不一致见LIVE-11；缺陷导入导出、不同角色权限未验 | `048*`、`049*`、`053*`、`055-bug-readback.json`、`076*`～`078*` |
| REQ-01 缺陷图片 | URL转存为8090字节本地附件，缺陷详情附件区显示缩略图，图片实际加载100×100 | 未逐项验图片删除、下载权限、备份与过期源URL | `054-bug-image.json`、`077*`、`078*` |
| REQ-01 用例展示 | 实际打开附件预览，图片100×100；浏览器读取系统提供的blob，8090字节和SHA256与转存结果一致 | 不是多年保留或备份恢复证明 | `062-case-detail-loaded.txt`、`064*`、`065-ui-image-byte-verification.json` |
| 直提执行 | 缺原始步骤被拒绝；2→1→重复1可提交，重复1为ERROR则整例ERROR；幂等重放同submissionId；再测2→1→额外3，额外3为ERROR也得整例ERROR；get保留原步骤顺序和重复记录 | 使用公开PNG作结构测试夹具，不冒充真实业务截图；全SUCCESS、跨用户/版本和全部附件边界未验 | `056-execution-missing-step.json`、`062*`、`063*`、`074*` |
| REQ-03 | 先给用例A关联缺陷，再网页执行同步；REST计划列表bugCount=1且bugList含该缺陷及计划用例关联ID | 只验证补齐缺失关联；手工关联保留、归档排除等边界未全部覆盖 | `054-bug-case-related.json`、`060*`、`066-plan-page-rest.json` |
| U11 评论 | 创建→编辑→删除→列表返回deleted=true、content为空的墓碑，旧版本未覆盖 | 回复、通知送达和他人权限未验；冲突错误分类未通过 | `049-comment-created.json`～`053-comment-tombstone.json` |
| R04/U13 | 网页选2条导出XLSX，任务SUCCEEDED/count=2；MCP下载5417字节，ZIP/XML有效且两条名称均匹配；MCP创建XMind任务成功并下载4986字节，ZIP结构有效 | 网页自动下载事件未单独取证；大数据、停止、重试、断网恢复、字段全选未验 | `058*`～`062*`、`067-mcp-xmind-start.json`、`068-xmind-status.json`、`069*`、`070-export-content-validation.json` |
| U13 导入 | 将上述仅含名称的2行XLSX通过MCP预检、提交、逐项SUCCESS、COMPLETED；网页出现4条记录 | 仅2行最小字段；完整字段/覆盖/失败回滚/500+行/传统网页导入未验 | `071-import-precheck.json`、`072-import-commit-native.json`、`073-import-status.json`、`073-project-case-list-after-import.txt` |
| U10 查询/快照 | 目录get、创建2条用例快照、按pageSize=1读取成功 | 新增后快照稳定性、失效/跨权限、多页大集合未验 | `067-snapshot-created.json`、`069-snapshot-page.json`、`071-module-read.json` |
| U15 归档 | 结构提交归档返回DONE及documentId；再次查询同一归档记录仍DONE | 未到资产页核对文档内容；没有真实业务数据时未造空数据资产，重试/脱敏未验 | `071-archive-prepare.json`、`073-archive-read.json` |

## 3. 全范围追踪与剩余验证

以下六份为用户确认的范围；以最新直提覆盖规则覆盖旧的图片语义/文字比对要求。

| 基线 | 需求 | 当前状态 | 下一步/限制 |
| --- | --- | --- | --- |
| requirements-consolidated-2026-10-22.md | REQ-01 | 部分通过 | 补齐三个目标的展示、权限、失败与生命周期 |
| 同上 | REQ-02/04/05/06 | 未通过 | LIVE-02～05 |
| 同上 | REQ-03 缺陷关联同步 | 部分通过 | 缺失关联补齐已验证，手工关联保留等边界未全部覆盖 |
| defect-remediation-consolidated-2026-10-22.md | DEF-01 | 未通过 | LIVE-01 |
| 同上 | DEF-02/03 | 部分通过 | 有效配置保存通过；其余边界与真实运行待验 |
| 同上 | INC-R01～11 | 部分/未验证 | 仅取得恢复状态只读证据；需部署方提供迁移、日志、慢SQL、断连恢复和优雅停机证据，未操作共享环境故障注入 |
| platform-optimization-implementation-plan-2026-10-21.md | R01/R05 | 未通过 | LIVE-08/09；已做选择前后对照 |
| 同上 | R02 | 未验证 | 未对共享服务发起721次调用或多副本压测 |
| 同上 | R03/R03A/R07 | 未验证 | 14天绝对期限、多端隔离、可信设备及认证失败弹框仍需独立验证 |
| 同上 | R04 | 部分通过 | 两种格式真实下载和解析通过；停止与恢复等待验 |
| 同上 | R06 | 未通过 | 已发现应隐藏AI入口仍可见，其他路由仍待枚举 |
| 同上 | R08 | 未通过/部分 | 计划切换失败；其他列表及外部变更刷新待测 |
| msp-remediation-complete-plan-20261020.md（21日更新） | U01 | 部分 | 主侧栏未见三个旧分组，旧路由/搜索尚未验完 |
| 同上 | U02～U05 | 部分 | 个人Agent、全局门禁、平台配置和任务页面可达；非全流程通过 |
| 同上 | U06/U16 | 未通过/部分 | 原生客户端写入幂等schema不一致；部署制品一致性仍需确认 |
| 同上 | U07/U08 | 部分通过 | 提交、缺步骤拒绝、乱序/重复/额外步骤及ERROR汇总、幂等和读取通过；权限版本边界待验 |
| 同上 | U09/U10 | 部分 | 附件部分和查询可用；下载/删除共享保护/快照等未验完 |
| 同上 | U11 | 部分通过 | 评论创建/编辑/删除/墓碑通过，冲突提示失败；回复/通知/权限待验 |
| 同上 | U12 | 未通过 | 确认页面不存在；未绕过人工确认直接删除 |
| 同上 | U13 | 部分通过 | 2行导入、2条双格式导出通过；失败/重试/大数据等待验 |
| 同上 | U14 | 未验证 | 未创建定时运行及真实Runner任务，不能从空列表推导通过 |
| 同上 | U15 | 部分通过 | 归档记录DONE，页面内容/数据资产等待验 |
| direct-step-coverage-change-20261021.md | 最新直提规则 | 部分通过 | 契约、实际提交和持久化符合所测规则；全SUCCESS、版本/权限边界未验 |
| mcp-bug-delivery-20261021.md | 缺陷MCP/模板/导入导出 | 部分通过 | 模板、字段缺失错误、正确格式创建、读取、网页和图片通过；字段类型协议失败、缺陷导入导出未验 |

## 4. 部署与执行限制

- 在线前端主文件为 `/assets/index-Cql24jZA.js`，本地已记录构建为另一个哈希。此差异只说明制品不同，不能据此断言线上后端提交版本。线上多个旧控件仍存在，需核对实际发布清单与前端缓存。
- 页面截图接口一次超时、一次生成黑图；黑图不作为应用缺陷或视觉验收证据。当前采用页面语义快照、实际URL和HTTP/MCP响应，需后续补可用截图。
- 本轮未运行单测、集成测试、lint、前后端构建、数据库迁移、Docker构建/启动；本轮任务为线上验收，且用户要求不重复构建、不并行启动环境。相关工程验收仍须以已部署版本的流水线和服务端证据补齐。
- 未执行高并发、数据库断连、服务重启、全局策略发布或现有业务数据删除；这些不能标记通过。
- 新增测试对象均在隔离项目内，名称/文件或来源请求有LIVE-AUDIT-20261023标识；两个导入用例继承原名称。两个临时Token均已停用，最后一次撤销后真实MCP请求为401；新建平台和登录档案均停用成功（enabled=false）。未运行对example.com的任务。证据：`079-cleanup.json`。

### 4.1 故障整改逐项未覆盖范围

| 条目 | 本轮证据与缺口 |
| --- | --- |
| INC-R01 排序规则迁移 | WRITEBACK扫描有成功记录；未取得线上DDL、Flyway历史和混合规则迁移数据，不能判迁移完成 |
| INC-R02 调度退避告警 | status接口含失败计数；未制造持续失败，未验证日志去重、退避和告警送达 |
| INC-R03 导入追踪 | MCP任务有逐项状态，未走传统网页Excel导入和服务重启中断恢复 |
| INC-R04 导入资源 | 仅2行串行测试；未做500+行、事务失败和资源曲线 |
| INC-R05 聚合查询/鉴权 | 查询可用，撤销Token真实401；未有SQL数量/延迟前后对照及角色变更缓存测试 |
| INC-R06 慢SQL | 无慢日志、执行计划和锁等待证据 |
| INC-R07 断连恢复 | runtime显示两个连接池pending=0；未做断连恢复，不等同自恢复通过 |
| INC-R08 现场监控 | runtime返回堆、GC、线程、系统负载及两个连接池active/idle/pending；告警和保留期未验。`071-runtime.json` |
| INC-R09 日志轮转 | 无部署侧日志级别、轮转和磁盘阈值证据 |
| INC-R10 优雅停机 | 未停止共享数据库或容器 |
| INC-R11 会话销毁警告 | 实际SSO登录成功；未取得服务端销毁事件和会话过期日志 |

### 4.2 前后端对应关系与断点

| 用户路径 | 实际接口/数据 | 结果 |
| --- | --- | --- |
| 计划详情切换 | URL中的planId与标题、文档 | 失败，展示旧状态；未写入验证 |
| 平台/登录配置表单 | POST /api/ai/environment-profiles、/api/ai/login-profiles；刷新GET | 正确参数保存及留存通过，错误重复提示待修 |
| 用例/缺陷图片 | MCP *.attachment.import_url → 本地文件/关联ID → 页面blob图片 | 所测转存和展示通过 |
| 个人直提 | contract → evidence.import_url → submit → execution.get → 用例列表失败状态 | 结构路径通过，不代表真实业务截图取证通过 |
| 关联同步 | POST /api/test-plan/functional/case/sync/project/{projectId} → POST /page | 所测缺失缺陷关联同步通过 |
| 网页与MCP导出 | /functional/case/export/excel → /export/tasks/{id} → MCP export.status/download | 同一任务可跨通道查询和下载 |
| MCP导入 | functional.import.precheck/commit/get → 两个新caseId → 网页4条记录 | 小批量最小字段通过 |
| 评论 | functional.comment.create/update/delete → comments.list | 墓碑通过，旧版本错误码失败 |
| 删除确认 | delete_preview → confirmationPath → 前端路由 | 后端预检有结果、前端404，链路失败 |
| 计划用例读取 | MCP test_plan.cases / REST functional/case/page | MCP失败，REST成功 |

### 4.3 保留的隔离测试对象

- 原用例A `1704669977162784770`、B `1704669994342653953`；导入新用例 `1704710727812489217`、`1704710727812489223`。
- 计划A `1704670698717290496`、B `1704670698717290508`；缺陷 `1704700093473464320`。
- 平台 `1704687071132622849`、登录 `1704695059771793408`，均已停用。
- 两次结构测试提交 `37277c4c-1892-4884-88ed-575bc6bb4e92`、`27ce07f5-3984-4c6e-8089-1a030a8ca53a`，均ERROR；图片明确命名STRUCTURE，不能作真实业务执行结果引用。
- 删除预检未执行删除；评论删除只影响本轮新建评论。保留项目和证据，方便复现14条问题；未清空他人业务数据。

## 5. 实际执行方式与交付状态

实际调用示例（已执行）：`& 'C:/Program Files/Citro Labs/ego lite/Application/0.6.0.16/ego-browser.exe' nodejs -e "await import('file:///C:/SoftWare/JetBrains/metersphere/.codex-tmp/msp-audit-20261023/round-59-submit-structure.mjs')"`；同方式执行round-41至round-75及之前留存的轮次，每轮使用新脚本，复用TaskSpace 2。MCP通过TEST连接器与同源 `/api/mcp` 真实调用；原生schema缺陷使用额外幂等HTTP头做对照。

文件校验实际执行Python标准库`zipfile.ZipFile(...).testzip()`和`xml.etree.ElementTree.fromstring(...)`：XLSX ZIP有效、3行含表头且两个预期名称一致；XMind ZIP有效，包含content.xml、meta.xml、styles.xml及manifest。校验记录`062-xlsx-validation.json`、`070-export-content-validation.json`及`069-mcp-export.xmind`。

未执行项的补证方式：14天期限需独立会话时间测试及服务端有效期证据；多设备/可信代理需配置对应客户端和可撤销设备；限流/断连/迁移/停机/大批量需隔离或指定演练窗口及部署侧日志。不是可在当前共享站点用一条安全命令替代的检查。本轮不提供未经验证的运维命令，避免误操作共享环境。工程构建命令沿用各基线文档“验证命令与环境准备”章节，须在后续源码修复后按实际改动执行。

本轮修改文件仅本报告、忽略目录下的测试脚本及证据。没有修复业务源码。当前整体状态：**部分完成，发现明确未通过项，尚不具备整体验收通过条件**。

证据已脱敏打包为`outputs/msp-live-audit-20261023/msp-live-audit-evidence-20261023.zip`（134个文件，约151KiB，排除黑色截图）。132个文本/JSON文件已完成脱敏，JSON均可解析。验证码不进入报告或证据包。工作区另外出现的事故文档、架构数据库审计和SQL方案改动不属于本次操作，保持原样。

取证脚本也有失败尝试：隐藏checkbox原生input无法点击，后改点可见label完成对照；execution.get首次漏caseId得到参数错误，补齐后成功；第一次计划查询使用page而非current，已另用正确schema重测仍失败。上述脚本/参数问题不单独计入14条应用问题。
