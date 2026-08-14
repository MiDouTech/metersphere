# task003 - P0 通知数据模型与 Flyway 迁移

> 状态：代码实现完成，待部署数据库执行验证  
> 前置依赖：无  
> 阻塞任务：task004、task007、task008、task011  
> 关联方案：§5

## 1. 任务目标

建立 Bot 配置、群会话、通知规则、临期 Timer 和 Outbox 的持久化模型，为可靠投递和多实例并发提供数据库约束。

## 2. 数据表

- [ ] `wecom_bot_config`；
- [ ] `wecom_bot_chat`；
- [ ] `wecom_notification_rule`；
- [ ] `wecom_notification_timer`；
- [ ] `wecom_notification_outbox`。

字段、状态和索引以关联方案 §5 为基线。迁移版本号以实施时最新版本为准。

## 3. 缺陷字段处理

以下内容已存在，不得重复实施：

```text
bug.expected_resolve_time BIGINT NULL
Bug.expectedResolveTime
V3.7.2_68__bug_expected_resolve_time.sql
```

本任务仅追加适合临期扫描的索引，并先使用 `EXPLAIN` 验证：

```sql
CREATE INDEX idx_bug_project_expected_resolution
    ON bug(project_id, expected_resolve_time, deleted);
```

## 4. 约束与状态

- [ ] BotID 唯一；
- [ ] `(bot_config_id, chat_id)` 唯一；
- [ ] `(rule_id, resource_type, resource_id)` Timer 唯一；
- [ ] `(trigger_key, target_type, target_id)` Outbox 唯一；
- [ ] Timer 到期扫描索引；
- [ ] Outbox 状态/重试时间索引；
- [ ] 所有表具备创建、更新审计字段；
- [ ] JSON 字段兼容当前 MySQL 版本和 MyBatis 使用习惯。

## 5. 代码范围

- Domain、Example、Mapper、Mapper XML；
- 必要的 ExtMapper/Repository；
- 状态常量或枚举；
- Mapper 基础测试；
- Flyway 正向迁移验证。

## 6. 验收标准

- [ ] 空库和已有 3.7.2 数据库均可升级；
- [ ] 重复 trigger/target 被数据库约束阻止；
- [ ] Timer 与 Outbox 到期查询命中索引；
- [ ] 不重复创建预计解决时间字段；
- [ ] 迁移不修改用户当前未提交的缺陷字段代码；
- [ ] 表结构与方案文档一致。
