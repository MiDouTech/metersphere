# task005 - P0 - Runner 协议、注册、租约与任务调度

> 状态：后端核心已实现（注册、认证、心跳、容量、领取、短租约、续租、连续事件和状态上报已完成；真实 Runner 契约测试待 task006）

## 目标

建设 MeterSphere 与独立 Browser Runner 之间安全、可恢复、可版本化的任务协议，支持注册、心跳、租约、事件、证据和控制指令。

## 实施范围

- 定义 `/internal/ai-runner/v1` 内部接口及版本协商。
- Runner 注册能力：版本、浏览器、OS、并发、环境标签和状态。
- 实现心跳、容量管理、任务排队、匹配、租约领取、续租、释放和过期处理。
- 使用一次性短期任务令牌，只授权当前租约、事件和附件操作。
- 实现事件批量上报、单调序号、幂等去重和断线续传。
- 实现暂停、继续、取消、人工登录完成等命令通道。
- 定义 Runner 断线后的安全恢复边界：首期默认从用例边界恢复，不从结果未知的提交动作恢复。
- 防止同一任务被多个 Runner 同时接受。

## 重点文件

- `backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/runner/**`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/service/runner/**`
- `backend/services/agent-integration/src/main/java/io/metersphere/agent/security/**`
- Runner 工程目录（实施时在仓库内确定并记录 ADR）

## 验收标准

- Runner 可注册、心跳、领取匹配任务并完成租约释放。
- 心跳超时后任务不会被双重执行，能进入阻塞或安全重调度。
- 过期/撤销令牌不能上报事件或上传证据。
- 重复事件不会产生重复记录，事件缺口可检测。
- 取消和暂停在安全点生效，所有控制操作可审计。

## 测试要求

- 协议契约、版本不兼容、租约竞争、超时、乱序、重复上报和网络分区测试。
- 令牌越权、任务 ID 替换、重放攻击测试。

## 当前实施记录

- 管理员可注册 Runner，`msrt_` Token 只返回一次，数据库仅保存 SHA-256 摘要。
- Runner 内部接口使用 `msrt_` 自认证；租约接口使用独立的短期 `msrl_` Token。
- 任务确认后进入 `QUEUED`，Runner 按组织、显式 runnerId 和容量领取。
- 任务领取通过 `status + version` 乐观锁绑定 Runner 和租约，避免双重执行。
- 租约默认 60 秒并支持续租；Runner 心跳超过 90 秒禁止领取新任务。
- 事件批量上报限制 100 条，要求任务级序号严格连续，并在服务端二次脱敏。
- Runner 只能上报 `RUNNING/WAITING_LOGIN/WRITING_BACK/FAILED` 等事实状态，不能直接设置 `SUCCESS`。
