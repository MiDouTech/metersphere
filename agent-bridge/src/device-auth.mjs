import { createHash, createPrivateKey, generateKeyPairSync, sign } from 'node:crypto';
import { getSecret, setSecret } from './secret-store.mjs';

const secretName = (deviceId) => `device-key-${deviceId}`;

export function createDeviceIdentity() {
  const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1',
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' } });
  return { publicKey, privateKey,
    fingerprint: createHash('sha256').update(publicKey).digest('hex') };
}

export async function saveDevicePrivateKey(deviceId, privateKey) {
  await setSecret(secretName(deviceId), privateKey);
}

export async function authenticateDevice(config) {
  const challengeResponse = await fetch(new URL('/ai/agent-bridge/challenge', config.platformUrl), {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ deviceId: config.deviceId }),
  });
  if (!challengeResponse.ok) throw new Error('Bridge device challenge failed');
  const challengeBody = await challengeResponse.json();
  const challenge = challengeBody.data ?? challengeBody;
  const privateKey = createPrivateKey(await getSecret(secretName(config.deviceId)));
  const signature = sign('sha256', Buffer.from(challenge.nonce, 'utf8'), privateKey).toString('base64');
  const authResponse = await fetch(new URL('/ai/agent-bridge/authenticate', config.platformUrl), {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ deviceId: config.deviceId, challengeId: challenge.challengeId,
      nonce: challenge.nonce, signature }),
  });
  if (!authResponse.ok) throw new Error('Bridge device authentication failed');
  const authBody = await authResponse.json();
  return authBody.data ?? authBody;
}
