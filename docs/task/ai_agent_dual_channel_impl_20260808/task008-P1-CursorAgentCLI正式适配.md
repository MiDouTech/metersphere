# task008 - P1 - Cursor Agent CLI 正式适配

## 状态

实验实现：官方 Cursor Agent CLI 状态和 stream-json 适配已落地；因 headless 工具权限需要 OS 沙箱，默认拒绝执行且 Feature Flag 关闭，真实账号未验收。

## 目标

通过用户设备上的官方 Cursor Agent CLI 提供用户 Agent 通道，支持浏览器登录或官方 User API Key、Headless 流式输出、取消和会话恢复，并明确 Cursor 的代码场景和计费边界。

## 依赖

- task003 Bridge 配对与凭据安全。
- task004 WSS 协议。
- task005 UserAgentConnector 与工具安全。

## 官方能力基线

- Cursor Agent CLI 支持浏览器登录和 User API Key。
- 支持 Headless、`--output-format stream-json`、模型参数和会话恢复。
- Background Agents API 属于独立仓库 Agent 能力，可能按使用量计费。
- 官方参考：<https://docs.cursor.com/en/cli/reference/authentication>、<https://docs.cursor.com/background-agent/api/overview>。

## 实现范围

### 1. 环境与授权

实现 `CursorBridgeProvider`：

- 检测 `cursor-agent`/官方当前命令名称和版本。
- 调用官方 `login/status/logout` 能力。
- 支持 User API Key 时只存用户本地密钥链，不上传平台。
- 区分个人 CLI 登录和 Background Agents API Key。

### 2. Headless 执行

- 使用 `--print --output-format stream-json` 或当期官方等价参数。
- 逐条解析 JSON 事件，忽略未知可选字段但记录协议版本。
- 支持真实取消、总超时和孤儿进程清理。
- 使用受限工作目录，不将 MeterSphere 源码目录或用户任意仓库作为默认 CWD。

### 3. 场景限制

- Cursor 标记为“代码场景优先”的 Agent。
- 若当前任务只包含文本需求，允许对话但不宣称所有模型/工具能力。
- 涉及仓库读取时必须由用户显式选择本机目录并单独授权；不属于首期默认流程。

### 4. 会话和模型

- 仅使用官方公开的会话恢复命令。
- 保存外部 Session ID，不读取 Cursor 内部数据库。
- 模型列表和可用性由 CLI 状态返回或保守声明，禁止硬编码虚假能力。

### 5. 用量和错误

- 区分会员 CLI 用量与 Background Agents API 用量。
- 页面显示“供应商可能单独计费”，平台不承诺包含额度。
- 映射登录过期、User API Key 无效、额度不足、CLI 版本不兼容、网络失败。
- 失败时不静默回退其他资源。

## 验收标准

- 用户能通过官方 Cursor 登录或 User API Key 建立本人连接。
- Headless 输出可稳定映射为平台流式事件。
- 取消真实停止 CLI 进程。
- 默认不能读取用户未授权目录或执行任意命令。
- Background Agents API 未配置时不影响本地 Cursor Agent。

## 测试要求

- 登录、状态、退出和 API Key 掩码测试。
- `stream-json` 正常、未知、损坏和断流测试。
- 会话恢复、模型不可用和 CLI 升级兼容测试。
- 受限目录、路径穿越和命令参数注入测试。
- 真实 Cursor 会员账号端到端测试。
- Background API 独立计费提示与配置隔离测试。

## 非目标

- 不把 Cursor 会员包装成通用 OpenAI Compatible API。
- 首期不开放任意本机仓库写入和自动提交。
