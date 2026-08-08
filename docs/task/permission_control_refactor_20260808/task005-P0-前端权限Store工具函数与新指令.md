# task005 - P0 - 前端权限 Store、工具函数与新指令

## 状态

未开始

## 目标

在前端引入 UI 权限判断能力，支持页面可见、按钮可见、按钮可操作三类判断，并保留现有 `v-permission` 兼容旧页面。

## 实现范围

- 扩展用户 Store 类型，保存 `uiPermissions`。
- 扩展 `frontend/src/utils/permission.ts`：
  - `hasPageVisible(resourceCode, typeList?)`
  - `hasButtonVisible(resourceCode, typeList?)`
  - `hasButtonOperable(resourceCode, permissions?, typeList?)`
  - `hasUiVisible(resourceCode, typeList?)`
  - `hasUiOperable(resourceCode, typeList?)`
- 新增指令：
  - `v-visible-permission`
  - `v-operable-permission`
- 保留 `v-permission` 原行为。

## 指令行为

### `v-visible-permission`

不可见时移除 DOM。

```vue
<MsButton v-visible-permission="'BUG_DETAIL_EDIT_BUTTON'" />
```

### `v-operable-permission`

不可操作时禁用按钮，并提示无操作权限。

```vue
<MsButton
  v-operable-permission="{ code: 'BUG_DETAIL_EDIT_BUTTON', permissions: ['PROJECT_BUG:READ+UPDATE'] }"
/>
```

## 不应实现的内容

- 不修改现有业务按钮逻辑。
- 不强制旧页面立即切换到新指令。
- 不在前端绕过后端 403。

## 验收标准

- 管理员调用所有新增工具函数均返回 true。
- 普通用户可按 UI 权限集合控制显示和禁用。
- `v-permission` 旧页面行为不变。
- TypeScript 类型检查通过。

## 验证要求

- 工具函数单测。
- 指令 mounted/updated 行为验证。
- 真实页面中验证按钮可见但禁用的交互。
