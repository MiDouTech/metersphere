import AiBot from '@wecom/aibot-node-sdk';
import { randomUUID } from 'node:crypto';

import { logger, mask, sdkLogger } from './logger.mjs';

const retryableCodes = new Set([-1, 45009, 60020, 846607]);

export class BridgeError extends Error {
  constructor(message, code = 'BRIDGE_ERROR', retryable = false) {
    super(message);
    this.code = String(code);
    this.retryable = retryable;
  }
}

export class WecomBotClient {
  constructor(config, platformClient, clientFactory) {
    this.config = config;
    this.platformClient = platformClient;
    this.clientFactory = clientFactory ?? ((options) => new AiBot.WSClient(options));
    this.client = undefined;
    this.state = config.enabled ? 'OFFLINE' : 'DISABLED';
    this.lastConnectedAt = undefined;
    this.lastHeartbeatAt = undefined;
    this.lastError = undefined;
    this.counters = { reconnects: 0, authFailures: 0, sendSuccess: 0, sendFailure: 0 };
  }

  status() {
    return {
      state: this.state,
      botIdMasked: mask(this.config.botId),
      lastConnectedAt: this.lastConnectedAt,
      lastHeartbeatAt: this.lastHeartbeatAt,
      lastError: this.lastError,
      counters: { ...this.counters },
    };
  }

  async configure({ botId, secret, enabled }) {
    this.stop();
    this.config.botId = botId || this.config.botId;
    this.config.secret = secret || this.config.secret;
    this.config.enabled = Boolean(enabled);
    if (this.config.enabled) await this.start();
    else await this.setState('DISABLED');
    return this.status();
  }

  async start() {
    if (!this.config.enabled) return this.setState('DISABLED');
    if (!this.config.botId || !this.config.secret) throw new BridgeError('Bot credentials are not configured', 'NOT_CONFIGURED');
    this.stop();
    await this.setState('CONNECTING');
    this.client = this.clientFactory({
      botId: this.config.botId,
      secret: this.config.secret,
      wsUrl: this.config.wsUrl,
      reconnectInterval: 750 + Math.floor(Math.random() * 500),
      maxReconnectAttempts: -1,
      heartbeatInterval: 30000,
      requestTimeout: this.config.requestTimeoutMs,
      logger: sdkLogger,
    });
    this.registerEvents(this.client);
    this.client.connect();
  }

