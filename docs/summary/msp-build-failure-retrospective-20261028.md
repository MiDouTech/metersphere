# 两次前端构建失败复盘与防复发规则

日期：2026-10-28。范围：CaseAssetBatchActions 的 TS2345、importJob 的 TS2339，以及修复后同次流水线的发布配置失败。证据来源：用户两次截图、Git提交差异、组件库类型声明、提交钩子、实际CI配置和2026-10-27已取得的Actions结果。本轮只整理复盘，不修改业务代码、工作流、分支保护或Secrets，不新增发布。

## 1. 结论与责任

两次都是我提交的前端代码不满足当前项目类型契约，不是已证实的依赖升级、IDE差异或GitHub故障。共同的流程根因是：没有取得完整Vue类型检查通过的证据，就将代码合并到了发布分支。第二次虽然明确披露了本机检查未完成，也仍然把缺陷发现推迟到了main构建阶段；披露风险没有替代质量检查。

本机内存不足确实限制全量检查，但它只解释为什么本机未执行，不能证明代码正确，也不构成把语法检查当成类型检查的理由。我的具体失误是：对新增表单未核对必填props；在异步重构中未核对可变ref的类型收窄；验证方式不匹配风险，且没有把远端完整校验前置到合并之前。

## 2. 事件与证据

| 事件 | 引入/修复 | 失败阶段 | 已确认的事实 |
|---|---|---|---|
| 第一次：CaseAssetBatchActions.vue:46，TS2345 | 引入fd09cbc8e1；修复580b7aed02 | `vue-tsc --noEmit`，Vite尚未运行 | a-form只有layout，没有必填model |
| 第二次：importJob.vue:249，TS2339 | 引入dcd9c9b018；修复7fa2c33034 | `vue-tsc --noEmit`，Vite尚未运行 | 异步闭包内读取的followupOptions.value仍为FollowupOptions或undefined |
| 修复后的10月27日CI | 7fa2c33034；Actions 40489516420 | 代码构建成功，发布失败 | 前端构建、ZIP打包/Artifact上传和后端编译通过；CNB静态上传提示缺CNB_TOKEN，镜像登录提示Password required |

