# MSP独立会话验证记录（2026-11-24）

范围：用户已批准的 sso-independent-session-plan.md。SSO仅验证初次登录身份；MSP独立14天绝对期限、并发多浏览器、仅当前MSP退出。先前全局退出讨论不再是验收条件。没有修改其他仓库，没有部署测试环境。

## 实现对应

|需求|实现文件与链路|证据|
|---|---|---|
|不受SSO令牌期限影响|MiduoSsoApplicationService.callback不保存上游sessionToken；LoginController.is-login不请求refresh；移除MiduoSsoRefreshService；前端user store不按旧needMiduoReauth跳桥|新增MspIndependentSessionTests、MspSessionHttpIntegrationTests、test-independent-session.mjs|
|14天由MSP管理|SessionLifetime绝对期限不滑动；修正新旧存活会话的空闲保留时间及Servlet秒粒度换算；服务端filter和is-login均检查绝对到期|SessionLifetimeTests与HTTP过期拒绝|
|多浏览器并存与当前退出|callback更换当前浏览器会话；退出只销毁当前Subject并清理该会话的旧Redis记录，不调用全用户退出或上游revoke|HTTP双会话登录、A退出后旧凭据401/B仍200|
|账号及权限控制|保留UserLoginService即时读取及现有管理员撤销；is-login禁用/删除返回401并销毁；角色重新写入当前SessionUser|HTTP权限允许→撤销→拒绝、账号禁用；controller删除账号测试|

## 执行记录

- 首轮旧行为回归：5项中1通过、2断言失败、2错误，确认旧逻辑保留过期重认证标记、is-login直接调用没有绝对到期拒绝以及禁用/删除异常。
- 前端行为测试：`node --test scripts/test-independent-session.mjs`，3项通过。使用真实user store转译执行、模拟API与浏览器环境，不是已部署浏览器验收。
- 前端lint：`npx eslint src/store/modules/user/index.ts src/models/user.ts scripts/test-independent-session.mjs --max-warnings 0`，通过。
- `npm run build`（含vue-tsc类型检查、Vite生产构建与manifest生成）：通过。index SHA256为 `1115cd70d89d642713de775d25be30adf58f1242f5cb854498b646fe2e03be6a`。manifest显示工作区基线e1fd443，构建包含未提交修改，不将其声称为已提交版本。
- 最终后端system-setting相关27项测试已通过、0失败/0跳过：独立会话5、期限3、HTTP6、脱敏2、SSO客户端3、历史存储隔离1、既有Agent桥7。
- 最终全应用验证命令：`./mvnw.cmd -pl backend/app -am -Dtest=Msp*Session*Tests,SessionLifetimeTests,MiduoSso*Test*,PermissionSession*Tests,Agent*Tests,SharedConfiguration*Tests,EvidenceCatalog*Tests,CaseAsset*Tests,FunctionalCaseImport*Tests,FunctionalCaseFile*Tests,DefaultHubCaseSync*Tests,TestAsset*Tests,ExternalExecutor*Tests,PermissionManifestCoverageTests -Dplan.review.repo.root=C:/SoftWare/JetBrains/metersphere -Dsurefire.failIfNoSpecifiedTests=false -DskipAntRunForJenkins=true package`。实际运行使用PowerShell单引号保护各-D参数。结果：BUILD SUCCESS；本轮164个实际运行测试类共609项，549项执行通过、60项条件跳过、0失败/错误。统计仅包含最终日志出现的Running测试类，排除了磁盘上旧测试报告。
- app.jar打包通过；已检查其嵌入的system-setting.jar与本轮产物逐字节hash一致，旧MiduoSsoRefreshService.class不存在。完整安全汇总见 proof/sso-final-results.json，源码绑定见 proof/sso-source-manifest.json。
- 独立静态复审通过，无剩余确认问题；报告见 reviews/independent-sso-review-r2.json，绑定源码及删除项，不冒充指定外部审查人员。
- 审查及提交记录：仓库AGENTS.md第13行要求“审查通过后提交”，已尝试向指定0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0会话送审，工具返回“No Codex thread found”；没有伪称送达/批准，也没有新建替代会话。用户得知此情况后明确指令“提交代码，提交PR”，现按该后续指令推进提交及PR，保留独立审查和指定会话不可达的真实记录。实际返回见 proof/review-handoff-status.json。源码已验证，后续新增记录仅为文档，不改变隔离验证过的代码；提交前精确差异及SHA记录在工作区 .tmp/asset-remediation-20261124/final-review.json 和 final-review.patch。
- 排除原有无关工作区修改后，对精确暂存树 `850450dcc0b969074d0c9a31cc611299ef0bd46c` 隔离执行同一选择器的 `clean package`：BUILD SUCCESS，164类共608项，548执行通过、60条件跳过、零失败/错误。导出的37项差异与Git blob逐字节一致，两个删除项不存在，app内嵌系统模块字节一致，旧续期class不存在。此隔离结果是交付源码的权威构建证据，工作区609项结果另列，不能混为同一个测试总数。安全汇总见 proof/sso-isolated-final-results.json。

