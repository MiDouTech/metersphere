# MSP 全板块用途与冗余评估：未接入大模型阶段

日期：2026-09-23。

## 1. 评估前提与结论

以用户确认的事实为准：**当前 MSP 尚未接入大模型。** 仓库存在模型配置、对话、执行器等代码，不代表部署中已经连通并可用。

当前 MSP 应定位为：测试资产与测试过程管理平台，向外部执行者提供受控接入，保存执行记录与证据，并由代码规则和人工复核决定结果能否采用。

平台内模型推理、自然语言规划、自动语义审核暂不作为必需能力。外部 Agent 可以在平台之外使用模型，通过 MCP 读取任务和提交结果；这与 MSP 自己接入模型是两种部署方式。是否已有外部 Agent 持续使用、是否注册了可用 Runner，本次没有生产数据验证。

建议优先收敛入口、统一数据归属、补齐无模型的执行验收链路。**不建议立即删表或删除整套 AI 代码。** 目前能确认的是部分入口重复、页面职责混杂和功能启用条件不清；尚不能仅凭代码判断某板块长期无人使用。

本次覆盖前端 routes/modules 下全部路由模块，按业务板块评估用途，对 Agent、资产、自动化执行做重点源码核对。不是逐按钮全栈验收，也不是生产使用率审计。

## 2. 用什么标准判断冗余

| 类型 | 判断标准 | 处理方式 |
|---|---|---|
| 入口重复 | 同一对象、同一操作、同一批用户，在多处出现 | 统一主入口，其余保留上下文链接或旧路由重定向 |
| 数据重复 | 相同业务事实存成两份，修改后可能不一致 | 指定唯一数据源，其他模块引用或生成不可变快照 |
| 职责重叠 | 名称不同，但用户无法解释各自解决什么问题 | 合并导航或重划职责，明确输入、输出、责任人 |
| 阶段性闲置 | 功能有用途，但前置能力尚未具备 | 保留代码/历史，按能力关闭入口，不宣称已可用 |
| 合理多视图 | 同一数据面向不同范围、角色或阶段 | 保留视图，共用查询与权限，不另建业务事实 |
| 无消费者能力 | 只有配置，没有实际引用或执行用途 | 确认无引用及无使用后归档；不能根据空页面直接删除 |

后文“合并”默认指产品入口和职责调整，不等于把不同权限、租户或运行时协议强行合成一张表。

## 3. MSP 全板块评估

| 板块 | 用途与产物 | 不接模型是否有价值 | 冗余判断与建议 |
|---|---|---|---|
| 工作台：首页、待办、关注、我创建的 | 个人工作入口，聚合任务与风险 | 有 | 保留；统计和列表引用领域数据，不维护第二套状态；待办承接人工复核 |
| 功能用例管理 | 维护测试意图、步骤、预期、业务分类 | 核心 | 保留为项目用例的主要编辑入口；与资产库明确“项目实例/共享模板”边界 |
| 用例生成 | 文档输入、对话生成、草稿审核、入库 | 部分 | 无模型时关闭生成/重生成动作；文档、草稿导入、人工审核是否可独立使用需拆分验证，不能因无模型删除全部 |
| 用例评审 | 执行前审查测试设计是否充分 | 核心 | 保留；不与执行后质量验收混同。当前主路由在测试计划下，旧用例路由用于兼容，不算两套业务 |
| 自动化执行 | 创建任务、看进度、暂停、取消、重试、查看证据 | 外部执行路径有价值 | 统一为执行工作台；无模型时平台模型通道禁用，外部 MCP 通道按实测能力开放 |
| 测试计划 | 针对版本/里程碑组织用例、分配执行、跟踪进度 | 核心 | 保留；计划是范围组织，执行任务是一次运行，二者不能互相替代 |
| 测试计划报告 | 汇总一次计划结果及质量结论 | 核心 | 保留；按固定版本引用执行结果，不能用最新结果覆盖历史报告 |
| 功能测试报告 | 功能用例维度汇总/报告 | 有 | 与计划报告存在表达重叠的可能；建议统一报告中心入口，保留范围与模板差异，未核实前不删引擎 |
| API 调试 | 单次请求试验与问题定位 | 核心 | 保留；调试成功不等于正式测试通过 |
| API 管理 | 接口定义、接口用例及复用资产 | 核心 | 保留为接口主要编辑入口；资产目录只索引和管理执行版本 |
| API 场景 | 多接口编排、依赖与断言 | 核心 | 保留；无需模型。与业务流可能共享概念，执行协议不同，不宜直接合表 |
| API 报告 | 请求、断言、耗时及失败详情 | 核心 | 保留专业报告；由报告中心统一导航，与计划汇总建立引用 |
| 缺陷管理 | 跟踪真实产品问题、修复与验证 | 核心 | 保留唯一缺陷状态机；门禁缺证据不自动转产品缺陷，资产目录不再编辑缺陷状态 |
| 测试资产 | 共享、版本冻结、来源和关联追溯 | 有 | 收敛为资产目录及复用能力；不再复制用例/API/缺陷的完整业务操作 |
| Agent | 接入、运行资源、配置、任务、评价混合集合 | 部分 | 当前板块过宽；拆为执行工作台、接入与执行资源、模型实验能力 |
| 项目管理 | 项目成员、权限、版本、模板、环境及配置 | 核心 | 保留；与组织/系统配置明确继承关系，而非独立重复维护 |
| 系统/组织设置 | 账户权限、组织、资源池、集成、任务、审计 | 核心 | 保留不同管理范围；对重复菜单做统一入口与范围切换，权限不合并放大 |
| 分享页面 | 报告的受控分享与查看 | 有 | 保留为访问方式；检查分享权限和有效期，不另建报告事实 |
| 全屏/独立页面 | 编辑器、详情等布局与路由支持 | 有 | 属于技术承载，不当作独立业务板块计数或删除 |

