# MSP 用例页面与退出登录修复记录

日期：2026-11-02。总体状态：**部分完成**。代码修复及前端生产构建通过，尚未具备三项需求的完整登录态端到端证据；未部署生产环境。

## 需求追踪

| 需求及验收条件 | 前端入口与实现 | 后端对应 | 数据/配置 | 验收及状态 |
| --- | --- | --- | --- | --- |
| 笔记本用例列表不再因固定视口扣减而只有很小的表格滚动区 | 测试用例 → 执行用例 → 用例列表；caseTable.vue 取消固定纵向滚动高度，使用页面滚动，保留横向滚动与分页 | POST /functional/case/page；继续使用原有接口和权限 | 无迁移，无部署配置变更 | 布局回归及构建通过；1366×768、1440×900 真实数据视觉验收未执行，已实现待验证 |
| 主动退出后保持退出状态，不能被自动 SSO 立即重新登录；接口失败不得冒充服务端退出成功 | 个人菜单 → useUser.ts → userStore.logout；登录页识别当前标签页主动退出标记，刷新仍停留退出页，点击重新登录才进入 SSO | GET /signout → LoginController；米多会话交给 MiduoSsoApplicationService 销毁平台会话；重新登录复用 /auth/miduo/status 与 bridge 接口 | sessionStorage 新增 msp_explicit_logout 标记；不改变上游全局 SSO 会话 | 新增 3 项流程单元测试通过；退出页刷新与重新登录失败分支浏览器验证通过；真实菜单退出及 SSO 成功回跳未验证 |
| 用例详情不显示额外概览窗口，步骤区域恢复单栏 | 测试用例 → 用例详情 → 详情；tabDetail.vue 删除额外 aside 和侧栏样式，保留表单最小宽度约束 | GET /functional/case/detail/{id}，数据、编辑和权限链路不变 | 无 | SFC 类型检查和生产打包通过；真实详情页视觉验收未执行，已实现待验证 |

边界与失败行为：保留列表空态、加载态、筛选、分页、选择与原有接口错误处理；横向列继续滚动。主动退出沿用 store 的 finally 清理本地凭据；接口失败显示服务端退出未确认提示。被动过期保持既有重认证行为，不设置主动退出标记。重新登录服务不可用时保留按钮和标记并提示重试。原有权限与后端数据校验不变。

## 本次修改文件

- frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue
- frontend/src/views/case-management/caseManagementFeature/components/tabContent/tabDetail.vue
- frontend/src/hooks/useUser.ts
- frontend/src/views/login/components/login-form.vue
- frontend/scripts/test-explicit-logout.mjs
- docs/testing/msp-ui-fixes-20261102.md
- docs/testing/msp-ui-fixes-20261102-logout.png

工作区其他 agent-integration 等修改不属于本次工作，未回退或覆盖。

## 实际验证

从 frontend 目录执行：

| 命令 | 结果 |
| --- | --- |
| node --test scripts/test-explicit-logout.mjs | 3/3 通过：主动退出、服务端失败、被动过期 |
| npm run test:case-layout-edit | 5/5 通过 |
| npm run test:layout-overflow | 通过 |
| npm run test:api-contracts | 通过 |
| npx eslint src/hooks/useUser.ts src/views/login/components/login-form.vue src/views/case-management/caseManagementFeature/components/caseTable.vue src/views/case-management/caseManagementFeature/components/tabContent/tabDetail.vue | 0 错误，19 条警告（已有 console、未使用变量） |
| npm run build | 退出码 0；包含 vue-tsc --noEmit、Vite 生产构建及 build manifest。有 eval、分包等警告 |

从仓库根目录执行：

```powershell
.\mvnw.cmd -pl backend/services/system-setting -am test '-Dtest=MiduoSsoSessionIsolationTests,LoginControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false'
git diff --check -- frontend
docker build -t msp-ui-fixes:20261102 -f frontend/Dockerfile frontend
```

