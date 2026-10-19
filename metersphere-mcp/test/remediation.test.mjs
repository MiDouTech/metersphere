import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { fileURLToPath } from "node:url";
import test from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { MeterSphereClient, MsApiError } from "../dist/client.js";
import { createCaseReviewTool, associateCaseReviewCasesTool } from "../dist/tools/planReview.js";
import { z } from "zod";

async function fixture(t, payload, status=200) {
  const received=[];
  const server=http.createServer(async(req,res)=>{const chunks=[];for await(const chunk of req)chunks.push(chunk);received.push({url:req.url,body:chunks.length?JSON.parse(Buffer.concat(chunks).toString()):undefined});res.statusCode=status;res.setHeader("Content-Type","application/json");res.end(JSON.stringify(payload));});
  await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  return {received,client:new MeterSphereClient({baseUrl:`http://127.0.0.1:${server.address().port}`,agentToken:"fixture-token",projectId:"p"})};
}
test("review schemas require stable keys and reject unknown rules",()=>{
  const schema=z.object(createCaseReviewTool.inputSchema);
  const request={projectId:"p",name:"review",reviewers:["u"],requestId:"stable-key"};
  assert.equal(schema.safeParse(request).success,true);
  assert.equal(schema.safeParse({...request,requestId:undefined}).success,false);
  assert.equal(schema.safeParse({...request,reviewPassRule:"UNKNOWN"}).success,false);
  assert.equal(z.object(associateCaseReviewCasesTool.inputSchema).safeParse({projectId:"p",reviewId:"r",caseIds:["c"]}).success,false);
});
test("review HTTP forwards requestId and reads the persisted review",async t=>{
  const {client,received}=await fixture(t,{code:100200,data:{id:"r"}});
  assert.deepEqual(await client.createCaseReview({projectId:"p",name:"review",reviewers:["u"],requestId:"stable-key"}),{id:"r"});
  assert.equal(received[0].body.requestId,"stable-key");
  await client.getCaseReview("r/foreign");assert.match(received[1].url,/r%2Fforeign$/);
});
test("HTTP 200 business failure cannot masquerade as success",async t=>{
  const {client}=await fixture(t,{code:100500,data:"",message:"SQL secret-password"});
  await assert.rejects(()=>client.getCaseReview("r"),error=>error instanceof MsApiError&&!/SQL|secret-password/.test(JSON.stringify(error)));
});
test("native errors discard raw details while retaining safe correlation",async t=>{
  const {client}=await fixture(t,{jsonrpc:"2.0",id:1,error:{code:-32001,message:"SQL secret-password",data:{code:"PERMISSION_DENIED",traceId:"trace-1",detail:"stacktrace"}}});
  const response=await client.callNativeTool("metersphere.case_review.get",{reviewId:"r"});
  assert.equal(response.isError,true);assert.doesNotMatch(JSON.stringify(response),/SQL|secret-password|stacktrace/);assert.equal(JSON.parse(response.content[0].text).data.traceId,"trace-1");
});

test("wrapped HTTP errors preserve safe code and trace without exposing server messages",()=>{
  const error=new MsApiError(409,JSON.stringify({code:100200,data:{code:"IDEMPOTENCY_CONFLICT",traceId:"trace-2",message:"SQL secret-password"}}));
  assert.equal(error.code,"IDEMPOTENCY_CONFLICT");assert.equal(error.traceId,"trace-2");assert.doesNotMatch(JSON.stringify(error),/SQL|secret-password/);
});

test("installed stdio review tool preserves wrapped conflict code safely",async t=>{
  const server=http.createServer((req,res)=>{req.resume();res.statusCode=409;res.setHeader("Content-Type","application/json");res.end(JSON.stringify({code:100200,data:{code:"IDEMPOTENCY_CONFLICT",traceId:"trace-package",message:"SQL secret-password"}}));});
  await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const client=new Client({name:"package-error-test",version:"1"});
  const transport=new StdioClientTransport({command:process.execPath,args:[process.env.MS_TEST_BINARY||fileURLToPath(new URL("../dist/index.js",import.meta.url))],env:{...process.env,MS_BASE_URL:`http://127.0.0.1:${server.address().port}`,MS_AGENT_TOKEN:"fixture-token",MS_PROJECT_ID:"p"},stderr:"pipe"});
  try {
    await client.connect(transport);
    const response=await client.callTool({name:"get_case_review",arguments:{reviewId:"r"}});
    assert.equal(response.isError,true);
    const error=JSON.parse(response.content[0].text);
    assert.equal(error.code,"IDEMPOTENCY_CONFLICT");assert.equal(error.traceId,"trace-package");
    assert.doesNotMatch(JSON.stringify(response),/SQL|secret-password/);
  } finally {await client.close();}
});
