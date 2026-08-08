# task002 - P0 - 权限资源编码规范与初始化目录

## 状态

未开始

## 目标

建立统一的页面、按钮、接口资源编码规范，并初始化第一批权限资源目录，避免权限字符串继续散落在前端组件和后端接口中。

## 实现范围

- 定义资源编码规范。
- 梳理现有路由 `meta.roles` 和常用 `v-permission`。
- 初始化系统、组织、项目三类 scope 的资源树。
- 先覆盖缺陷管理作为样板模块。
- 生成初始化 DML，写入 `permission_resource`。

## 编码建议

| 类型 | 格式 | 示例 |
| --- | --- | --- |
| 菜单 | `{MODULE}_MENU` | `BUG_MANAGEMENT_MENU` |
| 页面 | `{MODULE}_{PAGE}_PAGE` | `BUG_DETAIL_PAGE` |
| 按钮 | `{MODULE}_{PAGE}_{ACTION}_BUTTON` | `BUG_DETAIL_EDIT_BUTTON` |
| 接口 | `{MODULE}_{ACTION}_API` | `BUG_UPDATE_API` |

## 缺陷管理首批资源

| 资源名称 | resourceCode | 类型 | permissionId |
| --- | --- | --- | --- |
| 缺陷管理 | `BUG_MANAGEMENT_PAGE` | PAGE | `PROJECT_BUG:READ` |
| 缺陷详情 | `BUG_DETAIL_PAGE` | PAGE | `PROJECT_BUG:READ` |
| 编辑按钮 | `BUG_DETAIL_EDIT_BUTTON` | BUTTON | `PROJECT_BUG:READ+UPDATE` |
| 分享按钮 | `BUG_DETAIL_SHARE_BUTTON` | BUTTON | `PROJECT_BUG:READ` 或新增 `PROJECT_BUG:READ+SHARE` |
| 关注按钮 | `BUG_DETAIL_FOLLOW_BUTTON` | BUTTON | `PROJECT_BUG:READ` 或新增 `PROJECT_BUG:READ+FOLLOW` |
| 复制按钮 | `BUG_DETAIL_COPY_BUTTON` | BUTTON | `PROJECT_BUG:READ+ADD` |
| 删除按钮 | `BUG_DETAIL_DELETE_BUTTON` | BUTTON | `PROJECT_BUG:READ+DELETE` |
| 评论入口 | `BUG_DETAIL_COMMENT_BUTTON` | BUTTON | `PROJECT_BUG:READ+COMMENT` |

## 不应实现的内容

- 不一次性覆盖所有业务模块。
- 不新增没有后端接口支撑的“伪操作权限”作为安全边界。
- 不把资源编码和前端文案硬编码绑定。

## 验收标准

- 输出明确的资源编码规范文档。
- 缺陷管理资源树可通过后端接口查询。
- 初始化数据具备排序和父子关系。
- 管理员角色无需显式写入所有资源也能全权限通过。

## 验证要求

- 初始化脚本执行后查询资源树结构正确。
- 编码无重复。
- `permission_id` 能映射到现有权限或明确标记为只控制 UI 可见性。