- Maven：相关模块主代码及测试代码编译通过；MiduoSsoSessionIsolationTests 1/1 通过；LoginControllerTests 因 Testcontainers 未检测到 Docker 而启动失败，总体命令失败。Docker CLI 可用不代表 Java 测试容器探测通过。
- diff 检查通过。
- 正式前端镜像构建失败：Docker Hub nginx:1.24.0 授权端点连接超时。另用本地已缓存 nginx:alpine 作替代基础镜像验证，不能替代指定版本验收。

替代镜像实际执行命令（仓库 Dockerfile 未改写）：

```powershell
$taskDockerfile = (Get-Content frontend/Dockerfile -Raw).Replace('FROM nginx:1.24.0', 'FROM nginx:alpine')
$taskDockerfile | docker build --pull=false -t msp-ui-fixes:20261102-local -f - frontend
docker run -d --name msp-ui-fixes-verify-20261102 -p 127.0.0.1:15178:5100 msp-ui-fixes:20261102-local
Invoke-WebRequest http://127.0.0.1:15178/
docker logs msp-ui-fixes-verify-20261102 --tail 8
```

结果：构建及启动成功，首页 HTTP 200，包含应用挂载节点，日志无启动异常。此项不证明原 nginx:1.24.0 镜像、API 上游或全栈健康通过。验证后移除本次创建的容器，停止本次 Vite 和浏览器会话。

浏览器使用 Playwright CLI、本次独立 msp-fixes 会话、本地 Vite 页面，后端代理指向已有本地 18091 服务：

- 设置主动退出标记后刷新 /#/login：退出提示及重新登录按钮可见，未自动跳 SSO。
- 点击重新登录：本地 SSO 不可用时提示“暂时无法连接登录服务，请稍后重试。”，标记仍为 1，按钮保留。
- 尝试本地管理员登录返回 401，未进入真实用例列表和详情，不将此计作端到端通过。
- 截图：msp-ui-fixes-20261102-logout.png。该截图仅证明退出页展示；标记通过测试设置，不证明服务端会话失效。

## 未通过或未执行及补验

1. 真实登录态列表、详情、菜单退出、SSO 成功回跳与权限回归：缺少可用本地测试账号及本地 SSO 配置。风险为页面滚动、操作可达性、真实会话行为仍可能存在环境差异。使用有效测试账号在 `http://127.0.0.1:15177` 登录后，分别在 1366×768 和 1440×900 验收列表全部分页行可通过页面滚动访问、详情无概览、退出后刷新与后退不能恢复已授权页面，重新登录可成功回跳；需先启动代理指向可用后端的 Vite。
2. 登录集成测试：修复当前 Windows Java/Testcontainers 的 Docker 探测后重跑上面的 Maven 命令；未验证真实数据库登录集成。
3. 正式镜像构建、正式镜像启动健康检查：恢复 Docker Hub 访问后执行上面的 docker build，再执行 `docker run -d --name msp-ui-fixes-verify -p 127.0.0.1:15178:5100 msp-ui-fixes:20261102` 和 `Invoke-WebRequest http://127.0.0.1:15178/`。静态首页 200 仅代表前端服务健康；仓库 nginx 上游配置还需在隔离环境映射到测试后端后验证 API。
4. 后端应用完整打包、后端新镜像构建和全栈健康检查未执行：本次没有后端源码变更，相关模块编译已执行；完整应用需使用隔离依赖与既有部署流程。可执行 `.\mvnw.cmd -pl backend/app -am package '-DskipTests'`，解包产物后按根 Dockerfile 指定的 dependency 结构构建 `docker build -t msp-ui-fixes-full:20261102 -f Dockerfile .`；该命令要求先准备 BOOT-INF 解包目录，不能从本次证据推断全栈已通过。
5. 数据库迁移验证不适用：本次没有数据库或迁移变更。

## 风险和遗留

- 页面滚动是本次列表布局选择，较大分页会增加页面长度；没有保证屏幕一屏容纳固定条数。
- 平台退出不注销其他米多平台会话；服务端请求失败只能确认本地凭据已清理。
- 主动退出标记为标签页级，跨标签页同步退出未在本次实现。
- 无新增 TODO、Mock 接口、固定成功返回或空实现。新增前端单元测试使用依赖替身，不作为真实后端端到端证据。
