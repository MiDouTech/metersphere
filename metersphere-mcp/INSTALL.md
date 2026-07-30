# MeterSphere MCP 本地装配说明

本包由 MeterSphere「系统设置 → Agent 集成」下载，供 Cursor / Claude Desktop 等 MCP 客户端使用。

## 环境要求

- Node.js >= 18
- 已在 MeterSphere 创建 Agent Token（`msat_...`）

## 装配步骤

1. 解压 zip，进入目录：

```bash
cd metersphere-mcp
npm install --omit=dev
```

2. 将下方配置写入 Cursor 的 `~/.cursor/mcp.json`（或项目 `.cursor/mcp.json`），按实际值替换环境变量；`args` 改为本机**绝对路径**：

```json
{
  "mcpServers": {
    "metersphere": {
      "command": "node",
      "args": ["REPLACE_WITH_ABSOLUTE_PATH/metersphere-mcp/dist/index.js"],
      "env": {
        "MS_BASE_URL": "https://your-metersphere-host",
        "MS_AGENT_TOKEN": "msat_xxx",
        "MS_PROJECT_ID": "your-project-id",
        "MS_TEST_PLAN_ID": ""
      }
    }
  }
}
```

也可在平台「Agent 集成」页填写 Token / 项目后一键复制完整配置。

3. 重启 Cursor，在 Settings → MCP 确认 `metersphere` 已连接。

## 权限 Scope

| Scope | 能力 |
|-------|------|
| `AGENT_ALL` | 全能力（推荐闭环） |
| `BUG_READ` | 缺陷检索/详情 |
| `BUG_WRITE` | 缺陷创建/更新/关联（隐含读） |
| `FUNCTIONAL_*` / `CASE_WRITE` 等 | 见平台 Token 创建页说明 |

## 验证

在对话中试：「搜索当前项目缺陷」或「列出功能用例模块」。

详细工具列表见 `README.md`。
