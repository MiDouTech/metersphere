# MeterSphere 生产部署（文件配置，无 Nacos）

> **当前方案**：`SPRING_PROFILES_ACTIVE=local`，业务配置读宿主机  
> `/opt/metersphere/conf/metersphere.properties` + `redisson.yml`。  
> **不连接 Nacos**。云开发 + 服务器部署；本机一键启动已归档见 [`legacy/local-dev/`](../legacy/local-dev/README.md)。

> **安全**：真实地址与密码只写在服务器文件中，**禁止提交 Git**。  
> `env.file`、`metersphere.properties`、`redisson.yml`（非 example）已列入忽略规则。

历史「方案 A：Nacos」脚本仍保留在 `deploy/docker-run.sh` / `env.prod.example`，默认不再使用。

## 独立发布平台（推荐）

拉取 CNB 镜像后，在发布平台配置：

| 项 | 值 |
|----|-----|
| 环境变量 `SPRING_PROFILES_ACTIVE` | `local`（**必须**，勿再用 `nacos`） |
| 卷挂载 | 宿主机 conf → `/opt/metersphere/conf`（含 `metersphere.properties`、`redisson.yml`） |
| 卷挂载 | 宿主机 logs → `/opt/metersphere/logs` |
| 端口 | `8081`、`7071` |

**不要**再注入 `NACOS_*`（可省略）。  
详细见：[publish-platform.md](./publish-platform.md)

## 前置条件

1. MySQL / Redis / Kafka / MinIO 已就绪，且应用机可内网访问
2. 服务器已准备好配置文件（见下）
3. 已拉取镜像：  
   `docker.cnb.cool/miduoyanfa/middleground/metersphere/metersphere-backend:latest`

## 部署步骤（应用机手工）

### 1. 准备宿主机目录与配置

```bash
mkdir -p /opt/metersphere/conf /opt/metersphere/logs

cp deploy/conf/metersphere.properties.example /opt/metersphere/conf/metersphere.properties
cp deploy/conf/redisson.yml.example /opt/metersphere/conf/redisson.yml

vim /opt/metersphere/conf/metersphere.properties   # 填写 MySQL / Kafka / MinIO
vim /opt/metersphere/conf/redisson.yml             # 填写 Redis
```

`metersphere.properties` 关键项示例（值用现网真实内网地址，勿回写仓库）：

```properties
spring.datasource.url=jdbc:mysql://<mysql-host>:3306/metersphere?...
kafka.bootstrap-servers=<kafka-host>:9092
minio.endpoint=http://<minio-host>:9000
logging.file.path=/opt/metersphere/logs/metersphere
spring.redis.redisson.file=file:/opt/metersphere/conf/redisson.yml
run.mode=standalone
```

### 2. 准备运行环境变量

```bash
cp deploy/env.file.example /opt/metersphere/env.file
vim /opt/metersphere/env.file
```

关键：

```bash
SPRING_PROFILES_ACTIVE=local
MS_IMAGE=docker.cnb.cool/miduoyanfa/middleground/metersphere/metersphere-backend:latest
MS_CONTAINER_NAME=metersphere
MS_CONF_DIR=/opt/metersphere/conf
MS_LOG_DIR=/opt/metersphere/logs
# 若仅绑定内网网卡，取消注释并填写，例如：
# MS_HTTP_BIND=10.0.1.1:
```

若现网容器名为 `meterSphere`、日志在 `/data/metersphere/logs`，在 `env.file` 中改成一致即可。

### 3. 启动

```bash
chmod +x deploy/docker-run-file.sh
./deploy/docker-run-file.sh /opt/metersphere/env.file
```

### 4. 验证

```bash
docker ps
docker logs -f metersphere   # 或你设置的容器名
curl -I http://127.0.0.1:8081/
```

成功时：

- 无 `No spring.config.import property has been defined`
- 无 Nacos 连接重试刷屏
- 能连上 MySQL / Redis / Kafka / MinIO

## 故障排查

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| `No spring.config.import` | 仍是 `nacos` profile 或未设 `local` | 检查 `SPRING_PROFILES_ACTIVE=local` |
| 启动后立刻连库失败 | `metersphere.properties` 未改或仍是占位符 | 核对 JDBC / 账号 |
| Redis 失败 | `redisson.yml` 缺失或地址/密码错误 | 检查 conf 挂载 |
| 找不到配置 | 未挂载 `/opt/metersphere/conf` | 检查 volume |
| 附件失败 | MinIO endpoint 错误或未部署 | 确认 MinIO 可达 |

## 前端 Nginx

应用机后端起来后，Nginx 将 `/front/` 反代到后端 `8081`（现网应用机内网地址）。  
仓库 `frontend/nginx.conf` 默认为 `127.0.0.1:8081`，生产请在服务器上覆盖，**勿把真实 IP 提交回 Git**。

## 说明

- 配置热更新：改 `metersphere.properties` / `redisson.yml` 后需**重启容器**
- Redis 仍必需（与是否使用 Nacos 无关）
- 勿使用 `legacy/local-dev` 中弱口令配置上生产
