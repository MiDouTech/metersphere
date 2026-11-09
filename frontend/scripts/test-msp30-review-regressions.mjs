import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { compileFunction } from 'node:vm';
import test from 'node:test';
import ts from 'typescript';
import * as Vue from 'vue';
import { renderToString } from 'vue/server-renderer';
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc';

const read = path => readFileSync(new URL('../src/' + path, import.meta.url), 'utf8');
const transpile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
function module(path, dependencies = {}) {
  const exports = {};
  compileFunction(transpile(read(path)), ['exports', 'require'])(exports, name => {
    if (!(name in dependencies)) throw new Error('Unprovided dependency: ' + name);
    return dependencies[name];
  });
  return exports;
}
const enums = module('enums/routeEnum.ts');
function permissionsFixture(grants = [], role = '') {
  const user = { isAdmin: role === 'admin', currentRole: { projectPermissions: [], orgPermissions: [], systemPermissions: grants },
    userRolePermissions: role && role !== 'admin' ? [{ userRole: { id: role, type: role === 'project_admin' ? 'PROJECT' : 'ORGANIZATION', enabled: true } }] : [],
    userRoleRelations: [{ roleId: role, sourceId: role === 'project_admin' ? 'A' : 'org' }] };
  const app = { currentProjectId: 'A', currentOrgId: 'org', currentMenuConfig: [], projectList: [] };
  const store = { useUserStore: () => user, useAppStore: () => app };
  const permissions = module('utils/permission.ts', { 'vue-router': {}, '@/router/routes/base': { INDEX_ROUTE: {} }, '@/router/routes/index': { default: [] }, '@/store': store, '@/enums/routeEnum': enums });
  const hook = module('hooks/usePermission.ts', { 'vue-router': {}, '@/store': store, '@/store/modules/setting/ai': { default: () => ({ llmUiEnabled: true }) }, '@/utils/permission': permissions }).default;
  const routes = module('router/routes/modules/setting.ts', { '@/enums/routeEnum': enums, '../base': { DEFAULT_LAYOUT: null }, '../execution-settings': { default: [] } }).default;
  const system = routes.children.find(route => route.path === 'system');
  const parameter = system.children.find(route => route.path === 'parameter');
  return { user, app, permissions, hook, routes, system, parameter };
}
for (const grant of ['SYSTEM_CONFIG_WECOM_BOT:READ', 'SYSTEM_NOTIFICATION_RULE:READ', 'SYSTEM_NOTIFICATION_LOG:READ']) {
  test('CR-01 menu ancestry and actual direct guard allow only ' + grant, async () => {
    const f = permissionsFixture([grant]);
    for (const route of [f.routes, f.system, f.parameter]) assert.equal(f.hook().accessRouter(route), true);
    let guard;
    const setup = module('router/guard/permission.ts', {
      '@/api/modules/case-management/testReport': {}, '@/hooks/usePermission': { default: f.hook },
      '@/store/modules/app': { default: () => f.app }, '@/store/modules/setting/ai': { default: () => ({ llmUiEnabled: true }) },
      '@/enums/routeEnum': enums, '../constants': { featureRouteMap: {}, NO_RESOURCE_ROUTE_NAME: 'noPermission', WHITE_LIST: [] },
      nprogress: { default: { done() {} } },
    }).default;
    setup({ beforeEach(callback) { guard = callback; } });
    let result = 'not-called';
    await guard({ ...f.parameter, query: {} }, {}, value => { result = value; });
    assert.equal(result, undefined);
    assert.equal(f.permissions.hasAnyPermission(['SYSTEM_PARAMETER_SETTING_BASE:READ'], ['SYSTEM']), false);
    assert.equal(f.permissions.hasAnyPermission(['SYSTEM_CONFIG_WECOM_BOT:UPDATE'], ['SYSTEM']), false);
  });
}
for (const role of ['', 'project_admin', 'org_admin', 'admin']) {
  test('CR-01 system/parameter route boundary for ' + (role || 'unprivileged'), () => {
    const f = permissionsFixture([], role);
    for (const route of [f.system, f.parameter]) assert.equal(f.hook().accessRouter(route), role === 'admin');
    // Notification subgroups keep their system-only checks; project/admin bypass cannot grant Wecom updates.
    assert.equal(f.permissions.hasAnyPermission(['SYSTEM_CONFIG_WECOM_BOT:UPDATE'], ['SYSTEM']), role === 'admin');
  });
}
test('CR-01 managed hidden system route still rejects direct access', () => {
  const f = permissionsFixture(['SYSTEM_NOTIFICATION_RULE:READ']);
  f.user.uiPermissions = { system: { managedRoutes: [f.parameter.name], visibleRoutes: [] } };
  assert.equal(f.hook().accessRouter(f.parameter), false);
});

