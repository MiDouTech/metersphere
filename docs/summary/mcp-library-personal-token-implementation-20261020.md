# 用例库与个人 Token 联合实施记录（2026-10-20 续）

总体状态：实施中。保留既有工作区改动；质量门禁为最高约束。

|需求|入口/前端|后端/数据|验收|状态|
|---|---|---|---|---|
|认证隔离|现有个人设置、治理页面与远程客户端|AgentShiroFilterChainExtender、AgentTokenFilter/Service；无新增表|混合会话、无效凭据、双头、管理面拒绝、回归|实施中|
|轮换/授权版本|个人 Token 页面|生命周期、迁移、运行任务协调|并发轮换、撤权和旧代次失效|待实施|
|评论/附件|详情页面及 MCP/Node|评论与文件服务、可靠副作用|授权、版本、内容回读|待实施|
|删除/恢复|用例库、确认页、回收站、MCP|预检、用户决定、停止协调|执行中选择、迟到回调、恢复|待实施|
|导入导出/快照|用例库及 MCP/Node|异步任务、冻结数据、授权文件|重启恢复、逐项结果|待实施|
|客户端/治理|现有页面及技能包|注册中心、OAuth、策略|真实客户端及门禁全链路|待实施|

第一步复用现有页面/API，无新页面需求：在共同认证入口阻止个人凭据管理 Token，并确保无效 MCP 凭据不能回退到 Web 会话。返回安全 401/403，保留现有无 SSE GET 的 405 行为。后续各项实施前继续登记详细文件与验证证据。

## 本轮新增实现（待运行环境最终核验）

- 个人/治理及旧包管理路由接入共同过滤器，拒绝个人凭据；MCP POST 无效/缺失凭据直接 401，双认证头拒绝，严格匹配前缀。
- TokenService 防御性校验 enable/status/revokedAt；实际数据库有效状态沿用 ACTIVE，不将方案概念状态 ENABLED 直接替换数据库枚举。
- afterCompletion 在异常路径清理 Agent 请求上下文，不登出已有 Web 会话。
- 评论共享服务增加用例归属、作者、内容及 expectedUpdateTime 校验，事务锁和单调版本。现有网页评论编辑传入版本。
- 新增 metersphere.functional.comment.update 与 Node 适配，显式 CASE_COMMENT_EDIT_V2，不从旧通配授权继承；只编辑正文，保留回复和附件，不重发通知。审计保存前后内容摘要，不记录正文。
- Token 页面增加中文显式授权选项；技能包 1.2.1、Node 0.5.1。评论删除/墓碑及可靠通知仍未完成。

初步验证：针对性 Maven 测试 22 项通过，含认证过滤器、状态验证、作者/归属/版本拒绝。首次编译发现 Shiro afterCompletion 必须 public，修正后通过。Node 初次 13 项通过；新增评论 Schema 测试后的结果另记录。前端 type:check 退出 0。

新增改动主要文件：AgentTokenFilter.java、AgentTokenService.java、AgentShiroFilterChainExtender.java；FunctionalCaseCommentService.java、FunctionalCaseCommentRequest.java；FunctionalCommentUpdateToolHandler.java；AgentTokenScope/Parser、AgentMcpStreamableService、AgentSafeErrorMapper、AgentMcpBundleService；前端 ms-comment/comment.tsx、types.ts、agentIntegration/index.vue；Node caseLibrary.ts、版本及 README；AgentCommentEditTests、AgentTokenFilter/ServiceTests、caseLibrary.test.mjs 和 mcp-case-library.spec.ts。

未完成：完整授权契约迁移、Token 轮换/旧代次任务恢复、治理策略/OAuth；评论删除分页及可靠通知、附件完整生命周期、用例删除确认/停止协调/恢复、异步导入导出与快照、全写入口版本及全客户端验收。本轮局部实现不能代替两套方案整体完成。

## 已执行验证（本轮）

