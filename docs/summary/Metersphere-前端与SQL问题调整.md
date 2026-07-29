# 前端与 SQL 问题调整

> 文档说明：汇总 MeterSphere 本地开发环境中已排查并修复的前端 UI 与 MySQL `ONLY_FULL_GROUP_BY` 相关问题。  
> 适用环境：前端 `http://localhost:5173`，后端 `http://localhost:8081`，MySQL 8.0（`sql_mode` 含 `ONLY_FULL_GROUP_BY`）。

---

## 一、问题总览

| 序号 | 现象 | 根因类别 | 状态 |
|------|------|----------|------|
| 1 | 登录页左侧大图缺失、favicon 裂图 | 静态资源未同步 + URL/MIME 配置 | 已修复 |
| 2 | 工作台/测试计划 SQL 红色报错横幅 | `GROUP BY` 与 `ONLY_FULL_GROUP_BY` 冲突 | 已修复 |
| 3 | 组织成员页 SQL 报错、表格无数据 | `ExtOrganizationMapper` 子查询 `temp.*` | 已修复 |
| 4 | 登录后首次进入页面约 10s | Vite 开发模式按需编译 + 请求排队 | 已优化 |
| 5 | 测试用例模块树出现横向滚动条 | 分割面板宽度溢出 | 已修复 |
| 6 | 侧边栏「系统设置」展开后与上方菜单重叠 | 菜单容器无纵向滚动 | 已修复 |

---

## 二、SQL 问题

### 2.1 背景

本地 MySQL 容器若未应用 `dev/docker-compose.yml` 中的 `sql_mode` 配置，或连接外部 MySQL 实例时，常开启 `ONLY_FULL_GROUP_BY`。  
该模式下，`SELECT` 中的非聚合列必须出现在 `GROUP BY` 中，或具备函数依赖关系，否则抛出 `SQLSyntaxErrorException (1055)`。

`dev/docker-compose.yml` 推荐配置（不含 `ONLY_FULL_GROUP_BY`）：

```properties
sql_mode=STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION
```

若暂无法调整数据库，应在 Mapper 层兼容该模式。

---

### 2.2 ExtTestPlanBugMapper.xml — `selectPlanRelationBug`

**文件：** `backend/services/test-plan/src/main/java/io/metersphere/plan/mapper/ExtTestPlanBugMapper.xml`

**报错示例：**

```
Expression #7 of SELECT list is not in GROUP BY clause and contains nonaggregated column
'metersphere.brc.create_user' which is not functionally dependent on columns in GROUP BY clause
```

**原因：** `GROUP BY brc.bug_id` 的同时选择了 `brc.create_user`、`brc.create_time` 等非聚合列。

**修复方式：**

- `create_user`：使用 `GROUP_CONCAT` 按 `create_time` 倒序取最新关联人
- `create_time`：使用 `MAX(brc.create_time)`
- `bug` 表字段加入 `GROUP BY`，保证与 `ONLY_FULL_GROUP_BY` 兼容

**影响范围：** 工作台「测试计划概览」、Dashboard 中调用 `selectPlanRelationBug` 的接口。

---

### 2.3 ExtOrganizationMapper.xml — `listMember` / `listMemberByOrg`

**文件：** `backend/services/system-setting/src/main/java/io/metersphere/system/mapper/ExtOrganizationMapper.xml`

**报错示例：**

```
Expression #17 of SELECT list is not in GROUP BY clause and contains nonaggregated column 'temp.role_id'
```

**原因：** 嵌套子查询 `SELECT temp.* ... GROUP BY temp.id` 中，`temp.*` 包含 `role_id`、`memberTime`，与 `GROUP BY temp.id` 冲突。

**修复方式：** 去掉嵌套子查询，直接关联 `user` 与 `user_role_relation`，按 `u.id` 分组：

