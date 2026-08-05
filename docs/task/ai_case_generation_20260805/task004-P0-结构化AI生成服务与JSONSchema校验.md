# task004 - P0 - 结构化 AI 生成服务与 JSON Schema 校验

## 状态

部分完成

## 目标

实现 AI 结构化生成服务，要求模型输出统一 `CaseGenerationResult`，并由后端进行 JSON Schema 校验、结构修复、字段白名单过滤和错误保留。

## 实现范围

- 复用或改造现有 `FunctionalCaseAIService`、AI Conversation、模型配置能力。
- 定义 `CaseGenerationResult` 结构。
- 定义步骤结构：
  - 步骤序号。
  - 步骤描述。
  - 预期结果。
  - 引用来源。
- 定义用例字段：
  - 名称。
  - 等级。
  - 编辑模式。
  - 前置条件。
  - 步骤。
  - 预期结果。
  - 标签。
  - 来源引用。
  - 自定义字段。
- 引入 JSON Schema 校验。
- 模型输出不合规时执行一次结构修复。
- 修复失败时记录错误，不创建草稿或正式用例。
- 单次生成默认最多 50 条，最大 100 条。

## 安全约束

- 模型输出字段必须白名单过滤。
- 不允许模型输出覆盖项目 ID、用户 ID、权限字段。
- 不允许根据模型输出直接执行正式入库。

## 验收标准

- 文本需求可生成结构化草稿。
- 模型输出不合法时能返回明确错误。
- 不合法输出不会产生正式用例。
- 超过生成数量上限时拒绝或截断并提示。
- 生成结果字段与手工新建功能用例字段保持一致。

## 验证要求

- JSON Schema 校验测试。
- 非法 JSON 测试。
- 字段缺失测试。
- 超量生成测试。
- 模型断流 / 超时测试。

## 执行记录

- 已新增结构化生成数据结构：`CaseGenerationResult`、`CaseGenerationCaseDTO`。
- 已新增结构化生成接口：`POST /functional/case/ai/draft/generation/structured`。
- 已接入 `AiChatBaseService`，要求模型只返回 JSON，并限制默认 50 条、最大 100 条。
- 已实现 JSON 提取、解析失败后的旧 Markdown 结构修复尝试。
- 已实现字段白名单入草稿：模型输出不能覆盖项目 ID、用户 ID、权限字段。
- 已实现非法输出不创建正式用例；生成失败会记录到 `functional_case_ai_generation.error_message`。

## 未完成 / 未验证

- 未引入独立 JSON Schema 引擎文件；当前为后端 DTO 解析 + 业务校验。
- 来源引用字段尚未贯通到草稿字段。
- 自定义字段只保留空数组/草稿 JSON，未按模板完整生成。
- 尚未完成模型断流、超时、非法 JSON、超量生成的自动化测试。
