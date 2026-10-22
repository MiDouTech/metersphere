# 本次隔离验收的资源约束

只使用已有镜像 `msp-implementation20261022:verify`，串行验收；不同时运行 Maven、前端构建或多浏览器会话。低资源配置只用于本地功能验收，不用于生产容量结论。

3584 MiB 是本次整套容器验收的保守启动门槛，不是产品最低内存要求，也不意味着 IDEA 有额外虚拟内存。已核查 `.idea/workspace.xml`：`Application` 为本机 Spring Boot 运行配置，主类 `io.metersphere.Application`，未显式配置堆参数；`scripts/start-dev.ps1` 同样直接启动本机 Java，依赖由 `dev/docker-compose.yml` 提供。后续可优先评估本机单应用运行方式，不能为了通过验收擅自关闭核心业务依赖或修改开发数据库。

## 启动前

1. 确认 Docker 已就绪，检查 `docker ps`。本机 Docker 会自动恢复 `ms-dev-*` 容器；本次用户已授权临时停止 `ms-dev-mysql`、`ms-dev-redis`、`ms-dev-nacos`、`ms-dev-kafka`、`ms-dev-minio`，保留数据卷。不得据此停用其他项目。
2. 运行 `python scripts/guard-isolated-resources.py --preflight`。退出 0 才继续：Docker 已运行时，主机至少空闲 3584 MiB，提交率低于 92%。检查失败时等待资源释放，不降低阈值反复尝试。
3. 在独立终端启动监控，然后启动容器：

```powershell
python scripts/guard-isolated-resources.py --duration-minutes 90
docker compose --env-file .codex-tmp/implementation.env -p msp-implementation20261022 -f deploy/implementation-verify.compose.yml up -d --wait app
```

环境文件含本次随机测试凭据，只保存在本机，不提交或复制进公开证据。`docker compose config --quiet` 可校验配置，避免直接输出带密码的展开配置。

## 运行期间

- 应用限制 1536 MiB / 1.5 CPU，JVM 最大堆按容器 50%；Kafka 堆 128～256 MiB。数据库、Kafka、MinIO 各限 512 MiB，Redis 限 128 MiB。
- 监控每 5 秒取样；主机空闲低于 1536 MiB、提交率达到 92%，或监控到期时，先停本次应用，再停本次依赖。只接受 `msp-implementation<数字>` 项目名，不执行卷删除、prune 或其他项目操作。
- 监控不能替代足够的预留内存，也不能保证 Docker 引擎失去响应时仍能停止容器。停止命令的超时/失败会记录，遇到这种情况不继续启动新工作。
- 浏览器只启动本次一个会话；先完成迁移及健康检查再启动。大批量导入和故障演练留到基本路径通过后，单独执行。

## 停止与证据

```powershell
docker compose --env-file .codex-tmp/implementation.env -p msp-implementation20261022 -f deploy/implementation-verify.compose.yml stop
docker ps --filter label=com.docker.compose.project=msp-implementation20261022
```

保留内存采样、触发原因、停止结果、健康状态和实际验证输出。容器退出 137 时应同时核查 `OOMKilled`，区分资源保护主动停止与 OOM；不能直接推断为内存泄漏。

本次保护机制已实际触发并成功停止本次五个容器：采样 1513 MiB 时触线，应用 `OOMKilled=false`。随后内存回收至约 2.7 GiB，原启动预检通过；第二次启动仍触线并安全停止。因此将启动门槛从原 2560 MiB 提升至 3584 MiB，等待更多资源后再验收，不能依赖反复启动碰运气。

后续新增按需的 migration profile：仅启动本次 MySQL 与独立 Flyway 进程，使用已打包迁移，避免迁移期间叠加整个 Spring 应用及其他依赖；保护脚本也会停止本次 migration 容器。此路径尚未实际执行，不能记为迁移通过。用户暂不释放资源时，采用[无 Docker 补充验证](implementation-lightweight-verification-20261022.md)，不反复尝试启动或下调保护阈值。
