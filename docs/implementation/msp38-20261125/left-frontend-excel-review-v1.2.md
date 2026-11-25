# MSP-38 前端与Excel组审查 v1.2

审查者质量-左殇0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0，2026-11-25。绑定patch ac692a9c172fbd37591a6059ccf96dd8416c5fd5a20f57f832afb5630fc31e50。

本轮按下表完成指定变更通路的静态检查，不代表每个大文件所有未修改业务全文审查；测试结果与静态结论分开。CR-07仍未关闭。

|文件|实际检查结果|
|---|---|
|assetSystems.ts|URL、方法及name/parentId/ids/systemId/moduleId字段与控制器匹配；重命名不发送父节点，符合服务仅改名行为。|
|SystemCaseAssets.vue|新增/编辑/删除按钮分别使用ADD/UPDATE/DELETE；分类使用UPDATE，列表每页20，切目录/搜索重置页码并清空选择；请求序号及卸载标记隔离过期列表响应。详情传原caseId及assetCatalogId，分类不复制用例。失败提示、弹窗保存失败返回false可重试。树刷新只有active标记，无序号，快速多次刷新仍可能旧响应覆盖，属待验证竞争场景，未升级为已确认缺陷。|
|cases.vue|项目/系统路由按名称切换，标签结合page/route可见性，不用标签隐藏替代服务端权限；项目原目录组件保留。|
|asset-case-detail.vue|上传allSettled等待所有请求结束后仅刷新附件，失败仍清空文件选择器；删除也仅刷新附件；不重置草稿和expectedUpdateTime。保存仍走原更新校验；从系统详情返回系统路由。独立执行草稿回归1通过，未运行完整SFC/浏览器。|
|verify-route-tabs.mjs|增加双路由和资源码存在性约束；它是字符串结构检查，不能证明运行时授权。作者API/权限/路由测试仅作为作者证据。|
|AgentCaseImportService|各工作表预处理合并范围后设置同一sheet元数据，再用原listener收集预检；没有在预检阶段新增实际用例写入。元数据读取与正式导入共用工具。预检既有通用异常输出Throwable，见诊断限制。|
|CaseStepSplitUtils|STEP精确长度编码在HTML兼容处理前识别；歧义条件包含HTML变换、前后空白、换行和编号；普通数字点避免金额小数误匹配，保留普通项目历史规则。编码序号、长度和结束标记均校验。|
|CaseAssetExcelStepMetadata|按实际行列建批注；只处理步骤/预期表头。读取核对当前格完整hash与编码重建的可见文本，修改格不套旧批注；restore仅对验证后的TEXT解码一次，字面前缀不直接解码。总元数据长度有8Mi字符限制，非导入文件15MB限制。|
|FunctionalCaseImportEventListener|restore在合并预处理前执行；显式TEXT保留原内容并置steps为空数组；合并STEP逐行解析，描述/预期按位置配对。精确编码在拆步工具内还原，未将TEXT解码逻辑泛化到普通文本。|
|FunctionalCaseFileService|非合并STEP按歧义选择编码，合并资产STEP逐行单元素编码；资产TEXT编码并在生成工作簿后转可见原文，普通项目HTML分支保留。作者解析限定到已选资产引用的用户ID，不开放人员列表。最终单xlsx或多文件zip均在注册上传前检查15,000,000字节，等于允许、大于拒绝，finally清理临时目录。发现下述新增P2。|
|FunctionalCaseExportTaskService|AssetExportSizeException单独归类EXPORT_TOO_LARGE并提供安全提示；普通异常仍沿既有Throwable日志通路，不能将超限安全日志等同全部失败脱敏完成。|

## P2 CR-07补充：人员列解析新增逐行数据库查询

位置FunctionalCaseFileService.java buildExportOtherField，第811至818行附近（authorId/userMapper.selectByPrimaryKey）。每条资产、每个创建人/更新人字段单独查询用户。选择两列并导出N条资产最多新增2N次查询，即使全部引用同一个用户也重复。导出上限100000条，对应最多200000次作者查找。

建议在已授权导出快照范围收集实际引用用户ID、批量解析并构建局部映射，或至少任务内去重缓存；不扩大到组织全员列表。验证多个资产共用作者时查询次数随唯一作者批次数增长，姓名与不可用用户空值保持。属于静态可确定N+1，不声称已压测超时。可与正在修补的CR-07一并处理，但需要新哈希复审，不能自动关闭。

## 诊断与权限剩余上下文

需要同HEAD的明确类：io.metersphere.system.security.MsPermissionAnnotationMethodInterceptor、io.metersphere.system.security.realm.LocalRealm、io.metersphere.sdk.util.LogUtils。前两者用于从注解继续追最终组织权限解析；LogUtils用于确认现有Throwable日志是否统一脱敏。

统一异常处理类名无法由已读ShiroConfig确定，不编造类名：请提供仓库中处理MSException/DataAccessException的@ControllerAdvice或@RestControllerAdvice类的路径清单及实际类，核对查询/加锁异常是否安全处理。这是具体入口检索要求，不要求重发原包或改动生产代码。若前三类转调其他处理器，再根据真实调用指出依赖，不能以猜测代替源码。

两轮HTML/合并STEP问题在v1.2变更路径中已有对应修订；真实生产生成路径测试为作者证据，本审查人未独立重跑Java。系统组加本组覆盖17项变更入口和关键通路，但最终权限/日志处理依赖尚缺，CR-07及人员N+1未关闭；结论需修改及补充，不给全量通过。没有派发测试、新建会话、修改代码、提交或推送。
