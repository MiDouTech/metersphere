# task011 - P3 Excel 组织架构导入

> **阶段**：P3  
> **预估工期**：3 天  
> **前置依赖**：[task002](task002-P0-组织创建与切换API.md)、[task004](task004-P1-数据模型与Flyway迁移.md)、[task005](task005-P1-组织架构查询API.md)  
> **说明**：MeterSphere 扩展能力，myTapd 无此功能

---

## 1. 任务目标

支持通过 Excel 批量导入**部门树**与**组织成员**，作为无企微或企微不可用场景下的备选方案，复用 MS 现有用户导入基础设施。

---

## 2. Excel 模板设计

### 2.1 Sheet1 — 部门（department）

| 列名 | 必填 | 说明 | 示例 |
|------|------|------|------|
| 组织名称 | 是 | MS 组织 name | 默认组织 |
| 部门路径 | 是 | 用 `/` 分隔层级 | 默认组织/研发部/后端组 |
| 上级部门路径 | 否 | 空表示顶级 | 默认组织/研发部 |
| 排序 | 否 | sort_order | 1 |

**规则**：

- 组织名称必须已存在于 `organization` 表  
- 部门路径全局唯一（同组织内）  
- 先导入父部门再导入子部门（或按路径长度排序）  

### 2.2 Sheet2 — 用户（user）

| 列名 | 必填 | 说明 | 示例 |
|------|------|------|------|
| 姓名 | 是 | user.name | 张三 |
| 邮箱 | 是 | user.email，唯一 | zhangsan@example.com |
| 手机 | 否 | user.phone | 13800138000 |
| 组织名称 | 是 | 所属 MS 组织 | 默认组织 |
| 部门路径 | 否 | 对应 Sheet1 路径 | 默认组织/研发部 |
| 组织用户组 | 否 | 默认 org_member | org_member |
| 职位 | 否 | user.position | 测试工程师 |

---

## 3. 后端实现

### 3.1 模块结构

```text
backend/services/system-setting/src/main/java/io/metersphere/system/
├── controller/OrgStructureImportController.java
├── service/department/OrgStructureImportService.java
├── excel/OrgStructureImportEventListener.java
└── dto/department/OrgStructureImportResult.java
```

### 3.2 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/org-structure/import/template` | 下载 Excel 模板 |
| POST | `/org-structure/import` | multipart 上传导入 |
| GET | `/org-structure/import/result/{taskId}` | 异步结果（可选） |

### 3.3 导入流程

```
OrgStructureImportService.import(file, operatorId)
  1. 解析 Sheet1 部门
     - 校验组织存在
     - 按路径长度排序
     - upsert department（wecom_dept_id 为空，sync_status=0 表示手工导入）
  2. 解析 Sheet2 用户
     - 校验邮箱唯一
     - 解析部门路径 → department_id
     - 已存在：update；不存在：SimpleUserService.addUser + addMemberBySystem
  3. 返回 ImportResult（成功/失败行号及原因）
```

### 3.4 参考现有实现

| 参考 | 路径 |
|------|------|
| 用户 Excel 导入 | `SimpleUserService.importByExcel` |
| 事件监听 | `UserImportEventListener.java` |
| EasyExcel | 项目已依赖 `easyexcel` |

### 3.5 与企微同步共存

| 场景 | 行为 |
|------|------|
| 手工导入部门 | `wecom_dept_id = null`，`sync_status = 0` |
| 后续企微同步 | 按 wecom_dept_id upsert，不删除手工部门（可配置策略） |
| 冲突策略（推荐） | 企微同步仅更新有 wecom_dept_id 的记录；手工部门保留 |

---

## 4. 前端实现

### 4.1 入口

在 `orgStructure/index.vue` 增加「Excel 导入」按钮，或系统用户页增加 Tab。

### 4.2 交互

1. 下载模板  
2. 上传文件  
3. 展示导入结果（成功 N 条，失败 M 条 + 失败原因列表）  
4. 刷新部门树和成员表  

**参考**：`frontend/src/views/setting/system/user/index.vue` 导入交互。

---

## 5. 校验规则

| 规则 | 错误提示 |
|------|----------|
| 组织不存在 | 第 X 行：组织「XXX」不存在 |
| 部门路径重复 | 第 X 行：部门路径重复 |
| 上级部门不存在 | 第 X 行：上级部门路径无效 |
| 邮箱格式错误 | 第 X 行：邮箱格式不正确 |
| 邮箱已存在 | 第 X 行：邮箱已被使用 |
| 用户组不存在 | 第 X 行：用户组「XXX」不存在，已使用 org_member |

---

## 6. 测试要求

| 用例 | 预期 |
|------|------|
| 仅导入部门 | 树形结构正确 |
| 导入部门+用户 | 用户绑定部门 + org_member |
| 重复导入 | upsert 不重复 |
| 错误行 | 部分成功 + 错误报告 |
| 1000 行性能 | 60s 内完成（目标） |

---

## 7. 验收标准

- [ ] 模板可下载  
- [ ] 导入部门后 task005 树 API 可见  
- [ ] 导入用户后可登录（LOCAL 源）或作为组织成员  
- [ ] 错误行报告清晰  
- [ ] 与企微同步不互相破坏（按冲突策略）  

---

## 8. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 待开始 |
| 开始日期 | |
| 完成日期 | |
