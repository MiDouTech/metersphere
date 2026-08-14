# task037 - P0 - 双向孤岛与权限门禁

- 自动发现本次偏差补齐涉及的 Spring Controller 端点和前端 API 模块导出函数；后续新增补齐范围时必须同步扩大扫描目录。
- UI 接口必须存在前端 API、页面实际引用及路由/组件入口。
- 前端 API 必须能映射后端；未使用封装不得算作入口。
- 写接口必须具有权限或机器身份，写按钮必须具有对应权限。
- PROTOCOL/WEBHOOK/INTERNAL 必须有测试；LEGACY 必须有 owner/reason/expiresAt。
- 保留例外时必须到期，禁止永久通配豁免。

当前强制扫描范围：业务文档、个人 Token、管理员 Token、测试资产目录 4 个 Controller，以及 `caseGenerate.ts`、`agentIntegration.ts`、`ai-execution.ts` 3 个前端 API 模块。该范围覆盖本轮发现并补齐的接口偏差，不宣称已经替代平台所有历史模块的全仓接口治理。
