# 缺陷 MCP 图片读取

开发基线：MSP-14 / 已批准 MSP-11 完整产品方案 v1.2，文档 `862b3038-5678-44eb-9813-a884fc81cebb`，SHA256 `7D9FF9D6F3CE45D95036E00DA242AE4760C20BE2D46CCBBA91EBB68D76DB029A`。本文为实现使用说明，不替代批准全文或部署验收。

## 使用

1. 调用原生 `metersphere.bug.get`（`bugId`）或 stdio `get_bug`。原业务字段保留，新增 `images`、`imagesComplete`、`attachmentImages`、`attachmentImagesComplete` 和 `imageWarnings`。
2. 正文 `images` 按 DOM 顺序从 1 编号，保留重复出现位置及 `alt`；真实附件单列 `attachmentImages`，按关联创建时间、关联 ID 排序。`RICH_TEXT` 本身不算附件来源。无图返回空数组，不完整索引伴随完整性标志和安全警告。
3. 从对应位置取得 `imageRef`，显式提供 `projectId`、`bugId`，调用原生 `metersphere.bug.image.read` 或 stdio `read_bug_image`。三个参数均必填；不接受 URL、路径、文件名及其他额外字段。
4. 成功返回一项 MCP `image` content 和一项 JSON `text` metadata；原生还返回 `structuredContent.result`。metadata 包含 `imageRef`、`bugId`、`mimeType`、`sizeBytes`、`sha256`。Base64 解码后为原始文件字节，无转码、缩放或压缩。

```json
{"projectId":"项目内部ID","bugId":"缺陷内部ID","imageRef":"从当前详情原样复制"}
```

`imageRef` 是服务端生成的版本化受控引用（当前格式 `bi1_` 加两段 64 位十六进制摘要，共 133 字符），调用方不应构造或拆分。引用关联项目、缺陷、来源、文件及业务版本；附件另绑真实关联；存储版本摘要用于阻止旧引用读取替换后的字节。正文同一版本文件重复出现可共用引用；正文与附件引用不同。移除一处重复正文不使其余位置失效；移除全部正文、解绑附件、替换版本分别使相应引用失效。任何引用都不代替当前授权。

若索引阶段无法取得存储版本，条目保留位置，明确 `readable=false`、`reason`、`retryable` 和 `nextAction=REFRESH_BUG`。该占位引用从未绑定图像版本，恢复后也不能直接读取任意新版本；先刷新详情取得新绑定引用，再读取图片。暂时故障为 `STORAGE_UNAVAILABLE`，实际缺失为 `RESOURCE_UNAVAILABLE`，不再用全零摘要冒充有效存储版本。

## 权限与范围

每次索引/读取都重新检查有效 Token、明确 `BUG_READ`、Token 项目范围、当前用户 `PROJECT_BUG_READ`、当前缺陷项目及未删除状态。`BUG_WRITE`/`BUG_ATTACHMENT`/单独 `AGENT_ALL` 不替代新增能力明确要求的 `BUG_READ`。文件库关联按缺陷读取权限授权，不额外要求独立库浏览权限，也不提供库浏览功能。跨缺陷、跨项目或不存在的资源不返回受保护元信息。

仅支持原始字节非空、最大 **5,242,880** 字节、宽高为正且乘积最大 **20,000,000** 的可解码单帧 PNG/JPEG；等值允许。格式取自内容，不信任扩展名或声明 MIME。APNG/多帧、不支持格式、损坏文件不自动转换。正文外链、blob/data 临时图、缺关联图保留位置及安全原因，不抓取 URL。附件可通过 16 字节文件头识别被改扩展名的 PNG/JPEG；索引不全量读取或解码图片。

