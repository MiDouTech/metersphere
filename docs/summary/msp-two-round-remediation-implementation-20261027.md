# 两轮问题修复实施记录（2026-10-27）

状态：实施中；不代表部署验收通过。基准：[详细修复方案](msp-two-round-test-remediation-plan-20261027.md)。保留原两轮测试证据及未提交内容。

| 需求 | 前端入口/实现 | 后端接口/实现 | 数据/配置 | 验证与状态 |
|---|---|---|---|---|
| D01/D08 | 测试用例导入/预检弹窗 | ai/case-imports、ImportFollowupCoordinator | 原job/group/item | 代码已修改；见下方验证边界 |
| D02 | 个人中心Agent配置、MsTable | 原Token接口不变 | 无 | 代码已修改；见下方验证边界 |
| D03/D04 | 资产导出、Node导出 | CaseAssetExportAccess、FunctionalCaseExportTaskService | request_key扩列 | 代码已修改；见下方验证边界 |
| D05 | 资产列表同步诊断 | CaseAssetService、outbox、hub sync | 原来源映射/事件 | 代码已修改；见下方验证边界 |
| D06 | 用例资产等级 | 原资产响应 | 无 | 代码已修改；见下方验证边界 |
| D07/D13 | 用例目录移动、重名提示 | ModuleTreeService、module service、错误映射 | 无 | 代码已修改；见下方验证边界 |
| D09/D10 | Node工具、个人MCP | 原生schema、安全错误 | 包版本/客户端刷新 | 代码已修改；见下方验证边界 |
| D11/P01 | case/plan/review样式、Demo | 原业务API不变 | 无 | 代码已修改；见下方验证边界 |
| E01 | 管理/部署配置 | AgentFactCipher | 受控事实加密密钥 | 就绪诊断和部署文档已添加；真实配置与历史密文验收待执行 |
| D14新增 | 缺陷详情基本信息/标签 | 原bug更新接口不变 | 无 | 布局代码已修，真实多标签/长标签/窄侧栏页面验收待执行 |

异常与权限：保留原业务权限；请求取消不当业务失败；版本/幂等冲突不得强制覆盖；不降低证据/加密门槛。UI只改布局，保留原事件与按钮。

资源：开始时约1GiB空闲；验证串行、限制Node/JVM堆；不启动第二套环境，不强跑内存不足的全量构建。不能完成的运行验证单独列明。

## 已落地修改及前后端对应

- D01：导入弹窗改为独立分页依赖，按任务/页码去重，选项/评审人请求复用在途 Promise；关闭/换任务后迟到响应不能落到新弹窗。新增直接运行真实组件 setup 的两个回归测试，分页依赖保留动态 total。
- D02：Token 启用及操作列固定在右侧，修正表格容器宽度约束和横向滚动。真实1908/1366视口仍待测。
- D03/D04：导出持久化读取统一为真实列 `request_payload`；DTO、原生 MCP、Node 和服务校验统一128位标识，新增134扩列迁移；修改真实MySQL导出测试覆盖128位带点/冒号标识。迁移与真实文件生成未执行。
- D05：消费者按项目身份确保资产目录；映射歧义拒绝随意选择。个人执行发布后记录同步事件。补齐未首次执行的事件展示；新增状态查询与最多100条补偿入队，目录锁及已有未完成事件检查防止补偿重复入队。已知映射冲突显示受控原因及排查编号，未知异常继续保留安全提示。
- 新接口 `GET /case-asset/catalog/{id}/sync-status` 对接资产列表状态；`POST /case-asset/catalog/{id}/sync-reconcile` 对接补偿按钮。服务检查当前组织目录，接口保留读/更新权限；不会将补偿排队描述成同步成功。
- D06：等级按稳定字段 `functional_priority` 的 value/defaultValue 展示，保留顶层值优先。
- D07/D13：树构建复制嵌套节点 projectId/updateTime/附加信息；缺版本先刷新并要求重新选择，移动错误由本地统一展示一次。类型化同名异常映射为 MODULE_NAME_CONFLICT，不匹配多语言自由字符串。
- D08：追加关联组不再索要新评审人，后端读取既有评审当前成员并验证；传入不一致成员时拒绝。新建仍必须选择评审人/规则。已补真实MySQL测试，编译通过但未运行数据库测试。
- D09/D10：三个 Node 创建工具必须透传调用方稳定 requestId，包版本0.8.1；个人 CASE 证据错误说明一图可覆盖全部步骤，Runner 原有逐步门禁保持不变。外部发布与连接器缓存刷新待执行。
- D11/P01：试点表格批量区上移并常驻，零选择沿用原禁用逻辑；分页留在表格下方，原事件/权限保留。目录宽度252/220/190，表格行高、背景和间距使用语义变量；用例详情添加响应式正文/概览侧栏，保留原编辑表单、附件和其他入口；评审详情解除1100px头部硬约束。Demo样式类移至Vue挂载容器内部。未取得真实页面截图，不作为视觉验收通过。
- E01：保留缺密钥时拒绝敏感事实；新增受 `AI_EXECUTION_READ` 保护的 `GET /ai/recovery/status/facts-readiness`，只返回 ready/code，不显示密钥。配置有效不等于历史数据可解密。部署步骤见 [密钥部署说明](msp-facts-key-deployment-20261027.md)。
- D14：缺陷详情标签脱离固定32px居中行，按内容自然增高；标签内容限制最大宽度，长标签省略，避免挤占预计解决时间/后续元数据。标签修改及权限接口不变。页面运行验证待执行。

