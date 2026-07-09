# task002 - P0 数据模型与 Flyway 迁移

> **阶段**：P0  
> **预估工期**：0.5 天  
> **前置依赖**：[task001](task001-P0-agent-integration模块脚手架.md)  
> **阻塞任务**：task003、task013  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §5.1.1

---

## 1. 任务目标

新增 `agent_token`、`agent_exec_log` 表及对应 Domain / Mapper，为 Bearer 认证与执行审计提供持久化基础。

---

## 2. 迁移文件规划

**建议版本号**：按当前仓库 Flyway 版本递增（如 `3.7.1` 或下一可用版本）

| 文件 | 内容 |
|------|------|
| `ddl/V3.x.x_1__agent_integration.sql` | `agent_token`、`agent_exec_log` |

**路径**：`backend/framework/domain/src/main/resources/migration/{version}/ddl/`

---

## 3. 表结构设计

### 3.1 agent_token

```sql
CREATE TABLE agent_token (
    id           VARCHAR(50)  PRIMARY KEY,
    name         VARCHAR(100) NOT NULL COMMENT 'Token 名称',
    token_prefix VARCHAR(10)  NOT NULL COMMENT '前缀 msat',
    token_hash   VARCHAR(128) NOT NULL COMMENT 'SHA-256(token)',
    user_id      VARCHAR(50)  NOT NULL COMMENT '关联用户',
    project_id   VARCHAR(50)           COMMENT '默认项目 ID',
    scopes       VARCHAR(255)          COMMENT 'FUNCTIONAL_READ,FUNCTIONAL_SUBMIT',
    expire_time  BIGINT                COMMENT '过期时间戳，NULL=永不过期',
    enable       TINYINT(1)   DEFAULT 1,
    create_time  BIGINT,
    create_user  VARCHAR(50)
);
```

### 3.2 agent_exec_log

```sql
CREATE TABLE agent_exec_log (
    id                VARCHAR(50) PRIMARY KEY,
    case_id           VARCHAR(50) NOT NULL,
    test_plan_id      VARCHAR(50),
    test_plan_case_id VARCHAR(50),
    last_exec_result  VARCHAR(20) NOT NULL,
    executed_by       VARCHAR(100) COMMENT 'Agent 标识',
    steps_snapshot    LONGTEXT COMMENT '步骤执行快照 JSON',
    content           LONGTEXT COMMENT '执行备注',
    create_time       BIGINT,
    create_user       VARCHAR(50)
);
```

---

## 4. 领域模型与 Mapper

| 类型 | 路径 |
|------|------|
| Domain | `backend/framework/domain/.../domain/AgentToken.java` |
| Domain | `backend/framework/domain/.../domain/AgentExecLog.java` |
| Mapper | `AgentTokenMapper.java` + `.xml` |
| Mapper | `AgentExecLogMapper.java` + `.xml` |

**索引建议**：

```sql
CREATE INDEX idx_agent_token_hash ON agent_token(token_hash);
CREATE INDEX idx_agent_exec_log_case ON agent_exec_log(case_id, create_time DESC);
```

---

## 5. Token 初始化脚本（P0 手工）

在 `docs/task/fixtures/` 提供示例 SQL（不提交真实 token 明文）：

```sql
-- 示例：创建 Token（明文仅展示一次，入库为 SHA-256）
-- msat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
INSERT INTO agent_token (id, name, token_prefix, token_hash, user_id, project_id, scopes, enable, create_time, create_user)
VALUES (...);
```

---

## 6. 回滚脚本

在 `docs/task/rollback/` 提供：

```sql
DROP TABLE IF EXISTS agent_exec_log;
DROP TABLE IF EXISTS agent_token;
```

---

## 7. 验收标准

- [ ] 本地 `spring-boot:run` Flyway 迁移无报错（待 task010 运行时验证）  
- [x] 迁移脚本已定义 `agent_token`、`agent_exec_log` 表  
- [x] `AgentTokenMapper` 已实现  
- [x] 回滚脚本已记录（`docs/task/rollback/V3.7.1_agent_integration_rollback.sql`）  

---

## 8. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
| 备注 | 代码已交付；Flyway 运行时验证归属 task010 |
