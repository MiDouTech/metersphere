# 2026-09-02 优化需求实现追踪

状态约定：`已验证`表示已有自动化或构建证据；`已实现待环境验证`表示代码链路完整，但仍需数据库迁移、容器或浏览器 E2E 证据。

| 编号 | 需求与验收条件 | 前端入口/实现 | 后端/API 与数据 | 验证证据 | 状态 |
|---|---|---|---|---|---|
| 1 | Agent 一级页签只显示一套，保留系统顶部路由页签 | `frontend/src/views/agent/components/AgentPage.vue` 删除内层 `AgentTabs` | 无 | 前端类型检查、生产构建通过；待浏览器路由回归 | 已实现待环境验证 |
| 2 | Excel、XMind 等导入模块按名称自然排序，`F2 < F10`；导入后仍可手动拖拽 | 复用现有模块树与拖拽 | `FunctionalCaseModuleService` 对导入模块路径执行数字感知排序，复用 `pos` | `FunctionalCaseModuleServiceTests` 1/1 通过 | 已验证 |
| 3 | 缺陷筛选项语义去重，“新建/创建”等只显示一个，查询覆盖全部底层状态 ID | 复用缺陷列表筛选 | `BugStatusService` 归并显示项；`BugService` 在普通/高级筛选扩展底层 ID | `BugStatusServiceTests` 1/1 通过；待分页 E2E | 已实现待环境验证 |
| 4 | 仅删除测试计划详情“同步状态至测试计划”；普通用例列表入口保留 | `frontend/src/views/test-plan/testPlan/detail/featureCase/components/caseTable.vue` | 既有接口保留，兼容其他调用方 | 前端类型检查、生产构建通过；待可见性 E2E | 已实现待环境验证 |
| 5a | 角色成员候选查询不再返回服务器异常 | 复用权限角色成员弹窗 | `ExtUserRoleRelationMapper.xml` 将 `CASE` 返回值改为 MySQL/JDBC 可稳定转换的数值布尔 | 测试环境 traceId `ad36a07c-73fa-4f34-b997-57d0d8385934` 已定位；Testcontainers 用例已添加，本机 Docker 不可用 | 已实现待环境验证 |
| 5b | 企微每日通知计划可在 `weekdays` 为空时保存 | 复用企微通知规则页面 | `WecomBotService#validateSchedules` 使用可接收 null 的列表 | 测试环境 traceId `6108c20f-2fbf-4c67-a00b-8b47f9442a9f` 已定位；`WecomBotServiceTests` 9/9 通过 | 已验证 |
| 5c | 扫描其他同类服务器异常 | 无新增入口 | 复核统一异常响应及测试容器日志 | 2026-09-02 日志扫描只发现 5a/5b 两类；完整菜单 E2E 尚未完成 | 已实现待环境验证 |
| 6 | 功能用例列表直接修改执行结果、执行人；支持清空；同步最后执行人/时间及关联计划 | `caseTable.vue` 增加内联选择、loading、失败回滚和刷新 | 复用批量编辑接口；`FunctionalCaseService`、`ExtFunctionalCaseMapper` 实现清空和计划同步 | 执行人清空同步单测 1/1、后端编译、前端类型检查和生产构建通过；待结果同步集成/E2E | 已实现待环境验证 |
| 7 | 本轮点击修改字段采用缺陷详情“处理人”样式 | 用例等级、执行结果、执行人及资产分类采用文本态、悬浮提示、紫色选择器 | 复用既有字段 API | ESLint 通过（大文件保留 13 个既有 warning）；待视觉 E2E | 已实现待环境验证 |
| 8a | 建立方式支持 `MANUAL/AI/IMPORT/SYNC/AUTOMATION/UNKNOWN`，由可信后端写入 | 测试资产各 Tab 展示/筛选来源，含缺陷 | `TestAssetGovernanceService` 及用例、文档、缺陷、Runner/证据事件链；`test_asset_metadata` 幂等回填 | 资产相关定向测试纳入 22/22 通过；待迁移与 E2E | 已实现待环境验证 |
| 8b | 组织级独立分类树，最多 5 层；支持重名校验、循环校验、删除迁移、排序、审计和权限 | `TestAssetCategoryDrawer.vue`，列表筛选、单个/批量归类 | `/test-assets/categories/**`、归类 API；分类/元数据/审计表及权限迁移 | 后端编译、前端类型检查与生产构建通过；待真实 MySQL/权限/E2E | 已实现待环境验证 |
| 8c | 测试资产各类展示来源与分类并支持后端筛选，范围含缺陷 | `catalog.vue`、`cases.vue`、`documents.vue`、`versions.vue`、`relations.vue` | catalog/documents/versions/relations/functional-case 查询扩展；catalog 纳入 BUG | `TestAssetCatalogServiceTests` 13/13 通过；待分页/导出/E2E | 已实现待环境验证 |
| 8d | AI 资产追溯批次、来源固定版本、模型、审核；无权限裁剪正文/片段/原始输出 | 资产详情展示 AI 来源信息 | 元数据详情查询复用 AI 生成记录并执行项目权限裁剪 | 资产发布与目录测试通过；待权限集成测试 | 已实现待环境验证 |
| 8e | 仅允许治理 `UNKNOWN`；跨组织复制按分类路径文本匹配，不复用源分类 ID | 来源治理弹窗 | `/test-assets/source-governance`；默认 Hub 复制发布事件调用 `copyCategoryByPath` | `TestAssetGovernanceServiceTests`、`TestAssetPublicationListenerTests` 通过；待跨组织数据库集成 | 已实现待环境验证 |

## 已确认的失败行为

- 校验、权限、冲突、资源不存在、网络和未知服务器错误分别处理，前端写操作提供 loading 与失败提示。
- 批量归类逐项返回成功/失败，部分失败不会提示全部成功。
- 分类删除不级联删除业务资产；非空分类必须显式选择迁移或转未分类。
- 来源一经可信链路确定不可由普通页面覆盖，仅 `UNKNOWN` 可通过治理接口修改并写审计。
- 跨组织复制只传递分类路径文本，目标组织不存在同路径时归入未分类。

## 尚缺少的交付证据

- 本机 Docker daemon 不可用，MySQL Testcontainers Mapper 测试、镜像构建、迁移验证、容器启动和健康检查未执行。
- GitHub Actions `33614038485` 的 Linux 前端生产构建、后端全量打包均通过；CNB 制品上传因仓库未配置 `CNB_TOKEN` 失败，镜像构建/推送与版本发布被跳过。
- `msp.ebcone.net` 已登录会话可只读接管，但本提交尚未部署，因此核心路径 E2E 和修复后容器日志复核尚未执行。
- 用例执行人清空已有服务单测；执行结果及关联计划同步仍缺少真实数据库集成测试。
