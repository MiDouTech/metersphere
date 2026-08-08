# task006 - P1 - WorkBuddy Agent SDK 正式适配

## 状态

阻塞：仅保留安全失败边界；尚无可用于嵌入调用的正式 Managed SDK 凭据、费用/条款确认和真实购买账号验收，因此 Provider 默认关闭且不得宣称已接入。

## 目标

使用腾讯 WorkBuddy/CodeBuddy 官方 Agent SDK，实现用户本人登录授权、多轮会话、流式输出、工具调用、模型选择、取消和授权失效处理，作为首个正式用户 Agent 通道。

## 依赖

- task003 Bridge 配对与凭据安全。
- task004 WSS 协议。
- task005 UserAgentConnector 与工具安全。

## 官方能力基线

- Agent SDK 支持 `authenticate()` 登录流程、已有登录状态、异步流式消息和会话客户端。
- 支持 API Key、已有登录凭据或企业 OAuth Client Credentials，具体可用方式以购买账号和官方条款为准。
- 官方参考：<https://www.workbuddy.ai/docs/cli/sdk-python>、<https://www.workbuddy.ai/docs/cli/iam>。

## 实现范围

### 1. Bridge Provider 插件

实现 `WorkBuddyBridgeProvider`：

- 检测 SDK/CLI 版本。
- 获取授权 URL 并将“等待用户授权”状态返回平台。
- 完成授权后只上报脱敏账号和能力。
- 创建/恢复外部会话。
- 将 SDK 消息块转换为统一事件。
- 支持取消和连接健康检查。

### 2. 授权流程

```text
平台点击连接
-> Bridge 调用官方 authenticate()
-> 返回 auth_url
-> 用户在官方页面完成登录
-> Bridge 等待官方结果
-> 本地安全存储凭据
-> 平台连接状态改为 CONNECTED
```

平台不接收 SDK 返回的完整 Token。若 SDK 无法在不上传 Token 的前提下工作，则首期只支持本地 Bridge，不开放服务端模式。

### 3. 会话与模型

- 保存 `external_session_id`，支持多轮对话。
- 从官方能力获取可选模型，不能在平台写死未验证模型列表。
- 模型选择变化记录在外部会话绑定和消息审计中。
- Provider 不支持某能力时返回 `AGENT_CAPABILITY_UNSUPPORTED`。

### 4. 工具与权限

- 默认禁用 SDK 自带的任意 Shell、文件写入和桌面操作。
- 仅注册 task005 定义的 MeterSphere 受控工具。
- 若 SDK 无法精确限制工具，首期使用无工具模式并要求结构化返回，不得扩大权限。

### 5. 用量与错误

- 优先读取 SDK 提供的真实用量。
- 无真实 Token 时记录估算值和 `estimated=true`。
- 映射授权过期、额度不足、速率限制、模型不可用、网络失败和 SDK 版本不兼容。
- 不把 WorkBuddy错误自动回退到平台模型。

## 真实账号验证清单

上线前记录但不保存敏感凭据：

- 购买套餐名称和验证日期。
- SDK/CLI 版本。
- 登录方式。
- 是否消耗会员额度或产生独立费用。
- 多轮、流式、取消、工具和模型选择实测结果。
- 官方条款是否允许当前组织的嵌入式调用场景。

## 验收标准

- 用户可在平台发起并完成官方 WorkBuddy 授权。
- 授权后可以连续聊天并生成可编辑草稿。
- 停止生成能真实停止 SDK 执行和后续工具。
- Bridge 重启后可恢复授权状态或明确要求重新登录。
- 退出/撤销后旧会话不能继续调用。
- 完成从需求文档到正式用例保存的真实浏览器 E2E。

## 测试要求

- Mock SDK 单元测试。
- 授权成功、拒绝、超时、过期和撤销测试。
- 消息类型、delta、工具调用和错误映射契约测试。
- 多轮会话、模型切换和取消测试。
- 真实购买账号测试，敏感数据不得进入 CI 日志。
- 网络中断、额度不足和版本不兼容测试。

## 非目标

- 不通过 WorkBuddy 网页 Cookie 或私有接口调用。
- 不在本任务支持任意本地工具。