## 4. Agent 十二个主标签逐项评估

已核对 agent.ts 和 AgentTabs.vue，当前存在十二个主标签，另有隐藏执行详情路由。

| 现有标签 | 实际作用/源码观察 | 当前建议 | 保留条件与边界 |
|---|---|---|---|
| Agent 列表 | 列出 Runner 状态、版本、心跳、并发，支持注册 | 改名“执行器”，移至接入与执行资源 | 有真实 Runner 再开放业务入口；不能把外部 MCP 客户端和 Runner 当成同一对象 |
| 能力与授权 | 同页包含执行能力、我的 Token、运维告警、项目 AI 治理 | 拆分后撤销独立主标签 | 能力归接入状态；Token 归个人接入；告警归运行运维；策略归项目设置 |
| 环境执行配置 | 基础地址、Runner 类型、环境扩展配置 | 保留能力，合并入口 | 项目环境是环境身份来源；执行扩展引用 environmentId，不另造同名环境全集 |
| 凭据引用 | 凭据元数据、角色、版本、验证和过期 | 保留并下沉资源管理 | 凭据引用不等于明文密码。外部 Agent 不应因读取任务就获得所有凭据 |
| 模型执行配置 | MAP Gateway 逻辑模型和密钥引用、能力验证 | 当前关闭普通用户入口 | 管理员可见“未接入”；连通、能力、权限、失败恢复验证后再启用 |
| Prompt 模板 | 模板版本、输出协议、发布/预览等 | 当前模型执行用途暂缓 | 若外部 Agent 确有消费，可以保留为任务说明模板；不能把提示词当门禁 |
| 自动登录配置 | 登录地址、环境引用、MFA 策略 | 收入环境详情高级配置 | 配置本身不会执行登录；须有对应执行器消费者及凭据授权 |
| Page Object | 可复用页面元素与语义定位器 | 条件保留，移到 UI 自动化资产 | 不依赖模型的概念，但当前消费者需核实；不要求所有外部 Agent 为提交结果维护它 |
| 业务流 | UI/业务动作的复用定义 | 条件保留，与公共步骤统一发现入口 | 分清业务级流程、UI 动作和 API 场景；若只重复自然语言用例则不再独立维护 |
| 调度队列 | 任务、执行租约、调度规则三个标签 | 任务移到执行工作台；租约与调度归运维 | 没有执行消费者时禁用触发；已有调度先盘点再迁移 |
| 执行评价 | 完成率、证据率、耗时、重试、人工评分 | 保留统计，归执行质量视图 | 人工评分是体验反馈，不是批准。增加硬规则裁决前，不能把它叫质量门禁 |
| Agent 集成 | access.vue 复用设置中的 AgentIntegration，主要是个人 Token | 保留一个“外部接入/MCP”主入口 | 与能力页的 Token 视图去重；个人凭据与管理员全局审计权限分开 |

