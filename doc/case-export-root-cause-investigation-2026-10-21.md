# 测试用例底部批量导出故障排查

日期：2026-10-21（Asia/Shanghai）  
目标环境：用户明确指定 `https://msp.ebcone.cn`  
范围：测试用例列表勾选后，底部“导出 Excel 格式 (xlsx)”和“导出思维导图 (xmind)”入口。  
本轮为排查，不含业务代码修复或部署。

## 1. 本轮结论与证据等级

**已在 `.cn` 已登录页面实际复现 Excel 导出卡住。已确认线上发布代码存在“连接未成功就一直等待、业务任务创建受其阻塞”的缺陷。认证方式不匹配是目前证据最强的连接失败原因，但尚未取得本次点击对应的 WS 握手包，不能声称已经抓到了这次请求的 401。**

应分成两个问题处理：

1. **为什么错误表现为永久转圈：已查清代码机制。** 导出首先等待 WebSocket open，Promise 没有 error/close/超时出口。等待未完成时不会继续创建导出任务，也不会执行 finally 清理 loading。该缺陷已在 `.cn` 实际部署的 JS 中确认，不只是本地代码推测。
2. **为什么该连接没有正常进入 open：认证链路高度可疑，已有直接拒绝证据。** 页面 HTTP 认证使用 X-AUTH-TOKEN/CSRF-TOKEN；浏览器原生 WebSocket 在当前代码中只接收 URL，没有加入可供 Header Session Resolver 识别的认证机制。目标导出路径的 Upgrade 探测返回 401；同一 Edge 登录配置直接访问该路径也显示 code=100401。仍缺本次导出操作的 WS 握手请求/响应或后端关联日志，以闭环确认现场最初拒绝原因。

不能将本轮结论扩大成“文件生成和下载均没有问题”。目前尚未越过连接阻塞验证这些环节。

## 2. 实际用户路径复现

### 2.1 测试对象

- 用户坚持故障发生在 `.cn`，本轮未切换到 `.net` 执行导出。
- 截图中的“米多星球-订单与质量门禁复测-20261019”未出现在当前 `.cn` 项目列表中，因此改用同环境可访问项目做相同入口的最小复现，不将项目名称误认成环境证据。
- 项目：品牌商后台基础（新增刷脸实名认证模块）。
- projectId：`1596934828530458624`，组织 `100001`。
- 列表总量：132 条，当前页显示实际用例。
- 本次只勾选 ID `100040`，避免大量导出干扰判断。

### 2.2 实际步骤与结果

| 步骤 | 实际结果 |
|---|---|
| 已登录 Edge 打开 `/case-management/featureCase` | 显示当前用户、项目和真实列表 |
| 勾选 ID 100040 | 底部显示“已选择 1 条”，出现“导出”按钮 |
| 点击底部“导出” | 出现 Excel/xmind 两个菜单项，与用户截图入口一致 |
| 点击“导出 Excel 格式 (xlsx)” | 正常打开字段选择弹窗，标题“已选 1 项数据” |
| 保持默认字段“用例名称”，点击弹窗“导出” | 按钮进入 loading，取消按钮 disabled，弹窗不关闭 |
| 后续多次观察及截图 | 仍为 loading；没有完成/失败提示，没有拿到导出文件 |
| 点击右上角 Close | 弹窗可关闭，列表仍显示已选 1 条；没有修改用例业务数据 |

现场截图已在本轮工具输出展示。未将浏览器认证信息、Session 或业务凭证写入日志或证据文件。

“一条用例＋一个默认字段”即可复现，说明不能简单把此次卡住归因于图片太多或导出条数过大。是否存在其他格式/内容问题仍需修复连接后继续验证。

## 3. 已部署代码证据

通过目标首页实际引用定位路由 chunk，再只读下载公共发布文件；没有执行下载脚本中的浏览器代码。

| 线上文件 | 核实内容 |
|---|---|
| `/assets/index-w90uwhGj.js` | 用例路由实际加载 `index-B6LSXdU6.js` |
| `/assets/index-B6LSXdU6.js` | 导出确认函数设置 loading 后，先 `await Pl()`；Pl 等待 createSocket；随后才调用 Excel/XMind 创建函数 |
| `/assets/useWebsocket-BJDHttkt.js` | `new Promise(resolve => ...)`，只有 open 回调 resolve；没有 reject、error、close、超时 |
| `/assets/commonScript-BDTv71gB.js` | 拼接同源 `/ws/export/<fileId>` 后 `new WebSocket(url)`，无认证票据逻辑 |

归档路径：`output/diagnostics/case-export-20261021/`，含公共发布文件和 `evidence.json`。后者记录 HTTP 状态、字节数、SHA-256、执行时间及握手探测结果，可用于识别后续是否已换版本。

本轮归档时间为 `2026-10-21T03:41:24.027Z`（北京时间 11:41:24）。

### 3.1 调用链

