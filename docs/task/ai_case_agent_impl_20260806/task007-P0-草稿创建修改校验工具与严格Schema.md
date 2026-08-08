# task007 - P0 - 草稿创建、修改、校验工具与严格 Schema

## 状态

进行中。已实现幂等 `create_case_drafts`、严格等级/编辑类型/STEP/TEXT 参数校验、100 条硬上限和草稿会话关联；待补 update/validate 工具、自定义字段完整校验和来源引用回查。

## 目标

将现有固定 JSON 生成逻辑重构为受控 Agent 草稿工具，保证模型参数经过严格 Schema、权限、模板和业务校验后才能写入草稿。

## 依赖

- task005 Agent 工具框架。
- task006 来源引用协议可并行后接入。

## 实现范围

### 1. create_case_drafts

- 支持名称、等级、编辑模式、前置条件、步骤、文本描述、预期结果、标签、自定义字段和来源引用。
- 根对象、用例对象、步骤和来源引用全部设置 `additionalProperties=false`。
- 非法枚举必须报错，不能静默转换为 P1 或 STEP。
- 格式修复最多一次；修复后仍不合规则不得创建草稿。
- 单次数量使用项目配置，并设置绝对上限 100。

### 2. update_case_drafts

- 支持按 ID 批量修改允许字段。
- 请求必须携带 version，使用乐观锁。
- 禁止修改 projectId、createUser、formalCaseId、generationId 等控制字段。
- 模型只能更新当前会话、当前项目、当前用户草稿。

### 3. validate_case_drafts

校验：

- 名称、等级和编辑模式。
- STEP 用例步骤非空、序号与内容有效。
- TEXT 用例文本描述有效。
- 项目模板、自定义字段类型和必填规则。
- 标签、模块和模板归属。
- 来源引用真实性。
- 草稿和正式用例重复情况。

### 4. 工具返回

统一返回：

- successItems。
- warningItems。
- failureItems，包含稳定 errorCode 和字段路径。
- createdIds/updatedIds。

### 5. 幂等

- create 使用 requestId + toolCallId 保证重试不重复创建。
- update 使用 toolCallId + draftId + version 保证结果可追踪。

### 6. 兼容

旧 `/generation/structured` 过渡期可以内部调用 create 工具，但不得保留另一套校验规则。

## 验收标准

- 未知字段、非法等级、非法编辑模式和空 STEP 步骤均被拒绝。
- 修复后 Schema 失败不产生草稿。
- AI 能生成并校验项目模板自定义字段。
- 伪造来源引用不生效。
- 重复用例默认产生 warning，不自动阻止，除非项目配置阻止。
- 重复 toolCallId 不产生重复草稿。
- 乐观锁冲突返回明确错误。

## 测试要求

- JSON Schema 合法、非法和未知字段测试。
- Markdown/JSON 修复成功与失败测试。
- STEP/TEXT、自定义字段、标签和模板测试。
- 来源引用测试。
- 幂等与乐观锁并发测试。
- 跨用户、跨项目草稿工具越权测试。
