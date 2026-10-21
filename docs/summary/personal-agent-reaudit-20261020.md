# 个人 Agent、导航迁移与质量门禁重新审计

基线：f1bd305c6a。结论：部分完成，不能按全部需求验收。此次未修改业务代码，未部署远程环境。

## 需求追踪与发现

|需求|实际入口/实现|状态与影响|
|---|---|---|
|隐藏项目执行设置、系统运行维护、扩展能力|`frontend/src/router/routes/execution-settings.ts` 仍注册三组可见导航；setting.ts 实际引入|未实现。adminOnly 不是整体隐藏，用户截图与代码一致|
|个人中心 Agent配置承接实际操作|`ms-personal-drawer/index.vue` 仍只有“前往个人接入”跳转，中文名称仍是 Agent 集成|未实现迁移；个人 Token 管理本身存在，但入口不符合要求|
|执行中心承接全局门禁|`router/routes/modules/execution.ts` 的 quality-policy 隐藏并重定向旧系统设置|未实现迁移；父路由仍仅接受 AI_EXECUTION:READ，尚未实现方案要求的子页权限聚合|
|测试平台配置统一承接环境、登录、凭据|执行中心没有 platforms 子路由，旧配置仍在 execution-settings 分组|未实现统一入口与流程|
|个人 Token 经 MCP 操作用例|`/api/personal/agent-tokens`、`/api/mcp`、AgentDirectExecutionTools|本地接口链路复测通过；未验证真实用户通过目标个人中心界面创建 Token|
|无租约上传证据、审核并写回|AgentDirectExecutionService；contract/evidence.upload/submit/get；agent_case_evidence、agent_case_submission、functional_case_attachment、functional_case|实现了基础链路，本地端到端复测通过；不是全部 MCP 用例库功能完成|
|全局质量门禁|GlobalQualityGate 从 GLOBAL 状态读取已发布策略，校验策略摘要、证据角色、格式、大小及断言|全局策略及结构审核真实存在；个人通道仅支持文字精确 EQUALS|
|附件与步骤对应审核|上传绑定 Token、用户、项目、用例、内容摘要、步骤编号；提交校验对应关系|实现结构归属检查，未实现图片内容与操作/结果的相关性审核，不能证明实际执行|
|更广泛的 MCP 用例库覆盖|原生工具及 Node 注册表、既有实施记录|完整导入导出、执行中删除确认/停止协调/恢复、评论删除等仍未完成|
|截图中的任务加载失败|automationExecution/index.vue 的 reloadTaskList 捕获所有异常后显示同一句提示，并清空列表|失败事实明确，根因未确定；不能凭截图归因权限，也不能视为空列表成功|

## 最关键的验收缺陷

`frontend/e2e/personal-agent-execution.spec.ts` 使用页面左上角 2×2 像素截图作为证据，将 actual 填成预期文本，提交成功。这证明数据库发布与文件下载链路可用，却不证明图片含有测试步骤或结果的有效证据。

个人直提通道通过 `expected == actual` 产生成功结论。Agent 若复制预期文字并上传无关但有效的图片，现有规则无法识别。反过来，真实观察与预期语义一致但文字不同，也会被判为 ERROR。因此应撤回将这项测试解释为完整业务门禁验收的结论。

同一测试通过 HTTP 创建/修改 Token，未通过个人中心 UI 完成这些操作，无法覆盖导航迁移和个人配置交互。完整简化产品方案文件仍标记待研发实施，上次只交付了核心接口切片。

## 本次实际验证

```powershell
$env:MCP_CASE_LIBRARY_E2E='true'
$env:PW_BASE_URL='http://127.0.0.1:15173'
pnpm.cmd -C frontend exec playwright test e2e/personal-agent-execution.spec.ts --workers=1
npm.cmd --prefix metersphere-mcp test
docker ps --format "{{.Names}} {{.Status}} {{.Ports}}"
```

- Playwright：1 passed，25.9 秒，退出码 0；验证范围仅限测试脚本覆盖的本地路径。
- Node：16 passed，0 failed，退出码 0；客户端测试不能替代远程服务器验收。
- 隔离应用及 Redis、Kafka、MySQL、MinIO 容器为 healthy；健康状态不等于业务全部可用。
- 日志：`.codex-tmp/reaudit-personal-core.log`、`.codex-tmp/reaudit-mcp.log`。
- 阅读用户截图、产品方案、路由/页面、工具注册、服务逻辑、全局规则及验收脚本，核对上述差异。

本次未重新执行后端全量单元/集成测试、类型检查、lint、生产构建、迁移和镜像构建：此次为审计，无应用代码变更；这些检查也无法替代缺失的业务实现。修复后须重新执行，不沿用历史通过结果作为修复验收。

可复用检查命令：

```powershell
.\mvnw.cmd -pl backend/app -am test '-Dtest=Agent*Tests' '-Dquality.mysql=true' '-Dsurefire.failIfNoSpecifiedTests=false'
pnpm.cmd -C frontend run type:check
pnpm.cmd -C frontend exec eslint src/router/routes/execution-settings.ts src/router/routes/modules/execution.ts src/components/business/ms-personal-drawer/index.vue e2e/personal-agent-execution.spec.ts
pnpm.cmd -C frontend run build
.\mvnw.cmd -pl backend/app -am package '-DskipTests'
```

远程部署版本、截图对应请求的 HTTP 状态/业务错误/traceId、真实第三方 Agent 业务执行、各角色导航和旧书签访问尚未验证。截图不足以确定任务加载失败根因；后续应采集实际请求及服务日志，再定位修复。现有 2×2 图片验收用例应作为“不充分证据被接受”的回归案例，不能继续作为业务质量保证。

## 修复范围

1. 按简化产品方案完成路由、个人中心、配置页面迁移，覆盖管理员/项目管理员/普通用户，以及旧页签、搜索、快捷入口和旧 URL。
2. 保留有效的 Token、上传、事务发布和全局策略实现；补齐对证据充分性、步骤实际内容和断言事实的验收约束。不能只增加图片尺寸限制就声称实现语义对应。
3. 真实操作个人中心创建 Token，再用 MCP 提取真实用例、执行、上传实际截图；负例覆盖无关图片、复制预期、错步骤、缺附件、撤权和重复提交。
4. 独立复现截图中的任务列表失败；区分权限、参数、后端异常和部署差异，保留安全错误码及 traceId。
5. 对完整用例库扩展逐项落实未完成能力，未完成项继续明确标识，不能以核心切片替代全量验收。