```text
用例列表选中记录
  → 底部导出菜单
  → 字段配置弹窗
  → exportLoading = true
  → await startWebsocketGetExportResult()
       → new WebSocket(wss://msp.ebcone.cn/ws/export/<fileId>)
       → 只在 open 事件 resolve
       → 连接失败：Promise 无 reject / 无超时
  → [等待完成之后才会执行] POST /functional/case/export/excel 或 /xmind
  → [等待完成之后才会执行] 创建成功提示、关闭弹窗、清理 loading
```

“本次没有发 POST”的判断目前来自调用顺序与 UI 状态推导，未取得浏览器 Network 完整清单或目标后端访问日志，不将其标注为抓包事实。

## 4. 认证拒绝证据与限制

### 4.1 实际网络探测

使用 Node https.request 发出标准 Upgrade 请求，包含：

```text
Origin: https://msp.ebcone.cn
Connection: Upgrade
Upgrade: websocket
Sec-WebSocket-Version: 13
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
```

实际结果：

```json
{
  "path": "/ws/export/<diagnostic>",
  "origin": "https://msp.ebcone.cn",
  "status": 401,
  "upgraded": false,
  "error": {
    "code": 100401,
    "message": "user authentication failure"
  }
}
```

此请求不携带用户 Session，是目标认证边界探测，不冒充已登录导出请求。

### 4.2 已登录浏览器对照

同一 Edge 配置可进入平台已登录业务页面；新建标签页直接访问 `/ws/export/diagnostic-auth-boundary`，页面显示：

```json
{"code":100401,"message":"用户认证失败","messageDetail":null,"data":null}
```

这证明“浏览器其他标签已登录”不能使直接访问导出路径自然通过认证；该观察是普通 GET，不能替代 Upgrade 抓包。

### 4.3 本地服务端源码对照

- `frontend/src/api/http/index.ts`：HTTP 拦截器注入 X-AUTH-TOKEN、CSRF-TOKEN、组织和项目。
- `backend/services/system-setting/.../config/SessionConfig.java`：HeaderHttpSessionIdResolver，按 X-AUTH-TOKEN 解析会话。
- `backend/services/system-setting/.../config/ShiroConfig.java`：通用过滤链 `apikey, csrf, authc`。
- `backend/framework/sdk/.../FilterChainUtils.java`：当前默认规则未看到专门的 export WS 登录适配。

HTTP 拦截器不会作用于 `new WebSocket(url)`。不能以“用户已经登录”为由假设 WS 能携带同样的认证请求头。目标后端是否还存在其他代理层认证配置，尚需运行配置/日志确认。

## 5. 前后端追踪与证据状态

| 环节 | 入口/代码/API | 状态 |
|---|---|---|
| 勾选和底部菜单 | caseTable.vue 批量操作 | 已实测 |
| 字段弹窗 | 导出字段配置及默认选择 | 已实测 |
| 导出确认/通知连接 | caseTable.vue、useWebsocket.ts、commonScript.ts | 卡住已实测，线上代码已核实 |
| 连接认证 | `/ws/export/{fileId}`、Header Session 与过滤器 | 401 对照探测已完成；缺本次 WS 抓包 |
| 任务创建 | `/functional/case/export/excel`、`/export/xmind` | 本次未取得执行证据 |
| 文件生成/存储 | FunctionalCaseFileService、FunctionalCaseXmindService | 尚未验证 |
| 下载 | `/functional/case/download/file/{projectId}/{fileId}` | 尚未验证 |

## 6. 伴随缺陷，不作为本次已命中根因

1. Excel 和 XMind 导出 ids 为空时均直接返回，PREPARED 任务未结束；XMind 查 ID 位于 try 外，查询异常也可能留下中间态。
2. 每任务创建线程池而没有在该方法关闭，存在资源生命周期问题。
3. Future/WS Session 为单进程映射，多副本可能丢通知。
4. 任务完成通知先到时，页面 `exportingMessage.value.close()` 可能访问未初始化提示对象；需加保护和状态机。
5. 下载失败仅 console.log，缺用户可理解的提示及 Blob 错误转换。

这些是代码风险，不是通过此次实测逐个确认的现场故障。

## 7. 修复建议及验收边界

### 必须修复

- 固定 HTTP 幂等创建任务→返回 taskId→认证状态查询→可选 WS 通知加速，禁止等待 WS open 后才创建任务。保留 WS 时加入受认证的短效票据：通过已认证 HTTP 申请，绑定 userId、projectId、fileId/taskId、短有效期及来源，握手验证；不能把整个 WS 路由匿名放行。
- 如果采用不同的会话认证方案，应证明其与当前 Header Session、CSRF 和代理配置兼容。不得在 URL 放长期 Session/Agent Token。
- createSocket 处理 error、close 和连接超时（建议 10 秒可配置），reject 时清除计时器、监听和连接；上层 finally 必须恢复按钮与取消能力。
- 导出任务状态以持久化记录为准，WS 作为通知加速；断线时使用认证的任务状态查询恢复，避免连接成为唯一入口。
- 明确“尚未提交任务”和“任务已受理但通知失败”，不能在后者重复创建任务或错误地宣称未受理。
- 复用已有 export_task 表，补空结果、异常、取消和重启后的终态恢复；提供任务/下载权限校验。

