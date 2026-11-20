import { z } from "zod";
import type { MeterSphereClient } from "../client.js";
const id=z.string().min(1).max(128);
const key=z.string().regex(/^[A-Za-z0-9_.:-]{1,128}$/);
const lease={leaseId:id,leaseToken:z.string().min(1).max(512)};
const context={taskId:id,executionId:id,...lease};
const tool=(name:string,description:string,inputSchema:z.ZodRawShape)=>({name,description,inputSchema,
  handler:(client:MeterSphereClient,args:Record<string,unknown>)=>client.callNativeTool(name,args)});
export const managedExecutionTools=[
  tool("metersphere.task.search","Find authorized server-created personal tasks.",{projectId:id,taskId:id.optional(),agentType:id.optional(),capabilities:z.array(id).max(100).optional()}),
  tool("metersphere.task.claim","Claim one task. After a lost response query task state first; do not blindly claim again.",{projectId:id,taskId:id,agentType:id.optional(),capabilities:z.array(id).max(100).optional()}),
  tool("metersphere.task.lease.heartbeat","Renew a currently authorized lease.",lease),
  tool("metersphere.task.release","Release the current lease with a reason.",{taskId:id,...lease,reason:z.string().max(4000).optional()}),
  tool("metersphere.execution.step.submit","Submit actual step facts under the frozen assertion contract. Only the server evaluates the verdict. Reuse requestId unchanged.",{
    ...context,stepId:id,requestId:key,status:id,attempt:z.number().int().positive().optional(),
    inputSnapshot:z.string().optional(),outputSummary:z.string().optional(),assertionResult:z.string().optional(),
    errorCode:id.optional(),errorMessage:z.string().max(4000).optional(),artifactIds:z.array(id).max(100).optional(),
    traceId:id.optional(),startedAt:z.number().int().optional(),finishedAt:z.number().int().optional()}),
  tool("metersphere.execution.step.result.get","Read persisted step facts after response loss before retrying.",{taskId:id,executionId:id,stepId:id,requestId:key}),
  tool("metersphere.execution.complete","Request server finalization using the active lease; does not assert PASS.",{...lease,reason:z.string().max(4000).optional()}),
  tool("metersphere.execution.fail","Report execution failure using the active lease.",{...lease,reason:z.string().max(4000).optional()}),
  tool("metersphere.artifact.prepare","Prepare evidence upload; never reuse an expired upload authorization.",{...context,requestId:key,purpose:id,fileName:z.string().min(1).max(255),contentType:id,sizeBytes:z.number().int().positive(),sha256:z.string().regex(/^[a-fA-F0-9]{64}$/),caseId:id.optional(),stepId:id.optional(),traceId:id.optional(),redacted:z.boolean().optional()}),
  tool("metersphere.artifact.commit","Commit uploaded evidence with current authorization.",{...context,artifactId:id,uploadToken:z.string().min(1).max(1024),requestId:key,traceId:id.optional()}),
  tool("metersphere.artifact.list","Read authorized evidence metadata.",{taskId:id}),
];