function scriptHarness(path, exposed, dependencies) {
  const script = read(path).match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^\s*import[\s\S]*?;/gm, '');
  const output = ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
  return compileFunction(output + ';return {' + exposed + '};', Object.keys(dependencies))(...Object.values(dependencies));
}
function deferred() { let resolve, reject; const promise = new Promise((a,b) => { resolve=a;reject=b; });return { promise,resolve,reject }; }
const wecomPath = 'views/setting/system/config/components/wecomBotConfig.vue';
function wecomFixture() {
  const calls = [], successes = [];
  const f = permissionsFixture(['SYSTEM_CONFIG_WECOM_BOT:UPDATE']);
  const api = scriptHarness(wecomPath, 'sendUserTest,sendGroupTest,testUserId,loading,loadError,config,pending', {
    ref: Vue.ref, reactive: Vue.reactive, computed: Vue.computed, useAppStore: () => f.app, useI18n: () => ({ t: x => x }), onMounted() {}, hasAnyPermission: f.permissions.hasAnyPermission,
    Message: { success(message) { successes.push(message); }, warning() {} },
    testWecomUser(id) { const response=deferred();calls.push({ type:'user',id,response });return response.promise; },
    testWecomGroup(id) { const response=deferred();calls.push({ type:'group',id,response });return response.promise; },
  });
  api.loading.value=false;api.config.value.enabled=true;api.testUserId.value='user-A';
  return { api,calls,successes };
}
for (const kind of ['user','group']) for (const outcome of ['success','failure']) {
  test('CR-02 ' + kind + ' delayed ' + outcome + ' deduplicates, releases pending and permits retry', async () => {
    const f=wecomFixture();
    const send=()=>kind==='user'?f.api.sendUserTest():f.api.sendGroupTest({ id:'chat-A',active:true });
    const first=send(); const settled=Promise.allSettled([first]);
    await send(); assert.equal(f.calls.length,1);assert.equal(f.api.pending.size,1);
    if(outcome==='success')f.calls[0].response.resolve();else f.calls[0].response.reject(new Error('isolated failure'));
    const result=await settled; assert.equal(result[0].status,outcome==='success'?'fulfilled':'rejected');
    assert.equal(f.successes.length,outcome==='success'?1:0);assert.equal(f.api.pending.size,0);
    const retry=send();assert.equal(f.calls.length,2);f.calls[1].response.resolve();await retry;
    assert.equal(f.api.pending.size,0);
  });
}
test('CR-02 different users, groups and operation types remain independent', async () => {
  const f=wecomFixture();const running=[f.api.sendUserTest()];f.api.testUserId.value='user-B';running.push(f.api.sendUserTest());
  running.push(f.api.sendGroupTest({id:'chat-A',active:true}),f.api.sendGroupTest({id:'chat-B',active:true}));
  assert.equal(f.calls.length,4);assert.equal(f.api.pending.size,4);f.calls.forEach(c=>c.response.resolve());await Promise.all(running);
  assert.deepEqual(f.calls.map(c=>c.id),['user-A','user-B','chat-A','chat-B']);
});
for (const state of ['loading','loadError','disabled','permission']) {
  test('CR-02 no test writes while ' + state, async () => {
    const f=wecomFixture();
    if(state==='loading'||state==='loadError')f.api[state].value=true;
    if(state==='disabled')f.api.config.value.enabled=false;
    if(state==='permission') { /* Separate read-only fixture uses the actual system permission resolver below. */ }
    if(state!=='permission') await f.api.sendUserTest();
    if(state==='loading'||state==='loadError')await f.api.sendGroupTest({id:'chat-A',active:true});
    if(state==='disabled')await f.api.sendGroupTest({id:'chat-A',active:false});
    if(state!=='permission')assert.equal(f.calls.length,0);
    else {
      const grants=permissionsFixture(['SYSTEM_CONFIG_WECOM_BOT:READ']);let writes=0;
      const api=scriptHarness(wecomPath,'sendUserTest,sendGroupTest,loading,config,testUserId',{
        ref: Vue.ref, reactive: Vue.reactive, computed: Vue.computed,useAppStore:()=>grants.app,useI18n:()=>({t:x=>x}),onMounted(){},hasAnyPermission:grants.permissions.hasAnyPermission,
        Message:{warning(){},success(){}},testWecomUser(){writes++;},testWecomGroup(){writes++;},
      });
      api.loading.value=false;api.config.value.enabled=true;api.testUserId.value='user';
      await api.sendUserTest();await api.sendGroupTest({id:'chat',active:true});assert.equal(writes,0);
    }
  });
}

