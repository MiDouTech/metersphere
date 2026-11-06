import {createApp,ref,computed} from 'vue';
import Arco from '@arco-design/web-vue';import '@arco-design/web-vue/dist/arco.css';
import Toolbar from '../../src/components/business/plan-review/listToolbar.vue';
import ReadOnly from '../../src/components/business/plan-review/readOnlyCard.vue';
import {fixture,rows} from './mock.js';
createApp({components:{Toolbar,ReadOnly},setup(){const selected=ref([]),context=ref(0),loading=ref(false);return {fixture,selected,context,loading,rows:computed(()=>rows[fixture.side]),reload:async()=>{},clear:()=>{selected.value=[];},changeSide(){selected.value=[];fixture.side=fixture.side==='plan'?'review':'plan';context.value++;}};},template:`<main>
<h1>MSP-12 列表关联 v1.4 · 实际组件隔离自测</h1><p>使用仓库 Vue / Arco 组件，模拟接口与业务数据；不是部署验收。</p>
<button @click="changeSide">切换列表侧</button><button @click="selected=[];context++">刷新清选</button><button @click="fixture.state='FAILED_MANUAL';context++">模拟耗尽</button><button @click="fixture.state='WAITING_RETRY';context++">模拟自动等待</button>
<h2>{{fixture.side==='plan'?'测试计划':'评审计划'}}列表</h2><p>已选择 {{selected.length}} 条</p>
<Toolbar :side="fixture.side" :rows="rows" :selected-ids="selected" project="project" :loading="loading" :context="String(context)" :reload="reload" @clear="clear"/>
<table><thead><tr><th><input type="checkbox" aria-label="本页全选" @change="selected=$event.target.checked?rows.map(x=>x.id):[]"></th><th>编号</th><th>名称</th><th>状态</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td><input type="checkbox" v-model="selected" :value="row.id" :aria-label="'勾选 '+row.num"></td><td>{{row.num}}</td><td><span :data-relation-result="fixture.side+'-'+row.id" tabindex="-1">{{row.name}}</span></td><td>{{row.status==='PREPARED'?'未开始':'进行中'}}</td></tr></tbody></table>
<h2>详情只读区</h2><ReadOnly :key="fixture.side+context+JSON.stringify(fixture.pair)" :side="fixture.side" :object-id="rows[0].id"/>
</main>`}).use(Arco).mount('#app');
const style=document.createElement('style');style.textContent='body{font-family:Arial,"Microsoft Yahei";margin:24px;color:#272339}main{max-width:1360px;margin:auto}table{border-collapse:collapse;width:100%;margin:24px 0}th,td{padding:16px;text-align:left;border-bottom:1px solid #ddd}button{margin:4px}h1{font-size:24px}.flex{display:flex}.flex-wrap{flex-wrap:wrap}.items-center{align-items:center}.gap-2{gap:8px}.w-full{width:100%}.text-xs{font-size:12px}.mt-3{margin-top:12px}.arco-modal{max-width:calc(100vw - 32px)}.arco-modal-body{max-height:65vh;overflow:auto}';document.head.appendChild(style);
