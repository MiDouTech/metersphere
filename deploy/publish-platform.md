# MeterSphere 发布平台配置说明（文件模式，无 Nacos）

> 适用：独立发布平台拉取 CNB 镜像并部署后端容器。  
> 配置来源：宿主机 `/opt/metersphere/conf/metersphere.properties` + `redisson.yml`。  
> **安全**：真实地址与密码只在服务器 / 平台密钥区，勿写入本仓库。

## 1. 必改项

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `SPRING_PROFILES_ACTIVE` | `local` | **必须**。禁用 Nacos，走文件配置 |

> 若仍为 `nacos` 或未设置，可能报 `No spring.config.import` 或去连 Nacos。

## 2. 不再需要的变量

以下与 Nacos 相关的变量**可删除或留空**（文件模式不用）：

- `NACOS_SERVER_ADDR`
- `NACOS_NAMESPACE`
- `NACOS_GROUP`
- `NACOS_USERNAME`
- `NACOS_PASSWORD`

## 3. 卷挂载（必须）

| 宿主机路径 | 容器路径 | 说明 |
|------------|----------|------|
| `/opt/metersphere/conf` | `/opt/metersphere/conf` | 必须含 `metersphere.properties`、`redisson.yml` |
| `/opt/metersphere/logs`（或 `/data/metersphere/logs`） | `/opt/metersphere/logs` | 应用日志 |

首次部署前在宿主机用 example 生成并填实值：

```bash
cp deploy/conf/metersphere.properties.example /opt/metersphere/conf/metersphere.properties
cp deploy/conf/redisson.yml.example /opt/metersphere/conf/redisson.yml
# vim 填写后不要把填好的文件提交到 Git
```

## 4. 端口

| 容器端口 | 说明 |
|----------|------|
| `8081` | HTTP API |
| `7071` | TCP（JMeter 等） |

按需绑定内网 IP（例如仅 `10.0.1.1:8081`）。

## 5. 镜像

```
docker.cnb.cool/miduoyanfa/middleground/metersphere/metersphere-backend:latest
```

或指定版本：`metersphere-backend:${VERSION}`

## 6. 发布平台配置示例

```env
SPRING_PROFILES_ACTIVE=local
```

### 发布操作顺序

1. CI 构建并推送镜像到 CNB  
2. 确认宿主机 conf 已就绪（properties + redisson）  
3. 平台环境变量设为 `SPRING_PROFILES_ACTIVE=local`  
4. 确认卷挂载 conf / logs  
5. 重建容器  
6. `docker logs -f <容器名>`

## 7. 验收标准

- [ ] 容器 `Up`，非 `Restarting`
- [ ] 日志无 `No spring.config.import`
- [ ] 日志无 Nacos 连接失败重试
- [ ] `curl -I http://<host>:8081/` 正常
- [ ] 经 Nginx 访问业务正常

## 8. 回滚

回滚上一镜像版本即可；**保持** `SPRING_PROFILES_ACTIVE=local` 与 conf 卷挂载不变。

## 9. 手工脚本（无发布平台时）

见 [`README.md`](./README.md) 与 `./deploy/docker-run-file.sh`。
