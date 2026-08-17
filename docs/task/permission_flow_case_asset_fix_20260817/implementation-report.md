# MeterSphere 缺陷流程、Agent 集成、用例资产与异常修复实施记录

## 实施范围

- 缺陷流程的发布、开启使用、归档、删除及引用校验。
- 当前使用流程与历史缺陷的手工批量关联。
- 企业微信岗位预览、幂等同步和精确匹配。
- 管理员在合法下一步范围内强制流转，并记录原因和审计日志。
- 缺陷列表与详情的状态名称、状态编码统一解析。
- 系统设置与个人信息中的 Agent Token 共用同一组件，MCP 说明改为普通文字。
- 历史项目目录和历史用例幂等同步，保留源用例关系、标签和兼容的自定义字段。
- Excel/XMind 导入的参数、扩展名、大小校验，以及异步失败状态和真实原因持久化。
- 测试资产多页面目录 SQL 根因修复，以及缺失的 UI 权限资源补齐。

## 数据库迁移

- `V3.7.2_72__workflow_activation_wecom_position_case_asset_history.sql`
  - 增加 `workflow_definition.active_for_new`，通过生成列唯一索引保证同一场景最多只有一个使用中的流程。
  - 增加流程角色来源和匹配模式。
  - 增加历史项目用例与资产用例的源关系表。
- `V3.7.2_73__test_asset_catalog_page_permissions.sql`
  - 补齐公共步骤、接口资产、执行证据和缺陷资产页面权限资源。
- 已扫描全部 Flyway 文件，未发现重复版本号。
- V72 注释使用 ASCII，避免源码编码损坏 SQL 引号而导致容器启动失败。

## 多页面异常根因与修复

| 范围 | 根因 | 修复 |
| --- | --- | --- |
| 测试数据、环境、公共步骤、接口、执行证据、缺陷资产 | 查询一种资产时 `UNION` 六类历史表，放大缺表、字段和排序规则混用风险 | Mapper 改为按 `assetType` 只访问对应数据源，并降低查询开销 |
| 公共步骤、接口、执行证据、缺陷资产 | 路由有权限编码，数据库未初始化对应 `permission_resource` | 增加 V73 权限资源，并在路由中显式绑定 `resourceCode` |
| 用例资产历史补建 | 旧实现只建目录，不复制历史用例 | 按源用例 ID 幂等生成资产副本，并记录源关系 |
| Excel/XMind 导入 | 异步异常可逃出工作线程，导入服务会把底层原因替换为统一错误 | 工作线程统一落库 `FAILED`，保留原始异常原因，提交前校验文件 |
| 资产版本发布 | 聚合查询加锁无效，并发请求可能争抢相同版本号 | 删除无效聚合锁，捕获唯一键冲突后复用同内容版本或重试下一版本号 |

## 验证记录

- 后端联合编译：`system-setting`、`project-management`、`bug-management`、`case-management`、`agent-integration` 及依赖模块通过。
- 单元测试：`PermissionControlServiceTests` 12 条通过。
- 单元测试：`TestAssetCatalogServiceTests` 12 条、`TestAssetVersionServiceTests` 2 条，共 14 条通过。
- 前端：`pnpm type:check` 通过。
- Git 差异：`git diff --check` 通过。
- Mapper XML：语法解析通过。
- Flyway 版本扫描：无重复版本号。

## 部署后必验项

当前开发机未连接可运行的 Docker 引擎和部署数据库，因此以下项目需在发布环境完成：

1. 执行 V72、V73 后检查 `flyway_schema_history.success=1`。
2. 启动并重启容器，确认无 Flyway、Bean 初始化或 SQL 排序规则异常。
3. 对需求文档涉及的页面和操作执行真实环境回归。
4. 如仍出现 100500，按 requestId 提取完整后端堆栈，不仅依据前端提示判断原因。
