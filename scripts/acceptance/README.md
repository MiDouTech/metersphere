# 需求验收补充

`environment-binding.browser.js` 使用 Playwright CLI 的已登录浏览器执行真实 HTTP 验收，不使用接口 Mock。仅针对隔离验收端口 15175 和测试项目 100001100001，不能直接改成生产地址运行。

先按 `scripts/verify-case-export.ps1` 的 Frontend、Backend、Image、Start 步骤准备隔离环境，并在另一个终端运行 `node scripts/case-export-browser-proxy.mjs`。该脚本若受本机 PowerShell 策略限制，应逐步运行其中同等构建/容器命令，不更改执行策略。使用具有配置及环境权限的测试账号在命名浏览器中登录，然后执行：

```powershell
playwright-cli.cmd -s=remediation-full run-code --filename=scripts/acceptance/environment-binding.browser.js
playwright-cli.cmd -s=remediation-full run-code --filename=scripts/acceptance/environment-editor.browser.js
playwright-cli.cmd -s=remediation-full run-code --filename=scripts/acceptance/export-history.browser.js
playwright-cli.cmd -s=remediation-full run-code --filename=scripts/acceptance/export-retry-loss.browser.js
playwright-cli.cmd -s=remediation-full run-code --filename=scripts/acceptance/export-notifications.browser.js
playwright-cli.cmd -s=remediation-full run-code --filename=scripts/acceptance/export-notifications-logout.browser.js
```

必须检查输出中的 `### Result`，不能只检查 CLI 的进程退出码（脚本断言错误可能出现在 `### Error` 中）。

覆盖：个人平台无资产保存、未发布资产拒绝、发布后绑定、凭据范围稳定、过期更新 409、错误引用拒绝、旧客户端省略字段保留绑定、显式解除绑定。脚本创建带 `binding-acceptance-` 前缀的环境及平台，结束时停用自己的测试平台并删除自己的环境；平台保留为可审计测试记录。不会删除其他数据。完整受管任务、真实登录验证及跨角色矩阵仍需独立验收。

第二个脚本从真实页面新增配置，填写名称、地址、允许 Origin，不绑定资产提交，刷新后回读并停用该测试配置，保存页面截图。记录使用 `binding-ui-acceptance-` 前缀。

导出历史脚本只读当前账号的测试项目历史：零勾选打开独立入口、真实 API 遍历所有分页并核对总数及唯一任务、检查 1024/1280 抽屉宽度并截图。运行期间不要同时创建新导出，否则总数变动会明确失败。真实历史下载另由现有 `frontend/e2e/case-export-recovery.spec.ts` 覆盖。

重试丢响应脚本需要先运行导出 E2E，使用其失败导出记录。它让真实服务端完成一次重试创建，再中断该响应，重新点击后核对相同 requestId、相同新 taskId 和来源关联。不是用 Mock 成功响应代替后端；新导出任务保留为隔离验收审计记录。该脚本仅证明响应丢失恢复，不代替所有网络及状态竞争矩阵。

通知脚本需要同一隔离环境的第二个应用进程，先执行 `docker compose --env-file .codex-tmp/case-export.env -p msp-export-20261021 -f deploy/case-export-verify.compose.yml run -d --no-deps --name msp-export-20261021-peer -p 127.0.0.1:18086:8081 -p 127.0.0.1:17076:7071 app` 并等待 readiness。它验证真实已完成任务通知、公开子协议、URL 不含凭据、票据重放/跨任务拒绝及第一实例签发、第二实例消费。结束后仅停止并移除这个命名 peer。持续多连接、撤销、原子消费和故障补充测试见 `CaseExportNotification*Tests` / `CaseExportTicketFilterTests`，真实 Redis 通过 `-Dquality.redis=true` 显式开启；不以模拟连接测试代替真实握手验收。

该通知脚本还会并发向两个实例消费同一票据，要求恰有一个赢家，并验证两个独立连接均收到通知。退出脚本另开全新浏览器 context，使用隔离环境的默认测试管理员登录，先申请票据再退出，要求 peer 拒绝旧票据，同时原浏览器会话仍可查询；最后关闭新 context，不退出原会话。资源有限时 peer 可加 `-e JAVA_MAX_MEM_RATIO=35`，业务配置与制品不变。

生产通知默认关闭，HTTP 查询与下载不受影响。启用须配置 `case.export.websocket.enabled=true` 和明确的 `case.export.websocket.allowed-origins=https://实际网页域名`（多个用逗号分隔，最多 16 个，不支持通配符），并让代理转发 Upgrade/Connection/子协议头。票据只在子协议请求头中发送，禁止在网关、APM 或调试日志记录 `Sec-WebSocket-Protocol` 原始值。默认每实例 100 连接、每用户每实例 8 连接、2 秒复核状态与权限；可通过 `max-connections`、`per-user-connections`、`poll-ms` 配置，启动时校验范围。连接最多 10 分钟，网页每次重连申请新票据，总尝试最多 5 次，后台/离线暂停；HTTP 轮询独立保留。

验证真实容器内的定时临时文件清理（必须使用应用账号，避免 root 夹具创建不可写目录）：

```powershell
cmd.exe /d /c "docker exec -i --user appuser msp-export-20261021-app-1 sh -s < scripts\acceptance\export-temp-cleanup.sh"
```

该脚本只创建并清理带随机 UUID 的三个夹具，等待最多 120 秒，断言过期孤儿被回收、新目录及非导出目录被保留；不递归删除测试根目录或其他文件。应用目录尚未创建时，也由 appuser 创建。

相应持久化单元/集成测试位于 `AgentEnvironmentAssetBindingTests`、`AgentEnvironmentBindingMigrationMySqlTests`、`AgentExecutionServiceAssetNormalizationTests`。运行时显式开启 `-Dquality.mysql=true`，数据库为回环 13326 上的专属测试 MySQL，测试生成独立随机数据库并在结束后删除。

若 Alpine 默认 CDN TLS 下载中断，Image 阶段可传 `-AlpineMirror https://dl-2.alpinelinux.org/alpine`；直接 Docker 构建使用 `--build-arg ALPINE_MIRROR=https://dl-2.alpinelinux.org/alpine`。版本路径仍来自基础镜像，TLS 和 APK 签名检查保持启用。系统依赖安装位于应用复制之前，后续应用变更可以复用已验证的依赖层。
