# task001 - P0 - 权限资源目录数据模型与迁移

## 状态

未开始

## 目标

新增 UI 权限资源目录和角色 UI 权限配置表，为页面可见、按钮可见、按钮可操作提供稳定的数据基础，并与现有 `user_role_permission` 兼容。

## 实现范围

- 新增表 `permission_resource`。
- 新增表 `user_role_ui_permission`。
- 补充唯一约束、查询索引和必要注释。
- 补充 MyBatis domain、mapper、example 或项目当前生成规范对应代码。
- 补充迁移脚本，放入当前版本对应 migration 目录。

## 建议表结构

### `permission_resource`

| 字段 | 类型 | 要求 | 说明 |
| --- | --- | --- | --- |
| id | varchar(64) | PK | 主键 |
| code | varchar(128) | unique, not null | 资源编码 |
| name | varchar(255) | not null | 展示名称 |
| type | varchar(32) | not null | `MENU` / `PAGE` / `BUTTON` / `API` |
| scope_type | varchar(32) | not null | `SYSTEM` / `ORGANIZATION` / `PROJECT` |
| parent_code | varchar(128) | nullable | 父级资源编码 |
| route_name | varchar(128) | nullable | 页面路由名 |
| permission_id | varchar(128) | nullable | 关联现有操作权限 |
| visible_default | bit | not null default 1 | 默认可见 |
| operable_default | bit | not null default 0 | 默认可操作 |
| sort | int | not null default 0 | 排序 |
| enabled | bit | not null default 1 | 是否启用 |
| description | varchar(1000) | nullable | 描述 |

### `user_role_ui_permission`

| 字段 | 类型 | 要求 | 说明 |
| --- | --- | --- | --- |
| id | varchar(64) | PK | 主键 |
| role_id | varchar(64) | not null | 角色 ID |
| resource_code | varchar(128) | not null | 资源编码 |
| visible | bit | not null default 0 | 是否可见 |
| operable | bit | not null default 0 | 是否可操作 |

## 不应实现的内容

- 不改变现有 `user_role_permission` 的语义。
- 不删除或重命名现有权限 ID。
- 不在本任务实现前端页面。

## 验收标准

- 数据库迁移可重复执行或具备幂等保护。
- 两张新表创建成功，索引可用。
- `role_id + resource_code` 具备唯一性约束或服务层幂等写入保证。
- 已补充 mapper/domain，后端编译通过。

## 验证要求

- 本地迁移执行验证。
- mapper 基础增删查改单测或集成验证。
- 检查升级已有环境时不会影响现有角色权限。
