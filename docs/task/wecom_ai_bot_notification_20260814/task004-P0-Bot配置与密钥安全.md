# task004 - P0 Bot 配置与密钥安全

> 状态：代码实现完成，待部署 Secret 与真实 Bot 验收  
> 前置依赖：task003  
> 阻塞任务：task005、task012、task013  
> 关联方案：§6、§8.1、§8.2

## 1. 任务目标

提供系统级 Bot 配置、启停和连接测试能力，建立 Secret 安全边界。首期按系统级单 Bot 实现，数据模型保留未来多组织扩展空间。

## 2. 配置策略

优先支持部署 Secret 引用：

```text
MS_WECOM_BOT_ID
MS_WECOM_BOT_SECRET
MS_WECOM_BRIDGE_TOKEN
MS_WECOM_BRIDGE_CALLBACK_TOKEN
```

如必须支持页面录入 Secret，则使用独立部署主密钥 AES-GCM 加密；不得复用仅用于掩码的工具冒充加密。

## 3. 任务清单

- [ ] `WecomBotConfigService` 配置读取、保存和更新；
- [ ] GET 返回 `secretConfigured=true/false`，绝不返回原值；
- [ ] 前端提交掩码或空值表示“不修改”；
- [ ] Secret 更新触发受控断开和重连；
- [ ] 启用前校验 BotID、Secret 引用和 Bridge 可用；
- [ ] 禁用后停止新增发送并断开 Bot；
- [ ] 测试连接不落业务消息；
- [ ] 测试个人消息必须选择有效 MeterSphere 用户；
- [ ] 保存、启停、测试均写操作审计；
- [ ] 单测覆盖新建、保留 Secret、轮换、掩码和禁用。

## 4. 接口

```text
GET  /wecom-bot/config
POST /wecom-bot/config
POST /wecom-bot/config/test-connection
POST /wecom-bot/config/enable
POST /wecom-bot/config/disable
GET  /wecom-bot/status
```

## 5. 验收标准

- [ ] API、日志、异常和审计中搜索不到 Secret 原文；
- [ ] Secret 未变化时编辑其他字段不会覆盖凭证；
- [ ] Secret 轮换后旧连接停止、新连接认证成功；
- [ ] 禁用后健康状态和页面状态一致；
- [ ] 无权限用户无法读取或修改 Bot 配置。