## 实际验证

证据：[验证摘要](../testing/msp-remediation-20261027/verification-summary.json)、同目录日志。

| 实际命令 | 结果及边界 |
|---|---|
| `javac -J-Xmx192m -J-XX:MaxMetaspaceSize=160m @.codex-tmp/msp-remediation-20261027/compile.args` | 修改的生产Java文件增量编译通过；复用本机已有类和依赖，不等同Maven全量编译 |
| `javac -J-Xmx160m @.codex-tmp/msp-remediation-20261027/test-compile.args` | 定向单元及修改的两个MySQL测试编译通过 |
| `java -Xmx160m @.codex-tmp/msp-remediation-20261027/test-run.args` | 19/19通过：树revision、错误映射、个人执行、加密配置/实例重建可读/错误密钥拒绝；不代表真实部署重启 |
| `node --max-old-space-size=160 metersphere-mcp/node_modules/typescript/bin/tsc -p metersphere-mcp/tsconfig.json` | Node包类型编译通过 |
| `node --max-old-space-size=160 --test metersphere-mcp/test/*.test.mjs` | 30/30通过，包含真实stdio进程、图片透传、错误透传和创建身份边界 |
| `node --max-old-space-size=160 --test frontend/scripts/test-import-job-requests.mjs` | 2/2通过：PRECHECKED请求预算、任务切换迟到响应隔离；不替代Web导入E2E |
| `node --max-old-space-size=192 .codex-tmp/integrated-implementation/verify-sfc.mjs` | 16个修改Vue组件script/template编译通过；不等同vue-tsc完整类型检查 |
| frontend目录中对修改的17个Vue/TS文件执行 `node --max-old-space-size=192 node_modules/eslint/bin/eslint.js <文件列表>` | 初始16文件检查0错误、22条已有警告，新增评审详情单独检查0错误；保留旧console/未使用变量警告 |
| frontend目录执行 `node --max-old-space-size=160 scripts/verify-api-contracts.mjs` 和 `scripts/verify-layout-overflow.mjs` | 两项静态契约检查通过，不等同真实浏览器滚动验收 |
| `git diff --check` | 通过 |

## 未执行验证、风险及补验命令

本机持续约0.8–1.2GiB空闲，遵守低资源约束，本轮没有启动Docker、后端或另一套浏览器环境，也未强跑全量构建。以下均为未完成，不能据上述测试关闭原缺陷：

