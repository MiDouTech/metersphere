# task006 - P2 企微通讯录客户端

> **阶段**：P2  
> **预估工期**：2 天  
> **前置依赖**：[task004](task004-P1-数据模型与Flyway迁移.md)  
> **阻塞任务**：task007、task008  
> **参考项目**：myTapd `ticket-infrastructure/.../external/wework/WecomClient.java`

---

## 1. 任务目标

新建独立于 Webhook 的**企微通讯录 API 客户端**，封装 Token 管理、部门列表、成员列表等能力，供同步引擎调用。

---

## 2. 与现有代码边界

| 组件 | 路径 | 用途 | 本任务 |
|------|------|------|--------|
| WeComClient | `notice/utils/WeComClient.java` | Webhook 消息 | **不改动** |
| WeComNoticeSender | `notice/sender/impl/` | 通知发送 | **不改动** |
| WecomContactClient | **新建** | 通讯录 API | **本任务** |

---

## 3. 任务清单

### 3.1 WecomContactClient

**路径**：`backend/services/system-setting/src/main/java/io/metersphere/system/service/wecom/WecomContactClient.java`

| 方法 | 企微 API | 说明 |
|------|----------|------|
| `getAccessToken(corpId, contactSecret)` | `GET /cgi-bin/gettoken` | 内存缓存，过期前 5 分钟刷新 |
| `listDepartments(accessToken)` | `GET /cgi-bin/department/list` | 返回全量部门 |
| `listDepartmentUsers(accessToken, deptId, fetchChild)` | `GET /cgi-bin/user/list` | 部门成员 |
| `getUser(accessToken, userId)` | `GET /cgi-bin/user/get` | 单人详情（登录兜底用） |

### 3.2 DTO 定义

**路径**：`backend/services/system-setting/.../dto/wecom/`

| 类 | 字段 |
|----|------|
| `WecomDepartmentDTO` | id, name, parentid, order, departmentLeader |
| `WecomUserDTO` | userid, name, mobile, email, position, department[], status |
| `WecomTokenResponse` | errcode, errmsg, accessToken, expiresIn |

### 3.3 异常与重试

| 场景 | 处理 |
|------|------|
| errcode != 0 | 抛 `WecomApiException`，携带 errcode/errmsg |
| Token 过期 | 清除缓存并重试 1 次 |
| 网络超时 | 可配置重试（默认 3 次，指数退避） |

### 3.4 配置读取

从 `org_wecom_sync_config` 读取：

- `corp_id`  
- `contact_secret`  

**一期约定**：一个 MS 组织对应一条配置（见 task000 默认决策）。

---

## 4. 移植对照（myTapd → MS）

| myTapd | MeterSphere |
|--------|-------------|
| `WecomClient` | `WecomContactClient` |
| `sys_wework_config` | `org_wecom_sync_config` |
| 单租户 | 按 `organizationId` 取配置 |

移植时保留：

- Token 缓存逻辑  
- HTTP 客户端封装（RestTemplate 或现有 MS HTTP 工具）  
- errcode 统一校验  

---

## 5. 单元测试

使用 WireMock 或 MockServer：

- [x] gettoken 成功并缓存  
- [x] Token 过期后自动刷新  
- [x] department/list 解析正确  
- [x] user/list fetch_child=1 解析正确  
- [x] errcode 非 0 抛异常  

**测试路径**：`WecomContactClientTest.java`

---

## 6. 验收标准

- [x] `WecomContactClient` 可独立调用企微沙箱/测试企业  
- [x] 与 `notice/utils/WeComClient` 无类名/Bean 冲突  
- [x] Token 缓存有效，不会每次请求都 gettoken  
- [x] 单元测试覆盖主要 API 路径  

---

## 7. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-06 |
| 完成日期 | 2026-07-06 |
