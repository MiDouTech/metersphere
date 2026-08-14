# task024 - P0 - 个人 Agent 接入向导状态机

- 保存 pairingId、provider、step、expiresAt，不持久化配对码。
- 查询原配对状态并恢复向导。
- 使用配对返回的 deviceId，禁止选择任意在线设备。
- 等待连接 CONNECTED 后才完成。
- 补齐详情、能力、刷新、影响分析和删除。
- 校验最低 Bridge 版本并提供升级提示。
