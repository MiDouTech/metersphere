import test from "node:test";
import assert from "node:assert/strict";
import { z } from "zod";
import { bugTransferTools } from "../dist/tools/bugTransfer.js";
import { createBugInputSchema } from "../dist/tools/bugWrite.js";
test("bug creation requires an idempotency key",()=>{
  assert.equal(z.object(createBugInputSchema).safeParse({projectId:"p",title:"t"}).success,false);
});
test("bug transfer forwards unchanged preview and commit arguments",async()=>{
  for(const tool of bugTransferTools){const calls=[];const args={projectId:"p",requestId:"r",previewHash:"a".repeat(64),rows:[{title:"a"}]};
    const result=await tool.handler({callNativeTool:async(name,values)=>{calls.push({name,values});return {status:"PARTIAL"};}},args);
    assert.equal(result.status,"PARTIAL");assert.equal(calls[0].name,tool.name);assert.deepEqual(calls[0].values,args);
  }
});
test("bug export rejects empty and oversized selections; import commit requires hash",()=>{
  const tool=bugTransferTools.find(x=>x.name==="metersphere.bug.export");const schema=z.object(tool.inputSchema);
  assert.equal(schema.safeParse({projectId:"p",requestId:"r",bugIds:[]}).success,false);
  assert.equal(schema.safeParse({projectId:"p",requestId:"r",bugIds:Array(101).fill("b")}).success,false);
  const commit=z.object(bugTransferTools.find(x=>x.name.endsWith("import.commit")).inputSchema);
  assert.equal(commit.safeParse({projectId:"p",requestId:"r",rows:[{title:"t"}]}).success,false);
});
