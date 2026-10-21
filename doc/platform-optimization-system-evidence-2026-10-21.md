# 平台优化系统求证记录

> 更新：本轮已进一步实测 `.cn` 底部 Excel 导出卡住，并核对目标环境的实际发布文件，详见 [导出专项排查](case-export-root-cause-investigation-2026-10-21.md)。下文早期“未能完成导出按钮复现”的记录为当时状态；目前已推进到提交后 loading，但仍未拿到本次 WS 握手包及文件下载证据。

日期：2026-10-21（Asia/Shanghai）  
用户指定目标：**https://msp.ebcone.cn**  
关联：[详细实施方案](platform-optimization-implementation-plan-2026-10-21.md)、[源码范围候选清单](platform-optimization-scope-inventory-2026-10-21.md)。

## 1. 执行边界

本轮进行了目标站点只读探测、现有登录页面观察、本机验收容器检查和源码扫描；没有修改生产数据、退出用户、撤销凭证或部署业务代码。WebSocket 探测使用临时 diagnostic 标识，不涉及业务 fileId。本轮初期曾对两套域名做对照探测，用户明确后以 `.cn` 为准，`.net` 不作为本报告结论依据。

工作区已有其他业务变更，未触碰。本轮新增或更新的内容均为文档。

## 2. 目标系统实测

| 检查 | 实际结果 | 能证明什么 |
|---|---|---|
| `GET https://msp.ebcone.cn/` | 200，text/html | 页面入口可达 |
| 无凭证 `GET /api/is-login` | 401，application/json，code=100401 | 未认证请求被拒绝；不证明已有会话无效 |
| 无凭证 `/ws/export/diagnostic-<timestamp>` Upgrade | 401，application/json;charset=utf-8，未升级 | 目标路径在该请求下拒绝握手；不证明已登录请求同样失败 |
| 新 Edge 标签页进入原平台 origin | 出现平台导航、当前组织及项目，并显示“AI 自动化执行”等控件，没有进入登录页 | 当前配置在新标签页复用了登录态；AI 入口当前仍可见 |

浏览器初始快照同时出现模块总数 132 与列表“暂无数据”，但未能继续取得稳定的加载后状态，不能据此认定列表加载失败或数据丢失。

### 浏览器取证限制

尝试绑定原标签页时遇到 `Debugger unattached` 或连接超时；新建标签页曾成功返回一次页面状态，后续读取又超时。未完成勾选、导出、WebSocket 控制台、任务记录和文件下载观察。不能把“尝试操作”描述成已复现导出。

## 3. 本机运行环境证据

实际执行：

```powershell
docker ps --format '{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
Invoke-RestMethod http://127.0.0.1:17071/actuator/health/readiness
docker inspect msp-quality-verify-app-1 --format '{{.Image}} {{.Created}}'
docker exec msp-quality-verify-app-1 sh -c 'printenv | grep -E "^(SPRING_SESSION_TIMEOUT|SERVER_SERVLET_SESSION_TIMEOUT|SPRING_SESSION_REDIS_NAMESPACE|MIDUO_SSO_ENABLED|SPRING_PROFILES_ACTIVE)="'
docker exec msp-quality-verify-app-1 sh -c 'grep -n "spring.session.timeout\|spring.session.redis" /app/commons.properties'
```

实际输出摘要：

- app 容器：`msp-quality-verify-app-1`，镜像标签 `msp-quality:verify`，当时状态 healthy，端口 18081/17071。
- 本机验收 app 实例数为 1；配套 MySQL、Redis、Kafka、MinIO 容器 healthy。
- readiness 返回 `UP`。
- app 镜像 ID：`sha256:cac8c56b9e362cec1462eae1411dcee978f17526c8b9f541850763a49f2a0669`，容器创建于 `2026-10-20T07:35:34.362570095Z`。
- 镜像内 `/app/commons.properties:81` 为 `spring.session.timeout=43200s`，第 83 行配置 indexed Redis repository。
- 上述限定的环境变量查询没有返回项；这不排除外部配置文件、启动参数等更高优先级配置，不能视为已读取最终生效值。
- 对本机 `http://127.0.0.1:18081/ws/export/diagnostic-<timestamp>` 同类无凭证握手返回 `101 Switching Protocols`，区别于目标系统 401。
- 只读查询本机数据库得到 export_task 表存在，当前该表无任务状态聚合记录；无法用本机历史任务还原线上故障。

