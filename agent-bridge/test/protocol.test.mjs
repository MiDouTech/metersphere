import assert from 'node:assert/strict';
import test from 'node:test';
import { envelope, parseDownstream } from '../src/protocol.mjs';
import { sanitizeError } from '../src/process-runner.mjs';

test('envelope uses protocol 1.0 and sequence', () => {
  const value = envelope('content.delta', 'request-1', 2, { delta: 'hello' });
  assert.equal(value.protocolVersion, '1.0');
  assert.equal(value.sequence, 2);
});

test('downstream rejects private or unknown protocol operations', () => {
  assert.throws(() => parseDownstream(JSON.stringify({ protocolVersion: '1.0', type: 'shell.exec' })));
});

test('connection synchronization and status use the public protocol', () => {
  const downstream = parseDownstream(JSON.stringify({ protocolVersion: '1.0', type: 'connection.sync',
    requestId: 'connection', sequence: 0, timestamp: Date.now(), payload: { connections: [] } }));
  assert.equal(downstream.type, 'connection.sync');
  assert.equal(envelope('connection.status', 'connection:1', 1, {}).type, 'connection.status');
});

test('errors redact credential-shaped values', () => {
  assert.equal(sanitizeError('token=secret-value'), 'token=******');
});
