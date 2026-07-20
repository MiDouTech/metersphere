# 生产 / 测试环境隔离配置

本仓库已支持：

- **Kafka**：同一 broker，不同环境使用不同 topic 前缀与 consumer group 后缀
- **MinIO**：通过 `minio.endpoint` + `minio.bucket` + 独立 AK/SK 隔离

## 1. Kafka（共用 broker）

在对应环境的 `metersphere.properties` 中配置：

### 生产

```properties
kafka.bootstrap-servers=10.0.1.147:9092
kafka.topic.prefix=prod.
kafka.consumer.group.suffix=_prod
```

实际 topic 示例：`prod.EXPORT`、`prod.API_REPORT_TASK_TOPIC`

### 测试

```properties
kafka.bootstrap-servers=10.0.1.147:9092
kafka.topic.prefix=test.
kafka.consumer.group.suffix=_test
```

实际 topic 示例：`test.EXPORT`、`test.API_REPORT_TASK_TOPIC`

### 说明

- 留空 `kafka.topic.prefix` 则与官方默认 topic 名一致（单环境）
- 两套 MeterSphere **同时在线** 时，生产和测试必须使用不同前缀/后缀
- topic 在首次发送/消费时自动创建（Kafka `auto.create.topics.enable=true`）

## 2. MinIO（推荐双实例）

MeterSphere 支持配置：

```properties
minio.endpoint=http://10.0.1.147:9000
minio.access-key=ms-prod
minio.secret-key=生产强密码
minio.bucket=metersphere
```

### 方案 A：同机双 MinIO（省机器）

| 环境 | API 端口 | 数据目录 | bucket | AK |
|------|----------|----------|--------|-----|
| 生产 | 9000 | /data/minio-prod | metersphere | ms-prod |
| 测试 | 9002 | /data/minio-test | metersphere | ms-test |

生产 `metersphere.properties`：

```properties
minio.endpoint=http://10.0.1.147:9000
minio.access-key=ms-prod
minio.secret-key=xxx
minio.bucket=metersphere
```

测试 `metersphere.properties`：

```properties
minio.endpoint=http://10.0.1.147:9002
minio.access-key=ms-test
minio.secret-key=xxx
minio.bucket=metersphere
```

### 方案 B：两台 MinIO（最稳）

生产和测试各一台 ECS，仅 `minio.endpoint` 不同，其余同上。

### MinIO 初始化命令（生产实例 9000 示例）

```bash
docker exec -it minio mc alias set local http://127.0.0.1:9000 minioadmin 'root密码'
docker exec -it minio mc admin user add local ms-prod '生产强密码'
docker exec -it minio mc mb local/metersphere --ignore-existing
```

测试实例在 9002 上同样执行，用户改为 `ms-test`。

## 3. 构建与部署流程

```bash
# 1. 在功能分支开发完成后推送
git push origin feature/env-isolation-kafka-minio

# 2. 合并到 v3.x 后，GitHub Actions 自动构建后端镜像

# 3. 服务器更新后端
cd /opt/metersphere
docker compose pull metersphere
docker compose up -d --force-recreate metersphere

# 4. 确认配置
grep -E 'kafka.topic.prefix|minio.endpoint|minio.bucket' /opt/metersphere/conf/metersphere.properties
```

## 4. 与 MySQL / Redis 配合

| 组件 | 生产 | 测试 |
|------|------|------|
| MySQL | 独立库 `metersphere_prod` | 独立库 `metersphere` |
| Redis | `database=1` 或独立实例 | `database=0` |
| Kafka | 同一 broker + `prod.` 前缀 | 同一 broker + `test.` 前缀 |
| MinIO | 独立 endpoint / 实例 | 独立 endpoint / 实例 |
