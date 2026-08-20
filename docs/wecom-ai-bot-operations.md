# 企微 AI 机器人运维说明

## 部署方式

企微连接器已经内置到 MeterSphere 后端镜像。运维侧只部署一个 `metersphere` 容器，不需要：

- 单独部署 `wecom-bot-bridge`；
- 创建 Bridge Docker 网络；
- 创建 Bridge Token 或 Callback Token；
- 对外开放 8095 端口。

容器启动时会自动启动内部连接器并生成内部通信 Token。企微 Secret 的加密主密钥首次启动时自动写入：

```text
/opt/metersphere/conf/.wecom-master-key
```

因此必须持久化挂载 `/opt/metersphere/conf`，并且不要删除该文件。

MeterSphere Java 进程默认以 `1000:1000` 运行。若配置目录以只读方式挂载，运维必须在宿主机预先创建主密钥，并设置：

```bash
chown 1000:1000 /opt/metersphere/conf/.wecom-master-key
chmod 600 /opt/metersphere/conf/.wecom-master-key
```

首次启动且目录内没有主密钥时，配置目录必须可写；否则启动脚本会明确报错并终止。不要生成新文件覆盖已有主密钥，否则平台中历史加密的企微 Secret 将无法解密。

## 平台配置

进入系统设置中的企微机器人配置页面，填写企业微信 AI Bot 的 Bot ID 和 Secret，保存后执行“测试连接”，测试成功后启用。

## 状态检查

所有日志均在主容器中查看：

```bash
docker logs --tail 500 metersphere | grep -Ei 'wecom|bot|websocket|error'
```

主容器内的连接器只监听 `127.0.0.1:8095`，外部无法直接访问。

正常状态下，同一容器中必须同时存在 Node.js Bridge 和 Java 进程：

```bash
docker top metersphere -eo pid,user,comm,args
```

容器内部存活检查：

```bash
docker exec metersphere node -e "fetch('http://127.0.0.1:8095/health/live').then(async r=>{console.log(await r.text());process.exit(r.ok?0:1)}).catch(e=>{console.error(e.message);process.exit(1)})"
```

预期返回：

```json
{"status":"UP"}
```

启动日志应同时包含：

```text
wecom bot bridge listening
Started Application
```

镜像构建阶段会执行 `node --version`，确保最终 Java 镜像包含 Node 所需的 `libstdc++` 和 `libgcc`。Bridge 启动失败、健康检查超时或运行中异常退出时，主容器也会退出并由 Docker 重启策略整体恢复，不再保留“仅 Java 正常”的假健康状态。

## 升级与重启

重启或升级 `metersphere` 容器后，内部连接器会自动启动，并从数据库读取平台已保存的配置。无需单独启动任何进程。

如果 `.wecom-master-key` 丢失，历史加密的企微 Secret 将无法解密，需要在页面重新填写 Secret。因此应将整个 `/opt/metersphere/conf` 纳入备份。

## 常见故障

| 日志或现象 | 原因 | 处理方式 |
| --- | --- | --- |
| `Read-only file system` | 只读配置挂载中没有预置主密钥 | 在宿主机恢复或创建主密钥，并设置 `1000:1000/600` |
| `Unable to encrypt WeCom Bot secret` | Java 用户无法读取主密钥 | 检查主密钥 UID/GID 和权限，禁止输出密钥内容 |
| `libstdc++.so.6` 或 `libgcc_s.so.1` 缺失 | 使用了未包含 Node 运行库的旧镜像 | 发布包含运行库修复的新镜像，不要只在运行中容器临时安装 |
| `MS_WECOM_BRIDGE_TOKEN is not configured` | Node 无法生成内部 Token，或 Java 未继承启动环境 | 先确认 Node 进程和 Bridge 健康接口；内部 Token 不需要人工分发 |
| 容器运行但没有 Node 进程 | 旧启动脚本没有监控 Bridge 子进程 | 升级新镜像，新脚本会让容器快速失败并整体重启 |
