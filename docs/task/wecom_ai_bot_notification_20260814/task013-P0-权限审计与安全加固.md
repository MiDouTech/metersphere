# task013 - P0 权限、审计与安全加固

> 状态：代码实现与权限资源校验完成，待部署安全验收  
> 前置依赖：task004、task005、task007  
> 阻塞任务：task015  
> 关联方案：§6、§8.5

## 1. 任务目标

确保 Bot 配置、通知规则、目标选择、内部回调和日志查看均遵循最小权限，防止 Secret 泄露、越权通知、回调伪造和模板注入。

## 2. 权限建议

```text
SYSTEM_CONFIG_WECOM_BOT:READ
SYSTEM_CONFIG_WECOM_BOT:UPDATE
SYSTEM_NOTIFICATION_RULE:READ
SYSTEM_NOTIFICATION_RULE:CREATE
SYSTEM_NOTIFICATION_RULE:UPDATE
SYSTEM_NOTIFICATION_RULE:DELETE
SYSTEM_NOTIFICATION_LOG:READ
SYSTEM_NOTIFICATION_LOG:RETRY
```

项目级规则还需校验当前用户对 scope project 的管理权限。

## 3. 任务清单

- [ ] 后端注解、Owner 校验、前端路由和按钮一致；
- [ ] Bot 配置、Secret 轮换、规则 CRUD、测试发送、人工重试操作日志；
- [ ] 审计 before/after 对 Secret 和目标脱敏；
- [ ] 内部 Bridge API 机器身份鉴权；
- [ ] timestamp/nonce 防重放；
- [ ] 模板变量白名单和输出长度限制；
- [ ] URL 白名单，仅允许系统资源链接；
- [ ] 防止通过对象 ID 选择其他项目成员/群；
- [ ] 防止错误堆栈记录 Token/Secret；
- [ ] 依赖漏洞扫描和镜像最小权限；
- [ ] 日志注入、Markdown 特殊字符和超长输入测试；
- [ ] 安全审查清单。

## 4. 验收标准

- [ ] 无权限账号的读写请求均被拒绝；
- [ ] 项目管理员不能配置其他项目对象；
- [ ] 内部回调伪造、重放和过期请求被拒绝；
- [ ] 全仓库和运行日志搜索不到测试 Secret；
- [ ] 任意脚本/SQL/SpEL 无法进入规则；
- [ ] 安全测试无 P0/P1 未关闭问题。
