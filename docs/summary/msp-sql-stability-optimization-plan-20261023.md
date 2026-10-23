# MSP SQL 与稳定性完整优化方案

日期：2026-10-23。对象：MSP 生产平台、对应业务库及共享 MySQL 的访问链路。  
状态：方案已编制；优化实现、生产发布与性能验收尚未执行。  
依据：[结构审计](msp-architecture-db-risk-audit-20261023.md)、[事件整改清单](msp-incident-remediation-20261021.md)。

## 1. 目标与结论边界

本方案旨在降低同等业务量下的 SQL 数量、扫描量和连接占用时间，使慢查询或数据库短暂不可用时，请求有界失败并可恢复，避免整个业务平台持续排队。

已确认：列表资产关联与现有唯一索引不匹配；默认详情补全存在逐条多查询；本地 SQL 超时值为 25000 秒；第二次事件出现数据库读取超时、新连接初始化超时、连接池 idle=0 和请求排队。生产重启后统计也存在约 11 秒的列表/统计 SQL。

尚未确认：两次历史故障最初由哪个业务库/容器触发，是否存在特定长事务、磁盘瓶颈或网络故障。本方案不是根因归责报告，也不承诺仅修改 MSP 就能解决共享数据库其他服务的负载问题。

“最优”必须限定数据量、过滤条件、并发和业务语义。当前 SQL 已有确定改善空间，但不主张一开始就引入分库分表、读写分离、大范围缓存或新增大量索引。

## 2. 实施前基线与安全约束

1. 记录生产镜像不可变摘要、应用版本、最终生效配置、迁移版本及回滚镜像；本地已有改动不能视为已上线。
2. 所有对比在相同数据快照、硬件、缓存状态和请求集上进行；分别记录冷缓存、热缓存，不在生产清空 buffer pool 或刷新缓存来造基线。
3. 生产阶段只使用受限的元数据与普通 EXPLAIN；EXPLAIN ANALYZE、压测、数据库断连和导入重放在隔离环境执行。
4. 不在生产重新全量扫描或传输 6.1GB 慢日志；当前时段遵守用户暂停下载/传输的要求。历史缺证不阻止完成代码优化和隔离验证。
5. 不直接全库转换排序规则，不全局强制索引，不直接扩大连接池，不把 max_execution_time 当作所有写操作的超时控制。
6. 只记录 SQL 模板/摘要和必要计数，脱敏参数，避免正文、Token、密码进入证据与监控。

## 3. 需求追踪与前后端对应

下表为待实施追踪表，当前状态均为“未验收”；部分本地能力已存在，需要复用并核实部署。

| 编号 | 需求 | 用户入口/前端 | 后端位置 | 数据或配置 | 验证 |
| --- | --- | --- | --- | --- | --- |
| O01 | 资产关联唯一定位 | 用例列表、分类筛选、Agent 检索 | ExtFunctionalCaseMapper.list | 可信 organizationId；优先复用唯一索引 | EXPLAIN、结果/总数一致性、多组织 |
| O02 | 轻量精确计数与稳定分页 | 同上 | 列表 COUNT、排序及请求转换 | 共用过滤语义、按需候选联合索引 | 全部分支与分页一致性 |
| O03 | 批量详情读取 | Agent search/get、导出及步骤展示 | AgentFunctionalCaseSearchService、ExportService、FunctionalCaseService | 批量查询、请求级字典复用、字段选择 | SQL 数、耗时、权限、字段兼容 |
| O04 | 计划统计只聚合一次 | 用例列表的计划概览 | getTestPlanOverviewByCaseIds | 相关计划聚合、候选覆盖索引 | 计数/比例/归档/已删除语义 |
| O05 | 时间预算、容量和隔离 | 全部请求、导入任务 | MyBatis/JDBC/Hikari、调度池、入口保护 | 分层超时、唯一池名、总预算 | 锁等待/慢 SQL/断连恢复演练 |
| O06 | 短事务与可靠后置处理 | Excel 导入与任务记录 | FunctionalCaseFileService、LegacyImportService、同步/通知链 | 事务边界、持久任务、幂等、可选 outbox | 重试/回滚/重启/并发 |
| O07 | 低成本且可撤销的鉴权 | Agent/MCP | AgentTokenFilter/Service/RateLimiter、Shiro | 入口保护、请求级权限、审计聚合候选 | 撤销/过期/越权/锁争用 |
| O08 | 回写恢复稳定且可观测 | AI 执行状态 | recoverableTasks、WritebackService | 排序规则迁移、查询拆分候选、退避 | 真实 MySQL 集成与状态一致性 |
| O09 | 可定位和可回滚 | 运维监控、错误提示 | traceId、指标、健康检查 | 保留期、告警、发布开关 | 故障演练与版本核对 |

