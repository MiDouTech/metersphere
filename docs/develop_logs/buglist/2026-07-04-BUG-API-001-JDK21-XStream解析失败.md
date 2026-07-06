# BUG-API-001：JMeter XStream 在 JDK 21 下解析失败

> **日期**：2026-07-04  
> **关联任务**：全系统测试 / api-test 模块  
> **影响范围**：`MsHTTPElementTest` 等 JMX 解析单测  
> **严重程度**：P2

---

## 1. 问题现象

```
ConversionException: No converter available (java.util.IdentityHashMap)
module java.base does not "opens java.util" to unnamed module
```

---

## 2. 根因分析

JDK 21 模块系统限制反射访问 `java.util`；JMeter/XStream 解析 JMX 需 `--add-opens java.base/java.util=ALL-UNNAMED`。

---

## 3. 修复方案

**文件**：根 `pom.xml`

```xml
<argLine>--add-opens java.base/java.util=ALL-UNNAMED</argLine>
```

`backend/pom.xml` Surefire 已引用 `${argLine}`。

---

## 4. 验证

```bash
.\mvnw.cmd test -pl backend/services/api-test -Dtest=MsHTTPElementTest -DskipAntRunForJenkins=true
```

---

## 5. 备选

升级 XStream/JMeter 依赖（未采用，最小改动优先）。
