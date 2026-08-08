# task009 - P1 - 路由、菜单与页面可见性接入

## 状态

未开始

## 目标

将页面资源编码接入前端路由、菜单过滤和直连拦截，使页面可见性可以由角色 UI 权限统一控制。

## 实现范围

- 路由 `meta` 增加 `resourceCode`。
- 调整 `topLevelMenuHasPermission`。
- 调整 `getFirstRouteNameByPermission`。
- 调整 `routerNameHasPermission`。
- 菜单过滤同时考虑：
  - 项目模块配置
  - 旧 `meta.roles`
  - 新 `resourceCode` 页面可见权限
- 直接访问不可见页面时跳转无权限页或首个有权限页面。

## 兼容策略

- 无 `resourceCode` 的路由沿用旧 `meta.roles`。
- 管理员直接通过。
- 未配置 UI 权限的用户角色沿用旧权限判断。

## 不应实现的内容

- 不一次性要求所有路由补齐 `resourceCode`。
- 不改变白名单页面行为。
- 不影响登录页、分享页、SSO callback 等匿名页面。

## 验收标准

- 有页面可见权限时菜单展示。
- 无页面可见权限时菜单隐藏。
- 直接访问不可见页面被拦截。
- 未迁移路由行为不变。

## 验证要求

- 菜单渲染验证。
- 刷新页面验证。
- 项目切换验证。
- 白名单路由验证。
