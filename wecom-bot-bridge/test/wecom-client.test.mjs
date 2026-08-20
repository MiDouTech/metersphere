import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import test from 'node:test';

import { BridgeError, WecomBotClient } from '../src/wecom-client.mjs';

class FakeClient extends EventEmitter {
  connect() {}
  disconnect() {}
  async sendMessage() { return { errcode: 0, headers: { req_id: 'wecom-1' } }; }
}

const config = () => ({ enabled: true, botId: 'bot-id', secret: 'secret', requestTimeoutMs: 1000 });

test('authentication moves state to ONLINE and group messages are discovered', async () => {
  const callbacks = [];
  const fake = new FakeClient();
  const client = new WecomBotClient(config(), { post: async (path, body) => callbacks.push({ path, body }) }, () => fake);
  await client.start();
  fake.emit('authenticated');
  fake.emit('message', { body: { chattype: 'group', chatid: 'chat-1', msgid: 'msg-1', from: { userid: 'u-1' } } });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(client.status().state, 'ONLINE');
  assert.equal(callbacks.some((item) => item.path.endsWith('/chat') && item.body.chatId === 'chat-1' && item.body.fromUserid === 'u-1'), true);
});

test('delivery result is reported with outbox identity', async () => {
  const callbacks = [];
  const fake = new FakeClient();
  const client = new WecomBotClient(config(), { post: async (path, body) => callbacks.push({ path, body }) }, () => fake);
  await client.start();
  fake.emit('authenticated');
  await client.sendWithDelivery({ requestId: 'r-1', outboxId: 'o-1', target: { type: 'GROUP', id: 'g-1' }, message: { type: 'markdown', content: 'ok' } });
  assert.equal(callbacks.some((item) => item.path.endsWith('/delivery') && item.body.outboxId === 'o-1' && item.body.success), true);
});

test('delivery callback failure never turns a successful send into a retry', async () => {
  const fake = new FakeClient();
  const client = new WecomBotClient(config(), { post: async (path) => {
    if (path.endsWith('/delivery')) throw new Error('callback unavailable');
  } }, () => fake);
  await client.start();
  fake.emit('authenticated');

  const result = await client.sendWithDelivery({
    requestId: 'r-callback', outboxId: 'o-callback', target: { type: 'GROUP', id: 'g-1' },
    message: { type: 'markdown', content: 'ok' },
  });

  assert.equal(result.success, true);
  assert.equal(client.status().counters.sendSuccess, 1);
  assert.equal(client.status().counters.sendFailure, 0);
});

test('invalid target is permanent and does not reach SDK', async () => {
  const client = new WecomBotClient(config(), { post: async () => undefined }, () => new FakeClient());
  await client.start();
  client.client.emit('authenticated');
  await assert.rejects(
    () => client.send({ requestId: 'r-2', target: { type: 'EXTERNAL', id: 'x' }, message: { type: 'markdown', content: 'bad' } }),
    (error) => error instanceof BridgeError && error.code === 'INVALID_TARGET' && !error.retryable,
  );
});

test('WeCom acknowledgement errors retain errcode and errmsg in delivery result', async () => {
  const callbacks = [];
  const fake = new FakeClient();
  fake.sendMessage = async () => Promise.reject({ errcode: 93000, errmsg: 'invalid userid' });
  const client = new WecomBotClient(
    config(),
    { post: async (path, body) => callbacks.push({ path, body }) },
    () => fake,
  );
  await client.start();
  fake.emit('authenticated');

  await assert.rejects(
    () =>
      client.sendWithDelivery({
        requestId: 'r-error',
        outboxId: 'o-error',
        target: { type: 'USER', id: 'invalid-user' },
        message: { type: 'markdown', content: 'test' },
      }),
    (error) => error instanceof BridgeError && error.code === '93000' && error.message === 'invalid userid',
  );
  assert.equal(
    callbacks.some(
      (item) =>
        item.path.endsWith('/delivery') &&
        item.body.outboxId === 'o-error' &&
        item.body.errorCode === '93000' &&
        item.body.errorMessage === 'invalid userid' &&
        item.body.retryable === false,
    ),
    true,
  );
});

test('WeCom frequency limit acknowledgement remains retryable', async () => {
  const fake = new FakeClient();
  fake.sendMessage = async () =>
    Promise.reject({ errcode: 846607, errmsg: 'aibot send msg frequency limit exceeded' });
  const client = new WecomBotClient(config(), { post: async () => {} }, () => fake);
  await client.start();
  fake.emit('authenticated');

  await assert.rejects(
    () =>
      client.send({
        requestId: 'r-rate-limit',
        target: { type: 'USER', id: 'user-1' },
        message: { type: 'markdown', content: 'test' },
      }),
    (error) =>
      error instanceof BridgeError &&
      error.code === '846607' &&
      error.message === 'aibot send msg frequency limit exceeded' &&
      error.retryable === true,
  );
});
