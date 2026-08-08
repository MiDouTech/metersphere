# task008 - P1 - 缺陷管理页面样板改造

## 状态

未开始

## 目标

以缺陷管理为首个样板模块，验证页面可见、按钮可见、按钮可操作的完整链路，为后续模块滚动迁移提供标准实现。

## 实现范围

- 为缺陷管理路由补充 `resourceCode`。
- 为 `bug-detail-drawer.vue` 关键按钮接入新权限指令。
- 覆盖按钮：
  - 编辑
  - 分享
  - 关注
  - 复制
  - 删除
  - 评论
- 后端补齐缺失的操作权限常量或明确只做 UI 可见控制。
- 处理按钮可见但不可操作时的禁用态和提示。

## 建议资源映射

| 按钮 | resourceCode | permissionId |
| --- | --- | --- |
| 编辑 | `BUG_DETAIL_EDIT_BUTTON` | `PROJECT_BUG:READ+UPDATE` |
| 分享 | `BUG_DETAIL_SHARE_BUTTON` | `PROJECT_BUG:READ` 或 `PROJECT_BUG:READ+SHARE` |
| 关注 | `BUG_DETAIL_FOLLOW_BUTTON` | `PROJECT_BUG:READ` 或 `PROJECT_BUG:READ+FOLLOW` |
| 复制 | `BUG_DETAIL_COPY_BUTTON` | `PROJECT_BUG:READ+ADD` |
| 删除 | `BUG_DETAIL_DELETE_BUTTON` | `PROJECT_BUG:READ+DELETE` |
| 评论 | `BUG_DETAIL_COMMENT_BUTTON` | `PROJECT_BUG:READ+COMMENT` |

## 不应实现的内容

- 不在本任务迁移所有缺陷管理页面。
- 不只做前端隐藏而忽略后端接口权限。
- 不把分享、关注的权限语义模糊处理；若需要独立操作控制，应补权限常量与接口校验。

## 验收标准

- 有权限用户可正常看到并操作按钮。
- 无可见权限用户看不到对应按钮。
- 有可见但无操作权限用户看到按钮但按钮禁用。
- 直接调用无权限接口时后端返回 403。
- 缺陷详情页面在项目切换、刷新、分享链接进入时权限表现一致。

## 验证要求

- 至少准备管理员、只读用户、可见不可操作用户、完整操作用户四类账号验证。
- 前端 TypeScript 检查通过。
- 缺陷相关接口权限测试补齐。
