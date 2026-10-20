import test from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { z } from "zod";
import { MeterSphereClient } from "../dist/client.js";
import { caseLibraryTools } from "../dist/tools/caseLibrary.js";
import { searchFunctionalCasesInputSchema } from "../dist/tools/searchFunctionalCases.js";

test("case library remains the default even when a default test plan is configured", async t => {
  const requests=[];
  const server=http.createServer(async(req,res)=>{
    const chunks=[];for await(const chunk of req)chunks.push(chunk);
    requests.push({url:req.url,body:chunks.length?JSON.parse(Buffer.concat(chunks).toString()):null});
    res.setHeader("Content-Type","application/json");res.end(JSON.stringify({code:100200,data:{cases:[],total:0}}));
  });
  await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const client=new MeterSphereClient({baseUrl:`http://127.0.0.1:${server.address().port}`,agentToken:"fixture",projectId:"p",testPlanId:"configured-plan"});
  await client.searchFunctionalCases({projectId:"p",current:2,pageSize:2});
  assert.equal(requests[0].body.testPlanId,undefined);
  assert.equal(requests[0].body.projectId,"p");
  assert.equal(requests[0].body.current,2);
  await client.getFunctionalCase("case");assert.doesNotMatch(requests[1].url,/testPlanId/);
  await client.searchFunctionalCases({testPlanId:"explicit-plan"});assert.equal(requests[2].body.testPlanId,"explicit-plan");
});

test("case update and module mutations require versions and cannot set execution verdicts",()=>{
  const update=caseLibraryTools.find(t=>t.name.endsWith("case.update"));
  const schema=z.object(update.inputSchema);
  const request={projectId:"p",caseId:"c",expectedUpdateTime:1,requestId:"stable",patch:{customFields:{field:"value"}}};
  assert.equal(schema.safeParse(request).success,true);
  assert.equal(schema.safeParse({...request,expectedUpdateTime:undefined}).success,false);
  assert.equal(schema.safeParse({...request,patch:{lastExecuteResult:"PASS"}}).success,false);
  assert.equal(schema.safeParse({...request,patch:{}}).success,false);
  const move=z.object(caseLibraryTools.find(t=>t.name.endsWith("module.move")).inputSchema);
  assert.equal(move.safeParse({projectId:"p",moduleId:"m",parentId:"NONE",requestId:"move",expectedUpdateTime:1}).success,true);
  assert.equal(move.safeParse({projectId:"p",moduleId:"m",parentId:"NONE"}).success,false);
});

test("search schema preserves project identity and explicit tag matching",()=>{
  const schema=z.object(searchFunctionalCasesInputSchema);
  assert.equal(schema.safeParse({projectId:"name",identityType:"NAME",filters:{tags:["a"],tagMatch:"ALL"}}).success,true);
  assert.equal(schema.safeParse({filters:{tagMatch:"invalid"}}).success,false);
  assert.equal(schema.safeParse({pageSize:101}).success,false);
});

test("comment edit requires version, stable request identity and bounded content",()=>{
  const schema=z.object(caseLibraryTools.find(t=>t.name.endsWith("comment.update")).inputSchema);
  const args={projectId:"p",caseId:"c",commentId:"comment",content:"edited",expectedUpdateTime:10,requestId:"edit-1"};
  assert.equal(schema.safeParse(args).success,true);
  assert.equal(schema.safeParse({...args,expectedUpdateTime:undefined}).success,false);
  assert.equal(schema.safeParse({...args,expectedUpdateTime:1.5}).success,false);
  assert.equal(schema.safeParse({...args,content:""}).success,false);
  assert.equal(schema.safeParse({...args,requestId:""}).success,false);
});
