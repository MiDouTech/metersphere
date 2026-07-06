# BUG-SYS-001：单元测试 CommunityUserXpackServiceImplTest 注册场景 NPE

> **日期**：2026-07-04  
> **关联任务**：task001 回归 / M0 单元测试  
> **影响范围**：`CommunityUserXpackServiceImplTest`  
> **严重程度**：P2

---

## 1. 问题现象

```bash
.\mvnw.cmd test -pl backend/services/system-setting -Dtest=CommunityUserXpackServiceImplTest
```

`gwHowToAddUserRegisterReturnsSuccess` 失败：

```
NullPointerException: IDGenerator.DEFAULT_UID_GENERATOR is null
```

---

## 2. 根因分析

单元测试直接调用 `CommunityUserXpackServiceImpl.GWHowToAddUser(register, invite)`，内部使用 `IDGenerator.nextStr()`，测试上下文未初始化 UID 生成器。

注册用例未设置 `UserRegisterRequest.password`，触发 `CodingUtils.md5(null)` 二次失败。

---

## 3. 修复方案

**文件**：`CommunityUserXpackServiceImplTest.java`

- `MockedStatic<IDGenerator>`，`when(IDGenerator::nextStr).thenReturn("mock-user-id")`
- 注册用例设置 `registerRequest.setPassword("password123")`

---

## 4. 验证

`CommunityUserXpackServiceImplTest` 4/4 通过。

---

## 5. 关联

- task001：[task001-社区版Xpack与License-代码详细说明.md](../details/task001-社区版Xpack与License-代码详细说明.md)
- BUG001：[2026-06-26-BUG001-创建用户成功但列表不显示.md](./2026-06-26-BUG001-创建用户成功但列表不显示.md)
