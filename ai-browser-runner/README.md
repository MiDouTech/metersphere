# MeterSphere AI Browser Runner

独立的 Playwright + Chromium 执行进程，对接 `/internal/ai-runner/v1` 契约。Runner 只消费服务端冻结的 `actionJson/assertionJson`，不直接执行自然语言或模型输出的脚本。

## 本地启动

1. 管理员调用 `POST /ai/runner/register`，保存仅返回一次的 `runnerId/runnerToken`。
2. 复制 `.env.example` 中的变量到安全的进程环境；`MS_RUNNER_ALLOWED_ORIGINS` 必须显式列出被测站点 Origin。
3. 执行 `npm ci && npm run install-browser && npm run build && npm start`。

`loginMode=MANUAL` 时应使用 `MS_RUNNER_HEADLESS=false`（或在受控 VNC 容器中运行）。Runner 会保持隔离 Browser Context 并进入 `WAITING_LOGIN`，用户在 MeterSphere 确认登录完成后才继续；验证码和 MFA 不会被自动绕过。

## 安全边界

- 每个任务创建独立 Browser Context，不读取用户默认浏览器目录。
- 只执行 v1 白名单动作和确定性断言，不执行模型提供的 JavaScript/正则表达式。
- 顶层导航必须命中精确 Origin 白名单；`UPLOAD` 只能读取 `MS_RUNNER_UPLOAD_ROOT` 内文件。
- 截图上传前遮罩密码框及项目配置的敏感 CSS 区域；Token、Cookie 和凭据值不进入事件文本。
- 高风险动作禁止自动重试；定位自愈只接受唯一、精确语义候选，且不修改原始用例或预期结果。
