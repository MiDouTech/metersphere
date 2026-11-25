# MSP-38 v1.1独立审查

日期2026-11-25；审查者质量-左殇0538c5c8-3cb8-4047-a0c6-73d2b1d63eb0。

绑定patch：0ca11e02c67f0de192aa99882cfe74a8dd4d298421c7d26eb612309db8aeec80，实际SHA256核对一致。结论：需修改，不批准提交合并。未完成完整17文件和全部基线调用链审查，不能以本报告声称全量审查完成。

## P1 合并STEP导出绕过精确传输，仍可能改变步骤内容及数量

定位：backend/services/case-management/src/main/java/io/metersphere/functional/service/FunctionalCaseFileService.java第677至683行、第758至775行；FunctionalCaseImportEventListener.java第397行；CaseStepSplitUtils.java第85行。

触发：资产STEP导出请求isMerge=true，步骤正文含字面`<br>`、`<p>`、多行或行中编号。buildCaseExportData进入buildExportMergeData分支，每行直接写原始描述和预期，未调用parseAssetStepData。hideTransportCells只处理带传输前缀的格子，因此这些原文没有元数据保护。导入合并行仍调用getSingleRowSteps及splitCell，继续进行HTML替换和编号/换行拆分。

预期：一个步骤的完整描述及对应预期保持不变。实际静态调用路径会清洗HTML字面内容，或把单步骤正文拆成多个步骤。v1.1修复覆盖非合并STEP及TEXT，未覆盖该合并STEP路径。资产页面当前使用非合并导出降低默认UI触发概率，但导出服务保留的合并分支仍存在此问题。

建议：资产合并STEP按行对描述与预期也使用单元素精确传输，保持行级配对与整步空白；普通项目兼容行为不变。新增实际生产生成路径的合并STEP工作簿往返回归，覆盖HTML字面内容、编号、多行、两侧空白及空步骤。需同时覆盖单步骤isMerge=true，此时即使没有真正合并区域也会进入该导出分支。

证据性质：源码静态追踪，未独立执行Java工作簿复现。作者提供的38项、16项及构建结果未由本审查人重跑。

## 原P1复审

requiresExportFraming已包含normalizeHtmlBreaks差异检查；资产TEXT导出使用encodeText，restore在完整可见内容校验后解码。原非合并STEP及TEXT清洗缺口已有对应代码修订；本轮发现为仍未覆盖的合并STEP通路，不能将原问题整体关闭。

未修改生产代码、未派发测试或新建会话、未提交推送。其余权限、事务、基线全文及完整调用链还需继续审查，不把未审部分写作通过。
