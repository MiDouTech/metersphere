# task009 - P0 测试报告生成群通知与 @项目人员

> 状态：代码实现完成，待真实群 @ 格式与全链路验收  
> 前置依赖：task006、task007、task011  
> 阻塞任务：task015  
> 关联方案：§4.2.2

## 1. 任务目标

测试计划报告成功生成并提交事务后，向项目配置的内部群发送一次通知，并按规则 @对应人员；默认人员为当前项目全部有效成员。

## 2. 事件设计

新增内部事件 `TEST_REPORT_GENERATED`：

```text
eventId
reportId
testPlanId
projectId
reportName
generatorUserId
generationMode (MANUAL/AUTO)
generatedAt
```

事件发布位置在 `TestPlanReportService` 报告成功写入并事务提交之后，不只在 Controller 添加通知注解，以覆盖：

- `TestPlanReportController.genReportByManual`；
- `TestPlanReportController.genReportByAuto`；
- 后台自动生成和未来其他 Service 调用入口。

事务回滚不得发送通知。

## 3. 接收对象

- 群：规则中选择的、已发现且启用的 chatid；
- 人员默认：当前项目有效角色关系中的全部有效用户；
- 可选：指定成员、项目角色、用户组；
- 过滤：已删除/禁用、无 `wecom_userid` 用户；
- 去重：同一 userid 只 @一次；
- 非群成员：不阻塞群正文发送，记录企微回执/降级信息。

## 4. 任务清单

- [ ] 报告生成事务提交后事件；
- [ ] eventId/reportId 幂等；
- [ ] 手工/自动生成来源开关；
- [ ] 动态查询项目成员；
- [ ] 按 task001 实测格式生成 @内容；
- [ ] 人员/消息长度超过企微限制时分批或安全降级；
- [ ] 一群一条 Outbox，不按成员拆个人消息；
- [ ] 报告链接使用系统 Base URL；
- [ ] 报告摘要查询失败时仍可发送基础通知；
- [ ] 同一报告重复事件不重复发送；
- [ ] 测试计划/项目删除或规则停用时安全跳过。

## 5. 模板变量

```text
${projectName}
${testPlanName}
${reportName}
${reportGeneratorName}
${reportSummary}
${reportUrl}
${generatedAt}
```

## 6. 验收标准

- [ ] 手动和自动报告均覆盖；
- [ ] 报告事务失败/回滚不通知；
- [ ] 每个配置群只收到一条对应报告消息；
- [ ] 默认动态 @ 当前项目全部有效且已映射企微的成员；
- [ ] 规则改为指定人员后，下一份报告使用新规则；
- [ ] 大成员量和非群成员场景有确定降级策略与日志。
