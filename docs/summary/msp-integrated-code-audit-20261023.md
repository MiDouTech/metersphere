# 整合方案源码复核索引

日期：2026-10-23；本地HEAD `104fb6be2645bb47dc27e515254803398ce458ad`。用于支持[修订方案](msp-integrated-delivery-plan-20261023.md)，不代表运行验收。索引通过实际文件和符号匹配生成；行号可能随修改变化。

| 链路 | 源码 | 定位符号 | 结论 |
| --- | --- | --- | --- |
| 项目创建 | [CommonProjectService.java:171](../../backend/services/system-setting/src/main/java/io/metersphere/system/service/CommonProjectService.java#L171) | `serviceInvoker.invokeCreateServices` | 资源创建早于后续hub目录钩子 |
| 资源分发 | [ProjectServiceInvoker.java:28](../../backend/services/system-setting/src/main/java/io/metersphere/system/invoker/ProjectServiceInvoker.java#L28) | `public void invokeCreateServices` | 复用现有扩展接口 |
| 资产建项钩子 | [CaseAssetProjectResourceService.java:13](../../backend/services/case-management/src/main/java/io/metersphere/functional/asset/service/CaseAssetProjectResourceService.java#L13) | `public void createResources` | 已有资产目录创建，不是从零新增 |
| hub目录 | [DefaultHubFolderService.java:30](../../backend/services/system-setting/src/main/java/io/metersphere/system/service/DefaultHubFolderService.java#L30) | `public void onBizProjectCreated` | 已有第二条枢纽目录路径 |
| 目录名称同步 | [ProjectService.java:212](../../backend/services/project-management/src/main/java/io/metersphere/project/service/ProjectService.java#L212) | `private void synchronizeCatalogNameAfterProjectRename` | 单关联且非手工改名条件 |
| 资产分页 | [CaseAssetService.java:89](../../backend/services/case-management/src/main/java/io/metersphere/functional/asset/service/CaseAssetService.java#L89) | `public Map<String, Object> pageCatalogs` | 服务端分页，置顶必须先排序再分页 |
| 资产目录upsert | [CaseAssetService.java:626](../../backend/services/case-management/src/main/java/io/metersphere/functional/asset/service/CaseAssetService.java#L626) | `private Map<String, Object> upsertCatalog` | 按组织规范化名称匹配，与每项目独立存在差异 |
| 历史源同步 | [CaseAssetService.java:496](../../backend/services/case-management/src/main/java/io/metersphere/functional/asset/service/CaseAssetService.java#L496) | `private String syncHistoricalCaseSource` | 独立资产写入与sourceRefId映射 |
| 实时源同步 | [DefaultHubCaseSyncService.java:63](../../backend/services/case-management/src/main/java/io/metersphere/functional/hub/service/DefaultHubCaseSyncService.java#L63) | `public void syncCaseUpsert` | 物理caseId map、防环、哈希与skip |
| 实时源删除 | [DefaultHubCaseSyncService.java:111](../../backend/services/case-management/src/main/java/io/metersphere/functional/hub/service/DefaultHubCaseSyncService.java#L111) | `public void syncCaseDelete` | 软删镜像并删除map，恢复需兼容 |
| 版本治理监听 | [TestAssetPublicationListener.java:51](../../backend/services/agent-integration/src/main/java/io/metersphere/agent/service/TestAssetPublicationListener.java#L51) | `public void onFunctionalCaseChanged` | 治理/版本发布不等同目录镜像 |
| 文件导入UI | [index.vue:136](../../frontend/src/views/case-management/caseManagementFeature/components/import/index.vue#L136) | `url: '/ai/case-imports/precheck'` | 真正使用新持久任务入口 |
| 导入控制器 | [AgentCaseImportController.java:13](../../backend/services/agent-integration/src/main/java/io/metersphere/agent/controller/AgentCaseImportController.java#L13) | `@RequestMapping` | precheck/get/action现有协议 |
| 导入持久服务 | [AgentCaseImportService.java:47](../../backend/services/agent-integration/src/main/java/io/metersphere/agent/service/AgentCaseImportService.java#L47) | `public Map<String,Object> precheck` | 已有fingerprint/idempotency/items |
| 资产导入项目 | [DefaultHubCaseImportService.java:93](../../backend/services/case-management/src/main/java/io/metersphere/functional/hub/service/DefaultHubCaseImportService.java#L93) | `public DefaultHubJobResponse startImport` | 另一类任务，需结果适配 |
| 导入副作用 | [FunctionalImportEffectOutbox.java:63](../../backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalImportEffectOutbox.java#L63) | `public void drain` | 每轮5项，非CASE_SYNC分支当前视作NOTICE |
| 导入outbox迁移 | [V3.7.2_119__functional_import_effect_outbox.sql:1](../../backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_119__functional_import_effect_outbox.sql#L1) | `CREATE TABLE` | 已有表，不重复建 |
| 目录与血缘 | [V3.7.2_61__case_asset_catalog_and_lineage.sql:1](../../backend/framework/domain/src/main/resources/migration/3.7.2/ddl/V3.7.2_61__case_asset_catalog_and_lineage.sql#L1) | `CREATE TABLE IF NOT EXISTS case_asset_catalog` | 组织名唯一、多项目目录、lineage方向 |
| 树拖动接口 | [FunctionalCaseModuleController.java:52](../../backend/services/case-management/src/main/java/io/metersphere/functional/controller/FunctionalCaseModuleController.java#L52) | `@PostMapping("/move")` | 权限与owner已有 |
| 树版本移动 | [FunctionalCaseModuleService.java:139](../../backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseModuleService.java#L139) | `public void moveNode` | 项目锁/版本/循环已有 |
| 显式父级服务 | [FunctionalCaseModuleService.java:211](../../backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseModuleService.java#L211) | `public FunctionalCaseModule moveToParent` | 服务缺expectedUpdateTime契约 |
| 拖动DTO | [NodeMoveRequest.java:10](../../backend/services/system-setting/src/main/java/io/metersphere/system/dto/sdk/request/NodeMoveRequest.java#L10) | `public class NodeMoveRequest` | 不是直接父目录接口 |
| 全选父表 | [base-table.vue:679](../../frontend/src/components/pure/ms-table/base-table.vue#L679) | `const handleSelectAllChange` | 这里只发事件 |
| 全选状态所有者 | [useTable.ts:572](../../frontend/src/components/pure/ms-table/useTable.ts#L572) | `selectAllChange:` | 实际维护selectorStatus/selectedKeys/excludeKeys |
| 全选子组件 | [select-all.vue:119](../../frontend/src/components/pure/ms-table/select-all.vue#L119) | `const handleSelect =` | 本地状态需与新入口同步 |
| 计划名称列 | [planTable.vue:475](../../frontend/src/views/test-plan/testPlan/components/planTable.vue#L475) | `slotName: 'name'` | 含行内编辑，不能覆盖整个cell跳转 |
| 评审详情入口 | [reviewTable.vue:779](../../frontend/src/views/case-management/caseReview/components/index/reviewTable.vue#L779) | `function openDetail` | 复用既有详情路由 |
| 软删除 | [FunctionalCaseService.java:943](../../backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseService.java#L943) | `public void handDeleteFunctionalCase` | 执行任务保护、软删和评审事件 |
| 恢复 | [FunctionalCaseTrashService.java:65](../../backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseTrashService.java#L65) | `public void recoverVersions` | 已有RECOVER与关系恢复 |
| 评审异步重算 | [CaseReviewCaseProvider.java:58](../../backend/services/case-management/src/main/java/io/metersphere/functional/provider/CaseReviewCaseProvider.java#L58) | `public void updateCaseReview` | Async且处理失败只日志 |
| 彻底删除 | [DeleteFunctionalCaseService.java:56](../../backend/services/case-management/src/main/java/io/metersphere/functional/service/DeleteFunctionalCaseService.java#L56) | `public void deleteFunctionalCaseResource` | 会清除历史，不能声称无条件保留 |
| 计划可见性过滤 | [ExtTestPlanFunctionalCaseMapper.xml:85](../../backend/services/test-plan/src/main/java/io/metersphere/plan/mapper/ExtTestPlanFunctionalCaseMapper.xml#L85) | `AND functional_case.deleted` | 已有源deleted条件 |
| 计划创建 | [TestPlanService.java:170](../../backend/services/test-plan/src/main/java/io/metersphere/plan/service/TestPlanService.java#L170) | `public TestPlan add(` | savePlan与initPlan |
| 计划关联 | [TestPlanFunctionalCaseService.java:944](../../backend/services/test-plan/src/main/java/io/metersphere/plan/service/TestPlanFunctionalCaseService.java#L944) | `public void associateCases` | 默认collection及去重 |
| 评审创建 | [CaseReviewService.java:218](../../backend/services/case-management/src/main/java/io/metersphere/functional/service/CaseReviewService.java#L218) | `public CaseReview addCaseReview(CaseReviewRequest` | reviewer与关联初始化 |
| 评审追加 | [CaseReviewService.java:502](../../backend/services/case-management/src/main/java/io/metersphere/functional/service/CaseReviewService.java#L502) | `public void associateCase(` | 排除现有ID并建reviewer关系 |
| 评审必填 | [CaseReviewRequest.java:14](../../backend/services/case-management/src/main/java/io/metersphere/functional/request/CaseReviewRequest.java#L14) | `public class CaseReviewRequest` | module/passRule/reviewers等不能为空 |
| 模块依赖 | [pom.xml:41](../../backend/services/test-plan/pom.xml#L41) | `<artifactId>metersphere-case-management</artifactId>` | test-plan依赖case-management，避免反向依赖 |
| 隔离集成测试 | [AgentCaseImportMySqlTests.java:21](../../backend/services/agent-integration/src/test/java/io/metersphere/agent/service/AgentCaseImportMySqlTests.java#L21) | `@EnabledIfSystemProperty` | quality.mysql=true且端口13326，未执行不能算通过 |

## 本轮检查边界

仅静态读取、关联调用和迁移/字段对照。未运行构建、数据库、容器、浏览器或MCP；未修改业务代码。旧回归问题保留现场证据，不以静态推断覆盖。方案新增API、表与测试均为待实施项。
