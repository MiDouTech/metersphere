# MeterSphere-Spring循环依赖-排障与防再发-2026-07-24

> **文档类型**：事故/排障归档  
> **适用项目**：MeterSphere（Spring Boot 启动 / 枢纽同步 / Service 互注）  
> **编写日期**：2026-07-24  
> **关联提交**：`655892adaa`  
> **关联规则**：`.cursor/rules/spring-circular-dependency.mdc`  
> **标注**：【AI生成】已人工按容器日志与代码改动核对；部署结果以现场容器 health / 启动日志为准  

> 同日相关：Flyway 卡死见 `docs/summary/MeterSphere-Flyway迁移故障-排障与防再发-2026-07-24.md`。  
> **本次 502 为连环故障**：先 Flyway `3.7.2.10` success=0 → 修复后仍 502 → 实锤为 Bean 循环依赖。

---

## 1. 本次现象

| 项 | 内容 |
|----|------|
| 前端 | 登录页 Toast「网络错误」；接口 **502**（nginx 无可用上游） |
| Flyway | 已恢复：`Schema metersphere is up to date`，版本到 `3.7.2.12` |
| 容器 | 短暂 `health: starting` 后进程退出 / 无法变 healthy |
| 关键报错 | `APPLICATION FAILED TO START` + **circular references** |

日志特征（节选）：

```text
Error creating bean with name 'environmentController': Injection of resource dependencies failed
...
The dependencies of some of the beans in the application context form a cycle:

   environmentController → environmentService → projectService
     → commonProjectService → projectServiceInvoker → cleanupPlanResourceService
┌─────┐
|  testPlanService
↑     ↓
|  defaultHubPlanSyncService
└─────┘

Action:
Relying upon circular references is discouraged and they are prohibited by default.
... setting spring.main.allow-circular-references to true.
```

> 注意：大量 `BeanPostProcessorChecker` WARN（Shiro/MyBatis/Flyway 等）为既有噪音，**不是**本次根因。  
> `metersphere.properties not resolvable` 在部分镜像启动路径下也可能出现，与本次失败无关。

---

## 2. 根因

枢纽同步引入后，业务 Service 与 Hub Sync Service **双向 `@Resource` 注入**：

| Bean | 依赖 | 用途 |
|------|------|------|
| `TestPlanService` | `DefaultHubPlanSyncService` | 计划增删改后镜像同步 |
| `DefaultHubPlanSyncService` | `TestPlanService` | 枢纽侧创建/更新计划时复用业务逻辑 |

Spring Boot 3 默认 **禁止** 循环引用 → 上下文刷新失败 → 进程退出 → 网关 **502**。

同类已有 precedent（应用 `@Lazy` 打断）：

- `DefaultHubCaseSyncService` ↔ 用例相关 Service  
- `*ResourceEditAdapter` ↔ 对应业务 Service（自动保存）

本次遗漏：测试计划枢纽同步未按同样模式加 `@Lazy`。

---

## 3. 修复（已合入）

### 3.1 双向互注（第一次）

提交：`655892adaa` — `fix(hub): break circular dependency between TestPlanService and hub sync`

在**环的两侧**使用延迟注入：

```java
@Lazy
@Resource
private DefaultHubPlanSyncService defaultHubPlanSyncService;  // in TestPlanService

@Lazy
@Resource
private TestPlanService testPlanService;  // in DefaultHubPlanSyncService
```

### 3.2 自注入 self（第二次，同日）

`DefaultHubPlanImportService` / `DefaultHubSyncJobService` / `DefaultHubCaseImportService`：

```java
@Lazy
@Resource
private DefaultHubPlanImportService self;  // for @Async proxy
```

并对 Import → `TestPlanService` 同样 `@Lazy`。

**不要**首选打开 `spring.main.allow-circular-references=true`（掩盖设计问题，后续环会更多）。

---

## 4. 排障流程（前端 502 / 后端起不来）

按顺序，避免「只盯业务代码」：

```text
1) 看容器日志末尾是否 APPLICATION FAILED TO START
2) 若有 Flyway / metersphere_version.success=0 → 走 Flyway 归档文档
3) 若 Flyway 已 up to date，搜 circular / form a cycle
4) 按日志画出 A ↔ B（及调用链上的 Invoker/Cleanup）
5) 优先 @Lazy / 事件解耦 / 抽中间 Facade；禁止全局 allow-circular-references
6) 本地或灰度验证：启动无 cycle 报错 → health healthy → 登录接口非 502
```

堡垒机快速确认：

```bash
# 容器是否 healthy
docker ps | grep -i metersphere

# 启动失败关键字
docker logs <container> 2>&1 | grep -E 'FAILED TO START|form a cycle|Flyway|success=0' | tail -50
```

---

## 5. 编码防再发清单（写代码时）

新增 **Hub Sync / Cleanup / Invoker / Adapter** 与核心业务 Service 互相调用时：

1. **画依赖**：是否出现 `BizService` ↔ `XxxSyncService` / `XxxAdapter`？有环必须先打断。  
2. **默认 `@Lazy`**：Hub/适配器注入业务 Service，或业务 Service 注入 Hub/适配器——至少一侧 `@Lazy`（本仓库惯例两侧都加更稳）。  
3. **对照先例**：改 `*Hub*SyncService` 前，搜现有 `@Lazy`（用例枢纽、ResourceEditAdapter）。  
4. **禁止**靠 `allow-circular-references=true`「先跑起来」。  
5. **合并前**：至少跑一次后端启动（或部署冒烟），确认无 `form a cycle`。  
6. **可选解耦**（环复杂时）：ApplicationEvent、专用 Facade、把「只写库」逻辑下沉到无环的小 Service。

### 高风险改动面

- `backend/services/**/hub/service/*SyncService.java` / `*ImportService.java`
- 包内 `private XxxService self`（`@Async` 自调用）——**必须 `@Lazy`**
- `*ServiceInvoker`、`Cleanup*ResourceService`
- 业务大 Service（`TestPlanService` / `FunctionalCaseService` / `BugService`）新增对 Hub/Adapter 的注入

### 自注入踩坑（2026-07-24 二次实锤）

日志仅显示：

```text
testPlanController
┌─────┐
|  defaultHubPlanImportService
└─────┘
```

根因：`DefaultHubPlanImportService` 注入自身 `self` 以调用 `@Async`，未加 `@Lazy`。  
同包还有 `DefaultHubSyncJobService.self`、`DefaultHubCaseImportService.self`，一并加 `@Lazy` 防再炸。

---

## 6. 验收标准

| 检查项 | 期望 |
|--------|------|
| 启动日志 | 无 `form a cycle` / `APPLICATION FAILED TO START` |
| Flyway | `up to date` 或迁移全部 `success=1` |
| 容器 | `health: healthy` |
| 前端 | 登录/首页接口非 502 |

---

## 7. 经验一句话

> **502 + 进程起不来**：先分清 Flyway 卡死 vs Spring 循环依赖；枢纽/适配器与业务 Service 互注时，**编码阶段就必须 `@Lazy` 或解耦**，不要等灰度爆 502。
