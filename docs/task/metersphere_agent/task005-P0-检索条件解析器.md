# task005 - P0 检索条件解析器

> **阶段**：P0  
> **预估工期**：1 天  
> **前置依赖**：[task004](task004-P0-DTO与Schema适配层.md)  
> **阻塞任务**：task006  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §4.3

---

## 1. 任务目标

实现 `AgentQueryResolver` 与 `ModuleTreeMatcher`，将 Agent 传入的 `query + filters` 解析为可执行的检索条件（模块 ID 含子树、keyword、priority 自定义字段、tags、lastExecuteResult）。

---

## 2. 设计原则

1. **组合解析**：不做模块/标签/keyword 互斥 early-return  
2. **模块命中含子树**：匹配节点后展开所有子孙 `moduleIds`  
3. **query 与 filters 可叠加**：如 `query=订单` + `filters.priority=P0`  
4. **返回消歧信息**：`matchedBy`、`matchedModules`、`warnings`  

---

## 3. 任务清单

### 3.1 ModuleTreeMatcher

**路径**：`.../resolver/ModuleTreeMatcher.java`

| 方法 | 说明 |
|------|------|
| `match(projectId, query)` | 拉取 `FunctionalCaseModuleService.getTree()`，name/path contains 匹配 |
| `expandSubtree(nodeId, tree)` | 返回节点及所有子孙 ID |
| `buildPathMap(tree)` | id → 可读路径，如「订单/下单流程」 |

**匹配规则**：

- 节点 `name` 或完整 `path` 包含 query（忽略大小写）  
- 多节点命中时合并 moduleIds（去重）  

### 3.2 AgentQueryResolver

**路径**：`.../resolver/AgentQueryResolver.java`

**解析流程**：

```
输入: query + filters + projectId
    │
    ├─ 1. filters.moduleIds 已有 → 直接使用
    │
    ├─ 2. query 非空 → ModuleTreeMatcher
    │      命中 → expandedModuleIds + matchedModules
    │
    ├─ 3. 若未限定范围且 query 非空 → keyword 兜底
    │      （name/num/tags LIKE）
    │
    └─ 4. 叠加 filters
           priority → functional_priority 子查询
           lastExecuteResult → functional_case.last_execute_result
           tags → functional_case.tags JSON 包含
```

**输出**：`ResolvedSearchCondition`

```java
@Data
public class ResolvedSearchCondition {
    private List<String> moduleIds;
    private String keyword;
    private List<String> priorities;
    private List<String> lastExecuteResults;
    private List<String> tags;
    private List<String> matchedBy;
    private List<String> matchedModules;
    private List<String> warnings;
}
```

### 3.3 校验

- `query` 与 `filters` 至少一项非空，否则 400  
- `pageSize` 最大 500  

---

## 4. 解析示例

| 用户说法 | Agent 请求 | 解析结果 |
|---------|-----------|---------|
| 提取财务模块用例 | `{ "query": "财务" }` | moduleIds=财务子树 |
| 订单 P0 | `{ "query": "订单", "filters": { "priority": ["P0"] } }` | moduleIds + priority |
| 登录相关 | `{ "query": "登录" }` | keyword=登录（模块未命中时） |
| 计划内未执行 P0 | `{ "testPlanId": "...", "filters": { "priority": ["P0"], "lastExecuteResult": ["PENDING"] } }` | 仅 filters |

---

## 5. 单元测试

- [ ] 模块命中时展开子树 ID  
- [ ] 模块未命中降级 keyword，warning=`MODULE_NOT_MATCHED_KEYWORD_FALLBACK`  
- [ ] query + priority 组合生效  
- [ ] filters.moduleIds 跳过 NL 解析  
- [ ] 空 query + 空 filters 返回 400  

---

## 6. 验收标准

- [x] `AgentQueryResolver` / `ModuleTreeMatcher` 已实现  
- [x] 组合解析 + 模块子树展开逻辑已实现  
- [x] priority 映射到 `caseLevel` / `functional_priority` 查询条件  
- [ ] Resolver 单元测试覆盖上述场景（待 task010）  

---

## 7. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
