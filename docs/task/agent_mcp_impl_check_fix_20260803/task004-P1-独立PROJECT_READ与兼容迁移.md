# task004 - P1 独立 PROJECT_READ 与兼容迁移

> 问题：MCP-005  
> 依赖：task001（精确 scope 匹配）  
> 状态：待开始

## 目标

将项目读取能力从 `FUNCTIONAL_READ` 解耦，新增独立 `PROJECT_READ`，并在迁移期兼容旧 Token。

## 范围

- `AgentTokenScope.java`：新增 `PROJECT_READ`
- `AgentScopeAssert` / implies：`AGENT_ALL` 覆盖；迁移期 `FUNCTIONAL_READ`/`FUNCTIONAL_ALL` 临时覆盖 `PROJECT_READ`
- MCP：`metersphere.project.search|list|get` 改为断言 `PROJECT_READ`
- REST：`AgentProjectController` 同步调整
- 前端个人/系统 Agent Token 权限选项：增加「项目查看」
- 技能包：说明新权限与兼容策略

## 兼容策略

```text
新 Token：显式勾选 PROJECT_READ（或 AGENT_ALL）
旧 Token：仅有 FUNCTIONAL_READ / FUNCTIONAL_ALL 时临时放行 PROJECT_READ
日志/文档：标注 FUNCTIONAL_READ 覆盖 PROJECT_READ 将废弃
```

## 验收标准

- 仅有 `PROJECT_READ` 可检索项目，无需 `FUNCTIONAL_READ`。
- 仅有 `CASE_WRITE` 等非项目读权限不可检索项目。
- `AGENT_ALL` 可检索。
- 旧 `FUNCTIONAL_READ` Token 在迁移期仍可检索（若启用兼容）。
- 前端权限选项出现中文「项目查看」。

## 非目标

- 不在本任务完成分页与 SQL 下沉（见 task005）。