隐藏执行详情 `agent/execution/detail.vue` 直接复用 `bug-management/automationExecution/index.vue`。这是明确的组件复用；应统一导航和 URL 规则，不必重写详情组件。源码位于 bug-management 目录并不表示它应由缺陷板块负责，后续可以独立迁到 execution 目录。

## 5. 测试资产各子板块评估

| 子板块 | 应承担的独有价值 | 冗余边界/调整 |
|---|---|---|
| 文档 | 保存需求来源、版本和用例引用关系 | 与生成页文档列表共享同一源文档；无模型也可上传、阅读、追溯，解析能力需单独验证 |
| 用例：项目/系统 | 项目复用及跨项目共享模板 | 项目工作用例继续由用例管理编辑；系统模板采用复制或明确版本引用，不隐式同步修改所有项目 |
| 数据集 | 数据版本、参数集和运行引用 | 复用项目文件存储，但数据格式、租用/隔离等语义可独立；文件管理不等于数据集管理 |
| 环境 | 跨资产查找环境和查看版本引用 | 统一环境 ID；源编辑在项目环境，执行配置为扩展，目录提供索引 |
| 版本 | 已发布执行资产的不可变版本 | 保留；它不同于产品发布版本，也不同于 Git 构建摘要，必须显示版本类型 |
| 关系 | 需求—用例—计划—运行—证据—缺陷追溯 | 保留；优先作为详情内追溯视图，数据少时不必独占主标签 |
| 公共步骤 | 可复用动作或场景片段 | 与 API 场景、业务流使用类型区分；统一检索，不强行统一执行格式 |
| 接口 | 对 API 资产做来源、版本、引用管理 | 主编辑留在 API 管理；目录应明确只读/版本操作，避免双写 |
| 证据 | 从运行跨任务检索证据、查看引用和保留期 | 保留；不是普通附件库，更不能让任意附件自动成为可信证据 |
| 缺陷 | 展示缺陷与测试资产/运行关联 | 缺陷状态由缺陷管理维护；若只有重复列表，降为检索过滤项或详情关联 |

已读到 apis.vue、bugs.vue、environments.vue、common-steps.vue 均包装同一 Catalog 组件。Catalog 还提供发布执行版本、关联追溯、来源治理等动作，因此不是“只有重复页面”的简单结论。它的合理用途是索引与版本管理，需避免演变为第二个业务主数据系统。

## 6. 项目、组织与系统设置的边界

| 配置组 | 建议用途 | 冗余判断 |
|---|---|---|
| 项目列表、基本信息、成员、菜单、权限 | 项目范围的工作空间与可见能力 | 保留；系统账户与项目成员是不同对象，不能删掉一层 |
| 系统用户、角色与权限控制 | 身份与基础授权 | 保留为授权来源；不要另做一套 Agent 用户角色 |
| 组织与项目、组织结构、组织成员 | 管理层级与归属 | 多入口可合并导航；组织结构和项目成员权限不同，不能仅因都显示用户就合并数据 |
| 项目版本 | 产品/项目迭代范围 | 保留；与资产版本、运行构建分别命名和关联 |
| 项目/组织模板、字段 | 组织默认规范与项目覆盖 | 共用组件和继承关系；若是拷贝两份需披露版本，不允许两个页面争夺同一配置 |
| 环境管理 | 环境地址、变量和适用项目 | 保留主入口，与 Agent 环境扩展、资产环境目录关联 |
| 文件管理 | 通用上传、存储与下载 | 保留底层；需求文档、数据集和证据各自增加业务元数据 |
| 公共脚本、错误报告规则 | API 等执行规则复用 | 保留按执行引擎作用，不强行归入 AI Prompt |
| 消息管理、服务集成 | 通知规则、外部系统连接 | 不依赖模型，保留；Agent Token 与外部系统凭据不混用 |
| 任务中心：系统/组织/项目及执行队列 | 查看不同范围任务及调度运行 | 建议统一任务发现入口和状态解释，按范围授权；不同引擎可保留执行明细 |
| 系统参数：基础、显示、认证、清理等 | 部署和运行配置 | 保留；新增能力启用条件应在此或项目策略集中表达，不能散落多个页面 |
| 测试资源池 | 传统执行基础设施 | 保留；不因增加 Runner 就删除。可统一资源入口，生命周期与协议按类型区分 |
| 插件、授权管理 | 扩展与部署许可等系统能力 | 保留现有管理职责；无模型不影响其必要性 |
| 系统/组织/项目日志 | 同一审计体系的范围视图 | 可统一入口和查询协议；仍保留权限范围，避免组织用户读取系统日志 |