读取先取服务端记录和存储版本，再以最多上限加一字节读取原图，检查格式、帧数、尺寸和解码，计算原字节哈希。最终检查在独立 SERIALIZABLE 事务中对当前 Token、用户/角色/授权、项目/缺陷、正文/附件关联及选中文件记录持 InnoDB 共享记录锁及 next-key 范围锁（包括空集合/插入间隙），清除旧 ORM 会话缓存后重新验证授权和来源；同一事务内最终 ORM SELECT 也是加锁的当前读取，不能采用未保护的新授权或来源。随后再对最终选定的存储请求检查代际。最后一次存储代际检查是成功读取的判定点；相关数据库撤权/删除/解绑在锁释放前不能提交，之后生效的变化影响后续请求。读操作不新增缺陷、附件、文件版本或业务操作历史。

为识别同字节覆盖，平台 SDK 在缺陷/文件库私有存储域的三个保存入口以及复制/删除协调使用内部存储代际：MinIO 每次 PUT/COPY 随对象原子保存独立 `ms-object-generation` 用户元数据，复制保留原有用户元数据和内容头；LOCAL 使用持久化 WRITING/COMMITTED 代际、原子发布及 JVM/OS 写锁，写入中不认证图片版本。LOCAL 元数据保存在对象目录的 `.ms-object-generations` 私有目录。旧对象可按原标识读取，读请求不为旧对象补写元数据，下一次受控写入产生新代际。Git 仍绑定不可变提交/路径。没有新增业务写入入口、权限或表迁移，也不改变图片字节。

部署需确保所有平台存储写节点使用同版 SDK，再启用新读取能力；混合旧写节点、绕过平台直接修改存储或私有代际元数据不受此协调协议保证。真实部署须核查写入路径、共享卷锁和原子移动支持，不以单元测试宣称所有部署配置均已验证。存储超时期间的锁等待/死锁和跨节点兼容仍需联测。

## 失败、兼容和回退

安全错误含 `code`、`traceId` 及 `details.retryable` / `details.nextAction`。单图错误不影响另图或文字。只有暂时存储故障可重试：`BUG_IMAGE_STORAGE_UNAVAILABLE` 要求显式重试图片，`BUG_IMAGE_INDEX_STORAGE_UNAVAILABLE` 要求先刷新索引（`REFRESH_BUG`），stdio 不自动重传或自动刷新。`BUG_IMAGE_REFERENCE_EXPIRED` 要求重新获取详情。其他类别包括 `INVALID_ARGUMENT`、`RESOURCE_UNAVAILABLE`、`UNSAVED_IMAGE`、`EXTERNAL_IMAGE`、`UNSUPPORTED_FORMAT`、`EMPTY_FILE`、`SIZE_LIMIT`、`PIXEL_LIMIT`、`CORRUPT_IMAGE`（均带 `BUG_IMAGE_` 前缀）；权限拒绝沿用 `AGENT_RESOURCE_NOT_ACCESSIBLE`。无凭据、外链地址或存储路径进入新增错误信息。

stdio 包 `0.8.3` 注册 `read_bug_image`，保持原生 content、metadata 和安全错误详情。旧服务器缺工具或端点时明确返回 `BUG_IMAGE_UNSUPPORTED`，不抓 URL 兜底；旧文字工具仍可调用。升级后重连并刷新 `tools/list`。未测试宿主不宣称兼容。

配置 `agent.bug-images.enabled=false` 并重启可关闭新增索引及原生工具，恢复原详情路径；不会删除或修改文字、图片及业务版本。客户端继续提供工具入口，但服务端缺能力时明确提示不支持。

关闭读取开关不删除内部存储代际。若回退至不带代际协议的旧 SDK，应先关闭新读取能力；重新启用前核查所有写节点及存储一致性。这里不执行生产配置变更，也不自动发布。

## 验证边界

仓库单元测试覆盖内容验证、来源生命周期、权限优先、读取期间变更、字节上限、协议封装和真实 stdio 进程。测试夹具不代表真实数据库、MinIO/Git、线上 Token/RBAC 链或实际宿主已验收；部署后须按批准 AC-01～12完成联测及支持 image content 的宿主可视冒烟。

JPEG 宽高为 16 位无符号数，20,000,001 像素不存在两个均不超过 65,535 的整数因子。因此 PNG 测精确 N+1；JPEG 测 N 及最近可表示的超限矩形，并记录此夹具事实，不改变 20,000,000 像素的产品限制。
