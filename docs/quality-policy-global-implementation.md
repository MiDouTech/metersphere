# 平台统一质量策略实施与验收记录

日期：2026-09-24；分支：v3.2。依据 `quality-policy-permission-fix-plan.md`。实现与验收仅涉及本地工作区及隔离环境，未修改生产环境；GitHub 提交与分支合并以仓库提交记录为准，不代表生产部署。

**整体状态：部分完成。** 全局配置管理和本次权限问题已有真实浏览器验收证据；执行绑定与门禁判定已实现并通过单元/数据库集成验证，但尚无真实执行器全链路及生产切换验收，不能声明所有场景已完全受控。

## 需求追踪及前后端对应

| 需求 | 实现链路 | 证据及状态 |
| --- | --- | --- |
| R1 纯系统管理员访问 | 系统设置→平台质量门禁；系统角色校验，不要求项目关系 | 纯管理员仅 admin/system、member/system 两条系统关系，无项目绑定；UI 和无 PROJECT 请求头的 API 均通过。完成本地验收 |
| R2 全平台唯一版本 | `/system/quality/policies` → GlobalQualityPolicyService → GLOBAL 单行生效指针、全局版本表 | MySQL 发布事务、并发竞争、乐观锁、已发布不可变；所有项目共用指针。完成本地验收 |
| R3 系统分权 | SYSTEM_QUALITY:READ/MANAGE/PUBLISH → UI 按钮和服务端即时查询有效系统角色 | 三种真实账号分别验证；项目权限不能提升为系统权限，禁用/撤权/Agent Token 拒绝有数据库测试。完成本地验收 |
| R4 执行实际消费 | Runner/个人认领 → attempt 快照；步骤结果 → 冻结断言与证据校验；正式回写重新校验 | 跨任务绑定一致、发布后旧快照不变、哈希篡改、跨步骤证据、较新失败结果、伪造 passed 均有测试。Runner 接入前后截图和 actual 上报。完整执行器联调未验收，部分完成 |
| R5 历史迁移 | V95～97 → 原表保留、来源映射；归档页面 → 导入全局草稿；旧链接跳转、旧写明确拒绝 | 升级前后旧版本 7 条、发布事件 4 条一致，来源映射 7 条；迁移后生效指针 NULL；导入幂等且不自动发布。归档可看项目/hash/原 JSON；只读脚本输出规范化规则及参数差异、独立元数据与无策略项目清单。代表性生产克隆演练未执行，部分完成 |
| R6 错误体验 | 统一安全错误/traceId → 新管理 API 局部 403；401 保持原登录处理；JSON 编辑保留输入 | JSON 错误定位/保稿、延迟校验防错写、权限拒绝通过。编辑中真实撤权返回局部 403、保稿及恢复权限后重试通过；断网/500 的完整浏览器矩阵未验收，部分完成 |
| R7 审计与回退 | 发布原因、前后 hash/actor/traceId 与指针同事务；拒绝日志；历史版本复制为新草稿发布 | 发布审计 UI/接口与并发事务已测。生产备份恢复、多实例切换和程序回滚未演练，部分完成 |

管理 API 为 `/system/quality`（同时保留 `/api/system/quality` 映射），拒绝项目选择参数。旧项目策略的写/校验端点明确拒绝，历史原表不改写。旧 Agent 用例结果直写单条/批量链路统一拒绝，不能以历史权限或直接提交结果绕过门禁。

Runner 新接口 `POST /internal/ai-runner/v1/lease/{id}/step-result` 从有效租约取得 task/execution/attempt 身份；请求体不能覆盖身份。客户端提交按冻结断言顺序排列的 `[{"actual": ...}]` 字符串和证据 ID。服务端不信任客户端 passed/expected。正式回写按当前 executionId 做幂等并重新校验；回写/清理不完整时不报告 PASSED/SUCCEEDED。

## 实际验证

以下命令均在仓库根目录执行，标注 frontend 工作目录的除外。日志保存在本地 `.codex-tmp`，未纳入代码提交。