|命令|结果|
|---|---|
|`.\mvnw.cmd -pl backend/services/agent-integration -am test -Dtest=AgentTokenFilterTests,AgentTokenServiceTests,AgentCommentEditTests -Dsurefire.failIfNoSpecifiedTests=false`|22 项通过，退出 0|
|`.\mvnw.cmd -pl backend/app -am package -Dtest=Agent*Tests -Dquality.mysql=true -Dsurefire.failIfNoSpecifiedTests=false`|BUILD SUCCESS，Agent 239 项及依赖 7 项通过，无跳过，退出 0|
|`pnpm -C frontend run type:check`|退出 0|
|`pnpm -C frontend run build`|退出 0，既有 eval/大资源提示|
|`pnpm -C frontend exec eslint src/components/business/ms-comment/comment.tsx src/components/business/ms-comment/types.ts src/views/setting/system/agentIntegration/index.vue e2e/mcp-case-library.spec.ts --no-cache`|0 错误、1 个既有未使用参数警告|
|`npm --prefix metersphere-mcp test`|14 项通过|
|`npm pack ./metersphere-mcp --pack-destination .codex-tmp`；`npm install --prefix .codex-tmp/mcp-consumer-051 --ignore-scripts --no-audit --no-fund ./.codex-tmp/midoo-metersphere-mcp-0.5.1.tgz`；指定 MS_TEST_BINARY 后 `node --test metersphere-mcp/test/*.test.mjs`|安装包 14 项通过，包含新 comment.update 发现|
|`git diff --check`|通过，仅换行风格提示|
|`docker build -f Dockerfile.backend --build-arg DEPENDENCY=backend/app/target/personal-library-dependency-20261020 -t msp-quality:personal-library-standard .`|标准构建通过；第一份 JAR 的前端资源早于本次前端构建，最终部署需使用重新组装的产物|

日志统一位于 `.codex-tmp/personal-library-*`；初次针对性测试为 `.codex-tmp/personal-auth-comment-tests.log`。Node tarball SHA256：ae0adb63c6c546e03057a1ab2ab783cb90a8b74aaee9491b9dfa7217da9f823d。

本轮没有数据库迁移，尚不能据此声称未来凭据版本/轮换数据模型已完成。完整前后端测试、全局 lint、全部角色 UI、真实第三方客户端/OAuth/Runner 全链路，以及尚未实现能力的测试均未执行。原因是对应实现尚未完成或本轮验证只覆盖新增切片；风险仍包括异步撤权、删除协调、附件清理和客户端兼容性。全局 lint 可执行 `pnpm -C frontend exec eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts`；全后端测试可执行 `.\mvnw.cmd -pl backend/app -am test`。未建立的新增能力测试不得以空匹配命令替代。

## 最终制品与运行验证

- 前端完成后重新执行 `.\mvnw.cmd -pl backend/app -am package -DskipTests`，退出 0；JAR 中 BOOT-INF/classes/static/index.html 与 frontend/dist/index.html 逐字节一致。
- 最终重新执行标准 Dockerfile 构建失败：Alpine main 索引 TLS unexpected EOF / Permission denied。第一轮标准构建成功不能替代这次失败。
- 备用最终镜像：`docker build -f .codex-tmp/Dockerfile.personal-library-final -t msp-quality:personal-library-final .` 退出 0；以本轮成功的标准镜像为底，复制最终 JAR 的完整 lib/classes/META-INF。前后依赖文件名集合一致。标准最终镜像仍需软件源恢复后重跑：`docker build -f Dockerfile.backend --build-arg DEPENDENCY=backend/app/target/personal-library-final-20261020 -t msp-quality:personal-library-final .`。
- 保留隔离环境已有数据库/对象存储/事实加密密钥，执行 `.codex-tmp/start-personal-library.ps1`；内部运行 `docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml up -d --no-deps --wait app` 和 `scripts/verify-quality-environment.ps1`，退出 0。readiness、既有迁移记录、数据库事务、Redis、MinIO、Kafka 均通过；没有新增迁移。
- 设置 MCP_CASE_LIBRARY_E2E=true、PW_BASE_URL=http://127.0.0.1:15173 后运行 `pnpm -C frontend exec playwright test e2e/mcp-case-library.spec.ts --workers=1`：1 项通过，退出 0。真实网页登录及页面、管理接口混合凭据 403、无效 Token MCP 401、评论创建/编辑/回读、旧 AGENT_ALL 拒绝、旧版本冲突、目录/分页及撤销拒绝均通过。
- 当前隔离镜像：sha256:b9c1607d6df1d2135bce8a0a72101dbc1b22c1c1c7c219b3820dca5001a5050a；容器与最终解包目录 Agent JAR SHA256 均为 c8c5b8917ec38bf04eb7f65861cea59390b8ed0fe289c39f17e8d6466c6e87c1。
- 评论网页编辑版本字段已通过类型/构建检查；本轮未单独自动化操作浏览器中的评论编辑框，不能把 MCP 运行测试作为完整网页交互验收。

结论：认证隔离和本人评论编辑核心切片已实施并有局部运行证据；两套方案整体仍为部分完成。没有提交 Git，没有部署远程测试/生产环境。
