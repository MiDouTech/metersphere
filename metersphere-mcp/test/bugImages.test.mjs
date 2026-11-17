import test from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { fileURLToPath } from "node:url";
import { MeterSphereClient } from "../dist/client.js";
import { readBugImageTool } from "../dist/tools/bugImages.js";

const args = { projectId: "p", bugId: "b", imageRef: `bi1_${"a".repeat(64)}_${"b".repeat(64)}` };
async function fixture(t, response) {
  const calls = [];
  const server = http.createServer(async (req, res) => {
    let body = ""; for await (const chunk of req) body += chunk;
    calls.push({ path: req.url, payload: JSON.parse(body) });
    res.setHeader("Content-Type", "application/json"); res.end(JSON.stringify(response));
  });
  await new Promise(resolve => server.listen(0,"127.0.0.1",resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const baseUrl = `http://127.0.0.1:${server.address().port}`;
  return { calls, baseUrl, client: new MeterSphereClient({baseUrl,agentToken:"fixture",projectId:"p"}) };
}

test("original image, metadata and structured content survive the bridge unchanged with one call", async t => {
  const expected = { content: [{type:"image",mimeType:"image/png",data:"AQID"},{type:"text",text:'{"sizeBytes":3}'}], structuredContent:{result:{sizeBytes:3}} };
  const {client,calls}=await fixture(t,{jsonrpc:"2.0",id:1,result:expected});
  assert.deepEqual(await readBugImageTool.handler(client,args),expected);
  assert.equal(calls.length,1);assert.equal(calls[0].path,"/api/mcp");
  assert.deepEqual(calls[0].payload.params,{name:"metersphere.bug.image.read",arguments:args});
});
test("temporary storage error preserves retryability and does not automatically retry", async t => {
  const data={code:"BUG_IMAGE_STORAGE_UNAVAILABLE",traceId:"trace",details:{retryable:true,nextAction:"RETRY_IMAGE"}};
  const {client,calls}=await fixture(t,{jsonrpc:"2.0",id:1,error:{code:-32001,message:"图片存储暂不可用",data}});
  const result=await client.readBugImage(args);assert.equal(result.isError,true);
  assert.deepEqual(JSON.parse(result.content[0].text),{...data,message:"图片存储暂不可用"});assert.equal(calls.length,1);
});
test("older server explicitly reports unsupported without fetching any image URL", async t => {
  const {client,calls}=await fixture(t,{jsonrpc:"2.0",id:1,error:{code:-32001,message:"missing",data:{code:"MCP_TOOL_NOT_FOUND"}}});
  assert.equal(JSON.parse((await client.readBugImage(args)).content[0].text).code,"BUG_IMAGE_UNSUPPORTED");
  assert.equal(calls.length,1);assert.equal(calls[0].path,"/api/mcp");
});
test("schema requires all resource identifiers and rejects URL references", () => {
  assert.equal(readBugImageTool.inputSchema.imageRef.safeParse("https://example.org/image").success,false);
  assert.equal(readBugImageTool.inputSchema.bugId.safeParse("").success,false);
  assert.equal(readBugImageTool.inputSchema.projectId.safeParse(undefined).success,false);
});
test("unbound index error preserves REFRESH_BUG without automatically refreshing or retrying", async t => {
  const data={code:"BUG_IMAGE_INDEX_STORAGE_UNAVAILABLE",traceId:"index-trace",details:{retryable:true,nextAction:"REFRESH_BUG"}};
  const {client,calls}=await fixture(t,{jsonrpc:"2.0",id:1,error:{code:-32001,message:"请刷新缺陷详情",data}});
  const response=await client.readBugImage(args);assert.equal(response.isError,true);
  assert.deepEqual(JSON.parse(response.content[0].text),{...data,message:"请刷新缺陷详情"});assert.equal(calls.length,1);
});
test("actual stdio process advertises and returns image content through tools/call", async t => {
  const expected={content:[{type:"image",mimeType:"image/jpeg",data:"AQID"}]};
  const {baseUrl,calls}=await fixture(t,{jsonrpc:"2.0",id:1,result:expected});
  const client=new Client({name:"msp14-protocol-test",version:"1"});
  const transport=new StdioClientTransport({command:process.execPath,args:[fileURLToPath(new URL("../dist/index.js",import.meta.url))],
    env:{MS_BASE_URL:baseUrl,MS_AGENT_TOKEN:"synthetic",MS_PROJECT_ID:"p"}});
  try {
    await client.connect(transport);const {tools}=await client.listTools();assert.ok(tools.some(t=>t.name==="read_bug_image"));
    assert.deepEqual((await client.callTool({name:"read_bug_image",arguments:args})).content,expected.content);
    assert.equal(calls.length,1);
  }finally{await client.close();}
});
