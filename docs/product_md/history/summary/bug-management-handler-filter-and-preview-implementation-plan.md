# 缺陷管理多人处理人筛选与预览能力改造方案

## 1. 文档信息

- 模块：缺陷管理
- 改造范围：多人处理人筛选、缺陷附件预览、评论图片预览
- 文档状态：待评审
- 编制日期：2026-08-24

## 2. 背景与目标

当前缺陷已经支持配置多个处理人，但处理人筛选仍按数据库字段完整值匹配。当字段保存为 `userA,userB` 时，按 `userB` 筛选无法命中该缺陷。

缺陷附件预览已经在部分页面支持 TXT、PDF、Excel 和 DOCX，但独立缺陷编辑页没有接入非图片文件预览，旧版 `.doc` 文件也没有真正的预览能力。评论区支持上传和展示图片，但不支持点击放大、切换等预览操作。

本次改造目标如下：

1. 缺陷存在多个处理人时，按任一处理人筛选均可正确命中。
2. 普通筛选、高级筛选、分配给我、关联缺陷列表和统计查询保持相同的处理人匹配语义。
3. 缺陷详情及独立编辑页面均支持 TXT、PDF、Excel、Word 文件预览。
4. 评论正文中的图片支持点击放大和多图切换。
5. 所有新增能力具备权限控制、异常反馈和自动化验证。

## 3. 当前实现评估

| 需求 | 当前实现 | 状态 |
| --- | --- | --- |
| 多处理人筛选 | 前端支持多选，后端支持多人保存和回显；SQL仍按完整字段 `IN` 或 `=` 比较 | 未实现 |
| TXT/PDF/Excel/Word 附件预览 | 详情抽屉支持多数格式；独立编辑页未接入，`.doc` 仅提示下载 | 部分实现 |
| 评论图片预览 | 支持上传和显示，未提供点击放大及预览组 | 未实现 |

## 4. 多处理人筛选改造

### 4.1 设计原则

不建议继续使用字符串模糊匹配作为长期方案。`LIKE '%user1%'` 会误命中 `user10`，`FIND_IN_SET` 难以使用有效索引，并会使统计、权限和后续人员查询持续复杂化。

推荐新增缺陷处理人关系表，保留 `bug.handle_user` 作为兼容字段，逐步将筛选和统计切换到关系表。

### 4.2 数据模型

建议新增表：

```sql
CREATE TABLE bug_handle_user_relation (
    bug_id VARCHAR(50) NOT NULL COMMENT '缺陷ID',
    project_id VARCHAR(50) NOT NULL COMMENT '项目ID',
    platform VARCHAR(50) NOT NULL COMMENT '缺陷平台',
    handle_user_id VARCHAR(255) NOT NULL COMMENT '处理人标识',
    create_time BIGINT NOT NULL COMMENT '创建时间',
    PRIMARY KEY (bug_id, handle_user_id),
    INDEX idx_project_handle_user (project_id, handle_user_id, bug_id)
) COMMENT='缺陷处理人关系表';
```

说明：

- `bug.handle_user` 暂时保留，兼容第三方插件、导出、历史接口和已有展示逻辑。
- 关系表作为筛选、待办和统计的标准数据来源。
- `platform` 用于区分本地成员标识和第三方平台账号，避免不同平台标识冲突。
- 项目代码惯例若不使用数据库外键，本表同样不增加外键，通过业务事务保证一致性。

### 4.3 写入链路

新增 `BugHandleUserRelationService`，提供统一方法：

```java
void replaceRelations(String bugId, String projectId, String platform, String rawHandleUsers);
void deleteRelations(Collection<String> bugIds);
List<String> parseHandleUserIds(String rawHandleUsers);
```

`replaceRelations` 执行步骤：

1. 兼容解析单值、JSON数组和逗号分隔字符串。
2. 清理空值并去重。
3. 生成规范化的逗号分隔值，继续写入 `bug.handle_user`。
4. 删除当前缺陷原有处理人关系。
5. 批量写入新的处理人关系。
6. 与缺陷保存处于同一事务，任一步骤失败时整体回滚。

