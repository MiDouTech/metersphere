import { createHash, createHmac, randomUUID } from 'node:crypto';

import { logger } from './logger.mjs';

export function callbackSignature(token, timestamp, nonce, body) {
  const bodyHash = createHash('sha256').update(body).digest('hex');
  return createHmac('sha256', token).update(`${timestamp}\n${nonce}\n${bodyHash}`).digest('hex');
}

export class PlatformClient {
  constructor(config, fetchImpl = fetch) {
    this.config = config;
    this.fetch = fetchImpl;
  }

  async post(path, payload) {
    if (!this.config.callbackBaseUrl) return;
    const body = JSON.stringify(payload);
    const timestamp = String(Date.now());
    const nonce = randomUUID();
    const signature = callbackSignature(this.config.callbackToken, timestamp, nonce, body);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.config.requestTimeoutMs);
    try {
      const response = await this.fetch(`${this.config.callbackBaseUrl}${path}`, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-ms-timestamp': timestamp,
          'x-ms-nonce': nonce,
          'x-ms-signature': signature,
        },
        body,
        signal: controller.signal,
      });
      if (!response.ok) throw new Error(`callback returned HTTP ${response.status}`);
    } catch (error) {
      logger.warn('platform callback failed', { path, error: error.message });
    } finally {
      clearTimeout(timeout);
    }
  }
}