提交：[首次引入](https://github.com/chenqifen-miduo/metersphere/commit/fd09cbc8e1)、[首次修复](https://github.com/chenqifen-miduo/metersphere/commit/580b7aed02)、[第二次引入](https://github.com/chenqifen-miduo/metersphere/commit/dcd9c9b018)、[第二次修复](https://github.com/chenqifen-miduo/metersphere/commit/7fa2c33034)。

[第二次失败CI](https://github.com/chenqifen-miduo/metersphere/actions/runs/40487173684)、[修复后CI](https://github.com/chenqifen-miduo/metersphere/actions/runs/40489516420)。发布凭据结论仅对应当次已观察日志，不据此断言10月28日当前Secret仍缺失。

## 3. 第一次：漏传表单必填model

引入位置：`frontend/src/views/test-asset/components/CaseAssetBatchActions.vue`。

```vue
<!-- 引入时 -->
<a-form layout="vertical">

<!-- 修复后 -->
<a-form :model="{ categoryId, priority, tags, tagMode, sourceEditMode }" layout="vertical">
```

溯源链：新增资产批量操作弹窗 → 只考虑了子控件各自的v-model → 未检查父级Arco Form的props契约 → 遗漏必填model → Vue模板类型检查发现只传layout不满足组件参数 → 生产构建退出。

当前组件库的 `@arco-design/web-vue/es/form/form.d.ts` 明确声明 `model.required: true`。子控件绑定状态不等于父级表单收到model；页面可能具备部分运行表现，也不能证明模板类型契约成立。修复绑定实际表单字段，不能仅用任意空对象掩盖表单与状态之间的脱节。

漏检原因：没有在合并前取得覆盖Vue模板props的完整类型检查证据。普通ESLint、模板可解析或组件能够转译，均不足以验证第三方组件全部必填props。没有证据证明是包版本变化造成，不能把日志中的peer dependency警告当作根因。

## 4. 第二次：异步闭包不保留可变ref属性的非空收窄

引入位置：`frontend/src/views/case-management/caseManagementFeature/components/import/importJob.vue`。当时为了修复重复请求，将评审人加载抽成共享Promise，写成：

```ts
if (!followupOptions.value) return Promise.resolve();
const promise = (async () => {
  const { projectId } = followupOptions.value;
  // ...
})();
```

`followupOptions` 是可变响应式引用，值类型含undefined。外层判断不代表在新函数边界内该属性仍被类型系统认定非空；编译器在这里没有保留收窄，因此解构projectId触发TS2339。这个读取发生在闭包中第一次await之前，不能仅凭本次类型错误就声称已发生运行时竞态；已证实的是类型检查失败。实际组件又确实会在关闭/切换任务时清空选项，所以捕获本次操作的稳定上下文也是合理的实现方式。

修复：

```ts
const options = followupOptions.value;
if (!options || reviewersLoaded || policy.value !== 'CREATE_NEW_PAIR') return Promise.resolve();
if (reviewersPromise) return reviewersPromise;
const { projectId } = options;
const promise = (async () => {
  const users = await getReviewUsers(projectId, '');
  if (context !== generation) return;
  // 保留原来的迟到响应隔离及请求复用
})();
```

不采用非空断言、any或忽略诊断来消除报错；它们不会建立稳定上下文。只捕获参数也不能替代异步返回后的generation检查，必须同时保留。

### 为什么已有检查仍然漏掉

- SFC的script/template编译只证明可转换；测试中的 `ts.transpileModule` 不是完整项目语义类型检查。
- ESLint通过不代表TypeScript类型检查通过。
- 原两项组件测试走的是“不可新建”及任务切换路径，没有覆盖自动选中CREATE_NEW_PAIR后的评审人加载；而且即使这条路径能运行，也不会自动验证静态类型。
- 本机仅约560MiB空闲内存，提交使用命令级HUSKY=0，跳过了包含 `npm run type:check` 的本机钩子。这是第二次已有记录的事实；第一次是否同样跳过钩子缺乏足够记录，不作推断。
- 补救方式停留在“说明未运行全量检查”，未在合并前建立等价的远端完整校验。

修复后新增“自动选择新建时评审人请求去重”和“真实loadReviewers的严格语义类型检查”。旧代码反验能够复现同一错误，修复后4/4测试通过；随后远端完整前端构建也通过。该局部语义测试只覆盖提取出的函数，不等于整个SFC模板或整个项目均已完成类型检查。

## 5. 流程上的共同漏洞

当前 `frontend/package.json` 的生产命令是 `vue-tsc --noEmit && vite build ...`；本机pre-commit是 `type:check && lint-staged`。本来已有能拦截这两类问题的检查，问题在于合并前没有拿到它通过的证据。

本次核对时 `.github/workflows` 只有 `cnb-build.yml`，触发包括main/v3.x推送和workflow_dispatch，没有pull_request触发；chenqifen或普通修复分支的推送也不会自动触发该工作流。因此按当时的直接合并流程，完整检查发生在main更新后，且与需要Secret的发布绑在同一流水线中。这里不能据源码断言GitHub分支保护的实际设置；本轮未检查该设置。

这并不意味着用户要求合并有问题。执行者应把“按要求提交/合并”和“已验证可构建”明确区分，不能因为完成Git操作就将产品状态升级为通过。

## 6. 检查证据不能互相替代

| 证据 | 能证明 | 不能证明 |
|---|---|---|
| ESLint | 指定规则下没有代码规范错误 | Vue组件props和完整TS语义正确 |
| SFC编译/transpileModule | 模板/脚本可解析与转译 | 完整类型正确、Vite可打包 |
| 组件运行测试 | 所覆盖路径的行为 | 所有分支、静态类型、第三方组件契约 |
| 局部TS语义检查 | 所选真实函数在相应类型环境下通过 | 全项目/完整SFC模板通过 |
| vue-tsc | 当前配置下项目类型检查通过 | 生产打包或页面交互通过 |
| 生产构建 | 类型及打包成功 | 镜像启动、迁移、真实业务验收通过 |
| Git推送/合并 | 分支已包含指定提交 | 上述任何产品验证通过 |

## 7. 防复发行动

以下分为已实施和待实施；本复盘不把建议说成已落地。

| 行动 | 执行责任 | 状态/关闭依据 |
|---|---|---|
| 新表单按实际组件声明检查必填props，model绑定真实字段 | 后续代码作者/评审者 | 首次问题代码已修；后续作为审查项执行 |
| 异步重构捕获本次projectId/任务标识，返回后仍检查generation | 后续代码作者/评审者 | 本次加载函数已修，已有回归测试；不声称已审计全仓库 |
| 回归测试必须反验旧代码，确认能重现缺陷 | 测试作者 | 第二次语义测试已反验；首次表单问题尚无独立负例回归测试 |
| 本机资源不足时使用远端独立校验，不用转译结果替代vue-tsc | 交付执行者 | 规则已记录；独立远端校验工作流尚未配置 |
| 新增不依赖发布Secret的PR/修复分支校验工作流 | 后续CI实施者 | 待实施：冻结依赖安装、完整vue-tsc、定向回归与Vite生产构建；手动入口也应支持非main修复分支 |
| 将上述纯校验设为目标分支的required check，默认先验证再合并 | 仓库管理员 | 待核对/配置；不得称已启用。用户明确要求先合并时仍披露未验证状态，不伪造检查结果 |
| 按职责拆分纯校验、构建制品与CNB发布 | 后续CI实施者 | 待实施：不要求CNB_TOKEN才能验证代码；缺发布凭据应准确报发布阻塞 |
| 发布前预检必需Secret是否可用 | 后续CI实施者/管理员 | 待实施：只检查存在性，不打印值；不要让发布配置问题消耗完编译后才暴露 |
| 完成报告附提交SHA、检查范围、结果及未执行项 | 交付执行者 | 本轮已按此区分；后续持续执行，不将报告替代实际检查 |

建议的验证顺序：

1. 阅读本次涉及的组件API类型、异步边界及现有实现。
2. 用能复现旧问题的最小测试覆盖风险；UI模板契约必须有覆盖Vue模板的检查。
3. 资源允许时在frontend执行 `pnpm type:check`，运行 `node --max-old-space-size=160 --test scripts/test-import-job-requests.mjs`，对修改文件执行ESLint。
4. 在资源足够的隔离CI执行 `pnpm build`。生产命令已包含vue-tsc，不在同一未变化版本上机械重复全量检查；独立类型检查用于更早反馈。
5. 核对实际提交SHA和检查结果，再按用户授权进行合并/发布；修复后至少确认原失败阶段通过，不再只给“已提交，CI排队”。
6. 类型/构建通过后，仍按变更影响执行页面、权限及业务回归，不把构建成功说成功能全部验收。

低资源时仍可运行定向测试；它们提供局部证据，但完整检查未执行的状态必须保留。不能通过关闭strict、放宽组件类型、添加any/@ts-ignore、删掉vue-tsc或让CI吞错来换取绿色。

## 8. CNB_TOKEN问题的边界

7fa2c33034对应10月27日CI：前端构建、打包及GitHub Artifact上传成功，后端编译成功；随后CNB发布凭据不可用。这不是第三个已证实的代码编译错误，也不能据后端编译成功声称Docker镜像已构建：镜像登录失败后构建步骤被跳过。

修复路径是由有权限的管理员在正确的GitHub仓库/环境中受控配置CNB_TOKEN，核对其CNB仓库访问权限。不要把Token写进源码、截图或对话；本次没有查看、创建或修改密钥。只有发布成功、环境启动及验收完成后，才能分别更新对应状态。

## 9. 本次复盘交付与限制

本轮修改仅本文，并给原实施记录添加交叉链接。已执行Git历史/差异核对、源码/类型声明/工作流/测试阅读及文档检查；没有业务代码修改，没有重跑无影响构建，没有改变CI、钩子或分支保护。文档不适用前后端接口/数据库变更，未进行新的容器或E2E验证，也不能用本次复盘关闭原方案剩余验收。

复盘结论：两处已知代码构建错误已修且有对应证据；防复发规则已整理，自动化合并前门禁仍待实施。对这一缺口继续标为部分完成。
