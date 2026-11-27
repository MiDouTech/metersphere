# 测试资产部署反馈修复：开发验证记录 v1.2（2026-11-27，草稿）

需求依据为本轮十二项测试数据反馈、两项用例资产反馈、一项版本权限反馈，以及用户确认“已使用允许再次复制，保留标识”。逐项前后端追踪见同目录plan.md。开发基线61864540c1；尚不代表测试环境或生产环境已部署本次补丁。

## 已定位的实际部署故障

上传在现有测试环境实测HTTP503，安全分类DATA_ASSET_STORAGE_UNAVAILABLE；原始错误被统一响应又包进100200。存储路径data-assets不符合真实MinioRepository限定的system/project/organization路径。现改为organization/<组织>/data-assets，保留仓库校验，异常处理取消双包装。证据upload-deployed-repro.json。

系统目录首开失败是父页面PROJECT分页与子页面SYSTEM分页使用同method/URL，前者取消后者。刷新SYSTEM请求正常返回1342条。现按当前目录启动唯一加载链，组织/目录切换丢弃旧响应。未放宽全局取消规则、未吞掉错误。证据system-directory-deployed-repro.json。

## 实现与数据契约

- 单文件30MiB、100000行；JSON展开内容单独63MiB安全上限，Excel按整工作簿合计行数。真实MinIO上传/下载及异步解析验证覆盖30MiB与100000行。
- 历史文件在当前身份下按组织发现授权来源、逐family迁移，保留源文件和不可变版本。新增166迁移任务表；每family最多50版本/150MiB，超限安全失败、保留来源、明确重试；新来源版本重新排队，人工当前版本不被覆盖。
- 新增167记录投影/全局使用状态/幂等请求表。记录页100条；复制和标记分离，剪贴板成功才标记。状态按资产及内容和重复序号保存，名称/说明版本不清空；已使用仍可复制。失败outbox只存身份、资产、版本、记录key和requestId，不存内容/Token。
- TXT/CSV英文及中文逗号分字段，换行分记录；引号转义、重复记录和真实空行有测试。JSON根数组逐项，对象整项，不把业务对象中的rows/sheets误识别成表格。
- 默认分类为旧null分类的受保护虚拟入口，写入前归一化null；不新增实体数据迁移。分类管理沿用分类管理权限。
- 版本页导航对登录用户开放，内容接口保留原资产权限、组织隔离和Token项目白名单；不新增Token Scope。关联追溯保持项目级。
- 前端移除全局标签、旧页切换及替换文件入口；分类树、按钮、更多操作、批量删除Modal、详情双tab和返回按钮按本轮要求调整。响应式容器内部滚动。
- DataAssetJson为资产内容单独设置63MiB字符串上限，保留其他默认JSON安全约束。LargeAssetRequest/Converter仅接管明确选择该请求类型的数据编辑/MCP接口：总请求64MiB、字符串63MiB，不全局放宽解析器。覆盖了默认Jackson20,000,000字符阈值遗漏的大字段读取。
- AgentDataAssetMcpWriteService将读取与写入分开：读取无需幂等键；写入需稳定requestId，复用现有操作回执事务并在重放时重检用户、组织、白名单及归属。create/upload通过持久请求映射验证新建资产当前组织；成功回执缺映射时拒绝。batch使用稳定jobId及请求指纹，保留逐行事务与部分成功。原业务写开关继续生效。

主要前后端对应：datasets.vue调用listDataAssets/uploadDataAsset/batchDataAssets以及legacy/auto/status、legacy/auto/run，分别进入DataAssetController、DataAssetBatchService、DataAssetLegacyAutoController/Service；data-detail.vue加载资产与版本元信息，DataRecordContent.vue调用GET /test-data-assets/{id}/records、POST /records/copy、POST /records/used进入DataAssetRecordController和page/copy/used服务方法。分类树经listDataCategories及既有分类管理API进入TestAssetGovernanceService；默认分类约束服务端同样执行。cases.vue/SystemCaseAssets.vue沿用原case/page接口，仅修加载时序；versions.vue、testAsset.ts、pathMap.ts和PermissionUiService同步取消额外页面门槛。

新增HTTP入口createBody/update(LargeAssetRequest)/batchBody和MCP postBody使用LargeAssetRequest，原Map方法保留直接调用兼容。MCP数据工具经AgentMcpStreamableService→AgentGenericMcpWriteService→AgentDataAssetMcpWriteService进入同一真实资产服务；不是新增一套Mock接口。Native、Node工具及接入说明已同步动态requestId规则，无新增Token Scope。

