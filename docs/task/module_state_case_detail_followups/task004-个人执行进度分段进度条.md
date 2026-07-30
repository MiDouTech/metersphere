# task004 个人执行进度分段进度条

## 目标

把当前详情里的个人执行进度简版 `executed/total` 进度条升级为五状态分段进度条。

## 数据

复用当前接口：

```http
GET /functional/case/personal-progress?projectId={projectId}
```

字段：

- `passed`
- `failed`
- `blocked`
- `skipped`
- `unexecuted`
- `executed`
- `total`

## UI 要求

- 通过：绿色
- 失败：红色
- 阻塞：紫色
- 跳过：灰色
- 未执行：蓝色

每段提供 tooltip：`状态：数量（占比）`。

## 无障碍要求

- 不能只用颜色表达状态。
- 进度条提供 `aria-label` 或等价可读文本。
- 色弱用户仍能通过 tooltip/文本区分状态。

## 验收

- 五种状态数量之和等于总数。
- `executed = passed + failed + blocked + skipped`。
- 空数据时展示 `0/0` 且不报错。
- 前端类型检查通过。
