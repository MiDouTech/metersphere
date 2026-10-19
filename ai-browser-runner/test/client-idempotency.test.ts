import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, dirname, resolve } from "node:path";
import { RunnerClient } from "../src/client.js";
import type { LeaseAssignment } from "../src/types.js";

test("lost response and client restart reuse persisted request identity and payload", async (t) => {
  const directory = await mkdtemp(join(tmpdir(), "ms-result-"));
  const previous = process.env.MS_RUNNER_STATE_DIR;
  process.env.MS_RUNNER_STATE_DIR = directory;
  const bodies: string[] = [];
  t.mock.method(globalThis, "fetch", async (_url: unknown, init: RequestInit) => {
    bodies.push(String(init.body));
    if (bodies.length === 1) throw new Error("response lost");
    return new Response("{}", { status: 200 });
  });
  const assignment = { leaseId: "l1", leaseToken: "private-lease" } as LeaseAssignment;
  try {
    await new RunnerClient("http://localhost", "runner", "secret").stepResult(assignment, "s1", ["observed"], ["b", "a"]);
    await new RunnerClient("http://localhost", "runner", "secret").stepResult(assignment, "s1", ["observed"], ["a", "b"]);
    assert.equal(bodies.length, 3);
    assert.equal(bodies[0], bodies[1]);assert.equal(bodies[1], bodies[2]);
    assert.ok(JSON.parse(bodies[0]).requestId);
    assert.ok(!bodies[0].includes("private-lease"));
    await new RunnerClient("http://localhost", "runner", "secret").stepResult(assignment, "s1", ["changed"], ["a", "b"]);
    assert.notEqual(JSON.parse(bodies[2]).requestId, JSON.parse(bodies[3]).requestId);
  } finally {
    if (previous === undefined) delete process.env.MS_RUNNER_STATE_DIR; else process.env.MS_RUNNER_STATE_DIR = previous;
    assert.equal(dirname(resolve(directory)), resolve(tmpdir()));
    await rm(directory, { recursive: true, force: true });
  }
});
