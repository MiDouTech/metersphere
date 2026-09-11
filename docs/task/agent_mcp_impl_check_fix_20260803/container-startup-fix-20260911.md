# metersphere 启动故障修复记录（2026-09-11）

状态：部分完成。本地修复、定向测试、后端打包已验证；服务器恢复未验证。

## 现场证据与需求追踪

用户提供 aliy-docker2 上 RestartCount=95。日志显示 AgentMcpMaintenanceService 的 projectService 注入类型冲突，以及 BaseMapper.filterInWrapper SQL 片段未解析。数据库连接与 Flyway 3.7.2.90 检查已通过。

| 需求 | 前端入口/实现 | 后端 | 数据/配置 | 验证 |
| --- | --- | --- | --- | --- |
| MCP 服务初始化 | MCP 客户端，无页面变更 | Maintenance 显式注入 agentProjectService、agentBugWriteService | 无数据库变更 | Spring 实际装配回归通过 |
| 启动查询可解析共享 SQL | 原列表筛选入口不变 | MyBatis SQL 解析 | commons.properties 显式扫描 Mapper XML | 全部匹配 XML 解析及延迟 statement 解析通过 |
| 稳定启动和就绪 | 原产品入口 | Spring 应用、健康接口 | 不修改健康探针 | 未部署，待服务器验证 |

源码中存在 filterInWrapper；本次通过显式加载 XML 防止依赖 Mapper Bean 初始化顺序。尚不能排除现场镜像缺资源或外部配置覆盖 mapper-locations，部署时需使用新构建产物。额外发现 bugService 同名注入风险，一并修复。未修改 SQL 或任务 task008 的完成状态。

## 修改文件

- backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentMcpMaintenanceService.java
- backend/app/src/main/resources/commons.properties
- backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentMcpStartupTests.java
- 本记录

## 已执行验证

本机使用 IntelliJ 内置 Maven 3.9.11 和 Java 21，命令前缀为：

```powershell
& 'C:/SoftWare/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd' -pl backend/app -am package '-Dtest=AgentMcpStartupTests,AgentMcpMaintenanceServiceTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-DskipAntRunForJenkins=true'
git diff --check
docker info --format '{{.ServerVersion}}'
```

- Maven 日志 BUILD SUCCESS；Maintenance 13 项、启动回归 2 项，失败/错误/跳过均为 0。原始日志：`.codex-tmp/container-startup-package.log`。PowerShell 包装执行报告 exit 1，Maven 日志及 Surefire 报告确认成功；输出包含被 PowerShell 标记为 NativeCommandError 的 JVM/Mockito stderr 警告。
- 首轮启动测试因测试替身被 Spring 再次注入而失败，调整为预注册 singleton 后通过，未绕过被测服务的 Spring 注入。
- Python zipfile 检查 `backend/app/target/app-3.x.jar` 内扫描配置、system-setting JAR 中共享片段、agent-integration 字节码中的两个 Bean 名称，断言通过。
- git diff --check 通过。
- docker info 失败：本机 Docker Desktop Linux Engine 管道不存在。

## 未执行、风险与后续命令

未执行 Docker 构建/启动/健康验证、真实数据库集成测试和核心业务 E2E：本机 Docker 不可用，尚无目标服务器连接。不能据此认定服务器已恢复，不能认定 task008 全部验收通过。前端类型检查、lint、生产构建未执行，本次未修改前端；数据库迁移无变更，未执行新库迁移验证。

在同步修改后的仓库和可用 Docker 的构建机上，可运行以下后端验证及镜像构建命令（不会替换现场容器）：

```bash
./mvnw -pl backend/app -am package -Dtest=AgentMcpStartupTests,AgentMcpMaintenanceServiceTests -Dsurefire.failIfNoSpecifiedTests=false -DskipAntRunForJenkins=true
startup_dependency=$(mktemp -d backend/app/target/startup-dependency.XXXXXX)
(cd "$startup_dependency" && jar -xf ../app-3.x.jar)
docker build -f Dockerfile.backend --build-arg DEPENDENCY="$startup_dependency" -t metersphere:startup-fix-20260911 .
```

数据库及依赖齐备的隔离环境中运行完整集成验证：

```bash
./mvnw -pl backend/app -am verify -DskipAntRunForJenkins=true
```

现场应通过原有部署配置替换镜像，保留当前端口、挂载和环境变量；当前未取得完整现场部署配置，不编造 docker run 命令。部署后执行下面命令并在超过探针启动宽限期后复查，要求重启次数保持不变且健康状态为 healthy：

```bash
docker inspect metersphere --format 'Restarts={{.RestartCount}} State={{json .State}}'
docker exec metersphere curl -sS -i --max-time 10 http://127.0.0.1:8081/actuator/health
docker exec metersphere tail -n 100 /opt/metersphere/logs/metersphere/error.log
```

业务 E2E 需在真实入口登录并验证项目检索及授权/无权限 MCP 操作；当前缺少目标地址、测试账号和现场测试脚本，不能提供或声称执行了可用 E2E 命令。探针接受 HTTP 401，healthy 本身不能替代业务验收。
