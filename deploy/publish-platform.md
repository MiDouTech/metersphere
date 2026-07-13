# MeterSphere 发布平台配置说明（方案 1）

> 适用：独立发布平台拉取 CNB 镜像并部署后端容器。  
> 目标：修复 `No spring.config.import property has been defined` 启动失败。

## 1. 必改项（本次修复核心）

在发布平台的**容器环境变量**中新增或确认：

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `SPRING_PROFILES_ACTIVE` | `nacos` | **必须新增**。激活 `application-nacos.properties`，加载 `spring.config.import` |

> 仅合并代码、拉新镜像而不加此项，问题仍会复现。

## 2. 保持现有配置（不要覆盖）

以下变量若发布平台已配置真实值，**保持不变**，本次修复不需要修改：

| 变量名 | 示例 | 说明 |
|--------|------|------|
| `NACOS_SERVER_ADDR` | `10.0.1.1:8848` | Nacos 地址 |
| `NACOS_NAMESPACE` | `prod` | Nacos namespace |
| `NACOS_GROUP` | `METERSPHERE` | 配置分组 |
| `NACOS_USERNAME` | `nacos` | Nacos 用户名（若开启认证） |
| `NACOS_PASSWORD` | `***` | Nacos 密码（若开启认证） |

## 3. 卷挂载（保持现有）

| 宿主机路径 | 容器路径 | 说明 |
|------------|----------|------|
| `/opt/metersphere/conf` | `/opt/metersphere/conf` | 含 `redisson.yml`，不走 Nacos |
| `/opt/metersphere/logs` | `/opt/metersphere/logs` | 应用日志 |

## 4. 端口（保持现有）

| 容器端口 | 说明 |
|----------|------|
| `8081` | HTTP API |
| `7071` | TCP（JMeter 等） |

## 5. 镜像

```
docker.cnb.cool/miduoyanfa/middleground/metersphere/metersphere-backend:latest
```

或指定版本标签：`metersphere-backend:${VERSION}`

## 6. Nacos 配置中心（保持现有，不要重推模板）

确认 Nacos 中已存在（**不要用仓库模板覆盖线上已有配置**）：

- namespace: `prod`（或你线上实际 namespace）
- group: `METERSPHERE`
- dataId: `metersphere.properties`

## 7. 发布平台配置示例

### 环境变量区块

```env
SPRING_PROFILES_ACTIVE=nacos
NACOS_SERVER_ADDR=10.0.1.1:8848
NACOS_NAMESPACE=prod
NACOS_GROUP=METERSPHERE
NACOS_USERNAME=nacos
NACOS_PASSWORD=<保持平台已有值>
```

### 发布操作顺序

1. GitHub 合并触发 CI，构建并推送新镜像到 CNB
2. 发布平台拉取新镜像
3. **确认环境变量含 `SPRING_PROFILES_ACTIVE=nacos`**
4. 重建/重启容器
5. 检查日志：`docker logs -f metersphere`

## 8. 验收标准

发布成功后应满足：

- [ ] 容器状态为 `Up`，非 `Restarting`
- [ ] 日志无 `No spring.config.import property has been defined`
- [ ] 日志可见 Nacos 配置加载成功
- [ ] `curl -I http://<host>:8081/` 返回正常 HTTP 状态

## 9. 回滚

若新版本异常，发布平台回滚到上一镜像版本即可。  
`SPRING_PROFILES_ACTIVE=nacos` 及 Nacos 相关环境变量**仍需保留**，与镜像版本无关。