## 4. O01：资产元数据关联改造

### 4.1 问题与证据

当前关联条件：project_id、asset_type、asset_id。唯一索引为 `(organization_id, project_id, asset_type, asset_id)`。缺少首列使代表查询改用来源索引的前两列，并对 asset_id 继续过滤。

生产普通 EXPLAIN 对代表项目的原查询：资产表 type=ref，key=idx_test_asset_metadata_source，rows=112。对照补齐组织后：type=eq_ref，key=uk_test_asset_metadata，rows=1。rows 是优化器估算，不能宣称已经提速 112 倍。

### 4.2 推荐实现

从已授权项目加载组织归属，向 Mapper 显式传入可信 organizationId，不能信任客户端自行提交的组织 ID。

```sql
-- 结构示例，:name 为设计文档参数，不能直接复制给 MySQL 执行。
SELECT f.id, f.num, f.name, a.creation_source, a.category_id
FROM functional_case f
LEFT JOIN test_asset_metadata a
  ON a.organization_id = :verifiedOrganizationId
 AND a.project_id = f.project_id
 AND a.asset_type = 'CASE'
 AND a.asset_id = COALESCE(NULLIF(f.ref_id, ''), f.id)
WHERE f.project_id = :verifiedProjectId
  AND f.deleted = 0
  AND f.latest = 1
ORDER BY f.pos DESC, f.id ASC
LIMIT :pageSize OFFSET :offset;
```

实际改造保留列表现有的版本、模块、工作空间、分类和权限过滤，不以示例覆盖完整业务 SQL。MyBatis 使用 `#{}` 绑定值，不拼接不可信 SQL。

保留 `ref_id` 为空或空串时回退 id 的逻辑。函数在用例侧不意味着右侧唯一索引一定失效；对照计划已证明这一分支可唯一定位，不必先新增生成列。

### 4.3 边界与回滚

- 无资产元数据时仍返回用例；不能把 LEFT JOIN 的条件无意移入 WHERE 变成 INNER JOIN。
- 新旧查询有结果差异时，先查是否存在跨组织遗留/错误映射，不能简单认为旧结果正确而放宽组织条件。
- 不先新增重复索引。仅当还有明确的跨项目合法查询需要时，另行评估其索引。
- 代码可以回滚，但如果旧路径存在组织语义问题，应在业务审查后决定是否允许恢复旧逻辑。

## 5. O02：独立 COUNT、分页及候选索引

### 5.1 COUNT 与列表共用过滤语义，不共用不必要的展示关联

定义复用的过滤片段或查询条件构造，COUNT 仅保留影响集合的条件。主表筛选时：

```sql
SELECT COUNT(*)
FROM functional_case f
WHERE f.project_id = :verifiedProjectId
  AND f.deleted = 0
  AND f.latest = 1;
```

分类/来源参与筛选时，评估以 EXISTS 表达成员资格，避免多行关联扩大 COUNT：

```sql
SELECT COUNT(*)
FROM functional_case f
WHERE f.project_id = :verifiedProjectId
  AND f.deleted = 0
  AND f.latest = 1
  AND EXISTS (
    SELECT 1
    FROM test_asset_metadata a
    WHERE a.organization_id = :verifiedOrganizationId
      AND a.project_id = f.project_id
      AND a.asset_type = 'CASE'
      AND a.asset_id = COALESCE(NULLIF(f.ref_id, ''), f.id)
      AND a.creation_source = :source
  );
```