这里的结论为职责与导航层评估，未逐个证明各设置页后端实现完整。共享组件、兼容路由、范围视图不能列作需要删除的业务冗余。

## 7. 当前执行通道的关键限制

已读取 AgentExecutionService：任务通道按 personalMcp 分为 EXTERNAL_MCP_AGENT 与 MODEL_API_RUNNER；RUNNER 模式调用 modelProfileService.assertUsable；非个人 MCP 路径保存模型与 Prompt 快照。

因此不能直接说“没接模型也能用现有平台 Runner 完成脚本自动化”。传统 API 执行有自己的实现，与这条模型 Runner 路径不同。如果希望新增 SCRIPT_RUNNER，应独立定义能力、任务合同和回调协议，作为后续开发，不能仅改一个显示名称。

当前建议能力矩阵：

| 能力 | 当前处理 |
|---|---|
| 人工管理用例、计划、缺陷与报告 | 继续使用，实际部署权限和运行健康另行验证 |
| 传统 API 测试 | 独立于模型，按原执行链路使用 |
| MCP 查询/写入测试管理对象 | 可不依赖 MSP 模型，但逐工具鉴权和实际连通仍需验证 |
| 外部 Agent 领取任务、上传证据 | 作为优先验证和完善的通道；不能从代码存在推断已上线可用 |
| 平台模型 Runner、模型生成用例 | 在未接模型前标为未启用，后端同步拒绝不满足前置条件的启动 |
| 平台自动图像/语义审核 | 不作为一期能力；由代码能判定的断言和人工复核替代 |
| 独立普通脚本 Runner | 当前不据此认定已支持；如有需求单独实现与验收 |

未来建议 capability 响应分别表达“部署启用、配置就绪、连通健康、用户有权、项目允许”，而不是用一个 configured 布尔值代表可执行。前端根据它显示状态和原因，后端独立校验；隐藏菜单不等于禁止调用。

## 8. 建议的信息架构

普通测试人员的主线：

```text
工作台
测试设计：功能用例 / 用例评审 / 需求文档
接口测试：调试 / API 管理 / 场景
测试计划：范围、构建、分配与执行入口
执行工作台：任务 / 运行详情 / 证据与验收 / 重测
缺陷管理
报告：计划汇总 / 功能报告 / API 报告
资产目录：共享模板 / 数据集 / 可复用动作 / 版本与追溯
项目与系统设置：权限、环境、接入、执行资源、审计
```

这是目标分组，不要求一次性新增九个一级菜单。先在现有菜单中收敛重复入口，再按使用反馈调整导航。

管理员的接入设置分为：外部 MCP 客户端与个人授权、Runner 资源、环境与凭据、调度与告警。模型接入作为未启用的可选能力，普通用户不需要面对逻辑模型 ID、网关 App Caller 和 Prompt 策略 ID。

质量门禁嵌入“执行详情 + 待办复核 + 项目规则配置”；一期不再新增独立证据中心、反馈中心和治理中心，以免重复已有资产、任务和待办页面。

## 9. 对上一版质量门禁方案的修正

上一版中的独立 AI 审核属于未来可选能力，当前不应作为闭环前提。当前一期只需要：

1. 从计划/用例冻结范围与断言，记录环境和构建。
2. 外部执行者通过授权 API/MCP 提交事件、结构化结果和证据。
3. 后端检查身份、覆盖、版本、证据归属、哈希、状态一致性及可计算断言。
4. 需要图片语义或复杂判断时进入人工复核，不能用“有截图”自动判成功。
5. 平台验收后写入产品结果；发布条件另外判断。
6. 在现有执行详情展示反馈和重测操作。

不强制新增模型配置、Prompt 管理、Page Object 或业务流维护步骤才能提交结果。这些执行辅助资产只有在选定执行方式实际需要时才成为前置条件。

同时承认限制：没有独立可信采集，外部 Agent 自报证据只能达到较低信任等级；代码校验不能证明全部现场事实。应显示来源等级并安排必要复核。

## 10. 建议的改动顺序与落点

