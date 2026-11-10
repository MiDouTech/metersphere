# MSP-15 发送准备失败诊断修复 v1.1

日期：2026-11-10。作者/实现者：测试开发-埼玉（c92997d2-06f5-4822-9791-4845858c6b29）。状态：开发说明草稿，独立审查及部署验证待进行。

## 缺陷基线与范围

用户在共享会话 4f6dac68-1cdd-45fc-a51a-e5c87f81ce8e 授权修复故障诊断缺口，并要求将故障可诊断性写入开发指令。原事件有 payload hash、无发送记录、尝试 0/4，仅留下 CONFIG_OR_SOURCE_UNAVAILABLE。已核对实际部署字节码：保存和发送使用相同密钥组件及平台配置作用域；原始异常未被记录。尚未确认原始投递故障根因，不能将密钥或作用域问题当作已证实事实。

本轮按缺陷修复执行，不新增产品方案，不扩大投递、授权、补推、预算或密文迁移规则。原事件的请求正文、私密接收 URL、密文与密钥不进入本说明。

## 实现

- 发送准备失败记录 event、businessKey、阶段、白名单错误分类、异常类型、SQLState、数据库错误码、接收配置版本、凭据版本和时间。事件及业务键须通过格式检查；不输出原始异常消息或 Throwable。
- 阶段区分 CONFIGURATION、SOURCE_CAPTURE、TIME_EVIDENCE、LEGACY_CHECK、PAYLOAD_BUILD、PAYLOAD_SAVE、URL_DECRYPT、TOKEN_DECRYPT、ATTEMPT_INSERT、ATTEMPT_RESERVE。
- 加解密内部保留 KEY_CONFIGURATION、CIPHERTEXT_FORMAT、AUTHENTICATION_FAILED、CRYPTO_UNAVAILABLE、SECRET_STATE 安全分类；不保留可能包含密钥或密文的原始异常。AUTHENTICATION_FAILED 只能说明认证标签校验失败，不能单独区分错密钥、错作用域和密文损坏。
- 准备失败接入 worker 总失败计数、准备失败计数和最近失败快照。Micrometer 存在时绑定应用既有注册表；不新增网络监听或开放管理接口。
- 页面原有失败状态、原因码、每计划四次预算及恢复边界保持兼容。失败不自动补推或恢复。诊断输出失败不应替代原始故障处理。
- v1.1修正R1-01：Handoff边界先检查Base64密文是否至少包含12字节IV和16字节认证标签。合法Base64但解码不足28字节归CIPHERTEXT_FORMAT，不修改共享Codec或其他调用方。28字节是合法的最小结构长度，并不代表认证成功；正常加密空字符串的28字节密文仍可解密。

## 日志和监控入口

部署后，在现有 Docker 日志中按安全事件 ID 查询：

```bash
docker logs --since 30m metersphere 2>&1 | grep -F 'Webhook preparation failed'
```

必要时再按具体 event ID 筛选。日志示意：

```text
Webhook preparation failed event=<event>; businessKey=<key>; stage=ATTEMPT_INSERT; category=DB_STATE; exceptionType=SQLException; sqlState=42000; vendorCode=1142; urlRevision=1; credentialRevision=1; time=<epochMillis>
```

复用现有 Micrometer 注册表的指标：

| 指标 | 含义 |
| --- | --- |
| metersphere.handoff.preparation.failures | 发送准备失败累计次数 |
| metersphere.handoff.stage.failures | 按固定 stage 标签累计失败 |
| metersphere.handoff.worker.failures | worker 失败累计次数，包含准备失败 |
| metersphere.handoff.maintenance.failures | 维护失败累计次数 |

指标不含事件、用户、URL 或异常文本标签，避免泄密及无界标签。计数为进程内累计，重启后重置；应用重启或事务回滚不擦除已产生的 Docker 日志，但日志保留取决于运维配置。没有配置外部告警、指标抓取或日志保留策略，不能宣称生产监控已完整生效。管理接口是否暴露/可访问沿用部署现状，不为诊断开放新权限。

## 验证与限制

使用脚本 `python scripts/test-hifox-handoff.py --regression` 编译相关真实源码，执行隔离 H2/JDBC 和模拟接收端测试。新测试验证 URL 密文格式错误、认证失败、Token 错误、发送登记 SQL 异常、正常发送，以及日志脱敏、可关联性、阶段指标和失败不消耗预算。

v1.1实际执行结果：236项启动，229通过、7项前提中止、0失败；相比v1.0新增14项参数化边界实例全部通过。相关源码编译与git diff --check通过。

脚本补齐平台范围类、异常处理器和真实依赖。HTTP SessionUtils 仅使用 fail-closed 测试替身，显式 actor 的 JDBC 路径使用真实平台授权类；不代表 HTTP 会话、整应用 Spring 启动或真实 MySQL 验收。未运行线上联调、真实投递、生产部署、完整 Maven 应用构建、真实监控抓取与告警验收。

明确覆盖限制：配置读取、锁获取和事务提交仍位于发送准备阶段try之外；异步兜底保留安全SQL分类，但这些路径尚缺具体event/businessKey及阶段关联。ATTEMPT_RESERVE失败、提交失败、日志后端抛异常和真实Spring注册表注入未做故障注入，不宣称全部准备异常已可关联。本轮只修正R1-01并披露限制，不改变这些路径的事务或重试语义。

复审依据：独立审查报告R1 https://app.hifox.com/miduo/docs/d96d9d1c-42ea-4686-8b8b-08166a10af93 ，SHA256 7b3bf3a64ece48255a8fcd53863a127ae542f19189be30c592e68e7daaf4809f。作者已读取文档库全文并核对哈希。v1.1新增URL/Token的0、1、12、27字节格式边界，非法Base64、有效长度认证失败、正常空/非空解密及作用域不匹配测试；原v1.0文件和审查结论保留。

上线后由部署责任人部署经独立审查的代码，再使用现有预览/确认流程受控恢复原事件。新日志用于定位原始故障；不得修改数据库强制重发、增加预算或凭推断更换密钥。
