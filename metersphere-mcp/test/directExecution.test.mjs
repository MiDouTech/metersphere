import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { caseLibraryTools } from "../dist/tools/caseLibrary.js";
import { z } from "zod";

test("local evidence upload sends bytes and never sends the local path", async t => {
  const dir=await mkdtemp(join(tmpdir(),"ms-evidence-"));
  t.after(()=>rm(dir,{recursive:true,force:true}));
  const filePath=join(dir,"screen.png");await writeFile(filePath,Buffer.from([1,2,3]));
  const tool=caseLibraryTools.find(x=>x.name.endsWith("execution.evidence.upload"));
  const requests=[];const client={callNativeTool:async(name,args)=>{requests.push({name,args});return {evidenceId:"saved"};}};
  const args={projectId:"p",caseId:"c",contentHash:"a".repeat(64),stepNum:1,purpose:"AFTER_STEP",fileName:"screen.png",contentType:"image/png",requestId:"upload",filePath};
  assert.deepEqual(await tool.handler(client,args),{evidenceId:"saved"});
  assert.equal(requests[0].args.filePath,undefined);assert.equal(requests[0].args.base64,"AQID");
  await assert.rejects(()=>tool.handler(client,{...args,base64:"AQID"}),/exactly one/);
  await writeFile(filePath,Buffer.alloc(5242881));
  await assert.rejects(()=>tool.handler(client,args),/5 MiB/);
  assert.equal(requests.length,1);
});

test("direct submit requires matching case/policy revisions and complete step facts",()=>{
  const tool=caseLibraryTools.find(x=>x.name.endsWith("execution.submit"));
  const schema=z.object(tool.inputSchema);
  const args={projectId:"p",caseId:"c",contentHash:"a".repeat(64),policyHash:"b".repeat(64),requestId:"submit",steps:[{stepNum:1,actual:"observed",result:"SUCCESS",evidenceIds:["e"]}]};
  assert.equal(schema.safeParse(args).success,true);
  assert.equal(schema.safeParse({...args,policyHash:undefined}).success,false);
  assert.equal(schema.safeParse({...args,steps:[{...args.steps[0],evidenceIds:[]}]}).success,false);
  assert.equal(schema.safeParse({...args,steps:[{...args.steps[0],result:"PASS"}]}).success,false);
  assert.equal(schema.safeParse({...args,steps:[args.steps[0],args.steps[0],{...args.steps[0],stepNum:101}]}).success,true);
  assert.equal(schema.safeParse({...args,steps:Array.from({length:101},()=>args.steps[0])}).success,true);
  assert.equal(schema.safeParse({...args,steps:Array.from({length:1001},()=>args.steps[0])}).success,false);
  assert.equal(schema.safeParse({...args,steps:[{...args.steps[0],stepNum:1001}]}).success,false);
});
