# MeterSphere 单容器部署（文件配置）

本项目测试、正式环境均使用本地文件配置。企微 AI 机器人连接器已经内置到后端镜像中，不需要单独部署 Bridge、创建 Docker 网络或维护 Bridge 密钥。

## 部署结构

- 一个 `metersphere` 容器
- `/opt/metersphere/conf/metersphere.properties`：MySQL、Kafka、MinIO 等配置
- `/opt/metersphere/conf/redisson.yml`：Redis 配置
- `/opt/metersphere/logs`：日志目录
- 企微连接器随主容器自动启动，仅监听容器内的 `127.0.0.1:8095`

Java 进程默认以 `1000:1000` 运行。若 `/opt/metersphere/conf` 以只读方式挂载，必须在宿主机预置 `.wecom-master-key`，所有者为 `1000:1000`、权限为 `600`；已有主密钥不得覆盖。

## 发布平台部署

发布平台继续使用后端镜像：

```text
docker.cnb.cool/miduoyanfa/middleground/metersphere/metersphere-backend:latest
```

容器环境变量设置为：

```env
SPRING_PROFILES_ACTIVE=local
MS_CONFIG_DIR=/opt/metersphere/conf
MS_REDISSON_CONFIG=file:/opt/metersphere/conf/redisson.yml
```

保留原有端口和目录挂载：

```text
8081:8081
7071:7071
/opt/metersphere/conf:/opt/metersphere/conf
/opt/metersphere/logs:/opt/metersphere/logs
```

不要配置 `MS_WECOM_BRIDGE_URL` 或 Bridge Token。

## 首次准备配置文件

在仓库根目录执行：

```bash
install -d -m 755 /opt/metersphere/conf /opt/metersphere/logs
cp deploy/conf/metersphere.properties.example /opt/metersphere/conf/metersphere.properties
cp deploy/conf/redisson.yml.example /opt/metersphere/conf/redisson.yml
```

编辑两个文件，填写测试环境真实的 MySQL、Kafka、MinIO、Redis 地址和密码：

```bash
vim /opt/metersphere/conf/metersphere.properties
vim /opt/metersphere/conf/redisson.yml
```

如果文件中使用 `${MYSQL_HOST}` 等占位符，应在发布平台配置对应环境变量；也可以直接将真实值写入 `metersphere.properties`。

## 命令行部署

准备环境变量文件：

```bash
cp deploy/env.prod.example /opt/metersphere/env.prod
vim /opt/metersphere/env.prod
```

启动单个容器：

```bash
chmod +x deploy/docker-run.sh
./deploy/docker-run.sh /opt/metersphere/env.prod
```

该脚本只启动一个 `metersphere` 容器。企微连接器由镜像启动脚本自动管理，容器重启后会自动恢复，无需额外操作。

## 验证

```bash
docker ps --filter name=metersphere
docker logs --tail 300 metersphere
curl -I http://127.0.0.1:8081/
```

正常日志应同时包含：

```text
wecom bot bridge listening
Started Application
```

这里的 `bridge listening` 是主容器内部组件日志，不代表需要部署第二个服务。

还应验证 Bridge 和 Java 进程同时存在，且内部存活接口可用：

```bash
docker top metersphere -eo pid,user,comm,args
docker exec metersphere node -e "fetch('http://127.0.0.1:8095/health/live').then(async r=>{console.log(await r.text());process.exit(r.ok?0:1)}).catch(e=>{console.error(e.message);process.exit(1)})"
```

存活接口预期返回 `{"status":"UP"}`。新版启动脚本会在 Bridge 缺少运行库、启动超时或异常退出时让主容器失败，避免容器显示运行但企微能力不可用。

## 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 数据库连接失败 | `metersphere.properties` 中 MySQL 配置错误 | 检查文件及 `MYSQL_*` 环境变量 |
| Redis 连接失败 | `redisson.yml` 地址或密码错误 | 检查挂载文件 |
| 企微机器人离线 | Bot ID/Secret 不正确或服务器无法访问企微 WebSocket | 在系统页面执行“测试连接”并查看同一个容器日志 |
| 找不到配置文件 | 未挂载 `/opt/metersphere/conf` | 按本文准备并挂载两个配置文件 |

## 升级说明

升级只需发布新的后端镜像并重建 `metersphere` 容器。`/opt/metersphere/conf` 必须持久化，其中 `.wecom-master-key` 由镜像首次启动时自动生成，用于加密平台页面保存的企微 Secret，请勿删除。
