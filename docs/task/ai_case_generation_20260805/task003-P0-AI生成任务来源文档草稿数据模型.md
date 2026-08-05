# task003 - P0 - AI 生成任务、来源文档、草稿数据模型

## 状态

部分完成

## 执行记录

- 已新增 `functional_case_ai_generation`、`ai_source_document`、`functional_case_ai_draft` 三张表的迁移脚本。
- 已新增三张表对应 Domain。
- 已新增三张表对应 Mapper 接口与 XML。
- 已新增草稿乐观锁更新 Mapper 方法。
- 已新增生成任务、来源文档、草稿相关 DTO、Request、Response。
- 已新增生成任务状态、文档解析状态、草稿状态枚举。

## 未完成 / 未验证事项

- 未连接真实数据库执行 Flyway 迁移验证。
- 未新增 Mapper 单元测试。
- 未新增项目数据隔离相关服务层校验。
- 未开放业务接口；接口实现属于后续 task004-task006。

## 目标

新增 AI 生成用例所需的数据模型，支撑生成任务、来源文档、草稿用例、正式用例关联和审计追踪。

## 数据表建议

### functional_case_ai_generation

记录生成任务。

建议字段：

- `id`
- `project_id`
- `conversation_id`
- `model_source_id`
- `prompt`
- `status`
- `token_usage`
- `duration_ms`
- `error_message`
- `create_user`
- `create_time`
- `update_time`

### ai_source_document

记录产品方案来源文件。

建议字段：

- `id`
- `project_id`
- `conversation_id`
- `file_id`
- `original_name`
- `mime_type`
- `file_size`
- `sha256`
- `parse_status`
- `parsed_result_path`
- `parser_type`
- `error_message`
- `create_user`
- `create_time`

### functional_case_ai_draft

记录 AI 生成草稿。

建议字段：

- `id`
- `generation_id`
- `source_document_id`
- `project_id`
- `module_id`
- `template_id`
- `name`
- `case_level`
- `edit_type`
- `prerequisite`
- `steps`
- `expected_result`
- `tags`
- `custom_fields`
- `validation_status`
- `draft_status`
- `formal_case_id`
- `version`
- `create_user`
- `create_time`
- `update_time`

## 实现要求

- 新增数据库迁移脚本。
- 新增 Domain、Mapper、DTO、Request、Response。
- 草稿表必须支持乐观锁版本字段。
- 草稿与正式用例之间必须保留关联关系。
- 数据必须按 `project_id` 和 `create_user` 做权限隔离。

## 验收标准

- 迁移脚本可重复执行环境验证。
- 三张表能支撑任务、文档、草稿、正式用例关联。
- 删除或保存草稿后状态可追踪。
- 不允许通过草稿表直接冒充正式用例。

## 验证要求

- Flyway / 数据迁移测试。
- Mapper 单元测试。
- 乐观锁并发更新测试。
- 项目数据隔离测试。
