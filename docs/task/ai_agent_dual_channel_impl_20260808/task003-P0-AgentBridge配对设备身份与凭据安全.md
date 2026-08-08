# task003 - P0 - Agent Bridge 配对、设备身份与凭据安全

## 状态

实现中：安全配对、设备密钥、签名挑战和短令牌已落地；签名安装包、自动升级和真实设备安全验收未完成。

## 目标

实现用户设备上的 Agent Bridge 与 MeterSphere 的安全配对、设备认证、令牌轮换和撤销，使平台能够识别本人设备，但不获取设备上的第三方 Agent 凭据。

## 依赖

- task002 用户 Agent 连接、设备、会话绑定与权限数据模型。

## 实现范围

### 1. Bridge 最小职责

- 设备注册与配对。
- 检测 WorkBuddy/Codex/Cursor 官方 SDK/CLI。
- 调用官方登录流程，不读取或上传登录密钥。
- 与平台建立出站 WSS。
- 运行、取消 Agent 会话并转换事件。

Bridge 不开放公网入站端口，不保存 MeterSphere 密码。

### 2. 一次性配对

接口：

```http
POST /ai/agent-bridge/pairing
POST /ai/agent-bridge/pairing/consume
GET  /ai/agent-bridge/devices
POST /ai/agent-bridge/devices/{id}/revoke
```

要求：

- 配对码使用安全随机数，有效期不超过 5 分钟。
- 数据库只保存配对码哈希。
- 配对码绑定发起用户、预期 Provider 和可选设备名称。
- 使用行锁或原子条件更新保证只能消费一次。
- 错误响应不能区分“码不存在”和“码属于其他用户”。

### 3. 设备密钥

- Bridge 首次启动生成设备非对称密钥对。
- 私钥存入 Windows Credential Manager/macOS Keychain/Linux Secret Service。
- 平台保存公钥和证书指纹。
- 配对成功签发短期设备令牌，令牌绑定 `deviceId/userId/keyFingerprint`。
- 重连使用设备签名挑战，不使用长期静态 Bearer Token。

### 4. 凭据边界

不得上传：

```text
WorkBuddy/Cursor/Codex 登录 Token
网页登录 Cookie
CLI 本地配置文件
操作系统密钥链内容
第三方账号密码
```

平台只接收：Provider、脱敏账号标识、登录状态、能力、官方客户端版本和授权到期提示。

### 5. Bridge 分发

- Windows 首期提供签名安装包或受控压缩包。
- 下载接口返回版本、SHA-256 和签名信息。
- Bridge 启动时校验平台地址和 TLS 证书。
- 禁止静默降级到不安全 HTTP/WSS。

## 建议代码落点

- 后端新增 `AiAgentBridgePairingController/Service`。
- 后端新增 `AiAgentDeviceRepository`。
- 独立 `agent-bridge` 工程，避免在浏览器或前端进程中调用本地 CLI。
- 复用现有 `AiAuditService` 和加密工具，但不复用第三方 Agent Token 存储。

## 验收标准

- 用户可以在平台生成配对码并成功绑定本人 Bridge。
- 重复消费、过期码、跨用户码和伪造设备均被拒绝。
- 平台数据库、日志和网络抓包中不存在第三方 Agent 凭据。
- 撤销设备后旧会话立即失效，不能自动重新配对。
- Bridge 安装包和升级包可验证完整性与签名。

## 测试要求

- 配对码熵、过期、一次性和并发消费测试。
- 跨用户配对与设备冒用测试。
- 签名挑战、nonce 重放、时钟偏移测试。
- 设备令牌过期和轮换测试。
- 凭据泄漏静态扫描与日志断言。
- Windows Credential Manager 集成测试。

## 非目标

- 本任务不实现 Agent 聊天协议和 Provider 调用。
