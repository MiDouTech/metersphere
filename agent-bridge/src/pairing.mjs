import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { createDeviceIdentity, saveDevicePrivateKey } from './device-auth.mjs';

export async function pairDevice({ configPath, pairingCode, platformUrl }) {
  if (!/^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/.test(pairingCode ?? '')) {
    throw new Error('The pairing request is invalid or expired');
  }
  const config = JSON.parse(await readFile(configPath, 'utf8'));
  const targetPlatform = platformUrl || config.platformUrl;
  if (config.deviceId && new URL(config.platformUrl).origin !== new URL(targetPlatform).origin) {
    throw new Error('This MeterSphere Agent is already paired with a different platform');
  }
  if (!String(targetPlatform).startsWith('https://') && config.allowInsecureDevelopment !== true) {
    throw new Error('Pairing requires HTTPS');
  }
  const identity = createDeviceIdentity();
  const response = await fetch(new URL('/ai/agent-bridge/pairing/consume', targetPlatform), {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ pairingCode, deviceName: config.deviceName, publicKey: identity.publicKey,
      certificateFingerprint: identity.fingerprint, bridgeVersion: config.bridgeVersion,
      protocolVersion: config.protocolVersion, osType: process.platform }),
  });
  if (!response.ok) throw new Error('Pairing failed or the request expired');
  const pairedBody = await response.json();
  const paired = pairedBody.data ?? pairedBody;
  await saveDevicePrivateKey(paired.deviceId, identity.privateKey);
  const next = { ...config, platformUrl: targetPlatform, deviceId: paired.deviceId };
  delete next.accessToken;
  await mkdir(path.dirname(configPath), { recursive: true });
  const temporary = `${configPath}.tmp`;
  await writeFile(temporary, `${JSON.stringify(next, null, 2)}\n`, { mode: 0o600 });
  await rename(temporary, configPath);
  return paired;
}
