# task008 - P0 缺陷预计解决时间提醒

> 状态：代码实现完成，待数据库与企微全链路验收  
> 前置依赖：task003、task007、task011  
> 阻塞任务：task015  
> 关联方案：§4.2.1、§5.4、§8.3

## 1. 任务目标

基于已经存在的 `bug.expected_resolve_time` / `expectedResolveTime`，在预计解决时间前按配置提前量和间隔提醒处理人与创建人，并在到期或资源失效时停止。

## 2. 明确边界

默认提醒窗口：

```text
[expectedResolveTime - leadTime, expectedResolveTime]
```

默认接收者：处理人 + 创建人，按 MeterSphere 用户 ID 和最终 userid 双重去重。

停止条件：

- 当前时间超过预计解决时间；
- 缺陷进入规则配置的工作流终态；
- 缺陷被删除；
- 预计解决时间清空；
- 规则、项目或 Bot 停用。

首期不做无限逾期提醒；若后续需要，必须配置逾期间隔和最长持续时间。

## 3. 任务清单

- [ ] `BugExpectedResolutionChanged` 事务提交后事件；
- [ ] 缺陷创建、编辑时间、修改处理人、状态变化、删除均触发刷新判断；
- [ ] `BugExpectedResolutionTimerService` upsert/cancel；
- [ ] 系统级分钟扫描器，不为每个缺陷创建 Quartz Job；
- [ ] Timer 原子认领、处理租约和崩溃回收；
- [ ] 执行前重新读取缺陷与规则；
- [ ] 使用缺陷 `updateTime`/规则版本淘汰旧 Timer；
- [ ] 解析当前处理人和创建人；
- [ ] 缺少 userid 的对象记失败明细但不阻塞其他人；
- [ ] Outbox triggerKey 包含 bugId、ruleId、scheduledFireTime；
- [ ] 计算下一间隔，不得越过 deadline；
- [ ] 缺陷列表临期筛选索引 EXPLAIN 验证。

## 4. 模板变量

```text
${bugNum}
${bugTitle}
${bugHandlerNames}
${bugCreatorName}
${expectedResolveTime}
${remainingTime}
${projectName}
${resourceUrl}
```

## 5. 关键测试

- 提前 2 天、间隔 12 小时；
- 提前 6 小时、间隔 1 小时；
- 提前量大于剩余时间；
- 处理人与创建人为同一人；
- 修改预计时间到更早/更晚；
- 清空预计时间；
- 修改处理人；
- 提前关闭、删除；
- 扫描并发、进程重启、同一分钟重复扫描；
- 到期边界前后 1 毫秒；
- 用户没有 wecom_userid。

## 6. 验收标准

- [ ] 提醒从配置窗口开始，按间隔执行；
- [ ] 处理人和创建人收到个人会话消息且不重复；
- [ ] 字段/状态变化后旧提醒不会继续发送；
- [ ] 到期后不产生新提醒；
- [ ] 多实例扫描不重复创建 Outbox；
- [ ] 不重复修改已完成的预计解决时间字段基础链路。
