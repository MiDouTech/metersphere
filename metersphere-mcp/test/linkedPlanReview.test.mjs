import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {fileURLToPath} from 'node:url';
import {z} from 'zod';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {createTestPlanInputSchema,confirmLinkedCaseReviewTool} from '../dist/tools/planReview.js';

const settings={name:'Review',moduleId:'root',reviewers:['reviewer'],reviewPassRule:'SINGLE',followPlanName:true};
test('new plan input requires explicit valid review settings and never supplies a fallback reviewer',()=>{
  const schema=z.object(createTestPlanInputSchema).strict();
  assert.equal(schema.safeParse({requestId:'original',name:'Plan'}).success,false);
  for(const invalid of [{...settings,reviewers:[]},{...settings,reviewers:['same','same']},{...settings,moduleId:''},{...settings,name:' '},{...settings,reviewPassRule:'UNKNOWN'}]){
    assert.equal(schema.safeParse({requestId:'original',name:'Plan',reviewSettings:invalid}).success,false);
  }
  assert.deepEqual(schema.parse({requestId:'original',name:'Plan',reviewSettings:settings}).reviewSettings,settings);
  assert.equal(z.object(confirmLinkedCaseReviewTool.inputSchema).safeParse({requestId:'original',previewId:'preview',acknowledged:false}).success,false);
});

test('real stdio protocol exposes preview/confirm separately and preserves the original identity over HTTP',async t=>{
  const calls=[];
  const server=http.createServer(async(req,res)=>{
    let body='';for await(const chunk of req)body+=chunk;
    calls.push({path:req.url,body:JSON.parse(body)});
    res.setHeader('Content-Type','application/json');
    res.end(JSON.stringify({code:100200,data:req.url.endsWith('/preview')?{previewId:'preview',expiresAt:Date.now()+600000}:{operationId:'operation',reviewId:'review'}}));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>{server.closeAllConnections();server.close(resolve);}));
  const client=new Client({name:'msp12-contract',version:'1'});
  const transport=new StdioClientTransport({command:process.execPath,args:['--max-old-space-size=128',fileURLToPath(new URL('../dist/index.js',import.meta.url))],env:{MS_BASE_URL:`http://127.0.0.1:${server.address().port}`,MS_AGENT_TOKEN:'synthetic-test-only',MS_PROJECT_ID:'project'}});
  t.after(()=>client.close());await client.connect(transport);
  const {tools}=await client.listTools();
  for(const name of ['preview_linked_case_review','confirm_linked_case_review'])assert.ok(tools.some(tool=>tool.name===name));
  assert.ok(tools.find(tool=>tool.name==='create_test_plan').inputSchema.required.includes('reviewSettings'));
  assert.equal(calls.length,0);
  const preview={planId:'plan',requestId:'original',name:'Review',moduleId:'root',reviewers:['reviewer'],reviewPassRule:'SINGLE'};
  const previewResponse=await client.callTool({name:'preview_linked_case_review',arguments:preview});
  assert.notEqual(previewResponse.isError,true);
  assert.deepEqual(calls.map(call=>call.path),['/api/agent/v1/case-review/linked/preview']);
  const confirm={previewId:'preview',requestId:'original',acknowledged:true};
  await client.callTool({name:'confirm_linked_case_review',arguments:confirm});
  await client.callTool({name:'confirm_linked_case_review',arguments:confirm});
  assert.deepEqual(calls[1],calls[2]);
  assert.equal(calls[1].body.requestId,calls[0].body.requestId);
  assert.equal(calls[1].body.projectId,'project');
  await client.callTool({name:'create_test_plan',arguments:{requestId:'create-original',name:'Plan',reviewSettings:settings}});
  assert.equal(calls[3].path,'/api/agent/v1/test-plan/create');assert.deepEqual(calls[3].body.reviewSettings,settings);
});
