# task035 - P1 - 管理员 Token 治理与 Legacy 清理

- 系统设置 Agent 集成增加 Token 治理区域。
- 支持全局分页、筛选、管理员撤销和审计 CSV 下载。
- 前端使用 SYSTEM_USER READ/UPDATE，后端保持同权限。
- 旧 `/agent/token/**` 完成调用审计；无调用则删除，需兼容则标为 LEGACY 并提供到期日和契约测试。