需要接入的业务入口：

- 新建缺陷；
- 编辑缺陷；
- 批量修改处理人；
- 流程节点变更处理人；
- 第三方平台创建和同步缺陷；
- 缺陷复制；
- 缺陷删除、恢复和彻底清理。

### 4.4 历史数据迁移

迁移需要兼容：

```text
userA
userA,userB
["userA","userB"]
```

迁移流程：

1. 创建关系表和索引。
2. 将历史 `bug.handle_user` 拆分、去重后写入关系表。
3. 对比每条缺陷的解析人数与关系表记录数。
4. 输出迁移异常数据，禁止静默丢弃无法解析的值。
5. 确认迁移完成后启用关系表查询。

建议提供以下校验：

```sql
SELECT bug_id, COUNT(*)
FROM bug_handle_user_relation
GROUP BY bug_id;
```

上线初期可以保留兼容读取，但不建议长期同时使用关系表查询和字符串查询。

### 4.5 查询改造

普通筛选和高级筛选统一使用：

```sql
EXISTS (
    SELECT 1
    FROM bug_handle_user_relation relation
    WHERE relation.bug_id = b.id
      AND relation.project_id = b.project_id
      AND relation.handle_user_id IN (...)
)
```

筛选语义：

- 选择一个处理人：缺陷包含该处理人即命中。
- 选择多个处理人：默认命中任意一个已选处理人。
- `NOT_IN`：缺陷不包含任何已选处理人。
- `EMPTY`：关系表不存在该缺陷的有效处理人记录。
- `NOT_EMPTY`：关系表至少存在一条有效处理人记录。
- 分配给我：关系表中存在当前用户。

需要同步检查和改造：

- 缺陷主列表普通筛选；
- 缺陷高级筛选；
- “分配给我”工作台条件；
- 测试用例、测试计划等场景中的关联缺陷列表；
- 缺陷统计和处理人分组；
- 导出筛选；
- 批量操作中的全选条件查询。

## 5. 缺陷附件预览改造

### 5.1 通用预览组件

建议抽取：

```text
frontend/src/components/business/bug-attachment-preview/
├── index.vue
├── useAttachmentPreview.ts
└── types.ts
```

组件职责：

- 获取本地附件或文件库关联附件的鉴权字节流；
- 根据文件扩展名选择渲染器；
- 显示加载、空内容和转换失败状态；
- 统一国际化错误提示；
- 关闭弹窗及组件卸载时释放 Blob URL；
- 限制可预览文件大小，防止浏览器内存占用过高。

该组件应同时接入：

- 缺陷详情抽屉；
- 独立缺陷编辑页面；
- 后续其他缺陷附件入口。

### 5.2 文件格式处理

| 文件类型 | 实现方式 |
| --- | --- |
| TXT/LOG/CSV/JSON/XML/MD | 读取 Blob 文本并以只读文本区域展示 |
| PDF | 创建 Blob URL，通过 iframe 或统一 PDF Viewer 展示 |
| XLS/XLSX | 使用 `xlsx` 解析工作表并转换为只读 HTML 表格 |
| DOCX | 使用 `mammoth` 转换为 HTML，渲染前通过 DOMPurify 清理 |
| DOC | 后端使用 LibreOffice headless 转换为 PDF，再按 PDF 预览 |

### 5.3 `.doc` 服务端转换

旧版 `.doc` 无法在浏览器中可靠解析。若需求中的 Word 同时包含 `.doc` 和 `.docx`，必须增加服务端转换能力。

建议新增接口：

```http
POST /bug/attachment/preview/document
Content-Type: application/json

{
  "projectId": "...",
  "bugId": "...",
  "fileId": "...",
  "associated": false
}
```

接口处理流程：