### 必须验收

1. Excel 与 XMind 分别实际下载并打开，核对所选条目、字段与中文。
2. WS 401/断网/超时后界面恢复、提示明确，不永久转圈。
3. 任务成功但通知丢失时仍可查询并下载，重复点击不生成重复任务。
4. 空结果任务能终结，后续合法导出不被历史 PREPARED 阻塞。
5. 越权连接、访问他人任务和文件、过期票据被拒绝。

## 8. 本轮执行与交付状态

- 需求追踪：截图入口→字段弹窗→确认卡住已实际复现；发布代码→认证/Promise 缺陷已有证据。
- 实际执行：浏览器选择/导出操作与页面快照、现场截图；Node fetch 下载已部署静态 JS、SHA-256 归档、https.request Upgrade 探测；rg/Get-Content 源码对照。
- 浏览器日志接口返回空数组；只读脚本环境不提供 performance，未能取得 Network 请求清单。未把这两个失败检查写成通过。
- 修改文件：新增本报告，更新方案和系统求证记录；公共发布文件归档在 output/diagnostics。业务代码未修改。
- 未执行：XMind 单独点击验证、本次 WS 抓包、生产日志/数据库任务核对、文件生成/下载验证；原因是未越过当前连接阻塞且浏览器调试能力受限。
- 未执行构建、单元/集成测试、迁移、镜像构建和部署，因为本轮未实施修复；后续命令与环境准备见主方案第 15 节。
- 风险：修复首个阻断点之后仍可能暴露生成/下载问题，不能当前就承诺导出全链路正常。
- **状态：排查部分完成；Excel 卡住已复现，永久等待缺陷已确认，连接认证问题证据充分但缺该次握手闭环；功能未修复。**


## 9. 2026-10-21 静态复核补充

本节来自当前工作区源码及已有归档的复核，没有新增生产点击或抓包。归档四个 JS 文件的 SHA-256 和字节数与 evidence.json 全部一致；仅证明归档自洽，不证明目标系统当前仍部署同版本。

### 9.1 新确认的代码缺口

| 位置 | 源码事实 | 实施要求 |
|---|---|---|
| FunctionalCaseController.caseStopExport | 只有 taskId 参数，CheckOwner 却引用 #projectId；对应切面对 null 不执行资源检查 | 根据任务读取真实项目/所有者，服务端逐项授权 |
| FunctionalCaseFileService / FunctionalCaseXmindService | 完成时取用户/项目最新 PREPARED；创建检查和插入分离 | taskId 贯穿，唯一幂等约束，条件更新防串任务 |
| ExportTaskManager.stop | Kafka 消费无条件写 STOP | 重复停止、取消/完成竞争不得覆盖终态 |
| FunctionalCaseXmindService.exportXmind | 空集直接返回；查 ID 位于 try 外 | 与 Excel 共用完整异常/终态边界 |
| ExportWebSocketHandler.onMessage/onClose | 按客户端 DTO 的 fileId 转发；关闭按 fileId 移除 | 禁止伪造及跨任务消息；按当前连接精确移除 |
| FunctionalCaseFileService.downloadFile | 返回完整 byte[] | 大文件容量测试并改受控流式下载 |

这些是静态代码缺陷或风险，不是本次已证明的线上命中原因，也不等于运行时越权复现。

### 9.2 最终修复及验收基线

以 [平台方案第 19 节](platform-optimization-implementation-plan-2026-10-21.md) 的任务身份、原子状态、恢复、票据、快照、权限和 E01～E14 为详细设计；与 [原整改计划第 10 节](../docs/summary/msp-remediation-complete-plan-20261020.md) 共用 U13/RA14，禁止单独建重复导出系统。清理失败独立重试，不覆盖成功结果。网页修复不得代替 MCP 全链路验收。

后续现场证据必须关联实际点击时间、页面/发布资源摘要、requestId/taskId、WS Upgrade 请求与响应、创建 POST、任务记录、生成和下载结果。截图须补本地可追溯路径及脱敏摘要；此前只在工具输出展示的截图不宣称已完成文件归档。匿名 Upgrade 401 和普通 GET 401 不替代该次已登录握手。

先判断任务是否已受理，再决定恢复或幂等重试；区分连接未建立、创建失败、生成失败和下载失败。修复后分别测试 Excel/XMind、空集、并发创建、取消竞争、双副本、通知丢失、过期及撤权，并保存可打开文件及内容核对结果。

本轮只修订三份文档并进行静态校验；未修复业务代码，未执行应用测试、构建、迁移、部署或新生产复现。执行命令和隔离环境前提见主方案第 15、19.8 节。状态仍为排查部分完成、功能未修复；永久等待缺陷已确认，实际连接拒绝原因及生成/下载仍待运行证据闭环。