const listPath='views/project-management/messageManagement/components/messageList.vue';
function receiverFixture() {
  let persisted=[{id:'A',name:'Alice'}],fail=true,reloads=0,saves=0,rows=[],heldSave;
  const persistedOther=[{id:'other',name:'Other'}];
  const errors=[],successes=[];let table;
  const api=scriptHarness(listPath,'initialize,changeMessageReceivers,cancelReceiverDraft,updateReceiverDraft,receiverDrafts,receiverPending,handleChangeIntercept,editRobot',{
    ref: Vue.ref, reactive: Vue.reactive, computed: Vue.computed,defineEmits:()=>()=>{},useRouter:()=>({push(){throw new Error('Cannot edit template with unsaved receiver draft');}}),
    useI18n:()=>({t:x=>x}),hasAnyPermission:()=>true,watch(){},onBeforeMount(){},
    Message:{warning(){},success(m){successes.push(m);},error(m){errors.push(m);}},
    getMessageUserList:async()=>[],getRobotList:async()=>[],
    getMessageList:async()=>[{type:'module',name:'module',messageTaskTypeDTOList:[{taskType:'BUG_TASK',taskTypeName:'Bug',messageTaskDetailDTOList:[{event:'UPDATE',receivers:persisted.map(x=>({...x})),projectRobotConfigMap:{site:{robotId:'site',enable:true}}},{event:'DELETE',receivers:persistedOther.map(x=>({...x})),projectRobotConfigMap:{site:{robotId:'site',enable:true}}}]}]}],
    saveMessageConfig:async(input)=>{saves++;if(input.event==='DELETE')return;if(heldSave){const gate=heldSave;heldSave=undefined;await gate.promise;}else if(fail)throw new Error('isolated save failure');persisted=input.receiverIds.map(id=>({id,name:id}));},
    useTable(fetch,options,transform) {
      table={propsRes:Vue.ref({loading:false}),propsEvent:{},setLoadListParams(){},async loadList(){reloads++;rows=(await fetch({})).map(transform);}};
      return table;
    },
  });
  return {api,get row(){return rows[0].children[0];},get otherRow(){return rows[0].children[1];},get persisted(){return persisted;},get reloads(){return reloads;},get saves(){return saves;},errors,successes,
    allowSave(){fail=false;},holdNextSave(){heldSave=deferred();return heldSave;},reload(){return table.loadList();}};
}
test('CR-03 failed A-to-B edit stays visible across GET; retry saves B then reads persisted B', async () => {
  const f=receiverFixture();await f.api.initialize();let row=f.row;row.receivers=[{id:'B',name:'Bob'}];
  assert.equal(await f.api.changeMessageReceivers(false,row,'receiver'),false);
  assert.equal(f.reloads,1);assert.equal(f.row.receivers[0].id,'B');assert.equal(f.persisted[0].id,'A');
  assert.equal(f.api.receiverDrafts.size,1);assert.equal(f.errors.length,1);assert.equal(f.successes.length,0);
  await f.reload();row=f.row;assert.equal(row.receivers[0].id,'B');assert.equal(row.persistedReceivers[0].id,'A');
  assert.equal(await f.api.handleChangeIntercept(false,row,'site'),false);f.api.editRobot(row,'site');assert.equal(f.saves,1);
  f.allowSave();assert.equal(await f.api.changeMessageReceivers(false,row,'receiver'),true);
  assert.equal(f.persisted[0].id,'B');assert.equal(f.row.receivers[0].id,'B');assert.equal(f.api.receiverDrafts.size,0);assert.equal(f.successes.length,1);
});
test('CR-03 cancelling failed edit restores persisted A without writing or reloading',async()=>{
  const f=receiverFixture();await f.api.initialize();f.row.receivers=[{id:'B',name:'Bob'}];
  await f.api.changeMessageReceivers(false,f.row,'receiver');const before=f.reloads;
  f.api.cancelReceiverDraft(f.row);assert.equal(f.row.receivers[0].id,'A');assert.equal(f.persisted[0].id,'A');
  assert.equal(f.api.receiverDrafts.size,0);assert.equal(f.saves,1);assert.equal(f.reloads,before);
});
for (const path of [wecomPath,listPath]) test('changed SFC script/template compile: '+path,()=>{
  const {descriptor,errors}=parse(read(path));assert.deepEqual(errors,[]);const script=compileScript(descriptor,{id:path});
  assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:path,id:path,compilerOptions:{bindingMetadata:script.bindings,expressionPlugins:['typescript']}}).errors,[]);
});



