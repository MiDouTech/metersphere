# MCP 能力与可靠性实施追踪（2026-11-02）

依据：msp-mcp-capability-and-reliability-implementation-plan-20261030.md。当前状态：实施中，未完成全量交付。

|需求|入口/实现链路|数据配置|验证与状态|
|---|---|---|---|
|R01 参数契约|tools/list → tools/call → 有效Schema → 原业务校验|逐工具兼容策略|待实施；历史重放和批量部分成功不能破坏|
|R02 错误恢复|MCP → SafeErrorMapper → RPC error.data.details；请求MDC|沿用限流窗口|已实现第一批；13项定向测试通过，新增RPC入口测试待运行|
|R03 操作查询|operation.get → 原幂等记录 → 当前权限 → 安全资源引用|不新增缓存|待实施|
|R04 能力版本|initialize / capabilities → 授权工具与构建元数据|缺失版本UNKNOWN|待实施|
|R05 工作流|发现元数据 → 个人执行/任务执行既有协议|保持图片策略|待实施|
|R06 导出下载|bug.export.download → 临时附件鉴权 → 有界存储流|沿用TTL及5MiB|新增入口及定向测试已编写；第二轮编译被内存保护终止，待验证|
|R07 评审维护|search/cases/update/disassociate → 原评审服务和权限|版本冲突与归档规则|待实施|
|R08 缺陷关联查询|bug.case.list → 项目/缺陷/用例权限 → 关联分页|无DDL|待实施|
|R09 响应契约|Handler.outputSchema → structuredContent|兼容原结构|先修默认Schema重叠；具体DTO待实施|
|R10 权限发现|清单与调用共用Scope约束 → 实时资源鉴权|旧Token兼容|待实施|
|R11 注册策略|Registry → 正式名/别名 → 分发/幂等协议|保留历史Hash与键|先修重复覆盖；策略统一待实施|
|R12 轻量读取|显式SUMMARY/DETAIL → 字段白名单 → 原查询|默认不变|待实施|
|R13 完整验收|公开Handler/Schema、真实数据库、镜像健康、端到端|单环境串行|未执行；上一版定向67项不等同本轮通过|

## 第一批修改范围

只改原生MCP的错误传递、默认输出Schema和注册保护；不改数据库、前端路由及原业务副作用。共享错误映射的调用方会得到新增恢复元数据，保持code/message/traceId字段。

异常处理：限流返回稳定业务码；等待提示使用配置窗口的保守上界并明确为建议；未知异常不承诺自动重试。trace优先使用请求MDC，无请求时生成UUID。注册重名或别名冲突直接失败，避免静默覆盖。

## 验证记录

待补充实际命令与结果。当前本机空闲内存不足1GiB，不启动Docker或全量Maven。完整构建、真实数据库、容器健康及部署复验仍是未完成项，不能据此宣称方案完成。

## 2026-11-02 实际检查结果

- 第一批运行：`python .codex-tmp/check-mcp-capability-20261102.py`，修改生产类定向javac成功（8秒）、测试编译成功（9秒）、13项JUnit成功（4秒）。范围仅错误映射及注册/默认Schema，不代表后续下载类通过。
- 第二批同命令：main-compile被资源保护主动终止，空闲内存低于300MiB。没有运行新增下载测试，也不计为编译通过；没有反复重启。
- `git diff --check`通过（后续文档换行检查仍需保留）；未修改数据库或启动容器。
- 可复现定向入口：`python docs/testing/msp-mcp-capability-20261102/check-focused.py`。启动前要求900MiB空闲，运行时仍保留300MiB保护；使用已有依赖和编译产物，不能替代干净构建。保存的attempt-tests.log为第一批13项结果，attempt-main-compile.log为第二批未完成编译日志，两者不能组合成整批通过证据。

### 新下载协议

`metersphere.bug.export.download({projectId,fileId,offset:0,length:32768})`，只读BUG_READ工具，仍要求当前用户项目缺陷导出权限及文件属于当前Token/用户、purpose=BUG_EXPORT、未过期未关联。返回base64、offset/nextOffset/eof、sizeBytes、sha256、contentType、fileId。客户端按nextOffset续传，先校验文件长度和SHA-256再校验ZIP/Excel。不会扩大导出容量。

实现以8KiB缓冲扫描文件并计算完整摘要，只保留最多64KiB的响应块；因此每块会重新扫描存储流，内存有界但I/O随块数增长。5MiB既有限额下先保证正确性，后续可在已验证的存储范围读取和可信摘要元数据基础上优化；不能声称已完成性能验收。

### 尚未执行的必要检查与风险

完整后端编译/单元及真实数据库集成测试、前端共享路径回归、Docker构建/启动/健康、真实MCP下载和旧客户端验收均未执行。新类还存在未编译确认风险。后续先通过定向测试，再使用方案第14节的Maven命令和仓库现有CI/运行工作流；不可直接发布这一未经完整验证批次。

本次没有修改前端业务；工作区其他前端文件与周报属于并行工作，不合并进此MCP范围。尚未提交或推送本轮修改。整体状态：部分完成，R01/R03/R04/R05/R07/R08/R10/R12及R09/R11剩余部分待实施，R13待完整验收。
