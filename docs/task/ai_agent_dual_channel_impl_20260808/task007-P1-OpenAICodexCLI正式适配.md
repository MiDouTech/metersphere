# task007 - P1 - OpenAI Codex CLI 正式适配

## 状态

实现中：官方 Codex CLI 登录状态、JSONL 流、多轮和取消适配已落地；真实会员账号、版本兼容和浏览器闭环未验收，默认关闭。

## 目标

通过用户设备上官方 Codex CLI 的 ChatGPT 登录状态提供 Codex Agent 通道，同时明确普通 ChatGPT 会员不能当作 OpenAI API Key 使用。

## 依赖

- task003 Bridge 配对与凭据安全。
- task004 WSS 协议。
- task005 UserAgentConnector 与工具安全。

## 授权边界

- ChatGPT 订阅与 OpenAI API 分开计费；通用模型 API 继续要求 OpenAI Platform API Key。
- Codex Agent 只通过官方 Codex 客户端登录流程使用。
- 平台和 Bridge 不读取、导出或上传 Codex 本地登录凭据。
- 官方能力参考：<https://help.openai.com/en/articles/11369540-using-codex-with-your-chatgpt-plan>。

## 实现范围

### 1. 环境检测

实现 `CodexBridgeProvider`：

- 检测 `codex` 可执行文件、版本和认证状态。
- 支持由官方 CLI 打开登录流程。
- 将未安装、版本过低、未登录、授权过期分开上报。
- 不尝试复制另一台设备或另一用户的 Codex 配置目录。

### 2. 非交互执行

- 使用官方支持的非交互/结构化输出能力。
- 每次执行绑定受限工作目录。
- 解析文本、状态、错误、工具和完成事件。
- stderr 只用于本地诊断，回传前脱敏且默认不持久化全文。
- 进程使用显式 PID/进程组管理，取消时真实终止子进程。

### 3. 会话

- 外部会话/恢复能力必须来自官方 CLI，不自行拼接私有状态文件。
- 无法安全恢复时，每轮使用平台提供的受控历史上下文创建新执行。
- 会话切换和模型选择以 CLI 实际支持能力为准。

### 4. 工具安全

- 默认不授予任意 Shell、文件写入、网络或浏览器控制。
- Bridge 创建专用临时工作目录，只放本次授权的需求文本和非敏感材料。
- 优先使用 MeterSphere 工具回调；无法限制工具时使用纯输出模式。
- 禁止 `--force`、full-auto 等绕过审批的默认配置。

### 5. 错误和回退

- 登录失效返回 `AGENT_AUTH_EXPIRED`。
- 计划额度不足返回 `AGENT_PROVIDER_QUOTA_EXCEEDED`。
- 不自动改用 OpenAI API Key或平台模型。
- 用户显式切换到模型 API 时创建新的资源选择审计记录。

## 验收标准

- 用户通过官方 Codex 登录后，平台能显示本人 Codex Agent 在线。
- 平台可以流式显示 Codex 回复并生成草稿。
- 取消能够终止真实 Codex 执行。
- Codex 无法访问未授权本机目录和平台其他项目资料。
- ChatGPT/Codex 与 OpenAI API 在 UI、计费和审计中明确区分。

## 测试要求

- 可执行文件、版本、登录状态检测测试。
- 结构化输出解析和未知事件前向兼容测试。
- 进程启动、取消、崩溃、超时和孤儿进程清理测试。
- 临时目录隔离与路径穿越测试。
- 登录过期、额度不足和网络失败测试。
- 使用真实 ChatGPT/Codex 账号完成受控端到端验证。

## 非目标

- 不调用 ChatGPT 网页接口。
- 不将 ChatGPT 会员标记为 OpenAI API 模型额度。
- 不默认开放 Codex 编码工具和本机仓库写权限。
