# task001 - P0 企业微信官方 SDK 协议 PoC

> 状态：SDK 接入与自动化验证完成，待真实企微 Bot 凭据验收  
> 前置依赖：企微测试 BotID/Secret、测试 userid、测试内部群  
> 阻塞任务：task002、task006、task009  
> 关联方案：§3、§7、§14

## 1. 任务目标

在不接入生产业务的前提下，使用官方 `@wecom/aibot-node-sdk` 验证当前企微租户的真实协议能力，冻结 Bridge 所需契约。

## 2. 任务清单

- [ ] 创建隔离 PoC，不把 Secret 写入源码；
- [ ] 连接 `wss://openws.work.weixin.qq.com` 并完成认证；
- [ ] 记录 authenticated、close、error、heartbeat 行为；
- [ ] 向测试 `userid` 主动发送 Markdown；
- [ ] 将 Bot 加入测试内部群，群内 @Bot 触发事件；
- [ ] 从事件中确认 `chatid/chattype/from.userid/msgid` 字段；
- [ ] 使用 `chatid` 主动发送群消息；
- [ ] 实测 @单人、@多人、非群成员、重复 userid 和大量成员；
- [ ] 验证无效 userid、无效 chatid、Bot 被移出群、用户不在可见范围的错误；
- [ ] 验证断网、恢复、Secret 错误和 Secret 轮换；
- [ ] 整理可重试/不可重试错误样例，不记录 Secret。

## 3. 输出契约

形成 PoC 结果记录，至少包括：

```text
SDK 版本
Node.js 版本
WebSocket 地址
认证成功事件
主动发送请求/响应结构
群事件结构
成员提及格式
错误码分类
消息长度/成员数量限制
重连行为
私有部署差异（如适用）
```

## 4. 安全要求

- Secret 只通过临时环境变量注入；
- PoC 日志中的 BotID、userid、chatid 脱敏；
- 测试群和测试用户不得使用生产敏感消息；
- PoC 完成后清除临时 Secret，并视情况在企微后台轮换。

## 5. 验收标准

- [ ] 完成 Bot 认证、个人发送、群发现、群发送四个核心实测；
- [ ] @项目成员的真实 SDK 格式得到验证，不依赖猜测；
- [ ] Bridge 输入、输出、错误分类能够据此冻结；
- [ ] 不修改 MeterSphere 业务代码，不泄露凭证。
