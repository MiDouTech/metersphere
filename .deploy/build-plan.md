# MeterSphere CNB 制品存储构建计划

## 目标

CNB 在本方案中只作为制品存储位置，不负责发布到服务器、替换 Nginx 静态资源或重启后端容器。

构建职责划分：

- GitHub Actions：拉取源码、构建前端 zip、构建后端 Docker 镜像。
- CNB 静态仓库：存储前端 zip。
- CNB Docker Registry：存储后端 Docker 镜像。
- 部署发布：不在本构建计划内处理，后续由独立发布系统或人工流程消费 CNB 中的产物。

## 项目识别

MeterSphere 当前仓库同时包含 Java 后端和 Vue 前端：

- 根目录 `pom.xml` 聚合 `frontend`、`backend` 两个模块。
- 后端为 Spring Boot / Java 21，启动模块为 `backend/app`。
- 前端位于 `frontend`，使用 Vite + Vue，`package.json` 中构建命令为 `pnpm run build`。

按 CNB 构建模板规则，本项目归类为：

```text
product_type=1
Java 前端 + 后端
```

但本次不接入 CNB 发布模板，因此不需要在源码仓库或 CNB 仓库维护 `.cnb.yml`、`.cnb_setting.yml`、`.cnb/web_trigger.yml` 这类发布运行时文件。

## 制品位置

CNB 仓库：

```text
https://cnb.cool/miduoyanfa/middleground/metersphere
```

CNB Docker Registry：

```text
docker.cnb.cool/miduoyanfa/middleground/metersphere
```

后端镜像：

```text
docker.cnb.cool/miduoyanfa/middleground/metersphere/metersphere-backend:${VERSION}
docker.cnb.cool/miduoyanfa/middleground/metersphere/metersphere-backend:latest
```

前端静态仓库：

```text
https://cnb.cool/miduoyanfa/MD_static/metersphere
```

前端 zip 仓库路径：

```text
artifacts/${VERSION}/metersphere-frontend.zip
```

## 当前工作流

当前使用：

```text
.github/workflows/cnb-build.yml
```

可变配置集中在：

```text
.deploy/config.env
.deploy/services.json
.deploy/frontend.json
```

触发方式：

- 手动触发 `workflow_dispatch`，可传入 `version`。
- 推送到 `v3.x` 分支自动触发。

版本号规则：

- 手动传入 `version` 时使用传入值。
- 未传入时使用 `${yyyyMMdd}-${shortSha}`。

## 已确认构建参数

通用：

```text
JAVA_VERSION=21
NODE_VERSION=20.8.1
PNPM_VERSION=8.4.0
CNB_REGISTRY=docker.cnb.cool
CNB_NAMESPACE=miduoyanfa/middleground/metersphere
STATIC_REPO_URL=https://cnb.cool/miduoyanfa/MD_static/metersphere
STATIC_REPO_PATH=artifacts
```

后端：

```text
image=metersphere-backend
dockerfile=Dockerfile.backend
context=.
build_output=backend/app/target/dependency
```

后端构建命令：

```bash
./mvnw -B clean package -DskipTests -DskipAntRunForJenkins=true -pl '!frontend'
rm -rf backend/app/target/dependency
mkdir -p backend/app/target/dependency
APP_JAR="$(ls backend/app/target/app-*.jar | head -n 1)"
cd backend/app/target/dependency
jar -xf "../$(basename "${APP_JAR}")"
```

`-DskipAntRunForJenkins=true` 用于跳过 `backend/app` 中把 `frontend/dist` 拷贝进后端静态目录的逻辑。前端静态资源独立构建成 zip 存储，不随本次后端镜像发布。

前端：

```text
artifact=metersphere-frontend
context=frontend
node_version=20.8.1
pnpm_version=8.4.0
install=corepack enable && corepack prepare pnpm@8.4.0 --activate && pnpm install --no-frozen-lockfile
build=pnpm run build
package=zip frontend/dist
```

## GitHub Secrets

GitHub 仓库需要：

```text
CNB_TOKEN
```

如前端构建过程需要企业微信 OAuth 配置，还需要：

```text
VITE_WECOM_OAUTH_URL
```

不要在 workflow 或配置文件中硬编码令牌、账号或腾讯云密钥。

## 静态仓库创建

前端静态仓库需要通过本地 CNB API 创建：

```bash
curl -X POST "http://127.0.0.1:8000/api/cnb/static-repositories" \
  -H "accept: application/json" \
  -H "Content-Type: application/json" \
  -d '{
    "dbname": "metersphere",
    "description": "MeterSphere static artifacts",
    "visibility": "private"
  }'
```

当前目标仓库地址为：

```text
https://cnb.cool/miduoyanfa/MD_static/metersphere
```

## 发布平台运行时参数（需在独立发布平台配置）

CNB / GitHub Actions 只构建镜像，不注入容器运行参数。  
后端容器需在**独立发布平台**配置以下环境变量，否则启动会报 `No spring.config.import property has been defined`：

```env
SPRING_PROFILES_ACTIVE=nacos
NACOS_SERVER_ADDR=<线上 Nacos 地址>
NACOS_NAMESPACE=prod
NACOS_GROUP=METERSPHERE
NACOS_USERNAME=<若开启认证>
NACOS_PASSWORD=<若开启认证>
```

完整说明见 `deploy/publish-platform.md`。  
**不要**用仓库内 `deploy/nacos/prod/metersphere.properties` 模板覆盖线上 Nacos 已有配置。

## 不再需要的发布配置

因为 CNB 不负责发布，本计划不需要确认或维护以下配置：

- CNB 发布按钮、触发事件和版本文件。
- `TEST_HOSTS`、`DEV_HOSTS1`、`DEV_HOSTS2` 等服务器实例 ID。
- `TEST_PORTS_MAP`、`DEV_PORTS_MAP`、`TEST_ENV`、`DEV_ENV`、卷映射等容器运行参数。
- `STATIC_CVM`、`TEST_PROXY`、`DEV_PROXY`、`SUB_HOST` 等静态资源或 Nginx 发布参数。
- `SecretId`、`SecretKey`、`DEV_SecretId`、`DEV_SecretKey` 等腾讯云发布变量。
- 主机发布镜像、SSH 通道变量或服务器发布密钥等相关配置。

## 原工作流处理

原仓库 `.github/workflows/*.yml` 已移动到：

```text
.github/workflows.disabled/
```

这些文件保留作参考，但不会被 GitHub Actions 触发。保留原因：

- 方便后续参考原 PR 校验、Sonar、Codecov、Issue 自动化和 Gitee 同步逻辑。
- 避免原开源社区协作类 workflow 继续触发并依赖不存在的 secrets。

## 验收标准

- 源码仓库只保留 GitHub Actions 构建制品工作流，不提交 CNB 发布运行时文件。
- workflow 只构建并上传产物，不出现服务器发布、Nginx 替换、容器重启逻辑。
- 前端 zip 同时保留为 GitHub workflow artifact，并推送到 CNB 静态仓库。
- 后端镜像推送到 CNB Registry，并打 `${VERSION}` 与 `latest` 两个标签。
- 全仓库不残留旧制品库绑定、示例镜像名或迁移模板占位值。
