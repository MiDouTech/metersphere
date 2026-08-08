# task006 - P0 - Playwright Browser Runner MVP

## 目标

实现可真实运行的 Playwright + Chromium Browser Runner，完成浏览器生命周期、隔离、人工登录和结构化事件上报。

## 实施范围

- 建立 Runner 独立进程/容器和健康检查。
- 首期支持 Chromium，启动参数受白名单控制。
- 每个任务或用例使用独立 Browser Context；登录态复用必须显式配置。
- 支持基础 URL、视口、语言、时区、下载目录和超时策略。
- 实现页面 DOM/可访问性树观察、页面截图和控制台错误摘要。
- 无授权会话时进入 `WAITING_LOGIN`，用户完成登录后恢复。
- 捕获浏览器崩溃、页面关闭、导航失败和 Runner 退出，持续上报事实事件。
- 实现任务取消和资源清理，防止浏览器、临时文件和 Context 泄漏。

## 安全要求

- 不读取用户默认浏览器数据目录。
- 不向模型或普通日志输出 Cookie、LocalStorage Token 和密码。
- 禁止任意 Chromium 参数、任意本地文件访问和未授权下载执行。

## 验收标准

- 托管 Runner 可在测试环境中领取并打开指定白名单 URL。
- 多任务 Context 之间 Cookie、LocalStorage 和下载文件隔离。
- 人工登录后可恢复，验证码/MFA 不被自动绕过。
- 取消任务后浏览器资源在限定时间内释放。
- 浏览器异常有明确错误分类和最后截图（可采集时）。

## 测试要求

- Context 隔离、登录恢复、浏览器崩溃、资源清理、超时和取消测试。
- Linux 容器与目标部署环境冒烟测试。