EXISTS 只是候选表达，不保证一定快于唯一关联；用计划和数据验证。COUNT 不需要模块/版本名称时才能移除相应 JOIN；如果名称、分类路径或权限参与过滤，必须保留等价条件。

避免无依据地使用 COUNT(DISTINCT) 掩盖重复关联，它可能增加临时表/排序，并隐藏模型问题。前端需要精确总数时不改成近似值；如未来引入不计总数的游标模式，必须显式变更接口契约。

### 5.2 分页

- 保留 `pos DESC, id ASC` 的确定性顺序，检查当前所有入口是否一致。
- 第一阶段保留现有页码 API；第二阶段仅为批量导出/Agent 遍历增加可选游标，避免深 OFFSET。
- 对上述混合方向排序，游标下一页条件为 `pos < :lastPos OR (pos = :lastPos AND id > :lastId)`，需处理实际 pos 的空值规则。
- 单纯游标无法保证并发修改下的全量快照。需要稳定导出时冻结 ID 集或使用明确定义的快照任务，不能长时间持有生产一致性事务。

### 5.3 索引决策

候选：`functional_case(project_id, deleted, latest, pos DESC, id ASC)`。这是针对代表筛选/排序的候选，不是立即执行的 DDL。

1. 先评估现有 `(project_id,pos)`、`(project_id,deleted,create_time,create_user)` 等访问路径。
2. 采集真实高频条件，覆盖执行人、模块、版本等分支；不同谓词未必共用一个最佳索引。
3. 隔离库创建候选索引，比较计划、排序、扫描量、写入延迟及索引空间。
4. 只有收益明确才新增；降序/升序混合方向需核对实际 MySQL 8 行为。
5. 保留主键类型、字符集和排序规则一致性，不因字符串 ID 简单改成数值。
6. 索引上线评估 metadata lock、磁盘、I/O 和复制影响；即便在线 DDL 也不承诺无阻塞。新增索引初期不删除旧索引，回滚业务不要求立即删索引。

不为 `%keyword%`、JSON tags 等所有过滤盲目加普通索引。基线若证明这些是瓶颈，再评估前缀检索、结构化标签表或全文检索，并验证中文及匹配语义。

## 6. O03：批量详情补全，消除查询放大

### 6.1 当前行为

Agent search 默认 includeSteps=true；ExportService 对每条结果读取完整详情。详情包含正文、模板、字段、关注、附件、版本、模块和多个用户。getById 即使 includeSteps=false，仍先取得完整详情再移除步骤。

### 6.2 目标读取链

```text
授权项目与目标 ID 集
 → 列表/目标基本信息
 → 按需批量正文
 → 按需批量自定义字段 / 附件元数据 / 关注
 → 请求级批量模板、模块、版本和用户字典
 → 内存映射组装 DTO
```

- 新增内部批量读取方法，限定 ID 个数并分块 IN，例如每块 100～200 个作为压测初值，不是固定生产承诺。
- 正文仅查询需要字段，不能用一个巨型 JOIN 把正文×附件×字段组合成笛卡尔膨胀。
- 共享模板和用户信息采用请求级去重，跨请求缓存单独设计失效与容量，不先引入全局永久缓存。
- includeSteps=false 时不读取/解析正文；includeSteps=true 时批量读取，保持返回字段和解码规则一致。
- 兼容现有调用：第一阶段不静默改变默认 includeSteps；新增轻量调用能力并迁移客户端后，再决定是否版本化调整默认值。
- 限制每次返回字节数、条数和全局并发；超限采用明确分页/分块或可理解错误，不能截断后返回成功。
- 大字段可能是压缩内容，复用既有解码与大小限制，避免批量 API 绕过内容校验。

### 6.3 SQL 预算

按批次规模 k，目标从“每个 case 重复查询每个维度”改为“每个分块每个必要维度查一次 + 每次请求共享字典一次”。批次内 SQL 数不应随同模板用例数重复读取模板。

批量授权必须覆盖每个 ID；先按可信项目过滤，不允许先查询其他项目正文再在 DTO 阶段过滤。排序、缺失记录及已删除行为保持原接口语义。

