# MSP CI 70 项失败、10 项错误：逐项原因、影响与修复方案

日期：2026-10-29。状态：实施与复验中。第 1～5 节保留原始 80 条失败分析；第 6 节记录实际定位、修复及验证结果。尚未通过全部验收门槛，不是完成报告。

## 1. 证据范围与统计含义

- 原始运行：[40698030451](https://github.com/chenqifen-miduo/metersphere/actions/runs/40698030451)，受验代码 `baa75c80827a126261dad9266a9713e169309dce`。
- 原始证据：该运行 `backend-test-reports` 中的 Surefire XML、testcase 的 failure/error、system-out HTTP 响应及服务器异常栈。此前已下载至本机忽略目录 `.codex-tmp/weekly-fixes-20261028/ci-last-reports`；未将原始凭据、完整响应或日志堆栈写入本文。
- [逐条结构化清单及测试源码行号](../testing/msp-ci-failures-20261029/inventory.json)。
- 初始分析基线 `fd038b968c` 相对于该受验版本，仅修改离线 Python 诊断及文档；随后实施的提交和结果见第 6 节。
- 已执行 541 项：461 成功、70 个 **failure**、10 个 **error**。failure 是断言不满足；error 是未被测试预期处理的异常，**不等于 HTTP 500**。
- 统计仅覆盖此次实际进入的模块；system-setting 失败后，下游项目/API/缺陷/用例/计划/Agent 等 reactor 模块没有继续运行。Surefire 的 `skipped=0` 不能解释为下游均已验证。
- Docker 基础镜像 CDN 超时是另外一个 CI 阶段的失败，不是这 80 条记录的原因。
- 测试环境故障用例 100025 已于 2026-10-29 按用户授权移入回收站；它不在这 80 条系统设置测试记录中，删除该数据不会消除本表失败。

## 2. 根因归类总表（精确覆盖 80 条）

|编号|主题|failure|error|原因与结论|主要影响|修复优先级|
|---|---|---:|---:|---|---|---|
|S01|业务错误 HTTP 契约|59|0|旧断言期待 5xx，实际 HTTP 422、业务码 100422；每条具体业务拒绝见附录。不能据此认定所有 422 的语义都已正确|测试误报、接口/客户端错误语义可能不统一|P2，涉及权限拒绝的项目需优先复核|
|S02|成员角色绑定幂等|2|0|两条成功路径均实际 HTTP 500；重复插入命中 `(user_id,role_id,source_id)` 唯一约束，已由服务器栈和代码确认|重复添加/重试可能使整个成员添加事务失败|P1 产品缺陷|
|S03|角色部分更新兼容|1|0|旧 DTO 允许更新时不传 type，适配层却把 null 与原类型直接比较，误判为修改角色类型|仅更新部分字段的旧客户端或 API 调用被拒绝|P1 产品兼容缺陷|
|S04|旧/新角色写权限不一致|0|2|测试授予旧 `SYSTEM_USER_ROLE` 写权限，接口要求新 `SYSTEM_PERMISSION_CONTROL` 写权限；Vue 旧页面仍对两者取 OR，存在入口与接口不一致|旧权限用户可能看到按钮却收到 403|P1 前后端契约缺陷，同时需更新权限测试|
|S05|新项目默认模块|2|0|旧断言期待 moduleSetting=null；代码已明确默认为用例、缺陷、计划、接口四模块|主要是断言过期；错误“修复”为 null 会重新引入模块配置风险|P2 测试契约|
|S06|缺陷工作流迁移及连锁异常|3|4|旧写入口已主动关闭，新增/排序被 422 拒绝；另有一个流转目标列表顺序差异；新增未成功引出 4 个测试静态变量空指针|旧接口写入停止；测试互相依赖造成误判，不能证明新流程控制功能已正常|P1/P2：补新入口集成验证，清理旧链式测试|
|S07|权限树与数据库权限集合不一致|1|0|管理员权限集合未被返回树的测试遍历完全消费。差异已确认，具体缺失 ID 尚未提取；单模块权限定义资源范围/遗留 ID 是待核验候选|可能只是测试加载范围，也可能是权限定义漏项；未定位前不能排除产品影响|P1 定位，禁止直接删库中权限|
|S08|组织架构正向测试作用域错误|0|3|权限测试用户只绑定默认组织 100001，却查询 sys_default_organization_3；实际被拒绝符合组织边界|测试正向场景无法进入业务断言；不应以放宽跨组织权限解决|P2 测试夹具|
|S09|必备角色保护错误码变化|1|0|管理员角色删除仍被正确阻止，但由旧 100003 变为通用 100422|调用方按旧业务码分支时有兼容风险；没有“管理员被删除”的证据|P2 稳定错误契约|
|S10|个人 API Key 列表授权模型|0|1|列表按当前用户过滤，控制器没有旧角色权限注解；通用权限探针撤销旧权限后仍可读本人列表，因此抛错|不是已经证实的越权；必须验证本人、他人、匿名三条边界|P1 授权验收，P2 测试模型|
|S11|本地执行配置数量连锁失败|1|0|testAdd 在旧 5xx 断言处提前退出，后续第二条 UI 配置未创建，testGet 仍要求两条|测试依赖执行顺序；没有证据证明 GET 丢失已落库配置|P2 测试隔离|
|合计||70|10||||

这意味着不能把 80 条一律归为旧测试。S02、S03、S04 已有产品代码层面的具体问题；S07 仍需补差集证据。反过来，也不能把 10 个 error 说成 10 个服务器异常：其中 4 个空指针发生在测试代码，另外 6 个由通用权限探针主动抛出。

## 3. 具体修复方案、修改范围及通过标准

下文 SYSTEM=`backend/services/system-setting/src/main/java/io/metersphere/system`；TEST=`backend/services/system-setting/src/test`。除特别说明外，均为拟修改范围，不是已经实施。

### S01：业务错误协议与负向测试

**原因证据**：`SYSTEM/controller/handler/RestControllerExceptionHandler.java` 对安全业务 MSException 返回 422；无专用业务枚举时为 100422。59 条失败记录的实际响应均为这一协议，例如日期区间非法、组织不存在、至少保留一个管理员、旧密码错误等。部分名称带 Success 的测试也包含负向分支，不能据方法名判定正常功能失败。

**影响**：当前 CI 无法区分预期业务拒绝与真实服务器故障；老客户端若只处理 500 或旧 envelope，也可能提示不准确。权限拒绝/资源不存在/重复数据一概用 422 是否合理，应分别核定，不能把实际返回值直接当验收标准。

**修改**：

1. 建立端点、负向条件、HTTP、业务码、messageKey 的契约表。参数绑定/格式 400、未认证 401、权限/所有权拒绝 403、不存在 404、冲突 409、其他业务规则 422、未知服务异常 500 分别核定；既有兼容码需保留或明确迁移。
2. TEST 中相应 ControllerTests 按契约修改具体负向断言，不全局替换所有 is5xxServerError，不改成宽泛 is4xxClientError。
3. 增加共享断言：准确 HTTP + 业务码/messageKey + 非空 requestId/traceId + 安全提示 + 数据未变化。保留真正未知错误返回 500 的负向测试。
4. 核对前端统一错误解析与 Node/MCP 适配的安全消息、关联 ID 展示；只有发现不一致才修改对应转换层。

**通过**：59 条各自命中预期业务原因；未授权/不存在/重复不互相混淆；未知数据库错误不能被新的断言当作“正常业务拒绝”。

### S02：两个真实 HTTP 500，修复旧成员写入口的幂等性

**精确路径**：

- `OrganizationProjectControllerTests.testAddProjectMember` → `/organization/project/add-members` → `OrganizationProjectService.orgAddProjectMember` → `CommonProjectService.addProjectUser` → `UserRoleRelationMapper.batchInsert`，命中 `uk_user_role_relation_user_role_source`。
- `OrganizationUserRoleControllerTests.testOrganizationUserRoleDeleteOnlyMemberSuccess` 在执行删除角色之前，调用 `/user/role/organization/add-member` → `OrganizationUserRoleService.addMember` 的准备步骤即重复绑定失败；失败不在删除逻辑本身。
- 唯一约束由 `V3.7.2_56__user_role_relation_unique.sql` 引入，旧 addProjectUser/addMember 仍为无条件插入。不能通过移除唯一索引解决。

**影响**：用户重复点击、请求重试、同一批部分成员已存在、并发添加，都可能返回 500；事务内本应新增的其他成员也可能一起回滚。此处是“数据库约束补齐，而旧写入口未同步改造”的漏改，不是单纯测试过期。

**修改范围与实现**：

1. 在 SYSTEM 的共享角色关系持久化层补“按自然业务键确保关系存在”的方法，统一复用；不要在两个 Controller 里各补一套去重。
2. 写入前重新验证用户、角色、项目/组织、调用者权限；输入用户/角色去重；批量查询已有三元组，补齐缺失项。已有项的 id、创建人、创建时间保持。
3. 保留数据库唯一约束处理并发竞争。“先查后插”单独使用不够；插入遇到冲突时，只有确认是同一业务键、同一合法组织的已存在关系才按幂等成功处理，其余错误继续抛出。禁止笼统 catch 后返回成功、REPLACE 或无差别 INSERT IGNORE。
4. 成员绑定、必要的默认角色补充、日志和会话权限刷新保持事务一致；日志仅记录本次真实新增，不因回放重复记录。审计其他 UserRoleRelation 写入口和默认角色回退入口是否也绕过共享方法。
5. 不修改已经执行过的 V56；只有确实需要额外数据校验/修复时新增迁移，并先输出问题清单。

**通过**：同一请求连续两次、两客户端并发、重叠批次、批内重复均不重复关系、不重复日志；已存在+新成员混合批次可按约定完成；撤权/跨组织/非法角色仍拒绝；模拟数据库失败时原子回滚。用真实 MySQL 验证唯一约束竞争，不能只 Mock mapper 返回成功。

### S03：角色部分更新不能被当成改变类型

**证据**：`UserRoleUpdateRequest.type` 仅在 Created 组要求非空，历史 update 使用选择性更新；失败测试只提交 id。`PermissionControlService.saveRoleMetadata` 将空 type 转交完整 RoleSaveRequest，`saveRole` 直接比较原类型与 null，返回“角色类型创建后不可修改”。

**修改范围**：`PermissionControlService.saveRoleMetadata/saveRole`、旧 DTO 的字段存在性语义、GlobalUserRoleControllerTests、PermissionControlServiceTests。新完整保存命令仍保留完整校验，不因兼容旧接口而放宽。

**实现**：旧更新适配器加载原角色，未提供的 type/name/description/enabled 按旧选择性更新语义保留；显式提交其他 type 继续拒绝。特别检查旧 DTO `enabled=true` 默认值，避免“没提交启用字段”意外启用停用角色。元数据更新不能从 UI 权限树重建权限列表后覆盖原权限；元数据与显式权限替换应共用安全校验，但区分写入意图。保留权限控制、必备角色保护、事务审计和会话刷新。

**通过**：仅 id 为无操作成功；仅改名称/描述不改类型/启用/权限；停用角色未提供 enabled 时仍停用；显式改类型拒绝且零写入；完整新保存接口的必填及权限校验不变。

### S04：前后端角色写权限按操作对齐

**证据**：GlobalUserRoleController 的新增、更新、权限保存、删除采用新的 SYSTEM_PERMISSION_CONTROL 写权限；测试探针仍授予 SYSTEM_USER_ROLE 旧写权限。`frontend/src/views/setting/system/userGroup/systemUserGroup.vue` 的新增、编辑、权限保存入口仍允许新旧权限 OR，因此有真实“可见但不能操作”的风险。

**方案**：以当前集中权限控制策略为基准，为“角色元数据、角色权限、成员关系、读取”分别列权限矩阵。角色写按钮与接口统一用新权限；只读接口既有 OR 兼容按设计保留。成员关联接口当前仍有独立的 OR 规则，不可把所有 update-permission 一刀切替换。若需要迁移历史授权，必须输出影响清单并按明确策略映射，不能为使旧测试通过而直接重开旧写权限。

**修改范围**：上述 Vue、相应业务组件权限参数、GlobalUserRoleControllerTests/GlobalUserRoleRelationControllerTests、相关 API 权限注解和配置定义（仅确有不一致时修改）。

**通过**：新权限、仅旧权限、无权限三种用户分别验证按钮与 API；旧权限不被无意提升；无入口却被允许写、可见却必然 403 两种不一致均消除。

### S05：项目默认模块

`CommonProjectService` 已明确将未指定模块的项目设为 `[caseManagement,bugManagement,testPlan,apiTest]`，两个旧测试仍 assertNull。修改 SystemProjectControllerTests、OrganizationProjectControllerTests，准确断言四个默认模块及 DTO/落库/再次读取一致；另测显式模块配置、空列表语义、创建人管理员关系。不要把产品改回 null，也不要只把断言改成 notNull。无需本项数据迁移。

### S06：工作流旧入口退役与测试链式依赖

**证据**：OrganizationStatusFlowSettingController 对 BUG 场景主动抛出“已迁移至系统设置/权限控制/流程控制，旧入口不再允许写入”。新增、排序两条期待成功的测试因此失败；addStatusItem/anotherAddStatusItem 未赋值引起后续 4 个测试代码空指针。另一条失败只显示相同三个目标状态的排列不同。

**修改**：旧写接口测试改为验证“明确拒绝且状态项/流转/定义均未变化”，而不是恢复已退役写入口；将新增/编辑/排序/删除的成功业务覆盖移到 PermissionControlController 当前工作流入口，验证权限和事务。各测试独立准备数据，不依赖另一个 Test 成功后留下 static 变量。为非 BUG 场景保留仍受支持的旧能力回归。

实际新链路为 `/permission-control/flow/add` → `/flow/{flowId}/designer` 保存节点/流转 → `/flow/{flowId}/validate` → `/flow/{flowId}/publish?expectedVersion=...` → `/flow/{flowId}/activate`；流程元数据更新/删除分别为 `/flow/update`、`/flow/delete`，路径均带 `/permission-control` 前缀。前端调用位于 `frontend/src/api/modules/setting/permissionControl.ts`。应覆盖草稿节点编辑、发布版本冲突、启用及被引用流程禁止误删，不把旧状态项 CRUD 与新流程定义 CRUD 机械视为同一接口。

对于目标状态顺序，先核定是否有排序契约：若仅表达允许流转集合，精确比较集合和数量、保证无重复；若 UI 依赖顺序，则在后端查询/组装中明确稳定排序并补排序测试。不得仅观察当前结果后调整枚举顺序。

**通过**：新入口完成完整 CRUD 和流转权限验证；旧 BUG 写接口稳定拒绝，其他支持场景不回归；任一测试单独运行或随机顺序运行不因 static 变量报错；流转集合无增删、排序符合明确契约。

### S07：权限树集合差异，仍需补证据

失败点为 GlobalUserRoleControllerTests.getPermissionSetting 的“数据库权限已全部在返回树中消费”断言。CI 该次响应仅有系统、我的设置、组织三个根；PermissionConfig 从运行时 classpath 的 permission.json 合并定义，测试为独立 SystemApplication。尚未获取断言剩余的 permissionIds，故不能断言只是测试问题或权限真实丢失。

**定位/修复范围**：PermissionConfig、PermissionCache、BaseUserRoleService/GlobalUserRoleService、permission.json、种子权限迁移及测试加载配置。

1. 以只读诊断输出 DB 权限 ID 集合、递归权限树 ID 集合及双向差集，仅记 ID 和定义来源，不记用户凭据。
2. 同时记录加载到的 permission.json URL/模块身份；对比独立 system 模块与完整 app。若仅模块依赖不齐，修测试资源范围或将全模块断言移到完整 app 集成测试，不向 system 模块引入循环依赖。
3. 若完整 app 也缺定义，补真实权限定义、校验唯一 ID/层级/绑定；若是遗留 ID，明确迁移/兼容映射，禁止为让测试通过直接删除授权或默认勾选所有权限。
4. 核验测试当前只遍历两级是否遗漏更深层权限；递归遍历后仍需核对完整集合。

**通过**：每个差集 ID 有明确归属及处理结果；完整 app 定义与落库权限语义一致；保存角色不静默撤销未呈现在旧树中的合法权限。未完成此诊断前本项保持待定位。

### S08：组织架构测试夹具必须有正确作用域

`init_permission_test.sql` 把 ORGANIZATION 权限用户只关联默认组织 100001；3 条 OrgStructureMemberControllerTests 查询 sys_default_organization_3。OrgStructureAccessService 对成员所属组织再次校验，不能只授予一个权限位就绕过组织归属。

**修改**：在专属测试夹具中准备属于目标组织的非管理员用户及角色关系，使用 `SessionConstants.CURRENT_ORGANIZATION`（ORGANIZATION）对应目标上下文并刷新真实会话。正向测试结束精确清理；保留“只有 100001 成员资格却查组织 3”返回 403 的负向用例，以及无权限/匿名/跨组织成员详情测试。为避免影响成员计数，优先使用测试临时绑定并在统计断言前清理，或显式构建独立组织数据集。

**通过**：有权限且属于目标组织可读；仅有权限但不属于该组织仍拒绝；脱敏和成员计数断言实际执行并通过；不能修改产品的组织校验来迁就错误夹具。

### S09：必备角色保护保留，业务码稳定

`PermissionControlService.deleteRole` 先命中必备角色集合，以字符串 MSException 返回 100422；旧测试删除 admin 时期待 `INTERNAL_USER_ROLE_PERMISSION=100003`。正确行为仍是禁止删除。

**修改**：统一旧/新删除入口的保护业务枚举。推荐对已有内置角色保留 100003 兼容语义；新增必备角色规则若需独立业务码，先分配不冲突枚举并更新前后端契约，不能依赖中文提示或通用 100422 区分。验证管理员、其他必备角色、自定义角色、最后管理员、跨作用域五类，拒绝场景必须零删除、零权限关系丢失。

### S10：个人 API Key 列表不是全局角色管理接口

UserApiKeysController.list 没有旧角色权限注解，服务按 SessionUtils.getUserId → create_user 过滤。通用探针把“撤销旧权限后非 403”解释为缺少权限，因此抛出 error；该记录本身不能证明返回了别人的 Key。

**方案**：按当前“本人 API Key 自助管理”模型改测试：登录 A 只能读 A、B 只能读 B、匿名 401；请求参数/伪造归属不能越权；对他人的删除/更新/启停必须拒绝并保持数据。若需求明确要求管理开关，则前端入口及全部 API 统一加入新权限，并定义历史用户影响；不要只为通过旧探针给 list 随意补一个旧注解。本项不等同于 Agent Token 列表，避免修错模块。

### S11：本地配置测试去除跨方法依赖

testAdd 在“配置重复”分支得到正确业务拒绝后，旧 5xx 断言失败，后面的 UI 配置创建没有执行；testGet 却依赖此前已创建 API/UI 两条，得到 1 而非 2。这是 S01 的一条下游测试失败，不是第二个已证实的产品数据丢失问题。

**修改**：testGet 独立通过正式接口/明确夹具创建其所需两种配置，使用专属用户/标识并清理，断言两种类型及内容，而非依赖全库固定 count=2。保留重复类型拒绝、本人隔离、空字段、启停回归。修 S01 后还必须单独验证本项，不能认为依赖自然消失即完成。

## 4. 实施顺序、验收门槛及已有方案的缺口

1. 先修 S02 的真实 500、S03 的部分更新、S04 的按钮/接口权限不一致，补相关真实 MySQL/授权集成测试；三项不能只通过调整断言关闭。
2. 完成 S07 权限 ID 差集诊断；未定位之前不删权限、不批量赋权。
3. 按明确契约修 S01、S05、S06、S08～S11 的测试和必要产品协议；避免一个根因触发多条链式失败。
4. 分组串行回归，再执行完整 `./mvnw -B -pl backend/app -am -Dquality.mysql=true -Dapi.version=1.44 -DskipTests=false package`。若 system-setting 再次失败，必须明确下游未跑，不能只重报同一统计而忽略阻断。
5. system-setting 通过后补齐下游 Case/Plan/Agent/MCP 集成验证；测试通过不替代镜像构建、完整 app 健康、UI/E2E。基础镜像网络问题单独处理，不替换基础环境冒充生产镜像通过。

**低资源验证**：使用既有 MySQL 的随机专属测试 schema、串行用例和有界 JVM；不在本机同时启动新 Docker/应用栈，不重跑未受影响的前端构建。各测试必须自行清理、不能写测试环境业务项目或生产库。

**示例定向命令**（拟修后运行；本轮未执行，不代表已通过）：

```bash
./mvnw -B -pl backend/services/system-setting -am \
  -Dtest=OrganizationProjectControllerTests,OrganizationUserRoleControllerTests,GlobalUserRoleControllerTests,PermissionControlServiceTests \
  -Dsurefire.failIfNoSpecifiedTests=false -Dapi.version=1.44 test
```

需真实 Docker/Testcontainers、MySQL/Redis/MinIO/MockServer 等项目测试依赖，优先远端 CI；不能把缺依赖的本地执行计通过。新的并发和隔离测试创建后应纳入对应命令及完整套件。

前一交付追踪文档记录了“未通过”和主要主题，但没有将全部 80 条逐项定因；本文件补齐记录清单、代码证据、影响及待实施方案。S07 最终根因仍未锁定，不能声称所有原因已经查明。

## 5. 逐条记录清单

以下 ID 为本报告编号；F 对应 Surefire failure，E 对应 Surefire error。每条“方案”引用上述 S 编号，包含修改范围、影响及通过标准；业务提示来自该 testcase 最后一次 HTTP 响应，未把技术日志堆栈直接暴露给终端用户。

### 5.1 70 条 failure

|编号|测试类.方法|具体观测与直接原因|方案及影响|
|---|---|---|---|
|F001|`GlobalUserRoleControllerTests.update`|仅 id 更新期待 200，实际 422：角色类型创建后不可修改|S03|
|F002|`GlobalUserRoleControllerTests.getPermissionSetting`|数据库权限集合未被返回权限树全部覆盖；剩余 ID 待提取|S07|
|F003|`GlobalUserRoleControllerTests.delete`|删除内置 admin 被拒绝；期待业务码 100003，实际 100422|S09|
|F004|`OperationLogControllerTests.testGetOperationLogParamsError`|期待 5xx；实际 422 / 100422：开始日期必须小于结束日期|S01|
|F005|`OrganizationControllerTests.addMemberByOrgSuccess`|期待 5xx；实际 422 / 100422：组织不存在|S01|
|F006|`OrganizationControllerTests.addMemberByOrgError`|期待 5xx；实际 422 / 100422：用户不能为空|S01|
|F007|`OrganizationControllerTests.updateOrgMemberError`|期待 5xx；实际 422 / 100422：用户不存在|S01|
|F008|`OrganizationControllerTests.updateOrgMemberToRoleError`|期待 5xx；实际 422 / 100422：用户不能为空|S01|
|F009|`OrganizationControllerTests.addOrgMemberToProjectError`|期待 5xx；实际 422 / 100422：用户不能为空|S01|
|F010|`OrganizationControllerTests.removeOrgMemberSuccess`|期待 5xx；实际 422 / 100422：至少保留一个管理员|S01|
|F011|`OrganizationControllerTests.removeOrgMemberError`|期待 5xx；实际 422 / 100422：组织不存在|S01|
|F012|`OrganizationControllerTests.getProjectListByOrgError`|期待 5xx；实际 422 / 100422：组织不存在|S01|
|F013|`OrganizationControllerTests.getUserRoleListByOrgError`|期待 5xx；实际 422 / 100422：组织不存在|S01|
|F014|`OrganizationControllerTests.getNotExistUserListByOrgError`|期待 5xx；实际 422 / 100422：组织不存在|S01|
|F015|`OrganizationProjectControllerTests.testAddProjectSuccess`|期待 moduleSetting 为 null；实际为用例/缺陷/计划/接口四个默认模块|S05|
|F016|`OrganizationProjectControllerTests.testAddProjectError`|期待 5xx；实际 422 / 100422：项目名称已存在|S01|
|F017|`OrganizationProjectControllerTests.testUpdateProjectError`|期待 5xx；实际 422 / 100422：项目不存在|S01|
|F018|`OrganizationProjectControllerTests.testDeleteProjectError`|期待 5xx；实际 422 / 100422：项目不存在|S01|
|F019|`OrganizationProjectControllerTests.testRevokeProjectError`|期待 5xx；实际 422 / 100422：项目不存在|S01|
|F020|`OrganizationProjectControllerTests.testAddProjectMember`|期待 200；实际 500，成员角色关系重复插入触发唯一约束|S02|
|F021|`OrganizationProjectControllerTests.testAddProjectMemberError`|期待 5xx；实际 422 / 100422：用户不存在|S01|
|F022|`OrganizationProjectControllerTests.testRemoveProjectMemberError`|期待 5xx；实际 422 / 100422：用户不存在|S01|
|F023|`OrganizationProjectControllerTests.enableError`|期待 5xx；实际 422 / 100422：项目不存在|S01|
|F024|`OrganizationProjectControllerTests.disableError`|期待 5xx；实际 422 / 100422：项目不存在|S01|
|F025|`OrganizationProjectControllerTests.testGetAdminList`|期待 5xx；实际 422 / 100422：组织不存在|S01|
|F026|`OrganizationProjectControllerTests.testGetMemberList`|期待 5xx；实际 422 / 100422：组织不存在|S01|
|F027|`OrganizationProjectControllerTests.testUpdateNameError`|期待 5xx；实际 422 / 100422：项目名称已存在|S01|
|F028|`OrganizationStatusFlowSettingControllerTest.getStatusFlowSetting`|相同目标集合的顺序不同：处理中/挂起/已拒绝 vs 处理中/已拒绝/挂起|S06|
|F029|`OrganizationStatusFlowSettingControllerTest.addStatusItem`|期待旧写入口 200，实际 422：BUG 工作流已迁移，旧入口禁止写入|S06|
|F030|`OrganizationStatusFlowSettingControllerTest.sortStatusItem`|期待旧写入口 200，实际 422：BUG 工作流已迁移，旧入口禁止写入|S06|
|F031|`OrganizationUserRoleControllerTests.testOrganizationUserRoleAddError`|期待 5xx；实际 422 / 100422：用户组已存在|S01|
|F032|`OrganizationUserRoleControllerTests.testOrganizationUserRoleUpdateError`|期待 5xx；实际 422 / 100422：用户组不存在|S01|
|F033|`OrganizationUserRoleControllerTests.testOrganizationUserRoleDeleteError`|期待 5xx；实际 422 / 100422：用户组不存在|S01|
|F034|`OrganizationUserRoleControllerTests.testOrganizationUserRolePermissionSettingError`|期待 5xx；实际 422 / 100422：用户组不存在|S01|
|F035|`OrganizationUserRoleControllerTests.testOrganizationUserRolePermissionUpdateError`|期待 5xx；实际 422 / 100422：用户组不存在|S01|
|F036|`OrganizationUserRoleControllerTests.testOrganizationUserRoleAddMemberError`|期待 5xx；实际 422 / 100422：用户不存在|S01|
|F037|`OrganizationUserRoleControllerTests.testOrganizationUserRoleRemoveMemberError`|期待 5xx；实际 422 / 100422：用户不存在|S01|
|F038|`OrganizationUserRoleControllerTests.testOrganizationUserRoleDeleteOnlyMemberSuccess`|期待 200；实际 500，成员角色关系重复插入触发唯一约束|S02|
|F039|`SystemAIConfigControllerTests.testEdit`|期待 5xx；实际 422 / 100422：模型名称 测试模型1 已被系统中其他用户占用，请更换唯一名称后重试|S01|
|F040|`SystemAIConfigControllerTests.testDetail`|期待 5xx；实际 422 / 100422：模型信息不存在|S01|
|F041|`SystemOrganizationControllerTests.testUpdateOrganizationError`|期待 5xx；实际 422 / 100422：组织不存在|S01|
|F042|`SystemOrganizationControllerTests.testDeleteOrganization`|期待 5xx；实际 422 / 100422：默认组织无法删除|S01|
|F043|`SystemOrganizationControllerTests.testAddOrganizationMemberError`|期待 5xx；实际 422 / 100422：用户不存在|S01|
|F044|`SystemOrganizationControllerTests.testRemoveOrganizationMemberError`|期待 5xx；实际 422 / 100422：组织不存在|S01|
|F045|`SystemOrganizationControllerTests.testRemoveOrganizationMemberSuccess`|期待 5xx；实际 422 / 100422：至少保留一个管理员|S01|
|F046|`SystemOrganizationControllerTests.testAddOrganizationError`|期待 5xx；实际 422 / 100422：组织名已存在|S01|
|F047|`SystemOrganizationControllerTests.testSwitchOrganizationError`|期待 5xx；实际 422 / 100422：组织不存在|S01|
|F048|`SystemParameterControllerTests.testEmailConnect`|期待 5xx；实际 422 / 100422：连接失败|S01|
|F049|`SystemParameterControllerTests.testEditUploadConfigInfoError`|期待 5xx；实际 422 / 100422：文件限制参数错误|S01|
|F050|`SystemProjectControllerTests.testAddProjectSuccess`|期待 moduleSetting 为 null；实际为用例/缺陷/计划/接口四个默认模块|S05|
|F051|`SystemProjectControllerTests.testAddProjectError`|期待 5xx；实际 422 / 100422：项目名称已存在|S01|
|F052|`SystemProjectControllerTests.testUpdateProjectError`|期待 5xx；实际 422 / 100422：项目名称已存在|S01|
|F053|`SystemProjectControllerTests.testDeleteProjectError`|期待 5xx；实际 422 / 100422：项目不存在|S01|
|F054|`SystemProjectControllerTests.testRevokeProjectError`|期待 5xx；实际 422 / 100422：项目不存在|S01|
|F055|`SystemProjectControllerTests.testAddProjectMemberError`|期待 5xx；实际 422 / 100422：用户不存在|S01|
|F056|`SystemProjectControllerTests.testRemoveProjectMemberError`|期待 5xx；实际 422 / 100422：用户不存在|S01|
|F057|`SystemProjectControllerTests.enableError`|期待 5xx；实际 422 / 100422：项目不存在|S01|
|F058|`SystemProjectControllerTests.disableError`|期待 5xx；实际 422 / 100422：项目不存在|S01|
|F059|`SystemProjectControllerTests.testUpdateNameError`|期待 5xx；实际 422 / 100422：项目名称已存在|S01|
|F060|`TaskCenterScheduleControllerTests.getPageError`|期待 5xx；实际 422 / 100422：项目不存在|S01|
|F061|`TestResourcePoolControllerTests.getResourcePoolsDetailFiled`|期待 5xx；实际 422 / 100422：测试资源池不存在|S01|
|F062|`TestResourcePoolControllerTests.updateUiTestResourcePoolFiled`|期待 5xx；实际 422 / 100422：资源池名称已存在|S01|
|F063|`PersonalControllerTests.testPersonalGetId`|期待 5xx；实际 422 / 100422：无权操作非本人账户|S01|
|F064|`PersonalControllerTests.testPersonalUpdateInfo`|期待 5xx；实际 422 / 100422：无权操作非本人账户|S01|
|F065|`PersonalControllerTests.testPersonalUpdatePassword`|期待 5xx；实际 422 / 100422：旧密码输入错误，请重新输入|S01|
|F066|`PersonalControllerTests.testDetail`|期待 5xx；实际 422 / 100422：模型信息不存在|S01|
|F067|`UserApiKeysControllerTests.testAdd`|期待 5xx；实际 422 / 100422：最多能有5个Api key|S01|
|F068|`UserApiKeysControllerTests.testDisable`|期待 5xx；实际 422 / 100422：当前用户无操作该ApiKey的权限|S01|
|F069|`UserLocalConfigControllerTests.testAdd`|期待 5xx；实际 422 / 100422：当前用户本地配置已存在|S01|
|F070|`UserLocalConfigControllerTests.testGet`|期待两条配置，实际一条；前置 testAdd 断言失败，第二种配置尚未创建|S11|

### 5.2 10 条 error

|编号|测试类.方法|具体观测与直接原因|方案及影响|
|---|---|---|---|
|E001|`GlobalUserRoleControllerTests.add`|授予旧新增权限，实际接口正向调用仍被 403 拒绝|S04|
|E002|`GlobalUserRoleControllerTests.updatePermissionSetting`|授予旧编辑权限，实际权限保存正向调用仍被 403 拒绝|S04|
|E003|`OrganizationStatusFlowSettingControllerTest.updateStatusItem`|测试 static 状态项未创建，调用 getId 时空指针；未执行目标接口|S06|
|E004|`OrganizationStatusFlowSettingControllerTest.updateStatusDefinition`|测试 static 状态项未创建，调用 getId 时空指针；未执行目标接口|S06|
|E005|`OrganizationStatusFlowSettingControllerTest.updateStatusFlow`|测试 static 状态项未创建，调用 getId 时空指针；未执行目标接口|S06|
|E006|`OrganizationStatusFlowSettingControllerTest.deleteStatusItem`|测试 static 状态项未创建，调用 getId 时空指针；未执行目标接口|S06|
|E007|`OrgStructureMemberControllerTests.adminTreeWithStats`|正向权限探针得到 403：权限用户组织为 100001，目标为 sys_default_organization_3|S08|
|E008|`OrgStructureMemberControllerTests.memberPage`|正向权限探针得到 403：权限用户组织为 100001，目标为 sys_default_organization_3|S08|
|E009|`OrgStructureMemberControllerTests.memberDetailMasked`|正向权限探针得到 403：权限用户组织为 100001，目标为 sys_default_organization_3|S08|
|E010|`UserApiKeysControllerTests.testList`|撤销旧角色权限后本人列表仍可访问，通用探针因此抛异常|S10|


## 6. 2026-10-29 实施追踪（进行中）

本节为实施状态；前文及原始 80 条清单保留为受验版本历史证据。尚未全量通过，不可按改动数量宣称修复完成。

|需求|前端入口/实现|后端接口/实现|数据或配置|验证与状态|
|---|---|---|---|---|
|S02 重复成员绑定|组织/项目成员添加，沿用现有入口|add-members、organization/add-member → RoleRelationWriter|保留 V56 唯一约束；补精确业务键锁定查询|5 个写入单元测试通过；真实 MySQL 并发/回滚测试已补，待执行|
|S03 元数据部分更新|系统用户组编辑，原有入口|saveRoleMetadata 选择性更新，不再重建权限|旧 DTO enabled 未传为 null；新建仍默认启用|新增保留停用状态/权限、禁止改类型测试通过|
|S04 权限对齐|用户组新增、重命名、删除、保存权限按新权限；成员操作独立兼容|保留既有控制器权限门禁|不批量扩张授权|前端类型/lint/构建待执行|
|S07 权限树|系统权限控制/授权树|permission.json 与安装包控制器对应|确定缺失 SYSTEM_AGENT_PACKAGE:READ、READ+ADD、READ+UPDATE、READ+DELETE；补定义，不恢复已撤销的页面按钮|迁移 V49 和所有模块定义交叉检查已确认；完整 DB 差集复验待 CI|
|S01/S05/S09|沿用错误展示与默认模块入口|严格业务错误断言；必备角色保留 100003|默认四模块精确断言；没有改历史迁移|57 条直接断言及 2 条辅助路径已更新；后续分支仍需运行验证|
|S06|旧 BUG 写入口保持关闭|6 个退役写入口测试独立准备，只断言拒绝及数据库未变化|不放开旧接口|新流程入口端到端验证尚未完成|
|S08/S10/S11|组织成员、个人 API Key、本地执行配置|组织权限测试附目标归属与上下文；Key 按本人隔离测试；配置独立夹具|仅测试 schema 夹具|集成测试待 CI|

新发现的测试覆盖缺口：部分用例将 ResultMatcher 作为 BaseTest 的 URI 可变参数传入，实际上未执行状态断言。已在个人 API Key、本地配置本次相关调用改为明确 andExpect；不能把此前这些调用没有失败视为契约正确。

已执行低资源验证：有界 javac 编译当前改动成功；PermissionControlServiceTests + RoleRelationWriterTests 共 32 项通过（0 跳过、0 失败）。编译/运行日志暂存于 .codex-tmp/ci-remediation-20261029。未启动本机 Docker 或应用栈；当前本机余量约 1.1 GiB，后续真实 MySQL 与完整构建使用既有远端 CI。


### 6.1 首轮实际复验与进一步定位

- CI [40803524899](https://github.com/chenqifen-miduo/metersphere/actions/runs/40803524899)，提交 899c03d6fc：前端成功；后端执行 550 项，29 failure、47 error，未通过。测试数增加 9 项，不能仅用失败总数与原 80 比较完成率。
- 新增真实 MySQL 唯一键并发、重复回放、无关主键冲突回滚 2 项均通过；共享 writer 单元 5 项也通过。
- S07 已取得精确差集：[逐项证据](../testing/msp-ci-failures-20261029/permission-definition-diff.json)。原漏项不是单一原因：4 个安装包权限定义缺失；另有 4 个实际定义缺口（PROJECT_BASE_INFO:READ、TEST_ASSET_CATEGORY:ASSIGN/MANAGE、TEST_ASSET_SOURCE:GOVERN）；4 个已由 V57 停用的旧 workspace/classify 权限；4 个仅由其他模块提供的现行权限。8 个实际漏项均补活动定义；已退役项保留显式兼容清单并校验停用迁移，不加入活动树、不恢复入口；单模块断言只允许有证据的外部/退役归属，仍拒绝未定义或遗漏的本模块权限。新增 app 完整 classpath 定义覆盖回归。没有删除授权或自动扩权。
- 多个此前未执行到的权限探针在正向请求创建项目，使探针用户得到该项目管理员身份。原探针只删除一个权限位，既未清理新增管理员关系，也未保证异常时清理，故反向断言及后续探针持续失败。现改为关系快照、finally 删除本次新增关系和授权、刷新真实会话，再准确断言 403；禁止缺少身份时回退管理员身份。产品管理员规则不变。
- 组织联系人集成断言与 6b05f8d997（2026-07-29，stop syncing email/phone and use userid for @mentions）的明确规则冲突。已有独立单元测试同样要求 phone/email=null、wecomUserid 明文；此次对齐集成断言，没有重新暴露手机号/邮箱。
- 本轮工作流快照 SQL 错将 status_definition 当作有 id 列，已修为 status_id、definition_id。这是本轮新增测试代码错误，已纠正。
- 继续逐个核对进入后续分支后暴露的业务错误断言，采用源代码抛出的具体 message key，未使用任意 4xx 替代 500。
- 默认组织成员、批量项目管理员、批量项目成员相邻写路径补入共享 writer；本次真实新增的审计同步加入原事务。组织角色 add-member 仅对真实新增成员记一次审计，移除无条件日志切面；删除成员入口不变。新增重复回放关系 ID、数量、审计数量不变的集成断言。
- 新工作流 API 保存、发布版本冲突、启用、使用中禁止删除、禁用后删除的事务回滚集成测试待下一轮 CI；不会改变测试以外的活动流程。
- CI 只在完整测试成功后运行镜像/健康检查；失败时仍执行独立打包诊断，镜像检查标为未执行而不是通过。前端仅在树哈希相同且既有成功 artifact 可取得时复用本轮成功产物，避免重复构建。


### 6.2 第二轮复验与补齐的真实缺陷

- CI [40807861642](https://github.com/chenqifen-miduo/metersphere/actions/runs/40807861642)，提交 4e08e4f4ed：552 项，25 failure、2 error；仍未通过。新工作流测试开始执行，但其请求遗漏必需 code，暴露 addFlow 缺少服务端参数校验；现补必填/长度校验及负向单元测试，正向请求携带 code。
- 真实重复写缺陷：关闭组织模板时用 scene 而不是参数 key 查询，重复请求再次 insert，命中 organization_parameter 主键。改为按 (organization_id,param_key) 原子设置 false，并验证重复请求只有一行、结果保持 false；不使用吞异常或删除唯一索引。
- 真实非管理员路径缺陷：SystemProjectController.rename 的 CheckOwner 引用 #request.projectId，而 UpdateProjectNameRequest 只有 id。改为 #request.id，继续执行原归属校验，避免将普通授权用户请求变成 SpEL 500。
- 系统组织夹具 init_organization.sql 与早先角色测试遗留的 admin/org_admin 绑定冲突，初始化在第 8 条语句中断，导致后续大量组织不存在。仅重置本夹具的 default-organization-1～6、default-project、default-admin；组织角色夹具独立补其依赖组织，不再依赖其他类稍后初始化。
- 组织架构 API 明确以 organizationId 查询参数为管理目标，控制器临时上下文会覆盖活动页头。因此不能把“页头不同”断言为越权；改为真实查询无成员资格的另一组织并断言 403，不放宽归属检查。
- 系统用户邀请权限探针原先依赖前一邮件配置测试留下的无效外部 SMTP。改为使用已注册邮箱，真实进入授权后的业务拒绝分支，仍检验撤权返回 403；此权限探针不计为邮件投递成功验证。
- 继续扫描全部现行 Controller 的 RequiresPermissions（常量和字面量），发现额外 17 个活动定义漏项：AI 模型/凭据/Runner/触发器/证据 11 个、组织及系统任务中心 2 个、API 文档 2 个、AI 用例评审 1 个、用例资产导出 1 个。补相应所属模块定义和中英文名称，不自动给普通角色增加授权；app 增加全仓库控制器引用必须有活动定义的门禁。首次精确差集中的 8 个活动漏项之外，同类问题合计补齐 25 个定义。
- 全模块权限树还存在 order 未填写时比较器可能空指针的问题；比较改为明确空值后置、同序按 ID 稳定排序，补实际树组装单元回归。
- 最新本地验证：39 个涉及源码及测试的有界 javac 通过；37 项定向测试通过，0 跳过、0 失败。尚需下一轮远端完整集成复验，不能据此宣称全部通过。


### 6.3 第三轮复验与最后两个阻断项

- CI [40810937619](https://github.com/chenqifen-miduo/metersphere/actions/runs/40810937619)，提交 4b1d064242：554 项，2 failure、0 error；SDK 11 项全部通过，system-setting 543 项中 2 项失败。原始 80 条 testcase 全部执行，其中 79 条通过；剩余原始项是“部分用户不存在”提示需要包含具体用户 ID 的精确断言，现修为真实业务契约，不放宽匹配。
- 新工作流 API 已进入发布回归：数据库写入成功但响应仍是 DRAFT。原因是 JdbcTemplate 修改 workflow_definition 后，MyBatis 事务内一级缓存仍保存旧对象。为 workflow definition 的按 ID、列表两个读取明确关闭查询缓存并在读前刷新本地缓存；不删除测试事务或改成“重新请求后才判断成功”。场景中预热列表缓存，再检验发布响应和随后的列表均为 PUBLISHED，接着验证启用/禁止删除/禁用后删除。
- 补查系统用户组 UI 的删除权限：原有菜单外围可见性仍使用旧权限，导致只有新删除权限的用户看不到操作；系统页面上不同类型的全局角色菜单也误用了组织/项目权限。现按当前页面上下文与后端路由统一判断，成员权限仍独立兼容；共享组件原有项目用户组删除 TODO 已接入现存真实 API；错误关闭处理的最终实现见 6.4，不再只提示成功。
- 本次前端确有新变化，因此下一轮必须重新执行类型检查和生产构建，不复用旧树的产物。两处相关前端 eslint 已通过；mapper XML 已解析通过。下一轮完整 CI、镜像/健康及发布后 UI 验收仍待结果。


### 6.4 前端回调契约修正

- CI [40813726543](https://github.com/chenqifen-miduo/metersphere/actions/runs/40813726543)，提交 973dd17a4d：前端 TS2322 拦截，后端未执行。本轮新增删除回调返回 Promise<boolean>，但 useModal 明确要求 Promise<void>；这是本轮修改的契约错误，不是历史业务失败，也不能以 lint 通过替代类型检查。
- 阅读真实 useModal 后确认其 finally 无条件关闭弹窗，原本也不能正确保持请求失败状态。保持原 Promise<void> 公共契约，调用方传播失败，封装按成功/失败决定关闭并始终释放 loading；删除请求失败不会提示成功，也不会关闭弹窗。
- 新增执行真实 hook 源码的 5 项轻量契约测试：成功、失败后重试、同步异常、删除弹窗复用、等待时禁止取消，均已通过。测试仅替代外部 Modal 容器和语言环境，不替代业务 API；不计为浏览器端到端验证。
- 本地 Node 测试以 96 MiB 堆上限运行通过；格式检查执行完成。最新全量 scoped lint 在 128 MiB 上限退出，未提高本机上限，转为远端 CI 必需步骤。完整类型检查及生产构建继续作为远端必需门槛，未关闭检查或扩大类型。


- CI [40815278015](https://github.com/chenqifen-miduo/metersphere/actions/runs/40815278015)：新增 5 项弹窗运行时契约测试通过；scoped lint 指出新测试脚本的导入排序及两个 Promise executor 返回值，已按真实项目规则修正，未关闭规则。后端尚未执行。前端产物仅在该 job 成功时上传，避免失败后的“无产物”次生错误误导排查。
