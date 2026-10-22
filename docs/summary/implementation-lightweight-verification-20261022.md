# 无 Docker 补充验证记录

用户决定暂不继续释放内存，改用其他测试方式。本轮保持 Docker Desktop 关闭，未启动应用、数据库或浏览器；未重跑前后端业务构建，复用已构建产物。

## 需求追踪与验证边界

| 需求 | 本次执行 | 实际结果 | 尚不能证明 |
| --- | --- | --- | --- |
| DEF-02 / DEF-03 配置校验 | 调用真实前端 `platformConfiguration.ts`，不复制校验算法 | 36 项通过；覆盖完整地址、缺少协议、凭据、协议、端口、Origin 路径/查询/片段、协议及端口匹配、JSON 语法与对象类型 | 页面新增、后端结构校验、权限及数据库持久化 |
| REQ-01 外部图片 | 实例化已打包模块中的真实 `AgentExternalImageDownloader`，真实公网 GET | 9 项通过，详见下表；未使用 HTTP Mock | MCP 传输、令牌权限、附件关联、对象存储读取、执行证据发布 |
| 完整迁移 | 编译独立 Flyway 入口并检查已打包迁移资源 | 编译通过；116/117/118 资源存在；compose migration profile 校验通过 | 未连接数据库，不能记为迁移执行成功 |

下载器运行检查：

| 场景 | 结果 |
| --- | --- |
| 公网 JPEG 下载及解码 | 成功，35588 字节，类型 `image/jpeg`，文件名 `external-runtime.jpg` |
| 公网 302 重定向至 127.0.0.1 | 拦截：`EXTERNAL_IMAGE_TARGET_FORBIDDEN` |
| SVG 源 | 拒绝：`EXTERNAL_IMAGE_TYPE_UNSUPPORTED` |
| HTML 源 | 拒绝：`EXTERNAL_IMAGE_TYPE_UNSUPPORTED` |
| HTTP 404 | 安全失败：`EXTERNAL_IMAGE_FETCH_FAILED` |
| 云元数据地址 169.254.169.254 | 拦截：`EXTERNAL_IMAGE_TARGET_FORBIDDEN` |
| 带账号密码的 URL | 拒绝：`EXTERNAL_IMAGE_URL_INVALID` |
| 非允许端口 8443 | 拒绝：`EXTERNAL_IMAGE_URL_INVALID` |
| 延迟 20 秒的源 | 超时：`EXTERNAL_IMAGE_TIMEOUT`，实测约 5654 ms 返回 |

公网源为 `https://httpbin.org`；未发送用户资料、平台凭据或附件业务数据。源服务未来可能不可达，失败时需要区分网络环境和实现问题，不放宽安全断言以求通过。

## 修改文件与前后端关系

- `frontend/scripts/test-platform-configuration.mjs`：新增前端真实校验函数的输入测试，TypeScript 转译后执行；不是页面或后端 Mock。
- `frontend/package.json`：增加 `test:platform-configuration` 命令，仅改脚本，不改依赖或业务代码。
- `scripts/verification/ExternalImageRuntimeSmoke.java`：真实已打包下载器的独立冒烟入口，不启动 Spring；退出时关闭下载器线程池。
- `scripts/verification/IsolatedMigration.java`：复用产物内的 Flyway 和迁移；固定本次隔离项目、MySQL 服务、库及历史表，凭据只经环境传入。
- `deploy/implementation-verify.compose.yml`：新增按需启用的 migration profile，仅依赖隔离 MySQL；迁移堆 128 MiB、容器 384 MiB、0.5 CPU。应用默认启动不会额外启动它。
- `scripts/guard-isolated-resources.py`：保护停止范围补入本次 migration 服务，保留原阈值，不删除数据卷。

前端校验用于平台和登录配置表单，实际后端为 `/ai/environment-profiles` 和 `/ai/login-profiles`。图片下载器由 `AgentExternalImageService` 调用，三类 MCP 工具的业务写入仍走既有附件/执行服务；本轮仅验证到了下载器边界。

代码链路另核查：用例图片调用 `uploadMinioFile` 后转入正式用例目录及预览目录，缺陷图片调用 `transferTmpFile`，之后再标记临时记录已关联；执行上传沿用 `FUNCTIONAL_SUBMIT` 与 `CASE_ATTACHMENT` 双权限及暂存/提交门禁。这些属于静态核查，不能代替持久化和授权运行证据。

