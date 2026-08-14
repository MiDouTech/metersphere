# task006 - P0 群会话发现与目标管理

> 状态：代码实现完成，待真实群消息发现验收  
> 前置依赖：task005  
> 阻塞任务：task009、task012、task015  
> 关联方案：§5.2、§7.3、§8.5

## 1. 任务目标

从企微机器人真实事件中采集内部群 `chatid`，提供安全的群目标选择、备注、启停和测试发送能力。

## 2. 发现流程

1. 管理员把 Bot 加入内部群；
2. 群成员在群内 @Bot 发送测试消息；
3. Bridge 标准化并上报 `eventId/chatid/chattype/from.userid`；
4. Java 校验 Bot 配置并按 eventId 去重；
5. upsert `wecom_bot_chat`，更新 `last_seen_at`；
6. 管理员设置备注并显式启用该群；
7. 启用前可发送测试消息。

## 3. 任务清单

- [ ] chat 事件 DTO、签名校验和 eventId 幂等；
- [ ] 只接受 GROUP 事件进入群目标列表；
- [ ] `(botConfigId, chatId)` upsert；
- [ ] chatid API 脱敏展示；
- [ ] 管理员备注、启用、禁用；
- [ ] 测试发送使用 Outbox 或明确标记的可靠发送入口；
- [ ] 最近发现时间和最近发送状态；
- [ ] Bot 被移出群或目标无效时标记异常建议；
- [ ] 群名称不作为唯一键和发送依据；
- [ ] 禁止前端自由提交未知 chatid。

## 4. 接口

```text
GET  /wecom-bot/chats
POST /wecom-bot/chats/{id}/rename
POST /wecom-bot/chats/{id}/enable
POST /wecom-bot/chats/{id}/disable
POST /wecom-bot/messages/test-group
```

## 5. 验收标准

- [ ] 测试群 @Bot 后平台在合理时间内发现群；
- [ ] 重复事件不会创建重复群记录；
- [ ] 未启用群不能被通知规则选择；
- [ ] 伪造 chatid 无法通过公开 API 注入；
- [ ] Bot 移出群后的失败可诊断且不无限重试。
