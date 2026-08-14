# task011 - P0 Outbox 可靠投递、幂等与重试

> 状态：代码实现完成，待数据库并发与故障演练  
> 前置依赖：task003、task005  
> 阻塞任务：task008、task009、task010、task012、task015  
> 关联方案：§5.5、§8.4、§13

## 1. 任务目标

所有业务触发统一先写 Outbox，再异步调用 Bridge，保证事务边界、进程重启、网络异常和多实例条件下不丢失、不无界重复。

## 2. 状态机

```text
PENDING -> SENDING -> SUCCESS
                  -> RETRY -> SENDING
                  -> FAILED
PENDING/RETRY -> CANCELLED
```

## 3. 任务清单

- [ ] 业务事务内批量创建 Outbox；
- [ ] 唯一 triggerKey + targetType + targetId；
- [ ] `SELECT ... FOR UPDATE SKIP LOCKED` 或等效原子认领；
- [ ] SENDING 租约与超时回收；
- [ ] Bridge requestId 固定使用 outbox.id；
- [ ] 成功、临时失败、永久失败映射；
- [ ] 默认 1/5/15 分钟三次重试；
- [ ] 限流尊重 Retry-After 或错误建议；
- [ ] AUTH_FAILED/Bridge OFFLINE 暂停通道，不耗尽单条重试；
- [ ] 人工重试仅允许可重试失败，生成审计记录；
- [ ] 规则/资源失效时取消尚未发送消息；
- [ ] 正文按策略保存摘要/哈希，目标脱敏展示；
- [ ] 日志分页和失败详情 API；
- [ ] 定期清理按保留策略执行。

## 4. 接口

```text
GET  /wecom-bot/messages/logs
GET  /wecom-bot/messages/{id}
POST /wecom-bot/messages/{id}/retry
```

## 5. 并发与故障测试

- 两个 Java 实例同时消费；
- Bridge 超时但实际已受理；
- Java 在发送后、落成功前崩溃；
- Bridge 重启和 requestId 重放；
- 数据库短暂异常；
- 同一业务事件重复发布；
- AUTH_FAILED 后更新 Secret 恢复。

## 6. 验收标准

- [ ] 业务事件和 Timer 不直接调用 Bridge；
- [ ] 重启后 PENDING/RETRY 可继续处理；
- [ ] 相同 trigger/target 不重复落库；
- [ ] 相同 requestId 不重复发送；
- [ ] 错误分类可读且重试策略正确；
- [ ] 多实例下无重复投递和消息长期卡死。
