# MSP CI 70 项失败、10 项错误：逐项原因、影响与修复方案

日期：2026-10-29。当前：本轮CI失败修复、前端构建及13个隔离核心路径验收通过，见第7节；完整业务交付仍保留部署/历史数据/客户端矩阵等未执行项。第1～5节为原始80条分析，第6节为过程证据，历史“待查/失败”不代表当前CI结论。

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
|S02 重复成员绑定|组织/项目成员添加，沿用现有入口|add-members、organization/add-member → RoleRelationWriter|保留 V56 唯一约束；补精确业务键锁定查询|5 个写入单元、2 个真实 MySQL 并发/回滚及系统接口回放均通过；项目相邻路径后续进展见 6.6～6.7|
|S03 元数据部分更新|系统用户组编辑，原有入口|saveRoleMetadata 选择性更新，不再重建权限|旧 DTO enabled 未传为 null；新建仍默认启用|定向单元及真实控制器集成通过，原始相关项已逐项核对|
|S04 权限对齐|用户组新增、重命名、删除、保存权限按新权限；成员操作独立兼容|保留既有控制器权限门禁|不批量扩张授权|前端运行时、类型/lint/生产构建通过；发布后真实 UI 未验收|
|S07 权限树|系统权限控制/授权树|permission.json 与安装包控制器对应|确定缺失 SYSTEM_AGENT_PACKAGE:READ、READ+ADD、READ+UPDATE、READ+DELETE；补定义，不恢复已撤销的页面按钮|最终补 25 个活动定义；模块差集和完整 manifest 定向测试通过。app 完整集成仍受下游阻断|
|S01/S05/S09|沿用错误展示与默认模块入口|严格业务错误断言；必备角色保留 100003|默认四模块精确断言；没有改历史迁移|原始相关分支在第 6、8 轮均通过；保留未知异常 500 检查，不改为任意 4xx|
|S06|旧 BUG 写入口保持关闭|6 个退役写入口测试独立准备，只断言拒绝及数据库未变化|不放开旧接口|新入口 MockMvc 全生命周期及旧入口拒绝/数据库不变均通过；真实浏览器链路未验证|
|S08/S10/S11|组织成员、个人 API Key、本地执行配置|组织权限测试附目标归属与上下文；Key 按本人隔离测试；配置独立夹具|仅测试 schema 夹具|system-setting 对应集成全部通过，80 条原始项已核对|

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


### 6.5 原始 80 条已全部通过，首次进入下游模块