```sql
-- listMember
SELECT u.*,
       MAX(IF(urr.role_id = 'org_admin', TRUE, FALSE)) AS adminFlag,
       MIN(urr.create_time) AS groupTime
FROM user_role_relation urr
JOIN `user` u ON urr.user_id = u.id
WHERE ...
GROUP BY u.id
ORDER BY adminFlag DESC, groupTime DESC;

-- listMemberByOrg
SELECT u.*, MIN(urr.create_time) AS groupTime
FROM user_role_relation urr
JOIN `user` u ON urr.user_id = u.id AND u.deleted = FALSE
WHERE ...
GROUP BY u.id
ORDER BY groupTime DESC;
```

**影响范围：** 系统设置 → 组织 → 成员列表及分页 count 查询。

---

## 三、前端问题

### 3.1 登录页静态资源

**现象：**

- 登录页仅显示右侧表单，左侧 banner 缺失
- 浏览器标签页 favicon 显示裂图
- `/base-display/get/login-image` 返回约 2.6KB SVG 占位，而非约 500KB 的 JPEG

**原因：**

1. 本地 `start-dev.ps1` / `start.ps1` 使用 `-DskipAntRunForJenkins=true`，跳过 Maven 将 `frontend/public` 复制到 `backend/app/src/main/resources/static`
2. 图片 API 路径缺少前导 `/`，部分场景下解析异常
3. `setFavicon` 将 SVG 资源标记为 `image/x-icon`

**修复文件与内容：**

| 文件 | 调整 |
|------|------|
| `scripts/setup-local-env.ps1` | 启动前同步 `frontend/public` → `backend/.../static` |
| `scripts/start-dev.ps1` | 启动前同步静态资源 |
| `backend/.../BaseDisplayService.java` | `login-image` 返回正确 `Content-Type`（JPEG/SVG 分支） |
| `frontend/src/api/requrls/setting/config.ts` | 图片 URL 改为 `/front/base-display/...` 绝对路径 |
| `frontend/src/utils/theme.ts` | favicon 按 SVG 设置 `image/svg+xml` |

**验证：**

```text
GET http://localhost:8081/base-display/get/login-image  → 200, image/jpeg, ~501KB
GET http://localhost:5173/front/base-display/get/login-image → 200, image/jpeg
```

---

### 3.2 登录后首次加载慢（约 10s）

**现象：** Network 面板中 `thirdDemandDrawer.vue` 等模块首次请求耗时约 10s（Stalled 7s + TTFB 2.8s）。

**原因：**

- Vite 开发模式对 `.vue` 文件按需编译，首次访问耗时长
- 浏览器同域 HTTP/1.1 连接数限制（约 6 个），大量模块请求排队（Stalled）
- `caseTable.vue` 同步 import 重量级抽屉组件，进入测试用例页即触发编译

**修复：**

| 文件 | 调整 |
|------|------|
| `frontend/.../caseTable.vue` | `ThirdDemandDrawer` 改为 `defineAsyncComponent` 懒加载，并加 `v-if="showThirdDrawer"` |

**说明：** 该问题主要出现在 **开发模式**；生产环境 `npm run build` 预构建后首屏会明显更快。

---

### 3.3 测试用例模块树横向滚动条

**现象：** 左侧模块树区域底部出现不必要的横向滚动条，内容本可完整展示。

**原因：**

- `MsSplitBox` 左侧面板宽度为 `calc(size + 7px)`，超出分割面板
- 树节点 `inline-flex w-full` 未做 `min-w-0` 收缩

**修复文件：**

| 文件 | 调整 |
|------|------|
| `frontend/src/components/pure/ms-split-box/index.vue` | 左侧 `width: 100%`、`overflow-x: hidden` |
| `frontend/.../caseManagementFeature/index.vue` | 模块区 `min-w-0 overflow-x-hidden`，标题行 `truncate` |
| `frontend/.../caseTree.vue` | 节点标题 `flex-1 min-w-0` |
| `frontend/src/components/business/ms-tree/index.vue` | 树容器 `overflow-x-hidden overflow-y-auto` |

---

### 3.4 侧边栏「系统设置」菜单重叠

**现象：** 展开「系统设置 → 组织」时，子菜单与上方「测试计划 / 测试用例 / 缺陷管理」等项重叠。

**原因：** `.arco-menu-inner` 无高度上限与纵向滚动；菜单项增多或子菜单展开时内容向上溢出。

