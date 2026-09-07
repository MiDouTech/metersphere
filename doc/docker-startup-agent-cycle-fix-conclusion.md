# Docker 启动失败与 Agent 循环依赖修复结论

日期：2026-09-02

## 1. 结论摘要

本次容器持续处于 `health: starting` 并反复重启的直接原因，是 MeterSphere 后端在 Spring ApplicationContext 初始化阶段检测到 Bean 循环依赖。健康检查失败、8081 端口拒绝连接和容器重启均为应用启动失败后的结果，不是根因。

已在本地完成代码层修复，并通过针对性单元测试及服务依赖环扫描测试。修复代码尚未提交到 GitHub，也尚未构建新镜像或在部署环境验证，因此当前状态为**部分完成**。

## 2. 服务端故障证据

服务端日志明确报告：

```text
APPLICATION FAILED TO START

The dependencies of some of the beans in the application context form a cycle:

   agentCredentialReferenceController
┌─────┐
|  agentCredentialReferenceService
↑     ↓
|  agentRunnerService
↑     ↓
|  agentExecutionCheckpointService
↑     ↓
|  agentExecutionPreflightService
└─────┘
```

完整依赖链为：

```text
AgentCredentialReferenceController
→ AgentCredentialReferenceService
→ AgentRunnerService
→ AgentExecutionCheckpointService
→ AgentExecutionPreflightService
→ AgentCredentialReferenceService
```

Spring 取消上下文刷新后关闭 Jetty，Java 进程退出；容器配置的 `unless-stopped` 重启策略随后再次拉起容器，形成循环重启。

## 3. 排除项

以下项目已经通过运行证据排除为本次根因：

- Docker 启动脚本具有正确的 LF 换行和可执行权限。
- 镜像已包含启动脚本 CRLF 规范化处理。
- 容器以 `root` 用户运行，根文件系统不是只读。
- 日志目录挂载可写。
- Node.js 版本为 `v22.23.2`，企业微信桥接服务能够启动并返回 `UP`。
- Redis 连接成功。
- 数据库连接池能够初始化。
- 本次失败没有 OOMKilled 证据。
- 健康检查只是检测到 8081 服务没有成功保持运行。

## 4. 修复方式

修复通过新增独立的 `AgentRunnerLeaseAuthorizationService`，抽取 Runner 租约认证、租约有效性和任务状态校验逻辑。

原依赖：

```text
AgentCredentialReferenceService
→ AgentRunnerService
```

修复后：

```text
AgentCredentialReferenceService
→ AgentRunnerLeaseAuthorizationService

AgentRunnerService
→ AgentRunnerLeaseAuthorizationService
```

新服务只依赖 `AgentExecutionMapper`，不会重新进入 `Checkpoint → Preflight → CredentialReference` 依赖链，从而切断循环。

不建议通过以下配置绕过问题：

```properties
spring.main.allow-circular-references=true
```

该配置只会允许存在缺陷的依赖结构继续运行，不能替代代码层修复。

## 5. 需求追踪结果

| 需求 | 实现 | 验证证据 | 状态 |
| --- | --- | --- | --- |
| 定位容器循环启动原因 | 根据 Spring 启动日志还原完整 Bean 循环依赖链 | 服务端 `APPLICATION FAILED TO START` 日志 | 已验证 |
| 消除 Agent 服务循环依赖 | 抽取独立租约鉴权服务并替换 Credential、Runner 的相关依赖 | 代码审查、依赖环扫描测试 | 已验证 |
| 保持原租约安全校验 | 新服务保留 Token 哈希比对、状态、有效期及任务状态校验 | `AgentRunnerLeaseAuthorizationServiceTests` | 已验证 |
| 防止新增服务循环依赖 | 扫描 Agent 模块所有 `@Service` 依赖关系 | `AgentServiceDependencyCycleTests` | 已验证 |
| 提交到 fork 并创建 PR | 计划提交到 `chenqifen-miduo/metersphere`，再向 `Kee0909/metersphere` 创建 PR | 浏览器上传授权被拒绝/中断 | 阻塞 |
| 新镜像部署并健康检查 | 尚未构建和部署包含修复的镜像 | 无运行证据 | 未验证 |

## 6. 涉及文件

生产代码：

- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentCredentialReferenceService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentRunnerService.java`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentRunnerLeaseAuthorizationService.java`

测试代码：

- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentCredentialReferenceServiceTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentRunnerLeaseAuthorizationServiceTests.java`
- `backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentServiceDependencyCycleTests.java`

本修复为纯后端依赖调整，不涉及前端入口、页面、路由、API 协议、数据库迁移或 `caseTable.vue`。

## 7. 已执行验证

执行命令：

```powershell
.\mvnw.cmd -pl backend/services/agent-integration -am `
  "-Dtest=AgentServiceDependencyCycleTests,AgentRunnerLeaseAuthorizationServiceTests,AgentCredentialReferenceServiceTests" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

结果：

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

同时执行了六个目标文件的差异检查：

```powershell
git diff --check -- <六个 Agent 修复文件>
```

结果：未发现空白字符错误。

## 8. GitHub 提交状态

- 已确认可登录 `chenqifen-miduo` 账户。
- 已确认 `chenqifen-miduo/metersphere` fork 的上传页面具备提交权限。
- 浏览器文件上传操作随后被授权策略拒绝/中断，没有文件被上传。
- 六个 Agent 修复文件目前仍是本地未提交状态。
- 尚未产生 GitHub commit。
- 尚未向 `Kee0909/metersphere` 创建 PR。
- 周报、模板等无关文件没有纳入本次修复范围。

## 9. 尚未执行的验证

以下验证尚无实际运行证据：

- 后端完整打包。
- Docker 镜像构建。
- 新镜像容器启动。
- Spring ApplicationContext 完整启动。
- `/actuator/health` 健康检查。
- Agent Runner 租约认证接口冒烟测试。
- 生产或预发布环境端到端验证。

建议新镜像部署后执行：

```bash
C=metersphere

docker inspect \
  --format 'status={{.State.Status}} restart={{.RestartCount}} health={{if .State.Health}}{{.State.Health.Status}}{{end}}' \
  "$C"

docker exec "$C" /bin/sh -c \
  'curl -sS -i http://127.0.0.1:8081/actuator/health'

docker logs --since 5m "$C" 2>&1 |
grep -E 'APPLICATION FAILED|cycle|BeanCreationException|Started Application'
```

验收标准：

- 容器持续保持 `running`。
- `RestartCount` 不再增长。
- 健康状态最终变为 `healthy`。
- 日志不再出现循环依赖和 `APPLICATION FAILED TO START`。
- 健康接口返回 HTTP 200 或项目允许的 HTTP 401。

## 10. 已知风险和遗留问题

- 单元测试和静态依赖扫描通过，不等于真实容器已经能够启动。
- 当前部署镜像仍不包含本地循环依赖修复。
- 在新镜像完成启动和健康检查前，Docker 启动失败问题不能标记为完全解决。
- 本地仓库还有与本修复无关的未跟踪周报和模板文件，提交时必须继续排除。

## 11. 最终状态

**部分完成：故障根因已确认，代码修复及针对性自动测试已完成；GitHub 提交、PR、新镜像构建、容器启动和健康检查尚未完成。**
