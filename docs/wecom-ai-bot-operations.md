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

## 平台配置

进入系统设置中的企微机器人配置页面，填写企业微信 AI Bot 的 Bot ID 和 Secret，保存后执行“测试连接”，测试成功后启用。

## 状态检查

所有日志均在主容器中查看：

```bash
docker logs --tail 500 metersphere | grep -Ei 'wecom|bot|websocket|error'
```

主容器内的连接器只监听 `127.0.0.1:8095`，外部无法直接访问。

## 升级与重启

重启或升级 `metersphere` 容器后，内部连接器会自动启动，并从数据库读取平台已保存的配置。无需单独启动任何进程。

如果 `.wecom-master-key` 丢失，历史加密的企微 Secret 将无法解密，需要在页面重新填写 Secret。因此应将整个 `/opt/metersphere/conf` 纳入备份。
