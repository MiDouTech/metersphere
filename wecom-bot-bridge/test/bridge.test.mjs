import assert from 'node:assert/strict';
import { createHash, createHmac } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { IdempotencyStore } from '../src/idempotency-store.mjs';
import { mask, sanitize } from '../src/logger.mjs';
import { callbackSignature } from '../src/platform-client.mjs';

test('idempotency entries expire', () => {
  let now = 100;
  const store = new IdempotencyStore(10, () => now);
  store.set('request-1', { success: true });
  assert.deepEqual(store.get('request-1'), { success: true });
  now = 111;
  assert.equal(store.get('request-1'), undefined);
});

test('idempotency entries survive a bridge restart', () => {
  const directory = mkdtempSync(join(tmpdir(), 'wecom-idempotency-'));
  const file = join(directory, 'requests.json');
  try {
    const first = new IdempotencyStore(1000, () => 100, file);
    first.set('request-1', { success: true, wecomRequestId: 'wecom-1' });
    const restarted = new IdempotencyStore(1000, () => 101, file);
    assert.deepEqual(restarted.get('request-1'), { success: true, wecomRequestId: 'wecom-1' });
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test('callback signature covers timestamp nonce and body hash', () => {
  const body = '{"eventId":"event-1"}';
  const bodyHash = createHash('sha256').update(body).digest('hex');
  const expected = createHmac('sha256', 'callback-token').update(`1000\nnonce-1\n${bodyHash}`).digest('hex');
  assert.equal(callbackSignature('callback-token', '1000', 'nonce-1', body), expected);
});

test('logger masks secrets and identifiers', () => {
  assert.equal(mask('abcdefghij'), 'ab******ij');
  const result = sanitize({ secret: 'abcdefghij', nested: { userid: 'member-1001' }, status: 'ONLINE' });
  assert.equal(result.secret, 'ab******ij');
  assert.equal(result.nested.userid, 'me******01');
  assert.equal(result.status, 'ONLINE');
});
