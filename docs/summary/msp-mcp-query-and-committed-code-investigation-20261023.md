# MCP计划查询排查方案与已提交代码核查

日期：2026-10-23。范围：MCP `metersphere.test_plan.cases`内部错误，以及前轮“源码有修复、线上仍为旧行为”涉及的提交内容。**本轮为只读核查与方案编写，未修复业务代码、未重新部署。**

关联文档：[详细修复方案](msp-live-issues-detailed-remediation-plan-20261023.md)、[现场实测报告](msp-live-requirement-audit-20261023.md)。

## 1. 结论

1. **相关界面修复确实已提交并推送到用户仓库。** 实时执行`git ls-remote`，`chenqifen-miduo/metersphere`的main、chenqifen、v3.2均指向`413325302fca305de7d1338b8eebe2c9f43dbd9f`，提交日期2026-10-22 18:45:17 +0800。不是只存在于未提交工作区。
2. **已提交不等于已构建、已部署或功能完整。** 已核对删除确认路由、隐藏按钮、分页等具体diff；但部分刷新/全量同步仍存在源码缺口，不能全部归因于旧包或缓存。
3. **MCP计划查询发现首要代码疑点：校验SQL引用了不存在于仓库计划表定义的deleted字段。** 当前源码、已提交版本都存在该SQL；需线上只读DDL及日志确认，尚不能宣称已确认线上SQL异常或已修复。
4. 构建流程将前端ZIP、后端镜像分别产出；推送代码或仅更新后端，不能证明站点已经加载同版本前端。

## 2. Git与提交内容证据

### 2.1 远端分支

本轮实际执行：

```powershell
git ls-remote metersphere refs/heads/main refs/heads/chenqifen refs/heads/v3.2
git ls-remote upstream refs/heads/main refs/heads/test
git log -8 --oneline
git show --stat --oneline 413325302f -- frontend/src
git diff 413325302f -- frontend backend/services/agent-integration backend/services/test-plan
```

| 远端 | 分支 | 查询结果 |
| --- | --- | --- |
| metersphere：chenqifen-miduo/metersphere | main | `413325302fca305de7d1338b8eebe2c9f43dbd9f` |
| 同上 | chenqifen | 同一SHA |
| 同上 | v3.2 | 同一SHA |
| upstream：MiDouTech/metersphere | main | `bb835430187ada8f60b79a5a8e576ca92d766a67` |
| 同上 | test | `810058f9a79c4dab6aae490184a83376a1fb0fa8` |

两个仓库不能混称为“main”。upstream分支头不同，不足以单凭SHA判断其是否包含某提交；本轮没有拉取其最新提交进行祖先关系验证，也未确认测试部署系统实际监听哪个仓库/分支。不得据此宣称已发布到MiDouTech测试环境。

对frontend、agent-integration、test-plan的`git diff 413325302f -- ...`无差异，以下证据来自已提交代码。其他已有文档改动保持原样。

### 2.2 逐项核查

