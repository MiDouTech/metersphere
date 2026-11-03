# 测试用例布局与详情加载改造实施记录

日期：2026-11-03。总体状态：**部分完成**。第一阶段应用代码已改造；真实业务端到端及完整视觉矩阵未通过验收，未提交、合并或部署本轮改动。

最终构建补验：Vite最终重建退出码0，6717模块、3m56s；检查 `addStep-MojBf8HU.css` 已包含最终 `.arco-table-td-content>div` 宽度规则，随后生成build manifest。类型检查在此前 `npm run build` 中通过；最终补充仅涉及CSS。正式基础镜像拉取失败后，用缓存 `nginx:alpine` 通过stdin临时替换FROM（未修改仓库Dockerfile）完成替代镜像构建、启动、首页HTTP200及应用挂载节点检查；日志无启动错误，随后移除本次容器。不能替代nginx:1.24.0或API/全栈验收。

替代容器实际命令：

```powershell
$taskDockerfile=(Get-Content frontend/Dockerfile -Raw).Replace('FROM nginx:1.24.0','FROM nginx:alpine')
$taskDockerfile | docker build --pull=false -t msp-case-ui-verify:20261103-local -f - frontend
docker run -d --name msp-case-ui-verify-20261103 -p 127.0.0.1:15178:5100 msp-case-ui-verify:20261103-local
Invoke-WebRequest http://127.0.0.1:15178/
docker logs msp-case-ui-verify-20261103 --tail 6
docker rm -f msp-case-ui-verify-20261103
```

验证结束后清除本次专用本地15179来源中为测试写入的认证键、关闭本次浏览器空间和Vite；不撤销用户生产会话、不清除整个浏览器配置。组件详情行为证据见detail-component.json；步骤证据见step-layout.json及step-component.png。

## 需求追踪结果

| 需求 | 实现与接口对应 | 证据 | 状态 |
| --- | --- | --- | --- |
| R1 详情瞬时报错 | ms-detail-drawer 统一加载；PrevNext 在容器中只负责导航。latestDetail 复用同ID在途请求，切换时取消旧请求，并用序号隔离旧成功/失败；关闭时失效。HTTP 层保留取消标记。失败提示有重试按钮。GET /functional/case/detail/{id} 参数与响应不变；用例详情新增可选 AbortSignal 传递 | 新增5项加载器测试通过；真实Vue详情组件夹具验证挂载一次、在途刷新去重、下一条一次、刷新当前ID；请求序列 A,B,B，alert=0 | 已实现，组件级验证通过；真实接口E2E待验收 |
| R1 附带评论重复加载 | 删除附件初始化结束后的评论reload；functional-comment-list自身caseId的immediate watch负责首次查询，发布成功仍主动刷新 | 源码链路检查；未完成真实评论请求计数验收 | 已实现待验证 |
| R2 10行展示 | 保留列表单层页面纵向滚动和横向列滚动；列表专属CSS压缩行、表头、单元格与批量工具栏；进度条由最小56px减为40px | 生产构建与布局检查；真实10行及分页一屏截图未取得 | 部分完成：未证明1920×1080及1440×900目标达标；工具栏进一步合并尚未实施 |
| R3 放大后文案竖排 | addStep步骤/预期列280px，实际结果220px；按启用列累加总宽度替代100%强塞；表格局部横向滚动；输入框及其包装铺满内容区；正文正常换行 | 真实AddStep组件、合成数据，在1100/900/720px容器中表格1330px、两文案输入框约289.8px，均有横向滚动。见step-layout.json及step-component.png | 第一阶段已实现并通过组件测量；真实缩放矩阵及编辑执行待验证 |
| R4 移除概览 | 当前tabDetail已无概览aside，保持单列；未删除业务overview API | 源码检索确认；此前线上版本取证证明旧构建 | 本地实现已存在；线上需发布后验证 |
| R5 编辑按钮重合 | 前置条件编辑操作改为正常文档流flex行，支持换行，不再absolute覆盖正文；保留权限、锁提示、重试和自动保存状态 | SFC、类型、构建检查；长文本/锁态真实截图待验收 | 已实现待视觉验证 |

列表、详情、步骤保存、执行及评论继续复用原后端路由、鉴权和业务校验，本轮不更改后端参数、响应、数据库、依赖或部署配置。没有通过放宽权限来获得验收截图。

## 修改文件

- frontend/src/utils/latestDetail.ts：加载状态协调与取消识别。
- frontend/src/components/business/ms-detail-drawer/index.vue：唯一加载入口、失效、导航、失败重试。
- frontend/src/components/business/ms-prev-next-button/index.vue：可选navigationOnly；跨页失败不继续请求错误ID。
- frontend/src/api/http/index.ts：取消错误不转换成普通请求失败。
- frontend/src/api/modules/case-management/featureCase.ts：详情请求可传取消信号。
- frontend/src/views/case-management/caseManagementFeature/components/caseDetailDrawer.vue：接入可取消详情请求。
- 同目录addStep.vue：最小列宽和横向滚动、正文及输入宽度。
- 同目录caseTable.vue：列表紧凑样式和进度区高度。
- 同目录tabContent/tabDetail.vue：编辑操作布局、正文换行、移除评论重复刷新。
- frontend/scripts/test-latest-detail.mjs、frontend/package.json：可重复执行的行为测试命令。
- docs/summary/msp-case-detail-layout-remediation-plan-20261102.md：方案实施状态链接。
- 本目录：实施报告与脱敏组件证据。

