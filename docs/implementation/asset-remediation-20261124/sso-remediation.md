# MSP SSO 会话整改（开发草稿，2026-11-24）

> 本文件为讨论与诊断历史。用户随后批准“SSO仅验证身份、MSP独立管理14天及多浏览器会话”；当前实施范围以 sso-independent-session-plan.md 为准，结果见 sso-independent-session-verification.md。下文全局退出依赖不再阻塞当前已批准方案。

## 最新用户调整（覆盖下文此前的业务隔离要求）

2026-11-24用户明确：主动退出MSP时，同时退出其他通过SSO登录的米多业务，保证再次登录需要新的有效SSO。仍不允许修改其他仓库。此前“不得影响其他米多业务”的要求已被本条替代；此前观察及分析保留为历史依据。独立多浏览器登录不互踢和MSP自行管理14天会话的目标保留。

### 现有能力核对

- 本机参考SSO提供 POST /api/auth/logout，使用米多身份 access JWT；处理当前JWT黑名单、设备会话，并调用revokeAllUserSessions吊销该用户第三方应用令牌。
- MSP目前仅持有应用sessionToken和应用签名密钥，没有该身份access JWT；它们不能互换。后端不能拿应用令牌调用身份logout冒充用户。
- 本机参考前端logout会清理该域浏览器本地身份。MSP受浏览器同源限制不能直接清理米多域本地凭据。
- 尚未发现可由MSP跳转调用、完成全局退出并安全回跳的公开浏览器入口；也未发现应用签名可调用的全局退出接口。不得凭空拼接 /logout 地址或把revoke-session-token声称为全局退出。

### 最小外部依赖

需要SSO负责人提供已部署且受支持的浏览器全局退出入口（包含回跳白名单和完整失效语义），或现有应用端全局撤销协议。无需发送Token、密码、Cookie或密钥。如果当前SSO没有该能力，则在“仅修改MSP”约束下无法保证完整全局退出。

此前登录记录仅证明本地退出及 SSO 自动回流，不能证明主动退出后的重新验证满足需求。按本轮用户要求，将该验收判定更正为失败，不删除原始观察证据。

|需求|前端入口/实现|后端链路|数据与配置|验证|
|---|---|---|---|---|
|主动退出后必须扫码或手机验证码验证|账户菜单→useUser.logout→登录页重新登录|signout→MiduoSsoApplicationService→SSO应用授权撤销；重新登录桥须支持强制认证|用户本轮明确：同一有效SSO授权派生的其他MSP会话同时退出，独立授权及其他米多业务系统不受影响|真实退出、同授权MSP旧会话拒绝、独立授权保留、其他米多系统保留、SSO重新验证、上游失败时不能假报成功|
|不同浏览器登录不互踢|各浏览器独立凭据|Shiro/Spring Session→MiduoSsoSessionStore|当前源码按实例+用户+sessionId隔离，需检查部署差异|A/B登录后分别读取；A退出或续期不影响B|
|14天上限内正确续期|登录态维护及恢复页面时校验|is-login→MiduoSsoRefreshService→上游refresh|MSP空闲TTL、绝对14天期限、SSO8小时TTL分别校验|短时钟边界、续期失败、缺失期限、并发刷新、超期必须交互认证|

## 已确认的源码事实

- 当前 logout 仅删除本地 SSO 存储，未调用现有 revokeSessionToken。
- SSO revoke-session-token 吊销的是应用 sessionToken。读取本机米多后端参考实现可见，它不清除浏览器登录 SSO 的身份会话；因此仅补调用 revoke 仍不能保证重新扫码。
- 本机参考 SSO 登录桥只接收 appCode、redirectUri、state，没有已声明的强制认证参数。不能编造 forceLogin 参数后宣称生效。
- refresh 只由 is-login 触发；SSO存储TTL受上游过期时间约束。空闲至TTL过期后令牌消失，已无法续期；仅加长MSP的Spring Session期限不能延长上游授权。
- 当前源码已有按浏览器sessionId隔离的存储和14天绝对上限。部署中的互踢现象仍需定位，不以既有单元测试代替真实双浏览器验收。

## 边界

不得通过清空全部用户会话解决当前浏览器退出，不得以无限期限或忽略SSO失效解决续期。上游SSO能力以正式协议或运行证据为准；本机另一仓库仅作为参考，未经验证不代表当前部署版本。

## 联动实施所需协议

1. 交换令牌校验结果提供服务端可信的原始授权标识与认证时间；不能使用前端传入值或用户ID替代。
2. 撤销粒度为应用MSP+原始授权；同授权派生MSP会话统一失效，不调用米多全局logout/revokeAllUserSessions。
3. 撤销后，旧SSO身份在MSP登录桥不得静默换取新令牌；要求重新扫码或手机验证，通过后创建新授权。其他应用免登不改变。
4. SSO续期响应包含可信绝对到期时间；本地14天上限不可被轮换令牌重置。并发刷新与撤销需原子顺序保证，撤销后不得被迟到刷新复活。
5. 后台续期只处理仍有有效MSP授权的记录，分布式锁、过期及失败重试有界；不得依赖浏览器持续发请求以实现离线保留。

## 当前阻塞证据

本机参考米多后端的 OpenSsoValidateLoginTokenResponse 不含原始授权标识；ThirdPartySsoServiceImpl 每次交换创建独立应用sessionToken，revokeSessionToken只删除该令牌；SSO桥不提供已声明的强制认证能力。全局AuthController.logout会revokeAllUserSessions，违反其他米多业务不受影响的要求。需要确认并授权SSO对应仓库，或由SSO负责人提供现有正式协议；不能在MSP端假设该协议已存在。