**修复文件：** `frontend/src/components/business/ms-menu/index.vue`

**调整内容：**

- `.arco-menu-inner` 增加 `max-height: calc(100vh - 56px)`、`overflow-y: auto`
- 菜单列表容器增加 `min-h-0 overflow-hidden`

---

## 四、本地开发注意事项

### 4.1 启动与静态资源

```powershell
# 同步静态资源（首次或 public 变更后）
powershell -File scripts\setup-local-env.ps1

# 启动开发服务
scripts\start-dev.ps1

# 停止
scripts\start-dev.ps1 -Stop
# 或
.\start.ps1 -Stop -SkipDeps
```

**说明：** 修改 Mapper XML 或 Java 后需 **重启后端**；前端 Vue/TS 修改可由 Vite 热更新。

### 4.2 MySQL sql_mode（可选）

若希望从数据库层面关闭 `ONLY_FULL_GROUP_BY`（与 `docker-compose.yml` 一致）：

```sql
SET GLOBAL sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
```

持久化需修改 MySQL 配置并重建/重启容器。  
**推荐：** 代码层兼容 + 容器 `sql_mode` 配置双保险。

### 4.3 默认登录账号（本地）

- 用户名：`admin`
- 密码：`metersphere`

---

## 五、变更文件清单

### 后端

| 文件 | 变更类型 |
|------|----------|
| `backend/services/test-plan/.../ExtTestPlanBugMapper.xml` | SQL GROUP BY 修复 |
| `backend/services/system-setting/.../ExtOrganizationMapper.xml` | SQL GROUP BY 修复 |
| `backend/services/system-setting/.../BaseDisplayService.java` | 静态资源 Content-Type 与回退逻辑 |

### 前端

| 文件 | 变更类型 |
|------|----------|
| `frontend/src/api/requrls/setting/config.ts` | 图片 API 绝对路径 |
| `frontend/src/utils/theme.ts` | favicon MIME 类型 |
| `frontend/src/components/pure/ms-split-box/index.vue` | 左侧面板宽度/溢出 |
| `frontend/src/components/business/ms-tree/index.vue` | 树滚动方向 |
| `frontend/src/components/business/ms-menu/index.vue` | 侧栏菜单滚动 |
| `frontend/src/views/case-management/caseManagementFeature/index.vue` | 模块树布局 |
| `frontend/src/views/case-management/.../caseTree.vue` | 树节点布局 |
| `frontend/src/views/case-management/.../caseTable.vue` | ThirdDemandDrawer 懒加载 |

### 脚本

| 文件 | 变更类型 |
|------|----------|
| `scripts/setup-local-env.ps1` | 静态资源同步 |
| `scripts/start-dev.ps1` | 启动前静态资源同步 |

---

## 六、验证清单

- [ ] 登录页左侧 banner 与 favicon 正常显示
- [ ] 登录后进入工作台，无 SQL 红色报错横幅
- [ ] 系统设置 → 组织 → 成员：列表可加载，无 `temp.role_id` 报错
- [ ] 测试用例页左侧模块树无横向滚动条
- [ ] 展开「系统设置」子菜单时，侧栏可纵向滚动、无重叠
- [ ] 首次进入测试用例页时，不再同步加载 `thirdDemandDrawer`（Network 中仅在打开抽屉时出现）

---

## 七、后续建议

1. **同类 SQL 排查：** 全局搜索 `group by` + `select *` / 子查询 `temp.*` 模式，在 `ONLY_FULL_GROUP_BY` 下逐一验证。
2. **生产构建验证：** 执行 `npm run build` 后通过后端静态目录或 Nginx 访问，确认登录页与首屏性能符合预期。
3. **start.ps1 日志重定向：** 当前 `RedirectStandardOutput` 与 `RedirectStandardError` 指向同一文件会导致启动失败，可改为分开重定向或使用 `start-dev.ps1`。
4. **MySQL 数据卷：** 若容器仍带 `ONLY_FULL_GROUP_BY`，可能是旧 volume 未应用 compose 配置，需重建 MySQL 卷或手动修改 `sql_mode`。

---

*文档生成时间：2026-06-22*
