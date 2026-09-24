# v3.2 隔离验收环境

仅用于本地测试，端口只绑定 127.0.0.1。使用独立 Compose project、命名卷和测试口令；不连接共享开发库或生产。策略发布目前仅保存配置，执行合同消费、材料试算和结果审批尚待后续改造。

从仓库根目录执行 PowerShell：

```powershell
$env:QUALITY_DB_PASSWORD = 'quality-isolated-db-only'
$env:QUALITY_STORAGE_PASSWORD = 'quality-isolated-storage-only'
pnpm.cmd -C frontend run build
.\mvnw.cmd -pl backend/app -am package '-Dtest=QualityPolicy*Tests,AgentExecutionExceptionHandlerTests,AgentExecutionChannelPolicyTests' '-Dsurefire.failIfNoSpecifiedTests=false'
# 打包后在新建的 backend/app/target/quality-dependency 目录解包本次 app-3.x.jar。
New-Item -ItemType Directory -Force backend/app/target/quality-dependency
Push-Location backend/app/target/quality-dependency
jar -xf ../app-3.x.jar
Pop-Location
docker build -f Dockerfile.backend --build-arg DEPENDENCY=backend/app/target/quality-dependency -t msp-quality:verify .
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml config --quiet
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml up -d --wait
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-quality-environment.ps1
```

重复构建请使用新的解包目录并调整 DEPENDENCY，不将旧 JAR 文件残留混入新镜像。上述 Maven 命令只跑指定集合，不代表全仓测试通过。前端若受 PowerShell 脚本策略限制，使用 pnpm.cmd，不修改机器执行策略。

真实 MySQL 策略集成测试（固定使用独立 quality_verify 库，测试会清空其中两张策略表，不可改为共享库）：

```powershell
docker run -d --name msp-quality-mysql-verify -p 127.0.0.1:13326:3306 -e MYSQL_ROOT_PASSWORD=quality-test-only -e MYSQL_DATABASE=quality_verify mysql:8.0.35 --character-set-server=utf8mb4 --collation-server=utf8mb4_bin
docker exec msp-quality-mysql-verify mysqladmin ping -uroot -pquality-test-only
.\mvnw.cmd -pl backend/services/agent-integration -am test '-Dtest=QualityPolicy*Tests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dquality.mysql=true'
```

应用侧使用系统角色的 SYSTEM_QUALITY:READ、SYSTEM_QUALITY:MANAGE、SYSTEM_QUALITY:PUBLISH；纯系统管理员直接具备权限，不依赖任何项目绑定。旧项目授权不会提升为系统授权，Agent Token 不支持策略管理。入口为 `/setting/system/quality-policy`；旧 `/execution/quality-policy` 自动跳转。V95～V97 新增全局版本、权限和尝试快照，升级不自动发布策略，需管理员确认统一规则后发布。

浏览器访问生产构建可另开终端执行 `node scripts/quality-browser-proxy.mjs`，然后打开 `http://127.0.0.1:15173/#/login/admin`。该脚本仅在回环地址提供 `frontend/dist` 并把真实请求转发至 18081，不包含 Mock。需要先在隔离库显式开启 `system_parameter` 的 `ui.login.admin.enabled`，并配置测试系统角色。不要在生产库执行测试授权。迁移自带测试账号及初始化业务数据不能作为实际生产数据验收。

升级验收还需：真实存量克隆库迁移及对账、多角色登录、JSON/表单互换、发布冲突、刷新后持久化、跨项目读取同一全局版本、对象存储上传下载及在线 MCP。基础健康脚本不能替代这些用户路径验收。

配置缺陷回归（仅限上述隔离环境，测试会创建策略并发布，停用项目测试会在 finally 中恢复原状态）：

```powershell
Get-Content deploy/quality-verify/global-rbac-fixture.sql | docker exec -i msp-quality-verify-mysql-1 sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot metersphere_quality'
# 另一个终端启动 node scripts/quality-browser-proxy.mjs
$env:QUALITY_E2E = 'true'
$env:PW_BASE_URL = 'http://127.0.0.1:15173'
pnpm.cmd -C frontend exec playwright test e2e/quality-policy.spec.ts
```

E2E setup 通过正式 `/system/user/add` 创建三个系统分权账号及一个纯系统管理员账号，邮箱分别为 `quality-reader-api@quality.invalid`、`quality-editor-api@quality.invalid`、`quality-publisher-api@quality.invalid`，初始口令沿用平台规则（与邮箱相同）。随后 fixture 仅配置隔离系统角色关系；不属于正式迁移，不自动修改真实角色，也不绕过账号创建校验。V3.7.2_93 为新增发布审计表，旧发布版本显示缺少事件，不补造前后摘要。真实 MySQL 集成测试现清空专用 quality_verify 库的相关策略表。

保留失败容器日志后可执行 `docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml stop` 暂停本次环境；不删除共享卷。


全局迁移差异报告：合并归档 API 的所有分页为 `{ "items": [...], "total": 总数, "projects": [完整项目ID清单] }`，执行 `python scripts/quality-policy-migration-report.py --input archive.json --output migration-report.md`。该脚本只读、不会选择或发布候选；输出规则参数差异和独立元数据，供管理员在真实页面决定统一草稿。用 `python scripts/test-quality-policy-migration-report.py` 验证比较逻辑。
