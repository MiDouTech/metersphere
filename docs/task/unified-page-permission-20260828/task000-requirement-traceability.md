# 页面可见性统一权限改造追踪

> 日期：2026-08-28
> 状态：核心代码已实现并通过静态检查、单元测试和构建；运行环境 E2E、Docker 与迁移清理尚未验证

| 需求 | 验收条件 | 前端入口/实现 | 后端接口/实现 | 数据/配置 | 测试 |
| --- | --- | --- | --- | --- | --- |
| 页面可见性由一套权限控制 | 页面、菜单、路由不再被 `user_role_ui_permission` 独立授权结果覆盖 | 路由继续使用 `meta.roles`；权限资源表只展示业务权限推导结果 | 登录态 `PermissionUiService.aggregate` 仅从 `user_role_permission` 和管理员作用域推导 | 复用 `permission_resource.permission_id` 映射，不新增第二套授权表 | 聚合服务正向、拒绝、旧 UI 记录不生效测试；前端类型检查 |
| 无独立权限的 Tab 继承父页面 | Tab 的 `permission_id` 为空时，继承最近一个具有权限映射的父资源 | `hasTabVisible` 使用登录态中统一推导的资源集合 | 聚合时沿 `parent_code` 解析最近权限映射 | 无迁移 | 父权限继承与无权限拒绝测试 |
| 管理员按作用域放权 | 系统、组织、项目管理员仅在有效关系和对应作用域内看到全部资源 | 前端消费当前作用域聚合结果 | 按有效角色、关系及 `source_id` 聚合；系统管理员全局放权 | 复用现有角色和关系 | 管理员及普通角色回归测试 |
| 角色设置不再独立编辑 UI 授权 | 页面资源表只读展示“由业务权限决定”，保存只提交业务权限 | 权限控制 → 角色设置 → 编辑角色 | 保存角色时清理该角色旧 UI 明细，业务权限为唯一授权来源 | 旧 `user_role_ui_permission` 表暂保留以便回滚，但不参与实际判定 | 角色保存服务测试、前端构建 |
| 后端仍为最终安全边界 | 隐藏页面不能替代接口鉴权 | 无权限路由拒绝；安全错误由现有拦截器处理 | 现有 `@RequiresPermissions`/`SessionUtils.hasPermission` 继续校验同一业务权限 | 无 | 后端权限回归测试、E2E 允许/拒绝路径 |

## 已执行证据

- `PermissionUiServiceTests`：3 个测试通过。
- `PermissionControlServiceTests`：26 个测试通过。
- `pnpm.cmd type:check`：通过。
- `pnpm.cmd build`：通过。
- `pnpm.cmd test:permission-resources`：通过，91 个精确绑定、47 个兼容绑定、0 个未绑定资源。
- `pnpm.cmd test:route-tabs`：通过。
- `pnpm.cmd test:api-contracts`：通过。
- system-setting Maven reactor package：通过。

## 尚未执行

- ESLint：当前安装环境无法找到 `eslint` 可执行文件。
- Prettier：当前安装环境无法找到 `prettier` 可执行文件。
- 数据库迁移验证：本阶段未修改表结构；旧 UI 表仅退出读取链路，尚未物理清理。
- Docker 构建、容器启动和健康检查。
- 连接真实后端与数据库的允许/拒绝角色 E2E 冒烟测试。

## 边界与失败行为

- `permission_resource.permission_id` 非空时，只认对应业务权限。
- `permission_id` 为空时，继承最近的父资源权限；不存在可继承权限时默认不可见，父容器可因可见子资源被补充显示。
- 旧 UI 明细即使配置为可见，也不能让没有业务权限的角色看到页面。
- 旧 UI 明细即使配置为隐藏，也不能阻止拥有业务权限的角色访问页面。
- 管理员放权仍要求角色启用、关系有效且作用域匹配。
- 本阶段保留旧表和兼容 DTO，避免数据库破坏性变更；稳定观察后再单独下线表结构。