  stop() {
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer);
    this.heartbeatTimer = undefined;
    if (this.client) this.client.disconnect();
    this.client = undefined;
  }

  async reconnect() {
    if (!this.config.enabled) throw new BridgeError('Bot is disabled', 'DISABLED');
    await this.start();
    return this.status();
  }

  registerEvents(client) {
    client.on('connected', () => this.setState('CONNECTING'));
    client.on('authenticated', () => {
      this.lastConnectedAt = Date.now();
      this.lastHeartbeatAt = this.lastConnectedAt;
      this.lastError = undefined;
      this.setState('ONLINE');
      if (this.heartbeatTimer) clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = setInterval(() => {
        if (this.state !== 'ONLINE') return;
        this.lastHeartbeatAt = Date.now();
        this.setState('ONLINE');
      }, 30000);
      this.heartbeatTimer.unref();
    });
    client.on('reconnecting', (attempt) => {
      this.counters.reconnects += 1;
      this.setState('CONNECTING', undefined, { attempt });
    });
    client.on('disconnected', (reason) => this.setState('OFFLINE', 'DISCONNECTED', { reason }));
    client.on('error', (error) => {
      const authFailure = /auth|secret|credential/i.test(error?.message ?? '');
      if (authFailure) this.counters.authFailures += 1;
      this.setState(authFailure ? 'AUTH_FAILED' : 'OFFLINE', authFailure ? 'AUTH_FAILED' : 'SDK_ERROR', {
        message: error?.message,
      });
    });
    const discover = (frame) => this.reportChat(frame);
    client.on('message', discover);
    client.on('event', discover);
  }

  async setState(state, code, detail = {}) {
    this.state = state;
    if (code) this.lastError = { code, message: detail.message || detail.reason || code };
    logger.info('wecom bot state changed', { state, code, ...detail, botId: this.config.botId });
    await this.platformClient.post('/internal/wecom-bot/events/status', {
      eventId: randomUUID(),
      botId: this.config.botId,
      status: state,
      connectedAt: this.lastConnectedAt,
      heartbeatAt: this.lastHeartbeatAt,
      errorCode: code,
      errorMessage: this.lastError?.message,
      occurredAt: Date.now(),
    });
  }

  async reportChat(frame) {
    const body = frame?.body ?? {};
    const group = body.chattype === 'group';
    const chatId = group ? body.chatid : body.from?.userid;
    if (!chatId) return;
    await this.platformClient.post('/internal/wecom-bot/events/chat', {
      eventId: body.msgid || frame?.headers?.req_id || randomUUID(),
      botId: this.config.botId,
      chatType: group ? 'GROUP' : 'SINGLE',
      chatId,
      fromUserid: body.from?.userid,
      occurredAt: body.create_time ? Number(body.create_time) * 1000 : Date.now(),
    });
  }

  async send(request) {
    if (this.state !== 'ONLINE' || !this.client) throw new BridgeError('Bot is not online', this.state, true);
    if (!request?.target?.id || !['USER', 'GROUP'].includes(request?.target?.type)) {
      throw new BridgeError('Invalid target', 'INVALID_TARGET');
    }
    if (request?.message?.type?.toLowerCase() !== 'markdown' || !request?.message?.content) {
      throw new BridgeError('Only non-empty markdown messages are supported', 'INVALID_MESSAGE');
    }
    if (Buffer.byteLength(request.message.content, 'utf8') > 20480) {
      throw new BridgeError('Markdown content exceeds 20480 bytes', 'MESSAGE_TOO_LONG');
    }
    try {
      const frame = await this.client.sendMessage(request.target.id, {
        msgtype: 'markdown',
        markdown: { content: request.message.content },
      });
      if (frame?.errcode && frame.errcode !== 0) {
        throw new BridgeError(frame.errmsg || 'WeCom rejected the message', frame.errcode, retryableCodes.has(frame.errcode));
      }
      return { accepted: true, success: true, requestId: request.requestId, wecomRequestId: frame?.headers?.req_id };
    } catch (error) {
      if (error instanceof BridgeError) throw error;
      if (error?.errcode !== undefined && error?.errcode !== null) {
        throw new BridgeError(
          error.errmsg || `WeCom rejected the message (code: ${error.errcode})`,
          error.errcode,
          retryableCodes.has(Number(error.errcode)),
        );
      }
      throw new BridgeError(error?.message || 'WeCom send failed', 'SEND_FAILED', true);
    }
  }

  async sendWithDelivery(request) {
    try {
      const result = await this.send(request);
      this.counters.sendSuccess += 1;
      await this.safeReportDelivery(request, { success: true, retryable: false });
      return result;
    } catch (error) {
      const mapped = error instanceof BridgeError
        ? error
        : new BridgeError(error?.message || 'WeCom send failed', 'SEND_FAILED', true);
      this.counters.sendFailure += 1;
      await this.safeReportDelivery(request, {
        success: false,
        retryable: mapped.retryable,
        errorCode: mapped.code,
        errorMessage: mapped.message,
      });
      throw mapped;
    }
  }

  async reportDelivery(request, result) {
    if (!request.outboxId) return;
    await this.platformClient.post('/internal/wecom-bot/events/delivery', {
      eventId: `${request.requestId}:delivery`,
      outboxId: request.outboxId,
      requestId: request.requestId,
      occurredAt: Date.now(),
      ...result,
    });
  }

  async safeReportDelivery(request, result) {
    try {
      await this.reportDelivery(request, result);
    } catch (error) {
      logger.warn('delivery callback failed without changing send result', { error: error?.message });
    }
  }
}
