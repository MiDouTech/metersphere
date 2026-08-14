# task041 - P1/P2 - 权限与闭环门禁

## 任务

- [x] 业务文档详情增加项目所有权校验。
- [x] 个人中心 Token 菜单和按钮按 READ、CONNECT、REVOKE 控制。
- [x] 闭环脚本校验前后端 HTTP 方法和路径。
- [x] Controller 由已分类业务根路由自动定位，manifest 不再保存 Controller/API 文件扫描清单。
- [x] 筛选字段检查覆盖前端参数、后端 DTO 和查询层。

## 验收

- 人为制造 POST/PATCH 不一致时门禁失败。
- 新增 UI Controller 端点未分类或无页面入口时门禁失败。
- 无 CONNECT/REVOKE 权限时对应按钮不可见。