## 实际验证命令和结果

最终后端编译与测试（仓库根执行；使用本机Maven3.8.3、离线依赖及真实MinIO属性文件，测试JVM限制3GiB且保留原JaCoCo/模块参数）：

```powershell
& .build/run-usability-backend-v8.ps1
```

该脚本实际执行 `mvn -o -Dmaven.repo.local=C:/Users/midoo/.m2/repository -Dfile.encoding=UTF-8 -DskipAntRunForJenkins=true '-Dtest=DataAsset*,GlobalAsset*,GlobalData*,BusinessAssetServiceTests,SharedConfiguration*Tests,ExternalExecutorServiceTests,TestAsset*Tests,AgentMcp*Tests,AgentExecutionPreflight*Tests,AgentTestDataLeaseServiceTests,AgentSafeErrorMapperTests,AgentExecutionExceptionHandlerTests,BusinessGovernanceExceptionHandlerTests,AgentRunnerLeaseAuthorizationServiceTests,PermissionUiServiceTests,PermissionManifestCoverageTests,CaseAsset*Tests' -Dsurefire.failIfNoSpecifiedTests=false -pl backend/app -am test`。

`.build/usability-backend-v11-test.log`：BUILD SUCCESS。system-setting8/8，case-management41实际通过+18条件跳过，agent-integration246实际通过+12条件跳过，app2/2。共327项，297项在此实际执行；30项条件测试在下列隔离MySQL中全部实际执行通过。真实MinIO集成2/2、HTTP大请求5/5、大字段记录6/6均在本轮实际执行、非跳过。

```powershell
$env:JAVA_TOOL_OPTIONS='-Xmx3072m'
python .build/usability-native.py --native-only --native-test-filter GlobalAssetMigrationMySqlTests,DataAssetMigrationMySqlTests,SharedConfigurationAuditMySqlTests,GlobalDataCategoryMySqlTests,GlobalDataExecutionMySqlTests,DataAssetRecordMySqlTests,DataAssetLegacyAutoMySqlTests,DataAssetMcpReplayMySqlTests,DataAssetLegacyConcurrencyMySqlTests,DataAssetRecordServiceTests
python .build/usability-native.py --native-only --native-test-filter CaseAssetCatalogIdentityMySqlTests,CaseAssetSystemMySqlTests,CaseAssetSourceBindingGuardMySqlTests,CaseAssetSourceMappingRepairMySqlTests
```

日志分别为`.build/mysql-selftest/623bda28/native-suite.log`（18/18：12真实MySQL+6 H2记录回归）和`.build/mysql-selftest/9c752db0/native-suite.log`（18/18真实MySQL）；均BUILD SUCCESS、0跳过。覆盖160/166/167、混合排序规则、来源白名单、迁移恢复、十万行/500行批处理、独立连接旧快照并发、记录请求冲突及跨版本状态，以及用例目录/系统/来源绑定/修复SQL。两个隔离MySQL进程均已停止，未连接本机3306或既有业务库。

MCP重放MySQL测试使用真实操作回执事务及请求映射，验证回调次数、内容冲突、撤权、身份改变、跨组织与映射缺失；不冒称所有业务CRUD都在这一个测试中真实执行。实际资产CRUD/上传/编辑/复制另由真实数据服务、MinIO与浏览器证据覆盖。batch回归保持逐行部分成功，重放不重复处理行；写开关关闭时读取成功、8类mutation无副作用。

最终打包复用以上已编译并验证的class，避免重打依赖Jar触发无关重编：

```powershell
& .build/run-usability-package-final.ps1
python .build/verify-usability-package.py
python .build/verify-usability-freshness.py
```

实际打包参数包含 `-Dmaven.main.skip=true -DskipTests=true -pl backend/app -am package`；测试不是在这一步执行。`.build/usability-package-final.log` BUILD SUCCESS。嵌入模块Jar与target模块逐字节一致，新控制器/转换器/服务class与166/167等迁移一致，所有修改主源码的编译时间新于源码。

最终开发验证Jar SHA256：`ce75dce2d268d2a23b57d8ba131719e2915bb8c111eec7d6a93abe8037b81e26`。这是提交前验证产物，源码与精确差异哈希由独立审查包另行绑定；不冒称已部署版本。

前端最终检查（frontend目录）：

