# task002 - P0 MCP Tool Handler 拆分与 Schema 规范

## 目标

将当前集中在 `AgentMcpStreamableService` 的 MCP 工具注册和执行逻辑拆分为 Handler 注册表，避免新增工具继续堆积在大型 `switch` 中，并为所有新增工具提供严格 JSON Schema。

## 范围

- `backend/services/agent-integration/`
- MCP tools/list
- MCP tools/call
- 工具输入 Schema
- 工具注解 annotations
- 统一异常转换

## 实现要点

1. 新增工具 Handler 接口：

```java
public interface AgentMcpToolHandler {
    String name();
    String requiredScope();
    Map<String, Object> inputSchema();
    Map<String, Object> annotations();
    Object execute(Map<String, Object> arguments);
}
```

2. 按领域拆分工具目录：

```text
agent-integration/
  tool/
    AgentMcpToolHandler.java
    functional/
    bug/
    attachment/
    project/
    plan/
    review/
```

3. `AgentMcpStreamableService` 保留职责：

- MCP 协议处理
- 工具发现
- 工具分发
- 统一异常转换
- 统一响应封装

4. 新增工具 Schema 规则：

- `additionalProperties: false`
- 明确 `required`
- 明确字符串长度
- 明确数组最大数量
- 枚举使用 `enum`
- 项目标识字段统一名为 `projectId`
- 支持内部 ID、界面编号和精确项目名
- 所有写操作支持 `requestId`
- 更新操作支持 `expectedUpdateTime`
- 高风险操作支持 `confirm`

5. 工具 annotations 规则：

- 查询工具设置 `readOnlyHint: true`
- 删除、解除关联等工具设置 `destructiveHint: true`
- 支持幂等的写工具设置 `idempotentHint: true`

## 非目标

- 不在本任务实现具体业务工具逻辑。
- 不改变现有已发布工具的输入输出兼容性。

## 验收标准

- `tools/list` 能返回旧工具和新增工具的完整 Schema。
- 新增工具不再通过大型 `switch` 直接堆叠。
- 参数非法时，在进入业务 Service 前被拦截。
- JSON-RPC 错误结构稳定，业务错误码位于 `error.data`。
- 老工具行为保持兼容。
