import {test} from 'node:test';
import assert from 'node:assert/strict';
import {MsApiError, MeterSphereClient} from '../dist/client.js';
import http from 'node:http';
import {caseLibraryTools} from '../dist/tools/caseLibrary.js';
import {z} from 'zod';

test('patch discovery accepts definition fields and legacy nullable clears, rejects execution verdicts and invalid nested steps',()=>{
  const tool=caseLibraryTools.find(t=>t.name==='metersphere.functional.case.update');
  const schema=z.object(tool.inputSchema);
  const args={projectId:'p',caseId:'c',requestId:'r',expectedUpdateTime:1};
  assert.equal(schema.safeParse({...args,patch:{description:null,tags:null}}).success,true);
  assert.equal(schema.safeParse({...args,patch:{steps:[{num:1,desc:'a',expected:'b',actualResult:'legacy'}]}}).success,true);
  for(const patch of [{},{lastExecuteResult:'SUCCESS'},{steps:[{num:1.5}]},{steps:[{unknown:true}]}])
    assert.equal(schema.safeParse({...args,patch}).success,false);
});

test('safe field repair details survive the bridge while opaque data is dropped',()=>{
  const error=new MsApiError(400,JSON.stringify({code:'CASE_PATCH_FIELD_UNSUPPORTED',traceId:'trace-1',
    details:{field:'patch',allowedFields:['name','steps'],recommendedTool:'metersphere.functional.execution.submit',
      nextAction:'FIX_INPUT',retryable:false,raw:'database secret',secret:{token:'value'}}}));
  assert.equal(error.details.nextAction,'FIX_INPUT');
  assert.deepEqual(error.details.allowedFields,['name','steps']);
  assert.equal(error.details.recommendedTool,'metersphere.functional.execution.submit');
  assert.equal(JSON.stringify(error).includes('database secret'),false);
  assert.equal('secret' in error.details,false);
});

test('JSON-RPC field errors remain errors with actionable hints across the HTTP bridge',async t=>{
  const server=http.createServer((req,res)=>{
    req.resume();
    res.setHeader('Content-Type','application/json');
    res.end(JSON.stringify({jsonrpc:'2.0',id:1,error:{code:-32001,message:'opaque server message',data:{
      code:'CASE_PATCH_FIELD_UNSUPPORTED',traceId:'trace-http',details:{field:'patch',allowedFields:['name'],
        nextAction:'FIX_INPUT',recommendedTool:'metersphere.functional.execution.submit',retryable:false,rawStack:'private'}}}}));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const client=new MeterSphereClient({baseUrl:`http://127.0.0.1:${server.address().port}`,agentToken:'test',projectId:'p'});
  const result=await client.callNativeTool('metersphere.functional.case.update',{requestId:'r'});
  assert.equal(result.isError,true);
  const body=JSON.parse(result.content[0].text);
  assert.equal(body.data.code,'CASE_PATCH_FIELD_UNSUPPORTED');
  assert.equal(body.data.details.nextAction,'FIX_INPUT');
  assert.equal(body.data.traceId,'trace-http');
  assert.equal(JSON.stringify(result).includes('private'),false);
  assert.equal(JSON.stringify(result).includes('opaque server message'),false);
});
