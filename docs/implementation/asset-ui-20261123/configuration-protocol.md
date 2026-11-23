# 公共配置结构化敏感字段协议（开发草稿）
版本：v1.0；日期：2026-11-23。本文件描述本次实现，不代表用户对本文整版批准或运行验收通过。
需求依据：本会话六项优化要求、左操作右检索补充，以及“现在开始完成方案实现”的实施授权。产品基线仍为已批准的业务资产重建文档 v1.0。

## 保存与批量导入
POST /shared-configurations 和 /shared-configurations/batch 保留 kind=PLAIN / SECRET / DOCUMENT。
SECRET 新增可选 secretType：
- ACCOUNT_PASSWORD：value 为 JSON 字符串，fields 数组必须包含 username 与 password。
- OTHER：value 为同样的 JSON 字符串，自定义字段1至20行。
- 缺省或空字符串：兼容原单个敏感值协议，value 保持原样；UI 将它显示为“其他敏感配置”，不能据此猜测内部结构。

结构示例（虚构，禁止填写真实秘密到文档）：

    {"kind":"SECRET","secretType":"ACCOUNT_PASSWORD","value":"{\"fields\":[{\"name\":\"username\",\"value\":\"fixture-user\"},{\"name\":\"password\",\"value\":\"fixture-password\"}]}","expectedVersion":0}

数组上限20行，账号密码类占其中两行。名称为1至64字符、大小写敏感、不可重复；前端去除名称两端空格，后端拒绝未规范名称。值非空且最多100000字符，不裁剪实际秘密。完整内容仍受2 MB限制。服务端拒绝未知类型、多余结构字段、重复字段、重复 JSON 成员、尾随内容及空内容。

编辑已有 SECRET 时，可以发送 keepSecret=true 并省略 value / secretType。服务端验证项目、写权限、旧类型和 expectedVersion，在事务内读取旧密文并为新版本重新加密。不能同时提交替换值；新建不能保留旧值。页面不读取明文，选择替换时填写完整字段。
普通配置及文档不能指定非空 secretType。新建 expectedVersion=0，编辑为列表当前版本；冲突不覆盖。

## 元信息与执行器
列表和历史版本只增加 secretType 元信息，不返回敏感字段名或值；读取 SECRET 版本仍返回 ******，document=true 也不能绕过。
会话绑定的 /shared-configurations/{id}/resolve 仍需执行器身份、项目权限、运行中会话和固定引用。value 字段保持字符串类型，并附带 secretType：
- 非空分类表示结构化 JSON，执行器按 fields 精确读取所需字段。
- 空分类表示旧单值，禁止仅凭 value 看似 JSON 就自动拆解。
DOCUMENT 的 value 仍为 Base64。不得将 resolve 响应写入普通提示、事件、日志或截图。该接口仍设置 Cache-Control: no-store。已有运行会话保持启动时固定版本。

## 版本切换、迁移与回退
版本切换请求可增加 reason，最长500字符；新 UI 必填并确认，旧客户端缺省时记录兼容原因。切换仅改变当前版本指向，不删除历史；请求同一当前版本是无副作用操作，不重复记录切换审计。
157迁移只追加版本 secret_type 和审计 reason 两列，默认空字符串；不重新加密或猜测拆解旧值。先执行迁移，再使用新应用。旧程序可忽略追加列，但不会理解结构化字段，回退前须核对执行器兼容性；不删除新列或已有版本。

## 诊断
参数错误、权限错误、版本冲突与加解密失败复用现有安全错误结构。结构验证日志仅记录事件、阶段、错误分类与安全根因类型，不记录输入或异常堆栈；HTTP边界提供关联 traceId。
故障注入测试覆盖畸形 JSON、字段错误、跨项目拒绝、旧版本冲突、密钥和密文错误。未新增外部监控或告警平台。
