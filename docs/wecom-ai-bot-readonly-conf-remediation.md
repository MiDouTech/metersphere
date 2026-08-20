# MeterSphere 企微 AI Bot 容器启动失败运维处置单

## 1. 处置目标

解决 `metersphere` 容器因无法创建企微 Secret 加密主密钥而持续重启的问题，并保持现有 `/opt/metersphere/conf` 只读挂载策略。

## 2. 当前现象

容器日志持续出现：

```text
/deployments/run-with-wecom-bridge.sh: line 11: can't create /opt/metersphere/conf/.wecom-master-key: Read-only file system
```

当前挂载检查结果：

```text
ReadonlyRootfs=false
/opt/metersphere/conf -> /opt/metersphere/conf RW=false
/data/metersphere/logs -> /opt/metersphere/logs RW=true
```

## 3. 根因

企微连接器已经内置到 MeterSphere 后端镜像。容器首次启动时，启动脚本需要在以下路径生成企微 Bot Secret 的加密主密钥：

```text
/opt/metersphere/conf/.wecom-master-key
```

宿主机目录 `/opt/metersphere/conf` 被只读挂载到容器，因此容器无法创建该文件。启动脚本以非零状态退出，Docker 重启策略随后反复重启容器。

此问题与 Flyway 数据库迁移无关。

## 4. 运维执行前确认

在宿主机 `aliy-docker2` 上使用具备相应权限的账号执行。

首先检查主密钥是否已经存在：

```bash
if [ -s /opt/metersphere/conf/.wecom-master-key ]; then
  echo "master key exists"
else
  echo "master key is missing"
fi
```

禁止执行 `cat`、`head`、`tail` 等命令查看或输出主密钥内容。

> 重要：如果该环境以前已经在 MeterSphere 页面保存过企微 Bot Secret，应优先从备份恢复原来的 `.wecom-master-key`。生成新密钥后，使用旧密钥加密的历史 Secret 将无法解密。

## 5. 推荐处置方案：宿主机预生成密钥，容器继续只读挂载

仅当 `.wecom-master-key` 不存在或为空，并且确认不需要恢复历史密钥时执行：

```bash
if [ ! -s /opt/metersphere/conf/.wecom-master-key ]; then
  umask 077
  openssl rand -base64 48 > /opt/metersphere/conf/.wecom-master-key
fi
chown 1000:1000 /opt/metersphere/conf/.wecom-master-key
chmod 600 /opt/metersphere/conf/.wecom-master-key
```

该命令带有非空判断，不会覆盖已经存在的有效密钥。

检查文件属性，不输出文件内容：

```bash
stat /opt/metersphere/conf/.wecom-master-key
```

期望所有者和权限为：

```text
UID=1000 GID=1000 Access=(0600/-rw-------)
```

检查容器运行用户：

```bash
docker inspect metersphere --format='User={{.Config.User}}'
```

当前基础镜像中的 Java 进程以 `1000:1000` 运行，因此宿主机文件应设置为：

```bash
chown 1000:1000 /opt/metersphere/conf/.wecom-master-key
chmod 600 /opt/metersphere/conf/.wecom-master-key
```

## 6. 重启与验证

重启容器：

```bash
docker restart metersphere
```

查看本次启动的完整日志：

```bash
docker logs --timestamps --since 5m -f metersphere
```

正常启动日志应包含：

```text
wecom bot bridge listening
Started Application
```

另开终端检查容器状态：

```bash
docker inspect metersphere \
  --format='Status={{.State.Status}} ExitCode={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}} RestartCount={{.RestartCount}}'
```

验收要求：

- `Status=running`；
- `OOMKilled=false`；
- 日志不再出现 `Read-only file system`；
- 容器不再持续重启；
- 日志出现 `wecom bot bridge listening`；
- Java 应用日志出现 `Started Application`。

## 7. 失败时收集并回传

如果仍然启动失败，请回传以下输出。禁止回传主密钥内容、Bot Secret、数据库密码、Redis 密码或 Token。

```bash
docker logs --timestamps --tail 2000 metersphere 2>&1
```

```bash
docker inspect metersphere \
  --format='Status={{.State.Status}} ExitCode={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}} Error={{.State.Error}} RestartCount={{.RestartCount}}'
```

```bash
docker inspect metersphere \
  --format='User={{.Config.User}} ReadonlyRootfs={{.HostConfig.ReadonlyRootfs}}{{println}}{{range .Mounts}}{{println .Source "->" .Destination "RW=" .RW}}{{end}}'
```

```bash
stat /opt/metersphere/conf/.wecom-master-key
```

## 8. 备份要求

将以下文件纳入受控备份：

```text
/opt/metersphere/conf/.wecom-master-key
```

安全要求：

- 不得提交到 Git；
- 不得通过普通聊天工具传输文件内容；
- 不得写入工单正文或普通日志；
- 备份应加密并限制访问权限；
- 恢复时必须保持文件内容不变；
- 建议保持 `0600` 权限。

## 9. 备选方案：改为可读写挂载

如果不要求配置目录只读，可将发布平台挂载调整为：

```text
/opt/metersphere/conf:/opt/metersphere/conf:rw
```

然后重建 `metersphere` 容器。该方案允许启动脚本自动创建主密钥，但会改变现有只读安全策略，因此本次优先采用“宿主机预生成密钥”方案。

## 10. 职责边界

运维负责：

- 检查、恢复或生成 `.wecom-master-key`；
- 设置宿主机文件权限；
- 维护目录挂载和备份；
- 重启容器并确认启动成功；
- 回传脱敏后的容器状态和日志。

应用管理员负责：

- 容器恢复后登录 MeterSphere；
- 进入“系统设置 → 系统参数设置 → 扫码登录 → 企微智能机器人”；
- 填写 BotID 和 Secret；
- 保存并执行“测试连接”；
- 测试成功后启用机器人；
- 验证个人消息、群消息和投递日志。

## 11. 关联说明

常规部署及运行说明参见：`docs/wecom-ai-bot-operations.md`。