数据库查询通过容器现有环境读取连接信息，未打印密码：

```powershell
'SELECT state,COUNT(*) AS count FROM export_task GROUP BY state; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=''export_task'';' | docker exec -i msp-quality-verify-mysql-1 sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -N metersphere_quality'
```

## 4. 源码证据及故障路径

### 4.1 期限不一致：已确认默认配置问题

- `backend/app/src/main/resources/commons.properties:81`：Session 默认 43200 秒，即 12 小时。
- `frontend/src/utils/auth.ts:60`：前端按 30 天比较本地 loginExpires。
- `SessionConfig.sessionIdResolver`：通过 `X-AUTH-TOKEN` Header 解析会话。
- `auth.ts`：sessionId/csrfToken 存于当前 origin 的 localStorage。

结论：前端本地时间判断不能保证后端会话仍有效，自动化需要服务端校验及复用恢复机制。线上最终有效期尚未读取。

### 4.2 SSO 多端耦合：已确认实现结构

- `MiduoSsoSessionStore.java:37`：以 `miduo:sso:session:`＋userId 保存上游 sessionToken，新保存覆盖同键。
- `MiduoSsoApplicationService.java:136-144`：退出读取该用户上游凭证、调用撤销并删除 userId 对应键。
- `MiduoSsoRefreshService`：按 userId 读取/刷新同一份上游凭证。

结论：需按平台实例、设备/授权及具体会话设计映射，并区分普通退出、设备退出和全端退出。目前未证明所有本地 Session 在一端退出时立即被删，也未完成线上并行退出复现。

### 4.3 导出连接失败无出口：已确认实现路径

- `frontend/src/hooks/useWebsocket.ts:13-23`：Promise 只监听 open 后 resolve，未处理 error/close，也无超时 reject。
- `caseTable.vue` 的 exportConfirm：先 await startWebsocketGetExportResult，再请求 exportExcelCase/exportXMindCase。
- `commonScript.ts`：使用 `new WebSocket(uri)`；未显示附加登录票据的逻辑。
- 目标站点无凭证 Upgrade 返回 401，而 HTTP 业务请求使用 Header Session。应继续核对登录 Cookie、网关认证与 WS 专用认证方式，不能简单把 WS 改匿名作为修复。

条件推导：如果已登录浏览器的 WS 也未获认证或连接失败，Promise 将一直等待，POST 导出任务不会发出。这是有源码证据的故障路径，现场是否命中仍待验证。

### 4.4 空导出任务悬挂：已确认实现路径

- FunctionalCaseFileService.export 先创建 PREPARED 任务。
- exportFunctionalCaseZip 获取 ids 后，空集合直接 return null，未更新终态/发送完成消息。
- exportCheck 检测已有 PREPARED 后拒绝再次导出。

条件推导：空筛选或记录变化导致空结果时，可能形成“任务一直存在、后续导出受阻”。本机任务表当前无记录，尚无现场任务行证明此次故障由它导致。

### 4.5 其他需纳入修复的代码风险

- ExportTaskManager.exportAsyncTask 每任务创建单线程 ExecutorService，当前方法无 shutdown；应用共享有界线程池替代。
- 导出任务已持久化，但 Future Map 与 WS Session Map 均在单进程；多副本必须补状态查询和跨实例通知。
- 下载以 headers.get 读取 content-disposition；缺响应头、filename*、JSON Blob 错误等未完整处理。当前 Axios 版本可能支持 headers.get，不能仅凭这个写法判为根因。
- 导出页面成功消息发生在任务通知阶段，真实文件下载失败仅 console.log；用户可能只看到失败或无响应。

## 5. 源码范围扫描

扫描 `frontend/src` 下 vue/ts/tsx，分别按表格使用、批量操作和模型功能的明确代码信号生成候选列表，输出首次命中行。结果：

