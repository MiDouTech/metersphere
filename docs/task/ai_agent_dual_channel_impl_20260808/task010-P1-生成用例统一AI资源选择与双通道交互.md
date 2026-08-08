# task010 - P1 - 生成用例统一 AI 资源选择与双通道交互

## 状态

实现中：统一资源选择、会话恢复、跨通道确认和不可用原因已落地；细粒度 Agent 状态、文件能力降级、刷新续流和真实草稿闭环 E2E 未完成。

## 目标

将【生成用例】页面的“模型”选择升级为统一“AI 资源”选择，分组展示平台模型、个人 API 模型和我的 Agent，并完成资源切换、离线恢复和跨通道确认交互。

## 依赖

- task001 统一资源接口。
- task005 用户 Agent 编排。
- task009 个人 Agent 管理。

## 当前基础

- `views/case-management/caseGenerate/index.vue` 已有模型选择、会话、SSE、草稿和停止生成。
- `api/modules/case-management/caseGenerate.ts` 使用 `modelSourceId`。
- 当前模型下拉为空时整个发送入口不可用，缺少资源不可用诊断。

## 实现范围

### 1. AI 资源选择器

分组显示：

```text
平台模型
我的 API 模型
我的 Agent
```

资源项显示：名称、Provider、系统/个人标识、在线状态、核心能力、实验标识和不可用原因。

离线、授权过期、项目禁止和能力不足的资源保留可见但禁用，并提供“去连接/重新授权”入口。

### 2. 会话创建和恢复

- 新会话选择项目默认资源；无默认时只在可用资源中选择。
- 恢复会话时使用持久化的 `resourceType/resourceId`，不被当前下拉默认值覆盖。
- 历史连接已删除时会话仍可读，发送前要求重新选择资源。
- 项目切换后清空不属于新项目的资源和会话状态。

### 3. 资源切换

- API 模型之间按原逻辑切换。
- 切换到用户 Agent 时提示第三方数据发送范围。
- Agent 之间或 Agent/模型之间切换时，提示外部隐藏上下文不会自动迁移。
- 切换只影响后续消息，消息卡片显示实际资源来源。
- 跨通道切换写审计。

### 4. 聊天与状态

- 浏览器继续使用平台 SSE。
- Agent Bridge 离线时保留输入内容，不重复创建用户消息。
- 显示“等待本机 Agent”“设备已接收”“正在调用工具”等状态。
- 取消按钮对两个通道保持一致，后端负责不同实现。
- 断线恢复按 `requestId + afterSequence`，不能依赖页面内存。

### 5. 错误体验

针对以下错误提供行动按钮：

```text
AGENT_OFFLINE -> 检查 Bridge / 切换资源
AGENT_AUTH_REQUIRED -> 去授权
AGENT_AUTH_EXPIRED -> 重新登录
AGENT_BRIDGE_VERSION_UNSUPPORTED -> 更新 Bridge
AGENT_PROVIDER_QUOTA_EXCEEDED -> 查看供应商套餐 / 切换资源
AI_RESOURCE_NOT_ALLOWED -> 联系项目管理员
```

跨通道回退必须弹出用户确认，不使用静默自动回退。

### 6. 能力降级

- 不支持文件的 Agent：已选文档由平台提取为受控文本上下文，仍超限则阻止发送。
- 不支持工具的 Agent：只能返回文本/结构化结果，由平台解析并校验。
- 不支持取消时：平台标记停止接收结果并提示“供应商任务可能仍在运行”。

## 建议代码落点

- `frontend/src/views/case-management/caseGenerate/index.vue`
- `frontend/src/api/modules/case-management/caseGenerate.ts`
- `frontend/src/models/caseManagement/caseGenerate.ts`
- 新增 `AiResourceSelector.vue`、`AgentConnectionStatus.vue`。
- 对应中英文 locale。

## 验收标准

- 模型 API 和用户 Agent 在同一选择器中分组展示。
- 原模型会话和聊天行为无回归。
- Agent 在线、离线、授权过期、禁用均正确展示。
- 切换资源后新消息使用新资源，历史消息来源不改变。
- WorkBuddy 可完成连续聊天、草稿修改和确认保存闭环。
- 页面刷新和断线后可恢复执行与消息。

## 测试要求

- 资源分组、排序、禁用和不可用原因组件测试。
- 项目切换、用户切换和历史会话恢复测试。
- 模型->Agent、Agent->模型、Agent->Agent 切换 E2E。
- 离线、重新配对、授权过期和额度不足 E2E。
- SSE 正常、断线、重连、取消、重试回归。
- 草稿编辑和正式保存回归。

## 非目标

- 不允许浏览器直接访问 localhost Bridge。
- 不在资源选择器中收集第三方账号密码。
