# 个人 Agent 步骤对应校验调整

用户确认：取消实际结果与预期文字相等的成功判定；允许额外和重复步骤，仅禁止遗漏原用例步骤。采用纯代码对应关系校验，无需独立图片审核服务。

## 修改前需求追踪

|需求|入口与前端|后端实现|数据配置|验收|
|---|---|---|---|---|
|取消文本相等判定|个人中心 Agent配置；MCP说明|AgentDirectExecutionService、GlobalQualityGate 的个人直接提交方法|保留当前全局策略摘要和附件规则；业务状态来自提交值|不同实际文字仍可申报成功；相同文字也可申报失败|
|只禁止遗漏|functional.execution.contract/upload/submit/get|冻结 contentHash 加 stepNum；原步骤1..N必须至少出现一次；额外和重复记录原样保留|原 facts JSON 保留全部记录，无表结构变更|乱序、重复、额外均可；重复不能替代缺失步骤|
|汇总可追溯|用例库结果和附件；门禁说明页|任何提交记录 ERROR 则汇总 ERROR，包括重复和额外记录|结果来源标记 AGENT_REPORTED；校验模式 STRUCTURE_ONLY_V1|不会因最后一次成功覆盖已提交失败|
|保留安全约束|现有错误及加载流程|项目权限、版本、证据角色/归属、有效文件、请求幂等、事务原子发布|旧待核验历史不重写；不修改全局已发布策略|跨步骤证据拒绝；同一步重复引用去重；缺角色和过期契约仍拒绝|

边界：原用例最多100步；单次提交最多1000条、步骤序号1..1000、每条最多10个附件；提交唯一证据最多1000个且总容量50MiB，序列化事实最多1MiB。限制用于资源保护，不以条数相等判定步骤完整。额外步骤序号大于原用例步数，不计入原步骤覆盖。所有提交记录仍需合法状态、实际结果及符合全局规则的证据。

本调整只作用于个人 Agent 直接提交；受管任务的显式类型化断言验证保持原有语义。结构审核不证明真实执行或图片语义。

## 实施及验收结果

本次规则修改已实现，本地单元、数据库集成、构建、镜像、全新安装及浏览器验收通过。整体交付状态仍为部分完成：未部署 msp.ebcone.net；首次安装中发现的既有后台回写排序规则错误未在本次范围修复。不能将本地通过表述为远程环境已生效。

|验收场景|实际结果|证据|
|---|---|---|
|actual 与 expected 不同，声明 SUCCESS|保存 SUCCESS，不再比较预期文字|AgentDirectExecutionMySqlTests、浏览器冒烟|
|actual 与 expected 相同，声明 ERROR|保存 ERROR，不被文字相等改写|MySQL事务测试、浏览器冒烟|
|乱序、额外步骤、重复原步骤|全部保留；报告返回3条提交记录|MySQL事务测试、浏览器冒烟|
|只有额外步骤或重复其他步骤，遗漏原步骤|QUALITY_FINAL_FACTS_INCOMPLETE，未发布|覆盖单元测试、MySQL事务测试、浏览器冒烟|
|额外步骤声明失败|整体 ERROR|MySQL事务测试|
|重复步骤先失败后成功|整体 ERROR，不覆盖先前失败|MySQL事务测试、浏览器冒烟|
|同一步重复引用同一证据|每个正式附件只插入一次|MySQL计数、浏览器附件列表及重试|
|跨步骤、跨Token或过期版本证据|继续拒绝|MySQL事务测试、浏览器跨步骤负例|
|缺前置证据、损坏图片和错误MIME|继续拒绝|单元及MySQL集成测试|
|发布中附件写入失败|报告、附件、证据关联全部回滚|MySQL实际唯一键冲突与事务计数|
|未配置外部核验服务|个人直接提交仍可ACCEPTED/PUBLISHED，来源AGENT_REPORTED|全新独立环境浏览器验收；没有伪造SUPPORTED服务|
|页面说明与正式结果|用例列表显示失败；正式附件可见；门禁页说明纯结构校验|direct-coverage-case.png、direct-coverage-policy.png|

## 前后端及文件对应

源码仓库：`C:\SoftWare\JetBrains\metersphere`。本轮修改如下；没有修改用户已有的 `ExtTestPlanBugMapper.xml` 和 `docs/templates/agent-testing/README.md`。

|路径|文件|变化|
|---|---|---|
|个人中心Agent配置、门禁页|`frontend/src/views/setting/system/agentIntegration/index.vue`、`frontend/src/views/execution/quality-policy.vue`|解释声明结果、只校验结构、允许额外/重复、不要求外部核验器；移除与该通道无关的服务未配置告警|
|原生MCP契约和提交|`AgentDirectExecutionTools.java`、`AgentDirectExecutionService.java`|扩展边界，集合覆盖校验，保留全部记录和结果来源；原子保存声明结果、去重附件|
|规则及状态|`GlobalQualityGate.java`、`EvidenceContentReviewer.java`|直接通道只执行附件结构规则；状态接口明确个人通道不依赖适配器；受管任务断言与历史报告语义不变|
|下载技能包、Node客户端|`AgentMcpBundleService.java`、`metersphere-mcp/src/tools/caseLibrary.ts`、`metersphere-mcp/README.md`|同步说明与参数上限；原MCP操作名不变，新增返回validationMode/resultSource|
|回归|`AgentDirectExecutionTests.java`、`AgentDirectExecutionMySqlTests.java`、`AgentMcpBundleServiceTests.java`、`metersphere-mcp/test/directExecution.test.mjs`、`frontend/e2e/personal-agent-execution.spec.ts`|按新需求验证正例、负例、持久化和UI；不是关闭旧测试取得通过|
|追踪|本文件|需求、边界、证据、命令及限制|

