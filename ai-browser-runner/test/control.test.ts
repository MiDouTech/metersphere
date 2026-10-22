import assert from "node:assert/strict";
import test from "node:test";
import { safePoint } from "../src/executor.js";
import type { RunnerClient } from "../src/client.js";
import type { LeaseAssignment } from "../src/types.js";

test("human checkpoint prevents proceeding until the server resumes", async () => {
  let polls = 0;
  const client = { control: async () => ({ command: ++polls === 1 ? "WAIT_HUMAN" : "CONTINUE" }) };
  await safePoint(client as unknown as RunnerClient, {} as LeaseAssignment);
  assert.equal(polls, 2);
});

test("cancellation still interrupts a paused worker", async () => {
  let polls = 0;
  const client = { control: async () => ({ command: ++polls === 1 ? "PAUSE" : "CANCEL" }) };
  await assert.rejects(safePoint(client as unknown as RunnerClient, {} as LeaseAssignment),
    { name: "RunnerError", category: "RUNNER_TASK_CANCELED" });
  assert.equal(polls, 2);
});