| 命令 | 实际结果 |
| --- | --- |
| `pnpm.cmd -C frontend run type:check` | 通过；后续完整 build 再覆盖类型检查 |
| `pnpm.cmd -C frontend run build` | 生产构建通过，见 global-policy-build.log |
| `pnpm.cmd -C frontend exec eslint e2e/quality-policy.spec.ts src/api/modules/execution-quality.ts src/api/modules/ai-execution.ts src/views/execution/quality-policy.vue src/utils/permission.ts src/store/modules/user/index.ts src/router/routes/modules/setting.ts src/router/routes/modules/execution.ts src/router/routes/execution-settings.ts src/api/http/index.ts` | 0 错误；执行页面原有 unused handleEnvironmentChange 警告。E2E 文件最终单独 lint 通过 |
| `node frontend/scripts/test-global-quality-permissions.mjs` | 3/3 通过 |
| `node frontend/scripts/test-execution-navigation.mjs` | 9/9 通过 |
| `node frontend/scripts/verify-permission-resource-coverage.mjs` | 93 个精确绑定、50 个兼容绑定、0 未绑定 |
| `node frontend/scripts/verify-api-contracts.mjs` | 通过 |
| `node scripts/verify-route-tabs.mjs`（frontend 目录） | 通过；首次从根目录运行报路径错误，纠正工作目录后通过 |
| `.\mvnw.cmd -pl backend/app -am package '-Dtest=Agent*Tests,QualityPolicy*Tests,GlobalQuality*Tests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dquality.mysql=true'` | BUILD SUCCESS，238 项测试通过、无失败/跳过，完成应用打包；global-policy-final-package.log |
| `.\mvnw.cmd -pl backend/services/agent-integration -am test '-Dtest=GlobalQualityPolicyMySqlTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dquality.mysql=true'` | 补充最新结果/证据范围回写测试后，6/6 数据库集成测试通过；不与上一行重复累加 |
| `$env:MS_RUNNER_SMOKE_EXECUTABLE="$env:LOCALAPPDATA/ms-playwright/chromium-1223/chrome-win64/chrome.exe"; npm.cmd --prefix ai-browser-runner test` | 8/8 通过、无跳过，包含真实 Chromium 断言取值及范围边界 |
| `docker build -f Dockerfile.backend --build-arg "DEPENDENCY=backend/app/target/quality-global-20260924172708263" -t msp-quality:verify .` | 应用镜像构建成功；本次目录见 global-policy-dependency.txt |
| `docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml up -d --wait --wait-timeout 180 app` | 应用和 MySQL/Redis/MinIO/Kafka 均 healthy |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-quality-environment.ps1` | readiness、迁移、事务写入回滚、Redis、MinIO、Kafka 元数据均通过 |
| `$env:QUALITY_E2E='true'; $env:PW_BASE_URL='http://127.0.0.1:15173'; pnpm.cmd -C frontend exec playwright test e2e/quality-policy.spec.ts --timeout=90000` | 原 8/8 通过，58.1 秒；补充编辑中撤权测试使用相同命令加 `--grep "revocation during editing"`，1/1 通过（12.6 秒），合计 9 个浏览器场景；真实后端，延迟校验仅延迟请求、不模拟响应。首轮失败为 fixture 账号缺 admin 关系和隐藏弹窗选择器，修正后重跑全部通过 |
| `npx.cmd --yes --package @playwright/cli playwright-cli -s=mspglobal ...` | 真实纯系统管理员登录、打开系统质量门禁并截图；global-policy-pure-admin.png |
| `git diff --check` | 最终通过 |

迁移记录 `3.7.2.95/96/97 success=1`；历史数量在 E2E 后仍为 7/4。运行镜像 ID：`sha256:7f5330ff685154d1178e1a32f930f875f47951f3b11a309f78be13601495f0c1`。容器与本地前端 index.html SHA256 一致：`1a006a77cc8793ecd928605a2812e7d2ac73bbb69255986074b4d1e7c5ad5d66`。E2E 已在隔离库显式发布测试版本，与迁移自身不会发布策略的验证阶段分开。

## 未完成验证、风险和后续操作

1. **Runner 容器构建受外部镜像仓库阻断。** 两次执行 `docker build -t msp-quality-runner:verify ai-browser-runner`，拉取 `node:22-bookworm-slim` 均因 Docker Hub HEAD 请求 EOF 失败。未运行该新 Runner 容器的启动与真实控制面联调；网络恢复后重跑该命令，按 `ai-browser-runner/README.md` 注册隔离 Runner 并配置令牌、允许域名，再启动执行。不要将应用容器 healthy 当作 Runner 验收。
2. **真实执行器端到端矩阵未完成。** 尚未跑通真实模型规划/外部执行器认领、对象存储证据上传下载、成功/失败回写、重试、在途发布、审批及历史无绑定任务的全部组合。现有 MySQL 测试对绑定/策略/步骤结果使用真实数据库，对 artifact Mapper 使用受控测试对象；Chromium 测试验证浏览器取值，不冒充平台完整联调。不能以现有自动化数量替代该验收。
3. **兼容性变化。** 旧结果直写接口拒绝所有结果，旧执行器必须升级为尝试链路；旧任务缺少绑定不允许正式回写成功。新模型计划需明确 EQUALS/IN_RANGE 及冻结 expected；旧 CONTAINS/隐式布尔/动态 dataset 预期不能直接放行。详情见 Runner 与 MCP README。
4. **全局首次发布是部署前置条件。** 正式环境不会自动选取任何旧项目标准；管理员须核对项目/hash/规则并导入或创建全局草稿，再明确发布。未发布时新认领拒绝。只读预检脚本已实现结构化差异报告，未对生产数据作选择或发布。
5. **生产切换仍待验收。** 本次仅在隔离历史样本库演练，没有代表性生产克隆、多实例、备份恢复、规则回退完整 E2E 或程序回滚证据；不得直接滚回继续消费项目策略的旧程序。不能将本地通过表述为生产可用。
6. 门禁证据大小仍同时受现有上传上限约束（默认 5 MiB）；策略更宽松不绕过上传安全上限。发布审计包含 traceId，页面展示主体和前后摘要，拒绝/冲突进入安全应用日志。

## 迁移差异预检

将所有归档 API 分页合并为 `{ "items": [...], "total": 总数, "projects": [完整项目ID清单] }`，保存为 UTF-8 JSON，然后执行：

```powershell
python scripts/quality-policy-migration-report.py --input archive.json --output migration-report.md
python scripts/test-quality-policy-migration-report.py
```

脚本只读输入并生成 Markdown；拒绝未收齐的分页，按规则 ID 和参数内容比较、规范化集合参数顺序，名称/schemaVersion 等元数据单列；保留原始 hash 与每条来源、草稿/发布状态。不会选中候选、更新数据库或自动发布。未提供完整项目清单时明确标注无策略项目尚未评估。

本次在隔离库导出 7 条真实旧策略生成 `.codex-tmp/global-policy-migration-report.md`，识别 1 组规则内容、0 条无效记录；另有 3 项自动化覆盖相同规则不同名称/数组顺序、参数差异、损坏文档和不完整分页，全部通过。生产预检文件需妥善保管，包含原始策略来源信息。

## 修改文件清单

本次改动文件如下；用户原有 test-plan Mapper、模板 README、周报和其他研究文件均保留。

- `ai-browser-runner/README.md`
- `ai-browser-runner/src/client.ts`
- `ai-browser-runner/src/executor.ts`
- `ai-browser-runner/src/types.ts`
- `ai-browser-runner/test/browser-smoke.test.ts`
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_95__global_quality_policy.sql`
- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_97__quality_attempt_binding.sql`
- `backend/framework/domain/src/main/resources/migration/3.7.2/dml/V3.7.2_96__global_quality_permissions.sql`
- `backend/framework/sdk/src/main/java/io/metersphere/sdk/constants/PermissionConstants.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentExecutionExceptionHandler.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentRunnerInternalController.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/GlobalQualityPolicyController.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/QualityPolicyController.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/dto/AgentExecutionTaskDTO.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/dto/AgentRunnerLeaseAssignmentDTO.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/quality/GlobalQualityGate.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/quality/GlobalQualityPolicyService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/quality/QualityPolicyAccess.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentExecutionCaseWritebackService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentExecutionPlanningService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentExecutionService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentExecutionStepResultService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentExecutionWritebackService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentFunctionalCaseSubmitService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentRunnerService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentSafeErrorMapper.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentTaskClaimService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentTaskExecutionApplicationService.java`
- `backend/services/agent-integration/src/main/resources/ai-contract/execution-contract-v1.schema.json`
- `backend/services/agent-integration/src/main/resources/permission.json`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/quality/GlobalQualityAccessMySqlTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/quality/GlobalQualityGateTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/quality/GlobalQualityPolicyControllerTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/quality/GlobalQualityPolicyMySqlTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/quality/QualityPolicyControllerTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentExecutionPlanningServiceTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentFunctionalCaseSubmitServiceTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentTaskExecutionApplicationServiceTests.java`
- `deploy/quality-verify/README.md`
- `deploy/quality-verify/global-rbac-fixture.sql`
- `docs/quality-policy-global-implementation.md`
- `docs/quality-policy-permission-fix-plan.md`
- `frontend/e2e/quality-policy.spec.ts`
- `frontend/scripts/test-global-quality-permissions.mjs`
- `frontend/src/api/http/index.ts`
- `frontend/src/api/modules/ai-execution.ts`
- `frontend/src/api/modules/execution-quality.ts`
- `frontend/src/router/routes/execution-settings.ts`
- `frontend/src/router/routes/modules/execution.ts`
- `frontend/src/router/routes/modules/setting.ts`
- `frontend/src/store/modules/user/index.ts`
- `frontend/src/utils/permission.ts`
- `frontend/src/views/bug-management/automationExecution/index.vue`
- `frontend/src/views/execution/quality-policy.vue`
- `frontend/types/axios.d.ts`
- `metersphere-mcp/README.md`
- `scripts/verify-quality-environment.ps1`
- `scripts/quality-policy-migration-report.py`
- `scripts/test-quality-policy-migration-report.py`