HTTP集成测试使用真实Spring Session RepositoryFilter、MapSessionRepository、Shiro、LocalRealm、SSO应用服务及登录控制器，外部SSO和用户数据源为合成夹具。它不是Redis容器或完整生产CSRF/APIKey链路验收，不声称真实SSO端、数据库或部署健康已经通过。

6项HTTP覆盖：两个独立浏览器会话及A退出后A旧凭据401/B仍200；绝对期限拒绝；账号禁用；实际LocalRealm权限允许→撤销→403且期限不变；12小时空闲超过上游8小时仍保持MSP会话；旧缓存/审计故障仍完成本地退出且日志没有私密URL、凭据和异常堆栈。12小时空闲是修改合成会话最后访问时间的可控验证，不等同于已在测试站等待12小时。

无需增加或调整SSO续期配置。历史 session-ttl-seconds、refresh-ahead-seconds、login-grace-seconds 不再控制已建立MSP会话；旧Redis上游令牌不再读取/新写入，仅保留兼容清理。仍有效的旧MSP会话保留原绝对期限；已经从存储过期删除的会话不会被恢复，需要重新登录。

## 部署验收与限制

- 按用户约定不执行Docker镜像/容器构建。
- 本次不涉及数据库迁移，未新增数据库结构。
- 本轮60项条件跳过包含MySQL、Redis与公共图片外网测试；完整名单见隔离汇总JSON。它们不属于本轮SSO会话27项测试，不能计为通过；此前资产原生MySQL16项证据另存，不据此覆盖全部跳过项。若需补跑，在专用本机测试MySQL `127.0.0.1:13326`、Redis `127.0.0.1:16479` 与允许外网条件就绪后分别执行（不要指向业务数据库）：
  - `./mvnw.cmd -pl backend/app -am '-Dtest=*MySqlTests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-DskipAntRunForJenkins=true' test`
  - `./mvnw.cmd -pl backend/services/agent-integration -am '-Dtest=AgentTokenRateLimiterRedisTests' '-Dquality.redis=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-DskipAntRunForJenkins=true' test`
  - `./mvnw.cmd -pl backend/services/agent-integration -am '-Dtest=AgentExternalImageNetworkTests' '-Dexternal.image.network=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-DskipAntRunForJenkins=true' test`
- 新版本容器启动、健康检查、真实SSO与双浏览器、长时间空闲验收，需用户部署后执行；不能拿目前旧站点代替新代码验收。
- 部署后容器状态核对命令：`docker inspect metersphere --format 'status={{.State.Status}} restarting={{.State.Restarting}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}not-configured{{end}}'`。此处仅提供验证命令，未在服务器执行，也未重启或部署。
- 部署后分别在浏览器A/B通过SSO登录，验证各自业务页；退出A后刷新A应回登录，B继续可用；SSO短期令牌过期后仍在MSP14天期限内时继续可用；MSP自身到期后旧凭据必须401。
- 不保证退出MSP后SSO强制扫码，不影响其他米多业务；用户已从本轮范围移除全局退出及重新扫码要求。
- 会话建立后MSP本地账号、角色和管理员撤销是访问控制依据；上游SSO退出或短期令牌失效不再主动使MSP会话失效。
- 前端生产构建已有eval告警（包括现有登录表单及第三方库），本轮不将这些既有告警称为已消除。
