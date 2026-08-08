export const PROTOCOL_VERSION = '1.0';

export const upstreamTypes = new Set([
  'connection.ready', 'connection.heartbeat', 'connection.status', 'execution.accepted', 'message.start',
  'content.delta', 'tool.call', 'usage.reported', 'execution.completed',
  'execution.failed', 'execution.cancelled',
]);

export function envelope(type, requestId, sequence, payload = {}) {
  if (!upstreamTypes.has(type)) throw new Error(`unsupported upstream event: ${type}`);
  return {
    protocolVersion: PROTOCOL_VERSION,
    type,
    requestId,
    sequence,
    timestamp: Date.now(),
    nonce: crypto.randomUUID(),
    payload,
  };
}

export function parseDownstream(raw) {
  const value = JSON.parse(raw);
  if (value.protocolVersion !== PROTOCOL_VERSION) throw new Error('unsupported protocol version');
  if (!['execution.start', 'execution.cancel', 'tool.result', 'connection.probe', 'connection.sync',
    'connection.authorize', 'session.close'].includes(value.type)) {
    throw new Error(`unsupported downstream event: ${value.type}`);
  }
  return value;
}
