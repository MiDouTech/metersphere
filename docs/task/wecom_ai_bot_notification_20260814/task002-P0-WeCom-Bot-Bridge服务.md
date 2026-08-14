# task002 - P0 WeCom Bot Bridge 服务

> 状态：代码实现与单测完成，待真实企微联调  
> 前置依赖：task001  
> 阻塞任务：task005、task014  
> 关联方案：§3、§7

## 1. 任务目标

新增 `wecom-bot-bridge` Node.js 服务，使用企微官方 SDK 承载长连接协议，为 Java 后端提供稳定、最小化的内网发送与状态接口。

## 2. 目录与依赖

建议新增：

```text
wecom-bot-bridge/
├── package.json
├── package-lock.json
├── src/
├── test/
└── Dockerfile
```

仅引入必要依赖，锁定官方 SDK 精确版本；Node.js 最低版本以官方 SDK 要求为准。

## 3. 任务清单

- [ ] 配置加载与必填项校验；
- [ ] SDK Client 生命周期管理；
- [ ] 认证、心跳、断线重连、指数退避和抖动；
- [ ] 状态机：DISABLED/CONNECTING/ONLINE/OFFLINE/AUTH_FAILED；
- [ ] `GET /health/live`、`GET /health/ready`、`GET /v1/status`；
- [ ] `POST /v1/messages/send`，以 `requestId` 幂等；
- [ ] `POST /v1/reconnect`，只允许受信调用；
- [ ] 个人、群聊、Markdown 消息适配；
- [ ] 群/个人事件标准化上报；
- [ ] SIGTERM 优雅停机；
- [ ] JSON 结构化日志和敏感字段脱敏；
- [ ] 单测覆盖状态机、重连、幂等、异常映射。

## 4. 非目标

- 不保存业务规则、项目权限和 MeterSphere 用户信息；
- 不运行 Quartz；
- 不直接访问 MeterSphere 数据库；
- 不直接暴露公网；
- 首期不实现自然语言问答和 API/MCP 工具。

## 5. 验收标准

- [ ] 容器启动后可认证测试 Bot；
- [ ] ready 只有认证成功后才返回成功；
- [ ] 相同 requestId 重试不会重复发送；
- [ ] 网络恢复后自动重连；
- [ ] 认证失败进入 AUTH_FAILED 且不会高频重试；
- [ ] 日志、响应和健康检查均不泄露 Secret；
- [ ] Docker 镜像可在当前 MeterSphere 部署环境运行。