## 7. O04：关联计划先聚合再回填

现有 SQL 将 related 用例-计划关系关联到计划内全部有效用例，再按 related.caseId 分组。多个请求用例属于同一计划时，相同计划的统计被重复处理。

推荐候选为三阶段：相关关系去重 → 相关计划聚合一次 → 回填关系。MySQL 8 示例：

```sql
WITH related AS (
  SELECT DISTINCT functional_case_id AS case_id, test_plan_id
  FROM test_plan_functional_case
  WHERE functional_case_id IN (:authorizedCaseIds)
), relevant_plans AS (
  SELECT DISTINCT test_plan_id FROM related
), plan_stats AS (
  SELECT x.test_plan_id,
         COUNT(*) AS total,
         SUM(CASE WHEN x.last_exec_result IS NOT NULL
                   AND x.last_exec_result NOT IN ('','PENDING','UN_EXECUTED')
                  THEN 1 ELSE 0 END) AS executed,
         SUM(CASE WHEN x.last_exec_result='SUCCESS' THEN 1 ELSE 0 END) AS passed
  FROM relevant_plans rp
  JOIN test_plan_functional_case x ON x.test_plan_id=rp.test_plan_id
  JOIN functional_case f ON f.id=x.functional_case_id AND f.deleted=0
  GROUP BY x.test_plan_id
)
SELECT r.case_id, p.id, p.num, p.name, p.status,
       COALESCE(s.total,0) AS total,
       COALESCE(s.executed,0) AS executed,
       COALESCE(s.passed,0) AS passed,
       CASE WHEN COALESCE(s.total,0)=0 THEN 0
            ELSE ROUND(s.passed*100/s.total) END AS rate
FROM related r
JOIN test_plan p ON p.id=r.test_plan_id
LEFT JOIN plan_stats s ON s.test_plan_id=p.id
ORDER BY r.case_id,p.update_time DESC;
```

这是未执行、未集成的 SQL 原型；实际实现保留 archived 字段及现有 DTO 映射。`:authorizedCaseIds` 在 MyBatis 用 foreach 绑定，空集直接返回，禁止拼接字符串。

CTE 不保证自动更快，必须检查物化/合并与访问计划。可选实现为“相关计划统计一次 + Java 回填”，同样避免扩大聚合。候选覆盖索引 `(test_plan_id,functional_case_id,last_exec_result)` 与 `(functional_case_id,test_plan_id)` 必须对照现有单列索引、写入成本和结果语义后决定。

必须覆盖同一用例多计划、同一计划多条关系、空计划、删除用例和归档计划。COUNT 使用关联行还是去重用例须保持原业务定义，不顺手更改。

## 8. O05：超时、连接池与并发预算

### 8.1 分层时间预算

本地 MyBatis 默认值 25000 为秒；Hikari 30000 为获取连接等待毫秒。应记录各运行实例的最终值，避免改错单位。

建议实验初值：交互 SQL 3～5 秒、交互事务 8～10 秒、获取连接等待 1～3 秒、上层请求预算略大于内部执行/等待合计。具体取值根据业务基线审定，不能直接复制上线。多 SQL 请求需设置累计 deadline，不能误认为每条 5 秒就保证请求 10 秒。

后台导入、统计和导出使用独立预算及进度，不套用交互短时限。数据库连接建立/网络读取、事务和请求取消应协同配置；SQL cancel 不等于事务已回滚，必须验证归还连接前状态。

### 8.2 连接预算

业务池命名 `msp-prod-business`，调度池 `msp-prod-quartz`，测试环境使用不同名称。名称只是推荐，实施时可沿现有规范。

总预算应满足：所有实例业务池上限 + 所有调度池上限 + 其他服务池上限 + 管理/恢复预留 < 数据库安全连接预算。500 是当前连接配置上限，不是安全吞吐目标。

数据库吞吐没有提升时，增大池可能增加并发扫描和争用。根据实际活跃连接、等待、SQL 耗时和吞吐选择容量；虚拟线程不得视为无限并发许可。

### 8.3 隔离与降级