| 优先级 | 改什么 | 前端落点 | 后端/数据影响 | 验收重点 |
|---|---|---|---|---|
| P0 | 清楚表达模型未启用及可用通道 | 模型页、生成页、执行创建页 | 能力查询和创建任务前置校验 | 未接模型不会误导启动、长期排队或返回成功 |
| P0 | 外部执行提交验收闭环 | 执行详情、计划历史 | 提交、裁决、回写守卫及迁移 | MCP/REST/计划外/批量不能绕过 |
| P1 | 合并任务列表与详情入口 | automationExecution、queue、agent 路由 | 复用任务服务和 taskId | 旧链接可访问，取消/重试行为一致 |
| P1 | 拆能力页、统一接入入口 | capability、access、AgentTabs | Token/告警/策略服务复用 | 权限范围不扩大，个人密钥不重复展示 |
| P1 | 环境与资产数据归属 | 项目环境、environment-profile、Catalog | environmentId 映射、引用与版本 | 不重复维护，不丢已有任务快照 |
| P1 | 重评普通用户可见范围 | agent.ts、testAsset.ts及权限配置 | 领域权限不以菜单控制代替 | 测试员能做工作，管理员功能仍受限 |
| P2 | 收敛报告、共享资产与追溯入口 | 报告页、资产页、详情追溯 | 保留各领域事实与历史版本 | 统计口径一致，报告范围可解释 |
| 后续 | 开启平台模型/脚本执行通道 | 按能力逐项开放 | 分别实施、接入和验证 | 不依赖“页面已有”作为完成证据 |

代码位置：frontend/src/router/routes/modules；frontend/src/views/agent；frontend/src/views/bug-management/automationExecution/index.vue；frontend/src/views/test-asset；frontend/src/api/modules/ai-execution.ts；backend/services/agent-integration 下对应 Controller/Service；执行结果守卫还涉及 test-plan 和 case-management。迁移放现有 domain migration 目录。

删除标准：先查生产近一段时间的访问、写入、API 调用、定时任务、外部客户端及历史引用；确认无消费者再下线入口，观察后归档代码。数据库清理另做备份与回滚设计。当前没有足够证据提出立即删表清单。

## 11. 使用流程与评估指标

无模型阶段的理想路径：测试员维护用例 → 创建计划并冻结范围 → 外部执行者领取任务或人工执行 → 回传证据 → 自动规则检查 → 必要时人工复核 → 正式结果与缺陷 → 计划报告。

测试员不应为了完成一次外部执行，依次配置模型、Prompt、Page Object、业务流、Runner 和网关。配置由实际执行通道决定，能复用的环境和凭据自动引用，失败提示说明缺少哪项能力。

整改是否有效，用以下指标判断：创建任务必须填写的配置数量、首次成功提交时间、重复维护数据次数、用户找错入口次数、待复核处理时间、未接模型导致的失败任务数、不同视图状态不一致数。暂不虚构当前数值或合并后的效率提升比例。

## 12. 本次交付与验证边界

需求追踪：全平台板块用途见第 3、5、6 节；Agent 逐项见第 4 节；未接模型前提见第 1、7、9 节；冗余处理见第 2、8、10 节。前后端拟议对应见第 10 节。

修改文件：新增本文，给上一版 ai-quality-gates-research-and-practical-guide.md 增加前提修订提示；未修改业务代码、路由或数据库。

实际验证：Get-Content 阅读路由、页面和 AgentExecutionService；rg -n 定位调用/通道；rg --files 盘点文件；生成路由索引辅助核对。PowerShell 不支持首次使用的花括号路径展开，已改为目录搜索；一次 parameter 目录猜测不存在，未据此形成结论。组件复用和模型前置校验结论有源码依据。

未执行生产页面操作、线上数据统计、全功能前后端验证、单元/集成测试、类型/lint/构建、迁移、容器及 E2E：此次为产品与架构评估，没有功能代码变更。不能据此宣称模块可正常运行、某功能零使用或整改已完成。实施阶段需依据真实变更和部署配置给出并执行准确验证命令，本次不虚构运行命令或测试结果。

状态：**用途与冗余评估文档完成；工程调整未实施；全功能运行验证未进行。** 关键待确认项是实际启用菜单、外部执行通道健康、生产使用频度及重复配置的真实数据关系。它们影响最终删除决策，不影响先修正能力表达和收敛入口的方向。

## 附录：本次盘点的全部路由模块

