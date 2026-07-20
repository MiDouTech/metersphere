# MeterSphere 生产部署（方案 A：Nacos）

## 独立发布平台（推荐）

若使用独立发布平台拉取 CNB 镜像部署，**无需改 Dockerfile**，只需在发布平台增加一项环境变量：

```env
SPRING_PROFILES_ACTIVE=nacos
```

其余 `NACOS_*`、卷挂载、端口保持平台现有配置不变。  
详细参数见：[publish-platform.md](./publish-platform.md)

> **注意**：仅合并代码并发布镜像，若不加上述环境变量，启动失败不会自动修复。

## 前置条件

1. Nacos 已部署并可访问（如 `aliy-centos7-nacos-redis-mq`）
2. MySQL / Redis / Kafka / MinIO 已就绪
3. 服务器已拉取镜像：
   `docker.cnb.cool/miduoyanfa/middleground/metersphere/metersphere-backend:latest`

## 部署步骤

### 1. 准备 Nacos 配置

编辑 `deploy/nacos/prod/metersphere.properties`，将占位符替换为真实地址：

```properties
spring.datasource.url=jdbc:mysql://10.0.1.x:3306/metersphere?...
spring.datasource.username=...
spring.datasource.password=...
kafka.bootstrap-servers=10.0.1.x:9092
kafka.topic-prefix=metersphere-prod
minio.endpoint=http://10.0.1.x:9000
minio.bucket=metersphere-prod
...
```

推送到 Nacos（在仓库根目录执行）：

```bash
export NACOS_SERVER_ADDR=10.0.1.1:8848
export NACOS_NAMESPACE=prod
export NACOS_USERNAME=nacos
export NACOS_PASSWORD=your-password

chmod +x deploy/seed-nacos-prod.sh
./deploy/seed-nacos-prod.sh deploy/nacos/prod/metersphere.properties
```

在 Nacos 控制台确认：

- namespace: `prod`
- group: `METERSPHERE`
- dataId: `metersphere.properties`

### 2. 准备宿主机目录

```bash
mkdir -p /opt/metersphere/conf /opt/metersphere/logs
cp deploy/conf/redisson.yml.example /opt/metersphere/conf/redisson.yml
vim /opt/metersphere/conf/redisson.yml   # 填写 Redis 地址和密码
```

### 3. 准备运行环境变量

```bash
cp deploy/env.prod.example /opt/metersphere/env.prod
vim /opt/metersphere/env.prod
```

关键项：

```bash
SPRING_PROFILES_ACTIVE=nacos
NACOS_SERVER_ADDR=10.0.1.1:8848
NACOS_NAMESPACE=prod
NACOS_GROUP=METERSPHERE
NACOS_USERNAME=nacos
NACOS_PASSWORD=your-password
```

### 4. 启动容器

```bash
chmod +x deploy/docker-run.sh
./deploy/docker-run.sh /opt/metersphere/env.prod
```

### 5. 验证

```bash
docker ps
docker logs -f metersphere
curl -I http://127.0.0.1:8081/
```

启动成功时，日志中不应再出现 `No spring.config.import property has been defined`。

## 故障排查

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| `No spring.config.import` | 未设置 `SPRING_PROFILES_ACTIVE=nacos` | 检查 `env.prod` 并重启 |
| Nacos 连接失败 | 地址/认证/namespace 错误 | 检查 `NACOS_*` 环境变量 |
| MySQL 连接失败 | Nacos 中 datasource 配置错误 | 在 Nacos 控制台核对 `metersphere.properties` |
| Redis 连接失败 | `redisson.yml` 未挂载或密码错误 | 检查 `/opt/metersphere/conf/redisson.yml` |

## 说明

- `redisson.yml` 不走 Nacos，通过卷挂载到 `/opt/metersphere/conf/redisson.yml`
- 日志目录挂载到 `/opt/metersphere/logs`
- 业务配置（MySQL/Kafka/MinIO 等）统一由 Nacos `metersphere.properties` 管理
