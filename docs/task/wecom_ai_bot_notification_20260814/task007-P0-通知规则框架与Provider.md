# task007 - P0 通知规则框架与 Trigger Provider

> 状态：代码实现完成，待集成环境验收  
> 前置依赖：task003  
> 阻塞任务：task008、task009、task010、task012、task013  
> 关联方案：§4.2.3、§5.3、§8、§10

## 1. 任务目标

建立统一、可扩展但不可执行任意代码的通知规则框架，为缺陷临期、报告生成和通用 Cron 提供一致配置、校验、预览和收件人解析。

## 2. 首期类型

```text
BUG_EXPECTED_RESOLUTION_DUE  -> DEADLINE
TEST_REPORT_GENERATED        -> EVENT
CUSTOM_CRON                  -> CRON
```

每种类型通过 `NotificationTriggerProvider` 注册：

- 支持的作用域；
- 支持的对象/业务角色；
- 支持的时间策略；
- 可用模板变量；
- 参数 Schema 和校验器；
- 停止条件解释器；
- 预览所需的安全数据加载器。

## 3. 任务清单

- [ ] Provider 接口和注册表；
- [ ] 未知 notificationType 拒绝；
- [ ] Rule CRUD、复制、启停和预览；
- [ ] JSON trigger/recipient/stop 配置类型化 DTO；
- [ ] `WecomRecipientResolver`；
- [ ] 用户、业务角色、项目成员、项目角色、用户组、群聊解析；
- [ ] 模板白名单、缺失变量、长度和 URL 校验；
- [ ] 保存规则时做作用域和对象权限校验；
- [ ] 执行时再次校验资源与用户有效性；
- [ ] 规则版本或 updateTime 供 Timer/事件识别旧配置；
- [ ] 操作日志和变更前后值脱敏。

## 4. 接口

```text
GET    /wecom-bot/notification-rules
POST   /wecom-bot/notification-rules
PUT    /wecom-bot/notification-rules/{id}
DELETE /wecom-bot/notification-rules/{id}
POST   /wecom-bot/notification-rules/{id}/enable
POST   /wecom-bot/notification-rules/{id}/disable
POST   /wecom-bot/notification-rules/{id}/preview
POST   /wecom-bot/notification-rules/{id}/run-once
```

## 5. 禁止能力

- 任意 Java 类名；
- SQL、SpEL、Shell、JavaScript/Python；
- 越过项目权限选择其他项目用户或群；
- 在模板中读取任意 Bean、环境变量或 Secret；
- 将前端传入 URL 作为服务端任意请求目标。

## 6. 验收标准

- [ ] 三种类型都能使用统一 Rule 模型；
- [ ] 非法组合在保存阶段给出明确错误；
- [ ] 规则预览不发送真实消息；
- [ ] 对象解析动态读取最新用户/项目关系；
- [ ] 未知变量和越权对象不能通过；
- [ ] 后续新增类型无需修改核心 Dispatcher。
