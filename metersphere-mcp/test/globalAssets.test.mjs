import test from 'node:test';
import assert from 'node:assert/strict';
import { z } from 'zod';
import { dataAssetTools } from '../dist/tools/dataAssets.js';
import { businessAssetTools } from '../dist/tools/businessAssets.js';
import { sharedConfigurationTools } from '../dist/tools/sharedConfigurations.js';

test('data management forwards every supported operation and preserves native safe errors', async () => {
 const tool=dataAssetTools[0];
 for(const action of ['list','detail','content','versions','create','update','delete','batch','job','retry_job','upload','replace_upload','download','legacy_inventory','legacy_import']) {
  const args={sourceProjectId:'authorized-project',action,id:'asset',expectedRevision:3,requestId:'stable',base64:'Zml4dHVyZQ==',data:{name:'fixture'}};
  z.object(tool.inputSchema).parse(args);
  const response={isError:action==='delete',content:[{type:'text',text:JSON.stringify({code:action==='delete'?'ASSET_REFERENCED':'SUCCESS'})}]};
  assert.equal(await tool.handler({callNativeTool:async(name,body)=>{assert.equal(name,'metersphere.data_asset.manage');assert.deepEqual(body,args);return response;}},args),response);
 }
 assert.throws(()=>z.object(tool.inputSchema).parse({action:'list'}));
});
test('global business and configuration tools accept explicit context without legacy projectId',async()=>{
 for(const tool of [...businessAssetTools,...sharedConfigurationTools].filter(t=>t.name==='metersphere.business_asset.search'||t.name==='metersphere.shared_configuration.list')){
  const args={sourceProjectId:'p'};
  z.object(tool.inputSchema).parse(args);
  await tool.handler({callNativeTool:async(name,body)=>{assert.equal(name,tool.name);assert.deepEqual(body,args);return {content:[]};}},args);
 }
});

test('data CRUD upload replacement batch and download use authenticated native HTTP without changing context', async t => {
 const http=await import('node:http');
 const {MeterSphereClient}=await import('../dist/client.js');
 const calls=[];
 const server=http.createServer(async(req,res)=>{
  let raw='';for await(const chunk of req)raw+=chunk;
  const body=JSON.parse(raw);calls.push({path:req.url,body,token:req.headers.authorization});
  res.setHeader('Content-Type','application/json');res.end(JSON.stringify({jsonrpc:'2.0',id:body.id,result:{content:[{type:'text',text:JSON.stringify({action:body.params.arguments.action,revision:2})}]}}));
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 t.after(()=>new Promise(resolve=>server.close(resolve)));
 const client=new MeterSphereClient({baseUrl:`http://127.0.0.1:${server.address().port}`,agentToken:'synthetic-token',projectId:'legacy-default'});
 for(const action of ['create','detail','update','delete','upload','replace_upload','batch','download']){
  const args={sourceProjectId:'explicit-context',action,id:'asset',expectedRevision:1,requestId:'stable',base64:'Zml4dHVyZQ==',items:[{id:'asset',expectedRevision:1}],offset:0,chunkSize:1024};
  const result=await dataAssetTools[0].handler(client,args);
  assert.equal(result.isError,undefined);
  const call=calls.at(-1);assert.equal(call.path,'/api/mcp');assert.equal(call.token,'Bearer synthetic-token');
  assert.deepEqual(call.body.params,{name:'metersphere.data_asset.manage',arguments:args});
 }
 assert.equal(calls.length,8);
});

test('record actions forward immutable version keys and idempotency using the existing token contract', async()=>{
 const tool=dataAssetTools[0];
 for(const action of ['records','copy_records','mark_records_used']){
  const args={sourceProjectId:'org-context',action,id:'asset',versionId:'version',recordKeys:['hash:1','hash:2'],requestId:'stable',page:1,pageSize:100};
  z.object(tool.inputSchema).parse(args);
  await tool.handler({callNativeTool:async(name,body)=>{assert.equal(name,'metersphere.data_asset.manage');assert.deepEqual(body,args);assert.equal(body.scope,undefined);return {content:[]};}},args);
 }
});

test('data upload schema accepts 30MiB base64 boundary and rejects overflow',()=>{
 const schema=z.object(dataAssetTools[0].inputSchema);const args={sourceProjectId:'context',action:'upload',fileName:'data.txt',requestId:'same'};
 assert.doesNotThrow(()=>schema.parse({...args,base64:'A'.repeat(2666676)}));
 assert.doesNotThrow(()=>schema.parse({...args,base64:'A'.repeat(41943040)}));
 assert.throws(()=>schema.parse({...args,base64:'A'.repeat(41943041)}));
});
