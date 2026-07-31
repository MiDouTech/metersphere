# task006 - P0 全链路验收与防退化

## 目标

为模块状态保持、测试用例详情聚合、缺陷管理状态修复、测试计划关联用例对齐建立可重复验收方案，防止后续迭代回退。

## 范围

- 前端类型检查
- 后端相关模块编译
- 关键页面手工验收脚本
- 可选 Playwright 自动化
- Flyway 迁移检查

## 验收场景

1. 测试用例模块：
   - 树展开、筛选、排序、分页、滚动后切换模块再返回。
   - 打开用例详情后切换页面再返回，详情保持原状。
   - TEXT 模式提缺陷并确认上下文带入正确。
2. 缺陷管理：
   - 有数据时切换模块再返回，列表不空白。
   - 打开详情后切换其他模块，详情关闭。
3. 测试计划：
   - 关联用例字段和测试用例模块一致。
   - 最后执行时间排序正确。
4. 详情聚合：
   - 评审样式、测试计划进度、个人进度、进度条 hover 均正确。
5. 数据库：
   - Flyway 脚本版本不冲突。
   - 重复部署无失败迁移记录。

## 建议命令

```powershell
cd frontend
pnpm.cmd type:check
```

```powershell
.\mvnw.cmd -pl backend/services/case-management,backend/services/test-plan,backend/services/bug-management -am -DskipTests compile
```

```powershell
git diff --check
```

## 交付物

- 验收记录
- 问题清单
- 回归脚本或手工步骤
- 未覆盖原因说明
