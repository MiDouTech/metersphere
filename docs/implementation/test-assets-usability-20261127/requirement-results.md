# 需求追踪结果 v1.2（送审开发记录，2026-11-27）

以下为当前差异的实现与开发验证结果。真实环境已部署的是此前版本；本补丁的新容器健康、SSO和正式验收需用户合并部署后执行。当前身份与平台外壳为明确夹具的浏览器证据，不替代正式授权环境。

|需求|前端入口/实现|真实后端/方法|数据或配置|开发验证|
|---|---|---|---|---|
|1 单文件30MB、100000行|datasets上传/limits提示；详情分页|DataAssetLimits/Parser、ParseJobService.upload/drain、DataAssetJson；LargeAssetRequestConfig.Converter|既有multipart1024MB、Nginx1000m足够；JSON内容63MiB、HTTP64MiB局部预算|真实MinIO2项、HTTP容量5项、RecordService6项；页面上传30MiB及十万行，完整读取/HTTP编辑/复制hash一致|
|2 去除全局资产span|datasets、data-detail|无新增API|无|真实SFC及browser-evidence.detail.noGlobal|
|3 旧数据迁移并兼容|进入datasets自动推进、明确失败重试；移除旧页切换|LegacyAutoController.status/run、LegacyAutoService、LegacyService.importFile|166持久迁移任务；沿用来源映射/版本表|授权来源、并发领取/恢复、新来源版本、人工当前保护；U-04目标行锁/局部RC与外层RR冲突回滚，真实MCP回执/重放/撤权及网页auto共12并发场景；页面两版本100.50/205.75及原文件内容一致|
|4 批量删除页面弹窗|datasets deleteVisible Modal|DataAssetController.batchBody、BatchService.submit/get|沿用批次表/逐行事务|Modal取消、确认后真实SUCCESS及条目消失；batch-delete-modal.png|
|5 更多操作文字下拉|datasets a-dropdown|复用发布/任务/关联/历史/源文件接口|无|实际展开五项；窄窗滚动可见常驻操作|
|6 上传存储错误|datasets安全提示与解析状态|Service/ParseJob按organization前缀写入；ExceptionHandler取消错误双包装|保留仓库路径校验，不全局放宽|原环境HTTP503/trace已复现；真实MinioRepository+MinIO上传下载、解析与30MiB页面全链路通过|
|7 左树、管理/加号、默认分类|category-header、快速创建、既有管理抽屉|Service.categories、Governance.create/update/delete/reorder|旧null/空/虚拟ID归一化，默认不可改删/作为父节点|快捷创建/管理可见；默认不在可编辑树；后端防绕过及独立分类权限四组合|
|8 顶部按钮样式|平台primary/secondary、间距、wrap|无新增API|复用主题变量|normal/1004截图、scoped lint/style；表格固定列宽与内部横向wheel|
|9 详情返回、分隔线及清理|data-detail `<返回列表`、`|`；移除标签/替换入口|保留已有版本后端兼容|无|data-content.png；源码/模板回归及真实页面|
|10 内容/基本信息两tab|默认content；元信息/来源/项目引用归info|detail/versions元信息；records取全量分页|不再为详情默认拉整份content|首次默认内容、切基本信息与历史版本均实测|
|11 复制、复选、使用状态、允许再次复制|DataRecordContent.copy/retryMark/load及仅ID outbox|RecordController.page/copy/used、RecordService；Native/Node同能力|167投影/使用/请求表；按资产共享，内容hash+重复序号稳定key|单/批复制、重复复制、刷新/元信息版本保留、剪贴板失败不标、标记失败重试；剪贴板成功晚到的原归属逐请求outbox恢复；MySQL并发/幂等；30MiB页面实际复制后旧新版本均used|
|12 逗号字段、换行记录|逐条完整内容显示与表格编辑|Parser、Limits.textRecords、RecordService格式投影/CSV转义|无静默截断；JSON业务对象整项|中英文逗号、引号、CRLF/LF、空行/重复值、普通token内引号、大字段三格式往返；末行带引号空字段EOF及复制再导入、100001空字段尾记录拒绝|
|用例1 系统目录读取失败|cases仅当前视角加载；SystemCaseAssets隔离旧组织/请求；datasets同类过期错误也修复|原case/page接口沿用|不改全局Axios取消规则|真实部署抓到PROJECT取消SYSTEM；SYSTEM刷新1342成功；生命周期SFC回归和30项MySQL（含用例18项）|
|用例2 UI超出范围|cases/catalog/System局部MsCard auto-width、min-width0及表内scroll|无新增API|不改全局MsCard|1004视口文档宽1004，内容right947≤卡片right952；system-cases-1004.png仅布局验证|
|版本 无额外页面权限|versions路由/pathMap；一个无可读来源空态|PermissionUiService.aggregate开放登录用户导航，内容仍原权限|无新Token Scope、原资源归属保留|无角色导航投影、data-only和no-access模板、权限资源覆盖通过|

额外闭环：MCP读取不误要求写键；写入的幂等、开关、撤权/组织/Token白名单、回执映射及批次部分成功已修复并验证。统一回归327项中30条件测试已全部在隔离MySQL实际补跑；详细命令、运行哈希与边界见delivery-evidence.md及validation-summary.json。原MCP/Node有效Token无需重新配置Scope。

改动文件逐项见changed-files.md。独立审查以本次送审包完整patch及manifest为准；本文不预写审查通过或线上验收结论。
