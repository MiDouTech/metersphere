# MSP-38 核查记录（开发草稿）

## 环境和执行会话

2026-11-25，正式MCP项目搜索MSP返回0项；无关键词分页读取共70个可见项目，未包含测试项目名MSP需求验收-20261023。测试MCP精确搜索返回项目1704667279923322880。不能将测试ID在正式环境不可访问直接判为管理员权限故障；未向无关正式项目提单，未改变权限。

原会话b6f64333-54b1-4dd2-b36b-09ea7c7bed30通过当前测试MCP的executor.event提交COMPLETE/PASSED，requestKey=msp38-handoff-close-20261125-v1，stage=PINNED_VERSION_TEST_COMPLETE，errorCategory=NONE，返回recorded=true。仅结束原固定版本测试分支，不代表MSP-38验收通过。随后测试站执行器监控页面回读R1125-retry-local为PASSED，见session-readback.txt/png。未改库、未转移Token、未放宽所有权校验。当前连接能够结束，交接的旧403状态不再作为当前阻塞。

## MSP-28查重

已读取MSP-28在线任务。旧问题包含管理员导入联动权限、预检后提交失败、Webhook、计划切换、资产任务状态及等级映射；没有本次金额20.50误拆与编号残留的明确根因证据，不把两者合并为同根因。

## 目录基准

已完整读取获批产品文档MSP-业务资产重建与受控执行清理-产品文档-v1.0-20261119.md及control-inventory（ASSET-AUDIT-20261123-01）。现行文档规定用例资产Tab，未明确项目/系统双标签；当前路由project/system均载入cases.vue，正文统一目录。旧历史方案存在双视角要求，仅凭当前文档不能证明该要求被整版撤销。用户随后在当前聊天明确要求恢复项目/系统双目录及对应分类；实现与验证见verification.md。

## 原始证据

已从MSP-38任务附件下载原始4,951,226字节证据包。读取export-content.json与case-api-suite.json确认两行导出人员为空、详情含陈启芬；export.xlsx复制为测试资源（原字节未改）。原文件STEP行含[1]编号与20.50金额；新回归通过真实EasyExcel监听器解析。测试夹具最初只提供P1选项，导致原P0行被校验拒绝，已补齐P0后两行解析通过。此失败不是产品新缺陷。
