# AI Web UI 自动化执行 Runbook

## 1. 部署前检查

1. 按版本顺序执行数据库迁移，确认 `ai_execution_step`、`ai_execution_healing`、`ai_execution_artifact`、`ai_runner` 和 `ai_runner_lease` 存在。
2. 在 MeterSphere 配置可用 AI Provider，并在项目 AI 治理中配置模型白名单、并发数和 Token 配额。
3. 构建 `ai-browser-runner`，使用与 `package.json` 一致的 Playwright Chromium 版本；生产建议使用仓库 Dockerfile 固化浏览器依赖。
4. 注册 Runner 后只在密钥系统中保存一次性返回的 `msrt_` 令牌，不写入镜像、Git 或普通日志。
5. 配置 `MS_RUNNER_ALLOWED_ORIGINS` 为明确的协议、域名和端口集合；禁止通配符。凭据通过 `MS_RUNNER_VALUES_JSON` 或部署环境的密钥注入提供，测试步骤只引用 `valueRef`。

## 2. 健康检查

管理员调用 `GET /api/ai/execution/operations/summary`。以下任一条件应告警：

- `onlineRunnerCount=0` 且 `queuedTaskCount>0`；
- `staleRunnerCount>0`；
- `stuckTaskCount>0`（活动状态超过 10 分钟无更新）；
- `writebackBacklogCount>0` 或 `artifactBacklogCount>0`；
- `expiredArtifactCount` 持续增长两个清理周期以上。

任务排查统一携带 `taskId`，再从任务详情取得 `caseId`、`stepId`、`runnerLeaseId`，按事件 `sequence` 重建时间线。不得在工单或聊天中粘贴 Runner Token、Cookie、密码或未脱敏截图。

## 3. 常见故障处理

### Runner 全部离线

1. 暂停创建新任务；保留历史查询能力。
2. 检查进程、浏览器依赖、网络和令牌配置。
3. 超过 60 秒的活动租约会被服务端回收为 `EXPIRED`；不要手工把原任务改回运行态。
4. Runner 恢复后，由用户在任务页对失败/阻塞用例发起重试，生成新租约并从用例边界执行。

### MinIO 或证据存储不可用

1. 停止扩大执行流量，保留失败任务与事件。
2. 恢复存储后验证上传、下载和到期删除各一条。
3. 证据缺失不得把任务标记为完整成功；通过运维摘要持续观察 `artifactBacklogCount`。

### AI Provider 不可用

任务创建阶段的步骤规划应失败并回滚，不应产生可领取的半成品任务。恢复 Provider 后使用新的幂等键重试；不要跳过动作契约预检。

### 回写积压或部分失败

1. 查看任务的 `writebackStatus` 和 `CASE_WRITEBACK_FAILED` 事件。
2. 修复数据库或业务校验问题后，仅对失败/阻塞用例执行重试。
3. 回写幂等键包含 task、case、attempt 和结果；禁止通过直接改表绕过幂等记录。

### 疑似越权或误操作

立即停止 Runner，撤销注册令牌，保留事件和证据，导出相关 task/lease 审计记录。第一阶段高风险动作应在服务端预检或 Runner 契约层被拒绝；若发现已执行，按 P0 安全事件处理并停止灰度。

## 4. 扩缩容与升级

- 扩容时注册独立 Runner 身份，每个实例配置符合资源上限的 `maxConcurrency`。
- 缩容先停止轮询，等待 `activeCount=0` 后终止进程。
- 升级先以单实例验证 `contractVersion=v1`、真实 Chromium 烟测和一个只读用例，再滚动替换。
- 不允许新旧 Runner 共享同一个注册令牌或本地浏览器用户目录。

## 5. 回滚

1. 停止 Runner，阻断新任务创建入口。
2. 等待或取消活动任务，确认无活动租约。
3. 回滚应用代码；数据库新增表和列保留，避免破坏历史任务审计。
4. 历史任务、事件和证据保持只读可查询。
5. 回滚后执行权限、任务读取和附件下载抽查并记录结论。
