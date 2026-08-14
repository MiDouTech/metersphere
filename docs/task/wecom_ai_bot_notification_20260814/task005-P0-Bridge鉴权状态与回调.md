# task005 - P0 Bridge 鉴权、状态与回调集成

> 状态：代码实现与自动化验证完成，待容器网络联调  
> 前置依赖：task002、task004  
> 阻塞任务：task006、task011、task014  
> 关联方案：§6.2、§7、§8

## 1. 任务目标

打通 Java 与 Bridge 的双向机器通信，使发送、连接状态、会话事件和投递结果具备身份认证、幂等与防重放能力。

## 2. Java → Bridge

- [ ] `WecomBotBridgeClient`；
- [ ] 连接池、连接超时、读取超时和有限重试；
- [ ] Bearer/HMAC 鉴权；生产优先 mTLS；
- [ ] 透传 `requestId`，不得在 HTTP 层生成第二个业务 ID；
- [ ] 解析 ONLINE、AUTH_FAILED、限流和目标错误；
- [ ] 熔断/暂停策略，避免 Bridge 离线时请求风暴；
- [ ] 日志脱敏。

## 3. Bridge → Java

```text
POST /internal/wecom-bot/events/status
POST /internal/wecom-bot/events/chat
POST /internal/wecom-bot/events/delivery
```

- [ ] 校验 timestamp、nonce、body hash 和签名；
- [ ] Redis 或数据库记录 nonce，拒绝重放；
- [ ] 校验允许时间偏差；
- [ ] eventId 幂等；
- [ ] 只允许内网访问；
- [ ] 错误响应不泄露鉴权细节。

## 4. 状态一致性

- Bridge 状态为事实来源；
- Java 保存最近状态、认证时间、心跳和脱敏错误；
- 心跳超时后 Java 将状态视为 OFFLINE；
- AUTH_FAILED 暂停 Outbox 消费，凭证更新或人工重试后恢复；
- 管理页面不得仅根据进程存活显示 ONLINE。

## 5. 验收标准

- [ ] 伪造签名、过期请求、重复 nonce 均被拒绝；
- [ ] Bridge 断开、认证失败和恢复状态能正确落库；
- [ ] Java 超时不会导致重复业务请求；
- [ ] 内部 API 不进入普通匿名公网白名单；
- [ ] 自动化测试覆盖双向鉴权和重放攻击。
