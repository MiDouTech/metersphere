# MeterSphere 发布平台配置（单容器、文件配置）

## 镜像

```text
docker.cnb.cool/miduoyanfa/middleground/metersphere/metersphere-backend:latest
```

## 环境变量

```env
SPRING_PROFILES_ACTIVE=local
MS_CONFIG_DIR=/opt/metersphere/conf
MS_REDISSON_CONFIG=file:/opt/metersphere/conf/redisson.yml
```

依赖地址可以直接写入 `/opt/metersphere/conf/metersphere.properties`，也可以通过该文件引用的 `MYSQL_*`、`KAFKA_BOOTSTRAP_SERVERS`、`MINIO_*` 环境变量注入。

不需要任何 `MS_WECOM_BRIDGE_*` 配置。

## 挂载与端口

| 宿主机路径/端口 | 容器路径/端口 |
|---|---|
| `/opt/metersphere/conf` | `/opt/metersphere/conf` |
| `/opt/metersphere/logs` | `/opt/metersphere/logs` |
| `8081` | `8081` |
| `7071` | `7071` |

## 发布操作

1. 确认宿主机存在 `metersphere.properties` 和 `redisson.yml`。
2. 发布平台拉取最新后端镜像。
3. 重建一个 `metersphere` 容器。
4. 查看 `docker logs -f metersphere`。

企微连接器已在镜像内，由同一个容器自动启动和停止，不需要额外服务。
