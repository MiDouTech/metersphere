# AI Web UI 自动化执行验证记录

> 日期：2026-08-06
> 结论：代码级验证通过；真实环境全链路验收未完成。

## 已执行

| 验证项 | 结果 | 说明 |
| --- | --- | --- |
| 后端关联编译 | 通过 | `mvnw -pl backend/services/agent-integration -am -DskipTests -DskipAntRunForJenkins=true compile`，19/19 模块成功 |
| 后端定向单测 | 通过 | NL 解析、状态机、自愈策略、动作契约共 10 项，0 失败 |
| Runner 构建与测试 | 通过 | TypeScript 构建及 7 项测试，包含真实 Chromium 烟测 |
| 前端类型检查 | 通过 | `npm run type:check` |
| Mapper XML 解析 | 通过 | PowerShell XML 解析无错误 |
| Runner 依赖审计 | 通过 | 锁文件生成时报告 0 vulnerabilities |

## 已覆盖的关键行为

- 手工用例范围和自然语言筛选 DSL；筛选结果、原因、置信度和快照摘要可预览。
- AI 将人工步骤转换为受控 v1 动作/断言契约，服务端与 Runner 双重白名单校验。
- Chromium Context 按任务隔离，支持人工登录等待、暂停、取消和安全点控制。
- 截图遮罩、哈希校验、租约鉴权、证据关联、预览下载和到期清理。
- 低风险唯一候选的有限自愈；高风险动作第一阶段直接拒绝。
- 事件序号/ID 幂等、租约过期回收、按失败用例重试、计划内外结果幂等回写及部分成功。

## 待真实环境验证

- V31 数据库迁移正向执行、升级耗时及回滚兼容性。
- MeterSphere 应用、真实 AI Provider、MinIO 和 Browser Runner 的协议联调。
- 20 条标准用例连续 10 轮，以及人工登录、验证码/MFA、断线恢复和浏览器崩溃。
- 跨项目/跨组织、SSRF/DNS Rebinding、Prompt Injection、恶意上传和令牌重放专项。
- Provider、MinIO、数据库短暂故障注入，压力容量基线、告警平台及灰度回滚演练。

在上述待验证项完成并留存证据前，不得将 task014 或整体方案标记为生产完成。