工作区另有agent-integration和docs/artifacts改动，不属于本次工作，未回退或覆盖。

## 实际执行的验证

frontend目录：

| 命令 | 结果 |
| --- | --- |
| npm run test:detail-loading | 5/5通过：同ID去重、旧成功忽略、旧失败忽略、取消与真实失败/重试、关闭及空ID |
| node --test scripts/test-latest-detail.mjs scripts/test-explicit-logout.mjs | 8/8通过 |
| npm run test:case-layout-edit | 5/5通过 |
| npm run test:layout-overflow | 通过 |
| npm run test:api-contracts | 通过 |
| npm run test:weekly-remediation-sfc | 13个SFC语法/模板检查通过，不等同类型检查 |
| npx eslint（下列变更文件） | 0错误，24条已有未使用变量/console等警告 |
| npx prettier --check（变更源码和新增测试） | 通过 |
| npm run build | 通过，含vue-tsc --noEmit和Vite构建；首次因报表函数第二参数兼容性失败，修复为独立getDetailWithSignal入口后重跑通过 |

lint准确命令：

```powershell
npx eslint src/utils/latestDetail.ts src/components/business/ms-detail-drawer/index.vue src/components/business/ms-prev-next-button/index.vue src/api/http/index.ts src/api/modules/case-management/featureCase.ts src/views/case-management/caseManagementFeature/components/caseDetailDrawer.vue src/views/case-management/caseManagementFeature/components/addStep.vue src/views/case-management/caseManagementFeature/components/caseTable.vue src/views/case-management/caseManagementFeature/components/tabContent/tabDetail.vue
```

最终补充CSS后另执行 `npx vite build --config ./config/vite.config.prod.ts` 和 `node scripts/write-build-manifest.mjs`，以最终日志和产物核验记录为准。项目存在eval、分包及包体积警告，未为通过检查而放宽规则。

根目录：

```powershell
.\mvnw.cmd -pl backend/services/system-setting -am test '-Dtest=MiduoSsoSessionIsolationTests' '-Dsurefire.failIfNoSpecifiedTests=false'
.\mvnw.cmd -pl backend/services/system-setting -am test '-Dtest=LoginControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false'
git diff --check -- frontend
docker build -t msp-case-ui-verify:20261103 -f frontend/Dockerfile frontend
```

会话隔离测试通过（含相关模块编译），登录集成测试失败，详见下节。diff检查通过。正式Docker构建因拉取nginx:1.24.0时Docker Hub授权端点连接超时失败。

## 浏览器验证边界

使用本地Vite前端15179、已有用户会话及真实后端读取链路尝试进入用例路由，结果为“暂无资源权限，请联系管理员”。没有修改权限、伪造角色、创建或编辑业务数据。真实路由未进入，不把后续组件夹具计作业务E2E。

随后在本地页面挂载真实Vue详情容器和AddStep，使用明确标注的合成数据，完成请求次数及列宽验证。截图标题已注明“组件布局验证（合成数据，不代表真实端到端）”。直接导入完整CaseTable的补充列表夹具两次超时，未形成有效10行展示证据；不计通过。

## 未通过、未执行与风险

1. 真实用例路由E2E、真实详情GET只请求一次、评论去重、长前置条件/锁状态截图、列表10行一屏和100%/110%/125%完整视觉矩阵仍未验收。当前权限/资源状态拦截路由；需有可进入该模块的隔离测试账号或配置后，在本地最终构建执行上述方案中的矩阵。风险：真实页面高度、权限差异和辅助请求仍可能有问题。
2. LoginControllerTests当前环境失败；需要修复Java/Testcontainers的Docker连接后重跑准确Maven命令。Docker CLI可用并不能证明Java测试容器探测成功；本次不调整测试基础设施。
3. 正式指定基础镜像未构建成功；网络恢复后重跑docker build，再执行 `docker run -d --name msp-case-ui-verify -p 127.0.0.1:15178:5100 msp-case-ui-verify:20261103` 和 `Invoke-WebRequest http://127.0.0.1:15178/`。nginx实际API上游需要指向隔离测试后端，首页200仅证明静态服务。
4. 本轮无后端源码修改，未打包全部backend/app或构建全栈新镜像。需独立执行 `.\mvnw.cmd -pl backend/app -am package '-DskipTests'` 后按项目部署流程准备镜像上下文，并验证全栈健康与日志；不能用本轮模块编译代替完整应用发布验收。
5. 数据库迁移验证不适用，无迁移和schema变化。
6. 列表工具栏全面合并、窄屏步骤卡片视图尚未实现；后者是方案第二阶段。当前先采用最小宽度+横向滚动，保留编辑数据模型。
7. 没有新增TODO、空函数、固定成功接口或生产Mock。测试替身和合成组件数据仅作为测试证据，不是业务实现。

未进行Git提交、推送、合并或生产部署。源码修复不代表线上旧版本已更新。
