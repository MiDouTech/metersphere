# MeterSphere MCP 技能包

本包由 MeterSphere「系统设置 → Agent 集成」下载，交给 Cursor / Claude Desktop 等 AI 客户端，使其学会调用本平台 Agent API。

平台侧你只需要两步：

1. **下载本技能包**
2. **创建 Agent Token**（`msat_...`，关联用户为登录/执行身份）

Token 与 mcp.json 配置在**本机**完成，平台不会预填管理员账号密码。

## 环境要求

- Node.js >= 18
- 已在 MeterSphere 创建 Agent Token

## 装配步骤

1. 解压 zip，进入目录：

```bash
cd metersphere-mcp
npm install --omit=dev
```

2. 将下方配置写入 Cursor 的 `~/.cursor/mcp.json`（或项目 `.cursor/mcp.json`），按实际值替换；`args` 改为本机**绝对路径**：

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

说明：

- `MS_AGENT_TOKEN`：平台创建的 Token，作为 API 登录凭证
- `MS_PROJECT_ID`：当前操作项目；若 Token 绑定了单项目白名单，可与之对齐
- Token 仅本地保存，勿提交到 Git

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
