# task001 - P0 agent-integration 模块脚手架

> **阶段**：P0  
> **预估工期**：0.5 天  
> **前置依赖**：无  
> **阻塞任务**：task002、task003、task004、task008  
> **关联方案**：[改造方案](../../summary/MeterSphere-Agent集成-改造方案-2026-07-07.md) §4.1

---

## 1. 任务目标

新建 `backend/services/agent-integration` Maven 模块，完成目录骨架与依赖配置，确保 `backend/app` 可引入并编译通过。

---

## 2. 任务清单

### 2.1 新建模块目录

```
backend/services/agent-integration/
├── pom.xml
└── src/main/java/io/metersphere/agent/
    ├── controller/
    ├── service/
    ├── dto/
    ├── resolver/
    ├── mapper/
    └── security/
```

### 2.2 `agent-integration/pom.xml`

```xml
<artifactId>metersphere-agent-integration</artifactId>
<dependencies>
    <dependency>
        <groupId>io.metersphere</groupId>
        <artifactId>metersphere-sdk</artifactId>
    </dependency>
    <dependency>
        <groupId>io.metersphere</groupId>
        <artifactId>metersphere-case-management</artifactId>
    </dependency>
    <dependency>
        <groupId>io.metersphere</groupId>
        <artifactId>metersphere-test-plan</artifactId>
    </dependency>
    <dependency>
        <groupId>io.metersphere</groupId>
        <artifactId>metersphere-system-setting</artifactId>
    </dependency>
</dependencies>
```

### 2.3 父 POM 注册

**`backend/services/pom.xml`** 增加：

```xml
<module>agent-integration</module>
```

### 2.4 app 模块依赖

**`backend/app/pom.xml`** 增加：

```xml
<dependency>
    <groupId>io.metersphere</groupId>
    <artifactId>metersphere-agent-integration</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2.5 Spring 组件扫描

确认 `Application.java` 或现有 `@ComponentScan` 覆盖 `io.metersphere.agent` 包（通常 `io.metersphere` 根包已扫描）。

### 2.6 占位 Controller（可选）

创建空 `AgentFunctionalCaseController` 占位，验证路由注册：

```java
@RestController
@RequestMapping("/api/agent/v1/functional")
public class AgentFunctionalCaseController {
    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
```

---

## 3. 验收标准

- [x] `mvn compile -pl backend/services/agent-integration -am` 通过  
- [x] `mvn compile -pl backend/app -am` 通过  
- [x] `GET /api/agent/v1/functional/health` 接口已实现（`AgentFunctionalCaseController`）  
- [x] 不影响现有模块编译  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | 已完成 |
| 开始日期 | 2026-07-07 |
| 完成日期 | 2026-07-07 |
