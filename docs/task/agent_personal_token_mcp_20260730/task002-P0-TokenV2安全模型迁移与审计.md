# task002 - P0 Token v2 安全模型、迁移与审计

## 目标

升级 Token 安全模型，降低数据库泄露后的离线撞库风险，并补齐轮换、吊销、审计和使用统计。

## 范围

- `agent_token` 数据结构
- Flyway 迁移
- Token 生成与验证
- 旧 Token 兼容迁移
- 使用统计和审计日志

## 实现要点

1. Token 格式升级为：

```text
msat_{publicId}_{secret}
```

2. `publicId` 用于快速定位记录，`secret` 使用 Argon2id 或 BCrypt 慢哈希存储。
3. 支持旧 Token 迁移窗口：
   - 新 Token 使用 v2
   - 旧 Token 标记 `token_version = 1`
   - 迁移期内继续验证
   - 页面提示轮换
4. 增加字段：
   - `public_id`
   - `secret_hash`
   - `display_prefix`
   - `token_version`
   - `last_used_at`
   - `invocation_count`
   - `last_ip`
   - `revoked_at`
   - `revoked_by`
5. 鉴权成功后异步更新使用统计。
6. 审计记录覆盖创建、修改、轮换、禁用、启用、删除、鉴权成功/失败。
7. 日志禁止输出完整 Token 和请求认证头。

## 验收标准

- 新建 Token 不以明文或简单 SHA-256 保存。
- Token 轮换后旧 Token 立即失效。
- 禁用、删除、用户停用、权限撤回后 Token 立即失效。
- 使用统计不阻塞 MCP 主请求。
- Flyway 脚本版本无冲突，重复部署无失败迁移。
- 日志和数据库中无完整 Token 明文。