- 在取得数据库连接之前，对重型检索/正文/导入设置实例或全局并发阈值与有界队列，不能只按单 Token 限速。
- 队满返回 429 或 503 及受控重试提示；客户端指数退避并加随机抖动，写操作必须有幂等键。
- 数据库异常时失败应有界，禁止紧循环重试、错误时返回成功或把失败缓存成正常业务数据。
- 生存检查与就绪检查区分；生存失败不能仅由共享数据库短暂不可用触发而制造重启风暴。就绪可反映关键依赖，配合真实登录/列表合成探测。

## 9. O06：导入事务和可靠后置处理

复用本地 LegacyImportService 的任务、限制与进度能力，先确认上线版本。不要重复创建一套没有前端入口的任务表。

推荐拆分：

1. 文件接收：验证权限、大小、格式及重复请求，创建独立持久任务。
2. 解析校验：在业务写事务之外完成，设置解压、行数、正文和时间限制；中间结果存储有界且可清理。
3. 写入：先确认需要全量原子回滚还是可分批部分成功。前者保留单事务但缩短其工作内容；后者设计每批幂等和状态，不能静默改变语义。
4. 提交：保存确定结果；提交失败不写成功状态。跟踪状态更新失败应进入可对账状态。
5. 通知/默认项目同步/资产事件：采用提交后可靠队列或事务 outbox，失败可重试和去重。outbox 需版本化迁移及后台消费，不能仅改成无持久化的异步线程。
6. UI：显示排队、解析、写入、成功、部分失败、中断/结果待确认；明确重新提交是否安全，保留 jobId/traceId。

验收包括中途停止、数据库断连、对象存储不可用、重复上传、同项目并发、服务重启、权限撤销。不能用通知发送成功代表导入成功，也不能因通知失败让已提交数据被前端误判为未写入并重复提交。

## 10. O07/O08：鉴权与定时任务

### 鉴权

先测量每请求的 Token 查询、Shiro 登录、角色权限查询及 markUsed 更新成本。昂贵凭据校验前可加粗粒度入口保护，但不降低哈希强度。优先请求级权限复用；跨请求缓存必须支持撤销、到期、角色变更、租户隔离和容量限制。

Token 访问计数与最后访问时间可评估聚合写入，但若作为安全审计或计费依据则必须有可靠事件及补偿，不能简单异步丢弃。主数据库故障时认证可以安全失败，不能绕过授权保可用。

### 回写恢复

当前本地已有 BINARY 比较与退避，生产部署状态未确认。最终统一关联标识字段的排序/比较语义，迁移前检查唯一键冲突及大小写变化；BINARY 临时修复也要检查索引性能。

现有 OR + LEFT JOIN + DISTINCT + ORDER BY LIMIT 查询可评估拆成“WRITING_BACK 任务”和“到期失败同步记录”两个有界候选集，再去重。拆分必须保留全局数量限制、顺序、任务公平性和幂等；不能只执行两次 LIMIT 就认为等价。按实际计划决定任务 status/id 和同步状态/next_retry_at 的索引，现有 retry 索引应先复用。

异常按确定性结构错误、瞬态依赖故障、业务拒绝分类；确定性错误告警并退避，避免每 30 秒完整堆栈；修复后能自动恢复。不要把 Quartz 无法取得连接当成 Quartz 必然是最初根因。

## 11. O09：监控与错误协议

最小指标：

| 层级 | 指标与标签 | 用途 |
| --- | --- | --- |
| 请求 | 接口模板、环境、版本、请求量、p50/p95/p99、错误/超时 | 定位哪个路径先恶化 |
| 数据访问 | 每请求 SQL 数、摘要、耗时、事务持续时间 | 区分 N+1、慢 SQL 和长事务 |
| 连接池 | 唯一池名、active/idle/pending、获取耗时、超时 | 区分业务池与调度池 |
| MySQL | 运行线程、连接、锁等待、行扫描、临时表、buffer pool、磁盘延迟 | 判断资源瓶颈 |
| JVM/主机 | 堆、GC 停顿、CPU、磁盘、网络、重启 | 排除应用停顿与宿主机故障 |
| 任务 | jobId、项目、阶段、队列、耗时、重试、traceId | 关联导入和周期性活动 |

