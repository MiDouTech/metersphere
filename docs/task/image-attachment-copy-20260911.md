# 图片附件复制：需求与验收

范围：优先测试用例、缺陷管理。复制实际图片到系统剪贴板，不创建新的平台附件记录。

## 开发前需求追踪

| 需求 | 用户入口和前端实现 | 后端与数据 | 验收 |
| --- | --- | --- | --- |
| 测试用例图片复制 | tabDetail.vue → MsFileList，编辑和只读附件列表、图片预览 | 复用 featureCase.previewFile 的鉴权 Blob；无写接口/迁移 | 图片复制、非图片隐藏、只读可用 |
| 缺陷图片复制 | edit.vue、bugDetailTab.vue → MsFileList | 复用 bug previewFile 的鉴权 Blob，保留所属项目/缺陷参数；无写接口/迁移 | 新增待保存图片、已保存及关联图片 |
| 通用交互 | MsFileList → 复制按钮 → Clipboard API | 复用已可预览的图片，不添加匿名下载路径或修改权限 | loading、防重复、成功提示；拒绝权限、不支持、图片失败提示 |

边界：复制为 PNG；动画图片复制当前解码帧。仅复制用户有权预览的图片，不能绕过后端读取权限。HTTP 非安全上下文或浏览器不支持时给出提示，不把复制链接冒充复制图片。

## 实现结果

- 共享附件列表增加“复制图片”，覆盖普通操作区、标题操作区和图片列表；大图预览工具栏同步增加操作，切换图片后复制当前图片。
- 图片处于待保存或上传完成状态时显示；非图片、上传中、失败或删除标记的列表项不显示。复制不要求编辑权限，使用已有图片读取能力。
- 使用已有鉴权 Blob URL，缺少缓存时调用业务 getThumbnail 获取原图。测试用例对应 FunctionalCaseAttachmentController.preview（FUNCTIONAL_CASE_READ、项目归属校验）；缺陷对应 BugAttachmentController.preview（PROJECT_BUG_READ、项目及附件来源校验）。未新增匿名下载或数据写入接口。
- 复制按钮有 loading、防重复和成功/失败提示；异步图片加载通过 ClipboardItem 的 Promise 传入，剪贴板写入在点击手势内发起。原尺寸转换 PNG，超过 4000 万像素拒绝，图片加载超时 15 秒。浏览器权限拒绝与不支持使用独立提示，不展示原始异常。

## 修改文件

- `frontend/src/components/pure/ms-upload/fileList.vue`：共享列表及预览入口。
- `frontend/src/components/pure/ms-upload/copyImageButton.vue`：复制交互、loading、消息。
- `frontend/src/utils/imageClipboard.ts`：原尺寸 PNG 转换和 Clipboard API。
- `frontend/src/components/pure/ms-upload/locale/zh-CN.ts`、`en-US.ts`：文案。
- `frontend/scripts/test-image-clipboard.mjs`、`frontend/package.json`：7 项定向测试与运行入口。
- 本报告。没有修改后端、数据库迁移或容器配置；保留前一任务所有改动。

## 实际执行验证

```powershell
# 仓库根目录
npm --prefix frontend run test:image-clipboard
npm --prefix frontend run build
git diff --check
docker version --format '{{.Server.Version}}'
# frontend 目录，修改范围 lint
node node_modules/eslint/bin/eslint.js src/utils/imageClipboard.ts src/components/pure/ms-upload/copyImageButton.vue src/components/pure/ms-upload/fileList.vue src/components/pure/ms-upload/locale/zh-CN.ts src/components/pure/ms-upload/locale/en-US.ts scripts/test-image-clipboard.mjs
```

- 7 项测试通过：原尺寸 PNG、先发起写入再异步加载、不安全上下文、解码失败、权限拒绝、超大图、源读取拒绝。测试使用浏览器 API 替身，不作为真实剪贴板证明。
- 修改范围 lint 通过，git diff --check 通过。初次 lint 的 import 排序和换行问题已修正；测试脚本也通过 lint。
- 生产构建完成 vue-tsc 类型检查、Vite 构建及压缩，日志 `✓ built in 2m 44s`，本地 `.codex-tmp/image-copy-build.log`。依赖 eval、体积等警告仍存在；PowerShell 将 stderr 包装为 NativeCommandError 导致外层 exit 1，已依据最终构建标记及产物核对，不声称零警告。
- 使用 Playwright CLI 打开隔离 Vite 页面，挂载生产 CopyImageButton 和真实 Arco/i18n。点击后读取真实剪贴板得到 `{type: 'image/png', width: 32, height: 24}`，与 JPEG 源图尺寸一致。坏图点击后确认出现“图片复制失败，图片可能无法加载或尺寸过大，请重新预览后重试”。测试浏览器与服务均已关闭。复现入口为本地 `.codex-tmp/image-copy-browser.mjs`，命令 `node .codex-tmp/image-copy-browser.mjs`、`npx --yes --package @playwright/cli playwright-cli -s=image-copy open http://127.0.0.1:5199/image-copy-check`；此页面仅提供组件验证，不模拟平台鉴权通过。
- Docker Engine 不可连接：dockerDesktopLinuxEngine 管道不存在。

## 未执行验证与风险

| 检查 | 原因与风险 | 后续命令 / 验收步骤 |
| --- | --- | --- |
| 真实平台 E2E、用例/缺陷读取权限 | 本地 8081/5173 无运行中的平台，未部署；尚未验证真实已保存、关联、待保存图片与只读角色的完整链路 | 准备测试后端后 `npm --prefix frontend run dev`；进入测试用例详情、缺陷新增/编辑/详情，分别复制本地及关联图片并粘贴到支持图片的输入区；验证无读取权限时接口拒绝、预览切换复制当前图、非图片无按钮 |
| 后端单测/集成/打包 | 本轮未改后端；没有将前轮结果算作本轮平台验收，权限集成仍需实测 | 准备测试依赖后 `.\mvnw.cmd -pl backend/app -am test`、`.\mvnw.cmd -pl backend/app -am -DskipTests package` |
| 全量前端 lint | 本轮执行修改范围；前轮已发现未修改文件有 9 errors / 379 warnings，未重复全量修复 | frontend 目录执行 `node node_modules/eslint/bin/eslint.js . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts` |
| 容器构建/启动/健康 | Docker Engine 不可用；静态资源实际部署、HTTPS 和权限策略未验证 | 启动 Docker Desktop 后 `docker build -f frontend/Dockerfile -t metersphere-frontend:image-copy frontend`；准备运行配置后 `docker compose -f cds-compose.yml up -d app frontend`、`docker compose -f cds-compose.yml ps`、`docker compose -f cds-compose.yml logs --tail 100 frontend app`（compose 为仓库开发运行方式，不代替前一镜像启动验收） |
| 数据库迁移 | 无模式或持久化变更，不新增迁移；未重新执行全库迁移 | 在上述集成环境启动应用时检查现有迁移及健康日志 |

已知限制：只保证复制静态图像内容，不保留 GIF 动画、原文件名或原格式；浏览器需安全上下文及 Clipboard API。独立组件浏览器测试不能代替测试用例/缺陷页面 E2E，未验证所有浏览器及目标粘贴应用。

功能状态：**部分完成（代码、定向测试、组件浏览器验证与前端构建通过；平台部署及完整 E2E 未验证）**。
