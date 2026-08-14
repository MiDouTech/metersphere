# task014 - P1 部署、监控与运维手册

> 状态：交付物完成，待目标环境部署演练  
> 前置依赖：task002、task005  
> 阻塞任务：task015  
> 关联方案：§3.3、§11、§12、§15

## 1. 任务目标

把 Bridge 和 Java 通知链路纳入现有部署、健康检查、监控、告警、Secret 轮换、故障处理和回滚体系。

## 2. 部署

- [ ] Bridge 多阶段 Dockerfile、非 root 用户和只读文件系统；
- [ ] Compose/Kubernetes 配置样例；
- [ ] Secret/ConfigMap 分离；
- [ ] 出站仅需 `openws.work.weixin.qq.com:443`；
- [ ] Bridge 内网端口不暴露公网；
- [ ] live/readiness probe；
- [ ] CPU、内存和日志限制；
- [ ] 首期 replicas=1；HA 时 Redis 租约选主；
- [ ] 优雅停机和滚动升级参数。

## 3. 指标与告警

- [ ] Bot 在线状态、重连次数、认证失败；
- [ ] 发送总量、成功率、延迟、错误分类；
- [ ] Outbox pending 数和最老消息年龄；
- [ ] Timer 到期积压；
- [ ] 群发现数量；
- [ ] 离线 5 分钟、认证失败、积压 10 分钟等告警；
- [ ] 日志 requestId/outboxId 关联检索。

## 4. 运维手册

覆盖：

- 初次配置和网络验证；
- Bot 可见范围调整；
- 群 chatid 发现；
- Secret 轮换；
- AUTH_FAILED、OFFLINE、限流、目标无效排障；
- Bridge/Java/Redis/MySQL 重启恢复；
- Outbox 人工重试；
- 功能紧急停用；
- 灰度、回滚和数据保留清理。

## 5. 验收标准

- [ ] 标准部署脚本可拉起 Bridge 并通过 readiness；
- [ ] Secret 不出现在镜像层、Compose 明文示例和日志；
- [ ] 断网、进程崩溃、Secret 错误均触发正确告警；
- [ ] 按手册可完成 Secret 无泄露轮换；
- [ ] 停用开关能够阻止新增发送。
