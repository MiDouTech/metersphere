# 测试资产前端缺陷修复验证

2026-11-24，分支 `codex/asset-defect-remediation-20261124`，起点 `e1fd443ec737968c6e20eba29171c5d4a3038346`。需求来自[部署验收报告](../../testing/asset-deployed-acceptance-20261123-03/report.md)。本记录覆盖修改后的工作区。浏览器访问回环地址，由 Vite 加载真实页面和 Arco 组件，测试在浏览器网络层提供隔离的 API 响应。

| 编号 | 入口及修复 | 运行验证 |
| --- | --- | --- |
| 100012 | 用例目录创建、重命名复用 `saveCatalog`。Arco 的 `ok` 通知发生时弹窗已决定关闭，原处理函数返回 false 无效。现在用 `before-ok` 等待必填校验和保存请求，写入成功再触发目录刷新。来源治理弹窗同样等待来源依据和写入结果。 | 空白点击确定后保留弹窗；取消不写入；模拟 503 时输入保留，重试成功关闭。来源依据为空可以原地补填。 |
| 100015 | 用例详情将返回和保存移到 MsCard 页头插槽，正文按容器宽度滚动。原操作区在内容滚动条下方，1920 像素宽时轨道左缘进入按钮范围。 | Chromium 命中测试验证桌面保存按钮完整避开滚动轨道。按按钮中心物理坐标点击后发出 PUT，目录和用例 ID 正确。720 像素宽时按钮中心也位于可点击区域。 |
| 100017 | 版本目录保留原有管理权限判断，使用 HTTP 客户端现有 `joinParamsToUrl` 选项。服务端 `POST /test-assets/versions/{versionId}/deprecate` 通过 `@RequestParam` 读取三个参数。原客户端把 POST 的 params 移入 JSON 请求体，导致查询参数缺失。 | 点击停用并确认，直接检查浏览器实际发出的 POST URL，包含 projectId、assetType、assetId。 |
| 100018 | 公共配置编辑弹窗通过 Arco 的 labelAttrs、inputAttrs 和 textareaAttrs 将可见标签的 for 与原生控件的 id 关联。 | Chromium 按可访问名称找到配置名称、系统名称、环境名称和配置内容，分别点击标签，焦点均进入对应输入控件。 |
| 100019 | 用例资产的目录和用例两级工具栏均先排列操作，再排列检索；创建为首个主按钮，检索窄屏换行靠右。操作权限、禁用条件与处理函数沿用原逻辑。 | 1920 像素宽时新建按钮位于用例检索左侧。720 像素宽时检索落在操作下方且位于视口内。 |

## 实际验证

从 `frontend` 执行。用例仅接受回环测试地址；测试上下文使用合成身份和本地响应，不需要用户登录。

```powershell
node node_modules/vite/bin/vite.js --config config/vite.config.acceptance.ts --host 127.0.0.1 --port 15246 --strictPort
$env:PW_BASE_URL = 'http://127.0.0.1:15246'
node node_modules/@playwright/test/cli.js test e2e/asset-remediation.spec.ts --reporter=list --timeout 45000 --output ../.tmp/asset-remediation-20261124/ui-test-green
node --test scripts/test-business-assets.mjs scripts/test-asset-version-loading.mjs scripts/test-shared-configuration-fields.mjs scripts/test-case-layout-edit.mjs scripts/test-modal-contract.mjs scripts/test-evidence-preview-boundary.mjs
node scripts/verify-api-contracts.mjs
pnpm run type:check
node node_modules/eslint/bin/eslint.js --no-fix e2e/asset-remediation.spec.ts src/views/test-asset/cases.vue src/views/test-asset/asset-case-detail.vue src/views/test-asset/catalog.vue src/views/test-asset/shared-configuration.vue src/api/modules/ai-execution.ts scripts/test-evidence-preview-boundary.mjs
node node_modules/stylelint/bin/stylelint.js src/views/test-asset/cases.vue src/views/test-asset/asset-case-detail.vue src/views/test-asset/catalog.vue src/views/test-asset/shared-configuration.vue
```

修复前 6 个浏览器回归场景均失败，修复后 6 个通过。最终一次执行增加了取消零写入、物理点击实际请求及窄屏命中断言。6 个 Node 测试文件共 28 项通过，含项目切换和关闭弹窗后的迟到证据预览响应。前端 API 契约、全量类型检查、定向 ESLint 及 Stylelint 均退出 0。红绿日志分别位于运行目录 `.tmp/asset-remediation-20261124/ui-red.log`、`ui-green.log`。

现代 Web 指南查阅了 `forms` 与 `css-layout`。公共配置使用原生 label 的 `for` 和输入元素的 `id` 配对，用户可以点击标签聚焦。工具栏直接按可见操作顺序排列 DOM 元素，键盘顺序与视觉顺序一致。

### 生产构建与本地浏览器复测

```powershell
$env:NODE_OPTIONS='--max-old-space-size=8192'
$env:GITHUB_SHA=(git rev-parse HEAD).Trim() + '-dirty'
pnpm run build
node node_modules/vite/bin/vite.js preview --config config/vite.config.acceptance.ts --host 127.0.0.1 --port 15248 --strictPort
# 另开终端，从 frontend 执行
$env:PW_BASE_URL='http://127.0.0.1:15248'
node node_modules/@playwright/test/cli.js test e2e/asset-remediation.spec.ts --reporter=list --trace on --timeout 45000 --output ../.tmp/asset-remediation-20261124/ui-production-smoke-final
```

最终 `pnpm run build` 退出 0，包含严格类型检查、6771 个模块的生产打包和构建清单生成，保留既有的大包、动态导入及 eval 告警。逐个检查清单的 6 个入口资源均存在，`dist/index.html` 的 SHA-256 与清单一致，为 `1858d46a0fe11c727c9303f8d24338dec2348b61a88906ee9fcff1ad45c626de`，revision 为 `e1fd443ec737968c6e20eba29171c5d4a3038346-dirty`。检查生成的目录页面代码确认已包含证据预览最后的 HAR 文本类型修正。

浏览器由 Vite preview 加载这份生产产物，6 项通过，用时 36.8 秒，无重试。6 个 `trace.zip` 保存在 `.tmp/asset-remediation-20261124/ui-production-smoke-final/` 各场景目录内，含窄屏布局和保存按钮物理点击的回放。完整构建及浏览器日志同目录上一级的 `ui-production-build-final.log` 与 `ui-production-smoke-final.log` 可查。更早一次用 Python 本地静态服务测试上一版构建时 5 项通过，来源治理页的静态资源连接被拒绝；更换服务并重新加载最终产物后的上述 6 项均通过。

## 影响面与运行边界

前端变更涉及 `src/views/test-asset/cases.vue`、`asset-case-detail.vue`、`catalog.vue`、`shared-configuration.vue` 和 `src/api/modules/ai-execution.ts`，回归用例在 `e2e/asset-remediation.spec.ts`。本域未更改数据库、服务端路由及项目授权。部署后仍需对测试资产实际执行版本停用并刷新回读，检查服务器权限及持久化结果，并在部署页面核对交互。容器镜像构建按用户要求不执行。