| 候选分类 | 文件数 |
|---|---:|
| 列表与刷新 | 113 |
| 批量操作 | 28 |
| 模型功能 | 16 |

详见范围候选文档。分类有交叉，正则存在漏命中/误命中；不能把候选文件数当页面总数，也不能据此声称“所有控件已经核对”。实际观察到目标系统仍有“生成用例”“AI 自动化执行”等入口。

## 6. 可复现的只读网络探测

在仓库根目录 PowerShell 中运行以下 Node 脚本。仅访问目标系统首页、身份检查和随机诊断 WS 路径，不携带 Token，不读取真实导出文件：

```powershell
@'
import https from 'node:https';
const base='https://msp.ebcone.cn';
for (const path of ['/', '/api/is-login']) {
  const r=await fetch(base+path,{signal:AbortSignal.timeout(10000),redirect:'manual'});
  const body=await r.text();
  let code; try { code=JSON.parse(body).code; } catch {}
  console.log({path,status:r.status,type:r.headers.get('content-type'),code});
}
await new Promise(resolve=>{
  const req=https.request(base+'/ws/export/diagnostic-'+Date.now(),{
    headers:{Connection:'Upgrade',Upgrade:'websocket',
      'Sec-WebSocket-Version':'13','Sec-WebSocket-Key':'dGhlIHNhbXBsZSBub25jZQ=='}
  },res=>{console.log({status:res.statusCode,upgrade:false});res.resume();resolve();});
  req.on('upgrade',(res,socket)=>{console.log({status:res.statusCode,upgrade:true});socket.destroy();resolve();});
  req.setTimeout(10000,()=>req.destroy(new Error('timeout')));
  req.on('error',e=>{console.log({error:e.code||e.message});resolve();});
  req.end();
});
'@ | node --input-type=module
```

上述为本轮实际执行的目标系统探测逻辑的可复现版本；原执行还包含本机和早期另一域名对照。不要用附带长期 Session 的 URL 测试 WS，后续应实现受认证的短票据或任务查询方案。

## 7. 尚未执行的核验、原因和下一步

| 未执行项 | 原因 | 剩余风险 | 准确下一步 |
|---|---|---|---|
| 已登录导出 E2E | 浏览器连接反复中断 | 尚不能定位现场首个失败节点 | 恢复连接后：选一条有权限用例→导出 XLSX→确认 WS 状态、是否发 POST、taskId→下载并打开文件；XMind 同样执行 |
| 多端/多账号退出矩阵 | 无稳定独立测试会话，避免影响用户现有登录 | 无法确认实际互踢范围 | 目标系统用独立测试账号 A/B，两个浏览器配置依次登录、各调用 is-login、退出其中一个，再检查另一个身份/项目；不得对用户现有会话做退出测试 |
| 线上最终 Session TTL/SSO 配置 | 当前工具未获得目标系统运维会话 | 默认配置与线上可能不同 | 运维在目标系统只读检查启动参数、外部配置、Session repository 设置及脱敏 TTL；禁止导出 Redis 会话原文 |
| 线上副本及网关规则 | 无目标主机/集群运行访问 | WS 跨实例丢消息是否发生未知 | 在目标部署执行 `docker ps --format '{{.Names}}\t{{.Image}}\t{{.Status}}'`；如为 Kubernetes 则 `kubectl get deploy,pods,svc,ingress -n <实际命名空间>`，实际名称不可凭空填造 |
| 全页面运行盘点 | 本轮仅有源码扫描及一次页面观察 | 候选清单仍可能遗漏 | 按候选文档逐路由登记权限、批量、AI 与刷新结果 |
| 新功能测试/构建/迁移 | 本轮是求证与方案修订，未实施功能 | 新设计尚无运行证据 | 实施后执行主方案第 15 节命令，R03A 另补代理/扩展/设备授权集成测试 |

当前需求追踪：2.2 项已从纯未知推进为线上探测＋本机事实＋代码路径分析；导出和互踢现场归因仍为部分完成。免登录已补充可实施设计，尚未开发。