| 实测问题/需求 | 已提交代码中的实际内容 | 文件 | 判断 |
| --- | --- | --- | --- |
| LIVE-01 计划详情串数据 | planId由一次性ref改computed；新增detailSequence、项目/计划响应校验、watch重置、key及错误状态 | `frontend/src/views/test-plan/testPlan/detail/index.vue` | 有实质修复，413325302f；线上是否使用该资源待核 |
| LIVE-02 隐藏生成报告 | diff直接移除报告菜单/按钮、相关import及处理逻辑 | 同上 | 已提交移除，线上仍可见需核发布/缓存/不同页面实现 |
| LIVE-03 隐藏生成用例 | caseGenerate移除isTopMenu、实际组件，hideInMenu并重定向到用例页 | `frontend/src/router/routes/modules/caseManagement.ts` | 已提交路由改造；最终顶部菜单仍需实测 |
| LIVE-04 文档AI生成 | 删除生成入口及对应调用/依赖 | `frontend/src/views/test-asset/documents.vue` | 已提交删除，不等于删掉整个文档功能 |
| LIVE-05 资产四项控件 | 删除页面对象、业务流、环境下拉、AI可执行性检查及相关状态/调用 | `frontend/src/views/test-asset/cases.vue` | 已提交删除；同时删除AI可执行性列 |
| LIVE-07 URL前置校验 | 新增platformConfiguration.ts；表单提交调用platformUrl，检查Origin包含base Origin | `frontend/src/utils/platformConfiguration.ts`、`frontend/src/views/agent/environment-profile.vue` | 已提交校验；catch仍调用Message.error，重复提示不能只靠重新部署判定已解决 |
| LIVE-08 分页 | showPageSize=true；pageSizeOptions=[10,20,50,100]，默认10 | `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`及通用表格 | 已提交配置；组件传递、最终渲染、偏好持久化要分别验收 |
| LIVE-09 批量按钮 | 基础表格showBatchAction依据selectable/actionConfig；动作禁用依据disabled或选择数0 | `frontend/src/components/pure/ms-table/base-table.vue`、`batchAction.vue` | 已提交常驻/禁用相关改造；不能以只看子组件替代实际页面验证 |
| LIVE-13 确认页404 | 新增/case-deletion/confirm/:id路由，映射真实确认组件 | `frontend/src/router/routes/modules/caseDeletion.ts`、`frontend/src/views/execution/case-delete-confirm.vue` | 两个文件均在提交树内；还需核权限过滤、basePath及部署资源 |
| LIVE-14 跨项目旧详情 | 用例列表有项目watch，但主要清理导出上下文、列和数据获取；不等同于完整详情身份状态重置 | `caseManagementFeature/components/caseTable.vue`及详情父子组件 | 有相关改动，不能断言跨项目详情隔离已完整实现 |
| SYNC-01 计划进入刷新 | planTable.onActivated仅无数据才fetchData；已有数据不按该钩子刷新 | `frontend/src/views/test-plan/testPlan/components/planTable.vue` | 当前提交仍存在需求缺口 |
| SYNC-02/03 全量同步 | syncPlanCases更新结果/时间并调用缺陷补齐；没有全域自动一致性模型 | `TestPlanFunctionalCaseService`、`TestPlanDefectSyncService`及mapper | 是部分同步，尚不满足新要求 |
| LIVE-06 原生MCP幂等 | AgentProjectCreateRequest无requestId，工具使用DTO schema；通用MCP写入要求幂等键 | AgentProjectCreateRequest、BuiltinAgentMcpToolConfig、AgentMcpStreamableService | 已提交源码仍含契约缺口，不能归因于前端发布 |
| LIVE-10 评论冲突 | 抛出“已变更”，映射按“已被更新”匹配 | FunctionalCaseCommentAccess、AgentSafeErrorMapper | 已提交源码可确认的不一致 |
| LIVE-11 字段类型 | 后端Map<String,String>；公布schema对值类型限制不足 | AgentBugCreateRequest及DTO schema生成 | 已提交源码仍需修 |
| LIVE-12 计划MCP查询 | search先调用access.plan，其SQL带deleted=0 | AgentBusinessAccess、AgentFunctionalCaseSearchService | 已提交代码疑点，见第3节 |

上述矩阵表示“提交里有何内容”，不是把每一行重新标为验收通过。

### 2.3 构建链路核查

仓库`.github/workflows/cnb-build.yml`：

- push自动触发分支为main、v3.x；也支持workflow_dispatch。仅push chenqifen或v3.2本身不会按该配置自动触发。
- frontend job执行`.deploy/frontend.json`指定的`pnpm run build`，将frontend/dist打成ZIP，上传workflow artifact和CNB静态仓库。
- backend job使用`-DskipAntRunForJenkins=true -pl '!frontend'`构建，按`.deploy/services.json`使用`Dockerfile.backend`产出后端镜像。
- 两个job完成后发布版本信息。该文件的实际步骤没有登录测试主机、替换站点静态目录、重启测试服务的部署动作；不能把“Build And Store Artifacts”视为测试站点部署完成。
- 根Dockerfile还有复制`backend/app/src/main/resources/static`的路径；backend/app/pom.xml存在从frontend/dist复制静态资源的Maven步骤，而CI后端命令跳过该步骤。应按实际使用Dockerfile/部署拓扑判断，不混用前后端分离和内嵌静态资源的发布方式。

下一步发布对账应取得：Actions运行的仓库/ref/SHA、前端ZIP版本/摘要、后端镜像digest、测试主机实际前端目录/容器挂载、反向代理路由和HTML引用资源。使用同一构建的manifest对比；不同构建参数产生不同哈希，本地/线上哈希不同不是单独定罪依据。

## 3. MCP异常的首要排查项

