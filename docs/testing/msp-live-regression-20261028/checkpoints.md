# 今日提交部署回归

基准：用户Excel预检修复及单计划展示需求；提交808be981dd，2026-10-28本地日志当日唯一提交。环境https://msp.ebcone.net，用户提供账号的现有角色；不记录登录凭据。未提供独立输入用例，以下由需求派生。计划视觉基准为需求规定的单计划下拉；旧隔离截图仅作参考，不作线上像素基准。

|检查点|入口/操作|预期|方式/标注|结果|
|---|---|---|---|---|
|EXCEL-01a|测试用例→Excel导入原V4工作簿→预检|报告表不影响用例预检，展示真实逐行结果|DOM/接口/截图①|失败；original-precheck.json、raw/original-precheck.png|
|EXCEL-01b|导入仅报告表的文件|可理解错误，保留trace，无重复提示|DOM/接口；错误展示同①、说明区域②|失败；report-only-precheck.json；②说明缺失另见标注图|
|EXCEL-01c|有效预检→确认导入→刷新|隔离项目内用例实际持久化且不导入报告行|DOM/接口/数据|阻塞：原文件预检失败，未提交导入|
|IMPORT-STOP-01a|本轮创建任务→停止|正常停止，无policy误校验|接口/DOM③|失败；stop-ui.json、raw/stop-ui.png（未标注）|
|IMPORT-STOP-01b|本轮任务重试|无需无关策略参数；按合法状态处理|接口/DOM|失败；retry-ui.json|
|OVERVIEW-01a|多计划项目测试用例列表→下拉切换|仅一个计划进度，切换正确，不隐式筛选用例|DOM/截图④|失败；overview-live.json、cache-disabled-reload.json、annotated/04-overview.svg|
|OVERVIEW-01b|刷新/筛选/空数据/项目切换|有效选择保留，失效回退，空显示横线，切项目重置|DOM/接口|阻塞：下拉入口缺失，选择生命周期无法验证|
|OVERVIEW-01c|长名称/当前视口布局|按钮可见，进度不重叠，完整名称可访问|视觉/DOM⑤|阻塞：新组件缺失；旧页面长名称截断，未替代新组件验收|

测试写入限隔离项目或本轮明确创建的记录；不删除既有业务数据。前端导入对应POST /ai/case-imports/precheck、/{id}/commit、/{id}/stop、/{id}/retry；计划概览复用用例列表关联计划数据。无产品代码改动，构建/容器检查不属于本轮测试脚本交付验证。执行工具按用户指定使用ego，不另起Playwright浏览器；记录这一工具差异。
