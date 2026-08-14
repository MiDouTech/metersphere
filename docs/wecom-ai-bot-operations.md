# 企微智能机器人部署与运维

## 1. 运行边界

- Bridge 首期只运行 1 个副本，仅需出站访问 `openws.work.weixin.qq.com:443`。
- 8095 端口只在 MeterSphere 内部网络开放，不映射公网。
- Java 管理接口继续使用登录、CSRF 和权限控制；`/internal/wecom-bot/events/**` 不使用用户会话，但控制器强制校验 5 分钟时间窗、HMAC-SHA256、nonce 和 eventId。
- Bot Secret、Bridge Token、Callback Token、加密主密钥均使用 Docker/Kubernetes Secret 或受控环境变量，禁止写入 Git、Nacos 明文和日志。

## 2. 必需配置

MeterSphere Java 容器：

```text
MS_WECOM_BRIDGE_URL=http://wecom-bot-bridge:8095
MS_WECOM_BRIDGE_TOKEN_FILE=/run/secrets/wecom_bridge_token
MS_WECOM_BRIDGE_CALLBACK_TOKEN_FILE=/run/secrets/wecom_callback_token
MS_WECOM_SECRET_MASTER_KEY_FILE=/run/secrets/wecom_master_key
```

Bridge 容器使用 `deploy/docker-compose.wecom-bot.yml`，并设置 `MS_WECOM_IDEMPOTENCY_FILE=/var/lib/wecom-bot/idempotency.json`，通过持久化卷保存请求幂等记录。Token 文件必须与 Java 侧使用同一份。Bot Secret 推荐在页面保存引用 `env:MS_WECOM_BOT_SECRET`，Java 容器配置 `MS_WECOM_BOT_SECRET_FILE`；也可启用主密钥后由页面提交新 Secret，加密落库。现有 `deploy/docker-run.sh` 会把 Java 后端加入 Bridge 网络并挂载上述三项运行时 Secret。

启动前先创建内部网络，并将 MeterSphere 后端加入该网络：

```bash
docker network create metersphere-internal
docker compose -f deploy/docker-compose.wecom-bot.yml config
docker compose -f deploy/docker-compose.wecom-bot.yml up -d --build
docker compose -f deploy/docker-compose.wecom-bot.yml ps
```

不要在命令行直接写 Secret 值，三个 `*_FILE` 变量应指向宿主机权限为 600 的 Secret 文件。

## 3. 初次配置与群发现

1. 在企微管理端创建智能机器人，确认可见范围并取得 BotID/Secret。
2. 在“系统设置 → 系统参数 → 企微智能机器人”保存名称、BotID 和 Secret 引用。
3. 点击测试连接，状态应依次为 `CONNECTING`、`ONLINE`。
4. 将 Bot 加入内部群并 @Bot 发送任意消息；群会话出现在“群目标”后由管理员启用。
5. 新建规则时只能选择已发现且启用的群，不允许输入 chatid。

## 4. 健康检查与排障

```bash
docker exec wecom-bot-bridge node -e "fetch('http://127.0.0.1:8095/health/live').then(async r=>console.log(r.status,await r.text()))"
docker exec wecom-bot-bridge node -e "fetch('http://127.0.0.1:8095/health/ready').then(async r=>console.log(r.status,await r.text()))"
docker logs --since 10m wecom-bot-bridge
```

- `AUTH_FAILED`：核对 BotID/Secret 与可见范围，轮换 Secret 后重新测试并启用。
- `OFFLINE`：检查 DNS、TLS、代理和到企微 WSS 的 443 出站网络。
- `FAILED/DEAD`：在投递日志查看脱敏错误；修复后人工重试。永久目标错误不会自动无限重试。
- Outbox 积压：查询 `wecom_notification_outbox` 中 `PENDING/FAILED` 数量及最早 `create_time`；超过 10 分钟告警。
- Timer 积压：查询 `wecom_notification_timer` 中 `WAITING AND next_fire_at < 当前时间` 的数量。

建议告警阈值：Bot 离线 5 分钟、出现认证失败、最老 Outbox 超过 10 分钟、到期 Timer 超过 100 条。结构化日志按 requestId/outboxId 关联检索，严禁输出完整目标 ID、Token 或 Secret。

## 5. Secret 轮换与紧急停用

轮换时先更新 Secret 文件或页面 Secret，再点击“测试连接”；成功后启用。Token 轮换需同时更新 Java 与 Bridge Secret 并滚动重启，避免只更新单侧。

紧急停用顺序：先在页面停用 Bot，再停用相关规则，最后停止 Bridge。停用会阻止新规则触发和发送；历史 Outbox 保留用于审计。

## 6. 回滚与数据保留

应用回滚时保留六张 `wecom_*` 表，禁用 Bot/规则即可停止功能，不要回滚已经成功执行的 Flyway 记录。Outbox 和 callback 去重记录可按合规周期离线归档后分批清理；清理前保留失败审计数据。

真实企微的消息字段、群 @ 展示、错误码和限流行为必须在预发布环境用最小可见范围账号完成 task001/task015 验收，凭据不得提交到仓库。
