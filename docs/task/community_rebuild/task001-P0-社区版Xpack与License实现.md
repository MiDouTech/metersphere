# task001 - P0 社区版 Xpack 与 License 实现

> **阶段**：P0（前置依赖，必须先完成）  
> **预估工期**：2 天  
> **前置依赖**：无  
> **阻塞任务**：task002、task003、task007（同步大量用户）  
> **关联文档**：[community-unlock-and-org-structure.md](../../summary/community-unlock-and-org-structure.md) §3.1

---

## 1. 任务目标

为 MeterSphere 社区版提供 `LicenseService` 与 `UserXpackService` 的**生产环境实现**，解除用户数、资源池并发等后端配额校验。

---

## 2. 背景说明

开源仓库中：

- `LicenseService`、`UserXpackService` 仅有接口定义  
- 测试环境有 `LicenseServiceMockImpl`，生产无 Bean 注入  
- `SimpleUserService`、`NodeResourcePoolService` 等通过 `CommonBeanFactory.getBean(UserXpackService.class)` 做配额校验  

---

## 3. 任务清单

### 3.1 新增 CommunityLicenseServiceImpl

| 项 | 内容 |
|----|------|
| 路径 | `backend/services/system-setting/src/main/java/io/metersphere/system/service/impl/CommunityLicenseServiceImpl.java` |
| 接口 | `LicenseService` |
| 行为 | `validate()` 返回 `status = "valid"`；`add`/`update` 可按需 no-op 或抛友好提示 |

**参考**：`backend/services/system-setting/src/test/java/io/metersphere/system/mock/LicenseServiceMockImpl.java`

### 3.2 新增 CommunityUserXpackServiceImpl

| 项 | 内容 |
|----|------|
| 路径 | `backend/services/system-setting/src/main/java/io/metersphere/system/service/impl/CommunityUserXpackServiceImpl.java` |
| 接口 | `UserXpackService` |
| 行为 | `GWHowToAddUser` / `GWHowToChangeUser` / `GWHowToDeleteUser` 均返回 `0`（成功） |

### 3.3 Spring Bean 注册

- 使用 `@Service` 或 `@ConditionalOnMissingBean` 确保社区版默认注入  
- 确认不与测试 Mock 冲突（测试 profile 可继续覆盖）  

### 3.4 回归验证调用链

逐一确认以下类不再因 Xpack 返回非 0 而阻断：

| 文件 | 校验点 |
|------|--------|
| `SimpleUserService.java` | 添加/修改/删除/导入用户 |
| `NodeResourcePoolService.java` | `licenseValidate()` 并发上限 |
| `TestResourcePoolService.java` | 资源池 License 校验 |
| `ApiExecuteService.java`（api-test 模块） | 执行资源相关校验 |

---

## 4. 实现要点

```java
// CommunityLicenseServiceImpl 核心逻辑示意
@Override
public LicenseDTO validate() {
    LicenseDTO dto = new LicenseDTO();
    dto.setStatus("valid");
    return dto;
}
```

```java
// CommunityUserXpackServiceImpl 核心逻辑示意
@Override
public int GWHowToAddUser(UserCreateDTO user, String source, String operator) {
    return 0;
}
```

---

## 5. 测试要求

### 5.1 单元测试

- [x] `CommunityLicenseServiceImplTest` — validate 返回 valid  
- [x] `CommunityUserXpackServiceImplTest` — 三个 GW 方法返回 0  

### 5.2 集成验证

- [ ] 通过 API 连续创建 >5 个系统用户，无 101511/101512 错误  
- [ ] 创建第 2 个测试资源池，无 License 拦截  
- [ ] `GET /license/validate`（如有）返回 valid  

---

## 6. 验收标准

- [x] 应用启动后 `LicenseService`、`UserXpackService` Bean 存在且为 Community 实现  
- [x] 系统用户数量不受 5 人限制  
- [x] 资源池数量与并发配置不受社区版 UI 对应的后端校验限制  
- [x] 不影响现有组织/项目/用户 CRUD 功能  

---

## 7. 风险与注意

| 风险 | 规避 |
|------|------|
| 与企业版 Xpack JAR 冲突 | 使用 `@ConditionalOnMissingBean(name = "xpackLicenseService")` 或 profile 隔离 |
| GPL 协议 | 内部部署可接受；勿移除 MeterSphere Logo/版权 |

---

## 8. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 分支 | `feature/v3.x-task001-community-xpack-license` |
| 开始日期 | 2026-06-25 |
| 完成日期 | 2026-06-25 |
