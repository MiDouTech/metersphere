# 缺陷详情样板权限映射与验证记录

## 覆盖范围

本记录对应 `task010 - P1 - 后端接口权限一致性与测试补齐`，仅覆盖本次样板改造的缺陷详情抽屉入口。缺陷列表、回收站、关联用例、附件内部操作等仍按旧权限体系运行，后续滚动迁移时再补充资源编码。

## 页面 / 按钮资源映射

| UI 资源编码 | 类型 | 前端入口 | UI 可见/可操作关联权限 | 后端安全边界 |
| --- | --- | --- | --- | --- |
| `BUG_MANAGEMENT_PAGE` | PAGE | 缺陷管理列表路由 | `PROJECT_BUG:READ` | 列表/详情读取接口仍由 `PROJECT_BUG:READ` 控制 |
| `BUG_DETAIL_PAGE` | PAGE | 缺陷详情抽屉/详情路由 | `PROJECT_BUG:READ` | `/bug/get/{id}` 使用 `PROJECT_BUG:READ` |
| `BUG_DETAIL_EDIT_BUTTON` | BUTTON | 编辑按钮、详情内字段编辑 | `PROJECT_BUG:READ+UPDATE` | `/bug/update` 使用 `PROJECT_BUG:READ+UPDATE` |
| `BUG_DETAIL_SHARE_BUTTON` | BUTTON | 分享按钮 | `PROJECT_BUG:READ` | 前端复制链接行为，无写接口；读取链接落到详情读取权限 |
| `BUG_DETAIL_FOLLOW_BUTTON` | BUTTON | 关注/取消关注按钮 | `PROJECT_BUG:READ` | `/bug/follow/{id}`、`/bug/unfollow/{id}` 当前后端使用 `PROJECT_BUG:READ` |
| `BUG_DETAIL_COPY_BUTTON` | BUTTON | 更多菜单 - 复制 | `PROJECT_BUG:READ+ADD` | 复制跳转后创建接口 `/bug/add` 使用 `PROJECT_BUG:READ+ADD` |
| `BUG_DETAIL_DELETE_BUTTON` | BUTTON | 更多菜单 - 删除 | `PROJECT_BUG:READ+DELETE` | `/bug/delete/{id}` 使用 `PROJECT_BUG:READ+DELETE` |
| `BUG_DETAIL_COMMENT_BUTTON` | BUTTON | 评论输入框 / 发布评论 | `PROJECT_BUG:READ+COMMENT` | `/bug/comment/add` 使用 `PROJECT_BUG:READ+COMMENT` |

## 兼容策略

- 未配置 `user_role_ui_permission` 的角色，登录态聚合时继续使用旧 `user_role_permission` 兜底。
- 已配置某个 UI 资源后，该资源以 UI 权限配置为准。
- `operable=true` 自动保证 `visible=true`。
- 管理员内置角色不可编辑 UI 权限；`admin` 前端短路全权限，`org_admin/project_admin` 后端登录态聚合时在对应作用域给出全部 UI 权限。

## 自动化验证

新增轻量反射测试：

`backend/services/bug-management/src/test/java/io/metersphere/bug/controller/BugDetailPermissionMappingTests.java`

测试目的：

- 防止缺陷详情样板对应 Controller 方法后续丢失 `@RequiresPermissions`。
- 防止按钮资源与后端 Shiro 权限常量发生静默偏移。

本次已执行：

```bash
./mvnw.cmd -pl backend/services/system-setting,backend/services/project-management -am -DskipTests clean compile
./mvnw.cmd -pl backend/services/bug-management -Dtest=BugDetailPermissionMappingTests test
```

结果：以上命令均 `BUILD SUCCESS`。

说明：以上命令验证本次后端新增服务、Mapper、DTO、Controller 编译通过，并验证缺陷详情样板相关 Controller 权限注解未偏移；完整接口 403/成功用例仍建议在联调环境按 `task012` 账号矩阵执行。
