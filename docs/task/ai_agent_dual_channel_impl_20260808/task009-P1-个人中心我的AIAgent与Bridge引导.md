# task009 - P1 - 个人中心“我的 AI Agent”与 Bridge 引导

## 状态

实现中：个人中心连接、配对、状态轮询、设备撤销和官方本地 CLI 授权入口已落地；Bridge 签名下载、安装进度和浏览器 E2E 未完成。

## 目标

在个人中心提供用户 Agent 连接、Bridge 下载与配对、官方授权、设备状态、重新授权和撤销管理，使普通用户无需理解 Gateway、Token 或 WSS 即可完成接入。

## 依赖

- task002 用户 Agent 数据模型。
- task003 Bridge 配对与设备身份。
- task006–task008 Provider 适配状态接口。

## 当前基础

- `ms-personal-drawer` 已有模型设置和 Agent Integration 入口。
- `views/setting/system/agentIntegration` 当前主要提供 MeterSphere MCP 接入说明。
- 尚无个人外部 Agent 连接卡片、设备配对和授权状态页面。

## 实现范围

### 1. 菜单与页面

在个人中心新增独立“我的 AI Agent”，不要覆盖原“模型设置”和现有 MeterSphere MCP 接入说明。

页面区域：

```text
Agent 连接卡片
已配对设备
Bridge 下载与版本
安全与数据发送说明
```

### 2. Agent 卡片

WorkBuddy、Codex、Cursor 卡片至少显示：

- Provider 与显示名称。
- 连接方式：本地 Bridge/API Key/OAuth/远程 Gateway。
- 脱敏账号。
- 状态：未安装、未配对、待授权、在线、离线、授权过期、已禁用。
- 连接设备、最近心跳和能力。
- 操作：安装、配对、授权、重试、断开、撤销和删除。

卡片只在对应 Feature Flag 开启时显示。

### 3. 引导流程

```text
选择 Agent
-> 检测是否已有在线 Bridge
-> 无 Bridge：下载并安装
-> 生成一次性配对码
-> Bridge 配对完成
-> 启动官方 Agent 授权
-> 等待连接状态
-> 连接测试
-> 完成
```

每一步必须有超时、重试和退出后的恢复能力。

### 4. 设备管理

- 显示设备名、系统、Bridge 版本、在线状态、最近心跳和连接的 Agent。
- 撤销前提示该设备上的运行中任务和受影响连接。
- 撤销后前端立即清理缓存状态并刷新资源列表。
- 不显示设备公钥全文、证书或任何第三方凭据。

### 5. 安全提示

授权前明确说明：

- 需求文本和用户选中的文档内容会发送到本人授权 Agent。
- 平台不会要求第三方账号密码。
- 本地 Agent 可能受供应商额度和条款限制。
- 默认不允许 Agent 访问任意本机文件或执行任意命令。
- 用户可随时撤销连接和设备。

### 6. API 与状态管理

新增前端模块：

```text
api/modules/setting/userAgent.ts
api/requrls/setting/userAgent.ts
models/setting/userAgent.ts
store/modules/setting/userAgent.ts（确有跨页面状态需要时）
```

轮询授权状态要有明确停止条件；设备在线状态优先使用低频轮询或平台事件，不建立浏览器到本机连接。

## 验收标准

- 新用户可以按引导完成 Bridge 安装、配对和至少一个 Agent 授权。
- 页面刷新、抽屉关闭后重新打开可以恢复当前步骤。
- 授权过期、Bridge 离线和版本过低均给出准确修复操作。
- 撤销立即生效，生成用例资源列表同步刷新。
- 页面和浏览器存储不存在完整凭据。

## 测试要求

- 各连接状态组件和操作权限测试。
- 配对过期、授权拒绝、Bridge 离线、版本过低 E2E。
- 跨用户登录后的缓存清理测试。
- 响应式布局和中英文文案测试。
- 浏览器 LocalStorage/SessionStorage 敏感信息断言。
- WorkBuddy 真实授权 UI E2E；Codex/Cursor 按 Feature Flag 分别测试。

## 非目标

- 不在该页面配置普通模型 API Key；继续使用原模型设置。
- 不把现有 MeterSphere MCP 接入说明删除或混为外部 Agent 授权。