### 3.1 现场事实

已测项目`1704667279923322880`，计划`1704670698717290496`。网页REST分页200且有一条用例；原生MCP工具按发布schema传current/pageSize/includeSteps=false，仍返回`AI_EXECUTION_INTERNAL_ERROR`。

关键traceId：`04dd1f96-bf0c-44d4-a673-1e7db4f5d539`。证据：`outputs/msp-live-audit-20261023/063-plan-cases-correct-schema.json`及`066-plan-page-rest.json`。故障发生时间以这些响应文件、服务器日志时区对齐，不能拿客户端当前时间检索历史故障。

### 3.2 已确认的源码矛盾

`AgentFunctionalCaseSearchService.search()`在计划查询前调用：

```java
if (StringUtils.isNotBlank(request.getTestPlanId())) {
    access.plan(projectId, request.getTestPlanId());
}
```

`AgentBusinessAccess.plan()`执行：

```sql
SELECT COUNT(*) FROM test_plan
WHERE id=? AND project_id=? AND deleted=0
```

但仓库：

1. `backend/framework/domain/src/main/resources/migration/3.0.0/ddl/V3.0.0_3__test_plan.sql`中的test_plan建表没有deleted列。
2. `backend/framework/domain/src/main/java/io/metersphere/plan/domain/TestPlan.java`及字段枚举没有deleted属性。
3. `TestPlanService`删除计划采用deleteByPrimaryKey/deleteByExample；归档使用status。
4. 此SQL同样存在于远端分支头413325302f对应提交。

若线上schema也没有deleted，这条校验将先于计划分页抛SQL异常，经过安全错误映射后表现为内部错误；REST正常并不矛盾，因为REST计划分页不走此AgentBusinessAccess校验。该推断与现象高度吻合，但**未读取线上DDL/原异常栈前仍标记为“首要候选”，不是已确认线上根因**。

### 3.3 第一轮只读确认：先DDL和日志，再查分页

由能访问测试数据库的执行环境运行以下只读SQL，不修改表、不重启服务：

```sql
SELECT DATABASE() AS db_name;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='test_plan'
ORDER BY ORDINAL_POSITION;

SELECT id, project_id, type, status
FROM test_plan
WHERE id='1704670698717290496'
  AND project_id='1704667279923322880';
```

必须确认连接的是测试应用实际数据库。结果只记录该隔离计划，不导出业务表。随后在应用日志按上述traceId查完整cause链，寻找发生位置是否为AgentBusinessAccess.plan，以及是否存在MySQL1054/SQLState42S22、unknown column deleted等信息；这些是预计特征，不是本轮取得的日志。

分支判断：

| DDL/日志结果 | 下一步 |
| --- | --- |
| 无deleted且异常发生在该SQL | 确认根因，按3.4修复并集成验证 |
| 有deleted | 核对与仓库迁移的差异及实际应用版本，继续按3.5定位，不能盲删条件 |
| 无deleted但实际异常不是此处 | 记录独立代码缺陷，继续查该请求真正失败阶段 |
| 没有trace对应日志 | 单次重放最小请求拿新traceId，确认请求落到的副本、日志位置与时区 |

### 3.4 若首要候选确认后的修复方向

优先复用计划Mapper/领域读取，按计划ID、项目归属、类型及业务状态校验。若保持SQL，仅使用真实schema字段，删除错误deleted谓词；不能为了迁就错误查询给test_plan临时补deleted列。

同时保留Token项目范围和当前用户权限，核对计划读取的权限常量。不能把整个access.plan删掉或忽略SQL异常后继续查询。计划不存在/跨项目应返回安全拒绝或资源不存在，归档计划是否可读遵循原读取契约，写入另行禁止。

影响面：`AgentFunctionalCaseSearchService.search()`带testPlanId，以及`getById(...,testPlanId)`均调用该校验。除test_plan.cases外，还要回归functional.search/get的计划上下文；不推断所有MCP工具都失败。

## 4. 若首要候选未确认，按阶段继续排查

### 4.1 最小复现与对照

MCP调用体（使用现有已授权测试Token；不要在脚本或报告写入实际Token）：

```json
{
  "jsonrpc": "2.0",
  "id": "audit-plan-cases",
  "method": "tools/call",
  "params": {
    "name": "metersphere.test_plan.cases",
    "arguments": {
      "projectId": "1704667279923322880",
      "testPlanId": "1704670698717290496",
      "current": 1,
      "pageSize": 10,
      "includeSteps": false
    }
  }
}
```