1. 前端完整类型/lint样式/生产构建：在frontend执行 `pnpm type:check`、`pnpm exec stylelint <本轮修改的vue及less文件>`、`pnpm build`。风险是完整项目类型、样式和打包集成问题仍可能存在。
2. 后端完整编译/单元/集成：仓库根执行 `.\mvnw.cmd -T1 -pl backend/services/agent-integration -am test -Dsurefire.failIfNoSpecifiedTests=false`，再执行 `.\mvnw.cmd -T1 -pl backend/app -am package -DskipTests`。本机临时javac参数文件使用本机路径，CI应使用Maven命令。
3. 专用MySQL就绪后执行 `.\mvnw.cmd -T1 -pl backend/services/agent-integration -am test -Dquality.mysql=true -Dtest=ImportFollowupCoordinatorMySqlTests,FunctionalCaseExportMySqlTests -Dsurefire.failIfNoSpecifiedTests=false`。需覆盖134迁移、旧数据、真实分类创建、资产导出文件可解析和源→镜像收敛；新增两个测试只编译，未执行。分类创建及同步诊断的完整真实数据库回归覆盖仍需补齐，不能只以SQL静态核对关闭。
4. 镜像/迁移/健康：沿用仓库 `deploy/implementation-verify.compose.yml`，单套串行执行 `docker compose -f deploy/implementation-verify.compose.yml config`；先从本轮新打包的 `backend/app/target/app-3.x.jar` 解包至全新空目录 `backend/app/target/remediation-dependency-20261027`（进入该目录执行 `jar xf ../app-3.x.jar`），再从仓库根执行 `docker build -f Dockerfile.backend --build-arg DEPENDENCY=backend/app/target/remediation-dependency-20261027 -t msp-implementation20261022:verify .`，然后执行 `docker compose -f deploy/implementation-verify.compose.yml up -d`、`docker compose -f deploy/implementation-verify.compose.yml ps`。该compose使用image而非build段，不能把compose build空操作当镜像构建证据；按原隔离环境参数注入数据库/存储/密钥。未执行故无法证明容器可启动或迁移可成功，禁止在共享库直接尝试。
5. ego真实页面与端到端：部署本轮版本后复验D01导入→关联，D03真实下载，D05源修改/执行附件/删除/补偿与重试，D02/D11/D14同视口长文本/多标签/窄侧栏及200%缩放。没有新截图证据，UI当前仅代码修正。
6. 外部MCP发布与客户端刷新、受控密钥配置、重启后历史事实可读、全量75条累积验收债务均未完成。风险保留，不把单元测试代替实际连接器或部署验收。

功能状态：**部分完成（修复代码与定向验证已落地，数据库/部署/真实UI及完整E2E验收未完成）**。本轮代码与测试资料一并提交，分支同步结果以Git记录为准；应用部署和运行验收仍未完成。原测试资料保留。

## 本轮涉及文件