下列为源码中的局部 path 声明（嵌套路由需结合父路径），仅用于完整性盘点；兼容跳转、隐藏详情、分享及全屏页面不分别视为独立业务板块。

### agent.ts

```text
path: '/agent',
path: 'list',
path: 'capability',
path: 'environment-profile',
path: 'credential-reference',
path: 'model-profile',
path: 'prompt-template',
path: 'login-profile',
path: 'page-object',
path: 'business-flow',
path: 'queue',
path: 'evaluation',
path: 'execution/detail',
path: 'access',
```

### apiTest.ts

```text
path: '/api-test',
path: 'debug',
path: 'management',
path: 'recycle',
path: 'scenario',
path: 'scenario/recycle',
path: 'report',
```

### bugManagement.ts

```text
path: '/bug-management',
path: 'index',
path: 'detail/:mode?',
path: 'create-success',
path: 'recycle',
```

### caseManagement.ts

```text
path: '/case-management',
path: 'featureCase',
path: 'caseGenerate',
path: 'automation-execution',
path: 'featureCaseDetail/:mode?',
path: 'featureCaseCreateSuccess',
path: 'featureCaseRecycle',
path: 'caseReview',
path: 'caseManagementReview',
path: 'caseManagementReviewCreate',
path: 'caseManagementReviewDetail',
path: 'caseManagementReviewDetailCaseDetail',
path: 'testReport',
path: 'testReportDetail',
```

### fullPage.ts

```text
path: '/fullPage',
path: 'testPlanExportPDF',
path: 'scenarioExportPDF',
path: 'apiCaseExportPDF',
```

### projectManagement.ts

```text
path: '/project-management',
path: 'projects',
path: 'permission',
path: 'basicInfo',
path: 'menuManagement',
path: 'projectVersion',
path: 'member',
path: 'projectManagementTemplate',
path: 'projectManagementTemplateField',
path: 'projectManagementTemplateList',
path: 'projectManagementTemplateCaseDetail/:mode?',
path: 'projectManagementTemplateApiDetail/:mode?',
path: 'projectManagementTemplateBugDetail/:mode?',
path: 'fileManagement',
path: 'messageManagement',
path: 'messageManagementEdit',
path: 'commonScript',
path: 'errorReportRule',
path: 'environmentManagement',
path: 'log',
```

### setting.ts

```text
path: '/setting',
path: 'system',
path: 'user',
path: 'permission-control/roles/new',
path: 'permission-control/roles/:roleId',
path: 'permission-control',
path: 'organization-and-project',
path: 'org-structure',
path: 'parameter',
path: 'resourcePool',
path: 'resourcePoolDetail',
path: 'taskCenter',
path: 'pluginManager',
path: 'authorizedmanagement',
path: 'log',
path: 'organization',
path: 'member',
path: 'org-structure',
path: 'project',
path: 'serviceIntegration',
path: 'template',
path: 'templateFiledSetting',
path: 'templateManagement',
path: 'templateManagementCaseDetail/:mode?',
path: 'templateManagementApiDetail/:mode?',
path: 'templateManagementBugDetail/:mode?',
path: 'taskCenter',
path: 'log',
```

### share.ts

```text
path: '/share',
path: 'shareReportScenario',
path: 'shareReportCase',
path: 'shareReportTestPlan',
path: 'shareFunctionalTestReport',
path: 'shareDefinitionApi',
```

### testAsset.ts

```text
path: '/test-assets',
path: 'documents',
path: 'cases',
path: 'datasets',
path: 'environments',
path: 'versions',
path: 'relations',
path: 'common-steps',
path: 'apis',
path: 'evidence',
path: 'bugs',
path: 'cases/project',
path: 'cases/system',
path: 'cases/:catalogId/:caseId',
```

### testPlan.ts

```text
path: '/test-plan',
path: 'testPlanIndex',
path: 'testPlanReport',
path: 'caseReview',
path: 'caseReviewCreate',
path: 'caseReviewDetail',
path: 'caseReviewDetailCaseDetail',
path: 'functionalTestReport',
path: 'functionalTestReportDetail',
path: 'testPlanReportDetail',
path: 'testPlanIndexDetail',
path: 'testPlanIndexConfig',
path: 'testPlanIndexDetailFeatureCaseDetail',
```

### workbench.ts

```text
path: '/workstation',
path: 'home',
path: 'wait',
path: 'followed',
path: 'created',
```

