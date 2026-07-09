# Bug 修复记录（buglist）

本目录存放 MeterSphere 自研过程中**已定位并修复**的问题记录，与 `docs/develop_logs/` 开发摘要、`docs/task/` 任务规格配合使用。

## 命名约定

- 文件名：`YYYY-MM-DD-BUGnnn-简短主题.md` 或 `YYYY-MM-DD-BUG-模块-编号-简短主题.md`
- 每篇需包含：现象、根因、修复方案、变更文件、验证步骤

## 索引

| 编号 | 日期 | 文件 | 摘要 |
|------|------|------|------|
| BUG001 | 2026-06-26 | [2026-06-26-BUG001-创建用户成功但列表不显示.md](./2026-06-26-BUG001-创建用户成功但列表不显示.md) | UserXpack Bean 未注册 + Community 实现未落库 |
| BUG-PLAN-001 | 2026-07-04 | [2026-07-04-BUG-PLAN-001-执行历史Tab为空.md](./2026-07-04-BUG-PLAN-001-执行历史Tab为空.md) | listHis 扩展 exec_task + test_plan_report |
| BUG-PLAN-002 | 2026-07-04 | [2026-07-04-BUG-PLAN-002-报告列表为空.md](./2026-07-04-BUG-PLAN-002-报告列表为空.md) | postHandleReport NPE / result_status 未完成 |
| BUG-DASH-001 | 2026-07-04 | [2026-07-04-BUG-DASH-001-工作台orgId为空.md](./2026-07-04-BUG-DASH-001-工作台orgId为空.md) | lastOrganizationId 回退 |
| BUG-SYS-001 | 2026-07-04 | [2026-07-04-BUG-SYS-001-单元测试IDGenerator-NPE.md](./2026-07-04-BUG-SYS-001-单元测试IDGenerator-NPE.md) | Mock IDGenerator |
| BUG-SYS-002 | 2026-07-04 | [2026-07-04-BUG-SYS-002-未认证API返回500.md](./2026-07-04-BUG-SYS-002-未认证API返回500.md) | MsAuthenticationFilter 401 |
| BUG-API-001 | 2026-07-04 | [2026-07-04-BUG-API-001-JDK21-XStream解析失败.md](./2026-07-04-BUG-API-001-JDK21-XStream解析失败.md) | Surefire --add-opens |
| BUG-CASE-001 | 2026-07-04 | [2026-07-04-BUG-CASE-001-caseReview路由404.md](./2026-07-04-BUG-CASE-001-caseReview路由404.md) | 路由重定向 |
| BUG-PRJ-001 | 2026-07-04 | [2026-07-04-BUG-PRJ-001-项目所属组织更新不生效.md](./2026-07-04-BUG-PRJ-001-项目所属组织更新不生效.md) | CommonProjectService.update |

## 汇总文档

- 开发摘要：[2026-07-04-全系统缺陷修复-开发摘要.md](../2026-07-04-全系统缺陷修复-开发摘要.md)
- 测试输出：[MeterSphere-全系统-缺陷清单-2026-07-04.md](../../task/destination/MeterSphere-全系统-缺陷清单-2026-07-04.md)

## 仍开放（未在本目录单独成文）

| 编号 | 状态 | 说明 |
|------|------|------|
| ENV-001 | 环境阻塞 | Testcontainers / Docker |
| BUG-SYS-003 | 待修复 | mvn test 依赖 frontend/dist（文档/流程） |
| BUG-DOC-001 | 待修复 | 测试策略文档路径不一致 |
| BUG-SYS-004 | 待确认 | 系统设置「进入组织」走查覆盖缺口 |
