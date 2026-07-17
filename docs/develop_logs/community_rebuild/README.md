# 开发摘要日志（develop_logs）

本目录存放 MeterSphere 自研各阶段的**开发摘要日志**，与 `docs/task/`（任务规格）和 `docs/summary/`（方案摘要）配合使用。

## 目录说明

| 子目录/文件 | 用途 |
|-------------|------|
| `YYYY-MM-DD-*.md` | 按日期或里程碑记录的开发摘要 |
| `details/` | 逐文件、逐模块的变更详细说明 |
| `buglist/` | 已修复 Bug 的现象、根因与变更记录 |
| `README.md` | 本索引 |

## 日志索引

| 日期 | 文件 | 摘要 |
|------|------|------|
| 2026-06-26 | [2026-06-26-组织架构与社区版改造开发摘要.md](2026-06-26-组织架构与社区版改造开发摘要.md) | 项目检阅、task 文档、本地开发基建、task001 Xpack/License 实现 |
| 2026-07-03 | [2026-07-03-task002-组织创建与切换API-开发摘要.md](2026-07-03-task002-组织创建与切换API-开发摘要.md) | task002：组织 add/switch/switch-option API + 项目组织编辑修复 |
| 2026-07-04 | [2026-07-04-task003-前端License解除与组织入口-开发摘要.md](2026-07-04-task003-前端License解除与组织入口-开发摘要.md) | task003：`VITE_MS_UNLIMITED`、licenseStore、组织/资源池入口 |
| 2026-07-04 | [2026-07-04-全系统缺陷修复-开发摘要.md](2026-07-04-全系统缺陷修复-开发摘要.md) | 全系统测试 7 项缺陷代码修复汇总 |
| 2026-07-06 | [2026-07-06-task004-数据模型与Flyway迁移-开发摘要.md](2026-07-06-task004-数据模型与Flyway迁移-开发摘要.md) | task004：Flyway 3.7.0、部门/同步表、user 扩展、Mapper |
| 2026-07-06 | [2026-07-06-task005-组织架构查询API-开发摘要.md](2026-07-06-task005-组织架构查询API-开发摘要.md) | task005：部门树/成员分页/详情脱敏 API |
| 2026-07-06 | [2026-07-06-task006-企微通讯录客户端-开发摘要.md](2026-07-06-task006-企微通讯录客户端-开发摘要.md) | task006：WecomContactClient、Token 缓存、MockServer 单测 |
| 2026-07-06 | [2026-07-06-task007-组织架构同步引擎-开发摘要.md](2026-07-06-task007-组织架构同步引擎-开发摘要.md) | task007：部门/用户同步引擎、失活保护、org_sync_log |
| 2026-07-06 | [2026-07-06-task008-同步API与定时任务-开发摘要.md](2026-07-06-task008-同步API与定时任务-开发摘要.md) | task008：同步 API、Quartz 定时任务、409 并发锁 |

## 代码详细说明（details/）

| 任务 | 文件 |
|------|------|
| task001 | [details/task001-社区版Xpack与License-代码详细说明.md](details/task001-社区版Xpack与License-代码详细说明.md) |
| task002 | [details/task002-组织创建与切换API-代码详细说明.md](details/task002-组织创建与切换API-代码详细说明.md) |
| task003 | [details/task003-前端License解除与组织入口-代码详细说明.md](details/task003-前端License解除与组织入口-代码详细说明.md) |
| task004 | [details/task004-数据模型与Flyway迁移-代码详细说明.md](details/task004-数据模型与Flyway迁移-代码详细说明.md) |

## 缺陷修复（buglist/）

见 [buglist/README.md](../buglist/README.md)。全系统汇总：[MeterSphere-全系统-缺陷清单-2026-07-04.md](../../task/bugs/MeterSphere-全系统-缺陷清单-2026-07-04.md)

## 编写约定

- 文件名：`YYYY-MM-DD-简短主题.md`
- 每完成一个 task 或一次合并，可追加一篇摘要或更新对应章节
- 详细任务规格见 `docs/task/task000-实施总览与依赖关系.md`
