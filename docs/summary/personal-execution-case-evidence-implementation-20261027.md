# 个人 Agent 用例级证据：实施报告

日期：2026-10-27  
状态：**部分完成**。核心代码已落地，本地聚焦验证通过；尚未部署本轮代码，未完成全部设计项及整体验收。既有未提交代码保留，本轮未提交或推送 Git。

## 需求追踪与前后端对应

| 需求 | 入口/客户端 | 后端、数据 | 实际验证/状态 |
|---|---|---|---|
| 一用例至少一图，无逐步骤图片与客户端 Hash 必填 | Node caseLibrary；原生 DirectExecutionTools/ExternalImageTools | AgentDirectExecutionService、GlobalQualityGate；131迁移 | 100步骤一图、同 requestId 上传重放及提交、ERROR 优先、失败回滚通过；未做部署 E2E |
| 冻结定义及兼容旧证据 | contract/upload/submit | definition-v2 白名单、加密快照、STEP 保守兼容 | 单元及 MySQL 聚焦用例通过；生产历史数据未核查 |
| 图片可读取且不泄露跨项目资源 | functional.attachment.read；Node 图像结果透传 | FunctionalAttachmentReadTool、AgentMcpImageResult、StreamableService | 普通附件与提交历史读取、删除普通关联后的历史读取及跨项目拒绝通过 |
| 事实展示、执行身份与计划同步 | ms-execution-facts/index.vue | AgentDirectExecutionViewProvider；FunctionalCaseService、TestPlanFunctionalCaseService、Hub导入；current_execution 表 | 活动计划同步、归档隔离、清除旧引用、JSON 保持结构脱敏通过；页面视觉/交互未验收 |
| 幂等及当前权限复核 | 原生 MCP、Node；计划创建/关联 Agent REST | OperationExecutor、RequestFingerprint、GenericMcpWriteService、BusinessAccess；132/133迁移 | 真 MySQL 并发、冲突、旧别名、大小写、权限撤销及组织权限测试通过 |
| 导入与上传身份持久化 | 既有导入、上传工具 | CaseImportService、AttachmentUploadIntentService、IdempotencyService/Mapper | 已实现128字符、版本化指纹、身份墓碑、完成上传重放；相关 SQL 回归见验证记录 |
| 客户端/技能包契约一致 | metersphere-mcp 0.8.0、BundleService | 请求模式、错误安全转换、个人操作查询 | Node测试与真实 npm 包 stdio 冒烟通过；未发布 npm |

Runner 任务、租约、断言协议未改为 CASE 模型。首期 TEXT 用例仍拒绝写入，contract 明确说明。结构验收不等于验证图片语义或真实执行。

## 实现要点及异常行为

- CASE 图片数量1～10；上传继续验证 PNG/JPEG、5MiB、最小尺寸与总像素。步骤保留覆盖、重复与额外步骤校验，不要求图片逐步骤绑定。
- 定义只取用例身份、项目、版本、名称、类型、描述、前置条件、等级、自定义字段和值及有序原步骤，排除执行与展示字段。服务端负责 Hash，快照保持可追溯。
- 上传过期、已消费、归属不符、定义冲突、无当前权限均拒绝；事务内锁定并关联证据、提交和附件，异常回滚。结果重放仍重新校验当前权限。
- JSON 先解析再逐字段脱敏再序列化，避免正则直接破坏结构；技术细节保留日志，对调用方提供安全错误及 traceId。
- 17个通用业务 MCP 写入口纳入统一原子操作协议；导出任务保留自身持久任务协议。未配置安全协议的新业务写入口拒绝执行，路由清单测试防止漏接。
- 新记录使用 binary-v2 请求身份；旧 CI 记录仍按旧比较规则查找。未知 Hash/状态、多条别名或缺失不可确认结果时要求核对，不擅自重做。
- 测试计划 REST 读取补项目权限检查；已授权写操作返回 DTO 不额外依赖 PLAN_READ，避免 WRITE Token 被隐含读取权限阻断。