Java文件位于 `backend/services/agent-integration/src/main/java/io/metersphere/agent/` 下对应 quality/service/tool 目录，测试位于同模块 src/test/java。

没有新增数据库迁移或部署依赖；使用既有102/103表和facts JSON保存新校验模式及全部提交记录。历史待核验记录与旧请求号幂等结果不会被自动改写；重提需使用新requestId，并满足证据有效期。

## 实际验证命令

以下命令在源码仓库根目录执行，日志集中在 `C:\Users\midoo\Documents\automation_test\output\direct-coverage-*-20261021.log`。

|命令|实际结果|
|---|---|
|`.\mvnw.cmd -pl backend/services/agent-integration -am test '-Dtest=AgentDirectExecution*Tests,AgentMcpBundleServiceTests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'`|6项通过，包括真实MySQL临时表、事务发布及回滚|
|`.\mvnw.cmd -pl backend/app -am package '-Dtest=Agent*Tests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'`|251项通过，0失败、0错误、0跳过，打包成功|
|`npm.cmd --prefix metersphere-mcp test`|构建及16项测试通过，0失败、0跳过|
|`pnpm.cmd -C frontend run build`|类型检查与生产构建通过；保留既有eval/大包告警|
|`pnpm.cmd -C frontend exec eslint src/views/execution/quality-policy.vue src/views/setting/system/agentIntegration/index.vue e2e/personal-agent-execution.spec.ts`|最终0错误、0警告|
|`pnpm.cmd -C frontend exec playwright test e2e/personal-agent-execution.spec.ts --list`|发现1项；仅语法和发现检查，不计作执行通过|
|`.\mvnw.cmd -pl backend/app -am package '-DskipTests'`|通过；并行构建首次JAR使用旧首页，重新打包后JAR和dist首页摘要一致；此命令不计为重复测试证据|
|`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-msp-remediation.ps1 -Stage Image`|首次Alpine源TLS中断失败；按原配置重试构建成功，未关闭TLS校验|
|`git -c core.safecrlf=false diff --check`|通过|

浏览器实际执行：

```powershell
$env:PW_BASE_URL='http://127.0.0.1:15174'
$env:MCP_CASE_LIBRARY_E2E='true'
pnpm.cmd -C frontend exec playwright test e2e/personal-agent-execution.spec.ts --workers=1
```

第一次因全新环境默认关闭管理员账密入口而显示“网页不存在”，尚未执行门禁。仅在本次专用数据库通过既有 `ui.login.admin.enabled` 配置启用标准入口后，重新执行 **1项通过，24.2秒**。正常使用管理员账号密码登录，没有绕过鉴权、伪造响应或修改远程配置。验收后恢复入口关闭。

## 容器及首次安装

使用全新项目 `msp-coverage-20261021`，单独MySQL、Redis、Kafka、MinIO、应用卷和网络；应用端口18082、健康端口17072、浏览器代理15174，全部监听loopback。未替换旧的msp-quality-verify容器。

```powershell
docker compose -p msp-coverage-20261021 --env-file .codex-tmp/direct-coverage.env -f deploy/quality-verify.compose.yml -f .codex-tmp/direct-coverage.override.yml up -d --wait mysql redis minio kafka
docker compose -p msp-coverage-20261021 --env-file .codex-tmp/direct-coverage.env -f deploy/quality-verify.compose.yml -f .codex-tmp/direct-coverage.override.yml up -d --wait --wait-timeout 360 app
node .codex-tmp/direct-coverage-proxy.mjs
```

- 正式Dockerfile构建镜像 `sha256:ba976cb518f9d0c7dbe60b9a33b8cea82a58688c480f801379923d2eb714f95d`，本地标签 `msp-quality:direct-coverage-20261021`。
- JAR SHA256：`75ffc359e028c0746abe6e0ff58ce3f293be103f73aba583b92b4e2153f709a0`。
- 全新数据库成功应用140条迁移，最新3.7.2.103；102和103均成功，失败迁移数0。此修改没有新增DDL，未执行备份恢复演练。
- `Invoke-RestMethod 'http://127.0.0.1:17072/actuator/health/readiness'` 为UP；应用healthy，restartCount=0，OOMKilled=false。
- 实际启动、健康与业务冒烟均有证据，但仍存在既有后台错误，不能用UP代表整个平台无问题。

## 遗留问题与未执行项

1. **既有后台回写排序规则错误仍可复现。** 本次全新数据库中，后台回写重试查询的两表排序规则不同，出现错误。该问题已在前一轮报告披露；本次未修改回写模块或迁移，不能声称修复。个人直接提交的数据库和浏览器验收已独立通过。若要认定整个平台交付完成，需另行修复该问题并验证后台日志。
2. **没有部署msp.ebcone.net，也未执行远程新规则验收。** 当前证据属于本机独立环境。远程部署的真实命令与主机权限尚未核实，不能给出虚构命令。部署经核实的本轮制品后，应重新连接MCP刷新工具定义，并按本报告案例复验；Node客户端也需要更新相应构建。
3. 未做备份恢复、全部用户角色组合及最大批量并发压力验证。备份恢复必须使用实际部署备份和恢复配置，暂无经核实的一条通用命令；角色和并发验证可在专用环境扩展现有e2e及MySQL测试，不应将当前单账号冒烟当作这些检查通过。
4. 结构校验不会证明实际执行，也不会验证截图含义。用例结果来自提交方声明，额外及重复失败均影响汇总；这是本次确认的规则，不是AI独立判定。

独立环境在完成取证后停止，卷和本地镜像保留；临时Token和用例由测试finally清理。专用环境凭据保存在本机 `.codex-tmp/direct-coverage.env`，已加入本地Git排除，不包含在报告及证据中。
