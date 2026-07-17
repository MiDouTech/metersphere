# task003 - P0 执行用例与 Xmind用例拆分

> **阶段**：P0  
> **预估工期**：5–8 人日（含在线浏览）  
> **前置依赖**：无（可与 task004 并行）  
> **阻塞任务**：无  
> **关联方案**：[体验优化产品方案](../../summary/MeterSphere-体验优化-产品方案-2026-07-17.md) §3.5  
> **任务状态**：待开始

---

## 1. 任务目标

在顶栏「用例」页内增加二级 Tab：**执行用例** / **Xmind用例**。  
执行用例 = 现有功能用例能力，导入去掉 Xmind；Xmind用例 = 仅存文件资产，不自动解析为功能用例。  
**本期必做**：系统内**在线浏览**（只读）+ **下载**（产品已确认，非仅下载 MVP）。

---

## 2. 信息架构

```
顶栏「用例」
  └─ 页内二级 Tab
        ├─ 执行用例  → 现有列表/模块树/新建/导入(非 Xmind)
        └─ Xmind用例 → 上传、列表、在线浏览、下载、重命名、删除
```

默认 Tab：执行用例。切换二级 Tab 不改变顶栏选中态。

**在线浏览**：解析 `.xmind`（zip）后用只读脑图查看器渲染；可复用 `@7polo/kityminder-core` 或开源 xmind viewer。

---

## 3. 任务清单

### 3.1 前端二级 Tab 壳

| 项 | 说明 |
|----|------|
| 入口页 | `frontend/src/views/case-management/caseManagementFeature/index.vue`（或等价容器） |
| 路由 | 可用 query `?tab=execute|xmind` 或子路由；刷新保持 Tab |
| i18n | `menu.caseManagement.executeCase` / `xmindCase` |

### 3.2 执行用例

1. 迁入现有用例页全部能力（模块树、表格、新建、导出、评审结果列等）。  
2. **导入**：`components/import/*`、`exportCaseModal` 等去掉 Xmind 选项与模板入口。  
3. 后端 Xmind 导入 API **可保留**（兼容脚本），前端不可达即可。  

### 3.3 Xmind用例（文件库）

| 能力 | 说明 |
|------|------|
| 上传 | `.xmind`；记录名称、大小、上传人、时间；可选所属模块 |
| 存储 | 复用项目文件管理 / MinIO 附件能力；或新业务表 + 文件存储 |
| 列表 | 名称、更新时间；操作：打开查看 / 下载 / 重命名 / 删除 |
| 打开查看 | MVP：触发下载或新窗口提示；文案说明「请用本地 Xmind 打开」 |
| 与功能用例 | **禁止**上传后自动解析入库 |

### 3.4 后端（按需）

若无法直接复用文件管理 API，则新增：

| API | 说明 |
|-----|------|
| `POST /functional/case/xmind-file/upload` | 上传 |
| `POST /functional/case/xmind-file/page` | 分页列表 |
| `GET /functional/case/xmind-file/download/{id}` | 下载 |
| `POST /functional/case/xmind-file/rename` | 重命名 |
| `GET /functional/case/xmind-file/delete/{id}` | 删除 |

权限：对齐项目内功能用例文件/模块 `READ` / `READ+ADD` / `READ+DELETE`（实施时对照现有注解）。  
Flyway：若新表，版本号按仓库当前迁移递增；需人工审 SQL。

### 3.5 目录建议

```text
frontend/src/views/case-management/caseManagementFeature/
├── index.vue                 # 二级 Tab 容器
├── execute/                  # 原用例能力迁入或 re-export
└── xmind/
    ├── index.vue             # 列表
    └── components/UploadModal.vue
```

---

## 4. 测试用例

| 场景 | 预期 |
|------|------|
| 进入「用例」 | 默认「执行用例」；可见二级 Tab |
| 执行用例导入 | 无 Xmind 选项；Excel 仍可用 |
| 上传 xmind | 列表出现记录 |
| 下载/删除 | 成功；无权限时报错友好 |
| 上传后查功能用例列表 | **不出现**自动生成的功能用例 |

---

## 5. 验收标准

- [ ] 「用例」下可见「执行用例」「Xmind用例」  
- [ ] 执行用例导入无 Xmind  
- [ ] Xmind 可上传、列表、下载、删除（重命名如做则验收）  
- [ ] 打开查看按 MVP 定义可验收  
- [ ] 不自动解析为功能用例  

---

## 6. 交付物

- 前后端改动 + 迁移（若有）  
- 发版说明：Xmind 导入入口迁移到「Xmind用例」Tab  
- 本文件验收勾选更新  

---

**需人工审核**：文件存储路径、项目隔离、删除鉴权。  