## 修改文件

以下列本轮主要文件；工作区已有其他未提交需求改动，不能把整个 git diff 归为本轮。

- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/`：AgentDirectExecutionService、AgentDirectExecutionViewProvider、AgentOperationExecutor、AgentRequestFingerprint（新增）、AgentGenericMcpWriteService（新增）、AgentBusinessAccess、AgentIdempotencyService、AgentAttachmentUploadIntentService、AgentCaseImportService、AgentMcpStreamableService、AgentSensitiveDataSanitizer、AgentExecutionAuditService、AgentTestPlanWriteService、AgentMcpMaintenanceService、AgentProjectService、AgentMcpBundleService、AgentExternalImageService。
- 同模块 `tool/`：AgentMcpImageResult（新增）、AgentMcpToolSchemas（新增）、BuiltinAgentMcpToolConfig；`tool/functional/` 下 AgentDirectExecutionTools、AgentExternalImageTools、AgentCaseImportTools、FunctionalAttachmentReadTool；dto 中计划创建/关联请求。
- `backend/framework/domain/`：AgentIdempotencyRecord、AgentIdempotencyRecordMapper.xml；migration/3.7.2/ddl 的131、132、133迁移。
- `backend/services/case-management/`：FunctionalCaseService、ExtFunctionalCaseMapper.xml、DefaultHubCaseImportService；`backend/services/test-plan/`：TestPlanFunctionalCaseService；质量门禁 GlobalQualityGate。
- `frontend/src/components/business/ms-execution-facts/index.vue`。
- `metersphere-mcp/`：package.json/package-lock.json、README、src/client.ts、src/index.ts、src/tools/caseLibrary.ts、src/tools/planReview.ts、证据/错误/工具契约测试。
- agent-integration 测试：DirectExecution、PersonalOperation、Operation、LibraryLifecycle、Streamable、Bundle、CanonicalCache、WriteRouting 等聚焦测试及夹具。
- 本实施报告与原方案第25节。

## 实际验证

所有 Java 编译/测试串行运行，编译堆192MiB、测试堆128MiB；复用既有独立 MySQL，测试使用临时数据库，不启动第二套环境。

| 命令 | 结果 |
|---|---|
| `javac -J-Xmx192m '@.codex-tmp/personal-evidence-20261027/compile-final.args'` | 通过；基于已有依赖和编译产物的增量源文件编译，不能等同 Maven 全量构建 |
| `java '@.codex-tmp/personal-evidence-20261027/unit-current.args'` | 23测试通过，0跳过 |
| `java '@.codex-tmp/personal-evidence-20261027/mysql-current.args'` | 4测试通过，0跳过；包含100步骤证据与并发/权限/迁移 SQL 场景 |
| `java '@.codex-tmp/personal-evidence-20261027/mysql-broader.args'` | 20测试通过，0跳过（最终代码串行运行约49秒） |
| `npm --prefix metersphere-mcp run build` | 通过，0.8.0 |
| `node --test metersphere-mcp/test/*.test.mjs` | 27测试通过 |
| `npm pack --ignore-scripts`（metersphere-mcp目录，产物输出临时目录） | 已生成0.8.0 tgz，未发布 |
| 对解包产物设置 `MS_TEST_BINARY` 后运行 `node --test metersphere-mcp/test/remediation.test.mjs` | 6测试通过，实际包 stdio 冒烟 |
| `node --max-old-space-size=128 frontend/node_modules/eslint/bin/eslint.js frontend/src/components/business/ms-execution-facts/index.vue --no-fix` | 通过 |
| Vue compiler-sfc 编译该组件 script/template | 通过 |
| ElementTree 解析变更 Mapper XML、`git diff --check` | 通过（最终文档写入后再次核验） |

临时参数文件和日志位于 `.codex-tmp/personal-evidence-20261027/`，不属于可移植 CI 配置。SQL测试部分业务定义/外部依赖采用 mock，不能替代 HTTP 权限过滤器、真实迁移链及浏览器验收。此前旧评论测试因新增真实线程成员校验失败，已修正夹具并保留线程外用户/权限撤销拒绝断言，非跳过校验。

## 尚未完成的实现

1. 全部 Agent REST 业务写入口尚未统一纳入原子幂等；已覆盖计划创建/关联，其他 REST 入口需逐项接入和并发回归。MCP业务原子覆盖不能代表 REST 同样覆盖。
2. operation.get 当前限定个人执行的三个上传/提交工具；其他业务使用原工具同 key 重放或自身任务查询，尚无全业务统一操作查询。
3. canonical-v1 结果长期保留，最小墓碑/归档/容量告警未完整交付；旧缓存过期只清响应保留身份，完成上传记录保留。
4. 生产历史别名、非ASCII/超长 requestId、旧快照能否重建尚需迁移前排查；不可验证的旧结果保守要求人工核对。
5. 写开关为启动时配置，并未实现运行时动态灰度开关；不支持新旧写节点混跑。

## 未执行的整体验证与补验方式

当时空闲内存约0.5～0.6GiB，为避免本机再次卡死，没有运行下面的大型任务。还存在依赖/全量编译、完整迁移、HTTP安全链、生产布局和部署兼容风险。

在具备资源的隔离环境串行执行：

```powershell
.\mvnw.cmd -pl backend/services/agent-integration -am test
npm --prefix frontend run type:check
npm --prefix frontend run build
.\mvnw.cmd -DskipTests package
```

上述命令本轮未执行，可能暴露既有未提交代码的额外问题。Docker构建及健康检查必须使用仓库实际发布流程和新产物，不能拿运行三天的旧实例充当新版本证据；完整 Flyway 需在完整历史数据库副本执行，131～133的临时表场景测试不能替代完整迁移。Docker、实际部署和浏览器 E2E 未给出“已通过”结论。

## 发布与兼容约束

先升级 Node 0.8.0：计划创建与关联现在要求 requestId，旧客户端缺字段将被拒绝。先停止写入并排空请求，备份数据库，预查别名/大小写冲突，按正式链执行迁移（含工作区既有123～130），统一更换所有节点，再放开写入。

可用启动配置：`agent.personal-execution.writes-enabled=false`、`agent.mcp.business-writes-enabled=false`。关闭时拒绝新操作、允许已成功请求按权限重放；需要重启生效。新 CASE 数据写入后只可回退到理解新结构的兼容版本，不能直接恢复 STEP-only 旧应用。

最终验收还需真实环境完成：一图100步骤、Token撤权后重放、同key并发/断网重试、图片普通/历史读取、事实面板显示、活动/归档计划隔离及正常执行后旧引用消除。所有设计项和上述证据齐备之前，整体状态维持部分完成。


补验命令补充（本轮未执行）：在资源足够、已停止其他验证环境且已设置 `QUALITY_DB_PASSWORD` / `QUALITY_STORAGE_PASSWORD` 后，使用已有隔离验收流程；镜像脚本会核查打包依赖，不能跳过完整打包直接使用旧 JAR。

```powershell
python scripts/build-isolated-implementation.py --tag msp-implementation20261022:verify
docker compose -p msp-implementation20261022 -f deploy/implementation-verify.compose.yml up -d --wait mysql redis minio kafka
docker compose -p msp-implementation20261022 -f deploy/implementation-verify.compose.yml --profile migration run --rm migration
docker compose -p msp-implementation20261022 -f deploy/implementation-verify.compose.yml up -d --wait app
docker compose -p msp-implementation20261022 -f deploy/implementation-verify.compose.yml ps
```

该流程依赖已有 `.codex-tmp/implementation-migration/IsolatedMigration.class`；迁移器需先确认与当前完整迁移资源一致。上述空库链验证后，仍需在历史数据库脱敏副本重复升级验证，不能直接针对共享测试库执行。
