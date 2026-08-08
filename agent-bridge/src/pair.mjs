import { readFile, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { createDeviceIdentity, saveDevicePrivateKey } from './device-auth.mjs';

const configPath = process.env.MS_AGENT_BRIDGE_CONFIG ?? path.join(process.cwd(), 'config.json');
const config = JSON.parse(await readFile(configPath, 'utf8'));
const pairingCode = process.argv[2];
if (!/^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/.test(pairingCode ?? '')) {
  throw new Error('Usage: npm run pair -- XXXX-XXXX-XXXX');
}
if (!String(config.platformUrl).startsWith('https://') && config.allowInsecureDevelopment !== true) {
  throw new Error('Pairing requires HTTPS');
}
const identity = createDeviceIdentity();
const response = await fetch(new URL('/ai/agent-bridge/pairing/consume', config.platformUrl), {
  method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ pairingCode, deviceName: config.deviceName, publicKey: identity.publicKey,
    certificateFingerprint: identity.fingerprint, bridgeVersion: config.bridgeVersion,
    protocolVersion: config.protocolVersion, osType: process.platform }),
});
if (!response.ok) throw new Error('Pairing failed or code expired');
const pairedBody = await response.json();
const paired = pairedBody.data ?? pairedBody;
await saveDevicePrivateKey(paired.deviceId, identity.privateKey);
const next = { ...config, deviceId: paired.deviceId };
delete next.accessToken;
const temporary = `${configPath}.tmp`;
await writeFile(temporary, `${JSON.stringify(next, null, 2)}\n`, { mode: 0o600 });
await rename(temporary, configPath);
console.log(`Paired device ${paired.deviceId}. No provider credential was uploaded.`);
