import vue from '@vitejs/plugin-vue';
import { mkdtemp, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const frontend = fileURLToPath(new URL('../', import.meta.url));
const root = await mkdtemp(path.join(tmpdir(), 'msp-asset-ui-'));
const unix = (p) => p.replaceAll('\\', '/');
const files = {
  'index.html':
    '<html lang="zh-CN"><meta charset="utf-8"><title>资产组件隔离验证</title><div id="app"></div><script type="module" src="/entry.js"></script></html>',
  'store.js': 'export default () => ({ currentProjectId: "fixture-project" });',
  'permission.js':
    'export const hasAnyPermission = (keys) => keys.some(k => /^(BUSINESS_ASSET|SHARED_CONFIGURATION):/.test(k));',
  'router.js':
    'import { reactive } from "vue"; const route = reactive({ query: {} }); export const useRoute = () => route; export const useRouter = () => ({ push: ({query}) => { route.query = query || {}; } });',
  'http.js': `const rows = [{id:'fixture-config',name:'测试品牌商登录账号',systemKey:'米多大数据引擎',environmentKey:'测试环境',kind:'SECRET',secretType:'ACCOUNT_PASSWORD',currentVersion:2,parseStatus:'NOT_APPLICABLE'}];
const assets = [{id:'fixture-asset',title:'订单创建与提交业务理解',systemKey:'米多大数据引擎',moduleKey:'订单管理',flowKey:'创建订单',pageKeys:['order-create'],branchCount:12,versionNo:3,status:'CURRENT',revision:1,currentVersionId:'v3'}];
const versions = [{id:'v3',versionNo:3,status:'CURRENT',snapshot:{changeSummary:'补充库存不足校验',document:{content:'业务理解示例'},branches:[]}}];
export default {get:async({url})=>{if(url==='/shared-configurations')return rows;if(url.includes('/shared-configurations/')&&url.endsWith('/versions'))return [{versionNo:2,kind:'SECRET',secretType:'ACCOUNT_PASSWORD',parseStatus:'NOT_APPLICABLE',fileName:''}];if(url.includes('legacy-unassigned'))return [];if(url==='/business-assets')return assets;if(url.includes('/business-assets/'))return {id:'fixture-asset',versions,currentVersionId:'v3',audit:[],relations:[],revision:1};return [];},post:async({data})=>{window.__lastSubmission=data;return {id:'fixture-saved'};}};
`,
  'entry.js': `import {createApp,h,ref} from 'vue'; import Arco from '@arco-design/web-vue';import '@arco-design/web-vue/dist/arco.css';
import Business from '/@fs/${unix(path.join(frontend, 'src/views/test-asset/business-assets.vue'))}';
import Configuration from '/@fs/${unix(path.join(frontend, 'src/views/test-asset/shared-configuration.vue'))}';
import Versions from '/@fs/${unix(path.join(frontend, 'src/views/test-asset/versions.vue'))}';
import { setCustomTheme } from '/@fs/${unix(path.join(frontend, 'src/utils/theme.ts'))}'; setCustomTheme('#811fa3');
const app=createApp({setup(){const current=ref('配置');return()=>h('div',[h('header',{style:'padding:16px 24px;background:white;border-bottom:1px solid #eee'},[h('strong','组件隔离验证 · 虚构数据 '),...['业务','配置','版本'].map(n=>h('button',{onClick:()=>current.value=n,style:'margin-left:12px;padding:8px 16px'},n))]),h(current.value==='配置'?Configuration:current.value==='业务'?Business:Versions)]);}});
app.use(Arco);app.component('MsCard',{setup(_,ctx){return()=>h('section',ctx.slots.default?.());}});app.mount('#app');
`,
};
await Promise.all(Object.entries(files).map(([name, body]) => writeFile(path.join(root, name), body)));
const aliases = [
  { find: '@/api/http/index', replacement: path.join(root, 'http.js') },
  { find: '@/store/modules/app', replacement: path.join(root, 'store.js') },
  { find: '@/utils/permission', replacement: path.join(root, 'permission.js') },
  { find: 'vue-router', replacement: path.join(root, 'router.js') },
  { find: '@', replacement: path.join(frontend, 'src') },
  { find: 'vue', replacement: path.join(frontend, 'node_modules/vue/dist/vue.esm-bundler.js') },
  { find: '@arco-design/web-vue', replacement: path.join(frontend, 'node_modules/@arco-design/web-vue') },
];
const server = await createServer({
  configFile: false,
  root,
  plugins: [vue()],
  resolve: { alias: aliases, dedupe: ['vue'] },
  server: { host: '127.0.0.1', port: 5188, strictPort: true, fs: { allow: [root, frontend] } },
});
await server.listen();
process.stdout.write('Component fixture only: http://127.0.0.1:5188\n');