- `backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_134__export_request_identity.sql`
- `backend/framework/sdk/src/main/java/io/metersphere/sdk/exception/ModuleNameConflictException.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentRecoveryHealthController.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/security/AgentFactCipher.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentDirectExecutionService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentSafeErrorMapper.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/ImportFollowupCoordinator.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/TestAssetGovernanceService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/tool/functional/AgentCaseExportTools.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentFactCipherConfigurationTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentSafeErrorMapperTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/ImportFollowupCoordinatorMySqlTests.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/asset/controller/CaseAssetController.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/asset/service/CaseAssetService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/hub/service/DefaultHubModuleResolver.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/request/FunctionalCaseExportRequest.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseExportTaskService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseModuleService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseService.java`
- `backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalImportEffectOutbox.java`
- `backend/services/case-management/src/test/java/io/metersphere/functional/service/FunctionalCaseExportMySqlTests.java`
- `backend/services/project-management/src/main/java/io/metersphere/project/service/ModuleTreeService.java`
- `backend/services/project-management/src/test/java/io/metersphere/project/service/ModuleTreeRevisionTests.java`
- `frontend/scripts/test-import-job-requests.mjs`
- `frontend/src/api/modules/case-management/featureCase.ts`
- `frontend/src/assets/style/management-review.less`
- `frontend/src/components/pure/ms-table/base-table.vue`
- `frontend/src/views/bug-management/components/basicInfo.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/caseTree.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/import/ImportFollowupChoice.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/import/importJob.vue`
- `frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabDetail.vue`
- `frontend/src/views/case-management/caseManagementFeature/index.vue`
- `frontend/src/views/case-management/caseReview/components/index/reviewTable.vue`
- `frontend/src/views/case-management/caseReview/detail.vue`
- `frontend/src/views/case-management/caseReview/index.vue`
- `frontend/src/views/setting/system/agentIntegration/index.vue`
- `frontend/src/views/test-asset/cases.vue`
- `frontend/src/views/test-plan/testPlan/components/planTable.vue`
- `frontend/src/views/test-plan/testPlan/detail/featureCase/components/caseTable.vue`
- `frontend/src/views/test-plan/testPlan/index.vue`
- `metersphere-mcp/README.md`
- `metersphere-mcp/package-lock.json`
- `metersphere-mcp/package.json`
- `metersphere-mcp/src/client.ts`
- `metersphere-mcp/src/tools/caseExport.ts`
- `metersphere-mcp/src/tools/caseLibrary.ts`
- `metersphere-mcp/src/tools/caseWrite.ts`
- `metersphere-mcp/test/caseExport.test.mjs`
- `metersphere-mcp/test/caseWriteIdentity.test.mjs`

## Git提交说明

本机提交钩子 `frontend/.husky/pre-commit` 包含全量 `npm run type:check`，本次为遵守低资源约束，通过命令级 `HUSKY=0` 跳过本机钩子，未永久修改钩子或CI配置。上文定向验证仍有效；不能将本次提交/合并解释为全量类型检查、生产构建或部署验收通过。

## CI构建回归修复：importJob异步闭包类型收窄

2026-10-27，提交dcd9c9b018的CI前端构建在 `importJob.vue:249` 报TS2339：`followupOptions.value` 在函数入口已判断存在，但这个可变响应式属性在新的异步闭包内不会保留该类型收窄。此前SFC/transpile检查只证明语法转换通过，没有发现语义类型错误。

| 需求 | 前端入口/实现 | 后端/数据影响 | 验证 |
|---|---|---|---|
| 修复预检弹窗构建失败，保留原请求去重及任务隔离 | 测试用例导入；loadReviewers捕获options并在进入闭包前取出projectId | getReviewUsers接口、权限、数据库均不变；无新增迁移 | 4项组件/语义回归通过；旧版本反验可复现同一projectId/undefined诊断 |

修改文件：`frontend/src/views/case-management/caseManagementFeature/components/import/importJob.vue`、`frontend/scripts/test-import-job-requests.mjs`及本文。测试增加自动选择新建时评审人请求去重，并从真实组件提取loadReviewers，通过真实Vue ref类型执行严格TypeScript语义检查，避免只用transpile漏掉此类错误。

实际执行：`node --max-old-space-size=160 --test frontend/scripts/test-import-job-requests.mjs`，4/4通过；对两处前端文件运行ESLint，0错误、3条测试替身空函数警告；`git diff --check`通过。旧HEAD源文件运行新增语义检查按预期失败，确认回归测试能识别原错误。

本次约560MiB空闲内存，继续跳过本机全量提交钩子。未运行全量vue-tsc/Vite构建、后端/数据库/镜像或浏览器E2E；此修复不改后端和业务交互，完整前端构建由新提交的CI验证。状态：局部修复和回归通过；远端构建结果待确认，原方案的部署验收欠项不变。

## 构建失败复盘

两次错误的代码溯源、验证漏检、10月27日后续发布阻塞及防复发行动见 [两次前端构建失败复盘](msp-build-failure-retrospective-20261028.md)。建议的独立CI与required checks尚未实施，不能视为门禁已经启用。