1. 校验项目、缺陷和附件读取权限。
2. 获取原始附件字节流。
3. 将文件写入隔离临时目录。
4. 使用 LibreOffice headless 转换为 PDF。
5. 返回 PDF 字节流或读取转换缓存。
6. 清理临时文件。

必须增加：

- 转换超时；
- 文件大小上限；
- 并发转换限制；
- 临时目录隔离；
- 文件名和路径安全校验；
- 转换缓存，缓存键包含文件ID和文件版本；
- 转换失败时提供明确提示及下载入口；
- 后端镜像中的 LibreOffice 运行依赖与健康检查。

若当前版本不允许增加 LibreOffice，应明确一期只支持 `.docx`，不能将“提示用户下载”认定为 `.doc` 已完成预览。

### 5.4 安全和体验

- HTML预览必须使用 DOMPurify。
- PDF及转换结果只允许来自本系统生成的 Blob URL。
- 禁止将需要鉴权的内部附件地址传给外部 Office Online 服务。
- 文件加载和转换失败时显示本地化错误消息，不输出原始异常。
- 提供下载按钮作为预览失败后的兜底。
- Excel超大工作表应限制最大行列数或提示下载。

## 6. 评论图片预览改造

### 6.1 公共评论组件改造

评论正文由 `v-dompurify-html` 输出，不适合逐个通过 Vue模板绑定事件。建议在正文容器上使用事件代理：

```text
点击评论正文
    ↓
判断目标节点是否为 IMG
    ↓
收集当前评论中的全部图片
    ↓
确定当前图片索引
    ↓
打开 a-image-preview-group
```

建议给 `MsComment` 增加：

```ts
interface CommentPreviewProps {
  imagePreview?: boolean;
  resolvePreviewImage?: (src: string) => Promise<string>;
}
```

- `imagePreview` 控制是否启用图片放大。
- `resolvePreviewImage` 用于将需要鉴权的图片地址解析为可预览的 Blob URL。
- 公共评论组件不直接依赖缺陷附件接口，避免模块耦合。

### 6.2 交互要求

- 点击评论图片打开预览。
- 支持上一张、下一张、缩放、旋转和 ESC关闭。
- 父评论和回复评论行为一致。
- 图片悬停显示可点击光标。
- 图片加载失败时显示占位和错误提示。
- 关闭预览或组件卸载时释放 Blob URL。
- 图片预览不影响评论中的链接、@成员和其他富文本点击行为。

### 6.3 鉴权图片

若图片地址通过同源 Cookie 即可访问，可以直接传给预览组。

若图片请求依赖 Authorization Header，则需要：

1. 从图片地址解析附件ID。
2. 调用缺陷富文本附件预览接口获取 Blob。
3. 创建 Blob URL。
4. 将 Blob URL提供给图片预览组。
5. 在关闭或销毁时释放 Blob URL。

## 7. 权限与异常处理

### 7.1 权限

- 处理人筛选使用缺陷读取权限，不新增写权限。
- 附件预览接口必须校验 `PROJECT_BUG:READ` 和项目对象权限。
- 评论图片预览沿用评论和缺陷读取权限。
- 不得仅依赖前端按钮隐藏，后端必须继续执行权限和对象归属校验。

### 7.2 异常反馈

至少区分：

- 文件不存在；
- 无读取权限；
- 文件格式不支持；
- 文件超过预览限制；
- 文档转换失败或超时；
- 网络错误；
- 图片加载失败。

前端只展示安全、可理解的本地化消息，不展示SQL、内部路径或异常堆栈。

## 8. 自动化测试方案

### 8.1 多处理人筛选

| 场景 | 预期结果 |
| --- | --- |
| 缺陷处理人为A，筛选A | 命中 |
| 缺陷处理人为A、B，筛选A | 命中 |
| 缺陷处理人为A、B，筛选B | 命中 |
| 缺陷处理人为A、B，筛选C | 不命中 |
| 选择A、C | 按任意匹配语义命中 |
| `NOT_IN A` | 不包含A的缺陷命中 |
| 用户ID为user1，另一用户为user10 | 筛选user1不得误命中user10 |
| 分配给我 | 多处理人中包含当前用户即可命中 |
| 不同项目存在相同用户 | 只返回当前项目缺陷 |
| 批量修改处理人 | 关系表和兼容字段一致 |
| 第三方同步修改处理人 | 关系表同步更新 |

