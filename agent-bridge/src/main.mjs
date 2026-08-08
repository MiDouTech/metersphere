import { readFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import WebSocket from 'ws';
import { envelope, parseDownstream } from './protocol.mjs';
import { providers } from './providers.mjs';
import { authenticateDevice } from './device-auth.mjs';

const configPath = process.env.MS_AGENT_BRIDGE_CONFIG
  ?? path.join(process.cwd(), 'config.json');
const config = JSON.parse(await readFile(configPath, 'utf8'));
const providerMap = providers(config);
const running = new Map();
const sequences = new Map();

function next(requestId) {
  const value = (sequences.get(requestId) ?? 0) + 1;
  sequences.set(requestId, value);
  return value;
}

function send(socket, type, requestId, payload = {}) {
  socket.send(JSON.stringify(envelope(type, requestId, next(requestId), payload)));
}

async function reportConnectionStatus(socket, connection, provider) {
  const status = provider ? await provider.status() : { installed: false, authenticated: false };
  const connected = status.installed && status.authenticated;
  send(socket, 'connection.status', `connection:${connection.id}`, {
    connectionId: connection.id,
    status: connected ? 'CONNECTED' : status.installed ? 'AUTH_EXPIRED' : 'OFFLINE',
    capabilities: JSON.stringify(connected ? provider.capabilities : {}),
  });
}

async function connect() {
  if (!config.deviceId) {
    throw new Error('Pair the Bridge first; deviceId is missing');
  }
  const wsUrl = new URL('/ai/agent-bridge/ws', config.platformUrl);
  wsUrl.protocol = wsUrl.protocol === 'https:' ? 'wss:' : 'ws:';
  if (wsUrl.protocol !== 'wss:' && config.allowInsecureDevelopment !== true) {
    throw new Error('Agent Bridge requires TLS/WSS');
  }
  const authentication = await authenticateDevice(config);
  const socket = new WebSocket(wsUrl, { headers: {
    Authorization: `Bearer ${authentication.accessToken}`,
    'X-Agent-Device-Id': config.deviceId,
    'X-Agent-Protocol-Version': config.protocolVersion,
    'X-Agent-Bridge-Version': config.bridgeVersion,
  }, maxPayload: 1024 * 1024 });
  socket.on('open', () => {
    send(socket, 'connection.ready', 'connection', {
      bridgeVersion: config.bridgeVersion,
      protocolVersion: config.protocolVersion,
      providers: [...providerMap.keys()],
    });
  });
  socket.on('message', async (data) => {
    let message;
    try { message = parseDownstream(data.toString()); } catch { socket.close(1008); return; }
    if (message.type === 'connection.sync') {
      for (const connection of message.payload.connections ?? []) {
        const provider = providerMap.get(connection.provider);
        await reportConnectionStatus(socket, connection, provider);
      }
      return;
    }
    if (message.type === 'connection.authorize') {
      const connection = { id: message.payload.connectionId, provider: message.payload.provider };
      const provider = providerMap.get(connection.provider);
      if (!provider?.login) {
        await reportConnectionStatus(socket, connection, provider);
        return;
      }
      const login = provider.login();
      login.once('close', () => reportConnectionStatus(socket, connection, provider).catch(() => {}));
      login.once('error', () => reportConnectionStatus(socket, connection, provider).catch(() => {}));
      return;
    }
    if (message.type === 'execution.cancel') {
      const entry = running.get(message.requestId);
      entry?.provider.cancel(message.requestId);
      send(socket, 'execution.cancelled', message.requestId, {});
      return;
    }
    if (message.type !== 'execution.start') return;
    const provider = providerMap.get(message.payload.provider);
    if (!provider) {
      send(socket, 'execution.failed', message.requestId, { errorCode: 'AGENT_PROVIDER_UNSUPPORTED' });
      return;
    }
    const request = { requestId: message.requestId, ...message.payload };
    running.set(message.requestId, { provider });
    send(socket, 'execution.accepted', message.requestId, {});
    send(socket, 'message.start', message.requestId, { role: 'ASSISTANT' });
    try {
      for await (const event of provider.execute(request)) send(socket, event.type, message.requestId, event.payload);
      send(socket, 'execution.completed', message.requestId, {});
    } catch (error) {
      send(socket, 'execution.failed', message.requestId, {
        errorCode: error.message?.startsWith('CURSOR_') ? error.message : 'AGENT_PROVIDER_ERROR',
        message: String(error.message ?? 'Agent execution failed').slice(0, 1000),
      });
    } finally { running.delete(message.requestId); }
  });
  const heartbeat = setInterval(() => {
    if (socket.readyState === WebSocket.OPEN) send(socket, 'connection.heartbeat', 'connection', {});
  }, 15_000);
  heartbeat.unref();
  socket.on('close', () => {
    clearInterval(heartbeat);
    const reconnect = setTimeout(() => connect().catch(() => {}), 3_000 + Math.floor(Math.random() * 2_000));
    reconnect.unref();
  });
}

connect().catch((error) => {
  console.error(String(error.message ?? error));
  process.exitCode = 1;
});
