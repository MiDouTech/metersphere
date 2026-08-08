import path from 'node:path';
import process from 'node:process';
import { pairDevice } from './pairing.mjs';

const configPath = process.env.MS_AGENT_BRIDGE_CONFIG ?? path.join(process.cwd(), 'config.json');
const pairingCode = process.argv[2];
if (!/^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/.test(pairingCode ?? '')) {
  throw new Error('Usage: npm run pair -- XXXX-XXXX-XXXX');
}
const paired = await pairDevice({ configPath, pairingCode });
console.log(`Paired device ${paired.deviceId}. No provider credential was uploaded.`);
