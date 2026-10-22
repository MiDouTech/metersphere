import ts from 'typescript';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/utils/caseExportNotifications.ts', import.meta.url), 'utf8')
  .replace('import.meta.env.VITE_API_BASE_URL', "'api'");
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const settle = () => new Promise((resolve) => setImmediate(resolve));
function fixture(issue) {
  const listeners = new Map(); const timers = new Map(); const sockets = [];
  const surface = { addEventListener: (name, fn) => listeners.set(name, fn), removeEventListener: (name) => listeners.delete(name) };
  const document = { ...surface, visibilityState: 'visible' };
  const navigator = { onLine: true };
  let issued = 0; let changed = 0; let timerId = 0;
  class Socket {
    constructor(url, protocols) { this.url = String(url); this.protocols = protocols; sockets.push(this); }
    close(code = 1006) { this.closed = true; this.onclose?.({ code }); }
    emit(task) { this.onmessage?.({ data: JSON.stringify({ type: 'EXPORT_TASK', task }) }); }
  }
  const context = { exports: {}, URL, Math, Number, JSON, document, navigator, WebSocket: Socket,
    window: { ...surface, location: { origin: 'https://app.example', protocol: 'https:' } },
    setTimeout: (fn) => { timers.set(++timerId, fn); return timerId; }, clearTimeout: (id) => timers.delete(id),
    require: () => ({ getCaseExportNotificationTicket: async () => {
      issued += 1;
      return issue ? issue(issued) : { enabled: true, taskId: 'task', protocol: 'ms-case-export-v1', ticket: 'a'.repeat(43) };
    } }),
  };
  vm.runInNewContext(compiled, context);
  const dispose = context.exports.default('task', 'project', () => { changed += 1; });
  return { sockets, timers, listeners, document, navigator, dispose, get issued() { return issued; }, get changed() { return changed; } };
}
test('ticket never enters URL; only matching monotonic notification triggers authenticated re-read', async () => {
  const f = fixture(); await settle(); const socket = f.sockets[0];
  assert.equal(socket.url, 'wss://app.example/api/functional/case/export/ws?taskId=task');
  assert.deepEqual([...socket.protocols], ['ms-case-export-v1', `ticket.${'a'.repeat(43)}`]);
  socket.onopen(); assert.equal(f.timers.size, 0);
  socket.emit({ taskId: 'other', projectId: 'project', stateVersion: 4 });
  socket.emit({ taskId: 'task', projectId: 'other', stateVersion: 4 });
  socket.emit({ taskId: 'task', projectId: 'project', stateVersion: 2 });
  socket.emit({ taskId: 'task', projectId: 'project', stateVersion: 2 });
  socket.emit({ taskId: 'task', projectId: 'project', stateVersion: 1 });
  assert.equal(f.changed, 1); f.dispose(); assert.equal(f.listeners.size, 0); assert.ok(socket.closed);
});
test('late ticket response after disposal never opens a socket', async () => {
  let resolve; const f = fixture(() => new Promise((done) => { resolve = done; }));
  f.dispose(); resolve({ enabled: true, taskId: 'task', protocol: 'ms-case-export-v1', ticket: 'a'.repeat(43) });
  await settle(); assert.equal(f.sockets.length, 0); assert.equal(f.timers.size, 0);
});
test('hidden and offline pause; return uses a new ticket; policy close does not reconnect', async () => {
  const f = fixture(); await settle();
  f.document.visibilityState = 'hidden'; f.listeners.get('visibilitychange')();
  assert.ok(f.sockets[0].closed); assert.equal(f.timers.size, 0);
  f.document.visibilityState = 'visible'; f.navigator.onLine = false; f.listeners.get('visibilitychange')();
  await settle(); assert.equal(f.issued, 1);
  f.navigator.onLine = true; f.listeners.get('online')(); await settle(); assert.equal(f.issued, 2);
  f.sockets[1].close(1008); assert.equal(f.timers.size, 0); f.dispose();
});
test('failed ticket requests retry at most five times and disabled server removes listeners', async () => {
  const f = fixture(() => { throw new Error('unavailable'); }); await settle();
  for (let i = 0; i < 10; i += 1) {
    for (const [id, fn] of [...f.timers]) { f.timers.delete(id); fn(); }
    await settle();
  }
  assert.equal(f.issued, 5); assert.equal(f.sockets.length, 0); f.dispose();
  const disabled = fixture(() => ({ enabled: false })); await settle();
  assert.equal(disabled.listeners.size, 0); assert.equal(disabled.sockets.length, 0);
});
