# task011 - P0 - 用例列表入口与自动化执行工作台

## 目标

完成用户可操作的手工选例和自然语言执行入口，并在工作台展示任务、步骤、自愈、证据和回写状态。

## 实施范围

### 功能用例列表

- 勾选至少一条有效用例且有 `AI_EXECUTION:RUN` 权限时启用【AI执行】。
- 执行确认弹窗展示范围、计划、环境、URL、浏览器、登录方式、自愈/截图策略和风险。
- 超过数量门槛或存在高风险动作时要求显式勾选确认。
- 前端仅传 `caseIds`，后端重新校验；创建成功后跳转任务详情。

### 自动化执行工作台

- 左侧支持提示词、Provider/模型复用、结构化筛选条件、候选用例、命中原因和人工调整。
- 右侧展示任务状态、最新画面/截图、用例步骤树、事件日志、证据、自愈轨迹和回写状态。
- 支持确认、暂停、继续、取消、人工登录完成、失败项重试和转人工确认。
- 首期使用事件游标轮询，封装订阅层以便后续替换 SSE。
- 页面刷新后可按 taskId 恢复，不依赖前端内存保存运行状态。

## 重点文件

- `frontend/src/views/case-management/caseManagementFeature/components/caseTable.vue`
- `frontend/src/views/bug-management/automationExecution/index.vue`
- `frontend/src/api/modules/ai-execution.ts`
- `frontend/src/api/requrls/ai-execution.ts`
- 路由、权限和 i18n 文件

## 验收标准

- 两种入口均能创建同一后端任务模型。
- NL 命中范围在确认前清晰可见，不能静默执行隐藏用例。
- 自愈步骤可查看首次失败及前后截图。
- `PARTIAL_SUCCESS/NEEDS_REVIEW/WAITING_LOGIN` 不显示成普通成功或运行中。
- 无权限用户看不到入口，直接调用接口也会被拒绝。

## 测试要求

- 组件测试、权限测试、刷新恢复、大量日志、轮询断线和重复点击测试。
- 使用 Playwright 完成两个入口的前端 E2E。
