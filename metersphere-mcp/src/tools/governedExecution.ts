import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

const id = z.string().min(1).max(128);
const integer = z.number().int().nonnegative();
const record = z.record(z.unknown());
const evidence = z.array(z.object({ kind: z.enum(["PERSONAL", "TASK"]), id }).strict());
const write = { protocolVersion: z.literal("1.0"), idempotencyKey: id };
const session = { sessionId: id };
const instance = { ...session, caseExecutionId: id };
const current = { ...instance, expectedContextVersion: integer, leaseGeneration: integer };
const result = { ...instance, expectedResultVersion: integer };
const prefix = "metersphere.functional.execution.session.";
const tool = (operation: string, inputSchema: z.ZodRawShape, global = false) => ({
  name: global ? `metersphere.functional.${operation}` : `${prefix}${operation}`,
  description: `Governed protocol 1.0 ${operation}. Read session.contract first. Cache knowledge and observations only in memory; erase on logout, organization/identity changes and revoked access. Keep the original idempotencyKey AND payload on retry. A historical receipt is not a new execution permit. Prepare before external writes; UNKNOWN requires authoritative lookup, never blind retry. Human acceptance and substitution confirmation use the trusted human UI.`,
  inputSchema,
  handler: (client: MeterSphereClient, args: Record<string, unknown>) => client.callNativeTool(global ? `metersphere.functional.${operation}` : `${prefix}${operation}`, args),
});
export const governedExecutionTools = [
  tool("contract", { projectId: id, caseId: id }),
  tool("start", { ...write, projectId: id, sourceType: z.enum(["PERSONAL", "TASK"]), caseId: id.optional(), contractVersion: integer.optional(), taskId: id.optional(), sourceLeaseId: id.optional(), noLegacyBusinessActionStarted: z.literal(true).optional(), environmentProfileId: id, governanceUserId: id, clientCapabilities: z.array(id), memoryOnlyCache: z.literal(true) }),
  tool("resume", { ...write, ...session }),
  tool("lease.takeover", { ...write, ...current }),
  tool("case.prepare", { ...write, ...current, preflight: z.object({ accountId: id, environmentId: id, evidence, bindings: record.optional(), resumeToken: z.string().optional() }).strict(), dependencyInstances: z.record(z.object({ instanceId: id, resultVersion: integer }).strict()).optional() }),
  tool("context.save", { ...write, ...session, executionContextVersion: integer, variables: record }),
  tool("action.prepare", { ...write, ...current, actionType: id, targetEntity: id, objectId: id.optional(), retryOf: id.optional(), recoveryPermitId: id.optional(), stepId: id.optional(), target: z.string().min(1), parameters: record, expectedEffect: z.string().min(1), businessKey: id, queryMethod: z.string().min(1), knowledgeId: id.optional() }),
  tool("action.resolve", { ...write, ...instance, actionId: id, resolution: z.enum(["CONFIRMED_EFFECT", "CONFIRMED_NO_EFFECT", "UNKNOWN"]), objectId: id.optional(), queryEvidence: z.string().optional(), terminalProof: z.object({ type: z.enum(["CANCELLED_TERMINAL", "FAILED_TERMINAL", "UNIQUE_REQUEST_TERMINAL_NO_EFFECT"]), reference: z.string().min(1) }).strict().optional(), evidence: evidence.optional() }),
  tool("action.status", { ...instance, actionId: id.optional() }),
  tool("cleanup.lease.takeover", { ...write, ...current }),
  tool("cleanup.prepare", { ...write, ...current, evidence: evidence.optional(), actionType: id, targetEntity: id, objectId: id, retryOf: id.optional(), recoveryPermitId: id.optional(), target: z.string().min(1), parameters: record, expectedEffect: z.string().min(1), businessKey: id, queryMethod: z.string().min(1) }),
  tool("cleanup.resolve", { ...write, ...instance, actionId: id, resolution: z.enum(["CONFIRMED_EFFECT", "CONFIRMED_NO_EFFECT", "UNKNOWN"]), objectId: id.optional(), queryEvidence: z.string().min(1), terminalProof: record.optional(), evidence }),
  tool("cleanup.recovery.record", { ...write, ...current, failureEventId: id, observation: z.string().min(1), changedCondition: z.string().min(1), proposedAction: z.string().min(1), expectedObservation: z.string().min(1), evidence }),
  tool("data.record", { ...write, ...current, key: id, objectId: id, conditions: record, actionId: id.optional(), evidence }),
  tool("step.save", { ...write, ...current, stepId: id, recoveryPermitId: id.optional(), observation: z.string().min(1), outcome: z.enum(["SATISFIED", "VIOLATED", "UNKNOWN", "NOT_REACHED"]), evidence, variables: record.optional(), legacyAssertions: z.array(record).optional() }),
  tool("recovery.record", { ...write, ...current, failureEventId: id, observation: z.string().min(1), changedCondition: z.string().min(1), proposedAction: z.string().min(1), expectedObservation: z.string().min(1), evidence }),
  tool("human.request", { ...write, ...current, condition: id, reason: z.string().min(1), attempts: z.array(record).optional() }),
  tool("human.resolve", { ...write, ...instance, requestId: id, resolution: z.enum(["CANCELLED", "RECHECK_REQUESTED"]), reason: z.string().optional() }),
  tool("checkpoint.get", session),
  tool("case.finish", { ...write, ...current, result: z.enum(["PASS", "FAIL", "BLOCKED", "EXECUTION_ERROR", "NOT_RUN"]), assertions: z.array(record), outputs: record.optional(), reason: z.string().optional() }),
  tool("finish", { ...write, ...session, expectedSessionVersion: integer }),
  tool("governance.transfer", { ...write, ...session, expectedSessionVersion: integer, targetUserId: id, reason: z.string().min(1) }),
  tool("cancel", { ...write, ...result, reason: z.string().min(1) }),
  tool("result.evidence.append", { ...write, ...result, submissionRevision: integer, assertions: z.array(record), outputs: record.optional(), reason: z.string().optional(), stepEvidence: z.array(record).optional(), correctedSteps: z.array(record).optional() }),
  tool("result.status.get", instance),
  tool("result.history.get", { ...instance, resultVersion: integer }),
  tool("result.close-unaccepted", { ...write, ...result, submissionRevision: integer, reason: z.string().min(1), retainUnknown: z.boolean().optional(), unverifiableReason: z.enum(["NO_QUALIFIED_REVIEWER", "NO_AUTHORITY_QUERY", "EVIDENCE_UNAVAILABLE"]).optional() }),
  tool("result.correction.request", { ...write, ...result, reason: z.string().min(1), result: id.optional() }),
  tool("result.revalidate", { ...write, ...result, preflight: record, dependencyInstances: z.record(z.object({ instanceId: id, resultVersion: integer }).strict()).optional() }),
  tool("result.sync", { ...write, ...result }),
  tool("knowledge.adopt", { ...write, ...current, knowledgeId: id, knowledgeVersion: integer, stepId: id, purpose: z.string().min(1) }),
  ...["knowledge.substitution.propose", "knowledge.substitution.confirm"].map(operation => tool(operation, { ...write, ...instance, knowledgeId: id, oldVersion: integer, newVersion: integer, newKnowledgeId: id.optional(), proposalHash: id.optional(), difference: record.optional(), reason: z.string().optional() })),
  tool("observation.append", { ...write, ...instance, actionId: id, originalGeneration: integer, observation: z.string().min(1), evidence }),
  tool("evidence.upload", { ...write, ...instance, fileName: z.string().min(1), contentType: z.enum(["image/png", "image/jpeg"]), base64: z.string().min(1), redacted: z.literal(true), stepId: id.optional(), purpose: z.enum(["BEFORE_STEP", "AFTER_STEP", "FAILURE", "HEALING_BEFORE", "HEALING_AFTER"]) }),
  tool("knowledge.search", { ...write, ...instance, scope: z.string().optional(), system: z.string().optional(), module: z.string().optional(), type: id.optional(), organizationId: id, keyword: z.string().optional(), page: integer, size: integer }, true),
  tool("knowledge.get", { ...write, ...instance, organizationId: id, knowledgeId: id, version: integer }, true),
  tool("knowledge.save", { ...write, organizationId: id, environmentId: id, content: record, knowledgeId: id.optional(), expectedKnowledgeVersion: integer.optional() }, true),
];