不将 Token、caseId 等高基数数据无限作为指标标签；按权限留受控日志/追踪。日志保留至少覆盖约定排查窗口，慢日志按容量轮转；先取证后清理。

后端复用统一 `{code,message,details?,traceId?}` 协议；用户只看到安全提示。前端区分 loading、empty、success、validation-error、permission-denied、network-error、server-error；超时不显示 SQL/堆栈，不盲目自动重试非幂等写入。

## 12. 验证矩阵与建议验收门槛

以下为测试前需确认的建议目标，不是已经测得或保证能达到的结果。

| 维度 | 覆盖 | 建议门槛 |
| --- | --- | --- |
| 正确性 | 组织/项目/版本/分类/模块/标签/执行人/回收站及权限组合 | ID 集、字段、总数、排序与批准语义一致；零越权 |
| SQL 计划 | 代表 COUNT、列表、计划概览、恢复查询 | 资产关联唯一定位；无未经解释的行数膨胀；关键分支有计划证据 |
| 时延 | 真实大小项目，单用户与混合并发 | 热态列表 p95 目标≤1秒、p99≤3秒；不达标时报告瓶颈，不能删样本 |
| 查询放大 | 20/100/500 条正文读取、模板复用 | 固定分块内查询维度固定；同工作量 SQL 数相对基线显著下降，建议≥70%作为候选目标 |
| 资源 | 相同吞吐下 CPU、I/O、连接占用与堆/GC | 无持续排队或资源爬升；各项保留前后曲线，不仅比较总耗时 |
| 故障恢复 | 数据库短暂不可用、锁等待、存储失败 | 请求在预算内失败，依赖恢复后无需人工重启；建议1分钟内恢复正常吞吐，需经验证 |
| 批量业务 | 500+、1000 条及边界条数，附件大小/正文大小独立控制 | 进度可见、无伪成功/重复写入、事务语义正确；不得以500条作为全部容量边界 |
| 其他业务 | 同库其他服务的代表负载 | MSP 优化不导致其他业务明显退化；共享实例容量单独评估 |

测试记录必须包括数据规模、索引、镜像、配置、硬件、并发、持续时间、预热、样本量和错误分布。所有请求计入，失败/超时不能从分位数与成功率报告中隐去。MySQL TIMESTAMP/日志时间统一注明时区。

## 13. 验证命令与执行前提

以下为基于当前仓库结构的计划命令，**本次未执行**。在本机隔离环境运行，事先安装项目规定的 Java/Maven/Node/pnpm，核对测试中 MySQL、Redis、对象存储及 Kafka 配置不指向生产。新测试名称在实现阶段确定，不用不存在的测试冒充验证。

仓库根目录的后端测试与打包（PowerShell）：

```powershell
.\mvnw.cmd -pl backend/services/case-management,backend/services/agent-integration -am test
.\mvnw.cmd -pl backend/app -am package -DskipTests
```

第二条跳过测试，只验证打包，不能替代第一条。现有 AgentLibrarySearchMySqlTests、AgentLegacyImportMySqlTests、AgentRecoveryScanMySqlTests 等可复用；确认其真实 MySQL 执行而非被环境条件跳过。实施新增查询计划与事务故障测试。

在 `frontend` 目录：

```powershell
pnpm run type:check
pnpm exec eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts
pnpm run build
pnpm run test:api-contracts
```

仓库 lint 脚本含 `--fix`，上面选择非自动修改命令，避免验收过程改文件。E2E 应按实施后的真实入口补充用例并使用项目已有 Playwright 配置，测试登录、列表、详情、导入和数据库降级错误状态。

容器计划：后端打包后先按 Dockerfile.backend 的 `DEPENDENCY` 构建前提准备 `backend/app/target/dependency/BOOT-INF` 等内容，再构建；缺失该目录时不能把镜像构建失败误称为 SQL 问题。

```powershell
docker build -f Dockerfile.backend -t msp-quality:verify .
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml config --quiet
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml up -d
docker compose -p msp-quality-verify -f deploy/quality-verify.compose.yml ps
Invoke-RestMethod http://127.0.0.1:17071/actuator/health/readiness
```

