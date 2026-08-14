import http from 'node:http';

import { BridgeError } from './wecom-client.mjs';
import { logger } from './logger.mjs';

const json = (response, status, body) => {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(body));
};

const readBody = (request) =>
  new Promise((resolve, reject) => {
    let body = '';
    request.on('data', (chunk) => {
      body += chunk;
      if (body.length > 65536) reject(new BridgeError('Request body too large', 'BODY_TOO_LARGE'));
    });
    request.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        reject(new BridgeError('Invalid JSON', 'INVALID_JSON'));
      }
    });
    request.on('error', reject);
  });

export function createHttpServer(config, botClient, idempotencyStore) {
  return http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url, 'http://localhost');
      if (request.method === 'GET' && url.pathname === '/health/live') return json(response, 200, { status: 'UP' });
      if (request.method === 'GET' && url.pathname === '/health/ready') {
        return json(response, botClient.status().state === 'ONLINE' ? 200 : 503, botClient.status());
      }
      if (request.headers.authorization !== `Bearer ${config.bridgeToken}`) {
        return json(response, 401, { code: 'UNAUTHORIZED', message: 'Machine authentication failed' });
      }
      if (request.method === 'GET' && url.pathname === '/v1/status') return json(response, 200, botClient.status());
      if (request.method === 'GET' && url.pathname === '/metrics') {
        const status = botClient.status();
        const body = [
          `metersphere_wecom_bot_online ${status.state === 'ONLINE' ? 1 : 0}`,
          `metersphere_wecom_bot_reconnect_total ${status.counters.reconnects}`,
          `metersphere_wecom_bot_auth_failure_total ${status.counters.authFailures}`,
          `metersphere_wecom_bot_send_success_total ${status.counters.sendSuccess}`,
          `metersphere_wecom_bot_send_failure_total ${status.counters.sendFailure}`,
        ].join('\n') + '\n';
        response.writeHead(200, { 'content-type': 'text/plain; version=0.0.4' });
        return response.end(body);
      }
      if (request.method === 'POST' && url.pathname === '/v1/configure') {
        return json(response, 200, await botClient.configure(await readBody(request)));
      }
      if (request.method === 'POST' && url.pathname === '/v1/reconnect') {
        return json(response, 202, await botClient.reconnect());
      }
      if (request.method === 'POST' && url.pathname === '/v1/messages/send') {
        const body = await readBody(request);
        if (!body.requestId) throw new BridgeError('requestId is required', 'REQUEST_ID_REQUIRED');
        const cached = idempotencyStore.get(body.requestId);
        if (cached) return json(response, 200, { ...cached, idempotentReplay: true });
        const result = await botClient.sendWithDelivery(body);
        idempotencyStore.set(body.requestId, result);
        return json(response, 200, result);
      }
      return json(response, 404, { code: 'NOT_FOUND' });
    } catch (error) {
      const bridgeError = error instanceof BridgeError ? error : new BridgeError(error.message);
      logger.warn('bridge request failed', { path: request.url, code: bridgeError.code, retryable: bridgeError.retryable });
      return json(response, bridgeError.retryable ? 503 : 422, {
        code: bridgeError.code,
        message: bridgeError.message,
        retryable: bridgeError.retryable,
      });
    }
  });
}
