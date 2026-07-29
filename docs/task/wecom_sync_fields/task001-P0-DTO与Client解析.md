# task001 - P0 DTO 与 Client 解析

> **阶段**：P0  
> **预估工期**：0.5 人日  
> **前置依赖**：无  
> **阻塞任务**：task002、task004、task005  
> **关联总览**：[task000](task000-实施总览与依赖关系.md)  
> **关联方案**：§5.1  
> **状态**：✅ 已完成（2026-07-29）

---

## 1. 任务目标

让企微 `user/list`（及详情）响应中的 `biz_mail`、`main_department` 能正确反序列化进 `WecomUserDTO`，并为后续 Handler 提供可测契约。

---

## 2. 现状分析

| 项 | 说明 |
|----|------|
| DTO | `WecomUserDTO` 仅有 `userid/name/mobile/email/position/department/status` |
| 解析 | Jackson 默认按字段名映射；`biz_mail` / `main_department` **必须** `@JsonProperty` |
| 单测 | `WecomContactClientTest.listDepartmentUsers_*` fixture 未覆盖新字段 |

---

## 3. 任务清单

### 3.1 DTO

- [x] `WecomUserDTO` 新增：
  - `@JsonProperty("biz_mail") String bizMail`
  - `@JsonProperty("main_department") Long mainDepartment`
- [x] 保留既有字段不变；不引入无关字段（gender/avatar 本期不做）

### 3.2 Client 单测 fixture

- [x] 仅有 `biz_mail`、无 `email`
- [x] 同时有 `email` + `biz_mail`
- [x] 有 `main_department`（可与 `department` 列表并存）
- [x] `mobile` 缺失（字段不存在或空串）
- [x] 断言反序列化后 Java 字段值正确

### 3.3 回归

- [x] 原有 `listDepartmentUsers_fetchChildParseCorrectly` 仍通过  
- [x] 确认 `user/list` 与（若有）`user/get` 共用同一 DTO 时新字段均可解析  

---

## 4. 涉及文件

| 文件 | 改动 |
|------|------|
| `.../dto/wecom/WecomUserDTO.java` | 增字段 + JsonProperty |
| `.../wecom/WecomContactClientTest.java` | fixture / 断言 |

---

## 5. 测试用例

| 场景 | 预期 |
|------|------|
| JSON 仅 `biz_mail` | `getBizMail()` 有值，`getEmail()` 空 |
| JSON 同时有 email/biz_mail | 两字段均有值（优先级在 task002） |
| JSON 有 `main_department` | `getMainDepartment()` = 对应 Long |
| JSON 无 mobile | `getMobile()` 为 null/空，不抛错 |

---

## 6. 验收标准

- [x] 新字段可从企微 JSON 正确映射  
- [x] Client 相关单测通过  
- [x] 不改变现有 Token / 列表 API 行为  
