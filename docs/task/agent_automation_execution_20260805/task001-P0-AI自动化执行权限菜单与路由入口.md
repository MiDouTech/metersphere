# task001 - P0 - AI 自动化执行权限、菜单与路由入口

## 状态

已实现，待环境联调验证

## 执行记录（2026-08-05）

- 已新增 `AI_EXECUTION:READ/RUN/CANCEL/LOGIN/ADMIN` 后端权限常量与 3.7.2 角色授权迁移。
- 已新增缺陷管理下 `/bug-management/automation-execution` 前端路由、菜单映射和中英文文案。
- 已将缺陷管理父路由权限改为包含 `AI_EXECUTION:READ`，避免仅依赖 `PROJECT_BUG:READ`。
- 已新增自动化执行占位工作台页，可按 `executionTaskId` 查询任务。
- 已验证：后端 `agent-integration` 相关模块编译通过，前端 `vue-tsc --noEmit --skipLibCheck` 通过。
- 未完成验证：尚未用真实无权限账号验证菜单不可见/直连拦截；需部署后联调确认。

## 目标

在缺陷管理一级导航下新增【自动化执行】入口和独立路由，为后续执行工作台、任务详情和日志查看提供基础页面入口。

## 实现范围

- 新增前端路由：`/bug-management/automation-execution`。
- 在缺陷管理一级导航下新增【自动化执行】菜单项。
- 新增权限点：
  - `AI_EXECUTION:READ`
  - `AI_EXECUTION:RUN`
  - `AI_EXECUTION:CANCEL`
  - `AI_EXECUTION:LOGIN`
  - `AI_EXECUTION:ADMIN`
- 接入现有项目切换、租户/组织隔离和权限拦截逻辑。
- 补充中英文国际化文案。
- 后端权限枚举、菜单权限初始化和角色授权配置需同步补齐。

## 不应实现的内容

- 不在本任务中实现 AI 编排逻辑。
- 不在本任务中实现 Runner 或浏览器接管。
- 不复用 `PROJECT_BUG:READ` 作为最终权限口径。

## 验收标准

- 有权限用户可在缺陷管理下看到【自动化执行】入口。
- 无 `AI_EXECUTION:READ` 权限用户不可见入口，直接访问路由也被拦截。
- 路由刷新、项目切换、直接链接访问均能保持正确项目上下文。
- 权限点可被角色管理或权限初始化逻辑识别。

## 验证要求

- 前端路由和菜单显示验证。
- 无权限账号访问验证。
- 项目切换与刷新验证。
- 权限初始化脚本或迁移验证。
