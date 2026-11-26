# MSP-38 开发计划与需求追踪（开发草稿）

基线：155818422f；交接材料v1.0（20261125），已读取在线任务全文。用户于当前聊天授权继续完成开发。

|需求|前端入口/实现|后端|数据配置|验证|
|---|---|---|---|---|
|附件保留草稿|asset-case-detail.vue 上传/删除|CaseAssetController → CaseAssetService → FunctionalCaseAttachmentService|无迁移|草稿、失败、保存与并发|
|Excel往返|用例资产导出/导入|FunctionalCaseFileService → FunctionalCaseImportEventListener → CaseStepSplitUtils|无迁移|真实Excel、金额、编号、多步、空预期|
|人员导出|资产导出人员列|buildExportOtherField及人员转换器|不扩大用户可见范围|源创建/更新人员、不可用人员|
|目录规则|统一目录及system兼容路由|目录服务|核准现行产品依据|产品v1.0与control-inventory对照|
|正式权限诊断|MCP消费者|项目归属/Token/角色校验|不降低授权|区分环境ID与权限|
|执行会话结束|原执行器入口|ExternalExecutorService.owned|保留所有权校验|原Token结束并回读或记录阻塞|

- [x] 建立失败回归，再修复附件，保留expectedUpdateTime。
- [x] 取得原Excel，验证真实导入/导出步骤和预期；修复时保留合法导入能力。
- [x] 核实人员缺失上下文，修复当前授权资产的人员展示。
- [x] 检查15MB导出边界及错误诊断。
- [ ] 核准目录、诊断正式权限与会话归属。
- [ ] 单元/集成、类型、lint、前后端构建和本地核心冒烟；Docker构建按用户要求不执行。部署正式验收等待用户通知。
- [ ] 精确diff与SHA256交指定左殇独立审查，审查后按授权提交交付。

保留工作区原22项改动，不纳入本任务差异。历史同步、低权限测试、生产发布不在范围。

## 用户于当前聊天追加确认：恢复双目录

用户明确选择“恢复项目目录、系统目录双标签及对应分类功能”，覆盖前述目录待核准状态。复用现有functional_business_system、functional_system_module和functional_case_system_relation及用例system_id/system_module_id；项目目录关系保持不变，两视角同ID，不复制。系统目录支持系统/模块创建重命名删除，全部/待人工分类/系统/模块筛选，批量分类、移动和解除，保留源项目目录以便详情编辑。删除有子模块或归属用例时拒绝，跨组织/错属模块拒绝。权限沿用组织资产CRUD及更新，不新增TokenScope。

- [x] 后端：System分类服务/控制器与真实SQL回归；SYSTEM列表仅当前组织Hub资产目录子树，返回assetCatalogId/assetCatalogName；原PROJECT路径仍要求catalogId。
- [x] 前端：恢复路由对应双Tab；系统/模块树、列表、搜索分页、分类移动解除和维护弹窗；详情返回系统目录。
- [x] 系统分类前后端/数据库及本地浏览器回归；独立审查新增差异。
