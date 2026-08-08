import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { pairDevice } from './pairing.mjs';

const rawUrl = process.argv[2];
const request = new URL(rawUrl);
if (request.protocol !== 'metersphere-agent:' || request.hostname !== 'pair') {
  throw new Error('Unsupported MeterSphere Agent request');
}
const pairingCode = request.searchParams.get('pairingCode');
const platformUrl = request.searchParams.get('platformUrl');
const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const configPath = process.env.MS_AGENT_BRIDGE_CONFIG ?? path.join(root, 'config.json');
let paired = false;
try {
  const existing = JSON.parse(await readFile(configPath, 'utf8'));
  paired = Boolean(existing.deviceId) && new URL(existing.platformUrl).origin === new URL(platformUrl).origin;
} catch {
  // A first-time installation has no paired device yet.
}
if (!paired) await pairDevice({ configPath, pairingCode, platformUrl });

const child = spawn(process.execPath, [path.join(root, 'src', 'main.mjs')], {
  cwd: root, detached: true, windowsHide: true, stdio: 'ignore',
  env: { ...process.env, MS_AGENT_BRIDGE_CONFIG: configPath },
});
child.unref();