## 实际命令与证据

在仓库根目录串行执行，均已获得退出 0：

```powershell
node --max-old-space-size=128 --test frontend/scripts/test-platform-configuration.mjs
node --max-old-space-size=128 frontend/node_modules/eslint/bin/eslint.js frontend/scripts/test-platform-configuration.mjs --fix
docker compose --env-file .codex-tmp/implementation.env -p msp-implementation20261022 -f deploy/implementation-verify.compose.yml --profile migration config --quiet
```

Java 使用 `C:/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/` 下的 `javac.exe`、`java.exe`；编译目标为 Java 21。以下变量只是复现路径，不含凭据：

```powershell
$artifactLib = 'backend/app/target/implementation-image-bc94d69515322c33/app/BOOT-INF/lib'
$verificationClasses = '.codex-tmp/implementation-migration'
$javaTools = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin'
& "$javaTools/javac.exe" '-J-Xmx64m' --release 21 -cp "$artifactLib/flyway-core-11.7.2.jar" -d $verificationClasses scripts/verification/IsolatedMigration.java
& "$javaTools/java.exe" '-Xmx128m' '-XX:+UseSerialGC' -cp "$verificationClasses;$artifactLib/*" IsolatedMigration --artifact-only
& "$javaTools/javac.exe" '-J-Xmx64m' --release 21 -cp "$artifactLib/metersphere-agent-integration-3.x.jar;$artifactLib/metersphere-sdk-3.x.jar" -d $verificationClasses scripts/verification/ExternalImageRuntimeSmoke.java
& "$javaTools/java.exe" '-Xms32m' '-Xmx128m' '-XX:MaxMetaspaceSize=128m' '-XX:+UseSerialGC' '-XX:ActiveProcessorCount=1' '-Djava.awt.headless=true' -cp "$verificationClasses;$artifactLib/*" ExternalImageRuntimeSmoke
```

日志位于 `outputs/implementation-20261022/`：

- `platform-validation-tests.log`：36 项通过。
- `platform-test-lint-fixed.log`：无错误或警告，退出 0。
- `image-runtime-compile.log`、`image-runtime-smoke.log`：编译成功，9 项通过。
- `migration-runner-compile-minimal.log`、`migration-artifact-check.log`：编译及资源检查通过。
- `migration-compose-config.exit`：0；仅展开校验，不启动服务，也不公开展开后的凭据。

初次迁移工具编译使用整个应用依赖作为编译 classpath，在 64 MiB 堆上失败；收窄到实际所需的 Flyway core 后同一限制下成功，失败日志保留。新测试脚本初次 lint 存在循环及格式问题，已改用数组遍历并修正，最终 lint 和测试均通过；脚本协议字符串是明确的拒绝输入，不会执行。

本轮 Java 最大堆 128 MiB、Node 最大堆 128 MiB，均串行运行；这不是进程总内存上限。测试运行期间若干离散采样主机可用内存约 2.2～2.6 GiB，不将离散采样当作完整峰值报告。整理报告后的最终采样为 1148 MiB，测试进程已全部退出；主机可用内存仍有波动，不据此归因于测试或其他应用，继续保持服务停止。

## 尚未执行及状态

未重跑无业务改动影响的前后端生产构建、原定向测试集和 Docker 镜像构建。未执行完整迁移、健康检查、实际页面和在线 MCP，因为用户选择暂不释放更多资源，且先前资源预检不足。对应风险、恢复命令仍在主实施记录与资源操作记录中保留。

资源满足预检后，可先单独运行迁移，监控需在另一终端保持运行：

```powershell
docker compose --env-file .codex-tmp/implementation.env -p msp-implementation20261022 -f deploy/implementation-verify.compose.yml --profile migration up --abort-on-container-exit --exit-code-from migration migration
```

该命令只启动本次 MySQL 与迁移进程，迁移退出后停止依赖，不启动完整应用。此命令尚未执行，不代表迁移已成功。

功能整体状态：**部分完成**。本轮新增 **45 项补充检查通过**，完整业务链路仍未验收。