POST到`https://msp.ebcone.net/api/mcp`。这是只读工具，无需幂等键；不要把原生创建的幂等缺陷混入查询排查。网页REST使用当前网页会话和组织/项目头，POST `/api/test-plan/functional/case/page`传同一projectId/testPlanId/current/pageSize。分别保存HTTP状态和信封内JSON-RPC错误，不以HTTP200推导查询成功。

按最少请求依次变更：

1. 相同项目functional.search，不带testPlanId；与带计划上下文对比。
2. test_plan.cases、includeSteps=false。
3. 上一步成功后再includeSteps=true；只有此步失败才重点查步骤补齐。
4. 已授权空计划、非空计划、第二页；不存在/跨项目计划做负向校验。

从前轮证据看，false已失败，因此附件图片解码或步骤补齐不是第一优先方向。

### 4.2 分阶段日志点

| 阶段 | 检查位置 | 需记录的安全信息 |
| --- | --- | --- |
| A | MCP注册表及tool handler | 工具名、schema版本、后端构建SHA |
| B | AgentTestPlanQueryService.cases | 规范化projectId/testPlanId/current/pageSize/includeSteps |
| C | resolveProjectId、access.project、access.plan | 授权阶段名、组织/项目是否一致、权限结果；不记录Token/Session |
| D | AgentQueryResolver、ModuleTreeMatcher | 解析后的过滤项数量、是否命中模块、异常阶段 |
| E | searchInTestPlan/PageHelper | 真实执行的目标SQL与count SQL、绑定参数、数据库错误cause、行数/总数 |
| F | getFunctionalCasePage/handleCustomFields | 自定义字段、缺陷聚合、用户名、模块补齐哪一阶段失败 |
| G | AgentCaseSchemaMapper | caseId/testPlanCaseId、字段类型与null处理；不输出用例正文全集 |
| H | enrichWithSteps | 仅true路径，读取源定义/版本的失败对象 |
| I | 响应编码 | 实际DTO与outputSchema、枚举/集合序列化 |

所有阶段用同一traceId关联，原始异常只进受控日志，用户仍收到安全消息。不要长期开启全局DEBUG；临时日志只针对该工具/隔离项目，完成后恢复。

分页重点：PageHelper.startPage至目标mapper之间是否插入其他查询、count SQL是否正确、排序列是否真实存在、线程本地分页是否finally清理。此为备选排查，不是预判已确认根因。

## 5. 验证与测试补齐

当前已有`AgentFunctionalCaseSearchServiceTests`、`AgentCaseSchemaMapperTests`和`AgentLibrarySearchMySqlTests`。后者实际覆盖的是用例库mapper及临时functional_case等表，不是计划归属校验SQL；不能用它通过证明test_plan.deleted查询正确。

需要新增的集成验收：

- 使用仓库迁移得到的真实test_plan结构，执行AgentBusinessAccess.plan；不得在测试夹具中人为补deleted让错误SQL通过。
- 真实MCP tools/call→授权→计划分页→DTO全链路，不能mock掉access.plan/JdbcTemplate。
- 同用户、同计划，MCP与REST比较总数、caseId/testPlanCaseId、顺序、状态、版本及步骤。
- 空计划/多页/includeSteps开关/无权限/不存在/归档读取及写入限制；撤销Token后立即失败。
- functional.search/get带计划上下文回归；修复后响应不含SQL堆栈。

先执行定向测试和编译，再在一套环境串行完成迁移、启动、健康及网页/MCP冒烟。新增测试尚未编写，因此本报告不编造测试类名称或通过结果。本轮没有启动数据库、Docker、构建和压测。

## 6. 实施与交付状态

| 工作 | 状态 |
| --- | --- |
| 已提交内容核查、远端分支确认 | 已完成，有git show/ls-tree/diff/ls-remote证据 |
| 构建链路静态核查 | 已完成；尚未取得实际Actions运行及测试主机部署清单 |
| MCP首要代码疑点与可执行排查步骤 | 已整理；线上根因待DDL/日志确认 |
| 业务修复、测试、发布验收 | 本轮未执行，功能状态仍部分完成 |

本轮新增本文件，并更新详细方案中的MCP排查入口；未修改业务源码，也未覆盖其他已有工作区文档改动。
