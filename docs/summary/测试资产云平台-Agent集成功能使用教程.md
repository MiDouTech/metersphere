# 测试资产云平台 Agent 集成功能使用教程

> 适用平台：测试资产云平台（MeterSphere 自研版）
> 正式环境：`https://msp.ebcone.cn`
> MCP 地址：`https://msp.ebcone.cn/api/mcp`
> 教程目标：创建个人 Agent Token、下载并安装 AI 技能包、配置 Agent、验证连接并开始使用。

## 一、开始前需要知道的三件事

1. Agent 使用你的个人 Token 调用平台，实际权限是“本人 RBAC 权限、Token Scope、项目白名单、服务端 Tool 策略”的交集，Token 不会获得超过本人账号的权限。
2. Token 明文只在创建成功时展示一次。遗失后无法找回，应创建新 Token，并禁用或删除旧 Token。
3. AI 技能包只包含平台术语、工具说明、工作流和客户端配置模板，不包含个人 Token、密码或真实项目数据。

## 二、进入 Agent 集成页面

### 第 1 步：打开个人菜单

登录测试资产云平台，点击页面左下角的个人头像和姓名，在弹出的菜单中选择“个人中心”。

![打开个人菜单](../../output/playwright/agent-integration-tutorial/01-open-personal-menu.png)

### 第 2 步：进入 Agent 集成

个人中心左侧包含“基本信息、密码设置、API KEY、本地执行、三方平台账号、Agent 集成、模型设置”等入口，点击“Agent 集成”。

![个人中心中的 Agent 集成入口](../../output/playwright/agent-integration-tutorial/02-personal-center.png)

进入后，页面分为两个区域：

- **我的 Agent Token**：创建、启用、禁用、设置和删除个人 Token；
- **MCP 技能包**：下载供 Codex、Cursor、ChatGPT、WorkBuddy 等 Agent 使用的技能包，并查看平台地址与 Scope 说明。

## 三、创建个人 Agent Token

点击“创建 Token”，依次填写以下内容。

| 字段 | 建议填写方式 | 说明 |
|---|---|---|
| Token 名称 | `姓名-客户端-用途` | 例如 `张三-Codex-回归测试`，方便后续审计和吊销 |
| 客户端 | 选择实际使用的客户端 | 当前页面可见 ChatGPT 等用途标记；Codex 也可使用通用 MCP 配置 |
| 可访问项目 | 只勾选本次需要的项目 | 不选代表本人有权访问的全部项目，生产使用不建议留空 |
| 权限范围 | 选择最小可用 Scope | 只查询时选只读；需要回写结果时再增加结果提交能力 |

### 常用权限怎么选

| 使用目的 | 推荐权限 |
|---|---|
| 只让 Agent 查看项目和用例 | 项目查看 + 功能测试只读 |
| 检索用例并回写执行结果 | 测试用例管理，或功能测试只读 + 测试结果提交 |
| 查询和维护缺陷 | 缺陷管理 |
| 管理缺陷评论、附件及关联用例 | 缺陷评论/附件/关联 |
| 同时管理项目、用例、计划、评审和缺陷 | 全部 Agent 权限，仅用于确有需要的受控账号 |

本次实际演示已成功创建 Token：`Agent教程演示-20260808`，权限范围为“测试用例管理”。完整密钥未写入本文，也未保存在仓库中。

点击“确定”后，平台会展示：

- 完整 Token，格式类似 `msat_xxx`；
- 当前环境对应的 MCP URL；
- 可复制的 MCP 配置。

立即将 Token 保存到客户端的秘密存储或环境变量中。不要把 Token 放入语雀、聊天群、截图、代码仓库或普通配置模板。

## 四、下载并检查 AI 技能包

关闭 Token 成功弹窗后，在 Agent 集成页点击“下载 AI 技能包”。正常情况下会下载：

```text
metersphere-agent-skill-1.0.0.zip
```

解压后的标准结构如下：