```powershell
npx vue-tsc --noEmit --incremental --tsBuildInfoFile ../.build/usability-final.tsbuildinfo
node --test scripts/test-data-assets-components.mjs scripts/test-data-assets.mjs
node scripts/verify-api-contracts.mjs
node scripts/verify-route-tabs.mjs
node scripts/verify-permission-resource-coverage.mjs
```

完整类型检查退出0；组件62/62、API5/5，API/路由契约通过；权限资源82精确、61兼容、0未绑定。修改范围ESLint/Stylelint均退出0、无警告；具体文件清单见changed-files.md，日志与SHA见validation-summary.json。实际scoped命令如下（frontend目录，数组展开为本次检查的相同文件参数）：

```powershell
$assetVue=@('src/views/test-asset/business-assets.vue','src/views/test-asset/cases.vue','src/views/test-asset/catalog.vue','src/views/test-asset/components/DataVersionMetadata.vue','src/views/test-asset/components/DataRecordContent.vue','src/views/test-asset/components/SystemCaseAssets.vue','src/views/test-asset/data-detail.vue','src/views/test-asset/datasets.vue','src/views/test-asset/versions.vue')
npx eslint scripts/test-data-assets-components.mjs src/api/modules/test-data-assets.ts src/config/pathMap.ts src/router/routes/modules/testAsset.ts $assetVue
npx stylelint $assetVue
```

`metersphere-mcp`目录执行`npm test`，包含TypeScript构建和54/54测试，退出0。

Vite原工作目录生成文件偶发open UNKNOWN，独立读写同字节也可复现，Node24和CI同版Node20均受影响。最终将1702个实际输入文件逐个SHA校验后复制到`.build/usability-frontend-input`，复用同一依赖，插件完整启用、不使用诊断preload；源文件哈希最终再次全量复核一致。该目录执行：

```powershell
& '../tools/node-v20.8.1-win-x64/node.exe' ./node_modules/vite/bin/vite.js build --config ./config/vite.config.prod.ts --outDir ../usability-dist-v4
```

`.build/usability-build-staged-v4.log`：6791模块、built in6m5s、exit0，产物`.build/usability-dist-v4`。仅既有eval/chunk警告，无新错误。frontend-build.json记录index/manifest哈希；浏览器实际取得同一v4 manifest。夹具注入了身份bootstrap，不能将夹具HTML字节声称为未经注入的生产index。

所有失败尝试保留：旧预算/H2 fixture定义、窄窗布局与过期请求误报、20m默认JSON阈值、MCP写路由漏接、泛型编译及新批次SQL拼接。它们均经修正和最终范围重跑；没有把失败结果替换成“通过”或删除对应拒绝边界。

## 验证边界

Docker镜像构建：按用户要求不执行。新补丁完整应用容器启动、健康检查、真实SSO和部署后验收，需用户合并上游PR并部署后执行；本地真实存储/MVC/SQL集成不能替代该验收。部署后准确健康命令：`docker inspect metersphere --format '{{json .State.Health}}'`（测试容器）；生产为`metersphere_prod`。真实低权限账号实测与历史同步两个已知项目失败依用户要求忽略，不冒称通过。

任何失败日志均保留；曾失败的旧预算测试/H2生命周期/默认分类缺表fixture已修正并重跑，不降低边界断言。本机原工作目录Vite生成文件open UNKNOWN在Node24和CI同版Node20均复现；已使用逐字节校验的隔离输入目录保留完整生成器构建，不更改依赖或绕过检查。v1/v2均完整构建通过，第二次5分8秒；截图发现窄表竖排以及自动迁移刷新取消旧请求后错误残留，继续修正并构建最终版，不以旧结果替代最终代码验证。

## 开发浏览器集成证据

v1.1补测使用最终v4生产bundle：真实剪贴板写入`""`成功后人为延迟其promise完成，离开详情页再释放；此时原归属逐请求outbox保留，标记请求为0。返回同一资产后只重试标记，累计剪贴板调用1、标记请求1、outbox清空且记录已使用。将实际复制内容写成两字节CSV再次通过页面上传，仍显示一条空字段记录。可见Modal在动画结束后测得520×204，横向中心偏差0，采用平台默认纵向位置（中心较视口中心上移45.89px）；取消后数据保留。替换batch-delete-modal.png为本轮真实截图，数值不再取隐藏Modal。浏览器取得build-manifest的validationBuild为usability-v4，与frontend-build.json对应。

使用Ego真实浏览器、生产bundle、实际Spring MVC控制器/服务、隔离MySQL与真实MinIO。仅身份/权限入口、平台外壳及文件仓库注册为明确测试夹具。用例系统页仅测布局，源数据API未接真实用例服务，不冒称完整用例后端验收。实际部署首开取消的证据另见system-directory-deployed-repro.json，生命周期回归由真实SFC脚本执行。