for(const grant of ['SYSTEM_CONFIG_WECOM_BOT:READ','SYSTEM_NOTIFICATION_RULE:READ','SYSTEM_NOTIFICATION_LOG:READ']) {
  test('CR-01 preserves Wecom subtab and subgroup boundaries for '+grant,async()=>{
    const f=permissionsFixture([grant]);
    const api=scriptHarness(wecomPath,'activeKey,canReadConfig,canReadRules,canReadLogs,canUpdate,canUpdateRules,canDeleteRules,canRetry',{
      ref:Vue.ref,reactive:Vue.reactive,computed:Vue.computed,onMounted(){},useAppStore:()=>f.app,useI18n:()=>({t:x=>x}),hasAnyPermission:f.permissions.hasAnyPermission,
    });
    assert.equal(api.activeKey.value,grant.includes('WECOM_BOT')?'config':grant.includes('_RULE')?'rules':'logs');
    for(const name of ['canUpdate','canUpdateRules','canDeleteRules','canRetry'])assert.equal(api[name].value,false);
    const path='views/setting/system/config/components/messageNotification.vue';
    const bindings=scriptHarness(path,'userStore,route,canWecom,section',{
      ref:Vue.ref,computed:Vue.computed,watch:Vue.watch,useRoute:()=>({query:{}}),useUserStore:()=>f.user,
      hasAnyPermission:f.permissions.hasAnyPermission,hasTabVisible:f.permissions.hasTabVisible,
    });
    assert.equal(bindings.canWecom.value,true);
    const compiled=compileTemplate({source:parse(read(path)).descriptor.template.content,filename:path,id:path});assert.deepEqual(compiled.errors,[]);
    const code=compiled.code.replace(/import\s*\{([^}]+)\}\s*from ["']vue["']/g,(_all,names)=>`const {${names.replace(/\bas\b/g,':')}}=Vue;`).replace('export function render','function render');
    const render=compileFunction(code+';return render;',['Vue'])(Vue);
    const app=Vue.createSSRApp(Vue.defineComponent({setup:()=>bindings,render}));
    const wrapper=Vue.defineComponent({props:['title'],setup(props,{slots}){return()=>Vue.h('div',[props.title,slots.default?.()]);}});
    for(const name of ['a-alert','a-tabs','a-tab-pane','Wecom','Webhook','Messages','TemplateEditor'])app.component(name,wrapper);
    const html=await renderToString(app);assert.ok(html.includes('企微智能机器人'));assert.ok(!html.includes('Webhook测试计划推送'));assert.ok(!html.includes('消息管理'));
  });
}

test('CR-03 editing a failed draft again keeps the newest selection across unrelated reloads', async()=>{
  const f=receiverFixture();await f.api.initialize();f.row.receivers=[{id:'B',name:'Bob'}];
  await f.api.changeMessageReceivers(false,f.row,'receiver');f.row.receivers=[{id:'C',name:'Carol'}];
  f.api.updateReceiverDraft(f.row,f.row.receivers);await f.reload();
  assert.equal(f.row.receivers[0].id,'C');assert.equal(f.persisted[0].id,'A');
  f.api.cancelReceiverDraft(f.row);assert.equal(f.row.receivers[0].id,'A');
});

for (const outcome of ['success','failure']) {
  test('CR-04 failed draft retry survives other-row GET and releases current row after '+outcome,async()=>{
    const f=receiverFixture();await f.api.initialize();f.row.receivers=[{id:'B',name:'Bob'}];
    await f.api.changeMessageReceivers(false,f.row,'receiver');assert.equal(f.saves,1);
    const gate=f.holdNextSave();const stale=f.row;
    const retry=f.api.changeMessageReceivers(false,stale,'receiver');assert.equal(stale.loading,true);
    assert.equal(f.api.receiverPending.has(stale.key),true);
    // An independent business row saves successfully and triggers the actual list transformation.
    assert.equal(await f.api.changeMessageReceivers(false,f.otherRow,'receiver'),true);
    const refreshed=f.row;assert.notEqual(refreshed,stale);assert.equal(refreshed.loading,true);
    // MsSelect owns a loading v-model too: the stable operation guard must survive a local reset.
    refreshed.loading=false;
    assert.equal(await f.api.changeMessageReceivers(false,refreshed,'receiver'),false);
    const calls=f.saves;f.api.cancelReceiverDraft(refreshed);f.api.cancelReceiverDraft(stale);
    assert.equal(f.saves,calls);assert.equal(calls,3); // initial failure, ONE retry, independent row
    assert.equal(f.api.receiverDrafts.has(refreshed.key),true);assert.equal(refreshed.receivers[0].id,'B');
    if(outcome==='success')gate.resolve();else gate.reject(new Error('isolated retry failure'));
    assert.equal(await retry,outcome==='success');assert.equal(f.api.receiverPending.size,0);
    assert.equal(stale.loading,false);assert.equal(f.row.loading,false);
    assert.equal(f.row.receivers[0].id,'B');
    if(outcome==='success') {
      assert.equal(f.persisted[0].id,'B');assert.equal(f.api.receiverDrafts.size,0);
      assert.equal(f.row.persistedReceivers[0].id,'B');
    } else {
      assert.equal(f.persisted[0].id,'A');assert.equal(f.api.receiverDrafts.size,1);
      // The old callback must unlock the newly mounted row so another retry is now allowed.
      f.allowSave();assert.equal(await f.api.changeMessageReceivers(false,f.row,'receiver'),true);
      assert.equal(f.persisted[0].id,'B');assert.equal(f.api.receiverPending.size,0);
    }
  });
  test('CR-04 initial save callback restores rebuilt row on '+outcome,async()=>{
    const f=receiverFixture();await f.api.initialize();const stale=f.row;stale.receivers=[{id:'B',name:'Bob'}];
    const gate=f.holdNextSave();const save=f.api.changeMessageReceivers(false,stale,'receiver');
    await f.reload();assert.equal(f.row.loading,true);assert.notEqual(f.row,stale);
    assert.equal(await f.api.changeMessageReceivers(false,f.row,'receiver'),false);assert.equal(f.saves,1);
    if(outcome==='success')gate.resolve();else gate.reject(new Error('isolated initial save failure'));
    assert.equal(await save,outcome==='success');assert.equal(f.row.loading,false);assert.equal(stale.loading,false);
    assert.equal(f.row.receivers[0].id,'B');assert.equal(f.api.receiverPending.size,0);
    if(outcome==='failure') {
      f.api.cancelReceiverDraft(f.row);assert.equal(f.row.receivers[0].id,'A');assert.equal(f.persisted[0].id,'A');
    } else assert.equal(f.persisted[0].id,'B');
  });
}
