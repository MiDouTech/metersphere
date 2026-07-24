# task003 - P0 前端自动保存 SDK 与状态条

> **阶段**：S1  
> **预估工期**：2.5–3.5 天  
> **前置依赖**：task002（可用 Mock 并行）  
> **阻塞任务**：task004–006  
> **关联方案**：§2、§3.4  
> **提测**：Demo 页 + 随试点  

---

## 1. 任务目标

提供统一前端能力：`useAutoSaveEditor`（或等价）、状态条组件、离开拦截、快捷键、网络失败本地弱兜底。

---

## 2. 任务清单

- [ ] composable：`dirty` / `saving` / `error` / `lastSavedAt`；失焦保存；防抖 **1.5～2s**  
- [ ] 包装现有保存 Promise；成功回调通知后端写快照（或由后端在 update 内完成）  
- [ ] 状态条 UI：已保存 / 保存中 / 失败可重试 / 有未保存更改 / 他人编辑中  
- [ ] `beforeRouteLeave` + 抽屉 `before-close`：失败或 dirty（若配置）拦截确认  
- [ ] 快捷键：Ctrl+S、Ctrl+Z、Ctrl+Shift+Z（仅当前激活编辑器；避免与输入框原生冲突策略文档化）  
- [ ] 加锁：进入编辑 acquire；定时 heartbeat；卸载 release  
- [ ] 无写权限：强制只读，不注册自动保存  
- [ ] LocalStorage 弱兜底：保存失败暂存；恢复后优先 flush（不能替代跨天 Undo）  
- [ ] i18n 中英文案  

---

## 3. 验收标准

- [ ] 独立 Demo 或 Story 可演示全流程  
- [ ] 无业务页时即可联调 task002  
- [ ] ESLint / 类型检查通过  

---

## 4. 任务状态

| 字段 | 值 |
|------|-----|
| 状态 | **代码已完成，待业务页接入** |
| 备注 | `useAutoSaveEditor` + `ms-auto-save-status`；试点见 task004–006 |
