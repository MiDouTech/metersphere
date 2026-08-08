# task000 - 权限控制改造任务总览

> 来源方案：`docs/summary/Metersphere_permission-control-refactor-plan.md`
> 任务目录：`docs/task/permission_control_refactor_20260808`
> 拆分日期：2026-08-08
> 当前状态：未开始

## 总体目标

在现有 RBAC 能力基础上增强 MeterSphere 权限控制体系，实现页面可见、按钮可见、按钮可操作的可配置能力，同时保持管理员默认全权限、现有管理员成员关系不变、后端接口权限继续作为最终安全边界。

本次任务拆分不表示功能已实现。所有任务默认状态为“未开始”，只有完成代码实现、迁移验证、接口联调、页面验证和验收项验证后，才允许更新为“已完成”。部分完成必须明确记录剩余事项。

## 改造原则

- 不推翻现有 `user_role`、`user_role_relation`、`user_role_permission`。
- 新增 UI 权限资源目录和角色 UI 权限配置，兼容旧权限行为。
- 管理员默认拥有所有页面、按钮和接口操作权限。
- 前端控制体验：页面隐藏、按钮隐藏、按钮禁用、路由拦截。
- 后端控制安全：接口继续使用 `@RequiresPermissions` 或等价权限校验。
- 未迁移页面按旧权限体系继续可用。

## 任务清单

| 任务 | 名称 | 优先级 | 状态 | 主要交付 |
| --- | --- | --- | --- | --- |
| task001 | 权限资源目录数据模型与迁移 | P0 | 未开始 | `permission_resource`、`user_role_ui_permission` 表与索引 |
| task002 | 权限资源编码规范与初始化目录 | P0 | 未开始 | 页面/按钮/API 资源编码规范、初始化数据 |
| task003 | 后端 UI 权限服务与资源树接口 | P0 | 未开始 | 资源树查询、角色 UI 权限读写服务 |
| task004 | 登录态有效 UI 权限聚合与兼容策略 | P0 | 未开始 | `/is-login` 返回 visible/operable 权限集合 |
| task005 | 前端权限 Store、工具函数与新指令 | P0 | 未开始 | `hasPageVisible`、`hasButtonVisible`、`hasButtonOperable`、指令 |
| task006 | 角色权限配置页面改造 | P0 | 未开始 | 页面可见、按钮可见、按钮可操作树形配置 |
| task007 | 管理员全权限与存量成员兼容保护 | P0 | 未开始 | 管理员绕过、内置角色保护、迁移兼容验证 |
| task008 | 缺陷管理页面样板改造 | P1 | 未开始 | 缺陷详情按钮资源化与操作控制 |
| task009 | 路由、菜单与页面可见性接入 | P1 | 未开始 | 路由 `resourceCode`、菜单过滤、直连拦截 |
| task010 | 后端接口权限一致性与测试补齐 | P1 | 未开始 | 权限常量映射、接口 403 验证、权限测试 |
| task011 | 全模块滚动迁移计划 | P2 | 未开始 | 用例、接口、测试计划、系统设置逐步覆盖 |
| task012 | 联调验收、发布与回滚方案 | P0 | 未开始 | 端到端验收矩阵、发布步骤、回滚策略 |

## 关键依赖

- 现有角色权限接口：
  - `/user/role/project/permission/update`
  - `/user/role/organization/permission/update`
  - `/user/role/global/permission/update`
- 现有前端权限能力：
  - `frontend/src/utils/permission.ts`
  - `frontend/src/directive/permission/index.ts`
  - 路由 `meta.roles`
- 现有后端权限能力：
  - `user_role_permission`
  - Shiro `@RequiresPermissions`
  - `SessionUtils.hasPermission`

## 不在本批任务范围

- 不做资源级数据权限，例如单条缺陷、单条用例、单个模块节点的 ACL。
- 不替换 Shiro 权限体系。
- 不移除现有 `v-permission`。
- 不强制一次性改完所有业务页面。

## 总体验收标准

- 管理员用户不受新权限配置影响，默认拥有全部页面和按钮操作能力。
- 当前已配置的管理员成员保持不变。
- 普通角色可以配置页面是否可见。
- 普通角色可以配置按钮是否可见。
- 普通角色可以配置按钮是否可操作。
- 按钮可见但不可操作时，前端禁用并提示无操作权限。
- 用户绕过前端直接调用无权限接口时，后端返回 403。
- 未迁移页面仍按旧权限体系正常工作。
- 缺陷管理样板页面完成真实账号验证后，再滚动迁移其他模块。
