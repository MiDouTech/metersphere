# MeterSphere AI Browser Runner

独立的 Playwright + Chromium 执行进程，对接 `/internal/ai-runner/v1` 契约。Runner 只消费服务端冻结的 `actionJson/assertionJson`，不直接执行自然语言或模型输出的脚本。

## 本地启动

1. 管理员调用 `POST /ai/runner/register`，保存仅返回一次的 `runnerId/runnerToken`；按部署边界填写 `isolationMode=PROCESS/CONTAINER/VM`，未声明时平台会明确显示 `UNDECLARED`。
2. 复制 `.env.example` 中的变量到安全的进程环境；`MS_RUNNER_ALLOWED_ORIGINS` 必须显式列出被测站点 Origin。
3. 执行 `npm ci && npm run install-browser && npm run build && npm start`。

`loginMode=MANUAL` 时应使用 `MS_RUNNER_HEADLESS=false`（或在受控 VNC 容器中运行）。Runner 会保持隔离 Browser Context 并进入 `WAITING_LOGIN`，用户在 MeterSphere 确认登录完成后才继续；验证码和 MFA 不会被自动绕过。

## 安全边界

- 每个任务创建独立 Browser Context，不读取用户默认浏览器目录。
- 只执行 v1 白名单动作和确定性断言，不执行模型提供的 JavaScript/正则表达式。
- 顶层导航必须命中精确 Origin 白名单；`UPLOAD` 只能读取 `MS_RUNNER_UPLOAD_ROOT` 内文件。
- 截图上传前遮罩密码框及项目配置的敏感 CSS 区域；Token、Cookie 和凭据值不进入事件文本。
- 高风险动作禁止自动重试；定位自愈只接受唯一、精确语义候选，且不修改原始用例或预期结果。

## 全局质量门禁协议

认领响应包含 `qualityPolicy`（版本、内容哈希、规则快照）。新尝试绑定当时生效的全局版本；发布新版本不改变已认领任务。无已发布策略时认领失败，需要系统管理员先在“系统设置 → 质量门禁”发布。

Runner 为成功步骤上传 BEFORE_STEP/AFTER_STEP 截图，并调用 `POST /internal/ai-runner/v1/lease/{id}/step-result` 上报 `assertionResult` JSON 字符串（按冻结断言顺序排列的 `[{"actual": ...}]`）、证据 ID 和唯一 requestId。服务端从租约取得任务与尝试身份，重新验证原始预期、实际值、证据范围及策略。事件中的成功状态不能替代步骤结果。

新模型计划必须提供明确的 `operator` 和 `expected`；当前支持 EQUALS、IN_RANGE。EQUALS 不做类型转换，浏览器字符串值（含布尔状态字符串）与冻结字符串比较；IN_RANGE 的 expected 使用 `[下界,上界]` JSON 字符串，actual 上报有限数值。CONTAINS 等旧断言不能作为全局门禁通过依据。数据集引用不能替代冻结的明确预期。

截图按策略允许的 PNG/JPEG 编码并检查大小；项目截图选项不能关闭门禁证据。任何失败均不得报告正式通过。旧的直接用例结果提交接口返回 QUALITY_LEGACY_SUBMIT_FORBIDDEN，外部执行器须使用任务认领、证据上传、步骤结果提交与最终回写链路。
