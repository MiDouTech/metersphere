# task005 - P0 - UserAgentConnector、编排接入与工具安全

## 状态

部分实现：独立 Connector、双通道路由、工具白名单、取消、会话绑定、取消后迟到工具拒绝，以及无工具 Agent 复用模型 API 的结构化解析、单次受控修复和草稿校验链已落地；Prompt Injection、安全矩阵和完整 E2E 测试未完成。

## 目标

新增独立用户 Agent 适配接口，将 Bridge 流式事件接入现有用例 Agent 编排器，同时保持 MeterSphere 对工具、草稿、权限和正式保存的最终控制权。

## 依赖

- task001 双通道路由。
- task002 用户 Agent 数据模型。
- task004 WSS 长连接与事件协议。

## 当前基础

- `AiProviderAdapter` 和 `DefaultAiProviderAdapter` 负责模型 API。
- `AiCaseAgentOrchestrator` 已支持模型流、执行事件、工具循环和取消。
- `AiCaseAgentToolRegistry` 负责受控工具。
- `AiAgentGatewayService` 是远程 MCP/HTTP 调用，不是用户本地 Agent 生命周期接口。

## 实现范围

### 1. 新增接口

```java
public interface UserAgentConnector {
    boolean supports(String provider, String connectionMode);
    AgentConnectionStatus connectionStatus(String connectionId, String userId);
    AgentCapabilities capabilities(String connectionId, String userId);
    Flux<AgentStreamEvent> chatStream(UserAgentChatRequest request);
    void cancel(String requestId, String userId);
    void refresh(String connectionId, String userId);
    void revoke(String connectionId, String userId);
}
```

新增 Connector Registry，Provider 名称必须来自枚举或配置注册，不允许 `if/else` 分散在 Controller。

### 2. 编排分流

`AiCaseAgentOrchestrator` 在统一完成以下校验后再分流：

```text
用户登录
项目归属
会话所有者
FUNCTIONAL_CASE_AI:GENERATE
资源允许策略
并发与频率限制
来源文档权限
```

分流后：

- `MODEL_API` 调用现有 `AiProviderAdapter`。
- `USER_AGENT` 调用 `UserAgentConnector`。
- 两者输出统一为 `AiCaseExecutionEventDTO`。

### 3. 上下文构造

- 平台构造系统约束、历史消息摘要和已选来源文档。
- 不向 Bridge 发送数据库凭据、内部服务地址和无关项目 ID。
- 超长上下文在平台侧截断或摘要，记录丢弃策略。
- 外部 Session ID 只作为 Provider 会话绑定，不作为权限依据。

### 4. 工具闭环

首期允许：

```text
search_source_documents
get_selected_source_content
list_case_drafts
create_case_drafts
update_case_drafts
validate_case_drafts
find_similar_cases
```

禁止向外部 Agent 暴露 `save_formal_cases`。正式保存仍由用户在页面确认后调用原接口。

工具流程：

```text
Bridge tool.call
-> 校验 requestId/connectionId/userId/projectId
-> 工具白名单与 JSON Schema
-> 幂等键检查
-> 平台服务执行
-> 审计与脱敏
-> tool.result 返回 Bridge
```

### 5. 取消与重试

- 取消同时设置平台执行状态并向 Bridge 发 `execution.cancel`。
- 取消后所有迟到 `tool.call` 必须拒绝。
- 重试默认新建外部 Agent 执行，不复用失败中的流对象。
- 是否复用外部 Session 由 Connector 能力决定，并写审计。

### 6. 输出校验

- Agent 返回结构化用例仍经过现有严格 Schema。
- 非法 JSON 可按现有规则执行一次受控修复。
- Agent 输出不能覆盖 projectId、userId、formalCaseId、createUser 等控制字段。
- 文本回答可以保留，但不得作为正式草稿绕过校验。

## 建议代码落点

- `backend/services/system-setting/.../service/ai/agent/UserAgentConnector.java`
- `backend/services/system-setting/.../service/ai/agent/UserAgentConnectorRegistry.java`
- `backend/services/case-management/.../service/AiCaseAgentOrchestrator.java`
- `backend/services/case-management/.../service/AiCaseAgentToolRegistry.java`
- `backend/services/case-management/.../repository/AiCaseAgentRepository.java`

## 验收标准

- Mock Connector 可通过现有聊天接口流式生成和修改草稿。
- 模型 API 和用户 Agent 共享权限、Schema、草稿和正式保存规则。
- 非法工具、参数、项目资源和取消后调用均被拒绝。
- 外部 Agent 无法直接创建正式用例。
- 任一 Connector 故障不影响模型 API 通道。

## 测试要求

- Registry 路由与未知 Provider 测试。
- 模型/Agent 双通道参数化编排测试。
- 工具白名单、Schema、幂等和越权测试。
- 取消、重试、迟到事件和外部 Session 绑定测试。
- Prompt Injection 和控制字段覆盖测试。
- 原草稿与正式保存全量回归。

## 非目标

- 本任务不实现具体第三方 SDK/CLI。