```text
metersphere-agent/
├── SKILL.md
├── README.md
├── manifest.json
├── references/
│   ├── tools.md
│   ├── platforms.md
│   ├── permissions.md
│   ├── workflows.md
│   └── troubleshooting.md
├── examples/
│   ├── codex.prod.config.example.toml
│   ├── cursor.remote-mcp.prod.example.json
│   ├── chatgpt-remote-mcp.prod.example.json
│   ├── workbuddy-mcp.prod.example.json
│   └── generic-streamable-http.example.json
├── scripts/
│   └── verify-mcp-connection.js
└── checksums.txt
```

下载后建议先完成三项检查：

1. 压缩包中存在 `metersphere-agent/SKILL.md`；
2. `manifest.json` 中 `tokenEmbedded` 为 `false`；
3. 包中不存在 `msat_` 开头的真实 Token。

### 当前部署状态说明

本次实际操作中，正式环境页面显示“技能包暂不可用，请联系管理员重新打包部署”，所以无法在浏览器中完成真实下载和导入验证。

从当前仓库实现看，后端 `AgentMcpBundleService` 已具备动态生成 `metersphere-agent-skill-1.0.0.zip` 的逻辑；管理员需要检查正式环境是否已部署对应版本、个人技能包 manifest/download 接口是否可用，以及网关是否正确转发下载响应。修复后再继续下面的安装步骤。

## 五、在 Agent 中安装技能

技能包和 MCP 连接是两部分：

- **安装技能**：让 Agent 理解平台术语、工具选择规则和安全工作流；
- **配置 MCP**：让 Agent 真正连接测试资产云平台并获得工具。

两者都完成后，Agent 才能稳定工作。

### 方式 A：让 Agent 自动安装（推荐）

将下载的 zip 文件交给支持技能的 Agent，然后明确提出：

> 请安装这个 MeterSphere Agent 技能包。读取其中的 SKILL.md 和 references，安装到我的个人技能目录；不要把 Token 写入项目仓库。安装完成后，使用正式环境 MCP 地址配置连接，并先执行只读验证。

Agent 应完成以下动作：

1. 解压 zip；
2. 将完整的 `metersphere-agent` 目录安装到个人技能目录；
3. 读取 `SKILL.md` 和所需 references；
4. 按客户端类型生成 MCP 配置；
5. 从环境变量或秘密存储读取 Token；
6. 重启或刷新客户端后执行只读验证。

### 方式 B：手工安装技能

将解压后的整个 `metersphere-agent` 目录放入 Agent 支持的个人技能目录。不要只复制 `SKILL.md`，否则 `references`、`examples` 和验证脚本会丢失。

个人技能目录因客户端版本和企业策略不同而不同，应优先使用客户端的“安装技能 / 导入技能”功能；若客户端要求文件目录，按其当前版本文档选择用户级目录，避免放进业务仓库后被提交到 Git。

## 六、配置远程 MCP

### Codex

把以下配置加入 Codex 的个人配置，不要写入项目公共配置：

```toml
[mcp_servers.metersphere]
type = "streamable-http"
url = "https://msp.ebcone.cn/api/mcp"

[mcp_servers.metersphere.headers]
Authorization = "Bearer ${METERSPHERE_AGENT_TOKEN}"
```

在操作系统或客户端秘密存储中设置 `METERSPHERE_AGENT_TOKEN`，值为刚才创建的完整 Token。设置后重启 Codex。

### Cursor

在个人或工作区 MCP 配置中加入：

```json
{
  "mcpServers": {
    "metersphere": {
      "url": "https://msp.ebcone.cn/api/mcp",
      "headers": {
        "Authorization": "Bearer ${METERSPHERE_AGENT_TOKEN}"
      }
    }
  }
}
```

建议由本地秘密存储提供 `METERSPHERE_AGENT_TOKEN`。如果当前 Cursor 版本不支持变量替换，应使用客户端提供的 Secret 功能，不要把明文 Token 提交到 `.cursor/mcp.json`。

