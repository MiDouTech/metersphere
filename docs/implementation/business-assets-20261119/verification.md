# 业务资产重建验证记录（开发草稿）

本文件记录实际执行结果。代码开发及独立代码审查阶段已结束，运行验证存在阻塞，未部署、未开展正式验收。批准产品文档与需求链路见 traceability.md。

## 已执行

| 命令（在仓库根目录执行，另有标注除外） | 实际结果 |
| --- | --- |
| `python scripts/verify-business-asset-migrations.py --port 13306` | 真实隔离 MySQL 8.0.46；150–156 迁移全部执行，8 项数据/权限/导航检查通过；结果见 mysql-migration-verification.json。不连接业务数据库。 |
| `node frontend/scripts/test-business-assets.mjs` | 5 项通过：归类及实际 SFC 编辑函数异步竞态。 |
| `node frontend/scripts/test-execution-navigation.mjs` | 9 项通过：五个资产主入口、权限默认页、历史链接及执行导航。 |
| `npm run test:api-contracts`（frontend） | 通过；R3 变更后复测也通过。 |
| `npm test`（metersphere-mcp） | 46 项通过，包含新增业务资产与执行器工具契约。 |
| 变更范围 `npx eslint <files>`（frontend） | R3 变更文件检查通过。 |
| `npm run build`（frontend） | R3 生产构建通过，exit 0（含 vue-tsc）。 |
| `git diff --check` | 通过；R4 最终差异复查通过。 |

后端完整命令：

```powershell
.\mvnw.cmd -pl backend/app -am '-Dtest=BusinessAssetServiceTests,SharedConfigurationServiceTests,ExternalExecutorServiceTests,BusinessGovernanceExceptionHandlerTests,AgentHistoricalCompatibilityTests,AgentExecutionExceptionHandlerTests,BusinessAssetMcpTests,AgentMcpBundleServiceTests,AgentMcpStreamableServiceTests,AgentMcpWriteRoutingTests,AgentMcpToolContractTests,AgentMcpStartupTests,AgentTokenScopeParserTests,TestAssetControllerPermissionTests,PermissionManifestCoverageTests' '-Dsurefire.failIfNoSpecifiedTests=false' package -q
```

R2：56 项执行，55 通过、1 失败；失败为既有 linked.preview 重放路由遗漏，已修正，R3 复测通过，13 个套件 59 项零失败零跳过；R4 增加历史兼容与日志故障测试后，15 个套件 68 项全部通过，零失败、零跳过；完整应用 package exit 0。逐套件结果及原始报告哈希见 backend-test-verification.json。此前的测试实例化和 MockMvc Accept 合约问题也已修正；没有把失败轮次记为通过。

服务测试通过真实事务代理执行 H2 持久化，覆盖 HTTP JSON、项目对象隔离、幂等、冲突版本、历史切换、合并、配置批量回滚、加密与脱敏、固定上下文、执行器事件/指令及故障注入。它们不等同于浏览器到真实服务器的端到端验收。

## 未运行及风险

- Docker 镜像/容器构建：用户于 2026-11-19 明确取消强制验证，按用户要求不执行，不再作为交付阻塞条件；不声称构建通过。批准产品 v1.0 原文及其哈希保留，本次验证规则调整以当前用户指示和 AGENTS.md 更新为准。
- 容器启动和健康检查：本机未发现 Docker/桌面安装，WSL 查询失败；尚无可用隔离容器验证环境，未运行，仍不能声称系统可启动。具备环境、现成验证镜像并安全配置隔离测试凭据后，可执行以下项目现有验证路径（不用于 CDS 或共享环境）：

```powershell
docker compose -p msp-business-assets-review -f deploy/quality-verify.compose.yml config --quiet
docker compose -p msp-business-assets-review -f deploy/quality-verify.compose.yml up -d
docker compose -p msp-business-assets-review -f deploy/quality-verify.compose.yml ps
Invoke-WebRequest http://127.0.0.1:17071/actuator/health/readiness
```

现有 compose 需要安全设置 `QUALITY_DB_PASSWORD`、`QUALITY_STORAGE_PASSWORD`、`QUALITY_FACTS_KEY`；新配置/证据功能还需通过隔离 override 注入 `MSP_SHARED_CONFIGURATION_ENCRYPTION_KEY`，不能把值写进仓库或命令日志。该 compose 的 Kafka 仅为现成验证依赖，本任务未启动、配置或排查 Kafka。此处命令均未执行。

- 浏览器核心流程端到端和真实外部 AI 执行器联调：没有启动的完整应用及可用测试身份；没有运行。须覆盖新建/AI 上传/重复提交/冲突保留/分支归类/历史替换/配置上传与解析失败/运行中上下文替换/纠偏响应/跨项目拒绝/隐藏权限。推荐在用户通知测试环境部署完成后由 Agent 执行并留证。
- 本次仅执行所列后端相关测试，未宣称全仓库所有测试通过。
- 新增数据库升级迁移在合成历史样本上验证；生产真实数据规模及所有历史异常形态未验证。148/149 历史迁移保持原字节。
- 加密密钥需要安全注入及备份，未实现在线轮换；没有新增外部告警渠道。缺少密钥时明确失败，不伪装成功。详见 configuration.md。

## 交付边界

R4 精确代码差异审查已通过。用户随后明确要求提交代码并提 PR，按本次授权推进提交、自有分支合并推送及上游 PR；容器启动、健康检查与真实端到端验收仍未执行，不把提交或 PR 计为功能验收通过。未部署。

## 最终产物及审查绑定

R4 整体代码审查通过；精确差异及 195 个文件清单见 review-r4-manifest.json、review-r4.diff 和 review-record.md。逐文件哈希复核无审后代码变化。应用 JAR 检查确认新服务存在、被移除受控类不存在、156 迁移及最新前端已入包；证据见 package-verification.json。完整列表既包含本次新增/修改，也明确记录被删除文件，不将历史文档迁移和用户既有 AGENTS.md 修改混作新功能。

交付指示更新：用户明确要求“提交代码，并提pr”，因此继续代码提交及 PR；运行验证缺口保留在 PR 中。上游 PR 不由 Agent 合并，部署及正式验收仍待用户通知。
