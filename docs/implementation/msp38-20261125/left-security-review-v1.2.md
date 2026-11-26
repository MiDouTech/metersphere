# MSP-38 权限与日志依赖审查

日期2026-11-25，审查者质量-左殇0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0。绑定v1.2 patch ac692a9c172fbd37591a6059ccf96dd8416c5fd5a20f57f832afb5630fc31e50。补充包SHA256实测3cb80ac51f167511fb8198d9dd7c0139bf673a8ab2e1accda3eb580a84c82a2f，匹配。

## 权限最终解析结果

已读取MsPermissionAnnotationMethodInterceptor、LocalRealm及SessionUtils相关完整授权方法。注解拦截器从参数提取项目/组织，仅用于注解授权，finally清除线程变量；没有显式参数时SessionUtils从当前请求头再回退用户当前组织/项目。LocalRealm.isPermitted实际委托SessionUtils.hasPermission，不依赖返回null的doGetAuthorizationInfo。

hasPermission拒绝空身份，构建角色关系映射；管理员检查要求角色类型、sourceId及enabled；组织管理员带project时另核对项目属于组织。普通权限集合分别按项目、组织、系统source范围及启用状态过滤。请求头本身不授予权限，仍要匹配身份角色关系。结合前轮服务端组织目录、Hub归属及子树校验，在已检查链上未确认新增越权。此为静态结论，未执行低权限测试，也不证明正式.cn项目映射有效。

## P1 诊断链仍直接输出原始异常，不能认定统一脱敏

位置：

- AgentCaseImportService.java:112，预检catch(Exception)直接warn(message,failure)。
- FunctionalCaseImportEventListener.java:153、303，直接输出异常/异常message及堆栈。
- FunctionalCaseExportTaskService.java:177、201，快照和生成普通失败向LogUtils传原始Throwable。
- 基线LogUtils.java的error(Object,Throwable)直接logger.error(message,ex)，没有脱敏；不是统一安全包装。
- 基线RestControllerExceptionHandler.java:230–238，查询/加锁等未包装异常进入recordInternalError后将原始exception交给LOGGER.error。

触发：Excel解析、数据库或存储调用异常的message/cause包含私密单元格内容、连接信息或凭据。实际代码会把异常链原文写入服务端日志。对外响应虽为安全通用提示及requestId，不等于日志安全；新EXPORT_TOO_LARGE分支安全也不覆盖其余失败。

这是源码可确认的原始异常输出路径，未做真实秘密注入，不声称已发现实际泄露的线上日志。以上多数调用点已存在于基线，并非全部由v1.2引入；但本次明确要求核对的故障日志脱敏链仍不满足规则，不能判定此交付项通过。

建议最小处理本次资产目录/导入导出通路的安全失败分类，白名单记录阶段、任务/请求关联、时间、根因类型、经校验SQLState和错误码及安全栈位置，不输出原始异常消息/Throwable；确保更早的listener及预检日志也不先行泄露。不要未经确认全局改写全部业务异常策略，若需全局范围另行说明。补验合成secret sentinel在异常message/cause中时日志无原文、失败仍可关联定位及事务回滚不变。保留网络/数据库/解析阶段分类，不仅换成不可诊断的通用提示。

未宣称监控或告警已建立。本轮没有重跑Java或故障注入，没有修改生产代码、新建会话、派发测试或推送。

## 审查状态

本轮完成所请求的最终授权方法与上述异常输出链核对，不再因缺LogUtils/通用handler而悬置该判断。两项性能P2仍待新差异复审，不能由作者自测自动关闭；新增日志P1需修订验证。总体结论仍为需修改，不是全量批准。此报告只陈述实际读取的方法和通路，不声称补充包全部26文件每一行均审完。