### 8.2 附件预览

准备真实样本：

- UTF-8和中文编码TXT；
- 多页PDF；
- XLS和XLSX；
- DOC和DOCX；
- 损坏文件；
- 空文件；
- 超大文件；
- 无权限文件；
- 本地上传附件；
- 文件库关联附件。

从缺陷详情抽屉和独立编辑页面分别验证预览入口、内容、失败提示和下载兜底。

### 8.3 评论图片预览

- 单图评论；
- 多图评论；
- 父评论和回复评论；
- 编辑评论后的图片；
- 需要鉴权的图片；
- 图片加载失败；
- 连续打开和关闭预览，确认没有Blob URL泄漏；
- 无评论权限但有读取权限的用户仍可预览已有图片。

## 9. 验收标准

### 9.1 处理人筛选

- 多处理人缺陷按其中任意一个处理人筛选均可命中。
- 普通筛选和高级筛选结果一致。
- 分配给我、关联列表、导出和批量操作结果与主列表一致。
- 不存在用户ID子串误匹配。
- 历史缺陷迁移后能够正常筛选。

### 9.2 附件预览

- TXT、PDF、XLS、XLSX、DOC和DOCX均有可达的预览入口。
- 详情抽屉和独立编辑页面行为一致。
- `.doc` 能够转换并显示，而不是只提示下载。
- 无权限、损坏和超大文件均显示明确错误。
- 预览失败时仍可以下载原文件。

### 9.3 评论图片预览

- 评论正文图片可点击放大。
- 多图可前后切换。
- 父评论和回复评论均支持。
- 鉴权图片可以正常显示。
- 关闭预览后不残留失效的 Blob URL。

## 10. 建议实施顺序

1. 新增处理人关系表和历史数据迁移。
2. 接入所有处理人写入入口并改造主列表筛选。
3. 改造分配给我、关联列表、统计、导出和批量操作查询。
4. 抽取通用附件预览组件并接入详情抽屉和独立编辑页。
5. 增加 `.doc` 服务端转换和运行依赖。
6. 改造公共评论组件的图片预览。
7. 完成后端集成测试、前端组件测试及Playwright端到端验证。

## 11. 发布与回滚

### 11.1 发布步骤

1. 备份缺陷表及相关数据。
2. 执行关系表DDL和历史数据迁移。
3. 校验迁移结果。
4. 发布后端双写和关系表查询代码。
5. 发布前端预览组件。
6. 如支持 `.doc`，发布包含LibreOffice的后端镜像。
7. 执行冒烟测试并观察筛选耗时、转换失败率和附件接口错误率。

### 11.2 回滚策略

- 保留 `bug.handle_user`，后端可临时回退到原查询逻辑。
- 关系表在回滚期间保留，不立即删除，避免重新迁移。
- 文档转换接口可通过配置关闭，前端回退为下载提示。
- 评论预览为纯展示能力，可独立回滚，不影响评论数据。

## 12. 预计影响文件

后端主要影响：

```text
backend/framework/domain/src/main/resources/migration/
backend/services/bug-management/src/main/java/io/metersphere/bug/service/
backend/services/bug-management/src/main/java/io/metersphere/bug/mapper/
backend/services/bug-management/src/main/java/io/metersphere/bug/controller/
backend/services/bug-management/src/test/java/io/metersphere/bug/
```

前端主要影响：

```text
frontend/src/views/bug-management/index.vue
frontend/src/views/bug-management/edit.vue
frontend/src/views/bug-management/components/bugDetailTab.vue
frontend/src/views/bug-management/components/commentTab.vue
frontend/src/components/business/ms-comment/
frontend/src/components/business/bug-attachment-preview/
```