该 Compose 已有独立服务及本机端口，但执行前必须检查并设置 QUALITY_DB_PASSWORD、QUALITY_STORAGE_PASSWORD 及应用需要的其他测试配置；仅用于隔离验收。健康检查通过还必须执行真实登录/列表/导入冒烟，不能当作全链路完成。

迁移在该隔离库通过应用 Flyway 启动验证：覆盖全新库、生产代表结构升级、混合排序规则及失败重试。索引候选和新迁移编号以实施时仓库实际状态确定，不复用占用的版本号。

## 14. 发布顺序与回滚

| 批次 | 内容 | 发布门槛 | 回滚策略 |
| --- | --- | --- | --- |
| 0 | 版本/配置基线、唯一池名、最小监控 | 不改变业务结果，可观测 | 回滚配置/应用；保留证据 |
| 1 | O01、COUNT、明确超时配置 | 正确性与权限通过，代表计划改善，性能过关 | 独立发布开关或已验证旧镜像；超时值可调但不能无限延长掩盖故障 |
| 2 | 批量详情及计划统计 | 兼容性、SQL 数及并发验证 | 按入口逐步放量；确保所有实例一致，避免不可控双路径 |
| 3 | 索引、排序规则迁移 | 锁/空间/语义评估，备份及恢复演练 | 优先应用回滚；索引通常可保留，反向 DDL 单独评估，不急删 |
| 4 | 导入事务/后置可靠处理、鉴权优化 | 幂等、中断、撤权及升级兼容验证 | 停止新任务并排空/对账；不能直接丢弃已入队/outbox 数据 |

每批先隔离验收，再小范围灰度。建议观测至少覆盖下一次晚间实际业务高峰，并覆盖其他周期性任务；“等待一晚”不是唯一验证手段，应先完成受控压力/故障测试。

触发回滚：结果/总数/权限不一致、错误率明显上升、p95/p99 持续恶化、pending 持续增长、数据库 I/O 或锁等待显著增加、导入结果不可判定。量化阈值在发布前用基线确定，不上线后临时解释。

## 15. 工作拆分与最终完成定义

建议分为四个可独立评审的变更集：

1. SQL 关联与 COUNT：O01/O02，附生产代表计划与回归测试。
2. 批量读取与计划聚合：O03/O04，附相同工作量查询计数和性能证据。
3. 时间预算、连接池与监控：O05/O09，附断连恢复演练。
4. 导入、鉴权、回写：O06/O07/O08，分别验证状态机、幂等、权限及迁移。

数据库 DDL、应用、前端和配置变更必须有同一发布清单，避免只上线 UI 或只迁移表。具体责任人和工期尚未分配，不以本文估计替代团队排期。

所有 O01～O09 条目均应记录最终实现文件、迁移、准确执行命令与结果、容器健康及核心路径证据；任何必须项未验证时标为部分完成。性能目标不达标但正确性通过，也不能声称整体优化完成。

## 16. 本次交付说明

> 本节记录方案最初交付时的状态。2026-10-23 已按后续授权实施源码改造，最新变更、31 项测试结果、迁移及发布限制见 [SQL 稳定性实施记录](msp-sql-stability-implementation-20261023.md)。整体仍按验收证据标为部分完成。

- 新增文件：本文；未改动业务代码、前端、数据库或生产配置。
- 实际执行：读取现有审计与当前实现相关代码、package.json、Maven 模块配置、Dockerfile 和隔离 Compose；静态核对方案入口与验证命令依据；文档完整性检查。
- 前序执行过的生产 EXPLAIN/元数据、日志统计作为证据引用，本次不重复运行重型查询。
- 未执行：本文 SQL 原型、候选索引、性能基准、单元/集成/E2E、构建、迁移或容器运行。原因是本次请求为输出方案，尚未实施变更；风险是改造正确性和性能收益尚未验收，执行方法见第12～13节。
- 交付状态：**方案文档已输出；功能优化状态为部分完成/待实施，历史底层根因与责任容器仍未最终确认。**
