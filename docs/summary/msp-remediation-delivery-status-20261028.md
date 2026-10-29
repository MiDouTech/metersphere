# MSP 本轮修复交付追踪（2026-10-28）

> 2026-10-29更新：本轮CI失败修复、前端构建与13个核心隔离验收已通过，详见[msp-ci-80-failures-analysis-and-remediation-20261029.md 第7节](msp-ci-80-failures-analysis-and-remediation-20261029.md#7-本轮最终验证结果与交付边界2026-10-29)。下文保留当时状态；部署/真实历史库/客户端全矩阵等未执行项仍保留，完整业务交付不冒充全部验收完成。

2026-10-29 补充：[70 项 failure、10 项 error 的逐条分析和修复方案](msp-ci-80-failures-analysis-and-remediation-20261029.md)。复核确认有真实成员绑定幂等缺陷、角色部分更新兼容缺陷，以及前后端角色写权限不一致，不能将全部失败概括为旧测试；权限树差集仍待补证据。本补充是分析方案，未将这些修复标记为已实施。

状态：部分完成，未满足“全部测试通过后合并”的门槛。修复分支codex/weekly-remediation-20261028；PR #4目标chenqifen，PR #5目标main，均保持草稿。本文不把代码提交等同于发布或验收通过。

## 需求追踪与前后端对应

|需求|实际修改/链路|已执行证据|尚缺内容|
|---|---|---|---|
|C1 历史正文读取与防止再次损坏|详情/MCP/导出→FunctionalCaseService→功能用例专用Codec→两处MyBatis配置；安全422协议；搜索隔离坏记录；离线CAS恢复工具|Codec、真实370字节故障fixture、错误协议、批次隔离、真实MySQL往返及参数隔离测试|100025原始内容尚缺；实际恢复未执行；完整业务链与部署E2E未通过|
|R1 执行事实敏感保护|MCP事实写读→Sanitizer/Cipher统一递归判断，字符串JSON及set-cookie|定向单测通过|测试环境密钥配置与历史事实扫描、真实持久化回归|
|R2 导入停止/重试|importJob→导入控制器/服务→提交门禁、followup事务→迁移135|阶段单测、真实MySQL迁移验证通过|多客户端并发、重启和关联失败恢复的部署验收|
|R3 源/计划/资产同步|源执行/附件→版本推进与outbox→Hub投影；显式清空执行字段；迁移136补偿|执行投影、迁移与排序规则修复测试通过|完整附件/缺陷/解绑/删除恢复/版本切换字段矩阵仍未全部验证和闭环，不能计完整实现|
|R4 资产等级导出|资产鉴权→通用导出服务→functional_priority语义映射|P0～P3/未设置/冲突测试通过|部署真实XLSX全字段比对|
|R5 MCP实际契约|原生schema、Node实际stdio工具、技能包坏记录输出说明|Node31项通过，技能包测试通过|npm发布、实际连接器绑定/缓存刷新、双客户端真实业务回放|
|R7/UX1/UX2 UI|Token卡片宽度、表格窄屏、全选/取消所有页、计划名称自然宽度与概览同组对齐|前端完整vue-tsc+Vite远端通过，定向逻辑及SFC检查通过|新版本部署后多视口/缩放/实际操作与跨页请求验收|

## 文件范围

主要产品文件：SDK FunctionalCaseBlobCodec、系统FunctionalCaseInterceptor/MybatisInterceptor/RestControllerExceptionHandler/SystemLoginConfiguration/ResourceEditAdapterRegistry、用例FunctionalCaseService/FunctionalCaseBodyRepairTool/DefaultHubCaseSyncService/FunctionalCaseAttachmentService/FunctionalCaseFileService、Agent事实/导入/查询与导出服务、迁移135/136、前端caseTable/batchAction/base-table/Agent配置、Node包与技能说明。准确增删见修复分支与PR差异，不将测试夹具当作生产修复。

维护入口及参数：[正文恢复维护命令](../../scripts/maintenance/README-case-body-repair.md)。恢复需要可信来源、原始摘要/版本/项目/操作人校验、POSIX私有备份；无HTTP公开修复入口。尚未对业务数据库写恢复数据。

## 实际命令及结果

- `java @.codex-tmp/weekly-fixes-20261028/all-current-unit.args`：63项，62成功、0失败、1项Windows非POSIX分支中止；包含真实MySQL编解码及迁移检查。
- `java @.codex-tmp/weekly-fixes-20261028/isolation-unit.args`：新增2项成功；修复前两项失败，记录于isolation-before.log。
- `java @.codex-tmp/weekly-fixes-20261028/isolation-mysql.args`：1项成功，覆盖重复updateByExampleSelective、原始存储单层、调用方逻辑正文不被替换；与63项中的同一个测试类有重叠，不能相加为新增独立1项。
- `java @.codex-tmp/weekly-fixes-20261028/fixture-test.args`：新增1项成功，真实MySQL及Spring ScriptUtils执行同一夹具两遍并保留无关身份。
- `node --max-old-space-size=192 --test --test-concurrency=1 metersphere-mcp/test/*.test.mjs`：31项通过。
- Python诊断工具4项通过；前端选择逻辑6项、8个SFC检查通过；先前针对变更文件lint/stylelint通过（有警告）。未因后续纯Java改动重跑未受影响的前端构建。
- 远端完整前端构建：[40672471196](https://github.com/chenqifen-miduo/metersphere/actions/runs/40672471196)，vue-tsc及Vite通过；后续按完全相同frontend Git树复用产物。
- 全量Maven：`./mvnw -B -pl backend/app -am -Dquality.mysql=true -Dapi.version=1.44 -DskipTests=false package`。运行[40691237576](https://github.com/chenqifen-miduo/metersphere/actions/runs/40691237576)中system-setting 529项、89失败、17错误，SDK11项另通过；报告合计540项，不能称全量通过。后续CI继续修复和复验。
- `git diff --check`通过。原始本机日志位于忽略目录，摘要另见verification-progress.json，不发布真实恢复候选正文。

## 全量失败的处理边界

已修正：模块属性注册、可选适配器注册、测试登录入口前置、测试组织身份定位、重复组织夹具、独立通知角色、权限探针会话及残留清理、PageHelper线程残留。未放宽生产认证、角色保护、错误协议或健康检查。

仍需逐项核对的旧测试包括：业务错误旧预期500而实际422；必备角色禁止删除；缺陷工作流写入口已迁移、旧入口拒绝写入；项目默认模块已不再为空；API Key权限语义等。不能批量把断言改成观察值，不通过忽略测试使CI变绿。整体门禁仍失败。

## 未完成的运行与部署验收

远端CI增加独立应用镜像构建、全新临时库迁移、readiness及首页HTTP检查，结束清理本次项目卷；若全量测试失败，额外-DskipTests package仅供运行诊断，原测试step/job仍为失败。当前结果以对应CI证据为准，HTML检查不是浏览器E2E。

后续准确命令：

```bash
# 全量测试（已失败，需要修复后再运行）
./mvnw -B -pl backend/app -am -Dquality.mysql=true -Dapi.version=1.44 -DskipTests=false package
# 仅GitHub临时runner执行独立镜像、迁移及健康诊断；不要在本机/共享服务器伪造这些变量
bash deploy/quality-verify/ci-health.sh
# 当前修改的可重放MySQL专项
./mvnw -B -pl backend/services/agent-integration -am -Dquality.mysql=true -Dtest=FunctionalCaseBlobRoundTripTests,MybatisBodyParameterIsolationTests,FunctionalCaseBodyRepairMySqlTests,WeeklyRemediationMigrationMySqlTests -Dsurefire.failIfNoSpecifiedTests=false test
```

部署E2E、真实历史恢复、敏感事实历史扫描、实际连接器回放均不能由单测或构建替代。现有测试环境仍是用户先前发布版本，本轮未宣称已部署。待全部门槛达成后才能合并两条目标分支。
## 完整迁移证据与最新运行边界

运行40694298189日志中的真实Flyway记录为：`Successfully applied 173 migrations to schema test, now at version v3.7.2.136`。这是空库完整SQL迁移通过证据，不是产品镜像健康通过；先前“全库迁移未执行”仅指打包应用独立容器阶段，不能误读为所有完整迁移链都未验证。

同一运行的独立后端打包通过；541项测试71失败、11错误。Docker阶段在解析现有registry.fit2cloud.com/metersphere/alpine-openjdk21-jre基础镜像时，其官方CDN连接超时；尚未构建镜像或启动应用。保留生产基础镜像，没有换成另一个运行环境计通过。

baa75c8082补修组织架构SQL首个DELETE未执行及MockServer旧响应优先匹配；真实MySQL部门夹具连续两遍通过，测试基类定向编译通过。复验运行40698030451，结果待其完成。未将测试批量改成观察到的返回值，旧协议/旧角色与工作流断言仍需逐项核定。
## 最终复验：未通过

40698030451实际执行541项，70失败、10错误、0跳过；该统计仅覆盖已进入的SDK和system-setting测试，下游模块因reactor失败未执行。AiProviderEndToEndTests与DepartmentControllerTests已不在失败清单，证明对应MockServer/SQL夹具补修生效。组织架构成员正向权限测试仍使用不属于目标组织的权限用户，返回拒绝；全局角色权限、API Key权限、旧工作流及错误协议断言等仍需逐项核定，不能将全部80项一概归因旧测试或宣布产品正确。

本轮没有批量调整错误断言或移除未通过测试。打包再次通过；独立镜像/健康阶段仍失败，最终根因以该阶段日志为准。

离线Python诊断补充真实非法DEFLATE块回归，修复前抛zlib.error，修复后5项全通过，输出NEEDS_SOURCE。最后提交仅该离线脚本/测试及交付记录，前后端、数据库迁移和Docker构建源码不变，复用已有对应检查结果；原全量失败保持失败，PR保持草稿。
最终容器日志确认第二次仍在现有Java基础镜像官方CDN连接超时处失败（Dockerfile.backend第8行，尚未构建/启动应用）；未执行打包应用readiness。最终复验也再次成功执行173条完整迁移达到v3.7.2.136。最后离线脚本/文档提交不重跑相同前后端CI，不改变当前全量失败及禁止合并状态。
