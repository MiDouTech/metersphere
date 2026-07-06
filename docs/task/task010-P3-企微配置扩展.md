# task010 - P3 企微配置扩展

> **阶段**：P3  
> **预估工期**：2 天  
> **前置依赖**：[task008](./task008-P2-同步API与定时任务.md)  
> **阻塞任务**：无（可与 task009 并行）  
> **关联文件**：`frontend/src/views/setting/system/config/components/weComModal.vue`

---

## 1. 任务目标

在现有企微登录配置基础上，扩展**通讯录 Secret、定时同步 Cron** 等字段，使管理员可在 UI 完成同步配置，无需直接改数据库。

---

## 2. 现状

| 能力 | 现状 |
|------|------|
| 企微扫码登录 | `weComModal.vue` + `/we_com/info` 配置 |
| 企微消息 Webhook | 通知模块已支持 |
| 通讯录 Secret | **缺失** |
| 定时同步 Cron | **缺失** |

---

## 3. 任务清单

### 3.1 后端 — 配置 API 完善

task008 已提供 `POST /org-wecom/config/save`，本任务补充：

| API | 说明 |
|-----|------|
| GET `/org-wecom/config/get?organizationId=` | 读取当前组织配置 |
| POST `/org-wecom/config/test` | 测试连通性（gettoken + listDepartments） |

**test 响应**：

```json
{
  "success": true,
  "deptCount": 15,
  "message": "连接成功"
}
```

### 3.2 前端 — 配置面板

**方案 A（推荐）**：在 `orgStructure/index.vue` 内嵌「同步配置」抽屉  

**方案 B**：扩展 `weComModal.vue` 增加 Tab（登录 / 通讯录）

**配置项**：

| 字段 | 组件 | 说明 |
|------|------|------|
| organizationId | 选择器（系统管理员） | 绑定 MS 组织 |
| corpId | Input | 企微 CorpID |
| contactSecret | Input.Password | 通讯录 Secret |
| scheduleEnabled | Switch | 启用定时同步 |
| scheduleCron | Input + Cron 帮助链接 | 如 `0 0 2 * * ?` |
| retryTimes | InputNumber | 默认 3 |

### 3.3 组织映射 UI

**一期约定**：一个企微主体 = 一个 MS 组织

| UI 元素 | 说明 |
|---------|------|
| 组织选择 | 下拉 MS 组织列表 |
| 提示文案 | 「请确保该组织与企微主体一一对应」 |
| 重复绑定校验 | 同一 organizationId 不可重复保存不同 corpId（可选） |

### 3.4 与登录配置关系

| 配置 | 存储位置 | 说明 |
|------|----------|------|
| 登录 AgentId/Secret | 现有系统参数 / we_com 配置 | 不变 |
| 通讯录 Secret | `org_wecom_sync_config` | 新建 |

**注意**：登录 Secret ≠ 通讯录 Secret，UI 需明确区分。

---

## 4. 权限

| 操作 | 权限 |
|------|------|
| 查看配置 | 系统管理员 / 组织管理员 |
| 保存配置 | `SYSTEM_ORGANIZATION_PROJECT:READ+UPDATE` |
| 测试连接 | 同保存 |

---

## 5. 安全要求

- [x] contactSecret 接口返回时掩码（仅显示后 4 位）  
- [x] 保存时若 Secret 为掩码占位符则不更新原值  
- [x] 操作写审计日志  

---

## 6. 测试用例

| 场景 | 预期 |
|------|------|
| 首次保存 | 写入 org_wecom_sync_config |
| 修改 Cron | 定时任务下次按新 cron 执行 |
| 测试连接成功 |  toast 成功 + deptCount |
| 错误 Secret |  toast 失败 + errcode |
| 组织管理员 | 只能编辑本组织配置 |

---

## 7. 验收标准

- [x] UI 可配置 corpId + contactSecret + cron  
- [x] 测试连接可用  
- [x] 保存后 task008 手动同步可正常执行  
- [x] Secret 不在前端明文回显  
- [x] 登录配置与通讯录配置职责分离清晰  

---

## 8. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-06 |
| 完成日期 | 2026-07-06 |
