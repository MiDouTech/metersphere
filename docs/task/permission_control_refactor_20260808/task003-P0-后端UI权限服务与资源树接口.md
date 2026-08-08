# task003 - P0 - 后端 UI 权限服务与资源树接口

## 状态

未开始

## 目标

提供权限资源树查询、角色 UI 权限读取与保存能力，为权限配置页面和登录态权限聚合提供后端服务。

## 实现范围

- 新增资源树查询接口：
  - `GET /permission/resource/tree?scopeType=PROJECT`
  - `GET /permission/resource/tree?scopeType=ORGANIZATION`
  - `GET /permission/resource/tree?scopeType=SYSTEM`
- 新增角色 UI 权限查询接口。
- 扩展现有角色权限保存接口，支持 `uiPermissions`。
- 新增 DTO：
  - `PermissionResourceDTO`
  - `RoleUiPermissionDTO`
  - `PermissionSettingUpdateRequest.uiPermissions`
- 服务层实现树构建、保存幂等、输入校验。

## 接口行为

- 查询资源树只返回 `enabled=true` 的资源。
- 保存角色 UI 权限时：
  - `operable=true` 必须自动保证 `visible=true`。
  - `visible=false` 必须自动设置 `operable=false`。
  - 资源不存在时返回参数错误。
  - 内置管理员角色禁止写入降权配置。

## 不应实现的内容

- 不在接口层绕过现有 `@RequiresPermissions`。
- 不把按钮可操作直接等同于后端接口允许。
- 不允许普通用户修改自身权限。

## 验收标准

- 资源树接口返回层级结构稳定。
- 角色权限保存同时支持旧 `permissionIds` 和新 `uiPermissions`。
- 保存后再次查询能返回一致数据。
- 非授权用户无法访问权限配置接口。

## 验证要求

- 单测覆盖资源树构建。
- 单测覆盖 `visible/operable` 联动规则。
- 接口测试覆盖新增、更新、清空 UI 权限。