browser-evidence.json记录：

- CSV从页面文件选择器上传，四条记录含英文/中文逗号、引号字段；批量复制四条成功，全标已使用。
- 重复复制成功，刷新仍已使用；修改名称生成新版本后四条状态保留。
- 剪贴板成功后仅使用标记请求注入网络失败，刷新恢复outbox，只重试标记后清空；outbox仅actor/organization/assetId/versionId/keys/requestId。
- 两个历史源版本由页面自动迁移，版本1金额100.50、版本2金额205.75，各源文件下载字节内容与输入一致。
- 页面真实上传30MiB（31457280字节）TXT并READY，下载SHA256与原文件一致（fd9b580a0e26e23e4abd71a7d17d703e4a1122688d41b297d44deaf1729537a9）。自定义分类写入生效。
- 页面上传100000行CSV并READY；记录尾页100条、末索引99999，末条item99999/value99999。首次投影约15.9秒，之后分页读取快速；不将首次生成成本隐瞒为零延迟。
- 批量删除居中Modal可取消；确认后实际批次SUCCESS且条目消失。人工新建JSON三条123/345/456，单条复制后状态为已使用/未使用/未使用。
- 分类加号可创建、管理抽屉显示真实自定义分类；受保护默认分类不暴露改删入口，服务端防绕过有测试。

夹具首轮关联表缺project_id，导致历史导入事务回滚；补齐夹具字段后重试成功。该失败不冒称生产缺陷，未修改业务代码掩盖。Ego单次evaluate15秒超时的十万行首次投影只读请求随后完成；后续读取并核对总数/末页，没有通过截断数据缩短验证。

补充30MiB单字段全链路：真实上传后完整content与record各31,457,280字符；copy返回同长度且SHA256与原文件一致；HTTP PUT大字段保存为revision2；页面实际保留31,457,288个格式化显示字符，点击复制后提示“已复制，使用状态已保存”，旧/新版本使用状态均为true。该极端单字段在夹具冷开时首次渲染超过30秒，随后完成，未做生产性能承诺；常规十万行采用记录分页，不一次渲染十万行。未截断显示或复制数据。

最终窄窗证据：datasets-1004.png、datasets-actions-1004.png；文档宽1004等于视口，表格1476在内部滚动，真实横向wheel后scrollLeft865且可见常驻操作。system-cases-1004.png中卡片内容right947不超过容器right952。datasets-normal.png、data-content.png和batch-delete-modal.png已视觉检查。

诊断沿用现有日志，未新增外部采集或告警。MCP输入失败包含稳定事件、阶段、分类、traceId，以及容量/约束/语法/读取的安全根因类型；响应error.data.traceId可关联。故障注入检查秘密片段不进入日志/响应、不输出Throwable堆栈。数据服务、迁移与存储失败保留经校验SQLState/错误码；内部日志不冒称外部告警已建立。


## v1.2并发保护复验

U-04/P1修复和审查对象见review-resolution.md。真实MySQL最终编译产物补跑包含DataAssetLegacyConcurrencyMySqlTests的3项/12场景及此前数据测试，18/18（12真实MySQL+6重复H2）通过；用例18项原生MySQL证据沿用未改用例源码的9c752db0运行。因此统一测试30个条件跳过均有实际原生执行证据，不称它们在未配置数据库的Maven轮次中执行。新测试明确使用真实AgentDataAssetMcpWriteService/AgentOperationExecutor回执，不以手写RC事务替代；网页自动导入、外层RR旧快照冲突回滚、混合格式、人工元信息、重放及撤权均在同一原生测试覆盖。文件仓库为显式mock，物理原文件证据仍来自既有真实MinIO和浏览器运行。

反证：仅在.build/legacy-red-source中移除行锁/局部RC，25eb7fdf原生3/3失败（exit1），生产源码未回退；最终源码f7dc53f2原生3/3通过（exit0），随后又在最终Maven编译产物上18/18通过。日志哈希见validation-summary.json。没有将红测/旧classpath探针失败当作通过，也未删除失败日志。

本次后端锁修复未改变前端、HTTP字段或路由；已核验1702个输入仍与v4源码哈希一致，复用62组件、类型/lint、Vite及v4浏览器证据，不声称在新后端源码上再次手工跑了所有截图路径。实际并发端到端由原生MySQL测试覆盖；最终Jar/class/迁移再次核对。