- CI [40815944705](https://github.com/chenqifen-miduo/metersphere/actions/runs/40815944705)，提交 `13bd2c3f15`：前端运行时测试、scoped lint、完整类型检查和 Vite 生产构建全部通过。
- Surefire XML 逐项核对：**原始 80 条全部实际执行且通过**，没有将未执行计为通过。SDK 11 项、system-setting 543 项全部通过、零跳过。包括真实 MySQL 并发/回滚、新工作流保存—发布—启用—删除保护，以及原始角色元数据、成员回放等路径。[可审计逐项结果](../testing/msp-ci-failures-20261029/verification-round6.json)。
- 首次进入 project-management：280 项中 61 failure、4 error；全 reactor 到此累计 834 项、61 failure、4 error。它们是此前未执行的下游失败，不能说原始 80 项仍失败，也不能说整个项目已通过。后续 API/缺陷/用例/计划/Agent/app 模块仍未执行。
- 镜像及健康检查因完整测试失败而未执行，不能把独立 `skipTests package` 的诊断成功视为验收成功。发布后真实 UI 尚未验证。

### 6.6 下游进一步定位及修复（待 CI 复验）

|问题/影响|修改入口及范围|修改内容|通过标准|
|---|---|---|---|
|项目用户组重复 add-member 返回 500，阻断角色删除前置|用户组成员弹窗 → `/user/role/project/add-member` → ProjectUserRoleService|相邻漏接路径接入 RoleRelationWriter；校验项目存在、角色类型和项目归属；同批用户去重并稳定排序；保留唯一索引|同批重复仅调用一次 writer；重复请求保持原关系 ID/数量；无关项目/非项目角色拒绝且不写入|
|切换项目 DTO 约束未启用；旧权限测试模型不适用|项目切换 → `/project/switch` → ProjectController|启用已有 DTO 的 @Validated；仍按目标项目访问资格和本人身份授权，不能恢复当前页头权限位要求|空参数 400；匿名 401；替他人切换拒绝；无目标权限拒绝；真实授权切换成功|
|项目旧 BUG 流程测试共享 static 前置失败连锁 4 个 error|ProjectStatusFlowSettingControllerTest 6 个写入口|每条独立从 GET 准备数据；精确验证迁移提示；对 status_item/status_flow/status_definition 快照验证未变化|六条均独立执行，无测试端空指针，不重新开放旧写入口|
|文件管理先在“非仓库节点不存在”旧 5xx 断言中断，后续重名连锁失败|FileManagementControllerTests|首条初始化继续走完；已观测的非仓库、旧文件不存在、文件不存在分别使用严格安全业务错误断言|原成功路径真实走完，错误路径同时验证 HTTP/业务码/具体提示/请求关联 ID|
|项目多类旧 5xx 断言与现行安全错误协议不符|环境、环境组、全局参数、机器人、成员及角色集成测试|按每条已观测消息和实际服务端 key 修正，未把全部 5xx 全局替换为任意 4xx；[34 条精确观测清单](../testing/msp-ci-failures-20261029/downstream-observed-contracts.json)|逐分支运行，服务异常仍失败；后续新分支继续核对源代码|
|默认语言显示 Not Support Key|commons.properties|补环境不存在、全局参数不存在两个默认语言 key，其他三种语言原本已有|默认语言返回可理解提示；不能再输出内部翻译 key|
|权限探针依赖无效外部 SMTP；全局参数不存在依赖前序测试删除|ProjectMemberControllerTests、GlobalParamsControllerTests|邀请权限探针以已注册邮箱触发确定业务拒绝；缺失参数 GET 独立准备缺失状态|权限探针撤权仍 403；不将探针计作邮件投递验证；缺失测试不依赖前序提前退出|

本轮涉及 12 个 Java 修改文件及测试辅助源码有界 javac 通过；新增 4 个 ProjectUserRoleMemberReplayTests 全部通过（同批去重/回放、其他项目、其他类型、项目不存在）。新增的实际接口重放断言和上述下游整改仍待远端 MySQL 复验，未标记完成。本机继续保持无 Docker、无应用启动。前端源码未变，下一轮只在 exact frontend tree hash 与第 6 轮一致并成功取得产物时复用其已验证构建。


### 6.7 下游第二次复验及文件下载/移动异常溯源

- CI [40819053573](https://github.com/chenqifen-miduo/metersphere/actions/runs/40819053573)，提交 `3f7d5340ee`：前端按相同树哈希复用已成功验证产物；SDK 11、system-setting 543 均通过，project-management 284 项中 24 failure、0 error。原始 80 条仍全部通过。[逐项与模块统计](../testing/msp-ci-failures-20261029/verification-round8.json)。
- 实际项目用户组 add-member 重放已成功；新增 4 项服务边界测试也在远端执行通过。项目级六条旧流程写入口不再有 static 连锁空指针；切换项目有效、空参数、替他人切换、无目标权限、匿名场景均通过。
- 进一步确认项目角色夹具的 organization_id 错填 projectId。按 project.organization_id 修正夹具（default-organization-2），产品仍拒绝组织不一致的关系，未放宽 writer 归属匹配。
- 文件重传测试先将真实元数据 storage 改成 GIT 以测试拒绝，然后在旧 5xx 断言处中断，未恢复 MINIO。后续下载返回安全 JSON 拒绝而非二进制，批量移动默认筛选 MINIO 也漏掉这一行。已将恢复放进 finally；不得为此放宽二进制下载或全部移动的断言。后续通过情况仍需下一轮真实对象存储集成复验。
- 新暴露的错误分支继续使用具体业务消息和 requestId 严格断言。对尚未执行到的相邻分支只依据对应服务实现设置明确期望，不能以源码核对替代已执行证据；新增观测保存在 downstream-observed-contracts.json。
- 相邻项目用户组日志切面仍对回放写 UPDATE 审计。进一步调整为只对 writer 返回真实新增的成员生成一次审计，审计同步加入原事务，提交后仅刷新变更用户权限；回放不记“变更”审计。新增“实际插入才审计/刷新”单元测试，当前共 5 项全部通过；真实接口补审计数量不变断言，待下一轮 CI。
- 后端本轮增量有界 javac 通过。没有启动本机 Docker/应用，也没有合并失败分支。完整镜像/健康、下游未进入模块及部署后 UI 仍不可标记通过。



### 6.8 文件管理最后 8 项进一步溯源与新增真实修复

- CI [40820882842](https://github.com/chenqifen-miduo/metersphere/actions/runs/40820882842)，代码 `4a4880511a`：SDK 11、system-setting 543 全通过，project-management 285 项剩 8 failure、0 error；原始 80 项全部通过。[逐项证据](../testing/msp-ci-failures-20261029/verification-round9.json)。文件重传/下载/移动以及项目成员真实审计回放通过；项目级状态流和其余项目控制器均通过。
- 文件批量下载的超限测试临时插入 `test_0`～`test_19` 元数据，但没有对象文件。旧 5xx 断言中断后未清理，后续预览碰到 `test_19`。改为插入、负向断言、finally 删除的完整夹具生命周期；超限与空集分别严格匹配 `file.size.is.too.large`、`file.is.empty`。不向正向测试注入假文件字节，也不将 JSON 错误当下载成功。
- 同时确认真实代码缺陷：FileMetadataService.getFileByte 吞掉对象读取异常，随后 `TempFileUtils.getFile(null)` 导致空指针，掩盖实际存储错误。改为直接返回真实字节；元数据/MinIO NoSuchKey 缺失按业务错误，其他存储故障保留 cause 并由统一异常处理记录安全 500，不能将 AccessDenied/网络故障误报为文件不存在。新增 6 项定向单元测试全部通过。
- 文件更新“目录不存在”旧测试遗留 enable=true，先被“非 jar 不能启用”挡住，因此此前从未测试目录校验。重置此字段后，确认 update 原本没有目录存在性校验，会写入无效目录。补 FileManagementService 三参数范围校验（目录 ID/类型/所属项目）；更新按文件真实项目校验，上传及批量移动沿用同一校验，批量移动的显式文件集合也必须属于请求项目。真实集成补不同项目目录必须拒绝、原目录不变断言，待下一轮 CI。
- 继续追查项目角色组织归属错误的生产来源：ProjectUserRoleService.delete 把 role.scopeId（项目 ID）作为默认角色的 organizationId。改为读取实际 project.organizationId，再执行默认成员补位。夹具不再使用“本地角色却绑定到其他组织 source”这种无效关系，改为真实仅有一条角色的项目成员，接口删除后断言默认角色 source=原项目、organization_id=真实组织，待下一轮 CI。
- 上述新增生产修复及其相关测试有界 javac 通过；项目角色 5 + 文件下载 6，共 11 项定向单元测试通过，0 跳过/0 失败。剩余旧断言按已观测具体提示和对应 FileLimit/目录/预览实现逐条修正；尚未运行到的分支不标记通过。
- 历史数据不会由 writer 擅自改写。部署前可只读审计历史项目关系的错误组织归属；只有实际命中并确认项目身份后，才制定有备份、可审计的数据修复，不在未查证时批量覆盖：

```sql
SELECT r.id, r.user_id, r.role_id, r.source_id,
       r.organization_id, p.organization_id AS expected_organization_id
FROM user_role_relation r
JOIN user_role ur ON ur.id = r.role_id AND ur.type = 'PROJECT'
JOIN project p ON p.id = r.source_id
WHERE NOT (r.organization_id <=> p.organization_id);
```


### 6.9 仅剩一条批量移动权限探针：真实空集合 SQL 缺陷

- CI [40823167885](https://github.com/chenqifen-miduo/metersphere/actions/runs/40823167885)，提交 `a85a1deff0`：SDK 11、system-setting 543 全通过，project-management 291 项仅剩 1 failure、0 error。原始 80 条全部通过。[逐项证据](../testing/msp-ci-failures-20261029/verification-round10.json)。新增真实目录归属拒绝、删除项目角色补位的组织归属断言、下载异常 6 项都通过。
- 唯一失败不是应放宽的测试：普通 PROJECT 权限用户调用批量移动，`@CheckOwner(selectIds)` 将空的显式 ID 列表直接拼成 `IN ()`，MySQL 语法错误返回 500；管理员会跳过该切面，所以管理账号的正向用例未暴露。全选的合法请求本来就可能没有显式 ID，不能靠前端构造假 ID 解决。
- ExtCheckOwnerMapper.xml 六条动态归属查询对空/null 输入渲染 `IN (NULL)`，返回拒绝而非无效 SQL；对非空输入继续绑定 ID 与用户参数，不将空集默认为授权。新增真实 XML/BoundSql 渲染测试 3 项，覆盖六查询的空、null、有效 ID，共 18 种渲染组合。
- 文件批量移动保留既有 RequiresPermissions；移除不适用全选的显式 ID 切面，服务端读取真实项目，然后调用既有 `SessionUtils.hasPermission(actualOrganizationId,actualProjectId,PROJECT_FILE_MANAGEMENT_READ_UPDATE)` 做实际目标权限校验。项目管理员/组织管理员按现行范围规则判断，不能使用其他当前页头的管理身份授权目标项目；无权限返回统一 403。
- 进入业务层后，目录必须属于该项目，实际选择的每个文件也必须属于该项目。全选按项目过滤，显式跨项目集合拒绝。新增服务授权 4 项，覆盖合法空显式集、拒绝时不读写文件、其他项目权限无效、跨项目文件不写入。权限探针改用真实合法的目标默认目录和 selectAll=true，不再只用一个随机不存在目录制造业务错误。
- 同时补目录更新边界：省略 moduleId 保留；显式空串或空项目拒绝，不允许写入空目录；新增 3 项模块范围测试及实际接口空串拒绝断言。
- 本地有界 javac 成功，当前项目/文件/归属 SQL 共 21 项定向单元测试全通过（0 跳过/0 失败）；对应真实数据库和整套 reactor 待下一轮。其他批量 API 的 selectAll 业务授权须按各自接口验收，不能把通用 SQL 空输入护栏当作这些功能已验收的证据。


### 6.10 系统及项目模块全通过，首次进入 API 模块后的定位

- CI [40825196007](https://github.com/chenqifen-miduo/metersphere/actions/runs/40825196007)，提交 `54ae3c0d9d`：SDK 11、system-setting 546、project-management 298 项全部通过、零跳过；原始 80 条全部通过。此前下游 65 个项目问题已全部消除，新的文件全选目标权限、空/null 归属 SQL、目录边界及审计回放均通过。[完整模块与原始逐项结果](../testing/msp-ci-failures-20261029/verification-round11.json)。
- 首次实际进入 api-test：267 项，54 failure、2 error；累计已执行 1122 项。API 模块之后的服务仍未执行，整体仍为部分完成，不合并为全量验收通过版本。

|根因/影响|具体修改范围及内容|验证标准与当前证据|
|---|---|---|
|49 个已观测旧错误契约断言|API 调试/定义/Mock/文档/报告/场景/用例测试，逐条按实际 HTTP 422 或 404、具体业务码、消息 key、提示及 requestId 校验；[49 条观测清单](../testing/msp-ci-failures-20261029/api-observed-contracts.json)|不使用任意 4xx；104003 等专有业务码显式保留，404 与 422 分开严格断言。额外相邻树目录负向分支按实际服务抛出路径设置明确期望，待下一轮执行|
|真实 P01：复制接口 DTO 归属表达式错误|ApiDefinitionController.copy 使用 getSelectIds，但 ApiDefinitionCopyRequest 实际只有 id；改为 getId，保留原 RequiresPermissions 和资源归属检查|新增读取真实控制器注解并执行真实 SpEL 的契约单元通过；控制器集成改用真实存在的源 ID，普通权限用户必须返回 200，撤权必须标准 403|
|真实 P02：非法资源类型导致 null switch|ApiScenarioService.getStepResourceInfo 在 EnumValidator 返回 null 后仍 switch；先拒绝非法/缺失类型并返回既有安全参数错误|新增非法、空/缺失、合法类型但资源已删除的 3 项单元通过；GET 权限测试用有效资源/类型且强制正向 200，不再用 aa/bb 触发无意义 500|
|TCP 更新错误、Mock 重名误判的前置连锁|ApiDefinition.testAdd 在旧文件不存在断言中断，后面的 TCP 初始化没有执行；Mock.testAdd 同样在文件断言中断，之后的“重名接口定义test”专用重复夹具未创建，因此更新被正确当作非重名并返回 200|修正早期严格错误契约，让既有真实初始化继续执行；保留 TCP 更新和 Mock 唯一冲突的原业务断言，不将预期冲突改为接受 200|
|场景 GC 空数据、删除/恢复被拒绝的模块状态连锁|ApiScenarioControllerTests 六处临时关闭默认项目 API 模块，旧 5xx 断言中断后没有恢复，污染本类和之后执行的其他类|逐处 try/finally；ApiScenarioBatchOperationTestService 先读原 module_setting，再按原值恢复，包括 null；不再强行覆盖成固定七模块。业务拒绝仍按模块菜单错误严格断言|
|普通用户全选移动必须真实可用，而不只是不返回 500|项目文件集成新增真实 MinIO 小文件、匹配筛选全选、普通成员移动、查询目录持久化、撤权拒绝和 finally 只清理自建文件/目录|新增权限辅助方法对这些有效场景强制 200，撤权同时核验 100403/auth.forbidden；新场景已编译，待下一轮真实 MySQL/MinIO 执行|

本轮 18 个 Java 文件有界 javac 通过，API 4 项定向单元全部通过（复制 DTO/SpEL、非法类型、空类型、合法类型缺失资源兼容）。测试不再依赖 Testcontainers 私有 shaded commons-lang3 包，改用仓库已有公开 commons-lang3，没有新增依赖或更换版本。系统/项目上一轮通过的结果不替代新 API 改动验收。

前后端边界：现有 API 管理复制页面通过 openCopyApiTab 打开表单，仓库未发现当前前端模块调用 legacy `/api/definition/copy` 的客户端实现；本轮修复的是已存在 REST 接口的 DTO/归属契约，不声称该旧路由导致当前页面复制按钮失败，也不新造前端入口。资源类型查询仍由原场景步骤接口提供。前端源码未变，继续按相同树哈希复用成功构建。

### 6.11 API 剩余 12 项与定向验证门禁

- CI 40831105988 / `8525bb05a3`：SDK 11、系统 546、项目 299 全通过，API 271 项剩 12 failure、0 error；原始 80 项全通过。新增普通成员真实 MinIO 全选移动与撤权测试通过。逐项结果见 `verification-round12.json`。
- 追踪范围：现有 API 定义/Mock/用例、报告分享、场景目录/定时配置、任务中心入口；沿用原前端和后端路由，不增加页面、数据库迁移或授权。修正测试契约不改变产品行为。
- API 定义 transfer 的旧用法未提供 JSON 请求体，实际正确返回 400/100400/api.validationFailed；断言同时检查 requestId。Swagger 导入解析失败匹配既有 parse_data_error，不能放宽为任意 4xx。
- 报告不存在/过期分享、非法场景目标目录、缺失 API/用例均按实际 422/404 及精确业务提示匹配。相邻跨项目目录使用相同服务 guard，仍断言拒绝，不放松归属规则。
- Mock 的不存在测试在此前项目不存在分支后未恢复 projectId，实际未进入目标资源校验。先恢复真实项目；进一步核对控制器后确认，缺失 Mock 仍由前置模块校验按 422 拒绝，不能把服务层 404 当作该控制器实际响应。任务中心组织接口必须匹配组织不存在，项目接口匹配项目不存在。
- API 用例新增的缺失项目由该服务自有校验返回 422/project_is_not_exist，不误用其他控制器的 404。新增、更新及 Mock 更新权限探针恢复有效资源，并强制授权正向 200/撤权 403，不能以业务拒绝充当正向通过。
- 定时配置缺失资源先被模块校验拒绝；非法 cron 的既有调度器明确返回“Cron 表达式格式不正确”。保留成功创建、配置持久化及删除检查。
- 相关 8 个 Java 测试/辅助类在 384 MiB 上限下编译通过。真实数据库结果待定向 CI，不能把编译视为集成通过。
- 自检改为两阶段：workflow_dispatch 可选 `api-failures`，只执行本次失败的 7 个 API 控制器测试类和契约单元；依赖模块仍编译，但不重跑已通过套件。默认 `full` 继续执行完整 reactor，只有 full 测试成功才执行镜像与健康检查。移除每次测试失败后的重复 skipTests 打包诊断。定向成功不能代替最终全量验收。

定向 CI 40835521265 / deea21fdae：139 项，137 通过，2 failure、0 error、0 skipped。两项均为本轮错误使用404预期的 Mock 缺失/用例项目缺失分支，实际422；它们已按控制器前置模块检查及服务校验修正，尚待新版本运行。本轮未运行原始80项是定向范围选择，不能将其记为回归通过或失败。其余原12项对应分支均已执行通过。

### 6.12 防止下游未执行和重复自检的结果收集

- 全量 reactor 使用 Surefire 的 failure-ignore **仅用于继续执行下游模块**，不是放行失败。构建后独立 `check-test-reports.py` 强制检查 SDK、系统、项目、API、缺陷、用例、计划、工作台、Agent、app 十个有测试模块的 XML；缺模块、零测试、failure、error 或 skipped 均使 CI 非零退出。编译/打包本身失败也仍使流水线失败。
- 镜像/健康步骤同时要求 Maven 步骤和独立测试门禁成功。定向 API 运行不执行健康步骤，也不能视为全量通过。
- 新增门禁单元测试覆盖通过、缺模块、空套件以及 failure/error/skipped 六种结果；4 个测试方法（包含子场景）本机通过。将第12轮真实报告交给门禁，正确拒绝 12 个失败和 6 个未执行模块，未把系统/项目通过误认为整个 reactor 通过。
- 汇总 JSON 只记录测试标识和计数，不发布可能包含会话信息的原始失败文本；CI 上传完整原始报告仅用于授权诊断，仓库仍只保存脱敏证据。
- 远端 workflow_dispatch 返回403，保留原仓库权限；定向验证使用本分支既有 push 触发及明确 `[api-failures]` 标记。普通提交继续执行 full；不取消正在运行的定向任务来反复触发。

### 6.13 相邻资源归属契约审计（实施中）

337 处直接 getter 检查及注解根变量检查确认7处契约缺口。验收需保留 RequiresPermissions，按真实资源找到项目归属；不能仅删除 CheckOwner。

|需求/入口|修改范围|数据与失败行为|测试与状态|
|---|---|---|---|
|API定义/场景停止导出|原 stop/{taskId} 注解由不存在的 projectId 改为 taskId/export_task|按导出任务真实项目检查；不改任务消息或表结构|补真实注解表达式与资源类型契约；待集成|
|功能用例脑图树|FunctionalCaseMinderController.getTree 使用 request.projectId|保留项目归属拒绝|补注解根变量/DTO getter 自动检查；待验证|
|计划功能用例树|TestPlanFunctionalCaseController.getTree 使用 request.testPlanId|保留计划归属拒绝|同上|
|计划任务执行结果|get-result/{taskId} 使用 taskId/exec_task，与同控制器 get-task 一致|不能把任务ID当报告ID|同上|
|计划API/功能用例排序|sortNode 使用 testCollectionId 经 test_plan_collection 关联真实 test_plan；服务校验移动节点/目标节点/集合/计划/项目一致|不允许通过合法集合ID排序其他集合或项目节点；拒绝前无位置写入，无迁移|补同范围通过、跨集合/计划/项目/不存在拒绝单元与普通成员集成；待验证|

本节13个相关Java文件在384 MiB上限下编译通过；共享排序安全边界4项、覆盖337处getter及根参数引用的全仓控制器契约1项，共5项通过。真实普通成员排序200/撤权403、跨计划拒绝且位置不变的集成已补，待后续CI。测试夹具只交换原来绑定错计划的两个collection引用，保留用例身份和各计划数量；没有迁移或改写业务历史数据。

### 6.14 首次全模块收集与共同根因（实施中）

CI 40836860193 / b9ff143c82 收集10模块2079项：15 failure、328 error、8 skipped。原80项、SDK11、系统546、项目299、API271、app现有2项全部通过。case279中177error、plan130中123error、dashboard16全部error：三者共同缺少 qualified functionalImportEffectsScheduler，并非316个独立业务缺陷。Agent451有1failure/12error/3skip；缺陷74有14failure。原始模块XML统计见verification-round14.json；上下文初始化错误不视为实际执行业务路径。

|根因/影响范围|修改范围与内容|通过标准|
|---|---|---|
|导入副作用/评审重算调度器只定义在app层，模块应用无法加载|将既有默认与专用调度器移到case-management功能配置，app保留指标；不禁用定时任务|最小Spring上下文启动、两种调度器独立；用例/计划/工作台真实上下文正常加载|
|Agent真实MySQL测试依赖quality_verify库未创建|CI在自己的MySQL服务显式创建该专用空库；不接触用户环境|所有quality.mysql条件测试实际执行，不以跳过代替|
|AgentCaseImportMySqlTests只执行112旧迁移|夹具补123/130/135，按真实WEB提交规则保存IMPORT_ONLY明确选择；不绕过提交校验|暂停/恢复、事务回滚及重复提交保持原断言通过|
|Outbox手工构造测试遗漏新增assets依赖，实际未进入它声称的hub失败路径|仅测试注入真实存在的CaseAssetService mock，保留hub故障/重试/事务断言|首次失败可重试、随后一次成功且业务写入不重复|
|Redis/外部图片网络共8项条件测试未启用|CI添加独立Redis服务16479，启用quality.redis及external.image.network|8项都实际执行；零跳过门禁保留，外部源失败如实失败|
|缺陷14项|核对旧500/GET删除、状态409专用流转、附件前置数据中断；按真实路由与业务边界修正，500进一步溯源|普通字段更新不绕过状态流转；附件真实字节及数据库结果核验|

具体缺陷修改：旧GET删除改为现有DELETE/POST，缺失项目ID的批量删除补合法项目；编辑夹具使用数据库当前状态，不绕过专用状态流转；11标签更新沿用真实截取前10条规则并检查持久化；空集批量恢复按既有幂等无操作规则检查数据库不变。附件缺失返回精确业务错误，真实预览/下载比较原始字节；升级只测试已关联文件，并在生产服务拒绝缺失/已删/其他缺陷关联及跨项目文件后再执行升级，防止空指针和越界写。评论不存在/非本人操作改为安全404/403，删除测试核验实际数据库结果。

本节15个Java文件有界编译成功；独立调度器最小Spring上下文1项与附件范围3项，共4项本机通过。真实MySQL、Redis、MinIO及外部图片需后续CI；不因局部通过宣称328项已全部修复。下一轮downstream范围只运行缺陷、用例、计划、工作台、Agent与app六模块，使用独立模块门禁，暂不重复SDK/系统/项目/API测试；最终仍需full验收。

汇总JSON生成后因upload-artifact默认排除隐藏文件而没有上传；配置显式include-hidden-files，同步修复既有健康证据的隐藏文件上传。门禁本身已正常拒绝失败套件，没有放行镜像或健康步骤。

进一步静态核对HTTP调用，发现缺陷删除/关注/取消关注仍有7处旧GET测试，状态matcher同样误作为URI变量，原“通过”并未验证行为。改为真实DELETE/POST、显式andExpect，并检查软删/物理删除/关注关系数据库结果；组织模块2处本就故意测试错误方法的405，保持原HTTP方法，仅修复显式断言。该补充待下一轮/最终回归，不把此前绿色结果作为这些分支的证据。

### 6.15 六模块复验与共享测试断言修复（实施中）

CI 40843550676 / 564297b1ce：1072项，72failure、65error、0skipped、0缺失模块。app3全通过，Agent462仅1failure/0error；真实Redis7项及公网图片1项实际通过。缺陷77有4failure；用例280有44failure/62error；计划134有20failure/2error；工作台16有3failure/1error。调度器缺失造成的上下文阻断已消除，新增真实普通成员排序200/撤权403及跨计划拒绝测试通过。原80项在本次定向范围没有执行，不能冒称再次通过。

|共同根因/需求|具体修改范围|验收尺度|
|---|---|---|
|UID工具静态final在Spring就绪前（如单元静态mock）缓存null，随后23个真实接口及23个初始化路径失败|IDGenerator改为调用时解析现有DefaultUidGenerator单例；不改算法、worker分配、ID位宽，不创建备用时钟ID|类提前加载不污染后续初始化；不可用时明确失败；上下文就绪后恢复；仍使用真实单例分配UID|
|共享SQL引用creationSources/category属性，Provider DTO缺字段|TestCasePageProviderRequest补与功能用例分页一致的三个可选筛选字段|旧调用默认不筛选，显式值按现有绑定SQL筛选，关联列表实际可查询|
|空选集批量恢复在日志中拼接IN()|BugService.getBatchLogByRequest空集提前返回，不执行查询或产生变更审计|合法空集幂等无操作；数据库不变；不放开跨项目ID|
|重复附件删除导致NPE，标签测试误带已删除附件|BugService移除附件前完整校验ID；标签测试只提交标签和元数据，不重发已消费附件；保留状态流转保护|缺失/其他缺陷附件明确拒绝且不发生存储删除；标签前10条正确持久化|
|共享测试客户端把ResultMatcher吞进URI变量|BaseTest在GET/POST/multipart/upload等辅助路径拆出并真正执行matcher，保留真实URI变量；新独立MockMvc测试用错误预期主动证明会失败|不能以漏断言获得绿色；当前3项独立测试通过。影响所有使用者，下一轮必须全量回归，包括此前绿色模块|
|已观测旧错误契约及夹具连锁|保留本轮精确响应与实际业务码证据，按根因修复后再处理后续分支|不以任意4xx、跳过或宽松断言代替；尚未执行分支不记通过|

## 7. 交付与验证边界

- [本轮累计文件清单](../testing/msp-ci-failures-20261029/changed-files.json) 记录分析基线及代码提交。主要产品文件包括 RoleRelationWriter、ExtUserRoleRelationMapper、OrganizationUserRoleService、CommonProjectService、PermissionControlService、BaseOrganizationParameterService、OrganizationTemplateService、BaseUserRoleService、PermissionControlMapper、SystemProjectController、ProjectController、ProjectUserRoleService，以及前端系统用户组页面、共享用户组组件与 useModal。其余为权限资源/语言资源、定向及控制器集成测试、夹具和 CI 门禁，详见清单。
- 前后端对应：用户组操作页面/共享组件调用既有系统角色与项目角色接口；新元数据权限与接口一致，成员权限独立保留；项目切换的真实导航栏调用保持 projectId/userId 契约；失败请求保留错误提示并保持弹窗可重试。服务端校验和持久化均为真实实现，没有新增 Mock 业务端点。
- 实际执行的远端主命令：`pnpm run test:selection-toggle && pnpm run test:weekly-remediation-sfc && pnpm run test:modal-contract`；对修改的 3 个 TS/Vue 文件及测试脚本执行 scoped eslint；`pnpm run build` 包含 `vue-tsc --noEmit && vite build`，第 6 轮全部通过。后续仅在 frontend 树哈希完全一致时复用该成功产物。
- 后端远端主命令：`./mvnw -B -pl backend/app -am -Dquality.mysql=true -Dapi.version=1.44 -DskipTests=false package`。原 80 项按 XML classname/testcase 逐一核对，不能以运行总数抵消或推断缺失项。
- 本地补充命令：`javac -J-Xmx384m -J-Duser.language=en @.codex-tmp/ci-remediation-20261029/compile-round7.args`；`java @.codex-tmp/ci-remediation-20261029/unit-round7.args`，当前项目角色 5 项通过；此前系统相关 37 项通过。Node 弹窗运行时 5 项以 96 MiB 堆限制通过。局部编译、单元测试不替代真实数据库和应用端到端验收。
- 未完成检查及风险：完整 reactor 下游、应用全量迁移/镜像构建/启动/健康必须等全部测试成功；修改后真实浏览器的允许/拒绝角色按钮、删除失败保留弹窗、删除成功持久化及项目切换尚未在部署版本验收。这些缺少证据时整体状态为**部分完成**，不能合并为已验收版本。
- 可复验的准确入口：`gh workflow run msp-remediation-verification.yml --repo chenqifen-miduo/metersphere --ref codex/weekly-remediation-20261028`。工作流顺序执行前端、真实 MySQL reactor，只有测试成功才执行 `bash deploy/quality-verify/ci-health.sh`；该健康脚本刻意限定 GitHub 临时 runner，不应在本机或共享服务上强行执行。已在运行时可用 `gh run watch RUN_ID --repo chenqifen-miduo/metersphere --exit-status` 查看既有结果，无需重复触发。
- 本机没有执行完整 Maven、Docker 或应用启动，原因是保留用户要求的低资源策略；本机重型 Git hook 不作为通过证据，正式类型/lint/构建门禁仍在远端开启。未将完整 HTTP/session 日志纳入版本库，结构化证据只保存必要的用例标识、业务提示及统计。

### 6.16 第15轮根因修复与完整复验准备（部分完成）

基线为 CI 40843550676 / 564297b1ce：六模块1072项，72 failure、65 error、0 skipped，不能视为验收通过。脱敏明细见 verification-round15.json。本轮没有启动本机 Docker 或应用服务。

|需求/问题|前后端与修改范围|数据、边界及验收|
|---|---|---|
|创建/导入/附件请求500|system UID工具在调用时获取Spring管理的生成器，避免类首次加载缓存null|不改ID算法、工作节点或ID格式；未初始化明确失败；生命周期2项单元通过|
|关联用例/工作台查询500|共享Provider查询DTO补齐creationSources、assetCategoryId、includeAssetCategoryDescendants，沿用现有查询接口|默认null兼容旧调用；关联查询真实MySQL待复验|
|空批次缺陷操作500|BugService空ID集直接返回空日志；删除附件先校验请求ID均属于当前附件|不生成IN()，不删除其他数据；缺陷集成待复验|
|用例附件删除与镜像同步|功能用例附件服务显式区分projectId/userId，HTTP入口、用例更新调用传递真实操作人|缺身份/跨项目在写入前拒绝；已不存在附件不增加版本/不重复入outbox；3项单元通过|
|计划执行权限与同步|计划执行服务核对关联行、计划与请求项目，核对源用例未删除并拒绝归档计划；保留显式跨项目关联，附件使用源用例真实项目|源用例与所有活跃计划同步原有路径保留；4项安全边界单元通过，真实持久化仍待集成|
|请求间分页串扰|RequestTraceFilter入口及finally清理PageHelper线程状态|避免上一请求未执行SQL时LIMIT污染下一请求；成功/异常清理单元通过|
|正文损坏协议|HTTP422同时返回业务code100422，保留bodyInvalid键及原文保护|正向夹具写合法步骤数组；损坏负向夹具继续保留；错误协议单元通过|
|关系查询排序歧义|关系服务使用现有排序字段格式校验并限定fc别名，XML去除重复前缀|不放宽sort输入；真实关联分页待集成|
|MCP个人执行证据|数据库集成夹具按用例级evidenceIds提交|保留回滚、重复请求、已消费证据拒绝、100步单图、跨项目读取拒绝等验收；待真实MySQL复验|
|测试断言被静默忽略|BaseTest将误传至URI参数的ResultMatcher实际执行，覆盖GET/POST/multipart等|新契约3项通过；影响约147处旧调用，因此下一轮必须十模块全量，不沿用旧通过数|
|旧协议与测试连锁失败|按第15轮真实响应修正缺失资源422/业务码；默认启用项目模块的测试前提改正，保留禁用模块拒绝测试|不统一放宽为任意4xx，不接受500；授权唯一约束保留，仅改夹具为存在检查后插入|

本机40个相关Java文件以javac -J-Xmx384m编译通过；java -Xmx256m定向执行32项，32通过、0跳过。命令参数保存在本机 .codex-tmp/ci-remediation-20261029/compile-round16.args 和 unit-round16.args；正式可复核远端入口为 .github/workflows/msp-remediation-verification.yml。git diff --check通过。

仍须执行：十模块全量集成/打包、数据库迁移、镜像构建/容器启动/健康检查、核心路径E2E。当前代码尚不能标记为全部完成，失败门禁仍保持开启。前端未修改，继续按精确源码树哈希复用既有正式类型/lint/生产构建产物。

### 6.17 第16轮完整结果及后续修复（部分完成）

完整运行 40853057396 / 0f95b5a9b2 已执行十模块2113项：79 failure、4 error、0 skipped、无缺失模块；原始80项逐项全部通过。模块及用例清单见 verification-round16.json。前端复用精确树哈希产物并通过，后端门禁仍失败，未执行镜像/健康步骤，未合并。

本轮继续定位并修复以下实际缺口，尚待下一轮完整集成：

|影响路径|根因及修改内容|修改/数据范围|验收证据与待验证|
|---|---|---|---|
|用例关系候选/工作台/用例分页|共享查询增加关联表后默认pos或客户端排序未限定表名；三个HTTP入口统一限定functional_case并保留ID稳定排序|三个控制器；无迁移、无前端请求字段变化|真实MySQL分页待复验|
|删除含用例目录|checkCaseByModuleIds仅投影ID等字段，生命周期删除按projectId分组时得到null|Mapper补project_id/deleted；复用现有锁、删除保护、评审及outbox链路|现有删除目录集成检查删除标识与回收站，待复验|
|场景分享报告不存在|ApiScenarioReportService抛RuntimeException导致500|改为既有安全MSException，保持业务拒绝协议|分享报告负向集成待复验|
|环境更新不存在/跨项目|update未先读目标，0行更新也返回成功；请求projectId可覆盖来源项目|EnvironmentService先检查存在与真实项目；失败前不更新、不上传附件|新增2项单元通过；真实缺失与重复名称集成待复验|
|报告生成错误返回成功|genReport捕获所有异常后返回空Map，失败被误报成功|保留完整内部日志，业务异常原样传播，未知异常返回安全提示；沿用事务|新增2项单元通过；正向生成/失败回滚待完整集成|
|偶发Redis上下文启动失败|实际Redisson3.49字节码按随机delay%100/2生成tick；余数1得到0，触发Netty拒绝|默认单机/集群/哨兵配置保留原延迟策略与重试次数，向上对齐100ms，不改变地址、密码或超时|1501ms边界、尝试次数传递、10000次默认抖动采样3项单元通过；真实启动待复验|
|MCP数据库证据夹具|生产发布SQL已维护functional_case.update_time，独立临时表仍缺字段|仅补测试临时表真实字段与初值，不改生产表|Agent真实MySQL回滚/发布/幂等路径待复验|
|评论更新/删除旧测试|旧请求未带expectedUpdateTime，仍调用已关闭旧删除入口，且期待物理删除和级联删除回复|测试改为读取真实版本再更新、版本化POST删除；验证软删除、回复保留、重复删除版本不变；新增缺版本/冲突/非法回复无写入场景|测试修改待真实HTTP/MySQL复验；不放宽作者与版本约束|

对于返回200的旧负向断言，逐项检查业务含义：clear=true重复清空标签是有效操作；空选择/不满足归档条件的批量操作按既有逻辑跳过；停用三方平台后的模板读取允许回退。这些测试改为验证持久化字段或未改动的排除项，而非将所有200视为错误。反之，不存在环境更新、报告生成异常已按产品缺陷修复，不能仅改断言放行。

保留显式跨项目计划关联能力：核对真实plan-case关联与请求计划项目，附件目录使用源用例项目；不允许替换未关联caseId。恢复跨项目来源夹具并补源用例和计划行状态落库断言。本轮也修正上一轮测试调整中过度替换的断言：只对新项目默认模块启用改预期，不存在项目/类型仍必须拒绝；评审日期错误不应断言为“评审不存在”。

本机单实例低内存检查：39项定向单元通过，0跳过；脚本门禁4项、失败详情提取2项通过；workflow YAML解析和git diff --check通过。完整编译/集成的远端结果仍是最终依据。新增私有失败详情产物仅包含失败/错误/跳过testcase，剔除环境属性和通过用例日志，标记diagnosticOnly；不作为通过证据，不提交原始HTTP/session日志。

### 6.18 第17轮收敛结果与本轮剩余修复（实施中）

完整运行40862145183 / eba4337912：十模块2121项，15 failure、2 error、0 skipped、无缺失模块。SDK11、系统556、项目301、工作台16、app3全部通过。原始80项的逐项证据仍见第16轮；第17轮所在系统模块556项全部通过。本轮失败明细仅约190KiB，完整原始报告同时保留。脱敏标识见verification-round17.json。

已定位的17个失败/错误并非17个独立产品缺陷：

- 产品缺口：删除目录时生命周期已将用例移入回收站，但module_id仍指向被删除目录；补同事务、同项目、已删除用例的root归档，保留活跃任务保护和通知/outbox链路。缺陷评论的checkBug仍抛IllegalArgumentException；改为108001安全拒绝，并先校验回复父评论属于当前缺陷再处理附件。Swagger定时同步更新缺少cron的Updated组NotBlank约束；补齐，避免trim空值。MCP个人执行同步SQL原仅更新同项目计划，改为更新该源用例实际关联的所有未归档计划；源用例更新仍严格检查projectId/未删除/影响行数1，归档计划和其他用例不变。
- MCP数据库夹具：独立FunctionalCaseService未装配outbox，成功发布到达真实写入后NPE。改用真实119/127迁移临时表、真实FunctionalImportEffectOutbox和FunctionalReviewRecountService；新增outbox存储约束失败后的用例状态、版本、证据关联、提交记录、通知队列全部回滚断言，再恢复约束重试，验证重放不重复入队。另加跨项目活跃计划同步、归档计划及无关用例不变的真实SQL断言。
- 测试契约/前置条件：两个上传空文件名断言藏在公共辅助方法中，更新为精确422协议；API等级为空/批量类型错误/缺失场景报告改为100422安全业务拒绝。定时任务参数测试使用有效已有任务ID，单独触发字段校验。缺陷附件读取错误比较最底层原因，不依赖异常包装层数。评论辅助查询改为按主键读取完整正文，避免把未投影的null当正文快照。计划组第21条应拒绝容量上限，而非模块停用；其提前退出引起计划数量连锁失败。归档复制测试使用独立归档组夹具，不读取已被前序删除的ID。XMind count为空沿用服务器解析语义，空文件返回失败明细且无新增用例，而非将HTTP200批量结果等同于导入成功。

本机新增缺陷评论边界2项单元通过；最近一次相关Java编译通过。SQL/outbox回滚、计划容量完整流程和新增浏览器脚本仍待第18轮真实集成，不预先标记通过。

### 6.19 增量复验与核心用户路径验收（待执行）

为遵守“不重跑未受影响的已通过项”，新增affected范围：只对API、缺陷、用例、计划、工作台、Agent、app重新执行；SDK/系统/项目只能复用第17轮成功的868项。复用前核对12处POM、包装器、上游源码/测试及依赖树哈希，并逐个核对源XML确有testcase、与源汇总数量一致、无失败/错误/跳过；不匹配即拒绝，需full运行。保留源运行号、源提交、各依赖树哈希和模块计数；最终门禁仍覆盖十模块。汇总分开记录currentExecutionModules与retainedEvidence，不能将复用数量报告成本轮实跑。SDK/系统/项目生产或测试发生变更时不可使用该增量范围。

门禁还新增每条实际testcase标识/结果清单，并校验XML计数与testcase正文一致，拒绝只有成功计数却缺测试正文的报告。控制台不打印全部用例，精简汇总产物不记录HTTP头/令牌；原始私有测试报告仍可能含请求信息，不提交仓库。失败明细产物仅用于定位，标记diagnosticOnly，不能充当全量覆盖证明。

新增deploy/quality-verify/core-smoke.mjs，由同一隔离CI环境在镜像/迁移/健康通过后串行执行：

|检查点|真实用户/接口链路|通过尺度与证据|
|---|---|---|
|CORE-LOGIN|生产静态前端→管理员登录→真实会话|登录成功、生成有效会话；仅CI空库启用既有管理员入口|
|CORE-MCP-CREATE-IDEMPOTENT|个人Token→MCP创建目录/用例→相同请求重试|同一caseId；使用正式MCP requestId，不使用Mock|
|CORE-PLAN-ONE-SELECTOR-AUTO-WIDTH-ALIGNED|正式API创建两个不同长度计划并关联用例→真实用例页面切换|始终一个选择器/进度条；名称切换宽度自适应；中心线差≤3px；原始/编号标注截图及DOM边界JSON|
|CORE-SELECT-ALL-TOGGLE|常驻全选按钮→取消全选|按钮文字变化且真实行复选框勾选/取消，非仅检查文字|
|CORE-MCP-CASE-IMAGE-PUBLISH-REPLAY-DOWNLOAD-PLAN-SYNC|真实页面截图→MCP上传→用例级evidenceIds提交→原请求重放→原图读取→两个计划读取|发布SUCCESS、相同submissionId、原图字节一致、源用例与两计划执行结果一致|
|CORE-PLAN-REENTER-FRESH-STATE-AND-SWITCH|进入两个计划详情及测试用例页签|名称/当前计划正确，直接看到最新执行状态，无手动刷新|
|CORE-MCP-REVOKED-TOKEN-DENIED|停用个人Token→MCP读取|拒绝访问|

脚本位于frontend目录外，前端源码树哈希不变，继续复用既有正式类型/lint/生产构建产物。仅在GitHub临时库运行，使用独立项目/数据、同一套容器和单个无头浏览器；先停用本轮测试用的Redis/MySQL容器，健康与冒烟结束后统一销毁临时卷。本机不启动Docker或浏览器。此处截图是实际页面证据，不是已确认像素基准；不能据此声称全平台视觉回归通过。

已执行静态检查：node --check core-smoke.mjs、Git Bash bash -n ci-health.sh、workflow YAML解析、门禁6项、失败详情2项、复用证据3项脚本单元、git diff --check。浏览器/镜像/健康/数据库真实回滚与增量合并门禁尚未执行，当前整体状态仍为部分完成。


### 6.20 第18轮验证启动与运行阶段隔离（进行中）

提交4d7b200bd068b0dc00d0eabc1b6b6407dbab28b8已推送修复分支；运行40870448518正在执行affected集成。提交后12处上游依赖树哈希再次核对一致；前端精确源码树产物复用成功。当前尚未得到最终测试、镜像、健康或浏览器结论，未合并chenqifen/main。

静态复查发现新冒烟脚本创建项目路由误写为organization-project/add，实际控制器是organization/project/add，已修正脚本；此项不是生产接口缺失。为避免仅运行验收脚本变化仍重复执行通过的后端集成，增加独立runtime验收入口：必须先确认来源运行的十模块汇总门禁成功，精确匹配后端/前端源码树、构建输入、来源提交/运行号及JAR SHA256，再在一套新的临时容器串行完成镜像、迁移、健康与页面/MCP验收。缓存不存在、来源失败或哈希不同均拒绝复用；不跳过运行验收。本轮4项复用拒绝/接受边界单元通过；独立runtime入口尚未实际运行，不能作为通过证据。


本轮产品修复追踪（验收状态均以第18轮实际结果为准）：

|需求/异常流程|既有前端入口与请求|后端修改|数据/配置影响|验证与当前状态|
|---|---|---|---|---|
|删除含用例目录后回收站归档正确|测试用例→目录删除；沿用既有请求/权限|FunctionalCaseModuleService.deleteModuleByIds；复用生命周期保护后按项目归档root|同事务更新已删除用例module_id；无新增迁移|目录删除集成待复验；不得绕过活跃执行保护|
|缺陷评论缺失资源/越界回复安全拒绝|缺陷详情→评论/回复；沿用现有错误展示|BugCommentService.checkBug/checkAndSetReplyComment；先验证再转附件|无新表；拒绝前不写评论、不转附件|新增2项单元通过，HTTP/MySQL待复验|
|Swagger同步定时配置更新必填校验|接口测试→Swagger定时同步编辑|ApiScheduleRequest.value增加Updated校验组|无数据格式变化；cron为空拒绝更新|有效任务ID下负向集成待复验|
|MCP个人执行同步实际关联计划|既有个人Agent Token/MCP execution.submit；计划详情读取|FunctionalCaseService.publishPersonalExecution；按真实关联更新所有非归档计划|沿用源用例权限/版本条件与事务、队列；不改无关用例及归档计划|新增真实MySQL跨项目、回滚和幂等断言待复验；核心页面/MCP验收待执行|


### 6.21 第18轮结果与第19轮剩余范围（部分完成）

第18轮40870448518结束：实际执行1255项、复用源码一致868项，十模块合计2123项，2失败/1错误/0跳过/无缺失，XML计数一致性校验通过。API271、缺陷79、用例284、工作台16、app3本轮全部通过。完整脱敏testcase清单及复用来源见verification-round18.json。镜像/健康/页面MCP仍由门禁阻止，未执行，未合并。

剩余3项已定位：计划testPlanAddTest在计划组归档处失败，testPlanPageCountTest随之出现状态计数连锁失败。夹具仅写关联行SUCCESS，源用例却是PREPARED，而且用同一源用例构造SUCCESS/PENDING两种关联状态；与现已采用的源状态投影不一致。修正为真实SUCCESS源用例、另一个独立PENDING源用例，保留归档和统计预期，不修改生产统计或降低归档条件。AgentDirectExecutionMySqlTests已完成发布/回滚/重放/跨项目同步等前置断言，最后执行视图读取缺case_asset_source_relation；补72号迁移中真实映射表DDL、项目身份及源版本字段，同时补实际资产映射读取与清空当前执行引用后的无历史回退断言。

修改的6个Java编译单元以384MiB上限编译通过。第19轮仅执行计划和Agent，成功模块改从第18轮复用1521项；复用必须校验21处源码/构建依赖树，其中包含Agent/计划生产源码和POM，只有这两模块测试发生变化才允许保留其他模块证据。所有生产代码不变，正式前端产物继续按树哈希复用。未执行的单独runtime入口暂不提交启用；本轮优先消除剩余测试失败。


### 6.22 第19轮剩余单项与覆盖清单核对（部分完成）

第19轮40874331855：计划140项全部通过；Agent461项1错误，其余8模块复用1521项。零断言失败/零跳过，但仍有1错误且比第18轮少1项，不能声称完整通过。最后错误为MySQL的临时project表被生产查询自连接两次，触发1137 Can't reopen table。修正测试为每次建立随机quality_direct_独立库，project使用普通表；finally删除该独立库并关闭连接，生产SQL不改。其余事务表仍是该连接的临时表。不得在公共业务库建/删project表。

通过逐条比较第18/19轮executedTests确认漏项是Agent模块中包名为io.metersphere.functional.controller的AiSourceDocumentControllerSecurityTests.documentDetailShouldRequireProjectOwnershipAndAiReadPermission；仅按agent包过滤漏掉这项。第20轮显式纳入该类，只重跑Agent，计划140及其他通过模块合计1661项按20处依赖树验证后复用。新增基准testcase清单门禁，以第18轮完整2123条记录为最低范围；同一模块里漏类也拒绝通过，新增2项门禁回归并对第19轮真实汇总验证确实检测到此漏项。第19轮JSON已补录缺失清单，保留实际601实跑/1521复用边界，不将2122条当完整范围。Java有界编译、8项门禁与3项复用工具单元、YAML与diff检查通过。第20轮尚待执行，容器/页面验收仍未执行。


### 6.23 十模块测试通过与首次容器/浏览器实测（部分完成）

第20轮40876632979 / e9bdac6fea：十模块2123项，0失败/0错误/0跳过/0缺失模块/0缺失testcase/0报告计数错误；本轮Agent462实跑，其余1661项按源码树校验复用。完整清单和来源见verification-round20.json。包括此前漏掉的AiSourceDocumentControllerSecurityTests；不得把保留证据声称为本轮重跑。正式前端继续复用一致源码产物。

真实运行阶段已构建生产Dockerfile镜像sha256:79ff043749d472f8dd4106f45fdef58e2a94c85fc04bfd54013dde8ef8762292，173条迁移到v3.7.2.136成功，readiness UP、静态前端HTTP200、真实Chrome管理员登录通过。本轮核心MCP/页面冒烟未完成：项目创建已自动生成同名目录，而脚本再次用项目名调用module.create，正确收到MODULE_NAME_CONFLICT。只将冒烟目录改为独立名称，不修改生产重名校验，不把该拒绝当成功；后续检查尚未执行。

启用此前准备的独立runtime入口，来源固定本轮通过测试门禁的运行40876632979，恢复其已保存JAR/构建身份/汇总缓存；逐项检查来源、后端/前端树、POM及包装器输入、JAR SHA256和十模块无失败/跳过后，再重新建一套临时容器运行验收。不会重新Maven测试/打包或前端构建。本机4项包校验回归、JS语法、workflow YAML、diff检查通过；下一次运行尚未开始。当前仍未合并。


### 6.24 独立运行包恢复拒绝与缓存输入修正

独立runtime运行40878930561在缓存恢复前失败，未启动容器、未重跑测试。已证实源缓存输入哈希91ad8d…与干净工作区b1d5e2…不同；原hashFiles(.mvn/**)包含mvnw执行时自动下载且未纳入Git的.mvn/wrapper/maven-wrapper.jar，而新工作区没有该文件。原键不只取受控源码，属于验收基础设施缺陷。

修复：未来构建键只包含受控POM/包装器properties及脚本，排除target；已有第20轮包不能重造哈希，改为按相同后端/前端树在本分支查找唯一已验证缓存，恢复后严格检查原记录sourceCommit/sourceRun/构建输入标识/JAR SHA256及十模块通过证据，并直接比较源提交与当前Git的pom.xml/.mvn/mvnw/mvnw.cmd。找不到、歧义或任何源码/哈希不同均拒绝，不降级重编译冒充复用。新增受控输入改变时拒绝复用回归，5项通过。下一次只重试运行验收；应用源码不变。


### 6.25 官方镜像层下载阻塞与有界运行验收

运行40879844610已由30分钟上限结束（cancelled）。缓存包的来源、源码及SHA256校验通过，但Docker构建仍在下载原registry.fit2cloud.com基础镜像的53.26MB层；日志显示1711秒时仅23.07MB。没有启动app或浏览器，本轮不能误报为MCP业务超时。原镜像manifest digest为sha256:3245ef659597f134904fdd18d99c4dff1106d9abd1bec56bbbf61374c72ef897，慢层sha256:604b940e7f70da136149b40db3d506904c04c23a063f3da23377c3d5feec68b1。查询候选镜像源未证实有可公开获取的同digest镜像，未更换生产基础镜像。主动取消接口返回403，当前GitHub凭据无取消权限；未绕过或声称取消成功。

增加构建8分钟、浏览器外层5分钟/脚本内层4分钟上限；依赖及app原180/360秒上限保留；每个子阶段写ci-phase、每个浏览器检查点及时写summary。构建完成即按JAR SHA256、Dockerfile/wecom-bridge/入口脚本Git身份保存应用镜像缓存；后续只改测试脚本时直接校验并加载同一imageId，不再重复构建。新环境仍执行完整迁移/启动/健康及实际页面路径。缓存不存在才构建，错误键/包/镜像ID均拒绝复用。

补入两处此前仅有修改和历史问题截图、尚缺本次部署路径证据的UI验收：通过真实MCP读取模板并创建6个长标签缺陷，检查标签区域多行且不与预计解决时间或后续元数据重叠；个人中心检查启用/操作列完整位于1366宽窗口内，并实际UI停用/启用Token验证MCP权限变化及打开设置弹窗。仅写临时CI库，不使用原业务数据。上述新增脚本语法/工作流解析/bash语法/diff检查通过，实际执行待下一轮。整体仍部分完成，未合并。


### 6.26 实际发现计划概览样式未生效（修复与复验中）

运行40885756426成功完成镜像/迁移/健康、登录及真实MCP目录/用例创建和case.create重放。浏览器在计划选择框/进度条中心线断言处失败；截图runtime-round23-plan-layout-before.png可见选择框下移。定位到Arco Select渲染返回Fragment数组，class传到SelectView但未承接父SFC的scope标识；普通scoped .case-overview-plan-select未命中，grid-area:1/1和height:32px没有施加，选择框排在隐藏宽度测量span的下一网格行。源库组件实现可核对frontend/node_modules/@arco-design/web-vue/es/select/select.js的render trigger数组。此项是实际前端缺陷，不能放宽3px断言。

仅将caseTable.vue该规则改为:deep(.case-overview-plan-select)，仍限定当前组件祖先，保留既有按钮、事件、权限、API、名称测宽与工具提示。改动影响当前用例列表计划概览的选择框布局，不改数据/数据库/后端。新增前端构建必须执行类型/Vite生产构建、SFC/交互契约和该文件stylelint；本机192MiB的8个SFC检查、目标stylelint与6项包/前端身份校验单元通过，远端生产构建待执行。

为不重打后端，将前后端验收分开：后端JAR及镜像复用第20/23轮，要求当前backend Git树、受控外层构建输入及JAR/imageId一致；当前frontend必须由同轮前端job构建或按精确frontend Git树复用成功产物，并验证产物sourceCommit确实具有该frontend树。浏览器代理实际提供当前生产前端，API请求进入已校验后端；代理根index内容还逐字比对当前dist/index.html。后端JAR内嵌静态资源仍为第20轮版本，不将其声称为新前端；此处验证独立前端/API部署模式，生产发布如使用内嵌前端模式仍须在正式打包流程重新组装。后端源码不变，无需重跑2123项；改动前端必须正式构建，不把旧产物+临时CSS当实现。

第23轮已按JAR/Dockerfile/bridge/入口脚本身份保存451MB应用镜像缓存；后续加载同一imageId，避免重复拉取慢基础镜像或重建应用。验收证据见verification-round23.json。当前UI修复、长标签、Token列交互、后续MCP提交/附件/计划详情路径均待下一轮实际验收，仍不合并。


### 6.27 新前端构建通过与缓存路径契约补修

运行40888480458 / ff552bc0f3的frontend job全部成功，包含选择切换/SFC/modal契约、caseTable.vue stylelint、vue-tsc和Vite生产构建；新frontend树df82344053b5c5199271586145b87144ecae001f产物已缓存。runtime尚未启动容器：误将.ci-frontend-build.json加入旧后端缓存恢复path，虽然key相同，但Actions会将path集合计入缓存version，导致找不到旧记录。此项是本次工作流编辑错误，不是依赖被删除或后端失败。

已恢复后端生产端/恢复端完全相同的3个路径；前端元数据仅在其独立产物/缓存及验收证据内。增加3项真实workflow生产/消费缓存path契约测试，包括该误增路径负向回归，并在恢复前执行。新前端、后端JAR和应用镜像可分别复用，无需重跑已过构建。配置单元及YAML/diff检查通过；剩余实际页面/MCP验收继续执行，未合并。


### 6.28 修复后实际对齐、选择和MCP发布通过；继续页面验收

运行40890432868 / 8591abc71c已校验并恢复相同应用imageId 0150c852…，未重建应用或重新拉取慢基础镜像；新前端也按源码树复用。173条迁移、readiness及新前端index逐字校验通过。实际浏览器通过：登录；MCP目录/用例创建及case.create重放；只有一个计划选择器，短/长宽273.70/581.69px；选择框y364高32、进度条y377高6，中心线均380、偏差0px；全选/取消按钮及真实复选框；单张用例级截图上传/提交/重放/报告/原图字节一致/两个计划执行结果同步。截图与详情见verification-round25.json及runtime-round25-plan-summary*.png。

本轮停在计划详情“测试用例”入口的脚本定位：MsTab使用Arco渲染，实际入口没有tab角色，截图已显示正确计划名称与1/1执行成功。按真实.plan-detail-sticky内精确文本定位，不修改产品或放宽最新状态断言。截图增加等待下拉实际收起，避免动画残影；用两个不同计划关联集合验证上下文切换：短计划仅主用例，长计划另含一个未执行校验用例，防止两计划恰好同一用例列表掩盖缓存错误。长标签缺陷同时关联主用例，额外验证两个计划立即读取到相同缺陷，校验用例不参与执行。

本轮已通过项不是最终全部通过；计划详情列表切换、源缺陷关联、长标签和Token控件实际UI仍需后续完成。下一次仅重试必要运行验收，不重复前端生产构建或后端测试。JS语法与diff检查通过。


### 6.29 计划切换、缺陷同步、长标签UI通过；剩余Token UI入口

运行40893337833 / 540f8a5e2c复用新前端及相同后端/镜像，完成173条迁移、健康及8个核心检查点。短计划仅主用例、长计划另含独立未执行用例的真实列表切换通过；源用例新增关联缺陷后，两个计划无需手动同步即可返回相同缺陷标题/数量；6个长标签区域高266px，预计解决时间底374.5/tag顶390.5、tag底656.5/元数据顶672.5，均有16px间距，实际截图已查看，未重叠。证据verification-round26.json/runtime-round26-bug-tags-layout.png。

剩余Token界面未测完：脚本全局按Administrator文本定位，匹配了其他仍保留在DOM中的处理人/筛选文本，严格模式拒绝。改为实际#app侧栏菜单下.one-line-text精确匹配，未改用户菜单或放宽Token开关/列边界断言；Arco Switch原组件role=switch/aria-checked已核对源码。UI启停及最终吊销仍待下一轮，整体不提前标记完成。前端/后端/镜像源码均未变化，继续复用已通过产物。


### 6.30 Token列已可见，保留几何/操作断言继续复验

运行40894938103再次通过前8项核心检查，成功进入个人中心Agent配置。实际截图runtime-round27-personal-token-columns.png已显示启用和操作两列、开关和设置/删除按钮在1366px窗口内；原“columnheader+精确可访问名称”定位没有匹配真实表头，导致脚本中止。改用原Arco表头类.arco-table-th及精确可见文本定位，仍要求列边界完全在个人窗口内并实际切换Token权限；不依据截图提前宣告开关功能通过。仅修改验收定位，不改生产表格。证据verification-round27.json；后续真实启用/停用/设置及最终吊销仍待完成。


### 6.31 Token列边界及真实启停通过；设置弹窗定位按源码修正

运行40896294060通过个人窗口内两列表头边界断言；真实UI停用开关后MCP读取被拒绝，重新启用后相同Token可读取原caseId。脚本执行到设置入口才因错误的button角色定位超时，前置断言均未报错；本次已把列边界/启停/设置拆为独立检查点，后续失败不再隐藏已执行结果。

MsButton源码是带clickHandler的div.ms-button，故设置按实际.ms-button文本定位；Arco Modal当前版本也未生成dialog角色，已根据真实.arco-modal/标题/取消按钮定位，并等待弹窗关闭。仍验证确实打开“设置”弹窗，不将按钮存在当操作成功。补真实鼠标水平滚动/回滚与中间列截图，要求表格存在可滚动容器并实际scrollLeft改变，避免只靠固定右列掩盖中间信息不可达。此项尚待执行；没有修改产品或注入改变布局/数据的样式。JS语法、diff检查通过；正式前端/后端及镜像继续按相同身份复用。


## 7. 本轮最终验证结果与交付边界（2026-10-29）

代码受验提交a315b8691c254b32a5bb86806c25e947bc89be52；后续若仅补本文/证据，不重跑未改变源码的构建。原始80项已逐项通过，十模块完整测试库存2123项零failure/error/skipped/缺失；第20轮实跑Agent462，另1661项按源码/依赖树身份从已过运行复用，不说成本轮全重跑。各testcase结果/模块来源见verification-round20.json。前端唯一新增生产修复是caseTable.vue的scoped穿透，实际构建运行40888480458已通过选择/SFC/modal契约、目标stylelint、vue-tsc及Vite；后续前端树df82344053b5c5199271586145b87144ecae001f严格一致而复用。

最终运行[40898154538](https://github.com/chenqifen-miduo/metersphere/actions/runs/40898154538)整体success；新临时库173条迁移至3.7.2.136、readiness UP、当前前端index逐字校验/HTTP200，以下13个真实检查全部通过：

|验收项|对应入口/后端与判定|结果|
|---|---|---|
|登录|真实生产前端管理员入口→真实会话|通过|
|MCP创建幂等|个人Token→目录/用例创建；相同case.create请求返回相同caseId|通过|
|计划概览样式|真实caseTable切换短/长名称，宽273.70→581.69px，只有一组选择器，中心线误差0px|通过|
|全选切换|全选所有页→取消全选所有页，真实复选框勾选/清除|通过|
|MCP证据/提交|单张真实截图作为用例证据→提交SUCCESS→请求重放同submissionId→报告AGENT_REPORTED→读取原图字节相同→源及两个计划状态一致|通过|
|计划进入刷新|短计划1用例/100%，长计划2用例/50%；切换后各自集合正确且主用例最新SUCCESS，无手动刷新|通过|
|源缺陷同步|MCP创建缺陷关联主用例；两个计划立即读到同一缺陷标题/数量，无手动同步|通过|
|长标签布局|真实缺陷6个长标签、区域高266px，上/下分别16px间距|通过|
|Token两列边界|1366窗口内启用列x1080宽100、操作列x1180宽140，完整在右边界1320内|通过|
|Token水平滚动|真实鼠标轮事件令876px窗口/1660px内容scrollLeft增加并回到0，中间字段可访问|通过|
|Token真实启停|UI停用→MCP拒绝；UI启用→同Token恢复读取原caseId|通过|
|Token设置操作|实际MsButton打开“设置”窗口并取消关闭|通过|
|最终吊销|正式API停用后MCP读取拒绝|通过|

完整摘要、Git树/包SHA256/镜像身份见[verification-round29.json](../testing/msp-ci-failures-20261029/verification-round29.json)。已实际查看最终计划概览（含标注）、两计划详情、长标签、Token右列及中间列截图；runtime-final-*.png均来自真实页面，未用静态Demo或Mock充当验收。数据仅为GitHub临时库，卷/容器结束清理；本机未启动Docker/应用/浏览器，没有并行多套运行环境。

### 7.1 修改范围和前后端对应

- 用例目录删除入口→FunctionalCaseModuleService→既有生命周期保护/回收站/root归档；缺陷评论入口→BugCommentService→资源/回复归属校验→附件转移/评论持久化；Swagger定时配置→ApiScheduleRequest Updated校验；MCP execution.submit→FunctionalCaseService→源版本/派生计划状态及outbox事务。前端API/权限沿用原入口，没有为修复增加人工录入流程。
- caseTable.vue仅调整选择框样式作用域，按钮/交互/接口不变。历史代码修复与全部测试/资源清单见changed-files.json及分支diff；测试夹具调整与产品修复在第6节分别记录。
- CI脚本加入实际testcase库存/源码复用校验、缓存生产消费契约、运行超时/阶段证据；不会以failure-ignore跳过最终零失败门禁。最终包/镜像是相同后端源码，浏览器使用独立的当前正式前端产物；现有CNB构建采用前端ZIP与后端skipAntRunForJenkins分离方式，此处不冒充新前端已重新嵌入旧JAR。

### 7.2 实际命令与复核入口

- 后端：第20轮workflow中`./mvnw -B -pl backend/app -am -Dquality.mysql=true -Dquality.redis=true -Dexternal.image.network=true -Dapi.version=1.44 -DskipTests=false -Dmaven.test.failure.ignore=true '-Dtest=io.metersphere.agent.**,io.metersphere.functional.controller.AiSourceDocumentControllerSecurityTests' -Dsurefire.failIfNoSpecifiedTests=false package`通过；其余模块按完整Git树/真实XML复用后，`python3 deploy/quality-verify/check-test-reports.py --inventory .ci-baseline-summary/.ci-test-summary.json`通过。
- 前端：`pnpm run test:selection-toggle`、`pnpm run test:weekly-remediation-sfc`、`pnpm run test:modal-contract`、`pnpm exec stylelint src/views/case-management/caseManagementFeature/components/caseTable.vue`、`pnpm run build`通过。
- 运行：`bash deploy/quality-verify/ci-health.sh`和其调用的`node deploy/quality-verify/core-smoke.mjs`最终通过；镜像首次构建用真实Dockerfile，后续精确校验并加载同imageId，仍全新建库/启动/验收。
- 脚本单元：报告门禁8项、失败提取2项、源码复用3项、包/前端身份6项、workflow缓存契约3项分别通过；有界Java编译及对应新增缺陷评论2项单元通过。不同阶段重复/重叠不累加为产品独立测试数量。
- 同身份隔离复核命令：`gh workflow run msp-runtime-verification.yml --repo chenqifen-miduo/metersphere --ref codex/weekly-remediation-20261028 -f source_run=40876632979`。依赖现有受验包缓存；源码或构建输入不同会拒绝，不能用它跳过新后端修改应有的测试。

### 7.3 尚未执行和状态边界

本轮CI/上述13条核心路径已验证通过；完整需求交付仍为部分完成，以下不因代码合并或CI通过自动关闭：用户测试环境重新部署后的真实Ego/客户端回放、生产/历史库敏感事实扫描及真实密钥配置、导入多客户端重启恢复矩阵、源/计划/资产解绑/删除恢复/版本全字段矩阵、真实业务XLSX全字段比对、更多视口/缩放与真实双客户端缓存刷新。没有将单测替代这些部署/数据验收。

原因是新代码尚未由用户发布到现有测试环境，且未提供历史数据库/实际客户端接入的执行前置条件；风险为环境配置、历史数据和客户端缓存行为仍可能与隔离库不同。发布制品入口为既有`gh workflow run cnb-build.yml --repo chenqifen-miduo/metersphere --ref main`（生成制品不等于部署服务器）；服务器部署需沿用现有流程，仓库没有可无条件执行的服务器部署命令，不虚构访问方式。正文恢复维护命令见scripts/maintenance/README-case-body-repair.md；测试坏记录100025已按授权移入回收站，未猜造原步骤或动生产数据。完整业务验收不能标“完全完成”。

本阶段依据用户既有授权整合已通过代码检查及核心隔离验收的代码；合并仅表示代码整合，不作为以上未执行项的通过证据。