### WorkBuddy 或其他 Streamable HTTP 客户端

新增一个远程 MCP Server：

```json
{
  "name": "metersphere",
  "transport": "streamable-http",
  "url": "https://msp.ebcone.cn/api/mcp",
  "headers": {
    "Authorization": "Bearer ${METERSPHERE_AGENT_TOKEN}"
  }
}
```

如果客户端只提供表单，对应填写：

- 名称：`metersphere`
- 传输协议：`Streamable HTTP`
- URL：`https://msp.ebcone.cn/api/mcp`
- Header 名称：`Authorization`
- Header 值：`Bearer <你的完整 Token>`

## 七、验证连接

首次连接只做只读验证，不要直接创建、修改或删除数据。

建议依次向 Agent 发送：

1. `列出测试资产云平台中我可以访问的项目。`
2. `搜索“测试项目-测试一体化”，只返回项目名称和项目 ID。`
3. `列出该项目的功能用例模块，不执行写操作。`
4. `查找该项目中 P0 用例，只返回前 10 条摘要。`

预期现象：

- Agent 能看到 `metersphere.project.*`、`metersphere.functional.*` 等工具；
- 项目查询返回本人和 Token 都有权访问的项目；
- 用例查询不会越过 Token 项目白名单；
- 平台 Token 列表中的“最后使用时间”和“调用次数”发生更新。

连接确认后，可以继续尝试：

> 查询“测试项目-测试一体化”中标签包含“测试资产云平台”的 P0 用例，先向我确认范围，再读取步骤。

需要执行和回写时：

> 将确认后的用例加入指定测试计划，逐条执行；失败时记录实际结果和证据，回写前先向我确认。

## 八、常见问题

### 1. 页面提示“技能包暂不可用”

这是服务端技能包 manifest 或下载接口不可用，不是浏览器下载权限问题。联系管理员检查部署版本和 `/api/personal/agent-package/manifest`、`/api/personal/agent-package/skill/download`。

### 2. 返回 401

Token 缺失、格式错误、已禁用、已删除或已过期。确认 Header 是：

```text
Authorization: Bearer msat_xxx
```

### 3. 返回 403

当前操作不在 Token Scope、本人 RBAC 权限或项目白名单内。不要直接改成“全部 Agent 权限”，应先确认缺的是哪一项权限。

### 4. 返回 405

远程 MCP 使用 Streamable HTTP POST。直接在浏览器中 GET `/api/mcp` 可能返回 405，这不代表 MCP 服务异常。

### 5. 返回 429

调用频率超过限制。按服务返回的 `Retry-After` 等待，不要让 Agent 无限重试。

### 6. Agent 找不到目标项目

先调用项目搜索，再使用返回的项目 ID。若同名或模糊搜索命中多个项目，必须让用户确认，不能由 Agent 随机选择。

## 九、安全与回收

- Token 一人一端一用途，不多人共用；
- 优先绑定具体项目，避免默认开放本人全部项目；
- 日常查询使用只读 Scope，执行和写入使用独立 Token；
- 不在提示词、日志、截图和 Git 中复述完整 Token；
- 设备丢失、人员离职或 Token 疑似泄露时，立即禁用或删除；
- 定期检查“最后使用时间”和“调用次数”，清理长期不用的 Token；
- 删除、恢复、批量更新和结果回写前保留人工确认。

## 十、本次实操结论

- 已通过 Edge 9222 登录会话进入正式环境的“个人中心 → Agent 集成”；
- 已实际创建 `Agent教程演示-20260808` Token，Scope 为“测试用例管理”；
- Token 明文已脱敏，未写入教程或仓库；
- 当前正式环境的技能包下载不可用，因此技能真实下载、Agent 导入和 MCP 连接验证尚未完成；
- 待管理员恢复技能包下载后，可直接从本文第四节继续操作。
